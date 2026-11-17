import { History, Loader2, Maximize2, MessageSquarePlus, Minus, Sparkles, X } from "lucide-react";
import { useAgentActions, useAgentState } from "../../agent/agentStore";

interface Props {
  compact?: boolean;
  /** 页面上下文，例如「模型详情 · ResNet-50」 */
  contextLabel?: string;
  onDragStart?: (e: React.PointerEvent<HTMLDivElement>) => void;
  onToggleHistory?: () => void;
  historyOpen?: boolean;
  onMaximize?: () => void;
  onMinimize?: () => void;
  onClose: () => void;
}

export default function AgentChatHeader({
  compact = false,
  contextLabel,
  onDragStart,
  onToggleHistory,
  historyOpen = false,
  onMaximize,
  onMinimize,
  onClose,
}: Props) {
  const { status, sending } = useAgentState();
  const { newChat } = useAgentActions();

  const statusText =
    status === "waiting" ? "正在分析…" : status === "streaming" ? "正在输出…" : "随时待命";

  return (
    <div
      onPointerDown={(e) => {
        // 按钮/链接上按下不触发拖动
        if ((e.target as HTMLElement).closest("button,a")) return;
        onDragStart?.(e);
      }}
      className={`flex shrink-0 items-center gap-2 border-b border-gray-100 bg-white px-3 py-2.5 ${
        onDragStart ? "cursor-move select-none" : ""
      }`}
    >
      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-blue-500 to-indigo-500 text-white">
        <Sparkles className="h-3.5 w-3.5" />
      </span>

      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          <span className="truncate text-[13px] font-semibold text-gray-800">ModelHub Copilot</span>
          <span className="flex items-center gap-1 rounded-full bg-gray-50 px-1.5 py-0.5 text-[10px] text-gray-500">
            {sending && <Loader2 className="h-2.5 w-2.5 animate-spin" />}
            {statusText}
          </span>
        </div>
        <div className="truncate text-[10px] text-gray-400">
          {contextLabel ? `当前页面：${contextLabel}` : "智能助手 · 模型查询 / 推荐 / 推理"}
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-0.5">
        <IconBtn title="新建对话" onClick={() => void newChat()}>
          <MessageSquarePlus className="h-3.5 w-3.5" />
        </IconBtn>
        {onToggleHistory && (
          <IconBtn title="历史会话" active={historyOpen} onClick={onToggleHistory}>
            <History className="h-3.5 w-3.5" />
          </IconBtn>
        )}
        {onMaximize && (
          <IconBtn title={compact ? "全屏" : "全屏打开"} onClick={onMaximize}>
            <Maximize2 className="h-3.5 w-3.5" />
          </IconBtn>
        )}
        {onMinimize && (
          <IconBtn title="最小化" onClick={onMinimize}>
            <Minus className="h-3.5 w-3.5" />
          </IconBtn>
        )}
        <IconBtn title="关闭" onClick={onClose}>
          <X className="h-3.5 w-3.5" />
        </IconBtn>
      </div>
    </div>
  );
}

function IconBtn({
  children,
  title,
  onClick,
  active,
}: {
  children: React.ReactNode;
  title: string;
  onClick: () => void;
  active?: boolean;
}) {
  return (
    <button
      type="button"
      title={title}
      aria-label={title}
      onClick={onClick}
      className={`flex h-7 w-7 items-center justify-center rounded-lg transition ${
        active ? "bg-blue-50 text-blue-600" : "text-gray-400 hover:bg-gray-100 hover:text-gray-600"
      }`}
    >
      {children}
    </button>
  );
}
