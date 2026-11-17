import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAgentState } from "../../agent/agentStore";
import AgentChatContent, { AgentErrorBar } from "./AgentChatContent";
import AgentChatHeader from "./AgentChatHeader";
import AgentChatInput from "./AgentChatInput";
import AgentSessionSidebar from "./AgentSessionSidebar";

const PAGE_LABEL: Record<string, string> = {
  "/": "首页",
  "/about": "关于我们",
  "/models": "模型中心",
  "/predict": "在线体验",
  "/profile": "个人中心",
  "/dashboard": "数据统计",
  "/admin": "管理后台",
};

/** 全屏形态：左历史 + 右对话。与悬浮窗共用 AgentChatContent / AgentChatInput / AgentChatHeader。 */
export default function AgentFullscreenLayout() {
  const { sessions } = useAgentState();
  const navigate = useNavigate();
  const location = useLocation();
  const [historyOpen, setHistoryOpen] = useState(false);

  const from = (location.state as { from?: string } | null)?.from;
  const contextLabel = from ? (PAGE_LABEL[from] ?? "站内页面") : undefined;

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6">
      <div className="flex h-[calc(100vh-11rem)] min-h-[520px] gap-5">
        <AgentSessionSidebar className="hidden w-64 shrink-0 md:flex" />

        <section className="relative flex min-w-0 flex-1 flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white">
          <AgentChatHeader
            contextLabel={contextLabel}
            onToggleHistory={() => setHistoryOpen((v) => !v)}
            historyOpen={historyOpen}
            onClose={() => navigate("/")}
          />

          {historyOpen && (
            <div className="absolute left-2 right-2 top-12 z-20 shadow-xl md:hidden">
              <AgentSessionSidebar compact onClose={() => setHistoryOpen(false)} />
            </div>
          )}

          <AgentErrorBar />
          <AgentChatContent />
          <AgentChatInput autoFocus />
        </section>
      </div>

      <p className="mt-3 text-center text-[11px] text-gray-400">
        共 {sessions.length} 个历史会话 · 智能助手与页面右下角共用同一段对话
      </p>
    </div>
  );
}
