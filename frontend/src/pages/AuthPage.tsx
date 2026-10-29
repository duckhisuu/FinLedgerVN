import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Wallet,
  Eye,
  EyeOff,
  Loader2,
  CheckCircle2,
  PieChart,
  ShieldCheck,
  ReceiptText,
} from 'lucide-react';
import { authApi } from '../api/client';
import { useAuth } from '../context/AuthContext';

export function AuthPage() {
  const { loginWithResponse } = useAuth();
  const navigate = useNavigate();

  const [tab, setTab] = useState<'LOGIN' | 'REGISTER'>('LOGIN');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res =
        tab === 'REGISTER'
          ? await authApi.register({
              fullName: fullName.trim(),
              email: email.trim(),
              password,
            })
          : await authApi.login({
              email: email.trim(),
              password,
            });

      loginWithResponse(res.token, res.user);
      navigate('/', { replace: true });
    } catch (err: any) {
      setError(err.message || 'Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#090c10] text-slate-100 flex flex-col lg:flex-row">
      {/* Left Hero Panel (Desktop) */}
      <div className="hidden lg:flex lg:w-1/2 relative overflow-hidden bg-gradient-to-br from-[#0d141f] via-[#090c10] to-[#0c1f17] p-12 flex-col justify-between border-r border-white/[0.06]">
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-2xl bg-[#22c55e] flex items-center justify-center shadow-lg shadow-emerald-500/25">
            <Wallet className="w-6 h-6 text-slate-950 stroke-[2.4]" />
          </div>
          <div>
            <span className="text-xl font-extrabold tracking-tight text-white">FinLedger</span>
            <p className="text-xs text-slate-400">Quản lý tài chính cá nhân thông minh</p>
          </div>
        </div>

        <div className="max-w-md space-y-6">
          <h1 className="text-4xl font-extrabold tracking-tight text-white leading-tight">
            Kiểm soát dòng tiền, ví cá nhân và ngân sách mỗi ngày.
          </h1>
          <p className="text-slate-400 text-base leading-relaxed">
            Ghi chép thu chi tức thì, theo dõi xu hướng tiêu dùng trực quan, lập kế hoạch ngân sách theo tháng và quét hóa đơn tự động.
          </p>

          <div className="space-y-3.5 pt-2">
            {[
              {
                icon: Wallet,
                text: 'Quản lý đa ví: Tiền mặt, Ngân hàng, Ví điện tử, Thẻ tín dụng',
              },
              {
                icon: PieChart,
                text: 'Báo cáo xu hướng chi tiêu & cảnh báo hạn mức ngân sách',
              },
              {
                icon: ReceiptText,
                text: 'Quét hóa đơn siêu thị & chia tiền nhóm qua mã VietQR',
              },
              {
                icon: ShieldCheck,
                text: 'Số liệu chính xác tuyệt đối, bảo mật riêng tư theo từng tài khoản',
              },
            ].map((feat, i) => {
              const Icon = feat.icon;
              return (
                <div key={i} className="flex items-center gap-3 text-sm text-slate-300">
                  <div className="w-8 h-8 rounded-xl bg-emerald-500/15 text-[#22c55e] flex items-center justify-center shrink-0">
                    <Icon className="w-4 h-4" />
                  </div>
                  <span>{feat.text}</span>
                </div>
              );
            })}
          </div>
        </div>

        <p className="text-xs text-slate-500">
          © {new Date().getFullYear()} FinLedger VN — Tài chính cá nhân hiện đại.
        </p>
      </div>

      {/* Right Auth Form */}
      <div className="flex-1 flex items-center justify-center p-6 sm:p-10">
        <div className="w-full max-w-md space-y-6">
          {/* Mobile Brand Header */}
          <div className="flex lg:hidden items-center justify-center gap-3 mb-2">
            <div className="w-11 h-11 rounded-2xl bg-[#22c55e] flex items-center justify-center shadow-lg shadow-emerald-500/25">
              <Wallet className="w-6 h-6 text-slate-950 stroke-[2.4]" />
            </div>
            <div>
              <span className="text-xl font-extrabold text-white">FinLedger</span>
              <p className="text-xs text-slate-400">Quản lý tài chính cá nhân</p>
            </div>
          </div>

          <div className="bg-[#141923] border border-white/[0.07] rounded-3xl p-6 sm:p-8 shadow-2xl">
            {/* Segmented Switch */}
            <div className="grid grid-cols-2 p-1 rounded-2xl bg-[#0b0f17] border border-white/[0.05] mb-6">
              <button
                type="button"
                onClick={() => {
                  setTab('LOGIN');
                  setError(null);
                }}
                className={`py-2.5 rounded-xl text-sm font-semibold transition-all ${
                  tab === 'LOGIN'
                    ? 'bg-[#22c55e] text-slate-950 shadow-sm'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                Đăng nhập
              </button>
              <button
                type="button"
                onClick={() => {
                  setTab('REGISTER');
                  setError(null);
                }}
                className={`py-2.5 rounded-xl text-sm font-semibold transition-all ${
                  tab === 'REGISTER'
                    ? 'bg-[#22c55e] text-slate-950 shadow-sm'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                Tạo tài khoản
              </button>
            </div>

            <div className="mb-6">
              <h2 className="text-xl font-bold text-white">
                {tab === 'LOGIN' ? 'Chào mừng bạn trở lại' : 'Bắt đầu quản lý tài chính'}
              </h2>
              <p className="text-xs text-slate-400 mt-1">
                {tab === 'LOGIN'
                  ? 'Đăng nhập để xem số dư ví và báo cáo chi tiêu của bạn.'
                  : 'Ví Tiền mặt và bộ danh mục thu chi chuẩn Việt Nam sẽ được khởi tạo sẵn cho bạn.'}
              </p>
            </div>

            {error && (
              <div className="mb-4 p-3.5 rounded-2xl bg-rose-500/10 border border-rose-500/30 text-xs text-rose-300">
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              {tab === 'REGISTER' && (
                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1.5">
                    Họ và tên
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="Nguyễn Văn A"
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    className="w-full px-4 py-3 rounded-xl bg-[#0b0f17] border border-white/10 text-sm text-white placeholder:text-slate-500 focus:outline-none focus:border-[#22c55e]"
                  />
                </div>
              )}

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Địa chỉ Email
                </label>
                <input
                  type="email"
                  required
                  placeholder="ban@email.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-4 py-3 rounded-xl bg-[#0b0f17] border border-white/10 text-sm text-white placeholder:text-slate-500 focus:outline-none focus:border-[#22c55e]"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Mật khẩu
                </label>
                <div className="relative">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    required
                    minLength={6}
                    placeholder="Tối thiểu 6 ký tự"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="w-full px-4 py-3 pr-11 rounded-xl bg-[#0b0f17] border border-white/10 text-sm text-white placeholder:text-slate-500 focus:outline-none focus:border-[#22c55e]"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((s) => !s)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-200 p-1"
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full py-3.5 rounded-2xl bg-[#22c55e] hover:bg-[#1eb053] disabled:opacity-60 text-slate-950 font-bold text-sm flex items-center justify-center gap-2 shadow-lg shadow-emerald-500/20 transition-all mt-2"
              >
                {loading ? (
                  <Loader2 className="w-5 h-5 animate-spin" />
                ) : (
                  <>
                    <CheckCircle2 className="w-4 h-4" />
                    <span>{tab === 'LOGIN' ? 'Đăng nhập vào sổ' : 'Tạo tài khoản ngay'}</span>
                  </>
                )}
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}

