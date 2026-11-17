package com.aimodelhub.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /** 允许上传的图片 MIME 类型 */
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp"
    );

    /**
     * 文件大小上限：5 MB。
     * 必须与 {@code spring.servlet.multipart.max-file-size} 以及模型服务的上传限制
     * （{@code _MAX_UPLOAD_MB}）保持一致；否则文件能存进 MinIO，却被推理服务拒绝。
     */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${app.file-proxy.base-url:http://localhost:8080}")
    private String backendBaseUrl;

    public FileStorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * 把文件上传到 MinIO。
     *
     * @param file      上传的文件
     * @param subfolder 可选的子目录（如 "predictions"）
     * @return 包含对象 key、可访问 URL 与原始文件名的上传结果
     * @throws IllegalArgumentException 文件为空、过大或类型不合法时抛出
     * @throws RuntimeException         MinIO 上传失败时抛出
     */
    public UploadResult upload(MultipartFile file, String subfolder) {
        // 1. 参数校验
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "文件大小不能超过 " + (MAX_FILE_SIZE / 1024 / 1024) + "MB，当前: " +
                    (file.getSize() / 1024 / 1024) + "MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "不支持的文件类型: " + contentType + "，仅支持: " + ALLOWED_IMAGE_TYPES);
        }

        // 2. 生成安全的对象 key：subfolder/yyyy/MM/dd/uuid.ext
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "untitled";
        }
        // 清洗文件名：只保留扩展名，文件名部分替换为 UUID
        String ext = "";
        int dotIdx = originalName.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < originalName.length() - 1) {
            ext = originalName.substring(dotIdx).toLowerCase();
            // 校验扩展名与 MIME 类型是否匹配
            if (!ext.matches("\\.(jpg|jpeg|png|webp|gif|bmp|tiff|ico)")) {
                ext = ".jpg"; // 未知扩展名时默认使用 .jpg
            }
        } else {
            // 根据 content type 推断扩展名
            ext = switch (contentType.toLowerCase()) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                case "image/gif" -> ".gif";
                case "image/bmp" -> ".bmp";
                default -> ".bin";
            };
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String objectKey = (subfolder != null && !subfolder.isBlank()
                ? subfolder + "/" + datePath
                : datePath) + "/" + uuid + ext;

        // 3. 上传到 MinIO
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
            log.info("Uploaded '{}' ({}) → MinIO: {}/{}",
                    originalName, file.getSize(), bucket, objectKey);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinIO upload failed for '{}': {}", originalName, e.getMessage());
            throw new RuntimeException("文件存储失败，请稍后重试", e);
        }

        // 4. 拼接 URL —— 用后端代理地址，彻底避开 CORS 问题
        //    形如：http://backend:8080/api/files/{objectKey}
        //    实际下载由 FileProxyController 完成，
        //    因此不受 MinIO 的 CORS 与桶策略影响。
        String publicUrl = backendBaseUrl.replaceAll("/$", "") + "/api/files/" + objectKey;

        return new UploadResult(objectKey, publicUrl, originalName, contentType, file.getSize());
    }

    /**
     * 生成预签名的 GET 地址（有效期 1 小时）。
     * 桶不是公共读时可以使用这种方式。
     */
    public String getPresignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .method(Method.GET)
                            .expiry(1, TimeUnit.HOURS)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for '{}': {}", objectKey, e.getMessage());
            return null;
        }
    }

    /**
     * 从 MinIO 删除文件。
     */
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            log.info("Deleted MinIO object: {}/{}", bucket, objectKey);
        } catch (Exception e) {
            log.warn("Failed to delete MinIO object '{}': {}", objectKey, e.getMessage());
        }
    }

    /**
     * 暴露桶名，便于排查问题（FileProxyController 打日志时会用到）。
     */
    public String getBucketName() {
        return bucket;
    }

    /**
     * 从 MinIO 读取文件内容，并返回字节数组与 content type。
     * 供文件代理接口使用。
     *
     * @return 包含字节数组与 content type 的结果；文件不存在时返回 null
     */
    public DownloadResult download(String objectKey) {
        if (bucket == null || bucket.isBlank()) {
            log.error("MinIO bucket name is not configured (minio.bucket is null/empty)");
            return null;
        }
        try (var stream = minioClient.getObject(
                io.minio.GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build())) {

            byte[] data = stream.readAllBytes();
            String contentType = stream.headers().get("Content-Type");
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            log.debug("Downloaded MinIO object: {}/{} ({} bytes)", bucket, objectKey, data.length);
            return new DownloadResult(data, contentType);
        } catch (Exception e) {
            log.warn("Failed to download MinIO object bucket='{}' key='{}': {}",
                    bucket, objectKey, e.getMessage());
            return null;
        }
    }

    /**
     * 上传结果。
     */
    public record UploadResult(
            String objectKey,
            String publicUrl,
            String originalFilename,
            String contentType,
            long fileSize
    ) {}

    /**
     * 下载结果。
     */
    public record DownloadResult(
            byte[] data,
            String contentType
    ) {}
}
