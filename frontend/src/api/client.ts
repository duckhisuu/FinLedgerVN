// Personal Finance & Group Bill Splitting API Client

const TOKEN_STORAGE_KEY = 'finledger_token';

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

export function setStoredToken(token: string | null) {
  if (token) {
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
  }
}

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  defaultCurrency: string;
  avatarColor: string;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  user: UserProfile;
}

export interface Wallet {
  id: string;
  name: string;
  subtype: 'CASH' | 'BANK' | 'EWALLET' | 'CREDIT_CARD' | 'SAVINGS';
  currency: string;
  balance: number;
  icon: string;
  color: string;
  bankCode?: string | null;
  accountNumber?: string | null;
  includeInTotal: boolean;
  allowNegative: boolean;
  createdAt: string;
}

export interface WalletsSummary {
  totalBalance: number;
  totalAssets: number;
  totalCreditDebt: number;
  wallets: Wallet[];
}

export interface Category {
  id: string;
  name: string;
  type: 'EXPENSE' | 'INCOME';
  icon: string;
  color: string;
}

export interface SplitItemBreakdown {
  categoryId: string;
  categoryName: string;
  categoryColor: string;
  amount: number;
}

export interface PersonalTransaction {
  id: string;
  type: 'EXPENSE' | 'INCOME' | 'TRANSFER' | 'OPENING_BALANCE' | 'ADJUSTMENT' | 'RECEIPT_SCAN';
  amount: number;
  description: string;
  transactionDate: string;
  createdAt: string;
  walletId: string;
  walletName: string;
  walletIcon: string;
  walletColor: string;
  categoryId?: string;
  categoryName?: string;
  categoryIcon?: string;
  categoryColor?: string;
  toWalletId?: string;
  toWalletName?: string;
  toWalletIcon?: string;
  toWalletColor?: string;
  cancelled: boolean;
  reversalOfId?: string | null;
  breakdown?: SplitItemBreakdown[];
}

export interface TransactionsFeed {
  month: string;
  totalIncome: number;
  totalExpense: number;
  netCashflow: number;
  transactions: PersonalTransaction[];
}

export interface DailyTrendPoint {
  day: number;
  dailySpent: number;
  dailyIncome: number;
  cumulativeSpent: number;
  cumulativeIncome: number;
  prev3MonthAvgCumulativeSpent: number;
  prev3MonthAvgCumulativeIncome: number;
}

export interface TopSpendingItem {
  categoryId: string;
  categoryName: string;
  icon: string;
  color: string;
  amount: number;
  percentage: number;
}

export interface BudgetAlert {
  budgetId: string;
  categoryName: string;
  amountLimit: number;
  spentAmount: number;
  percentage: number;
  exceeded: boolean;
}

export interface HomeOverview {
  totalBalance: number;
  currentMonth: string;
  wallets: Wallet[];
  monthSpent: number;
  monthIncome: number;
  prev3MonthAvgSpent: number;
  prev3MonthAvgIncome: number;
  dailyTrend: DailyTrendPoint[];
  topSpendingMonth: TopSpendingItem[];
  topSpendingWeek: TopSpendingItem[];
  recentTransactions: PersonalTransaction[];
  budgetAlerts: BudgetAlert[];
}

export interface BudgetItem {
  id: string;
  categoryId?: string | null;
  categoryName: string;
  categoryIcon: string;
  categoryColor: string;
  monthYear: string;
  amountLimit: number;
  spentAmount: number;
  remainingAmount: number;
  percentage: number;
  overBudget: boolean;
  note?: string | null;
}

export interface BudgetsOverview {
  monthYear: string;
  daysInMonth: number;
  daysRemaining: number;
  totalBudgetLimit: number;
  totalSpentThisMonth: number;
  totalRemaining: number;
  recommendedDailySpend: number;
  budgets: BudgetItem[];
}

export interface MonthlyCashflowItem {
  month: string;
  label: string;
  income: number;
  expense: number;
  net: number;
}

