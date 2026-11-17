package com.aimodelhub.agent.service;

import com.aimodelhub.agent.config.AgentProperties;
import com.aimodelhub.common.exception.RateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

/**
 * 每用户每日大模型调用配额。
 *
 * <h3>为什么必须有</h3>
 * 一次对话就是一次真实的付费 API 调用。如果没有配额，一个脚本循环刷
 * {@code POST /api/agent/chat} 就能把账户余额烧光——这是 LLM 应用最典型的成本失控方式。
 *
 * <h3>实现取舍</h3>
 * 用 Redis 的 {@code INCR} + 首次设置过期时间，是「原子自增 + 自动清零」最省事的组合，
 * 不需要定时任务。key 里带日期，跨天自然换新 key。
 * <p>
 * <b>Redis 不可用时选择放行（fail-open）</b>：配额是成本保护，不是安全边界。
 * 为了它让整个聊天不可用，代价比偶尔超支更大。日志会留下告警供排查。
 */
@Service
public class AgentQuotaService {

    private static final Logger log = LoggerFactory.getLogger(AgentQuotaService.class);

    private static final String KEY_PREFIX = "agent:quota:";

    private final StringRedisTemplate redisTemplate;
    private final AgentProperties properties;

    public AgentQuotaService(StringRedisTemplate redisTemplate, AgentProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 消耗一次额度；超出当日上限时抛 429。
     *
     * @param userId 当前登录用户（来自 JWT，不接受前端传值）
     */
    public void checkAndConsume(Long userId) {
        String key = KEY_PREFIX + LocalDate.now() + ":" + userId;
        int limit = properties.getDailyLlmQuota();
        try {
            Long used = redisTemplate.opsForValue().increment(key);
            if (used != null && used == 1L) {
                // 首次自增时设置过期：即使服务挂了也不会留下永不过期的计数
                redisTemplate.expire(key, Duration.ofDays(2));
            }
            if (used != null && used > limit) {
                throw new RateLimitException("今日 AI 调用次数已用完（上限 " + limit + " 次/天），请明天再试");
            }
        } catch (RateLimitException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("配额检查失败（Redis 不可用），本轮放行: {}", e.getMessage());
        }
    }

    /** 查询今日已用次数，供调试与前端展示。Redis 不可用时返回 -1。 */
    public long usedToday(Long userId) {
        try {
            String value = redisTemplate.opsForValue().get(KEY_PREFIX + LocalDate.now() + ":" + userId);
            return value == null ? 0 : Long.parseLong(value);
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
