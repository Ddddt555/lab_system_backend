package com.lab.labsystem.service;

import io.minio.*;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Service
public class MinioService {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /** 上传文件 */
    public String upload(MultipartFile file, String objectName) throws Exception {
        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(in, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build()
            );
        }
        return objectName;
    }

    /** 生成预签名下载 URL（默认有效期 1 小时） */
    public String getPresignedDownloadUrl(String objectName) throws Exception {
        return minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucketName)
                .object(objectName)
                .expiry(1, TimeUnit.HOURS)
                .build()
        );
    }

    /** 生成预签名预览 URL（用于 PDF 在浏览器 inline 打开） */
    public String getPresignedPreviewUrl(String objectName) throws Exception {
        return minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucketName)
                .object(objectName)
                .expiry(30, TimeUnit.MINUTES)
                .build()
        );
    }

    /** ⚠️ 步骤 6 新增：删除文件（用于失败补偿清理孤儿文件） */
    public void remove(String objectName) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build()
            );
        } catch (Exception e) {
            System.err.println("清理 MinIO 文件失败: " + objectName + ", 原因: " + e.getMessage());
        }
    }
    /** ⚠️ 新增：获取文件流（用于后端代理转发，避免暴露 MinIO 地址） */
    public java.io.InputStream getObjectStream(String objectName) throws Exception {
        return minioClient.getObject(
            io.minio.GetObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build()
        );
    }
}