package com.aimodelhub.agent.service;

import com.aimodelhub.agent.config.AgentProperties;
import com.aimodelhub.agent.entity.AgentAttachment;
import com.aimodelhub.agent.mapper.AgentAttachmentMapper;
import com.aimodelhub.common.exception.BusinessException;
import com.aimodelhub.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Agent 附件的落盘与读取。
 *
 * <h3>安全要点</h3>
 * <ul>
 *   <li>文件名<b>不参与</b>磁盘路径：一律用 UUID + 白名单后缀，杜绝 {@code ../../} 之类的路径穿越；</li>
 *   <li>大小与 MIME 白名单在这里先拦一次（与 {@code FileStorageService} 的规则保持一致），
 *       避免把明显不合法的文件写到磁盘上再失败；</li>
 *   <li>读取时按 id + userId 查库，别人的附件 id 猜中了也读不到。</li>
 * </ul>
 */
@Service
public class AgentAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AgentAttachmentService.class);

    /** 与 FileStorageService.ALLOWED_IMAGE_TYPES / MAX_FILE_SIZE 保持一致 */
    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp");
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private final AgentAttachmentMapper attachmentMapper;
    private final Path root;

    public AgentAttachmentService(AgentAttachmentMapper attachmentMapper, AgentProperties properties) {
        this.attachmentMapper = attachmentMapper;
        this.root = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
    }

    /** 保存上传文件，返回附件记录（含 id，供后续任务引用）。 */
    public AgentAttachment store(Long userId, Long sessionId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException("文件大小不能超过 5MB，当前 " + (file.getSize() / 1024) + "KB");
        }
        String contentType = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException("不支持的文件类型：" + contentType + "，仅支持 JPG/PNG/WEBP/GIF/BMP");
        }

        String storageKey = userId + "/" + UUID.randomUUID() + extensionOf(contentType);
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            // 理论上不可能触发（storageKey 完全由服务端生成），保留作为防御
            throw new BusinessException("非法的存储路径");
        }

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (IOException e) {
            log.error("附件写入失败: {}", target, e);
            throw new BusinessException("文件保存失败，请重试");
        }

        AgentAttachment attachment = new AgentAttachment();
        attachment.setUserId(userId);
        attachment.setSessionId(sessionId);
        attachment.setOriginalFilename(file.getOriginalFilename());
        attachment.setContentType(contentType);
        attachment.setFileSize(file.getSize());
        attachment.setStorageKey(storageKey);
        attachment.setStatus(AgentAttachment.STATUS_TEMP);
        attachmentMapper.insert(attachment);

        log.info("Agent 附件已保存: id={}, userId={}, {} ({} bytes)",
                attachment.getId(), userId, file.getOriginalFilename(), file.getSize());
        return attachment;
    }

    /** 按 id + userId 取附件，并校验物理文件仍在。 */
    public AgentAttachment require(Long userId, Long attachmentId) {
        AgentAttachment attachment = attachmentMapper.findByIdAndUser(attachmentId, userId);
        if (attachment == null) {
            throw new NotFoundException("附件不存在");
        }
        return attachment;
    }

    /** 读回文件字节。文件已被清理时给出明确提示，而不是抛 NullPointerException。 */
    public byte[] loadBytes(AgentAttachment attachment) {
        Path path = root.resolve(attachment.getStorageKey()).normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            throw new BusinessException("附件已过期或已被清理，请重新上传");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BusinessException("读取附件失败，请重新上传");
        }
    }

    public void markUsed(Long userId, Long attachmentId) {
        attachmentMapper.markUsed(attachmentId, userId);
    }

    private static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/bmp" -> ".bmp";
            default -> ".jpg";
        };
    }
}
