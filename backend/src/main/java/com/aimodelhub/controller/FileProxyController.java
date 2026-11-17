package com.aimodelhub.controller;

import com.aimodelhub.service.FileStorageService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 通过后端代理 MinIO 的文件下载。
 *
 * 解决两个问题：
 * 1. MinIO 的 CORS —— 浏览器直连 MinIO 会被拦截
 * 2. 公共读策略 —— 即使桶策略设置失败，用户仍能查看文件
 *
 * {@code predict_record.file_url} 中保存的都应是通过本接口的相对地址：
 *   /api/files/predictions/2026/06/04/uuid.jpg
 *
 * 该接口是公开的（读取图片不需要登录）。
 */
@RestController
@RequestMapping("/api/files")
public class FileProxyController {

    private static final Logger log = LoggerFactory.getLogger(FileProxyController.class);

    private static final int CACHE_MAX_AGE_SECONDS = 3600;

    private final FileStorageService fileStorageService;

    public FileProxyController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * 代理下载 MinIO 中的文件。
     * <p>
     * 使用 {@code {*objectKey}}（PathPatternParser 的「剩余路径段」语法）
     * 完整捕获多段的 MinIO 对象 key，包括斜杠、UUID 与扩展名。
     * 这样可以避免用 {@code HttpServletRequest.getRequestURI()} 截取字符串的不可靠做法。
     * <p>
     * 示例：{@code GET /api/files/predictions/2026/06/04/abc.jpg}
     * → objectKey = {@code predictions/2026/06/04/abc.jpg}
     */
    @GetMapping("/{*objectKey}")
    public void getFile(@PathVariable String objectKey,
                        HttpServletResponse response) throws IOException {

        if (objectKey == null || objectKey.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing file path");
            return;
        }

        // URL 解码（浏览器可能对特殊字符做百分号编码）
        String decodedKey = URLDecoder.decode(objectKey, StandardCharsets.UTF_8);

        log.debug("Proxying MinIO file: bucket={}, objectKey={}",
                fileStorageService.getBucketName(), decodedKey);

        FileStorageService.DownloadResult result = fileStorageService.download(decodedKey);

        if (result == null || result.data() == null) {
            log.warn("File not found in MinIO: objectKey={}", decodedKey);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found: " + decodedKey);
            return;
        }

        response.setContentType(result.contentType());
        response.setContentLength(result.data().length);
        response.setHeader("Cache-Control", "public, max-age=" + CACHE_MAX_AGE_SECONDS);
        response.getOutputStream().write(result.data());
        response.getOutputStream().flush();
    }
}
