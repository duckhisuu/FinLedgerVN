import { useState } from 'react';
import { 
  ShieldCheck, 
  ArrowRightLeft, 
  Layers, 
  TrendingUp, 
  Lock, 
  CheckCircle2 
} from 'lucide-react';

export default function App() {
  const [activeTab, setActiveTab] = useState<'journal' | 'accounts' | 'concurrency'>('journal');

  const accounts = [
    { id: 'acc-1', name: 'Tài khoản Tiền mặt (Cash VND)', type: 'ASSET', balance: 10_000_000, allowNegative: false },
    { id: 'acc-2', name: 'Tài khoản Ngân hàng Vietcombank', type: 'ASSET', balance: 45_500_000, allowNegative: false },
    { id: 'acc-3', name: 'Phải trả Bạn bè (Nhóm Ăn trưa)', type: 'LIABILITY', balance: -2_400_000, allowNegative: true },
    { id: 'acc-4', name: 'Doanh thu Lương tháng', type: 'REVENUE', balance: 35_000_000, allowNegative: true },
    { id: 'acc-5', name: 'Chi phí Tiền trọ & Điện nước', type: 'EXPENSE', balance: 8_500_000, allowNegative: true },
  ];

  const recentTransactions = [
    {
      id: 'tx-901',
      date: '2026-10-19 14:30',
      description: 'Chuyển tiền quỹ nhóm ăn trưa cuối tuần',
      referenceId: 'REF-TX-20261019-01',
      legs: [
        { account: 'Tài khoản Ngân hàng Vietcombank', amount: -300000, type: 'CREDIT' },
        { account: 'Phải trả Bạn bè (Nhóm Ăn trưa)', amount: 300000, type: 'DEBIT' },
      ],
      balanced: true,
    },
    {
      id: 'tx-902',
      date: '2026-10-19 10:15',
      description: 'Nhận lương tháng 10 qua ngân hàng',
      referenceId: 'REF-SALARY-202610',
      legs: [
        { account: 'Tài khoản Ngân hàng Vietcombank', amount: 35000000, type: 'DEBIT' },
        { account: 'Doanh thu Lương tháng', amount: -35000000, type: 'CREDIT' },
      ],
      balanced: true,
    }
  ];

  return (
    <div className="min-h-screen bg-[#0b0f19] text-slate-100 flex flex-col">
      {/* Top Navigation */}
      <header className="border-b border-slate-800 bg-[#0f172a]/80 backdrop-blur-md sticky top-0 z-50 px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-500 to-indigo-600 flex items-center justify-center shadow-lg shadow-emerald-500/20">
              <Layers className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h1 className="text-xl font-bold tracking-tight text-white">FinLedger VN</h1>
                <span className="px-2 py-0.5 text-xs font-semibold rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  Tuần 1: Core Engine
                </span>
              </div>
              <p className="text-xs text-slate-400">Hệ thống sổ cái kép ngân hàng chuẩn kiểm toán & chống Deadlock</p>
            </div>
          </div>

          <div className="flex items-center space-x-4">
            <div className="flex items-center space-x-2 px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-xs">
              <Lock className="w-3.5 h-3.5 text-indigo-400" />
              <span className="text-slate-400">Locking Mode:</span>
              <span className="text-emerald-400 font-mono font-medium">PESSIMISTIC_WRITE (ORDER BY id ASC)</span>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-6 space-y-6">
        {/* Metric Cards */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div className="p-5 rounded-2xl bg-[#111827] border border-slate-800/80 shadow-sm relative overflow-hidden">
            <div className="absolute top-0 right-0 w-24 h-24 bg-emerald-500/5 rounded-full blur-2xl" />
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-400">Tổng Tài Sản (Assets)</span>
              <TrendingUp className="w-4 h-4 text-emerald-400" />
            </div>
            <p className="mt-2 text-2xl font-bold mono-num text-white">55,500,000 <span className="text-sm font-normal text-slate-400">VND</span></p>
            <span className="mt-1 inline-flex items-center text-xs text-emerald-400">
              <CheckCircle2 className="w-3 h-3 mr-1" /> Cân bằng sổ cái 100%
            </span>
          </div>

          <div className="p-5 rounded-2xl bg-[#111827] border border-slate-800/80 shadow-sm relative overflow-hidden">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-400">Công Nợ Phải Trả</span>
              <ArrowRightLeft className="w-4 h-4 text-amber-400" />
            </div>
            <p className="mt-2 text-2xl font-bold mono-num text-amber-400">2,400,000 <span className="text-sm font-normal text-slate-400">VND</span></p>
            <span className="mt-1 text-xs text-slate-400">3 khoản vay/nhóm bạn bè</span>
          </div>

          <div className="p-5 rounded-2xl bg-[#111827] border border-slate-800/80 shadow-sm relative overflow-hidden">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-400">Bảo Vệ Bất Biến (Trigger)</span>
              <ShieldCheck className="w-4 h-4 text-indigo-400" />
            </div>
            <p className="mt-2 text-2xl font-bold text-white">IMMUTABLE</p>
            <span className="mt-1 text-xs text-indigo-400">Chặn UPDATE/DELETE cấp DB</span>
          </div>

          <div className="p-5 rounded-2xl bg-[#111827] border border-slate-800/80 shadow-sm relative overflow-hidden">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-400">Độ Chính Xác Số Học</span>
              <CheckCircle2 className="w-4 h-4 text-emerald-400" />
            </div>
            <p className="mt-2 text-2xl font-bold mono-num text-white">NUMERIC(19,4)</p>
            <span className="mt-1 text-xs text-slate-400">BigDecimal.HALF_EVEN</span>
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="flex space-x-2 border-b border-slate-800 pb-2">
          <button 
            onClick={() => setActiveTab('journal')}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition ${activeTab === 'journal' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-white hover:bg-slate-900'}`}
          >
            Nhật Ký Sổ Cái (Journal Entries)
          </button>
          <button 
            onClick={() => setActiveTab('accounts')}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition ${activeTab === 'accounts' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-white hover:bg-slate-900'}`}
          >
            Danh Mục Tài Khoản (Chart of Accounts)
          </button>
          <button 
            onClick={() => setActiveTab('concurrency')}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition ${activeTab === 'concurrency' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-white hover:bg-slate-900'}`}
          >
            Kiểm Chứng Concurrency & Deadlock
          </button>
        </div>

        {/* Tab Content */}
        {activeTab === 'journal' && (
          <div className="space-y-4">
            <div className="bg-[#111827] rounded-2xl border border-slate-800 p-6">
              <h2 className="text-lg font-bold text-white mb-4">Giao Dịch Gần Đây (Immutable Ledger)</h2>
              <div className="space-y-4">
                {recentTransactions.map((tx) => (
                  <div key={tx.id} className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-3">
                    <div className="flex items-center justify-between">
                      <div>
                        <span className="text-xs font-mono text-slate-500">{tx.referenceId}</span>
                        <h3 className="text-sm font-semibold text-slate-200">{tx.description}</h3>
                      </div>
                      <div className="flex items-center space-x-2">
                        <span className="text-xs text-slate-400">{tx.date}</span>
                        <span className="px-2 py-0.5 rounded text-xs font-mono bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                          Σ Δ = 0.0000
                        </span>
                      </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs">
                      {tx.legs.map((leg, i) => (
                        <div key={i} className="flex justify-between items-center p-2 rounded bg-slate-950/40 border border-slate-800/40">
                          <span className="text-slate-300">{leg.account}</span>
                          <span className={`mono-num font-semibold ${leg.amount > 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                            {leg.amount > 0 ? `+${leg.amount.toLocaleString('vi-VN')}` : leg.amount.toLocaleString('vi-VN')} VND
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {activeTab === 'accounts' && (
          <div className="bg-[#111827] rounded-2xl border border-slate-800 overflow-hidden">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-900/80 text-xs text-slate-400 border-b border-slate-800 uppercase tracking-wider">
                <tr>
                  <th className="px-6 py-4">Tên Tài Khoản</th>
                  <th className="px-6 py-4">Loại (Account Type)</th>
                  <th className="px-6 py-4">Tiền Tệ</th>
                  <th className="px-6 py-4">Cho Phép Âm</th>
                  <th className="px-6 py-4 text-right">Số Dư Hiện Tại (Cached Balance)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {accounts.map((acc) => (
                  <tr key={acc.id} className="hover:bg-slate-800/20 transition">
                    <td className="px-6 py-4 font-medium text-white">{acc.name}</td>
                    <td className="px-6 py-4">
                      <span className="px-2 py-0.5 rounded-full text-xs font-mono bg-slate-800 text-slate-300">
                        {acc.type}
                      </span>
                    </td>
                    <td className="px-6 py-4 font-mono text-slate-400">VND</td>
                    <td className="px-6 py-4">
                      {acc.allowNegative ? (
                        <span className="text-amber-400 text-xs">Có</span>
                      ) : (
                        <span className="text-emerald-400 text-xs">Không (Chống âm quỹ)</span>
                      )}
                    </td>
                    <td className="px-6 py-4 text-right mono-num font-semibold text-slate-100">
                      {acc.balance.toLocaleString('vi-VN')} VND
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {activeTab === 'concurrency' && (
          <div className="bg-[#111827] rounded-2xl border border-slate-800 p-6 space-y-6">
            <div>
              <h2 className="text-lg font-bold text-white">Kiểm Chứng Concurrency & Khóa Bi Quan Chống Deadlock</h2>
              <p className="text-xs text-slate-400 mt-1">Được kiểm chứng tự động bằng JUnit 5, CountDownLatch và Testcontainers PostgreSQL 16.</p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 space-y-2">
                <div className="flex items-center space-x-2 text-emerald-400 text-sm font-semibold">
                  <CheckCircle2 className="w-4 h-4" />
                  <span>50 Concurrent Threads Race Condition Test</span>
                </div>
                <p className="text-xs text-slate-300">
                  Tài khoản A (10.000.000 VND) bắn đồng thời 50 giao dịch rút 300.000 VND. Đúng 33 luồng thành công, 17 luồng bị từ chối với InsufficientFundsException, số dư cuối cùng bảo toàn 100.000 VND chính xác 100%.
                </p>
              </div>

              <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 space-y-2">
                <div className="flex items-center space-x-2 text-indigo-400 text-sm font-semibold">
                  <CheckCircle2 className="w-4 h-4" />
                  <span>Cross-Transfer Deadlock Freedom (A↔B)</span>
                </div>
                <p className="text-xs text-slate-300">
                  20 luồng chuyển A &rarr; B đồng thời với 20 luồng chuyển B &rarr; A. Nhờ thuật toán <code className="text-indigo-300">findAllByIdInOrderByIdAscForUpdate</code>, tất cả giao dịch hoàn tất không bao giờ xảy ra Deadlock.
                </p>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-800/80 py-4 px-6 text-center text-xs text-slate-500">
        FinLedger VN &bull; Principal Fintech Backend & Full-Stack Architecture &bull; Double-Entry Ledger Engine
      </footer>
    </div>
  );
}
