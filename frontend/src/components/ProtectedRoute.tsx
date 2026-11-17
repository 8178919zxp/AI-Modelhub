import { Navigate } from "react-router-dom";
import { useStore } from "../store/useStore";

/**
 * ProtectedRoute — 路由级权限控制
 *
 * 三种模式：
 * 1. 默认（仅登录）：未登录 → /login，已登录即可访问
 * 2. adminOnly：未登录 → /login，非 admin → /（首页），admin 可访问
 *
 * 使用示例：
 *   <Route path="/profile" element={<ProtectedRoute><Profile /></ProtectedRoute>} />
 *   <Route path="/admin"   element={<ProtectedRoute adminOnly><Admin /></ProtectedRoute>} />
 */

interface ProtectedRouteProps {
  children: React.ReactNode;
  /** 是否仅限管理员访问 */
  adminOnly?: boolean;
}

export default function ProtectedRoute({ children, adminOnly = false }: ProtectedRouteProps) {
  const { isAuthenticated, user } = useStore();

  // 1. 未登录 → 跳转登录页
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // 2. 需要管理员但角色不符 → 跳转首页
  if (adminOnly && user?.role !== "admin") {
    return <Navigate to="/" replace />;
  }

  // 3. 鉴权通过 → 渲染子路由
  return <>{children}</>;
}
