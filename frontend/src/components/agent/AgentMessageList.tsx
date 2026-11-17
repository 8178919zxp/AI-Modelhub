import { useCallback, useEffect, useRef, useState } from "react";
import { ArrowDown, Loader2 } from "lucide-react";
import type { AgentItem } from "../../agent/types";
import AgentMessageItem from "./AgentMessageItem";

interface Props {
  items: AgentItem[];
  compact?: boolean;
  loading?: boolean;
  onConfirm: (itemKey: string, taskId: number) => void;
  /** 空状态内容由调用方决定（悬浮窗与全屏的引导语不同） */
  empty?: React.ReactNode;
  header?: React.ReactNode;
}

/**
 * 消息列表 + 滚动策略。
 *
 * 规则（按优先级）：
 * 1. 换会话 → 直接贴底，并从"贴底"状态重新开始；
 * 2. 自己刚发了消息 → 强制贴底（用户可能刚在翻历史，但发送意味着要看回复）；
 * 3. 贴底时才跟随流式内容；用户一旦上翻就不再抢滚动，改显示「回到最新」。
 *
 * 第 1、2 条是为了修一个真实问题：如果只在"贴底"时跟随，打开历史会话时
 * 初始 scrollTop=0 会被判定成"未贴底"，于是整轮回复都在上方增长，
 * 用户看到的一直停在开头，还必须手动点「回到最新」。
 */
export default function AgentMessageList({
  items,
  compact = false,
  loading,
  onConfirm,
  empty,
  header,
}: Props) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const atBottomRef = useRef(true);
  const firstKeyRef = useRef<string | null>(null);
  const [showJump, setShowJump] = useState(false);

  const lastItem = items[items.length - 1];
  const streaming = lastItem?.kind === "text" && Boolean(lastItem.streaming);

  const handleScroll = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 24;
    atBottomRef.current = atBottom;
    if (atBottom) setShowJump(false);
  }, []);

  const stickToBottom = useCallback((smooth: boolean) => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? "smooth" : "auto" });
    atBottomRef.current = true;
    setShowJump(false);
  }, []);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;

    const firstKey = items[0]?.key ?? null;
    const sessionChanged = firstKeyRef.current !== null && firstKey !== firstKeyRef.current;
    if (firstKeyRef.current === null) firstKeyRef.current = firstKey;

    const justSentByUser = lastItem?.kind === "text" && lastItem.role === "USER";

    if (sessionChanged || justSentByUser) {
      stickToBottom(!streaming);
      return;
    }

    if (atBottomRef.current) {
      // 流式输出用 auto：smooth 每帧排队会"追不上"内容增长速度
      el.scrollTo({ top: el.scrollHeight, behavior: streaming ? "auto" : "smooth" });
    } else {
      setShowJump(true);
    }
  }, [items, streaming, lastItem, stickToBottom]);

  return (
    <div className="relative min-h-0 flex-1">
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className={`h-full overflow-y-auto overflow-x-hidden ${
          compact ? "space-y-3.5 px-3 py-3" : "space-y-5 px-4 py-5"
        }`}
      >
        {header}
        {items.length === 0 && !loading && empty}
        {loading && (
          <div className="flex items-center justify-center py-10 text-sm text-gray-400">
            <Loader2 className="mr-2 h-4 w-4 animate-spin" /> 正在加载历史消息…
          </div>
        )}
        {items.map((it) => (
          <AgentMessageItem key={it.key} item={it} compact={compact} onConfirm={onConfirm} />
        ))}
        <div className="h-1" />
      </div>

      {showJump && (
        <button
          type="button"
          onClick={() => stickToBottom(true)}
          className="absolute bottom-3 left-1/2 inline-flex -translate-x-1/2 items-center gap-1 rounded-full border border-gray-200 bg-white/95 px-3 py-1.5 text-[11px] text-gray-600 shadow-md backdrop-blur transition hover:text-blue-600"
        >
          <ArrowDown className="h-3 w-3" /> 回到最新
        </button>
      )}
    </div>
  );
}
