package com.aimodelhub.agent.vo;

import com.aimodelhub.agent.entity.AgentMessage;

import java.time.LocalDateTime;

/**
 * 历史消息项。
 */
public class AgentMessageVO {

    private Long id;
    private String role;
    private String content;
    private String toolName;
    private LocalDateTime createTime;

    public static AgentMessageVO from(AgentMessage message) {
        AgentMessageVO vo = new AgentMessageVO();
        vo.id = message.getId();
        vo.role = message.getRole();
        vo.content = message.getContent();
        vo.toolName = message.getToolName();
        vo.createTime = message.getCreateTime();
        return vo;
    }

    public Long getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getToolName() {
        return toolName;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }
}
