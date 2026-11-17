package com.aimodelhub.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    /** 允许匿名读取桶内所有对象的 JSON 策略文档 */
    private static final String PUBLIC_READ_POLICY = """
        {
          "Version": "2012-10-17",
          "Statement": [
            {
              "Effect": "Allow",
              "Principal": {"AWS": ["*"]},
              "Action": ["s3:GetObject"],
              "Resource": ["arn:aws:s3:::%s/*"]
            }
          ]
        }
        """;

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket}")
    private String bucket;

    /**
     * 创建 MinIO 客户端，并在启动时做连通性检查、自动建桶与设置公共读策略。
     * MinIO 不可用只打告警，不阻断应用启动。
     */
    @Bean
    public MinioClient minioClient() {
        log.info("Initializing MinIO client: endpoint={}, bucket={}", endpoint, bucket);

        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        // 1. 连通性检查
        try {
            client.listBuckets();
            log.info("MinIO connected successfully at {}", endpoint);
        } catch (Exception e) {
            log.error("============================================");
            log.error("MinIO is NOT REACHABLE at {}", endpoint);
            log.error("Ensure MinIO container is running:");
            log.error("  docker run -d --name minio -p 9000:9000 -p 9001:9001 \\");
            log.error("    -e MINIO_ROOT_USER=<user> -e MINIO_ROOT_PASSWORD=<secret> \\");
            log.error("    minio/minio server /data --console-address :9001");
            log.error("============================================");
            log.error("MinIO features (file upload/image storage) will NOT work.", e);
            return client;
        }

        // 2. 自动创建桶
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket '{}' created successfully", bucket);
            } else {
                log.info("MinIO bucket '{}' already exists", bucket);
            }
        } catch (Exception e) {
            log.error("Failed to check/create MinIO bucket '{}': {}", bucket, e.getMessage());
            return client;
        }

        // 3. 自动设置公共读策略（幂等，容器重启后依然生效）
        try {
            String policy = String.format(PUBLIC_READ_POLICY, bucket);
            client.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucket)
                            .config(policy)
                            .build()
            );
            log.info("MinIO bucket '{}' policy set to public-read (auto)", bucket);
        } catch (Exception e) {
            log.warn("Failed to set public-read policy on bucket '{}': {}", bucket, e.getMessage());
            log.warn("Files may not be directly accessible via URL. The presigned URL fallback will be used.");
        }

        return client;
    }
}
