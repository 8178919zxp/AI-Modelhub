package com.aimodelhub.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发送一条聊天消息的请求体。
 * <p>
 * {@code sessionId} 为空表示「开一个新会话」：后端会先建会话再回答，
 * 前端因此不需要先调一次创建接口，少一次往返。
 */
public class AgentChatRequest {

    /** 会话 id；为空则自动新建会话。 */
    private Long sessionId;

    /** 用户输入。上限 4000 字符，与服务端 app.agent.max-input-chars 呼应。 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容不能超过 4000 字符")
    private String message;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
