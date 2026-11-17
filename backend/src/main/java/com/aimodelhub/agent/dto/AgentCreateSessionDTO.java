package com.aimodelhub.agent.dto;

import jakarta.validation.constraints.Size;

/**
 * 新建会话的请求体。标题可以不传，不传时由首条用户消息自动生成。
 */
public class AgentCreateSessionDTO {

    @Size(max = 120, message = "会话标题不能超过 120 字符")
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
