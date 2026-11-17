package com.aimodelhub.agent.entity;

import com.aimodelhub.agent.enums.MessageRole;

import java.time.LocalDateTime;

/**
 * Agent 消息（对应表 {@code agent_message}）。
 * <p>
 * 三类记录共用一张表：
 * <ul>
 *   <li>{@code USER} —— 用户输入</li>
 *   <li>{@code ASSISTANT} —— 模型回复（Markdown）</li>
 *   <li>{@code TOOL} —— 工具调用快照（第二、三阶段使用）</li>
 * </ul>
 * 用一张表而不是三张，是因为它们永远按 {@code session_id + id} 顺序一起读出来展示，
 * 拆表只会多一次 JOIN。
 */
public class AgentMessage {

    private Long id;
    private Long sessionId;
    private Long userId;
    private String role;
    private String content;
    private String toolName;
    private String toolPayload;
    private Integer tokenCount;
    private Integer status = 1;
    private LocalDateTime createTime;

    public static AgentMessage of(Long sessionId, Long userId, MessageRole role, String content) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role.name());
        message.setContent(content);
        message.setTokenCount(estimateTokens(content));
        return message;
    }

    /**
     * 极简 token 估算：中文约 1 字 ≈ 1 token，英文约 4 字符 ≈ 1 token。
     * 不引入分词器是刻意的——这里只用于「上下文裁剪」和「成本监控」的粗粒度判断，
     * 精确计费应该看 DeepSeek 返回的 usage 字段。
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                cjk++;
            } else {
                other++;
            }
        }
        return cjk + Math.max(1, other / 4);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolPayload() {
        return toolPayload;
    }

    public void setToolPayload(String toolPayload) {
        this.toolPayload = toolPayload;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
