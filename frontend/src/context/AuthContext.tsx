import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import {
  authApi,
  getStoredToken,
  setStoredToken,
  UserProfile,
  PersonalTransaction,
} from '../api/client';

export interface TransactionModalPreset {
  mode?: 'EXPENSE' | 'INCOME' | 'TRANSFER' | 'OCR';
  editTransaction?: PersonalTransaction;
  defaultWalletId?: string;
}

interface AuthContextValue {
  user: UserProfile | null;
  isLoading: boolean;
  hideBalance: boolean;
  toggleHideBalance: () => void;
  loginWithResponse: (token: string, user: UserProfile) => void;
  refreshUser: () => Promise<void>;
  logout: () => void;
  dataVersion: number;
  triggerDataRefresh: () => void;
  txModalPreset: TransactionModalPreset | null;
  openTransactionModal: (preset?: TransactionModalPreset) => void;
  closeTransactionModal: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const HIDE_BALANCE_KEY = 'finledger_hide_balance';

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [hideBalance, setHideBalance] = useState<boolean>(() => {
    return localStorage.getItem(HIDE_BALANCE_KEY) === 'true';
  });
  const [dataVersion, setDataVersion] = useState<number>(0);
  const [txModalPreset, setTxModalPreset] = useState<TransactionModalPreset | null>(null);

  const triggerDataRefresh = useCallback(() => {
    setDataVersion((v) => v + 1);
  }, []);

  const toggleHideBalance = useCallback(() => {
    setHideBalance((prev) => {
      const next = !prev;
      localStorage.setItem(HIDE_BALANCE_KEY, String(next));
      return next;
    });
  }, []);

  const loginWithResponse = useCallback((token: string, profile: UserProfile) => {
    setStoredToken(token);
    setUser(profile);
  }, []);

  const logout = useCallback(() => {
    setStoredToken(null);
    setUser(null);
    setTxModalPreset(null);
  }, []);

  const refreshUser = useCallback(async () => {
    const token = getStoredToken();
    if (!token) {
      setUser(null);
      setIsLoading(false);
      return;
    }
    try {
      const profile = await authApi.me();
      setUser(profile);
    } catch {
      setStoredToken(null);
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshUser();
  }, [refreshUser]);

  useEffect(() => {
    const handleUnauthorized = () => {
      setUser(null);
      setTxModalPreset(null);
    };
    window.addEventListener('finledger:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('finledger:unauthorized', handleUnauthorized);
  }, []);

  const openTransactionModal = useCallback((preset?: TransactionModalPreset) => {
    setTxModalPreset(preset || { mode: 'EXPENSE' });
  }, []);

  const closeTransactionModal = useCallback(() => {
    setTxModalPreset(null);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        hideBalance,
        toggleHideBalance,
        loginWithResponse,
        refreshUser,
        logout,
        dataVersion,
        triggerDataRefresh,
        txModalPreset,
        openTransactionModal,
        closeTransactionModal,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return ctx;
}

