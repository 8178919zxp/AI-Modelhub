import { useRef, useState } from "react";

interface Props {
  /** 传入本次指针移动产生的增量（像素），由父组件负责边界收敛 */
  onResize: (deltaW: number, deltaH: number) => void;
}

/**
 * 右下角缩放手柄。
 *
 * 用 Pointer Events + setPointerCapture，而不是给 window 挂 mousemove：
 * 指针移出手柄甚至移出窗口时仍能继续收到事件，且松手自动释放，不需要额外清理。
 * 增量式回传（而不是绝对坐标）让父组件把「最小/最大尺寸 + 视口边界」集中在一处处理。
 */
export default function AgentResizeHandle({ onResize }: Props) {
  const last = useRef<{ x: number; y: number } | null>(null);
  const [active, setActive] = useState(false);

  return (
    <div
      role="separator"
      aria-label="调整窗口大小"
      onPointerDown={(e) => {
        e.preventDefault();
        e.currentTarget.setPointerCapture(e.pointerId);
        last.current = { x: e.clientX, y: e.clientY };
        setActive(true);
      }}
      onPointerMove={(e) => {
        if (!last.current) return;
        const dx = e.clientX - last.current.x;
        const dy = e.clientY - last.current.y;
        if (dx === 0 && dy === 0) return;
        last.current = { x: e.clientX, y: e.clientY };
        onResize(dx, dy);
      }}
      onPointerUp={(e) => {
        e.currentTarget.releasePointerCapture(e.pointerId);
        last.current = null;
        setActive(false);
      }}
      onPointerCancel={() => {
        last.current = null;
        setActive(false);
      }}
      className={`absolute bottom-0 right-0 z-10 flex h-5 w-5 cursor-nwse-resize items-end justify-end p-1 ${
        active ? "text-blue-500" : "text-gray-300 hover:text-gray-500"
      }`}
      title="拖动调整大小"
    >
      <svg
        viewBox="0 0 8 8"
        className="h-2.5 w-2.5"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.4"
      >
        <path d="M7 1 1 7M7 4.5 4.5 7" strokeLinecap="round" />
      </svg>
    </div>
  );
}
