import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  Home,
  PieChart,
  User,
  Plus,
  ReceiptText,
} from 'lucide-react';
import { Sidebar } from './Sidebar';
import { useAuth } from '../../context/AuthContext';

export function MainLayout({ children }: { children: React.ReactNode }) {
  const { openTransactionModal } = useAuth();

  return (
    <div className="flex min-h-screen bg-[#080A0F] text-slate-100 relative selection:bg-emerald-500/30">
      {/* Architectural Coordinate Grid Layer with Radial Mask */}
      <div className="pointer-events-none fixed inset-0 bg-grid-pattern mask-radial-faded opacity-50 z-0" />

      {/* Ambient Top Mesh Glow */}
      <div
        className="pointer-events-none fixed inset-x-0 top-0 h-96 z-0 opacity-70"
        style={{
          backgroundImage:
            'radial-gradient(ellipse 80% 50% at 50% -20%, rgba(16, 185, 129, 0.12), rgba(56, 189, 248, 0.04), transparent)',
        }}
      />

      {/* Desktop Sidebar (Linear / Mercury Tier-1) */}
      <Sidebar />

      {/* Main Workspace Area with Fluid Width max-w-[1520px] */}
      <div className="relative z-10 flex-1 flex flex-col min-w-0 pb-28 lg:pb-10">
        <main className="flex-1 w-full max-w-[1520px] mx-auto px-4 sm:px-6 lg:px-10 py-5 sm:py-7">
          {children}
        </main>
      </div>

      {/* Mobile Floating Bottom Navigation Dock */}
      <div className="lg:hidden fixed bottom-3 left-3 right-3 z-40 pointer-events-none">
        <div className="max-w-md mx-auto bg-[#12151F]/90 backdrop-blur-2xl border border-white/[0.09] rounded-[28px] px-2.5 py-2 shadow-2xl shadow-black/80 pointer-events-auto flex items-center justify-between relative">
          <MobileTab to="/" exact icon={Home} label="Tổng quan" />
          <MobileTab to="/transactions" icon={ReceiptText} label="Giao dịch" />

          {/* Center Green + FAB */}
          <div className="relative -mt-6 px-1">
            <button
              onClick={() => openTransactionModal({ mode: 'EXPENSE' })}
              aria-label="Ghi giao dịch mới"
              className="w-13 h-13 sm:w-14 sm:h-14 rounded-full bg-gradient-to-b from-emerald-400 to-emerald-600 active:scale-95 text-slate-950 flex items-center justify-center shadow-xl shadow-emerald-500/35 ring-4 ring-[#090A0F] transition-all cursor-pointer"
            >
              <Plus className="w-6 h-6 sm:w-7 sm:h-7 stroke-[2.8]" />
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
          isActive
            ? 'bg-white/[0.08] text-emerald-400 font-semibold'
            : 'text-slate-400 hover:text-slate-200'
        }`
      }
    >
      <Icon className="w-5 h-5" />
      <span className="text-[10px] font-medium mt-1 leading-none">{label}</span>
    </NavLink>
  );
}
