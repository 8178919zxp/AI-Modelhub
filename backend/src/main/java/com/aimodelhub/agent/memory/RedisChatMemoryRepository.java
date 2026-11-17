package com.aimodelhub.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 基于 Redis 的 {@link ChatMemoryRepository} —— Spring AI 上下文窗口的存储后端。
 *
 * <h3>为什么上下文放 Redis，而全量历史放 MySQL？</h3>
 * <ul>
 *   <li><b>MySQL（agent_message）</b>是「事实档案」：用户问过什么、Agent 答过什么，
 *       永久保存，供会话列表、历史回放和审计使用。</li>
 *   <li><b>Redis（本类）</b>是「工作记忆」：只保留最近 N 条，带 TTL，
 *       每轮请求都要读一次、写一次，放在内存数据库才够快。</li>
 * </ul>
 * 两者职责不同，不能互相替代：全量历史塞进 prompt 会打爆 token；
 * 只存 Redis 则重启即失忆。
 *
 * <h3>故障策略</h3>
 * Redis 不可用时 <b>降级为「无上下文」而不是报错</b>：读返回空列表，写直接丢弃并打告警。
 * 权衡是「这一轮模型看不到历史」，但对话本身仍然可用——比整个聊天接口 500 要好。
 */
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryRepository.class);

    /** key 前缀。刻意与业务缓存（aimh:dev:）分开，便于单独清理。 */
    private static final String KEY_PREFIX = "agent:memory:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final int maxMessages;

    public RedisChatMemoryRepository(StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     Duration ttl,
                                     int maxMessages) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
        this.maxMessages = maxMessages;
    }

    @Override
    public List<String> findConversationIds() {
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }
            return keys.stream().map(k -> k.substring(KEY_PREFIX.length())).toList();
        } catch (RuntimeException e) {
            log.warn("Redis 不可用，无法枚举会话上下文: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + conversationId);
            return AgentMessageCodec.decode(json, objectMapper);
        } catch (RuntimeException e) {
            log.warn("Redis 不可用，本轮对话无历史上下文 (conversationId={}): {}",
                    conversationId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        // 只保留最近 maxMessages 条：窗口裁剪放在存储层做，
        // 这样即使上层传了全量历史，Redis 里的 key 也不会无限增长。
        List<Message> window = messages.size() <= maxMessages
                ? messages
                : new ArrayList<>(messages.subList(messages.size() - maxMessages, messages.size()));
        try {
            redisTemplate.opsForValue()
                    .set(KEY_PREFIX + conversationId,
                            AgentMessageCodec.encode(window, objectMapper),
                            ttl);
        } catch (RuntimeException e) {
            log.warn("Redis 不可用，上下文未写入 (conversationId={}): {}",
                    conversationId, e.getMessage());
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        try {
            redisTemplate.delete(KEY_PREFIX + conversationId);
        } catch (RuntimeException e) {
            log.warn("Redis 不可用，上下文未清理 (conversationId={}): {}",
                    conversationId, e.getMessage());
        }
    }

    /** 供服务层判断「窗口是否为空、需不需要从 MySQL 回填」。 */
    public boolean isEmpty(String conversationId) {
        return findByConversationId(conversationId).isEmpty();
    }
}
