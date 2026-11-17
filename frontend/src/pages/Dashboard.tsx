import { useEffect, useState } from "react";
import { fetchDashboardApi, type DashboardOverview } from "../api/dashboard";
import { fetchModelsApi, type ModelVO } from "../api/models";
import { getDisplayName } from "../data/models";
import { accuracyNumber, formatAccuracy } from "../utils/format";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend,
} from "recharts";
import { Activity, Box, Users, TrendingUp, Database, Cpu, Globe, Loader2 } from "lucide-react";

const PIE_COLORS = ["#06b6d4", "#ef4444", "#6366f1", "#10b981"];

export default function Dashboard() {
  const [overview, setOverview] = useState<DashboardOverview | null>(null);
  const [models, setModels] = useState<ModelVO[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const [dashResp, modelsResp] = await Promise.all([fetchDashboardApi(), fetchModelsApi()]);
        if (dashResp.code === 200) setOverview(dashResp.data);
        if (modelsResp.code === 200) setModels(modelsResp.data);
      } catch {
                // 接口不可用
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-32">
        <Loader2 className="h-8 w-8 text-blue-600 animate-spin" />
      </div>
    );
  }

  const typeDist = (["image", "object", "text", "ml"] as const)
    .map((type, i) => ({
      name: { image: "图像分类", object: "目标检测", text: "文本分类", ml: "机器学习" }[type],
      value: models.filter((m) => m.type === type).length,
      color: PIE_COLORS[i],
    }))
    .filter((d) => d.value > 0);

  const topModels = [...models]
    .sort((a, b) => (b.accuracy || 0) - (a.accuracy || 0))
    .slice(0, 5)
    .map((m) => ({ name: getDisplayName(m.name), value: accuracyNumber(m.accuracy) }));

  const stats = [
    {
      label: "在线模型",
      value: `${overview?.onlineModels || 0} / ${overview?.totalModels || 0}`,
      icon: Box,
      color: "from-blue-600 to-indigo-600",
    },
    {
      label: "累计推理",
      value: `${((overview?.totalCalls || 0) / 10000).toFixed(1)}万`,
      icon: Activity,
      color: "from-cyan-600 to-blue-600",
    },
    {
      label: "注册用户",
      value: String(overview?.totalUsers || 0),
      icon: Users,
      color: "from-emerald-600 to-teal-600",
    },
    {
      label: "热门模型",
      value: overview?.hotModels?.[0] || "—",
      icon: TrendingUp,
      color: "from-amber-600 to-orange-600",
    },
  ];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6">
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-gray-900">数据统计</h1>
        <p className="text-sm text-gray-600 mt-1">平台运行状态 · 模型调用 · 用户活跃度</p>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        {stats.map((s) => {
          const Icon = s.icon;
          return (
            <div key={s.label} className="glass rounded-2xl p-5 card-hover">
              <div className={`inline-flex p-2.5 rounded-lg bg-gradient-to-br ${s.color} mb-3`}>
                <Icon className="h-5 w-5 text-white" />
              </div>
              <div className="text-2xl font-bold text-gray-900 mb-1 truncate">{s.value}</div>
              <div className="text-xs text-gray-500">{s.label}</div>
            </div>
          );
        })}
      </div>

      <div className="grid lg:grid-cols-3 gap-4 mb-4">
        <div className="lg:col-span-2 glass rounded-2xl p-5">
          <h3 className="font-semibold text-gray-900 mb-1">热门模型 (按准确率)</h3>
          <p className="text-xs text-gray-500 mb-3">按准确率排序，取前 5 个模型</p>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={topModels} layout="vertical">
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.06)" />
              <XAxis
                type="number"
                domain={[0, 100]}
                tickFormatter={(v: number) => `${v}%`}
                stroke="#9ca3af"
                fontSize={10}
              />
              <YAxis type="category" dataKey="name" stroke="#6b7280" fontSize={10} width={100} />
              <Tooltip
                formatter={(value) => [formatAccuracy(Number(value)), "准确率"]}
                contentStyle={{
                  background: "#ffffff",
                  border: "1px solid #e5e7eb",
                  borderRadius: 8,
                  color: "#1f2937",
                }}
              />
              <Bar dataKey="value" fill="#6366f1" radius={[0, 6, 6, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="glass rounded-2xl p-5">
          <h3 className="font-semibold text-gray-900 mb-1">模型类型分布</h3>
          <p className="text-xs text-gray-500 mb-2">按模型类型统计在线服务数量</p>
          {typeDist.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie
                  data={typeDist}
                  dataKey="value"
                  nameKey="name"
                  innerRadius={50}
                  outerRadius={80}
                  paddingAngle={3}
                >
                  {typeDist.map((e, i) => (
                    <Cell key={i} fill={e.color} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{
                    background: "#ffffff",
                    border: "1px solid #e5e7eb",
                    borderRadius: 8,
                    color: "#1f2937",
                  }}
                />
                <Legend iconSize={8} wrapperStyle={{ fontSize: 11, color: "#6b7280" }} />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <p className="text-gray-500 text-sm text-center py-10">暂无数据</p>
          )}
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {[
          { l: "FastAPI 服务", v: "运行中", icon: Cpu, color: "text-emerald-600" },
          { l: "MySQL 数据库", v: "在线", icon: Database, color: "text-cyan-600" },
          { l: "Redis 缓存", v: "在线", icon: Database, color: "text-cyan-600" },
          { l: "Spring Boot", v: "运行中", icon: Globe, color: "text-emerald-600" },
        ].map((s) => {
          const Icon = s.icon;
          return (
            <div key={s.l} className="glass rounded-xl p-3 flex items-center gap-3">
              <Icon className={`h-5 w-5 ${s.color}`} />
              <div className="flex-1 min-w-0">
                <div className="text-xs text-gray-500">{s.l}</div>
                <div className={`text-sm font-medium ${s.color}`}>● {s.v}</div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