export interface FinancialReports {
  selectedMonth: string;
  netWorth: number;
  totalIncome: number;
  totalExpense: number;
  netSavings: number;
  savingsRatePercent: number;
  monthlyHistory: MonthlyCashflowItem[];
  expenseByCategory: TopSpendingItem[];
  incomeByCategory: TopSpendingItem[];
  wallets: Wallet[];
}

export interface ReceiptPreviewItem {
  productName: string;
  quantity: number;
  unitPrice: number;
  lineAmount: number;
  suggestedCategoryId: string;
  suggestedCategoryName: string;
}

export interface ReceiptPreview {
  merchantName: string;
  detectedTotalAmount: number;
  computedSum: number;
  items: ReceiptPreviewItem[];
}

// Group Split interfaces
export interface GroupMember {
  id: string;
  displayName: string;
  isGuest: boolean;
  bankCode?: string;
  bankAccountNumber?: string;
  memberLedgerAccountId?: string;
  netBalance: number;
}

export interface GroupExpenseShare {
  memberId: string;
  memberName?: string;
  shareAmount: number;
  rawWeight?: number;
}

export interface GroupExpense {
  id: string;
  title: string;
  totalAmount: number;
  splitStrategy: 'EQUAL' | 'EXACT' | 'PERCENTAGE' | 'SHARES';
  paidByMemberId: string;
  paidByMemberName?: string;
  createdAt: string;
  shares: GroupExpenseShare[];
}

export interface DebtSettlement {
  id: string;
  fromMemberId: string;
  fromMemberName: string;
  toMemberId: string;
  toMemberName: string;
  toBankCode?: string;
  toBankAccountNumber?: string;
  amount: number;
  settlementCode: string;
  status: 'PENDING' | 'COMPLETED' | 'CANCELLED';
  vietQrUrl: string;
  completedAt?: string | null;
}

export interface ExpenseGroupSummary {
  id: string;
  name: string;
  currency: string;
  shareToken: string;
  simplifyDebts: boolean;
  createdAt: string;
}

export interface PublicGroupShareResponse {
  groupId: string;
  groupName: string;
  currency: string;
  shareToken: string;
  simplifyDebts: boolean;
  createdAt: string;
  members: GroupMember[];
  expenses: GroupExpense[];
  netBalances: Record<string, number>;
  settlements: DebtSettlement[];
}

const num = (v: unknown): number => {
  if (typeof v === 'number') return v;
  if (typeof v === 'string') {
    const parsed = parseFloat(v);
    return Number.isNaN(parsed) ? 0 : parsed;
  }
  return 0;
};

async function extractErrorMessage(res: Response): Promise<string> {
  try {
    const text = await res.text();
    if (!text) return `Yêu cầu thất bại (${res.status})`;
    const json = JSON.parse(text);
    return json.detail || json.message || json.error || text;
  } catch {
    return `Yêu cầu thất bại (${res.status})`;
  }
}

async function fetchJson<T>(url: string, options?: RequestInit, skipAuth = false): Promise<T> {
  const token = getStoredToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options?.headers as Record<string, string>) || {}),
  };

  if (!skipAuth && token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(url, {
    ...options,
    headers,
  });

  if (res.status === 401 && !skipAuth) {
    setStoredToken(null);
    window.dispatchEvent(new Event('finledger:unauthorized'));
  }

  if (!res.ok) {
    throw new Error(await extractErrorMessage(res));
  }

  return res.json();
}

function normalizeWallet(w: any): Wallet {
  return {
    id: w.id,
    name: w.name,
    subtype: w.subtype || 'CASH',
    currency: w.currency || 'VND',
    balance: num(w.balance),
    icon: w.icon || 'wallet',
    color: w.color || '#22c55e',
    bankCode: w.bankCode || null,
    accountNumber: w.accountNumber || null,
    includeInTotal: w.includeInTotal ?? true,
    allowNegative: !!w.allowNegative,
    createdAt: w.createdAt || new Date().toISOString(),
  };
}

