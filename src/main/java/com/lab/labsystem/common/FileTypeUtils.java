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
                    // 🛑【核心修复】不仅查魔数，还要防 ZIP 炸弹
                    if (!hex.startsWith("504B0304")) return false;
                    return isSafeZip(file);
                case ".rar":
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
     * 🛑【新增】防 Zip 炸弹：限制压缩包内文件数量和解压后的总大小
     */
    private static boolean isSafeZip(MultipartFile file) {
        long totalUncompressedSize = 0;
        int entryCount = 0;
        final long MAX_SIZE = 200L * 1024 * 1024; // 限制解压后最大 200MB
        final int MAX_ENTRIES = 1000;             // 限制最多 1000 个文件

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) return false;
                
                totalUncompressedSize += entry.getSize();
                if (totalUncompressedSize > MAX_SIZE) {
                    return false;
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