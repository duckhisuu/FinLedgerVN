import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import {
  Activity,
  AlertTriangle,
  ArrowDownLeft,
  ArrowLeftRight,
  ArrowUpRight,
  Check,
  CheckCircle2,
  Copy,
  Eye,
  EyeOff,
  KeyRound,
  Landmark,
  Link2,
  Loader2,
  RefreshCw,
  ShieldCheck,
  Smartphone,
} from 'lucide-react';
import {
  ConnectedSourceTelemetry,
  SyncedEventLog,
  Wallet,
  financeApi,
  getApiOrigin,
  ingestionApi,
} from '../api/client';
import { useAuth } from '../context/AuthContext';
import { AnimatedCurrency } from '../components/ui/AnimatedCurrency';

const formatVndPlain = (val: number) =>
  new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(Math.round(val));

export function BankConnectionsPage() {
  const { hideBalance, dataVersion, triggerDataRefresh } = useAuth();

  const [sources, setSources] = useState<ConnectedSourceTelemetry[]>([]);
  const [events, setEvents] = useState<SyncedEventLog[]>([]);
  const [wallets, setWallets] = useState<Wallet[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  // Connect / Configure Source Modal
  const [modalProvider, setModalProvider] = useState<'VCB' | 'MOMO' | null>(null);
  const [modalIdentifier, setModalIdentifier] = useState('');
  const [modalWalletId, setModalWalletId] = useState<string>('');
  const [modalWalletName, setModalWalletName] = useState('');
  const [modalInitialBalance, setModalInitialBalance] = useState('');
  const [savingSource, setSavingSource] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  // Bridge credentials & setup tabs
  const [showToken, setShowToken] = useState(false);
  const [copiedKey, setCopiedKey] = useState<string | null>(null);
  const [rotatingToken, setRotatingToken] = useState(false);
  const [tokenError, setTokenError] = useState<string | null>(null);
  const [guideTab, setGuideTab] = useState<'IOS' | 'ANDROID' | 'EMAIL'>('IOS');

  const loadAll = async (silent = false) => {
    if (!silent) setLoading(true);
    else setRefreshing(true);
    try {
      const [srcList, walletSummary, eventLogs] = await Promise.all([
        ingestionApi.getSources(),
        financeApi.getWallets(),
        ingestionApi.getSyncedEvents(),
      ]);
      setSources(srcList);
      setWallets(walletSummary.wallets || []);
      setEvents(eventLogs);
    } catch (err) {
      console.error('Failed to load bank connections telemetry:', err);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    loadAll();
  }, [dataVersion]);

  const vcbSource = sources.find((s) => s.providerCode === 'VCB') || null;
  const momoSource = sources.find((s) => s.providerCode === 'MOMO') || null;
  const activeBridgeToken =
    vcbSource?.webhookSecretToken ||
    momoSource?.webhookSecretToken ||
    sources[0]?.webhookSecretToken ||
    '';

  const apiBaseOrigin =
    getApiOrigin() ||
    (typeof window !== 'undefined' ? window.location.origin : 'http://localhost:8080');
  const mobileBridgeUrl = `${apiBaseOrigin}/api/v1/webhooks/mobile-bridge`;
  const inboundEmailUrl = `${apiBaseOrigin}/api/v1/webhooks/inbound-email`;

  const copyValue = async (key: string, val: string) => {
    try {
      await navigator.clipboard.writeText(val);
      setCopiedKey(key);
      setTimeout(() => setCopiedKey(null), 1800);
    } catch {
      // Clipboard fallback ignored
    }
  };

  const openConnectModal = (provider: 'VCB' | 'MOMO') => {
    const existing = provider === 'VCB' ? vcbSource : momoSource;
    setModalProvider(provider);
    setModalIdentifier(existing?.accountIdentifier || '');
    setModalWalletId(existing?.walletId || '');
    setModalWalletName(
      existing?.walletName || (provider === 'VCB' ? 'Vietcombank' : 'Ví MoMo')
    );
    setModalInitialBalance(
      existing ? String(Math.round(existing.ledgerBalance)) : ''
    );
    setModalError(null);
  };

  const handleSaveSource = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!modalProvider) return;
    setSavingSource(true);
    setModalError(null);
    try {
      const cleanedBal = modalInitialBalance.trim().replace(/[.,\s]/g, '');
      const balNum = cleanedBal ? Number(cleanedBal) : undefined;
      await ingestionApi.connectSource({
        providerCode: modalProvider,
        accountIdentifier: modalIdentifier.trim(),
        walletId: modalWalletId || undefined,
        walletName: modalWalletName.trim() || undefined,
        initialBalance: balNum !== undefined && !Number.isNaN(balNum) ? balNum : undefined,
      });
      setModalProvider(null);
      await loadAll(true);
      triggerDataRefresh();
    } catch (err: any) {
      setModalError(err.message || 'Không thể lưu cấu hình nguồn tiền.');
    } finally {
      setSavingSource(false);
    }
  };

  const handleRotateToken = async () => {
    setRotatingToken(true);
    setTokenError(null);
    try {
      const updated = await ingestionApi.rotateBridgeToken();
      setSources(updated);
      setShowToken(true);
    } catch (err: any) {
      setTokenError(err.message || 'Không thể xoay vòng Bridge Token.');
    } finally {
      setRotatingToken(false);
    }
  };

  return (
    <div className="space-y-6 pb-8">
      {/* Editorial Fintech Header (Left-aligned per VitalCare Rules) */}
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.35 }}
        className="flex flex-wrap items-end justify-between gap-4 border-b border-white/[0.06] pb-5"
      >
        <div>
          <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded-full bg-white/[0.04] border border-white/[0.08] mb-2.5">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
            <span className="font-mono text-[10px] tracking-wider uppercase text-zinc-300">
              SYS.INGEST // MULTI-SOURCE BRIDGE v1.0
            </span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-[#F8FAFC]">
            Kết nối Ngân hàng &amp; Ví điện tử
          </h1>
          <p className="text-xs sm:text-sm text-[#94A3B8] mt-1 max-w-2xl">
            Liên kết tài khoản Vietcombank và Ví MoMo của bạn để tự động ghi nhận biến động số dư qua thông báo đẩy, SMS hoặc Email, chống ghi nhận trùng lặp và tự động đối soát số dư.
          </p>
        </div>

        <button
          type="button"
          onClick={() => loadAll(true)}
          disabled={refreshing}
          className="px-3.5 py-2 rounded-xl bg-white/[0.04] hover:bg-white/[0.08] border border-white/[0.08] text-xs font-medium text-slate-200 flex items-center gap-2 transition-colors cursor-pointer"
        >
          <RefreshCw className={`w-3.5 h-3.5 text-emerald-400 ${refreshing ? 'animate-spin' : ''}`} />
          <span>Làm mới đối soát</span>
        </button>
      </motion.div>

      {/* Row 1: Connected Telemetry Cards (VCB Emerald & MoMo Rose) */}
      {loading ? (
        <div className="py-16 flex justify-center">
          <Loader2 className="w-6 h-6 animate-spin text-emerald-400" />
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          <SourceTelemetryCard
            providerCode="VCB"
            title="Vietcombank Digibank"
            subtitle="OTT Push · SMS Banking · Email Alert"
            accent="emerald"
            icon={<Landmark className="w-5 h-5 text-emerald-400" />}
            source={vcbSource}
            hideBalance={hideBalance}
            onConfigure={() => openConnectModal('VCB')}
          />
          <SourceTelemetryCard
            providerCode="MOMO"
            title="Ví điện tử MoMo"
            subtitle="App Notification · Email Biên lai"
            accent="rose"
            icon={<Smartphone className="w-5 h-5 text-pink-400" />}
            source={momoSource}
            hideBalance={hideBalance}
            onConfigure={() => openConnectModal('MOMO')}
          />
        </div>
      )}

      {/* Row 2: Bridge Setup Credentials + Live Synced Events Feed */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
        {/* Left 5 cols: Bridge Setup & Credentials */}
        <motion.div
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.08 }}
          className="lg:col-span-5 bento-card p-5 sm:p-6 flex flex-col justify-between gap-5"
        >
          <div className="space-y-4">
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-cyan-500/10 border border-cyan-500/25 flex items-center justify-center">
                  <Link2 className="w-4 h-4 text-cyan-400" />
                </div>
                <div>
                  <h2 className="text-sm font-semibold text-[#F8FAFC]">Cấu hình Bridge trên thiết bị</h2>
                  <p className="text-[11px] text-[#94A3B8]">Thiết lập chuyển tiếp thông báo thật trong 30 giây</p>
                </div>
              </div>
              <span className="font-mono text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                TOKEN AUTH
              </span>
            </div>

            <div className="space-y-2.5">
              <CopyableField
                label="Mobile Bridge Endpoint (iOS / Android)"
                value={mobileBridgeUrl}
                copyKey="mobile-url"
                copiedKey={copiedKey}
                onCopy={copyValue}
              />
              <CopyableField
                label="Inbound Email Webhook"
                value={inboundEmailUrl}
                copyKey="email-url"
                copiedKey={copiedKey}
                onCopy={copyValue}
              />
            </div>

            <div className="p-3.5 rounded-xl bg-[#090B10]/80 border border-white/[0.07] space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-medium text-slate-300 flex items-center gap-1.5">
                  <KeyRound className="w-3.5 h-3.5 text-emerald-400" />
                  <span>
                    Header <code className="font-mono text-emerald-300">X-Bridge-Token</code>
                  </span>
                </span>
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => setShowToken((v) => !v)}
                    className="p-1 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-white/[0.06] cursor-pointer"
                    title={showToken ? 'Ẩn token' : 'Hiện token'}
                  >
                    {showToken ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                  </button>
                  <button
                    type="button"
                    onClick={handleRotateToken}
                    disabled={!activeBridgeToken || rotatingToken}
                    className="text-[11px] px-2 py-0.5 rounded-md bg-white/[0.05] hover:bg-white/[0.1] text-slate-300 disabled:opacity-40 cursor-pointer"
                  >
                    {rotatingToken ? '...' : 'Cấp lại token'}
                  </button>
                </div>
              </div>
              {tokenError && <p className="text-[11px] text-rose-400">{tokenError}</p>}
              <div className="flex items-center justify-between gap-2">
                <code className="font-mono text-xs text-slate-100 truncate">
                  {activeBridgeToken
                    ? showToken
                      ? activeBridgeToken
                      : `${activeBridgeToken.slice(0, 8)}••••••••••••••••${activeBridgeToken.slice(-4)}`
                    : 'Liên kết VCB hoặc MoMo để nhận Token bảo mật'}
                </code>
                {activeBridgeToken && (
                  <button
                    type="button"
                    onClick={() => copyValue('token', activeBridgeToken)}
                    className="shrink-0 px-2.5 py-1 rounded-lg bg-emerald-500/15 border border-emerald-500/30 text-emerald-300 text-[11px] font-medium flex items-center gap-1 hover:bg-emerald-500/25 cursor-pointer"
                  >
                    {copiedKey === 'token' ? <Check className="w-3 h-3" /> : <Copy className="w-3 h-3" />}
                    <span>{copiedKey === 'token' ? 'Đã chép' : 'Copy'}</span>
                  </button>
                )}
              </div>
            </div>

            <div className="space-y-2.5">
              <div className="flex items-center gap-1 p-1 rounded-xl bg-white/[0.03] border border-white/[0.06]">
                {(['IOS', 'ANDROID', 'EMAIL'] as const).map((tab) => (
                  <button
                    key={tab}
                    type="button"
                    onClick={() => setGuideTab(tab)}
                    className={`flex-1 py-1.5 rounded-lg text-[11px] font-semibold transition-all cursor-pointer ${
                      guideTab === tab
                        ? 'bg-white/[0.1] text-white border border-white/[0.1]'
                        : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    {tab === 'IOS' ? 'iOS Shortcuts' : tab === 'ANDROID' ? 'Android Forwarder' : 'Email Filter'}
                  </button>
                ))}
              </div>

              <div className="p-3.5 rounded-xl bg-[#090B10]/80 border border-white/[0.06] text-xs text-slate-300 space-y-2 leading-relaxed">
                {guideTab === 'IOS' && (
                  <>
                    <p>1. Mở ứng dụng <strong className="text-white">Phím tắt (Shortcuts) → Tự động hóa → Tin nhắn</strong>.</p>
                    <p>2. Chọn người gửi từ <code className="font-mono text-emerald-300">Vietcombank</code> hoặc <code className="font-mono text-pink-300">MoMo</code>, bật <strong className="text-white">Chạy ngay lập tức</strong>.</p>
                    <p>3. Action <strong className="text-white">Lấy nội dung của URL</strong> (Method: <code className="font-mono">POST</code>), thêm header <code className="font-mono text-emerald-300">X-Bridge-Token</code> và JSON body <code className="font-mono">{'{"rawMessage": "..."}'}</code>.</p>
                  </>
                )}
                {guideTab === 'ANDROID' && (
                  <>
                    <p>1. Cài <strong className="text-white">Notification Forwarder / MacroDroid</strong> cấp quyền đọc thông báo.</p>
                    <p>2. Lọc ứng dụng <code className="font-mono text-emerald-300">com.VCB</code> &amp; <code className="font-mono text-pink-300">com.mservice.momotransfer</code>.</p>
                    <p>3. Gửi HTTP POST Webhook tới endpoint trên kèm header <code className="font-mono text-emerald-300">X-Bridge-Token</code>.</p>
                  </>
                )}
                {guideTab === 'EMAIL' && (
                  <>
                    <p>1. Thiết lập Google Apps Script hoặc Email Worker chuyển tiếp thư từ <code className="font-mono text-emerald-300">VCBDigibank@vietcombank.com.vn</code> hoặc biên lai MoMo.</p>
                    <p>2. POST JSON <code className="font-mono">{'{"sender", "subject", "body"}'}</code> tới endpoint Inbound Email kèm <code className="font-mono text-emerald-300">X-Bridge-Token</code>.</p>
                    <p>3. Hệ thống tự gộp kênh <code className="font-mono text-cyan-300">MOBILE_PUSH,EMAIL</code> nếu thông báo đã đến trong vòng 5 phút.</p>
                  </>
                )}
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2 text-[11px] text-slate-400 pt-2 border-t border-white/[0.06]">
            <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>Số tài khoản và SĐT trong thông báo gốc được ẩn danh tự động trước khi lưu nhật ký kiểm toán.</span>
          </div>
        </motion.div>

        {/* Right 7 cols: Real Synced Ingestion Audit Stream */}
        <motion.div
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.14 }}
          className="lg:col-span-7 bento-card p-5 sm:p-6 flex flex-col gap-4"
        >
          <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-white/[0.06]">
            <div className="flex items-center gap-2.5">
              <div className="w-9 h-9 rounded-xl bg-emerald-500/10 border border-emerald-500/25 flex items-center justify-center">
                <Activity className="w-4 h-4 text-emerald-400" />
              </div>
              <div>
                <h2 className="text-sm font-semibold text-[#F8FAFC]">
                  Nhật ký Đồng bộ Thời gian thực
                </h2>
                <p className="text-[11px] text-[#94A3B8]">
                  Lịch sử bút toán tự động nhận từ Mobile Bridge / Inbound Email và trạng thái gộp nguồn
                </p>
              </div>
            </div>
            <span className="font-mono text-[10px] px-2.5 py-1 rounded-full bg-white/[0.04] border border-white/[0.08] text-slate-300">
              {events.length} bút toán đã đồng bộ
            </span>
          </div>

          {events.length === 0 ? (
            <div className="flex-1 flex flex-col items-center justify-center py-14 px-6 text-center space-y-3 rounded-xl bg-[#090B10]/60 border border-dashed border-white/[0.08]">
              <div className="w-12 h-12 rounded-2xl bg-white/[0.04] border border-white/[0.08] flex items-center justify-center">
                <ArrowLeftRight className="w-5 h-5 text-slate-400" />
              </div>
              <div className="space-y-1 max-w-md">
                <p className="text-sm font-semibold text-slate-200">
                  Chưa nhận thông báo biến động số dư nào
                </p>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Sau khi liên kết số tài khoản Vietcombank / SĐT MoMo và cấu hình chuyển tiếp trên điện thoại, mọi giao dịch phát sinh sẽ tự động xuất hiện tại đây và trên Sổ giao dịch.
                </p>
              </div>
            </div>
          ) : (
            <div className="space-y-2.5 max-h-[420px] overflow-y-auto pr-1">
              {events.map((ev) => {
                const isTransfer = ev.entryType === 'TRANSFER';
                const isIn = ev.transferDirection === 'IN';
                const sourceChannels = ev.sourcesMerged.split(',').filter(Boolean);

                return (
                  <div
                    key={ev.id}
                    className="p-3.5 rounded-xl bg-[#090B10]/85 border border-white/[0.07] hover:border-white/[0.14] transition-colors flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                  >
                    <div className="flex items-start gap-3 min-w-0">
                      <div
                        className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 border ${
                          isTransfer
                            ? 'bg-cyan-500/10 border-cyan-500/25 text-cyan-400'
                            : isIn
                              ? 'bg-emerald-500/10 border-emerald-500/25 text-emerald-400'
                              : 'bg-rose-500/10 border-rose-500/25 text-rose-400'
                        }`}
                      >
                        {isTransfer ? (
                          <ArrowLeftRight className="w-4 h-4" />
                        ) : isIn ? (
                          <ArrowDownLeft className="w-4 h-4" />
                        ) : (
                          <ArrowUpRight className="w-4 h-4" />
                        )}
                      </div>

                      <div className="min-w-0 space-y-1">
                        <div className="flex flex-wrap items-center gap-1.5">
                          <span className="font-mono text-[10px] font-bold px-1.5 py-0.5 rounded bg-white/[0.06] text-slate-200">
                            {ev.providerCode}
                          </span>
                          <span className="text-xs font-semibold text-slate-100 truncate max-w-[260px]">
                            {ev.description || ev.walletName}
                          </span>
                        </div>
                        <div className="flex flex-wrap items-center gap-2 text-[11px] text-slate-400">
                          <span className="font-mono text-[10px]">
                            {new Date(ev.createdAt).toLocaleString('vi-VN')}
                          </span>
                          {ev.extractedReferenceCode && (
                            <span className="font-mono text-[10px] text-cyan-300 bg-cyan-500/10 px-1.5 py-0.5 rounded">
                              Ref: {ev.extractedReferenceCode}
                            </span>
                          )}
                          <div className="flex items-center gap-1">
                            {sourceChannels.map((ch) => (
                              <span
                                key={ch}
                                className="font-mono text-[9px] px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-300 border border-emerald-500/20"
                              >
                                {ch}
                              </span>
                            ))}
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="flex sm:flex-col items-center sm:items-end justify-between shrink-0 gap-1">
                      <div className="flex items-center gap-1.5">
                        <AnimatedCurrency
                          value={isIn ? ev.amount : -ev.amount}
                          showSign={!isTransfer}
                          hideBalance={hideBalance}
                          className={`text-sm font-bold ${
                            isTransfer
                              ? 'text-cyan-300'
                              : isIn
                                ? 'text-emerald-400'
                                : 'text-rose-400'
                          }`}
                        />
                        <span className="font-mono text-[10px] text-slate-500">VND</span>
                      </div>
                      <span className="font-mono text-[9px] text-slate-500">
                        JE #{ev.journalEntryId.slice(0, 8)}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </motion.div>
      </div>

      {/* Connect / Edit Source Modal */}
      {modalProvider && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-md bento-card p-6 space-y-4 border border-white/[0.12]">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-white">
                {modalProvider === 'VCB' ? 'Liên kết Tài khoản Vietcombank' : 'Liên kết Ví MoMo'}
              </h3>
              <button
                type="button"
                onClick={() => setModalProvider(null)}
                className="text-slate-400 hover:text-white text-xs cursor-pointer"
              >
                Đóng
              </button>
            </div>

            {modalError && (
              <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-xs text-rose-300">
                {modalError}
              </div>
            )}

            <form onSubmit={handleSaveSource} className="space-y-3.5">
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1">
                  {modalProvider === 'VCB' ? 'Số tài khoản Vietcombank' : 'Số điện thoại Ví MoMo'}
                </label>
                <input
                  type="text"
                  required
                  value={modalIdentifier}
                  onChange={(e) => setModalIdentifier(e.target.value)}
                  placeholder={modalProvider === 'VCB' ? 'Nhập số TK VCB của bạn' : 'Nhập SĐT đăng ký MoMo'}
                  className="w-full bg-[#090B10] border border-white/[0.1] rounded-xl px-3.5 py-2.5 text-sm font-mono text-white focus:outline-none focus:border-emerald-500/50"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1">
                  Ví Sổ cái liên kết
                </label>
                <select
                  value={modalWalletId}
                  onChange={(e) => setModalWalletId(e.target.value)}
                  className="w-full bg-[#090B10] border border-white/[0.1] rounded-xl px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-emerald-500/50"
                >
                  <option value="">+ Tạo ví mới tự động</option>
                  {wallets.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.name} ({formatVndPlain(w.balance)} VND)
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1">
                  Tên hiển thị ví
                </label>
                <input
                  type="text"
                  value={modalWalletName}
                  onChange={(e) => setModalWalletName(e.target.value)}
                  className="w-full bg-[#090B10] border border-white/[0.1] rounded-xl px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-emerald-500/50"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1">
                  Số dư hiện tại trong tài khoản (VND - tùy chọn)
                </label>
                <input
                  type="text"
                  value={modalInitialBalance}
                  onChange={(e) => setModalInitialBalance(e.target.value)}
                  placeholder="Để trống để giữ nguyên số dư ví"
                  className="w-full bg-[#090B10] border border-white/[0.1] rounded-xl px-3.5 py-2.5 text-sm font-mono text-white focus:outline-none focus:border-emerald-500/50"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setModalProvider(null)}
                  className="px-4 py-2 rounded-xl bg-white/[0.05] text-xs font-medium text-slate-300 hover:bg-white/[0.1] cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={savingSource}
                  className="px-4 py-2 rounded-xl bg-gradient-to-b from-emerald-400 to-emerald-600 text-slate-950 text-xs font-bold flex items-center gap-1.5 cursor-pointer"
                >
                  {savingSource && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  <span>Lưu liên kết</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

function SourceTelemetryCard({
  providerCode,
  title,
  subtitle,
  accent,
  icon,
  source,
  hideBalance,
  onConfigure,
}: {
  providerCode: 'VCB' | 'MOMO';
  title: string;
  subtitle: string;
  accent: 'emerald' | 'rose';
  icon: React.ReactNode;
  source: ConnectedSourceTelemetry | null;
  hideBalance: boolean;
  onConfigure: () => void;
}) {
  const isEmerald = accent === 'emerald';
  const ringBg = isEmerald
    ? 'bg-emerald-500/10 border-emerald-500/25'
    : 'bg-pink-500/10 border-pink-500/25';
  const dotColor = isEmerald ? 'bg-emerald-400' : 'bg-pink-400';

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35 }}
      className="bento-card p-5 sm:p-6 flex flex-col justify-between gap-5"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3.5">
          <div className={`w-11 h-11 rounded-2xl border flex items-center justify-center ${ringBg}`}>
            {icon}
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-[#F8FAFC]">{title}</h2>
              <span className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-white/[0.06] text-slate-300">
                {providerCode}
              </span>
            </div>
            <p className="text-xs text-[#94A3B8] mt-0.5">{subtitle}</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {source ? (
            <span
              className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-mono font-semibold border ${
                isEmerald
                  ? 'bg-emerald-500/10 text-emerald-300 border-emerald-500/25'
                  : 'bg-pink-500/10 text-pink-300 border-pink-500/25'
              }`}
            >
              <span className={`w-1.5 h-1.5 rounded-full animate-pulse ${dotColor}`} />
              LIVE SYNC ACTIVE
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-mono text-zinc-400 bg-white/[0.04] border border-white/[0.08]">
              CHƯA LIÊN KẾT
            </span>
          )}
        </div>
      </div>

      {source ? (
        <>
          <div className="grid grid-cols-2 gap-3.5 p-3.5 rounded-xl bg-[#090B10]/75 border border-white/[0.06]">
            <div>
              <p className="text-[11px] text-slate-400">Số dư Sổ cái (Ledger)</p>
              <div className="mt-1 flex items-center gap-1.5">
                <AnimatedCurrency
                  value={source.ledgerBalance}
                  hideBalance={hideBalance}
                  className="text-lg sm:text-xl font-bold text-[#F8FAFC]"
                />
                <span className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-white/[0.06] text-slate-400">
                  VND
                </span>
              </div>
            </div>

            <div>
              <p className="text-[11px] text-slate-400">Ngân hàng / Ví báo (App)</p>
              {source.lastReportedBalance !== null ? (
                <div className="mt-1 flex items-center gap-1.5">
                  <AnimatedCurrency
                    value={source.lastReportedBalance}
                    hideBalance={hideBalance}
                    className={`text-lg sm:text-xl font-bold ${isEmerald ? 'text-emerald-400' : 'text-pink-400'}`}
                  />
                  <span className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-white/[0.06] text-slate-400">
                    VND
                  </span>
                </div>
              ) : (
                <p className="mt-2 font-mono text-xs text-slate-500">Chưa có biến động</p>
              )}
            </div>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <span>TK/SĐT:</span>
              <code className="font-mono text-slate-200 bg-white/[0.05] px-2 py-0.5 rounded">
                {source.accountIdentifier}
              </code>
              <span className="text-slate-600">•</span>
              <span className="truncate max-w-[140px] text-slate-300">{source.walletName}</span>
            </div>

            <div className="flex items-center gap-2">
              {source.reconciliationDelta !== null ? (
                source.reconciled ? (
                  <span className="inline-flex items-center gap-1 font-mono text-[11px] font-semibold px-2.5 py-1 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/25">
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    RECONCILED: Δ = 0 VND
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 font-mono text-[11px] font-semibold px-2.5 py-1 rounded-full bg-amber-500/10 text-amber-300 border border-amber-500/25">
                    <AlertTriangle className="w-3.5 h-3.5" />
                    Δ = {formatVndPlain(source.reconciliationDelta)} VND
                  </span>
                )
              ) : (
                <span className="font-mono text-[10px] px-2.5 py-1 rounded-full bg-white/[0.04] text-slate-400 border border-white/[0.06]">
                  STANDBY // DELTA PENDING
                </span>
              )}

              <button
                type="button"
                onClick={onConfigure}
                className="px-2.5 py-1 rounded-lg bg-white/[0.05] hover:bg-white/[0.1] text-[11px] font-medium text-slate-200 cursor-pointer"
              >
                Cấu hình
              </button>
            </div>
          </div>
        </>
      ) : (
        <div className="flex items-center justify-between gap-4 p-4 rounded-xl bg-[#090B10]/60 border border-dashed border-white/[0.1]">
          <p className="text-xs text-slate-400">
            Chưa liên kết {providerCode}. Thêm tài khoản để nhận biến động số dư và đối soát tự động.
          </p>
          <button
            type="button"
            onClick={onConfigure}
            className="shrink-0 px-3.5 py-2 rounded-xl bg-white/[0.08] hover:bg-white/[0.14] text-xs font-semibold text-white cursor-pointer"
          >
            + Kết nối {providerCode}
          </button>
        </div>
      )}
    </motion.div>
  );
}

function CopyableField({
  label,
  value,
  copyKey,
  copiedKey,
  onCopy,
}: {
  label: string;
  value: string;
  copyKey: string;
  copiedKey: string | null;
  onCopy: (key: string, val: string) => void;
}) {
  return (
    <div className="p-3 rounded-xl bg-[#090B10]/80 border border-white/[0.07] space-y-1">
      <p className="text-[11px] text-slate-400">{label}</p>
      <div className="flex items-center justify-between gap-2">
        <code className="font-mono text-[11px] text-cyan-300 truncate">{value}</code>
        <button
          type="button"
          onClick={() => onCopy(copyKey, value)}
          className="shrink-0 p-1.5 rounded-lg bg-white/[0.05] hover:bg-white/[0.1] text-slate-300 cursor-pointer"
          title="Sao chép"
        >
          {copiedKey === copyKey ? (
            <Check className="w-3.5 h-3.5 text-emerald-400" />
          ) : (
            <Copy className="w-3.5 h-3.5" />
          )}
        </button>
      </div>
    </div>
  );
}
