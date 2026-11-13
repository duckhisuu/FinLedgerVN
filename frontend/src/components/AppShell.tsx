import React from 'react';
import { MainLayout } from './layout/MainLayout';

export function AppShell({ children }: { children: React.ReactNode }) {
  return <MainLayout>{children}</MainLayout>;
}
