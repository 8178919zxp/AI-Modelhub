package com.aimodelhub.agent.mapper;

import com.aimodelhub.agent.entity.AgentMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@link AgentMessage} 的 MyBatis Mapper。
 * <p>
 * 与 {@link AgentSessionMapper} 一样，每个查询都带 {@code user_id} 条件：
 * 消息表虽然挂在 session 下，但多一层用户校验可以让「猜 sessionId」这条攻击路径失效。
 */
@Mapper
public interface AgentMessageMapper {

    int insert(AgentMessage message);

    /** 某会话的全部消息，按时间正序（前端渲染顺序）。 */
    List<AgentMessage> findBySession(@Param("sessionId") Long sessionId,
                                     @Param("userId") Long userId);

    /** 某会话最近 N 条消息，按时间倒序（用于回填 Redis 上下文窗口）。 */
    List<AgentMessage> findRecentDesc(@Param("sessionId") Long sessionId,
                                      @Param("userId") Long userId,
                                      @Param("limit") int limit);

    /** 只取最近 N 轮的用户+助手文本，用于 Redis 丢失后重建上下文。 */
    List<AgentMessage> findRecentDialogueDesc(@Param("sessionId") Long sessionId,
                                              @Param("userId") Long userId,
                                              @Param("limit") int limit);

    int countBySession(@Param("sessionId") Long sessionId);

    /** 会话删除时同步隐藏消息，保留数据。 */
    int softDeleteBySession(@Param("sessionId") Long sessionId, @Param("userId") Long userId);
}
