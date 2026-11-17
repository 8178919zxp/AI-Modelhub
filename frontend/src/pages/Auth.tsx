import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useStore } from "../store/useStore";
import { loginApi, registerApi, getSecurityQuestionApi, resetPasswordApi } from "../api/auth";
import { User, Lock, Eye, EyeOff, ArrowRight, ArrowLeft } from "lucide-react";

/* ──────────────────────────────────────────────
   Internal unified Auth page
   mode ∈ { login | register | forgot }
   Exported Login / Register are thin wrappers
   ────────────────────────────────────────────── */

function AuthPage({ initialMode }: { initialMode: "login" | "register" | "forgot" }) {
  const navigate = useNavigate();
  const { setSession } = useStore();

  const [mode, setMode] = useState<"login" | "register" | "forgot">(initialMode);

    // ── 通用文案 ──
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

    // ── 登录 ──
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPwd, setShowPwd] = useState(false);

    // ── 注册 ──
  const [form, setForm] = useState({
    username: "", password: "", confirm: "", nickname: "",
    securityQuestion: "", securityAnswer: "",
  });

    // ── 找回密码 ──
  const [step, setStep] = useState<1 | 2>(1);
  const [securityQuestion, setSecurityQuestion] = useState("");
  const [forgotUsername, setForgotUsername] = useState("");
  const [forgotAnswer, setForgotAnswer] = useState("");
  const [forgotNewPwd, setForgotNewPwd] = useState("");
  const [forgotConfirmPwd, setForgotConfirmPwd] = useState("");
  const [forgotShowPwd, setForgotShowPwd] = useState(false);

  /* ────────────────────────────────────
     Handlers
     ──────────────────────────────────── */

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const resp = await loginApi({ username, password });
      setSession({
        user: {
          id: resp.data.userId,
          username: resp.data.username,
          nickname: resp.data.nickname,
          role: resp.data.role,
          avatar: resp.data.avatar || (resp.data.role === "admin" ? "👨‍💼" : "🧑‍🔬"),
          createTime: "",
        },
        token: resp.data.token,
      });
      navigate("/");
    } catch (err: any) {
      setError(err?.message || "登录失败，请检查用户名和密码");
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!form.username || !form.password) return setError("请填写用户名和密码");
    if (form.password !== form.confirm) return setError("两次密码不一致");
    if (form.password.length < 6) return setError("密码长度至少 6 位");
    if (!form.securityQuestion || !form.securityAnswer) return setError("请填写密保问题和答案");
    setLoading(true);
    try {
      await registerApi({
        username: form.username,
        password: form.password,
        nickname: form.nickname || form.username,
        securityQuestion: form.securityQuestion,
        securityAnswer: form.securityAnswer,
      });
      const loginResp = await loginApi({ username: form.username, password: form.password });
      setSession({
        user: {
          id: loginResp.data.userId,
          username: loginResp.data.username,
          nickname: loginResp.data.nickname,
          role: loginResp.data.role,
          avatar: loginResp.data.avatar || "🧑‍🔬",
          createTime: "",
        },
        token: loginResp.data.token,
      });
      navigate("/");
    } catch (err: any) {
      setError(err?.message || "注册失败，请稍后重试");
    } finally {
      setLoading(false);
    }
  };

  const handleForgotStep1 = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!forgotUsername.trim()) return setError("请输入用户名");
    setLoading(true);
    try {
      const resp = await getSecurityQuestionApi(forgotUsername.trim());
      setSecurityQuestion(resp.data.securityQuestion);
      setStep(2);
    } catch (err: any) {
      setError(err?.message || "获取密保问题失败");
    } finally {
      setLoading(false);
    }
  };

  const handleForgotStep2 = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!forgotAnswer.trim()) return setError("请输入密保答案");
    if (!forgotNewPwd) return setError("请输入新密码");
    if (forgotNewPwd.length < 6) return setError("新密码长度至少 6 位");
    if (forgotNewPwd !== forgotConfirmPwd) return setError("两次密码不一致");
    setLoading(true);
    try {
      await resetPasswordApi({
        username: forgotUsername.trim(),
        securityAnswer: forgotAnswer.trim(),
        newPassword: forgotNewPwd,
      });
      alert("密码重置成功，请使用新密码登录");
            // 重置找回密码状态并切回登录
      setForgotUsername("");
      setForgotAnswer("");
      setForgotNewPwd("");
      setForgotConfirmPwd("");
      setSecurityQuestion("");
      setStep(1);
      setMode("login");
    } catch (err: any) {
      setError(err?.message || "密码重置失败");
    } finally {
      setLoading(false);
    }
  };

  const switchToForgot = () => {
    setError("");
    setForgotUsername(username); // 用登录页填过的用户名预填
    setStep(1);
    setMode("forgot");
  };

  /* ────────────────────────────────────
     Shared decoration background
     ──────────────────────────────────── */

  const decoration = (
    <>
      <div className="absolute inset-0 grid-bg opacity-30 pointer-events-none" />
      <div className="absolute top-1/3 left-1/4 w-72 h-72 bg-blue-200/60 rounded-full blur-3xl" />
      <div className="absolute bottom-1/3 right-1/4 w-72 h-72 bg-indigo-200/60 rounded-full blur-3xl" />
    </>
  );

  /* ════════════════════════════════════════
     RENDER: Login mode
     ════════════════════════════════════════ */

  if (mode === "login") {
    return (
      <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center p-4">
        {decoration}

        <div className="relative w-full max-w-md">
          <div className="glass rounded-3xl p-8 shadow-lg">
            <div className="text-center mb-8">
              <div className="inline-flex h-14 w-14 items-center justify-center mb-4">
                <img src="/logo.svg" alt="AI ModelHub" className="h-12 w-12" />
              </div>
              <h1 className="text-2xl font-bold text-gray-900">欢迎回来 👋</h1>
              <p className="text-sm text-gray-500 mt-1">登录 AI ModelHub</p>
            </div>

            <form onSubmit={handleLogin} className="space-y-4">
              <div>
                <label className="text-xs text-gray-500 mb-1.5 block">用户名</label>
                <div className="relative">
                  <User className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                  <input
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    className="w-full pl-10 pr-3 py-2.5 rounded-xl bg-white border border-gray-300 text-gray-800 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 transition placeholder:text-gray-400"
                    placeholder="请输入用户名"
                  />
                </div>
              </div>
              <div>
                <label className="text-xs text-gray-500 mb-1.5 block">密码</label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                  <input
                    type={showPwd ? "text" : "password"}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="w-full pl-10 pr-10 py-2.5 rounded-xl bg-white border border-gray-300 text-gray-800 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 transition placeholder:text-gray-400"
                    placeholder="请输入密码"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPwd(!showPwd)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  >
                    {showPwd ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              {/* Forgot password link */}
              <div className="text-right">
                <button
                  type="button"
                  onClick={switchToForgot}
                  className="text-xs text-blue-600 hover:text-blue-700 font-medium"
                >
                  忘记密码？
                </button>
              </div>

              {error && (
                <div className="text-xs px-3 py-2 rounded-lg bg-red-50 border border-red-200 text-red-600">
                  ⚠️ {error}
                </div>
              )}

              <button
                type="submit"
                disabled={loading}
                className="w-full py-2.5 rounded-xl bg-blue-600 text-white font-medium hover:bg-blue-700 hover:shadow-lg hover:shadow-blue-500/20 transition disabled:opacity-60 flex items-center justify-center gap-2"
              >
                {loading ? (
                  "登录中..."
                ) : (
                  <>
                    登 录 <ArrowRight className="h-4 w-4" />
                  </>
                )}
              </button>
            </form>

            <div className="mt-6 pt-6 border-t border-gray-200 text-center text-sm text-gray-500">
              还没有账号？
              <Link to="/register" className="text-blue-600 hover:text-blue-700 ml-1 font-medium">
                立即注册
              </Link>
            </div>
          </div>
        </div>
      </div>
    );
  }

  /* ════════════════════════════════════════
     RENDER: Register mode
     ════════════════════════════════════════ */

  if (mode === "register") {
    return (
      <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center p-4">
        <div className="absolute inset-0 grid-bg opacity-30 pointer-events-none" />
        <div className="absolute top-1/3 right-1/4 w-72 h-72 bg-blue-200/60 rounded-full blur-3xl" />
        <div className="absolute bottom-1/3 left-1/4 w-72 h-72 bg-indigo-200/60 rounded-full blur-3xl" />

        <div className="relative w-full max-w-md">
          <div className="glass rounded-3xl p-8 shadow-lg">
            <div className="text-center mb-6">
              <div className="inline-flex h-14 w-14 items-center justify-center mb-4">
                <img src="/logo.svg" alt="AI ModelHub" className="h-12 w-12" />
              </div>
              <h1 className="text-2xl font-bold text-gray-900">创建账号</h1>
              <p className="text-sm text-gray-500 mt-1">注册 AI ModelHub 账号</p>
            </div>

            <form onSubmit={handleRegister} className="space-y-3.5">
              <div>
                <label className="text-xs text-gray-500 mb-1.5 block">用户名 *</label>
                <input
                  value={form.username}
                  onChange={(e) => setForm({ ...form, username: e.target.value })}
                  className="w-full px-3 py-2.5 rounded-xl bg-white border border-gray-300 text-gray-800 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 placeholder:text-gray-400"
                  placeholder="3-20 位字符"
                />
              </div>
              <div>
                <label className="text-xs text-gray-500 mb-1.5 block">昵称</label>
                <input
                  value={form.nickname}
                  onChange={(e) => setForm({ ...form, nickname: e.target.value })}
                  className="w-full px-3 py-2.5 rounded-xl bg-white border border-gray-300 text-gray-800 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 placeholder:text-gray-400"
                  placeholder="留空将使用用户名"
                />
              </div>
              <div>
                <label className="text-xs text-gray-500 mb-1.5 block">密保问题 *</label>
                <input
                  value={form.securityQuestion}
                  onChange={(e) => setForm({ ...form, securityQuestion: e.target.value })}
                  className="w-full px-3 py-2.5 rounded-xl bg-white border border-gray-300 text-gray-800 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 placeholder:text-gray-400"
                  placeholder="例如：你的小学名称？"
                />
              </div>
              <div>
                <label className="text-xs text-gray-500 mb-1.5 block">密保答案 *</label>
                <input
                  value={form.securityAnswer}
                  onChange={(e) => setForm({ ...form, securityAnswer: e.target.value })}
                  className="w-full px-3 py-2.5 rounded-xl bg-white border border-gray-300 text-gray-800 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 placeholder:text-gray-400"
                  placeholder="用于找回密码"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-gray-500 mb-1.5 block">密码 *</label>
                  <input
                    type="password"
                    value={form.password}
                    onChange={(e) => setForm({ ...form, password: e.target.value })}
                    className="w-full px-3 py-2.5 rounded-xl bg-white border border-gray-300 text-gray-800 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 placeholder:text-gray-400"
                    placeholder="≥ 6 位"
                  />
                </div>
                <div>
                  <label className="text-xs text-gray-500 mb-1.5 block">确认密码 *</label>
                  <input
                    type="password"
                    value={form.confirm}
                    onChange={(e) => setForm({ ...form, confirm: e.target.value })}
                    className="w-full px-3 py-2.5 rounded-xl bg-white border border-gray-300 text-gray-800 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 placeholder:text-gray-400"
                  />
                </div>
              </div>

              {error && (
                <div className="text-xs px-3 py-2 rounded-lg bg-red-50 border border-red-200 text-red-600">
                  ⚠️ {error}
                </div>
              )}

              <button
                type="submit"
                disabled={loading}
                className="w-full py-2.5 rounded-xl bg-blue-600 text-white font-medium hover:bg-blue-700 hover:shadow-lg hover:shadow-blue-500/20 transition disabled:opacity-60"
              >
                {loading ? "注册中..." : "立即注册"}
              </button>
            </form>

            <div className="mt-5 pt-5 border-t border-gray-200 text-center text-sm text-gray-500">
              已有账号？
              <Link to="/login" className="text-blue-600 hover:text-blue-700 ml-1 font-medium">
                直接登录
              </Link>
            </div>
          </div>
        </div>
      </div>
    );
  }

  /* ════════════════════════════════════════
     RENDER: Forgot password mode
     ════════════════════════════════════════ */

  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center p-4">
      {decoration}

      <div className="relative w-full max-w-md">
        <div className="bg-white border border-gray-200 rounded-xl shadow-sm p-8">
          {/* Header */}
          <div className="text-center mb-6">
            <div className="inline-flex h-14 w-14 items-center justify-center mb-4">
              <img src="/logo.svg" alt="AI ModelHub" className="h-12 w-12" />
            </div>
            <h1 className="text-2xl font-bold text-gray-900">找回密码</h1>
            <p className="text-sm text-gray-500 mt-1">
              {step === 1
                ? "请输入您的用户名以获取密保问题"
                : "请回答密保问题并设置新密码"}
            </p>
          </div>

          {/* Step 1: enter username → get security question */}
          {step === 1 && (
            <form onSubmit={handleForgotStep1} className="space-y-4">
              <div>
                <label className="text-xs text-gray-500 mb-1.5 block">用户名</label>
                <div className="relative">
                  <User className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                  <input
                    value={forgotUsername}
                    onChange={(e) => setForgotUsername(e.target.value)}
                    className="w-full pl-10 pr-3 py-2.5 rounded-xl bg-white border border-gray-300 text-gray-800 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 transition placeholder:text-gray-400"
                    placeholder="请输入用户名"
                  />
                </div>
              </div>

              {error && (
                <div className="text-xs px-3 py-2 rounded-lg bg-red-50 border border-red-200 text-red-600">
                  ⚠️ {error}
                </div>
              )}

              <button
                type="submit"
                disabled={loading}
                className="w-full py-2.5 rounded-xl bg-blue-600 text-white font-medium hover:bg-blue-700 hover:shadow-lg hover:shadow-blue-500/20 transition disabled:opacity-60 flex items-center justify-center gap-2"
              >
                {loading ? "查询中..." : "下一步"}
                <ArrowRight className="h-4 w-4" />
              </button>

              <div className="text-center">
                <button
                  type="button"
                  onClick={() => { setMode("login"); setError(""); }}
                  className="text-sm text-gray-500 hover:text-gray-700 inline-flex items-center gap-1"
                >
                  <ArrowLeft className="h-3.5 w-3.5" />
                  返回登录
                </button>
              </div>
            </form>
          )}

          {/* Step 2: answer question + set new password */}
          {step === 2 && (
            <form onSubmit={handleForgotStep2} className="space-y-3.5">
              {/* Display security question (read-only) */}
              <div>
                <label className="text-xs text-gray-500 mb-1.5 block">密保问题</label>
                <div className="w-full px-3 py-2.5 rounded-xl bg-gray-50 border border-gray-200 text-gray-700 text-sm">
                  {securityQuestion}
                </div>
              </div>

              <div>
                <label className="text-xs text-gray-500 mb-1.5 block">密保答案 *</label>
                <input
                  value={forgotAnswer}
                  onChange={(e) => setForgotAnswer(e.target.value)}
                  className="w-full px-3 py-2.5 rounded-xl bg-white border border-gray-300 text-gray-800 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 placeholder:text-gray-400"
                  placeholder="请输入密保答案"
                />
              </div>

              <div>
                <label className="text-xs text-gray-500 mb-1.5 block">新密码 *</label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                  <input
                    type={forgotShowPwd ? "text" : "password"}
                    value={forgotNewPwd}
                    onChange={(e) => setForgotNewPwd(e.target.value)}
                    className="w-full pl-10 pr-10 py-2.5 rounded-xl bg-white border border-gray-300 text-gray-800 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 placeholder:text-gray-400"
                    placeholder="≥ 6 位"
                  />
                  <button
                    type="button"
                    onClick={() => setForgotShowPwd(!forgotShowPwd)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  >
                    {forgotShowPwd ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              <div>
                <label className="text-xs text-gray-500 mb-1.5 block">确认新密码 *</label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                  <input
                    type={forgotShowPwd ? "text" : "password"}
                    value={forgotConfirmPwd}
                    onChange={(e) => setForgotConfirmPwd(e.target.value)}
                    className="w-full pl-10 pr-3 py-2.5 rounded-xl bg-white border border-gray-300 text-gray-800 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 placeholder:text-gray-400"
                    placeholder="再次输入新密码"
                  />
                </div>
              </div>

              {error && (
                <div className="text-xs px-3 py-2 rounded-lg bg-red-50 border border-red-200 text-red-600">
                  ⚠️ {error}
                </div>
              )}

              <button
                type="submit"
                disabled={loading}
                className="w-full py-2.5 rounded-xl bg-blue-600 text-white font-medium hover:bg-blue-700 hover:shadow-lg hover:shadow-blue-500/20 transition disabled:opacity-60"
              >
                {loading ? "重置中..." : "重置密码"}
              </button>

              <div className="text-center flex justify-between">
                <button
                  type="button"
                  onClick={() => { setStep(1); setError(""); setSecurityQuestion(""); }}
                  className="text-sm text-gray-500 hover:text-gray-700 inline-flex items-center gap-1"
                >
                  <ArrowLeft className="h-3.5 w-3.5" />
                  上一步
                </button>
                <button
                  type="button"
                  onClick={() => { setMode("login"); setError(""); setStep(1); setSecurityQuestion(""); }}
                  className="text-sm text-gray-500 hover:text-gray-700"
                >
                  返回登录
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}

/* ──────────────────────────────────────────────
   Exported wrappers (preserve existing routes)
   ────────────────────────────────────────────── */

export function Login() {
  return <AuthPage initialMode="login" />;
}

export function Register() {
  return <AuthPage initialMode="register" />;
}
