package com.lab.labsystem.service;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class MinioService {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * 【核心修复】系统启动时自动检查并创建 Bucket
     * 彻底解决 NoSuchBucket 报错，保证全新部署开箱即用
     */
    @PostConstruct
    public void init() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                System.out.println("✅ MinIO 存储桶 [" + bucketName + "] 初始化成功！");
            } else {
                System.out.println("✅ MinIO 存储桶 [" + bucketName + "] 已存在，无需创建。");
            }
        } catch (Exception e) {
            System.err.println("❌ MinIO 存储桶初始化失败: " + e.getMessage());
        }
    }

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

    /** ⚠️ 删除文件（用于失败补偿清理孤儿文件） */
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

    /** 获取文件流（用于后端代理转发，避免暴露 MinIO 地址） */
    public java.io.InputStream getObjectStream(String objectName) throws Exception {
        return minioClient.getObject(
            io.minio.GetObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build()
        );
    }

    /** 
     * 【新增：获取 MinIO 所有文件列表】
     * 用于定时任务扫描并清理孤儿文件（有文件但数据库无记录）
     */
    public List<String> listAllFiles() throws Exception {
        List<String> fileNames = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());
        for (Result<Item> result : results) {
            Item item = result.get();
            fileNames.add(item.objectName());
        }
        return fileNames;
    }
    
    /**
     * 【新增】获取文件的最后修改时间（用于防误删正在上传的文件）
     */
    public java.time.LocalDateTime getLastModifiedTime(String objectName) throws Exception {
        io.minio.StatObjectResponse stat = minioClient.statObject(
            io.minio.StatObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build()
        );
        return java.time.LocalDateTime.ofInstant(stat.lastModified().toInstant(), java.time.ZoneId.systemDefault());
    }
}