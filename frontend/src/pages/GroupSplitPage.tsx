import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Users,
  Plus,
  UserPlus,
  Share2,
  CheckCircle2,
  ArrowRight,
  Loader2,
  X,
  RefreshCw,
} from 'lucide-react';
import {
  ExpenseGroupSummary,
  PublicGroupShareResponse,
  groupsApi,
} from '../api/client';
import { formatVnd } from '../utils/format';

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
];

export function GroupSplitPage() {
  const [groups, setGroups] = useState<ExpenseGroupSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string>('');
  const [detail, setDetail] = useState<PublicGroupShareResponse | null>(null);
  const [loading, setLoading] = useState(true);

  // Modals
  const [showGroupModal, setShowGroupModal] = useState(false);
  const [newGroupName, setNewGroupName] = useState('');

  const [showMemberModal, setShowMemberModal] = useState(false);
  const [memberName, setMemberName] = useState('');
  const [memberBank, setMemberBank] = useState('VCB');
  const [memberAccNum, setMemberAccNum] = useState('');

  const [showExpenseModal, setShowExpenseModal] = useState(false);
  const [expTitle, setExpTitle] = useState('');
  const [expAmountInput, setExpAmountInput] = useState('');
  const [paidById, setPaidById] = useState('');
  const [strategy, setStrategy] = useState<'EQUAL' | 'EXACT' | 'PERCENTAGE'>('EQUAL');
  const [selectedMemberIds, setSelectedMemberIds] = useState<string[]>([]);
  const [customWeights, setCustomWeights] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [copiedLink, setCopiedLink] = useState(false);

  const loadGroups = async (preferGroupId?: string) => {
    setLoading(true);
    try {
      const list = await groupsApi.getGroups();
      setGroups(list);
      const targetId = preferGroupId || selectedId || list[0]?.id || '';
      setSelectedId(targetId);
      if (targetId) {
        const g = await groupsApi.getGroup(targetId);
        setDetail(g);
      } else {
        setDetail(null);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadGroups();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSelectGroup = async (id: string) => {
    setSelectedId(id);
    setLoading(true);
    try {
      const g = await groupsApi.getGroup(id);
      setDetail(g);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateGroup = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newGroupName.trim()) return;
    const created = await groupsApi.createGroup({
      name: newGroupName.trim(),
      currency: 'VND',
      simplifyDebts: true,
    });
    setNewGroupName('');
    setShowGroupModal(false);
    await loadGroups(created.id);
  };

  const handleAddMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedId || !memberName.trim()) return;
    await groupsApi.addMember(selectedId, {
      displayName: memberName.trim(),
      isGuest: false,
      bankCode: memberBank || undefined,
      bankAccountNumber: memberAccNum.trim() || undefined,
    });
    setMemberName('');
    setMemberAccNum('');
    setShowMemberModal(false);
    await handleSelectGroup(selectedId);
  };

  const openExpenseCreator = () => {
    if (!detail || detail.members.length === 0) return;
    setExpTitle('');
    setExpAmountInput('');
    setPaidById(detail.members[0].id);
    setStrategy('EQUAL');
    setSelectedMemberIds(detail.members.map((m) => m.id));
    setCustomWeights({});
    setFormError(null);
    setShowExpenseModal(true);
  };

  const numericExpAmt = Number(expAmountInput.replace(/\D/g, '')) || 0;

  const handleCreateExpense = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!detail) return;
    setFormError(null);

    if (numericExpAmt <= 0) {
      setFormError('Vui lòng nhập tổng số tiền lớn hơn 0 ₫.');
      return;
    }
    if (selectedMemberIds.length === 0) {
      setFormError('Cần chọn ít nhất 1 thành viên tham gia.');
      return;
    }

    try {
      const shares = selectedMemberIds.map((mId) => {
        const raw = Number((customWeights[mId] || '0').replace(/[^\d.]/g, '')) || 0;
        if (strategy === 'EXACT') return { memberId: mId, specifiedAmount: raw };
        if (strategy === 'PERCENTAGE') return { memberId: mId, percentage: raw };
        return { memberId: mId };
      });

      await groupsApi.createExpense(detail.groupId, {
        title: expTitle.trim() || 'Chi tiêu chung',
        paidByMemberId: paidById,
        totalAmount: numericExpAmt,
        splitStrategy: strategy,
        shares,
      });

      await groupsApi.generatePlan(detail.groupId);
      setShowExpenseModal(false);
      await handleSelectGroup(detail.groupId);
    } catch (err: any) {
      setFormError(err.message || 'Không thể lưu khoản chi nhóm.');
    }
  };

  const handleRecalculate = async () => {
    if (!detail) return;
    await groupsApi.generatePlan(detail.groupId);
    await handleSelectGroup(detail.groupId);
  };

  const handleSettle = async (settlementId: string) => {
    if (!detail) return;
    await groupsApi.settleDebt(settlementId);
    await handleSelectGroup(detail.groupId);
  };

  const copyShareUrl = () => {
    if (!detail?.shareToken) return;
    const url = `${window.location.origin}/share/${detail.shareToken}`;
    navigator.clipboard.writeText(url);
    setCopiedLink(true);
    setTimeout(() => setCopiedLink(false), 2000);
  };

  return (
    <div className="space-y-5 pb-4">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-white">Sổ chia tiền nhóm</h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Theo dõi chi tiêu chung cho chuyến đi, nhóm bạn, gia đình & thanh toán VietQR
          </p>
        </div>

        <button
          onClick={() => setShowGroupModal(true)}
          className="px-4 py-2.5 rounded-xl bg-[#22c55e] hover:bg-[#1eb053] text-slate-950 font-semibold text-xs sm:text-sm flex items-center gap-1.5 shadow-lg shadow-emerald-500/20"
        >
          <Plus className="w-4 h-4 stroke-[2.5]" />
          <span>Tạo nhóm mới</span>
        </button>
      </div>

      {/* Group Selector Pills */}
      {groups.length > 0 && (
        <div className="flex items-center gap-2 overflow-x-auto pb-1">
          {groups.map((g) => (
            <button
              key={g.id}
              onClick={() => handleSelectGroup(g.id)}
              className={`px-4 py-2 rounded-full text-xs font-semibold shrink-0 transition-all ${g.id === selectedId
                  ? 'bg-[#22c55e] text-slate-950 shadow'
                  : 'bg-[#141923] text-slate-300 border border-white/[0.06] hover:bg-white/[0.06]'
                }`}
            >
              {g.name}
            </button>
          ))}
        </div>
      )}

      {loading ? (
        <div className="py-16 flex justify-center">
          <Loader2 className="w-6 h-6 animate-spin text-[#22c55e]" />
        </div>
      ) : !detail ? (
        <div className="bg-[#141923] border border-white/[0.06] rounded-2xl py-16 px-4 text-center space-y-3">
          <Users className="w-10 h-10 text-slate-500 mx-auto" />
          <p className="text-sm font-medium text-slate-200">
            Bạn chưa có nhóm chia tiền nào
          </p>
          <p className="text-xs text-slate-400 max-w-md mx-auto">
            Tạo nhóm cho chuyến du lịch, bữa ăn chung hoặc chi tiêu phòng trọ để tự động tính toán ai cần chuyển khoản cho ai.
          </p>
          <button
            onClick={() => setShowGroupModal(true)}
            className="px-4 py-2.5 rounded-xl bg-[#22c55e] text-slate-950 font-semibold text-xs"
          >
            + Tạo nhóm đầu tiên
          </button>
        </div>
      ) : (
        <div className="space-y-5">
          {/* Group Action Bar */}
          <div className="bg-[#141923] border border-white/[0.06] rounded-2xl p-4 sm:p-5 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-bold text-white">{detail.groupName}</h2>
              <p className="text-xs text-slate-400 mt-0.5">
                {detail.members.length} thành viên • {detail.expenses.length} khoản chi chung
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <button
                onClick={() => setShowMemberModal(true)}
                className="px-3 py-2 rounded-xl bg-white/[0.06] hover:bg-white/[0.12] text-xs font-semibold text-white flex items-center gap-1.5"
              >
                <UserPlus className="w-4 h-4 text-[#22c55e]" />
                <span>Thêm thành viên</span>
              </button>

              {detail.members.length >= 2 && (
                <button
                  onClick={openExpenseCreator}
                  className="px-3.5 py-2 rounded-xl bg-[#22c55e] hover:bg-[#1eb053] text-slate-950 text-xs font-bold flex items-center gap-1.5"
                >
                  <Plus className="w-4 h-4 stroke-[2.5]" />
                  <span>Ghi khoản chi chung</span>
                </button>
              )}

              {detail.shareToken && (
                <>
                  <button
                    onClick={copyShareUrl}
                    className="px-3 py-2 rounded-xl bg-white/[0.06] hover:bg-white/[0.12] text-xs font-semibold text-slate-200 flex items-center gap-1.5"
                  >
                    <Share2 className="w-4 h-4 text-sky-400" />
                    <span>{copiedLink ? 'Đã sao chép link!' : 'Sao chép link VietQR'}</span>
                  </button>
                  <Link
                    to={`/share/${detail.shareToken}`}
                    className="px-3 py-2 rounded-xl bg-sky-500/15 hover:bg-sky-500/25 text-xs font-semibold text-sky-300"
                  >
                    Mở trang thanh toán →
                  </Link>
                </>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
            {/* Members & Balances */}
            <section className="bg-[#141923] border border-white/[0.06] rounded-2xl p-5 space-y-3">
              <h3 className="text-sm font-bold text-white">Số dư công nợ từng người</h3>
              {detail.members.length === 0 ? (
                <p className="text-xs text-slate-400 py-6 text-center">
                  Hãy thêm ít nhất 2 thành viên vào nhóm để bắt đầu chia hóa đơn.
                </p>
              ) : (
                <div className="divide-y divide-white/[0.05]">
                  {detail.members.map((m) => (
                    <div
                      key={m.id}
                      className="py-3 flex items-center justify-between gap-3"
                    >
                      <div>
                        <p className="text-sm font-semibold text-white">{m.displayName}</p>
                        {m.bankCode && (
                          <p className="text-[11px] text-slate-400">
                            {m.bankCode} {m.bankAccountNumber ? `• ${m.bankAccountNumber}` : ''}
                          </p>
                        )}
                      </div>
                      <span
                        className={`text-sm font-bold mono-num ${m.netBalance > 0
                            ? 'text-[#22c55e]'
                            : m.netBalance < 0
                              ? 'text-rose-400'
                              : 'text-slate-400'
                          }`}
                      >
                        {m.netBalance > 0
                          ? `Được nhận +${formatVnd(m.netBalance)}`
                          : m.netBalance < 0
                            ? `Cần trả ${formatVnd(Math.abs(m.netBalance))}`
                            : 'Đã cân bằng'}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </section>

            {/* Settlements Plan */}
            <section className="bg-[#141923] border border-white/[0.06] rounded-2xl p-5 space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-white">Đề xuất thanh toán gọn nhất</h3>
                <button
                  onClick={handleRecalculate}
                  className="text-xs text-slate-400 hover:text-white flex items-center gap-1"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                  <span>Cập nhật</span>
                </button>
              </div>

              {detail.settlements.length === 0 ? (
                <p className="text-xs text-slate-400 py-6 text-center">
                  Chưa có khoản nợ nào cần thanh toán.
                </p>
              ) : (
                <div className="space-y-2.5">
                  {detail.settlements.map((s) => (
                    <div
                      key={s.id}
                      className="p-3.5 rounded-xl bg-[#0c1017] border border-white/[0.06] flex items-center justify-between gap-3"
                    >
                      <div className="min-w-0">
                        <div className="flex items-center gap-1.5 text-sm font-semibold text-white">
                          <span>{s.fromMemberName}</span>
                          <ArrowRight className="w-3.5 h-3.5 text-slate-500" />
                          <span className="text-[#22c55e]">{s.toMemberName}</span>
                        </div>
                        <p className="text-xs mono-num font-bold text-slate-200 mt-0.5">
                          {formatVnd(s.amount)}
                        </p>
                      </div>

                      {s.status === 'COMPLETED' ? (
                        <span className="px-2.5 py-1 rounded-full bg-emerald-500/15 text-[#22c55e] text-xs font-semibold flex items-center gap-1">
                          <CheckCircle2 className="w-3.5 h-3.5" />
                          <span>Đã trả</span>
                        </span>
                      ) : (
                        <button
                          onClick={() => handleSettle(s.id)}
                          className="px-3 py-1.5 rounded-xl bg-[#22c55e] hover:bg-[#1eb053] text-slate-950 text-xs font-bold shrink-0"
                        >
                          Đánh dấu đã trả
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>

          {/* Group Expenses History */}
          <section className="bg-[#141923] border border-white/[0.06] rounded-2xl p-5 space-y-3">
            <h3 className="text-sm font-bold text-white">Lịch sử chi tiêu chung</h3>
            {detail.expenses.length === 0 ? (
              <p className="text-xs text-slate-400 py-8 text-center">
                Chưa có khoản chi chung nào trong nhóm này.
              </p>
            ) : (
              <div className="divide-y divide-white/[0.05]">
                {detail.expenses.map((exp) => (
                  <div
                    key={exp.id}
                    className="py-3.5 flex items-center justify-between gap-3"
                  >
                    <div>
                      <p className="text-sm font-semibold text-white">{exp.title}</p>
                      <p className="text-xs text-slate-400 mt-0.5">
                        <strong className="text-slate-200">{exp.paidByMemberName}</strong> đã ứng trước •{' '}
                        {new Date(exp.createdAt).toLocaleDateString('vi-VN')}
                      </p>
                    </div>
                    <span className="text-sm font-bold mono-num text-white">
                      {formatVnd(exp.totalAmount)}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      )}

      {/* Create Group Modal */}
      {showGroupModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4">
          <div className="w-full max-w-md bg-[#141923] border border-white/10 rounded-3xl p-5 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-white">Tạo nhóm chia tiền</h3>
              <button
                onClick={() => setShowGroupModal(false)}
                className="p-1 text-slate-400 hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleCreateGroup} className="space-y-4">
              <div>
                <label className="block text-xs text-slate-400 mb-1.5">Tên nhóm</label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Du lịch Đà Lạt, Ăn trưa văn phòng..."
                  value={newGroupName}
                  onChange={(e) => setNewGroupName(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white"
                />
              </div>
              <button
                type="submit"
                className="w-full py-3 rounded-2xl bg-[#22c55e] text-slate-950 font-bold text-sm"
              >
                Tạo nhóm
              </button>
            </form>
          </div>
        </div>
      )}

      {/* Add Member Modal */}
      {showMemberModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4">
          <div className="w-full max-w-md bg-[#141923] border border-white/10 rounded-3xl p-5 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-white">Thêm thành viên</h3>
              <button
                onClick={() => setShowMemberModal(false)}
                className="p-1 text-slate-400 hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleAddMember} className="space-y-3.5">
              <div>
                <label className="block text-xs text-slate-400 mb-1.5">Tên hiển thị</label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Minh, Lan, Bạn..."
                  value={memberName}
                  onChange={(e) => setMemberName(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs text-slate-400 mb-1.5">
                    Ngân hàng nhận QR
                  </label>
                  <select
                    value={memberBank}
                    onChange={(e) => setMemberBank(e.target.value)}
                    className="w-full px-3 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white"
                  >
                    {VN_BANKS.map((b) => (
                      <option key={b} value={b}>
                        {b}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-slate-400 mb-1.5">
                    Số tài khoản (tuỳ chọn)
                  </label>
                  <input
                    type="text"
                    placeholder="Tạo mã VietQR"
                    value={memberAccNum}
                    onChange={(e) => setMemberAccNum(e.target.value)}
                    className="w-full px-3 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white"
                  />
                </div>
              </div>
              <button
                type="submit"
                className="w-full py-3 rounded-2xl bg-[#22c55e] text-slate-950 font-bold text-sm"
              >
                Lưu thành viên
              </button>
            </form>
          </div>
        </div>
      )}

      {/* Add Group Expense Modal */}
      {showExpenseModal && detail && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/75 backdrop-blur-sm p-0 sm:p-4">
          <div className="w-full max-w-lg bg-[#141923] border border-white/10 rounded-t-3xl sm:rounded-3xl p-5 max-h-[90vh] overflow-y-auto space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-white">Ghi khoản chi chung</h3>
              <button
                onClick={() => setShowExpenseModal(false)}
                className="p-1 text-slate-400 hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {formError && (
              <div className="p-3 rounded-xl bg-rose-500/15 text-rose-300 text-xs">
                {formError}
              </div>
            )}

            <form onSubmit={handleCreateExpense} className="space-y-4">
              <div>
                <label className="block text-xs text-slate-400 mb-1.5">Nội dung chi</label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Tiền khách sạn, Ăn lẩu..."
                  value={expTitle}
                  onChange={(e) => setExpTitle(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs text-slate-400 mb-1.5">
                    Tổng số tiền (VNĐ)
                  </label>
                  <input
                    type="text"
                    inputMode="numeric"
                    required
                    placeholder="0"
                    value={numericExpAmt > 0 ? numericExpAmt.toLocaleString('vi-VN') : ''}
                    onChange={(e) => setExpAmountInput(e.target.value.replace(/\D/g, ''))}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm font-bold mono-num text-white"
                  />
                </div>
                <div>
                  <label className="block text-xs text-slate-400 mb-1.5">
                    Người trả trước
                  </label>
                  <select
                    value={paidById}
                    onChange={(e) => setPaidById(e.target.value)}
                    className="w-full px-3 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white"
                  >
                    {detail.members.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.displayName}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs text-slate-400 mb-1.5">Cách chia</label>
                <div className="grid grid-cols-3 gap-1.5 p-1 rounded-xl bg-[#0c1017]">
                  {(
                    [
                      { id: 'EQUAL', label: 'Chia đều' },
                      { id: 'EXACT', label: 'Số tiền cụ thể' },
                      { id: 'PERCENTAGE', label: 'Theo %' },
                    ] as const
                  ).map((st) => (
                    <button
                      key={st.id}
                      type="button"
                      onClick={() => setStrategy(st.id)}
                      className={`py-2 rounded-lg text-xs font-semibold ${strategy === st.id
                          ? 'bg-[#22c55e] text-slate-950'
                          : 'text-slate-400'
                        }`}
                    >
                      {st.label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="space-y-2">
                <label className="block text-xs text-slate-400">
                  Thành viên tham gia ({selectedMemberIds.length})
                </label>
                {detail.members.map((m) => {
                  const checked = selectedMemberIds.includes(m.id);
                  return (
                    <div
                      key={m.id}
                      className="flex items-center justify-between p-2.5 rounded-xl bg-[#0c1017] border border-white/[0.05]"
                    >
                      <label className="flex items-center gap-2.5 text-sm text-white cursor-pointer">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setSelectedMemberIds((prev) => [...prev, m.id]);
                            } else {
                              setSelectedMemberIds((prev) =>
                                prev.filter((id) => id !== m.id)
                              );
                            }
                          }}
                          className="rounded border-white/20 bg-[#141923] text-[#22c55e]"
                        />
                        <span>{m.displayName}</span>
                      </label>

                      {checked && strategy !== 'EQUAL' && (
                        <input
                          type="number"
                          placeholder={strategy === 'PERCENTAGE' ? '%' : 'VNĐ'}
                          value={customWeights[m.id] || ''}
                          onChange={(e) =>
                            setCustomWeights((prev) => ({
                              ...prev,
                              [m.id]: e.target.value,
                            }))
                          }
                          className="w-28 px-2.5 py-1 rounded-lg bg-[#141923] border border-white/10 text-xs text-right mono-num text-white"
                        />
                      )}
                    </div>
                  );
                })}
              </div>

              <button
                type="submit"
                className="w-full py-3 rounded-2xl bg-[#22c55e] text-slate-950 font-bold text-sm"
              >
                Lưu khoản chi
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
