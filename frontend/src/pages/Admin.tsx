import { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { useStore } from "../store/useStore";
import {
  fetchAdminModelsApi,
  createModelApi,
  updateModelApi,
  toggleModelStatusApi,
  deleteModelApi,
  type ModelVO,
  type ModelSavePayload,
  type AdminPageVO,
} from "../api/models";
import { fetchDashboardApi, type DashboardOverview } from "../api/dashboard";
import {
  Users,
  Box,
  History,
  BarChart3,
  Shield,
  Search,
  Loader2,
  TrendingUp,
  Activity,
  Plus,
  Pencil,
  Power,
  PowerOff,
  Trash2,
  X,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  KeyRound,
} from "lucide-react";
import { fetchUsersApi, resetUserPasswordApi, type UserVO } from "../api/user";
import { formatAccuracy } from "../utils/format";
import { getTypeLabel } from "../data/models";

const TABS = [
  { key: "models", label: "模型管理", icon: Box },
  { key: "users", label: "用户管理", icon: Users },
  { key: "stats", label: "平台总览", icon: BarChart3 },
] as const;
type Tab = (typeof TABS)[number]["key"];

const EMPTY_FORM: ModelSavePayload = {
  modelName: "",
  modelType: "",
  algorithm: "",
  accuracy: undefined,
  description: "",
  cover: "",
  modelApi: "",
  status: 1,
};

const MODEL_TYPES = ["image", "text", "object", "ml", "audio", "video"] as const;

export default function Admin() {
  const { user, isAuthenticated } = useStore();
  const [models, setModels] = useState<ModelVO[]>([]);
  const [overview, setOverview] = useState<DashboardOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<Tab>("models");
  const [search, setSearch] = useState("");

    // 弹窗状态
  const [modal, setModal] = useState<{ type: "create" | "edit"; open: boolean }>({
    type: "create",
    open: false,
  });
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<ModelSavePayload>({ ...EMPTY_FORM });
  const [saving, setSaving] = useState(false);

    // 删除确认
  const [deleteTarget, setDeleteTarget] = useState<ModelVO | null>(null);
  const [deleting, setDeleting] = useState(false);

    // 错误提示
  const [feedback, setFeedback] = useState<{ type: "success" | "error"; msg: string } | null>(null);

    // ── 分页状态 ───────────────────────────────────────────────────
  const pageSize = 10;
  const [currentPage, setCurrentPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

    // ── 用户管理状态 ───────────────────────────────────────────────
  const [users, setUsers] = useState<UserVO[]>([]);
  const [userLoading, setUserLoading] = useState(false);
  const [userPage, setUserPage] = useState(0);
  const [userTotalElements, setUserTotalElements] = useState(0);
  const [userTotalPages, setUserTotalPages] = useState(1);
  const [userSearch, setUserSearch] = useState("");
  const [resetTarget, setResetTarget] = useState<UserVO | null>(null);
  const [resetting, setResetting] = useState(false);

    // ── 数据加载 ───────────────────────────────────────────────────

  const refresh = useCallback(async (page = 0) => {
    try {
      const [mResp, dResp] = await Promise.all([
        fetchAdminModelsApi(page, pageSize),
        fetchDashboardApi(),
      ]);
      if (mResp.code === 200) {
        const data = mResp.data;
                // 判断是否为分页响应
        if (data && typeof data === "object" && "content" in data) {
          const p = data as AdminPageVO<ModelVO>;
          setModels(p.content);
          setTotalElements(p.totalElements);
          setTotalPages(p.totalPages);
          setCurrentPage(p.currentPage);
        } else {
          setModels(data as ModelVO[]);
        }
      }
      if (dResp.code === 200) setOverview(dResp.data);
    } catch {
            // 忽略（接口不可用时保持空列表）
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh(currentPage);
  }, [refresh, currentPage]);

    // ── 用户列表加载 ───────────────────────────────────────────────

  const refreshUsers = useCallback(async (page = 0) => {
    setUserLoading(true);
    try {
      const res = await fetchUsersApi(page, pageSize);
      if (res.code === 200 && res.data) {
        setUsers(res.data.content);
        setUserTotalElements(res.data.totalElements);
        setUserTotalPages(res.data.totalPages);
        setUserPage(res.data.currentPage);
      }
    } catch {
            // 忽略（接口不可用时保持空列表）
    } finally {
      setUserLoading(false);
    }
  }, []);

  useEffect(() => {
    if (tab === "users") refreshUsers(userPage);
  }, [tab, userPage, refreshUsers]);

  const goToUserPage = (page: number) => {
    const clamped = Math.max(0, Math.min(page, userTotalPages - 1));
    setUserPage(clamped);
  };

  const handleResetPassword = async () => {
    if (!resetTarget) return;
    setResetting(true);
    try {
      const res = await resetUserPasswordApi(resetTarget.id);
      if (res.code === 200) {
        const temporaryPassword = res.data?.temporaryPassword;
        showFeedback(
          "success",
          temporaryPassword
            ? `用户 "${resetTarget.username}" 密码已重置，临时密码：${temporaryPassword}（仅显示一次，请立即转交）`
            : `用户 "${resetTarget.username}" ${res.message}`,
        );
        setResetTarget(null);
        await refreshUsers(userPage);
      } else {
        showFeedback("error", res.message || "操作失败");
      }
    } catch {
      showFeedback("error", "重置密码失败，请稍后重试");
    } finally {
      setResetting(false);
    }
  };

    // ── 辅助方法 ───────────────────────────────────────────────────

  const showFeedback = (type: "success" | "error", msg: string) => {
    setFeedback({ type, msg });
    setTimeout(() => setFeedback(null), 3500);
  };

  const openCreate = () => {
    setForm({ ...EMPTY_FORM });
    setEditingId(null);
    setModal({ type: "create", open: true });
  };

  const openEdit = (m: ModelVO) => {
    setForm({
      modelName: m.name,
      modelType: m.type,
      algorithm: m.algorithm || "",
      accuracy: m.accuracy,
      description: m.description || "",
      cover: m.cover || "",
      modelApi: m.api || "",
      status: m.status,
    });
    setEditingId(m.id);
    setModal({ type: "edit", open: true });
  };

  const closeModal = () => setModal({ ...modal, open: false });

    // ── 增删改操作 ─────────────────────────────────────────────────

  const goToPage = (page: number) => {
    const clamped = Math.max(0, Math.min(page, totalPages - 1));
    setCurrentPage(clamped);
  };

  const handleSave = async () => {
    if (!form.modelName.trim() || !form.modelType.trim()) {
      showFeedback("error", "模型名称和类型为必填项");
      return;
    }
    setSaving(true);
    try {
      const payload: ModelSavePayload = {
        ...form,
        modelName: form.modelName.trim(),
        modelType: form.modelType.trim(),
        accuracy: form.accuracy != null ? form.accuracy : undefined,
      };

      if (modal.type === "create") {
        const res = await createModelApi(payload);
        if (res.code === 200) {
          showFeedback("success", `模型 "${res.data.name}" 创建成功`);
        }
      } else if (editingId != null) {
        const res = await updateModelApi(editingId, payload);
        if (res.code === 200) {
          showFeedback("success", `模型 "${res.data.name}" 更新成功`);
        }
      }
      closeModal();
      await refresh(currentPage);
    } catch {
      showFeedback("error", "操作失败，请检查输入或网络连接");
    } finally {
      setSaving(false);
    }
  };

  const handleToggle = async (m: ModelVO) => {
    const newStatus = m.status === 1 ? 0 : 1;
    try {
      const res = await toggleModelStatusApi(m.id, newStatus as 0 | 1);
      if (res.code === 200) {
        showFeedback("success", `模型 "${m.name}" 已${newStatus === 1 ? "上架" : "下架"}`);
        await refresh(currentPage);
      }
    } catch {
      showFeedback("error", "状态切换失败");
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      const res = await deleteModelApi(deleteTarget.id);
      if (res.code === 200) {
        showFeedback("success", `模型 "${deleteTarget.name}" 已删除（软删除）`);
        setDeleteTarget(null);
        await refresh(currentPage);
      }
    } catch {
      showFeedback("error", "删除失败");
    } finally {
      setDeleting(false);
    }
  };

    // ── 派生数据 ───────────────────────────────────────────────────

  const statusLabel = (s: number) => {
    if (s === 1) return { text: "在线", cls: "bg-emerald-50 text-emerald-700" };
    if (s === 0) return { text: "离线", cls: "bg-amber-50 text-amber-700" };
    return { text: "已删除", cls: "bg-red-50 text-red-600" };
  };

  const visibleModels = models.filter(
    (m) =>
      m.name.toLowerCase().includes(search.toLowerCase()) ||
      (m.algorithm && m.algorithm.toLowerCase().includes(search.toLowerCase())),
  );

  const onlineCount = models.filter((m) => m.status === 1).length;

    // ── 权限校验 ───────────────────────────────────────────────────

  if (!isAuthenticated || !user || user.role !== "admin") {
    return (
      <div className="max-w-md mx-auto p-12 text-center">
        <Shield className="h-12 w-12 text-red-400 mx-auto mb-3" />
        <p className="text-gray-700 mb-2">权限不足</p>
        <p className="text-xs text-gray-500 mb-4">该页面仅管理员可访问</p>
        <Link
          to="/login"
          className="px-4 py-2 rounded-lg bg-blue-600 text-white inline-block text-sm hover:bg-blue-700 transition"
        >
          切换账号
        </Link>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-32">
        <Loader2 className="h-8 w-8 text-blue-600 animate-spin" />
      </div>
    );
  }

    // ── 渲染 ───────────────────────────────────────────────────────

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6">
      {/* Header */}
      <div className="mb-6 flex items-end justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 flex items-center gap-2">
            <Shield className="h-7 w-7 text-amber-500" /> 管理后台
          </h1>
          <p className="text-sm text-gray-500 mt-1">仅管理员可见 · 当前身份：{user.nickname}</p>
        </div>
      </div>

      {/* Feedback toast */}
      {feedback && (
        <div
          className={`mb-4 px-4 py-3 rounded-xl text-sm flex items-center gap-2 ${
            feedback.type === "success"
              ? "bg-emerald-50 border border-emerald-200 text-emerald-700"
              : "bg-red-50 border border-red-200 text-red-700"
          }`}
        >
          {feedback.type === "success" ? "✅" : "❌"} {feedback.msg}
          <button onClick={() => setFeedback(null)} className="ml-auto">
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      )}

      {/* KPI cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-5">
        {[
          { l: "注册用户", v: overview?.totalUsers || 0, i: Users, c: "from-cyan-600 to-blue-600" },
          {
            l: "在线模型",
            v: `${onlineCount}/${models.filter((m) => m.status !== -1).length}`,
            i: Box,
            c: "from-blue-600 to-indigo-600",
          },
          {
            l: "预测记录",
            v: overview?.totalCalls || 0,
            i: History,
            c: "from-emerald-600 to-teal-600",
          },
          {
            l: "热门模型",
            v: overview?.hotModels?.[0] || "—",
            i: TrendingUp,
            c: "from-amber-600 to-orange-600",
          },
        ].map((s) => {
          const Icon = s.i;
          return (
            <div key={s.l} className="glass rounded-xl p-4 card-hover">
              <div className={`inline-flex p-2 rounded-lg bg-gradient-to-br ${s.c} mb-2`}>
                <Icon className="h-4 w-4 text-white" />
              </div>
              <div className="text-xl font-bold text-gray-900">{String(s.v)}</div>
              <div className="text-[10px] text-gray-500">{s.l}</div>
            </div>
          );
        })}
      </div>

      {/* Tab layout */}
      <div className="grid lg:grid-cols-4 gap-4">
        {/* Sidebar tabs */}
        <div className="lg:col-span-1">
          <div className="glass rounded-2xl p-2 sticky top-20">
            {TABS.map((t) => {
              const Icon = t.icon;
              return (
                <button
                  key={t.key}
                  onClick={() => setTab(t.key)}
                  className={`w-full flex items-center gap-2.5 px-3 py-2.5 rounded-xl text-sm transition ${
                    tab === t.key
                      ? "bg-amber-50 text-amber-800 border border-amber-200"
                      : "text-gray-500 hover:bg-gray-50 hover:text-gray-900"
                  }`}
                >
                  <Icon className="h-4 w-4" /> {t.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* Main content */}
        <div className="lg:col-span-3 space-y-3">
          {tab === "models" && (
            <div className="glass rounded-2xl p-5">
              {/* Toolbar */}
              <div className="flex items-center justify-between mb-3 flex-wrap gap-2">
                <h3 className="font-semibold text-gray-900">
                  模型管理 ({visibleModels.length})
                  {models.filter((m) => m.status === -1).length > 0 && (
                    <span className="ml-2 text-xs text-red-500">
                      (含{models.filter((m) => m.status === -1).length}个已删除)
                    </span>
                  )}
                </h3>
                <div className="flex items-center gap-2">
                  <div className="relative">
                    <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-gray-400" />
                    <input
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                      placeholder="搜索模型"
                      className="pl-8 pr-3 py-1.5 rounded-lg bg-white border border-gray-300 text-sm text-gray-800 placeholder-gray-400 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 w-40"
                    />
                  </div>
                  <button
                    onClick={openCreate}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-blue-600 text-white text-sm hover:bg-blue-700 hover:shadow-lg hover:shadow-blue-500/20 transition"
                  >
                    <Plus className="h-3.5 w-3.5" /> 新增
                  </button>
                </div>
              </div>

              {/* Table */}
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-xs text-gray-500 border-b border-gray-200 bg-gray-50">
                      <th className="text-left py-2.5 px-2 font-medium">ID</th>
                      <th className="text-left py-2.5 px-2 font-medium">名称</th>
                      <th className="text-left py-2.5 px-2 font-medium">类型</th>
                      <th className="text-left py-2.5 px-2 font-medium">算法</th>
                      <th className="text-right py-2.5 px-2 font-medium">准确率</th>
                      <th className="text-center py-2.5 px-2 font-medium">状态</th>
                      <th className="text-left py-2.5 px-2 font-medium">更新时间</th>
                      <th className="text-center py-2.5 px-2 w-32 font-medium">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibleModels.map((m) => {
                      const st = statusLabel(m.status);
                      return (
                        <tr key={m.id} className="border-b border-gray-100 hover:bg-gray-50">
                          <td className="py-2.5 px-2 text-gray-500 font-mono text-xs">#{m.id}</td>
                          <td className="py-2.5 px-2">
                            <Link
                              to={`/models/${m.id}`}
                              className="text-gray-900 text-sm hover:text-blue-600 font-medium"
                            >
                              {m.name}
                            </Link>
                          </td>
                          <td className="py-2.5 px-2 text-xs text-gray-600">{getTypeLabel(m.type)}</td>
                          <td className="py-2.5 px-2 text-xs text-gray-600">
                            {m.algorithm || "—"}
                          </td>
                          <td className="py-2.5 px-2 text-right font-mono text-xs text-emerald-600 font-medium">
                            {formatAccuracy(m.accuracy)}
                          </td>
                          <td className="py-2.5 px-2 text-center">
                            <span className={`text-[10px] px-1.5 py-0.5 rounded ${st.cls}`}>
                              {st.text}
                            </span>
                          </td>
                          <td className="py-2.5 px-2 text-[10px] text-gray-500">
                            {m.updateTime
                              ? new Date(m.updateTime).toLocaleDateString("zh-CN")
                              : "—"}
                          </td>
                          <td className="py-2.5 px-2">
                            {m.status !== -1 ? (
                              <div className="flex items-center justify-center gap-1">
                                <button
                                  onClick={() => openEdit(m)}
                                  className="p-1 rounded text-gray-400 hover:text-amber-500 hover:bg-amber-50 transition"
                                  title="编辑"
                                >
                                  <Pencil className="h-3.5 w-3.5" />
                                </button>
                                <button
                                  onClick={() => handleToggle(m)}
                                  className="p-1 rounded text-gray-400 hover:text-cyan-600 hover:bg-cyan-50 transition"
                                  title={m.status === 1 ? "下架" : "上架"}
                                >
                                  {m.status === 1 ? (
                                    <PowerOff className="h-3.5 w-3.5" />
                                  ) : (
                                    <Power className="h-3.5 w-3.5" />
                                  )}
                                </button>
                                <button
                                  onClick={() => setDeleteTarget(m)}
                                  className="p-1 rounded text-gray-400 hover:text-red-500 hover:bg-red-50 transition"
                                  title="删除"
                                >
                                  <Trash2 className="h-3.5 w-3.5" />
                                </button>
                              </div>
                            ) : (
                              <span className="text-[10px] text-red-400/60">已删除</span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                    {visibleModels.length === 0 && (
                      <tr>
                        <td colSpan={8} className="py-8 text-center text-gray-400 text-sm">
                          {search ? "无匹配模型" : "暂无模型，点击「新增」创建第一个模型"}
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>

              {/* Pagination controls */}
              {totalElements > 0 && (
                <div className="flex items-center justify-between mt-4 pt-3 border-t border-gray-200">
                  <span className="text-xs text-gray-500">
                    共 {totalElements} 个模型 · 第 {currentPage + 1}/{totalPages} 页
                  </span>
                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => goToPage(0)}
                      disabled={currentPage === 0}
                      className="px-2 py-1 rounded text-xs text-gray-500 hover:text-gray-900 hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed transition"
                    >
                      首页
                    </button>
                    <button
                      onClick={() => goToPage(currentPage - 1)}
                      disabled={currentPage === 0}
                      className="p-1 rounded text-gray-500 hover:text-gray-900 hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed transition"
                    >
                      <ChevronLeft className="h-4 w-4" />
                    </button>
                    {/* Page number buttons */}
                    {Array.from({ length: Math.min(totalPages, 7) }, (_, i) => {
                                            // 只展示当前页附近的页码
                      let pageNum: number;
                      if (totalPages <= 7) {
                        pageNum = i;
                      } else if (currentPage < 4) {
                        pageNum = i;
                      } else if (currentPage >= totalPages - 4) {
                        pageNum = totalPages - 7 + i;
                      } else {
                        pageNum = currentPage - 3 + i;
                      }
                      return (
                        <button
                          key={pageNum}
                          onClick={() => goToPage(pageNum)}
                          className={`min-w-[28px] h-7 rounded text-xs transition ${
                            pageNum === currentPage
                              ? "bg-blue-50 border border-blue-200 text-blue-700 font-medium"
                              : "text-gray-500 hover:text-gray-900 hover:bg-gray-100"
                          }`}
                        >
                          {pageNum + 1}
                        </button>
                      );
                    })}
                    <button
                      onClick={() => goToPage(currentPage + 1)}
                      disabled={currentPage >= totalPages - 1}
                      className="p-1 rounded text-gray-500 hover:text-gray-900 hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed transition"
                    >
                      <ChevronRight className="h-4 w-4" />
                    </button>
                    <button
                      onClick={() => goToPage(totalPages - 1)}
                      disabled={currentPage >= totalPages - 1}
                      className="px-2 py-1 rounded text-xs text-gray-500 hover:text-gray-900 hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed transition"
                    >
                      末页
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {tab === "users" && (
            <div className="glass rounded-2xl p-5">
              {/* Toolbar */}
              <div className="flex items-center justify-between mb-3 flex-wrap gap-2">
                <h3 className="font-semibold text-gray-900">
                  用户管理 ({users.length})
                </h3>
                <div className="relative">
                  <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-gray-400" />
                  <input
                    value={userSearch}
                    onChange={(e) => setUserSearch(e.target.value)}
                    placeholder="搜索用户名或昵称"
                    className="pl-8 pr-3 py-1.5 rounded-lg bg-white border border-gray-300 text-sm text-gray-800 placeholder-gray-400 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 w-48"
                  />
                </div>
              </div>

              {/* Table */}
              {userLoading ? (
                <div className="flex items-center justify-center py-16">
                  <Loader2 className="h-6 w-6 text-blue-600 animate-spin" />
                </div>
              ) : (
                <>
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="text-xs text-gray-500 border-b border-gray-200 bg-gray-50">
                          <th className="text-left py-2.5 px-2 font-medium">ID</th>
                          <th className="text-left py-2.5 px-2 font-medium">用户名</th>
                          <th className="text-left py-2.5 px-2 font-medium">昵称</th>
                          <th className="text-center py-2.5 px-2 font-medium">角色</th>
                          <th className="text-left py-2.5 px-2 font-medium">注册时间</th>
                          <th className="text-center py-2.5 px-2 w-28 font-medium">操作</th>
                        </tr>
                      </thead>
                      <tbody>
                        {users
                          .filter(
                            (u) =>
                              u.username.toLowerCase().includes(userSearch.toLowerCase()) ||
                              (u.nickname && u.nickname.toLowerCase().includes(userSearch.toLowerCase())),
                          )
                          .map((u) => (
                            <tr key={u.id} className="border-b border-gray-100 hover:bg-gray-50">
                              <td className="py-2.5 px-2 text-gray-500 font-mono text-xs">#{u.id}</td>
                              <td className="py-2.5 px-2">
                                <span className="text-gray-900 text-sm font-medium">{u.username}</span>
                              </td>
                              <td className="py-2.5 px-2 text-sm text-gray-700">{u.nickname || "—"}</td>
                              <td className="py-2.5 px-2 text-center">
                                <span
                                  className={`text-[10px] px-1.5 py-0.5 rounded ${
                                    u.role === "admin"
                                      ? "bg-amber-50 text-amber-700"
                                      : "bg-blue-50 text-blue-700"
                                  }`}
                                >
                                  {u.role === "admin" ? "管理员" : "普通用户"}
                                </span>
                              </td>
                              <td className="py-2.5 px-2 text-[10px] text-gray-500">
                                {u.createTime
                                  ? new Date(u.createTime).toLocaleDateString("zh-CN")
                                  : "—"}
                              </td>
                              <td className="py-2.5 px-2 text-center">
                                <button
                                  onClick={() => setResetTarget(u)}
                                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-blue-600 text-white text-[11px] hover:bg-blue-700 hover:shadow-lg hover:shadow-blue-500/20 transition"
                                  title="重置为随机临时密码（仅显示一次）"
                                >
                                  <KeyRound className="h-3 w-3" /> 重置密码
                                </button>
                              </td>
                            </tr>
                          ))}
                        {users.filter((u) =>
                          u.username.toLowerCase().includes(userSearch.toLowerCase()) ||
                          (u.nickname && u.nickname.toLowerCase().includes(userSearch.toLowerCase())),
                        ).length === 0 && (
                          <tr>
                            <td colSpan={6} className="py-8 text-center text-gray-400 text-sm">
                              {userSearch ? "无匹配用户" : "暂无用户数据"}
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>

                  {/* Pagination */}
                  {userTotalElements > 0 && (
                    <div className="flex items-center justify-between mt-4 pt-3 border-t border-gray-200">
                      <span className="text-xs text-gray-500">
                        共 {userTotalElements} 个用户 · 第 {userPage + 1}/{userTotalPages} 页
                      </span>
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => goToUserPage(0)}
                          disabled={userPage === 0}
                          className="px-2 py-1 rounded text-xs text-gray-500 hover:text-gray-900 hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed transition"
                        >
                          首页
                        </button>
                        <button
                          onClick={() => goToUserPage(userPage - 1)}
                          disabled={userPage === 0}
                          className="p-1 rounded text-gray-500 hover:text-gray-900 hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed transition"
                        >
                          <ChevronLeft className="h-4 w-4" />
                        </button>
                        {Array.from({ length: Math.min(userTotalPages, 7) }, (_, i) => {
                          let pageNum: number;
                          if (userTotalPages <= 7) {
                            pageNum = i;
                          } else if (userPage < 4) {
                            pageNum = i;
                          } else if (userPage >= userTotalPages - 4) {
                            pageNum = userTotalPages - 7 + i;
                          } else {
                            pageNum = userPage - 3 + i;
                          }
                          return (
                            <button
                              key={pageNum}
                              onClick={() => goToUserPage(pageNum)}
                              className={`min-w-[28px] h-7 rounded text-xs transition ${
                                pageNum === userPage
                                  ? "bg-blue-50 border border-blue-200 text-blue-700 font-medium"
                                  : "text-gray-500 hover:text-gray-900 hover:bg-gray-100"
                              }`}
                            >
                              {pageNum + 1}
                            </button>
                          );
                        })}
                        <button
                          onClick={() => goToUserPage(userPage + 1)}
                          disabled={userPage >= userTotalPages - 1}
                          className="p-1 rounded text-gray-500 hover:text-gray-900 hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed transition"
                        >
                          <ChevronRight className="h-4 w-4" />
                        </button>
                        <button
                          onClick={() => goToUserPage(userTotalPages - 1)}
                          disabled={userPage >= userTotalPages - 1}
                          className="px-2 py-1 rounded text-xs text-gray-500 hover:text-gray-900 hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed transition"
                        >
                          末页
                        </button>
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>
          )}

          {tab === "stats" && (
            <div className="space-y-3">
              <div className="glass rounded-2xl p-5">
                <h3 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
                  <Activity className="h-4 w-4 text-cyan-600" /> 平台运行指标
                </h3>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                  {[
                    { l: "总模型数", v: overview?.totalModels || 0 },
                    { l: "在线模型", v: overview?.onlineModels || 0 },
                    { l: "总用户数", v: overview?.totalUsers || 0 },
                    { l: "总调用量", v: overview?.totalCalls || 0 },
                  ].map((x) => (
                    <div
                      key={x.l}
                      className="p-3 rounded-xl bg-gray-50 border border-gray-200 text-center"
                    >
                      <div className="text-[10px] text-gray-500 mb-1">{x.l}</div>
                      <div className="text-lg font-bold text-gray-900 font-mono">{String(x.v)}</div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="glass rounded-2xl p-5">
                <h3 className="font-semibold text-gray-900 mb-3">🔥 热门模型 Top 3</h3>
                <div className="space-y-2">
                  {(overview?.hotModels || []).map((name, i) => (
                    <div
                      key={name}
                      className="flex items-center gap-3 p-3 rounded-xl bg-gray-50 border border-gray-200"
                    >
                      <span className="text-lg">{["🥇", "🥈", "🥉"][i] || "📊"}</span>
                      <span className="text-sm text-gray-900 font-medium">{name}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ── Create/Edit Modal ─────────────────────────────────── */}
      {modal.open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
          <div className="bg-white rounded-2xl w-full max-w-lg max-h-[85vh] overflow-y-auto p-6 border border-gray-200 shadow-xl">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-bold text-gray-900">
                {modal.type === "create" ? "新增模型" : "编辑模型"}
              </h2>
              <button onClick={closeModal} className="p-1 rounded text-gray-400 hover:text-gray-900 hover:bg-gray-100">
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs text-gray-600 mb-1">
                    名称 <span className="text-red-500">*</span>
                  </label>
                  <input
                    value={form.modelName}
                    onChange={(e) => setForm({ ...form, modelName: e.target.value })}
                    placeholder="如 ResNet-50"
                    className="w-full px-3 py-2 rounded-lg bg-white border border-gray-300 text-gray-900 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 placeholder:text-gray-400"
                  />
                </div>
                <div>
                  <label className="block text-xs text-gray-600 mb-1">
                    类型 <span className="text-red-500">*</span>
                  </label>
                  <select
                    value={form.modelType}
                    onChange={(e) => setForm({ ...form, modelType: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg bg-white border border-gray-300 text-gray-800 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20"
                  >
                    <option value="">选择类型</option>
                    {MODEL_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {getTypeLabel(t)}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs text-gray-600 mb-1">算法</label>
                <input
                  value={form.algorithm || ""}
                  onChange={(e) => setForm({ ...form, algorithm: e.target.value })}
                  placeholder="如 Deep Residual Learning"
                  className="w-full px-3 py-2 rounded-lg bg-white border border-gray-300 text-gray-900 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 placeholder:text-gray-400"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs text-gray-600 mb-1">准确率 (%)</label>
                  <input
                    type="number"
                    min="0"
                    max="100"
                    step="0.1"
                    value={form.accuracy ?? ""}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        accuracy: e.target.value ? Number(e.target.value) : undefined,
                      })
                    }
                    placeholder="如 92.1"
                    className="w-full px-3 py-2 rounded-lg bg-white border border-gray-300 text-gray-900 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 placeholder:text-gray-400"
                  />
                </div>
                <div>
                  <label className="block text-xs text-gray-600 mb-1">初始状态</label>
                  <select
                    value={form.status ?? 1}
                    onChange={(e) => setForm({ ...form, status: Number(e.target.value) })}
                    className="w-full px-3 py-2 rounded-lg bg-white border border-gray-300 text-gray-800 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20"
                  >
                    <option value={1}>在线</option>
                    <option value={0}>离线</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs text-gray-600 mb-1">接口地址</label>
                <input
                  value={form.modelApi || ""}
                  onChange={(e) => setForm({ ...form, modelApi: e.target.value })}
                  placeholder="如 /api/predict/image"
                  className="w-full px-3 py-2 rounded-lg bg-white border border-gray-300 text-gray-900 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 placeholder:text-gray-400"
                />
              </div>

              <div>
                <label className="block text-xs text-gray-600 mb-1">封面 URL</label>
                <input
                  value={form.cover || ""}
                  onChange={(e) => setForm({ ...form, cover: e.target.value })}
                  placeholder="https://..."
                  className="w-full px-3 py-2 rounded-lg bg-white border border-gray-300 text-gray-900 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 placeholder:text-gray-400"
                />
              </div>

              <div>
                <label className="block text-xs text-gray-600 mb-1">描述</label>
                <textarea
                  value={form.description || ""}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                  placeholder="模型描述..."
                  rows={3}
                  className="w-full px-3 py-2 rounded-lg bg-white border border-gray-300 text-gray-900 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 resize-none placeholder:text-gray-400"
                />
              </div>
            </div>

            <div className="flex gap-2 mt-5">
              <button
                onClick={closeModal}
                className="flex-1 px-4 py-2 rounded-lg border border-gray-200 text-sm text-gray-700 hover:bg-gray-50 transition"
              >
                取消
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className="flex-1 px-4 py-2 rounded-lg bg-blue-600 text-white text-sm hover:bg-blue-700 hover:shadow-lg hover:shadow-blue-500/20 transition disabled:opacity-50"
              >
                {saving ? "保存中..." : modal.type === "create" ? "创建模型" : "保存修改"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Delete Confirmation Modal ─────────────────────────── */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
          <div className="bg-white rounded-2xl w-full max-w-sm p-6 border border-gray-200 shadow-xl text-center">
            <AlertTriangle className="h-10 w-10 text-red-400 mx-auto mb-3" />
            <h3 className="text-lg font-bold text-gray-900 mb-1">确认删除</h3>
            <p className="text-sm text-gray-600 mb-2">确定要删除模型「{deleteTarget.name}」吗？</p>
            <p className="text-xs text-gray-500 mb-5">
              此操作为软删除（状态标记为已删除），历史预测记录将保留。删除后可在管理后台查看已删除的模型。
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => setDeleteTarget(null)}
                className="flex-1 px-4 py-2 rounded-lg border border-gray-200 text-sm text-gray-700 hover:bg-gray-50 transition"
              >
                取消
              </button>
              <button
                onClick={handleDelete}
                disabled={deleting}
                className="flex-1 px-4 py-2 rounded-lg bg-red-50 border border-red-200 text-red-600 text-sm hover:bg-red-100 transition disabled:opacity-50"
              >
                {deleting ? "删除中..." : "确认删除"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Reset Password Confirmation Modal ────────────────── */}
      {resetTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
          <div className="bg-white rounded-2xl w-full max-w-sm p-6 border border-gray-200 shadow-xl text-center">
            <KeyRound className="h-10 w-10 text-blue-400 mx-auto mb-3" />
            <h3 className="text-lg font-bold text-gray-900 mb-1">确认重置密码</h3>
            <p className="text-sm text-gray-600 mb-2">
              确定要重置用户「{resetTarget.username}」的密码吗？
            </p>
            <p className="text-xs text-gray-500 mb-5">
              系统将生成 <strong>12 位随机临时密码</strong>并仅显示一次，请通过安全渠道转交用户；该用户已登录的会话会立即失效。
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => setResetTarget(null)}
                className="flex-1 px-4 py-2 rounded-lg border border-gray-200 text-sm text-gray-700 hover:bg-gray-50 transition"
              >
                取消
              </button>
              <button
                onClick={handleResetPassword}
                disabled={resetting}
                className="flex-1 px-4 py-2 rounded-lg bg-blue-600 text-white text-sm hover:bg-blue-700 hover:shadow-lg hover:shadow-blue-500/20 transition disabled:opacity-50"
              >
                {resetting ? "重置中..." : "确认重置"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
