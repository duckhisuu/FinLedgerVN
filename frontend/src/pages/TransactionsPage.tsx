import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  ChevronLeft,
  ChevronRight,
  Search,
  Plus,
  Loader2,
  Edit3,
  Trash2,
  X,
} from 'lucide-react';
import {
  PersonalTransaction,
  TransactionsFeed,
  Wallet,
  financeApi,
} from '../api/client';
import { useAuth } from '../context/AuthContext';
import { CategoryIcon } from '../components/CategoryIcon';
import {
  formatDateHeaderVi,
  formatSignedVnd,
  formatVnd,
  formatYearMonthLabel,
  getCurrentYearMonth,
  shiftYearMonth,
} from '../utils/format';

export function TransactionsPage() {
  const { hideBalance, dataVersion, openTransactionModal, triggerDataRefresh } =
    useAuth();
  const [searchParams] = useSearchParams();

  const [month, setMonth] = useState(getCurrentYearMonth());
  const [walletId, setWalletId] = useState<string>(
    () => searchParams.get('walletId') || ''
  );
  const [typeFilter, setTypeFilter] = useState<'ALL' | 'EXPENSE' | 'INCOME' | 'TRANSFER'>('ALL');
  const [search, setSearch] = useState('');
  const [includeCancelled, setIncludeCancelled] = useState(false);

  const [wallets, setWallets] = useState<Wallet[]>([]);
  const [feed, setFeed] = useState<TransactionsFeed | null>(null);
  const [loading, setLoading] = useState(true);
  const [selectedTx, setSelectedTx] = useState<PersonalTransaction | null>(null);
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    financeApi
      .getWallets()
      .then((res) => setWallets(res.wallets))
      .catch(() => {});
  }, [dataVersion]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    financeApi
      .getTransactions({
        month,
        walletId: walletId || undefined,
        type: typeFilter,
        search: search.trim() || undefined,
        includeCancelled,
      })
      .then((res) => {
        if (active) setFeed(res);
      })
      .catch((err) => console.error(err))
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [month, walletId, typeFilter, search, includeCancelled, dataVersion]);

  // Group transactions by local calendar date
  const groupedDays = useMemo(() => {
    if (!feed) return [];
    const map = new Map<
      string,
      { label: string; isoSample: string; netDay: number; items: PersonalTransaction[] }
    >();

    for (const tx of feed.transactions) {
      const d = new Date(tx.transactionDate);
      const dayKey = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
        d.getDate()
      ).padStart(2, '0')}`;

      if (!map.has(dayKey)) {
        map.set(dayKey, {
          label: formatDateHeaderVi(tx.transactionDate),
          isoSample: tx.transactionDate,
          netDay: 0,
          items: [],
        });
      }
      const bucket = map.get(dayKey)!;
      bucket.items.push(tx);
      if (!tx.cancelled) {
        if (tx.type === 'INCOME') bucket.netDay += tx.amount;
        else if (tx.type === 'EXPENSE' || tx.type === 'RECEIPT_SCAN')
          bucket.netDay -= tx.amount;
      }
    }

    return Array.from(map.values());
  }, [feed]);

  const handleCancelTransaction = async (tx: PersonalTransaction) => {
    setCancelling(true);
    try {
      await financeApi.cancelTransaction(tx.id, 'Hủy từ sổ giao dịch');
      setSelectedTx(null);
      triggerDataRefresh();
    } catch (err: any) {
      alert(err.message || 'Không thể hủy giao dịch.');
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div className="space-y-5 pb-4">
      {/* Page Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-white">Sổ giao dịch</h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Theo dõi toàn bộ thu chi và luân chuyển giữa các ví
          </p>
        </div>
        <button
          onClick={() =>
            openTransactionModal({
              mode: 'EXPENSE',
              defaultWalletId: walletId || undefined,
            })
          }
          className="px-4 py-2.5 rounded-xl bg-[#22c55e] hover:bg-[#1eb053] text-slate-950 font-semibold text-xs sm:text-sm flex items-center gap-1.5 shadow-lg shadow-emerald-500/20"
        >
          <Plus className="w-4 h-4 stroke-[2.5]" />
          <span>Thêm giao dịch</span>
        </button>
      </div>

      {/* Filters Bar */}
      <div className="bg-[#141923] border border-white/[0.06] rounded-2xl p-4 space-y-3">
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
          {/* Month Navigator */}
          <div className="flex items-center justify-between sm:justify-start gap-2 bg-[#0c1017] border border-white/[0.06] rounded-xl px-2 py-1.5">
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

          {/* Wallet Filter + Search */}
          <div className="flex flex-1 flex-col sm:flex-row gap-2.5">
            <select
              value={walletId}
              onChange={(e) => setWalletId(e.target.value)}
              className="px-3.5 py-2 rounded-xl bg-[#0c1017] border border-white/10 text-xs sm:text-sm text-white focus:outline-none focus:border-[#22c55e]"
            >
              <option value="">Tất cả các ví</option>
              {wallets.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name}
                </option>
              ))}
            </select>

            <div className="relative flex-1">
              <Search className="w-4 h-4 text-slate-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Tìm theo ghi chú, danh mục..."
                autoFocus={searchParams.get('focusSearch') === '1'}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full pl-9 pr-3.5 py-2 rounded-xl bg-[#0c1017] border border-white/10 text-xs sm:text-sm text-white placeholder:text-slate-500 focus:outline-none focus:border-[#22c55e]"
              />
            </div>
          </div>
        </div>

        {/* Type Pills + Show Cancelled */}
        <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
          <div className="flex flex-wrap gap-1.5">
            {(
              [
                { id: 'ALL', label: 'Tất cả' },
                { id: 'EXPENSE', label: 'Khoản chi' },
                { id: 'INCOME', label: 'Khoản thu' },
                { id: 'TRANSFER', label: 'Chuyển ví' },
              ] as const
            ).map((t) => (
              <button
                key={t.id}
                onClick={() => setTypeFilter(t.id)}
                className={`px-3 py-1.5 rounded-full text-xs font-semibold transition-all ${
                  typeFilter === t.id
                    ? 'bg-[#22c55e] text-slate-950'
                    : 'bg-[#0c1017] text-slate-400 hover:text-white border border-white/[0.05]'
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>

          <label className="flex items-center gap-2 text-xs text-slate-400 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={includeCancelled}
              onChange={(e) => setIncludeCancelled(e.target.checked)}
              className="rounded border-white/20 bg-[#0c1017] text-[#22c55e] focus:ring-0"
            />
            <span>Hiện giao dịch đã hủy</span>
          </label>
        </div>
      </div>

      {/* Monthly Cashflow Summary Card (Money Lover Style) */}
      {feed && (
        <div className="bg-[#141923] border border-white/[0.06] rounded-2xl p-4 sm:p-5 grid grid-cols-3 gap-3 text-center">
          <div>
            <p className="text-[11px] sm:text-xs text-slate-400">Tiền vào</p>
            <p className="text-sm sm:text-base font-bold mono-num text-[#22c55e] mt-1">
              +{formatVnd(feed.totalIncome, hideBalance)}
            </p>
          </div>
          <div className="border-x border-white/[0.06]">
            <p className="text-[11px] sm:text-xs text-slate-400">Tiền ra</p>
            <p className="text-sm sm:text-base font-bold mono-num text-rose-400 mt-1">
              -{formatVnd(feed.totalExpense, hideBalance)}
            </p>
          </div>
          <div>
            <p className="text-[11px] sm:text-xs text-slate-400">Chênh lệch</p>
            <p
              className={`text-sm sm:text-base font-bold mono-num mt-1 ${
                feed.netCashflow >= 0 ? 'text-sky-400' : 'text-rose-400'
              }`}
            >
              {feed.netCashflow >= 0 ? '+' : ''}
              {formatVnd(feed.netCashflow, hideBalance)}
            </p>
          </div>
        </div>
      )}

      {/* Grouped Transactions Feed */}
      {loading ? (
        <div className="py-16 flex justify-center">
          <Loader2 className="w-6 h-6 animate-spin text-[#22c55e]" />
        </div>
      ) : groupedDays.length === 0 ? (
        <div className="bg-[#141923] border border-white/[0.06] rounded-2xl py-16 px-4 text-center space-y-3">
          <p className="text-sm text-slate-400">
            Không có giao dịch nào trong khoảng thời gian này.
          </p>
          <button
            onClick={() => openTransactionModal({ mode: 'EXPENSE' })}
            className="px-4 py-2 rounded-xl bg-white/[0.06] hover:bg-white/[0.1] text-xs font-semibold text-[#22c55e]"
          >
            + Ghi chép giao dịch ngay
          </button>
        </div>
      ) : (
        <div className="space-y-4">
          {groupedDays.map((group) => (
            <div
              key={group.label}
              className="bg-[#141923] border border-white/[0.06] rounded-2xl overflow-hidden"
            >
              {/* Day Header */}
              <div className="px-4 sm:px-5 py-2.5 bg-[#10141d] border-b border-white/[0.05] flex items-center justify-between">
                <span className="text-xs font-semibold text-slate-300">
                  {group.label}
                </span>
                <span
                  className={`text-xs font-bold mono-num ${
                    group.netDay >= 0 ? 'text-slate-300' : 'text-slate-400'
                  }`}
                >
                  {group.netDay > 0 ? '+' : ''}
                  {formatVnd(group.netDay, hideBalance)}
                </span>
              </div>

              {/* Day Items */}
              <div className="divide-y divide-white/[0.04]">
                {group.items.map((tx) => (
                  <button
                    key={tx.id}
                    type="button"
                    onClick={() => setSelectedTx(tx)}
                    className={`w-full px-4 sm:px-5 py-3.5 flex items-center justify-between gap-3 hover:bg-white/[0.03] text-left transition-colors ${
                      tx.cancelled ? 'opacity-50' : ''
                    }`}
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <CategoryIcon
                        icon={
                          tx.type === 'TRANSFER'
                            ? 'transfer'
                            : tx.type === 'ADJUSTMENT' || tx.type === 'OPENING_BALANCE'
                              ? 'adjustment'
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
                        <div className="flex items-center gap-2">
                          <p
                            className={`text-sm font-semibold text-white truncate ${
                              tx.cancelled ? 'line-through' : ''
                            }`}
                          >
                            {tx.description || tx.categoryName || 'Giao dịch'}
                          </p>
                          {tx.cancelled && (
                            <span className="px-1.5 py-0.5 rounded bg-slate-700/60 text-[10px] text-slate-300">
                              Đã hủy
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-slate-400 truncate mt-0.5">
                          {tx.walletName}
                          {tx.type === 'TRANSFER' && tx.toWalletName
                            ? ` → ${tx.toWalletName}`
                            : tx.categoryName
                              ? ` • ${tx.categoryName}`
                              : ''}
                        </p>
                      </div>
                    </div>

                    <span
                      className={`text-sm font-bold mono-num shrink-0 ${
                        tx.type === 'INCOME'
                          ? 'text-[#22c55e]'
                          : tx.type === 'TRANSFER'
                            ? 'text-sky-400'
                            : tx.type === 'ADJUSTMENT' || tx.type === 'OPENING_BALANCE'
                              ? 'text-slate-300'
                              : 'text-rose-400'
                      }`}
                    >
                      {formatSignedVnd(tx.amount, tx.type, hideBalance)}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Transaction Detail & Action Modal */}
      {selectedTx && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/75 backdrop-blur-sm p-0 sm:p-4">
          <div className="w-full max-w-md bg-[#141923] border border-white/[0.08] rounded-t-3xl sm:rounded-3xl p-5 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-white">Chi tiết giao dịch</h3>
              <button
                onClick={() => setSelectedTx(null)}
                className="p-1.5 rounded-full text-slate-400 hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-4 rounded-2xl bg-[#0c1017] border border-white/[0.05] text-center space-y-1">
              <p className="text-xs text-slate-400">
                {selectedTx.categoryName || selectedTx.type}
              </p>
              <p
                className={`text-2xl font-extrabold mono-num ${
                  selectedTx.type === 'INCOME'
                    ? 'text-[#22c55e]'
                    : selectedTx.type === 'TRANSFER'
                      ? 'text-sky-400'
                      : 'text-rose-400'
                }`}
              >
                {formatSignedVnd(selectedTx.amount, selectedTx.type)}
              </p>
              <p className="text-sm text-slate-200 font-medium pt-1">
                {selectedTx.description}
              </p>
            </div>

            <div className="space-y-2 text-xs text-slate-300">
              <div className="flex justify-between py-1.5 border-b border-white/[0.05]">
                <span className="text-slate-400">Ví thực hiện</span>
                <span className="font-semibold text-white">
                  {selectedTx.walletName}
                  {selectedTx.toWalletName ? ` → ${selectedTx.toWalletName}` : ''}
                </span>
              </div>
              <div className="flex justify-between py-1.5 border-b border-white/[0.05]">
                <span className="text-slate-400">Thời gian</span>
                <span className="font-medium">
                  {new Date(selectedTx.transactionDate).toLocaleString('vi-VN')}
                </span>
              </div>
              {selectedTx.breakdown && selectedTx.breakdown.length > 1 && (
                <div className="pt-2 space-y-1.5">
                  <span className="text-slate-400 block">Phân bổ danh mục:</span>
                  {selectedTx.breakdown.map((b, i) => (
                    <div key={i} className="flex justify-between text-slate-200">
                      <span>• {b.categoryName}</span>
                      <span className="mono-num">{formatVnd(b.amount)}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {!selectedTx.cancelled &&
              selectedTx.type !== 'OPENING_BALANCE' &&
              selectedTx.type !== 'ADJUSTMENT' && (
                <div className="grid grid-cols-2 gap-2.5 pt-2">
                  {selectedTx.type !== 'RECEIPT_SCAN' && (
                    <button
                      type="button"
                      onClick={() => {
                        const tx = selectedTx;
                        setSelectedTx(null);
                        openTransactionModal({ editTransaction: tx });
                      }}
                      className="py-2.5 rounded-xl bg-white/[0.07] hover:bg-white/[0.12] text-white text-xs font-semibold flex items-center justify-center gap-1.5"
                    >
                      <Edit3 className="w-4 h-4" />
                      <span>Chỉnh sửa</span>
                    </button>
                  )}
                  <button
                    type="button"
                    disabled={cancelling}
                    onClick={() => handleCancelTransaction(selectedTx)}
                    className={`py-2.5 rounded-xl bg-rose-500/15 hover:bg-rose-500/25 text-rose-400 text-xs font-semibold flex items-center justify-center gap-1.5 ${
                      selectedTx.type === 'RECEIPT_SCAN' ? 'col-span-2' : ''
                    }`}
                  >
                    <Trash2 className="w-4 h-4" />
                    <span>{cancelling ? 'Đang hủy...' : 'Hủy giao dịch'}</span>
                  </button>
                </div>
              )}
          </div>
        </div>
      )}
    </div>
  );
}

