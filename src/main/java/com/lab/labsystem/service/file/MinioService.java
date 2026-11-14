package com.lab.labsystem.service.file;

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
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MinioService {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * 系统启动时自动检查并创建 Bucket
     */
    @PostConstruct
    public void init() {
        try {
            ensureBucketExists();
            log.info("MinIO 存储桶 [{}] 初始化成功！", bucketName);
        } catch (Exception e) {
            log.error("MinIO 存储桶初始化失败，系统可能无法正常提供文件服务", e);
        }
    }

    /**
     * 确保存储桶存在，幂等操作
     */
    private void ensureBucketExists() throws Exception {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!found) {
            try {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("自动创建 MinIO 存储桶: {}", bucketName);
            } catch (Exception e) {
                // 处理并发创建冲突
                if (!e.getMessage().contains("BucketAlreadyOwnedByYou") && !e.getMessage().contains("BucketAlreadyExists")) {
                    throw e;
                }
            }
        }
    }

    /**
     * 上传文件
     */
    public String upload(MultipartFile file, String objectName) throws Exception {
        // 兜底：每次上传前检查一次，防止启动时 MinIO 没起导致桶没建
        ensureBucketExists();
        
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

    /** 删除文件（用于失败补偿清理孤儿文件） */
    public void remove(String objectName) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build()
            );
        } catch (Exception e) {
            log.error("清理 MinIO 文件失败: {}", objectName, e);
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
     * 获取 MinIO 所有文件列表
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
     * 获取文件的最后修改时间（用于防误删正在上传的文件）
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