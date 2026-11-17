import { Outlet, NavLink, useNavigate, useLocation, Link } from "react-router-dom";
import { useStore } from "../store/useStore";
import { logoutApi } from "../api/auth";
import {
  LayoutDashboard,
  BarChart3,
  LogOut,
  Shield,
  Cpu,
  Menu,
  X,
  Building2,
  ChevronDown,
  FlaskConical,
  Sparkles,
} from "lucide-react";
import { useState, useRef, useEffect } from "react";
import Footer from "./Footer";
import { AgentProvider } from "../agent/AgentContext";
import AgentFloatingWidget from "./agent/AgentFloatingWidget";

export default function Layout() {
  const { user, isAuthenticated, logout, refreshUser } = useStore();
  const navigate = useNavigate();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // 校验 localStorage 里持久化的会话是否仍然有效：token 可能已过期、
  // 被登出拉黑，或因改密而吊销。失败时 refreshUser 内部会自动登出。
  useEffect(() => {
    if (isAuthenticated) {
      refreshUser();
    }
    // 仅在挂载时校验一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 点击外部关闭下拉
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  /** 主导航 — 首页 | 关于我们 | 产品服务 | 在线体验 | 智能助理 */
  const mainNavItems = [
    { to: "/", label: "首页", icon: LayoutDashboard, end: true },
    { to: "/about", label: "关于我们", icon: Building2 },
    { to: "/models", label: "产品服务", icon: FlaskConical },
    { to: "/predict", label: "在线体验", icon: Cpu },
    // ModelHub Copilot：登录后可用（路由层有 ProtectedRoute 兜底）
    { to: "/agent", label: "智能助理", icon: Sparkles },
  ];

  /**
   * 用户下拉菜单 — 角色可见性控制
   * - 普通用户：仅显示"退出登录"
   * - 管理员：显示"管理后台" + "数据统计" + "退出登录"
   *
   * 管理后台与数据统计不在主导航显示，仅通过下拉菜单或直接 URL 访问。
   */
  const userDropdownItems =
    user?.role === "admin"
      ? [
          { to: "/admin", label: "管理后台", icon: Shield },
          { to: "/dashboard", label: "数据统计", icon: BarChart3 },
        ]
      : [];

  /**
   * 退出登录流程：
   * 1. 调用后端 POST /api/auth/logout，把 JWT 加入黑名单
   * 2. 无论接口结果如何，都清除本地状态（令牌、用户信息、收藏）
   * 3. 跳转到登录页
   */
  const handleLogout = async () => {
    try {
      await logoutApi();
    } catch {
      // 后端不可达或 Redis 故障 —— 仍然要清除本地登录态
    } finally {
      logout();
      navigate("/login");
    }
  };

  return (
    // Provider 放在 Layout 内、路由之上：整个登录态下只有一份 Agent 状态，
    // 页面切换（含进出 /agent）不会卸载它，正在进行的对话与 SSE 不会中断。
    <AgentProvider>
      <div className="min-h-screen flex flex-col bg-gray-50">
        {/* 顶部导航 */}
        <header className="sticky top-0 z-50 bg-white border-b border-gray-200 shadow-sm">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
            {/* Logo — AI ModelHub */}
            <Link to="/" className="flex items-center gap-3 shrink-0">
              <img src="/logo.svg" alt="AI ModelHub" className="h-10 w-10" />
              <span className="text-base font-semibold text-gray-800 hidden sm:inline">
                AI ModelHub
              </span>
            </Link>

            {/* 桌面主导航 */}
            <nav className="hidden lg:flex items-center gap-0.5">
              {mainNavItems.map((it) => {
                const Icon = it.icon;
                const active =
                  (it.end && location.pathname === it.to) ||
                  (!it.end && location.pathname.startsWith(it.to));
                return (
                  <NavLink
                    key={it.to}
                    to={it.to}
                    end={it.end}
                    className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-medium transition ${
                      active
                        ? "bg-blue-50 text-blue-700"
                        : "text-gray-600 hover:text-gray-900 hover:bg-gray-100"
                    }`}
                  >
                    <Icon className="h-3.5 w-3.5" />
                    {it.label}
                  </NavLink>
                );
              })}
            </nav>

            {/* 右侧用户区 */}
            <div className="flex items-center gap-2">
              {isAuthenticated && user ? (
                <div className="hidden lg:block relative" ref={dropdownRef}>
                  <button
                    onClick={() => setDropdownOpen(!dropdownOpen)}
                    className="flex items-center gap-2 px-3 py-1.5 rounded-lg hover:bg-gray-100 transition"
                  >
                    <span className="text-xl">{user.avatar}</span>
                    <span className="text-sm font-medium text-gray-700">{user.nickname}</span>
                    <ChevronDown
                      className={`h-3.5 w-3.5 text-gray-400 transition ${
                        dropdownOpen ? "rotate-180" : ""
                      }`}
                    />
                  </button>

                  {/* 下拉菜单 */}
                  {dropdownOpen && (
                    <div className="absolute right-0 top-full mt-1 w-48 rounded-xl bg-white border border-gray-200 shadow-lg py-1.5 z-50">
                      {userDropdownItems.map((d) => {
                        const DIcon = d.icon;
                        const dActive =
                          location.pathname === d.to || location.pathname.startsWith(d.to);
                        return (
                          <Link
                            key={d.to}
                            to={d.to}
                            onClick={() => setDropdownOpen(false)}
                            className={`flex items-center gap-2.5 px-4 py-2.5 text-sm ${
                              dActive
                                ? "bg-blue-50 text-blue-700"
                                : "text-gray-600 hover:bg-gray-50"
                            }`}
                          >
                            <DIcon className="h-4 w-4" />
                            {d.label}
                          </Link>
                        );
                      })}
                      {userDropdownItems.length > 0 && <hr className="my-1 border-gray-100" />}
                      <button
                        onClick={() => {
                          setDropdownOpen(false);
                          handleLogout();
                        }}
                        className="w-full flex items-center gap-2.5 px-4 py-2.5 text-sm text-red-500 hover:bg-red-50"
                      >
                        <LogOut className="h-4 w-4" /> 退出登录
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <div className="hidden lg:flex items-center gap-2">
                  <Link
                    to="/login"
                    className="px-4 py-1.5 text-sm text-gray-600 hover:text-gray-900 transition"
                  >
                    登录
                  </Link>
                  <Link
                    to="/register"
                    className="px-4 py-1.5 text-sm rounded-lg bg-blue-600 text-white hover:bg-blue-700 hover:shadow-lg hover:shadow-blue-500/20 transition"
                  >
                    免费注册
                  </Link>
                </div>
              )}

              {/* 移动端汉堡按钮 */}
              <button
                className="lg:hidden p-2 rounded-lg text-gray-500 hover:bg-gray-100"
                onClick={() => setMobileOpen(!mobileOpen)}
              >
                {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
              </button>
            </div>
          </div>

          {/* 移动端菜单 */}
          {mobileOpen && (
            <div className="lg:hidden border-t border-gray-200 bg-white max-h-[calc(100vh-4rem)] overflow-y-auto">
              <div className="p-3 space-y-1">
                {mainNavItems.map((it) => {
                  const Icon = it.icon;
                  const active =
                    (it.end && location.pathname === it.to) ||
                    (!it.end && location.pathname.startsWith(it.to));
                  return (
                    <NavLink
                      key={it.to}
                      to={it.to}
                      end={it.end}
                      onClick={() => setMobileOpen(false)}
                      className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm ${
                        active
                          ? "bg-blue-50 text-blue-700 font-medium"
                          : "text-gray-600 hover:bg-gray-50"
                      }`}
                    >
                      <Icon className="h-4 w-4" />
                      {it.label}
                    </NavLink>
                  );
                })}
                <div className="pt-2 mt-2 border-t border-gray-200">
                  {isAuthenticated ? (
                    <>
                      <div className="flex items-center gap-2.5 px-3 py-2 mb-1">
                        <span className="text-xl">{user?.avatar}</span>
                        <div>
                          <div className="text-sm font-medium text-gray-900">{user?.nickname}</div>
                          <div className="text-[10px] text-gray-500">
                            {user?.role === "admin" ? "管理员" : "普通用户"}
                          </div>
                        </div>
                      </div>
                      {userDropdownItems.map((d) => {
                        const DIcon = d.icon;
                        return (
                          <NavLink
                            key={d.to}
                            to={d.to}
                            onClick={() => setMobileOpen(false)}
                            className={({ isActive }) =>
                              `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm ${
                                isActive
                                  ? "bg-blue-50 text-blue-700 font-medium"
                                  : "text-gray-600 hover:bg-gray-50"
                              }`
                            }
                          >
                            <DIcon className="h-4 w-4" />
                            {d.label}
                          </NavLink>
                        );
                      })}
                      <button
                        onClick={() => {
                          setMobileOpen(false);
                          handleLogout();
                        }}
                        className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm text-red-500 hover:bg-red-50"
                      >
                        <LogOut className="h-4 w-4" /> 退出登录
                      </button>
                    </>
                  ) : (
                    <div className="flex gap-2">
                      <Link
                        to="/login"
                        onClick={() => setMobileOpen(false)}
                        className="flex-1 text-center px-3 py-2 rounded-lg bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 transition"
                      >
                        登录
                      </Link>
                      <Link
                        to="/register"
                        onClick={() => setMobileOpen(false)}
                        className="flex-1 text-center px-3 py-2 rounded-lg bg-blue-600 text-sm text-white hover:bg-blue-700 transition"
                      >
                        注册
                      </Link>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </header>

        {/* 内容区 */}
        <main className="flex-1">
          <Outlet />
        </main>

        <Footer />

        {/* 全站唯一的 Agent 入口；/agent 页内会自动隐藏 */}
        <AgentFloatingWidget />
      </div>
    </AgentProvider>
  );
}
