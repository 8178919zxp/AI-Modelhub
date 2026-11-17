import { MessageSquarePlus, Trash2 } from "lucide-react";
import { useAgentActions, useAgentState } from "../../agent/agentStore";

interface Props {
  /** 悬浮窗里的紧凑版：更小的行高、无外框 */
  compact?: boolean;
  className?: string;
  /** 悬浮窗抽屉里选中/新建后收起面板 */
  onClose?: () => void;
}

export default function AgentSessionSidebar({ compact = false, className = "", onClose }: Props) {
  const { sessions, activeId, sending } = useAgentState();
  const { newChat, selectSession, deleteSession } = useAgentActions();

  return (
    <aside
      className={`flex flex-col overflow-hidden ${
        compact
          ? "rounded-xl border border-gray-200 bg-white"
          : "rounded-2xl border border-gray-200 bg-white"
      } ${className}`}
    >
      <div className={compact ? "p-2" : "p-3"}>
        <button
          type="button"
          onClick={() => {
            void newChat();
            onClose?.();
          }}
          className={`flex w-full items-center justify-center gap-2 rounded-xl bg-blue-600 font-medium text-white transition hover:bg-blue-700 ${
            compact ? "px-2 py-2 text-xs" : "px-3 py-2.5 text-sm"
          }`}
        >
          <MessageSquarePlus className={compact ? "h-3.5 w-3.5" : "h-4 w-4"} />
          新建对话
        </button>
      </div>

      <div className={`flex-1 space-y-1 overflow-y-auto ${compact ? "max-h-56 p-1.5" : "p-2"}`}>
        {sessions.length === 0 && (
          <p className="px-2 py-6 text-center text-xs text-gray-400">暂无历史会话</p>
        )}
        {sessions.map((s) => {
          const active = s.id === activeId;
          return (
            <div
              key={s.id}
              onClick={() => {
                selectSession(s.id);
                onClose?.();
              }}
              title={sending ? "回复生成中，暂不能切换会话" : s.title}
              className={`group flex cursor-pointer items-start gap-2 rounded-lg px-2.5 transition ${
                compact ? "py-1.5" : "py-2"
              } ${active ? "bg-blue-50 text-blue-700" : "text-gray-600 hover:bg-gray-50"} ${
                sending ? "cursor-not-allowed opacity-60" : ""
              }`}
            >
              <div className="min-w-0 flex-1">
                <p className={`truncate ${compact ? "text-xs" : "text-sm"}`}>{s.title}</p>
                <p className="mt-0.5 text-[10px] text-gray-400">{s.messageCount} 条消息</p>
              </div>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  void deleteSession(s.id);
                }}
                className="text-gray-300 opacity-0 transition hover:text-red-500 group-hover:opacity-100"
                title="删除会话"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </div>
          );
        })}
      </div>
    </aside>
  );
}
