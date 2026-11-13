import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Eye,
  EyeOff,
  Search,
  Bell,
  HelpCircle,
  ChevronLeft,
  ChevronRight,
  Plus,
  Loader2,
  AlertTriangle,
} from 'lucide-react';
import {
  DailyTrendPoint,
  HomeOverview,
  PersonalTransaction,
  financeApi,
} from '../api/client';
import { useAuth } from '../context/AuthContext';
import { CategoryIcon } from '../components/CategoryIcon';
import { AnimatedCurrency } from '../components/ui/AnimatedCurrency';
import { formatCompactVnd, formatSignedVnd, formatVnd } from '../utils/format';

export function HomePage() {
  const {
    hideBalance,
    toggleHideBalance,
    dataVersion,
    openTransactionModal,
  } = useAuth();
  const navigate = useNavigate();

  const [overview, setOverview] = useState<HomeOverview | null>(null);
  const [loading, setLoading] = useState(true);

  // Report card states (matching Screenshot 1)
  const [reportMetric, setReportMetric] = useState<'SPENT' | 'INCOME'>('SPENT');
  const [chartView, setChartView] = useState<'TRENDING' | 'DAILY'>('TRENDING');

  // Top spending toggle (matching Screenshot 2: Week | Month)
  const [topPeriod, setTopPeriod] = useState<'WEEK' | 'MONTH'>('MONTH');

  // Popovers
  const [showBalanceHint, setShowBalanceHint] = useState(false);
  const [showAlerts, setShowAlerts] = useState(false);

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const res = await financeApi.getOverview();
        if (active) setOverview(res);
      } catch (err) {
        console.error('Failed to load overview', err);
      } finally {
        if (active) setLoading(false);
      }
    };
    load();
    return () => {
      active = false;
    };
  }, [dataVersion]);

  if (loading && !overview) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <Loader2 className="w-7 h-7 animate-spin text-[#22c55e]" />
      </div>
    );
  }

  const data = overview || {
    totalBalance: 0,
    currentMonth: '',
    wallets: [],
    monthSpent: 0,
    monthIncome: 0,
    prev3MonthAvgSpent: 0,
    prev3MonthAvgIncome: 0,
    dailyTrend: [],
    topSpendingMonth: [],
    topSpendingWeek: [],
    recentTransactions: [],
    budgetAlerts: [],
  };

  const topItems =
    topPeriod === 'MONTH' ? data.topSpendingMonth : data.topSpendingWeek;

  const activeMetricTotal =
    reportMetric === 'SPENT' ? data.monthSpent : data.monthIncome;
  const activePrevAvg =
    reportMetric === 'SPENT' ? data.prev3MonthAvgSpent : data.prev3MonthAvgIncome;
  const hasChartData = activeMetricTotal > 0 || activePrevAvg > 0;

  return (
    <div className="space-y-6 pb-4">
      {/* 1. TOP BALANCE BAR (Money Lover Header) */}
      <div className="flex items-start justify-between gap-4 relative">
        <div>
          <div className="flex items-center gap-2.5">
            <AnimatedCurrency
              value={data.totalBalance}
              hideBalance={hideBalance}
              className="text-2xl sm:text-3xl font-extrabold text-white"
            />
            <button
              onClick={toggleHideBalance}
              aria-label="Ẩn/Hiện số dư"
              className="p-1.5 rounded-full text-slate-300 hover:text-white hover:bg-white/[0.06] transition-colors"
            >
              {hideBalance ? (
                <EyeOff className="w-5 h-5" />
              ) : (
                <Eye className="w-5 h-5" />
              )}
            </button>
          </div>

          <div className="flex items-center gap-1.5 mt-1 relative">
            <span className="text-xs font-medium text-slate-400">Tổng số dư</span>
            <button
              type="button"
              onClick={() => setShowBalanceHint((s) => !s)}
              className="text-slate-500 hover:text-slate-300 transition-colors"
            >
              <HelpCircle className="w-3.5 h-3.5" />
            </button>
            {showBalanceHint && (
              <div className="absolute top-6 left-0 z-30 w-64 p-3 rounded-xl bg-[#1e2536] border border-white/10 text-xs text-slate-200 shadow-xl">
                Tổng số dư được cộng từ tất cả các ví đang bật tùy chọn{' '}
                <strong className="text-white">"Tính vào tổng số dư"</strong>.
              </div>
            )}
          </div>
        </div>

        {/* Search & Notification Bell */}
        <div className="flex items-center gap-2 relative">
          <button
            onClick={() => navigate('/transactions?focusSearch=1')}
            title="Tìm kiếm giao dịch"
            className="w-10 h-10 rounded-full bg-[#161b26] border border-white/[0.07] hover:bg-white/[0.08] flex items-center justify-center text-slate-200 transition-colors"
          >
            <Search className="w-4 h-4" />
          </button>

          <button
            onClick={() => setShowAlerts((s) => !s)}
            title="Thông báo ngân sách"
            className="w-10 h-10 rounded-full bg-[#161b26] border border-white/[0.07] hover:bg-white/[0.08] flex items-center justify-center text-slate-200 transition-colors relative"
          >
            <Bell className="w-4 h-4" />
            {data.budgetAlerts.length > 0 && (
              <span className="absolute top-2 right-2.5 w-2 h-2 rounded-full bg-rose-500 ring-2 ring-[#161b26]" />
            )}
          </button>

          {showAlerts && (
            <div className="absolute right-0 top-12 z-30 w-80 bg-[#181f2e] border border-white/10 rounded-2xl p-4 shadow-2xl">
              <div className="flex items-center justify-between mb-2.5">
                <span className="text-xs font-bold text-white uppercase tracking-wider">
                  Cảnh báo ngân sách
                </span>
                <Link
                  to="/budgets"
                  onClick={() => setShowAlerts(false)}
                  className="text-xs text-[#22c55e] hover:underline"
                >
                  Quản lý
                </Link>
              </div>
              {data.budgetAlerts.length === 0 ? (
                <p className="text-xs text-slate-400 py-3 text-center">
                  Chi tiêu tháng này đang nằm trong ngưỡng an toàn.
                </p>
              ) : (
                <div className="space-y-2.5">
                  {data.budgetAlerts.map((al) => (
                    <div
                      key={al.budgetId}
                      className="p-2.5 rounded-xl bg-[#10141d] border border-white/[0.05] flex items-start gap-2.5"
                    >
                      <AlertTriangle
                        className={`w-4 h-4 shrink-0 mt-0.5 ${al.exceeded ? 'text-rose-400' : 'text-amber-400'
                          }`}
                      />
                      <div className="text-xs">
                        <p className="font-semibold text-white">{al.categoryName}</p>
                        <p className="text-slate-400 mt-0.5">
                          Đã dùng{' '}
                          <span
                            className={`font-semibold ${al.exceeded ? 'text-rose-400' : 'text-amber-400'
                              }`}
                          >
                            {al.percentage}%
                          </span>{' '}
                          ({formatVnd(al.spentAmount)} / {formatVnd(al.amountLimit)})
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* 2. MY WALLETS CARD */}
      <section className="bento-card overflow-hidden">
        <div className="px-4 sm:px-5 py-3.5 flex items-center justify-between border-b border-white/[0.05]">
          <h2 className="text-sm font-bold text-white">Ví của tôi</h2>
          <Link
            to="/wallets"
            className="text-xs font-semibold text-[#22c55e] hover:text-emerald-300 transition-colors"
          >
            Xem tất cả
          </Link>
        </div>

        <div className="divide-y divide-white/[0.04]">
          {data.wallets.slice(0, 4).map((w) => (
            <Link
              key={w.id}
              to={`/transactions?walletId=${w.id}`}
              className="px-4 sm:px-5 py-3.5 flex items-center justify-between hover:bg-white/[0.03] transition-colors"
            >
              <div className="flex items-center gap-3 min-w-0">
                <CategoryIcon icon={w.icon} color={w.color} size="md" />
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-white truncate">{w.name}</p>
                  {w.bankCode && (
                    <p className="text-[11px] text-slate-400">
                      {w.bankCode}
                      {w.accountNumber ? ` • ${w.accountNumber}` : ''}
                    </p>
                  )}
                </div>
              </div>
              <span
                className={`text-sm font-bold mono-num ${w.balance < 0 ? 'text-rose-400' : 'text-white'
                  }`}
              >
                {formatVnd(w.balance, hideBalance)}
              </span>
            </Link>
          ))}

          {data.wallets.length <= 1 && (
            <Link
              to="/wallets"
              className="px-4 sm:px-5 py-3 flex items-center gap-2.5 text-xs font-medium text-slate-400 hover:text-[#22c55e] transition-colors"
            >
              <Plus className="w-4 h-4 text-[#22c55e]" />
              <span>Thêm ví Ngân hàng, MoMo hoặc Thẻ tín dụng</span>
            </Link>
          )}
        </div>
      </section>

      {/* 3. REPORT THIS MONTH SECTION */}
      <section className="space-y-2.5">
        <div className="flex items-center justify-between px-0.5">
          <h2 className="text-sm font-semibold text-slate-300">Báo cáo tháng này</h2>
          <Link
            to="/reports"
            className="text-xs font-semibold text-[#22c55e] hover:text-emerald-300 transition-colors"
          >
            Xem báo cáo
          </Link>
        </div>

        <div className="bento-card p-4 sm:p-5">
          {/* Two Top Tabs: Total spent | Total income */}
          <div className="grid grid-cols-2 border-b border-white/[0.07]">
            <button
              type="button"
              onClick={() => setReportMetric('SPENT')}
              className={`pb-3 text-center border-b-2 transition-all ${reportMetric === 'SPENT'
                  ? 'border-rose-500'
                  : 'border-transparent opacity-60 hover:opacity-90'
                }`}
            >
              <p className="text-xs text-slate-400">Tổng đã chi</p>
              <div className="mt-0.5">
                <AnimatedCurrency
                  value={data.monthSpent}
                  hideBalance={hideBalance}
                  className="text-base sm:text-lg font-bold text-rose-400"
                />
              </div>
            </button>

            <button
              type="button"
              onClick={() => setReportMetric('INCOME')}
              className={`pb-3 text-center border-b-2 transition-all ${reportMetric === 'INCOME'
                  ? 'border-sky-400'
                  : 'border-transparent opacity-60 hover:opacity-90'
                }`}
            >
              <p className="text-xs text-slate-400">Tổng thu nhập</p>
              <div className="mt-0.5">
                <AnimatedCurrency
                  value={data.monthIncome}
                  hideBalance={hideBalance}
                  className="text-base sm:text-lg font-bold text-sky-400"
                />
              </div>
            </button>
          </div>

          {/* Chart Body */}
          <div className="pt-5 pb-3">
            {!hasChartData ? (
              /* Clean Empty Chart Grid (exact match to Screenshot 1) */
              <div className="relative h-44 border border-white/[0.06] rounded-xl flex flex-col justify-between p-4 bg-[#10141d]/50">
                <div className="border-b border-dashed border-white/[0.07] w-full" />
                <div className="border-b border-dashed border-white/[0.07] w-full" />
                <div className="border-b border-dashed border-white/[0.07] w-full" />
                <div className="border-b border-dashed border-white/[0.07] w-full" />
                <div className="absolute inset-0 flex flex-col items-center justify-center gap-2">
                  <p className="text-xs sm:text-sm text-slate-300 font-medium">
                    Nhập giao dịch để xem báo cáo
                  </p>
                  <button
                    type="button"
                    onClick={() =>
                      openTransactionModal({
                        mode: reportMetric === 'INCOME' ? 'INCOME' : 'EXPENSE',
                      })
                    }
                    className="px-3 py-1.5 rounded-xl bg-white/[0.06] hover:bg-white/[0.12] text-xs font-medium text-[#22c55e] transition-colors"
                  >
                    + Ghi giao dịch đầu tiên
                  </button>
                </div>
              </div>
            ) : chartView === 'TRENDING' ? (
              <TrendingSvgChart
                points={data.dailyTrend}
                metric={reportMetric}
              />
            ) : (
              <DailyBarsSvgChart
                points={data.dailyTrend}
                metric={reportMetric}
              />
            )}

            {/* Legend */}
            <div className="flex flex-wrap items-center gap-5 mt-4 text-xs text-slate-400">
              <div className="flex items-center gap-2">
                <span
                  className={`w-2.5 h-2.5 rounded-full ${reportMetric === 'SPENT' ? 'bg-rose-500' : 'bg-sky-400'
                    }`}
                />
                <span>Tháng này</span>
              </div>
              {chartView === 'TRENDING' && (
                <div className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-slate-500" />
                  <span>Trung bình 3 tháng trước</span>
                </div>
              )}
            </div>
          </div>

          {/* Bottom Carousel Switcher (< Trending report >) */}
          <div className="pt-3 border-t border-white/[0.05] flex flex-col items-center gap-2">
            <div className="w-full flex items-center justify-between px-2">
              <button
                type="button"
                onClick={() =>
                  setChartView((v) => (v === 'TRENDING' ? 'DAILY' : 'TRENDING'))
                }
                className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/[0.06]"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <span className="text-xs font-semibold text-[#22c55e]">
                {chartView === 'TRENDING'
                  ? 'Báo cáo xu hướng lũy kế'
                  : 'Biến động theo từng ngày'}
              </span>
              <button
                type="button"
                onClick={() =>
                  setChartView((v) => (v === 'TRENDING' ? 'DAILY' : 'TRENDING'))
                }
                className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/[0.06]"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
            <div className="flex items-center gap-1.5">
              <span
                className={`w-1.5 h-1.5 rounded-full ${chartView === 'TRENDING' ? 'bg-white' : 'bg-slate-600'
                  }`}
              />
              <span
                className={`w-1.5 h-1.5 rounded-full ${chartView === 'DAILY' ? 'bg-white' : 'bg-slate-600'
                  }`}
              />
            </div>
          </div>
        </div>
      </section>

      {/* 4. TOP SPENDING SECTION + RECENT TRANSACTIONS (Responsive 2-Col on Desktop) */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Top Spending */}
        <section className="space-y-2.5">
          <div className="flex items-center justify-between px-0.5">
            <h2 className="text-sm font-semibold text-slate-300">Chi tiêu nhiều nhất</h2>
            <Link
              to="/reports"
              className="text-xs font-semibold text-[#22c55e] hover:text-emerald-300"
            >
              Xem chi tiết
            </Link>
          </div>

          <div className="bento-card p-4 sm:p-5">
            {/* Week | Month Pill Switcher (exact match to Screenshot 2) */}
            <div className="grid grid-cols-2 p-1 rounded-full bg-[#0c1017] border border-white/[0.05] mb-4">
              <button
                type="button"
                onClick={() => setTopPeriod('WEEK')}
                className={`py-1.5 rounded-full text-xs font-semibold transition-all ${topPeriod === 'WEEK'
                    ? 'bg-slate-600/70 text-white shadow'
                    : 'text-slate-400 hover:text-slate-200'
                  }`}
              >
                Tuần này
              </button>
              <button
                type="button"
                onClick={() => setTopPeriod('MONTH')}
                className={`py-1.5 rounded-full text-xs font-semibold transition-all ${topPeriod === 'MONTH'
                    ? 'bg-slate-600/70 text-white shadow'
                    : 'text-slate-400 hover:text-slate-200'
                  }`}
              >
                Tháng này
              </button>
            </div>

            {topItems.length === 0 ? (
              <div className="py-10 text-center text-xs text-slate-400">
                Chưa có khoản chi tiêu nào trong{' '}
                {topPeriod === 'WEEK' ? 'tuần này' : 'tháng này'}.
              </div>
            ) : (
              <div className="space-y-3.5">
                {topItems.slice(0, 5).map((item) => (
                  <div key={item.categoryId} className="flex items-center gap-3">
                    <CategoryIcon icon={item.icon} color={item.color} size="md" />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between gap-2 mb-1">
                        <span className="text-sm font-medium text-slate-100 truncate">
                          {item.categoryName}
                        </span>
                        <span className="text-xs font-bold mono-num text-rose-400 shrink-0">
                          {item.percentage}%
                        </span>
                      </div>
                      <div className="flex items-center justify-between gap-3">
                        <div className="flex-1 h-1.5 rounded-full bg-white/[0.06] overflow-hidden">
                          <div
                            className="h-full rounded-full transition-all duration-500"
                            style={{
                              width: `${Math.min(100, item.percentage)}%`,
                              backgroundColor: item.color,
                            }}
                          />
                        </div>
                        <span className="text-[11px] mono-num text-slate-400 shrink-0">
                          {formatVnd(item.amount, hideBalance)}
                        </span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </section>

        {/* Recent Transactions */}
        <section className="space-y-2.5">
          <div className="flex items-center justify-between px-0.5">
            <h2 className="text-sm font-semibold text-slate-300">Giao dịch gần đây</h2>
            <Link
              to="/transactions"
              className="text-xs font-semibold text-[#22c55e] hover:text-emerald-300"
            >
              Xem tất cả
            </Link>
          </div>

          <div className="bento-card overflow-hidden">
            {data.recentTransactions.length === 0 ? (
              <div className="py-12 px-4 text-center">
                <p className="text-xs sm:text-sm text-slate-400">
                  Giao dịch vừa ghi chép sẽ hiển thị tại đây 🙌
                </p>
              </div>
            ) : (
              <div className="divide-y divide-white/[0.05]">
                {data.recentTransactions.map((tx: PersonalTransaction) => (
                  <button
                    key={tx.id}
                    type="button"
                    onClick={() => openTransactionModal({ editTransaction: tx })}
                    className="w-full px-4 py-3.5 flex items-center justify-between gap-3 hover:bg-white/[0.03] text-left transition-colors"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <CategoryIcon
                        icon={
                          tx.type === 'TRANSFER'
                            ? 'transfer'
                            : tx.categoryIcon || tx.walletIcon
                        }
                        color={
                          tx.type === 'TRANSFER'
                            ? '#38bdf8'
                            : tx.categoryColor || tx.walletColor
                        }
                        size="md"
                      />
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-slate-100 truncate">
                          {tx.description || tx.categoryName || 'Giao dịch'}
                        </p>
                        <p className="text-[11px] text-slate-400 truncate mt-0.5">
                          {tx.walletName}
                          {tx.type === 'TRANSFER' && tx.toWalletName
                            ? ` → ${tx.toWalletName}`
                            : tx.categoryName
                              ? ` • ${tx.categoryName}`
                              : ''}{' '}
                          •{' '}
                          {new Date(tx.transactionDate).toLocaleDateString('vi-VN', {
                            day: '2-digit',
                            month: '2-digit',
                          })}
                        </p>
                      </div>
                    </div>

                    <span
                      className={`text-sm font-bold mono-num shrink-0 ${tx.type === 'INCOME'
                          ? 'text-[#22c55e]'
                          : tx.type === 'TRANSFER'
                            ? 'text-sky-400'
                            : 'text-rose-400'
                        }`}
                    >
                      {formatSignedVnd(tx.amount, tx.type, hideBalance)}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}

/* ============================================================================
   SVG TRENDING CUMULATIVE CHART (Current Month vs Previous 3-Month Average)
   ============================================================================ */
function TrendingSvgChart({
  points,
  metric,
}: {
  points: DailyTrendPoint[];
  metric: 'SPENT' | 'INCOME';
}) {
  if (points.length === 0) return null;

  const todayDay = new Date().getDate();
  const width = 560;
  const height = 175;
  const padL = 8;
  const padR = 46;
  const padT = 12;
  const padB = 22;
  const plotW = width - padL - padR;
  const plotH = height - padT - padB;

  const curValues = points.map((p) =>
    metric === 'SPENT' ? p.cumulativeSpent : p.cumulativeIncome
  );
  const avgValues = points.map((p) =>
    metric === 'SPENT'
      ? p.prev3MonthAvgCumulativeSpent
      : p.prev3MonthAvgCumulativeIncome
  );

  const maxVal = Math.max(1, ...curValues, ...avgValues);
  const xFor = (idx: number) =>
    padL + (points.length > 1 ? (idx / (points.length - 1)) * plotW : 0);
  const yFor = (val: number) => padT + plotH - (val / maxVal) * plotH;

  // Draw current month curve only up to today's day of month
  const activeCount = Math.min(points.length, Math.max(1, todayDay));
  const curPath = curValues
    .slice(0, activeCount)
    .map((v, i) => `${i === 0 ? 'M' : 'L'} ${xFor(i).toFixed(1)} ${yFor(v).toFixed(1)}`)
    .join(' ');

  const areaPath = `${curPath} L ${xFor(activeCount - 1).toFixed(1)} ${(
    padT + plotH
  ).toFixed(1)} L ${xFor(0).toFixed(1)} ${(padT + plotH).toFixed(1)} Z`;

  const avgPath = avgValues
    .map((v, i) => `${i === 0 ? 'M' : 'L'} ${xFor(i).toFixed(1)} ${yFor(v).toFixed(1)}`)
    .join(' ');

  const strokeColor = metric === 'SPENT' ? '#f43f5e' : '#38bdf8';
  const gradId = metric === 'SPENT' ? 'spentGrad' : 'incomeGrad';

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-44 overflow-visible">
      <defs>
        <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={strokeColor} stopOpacity="0.28" />
          <stop offset="100%" stopColor={strokeColor} stopOpacity="0.0" />
        </linearGradient>
      </defs>

      {/* Grid lines */}
      {[0, 0.5, 1].map((ratio, i) => {
        const y = padT + plotH * (1 - ratio);
        return (
          <g key={i}>
            <line
              x1={padL}
              y1={y}
              x2={padL + plotW}
              y2={y}
              stroke="rgba(255,255,255,0.07)"
              strokeDasharray="3 3"
            />
            <text
              x={padL + plotW + 6}
              y={y + 3}
              fill="#64748b"
              fontSize="10"
              className="mono-num"
            >
              {formatCompactVnd(maxVal * ratio)}
            </text>
          </g>
        );
      })}

      {/* 3-Month Avg Dashed Line */}
      <path
        d={avgPath}
        fill="none"
        stroke="#64748b"
        strokeWidth="1.75"
        strokeDasharray="4 4"
      />

      {/* Current Month Area + Solid Line */}
      <path d={areaPath} fill={`url(#${gradId})`} />
      <path
        d={curPath}
        fill="none"
        stroke={strokeColor}
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* Current Day Dot */}
      {activeCount > 0 && (
        <circle
          cx={xFor(activeCount - 1)}
          cy={yFor(curValues[activeCount - 1])}
          r="4"
          fill={strokeColor}
          stroke="#141923"
          strokeWidth="2"
        />
      )}

      {/* X-axis Labels */}
      {[0, Math.floor(points.length / 2), points.length - 1].map((idx) => (
        <text
          key={idx}
          x={xFor(idx)}
          y={height - 4}
          fill="#64748b"
          fontSize="10"
          textAnchor={idx === 0 ? 'start' : idx === points.length - 1 ? 'end' : 'middle'}
        >
          Ngày {points[idx].day}
        </text>
      ))}
    </svg>
  );
}

function DailyBarsSvgChart({
  points,
  metric,
}: {
  points: DailyTrendPoint[];
  metric: 'SPENT' | 'INCOME';
}) {
  if (points.length === 0) return null;
  const width = 560;
  const height = 175;
  const padL = 8;
  const padR = 46;
  const padT = 12;
  const padB = 22;
  const plotW = width - padL - padR;
  const plotH = height - padT - padB;

  const vals = points.map((p) => (metric === 'SPENT' ? p.dailySpent : p.dailyIncome));
  const maxVal = Math.max(1, ...vals);
  const barW = Math.max(4, plotW / points.length - 3);
  const barColor = metric === 'SPENT' ? '#f43f5e' : '#38bdf8';

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-44">
      {[0, 0.5, 1].map((ratio, i) => {
        const y = padT + plotH * (1 - ratio);
        return (
          <g key={i}>
            <line
              x1={padL}
              y1={y}
              x2={padL + plotW}
              y2={y}
              stroke="rgba(255,255,255,0.07)"
              strokeDasharray="3 3"
            />
            <text
              x={padL + plotW + 6}
              y={y + 3}
              fill="#64748b"
              fontSize="10"
              className="mono-num"
            >
              {formatCompactVnd(maxVal * ratio)}
            </text>
          </g>
        );
      })}

      {vals.map((v, i) => {
        const h = (v / maxVal) * plotH;
        const x = padL + (i / points.length) * plotW + 1.5;
        const y = padT + plotH - h;
        return (
          <rect
            key={i}
            x={x}
            y={y}
            width={barW}
            height={Math.max(v > 0 ? 2 : 0, h)}
            rx="2"
            fill={barColor}
            opacity={v > 0 ? 0.9 : 0.15}
          />
        );
      })}

      {[0, Math.floor(points.length / 2), points.length - 1].map((idx) => (
        <text
          key={idx}
          x={padL + (idx / points.length) * plotW}
          y={height - 4}
          fill="#64748b"
          fontSize="10"
        >
          Ngày {points[idx].day}
        </text>
      ))}
    </svg>
  );
}

