package com.aimodelhub.agent.vo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSE 事件单元。
 * <p>
 * 流式接口不能只推「文本增量」：前端还要知道会话 id、消息 id、什么时候结束、
 * 出错原因是什么。所以后端统一推事件流，每种事件一个 {@code type}：
 *
 * <pre>
 *   session  ── 本轮所属会话（{sessionId, title}），新建会话时下发
 *   token    ── 文本增量（{text}）
 *   done     ── 本轮结束（{messageId, sessionId, tokenCount, elapsedMs}）
 *   error    ── 出错（{message}），前端据此展示提示而不是白屏
 * </pre>
 *
 * 后续阶段会再加 {@code tool_call} / {@code recommend} / {@code confirm_required} 等类型，
 * 前端按 type 分发即可，不必改动传输层。
 */
public class AgentEvent {

    private final String type;
    private final Map<String, Object> data;

    private AgentEvent(String type, Map<String, Object> data) {
        this.type = type;
        this.data = data;
    }

    public static AgentEvent of(String type, Map<String, Object> data) {
        return new AgentEvent(type, data == null ? Map.of() : data);
    }

    public static AgentEvent session(Long sessionId, String title) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("title", title);
        return new AgentEvent("session", data);
    }

    public static AgentEvent token(String text) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", text);
        return new AgentEvent("token", data);
    }

    public static AgentEvent done(Long sessionId, Long messageId, int tokenCount, long elapsedMs) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("messageId", messageId);
        data.put("tokenCount", tokenCount);
        data.put("elapsedMs", elapsedMs);
        return new AgentEvent("done", data);
    }

    public static AgentEvent error(String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", message);
        return new AgentEvent("error", data);
    }

    /** 工具开始执行（前端可展示「正在查询平台模型…」）。 */
    public static AgentEvent toolCall(String toolName, String summary) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", toolName);
        data.put("summary", summary);
        return new AgentEvent("tool_call", data);
    }

    /** 工具执行完成，附带是否成功与耗时。 */
    public static AgentEvent toolResult(String toolName, boolean success, long elapsedMs, String summary) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", toolName);
        data.put("success", success);
        data.put("elapsedMs", elapsedMs);
        data.put("summary", summary);
        return new AgentEvent("tool_result", data);
    }

    /** 模型推荐卡片数据（前端据此渲染卡片，而非解析 Markdown 文本）。 */
    public static AgentEvent recommend(Object models) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("models", models);
        return new AgentEvent("recommend", data);
    }

    /**
     * 待用户确认的推理任务。
     * <p>
     * 前端收到后渲染「确认执行」卡片；用户点击 → {@code POST /api/agent/tasks/{id}/confirm}。
     * 这是「Agent 不能自己执行推理」这道边界在前端的落点。
     */
    public static AgentEvent confirmRequired(Long taskId, String taskNo, String modelName,
                                             String inputType, String inputSummary) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", taskId);
        data.put("taskNo", taskNo);
        data.put("modelName", modelName);
        data.put("inputType", inputType);
        data.put("inputSummary", inputSummary);
        return new AgentEvent("confirm_required", data);
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
