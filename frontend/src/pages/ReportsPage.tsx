import { useEffect, useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  ChevronLeft,
  ChevronRight,
  Loader2,
  TrendingUp,
  TrendingDown,
  PiggyBank,
  Sparkles,
  RotateCcw,
} from 'lucide-react';
import {
  FinancialReports,
  financeApi,
} from '../api/client';
import { useAuth } from '../context/AuthContext';
import { BentoCard } from '../components/ui/BentoCard';
import { AnimatedCurrency } from '../components/ui/AnimatedCurrency';
import { MiniSparkline } from '../components/ui/MiniSparkline';
import { InteractiveDonut, DonutItem } from '../components/ui/InteractiveDonut';
import { RichEmptyState } from '../components/ui/RichEmptyState';
import { CategoryIcon } from '../components/CategoryIcon';
import {
  formatCompactVnd,
  formatVnd,
  formatYearMonthLabel,
  getCurrentYearMonth,
  shiftYearMonth,
} from '../utils/format';

// Demo realistic dataset for instant interactive visualization
const DEMO_REPORTS: FinancialReports = {
  selectedMonth: '2026-10',
  netWorth: 185450000,
  totalIncome: 38500000,
  totalExpense: 21350000,
  netSavings: 17150000,
  savingsRatePercent: 45,
  monthlyHistory: [
    { month: '2026-05', label: 'T5/26', income: 32000000, expense: 19500000, net: 12500000 },
    { month: '2026-06', label: 'T6/26', income: 34500000, expense: 22800000, net: 11700000 },
    { month: '2026-07', label: 'T7/26', income: 33000000, expense: 18400000, net: 14600000 },
    { month: '2026-08', label: 'T8/26', income: 36000000, expense: 20100000, net: 15900000 },
    { month: '2026-09', label: 'T9/26', income: 35000000, expense: 19800000, net: 15200000 },
    { month: '2026-10', label: 'T10/26', income: 38500000, expense: 21350000, net: 17150000 },
  ],
  expenseByCategory: [
    { categoryId: 'demo-c1', categoryName: 'Ăn uống & Cà phê', icon: 'utensils', color: '#ef4444', amount: 8900000, percentage: 42 },
    { categoryId: 'demo-c2', categoryName: 'Tiền thuê nhà & Điện nước', icon: 'home', color: '#f97316', amount: 5350000, percentage: 25 },
    { categoryId: 'demo-c3', categoryName: 'Mua sắm & Đồ dùng', icon: 'shopping-bag', color: '#ec4899', amount: 3000000, percentage: 14 },
    { categoryId: 'demo-c4', categoryName: 'Xăng xe & Di chuyển', icon: 'car', color: '#06b6d4', amount: 2100000, percentage: 10 },
    { categoryId: 'demo-c5', categoryName: 'Giải trí & Phim ảnh', icon: 'gamepad-2', color: '#8b5cf6', amount: 2000000, percentage: 9 },
  ],
  incomeByCategory: [
    { categoryId: 'demo-i1', categoryName: 'Lương cố định Tech', icon: 'briefcase', color: '#10b981', amount: 32000000, percentage: 83 },
    { categoryId: 'demo-i2', categoryName: 'Freelance & Tư vấn', icon: 'sparkles', color: '#38bdf8', amount: 4500000, percentage: 12 },
    { categoryId: 'demo-i3', categoryName: 'Cổ tức & Đầu tư', icon: 'trending-up', color: '#a855f7', amount: 2000000, percentage: 5 },
  ],
  wallets: [],
};

