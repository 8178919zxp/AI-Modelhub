import { Sparkles } from "lucide-react";

interface Props {
  onClick: () => void;
  unread?: boolean;
  /** 回复生成中：显示一个转圈的细环，告诉用户"关掉窗口不等于停了" */
  busy?: boolean;
  label?: string;
}

/**
 * 右下角悬浮球。
 * 呼吸动效只做在光晕层（animate-ping 的慢速版），按钮本身不缩放，
 * 避免长时间停留时产生廉价感；hover 才有位移与阴影反馈。
 */
export default function AgentFloatingButton({
  onClick,
  unread = false,
  busy = false,
  label = "AI 助手",
}: Props) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className="group fixed bottom-6 right-6 z-[60] flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-br from-blue-600 to-indigo-600 text-white shadow-lg shadow-blue-600/25 transition duration-200 hover:-translate-y-0.5 hover:shadow-xl hover:shadow-blue-600/30 focus:outline-none focus-visible:ring-4 focus-visible:ring-blue-200 active:translate-y-0"
    >
      <span className="pointer-events-none absolute inset-0 rounded-full bg-blue-500/30 animate-[agent-halo_3.2s_ease-out_infinite]" />
      <Sparkles className="relative h-6 w-6" />
      {busy && (
        <span className="absolute inset-0 rounded-full border-2 border-white/50 border-t-transparent animate-spin" />
      )}
      {unread && !busy && (
        <span className="absolute -right-0.5 -top-0.5 h-3 w-3 rounded-full border-2 border-white bg-rose-500" />
      )}
    </button>
  );
}