function normalizeTransaction(t: any): PersonalTransaction {
  return {
    id: t.id,
    type: t.type || 'EXPENSE',
    amount: num(t.amount),
    description: t.description || '',
    transactionDate: t.transactionDate || new Date().toISOString(),
    createdAt: t.createdAt || new Date().toISOString(),
    walletId: t.walletId,
    walletName: t.walletName || 'Ví',
    walletIcon: t.walletIcon || 'wallet',
    walletColor: t.walletColor || '#22c55e',
    categoryId: t.categoryId,
    categoryName: t.categoryName,
    categoryIcon: t.categoryIcon,
    categoryColor: t.categoryColor,
    toWalletId: t.toWalletId,
    toWalletName: t.toWalletName,
    toWalletIcon: t.toWalletIcon,
    toWalletColor: t.toWalletColor,
    cancelled: !!t.cancelled,
    reversalOfId: t.reversalOfId || null,
    breakdown: (t.breakdown || []).map((b: any) => ({
      categoryId: b.categoryId,
      categoryName: b.categoryName,
      categoryColor: b.categoryColor,
      amount: num(b.amount),
    })),
  };
}

function normalizeTopItem(i: any): TopSpendingItem {
  return {
    categoryId: i.categoryId,
    categoryName: i.categoryName,
    icon: i.icon || 'tag',
    color: i.color || '#ef4444',
    amount: num(i.amount),
    percentage: num(i.percentage),
  };
}

// =========================================================================
// 1. AUTHENTICATION API
// =========================================================================

