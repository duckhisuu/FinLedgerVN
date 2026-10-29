import React from 'react';
import { NavLink, useLocation } from 'react-router-dom';
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
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export function AppShell({ children }: { children: React.ReactNode }) {
  const { user, openTransactionModal, logout } = useAuth();
  const location = useLocation();

  const desktopLinks = [
    { to: '/', label: 'Tổng quan', icon: Home, exact: true },
    { to: '/transactions', label: 'Sổ giao dịch', icon: ReceiptText },
    { to: '/wallets', label: 'Ví của tôi', icon: Wallet },
    { to: '/budgets', label: 'Ngân sách', icon: PieChart },
    { to: '/reports', label: 'Báo cáo phân tích', icon: BarChart3 },
    { to: '/groups', label: 'Chia tiền nhóm', icon: Users },
    { to: '/account', label: 'Tài khoản', icon: User },
  ];

  const isRouteActive = (path: string, exact?: boolean) => {
    if (exact) return location.pathname === path;
    return location.pathname.startsWith(path);
  };

  return (
    <div className="flex min-h-screen bg-[#090c10] text-slate-100">
      {/* Desktop Sidebar */}
      <aside className="hidden lg:flex lg:w-64 xl:w-72 flex-col shrink-0 bg-[#10141d] border-r border-white/[0.06] sticky top-0 h-screen">
        {/* Brand */}
        <div className="px-6 pt-6 pb-4 flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-gradient-to-br from-emerald-400 to-green-600 flex items-center justify-center shadow-lg shadow-emerald-500/20">
            <Wallet className="w-5 h-5 text-slate-950 stroke-[2.4]" />
          </div>
          <div>
            <h1 className="text-lg font-bold tracking-tight text-white leading-none">
              FinLedger
            </h1>
            <p className="text-xs text-slate-400 mt-1">Tài chính cá nhân</p>
          </div>
        </div>

        {/* Quick Add Button */}
        <div className="px-4 py-3">
          <button
            onClick={() => openTransactionModal({ mode: 'EXPENSE' })}
            className="w-full py-3 px-4 rounded-2xl bg-[#22c55e] hover:bg-[#1eb053] active:scale-[0.99] text-slate-950 font-semibold text-sm flex items-center justify-center gap-2 shadow-lg shadow-emerald-500/20 transition-all"
          >
            <Plus className="w-5 h-5 stroke-[2.5]" />
            <span>Ghi giao dịch</span>
          </button>
        </div>

        {/* Navigation Links */}
        <nav className="flex-1 px-3 py-2 space-y-1 overflow-y-auto">
          {desktopLinks.map((item) => {
            const Icon = item.icon;
            const active = isRouteActive(item.to, item.exact);
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.exact}
                className={`flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition-all ${
                  active
                    ? 'bg-emerald-500/15 text-emerald-400'
                    : 'text-slate-400 hover:text-slate-100 hover:bg-white/[0.04]'
                }`}
              >
                <Icon className={`w-5 h-5 ${active ? 'text-emerald-400' : 'text-slate-400'}`} />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>

        {/* User Footer */}
        {user && (
          <div className="p-4 border-t border-white/[0.06]">
            <div className="flex items-center gap-3 px-2 py-1.5">
              <div
                className="w-9 h-9 rounded-full flex items-center justify-center text-sm font-bold text-slate-950 shrink-0"
                style={{ backgroundColor: user.avatarColor || '#22c55e' }}
              >
                {user.fullName.charAt(0).toUpperCase()}
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold text-slate-100 truncate">{user.fullName}</p>
                <p className="text-xs text-slate-400 truncate">{user.email}</p>
              </div>
              <button
                onClick={logout}
                title="Đăng xuất"
                className="p-2 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col min-w-0 pb-28 lg:pb-8">
        <main className="flex-1 w-full max-w-5xl mx-auto px-4 sm:px-6 pt-5 lg:pt-8">
          {children}
        </main>
      </div>

      {/* Mobile Floating Bottom Navigation Dock (Money Lover Style) */}
      <div className="lg:hidden fixed bottom-3 left-3 right-3 z-40 pointer-events-none">
        <div className="max-w-md mx-auto bg-[#161b26]/95 backdrop-blur-xl border border-white/[0.09] rounded-[28px] px-2 py-2 shadow-2xl shadow-black/70 pointer-events-auto flex items-center justify-between relative">
          <MobileTab to="/" exact icon={Home} label="Tổng quan" />
          <MobileTab to="/transactions" icon={ReceiptText} label="Giao dịch" />

          {/* Center Green + FAB */}
          <div className="relative -mt-6 px-1">
            <button
              onClick={() => openTransactionModal({ mode: 'EXPENSE' })}
              aria-label="Ghi giao dịch mới"
              className="w-14 h-14 rounded-full bg-[#22c55e] hover:bg-[#1eb053] active:scale-95 text-slate-950 flex items-center justify-center shadow-xl shadow-emerald-500/35 ring-4 ring-[#090c10] transition-transform"
            >
              <Plus className="w-7 h-7 stroke-[2.6]" />
            </button>
          </div>

          <MobileTab to="/budgets" icon={PieChart} label="Ngân sách" />
          <MobileTab to="/account" icon={User} label="Tài khoản" />
        </div>
      </div>
    </div>
  );
}

function MobileTab({
  to,
  exact,
  icon: Icon,
  label,
}: {
  to: string;
  exact?: boolean;
  icon: React.ComponentType<{ className?: string }>;
  label: string;
}) {
  return (
    <NavLink
      to={to}
      end={exact}
      className={({ isActive }) =>
        `flex-1 flex flex-col items-center justify-center py-1.5 rounded-2xl transition-all ${
          isActive ? 'bg-white/[0.07] text-[#22c55e]' : 'text-slate-400 hover:text-slate-200'
        }`
      }
    >
      <Icon className="w-5 h-5" />
      <span className="text-[11px] font-medium mt-1 leading-none">{label}</span>
    </NavLink>
  );
}

