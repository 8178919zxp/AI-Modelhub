import { Link } from "react-router-dom";
import {
  MapPin,
  Clock,
  ArrowRight,
  Stethoscope,
  Scan,
  FileText,
  TrendingUp,
} from "lucide-react";

const SERVICE_LINKS = [
  { to: "/models?category=medical", label: "医学影像分析", icon: Stethoscope },
  { to: "/models?category=vision", label: "图片内容检测", icon: Scan },
  { to: "/models?category=text", label: "文字内容分析", icon: FileText },
  { to: "/models?category=data", label: "业务数据分析", icon: TrendingUp },
];

const QUICK_LINKS = [
  { to: "/", label: "首页" },
  { to: "/about", label: "关于我们" },
  { to: "/models", label: "产品服务" },
  { to: "/predict", label: "在线体验" },
];

const ABOUT_LINKS = [
  { to: "/about", label: "项目介绍" },
  { label: "隐私政策", to: "/" },
  { label: "用户协议", to: "/" },
];

export default function Footer() {
  return (
    <footer className="gradient-footer text-gray-400">
      {/* 主链接区 */}
      <div className="max-w-7xl mx-auto px-6 py-16">
        <div className="grid sm:grid-cols-2 lg:grid-cols-5 gap-10">
          {/* ① 项目信息 */}
          <div className="lg:col-span-1">
            <Link to="/" className="inline-block mb-4">
              <span className="text-lg font-bold text-white tracking-tight">
                AI ModelHub
              </span>
            </Link>
            <p className="text-sm text-gray-400 leading-relaxed mb-5">
              开源 AI 模型体验与推理平台，覆盖模型管理、
              在线推理与智能助手。
            </p>
            <div className="space-y-2.5 text-sm">
              <div className="flex items-start gap-2">
                <MapPin className="h-4 w-4 mt-0.5 shrink-0 text-gray-600" />
                <span className="text-gray-400 text-xs leading-relaxed">
                  演示环境
                  <br />
                  仅供学习与技术交流
                </span>
              </div>
              <div className="flex items-center gap-2">
                <Clock className="h-4 w-4 shrink-0 text-gray-600" />
                <span className="text-gray-400 text-xs">
                  周一至周五 9:00 – 18:00
                </span>
              </div>
            </div>
          </div>

          {/* ② 快速链接 */}
          <div>
            <h4 className="text-white font-semibold text-sm mb-5">快速链接</h4>
            <ul className="space-y-3">
              {QUICK_LINKS.map((l) => (
                <li key={l.to + l.label}>
                  <Link
                    to={l.to}
                    className="text-sm text-gray-400 hover:text-white transition-colors"
                  >
                    {l.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          {/* ③ 产品服务 */}
          <div>
            <h4 className="text-white font-semibold text-sm mb-5">产品服务</h4>
            <ul className="space-y-3">
              {SERVICE_LINKS.map((l) => {
                const Icon = l.icon;
                return (
                  <li key={l.to}>
                    <Link
                      to={l.to}
                      className="flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors group"
                    >
                      <Icon className="h-3.5 w-3.5 text-gray-600 group-hover:text-blue-400 transition-colors" />
                      <span>{l.label}</span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </div>

          {/* ④ 关于 & 法律 */}
          <div>
            <h4 className="text-white font-semibold text-sm mb-5">关于</h4>
            <ul className="space-y-3">
              {ABOUT_LINKS.map((l) => (
                <li key={l.label}>
                  <Link
                    to={l.to}
                    className="text-sm text-gray-400 hover:text-white transition-colors"
                  >
                    {l.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          {/* ⑤ 体验入口 */}
          <div>
            <h4 className="text-white font-semibold text-sm mb-5">开始使用</h4>
            <p className="text-sm text-gray-400 leading-relaxed mb-4">
              无需安装，打开浏览器即可开始使用
            </p>
            <Link
              to="/predict"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 text-white text-sm font-medium hover:bg-blue-500 transition-colors shadow-lg shadow-blue-600/20"
            >
              开始使用
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
        </div>
      </div>

      {/* 底部条 */}
      <div className="border-t border-gray-800">
        <div className="max-w-7xl mx-auto px-6 py-6 flex flex-col sm:flex-row items-center justify-between gap-3">
          <p className="text-xs text-gray-600">
            Copyright &copy; 2025-2026 AI ModelHub. All rights reserved.
          </p>
          <div className="flex items-center gap-4 text-xs text-gray-700">
            <Link to="/" className="hover:text-gray-400 transition-colors">
              隐私政策
            </Link>
            <Link to="/" className="hover:text-gray-400 transition-colors">
              用户协议
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
