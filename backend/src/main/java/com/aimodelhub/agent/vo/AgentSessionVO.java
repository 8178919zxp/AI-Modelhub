package com.aimodelhub.agent.vo;

import com.aimodelhub.agent.entity.AgentSession;

import java.time.LocalDateTime;

/**
 * 会话列表项。刻意不返回 {@code user_id}：
 * 前端拿到的数据里不该出现「我是几号用户」这种可用于横向试探的字段。
 */
public class AgentSessionVO {

    private Long id;
    private String title;
    private Integer messageCount;
    private LocalDateTime lastMessageAt;

    public static AgentSessionVO from(AgentSession session) {
        AgentSessionVO vo = new AgentSessionVO();
        vo.id = session.getId();
        vo.title = session.getTitle();
        vo.messageCount = session.getMessageCount();
        vo.lastMessageAt = session.getLastMessageAt() != null
                ? session.getLastMessageAt()
                : session.getCreateTime();
        return vo;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Integer getMessageCount() {
        return messageCount;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }
}
