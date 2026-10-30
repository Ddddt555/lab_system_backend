package com.lab.labsystem.common;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;

public class FileTypeUtils {

    /**
     * 校验文件真实类型（魔数校验）
     * @param file 上传的文件
     * @param expectedSuffix 期望的后缀名（如 .pdf）
     * @return 是否为真实合法的文件
     */
    public static boolean isValidFile(MultipartFile file, String expectedSuffix) {
        if (file.isEmpty()) return false;
        
        byte[] header = new byte[8];
        try (InputStream is = file.getInputStream()) {
            int read = is.read(header);
            if (read < 4) return false; // 文件太小，肯定不合法

            // 转为十六进制字符串比对
            String hex = bytesToHex(header);
            
            switch (expectedSuffix.toLowerCase()) {
                case ".pdf":
                    // %PDF: 25 50 44 46
                    return hex.startsWith("25504446");
                case ".docx":
                case ".zip":
                    // PK: 50 4B 03 04 (docx 本质上是个 zip)
                    return hex.startsWith("504B0304");
                case ".rar":
                    // Rar!: 52 61 72 21
                    return hex.startsWith("52617221");
                case ".doc":
                    // OLE2: D0 CF 11 E0 A1 B1 1A E1 (老版 Word)
                    return hex.startsWith("D0CF11E0");
                default:
                    return false;
            }
        } catch (IOException e) {
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