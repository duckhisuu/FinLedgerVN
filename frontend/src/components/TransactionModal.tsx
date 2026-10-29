import React, { useEffect, useState } from 'react';
import {
  X,
  ArrowDownLeft,
  ArrowUpRight,
  ArrowLeftRight,
  Camera,
  Plus,
  Loader2,
  Trash2,
  Check,
} from 'lucide-react';
import {
  Category,
  Wallet,
  financeApi,
  ReceiptPreviewItem,
} from '../api/client';
import { useAuth } from '../context/AuthContext';
import { CategoryIcon, AVAILABLE_COLORS, AVAILABLE_ICONS } from './CategoryIcon';
import { formatVnd } from '../utils/format';

export function TransactionModal() {
  const { txModalPreset, closeTransactionModal, triggerDataRefresh } = useAuth();

  const [mode, setMode] = useState<'EXPENSE' | 'INCOME' | 'TRANSFER' | 'OCR'>('EXPENSE');
  const [wallets, setWallets] = useState<Wallet[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loadingInit, setLoadingInit] = useState(false);

  // Form states
  const [amountInput, setAmountInput] = useState('');
  const [walletId, setWalletId] = useState('');
  const [toWalletId, setToWalletId] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [description, setDescription] = useState('');
  const [txDate, setTxDate] = useState(() => new Date().toISOString().slice(0, 10));

  // Quick inline category creator
  const [showNewCat, setShowNewCat] = useState(false);
  const [newCatName, setNewCatName] = useState('');
  const [newCatIcon, setNewCatIcon] = useState('tag');
  const [newCatColor, setNewCatColor] = useState('#ef4444');

  // OCR Scan states
  const [ocrScanning, setOcrScanning] = useState(false);
  const [ocrMerchant, setOcrMerchant] = useState('');
  const [ocrItems, setOcrItems] = useState<
    Array<{ productName: string; lineAmount: number; categoryId: string }>
  >([]);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const editTx = txModalPreset?.editTransaction;

  useEffect(() => {
    if (!txModalPreset) return;
    setError(null);
    setShowNewCat(false);

    const init = async () => {
      setLoadingInit(true);
      try {
        const [walletRes, cats] = await Promise.all([
          financeApi.getWallets(),
          financeApi.getCategories(),
        ]);
        setWallets(walletRes.wallets);
        setCategories(cats);

        if (editTx) {
          const editMode =
            editTx.type === 'INCOME'
              ? 'INCOME'
              : editTx.type === 'TRANSFER'
                ? 'TRANSFER'
                : 'EXPENSE';
          setMode(editMode);
          setAmountInput(String(Math.round(editTx.amount)));
          setWalletId(editTx.walletId);
          setToWalletId(editTx.toWalletId || walletRes.wallets[1]?.id || '');
          setCategoryId(editTx.categoryId || '');
          setDescription(editTx.description || '');
          setTxDate(new Date(editTx.transactionDate).toISOString().slice(0, 10));
        } else {
          const initialMode = txModalPreset.mode || 'EXPENSE';
          setMode(initialMode);
          setAmountInput('');
          setDescription('');
          setTxDate(new Date().toISOString().slice(0, 10));
          setOcrItems([]);
          setOcrMerchant('');

          const defaultW =
            txModalPreset.defaultWalletId || walletRes.wallets[0]?.id || '';
          setWalletId(defaultW);
          const secondW =
            walletRes.wallets.find((w) => w.id !== defaultW)?.id || '';
          setToWalletId(secondW);

          const targetCatType = initialMode === 'INCOME' ? 'INCOME' : 'EXPENSE';
          const firstCat = cats.find((c) => c.type === targetCatType);
          setCategoryId(firstCat?.id || '');
        }
      } catch (err: any) {
        setError(err.message || 'Không thể tải danh sách ví và danh mục.');
      } finally {
        setLoadingInit(false);
      }
    };
    init();
  }, [txModalPreset, editTx]);

  // Keep category aligned when switching EXPENSE <-> INCOME
  useEffect(() => {
    if (mode !== 'EXPENSE' && mode !== 'INCOME') return;
    const filtered = categories.filter((c) => c.type === mode);
    if (!filtered.some((c) => c.id === categoryId)) {
      setCategoryId(filtered[0]?.id || '');
    }
  }, [mode, categories, categoryId]);

  if (!txModalPreset) return null;

  const numericAmount = Number(amountInput.replace(/\D/g, '')) || 0;
  const filteredCategories = categories.filter(
    (c) => c.type === (mode === 'INCOME' ? 'INCOME' : 'EXPENSE')
  );
  const expenseCategories = categories.filter((c) => c.type === 'EXPENSE');

  // Quick VND suggestions when user types 1-4 digits (e.g. "45" -> 45.000, 450.000)
  const quickSuggestions: number[] = [];
  if (numericAmount > 0 && numericAmount < 10000) {
    quickSuggestions.push(numericAmount * 1000);
    if (numericAmount < 1000) {
      quickSuggestions.push(numericAmount * 10000);
      quickSuggestions.push(numericAmount * 100000);
    }
  }

  const handleCreateInlineCategory = async () => {
    if (!newCatName.trim()) return;
    try {
      const catType = mode === 'INCOME' ? 'INCOME' : 'EXPENSE';
      const created = await financeApi.createCategory({
        name: newCatName.trim(),
        type: catType,
        icon: newCatIcon,
        color: newCatColor,
      });
      setCategories((prev) => [...prev, created]);
      setCategoryId(created.id);
      setNewCatName('');
      setShowNewCat(false);
    } catch (err: any) {
      setError(err.message);
    }
  };

  const handleReceiptFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setError(null);
    setOcrScanning(true);
    try {
      const preview = await financeApi.scanReceipt(file);
      setOcrMerchant(preview.merchantName || 'Hóa đơn mua sắm');
      const fallbackCatId = expenseCategories[0]?.id || '';
      setOcrItems(
        preview.items.map((it: ReceiptPreviewItem) => ({
          productName: it.productName,
          lineAmount: Math.round(it.lineAmount),
          categoryId: it.suggestedCategoryId || fallbackCatId,
        }))
      );
    } catch (err: any) {
      setError(err.message || 'Không thể đọc ảnh hóa đơn.');
    } finally {
      setOcrScanning(false);
      e.target.value = '';
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!walletId) {
      setError('Vui lòng chọn ví giao dịch.');
      return;
    }

    const isoDate = (() => {
      const now = new Date();
      const todayStr = now.toISOString().slice(0, 10);
      if (txDate === todayStr) return now.toISOString();
      return new Date(`${txDate}T12:00:00`).toISOString();
    })();

    setSubmitting(true);
    try {
      if (mode === 'OCR') {
        if (ocrItems.length === 0) {
          throw new Error('Vui lòng tải ảnh hóa đơn lên trước khi lưu.');
        }
        await financeApi.confirmReceipt({
          walletId,
          merchantName: ocrMerchant,
          transactionDate: isoDate,
          items: ocrItems,
        });
      } else {
        if (numericAmount <= 0) {
          throw new Error('Vui lòng nhập số tiền lớn hơn 0 ₫.');
        }
        if (mode === 'TRANSFER') {
          if (!toWalletId || toWalletId === walletId) {
            throw new Error('Vui lòng chọn ví đích khác với ví nguồn.');
          }
        } else if (!categoryId) {
          throw new Error('Vui lòng chọn danh mục.');
        }

        const payload = {
          type: mode,
          amount: numericAmount,
          walletId,
          categoryId: mode !== 'TRANSFER' ? categoryId : undefined,
          toWalletId: mode === 'TRANSFER' ? toWalletId : undefined,
          description: description.trim() || undefined,
          transactionDate: isoDate,
        };

        if (editTx) {
          await financeApi.updateTransaction(editTx.id, payload);
        } else {
          await financeApi.recordTransaction(payload);
        }
      }

      triggerDataRefresh();
      closeTransactionModal();
    } catch (err: any) {
      setError(err.message || 'Có lỗi xảy ra khi lưu giao dịch.');
    } finally {
      setSubmitting(false);
    }
  };

  const ocrTotal = ocrItems.reduce((sum, it) => sum + (Number(it.lineAmount) || 0), 0);

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/75 backdrop-blur-sm p-0 sm:p-4">
      <div className="w-full max-w-lg bg-[#141923] border border-white/[0.08] rounded-t-3xl sm:rounded-3xl max-h-[92vh] flex flex-col shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-white/[0.06]">
          <h2 className="text-base font-bold text-white">
            {editTx ? 'Chỉnh sửa giao dịch' : 'Ghi chép giao dịch'}
          </h2>
          <button
            onClick={closeTransactionModal}
            className="p-2 rounded-full text-slate-400 hover:text-white hover:bg-white/[0.06] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Mode Selector Tabs */}
        <div className="px-5 pt-3">
          <div className="grid grid-cols-4 gap-1 p-1 rounded-2xl bg-[#0c1017] border border-white/[0.05]">
            <button
              type="button"
              onClick={() => setMode('EXPENSE')}
              className={`py-2 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-all ${mode === 'EXPENSE'
                  ? 'bg-rose-500 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
                }`}
            >
              <ArrowUpRight className="w-3.5 h-3.5" />
              <span>Khoản chi</span>
            </button>
            <button
              type="button"
              onClick={() => setMode('INCOME')}
              className={`py-2 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-all ${mode === 'INCOME'
                  ? 'bg-[#22c55e] text-slate-950 shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
                }`}
            >
              <ArrowDownLeft className="w-3.5 h-3.5" />
              <span>Khoản thu</span>
            </button>
            <button
              type="button"
              disabled={wallets.length < 2}
              onClick={() => setMode('TRANSFER')}
              title={wallets.length < 2 ? 'Cần ít nhất 2 ví để chuyển tiền' : ''}
              className={`py-2 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-all disabled:opacity-40 ${mode === 'TRANSFER'
                  ? 'bg-sky-500 text-slate-950 shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
                }`}
            >
              <ArrowLeftRight className="w-3.5 h-3.5" />
              <span>Chuyển ví</span>
            </button>
            {!editTx && (
              <button
                type="button"
                onClick={() => setMode('OCR')}
                className={`py-2 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-all ${mode === 'OCR'
                    ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40'
                    : 'text-slate-400 hover:text-slate-200'
                  }`}
              >
                <Camera className="w-3.5 h-3.5" />
                <span>Quét bill</span>
              </button>
            )}
          </div>
        </div>

        {/* Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          {error && (
            <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-xs text-rose-300">
              {error}
            </div>
          )}

          {loadingInit ? (
            <div className="py-12 flex justify-center">
              <Loader2 className="w-6 h-6 animate-spin text-emerald-400" />
            </div>
          ) : mode === 'OCR' ? (
            /* OCR RECEIPT SCANNER TAB */
            <div className="space-y-4">
              <label className="flex flex-col items-center justify-center border-2 border-dashed border-white/15 hover:border-emerald-500/50 rounded-2xl p-6 cursor-pointer bg-[#0c1017]/60 transition-colors">
                {ocrScanning ? (
                  <div className="flex flex-col items-center gap-2 text-emerald-400">
                    <Loader2 className="w-7 h-7 animate-spin" />
                    <span className="text-sm font-medium">Đang đọc hóa đơn...</span>
                  </div>
                ) : (
                  <div className="flex flex-col items-center gap-1.5 text-center">
                    <div className="w-11 h-11 rounded-full bg-emerald-500/15 text-emerald-400 flex items-center justify-center mb-1">
                      <Camera className="w-5 h-5" />
                    </div>
                    <span className="text-sm font-semibold text-slate-200">
                      Chụp hoặc tải ảnh hóa đơn siêu thị / cửa hàng
                    </span>
                    <span className="text-xs text-slate-400">
                      Hỗ trợ JPG, PNG, WEBP (tối đa 5MB)
                    </span>
                  </div>
                )}
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  onChange={handleReceiptFileChange}
                  className="hidden"
                  disabled={ocrScanning}
                />
              </label>

              {/* Wallet Selector for OCR */}
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">
                  Thanh toán từ ví
                </label>
                <select
                  value={walletId}
                  onChange={(e) => setWalletId(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white focus:outline-none focus:border-emerald-500"
                >
                  {wallets.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.name} ({formatVnd(w.balance)})
                    </option>
                  ))}
                </select>
              </div>

              {ocrItems.length > 0 && (
                <div className="space-y-3">
                  <div>
                    <label className="block text-xs font-medium text-slate-400 mb-1">
                      Tên cửa hàng / Ghi chú
                    </label>
                    <input
                      type="text"
                      value={ocrMerchant}
                      onChange={(e) => setOcrMerchant(e.target.value)}
                      className="w-full px-3.5 py-2 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white"
                    />
                  </div>

                  <div className="space-y-2">
                    <div className="flex items-center justify-between text-xs text-slate-400">
                      <span>Chi tiết mặt hàng ({ocrItems.length})</span>
                      <span className="mono-num font-semibold text-emerald-400">
                        Tổng: {formatVnd(ocrTotal)}
                      </span>
                    </div>
                    {ocrItems.map((item, idx) => (
                      <div
                        key={idx}
                        className="p-3 rounded-xl bg-[#0c1017] border border-white/[0.06] space-y-2"
                      >
                        <div className="flex items-center gap-2">
                          <input
                            type="text"
                            value={item.productName}
                            onChange={(e) => {
                              const next = [...ocrItems];
                              next[idx] = { ...next[idx], productName: e.target.value };
                              setOcrItems(next);
                            }}
                            className="flex-1 bg-transparent text-sm text-white focus:outline-none border-b border-transparent focus:border-white/20"
                          />
                          <button
                            type="button"
                            onClick={() =>
                              setOcrItems(ocrItems.filter((_, i) => i !== idx))
                            }
                            className="text-slate-500 hover:text-rose-400 p-1"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                        <div className="flex items-center gap-2">
                          <select
                            value={item.categoryId}
                            onChange={(e) => {
                              const next = [...ocrItems];
                              next[idx] = { ...next[idx], categoryId: e.target.value };
                              setOcrItems(next);
                            }}
                            className="flex-1 px-2.5 py-1.5 rounded-lg bg-[#141923] border border-white/10 text-xs text-slate-200"
                          >
                            {expenseCategories.map((c) => (
                              <option key={c.id} value={c.id}>
                                {c.name}
                              </option>
                            ))}
                          </select>
                          <input
                            type="number"
                            value={item.lineAmount}
                            onChange={(e) => {
                              const next = [...ocrItems];
                              next[idx] = {
                                ...next[idx],
                                lineAmount: Number(e.target.value) || 0,
                              };
                              setOcrItems(next);
                            }}
                            className="w-28 px-2.5 py-1.5 rounded-lg bg-[#141923] border border-white/10 text-xs text-right mono-num text-white"
                          />
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ) : (
            /* MANUAL EXPENSE / INCOME / TRANSFER TAB */
            <>
              {/* Amount Box */}
              <div className="p-4 rounded-2xl bg-[#0c1017] border border-white/[0.06]">
                <label className="block text-xs text-slate-400 mb-1">Số tiền (VNĐ)</label>
                <div className="flex items-baseline gap-2">
                  <input
                    type="text"
                    inputMode="numeric"
                    placeholder="0"
                    autoFocus
                    value={numericAmount > 0 ? numericAmount.toLocaleString('vi-VN') : ''}
                    onChange={(e) => setAmountInput(e.target.value.replace(/\D/g, ''))}
                    className={`w-full bg-transparent text-3xl font-bold mono-num focus:outline-none ${mode === 'EXPENSE'
                        ? 'text-rose-400'
                        : mode === 'INCOME'
                          ? 'text-[#22c55e]'
                          : 'text-sky-400'
                      }`}
                  />
                  <span className="text-xl font-bold text-slate-400">₫</span>
                </div>

                {/* Quick VND Multiplier Chips */}
                {quickSuggestions.length > 0 && (
                  <div className="flex flex-wrap gap-2 mt-3 pt-2.5 border-t border-white/[0.05]">
                    {quickSuggestions.map((sug) => (
                      <button
                        key={sug}
                        type="button"
                        onClick={() => setAmountInput(String(sug))}
                        className="px-2.5 py-1 rounded-lg bg-white/[0.06] hover:bg-white/[0.12] text-xs mono-num text-slate-200 transition-colors"
                      >
                        {sug.toLocaleString('vi-VN')} ₫
                      </button>
                    ))}
                  </div>
                )}
              </div>

              {/* Wallet Selector(s) */}
              <div className={mode === 'TRANSFER' ? 'grid grid-cols-1 sm:grid-cols-2 gap-3' : ''}>
                <div>
                  <label className="block text-xs font-medium text-slate-400 mb-1.5">
                    {mode === 'TRANSFER' ? 'Từ ví nguồn' : 'Ví giao dịch'}
                  </label>
                  <select
                    value={walletId}
                    onChange={(e) => setWalletId(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white focus:outline-none focus:border-emerald-500"
                  >
                    {wallets.map((w) => (
                      <option key={w.id} value={w.id}>
                        {w.name} ({formatVnd(w.balance)})
                      </option>
                    ))}
                  </select>
                </div>

                {mode === 'TRANSFER' && (
                  <div>
                    <label className="block text-xs font-medium text-slate-400 mb-1.5">
                      Đến ví đích
                    </label>
                    <select
                      value={toWalletId}
                      onChange={(e) => setToWalletId(e.target.value)}
                      className="w-full px-3.5 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white focus:outline-none focus:border-emerald-500"
                    >
                      {wallets
                        .filter((w) => w.id !== walletId)
                        .map((w) => (
                          <option key={w.id} value={w.id}>
                            {w.name} ({formatVnd(w.balance)})
                          </option>
                        ))}
                    </select>
                  </div>
                )}
              </div>

              {/* Category Grid */}
              {mode !== 'TRANSFER' && (
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <label className="text-xs font-medium text-slate-400">Danh mục</label>
                    <button
                      type="button"
                      onClick={() => setShowNewCat((s) => !s)}
                      className="text-xs font-semibold text-[#22c55e] hover:underline flex items-center gap-1"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      <span>Tạo danh mục</span>
                    </button>
                  </div>

                  {showNewCat && (
                    <div className="mb-3 p-3 rounded-xl bg-[#0c1017] border border-white/10 space-y-2.5">
                      <input
                        type="text"
                        placeholder="Tên danh mục mới..."
                        value={newCatName}
                        onChange={(e) => setNewCatName(e.target.value)}
                        className="w-full px-3 py-2 rounded-lg bg-[#141923] border border-white/10 text-xs text-white focus:outline-none focus:border-emerald-500"
                      />
                      <div className="flex items-center gap-1.5 flex-wrap">
                        {AVAILABLE_COLORS.map((c) => (
                          <button
                            key={c}
                            type="button"
                            onClick={() => setNewCatColor(c)}
                            className={`w-5 h-5 rounded-full transition-transform ${newCatColor === c ? 'scale-125 ring-2 ring-white' : ''
                              }`}
                            style={{ backgroundColor: c }}
                          />
                        ))}
                      </div>
                      <div className="flex items-center justify-between gap-2">
                        <select
                          value={newCatIcon}
                          onChange={(e) => setNewCatIcon(e.target.value)}
                          className="px-2.5 py-1.5 rounded-lg bg-[#141923] border border-white/10 text-xs text-slate-200"
                        >
                          {AVAILABLE_ICONS.map((ic) => (
                            <option key={ic.key} value={ic.key}>
                              Biểu tượng: {ic.label}
                            </option>
                          ))}
                        </select>
                        <button
                          type="button"
                          onClick={handleCreateInlineCategory}
                          className="px-3 py-1.5 rounded-lg bg-[#22c55e] text-slate-950 text-xs font-semibold flex items-center gap-1"
                        >
                          <Check className="w-3.5 h-3.5" />
                          <span>Thêm</span>
                        </button>
                      </div>
                    </div>
                  )}

                  <div className="grid grid-cols-3 sm:grid-cols-4 gap-2 max-h-48 overflow-y-auto pr-1">
                    {filteredCategories.map((cat) => {
                      const selected = cat.id === categoryId;
                      return (
                        <button
                          key={cat.id}
                          type="button"
                          onClick={() => setCategoryId(cat.id)}
                          className={`p-2.5 rounded-2xl border flex flex-col items-center text-center gap-1.5 transition-all ${selected
                              ? 'bg-white/[0.08] border-[#22c55e] shadow-sm'
                              : 'bg-[#0c1017]/70 border-white/[0.05] hover:bg-white/[0.04]'
                            }`}
                        >
                          <CategoryIcon icon={cat.icon} color={cat.color} size="sm" />
                          <span className="text-[11px] font-medium text-slate-200 line-clamp-1">
                            {cat.name}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* Note & Date */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="sm:col-span-2">
                  <label className="block text-xs font-medium text-slate-400 mb-1.5">
                    Ghi chú
                  </label>
                  <input
                    type="text"
                    placeholder="Ví dụ: Ăn trưa, Đổ xăng, Tiền lương..."
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white placeholder:text-slate-500 focus:outline-none focus:border-emerald-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-400 mb-1.5">
                    Ngày giao dịch
                  </label>
                  <div className="relative">
                    <input
                      type="date"
                      value={txDate}
                      onChange={(e) => setTxDate(e.target.value)}
                      className="w-full px-3 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white focus:outline-none focus:border-emerald-500"
                    />
                  </div>
                </div>
              </div>
            </>
          )}

          {/* Submit */}
          <div className="pt-2">
            <button
              type="submit"
              disabled={submitting || (mode === 'OCR' && ocrItems.length === 0)}
              className="w-full py-3.5 rounded-2xl bg-[#22c55e] hover:bg-[#1eb053] disabled:opacity-50 text-slate-950 font-bold text-sm flex items-center justify-center gap-2 transition-all shadow-lg shadow-emerald-500/20"
            >
              {submitting ? (
                <Loader2 className="w-5 h-5 animate-spin" />
              ) : (
                <span>
                  {editTx
                    ? 'Cập nhật giao dịch'
                    : mode === 'OCR'
                      ? `Lưu hóa đơn (${formatVnd(ocrTotal)})`
                      : 'Lưu giao dịch'}
                </span>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
