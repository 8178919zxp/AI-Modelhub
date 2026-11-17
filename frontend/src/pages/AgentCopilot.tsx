import AgentFullscreenLayout from "../components/agent/AgentFullscreenLayout";

/**
 * /agent —— 完整 Agent 页面（保留原有路由）。
 *
 * 这里不再持有任何状态：消息、SSE、会话列表都在 AgentProvider 里，
 * 与右下角悬浮窗共用同一份数据。因此「悬浮窗 → 全屏 → 返回」不会丢对话。
 */
export default function AgentCopilot() {
  return <AgentFullscreenLayout />;
}