export const authApi = {
  register: (payload: { fullName: string; email: string; password: string }) =>
    fetchJson<AuthResponse>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, true),

  login: (payload: { email: string; password: string }) =>
    fetchJson<AuthResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, true),

  me: () => fetchJson<UserProfile>('/api/v1/auth/me'),

  updateProfile: (payload: { fullName: string; avatarColor?: string }) =>
    fetchJson<UserProfile>('/api/v1/auth/profile', {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  changePassword: (payload: { currentPassword: string; newPassword: string }) =>
    fetchJson<{ message: string }>('/api/v1/auth/password', {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
};

// =========================================================================
// 2. PERSONAL FINANCE API
// =========================================================================

export const financeApi = {
  getOverview: async (): Promise<HomeOverview> => {
    const d = await fetchJson<any>('/api/v1/finance/overview');
    return {
      totalBalance: num(d.totalBalance),
      currentMonth: d.currentMonth,
      wallets: (d.wallets || []).map(normalizeWallet),
      monthSpent: num(d.monthSpent),
      monthIncome: num(d.monthIncome),
      prev3MonthAvgSpent: num(d.prev3MonthAvgSpent),
      prev3MonthAvgIncome: num(d.prev3MonthAvgIncome),
      dailyTrend: (d.dailyTrend || []).map((p: any) => ({
        day: Number(p.day),
        dailySpent: num(p.dailySpent),
        dailyIncome: num(p.dailyIncome),
        cumulativeSpent: num(p.cumulativeSpent),
        cumulativeIncome: num(p.cumulativeIncome),
        prev3MonthAvgCumulativeSpent: num(p.prev3MonthAvgCumulativeSpent),
        prev3MonthAvgCumulativeIncome: num(p.prev3MonthAvgCumulativeIncome),
      })),
      topSpendingMonth: (d.topSpendingMonth || []).map(normalizeTopItem),
      topSpendingWeek: (d.topSpendingWeek || []).map(normalizeTopItem),
      recentTransactions: (d.recentTransactions || []).map(normalizeTransaction),
      budgetAlerts: (d.budgetAlerts || []).map((a: any) => ({
        budgetId: a.budgetId,
        categoryName: a.categoryName,
        amountLimit: num(a.amountLimit),
        spentAmount: num(a.spentAmount),
        percentage: num(a.percentage),
        exceeded: !!a.exceeded,
      })),
    };
  },

  getReports: async (month?: string): Promise<FinancialReports> => {
    const q = month ? `?month=${encodeURIComponent(month)}` : '';
    const d = await fetchJson<any>(`/api/v1/finance/reports${q}`);
    return {
      selectedMonth: d.selectedMonth,
      netWorth: num(d.netWorth),
      totalIncome: num(d.totalIncome),
      totalExpense: num(d.totalExpense),
      netSavings: num(d.netSavings),
      savingsRatePercent: num(d.savingsRatePercent),
      monthlyHistory: (d.monthlyHistory || []).map((m: any) => ({
        month: m.month,
        label: m.label,
        income: num(m.income),
        expense: num(m.expense),
        net: num(m.net),
      })),
      expenseByCategory: (d.expenseByCategory || []).map(normalizeTopItem),
      incomeByCategory: (d.incomeByCategory || []).map(normalizeTopItem),
      wallets: (d.wallets || []).map(normalizeWallet),
    };
  },

  getWallets: async (): Promise<WalletsSummary> => {
    const d = await fetchJson<any>('/api/v1/finance/wallets');
    return {
      totalBalance: num(d.totalBalance),
      totalAssets: num(d.totalAssets),
      totalCreditDebt: num(d.totalCreditDebt),
      wallets: (d.wallets || []).map(normalizeWallet),
    };
  },

  createWallet: async (payload: {
    name: string;
    subtype: string;
    initialBalance?: number;
    icon?: string;
    color?: string;
    bankCode?: string;
    accountNumber?: string;
    includeInTotal?: boolean;
    allowNegative?: boolean;
  }): Promise<Wallet> => {
    const d = await fetchJson<any>('/api/v1/finance/wallets', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    return normalizeWallet(d);
  },

  updateWallet: async (
    id: string,
    payload: {
      name: string;
      icon?: string;
      color?: string;
      bankCode?: string;
      accountNumber?: string;
      includeInTotal?: boolean;
      allowNegative?: boolean;
      targetBalance?: number;
    }
  ): Promise<Wallet> => {
    const d = await fetchJson<any>(`/api/v1/finance/wallets/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
    return normalizeWallet(d);
  },

  archiveWallet: (id: string) =>
    fetchJson<{ message: string }>(`/api/v1/finance/wallets/${id}`, {
      method: 'DELETE',
    }),

  getCategories: () => fetchJson<Category[]>('/api/v1/finance/categories'),

  createCategory: (payload: { name: string; type: 'EXPENSE' | 'INCOME'; icon?: string; color?: string }) =>
    fetchJson<Category>('/api/v1/finance/categories', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  archiveCategory: (id: string) =>
    fetchJson<{ message: string }>(`/api/v1/finance/categories/${id}`, {
      method: 'DELETE',
    }),

  getTransactions: async (params?: {
    month?: string;
    walletId?: string;
    type?: string;
    search?: string;
    includeCancelled?: boolean;
  }): Promise<TransactionsFeed> => {
    const sp = new URLSearchParams();
    if (params?.month) sp.set('month', params.month);
    if (params?.walletId) sp.set('walletId', params.walletId);
    if (params?.type && params.type !== 'ALL') sp.set('type', params.type);
    if (params?.search) sp.set('search', params.search);
    if (params?.includeCancelled) sp.set('includeCancelled', 'true');
    const qs = sp.toString();
    const d = await fetchJson<any>(`/api/v1/finance/transactions${qs ? `?${qs}` : ''}`);
    return {
      month: d.month,
      totalIncome: num(d.totalIncome),
      totalExpense: num(d.totalExpense),
      netCashflow: num(d.netCashflow),
      transactions: (d.transactions || []).map(normalizeTransaction),
    };
  },

  recordTransaction: async (payload: {
    type: 'EXPENSE' | 'INCOME' | 'TRANSFER';
    amount: number;
    walletId: string;
    categoryId?: string;
    toWalletId?: string;
    description?: string;
    transactionDate?: string;
  }): Promise<PersonalTransaction> => {
    const d = await fetchJson<any>('/api/v1/finance/transactions', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    return normalizeTransaction(d);
  },

  updateTransaction: async (
    id: string,
    payload: {
      type: 'EXPENSE' | 'INCOME' | 'TRANSFER';
      amount: number;
      walletId: string;
      categoryId?: string;
      toWalletId?: string;
      description?: string;
      transactionDate?: string;
    }
  ): Promise<PersonalTransaction> => {
    const d = await fetchJson<any>(`/api/v1/finance/transactions/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
    return normalizeTransaction(d);
  },

  cancelTransaction: (id: string, reason?: string) => {
    const q = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    return fetchJson<{ message: string }>(`/api/v1/finance/transactions/${id}${q}`, {
      method: 'DELETE',
    });
  },

  getBudgets: async (month?: string): Promise<BudgetsOverview> => {
    const q = month ? `?month=${encodeURIComponent(month)}` : '';
    const d = await fetchJson<any>(`/api/v1/finance/budgets${q}`);
    return {
      monthYear: d.monthYear,
      daysInMonth: Number(d.daysInMonth || 30),
      daysRemaining: Number(d.daysRemaining || 0),
      totalBudgetLimit: num(d.totalBudgetLimit),
      totalSpentThisMonth: num(d.totalSpentThisMonth),
      totalRemaining: num(d.totalRemaining),
      recommendedDailySpend: num(d.recommendedDailySpend),
      budgets: (d.budgets || []).map((b: any) => ({
        id: b.id,
        categoryId: b.categoryId || null,
        categoryName: b.categoryName,
        categoryIcon: b.categoryIcon || 'tag',
        categoryColor: b.categoryColor || '#ef4444',
        monthYear: b.monthYear,
        amountLimit: num(b.amountLimit),
        spentAmount: num(b.spentAmount),
        remainingAmount: num(b.remainingAmount),
        percentage: num(b.percentage),
        overBudget: !!b.overBudget,
        note: b.note || null,
      })),
    };
  },

  upsertBudget: (payload: {
    categoryId?: string | null;
    monthYear: string;
    amountLimit: number;
    note?: string;
  }) =>
    fetchJson<BudgetItem>('/api/v1/finance/budgets', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  deleteBudget: (id: string) =>
    fetchJson<{ message: string }>(`/api/v1/finance/budgets/${id}`, {
      method: 'DELETE',
    }),

  scanReceipt: async (file: File): Promise<ReceiptPreview> => {
    const token = getStoredToken();
    const formData = new FormData();
    formData.append('image', file);
    const res = await fetch('/api/v1/finance/receipts/scan', {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      body: formData,
    });
    if (!res.ok) {
      throw new Error(await extractErrorMessage(res));
    }
    const d = await res.json();
    return {
      merchantName: d.merchantName || 'Hóa đơn mua sắm',
      detectedTotalAmount: num(d.detectedTotalAmount),
      computedSum: num(d.computedSum),
      items: (d.items || []).map((it: any) => ({
        productName: it.productName,
        quantity: num(it.quantity) || 1,
        unitPrice: num(it.unitPrice),
        lineAmount: num(it.lineAmount),
        suggestedCategoryId: it.suggestedCategoryId,
        suggestedCategoryName: it.suggestedCategoryName,
      })),
    };
  },

  confirmReceipt: async (payload: {
    walletId: string;
    merchantName?: string;
    transactionDate?: string;
    actualPaidAmount?: number;
    items: Array<{ productName: string; lineAmount: number; categoryId: string }>;
  }): Promise<PersonalTransaction> => {
    const d = await fetchJson<any>('/api/v1/finance/receipts/confirm', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    return normalizeTransaction(d);
  },
};

// =========================================================================
// 3. GROUP BILL SPLITTING & PUBLIC SHARE API
// =========================================================================

function normalizePublicGroup(data: any): PublicGroupShareResponse {
  const netBalances = data.netBalances || {};
  const members = (data.members || []).map((m: any) => {
    const bal = m.netBalance !== undefined ? m.netBalance : (netBalances[m.id] ?? 0);
    return {
      id: m.id,
      displayName: m.displayName || m.name || 'Thành viên',
      isGuest: !!m.isGuest,
      bankCode: m.bankCode || '',
      bankAccountNumber: m.bankAccountNumber || '',
      memberLedgerAccountId: m.memberLedgerAccountId || '',
      netBalance: num(bal),
    };
  });

  const expenses = (data.expenses || []).map((e: any) => ({
    id: e.id,
    title: e.title || '',
    totalAmount: num(e.totalAmount),
    splitStrategy: e.splitStrategy || 'EQUAL',
    paidByMemberId: e.paidByMemberId,
    paidByMemberName: e.paidByMemberName || '',
    createdAt: e.createdAt || new Date().toISOString(),
    shares: (e.shares || []).map((s: any) => ({
      memberId: s.memberId,
      memberName: s.memberName,
      shareAmount: num(s.shareAmount),
      rawWeight: s.rawWeight,
    })),
  }));

  const settlements = (data.settlements || []).map((s: any) => ({
    id: s.id,
    fromMemberId: s.fromMemberId,
    fromMemberName: s.fromMemberName || '',
    toMemberId: s.toMemberId,
    toMemberName: s.toMemberName || '',
    toBankCode: s.toBankCode || '',
    toBankAccountNumber: s.toBankAccountNumber || '',
    amount: num(s.amount),
    settlementCode: s.settlementCode || '',
    status: s.status || 'PENDING',
    vietQrUrl: s.vietQrUrl || '',
    completedAt: s.completedAt || null,
  }));

  return {
    groupId: data.groupId || data.id,
    groupName: data.groupName || data.name || 'Nhóm chi tiêu',
    currency: data.currency || 'VND',
    shareToken: data.shareToken || '',
    simplifyDebts: !!data.simplifyDebts,
    createdAt: data.createdAt || new Date().toISOString(),
    members,
    expenses,
    netBalances,
    settlements,
  };
}

export interface SplitShareItem {
  memberId: string;
  specifiedAmount?: number;
  percentage?: number;
  shares?: number;
}

export const groupsApi = {
  getGroups: () => fetchJson<ExpenseGroupSummary[]>('/api/v1/groups'),

  getGroup: async (id: string): Promise<PublicGroupShareResponse> => {
    const data = await fetchJson<any>(`/api/v1/groups/${id}`);
    return normalizePublicGroup(data);
  },

  createGroup: (data: { name: string; currency?: string; simplifyDebts?: boolean }) =>
    fetchJson<ExpenseGroupSummary>('/api/v1/groups', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  addMember: (
    groupId: string,
    data: { displayName: string; isGuest?: boolean; bankCode?: string; bankAccountNumber?: string }
  ) =>
    fetchJson<GroupMember>(`/api/v1/groups/${groupId}/members`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  createExpense: (
    groupId: string,
    data: {
      title: string;
      paidByMemberId: string;
      totalAmount: number;
      splitStrategy: 'EQUAL' | 'EXACT' | 'PERCENTAGE' | 'SHARES';
      shares: SplitShareItem[];
    }
  ) => {
    const splitInputs = data.shares.map((s) => {
      let weight: number | null = null;
      if (data.splitStrategy === 'EXACT') weight = s.specifiedAmount ?? null;
      else if (data.splitStrategy === 'PERCENTAGE') weight = s.percentage ?? null;
      else if (data.splitStrategy === 'SHARES') weight = s.shares ?? null;
      return {
        memberId: s.memberId,
        amountOrWeight: weight,
      };
    });

    return fetchJson<GroupExpense>(`/api/v1/groups/${groupId}/expenses`, {
      method: 'POST',
      body: JSON.stringify({
        title: data.title,
        paidByMemberId: data.paidByMemberId,
        totalAmount: data.totalAmount,
        splitStrategy: data.splitStrategy,
        splitInputs,
      }),
    });
  },

  generatePlan: (groupId: string) =>
    fetchJson<DebtSettlement[]>(`/api/v1/groups/${groupId}/settlements/recalculate`, {
      method: 'POST',
    }),

  settleDebt: (settlementId: string) =>
    fetchJson<any>(`/api/v1/groups/settlements/${settlementId}/settle`, {
      method: 'POST',
    }),
};

export const publicShareApi = {
  getPublicGroup: async (shareToken: string): Promise<PublicGroupShareResponse> => {
    const data = await fetchJson<any>(`/api/v1/public/groups/${shareToken}`, undefined, true);
    return normalizePublicGroup(data);
  },
  getStreamUrl: (shareToken: string) => `/api/v1/public/groups/${shareToken}/stream`,
};
