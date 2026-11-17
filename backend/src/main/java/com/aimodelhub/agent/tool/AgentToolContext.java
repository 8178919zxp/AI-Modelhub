package com.aimodelhub.agent.tool;

import com.aimodelhub.agent.vo.ModelRecommendVO;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 一次对话请求内的工具执行上下文。
 *
 * <h3>为什么不是 Spring 单例 Bean</h3>
 * 工具方法里需要一个「当前用户」来做权限校验，而这个身份<b>绝不能</b>由大模型传入参数
 * （LLM 完全可以把 userId 改成别人）。所以每次请求 new 一个上下文，把
 * {@code userId} 固定在对象字段里；工具方法只能读它，改不了。
 * <p>
 * 同时它还是「副作用收集器」：工具执行了哪些调用、推荐了哪些模型，
 * 编排层在回答结束后读出来推给前端（{@code tool_call} / {@code recommend} 事件）。
 * 这样既不需要工具直接操作 SSE，也让「模型说了什么」和「实际做了什么」可对照。
 *
 * <p>线程安全：Spring AI 内部可能在别的线程执行工具，因此这里用
 * {@link CopyOnWriteArrayList}，避免并发写。
 */
public class AgentToolContext {

    private final Long userId;
    private final Long sessionId;
    private final List<ToolInvocation> invocations = new CopyOnWriteArrayList<>();
    private final List<ModelRecommendVO> recommendations = new CopyOnWriteArrayList<>();

    /** 本轮通过 prepareInference 创建的任务 id（用于推送「请确认」卡片）。 */
    private volatile Long pendingTaskId;

    public AgentToolContext(Long userId, Long sessionId) {
        this.userId = userId;
        this.sessionId = sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void recordInvocation(String toolName, String arguments, String resultSummary, boolean success, long elapsedMs) {
        invocations.add(new ToolInvocation(toolName, arguments, resultSummary, success, elapsedMs));
    }

    public List<ToolInvocation> getInvocations() {
        return Collections.unmodifiableList(invocations);
    }

    /** 记录最近一次推荐结果，供前端渲染卡片。同一轮多次推荐以最后一次为准。 */
    public void replaceRecommendations(List<ModelRecommendVO> items) {
        recommendations.clear();
        if (items != null) {
            recommendations.addAll(items);
        }
    }

    public List<ModelRecommendVO> getRecommendations() {
        return Collections.unmodifiableList(recommendations);
    }

    public Long getPendingTaskId() {
        return pendingTaskId;
    }

    public void setPendingTaskId(Long pendingTaskId) {
        this.pendingTaskId = pendingTaskId;
    }

    /** 一次工具调用的快照，用于日志与前端「正在查询…」提示。 */
    public record ToolInvocation(String toolName,
                                 String arguments,
                                 String resultSummary,
                                 boolean success,
                                 long elapsedMs) {
    }
}
