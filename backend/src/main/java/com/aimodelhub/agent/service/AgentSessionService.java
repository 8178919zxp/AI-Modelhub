package com.aimodelhub.agent.service;

import com.aimodelhub.agent.config.AgentProperties;
import com.aimodelhub.agent.entity.AgentMessage;
import com.aimodelhub.agent.entity.AgentSession;
import com.aimodelhub.agent.enums.MessageRole;
import com.aimodelhub.agent.mapper.AgentMessageMapper;
import com.aimodelhub.agent.mapper.AgentSessionMapper;
import com.aimodelhub.agent.vo.AgentMessageVO;
import com.aimodelhub.agent.vo.AgentSessionVO;
import com.aimodelhub.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话与消息的读写中心。
 * <p>
 * <b>职责边界：</b>这个类只管「存什么、按什么顺序取」，不关心大模型怎么回答。
 * 因此 {@link AgentChatService} 依赖它，而它<b>不</b>依赖 ChatClient——
 * 依赖方向单向，将来换模型或换存储都不会牵一发动全身。
 */
@Service
public class AgentSessionService {

    private static final Logger log = LoggerFactory.getLogger(AgentSessionService.class);

    /** 单个用户最多保留的会话数，防止刷接口把表撑爆。 */
    private static final int MAX_SESSIONS_PER_USER = 200;

    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentProperties properties;

    public AgentSessionService(AgentSessionMapper sessionMapper,
                               AgentMessageMapper messageMapper,
                               AgentProperties properties) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.properties = properties;
    }

    // ================================================================
    // 会话
    // ================================================================

    /** 新建会话。标题留空时用「新会话」，等首条用户消息再补。 */
    @Transactional
    public AgentSession create(Long userId, String title) {
        if (sessionMapper.countByUser(userId) >= MAX_SESSIONS_PER_USER) {
            throw new com.aimodelhub.common.exception.BusinessException(
                    "会话数量已达上限（" + MAX_SESSIONS_PER_USER + "），请先删除历史会话");
        }
        AgentSession session = new AgentSession();
        session.setUserId(userId);
        session.setTitle(normalizeTitle(title));
        session.setMessageCount(0);
        session.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        session.setCreateTime(now);
        session.setUpdateTime(now);
        sessionMapper.insert(session);
        log.info("Agent session created: id={}, userId={}", session.getId(), userId);
        return session;
    }

    public List<AgentSessionVO> list(Long userId) {
        return sessionMapper.findByUser(userId, MAX_SESSIONS_PER_USER)
                .stream()
                .map(AgentSessionVO::from)
                .toList();
    }

    /**
     * 取会话并校验归属。
     * <p>
     * 查不到（不存在 / 别人的 / 已删除）统一抛 404：
     * 如果对「别人的会话」返回 403，就等于告诉攻击者「这个 id 真实存在」。
     */
    public AgentSession requireOwned(Long userId, Long sessionId) {
        AgentSession session = sessionMapper.findByIdAndUser(sessionId, userId);
        if (session == null) {
            throw new NotFoundException("会话不存在");
        }
        return session;
    }

    @Transactional
    public void delete(Long userId, Long sessionId) {
        int affected = sessionMapper.softDelete(sessionId, userId);
        if (affected == 0) {
            throw new NotFoundException("会话不存在");
        }
        messageMapper.softDeleteBySession(sessionId, userId);
        log.info("Agent session deleted: id={}, userId={}", sessionId, userId);
    }

    // ================================================================
    // 消息
    // ================================================================

    public List<AgentMessageVO> listMessages(Long userId, Long sessionId) {
        requireOwned(userId, sessionId);
        return messageMapper.findBySession(sessionId, userId)
                .stream()
                .map(AgentMessageVO::from)
                .toList();
    }

    /** 追加一条消息，并同步刷新会话统计。 */
    @Transactional
    public AgentMessage append(Long sessionId, Long userId, MessageRole role, String content) {
        AgentMessage message = AgentMessage.of(sessionId, userId, role, content);
        messageMapper.insert(message);

        AgentSession session = sessionMapper.findByIdAndUser(sessionId, userId);
        if (session != null) {
            int count = messageMapper.countBySession(sessionId);
            // 首条用户消息作为会话标题，让历史列表能看懂「这段对话在聊什么」
            String title = session.getTitle();
            if (role == MessageRole.USER && count <= 1) {
                title = buildTitle(content);
            }
            sessionMapper.updateStats(sessionId, userId, title, count, LocalDateTime.now());
        }
        return message;
    }

    /**
     * 追加一条「已经组装好」的消息（例如工具调用留痕），并刷新会话统计。
     * 与 {@link #append} 的区别：不改标题、不推断角色，纯粹落库。
     */
    @Transactional
    public AgentMessage appendEntity(AgentMessage message) {
        messageMapper.insert(message);
        AgentSession session = sessionMapper.findByIdAndUser(message.getSessionId(), message.getUserId());
        if (session != null) {
            int count = messageMapper.countBySession(message.getSessionId());
            sessionMapper.updateStats(message.getSessionId(), message.getUserId(),
                    session.getTitle(), count, LocalDateTime.now());
        }
        return message;
    }

    /**
     * 取最近 N 条「纯对话」消息（时间正序），用于 Redis 上下文丢失后重建窗口。
     * <p>
     * SQL 里按 id 倒序取，这里再翻转——这样只取最近 N 条，
     * 而不是把整个会话几万字都读出来再截断。
     */
    public List<AgentMessage> recentDialogue(Long sessionId, Long userId, int limit) {
        List<AgentMessage> desc = messageMapper.findRecentDialogueDesc(sessionId, userId, limit);
        List<AgentMessage> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        return asc;
    }

    public int countMessages(Long sessionId) {
        return messageMapper.countBySession(sessionId);
    }

    // ================================================================
    // 内部
    // ================================================================

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "新会话";
        }
        String trimmed = title.trim();
        int max = properties.getTitleMaxLength();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /** 把首条用户消息压成一行标题：去掉换行、截断、补省略号。 */
    private String buildTitle(String firstUserMessage) {
        if (firstUserMessage == null || firstUserMessage.isBlank()) {
            return "新会话";
        }
        String oneLine = firstUserMessage.replaceAll("\\s+", " ").trim();
        int max = properties.getTitleMaxLength();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }
}
