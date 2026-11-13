import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { AppShell } from './components/AppShell';
import { TransactionModal } from './components/TransactionModal';
import { AuthPage } from './pages/AuthPage';
import { LandingPage } from './pages/LandingPage';
import { HomePage } from './pages/HomePage';
import { TransactionsPage } from './pages/TransactionsPage';
import { WalletsPage } from './pages/WalletsPage';
import { BudgetsPage } from './pages/BudgetsPage';
import { ReportsPage } from './pages/ReportsPage';
import { GroupSplitPage } from './pages/GroupSplitPage';
import { AccountPage } from './pages/AccountPage';
import { PublicSharePage } from './pages/PublicSharePage';

function ProtectedLayout() {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#090A0F] flex flex-col items-center justify-center gap-3 text-slate-300">
        <Loader2 className="w-8 h-8 animate-spin text-[#22c55e]" />
        <p className="text-xs font-medium text-slate-400">Đang tải dữ liệu tài chính...</p>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  return (
    <>
      <AppShell>
        <Outlet />
      </AppShell>
      <TransactionModal />
    </>
  );
}

function GuestRoute() {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#090A0F] flex items-center justify-center">
        <Loader2 className="w-8 h-8 animate-spin text-[#22c55e]" />
      </div>
    );
  }

  if (user) {
    return <Navigate to="/" replace />;
  }

  return <AuthPage />;
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public Landing & Marketing Page */}
          <Route path="/landing" element={<LandingPage />} />

          {/* Public Authentication & Public Bill Share */}
          <Route path="/login" element={<GuestRoute />} />
          <Route path="/share/:shareToken" element={<PublicSharePage />} />

          {/* Authenticated Personal Finance Workspace */}
          <Route element={<ProtectedLayout />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/transactions" element={<TransactionsPage />} />
            <Route path="/wallets" element={<WalletsPage />} />
            <Route path="/budgets" element={<BudgetsPage />} />
            <Route path="/reports" element={<ReportsPage />} />
            <Route path="/groups" element={<GroupSplitPage />} />
            <Route path="/account" element={<AccountPage />} />
          </Route>

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
