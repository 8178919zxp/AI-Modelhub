package com.aimodelhub.agent.entity;

import java.time.LocalDateTime;

/**
 * Agent 附件（对应表 {@code agent_attachment}）。
 * <p>
 * 只存元数据与本地相对路径，<b>不存文件内容</b>：
 * 把 5MB 图片塞进 MySQL 会让备份、主从同步和慢查询一并变糟。
 */
public class AgentAttachment {

    public static final String STATUS_TEMP = "TEMP";
    public static final String STATUS_USED = "USED";

    private Long id;
    private Long userId;
    private Long sessionId;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private String storageKey;
    private String status;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
