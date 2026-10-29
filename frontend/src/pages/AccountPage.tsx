import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Wallet,
  BarChart3,
  Users,
  Tags,
  KeyRound,
  LogOut,
  ChevronRight,
  Plus,
  Trash2,
  Check,
  X,
} from 'lucide-react';
import { Category, authApi, financeApi } from '../api/client';
import { useAuth } from '../context/AuthContext';
import {
  AVAILABLE_COLORS,
  AVAILABLE_ICONS,
  CategoryIcon,
} from '../components/CategoryIcon';

export function AccountPage() {
  const { user, refreshUser, logout, triggerDataRefresh } = useAuth();

  // Edit profile modal
  const [showProfileModal, setShowProfileModal] = useState(false);
  const [fullName, setFullName] = useState(user?.fullName || '');
  const [avatarColor, setAvatarColor] = useState(user?.avatarColor || '#22c55e');
  const [profileMsg, setProfileMsg] = useState<string | null>(null);

  // Change password modal
  const [showPwdModal, setShowPwdModal] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [pwdError, setPwdError] = useState<string | null>(null);
  const [pwdSuccess, setPwdSuccess] = useState<string | null>(null);

  // Categories manager modal
  const [showCatModal, setShowCatModal] = useState(false);
  const [categories, setCategories] = useState<Category[]>([]);
  const [catTab, setCatTab] = useState<'EXPENSE' | 'INCOME'>('EXPENSE');
  const [newCatName, setNewCatName] = useState('');
  const [newCatIcon, setNewCatIcon] = useState('tag');
  const [newCatColor, setNewCatColor] = useState('#ef4444');

  useEffect(() => {
    if (user) {
      setFullName(user.fullName);
      setAvatarColor(user.avatarColor || '#22c55e');
    }
  }, [user]);

  const loadCategories = async () => {
    const list = await financeApi.getCategories();
    setCategories(list);
  };

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await authApi.updateProfile({ fullName, avatarColor });
      await refreshUser();
      setProfileMsg('Đã cập nhật hồ sơ cá nhân.');
      setTimeout(() => {
        setProfileMsg(null);
        setShowProfileModal(false);
      }, 900);
    } catch (err: any) {
      alert(err.message);
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setPwdError(null);
    setPwdSuccess(null);
    try {
      const res = await authApi.changePassword({ currentPassword, newPassword });
      setPwdSuccess(res.message);
      setCurrentPassword('');
      setNewPassword('');
    } catch (err: any) {
      setPwdError(err.message || 'Đổi mật khẩu thất bại.');
    }
  };

  const handleAddCategory = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newCatName.trim()) return;
    try {
      await financeApi.createCategory({
        name: newCatName.trim(),
        type: catTab,
        icon: newCatIcon,
        color: newCatColor,
      });
      setNewCatName('');
      await loadCategories();
      triggerDataRefresh();
    } catch (err: any) {
      alert(err.message);
    }
  };

  const handleDeleteCategory = async (id: string) => {
    if (!window.confirm('Xóa danh mục này khỏi danh sách lựa chọn?')) return;
    try {
      await financeApi.archiveCategory(id);
      await loadCategories();
      triggerDataRefresh();
    } catch (err: any) {
      alert(err.message);
    }
  };

  if (!user) return null;

  return (
    <div className="max-w-2xl mx-auto space-y-5 pb-4">
      <h1 className="text-xl sm:text-2xl font-bold text-white">Tài khoản cá nhân</h1>

      {/* Profile Card */}
      <div className="bg-[#141923] border border-white/[0.06] rounded-2xl p-5 flex items-center justify-between gap-4">
        <div className="flex items-center gap-4 min-w-0">
          <div
            className="w-14 h-14 rounded-full flex items-center justify-center text-xl font-extrabold text-slate-950 shrink-0 shadow-lg"
            style={{ backgroundColor: user.avatarColor || '#22c55e' }}
          >
            {user.fullName.charAt(0).toUpperCase()}
          </div>
          <div className="min-w-0">
            <h2 className="text-base sm:text-lg font-bold text-white truncate">
              {user.fullName}
            </h2>
            <p className="text-xs sm:text-sm text-slate-400 truncate">{user.email}</p>
            <p className="text-[11px] text-slate-500 mt-1">
              Tiền tệ mặc định: {user.defaultCurrency}
            </p>
          </div>
        </div>

        <button
          onClick={() => setShowProfileModal(true)}
          className="px-3.5 py-2 rounded-xl bg-white/[0.06] hover:bg-white/[0.12] text-xs font-semibold text-white shrink-0"
        >
          Chỉnh sửa
        </button>
      </div>

      {/* Finance Utilities Menu */}
      <div className="bg-[#141923] border border-white/[0.06] rounded-2xl divide-y divide-white/[0.05] overflow-hidden">
        <Link
          to="/wallets"
          className="px-5 py-4 flex items-center justify-between hover:bg-white/[0.03] transition-colors"
        >
          <div className="flex items-center gap-3.5">
            <div className="w-9 h-9 rounded-xl bg-orange-500/15 text-orange-400 flex items-center justify-center">
              <Wallet className="w-5 h-5" />
            </div>
            <div>
              <p className="text-sm font-semibold text-white">Quản lý Ví của tôi</p>
              <p className="text-xs text-slate-400">
                Tiền mặt, tài khoản ngân hàng, ví điện tử, thẻ tín dụng
              </p>
            </div>
          </div>
          <ChevronRight className="w-4 h-4 text-slate-500" />
        </Link>

        <button
          type="button"
          onClick={() => {
            loadCategories();
            setShowCatModal(true);
          }}
          className="w-full px-5 py-4 flex items-center justify-between hover:bg-white/[0.03] transition-colors text-left"
        >
          <div className="flex items-center gap-3.5">
            <div className="w-9 h-9 rounded-xl bg-pink-500/15 text-pink-400 flex items-center justify-center">
              <Tags className="w-5 h-5" />
            </div>
            <div>
              <p className="text-sm font-semibold text-white">Danh mục Thu & Chi</p>
              <p className="text-xs text-slate-400">
                Tùy chỉnh nhóm chi tiêu và nguồn thu nhập cá nhân
              </p>
            </div>
          </div>
          <ChevronRight className="w-4 h-4 text-slate-500" />
        </button>

        <Link
          to="/reports"
          className="px-5 py-4 flex items-center justify-between hover:bg-white/[0.03] transition-colors"
        >
          <div className="flex items-center gap-3.5">
            <div className="w-9 h-9 rounded-xl bg-sky-500/15 text-sky-400 flex items-center justify-center">
              <BarChart3 className="w-5 h-5" />
            </div>
            <div>
              <p className="text-sm font-semibold text-white">Báo cáo thống kê</p>
              <p className="text-xs text-slate-400">
                Biểu đồ dòng tiền 6 tháng và tỷ lệ tiết kiệm
              </p>
            </div>
          </div>
          <ChevronRight className="w-4 h-4 text-slate-500" />
        </Link>

        <Link
          to="/groups"
          className="px-5 py-4 flex items-center justify-between hover:bg-white/[0.03] transition-colors"
        >
          <div className="flex items-center gap-3.5">
            <div className="w-9 h-9 rounded-xl bg-emerald-500/15 text-[#22c55e] flex items-center justify-center">
              <Users className="w-5 h-5" />
            </div>
            <div>
              <p className="text-sm font-semibold text-white">Sổ chia tiền nhóm</p>
              <p className="text-xs text-slate-400">
                Chia hóa đơn du lịch, ăn uống, bạn cùng phòng & tạo mã VietQR
              </p>
            </div>
          </div>
          <ChevronRight className="w-4 h-4 text-slate-500" />
        </Link>
      </div>

      {/* Security & Sign Out */}
      <div className="bg-[#141923] border border-white/[0.06] rounded-2xl divide-y divide-white/[0.05] overflow-hidden">
        <button
          type="button"
          onClick={() => {
            setPwdError(null);
            setPwdSuccess(null);
            setShowPwdModal(true);
          }}
          className="w-full px-5 py-4 flex items-center justify-between hover:bg-white/[0.03] transition-colors text-left"
        >
          <div className="flex items-center gap-3.5">
            <div className="w-9 h-9 rounded-xl bg-violet-500/15 text-violet-400 flex items-center justify-center">
              <KeyRound className="w-5 h-5" />
            </div>
            <div>
              <p className="text-sm font-semibold text-white">Đổi mật khẩu</p>
              <p className="text-xs text-slate-400">Bảo vệ tài khoản cá nhân của bạn</p>
            </div>
          </div>
          <ChevronRight className="w-4 h-4 text-slate-500" />
        </button>

        <button
          type="button"
          onClick={logout}
          className="w-full px-5 py-4 flex items-center gap-3.5 hover:bg-rose-500/10 transition-colors text-left text-rose-400"
        >
          <div className="w-9 h-9 rounded-xl bg-rose-500/15 flex items-center justify-center">
            <LogOut className="w-5 h-5" />
          </div>
          <span className="text-sm font-semibold">Đăng xuất khỏi thiết bị</span>
        </button>
      </div>

      {/* Edit Profile Modal */}
      {showProfileModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4">
          <div className="w-full max-w-md bg-[#141923] border border-white/10 rounded-3xl p-5 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-white">Cập nhật hồ sơ</h3>
              <button
                onClick={() => setShowProfileModal(false)}
                className="p-1 text-slate-400 hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            {profileMsg && (
              <div className="p-3 rounded-xl bg-emerald-500/15 text-emerald-300 text-xs">
                {profileMsg}
              </div>
            )}
            <form onSubmit={handleSaveProfile} className="space-y-4">
              <div>
                <label className="block text-xs text-slate-400 mb-1.5">Họ và tên</label>
                <input
                  type="text"
                  required
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1.5">
                  Màu ảnh đại diện
                </label>
                <div className="flex gap-2 flex-wrap">
                  {AVAILABLE_COLORS.map((c) => (
                    <button
                      key={c}
                      type="button"
                      onClick={() => setAvatarColor(c)}
                      className={`w-7 h-7 rounded-full ${
                        avatarColor === c ? 'ring-2 ring-white scale-110' : ''
                      }`}
                      style={{ backgroundColor: c }}
                    />
                  ))}
                </div>
              </div>
              <button
                type="submit"
                className="w-full py-3 rounded-2xl bg-[#22c55e] text-slate-950 font-bold text-sm"
              >
                Lưu hồ sơ
              </button>
            </form>
          </div>
        </div>
      )}

      {/* Change Password Modal */}
      {showPwdModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4">
          <div className="w-full max-w-md bg-[#141923] border border-white/10 rounded-3xl p-5 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-white">Đổi mật khẩu</h3>
              <button
                onClick={() => setShowPwdModal(false)}
                className="p-1 text-slate-400 hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            {pwdError && (
              <div className="p-3 rounded-xl bg-rose-500/15 text-rose-300 text-xs">
                {pwdError}
              </div>
            )}
            {pwdSuccess && (
              <div className="p-3 rounded-xl bg-emerald-500/15 text-emerald-300 text-xs">
                {pwdSuccess}
              </div>
            )}
            <form onSubmit={handleChangePassword} className="space-y-3.5">
              <div>
                <label className="block text-xs text-slate-400 mb-1.5">
                  Mật khẩu hiện tại
                </label>
                <input
                  type="password"
                  required
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1.5">
                  Mật khẩu mới (tối thiểu 6 ký tự)
                </label>
                <input
                  type="password"
                  required
                  minLength={6}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#0c1017] border border-white/10 text-sm text-white"
                />
              </div>
              <button
                type="submit"
                className="w-full py-3 rounded-2xl bg-[#22c55e] text-slate-950 font-bold text-sm"
              >
                Xác nhận đổi mật khẩu
              </button>
            </form>
          </div>
        </div>
      )}

      {/* Categories Manager Modal */}
      {showCatModal && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/75 backdrop-blur-sm p-0 sm:p-4">
          <div className="w-full max-w-lg bg-[#141923] border border-white/10 rounded-t-3xl sm:rounded-3xl p-5 max-h-[88vh] flex flex-col space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-white">Quản lý danh mục</h3>
              <button
                onClick={() => setShowCatModal(false)}
                className="p-1 text-slate-400 hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="grid grid-cols-2 p-1 rounded-xl bg-[#0c1017]">
              <button
                type="button"
                onClick={() => {
                  setCatTab('EXPENSE');
                  setNewCatColor('#ef4444');
                }}
                className={`py-2 rounded-lg text-xs font-semibold ${
                  catTab === 'EXPENSE'
                    ? 'bg-rose-500 text-white'
                    : 'text-slate-400'
                }`}
              >
                Danh mục Chi tiêu
              </button>
              <button
                type="button"
                onClick={() => {
                  setCatTab('INCOME');
                  setNewCatColor('#22c55e');
                }}
                className={`py-2 rounded-lg text-xs font-semibold ${
                  catTab === 'INCOME'
                    ? 'bg-[#22c55e] text-slate-950'
                    : 'text-slate-400'
                }`}
              >
                Danh mục Thu nhập
              </button>
            </div>

            {/* Add New Category Form */}
            <form
              onSubmit={handleAddCategory}
              className="p-3.5 rounded-2xl bg-[#0c1017] border border-white/[0.06] space-y-2.5"
            >
              <div className="flex gap-2">
                <input
                  type="text"
                  placeholder="Tên danh mục mới..."
                  value={newCatName}
                  onChange={(e) => setNewCatName(e.target.value)}
                  className="flex-1 px-3 py-2 rounded-xl bg-[#141923] border border-white/10 text-xs text-white"
                />
                <select
                  value={newCatIcon}
                  onChange={(e) => setNewCatIcon(e.target.value)}
                  className="px-2.5 py-2 rounded-xl bg-[#141923] border border-white/10 text-xs text-slate-200"
                >
                  {AVAILABLE_ICONS.map((ic) => (
                    <option key={ic.key} value={ic.key}>
                      {ic.label}
                    </option>
                  ))}
                </select>
                <button
                  type="submit"
                  className="px-3 py-2 rounded-xl bg-[#22c55e] text-slate-950 text-xs font-bold flex items-center gap-1"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <Check className="w-3.5 h-3.5" />
                </button>
              </div>
              <div className="flex items-center gap-1.5">
                {AVAILABLE_COLORS.map((c) => (
                  <button
                    key={c}
                    type="button"
                    onClick={() => setNewCatColor(c)}
                    className={`w-5 h-5 rounded-full ${
                      newCatColor === c ? 'ring-2 ring-white scale-110' : ''
                    }`}
                    style={{ backgroundColor: c }}
                  />
                ))}
              </div>
            </form>

            {/* Category List */}
            <div className="flex-1 overflow-y-auto divide-y divide-white/[0.05]">
              {categories
                .filter((c) => c.type === catTab)
                .map((cat) => (
                  <div
                    key={cat.id}
                    className="py-2.5 px-1 flex items-center justify-between"
                  >
                    <div className="flex items-center gap-3">
                      <CategoryIcon icon={cat.icon} color={cat.color} size="sm" />
                      <span className="text-sm font-medium text-white">{cat.name}</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => handleDeleteCategory(cat.id)}
                      className="p-1.5 text-slate-500 hover:text-rose-400"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

