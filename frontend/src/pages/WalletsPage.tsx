import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Plus,
  Loader2,
  Edit3,
  Archive,
  X,
  CheckCircle2,
} from 'lucide-react';
import { Wallet, WalletsSummary, financeApi } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { CategoryIcon, AVAILABLE_COLORS } from '../components/CategoryIcon';
import { formatVnd } from '../utils/format';

const WALLET_TYPES = [
  { id: 'CASH', label: 'Tiền mặt', icon: 'wallet', defaultColor: '#f97316' },
  { id: 'BANK', label: 'Ngân hàng', icon: 'landmark', defaultColor: '#22c55e' },
  { id: 'EWALLET', label: 'Ví điện tử (MoMo, ZaloPay...)', icon: 'smartphone', defaultColor: '#ec4899' },
  { id: 'CREDIT_CARD', label: 'Thẻ tín dụng', icon: 'credit-card', defaultColor: '#8b5cf6' },
  { id: 'SAVINGS', label: 'Sổ tiết kiệm / Đầu tư', icon: 'piggy-bank', defaultColor: '#06b6d4' },
] as const;

const VN_BANKS = [
  'VCB',
  'TCB',
  'MB',
  'ACB',
  'BIDV',
  'CTG',
  'VPB',
  'TPB',
  'VIB',
  'SHB',
  'MOMO',
  'ZALOPAY',
];

