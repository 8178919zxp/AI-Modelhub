package com.aimodelhub.agent.entity;

import java.time.LocalDateTime;

/**
 * Agent 会话（对应表 {@code agent_session}）。
 * <p>
 * 一个会话 = 一段独立的多轮对话上下文。会话永远归属于某个用户，
 * {@code user_id} 由服务端从 JWT 中取出后写入，<b>不接受前端传值</b>——
 * 这是防止水平越权的第一道闸门。
 */
public class AgentSession {

    private Long id;
    private Long userId;
    private String title;
    private Integer messageCount = 0;
    private LocalDateTime lastMessageAt;
    private Integer status = 1;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(Integer messageCount) {
        this.messageCount = messageCount;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
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

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
