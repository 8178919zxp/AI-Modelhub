import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import Layout from "./components/Layout";
import ProtectedRoute from "./components/ProtectedRoute";
import Home from "./pages/Home";
import { Login, Register } from "./pages/Auth";
import ModelCenter from "./pages/ModelCenter";
import ModelDetail from "./pages/ModelDetail";
import Predict from "./pages/Predict";
import Profile from "./pages/Profile";
import Dashboard from "./pages/Dashboard";
import Admin from "./pages/Admin";
import About from "./pages/About";
import AgentCopilot from "./pages/AgentCopilot";


/**
 * 路由权限一览：
 * ───────────────────────────────────────
 * 公开页面（无需登录）：
 *   /           首页
 *   /about      关于我们
 *   /models     产品服务
 *   /models/:id 模型详情
 *   /predict    在线体验
 * ───────────────────────────────────────
 * 需登录页面：
 *   /dashboard  数据统计
 *   /profile    个人中心
 * ───────────────────────────────────────
 * 仅管理员：
 *   /admin      管理后台
 */

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 独立页 — 无 Layout 包裹 */}
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

        {/* 主体页 — Layout 包裹 */}
        <Route element={<Layout />}>
          {/* 企业官网展示页（公开） */}
          <Route path="/" element={<Home />} />
          <Route path="/about" element={<About />} />


          {/* AI 能力 & 体验（公开） */}
          <Route path="/models" element={<ModelCenter />} />
          <Route path="/models/:id" element={<ModelDetail />} />
          <Route path="/predict" element={<Predict />} />

          {/* 用户功能（需登录） */}
          <Route
            path="/agent"
            element={
              <ProtectedRoute>
                <AgentCopilot />
              </ProtectedRoute>
            }
          />
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <Dashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/profile"
            element={
              <ProtectedRoute>
                <Profile />
              </ProtectedRoute>
            }
          />

          {/* 管理后台（仅管理员） */}
          <Route
            path="/admin"
            element={
              <ProtectedRoute adminOnly>
                <Admin />
              </ProtectedRoute>
            }
          />

          {/* 兜底 */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
