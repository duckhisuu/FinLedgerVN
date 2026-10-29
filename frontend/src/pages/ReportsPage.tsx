import { useEffect, useState } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  Loader2,
  TrendingUp,
  TrendingDown,
  PiggyBank,
} from 'lucide-react';
import {
  FinancialReports,
  TopSpendingItem,
  financeApi,
} from '../api/client';
import { useAuth } from '../context/AuthContext';
import { CategoryIcon } from '../components/CategoryIcon';
import {
  formatCompactVnd,
  formatVnd,
  formatYearMonthLabel,
  getCurrentYearMonth,
  shiftYearMonth,
} from '../utils/format';

export function ReportsPage() {
  const { hideBalance, dataVersion } = useAuth();

  const [month, setMonth] = useState(getCurrentYearMonth());
  const [reports, setReports] = useState<FinancialReports | null>(null);
  const [loading, setLoading] = useState(true);

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

  const maxHistoryVal = reports
    ? Math.max(
        1,
        ...reports.monthlyHistory.flatMap((m) => [m.income, m.expense])
      )
    : 1;

  return (
    <div className="space-y-5 pb-4">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-white">
            Báo cáo & Phân tích tài chính
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Cái nhìn toàn diện về thu nhập, cơ cấu chi tiêu và tỷ lệ tiết kiệm
          </p>
        </div>

        {/* Month Selector */}
        <div className="flex items-center gap-2 bg-[#141923] border border-white/[0.06] rounded-xl px-2 py-1.5">
          <button
            onClick={() => setMonth((m) => shiftYearMonth(m, -1))}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/[0.06]"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
          <span className="text-xs sm:text-sm font-bold text-white min-w-[140px] text-center">
            {formatYearMonthLabel(month)}
          </span>
          <button
            onClick={() => setMonth((m) => shiftYearMonth(m, 1))}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/[0.06]"
          >
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>

      {loading && !reports ? (
        <div className="py-16 flex justify-center">
          <Loader2 className="w-6 h-6 animate-spin text-[#22c55e]" />
        </div>
      ) : reports ? (
        <>
          {/* KPI Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div className="bg-[#141923] border border-white/[0.06] rounded-2xl p-4 flex items-center gap-3.5">
              <div className="w-11 h-11 rounded-2xl bg-emerald-500/15 text-[#22c55e] flex items-center justify-center shrink-0">
                <TrendingUp className="w-5 h-5" />
              </div>
              <div>
                <p className="text-xs text-slate-400">Tổng thu nhập tháng</p>
                <p className="text-lg font-extrabold mono-num text-[#22c55e] mt-0.5">
                  {formatVnd(reports.totalIncome, hideBalance)}
                </p>
              </div>
            </div>

            <div className="bg-[#141923] border border-white/[0.06] rounded-2xl p-4 flex items-center gap-3.5">
              <div className="w-11 h-11 rounded-2xl bg-rose-500/15 text-rose-400 flex items-center justify-center shrink-0">
                <TrendingDown className="w-5 h-5" />
              </div>
              <div>
                <p className="text-xs text-slate-400">Tổng chi tiêu tháng</p>
                <p className="text-lg font-extrabold mono-num text-rose-400 mt-0.5">
                  {formatVnd(reports.totalExpense, hideBalance)}
                </p>
              </div>
            </div>

            <div className="bg-[#141923] border border-white/[0.06] rounded-2xl p-4 flex items-center gap-3.5">
              <div className="w-11 h-11 rounded-2xl bg-sky-500/15 text-sky-400 flex items-center justify-center shrink-0">
                <PiggyBank className="w-5 h-5" />
              </div>
              <div>
                <p className="text-xs text-slate-400">
                  Tiết kiệm ròng ({reports.savingsRatePercent}%)
                </p>
                <p
                  className={`text-lg font-extrabold mono-num mt-0.5 ${
                    reports.netSavings >= 0 ? 'text-sky-400' : 'text-rose-400'
                  }`}
                >
                  {reports.netSavings >= 0 ? '+' : ''}
                  {formatVnd(reports.netSavings, hideBalance)}
                </p>
              </div>
            </div>
          </div>

          {/* 6-Month Cashflow Comparison */}
          <section className="bg-[#141923] border border-white/[0.06] rounded-2xl p-5 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-sm font-bold text-white">
                Dòng tiền Thu - Chi 6 tháng gần nhất
              </h2>
              <div className="flex items-center gap-4 text-xs text-slate-400">
                <span className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-sm bg-[#22c55e]" /> Thu nhập
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-sm bg-rose-500" /> Chi tiêu
                </span>
              </div>
            </div>

            <div className="grid grid-cols-6 gap-2 sm:gap-4 items-end h-48 pt-6 border-b border-white/[0.06] pb-2">
              {reports.monthlyHistory.map((item) => {
                const incH = Math.round((item.income / maxHistoryVal) * 140);
                const expH = Math.round((item.expense / maxHistoryVal) * 140);
                const isSelected = item.month === month;
                return (
                  <button
                    key={item.month}
                    type="button"
                    onClick={() => setMonth(item.month)}
                    className={`flex flex-col items-center gap-2 group rounded-xl py-1 transition-colors ${
                      isSelected ? 'bg-white/[0.04]' : 'hover:bg-white/[0.02]'
                    }`}
                  >
                    <div className="flex items-end gap-1 sm:gap-1.5 h-[140px]">
                      <div
                        title={`Thu: ${formatVnd(item.income)}`}
                        className="w-3 sm:w-5 rounded-t-md bg-[#22c55e] transition-all duration-500"
                        style={{ height: `${Math.max(item.income > 0 ? 4 : 1, incH)}px` }}
                      />
                      <div
                        title={`Chi: ${formatVnd(item.expense)}`}
                        className="w-3 sm:w-5 rounded-t-md bg-rose-500 transition-all duration-500"
                        style={{ height: `${Math.max(item.expense > 0 ? 4 : 1, expH)}px` }}
                      />
                    </div>
                    <div className="text-center">
                      <span
                        className={`text-[11px] font-semibold block ${
                          isSelected ? 'text-[#22c55e]' : 'text-slate-400'
                        }`}
                      >
                        {item.label}
                      </span>
                      <span className="text-[10px] mono-num text-slate-500 hidden sm:block">
                        {item.net >= 0 ? '+' : ''}
                        {formatCompactVnd(item.net)}
                      </span>
                    </div>
                  </button>
                );
              })}
            </div>
          </section>

          {/* Expense & Income Category Breakdown */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
            <CategoryBreakdownCard
              title="Cơ cấu chi tiêu theo danh mục"
              emptyText="Chưa có khoản chi nào trong tháng này."
              items={reports.expenseByCategory}
              hideBalance={hideBalance}
            />
            <CategoryBreakdownCard
              title="Cơ cấu nguồn thu nhập"
              emptyText="Chưa có khoản thu nào trong tháng này."
              items={reports.incomeByCategory}
              hideBalance={hideBalance}
            />
          </div>
        </>
      ) : null}
    </div>
  );
}

function CategoryBreakdownCard({
  title,
  emptyText,
  items,
  hideBalance,
}: {
  title: string;
  emptyText: string;
  items: TopSpendingItem[];
  hideBalance: boolean;
}) {
  return (
    <section className="bg-[#141923] border border-white/[0.06] rounded-2xl p-5 space-y-4">
      <h3 className="text-sm font-bold text-white">{title}</h3>
      {items.length === 0 ? (
        <p className="text-xs text-slate-400 py-10 text-center">{emptyText}</p>
      ) : (
        <div className="space-y-3.5">
          {items.map((it) => (
            <div key={it.categoryId} className="flex items-center gap-3">
              <CategoryIcon icon={it.icon} color={it.color} size="md" />
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between text-xs mb-1">
                  <span className="font-semibold text-slate-100 truncate">
                    {it.categoryName}
                  </span>
                  <span className="mono-num font-bold text-white">
                    {formatVnd(it.amount, hideBalance)}{' '}
                    <span className="text-slate-400 font-normal">({it.percentage}%)</span>
                  </span>
                </div>
                <div className="h-2 rounded-full bg-[#0c1017] overflow-hidden">
                  <div
                    className="h-full rounded-full transition-all duration-500"
                    style={{
                      width: `${Math.min(100, it.percentage)}%`,
                      backgroundColor: it.color,
                    }}
                  />
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

