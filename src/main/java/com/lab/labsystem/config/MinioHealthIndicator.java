package com.lab.labsystem.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;

@Component
public class MinioHealthIndicator implements HealthIndicator {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Override
    public Health health() {
        try {
            // 发起一次轻量级请求，检查桶是否存在及网络连通性
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                return Health.down()
                        .withDetail("MinIO", "连接正常，但存储桶不存在")
                        .withDetail("bucket", bucketName)
                        .build();
            }
            return Health.up().withDetail("MinIO", "连接正常").build();
        } catch (Exception e) {
            // 连不上则返回 DOWN，促使 /actuator/health/readiness 返回 503
            return Health.down()
                    .withDetail("MinIO", "连接失败")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}