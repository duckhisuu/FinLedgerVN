import React, { useEffect, useState } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  Plus,
  Loader2,
  Trash2,
  X,
  CalendarClock,
  Sparkles,
} from 'lucide-react';
import {
  BudgetsOverview,
  Category,
  financeApi,
} from '../api/client';
import { useAuth } from '../context/AuthContext';
import { CategoryIcon } from '../components/CategoryIcon';
import { AnimatedCurrency } from '../components/ui/AnimatedCurrency';
import { RichEmptyState } from '../components/ui/RichEmptyState';
import {
  formatVnd,
  formatYearMonthLabel,
  getCurrentYearMonth,
  shiftYearMonth,
} from '../utils/format';

export function BudgetsPage() {
  const { hideBalance, dataVersion, triggerDataRefresh } = useAuth();

  const [month, setMonth] = useState(getCurrentYearMonth());
  const [overview, setOverview] = useState<BudgetsOverview | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);

  // Modal state
  const [showModal, setShowModal] = useState(false);
  const [selectedCatId, setSelectedCatId] = useState<string>('');
  const [limitInput, setLimitInput] = useState('');
  const [noteInput, setNoteInput] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    Promise.all([financeApi.getBudgets(month), financeApi.getCategories()])
      .then(([bRes, cRes]) => {
        if (!active) return;
        setOverview(bRes);
        setCategories(cRes.filter((c) => c.type === 'EXPENSE'));
      })
      .catch((err) => console.error(err))
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [month, dataVersion]);

  const numericLimit = Number(limitInput.replace(/\D/g, '')) || 0;

  const handleSaveBudget = async (e: React.FormEvent) => {
    e.preventDefault();
    if (numericLimit <= 0) {
      setError('Vui lòng nhập hạn mức lớn hơn 0 ₫.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await financeApi.upsertBudget({
        categoryId: selectedCatId || null,
        monthYear: month,
        amountLimit: numericLimit,
        note: noteInput.trim() || undefined,
      });
      setShowModal(false);
      setLimitInput('');
      setNoteInput('');
      triggerDataRefresh();
    } catch (err: any) {
      setError(err.message || 'Không thể lưu ngân sách.');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await financeApi.deleteBudget(id);
      triggerDataRefresh();
    } catch (err: any) {
      alert(err.message);
    }
  };

  const totalPct =
    overview && overview.totalBudgetLimit > 0
      ? Math.min(100, Math.round((overview.totalSpentThisMonth / overview.totalBudgetLimit) * 100))
      : 0;

  return (
    <div className="space-y-5 pb-4">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-white">Ngân sách chi tiêu</h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Thiết lập hạn mức để kiểm soát chi tiêu khoa học mỗi tháng
          </p>
        </div>

        <button
          onClick={() => {
            setSelectedCatId('');
            setLimitInput('');
            setNoteInput('');
            setError(null);
            setShowModal(true);
          }}
          className="px-4 py-2.5 rounded-xl bg-[#22c55e] hover:bg-[#1eb053] text-slate-950 font-semibold text-xs sm:text-sm flex items-center gap-1.5 shadow-lg shadow-emerald-500/20"
        >
          <Plus className="w-4 h-4 stroke-[2.5]" />
          <span>Tạo ngân sách</span>
        </button>
      </div>

      {/* Month Switcher */}
      <div className="flex items-center justify-center gap-3 bg-[#141923] border border-white/[0.06] rounded-2xl p-3">
        <button
          onClick={() => setMonth((m) => shiftYearMonth(m, -1))}
          className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/[0.06]"
        >
          <ChevronLeft className="w-4 h-4" />
        </button>
        <span className="text-sm font-bold text-white min-w-[160px] text-center">
          {formatYearMonthLabel(month)}
        </span>
        <button
          onClick={() => setMonth((m) => shiftYearMonth(m, 1))}
          className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/[0.06]"
        >
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>

      {loading ? (
        <div className="py-16 flex justify-center">
          <Loader2 className="w-6 h-6 animate-spin text-[#22c55e]" />
        </div>
      ) : overview && overview.budgets.length > 0 ? (
        <>
          {/* Summary Card */}
          <div className="bento-card p-5 sm:p-6 space-y-4">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div>
                <p className="text-xs text-slate-400">Số tiền còn có thể chi</p>
                <div className="mt-1">
                  <AnimatedCurrency
                    value={overview.totalRemaining}
                    hideBalance={hideBalance}
                    className={`text-2xl sm:text-3xl font-extrabold ${
                      overview.totalRemaining < 0 ? 'text-rose-400' : 'text-emerald-400'
                    }`}
                  />
                </div>
              </div>
              <div className="text-right">
                <p className="text-xs text-slate-400">Tổng ngân sách</p>
                <div className="mt-0.5">
                  <AnimatedCurrency
                    value={overview.totalBudgetLimit}
                    hideBalance={hideBalance}
                    className="text-sm font-bold text-white"
                  />
                </div>
              </div>
            </div>

            {/* Progress Bar */}
            <div className="space-y-1.5">
              <div className="h-3 rounded-full bg-[#0c1017] overflow-hidden">
                <div
                  className={`h-full rounded-full transition-all duration-500 ${
                    overview.totalRemaining < 0
                      ? 'bg-rose-500'
                      : totalPct >= 80
                        ? 'bg-amber-400'
                        : 'bg-[#22c55e]'
                  }`}
                  style={{ width: `${totalPct}%` }}
                />
              </div>
              <div className="flex justify-between text-xs text-slate-400">
                <span className="flex items-center gap-1">
                  <span>Đã chi:</span>
                  <AnimatedCurrency
                    value={overview.totalSpentThisMonth}
                    hideBalance={hideBalance}
                    className="text-slate-200 font-bold"
                  />
                </span>
                <span className="mono-num">{totalPct}%</span>
              </div>
            </div>

            {/* Scientific Daily Spend Recommendation */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 border-t border-white/[0.05]">
              <div className="flex items-center gap-3 p-3 rounded-xl bg-[#0c1017]">
                <CalendarClock className="w-5 h-5 text-sky-400 shrink-0" />
                <div>
                  <p className="text-[11px] text-slate-400">Thời gian còn lại</p>
                  <p className="text-sm font-bold text-white">
                    {overview.daysRemaining} ngày
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-3 p-3 rounded-xl bg-[#0c1017]">
                <Sparkles className="w-5 h-5 text-[#22c55e] shrink-0" />
                <div>
                  <p className="text-[11px] text-slate-400">Khuyến nghị chi mỗi ngày</p>
                  <div className="mt-0.5 flex items-center">
                    <AnimatedCurrency
                      value={overview.recommendedDailySpend}
                      hideBalance={hideBalance}
                      className="text-sm font-bold text-white"
                    />
                    <span className="text-xs text-slate-400 ml-1">/ ngày</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Individual Budgets List */}
          <div className="space-y-3">
            {overview.budgets.map((b) => {
              const barColor = b.overBudget
                ? '#f43f5e'
                : b.percentage >= 80
                  ? '#f59e0b'
                  : b.categoryColor || '#22c55e';
              return (
                <div
                  key={b.id}
                  className="bento-card p-4 sm:p-5 space-y-3"
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-3 min-w-0">
                      <CategoryIcon
                        icon={b.categoryIcon}
                        color={b.categoryColor}
                        size="md"
                      />
                      <div className="min-w-0">
                        <p className="text-sm font-bold text-white truncate">
                          {b.categoryName}
                        </p>
                        <p className="text-xs text-slate-400 mono-num mt-0.5">
                          {formatVnd(b.spentAmount, hideBalance)} /{' '}
                          {formatVnd(b.amountLimit, hideBalance)}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => {
                          setSelectedCatId(b.categoryId || '');
                          setLimitInput(String(Math.round(b.amountLimit)));
                          setNoteInput(b.note || '');
                          setShowModal(true);
                        }}
                        className="px-2.5 py-1 rounded-lg bg-white/[0.05] hover:bg-white/[0.1] text-xs text-slate-300"
                      >
                        Sửa
                      </button>
                      <button
                        onClick={() => handleDelete(b.id)}
                        className="p-1.5 rounded-lg text-slate-500 hover:text-rose-400"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>

                  <div className="h-2 rounded-full bg-[#0c1017] overflow-hidden">
                    <div
                      className="h-full rounded-full transition-all duration-500"
                      style={{
                        width: `${Math.min(100, b.percentage)}%`,
                        backgroundColor: barColor,
                      }}
                    />
                  </div>

                  <div className="flex items-center justify-between text-xs">
                    <span
                      className={
                        b.overBudget
                          ? 'text-rose-400 font-semibold'
                          : 'text-slate-400'
                      }
                    >
                      {b.overBudget
                        ? `Vượt ngân sách ${formatVnd(Math.abs(b.remainingAmount), hideBalance)}`
                        : `Còn lại ${formatVnd(b.remainingAmount, hideBalance)}`}
                    </span>
                    <span className="mono-num font-semibold text-slate-300">
                      {b.percentage}%
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </>
      ) : (
        <div className="bento-card py-10 px-4 text-center">
          <RichEmptyState
            variant="donut"
            title={`Chưa có ngân sách nào cho ${formatYearMonthLabel(month)}`}
            description="Đặt hạn mức cho từng danh mục hoặc tổng ngân sách để kiểm soát chi tiêu và nhận gợi ý hạn mức an toàn mỗi ngày"
            actionLabel="+ Thiết lập ngân sách ngay"
            onAction={() => setShowModal(true)}
          />
        </div>
      )}

      {/* Create / Edit Budget Modal */}
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/75 backdrop-blur-sm p-0 sm:p-4">
          <div className="w-full max-w-md bg-[#141923] border border-white/[0.08] rounded-t-3xl sm:rounded-3xl p-5 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-white">Thiết lập ngân sách</h3>
              <button
                onClick={() => setShowModal(false)}
                className="p-1.5 rounded-full text-slate-400 hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {error && (
              <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-xs text-rose-300">
                {error}
              </div>
            )}

            <form onSubmit={handleSaveBudget} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">
                  Phạm vi ngân sách
                </label>
                <select
                  value={selectedCatId}
                  onChange={(e) => setSelectedCatId(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white focus:outline-none focus:border-[#22c55e]"
                >
                  <option value="">Tổng toàn bộ chi tiêu trong tháng</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      Danh mục: {c.name}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">
                  Hạn mức tối đa (VNĐ)
                </label>
                <input
                  type="text"
                  inputMode="numeric"
                  required
                  placeholder="Ví dụ: 5.000.000"
                  value={numericLimit > 0 ? numericLimit.toLocaleString('vi-VN') : ''}
                  onChange={(e) => setLimitInput(e.target.value.replace(/\D/g, ''))}
                  className="w-full px-4 py-3 rounded-xl bg-[#0c1017] border border-white/10 text-lg font-bold mono-num text-white focus:outline-none focus:border-[#22c55e]"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">
                  Ghi chú (tuỳ chọn)
                </label>
                <input
                  type="text"
                  placeholder="Mục tiêu tiết kiệm..."
                  value={noteInput}
                  onChange={(e) => setNoteInput(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white focus:outline-none focus:border-[#22c55e]"
                />
              </div>

              <button
                type="submit"
                disabled={saving}
                className="w-full py-3 rounded-2xl bg-[#22c55e] hover:bg-[#1eb053] text-slate-950 font-bold text-sm"
              >
                {saving ? 'Đang lưu...' : 'Lưu ngân sách'}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

