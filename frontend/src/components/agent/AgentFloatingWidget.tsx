import { useCallback, useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Maximize2, Sparkles, X } from "lucide-react";
import { useAgentActions, useAgentState } from "../../agent/agentStore";
import type { AgentWindowGeometry } from "../../agent/types";
import { useMediaQuery } from "../../hooks/useMediaQuery";
import { useStore } from "../../store/useStore";
import AgentChatWindow from "./AgentChatWindow";
import AgentFloatingButton from "./AgentFloatingButton";

const STORAGE_KEY = "aimh:agent:window";
const MIN_W = 340;
const MIN_H = 420;
const MAX_W = 720;
const DEFAULT_W = 420;
const DEFAULT_H = 620;

/** 只存位置和尺寸；聊天内容、token 一律不进 localStorage */
const clamp = (g: AgentWindowGeometry, vw: number, vh: number): AgentWindowGeometry => {
  const maxW = Math.max(MIN_W, Math.min(MAX_W, vw - 16));
  const maxH = Math.max(MIN_H, Math.min(Math.round(vh * 0.85), vh - 16));
  const w = Math.min(Math.max(g.w, MIN_W), maxW);
  const h = Math.min(Math.max(g.h, MIN_H), maxH);
  const x = Math.min(Math.max(g.x, 8), Math.max(8, vw - w - 8));
  const y = Math.min(Math.max(g.y, 8), Math.max(8, vh - h - 8));
  return { x, y, w, h };
};

const defaultGeometry = (): AgentWindowGeometry => {
  const vw = typeof window === "undefined" ? 1440 : window.innerWidth;
  const vh = typeof window === "undefined" ? 900 : window.innerHeight;
  return clamp(
    { x: vw - DEFAULT_W - 24, y: vh - DEFAULT_H - 24, w: DEFAULT_W, h: DEFAULT_H },
    vw,
    vh,
  );
};

function loadGeometry(): AgentWindowGeometry {
  const fallback = defaultGeometry();
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return fallback;
    const parsed = JSON.parse(raw) as Partial<AgentWindowGeometry>;
    if (
      typeof parsed.x !== "number" ||
      typeof parsed.y !== "number" ||
      typeof parsed.w !== "number" ||
      typeof parsed.h !== "number"
    ) {
      return fallback;
    }
    return clamp(parsed as AgentWindowGeometry, window.innerWidth, window.innerHeight);
  } catch {
    return fallback;
  }
}

const PAGE_LABEL: Record<string, string> = {
  "/": "首页",
  "/about": "关于我们",
  "/models": "模型中心",
  "/predict": "在线体验",
  "/profile": "个人中心",
  "/dashboard": "数据统计",
  "/admin": "管理后台",
};

function pageContext(pathname: string): string | undefined {
  if (pathname.startsWith("/models/")) {
    const id = pathname.split("/")[2];
    return id ? `模型详情 #${id}` : "模型详情";
  }
  return PAGE_LABEL[pathname];
}

/**
 * 全站悬浮助手：挂在 Layout 内（不在各页面里），因此全站只有一个实例，
 * 路由切换不会重建，正在进行的对话与 SSE 也不会中断。
 */
export default function AgentFloatingWidget() {
  const { open, minimized, hasUnread, sending, sessionsLoaded } = useAgentState();
  const { openWindow, closeWindow, minimizeWindow, restoreWindow } = useAgentActions();
  const { token } = useStore();
  const location = useLocation();
  const navigate = useNavigate();
  const isMobile = useMediaQuery("(max-width: 767px)");

  const [geometry, setGeometry] = useState<AgentWindowGeometry>(loadGeometry);
  const geoRef = useRef(geometry);
  /** 拖动期间挂的 window 监听：组件被卸载（退出登录等）时必须解绑 */
  const dragCleanupRef = useRef<(() => void) | null>(null);
  useEffect(() => {
    geoRef.current = geometry;
  }, [geometry]);

  // 视口变化时收敛位置，避免窗口跑到屏幕外
  useEffect(() => {
    const onResize = () => setGeometry((g) => clamp(g, window.innerWidth, window.innerHeight));
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(geometry));
    } catch {
      /* 隐私模式下写入失败，忽略 */
    }
  }, [geometry]);

  // ESC 只收起窗口，不中断 SSE、也不取消任务
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") closeWindow();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, closeWindow]);

  const startDrag = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    if (e.button !== 0) return;
    const startX = e.clientX;
    const startY = e.clientY;
    const origin = geoRef.current;
    document.body.style.userSelect = "none";

    const move = (ev: PointerEvent) => {
      const next = clamp(
        { ...origin, x: origin.x + (ev.clientX - startX), y: origin.y + (ev.clientY - startY) },
        window.innerWidth,
        window.innerHeight,
      );
      setGeometry(next);
    };
    const cleanup = () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
      document.body.style.userSelect = "";
      dragCleanupRef.current = null;
    };
    const up = () => cleanup();
    dragCleanupRef.current = cleanup;
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
  }, []);

  useEffect(() => {
    return () => dragCleanupRef.current?.();
  }, []);

  const onResize = useCallback((dw: number, dh: number) => {
    setGeometry((g) =>
      clamp({ ...g, w: g.w + dw, h: g.h + dh }, window.innerWidth, window.innerHeight),
    );
  }, []);

  // 独立的 /agent 页面已占满屏，这里不再画悬浮层
  if (location.pathname.startsWith("/agent")) return null;

  const handleOpen = () => {
    if (!token) {
      void navigate(`/login?redirect=${encodeURIComponent(location.pathname)}`);
      return;
    }
    openWindow();
  };

  if (!open) {
    return <AgentFloatingButton onClick={handleOpen} unread={hasUnread} busy={sending} />;
  }

  if (minimized && !isMobile) {
    return (
      <div className="fixed bottom-6 right-6 z-[60] flex items-center gap-2 rounded-2xl border border-gray-200 bg-white px-3 py-2 shadow-lg">
        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-gradient-to-br from-blue-500 to-indigo-500 text-white">
          <Sparkles className="h-3.5 w-3.5" />
        </span>
        <button
          type="button"
          onClick={restoreWindow}
          className="text-xs font-medium text-gray-700 hover:text-blue-600"
        >
          ModelHub Copilot
        </button>
        <button
          type="button"
          onClick={restoreWindow}
          className="rounded-lg p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
          title="展开"
        >
          <Maximize2 className="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          onClick={closeWindow}
          className="rounded-lg p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
          title="关闭"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      </div>
    );
  }

  if (!sessionsLoaded && !isMobile) {
    // Provider 首次拉会话期间先别弹窗，避免闪一下空列表
    return <AgentFloatingButton onClick={openWindow} busy />;
  }

  return (
    <AgentChatWindow
      geometry={geometry}
      mobile={isMobile}
      contextLabel={pageContext(location.pathname)}
      onDragStart={startDrag}
      onResize={onResize}
      onMaximize={() => navigate("/agent")}
      onMinimize={minimizeWindow}
      onClose={closeWindow}
    />
  );
}
