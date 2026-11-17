import { createContext, useContext } from "react";
import type { AgentActions, AgentState } from "./types";

/**
 * Context 与读取 Hook 单独放在非组件文件里，Provider 放在 AgentContext.tsx：
 * 否则同一个文件既导出组件又导出 Hook，会破坏 React Fast Refresh。
 *
 * 拆两个 Context 是为了减少无意义重渲染：
 * 动作对象稳定（useMemo），只读动作的组件不会因为"来了一帧 token"而重绘。
 */
export const AgentStateContext = createContext<AgentState | null>(null);
export const AgentActionsContext = createContext<AgentActions | null>(null);

export function useAgentState(): AgentState {
  const ctx = useContext(AgentStateContext);
  if (!ctx) throw new Error("useAgentState 必须在 AgentProvider 内使用");
  return ctx;
}

export function useAgentActions(): AgentActions {
  const ctx = useContext(AgentActionsContext);
  if (!ctx) throw new Error("useAgentActions 必须在 AgentProvider 内使用");
  return ctx;
}
