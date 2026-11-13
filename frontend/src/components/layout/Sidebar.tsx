import React from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Home,
  Wallet,
  PieChart,
  User,
  Plus,
  ReceiptText,
  BarChart3,
  Users,
  LogOut,
  Sparkles,
  ExternalLink,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

export interface NavItem {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  exact?: boolean;
}

export const DESKTOP_NAV_LINKS: NavItem[] = [
  { to: '/', label: 'Tổng quan', icon: Home, exact: true },
  { to: '/transactions', label: 'Sổ giao dịch', icon: ReceiptText },
  { to: '/wallets', label: 'Ví của tôi', icon: Wallet },
  { to: '/budgets', label: 'Ngân sách', icon: PieChart },
  { to: '/reports', label: 'Báo cáo phân tích', icon: BarChart3 },
  { to: '/groups', label: 'Chia tiền nhóm', icon: Users },
  { to: '/account', label: 'Tài khoản', icon: User },
];

export function Sidebar() {
  const { user, openTransactionModal, logout } = useAuth();
  const location = useLocation();

  const isRouteActive = (path: string, exact?: boolean) => {
    if (exact) return location.pathname === path;
    return location.pathname.startsWith(path);
  };

  return (
    <aside className="hidden lg:flex lg:w-64 xl:w-72 flex-col shrink-0 bg-[#0E1017]/90 backdrop-blur-2xl border-r border-white/[0.06] sticky top-0 h-screen z-30 select-none">
      {/* Top Ambient Glow inside Sidebar */}
      <div className="pointer-events-none absolute inset-x-0 top-0 h-40 bg-gradient-to-b from-emerald-500/[0.04] to-transparent" />

      {/* Brand Header */}
      <div className="relative px-6 pt-6 pb-4 flex items-center justify-between">
        <NavLink to="/" className="flex items-center gap-3 group">
          <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-emerald-400 via-teal-500 to-cyan-500 flex items-center justify-center shadow-lg shadow-emerald-500/25 group-hover:scale-105 transition-transform">
            <Wallet className="w-5 h-5 text-slate-950 stroke-[2.4]" />
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <h1 className="text-base font-bold tracking-tight text-white leading-none">
                FinLedger
              </h1>
              <span className="px-1.5 py-0.5 rounded-full text-[9px] font-mono font-bold tracking-wider bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
                PRO
              </span>
            </div>
            <p className="text-[11px] text-slate-400 mt-1 font-medium">Tài chính cá nhân VN</p>
          </div>
        </NavLink>
      </div>

      {/* Primary Action Button (+ Ghi giao dịch) */}
      <div className="relative px-4 py-3">
        <button
          onClick={() => openTransactionModal({ mode: 'EXPENSE' })}
          className="w-full py-3 px-4 rounded-2xl bg-gradient-to-b from-emerald-400 to-emerald-600 text-slate-950 font-semibold text-sm flex items-center justify-center gap-2 shadow-[inset_0_1px_0_0_rgba(255,255,255,0.4),0_4px_20px_-2px_rgba(16,185,129,0.4)] hover:brightness-110 active:scale-[0.98] transition-all cursor-pointer"
        >
          <Plus className="w-4 h-4 stroke-[2.8]" />
          <span>Ghi giao dịch</span>
        </button>
      </div>

      {/* Navigation Links with Framer Motion LayoutId Pill */}
      <nav className="relative flex-1 px-3 py-2 space-y-1 overflow-y-auto">
        {DESKTOP_NAV_LINKS.map((item) => {
          const Icon = item.icon;
          const active = isRouteActive(item.to, item.exact);

          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.exact}
              className={`relative flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition-all group ${
                active ? 'text-emerald-400 font-semibold' : 'text-slate-400 hover:text-slate-200 hover:bg-white/[0.03]'
              }`}
            >
              {/* Active Pill Indicator */}
              {active && (
                <motion.div
                  layoutId="sidebar-active-pill"
                  className="absolute inset-0 bg-emerald-500/[0.1] border border-emerald-500/25 rounded-xl shadow-[inset_0_1px_0_0_rgba(16,185,129,0.2)]"
                  transition={{ type: 'spring', stiffness: 350, damping: 30 }}
                />
              )}

              {/* Vertical accent indicator line */}
              {active && (
                <motion.div
                  layoutId="sidebar-active-indicator"
                  className="absolute left-0 top-2 bottom-2 w-1 rounded-r-full bg-emerald-400 shadow-[0_0_12px_#10b981]"
                  transition={{ type: 'spring', stiffness: 350, damping: 30 }}
                />
              )}

              <Icon
                className={`relative z-10 w-4.5 h-4.5 transition-colors ${
                  active ? 'text-emerald-400' : 'text-slate-400 group-hover:text-slate-200'
                }`}
              />
              <span className="relative z-10 leading-none">{item.label}</span>
            </NavLink>
          );
        })}

        {/* Landing Page link */}
        <div className="pt-3 mt-2 border-t border-white/[0.06]">
          <NavLink
            to="/landing"
            className="flex items-center justify-between px-3.5 py-2 rounded-xl text-xs font-medium text-slate-400 hover:text-cyan-300 hover:bg-white/[0.03] transition-colors"
          >
            <span className="flex items-center gap-2">
              <Sparkles className="w-3.5 h-3.5 text-cyan-400" />
              <span>Giới thiệu sản phẩm</span>
            </span>
            <ExternalLink className="w-3 h-3 text-slate-500" />
          </NavLink>
        </div>
      </nav>

      {/* User Footer Profile */}
      {user && (
        <div className="relative p-3.5 border-t border-white/[0.06] bg-[#0A0C12]/60">
          <div className="flex items-center gap-3 px-2 py-1">
            <div
              className="w-9 h-9 rounded-full flex items-center justify-center text-xs font-bold text-slate-950 shrink-0 shadow-md ring-2 ring-white/10"
              style={{ backgroundColor: user.avatarColor || '#22c55e' }}
            >
              {user.fullName.charAt(0).toUpperCase()}
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold text-slate-100 truncate">{user.fullName}</p>
              <p className="text-[11px] text-slate-400 truncate font-mono">{user.email}</p>
            </div>
            <button
              onClick={logout}
              title="Đăng xuất"
              className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors cursor-pointer"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}
    </aside>
  );
}
