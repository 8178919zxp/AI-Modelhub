import type { ReactNode } from "react";
import { useAgentChat } from "../hooks/agent/useAgentChat";
import { AgentActionsContext, AgentStateContext } from "./agentStore";

/**
 * Agent 的全局状态容器：挂在 Layout 内、路由之上。
 * 路由切换不会卸载它，所以"悬浮窗 ↔ 全屏"共享同一份对话与同一条 SSE。
 */
export function AgentProvider({ children }: { children: ReactNode }) {
  const { state, actions } = useAgentChat();
  return (
    <AgentActionsContext.Provider value={actions}>
      <AgentStateContext.Provider value={state}>{children}</AgentStateContext.Provider>
    </AgentActionsContext.Provider>
  );
}