export function WalletsPage() {
  const { hideBalance, dataVersion, triggerDataRefresh } = useAuth();

  const [summary, setSummary] = useState<WalletsSummary | null>(null);
  const [loading, setLoading] = useState(true);

  // Modal state
  const [showModal, setShowModal] = useState(false);
  const [editingWallet, setEditingWallet] = useState<Wallet | null>(null);
  const [name, setName] = useState('');
  const [subtype, setSubtype] = useState<string>('BANK');
  const [balanceInput, setBalanceInput] = useState('');
  const [isNegativeBalance, setIsNegativeBalance] = useState(false);
  const [bankCode, setBankCode] = useState('');
  const [accountNumber, setAccountNumber] = useState('');
  const [color, setColor] = useState('#22c55e');
  const [includeInTotal, setIncludeInTotal] = useState(true);
  const [allowNegative, setAllowNegative] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    financeApi
      .getWallets()
      .then((res) => {
        if (active) setSummary(res);
      })
      .catch((err) => console.error(err))
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [dataVersion]);

  const openCreateModal = () => {
    setEditingWallet(null);
    setName('');
    setSubtype('BANK');
    setBalanceInput('');
    setIsNegativeBalance(false);
    setBankCode('VCB');
    setAccountNumber('');
    setColor('#22c55e');
    setIncludeInTotal(true);
    setAllowNegative(false);
    setError(null);
    setShowModal(true);
  };

  const openEditModal = (w: Wallet) => {
    setEditingWallet(w);
    setName(w.name);
    setSubtype(w.subtype);
    const absBal = Math.abs(Math.round(w.balance));
    setBalanceInput(absBal > 0 ? String(absBal) : '0');
    setIsNegativeBalance(w.balance < 0);
    setBankCode(w.bankCode || '');
    setAccountNumber(w.accountNumber || '');
    setColor(w.color || '#22c55e');
    setIncludeInTotal(w.includeInTotal);
    setAllowNegative(w.allowNegative);
    setError(null);
    setShowModal(true);
  };

  const numericAbs = Number(balanceInput.replace(/\D/g, '')) || 0;
  const signedBalance = isNegativeBalance ? -numericAbs : numericAbs;

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('Vui lòng nhập tên ví.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const selectedType = WALLET_TYPES.find((t) => t.id === subtype) || WALLET_TYPES[0];
      if (editingWallet) {
        await financeApi.updateWallet(editingWallet.id, {
          name: name.trim(),
          color,
          bankCode: bankCode || undefined,
          accountNumber: accountNumber || undefined,
          includeInTotal,
          allowNegative: subtype === 'CREDIT_CARD' ? true : allowNegative,
          targetBalance: signedBalance,
        });
      } else {
        await financeApi.createWallet({
          name: name.trim(),
          subtype,
          initialBalance: signedBalance,
          icon: selectedType.icon,
          color,
          bankCode: subtype === 'BANK' || subtype === 'EWALLET' ? bankCode : undefined,
          accountNumber:
            subtype === 'BANK' || subtype === 'EWALLET' ? accountNumber : undefined,
          includeInTotal,
          allowNegative: subtype === 'CREDIT_CARD' ? true : allowNegative,
        });
      }
      setShowModal(false);
      triggerDataRefresh();
    } catch (err: any) {
      setError(err.message || 'Không thể lưu ví.');
    } finally {
      setSaving(false);
    }
  };

  const handleArchive = async (w: Wallet) => {
    if (!window.confirm(`Lưu trữ ví "${w.name}"? Lịch sử giao dịch cũ vẫn được giữ nguyên.`)) {
      return;
    }
    try {
      await financeApi.archiveWallet(w.id);
      triggerDataRefresh();
    } catch (err: any) {
      alert(err.message);
    }
  };

  return (
    <div className="space-y-5 pb-4">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-white">Ví của tôi</h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Quản lý tiền mặt, tài khoản ngân hàng, ví điện tử và thẻ tín dụng
          </p>
        </div>

        <button
          onClick={openCreateModal}
          className="px-4 py-2.5 rounded-xl bg-[#22c55e] hover:bg-[#1eb053] text-slate-950 font-semibold text-xs sm:text-sm flex items-center gap-1.5 shadow-lg shadow-emerald-500/20"
        >
          <Plus className="w-4 h-4 stroke-[2.5]" />
          <span>Thêm ví mới</span>
        </button>
      </div>

      {/* Overview Totals */}
      {summary && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div className="bg-[#141923] border border-white/[0.06] rounded-2xl p-4">
            <p className="text-xs text-slate-400">Tổng số dư ròng</p>
            <p className="text-xl font-extrabold mono-num text-white mt-1">
              {formatVnd(summary.totalBalance, hideBalance)}
            </p>
          </div>
          <div className="bg-[#141923] border border-white/[0.06] rounded-2xl p-4">
            <p className="text-xs text-slate-400">Tổng tài sản khả dụng</p>
            <p className="text-xl font-extrabold mono-num text-[#22c55e] mt-1">
              {formatVnd(summary.totalAssets, hideBalance)}
            </p>
          </div>
          <div className="bg-[#141923] border border-white/[0.06] rounded-2xl p-4">
            <p className="text-xs text-slate-400">Dư nợ thẻ / Thấu chi</p>
            <p className="text-xl font-extrabold mono-num text-rose-400 mt-1">
              {formatVnd(summary.totalCreditDebt, hideBalance)}
            </p>
          </div>
        </div>
      )}

      {/* Wallets List */}
      {loading ? (
        <div className="py-16 flex justify-center">
          <Loader2 className="w-6 h-6 animate-spin text-[#22c55e]" />
        </div>
      ) : summary && summary.wallets.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {summary.wallets.map((w) => {
            const typeLabel =
              WALLET_TYPES.find((t) => t.id === w.subtype)?.label || 'Ví cá nhân';
            return (
              <div
                key={w.id}
                className="bg-[#141923] border border-white/[0.06] rounded-2xl p-5 flex flex-col justify-between gap-4"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center gap-3 min-w-0">
                    <CategoryIcon icon={w.icon} color={w.color} size="lg" />
                    <div className="min-w-0">
                      <h3 className="text-base font-bold text-white truncate">
                        {w.name}
                      </h3>
                      <p className="text-xs text-slate-400 mt-0.5">
                        {typeLabel}
                        {w.bankCode ? ` • ${w.bankCode}` : ''}
                        {w.accountNumber ? ` (${w.accountNumber})` : ''}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => openEditModal(w)}
                      title="Sửa / Điều chỉnh số dư"
                      className="p-2 rounded-lg text-slate-400 hover:text-white hover:bg-white/[0.06]"
                    >
                      <Edit3 className="w-4 h-4" />
                    </button>
                    {summary.wallets.length > 1 && (
                      <button
                        onClick={() => handleArchive(w)}
                        title="Lưu trữ ví"
                        className="p-2 rounded-lg text-slate-500 hover:text-rose-400 hover:bg-rose-500/10"
                      >
                        <Archive className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </div>

                <div className="pt-3 border-t border-white/[0.05] flex items-end justify-between">
                  <div>
                    <p className="text-[11px] text-slate-400">Số dư hiện tại</p>
                    <p
                      className={`text-xl font-extrabold mono-num mt-0.5 ${
                        w.balance < 0 ? 'text-rose-400' : 'text-white'
                      }`}
                    >
                      {formatVnd(w.balance, hideBalance)}
                    </p>
                  </div>

                  <div className="flex items-center gap-3">
                    {!w.includeInTotal && (
                      <span className="px-2 py-0.5 rounded bg-slate-800 text-[10px] text-slate-400">
                        Không tính vào tổng
                      </span>
                    )}
                    <Link
                      to={`/transactions?walletId=${w.id}`}
                      className="text-xs font-semibold text-[#22c55e] hover:underline"
                    >
                      Xem giao dịch →
                    </Link>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      ) : null}

      {/* Create / Edit Wallet Modal */}
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/75 backdrop-blur-sm p-0 sm:p-4">
          <div className="w-full max-w-md bg-[#141923] border border-white/[0.08] rounded-t-3xl sm:rounded-3xl p-5 max-h-[90vh] overflow-y-auto space-y-4 shadow-2xl">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-white">
                {editingWallet ? 'Chỉnh sửa & Điều chỉnh số dư' : 'Thêm ví mới'}
              </h3>
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

            <form onSubmit={handleSave} className="space-y-4">
              {!editingWallet && (
                <div>
                  <label className="block text-xs font-medium text-slate-400 mb-1.5">
                    Loại ví
                  </label>
                  <select
                    value={subtype}
                    onChange={(e) => {
                      const val = e.target.value;
                      setSubtype(val);
                      const found = WALLET_TYPES.find((t) => t.id === val);
                      if (found) setColor(found.defaultColor);
                      if (val === 'CREDIT_CARD') setAllowNegative(true);
                    }}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white focus:outline-none focus:border-[#22c55e]"
                  >
                    {WALLET_TYPES.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.label}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">
                  Tên ví
                </label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Vietcombank Lương, MoMo, Ví tiền mặt..."
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white focus:outline-none focus:border-[#22c55e]"
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label className="text-xs font-medium text-slate-400">
                    {editingWallet ? 'Số dư thực tế hiện tại (VNĐ)' : 'Số dư ban đầu (VNĐ)'}
                  </label>
                  {(subtype === 'CREDIT_CARD' || allowNegative) && (
                    <button
                      type="button"
                      onClick={() => setIsNegativeBalance((n) => !n)}
                      className={`text-[11px] px-2 py-0.5 rounded font-semibold ${
                        isNegativeBalance
                          ? 'bg-rose-500/20 text-rose-300'
                          : 'bg-white/[0.06] text-slate-400'
                      }`}
                    >
                      {isNegativeBalance ? 'Đang âm (-)' : 'Số dương (+)'}
                    </button>
                  )}
                </div>
                <input
                  type="text"
                  inputMode="numeric"
                  placeholder="0"
                  value={numericAbs > 0 ? numericAbs.toLocaleString('vi-VN') : ''}
                  onChange={(e) => setBalanceInput(e.target.value.replace(/\D/g, ''))}
                  className="w-full px-4 py-3 rounded-xl bg-[#0c1017] border border-white/10 text-lg font-bold mono-num text-white focus:outline-none focus:border-[#22c55e]"
                />
              </div>

              {(subtype === 'BANK' || subtype === 'EWALLET') && (
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-slate-400 mb-1.5">
                      Ngân hàng / Ví
                    </label>
                    <select
                      value={bankCode}
                      onChange={(e) => setBankCode(e.target.value)}
                      className="w-full px-3 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white"
                    >
                      <option value="">Chọn...</option>
                      {VN_BANKS.map((b) => (
                        <option key={b} value={b}>
                          {b}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-400 mb-1.5">
                      Số tài khoản (tuỳ chọn)
                    </label>
                    <input
                      type="text"
                      placeholder="Đồng bộ tự động"
                      value={accountNumber}
                      onChange={(e) => setAccountNumber(e.target.value)}
                      className="w-full px-3 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white"
                    />
                  </div>
                </div>
              )}

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">
                  Màu sắc nhận diện
                </label>
                <div className="flex items-center gap-2 flex-wrap">
                  {AVAILABLE_COLORS.map((c) => (
                    <button
                      key={c}
                      type="button"
                      onClick={() => setColor(c)}
                      className={`w-6 h-6 rounded-full transition-transform ${
                        color === c ? 'scale-125 ring-2 ring-white' : ''
                      }`}
                      style={{ backgroundColor: c }}
                    />
                  ))}
                </div>
              </div>

              <div className="space-y-2 pt-1">
                <label className="flex items-center gap-2.5 text-xs text-slate-300 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={includeInTotal}
                    onChange={(e) => setIncludeInTotal(e.target.checked)}
                    className="rounded border-white/20 bg-[#0c1017] text-[#22c55e]"
                  />
                  <span>Tính vào tổng số dư trên màn hình chính</span>
                </label>

                {subtype !== 'CREDIT_CARD' && (
                  <label className="flex items-center gap-2.5 text-xs text-slate-300 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={allowNegative}
                      onChange={(e) => setAllowNegative(e.target.checked)}
                      className="rounded border-white/20 bg-[#0c1017] text-[#22c55e]"
                    />
                    <span>Cho phép chi tiêu thấu chi (số dư âm)</span>
                  </label>
                )}
              </div>

              <button
                type="submit"
                disabled={saving}
                className="w-full py-3 rounded-2xl bg-[#22c55e] hover:bg-[#1eb053] text-slate-950 font-bold text-sm flex items-center justify-center gap-1.5"
              >
                <CheckCircle2 className="w-4 h-4" />
                <span>{saving ? 'Đang lưu...' : editingWallet ? 'Lưu thay đổi' : 'Tạo ví'}</span>
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