export function ReportsPage() {
  const { hideBalance, dataVersion, openTransactionModal } = useAuth();

  const [month, setMonth] = useState(getCurrentYearMonth());
  const [reports, setReports] = useState<FinancialReports | null>(null);
  const [loading, setLoading] = useState(true);
  const [isDemoMode, setIsDemoMode] = useState(false);
  const [timeframe, setTimeframe] = useState<'6M' | '1Y'>('6M');

  // Chart hover state for 6-month cashflow
  const [hoveredMonthIdx, setHoveredMonthIdx] = useState<number | null>(null);
  const [hoveredExpenseCatId, setHoveredExpenseCatId] = useState<string | null>(null);
  const [hoveredIncomeCatId, setHoveredIncomeCatId] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    financeApi
      .getReports(month)
      .then((res) => {
        if (active) setReports(res);
      })
      .catch((err) => console.error(err))
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [month, dataVersion]);

  // Active data source: real or demo preview
  const activeReports: FinancialReports | null = isDemoMode
    ? { ...DEMO_REPORTS, selectedMonth: month }
    : reports;

  const isRealDataAllZero = useMemo(() => {
    if (!reports) return true;
    return (
      reports.totalIncome === 0 &&
      reports.totalExpense === 0 &&
      reports.monthlyHistory.every((m) => m.income === 0 && m.expense === 0)
    );
  }, [reports]);

  // Calculate sparkline numbers from 6-month history
  const historyData = activeReports?.monthlyHistory || [];
  const incomeSparkline = historyData.map((m) => m.income);
  const expenseSparkline = historyData.map((m) => m.expense);
  const netSparkline = historyData.map((m) => m.net);

  // Compute trend percentages compared to previous month
  const prevMonthItem = historyData.length >= 2 ? historyData[historyData.length - 2] : null;
  const currentMonthItem = historyData.length >= 1 ? historyData[historyData.length - 1] : null;

  const incomeGrowthPercent = useMemo(() => {
    if (!prevMonthItem || !currentMonthItem || prevMonthItem.income === 0) return null;
    return Math.round(((currentMonthItem.income - prevMonthItem.income) / prevMonthItem.income) * 100);
  }, [prevMonthItem, currentMonthItem]);

  const expenseGrowthPercent = useMemo(() => {
    if (!prevMonthItem || !currentMonthItem || prevMonthItem.expense === 0) return null;
    return Math.round(((currentMonthItem.expense - prevMonthItem.expense) / prevMonthItem.expense) * 100);
  }, [prevMonthItem, currentMonthItem]);

  // Max value for 6-month chart scaling
  const maxHistoryVal = useMemo(() => {
    if (!activeReports) return 10_000_000;
    const maxVal = Math.max(
      1,
      ...activeReports.monthlyHistory.flatMap((m) => [m.income, m.expense])
    );
    return maxVal === 0 ? 10_000_000 : maxVal * 1.15;
  }, [activeReports]);

  // Transform breakdown for Interactive Donut
  const expenseDonutItems: DonutItem[] = useMemo(() => {
    if (!activeReports) return [];
    return activeReports.expenseByCategory.map((it) => ({
      id: it.categoryId,
      name: it.categoryName,
      amount: it.amount,
      percentage: it.percentage,
      color: it.color,
      icon: it.icon,
    }));
  }, [activeReports]);

  const incomeDonutItems: DonutItem[] = useMemo(() => {
    if (!activeReports) return [];
    return activeReports.incomeByCategory.map((it) => ({
      id: it.categoryId,
      name: it.categoryName,
      amount: it.amount,
      percentage: it.percentage,
      color: it.color,
      icon: it.icon,
    }));
  }, [activeReports]);

  return (
    <div className="space-y-6 pb-6">
      {/* Top Header & Glass MonthPicker Controls */}
      <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5">
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-white">
              Báo cáo & Phân tích tài chính
            </h1>
            {isDemoMode && (
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-cyan-500/15 text-cyan-400 border border-cyan-500/30 flex items-center gap-1 animate-pulse">
                <Sparkles className="w-3 h-3" />
                <span>Chế độ Demo trực quan</span>
              </span>
            )}
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Đo lường sức khỏe dòng tiền, cơ cấu chi tiêu và tốc độ tích lũy tài sản
          </p>
        </div>

        {/* Action Controls Bar */}
        <div className="flex flex-wrap items-center gap-2.5">
          {/* Timeframe Switcher */}
          <div className="flex items-center p-1 rounded-xl bg-[#12141C]/80 border border-white/[0.08] text-xs">
            <button
              onClick={() => setTimeframe('6M')}
              className={`px-3 py-1.5 rounded-lg font-medium transition-all ${
                timeframe === '6M'
                  ? 'bg-white/[0.09] text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              6 tháng
            </button>
            <button
              onClick={() => setTimeframe('1Y')}
              className={`px-3 py-1.5 rounded-lg font-medium transition-all ${
                timeframe === '1Y'
                  ? 'bg-white/[0.09] text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              1 năm
            </button>
          </div>

          {/* Month Selector Glass Pill */}
          <div className="flex items-center gap-1.5 bg-[#12141C]/80 border border-white/[0.08] backdrop-blur-xl rounded-xl p-1 shadow-sm">
            <button
              onClick={() => setMonth((m) => shiftYearMonth(m, -1))}
              className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/[0.06] transition-colors cursor-pointer"
              title="Tháng trước"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <span className="text-xs sm:text-sm font-semibold text-white min-w-[130px] text-center font-mono">
              {formatYearMonthLabel(month)}
            </span>
            <button
              onClick={() => setMonth((m) => shiftYearMonth(m, 1))}
              className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/[0.06] transition-colors cursor-pointer"
              title="Tháng sau"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>

          {/* Interactive Demo Toggle Button */}
          <button
            onClick={() => setIsDemoMode((prev) => !prev)}
            className={`inline-flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
              isDemoMode
                ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 shadow-[0_0_15px_rgba(56,189,248,0.2)]'
                : 'bg-white/[0.05] hover:bg-white/[0.09] text-slate-300 border border-white/[0.08]'
            }`}
            title={isDemoMode ? 'Quay lại dữ liệu thật' : 'Nạp dữ liệu giả lập để xem demo các biểu đồ'}
          >
            {isDemoMode ? (
              <>
                <RotateCcw className="w-3.5 h-3.5 text-cyan-400" />
                <span>Xem dữ liệu thật</span>
              </>
            ) : (
              <>
                <Sparkles className="w-3.5 h-3.5 text-cyan-400" />
                <span>✨ Dữ liệu Demo trực quan</span>
              </>
            )}
          </button>
        </div>
      </div>

      {loading && !activeReports ? (
        <div className="py-24 flex flex-col items-center justify-center gap-3">
          <Loader2 className="w-8 h-8 animate-spin text-emerald-400" />
          <span className="text-xs text-slate-400">Đang tổng hợp báo cáo tài chính...</span>
        </div>
      ) : activeReports ? (
        <div className="grid grid-cols-12 gap-5">
          {/* ======================================================== */}
          {/* ROW 1: 3 KPI Bento Cards (12 cols: 4 - 4 - 4) */}
          {/* ======================================================== */}

          {/* 1. Tổng thu nhập */}
          <BentoCard
            className="col-span-12 md:col-span-4 p-5 flex flex-col justify-between"
            spotlightColor="rgba(16, 185, 129, 0.16)"
          >
            {/* Top row */}
            <div className="flex items-start justify-between">
              <div>
                <span className="text-xs font-medium text-slate-400 tracking-wide">
                  Tổng thu nhập tháng
                </span>
                <div className="mt-1.5 flex items-baseline gap-1">
                  <AnimatedCurrency
                    value={activeReports.totalIncome}
                    hideBalance={hideBalance}
                    className="text-2xl lg:text-3xl font-bold text-emerald-400"
                  />
                </div>
              </div>
              <div className="w-10 h-10 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center shrink-0 shadow-[0_0_15px_-3px_rgba(16,185,129,0.3)]">
                <TrendingUp className="w-5 h-5" />
              </div>
            </div>

            {/* Bottom trend & Sparkline */}
            <div className="mt-4 pt-3 border-t border-white/[0.05] flex items-center justify-between">
              <div className="flex items-center gap-1.5">
                {incomeGrowthPercent !== null ? (
                  <span
                    className={`inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full text-[11px] font-mono font-semibold ${
                      incomeGrowthPercent >= 0
                        ? 'bg-emerald-500/15 text-emerald-400'
                        : 'bg-rose-500/15 text-rose-400'
                    }`}
                  >
                    {incomeGrowthPercent >= 0 ? '+' : ''}
                    {incomeGrowthPercent}%
                    <span className="text-[10px] font-sans font-normal text-slate-400 ml-1">
                      so với tháng trước
                    </span>
                  </span>
                ) : (
                  <span className="text-[11px] text-slate-500">Chu kỳ hiện tại</span>
                )}
              </div>
            </div>
            <MiniSparkline data={incomeSparkline} color="emerald" className="mt-1" />
          </BentoCard>

          {/* 2. Tổng chi tiêu */}
          <BentoCard
            className="col-span-12 md:col-span-4 p-5 flex flex-col justify-between"
            spotlightColor="rgba(244, 63, 94, 0.16)"
          >
            <div className="flex items-start justify-between">
              <div>
                <span className="text-xs font-medium text-slate-400 tracking-wide">
                  Tổng chi tiêu tháng
                </span>
                <div className="mt-1.5 flex items-baseline gap-1">
                  <AnimatedCurrency
                    value={activeReports.totalExpense}
                    hideBalance={hideBalance}
                    className="text-2xl lg:text-3xl font-bold text-rose-400"
                  />
                </div>
              </div>
              <div className="w-10 h-10 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400 flex items-center justify-center shrink-0 shadow-[0_0_15px_-3px_rgba(244,63,94,0.3)]">
                <TrendingDown className="w-5 h-5" />
              </div>
            </div>

            <div className="mt-4 pt-3 border-t border-white/[0.05] flex items-center justify-between">
              <div className="flex items-center gap-1.5">
                {expenseGrowthPercent !== null ? (
                  <span
                    className={`inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full text-[11px] font-mono font-semibold ${
                      expenseGrowthPercent <= 0
                        ? 'bg-emerald-500/15 text-emerald-400'
                        : 'bg-rose-500/15 text-rose-400'
                    }`}
                  >
                    {expenseGrowthPercent >= 0 ? '+' : ''}
                    {expenseGrowthPercent}%
                    <span className="text-[10px] font-sans font-normal text-slate-400 ml-1">
                      so với tháng trước
                    </span>
                  </span>
                ) : (
                  <span className="text-[11px] text-slate-500">Chu kỳ hiện tại</span>
                )}
              </div>
            </div>
            <MiniSparkline data={expenseSparkline} color="rose" className="mt-1" />
          </BentoCard>

          {/* 3. Tiết kiệm ròng */}
          <BentoCard
            className="col-span-12 md:col-span-4 p-5 flex flex-col justify-between"
            spotlightColor="rgba(56, 189, 248, 0.16)"
          >
            <div className="flex items-start justify-between">
              <div>
                <span className="text-xs font-medium text-slate-400 tracking-wide">
                  Tiết kiệm ròng
                </span>
                <div className="mt-1.5 flex items-baseline gap-1">
                  <AnimatedCurrency
                    value={activeReports.netSavings}
                    hideBalance={hideBalance}
                    showSign
                    className={`text-2xl lg:text-3xl font-bold ${
                      activeReports.netSavings >= 0 ? 'text-sky-400' : 'text-rose-400'
                    }`}
                  />
                </div>
              </div>
              <div className="w-10 h-10 rounded-xl bg-sky-500/10 border border-sky-500/20 text-sky-400 flex items-center justify-center shrink-0 shadow-[0_0_15px_-3px_rgba(56,189,248,0.3)]">
                <PiggyBank className="w-5 h-5" />
              </div>
            </div>

            <div className="mt-4 pt-3 border-t border-white/[0.05] flex items-center justify-between">
              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-mono font-semibold bg-sky-500/15 text-sky-300 border border-sky-500/25">
                <span>Tỷ lệ tiết kiệm:</span>
                <span className="text-white font-bold">{activeReports.savingsRatePercent}%</span>
              </span>
            </div>
            <MiniSparkline data={netSparkline} color="cyan" className="mt-1" />
          </BentoCard>

          {/* ======================================================== */}
          {/* ROW 2: Biểu đồ Dòng tiền 6 tháng gần nhất (col-span-12) */}
          {/* ======================================================== */}
          <BentoCard
            className="col-span-12 p-6"
            spotlightColor="rgba(16, 185, 129, 0.09)"
          >
            <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
              <div>
                <h2 className="text-base font-bold text-white tracking-tight">
                  Dòng tiền Thu – Chi 6 tháng gần nhất
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  Đối sánh thực chi và tích lũy hàng tháng theo thời gian thực
                </p>
              </div>

              {/* Legends */}
              <div className="flex items-center gap-4 text-xs text-slate-300 font-medium">
                <span className="flex items-center gap-2">
                  <span className="w-3 h-3 rounded-md bg-gradient-to-t from-emerald-500 to-emerald-400 shadow-[0_0_8px_rgba(16,185,129,0.5)]" />
                  <span>Thu nhập</span>
                </span>
                <span className="flex items-center gap-2">
                  <span className="w-3 h-3 rounded-md bg-gradient-to-t from-rose-500 to-rose-400 shadow-[0_0_8px_rgba(244,63,94,0.5)]" />
                  <span>Chi tiêu</span>
                </span>
                <span className="flex items-center gap-2 text-slate-400">
                  <span className="w-2.5 h-0.5 bg-cyan-400" />
                  <span>Chênh lệch ròng</span>
                </span>
              </div>
            </div>

            {/* If all values are 0 in real mode */}
            {!isDemoMode && isRealDataAllZero ? (
              <RichEmptyState
                variant="bars"
                title="Chưa có dòng tiền phát sinh trong 6 tháng gần nhất"
                description="Bắt đầu ghi nhận các khoản thu chi đầu tiên hoặc bấm nạp dữ liệu Demo để xem trực quan biểu đồ đối sánh"
                actionLabel="+ Ghi giao dịch ngay"
                onAction={() => openTransactionModal({ mode: 'EXPENSE' })}
                secondaryActionLabel="✨ Bật xem Demo trực quan"
                onSecondaryAction={() => setIsDemoMode(true)}
                className="py-12"
              />
            ) : (
              /* Enhanced SVG Bar & Grid Chart */
              <div className="relative pt-4">
                {/* SVG Definitions for Gradients */}
                <svg className="absolute w-0 h-0" aria-hidden="true">
                  <defs>
                    <linearGradient id="bar-income-grad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#34d399" stopOpacity="0.95" />
                      <stop offset="100%" stopColor="#10b981" stopOpacity="0.35" />
                    </linearGradient>
                    <linearGradient id="bar-expense-grad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#fb7185" stopOpacity="0.95" />
                      <stop offset="100%" stopColor="#f43f5e" stopOpacity="0.35" />
                    </linearGradient>
                  </defs>
                </svg>

                {/* Grid Lines + Y-Axis Labels + Bars Container */}
                <div className="relative h-64 flex">
                  {/* Left Y-Axis Labels */}
                  <div className="w-16 sm:w-20 shrink-0 h-full flex flex-col justify-between text-[11px] font-mono text-slate-500 text-right pr-3 select-none">
                    <span>{formatCompactVnd(maxHistoryVal)}</span>
                    <span>{formatCompactVnd(maxHistoryVal * 0.75)}</span>
                    <span>{formatCompactVnd(maxHistoryVal * 0.5)}</span>
                    <span>{formatCompactVnd(maxHistoryVal * 0.25)}</span>
                    <span>0 ₫</span>
                  </div>

                  {/* Chart Body with Horizontal Grid Lines */}
                  <div className="relative flex-1 h-full border-l border-b border-white/[0.08]">
                    {/* Horizontal Dashed Grid Lines */}
                    <div className="absolute inset-0 flex flex-col justify-between pointer-events-none">
                      <div className="w-full border-b border-dashed border-white/[0.06]" />
                      <div className="w-full border-b border-dashed border-white/[0.06]" />
                      <div className="w-full border-b border-dashed border-white/[0.06]" />
                      <div className="w-full border-b border-dashed border-white/[0.06]" />
                      <div className="w-full border-b border-transparent" />
                    </div>

                    {/* Columns Grid */}
                    <div className="relative z-10 grid grid-cols-6 h-full px-2 sm:px-6">
                      {historyData.map((item, idx) => {
                        const incPercent = (item.income / maxHistoryVal) * 100;
                        const expPercent = (item.expense / maxHistoryVal) * 100;
                        const isHovered = hoveredMonthIdx === idx;
                        const isSelected = item.month === month;

                        return (
                          <div
                            key={item.month}
                            onMouseEnter={() => setHoveredMonthIdx(idx)}
                            onMouseLeave={() => setHoveredMonthIdx(null)}
                            onClick={() => setMonth(item.month)}
                            className="relative flex flex-col items-center justify-end h-full group cursor-pointer"
                          >
                            {/* Column Bars */}
                            <div className="w-full flex items-end justify-center gap-1 sm:gap-2.5 h-[85%] pb-1">
                              {/* Income Bar */}
                              <motion.div
                                className="w-3 sm:w-5 sm:max-w-[24px] rounded-t-md relative transition-all duration-300"
                                style={{
                                  background: 'url(#bar-income-grad), linear-gradient(to top, rgba(16,185,129,0.3), #34d399)',
                                  boxShadow: isHovered
                                    ? '0 0 16px rgba(16, 185, 129, 0.45)'
                                    : 'none',
                                }}
                                initial={{ height: 0 }}
                                animate={{
                                  height: `${Math.max(item.income > 0 ? 5 : 2, incPercent)}%`,
                                }}
                                transition={{
                                  type: 'spring',
                                  stiffness: 160,
                                  damping: 20,
                                  delay: idx * 0.05,
                                }}
                              />

                              {/* Expense Bar */}
                              <motion.div
                                className="w-3 sm:w-5 sm:max-w-[24px] rounded-t-md relative transition-all duration-300"
                                style={{
                                  background: 'url(#bar-expense-grad), linear-gradient(to top, rgba(244,63,94,0.3), #fb7185)',
                                  boxShadow: isHovered
                                    ? '0 0 16px rgba(244, 63, 94, 0.45)'
                                    : 'none',
                                }}
                                initial={{ height: 0 }}
                                animate={{
                                  height: `${Math.max(item.expense > 0 ? 5 : 2, expPercent)}%`,
                                }}
                                transition={{
                                  type: 'spring',
                                  stiffness: 160,
                                  damping: 20,
                                  delay: idx * 0.05 + 0.02,
                                }}
                              />
                            </div>

                            {/* Month Label below */}
                            <div className="pt-2 text-center select-none">
                              <span
                                className={`text-[11px] sm:text-xs font-semibold block transition-colors ${
                                  isSelected
                                    ? 'text-emerald-400 font-bold'
                                    : isHovered
                                      ? 'text-white'
                                      : 'text-slate-400'
                                }`}
                              >
                                {item.label}
                              </span>
                            </div>

                            {/* Floating Glass Tooltip on Hover */}
                            <AnimatePresence>
                              {isHovered && (
                                <motion.div
                                  initial={{ opacity: 0, y: 8, scale: 0.95 }}
                                  animate={{ opacity: 1, y: 0, scale: 1 }}
                                  exit={{ opacity: 0, y: 4, scale: 0.95 }}
                                  transition={{ duration: 0.15 }}
                                  className="absolute -top-26 z-30 pointer-events-none bg-[#161924]/95 backdrop-blur-xl border border-white/[0.12] rounded-xl px-3 py-2.5 shadow-2xl text-left min-w-[170px]"
                                >
                                  <p className="text-[11px] font-bold text-white border-b border-white/[0.08] pb-1 mb-1.5">
                                    Tháng {item.label}
                                  </p>
                                  <div className="space-y-1 text-[11px] font-mono">
                                    <div className="flex items-center justify-between text-emerald-400">
                                      <span className="font-sans text-slate-400">Thu:</span>
                                      <span className="font-bold">{formatVnd(item.income)}</span>
                                    </div>
                                    <div className="flex items-center justify-between text-rose-400">
                                      <span className="font-sans text-slate-400">Chi:</span>
                                      <span className="font-bold">{formatVnd(item.expense)}</span>
                                    </div>
                                    <div className="flex items-center justify-between text-sky-400 pt-1 border-t border-white/[0.06]">
                                      <span className="font-sans text-slate-400">Ròng:</span>
                                      <span className="font-bold">
                                        {item.net >= 0 ? '+' : ''}
                                        {formatVnd(item.net)}
                                      </span>
                                    </div>
                                  </div>
                                </motion.div>
                              )}
                            </AnimatePresence>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </div>
              </div>
            )}
          </BentoCard>

          {/* ======================================================== */}
          {/* ROW 3: Cơ cấu Chi tiêu & Cơ cấu Thu nhập (col 6 & 6) */}
          {/* ======================================================== */}

          {/* Cơ cấu chi tiêu */}
          <BentoCard
            className="col-span-12 lg:col-span-6 p-6"
            spotlightColor="rgba(244, 63, 94, 0.1)"
          >
            <div className="flex items-center justify-between mb-5">
              <div>
                <h3 className="text-base font-bold text-white tracking-tight">
                  Cơ cấu chi tiêu theo danh mục
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  Tỷ trọng các nhóm chi tiêu trong tháng {formatYearMonthLabel(month)}
                </p>
              </div>
              <span className="text-xs font-mono font-semibold text-rose-400 px-2 py-0.5 rounded-full bg-rose-500/10 border border-rose-500/20">
                {activeReports.expenseByCategory.length} danh mục
              </span>
            </div>

            {activeReports.expenseByCategory.length === 0 ? (
              <RichEmptyState
                variant="donut"
                title="Chưa có khoản chi nào trong tháng này"
                description="Ghi nhận các chi tiêu ăn uống, mua sắm hoặc hóa đơn để theo dõi cơ cấu ngân sách trực quan"
                actionLabel="+ Ghi khoản chi đầu tiên"
                onAction={() => openTransactionModal({ mode: 'EXPENSE' })}
                secondaryActionLabel={isDemoMode ? undefined : '✨ Xem dữ liệu demo'}
                onSecondaryAction={() => setIsDemoMode(true)}
                className="py-8"
              />
            ) : (
              <div className="flex flex-col sm:flex-row items-center gap-6">
                {/* Donut Chart */}
                <InteractiveDonut
                  items={expenseDonutItems}
                  totalAmount={activeReports.totalExpense}
                  hideBalance={hideBalance}
                  activeItemId={hoveredExpenseCatId}
                  onHoverItem={setHoveredExpenseCatId}
                  size={180}
                  strokeWidth={20}
                />

                {/* Category List with Progress Bars */}
                <div className="flex-1 w-full space-y-3 max-h-60 overflow-y-auto pr-1">
                  {activeReports.expenseByCategory.map((it) => {
                    const isHovered = hoveredExpenseCatId === it.categoryId;

                    return (
                      <div
                        key={it.categoryId}
                        onMouseEnter={() => setHoveredExpenseCatId(it.categoryId)}
                        onMouseLeave={() => setHoveredExpenseCatId(null)}
                        className={`p-2 rounded-xl transition-all cursor-pointer ${
                          isHovered ? 'bg-white/[0.06] scale-[1.01]' : 'hover:bg-white/[0.03]'
                        }`}
                      >
                        <div className="flex items-center justify-between text-xs mb-1.5">
                          <div className="flex items-center gap-2 min-w-0">
                            <CategoryIcon icon={it.icon} color={it.color} size="sm" />
                            <span className="font-semibold text-slate-100 truncate">
                              {it.categoryName}
                            </span>
                          </div>
                          <div className="text-right shrink-0">
                            <span className="mono-num font-bold text-white">
                              {formatVnd(it.amount, hideBalance)}
                            </span>
                            <span className="text-[11px] font-mono text-slate-400 ml-1.5 font-normal">
                              ({it.percentage}%)
                            </span>
                          </div>
                        </div>

                        {/* Progress Bar */}
                        <div className="h-1.5 rounded-full bg-white/[0.06] overflow-hidden">
                          <motion.div
                            className="h-full rounded-full"
                            style={{ backgroundColor: it.color }}
                            initial={{ width: 0 }}
                            animate={{ width: `${Math.min(100, it.percentage)}%` }}
                            transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </BentoCard>

          {/* Cơ cấu thu nhập */}
          <BentoCard
            className="col-span-12 lg:col-span-6 p-6"
            spotlightColor="rgba(16, 185, 129, 0.1)"
          >
            <div className="flex items-center justify-between mb-5">
              <div>
                <h3 className="text-base font-bold text-white tracking-tight">
                  Cơ cấu nguồn thu nhập
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  Phân bổ dòng tiền vào từ lương, thưởng và đầu tư
                </p>
              </div>
              <span className="text-xs font-mono font-semibold text-emerald-400 px-2 py-0.5 rounded-full bg-emerald-500/10 border border-emerald-500/20">
                {activeReports.incomeByCategory.length} nguồn thu
              </span>
            </div>

            {activeReports.incomeByCategory.length === 0 ? (
              <RichEmptyState
                variant="donut"
                title="Chưa có khoản thu nào trong tháng này"
                description="Ghi nhận thu nhập tiền lương, thưởng định kỳ hoặc lợi nhuận đầu tư"
                actionLabel="+ Ghi khoản thu đầu tiên"
                onAction={() => openTransactionModal({ mode: 'INCOME' })}
                secondaryActionLabel={isDemoMode ? undefined : '✨ Xem dữ liệu demo'}
                onSecondaryAction={() => setIsDemoMode(true)}
                className="py-8"
              />
            ) : (
              <div className="flex flex-col sm:flex-row items-center gap-6">
                {/* Donut Chart */}
                <InteractiveDonut
                  items={incomeDonutItems}
                  totalAmount={activeReports.totalIncome}
                  hideBalance={hideBalance}
                  activeItemId={hoveredIncomeCatId}
                  onHoverItem={setHoveredIncomeCatId}
                  size={180}
                  strokeWidth={20}
                />

                {/* Income List with Progress Bars */}
                <div className="flex-1 w-full space-y-3 max-h-60 overflow-y-auto pr-1">
                  {activeReports.incomeByCategory.map((it) => {
                    const isHovered = hoveredIncomeCatId === it.categoryId;

                    return (
                      <div
                        key={it.categoryId}
                        onMouseEnter={() => setHoveredIncomeCatId(it.categoryId)}
                        onMouseLeave={() => setHoveredIncomeCatId(null)}
                        className={`p-2 rounded-xl transition-all cursor-pointer ${
                          isHovered ? 'bg-white/[0.06] scale-[1.01]' : 'hover:bg-white/[0.03]'
                        }`}
                      >
                        <div className="flex items-center justify-between text-xs mb-1.5">
                          <div className="flex items-center gap-2 min-w-0">
                            <CategoryIcon icon={it.icon} color={it.color} size="sm" />
                            <span className="font-semibold text-slate-100 truncate">
                              {it.categoryName}
                            </span>
                          </div>
                          <div className="text-right shrink-0">
                            <span className="mono-num font-bold text-white">
                              {formatVnd(it.amount, hideBalance)}
                            </span>
                            <span className="text-[11px] font-mono text-slate-400 ml-1.5 font-normal">
                              ({it.percentage}%)
                            </span>
                          </div>
                        </div>

                        {/* Progress Bar */}
                        <div className="h-1.5 rounded-full bg-white/[0.06] overflow-hidden">
                          <motion.div
                            className="h-full rounded-full"
                            style={{ backgroundColor: it.color }}
                            initial={{ width: 0 }}
                            animate={{ width: `${Math.min(100, it.percentage)}%` }}
                            transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </BentoCard>
        </div>
      ) : null}
    </div>
  );
}
