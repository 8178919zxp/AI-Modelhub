package com.aimodelhub.agent.vo;

/**
 * 非流式对话的返回体（{@code POST /api/agent/chat/sync}）。
 * <p>
 * 流式接口（SSE）不返回这个对象，而是把同样的内容拆成 {@code token} / {@code done} 事件。
 */
public class AgentChatResponse {

    private Long sessionId;
    private Long messageId;
    private String content;
    private Integer tokenCount;
    private Long elapsedMs;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public Long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }
}
