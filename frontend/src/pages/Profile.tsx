import { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { useStore } from "../store/useStore";
import { fetchModelsApi, type ModelVO } from "../api/models";
import { fetchRecordsApi, type PredictRecordVO } from "../api/user";
import { getDisplayName, getTypeLabel } from "../data/models";
import { formatAccuracy } from "../utils/format";
import { User, Heart, History, Clock, ExternalLink, Loader2 } from "lucide-react";

const TABS = [
  { key: "info", label: "我的信息", icon: User },
  { key: "records", label: "预测记录", icon: History },
  { key: "favorites", label: "收藏模型", icon: Heart },
] as const;
type Tab = (typeof TABS)[number]["key"];

export default function Profile() {
  const { user, favorites, isAuthenticated, loadFavorites } = useStore();
  const [models, setModels] = useState<ModelVO[]>([]);
  const [records, setRecords] = useState<PredictRecordVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<Tab>("info");

  useEffect(() => {
    (async () => {
      try {
        const [mResp, recResp] = await Promise.all([
          fetchModelsApi(),
          fetchRecordsApi().catch(() => ({ code: 0, message: "", data: [] })),
          loadFavorites(),
        ]);
        if (mResp.code === 200) setModels(mResp.data);
        if (recResp.code === 200) setRecords(recResp.data);
      } catch {
                // 忽略
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  if (!isAuthenticated || !user) {
    return (
      <div className="max-w-md mx-auto p-12 text-center">
        <p className="text-gray-600 mb-4">请先登录</p>
        <Link to="/login" className="px-4 py-2 rounded-lg bg-blue-600 text-white inline-block hover:bg-blue-700 transition">
          去登录
        </Link>
      </div>
    );
  }

  const myFavModels = models.filter((m) => favorites.includes(m.id));

  if (loading) {
    return (
      <div className="flex items-center justify-center py-32">
        <Loader2 className="h-8 w-8 text-blue-600 animate-spin" />
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6">
      <h1 className="text-3xl font-bold text-gray-900 mb-1">个人中心</h1>
      <p className="text-sm text-gray-600 mb-6">管理你的个人信息、预测记录与收藏</p>

      <div className="grid lg:grid-cols-4 gap-6">
        {/* Sidebar */}
        <div className="lg:col-span-1 space-y-3">
          <div className="glass rounded-2xl p-5 text-center">
            <div className="text-5xl mb-3">{user.avatar || "🧑"}</div>
            <h3 className="font-semibold text-gray-900">{user.nickname}</h3>
            <p className="text-xs text-gray-500 mt-0.5">@{user.username}</p>
            <div className="mt-3 pt-3 border-t border-gray-200 grid grid-cols-2 gap-2 text-center">
              <div>
                <div className="text-lg font-bold text-gray-900">{records.length}</div>
                <div className="text-[10px] text-gray-500">预测记录</div>
              </div>
              <div>
                <div className="text-lg font-bold text-gray-900">{myFavModels.length}</div>
                <div className="text-[10px] text-gray-500">收藏模型</div>
              </div>
            </div>
          </div>

          <div className="glass rounded-2xl p-2">
            {TABS.map((t) => {
              const Icon = t.icon;
              return (
                <button
                  key={t.key}
                  onClick={() => setTab(t.key)}
                  className={`w-full flex items-center gap-2.5 px-3 py-2.5 rounded-xl text-sm transition ${
                    tab === t.key
                      ? "bg-blue-50 text-blue-700 border border-blue-200"
                      : "text-gray-500 hover:bg-gray-100 hover:text-gray-900"
                  }`}
                >
                  <Icon className="h-4 w-4" /> {t.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* Content */}
        <div className="lg:col-span-3 space-y-3">
          {tab === "info" && (
            <div className="glass rounded-2xl p-6">
              <h3 className="font-semibold text-gray-900 mb-4 flex items-center gap-2">
                <User className="h-4 w-4 text-blue-600" /> 基础信息
              </h3>
              <div className="grid sm:grid-cols-2 gap-4">
                {[
                  ["用户ID", `#${user.id}`],
                  ["用户名", user.username],
                  ["昵称", user.nickname],
                  ["角色", user.role === "admin" ? "🔑 管理员" : "普通用户"],
                  ["创建时间", user.createTime || "—"],
                  ["状态", "✅ 正常"],
                ].map(([k, v]) => (
                  <div key={k} className="p-3 rounded-xl bg-gray-50 border border-gray-200">
                    <div className="text-[10px] text-gray-500 mb-1">{k}</div>
                    <div className="text-sm text-gray-900">{v}</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {tab === "records" && (
            <div className="glass rounded-2xl p-5">
              <h3 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
                <History className="h-4 w-4 text-blue-600" /> 预测记录 ({records.length})
              </h3>
              {records.length === 0 ? (
                <div className="text-center py-12 text-gray-500 text-sm">
                  还没有预测记录，
                  <Link to="/predict" className="text-blue-600 hover:text-blue-700">
                    去体验 →
                  </Link>
                </div>
              ) : (
                <div className="space-y-2">
                  {records.map((r) => (
                    <div
                      key={r.id}
                      className="flex items-start gap-3 p-3 rounded-xl bg-gray-50 border border-gray-200 hover:border-blue-300 transition"
                    >
                      {/* File icon or thumbnail */}
                      <div className="h-10 w-10 rounded-lg bg-gradient-to-br from-blue-100 to-indigo-100 flex items-center justify-center text-lg shrink-0">
                        {r.fileUrl ? "🖼️" : "📝"}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-0.5 flex-wrap">
                          <span className="text-sm font-medium text-gray-900 truncate">
                            {r.predictResult}
                          </span>
                          <span className="text-[10px] font-mono text-emerald-600">
                            {(r.confidence * 100).toFixed(1)}%
                          </span>
                        </div>
                        {r.originalFilename && (
                          <div className="text-[10px] text-gray-500 mb-0.5">
                            文件：{r.originalFilename}
                          </div>
                        )}
                        {r.fileUrl && (
                          <a
                            href={r.fileUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1 text-[10px] text-blue-600 hover:text-blue-700 mt-0.5"
                          >
                            <ExternalLink className="h-2.5 w-2.5" /> 查看文件
                          </a>
                        )}
                        {r.inputText && (
                          <div className="text-[10px] text-gray-500 mt-0.5 line-clamp-1">
                            输入：
                            {r.inputText.length > 60
                              ? r.inputText.slice(0, 60) + "..."
                              : r.inputText}
                          </div>
                        )}
                      </div>
                      <div className="text-right shrink-0">
                        <div className="text-[10px] text-gray-500 flex items-center gap-1 justify-end">
                          <Clock className="h-2.5 w-2.5" /> {r.duration}ms
                        </div>
                        <div className="text-[10px] text-gray-500 mt-1">
                          {r.createTime ? r.createTime.slice(0, 16).replace("T", " ") : ""}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {tab === "favorites" && (
            <div className="glass rounded-2xl p-5">
              <h3 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
                <Heart className="h-4 w-4 text-rose-500" /> 收藏模型 ({myFavModels.length})
              </h3>
              {myFavModels.length === 0 ? (
                <div className="text-center py-12 text-gray-500 text-sm">
                  还没有收藏模型，
                  <Link to="/models" className="text-blue-600 hover:text-blue-700">
                    去浏览 →
                  </Link>
                </div>
              ) : (
                <div className="grid sm:grid-cols-2 gap-3">
                  {myFavModels.map((m) => {
                    const typeIcons: Record<string, string> = {
                      image: "🖼️",
                      object: "🎯",
                      text: "📝",
                      ml: "🌲",
                    };
                    return (
                      <Link
                        key={m.id}
                        to={`/models/${m.id}`}
                        className="flex items-center gap-3 p-3 rounded-xl bg-gray-50 border border-gray-200 hover:border-rose-300 transition"
                      >
                        <div className="h-12 w-12 rounded-xl bg-gradient-to-br from-blue-100 to-indigo-100 flex items-center justify-center text-2xl shrink-0">
                          {typeIcons[m.type] || "📦"}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="text-sm font-medium text-gray-900 truncate">{getDisplayName(m.name)}</div>
                          <div className="text-[10px] text-gray-500">
                            {getTypeLabel(m.type)} · {formatAccuracy(m.accuracy)}
                          </div>
                        </div>
                        <Heart className="h-4 w-4 text-rose-500 fill-current" />
                      </Link>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
