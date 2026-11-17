package com.aimodelhub.agent.mapper;

import com.aimodelhub.agent.entity.AgentSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link AgentSession} 的 MyBatis Mapper。
 * <p>
 * <b>注意方法签名里的 {@code userId}：</b>所有查询都带 {@code AND user_id = #{userId}}，
 * 用户隔离在这里强制生效，而不是靠上层「记得先判断一下」。
 * 这样即使 Service 层漏了校验，SQL 也查不出别人的数据。
 */
@Mapper
public interface AgentSessionMapper {

    int insert(AgentSession session);

    /** 按「会话 id + 所属用户」查询；查不到返回 null（他人会话与不存在同样处理，避免探测）。 */
    AgentSession findByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    /** 当前用户的会话列表，按最后消息时间倒序。 */
    List<AgentSession> findByUser(@Param("userId") Long userId, @Param("limit") int limit);

    /** 会话结束后刷新统计字段（消息数、最后消息时间、标题）。 */
    int updateStats(@Param("id") Long id,
                    @Param("userId") Long userId,
                    @Param("title") String title,
                    @Param("messageCount") int messageCount,
                    @Param("lastMessageAt") LocalDateTime lastMessageAt);

    /** 软删除：status 置 0，数据仍保留，便于审计。 */
    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    int countByUser(@Param("userId") Long userId);
}
