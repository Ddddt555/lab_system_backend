package com.lab.labsystem.common;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileTypeUtils {

    public static boolean isValidFile(MultipartFile file, String expectedSuffix) {
        if (file.isEmpty()) return false;
        
        byte[] header = new byte[8];
        try (InputStream is = file.getInputStream()) {
            int read = is.read(header);
            if (read < 4) return false; 

            String hex = bytesToHex(header);
            
            switch (expectedSuffix.toLowerCase()) {
                case ".pdf":
                    return hex.startsWith("25504446");
                case ".docx":
                case ".zip":
                    if (!hex.startsWith("504B0304")) return false;
                    return isSafeZip(file);
                case ".rar":
                    // RAR 格式由于没有原生的流式解析库，仅做魔数校验
                    // 兜底策略依赖 Spring 的 max-file-size 限制及服务器磁盘监控
                    return hex.startsWith("52617221");
                case ".doc":
                    return hex.startsWith("D0CF11E0");
                default:
                    return false;
            }
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 防 Zip 炸弹：通过流式读取实际解压后的数据量，而不是信任文件头声明的大小
     */
    private static boolean isSafeZip(MultipartFile file) {
        long totalUncompressedSize = 0;
        int entryCount = 0;
        
        // 限制解压后总大小最大 200MB
        final long MAX_TOTAL_SIZE = 200L * 1024 * 1024; 
        // 限制单个文件解压后最大 50MB
        final long MAX_SINGLE_SIZE = 50L * 1024 * 1024; 
        // 限制最多 1000 个文件
        final int MAX_ENTRIES = 1000;             

        byte[] buffer = new byte[8192];

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    return false;
                }

                long currentEntrySize = 0;
                int read;
                // 逐个读取当前 entry 的字节，计算真实解压大小
                while ((read = zis.read(buffer)) != -1) {
                    currentEntrySize += read;
                    totalUncompressedSize += read;

                    if (currentEntrySize > MAX_SINGLE_SIZE || totalUncompressedSize > MAX_TOTAL_SIZE) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}