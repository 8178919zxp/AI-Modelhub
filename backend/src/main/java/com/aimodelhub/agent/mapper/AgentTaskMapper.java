package com.aimodelhub.agent.mapper;

import com.aimodelhub.agent.entity.AgentTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@link AgentTask} 的 Mapper。
 *
 * <h3>状态流转方法为什么返回 int</h3>
 * 所有状态迁移都是「带条件的 UPDATE」：
 * <pre>
 *   UPDATE agent_task SET task_status='CONFIRMED'
 *    WHERE id=? AND user_id=? AND task_status='PENDING'
 * </pre>
 * 返回受影响行数：1 = 抢到了这次迁移，0 = 状态已被别人改过（重复确认/并发执行）。
 * 调用方必须检查这个返回值，否则「重复点击确认」会导致同一任务推理两次。
 */
@Mapper
public interface AgentTaskMapper {

    int insert(AgentTask task);

    AgentTask findById(@Param("id") Long id);

    AgentTask findByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    List<AgentTask> findByUser(@Param("userId") Long userId, @Param("limit") int limit);

    /** PENDING → CONFIRMED，仅当仍是 PENDING 时成功。 */
    int markConfirmed(@Param("id") Long id, @Param("userId") Long userId);

    /** CONFIRMED → RUNNING，返回 0 表示已被执行或状态不符。 */
    int markRunning(@Param("id") Long id, @Param("userId") Long userId);

    /** 执行成功：RUNNING → SUCCESS，写入结果。 */
    int markSuccess(@Param("id") Long id,
                    @Param("resultSummary") String resultSummary,
                    @Param("resultPayload") String resultPayload,
                    @Param("recordId") Long recordId,
                    @Param("durationMs") Integer durationMs);

    /** 执行失败：RUNNING → FAILED。 */
    int markFailed(@Param("id") Long id,
                   @Param("errorMessage") String errorMessage,
                   @Param("durationMs") Integer durationMs);

    /** 过期：PENDING → EXPIRED。 */
    int markExpired(@Param("id") Long id);
}
