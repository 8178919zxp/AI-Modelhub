import { useState } from "react";
import { Loader2, Sparkles } from "lucide-react";
import type { AgentWindowGeometry } from "../../agent/types";
import { useAgentState } from "../../agent/agentStore";
import AgentChatContent, { AgentErrorBar } from "./AgentChatContent";
import AgentChatHeader from "./AgentChatHeader";
import AgentChatInput from "./AgentChatInput";
import AgentResizeHandle from "./AgentResizeHandle";
import AgentSessionSidebar from "./AgentSessionSidebar";

interface Props {
  geometry: AgentWindowGeometry;
  mobile: boolean;
  contextLabel?: string;
  onDragStart: (e: React.PointerEvent<HTMLDivElement>) => void;
  onResize: (deltaW: number, deltaH: number) => void;
  onMaximize: () => void;
  onMinimize: () => void;
  onClose: () => void;
}

/**
 * 悬浮窗外壳：只负责"窗口"这一层（位置、尺寸、拖动/缩放手柄、历史抽屉），
 * 聊天内容全部来自 AgentChatContent，与全屏页共用。
 */
export default function AgentChatWindow({
  geometry,
  mobile,
  contextLabel,
  onDragStart,
  onResize,
  onMaximize,
  onMinimize,
  onClose,
}: Props) {
  const { sessionsLoaded } = useAgentState();
  const [historyOpen, setHistoryOpen] = useState(false);

  // 移动端：整屏抽屉，禁用拖动与缩放（软键盘弹出时也不会把窗口顶出视口）
  const style = mobile
    ? { left: 0, top: 0, right: 0, bottom: 0, width: "auto", height: "auto" }
    : { left: geometry.x, top: geometry.y, width: geometry.w, height: geometry.h };

  return (
    <div
      role="dialog"
      aria-label="ModelHub Copilot 助手"
      style={style}
      className={`fixed z-[60] flex flex-col overflow-hidden border border-gray-200 bg-white shadow-2xl ${
        mobile ? "" : "rounded-2xl"
      } animate-[agent-pop_.18s_ease-out]`}
    >
      <AgentChatHeader
        compact
        contextLabel={contextLabel}
        onDragStart={mobile ? undefined : onDragStart}
        onToggleHistory={() => setHistoryOpen((v) => !v)}
        historyOpen={historyOpen}
        onMaximize={onMaximize}
        onMinimize={onMinimize}
        onClose={onClose}
      />

      {historyOpen && (
        <div className="absolute left-2 right-2 top-12 z-20 shadow-xl">
          <AgentSessionSidebar compact onClose={() => setHistoryOpen(false)} />
        </div>
      )}

      <AgentErrorBar />

      {sessionsLoaded ? (
        <AgentChatContent compact />
      ) : (
        <div className="flex flex-1 items-center justify-center text-xs text-gray-400">
          <Loader2 className="mr-2 h-3.5 w-3.5 animate-spin" /> 正在准备助手…
        </div>
      )}

      <AgentChatInput compact autoFocus />

      {!mobile && <AgentResizeHandle onResize={onResize} />}

      {mobile && (
        <span className="pointer-events-none absolute bottom-1 left-1/2 -translate-x-1/2 text-[9px] text-gray-300">
          <Sparkles className="mr-0.5 inline h-2.5 w-2.5" />
          AI 助手
        </span>
      )}
    </div>
  );
}
