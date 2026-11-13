import { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Wallet,
  ArrowRight,
  ShieldCheck,
  Receipt,
  QrCode,
  PieChart,
  ChevronDown,
  ChevronUp,
  Database,
  Lock,
  Zap,
  RefreshCw,
  Cpu,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

// 5 Narrative slides matching the storytelling flow
const SECTIONS = [
  { id: 'hero', label: 'Khởi đầu Telemetry', tag: 'SYS.01' },
  { id: 'journey', label: 'Hành trình Dòng tiền', tag: 'PIPELINE.02' },
  { id: 'bento', label: 'Kiến trúc Bento', tag: 'BENTO.03' },
  { id: 'invariants', label: 'Chuẩn Ngân hàng', tag: 'AUDIT.04' },
  { id: 'cta', label: 'Bắt đầu Ngay', tag: 'LAUNCH.05' },
];

export function LandingPage() {
  const { user } = useAuth();
  const [activeIndex, setActiveIndex] = useState(0);
  const [direction, setDirection] = useState(1); // 1 = down, -1 = up
  const isTransitioningRef = useRef(false);
  const touchStartY = useRef<number | null>(null);

  const goToSection = useCallback((targetIndex: number) => {
    if (targetIndex === activeIndex || isTransitioningRef.current) return;
    if (targetIndex < 0 || targetIndex >= SECTIONS.length) return;

    isTransitioningRef.current = true;
    setDirection(targetIndex > activeIndex ? 1 : -1);
    setActiveIndex(targetIndex);

    setTimeout(() => {
      isTransitioningRef.current = false;
    }, 750);
  }, [activeIndex]);

  const nextSection = useCallback(() => {
    if (activeIndex < SECTIONS.length - 1) {
      goToSection(activeIndex + 1);
    }
  }, [activeIndex, goToSection]);

  const prevSection = useCallback(() => {
    if (activeIndex > 0) {
      goToSection(activeIndex - 1);
    }
  }, [activeIndex, goToSection]);

  // Handle mouse wheel with throttle for smooth single-section transition
  const handleWheel = useCallback((e: React.WheelEvent) => {
    if (isTransitioningRef.current) return;
    if (Math.abs(e.deltaY) < 28) return;

    if (e.deltaY > 0) {
      nextSection();
    } else {
      prevSection();
    }
  }, [nextSection, prevSection]);

  // Handle Touch gestures (Mobile swipe)
  const handleTouchStart = (e: React.TouchEvent) => {
    touchStartY.current = e.touches[0].clientY;
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (touchStartY.current === null || isTransitioningRef.current) return;
    const touchEndY = e.changedTouches[0].clientY;
    const diff = touchStartY.current - touchEndY;
    touchStartY.current = null;

    if (diff > 45) {
      nextSection();
    } else if (diff < -45) {
      prevSection();
    }
  };

  // Handle Keyboard Navigation (ArrowDown, ArrowUp, PageDown, PageUp)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (['ArrowDown', 'PageDown'].includes(e.key)) {
        e.preventDefault();
        nextSection();
      } else if (['ArrowUp', 'PageUp'].includes(e.key)) {
        e.preventDefault();
        prevSection();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [nextSection, prevSection]);

  return (
    <div
      onWheel={handleWheel}
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
      className="fixed inset-0 bg-[#080A0F] text-[#F8FAFC] selection:bg-emerald-500/30 overflow-hidden font-sans select-none"
    >
      {/* Architectural Coordinate Grid Background Layer */}
      <div className="fixed inset-0 pointer-events-none bg-grid-pattern mask-radial-faded opacity-55 z-0" />

      {/* Ambient Lighting Meshes (Restrained VitalCare Aesthetic) */}
      <div className="fixed inset-0 pointer-events-none z-0 overflow-hidden">
        <div className="absolute top-[-10%] left-1/2 -translate-x-1/2 w-[1100px] h-[550px] bg-gradient-to-b from-emerald-500/[0.08] via-teal-500/[0.03] to-transparent blur-[120px] rounded-full" />
        <div className="absolute top-[35%] right-[-10%] w-[650px] h-[550px] bg-cyan-500/[0.04] blur-[130px] rounded-full" />
        <div className="absolute top-[70%] left-[-10%] w-[650px] h-[550px] bg-emerald-500/[0.03] blur-[130px] rounded-full" />
      </div>

      {/* ======================================================== */}
      {/* 1. FLOATING GLASS NAVBAR (VitalCare Benchmark) */}
      {/* ======================================================== */}
      <header className="fixed top-4 inset-x-0 max-w-6xl mx-auto z-50 px-4 sm:px-6">
        <div className="rounded-full bg-[#0B0F17]/75 backdrop-blur-2xl border border-white/[0.08] px-5 sm:px-7 py-3 shadow-[0_20px_50px_rgba(0,0,0,0.6)] flex items-center justify-between">
          {/* Logo */}
          <Link to="/" className="flex items-center gap-3 group">
            <div className="w-8 h-8 rounded-xl bg-gradient-to-tr from-emerald-400 via-teal-400 to-cyan-500 flex items-center justify-center shadow-lg shadow-emerald-500/20 group-hover:scale-105 transition-transform">
              <Wallet className="w-4 h-4 text-slate-950 stroke-[2.6]" />
            </div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-bold tracking-tight text-white leading-none">
                FinLedger VN
              </span>
              <span className="hidden sm:inline-block px-1.5 py-0.5 rounded text-[9px] font-mono font-bold tracking-wider bg-white/[0.06] text-zinc-400 border border-white/[0.08]">
                SYS.v2.4
              </span>
            </div>
          </Link>

          {/* Desktop Navigation Links */}
          <nav className="hidden md:flex items-center gap-6 text-xs font-medium text-zinc-400">
            {SECTIONS.slice(1, 4).map((sec, idx) => (
              <button
                key={sec.id}
                onClick={() => goToSection(idx + 1)}
                className={`transition-colors cursor-pointer ${
                  activeIndex === idx + 1 ? 'text-emerald-400 font-semibold' : 'hover:text-white'
                }`}
              >
                {sec.label}
              </button>
            ))}
          </nav>

          {/* Action CTAs */}
          <div className="flex items-center gap-2.5">
            {user ? (
              <Link
                to="/"
                className="px-4 py-2 rounded-full bg-white/10 hover:bg-white/15 text-white border border-white/20 text-xs font-semibold shadow-sm transition-all flex items-center gap-1.5"
              >
                <span>Vào Sổ cái</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            ) : (
              <>
                <Link
                  to="/login"
                  className="px-3.5 py-1.5 rounded-full text-xs font-medium text-zinc-400 hover:text-white transition-colors"
                >
                  Đăng nhập
                </Link>
                <Link
                  to="/login"
                  className="px-4 py-2 rounded-full bg-white/[0.09] hover:bg-white/[0.16] text-[#F8FAFC] border border-white/[0.18] text-xs font-semibold shadow-[inset_0_1px_0_0_rgba(255,255,255,0.2)] active:scale-95 transition-all flex items-center gap-1.5"
                >
                  <span>Tạo tài khoản</span>
                  <ArrowRight className="w-3 h-3 text-emerald-400" />
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      {/* ======================================================== */}
      {/* 2. VERTICAL RIGHT-SIDE DOT PAGINATION (VitalCare Visual) */}
      {/* ======================================================== */}
      <aside className="fixed right-6 top-1/2 -translate-y-1/2 z-40 hidden xl:flex flex-col items-center gap-3.5 pointer-events-auto">
        {SECTIONS.map((sec, idx) => {
          const isActive = activeIndex === idx;
          return (
            <button
              key={sec.id}
              onClick={() => goToSection(idx)}
              className="group relative flex items-center justify-end cursor-pointer"
              title={sec.label}
            >
              {/* Tooltip Label */}
              <span className="absolute right-7 px-2.5 py-1 rounded-md text-[10px] font-mono tracking-wider text-zinc-300 bg-[#0E121A]/95 border border-white/10 opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none whitespace-nowrap shadow-xl">
                0{idx + 1} // {sec.label}
              </span>

              {/* Dot / Pill Indicator */}
              <div
                className={`transition-all duration-300 rounded-full ${
                  isActive
                    ? 'w-2 h-7 bg-emerald-400 shadow-[0_0_12px_#10b981]'
                    : 'w-2 h-2 bg-white/20 hover:bg-white/40'
                }`}
              />
            </button>
          );
        })}
      </aside>

      {/* ======================================================== */}
      {/* 3. SLIDE CONTROLLER & NARRATIVE PRESENTATION CONTAINER */}
      {/* ======================================================== */}
      <div className="relative w-full h-full pt-20 pb-16 flex items-center justify-center z-10">
        <AnimatePresence mode="wait" custom={direction}>
          <motion.div
            key={activeIndex}
            custom={direction}
            initial={{
              opacity: 0,
              y: direction > 0 ? 36 : -36,
              scale: 0.98,
              filter: 'blur(10px)',
            }}
            animate={{
              opacity: 1,
              y: 0,
              scale: 1,
              filter: 'blur(0px)',
            }}
            exit={{
              opacity: 0,
              y: direction > 0 ? -36 : 36,
              scale: 0.98,
              filter: 'blur(10px)',
            }}
            transition={{
              duration: 0.62,
              ease: [0.16, 1, 0.3, 1],
            }}
            className="w-full max-w-7xl px-5 sm:px-8 max-h-[calc(100vh-140px)] overflow-y-auto no-scrollbar my-auto"
          >
            {activeIndex === 0 && <HeroSlide onExplore={() => goToSection(1)} />}
            {activeIndex === 1 && <JourneySlide />}
            {activeIndex === 2 && <BentoSlide />}
            {activeIndex === 3 && <InvariantsSlide />}
            {activeIndex === 4 && <CtaSlide />}
          </motion.div>
        </AnimatePresence>
      </div>

      {/* ======================================================== */}
      {/* 4. BOTTOM DOCK CONTROLS (Slide Stepper & Keyboard Guide) */}
      {/* ======================================================== */}
      <div className="fixed bottom-4 inset-x-0 z-40 pointer-events-none flex items-center justify-between px-6 max-w-7xl mx-auto">
        {/* Slide Counter Pill */}
        <div className="pointer-events-auto flex items-center gap-2 px-3 py-1.5 rounded-full bg-[#0E121A]/85 backdrop-blur-xl border border-white/[0.08] text-[11px] font-mono text-zinc-400 shadow-xl">
          <span className="text-emerald-400 font-bold">0{activeIndex + 1}</span>
          <span className="text-zinc-600">/</span>
          <span>0{SECTIONS.length}</span>
          <span className="text-zinc-600 mx-1">•</span>
          <span className="text-zinc-300 font-sans hidden sm:inline">
            {SECTIONS[activeIndex].label}
          </span>
        </div>

        {/* Navigation Step Arrows */}
        <div className="pointer-events-auto flex items-center gap-1.5 p-1 rounded-full bg-[#0E121A]/85 backdrop-blur-xl border border-white/[0.08] shadow-xl">
          <button
            onClick={prevSection}
            disabled={activeIndex === 0}
            className="p-1.5 rounded-full text-zinc-400 hover:text-white disabled:opacity-30 disabled:hover:text-zinc-400 hover:bg-white/[0.06] transition-all cursor-pointer"
            title="Giai đoạn trước (Arrow Up)"
          >
            <ChevronUp className="w-4 h-4" />
          </button>
          <button
            onClick={nextSection}
            disabled={activeIndex === SECTIONS.length - 1}
            className="p-1.5 rounded-full text-zinc-400 hover:text-white disabled:opacity-30 disabled:hover:text-zinc-400 hover:bg-white/[0.06] transition-all cursor-pointer"
            title="Giai đoạn kế tiếp (Arrow Down / Scroll)"
          >
            <ChevronDown className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}

// =============================================================================
// SLIDE 0: ASYMMETRIC HERO & TELEMETRY CONSOLE
// =============================================================================
function HeroSlide({ onExplore }: { onExplore: () => void }) {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 lg:gap-8 items-center py-4">
      {/* Left 50% (7 cols): Editorial Typography */}
      <div className="lg:col-span-7 flex flex-col justify-center text-left">
        <div className="inline-flex items-center gap-2.5 px-3.5 py-1.5 rounded-full bg-[#121620]/90 border border-white/[0.08] text-[11px] font-mono text-zinc-300 w-fit mb-5 shadow-inner">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse shadow-[0_0_8px_#10b981]" />
          <span className="font-semibold text-white tracking-wider">
            SYS.ENGINE // DOUBLE-ENTRY v2.4
          </span>
          <span className="text-zinc-500">•</span>
          <span className="text-zinc-400">ZERO-FLOAT PRECISION</span>
        </div>

        <h1 className="text-3xl sm:text-5xl lg:text-[3.5rem] font-extrabold tracking-tight text-[#F8FAFC] leading-[1.12]">
          Kiểm soát dòng tiền tuyệt đối với chuẩn{' '}
          <span className="text-[#94A3B8]">Kế toán kép.</span>
        </h1>

        <p className="mt-5 text-xs sm:text-sm text-zinc-400 leading-relaxed max-w-xl">
          Kiến trúc append-only bất biến bảo toàn từng đồng vốn. Tự động hóa kết nối biến động số
          dư ngân hàng, bóc tách hóa đơn siêu thị OCR trong 1.2s và tối giản công nợ nhóm qua mã
          VietQR Napas 24/7.
        </p>

        {/* CTA Buttons */}
        <div className="mt-7 flex flex-wrap items-center gap-3">
          <Link
            to="/login"
            className="px-6 py-3.5 rounded-2xl bg-gradient-to-b from-white to-zinc-200 text-slate-950 font-bold text-xs sm:text-sm shadow-[0_10px_30px_rgba(255,255,255,0.15)] hover:brightness-105 active:scale-[0.98] transition-all flex items-center gap-2"
          >
            <span>Khởi tạo Sổ cái Cá nhân</span>
            <ArrowRight className="w-4 h-4 stroke-[2.4]" />
          </Link>
          <button
            onClick={onExplore}
            className="px-5 py-3.5 rounded-2xl bg-[#121622]/80 hover:bg-[#181E2E] text-zinc-300 border border-white/[0.08] hover:border-white/[0.14] font-semibold text-xs sm:text-sm transition-all flex items-center gap-2 cursor-pointer"
          >
            <span>Xem Hành trình Dòng tiền</span>
          </button>
        </div>

        {/* Glass Bento Metric Strip */}
        <div className="mt-8 pt-5 border-t border-white/[0.06] grid grid-cols-3 gap-3 max-w-lg">
          <div className="bento-card p-3 text-left">
            <div className="flex items-center gap-1.5 mb-1">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
              <span className="text-[10px] font-mono text-zinc-400 uppercase">Sai số làm tròn</span>
            </div>
            <p className="text-base sm:text-lg font-bold font-mono text-white tabular-nums">
              0.0000 <span className="text-[11px] font-normal text-zinc-400">₫</span>
            </p>
          </div>

          <div className="bento-card p-3 text-left">
            <div className="flex items-center gap-1.5 mb-1">
              <span className="w-1.5 h-1.5 rounded-full bg-cyan-400" />
              <span className="text-[10px] font-mono text-zinc-400 uppercase">Hạch toán Kép</span>
            </div>
            <p className="text-base sm:text-lg font-bold font-mono text-white tabular-nums">
              100% <span className="text-[11px] font-normal text-zinc-400">Balanced</span>
            </p>
          </div>

          <div className="bento-card p-3 text-left">
            <div className="flex items-center gap-1.5 mb-1">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
              <span className="text-[10px] font-mono text-zinc-400 uppercase">Xử lý Hóa đơn</span>
            </div>
            <p className="text-base sm:text-lg font-bold font-mono text-white tabular-nums">
              1.2 <span className="text-[11px] font-normal text-zinc-400">giây</span>
            </p>
          </div>
        </div>

        {/* VitalCare Scroll Cue */}
        <div className="mt-5 pt-1">
          <button
            onClick={onExplore}
            className="inline-flex items-center gap-2 text-xs font-medium text-zinc-400 hover:text-white transition-colors cursor-pointer"
          >
            <span>Cuộn chuột để xem hành trình của một dòng tiền</span>
            <ChevronDown className="w-3.5 h-3.5 text-emerald-400 animate-bounce" />
          </button>
        </div>
      </div>

      {/* Right 50% (5 cols): Live Financial Telemetry Console */}
      <div className="lg:col-span-5">
        <LiveFinancialTelemetryConsole />
      </div>
    </div>
  );
}

// =============================================================================
// SLIDE 1: HÀNH TRÌNH DÒNG TIỀN (LIFECYCLE STORYTELLING)
// =============================================================================
function JourneySlide() {
  const [activeStep, setActiveStep] = useState(0);

  const journeySteps = [
    {
      step: '01',
      title: 'Thu thập Dòng tiền Đa kênh',
      sub: 'Webhook HMAC & OCR Vision',
      desc: 'Biến động số dư tài khoản ngân hàng (VCB, TCB, MB, MoMo) được đẩy tức thì qua Webhook bảo mật bằng chữ ký số HMAC-SHA256, hoặc quét ảnh hóa đơn mua sắm trong 1.2s.',
      spec: 'INGESTION LAYER // PUSH & OCR',
      code: 'POST /webhook -> verify(HMAC_SHA256) -> 200 OK',
    },
    {
      step: '02',
      title: 'Khóa Bi quan Chống Deadlock',
      sub: 'Ordered Write Locks',
      desc: 'Để triệt tiêu hoàn toàn xung đột ghi số dư giữa các giao dịch đồng thời, hệ thống sắp xếp danh sách account_id theo thứ tự tăng dần cố định và khóa bi quan trên PostgreSQL.',
      spec: 'CONCURRENCY GUARANTEE // ZERO DEADLOCK',
      code: 'SELECT * FROM accounts WHERE id IN (102, 109) ORDER BY id ASC FOR UPDATE',
    },
    {
      step: '03',
      title: 'Hạch toán Sổ cái Kép Bất biến',
      sub: 'Zero-Float Balance Equation',
      desc: 'Mỗi giao dịch sinh tối thiểu 2 dòng hạch toán bằng kiểu số thực chính xác BigDecimal. Tổng đại số bắt buộc bằng đúng 0.0000 ₫. Cấm tuyệt đối câu lệnh UPDATE hay DELETE.',
      spec: 'CORE LEDGER // APPEND-ONLY',
      code: 'INSERT INTO journal_entries (sum(debit) - sum(credit) = 0.0000)',
    },
    {
      step: '04',
      title: 'Tự động Điều tiết Ngân sách & VietQR',
      sub: 'Simplex Graph Simplification',
      desc: 'Cập nhật hạn mức ngân sách tháng theo thời gian thực và tự động tối giản các khoản nợ chéo trong nhóm du lịch thành các đường chuyển khoản VietQR trực tiếp.',
      spec: 'SETTLEMENT ENGINE // NAPAS 24/7',
      code: 'minCashFlow(debts) -> VietQR(account, amount, memo)',
    },
  ];

  return (
    <div className="py-4">
      <div className="text-center max-w-3xl mx-auto mb-8">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-mono font-semibold mb-2">
          <span>PIPELINE // SECURE LEDGER LIFECYCLE</span>
        </div>
        <h2 className="text-2xl sm:text-4xl font-extrabold text-[#F8FAFC] tracking-tight">
          Vòng đời minh bạch của một dòng tiền
        </h2>
        <p className="mt-2 text-xs sm:text-sm text-zinc-400 leading-relaxed max-w-xl mx-auto">
          Mỗi biến động số dư đều trải qua 4 giai đoạn kiểm toán tự động trước khi ghi vào sổ cái.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-center">
        {/* Steps List (Left 5 cols) */}
        <div className="lg:col-span-5 space-y-2.5">
          {journeySteps.map((st, idx) => {
            const isActive = activeStep === idx;
            return (
              <button
                key={st.step}
                onClick={() => setActiveStep(idx)}
                className={`w-full text-left p-3.5 rounded-2xl border transition-all cursor-pointer ${
                  isActive
                    ? 'bg-[#121622] border-emerald-500/40 shadow-lg shadow-emerald-500/5'
                    : 'bg-[#0E1119]/60 border-white/[0.05] hover:bg-white/[0.03]'
                }`}
              >
                <div className="flex items-center justify-between mb-1">
                  <span className="text-[11px] font-mono font-bold text-emerald-400">
                    GIAI ĐOẠN {st.step}
                  </span>
                  <span className="text-[10px] font-mono text-zinc-500">{st.sub}</span>
                </div>
                <h4 className="text-xs sm:text-sm font-bold text-white tracking-tight">
                  {st.title}
                </h4>
              </button>
            );
          })}
        </div>

        {/* Step Detailed Visual Console (Right 7 cols) */}
        <div className="lg:col-span-7">
          <div className="bento-card p-6 text-left relative overflow-hidden">
            <div className="flex items-center justify-between pb-3 border-b border-white/[0.06] mb-4">
              <div className="flex items-center gap-2">
                <Cpu className="w-4 h-4 text-emerald-400" />
                <span className="text-xs font-mono font-bold text-white">
                  {journeySteps[activeStep].spec}
                </span>
              </div>
              <span className="text-[10px] font-mono text-zinc-500">
                PHASE {journeySteps[activeStep].step} OF 04
              </span>
            </div>

            <h3 className="text-lg font-bold text-white tracking-tight mb-2">
              {journeySteps[activeStep].title}
            </h3>
            <p className="text-xs text-zinc-300 leading-relaxed mb-5">
              {journeySteps[activeStep].desc}
            </p>

            <div className="p-3 rounded-xl bg-black/70 border border-white/[0.06] font-mono text-xs text-emerald-300">
              <span className="text-zinc-500 mr-2">$</span>
              {journeySteps[activeStep].code}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// =============================================================================
// SLIDE 2: BENTO ARCHITECTURE WITH MINI-UIS
// =============================================================================
function BentoSlide() {
  return (
    <div className="py-3">
      <div className="text-center max-w-3xl mx-auto mb-6">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-cyan-500/10 border border-cyan-500/20 text-cyan-400 text-xs font-mono font-semibold mb-2">
          <span>BENTO ARCHITECTURE // ZERO AI SLOP</span>
        </div>
        <h2 className="text-2xl sm:text-4xl font-extrabold text-[#F8FAFC] tracking-tight">
          Hệ sinh thái tính năng với Mini-UI tương tác thực
        </h2>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-12 gap-4">
        {/* Card 1: Sổ cái Kép Bất biến (col 7) */}
        <div className="md:col-span-7 bento-card p-5 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center">
                  <ShieldCheck className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white tracking-tight">
                    Lõi Kế toán Kép Bất biến
                  </h3>
                  <p className="text-[10px] font-mono text-zinc-400">Append-Only Immutability</p>
                </div>
              </div>
              <span className="px-2 py-0.5 rounded-full text-[9px] font-mono bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                SUM = 0.0000 ₫
              </span>
            </div>
          </div>
          <LiveLedgerMiniUi />
        </div>

        {/* Card 2: Quét Hóa đơn Laser OCR (col 5) */}
        <div className="md:col-span-5 bento-card p-5 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-cyan-500/10 border border-cyan-500/20 text-cyan-400 flex items-center justify-center">
                  <Receipt className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white tracking-tight">
                    Laser Scanning Receipt OCR
                  </h3>
                  <p className="text-[10px] font-mono text-zinc-400">Computer Vision Worker</p>
                </div>
              </div>
              <span className="w-2 h-2 rounded-full bg-cyan-400 animate-ping" />
            </div>
          </div>
          <LaserOcrMiniUi />
        </div>

        {/* Card 3: Tối giản Công nợ Nhóm VietQR (col 5) */}
        <div className="md:col-span-5 bento-card p-5 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 flex items-center justify-center">
                  <QrCode className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white tracking-tight">
                    Tối giản Công nợ & VietQR
                  </h3>
                  <p className="text-[10px] font-mono text-zinc-400">Min-Cash-Flow Simplex Graph</p>
                </div>
              </div>
            </div>
          </div>
          <DebtGraphMiniUi />
        </div>

        {/* Card 4: Dòng tiền & Ngân sách Vận tốc (col 7) */}
        <div className="md:col-span-7 bento-card p-5 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400 flex items-center justify-center">
                  <PieChart className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white tracking-tight">
                    Vận tốc Chi tiêu & Ngân sách
                  </h3>
                  <p className="text-[10px] font-mono text-zinc-400">Daily Spend Recommendation</p>
                </div>
              </div>
            </div>
          </div>
          <CashflowVelocityMiniUi />
        </div>
      </div>
    </div>
  );
}

// =============================================================================
// SLIDE 3: CHUẨN NGÂN HÀNG & 4 BẤT BIẾN KẾ TOÁN
// =============================================================================
function InvariantsSlide() {
  return (
    <div className="py-4">
      <div className="text-center max-w-2xl mx-auto mb-8">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/[0.05] border border-white/[0.1] text-zinc-300 text-xs font-mono font-medium mb-2">
          <span>ENGINEERING AUDIT SPECIFICATION</span>
        </div>
        <h2 className="text-2xl sm:text-4xl font-extrabold text-[#F8FAFC] tracking-tight">
          4 Bất biến Kế toán Không thể Thương lượng
        </h2>
        <p className="mt-2 text-xs sm:text-sm text-zinc-400">
          Các bất biến cốt lõi bảo vệ tính toàn vẹn của dữ liệu tài chính người dùng.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="bento-card p-5 border-l-4 border-l-emerald-500 text-left">
          <div className="flex items-center gap-2 text-white font-bold text-sm mb-1.5">
            <span className="font-mono text-emerald-400">01.</span>
            <span>Không sử dụng Số thực (Zero Floating-Point)</span>
          </div>
          <p className="text-xs text-zinc-400 leading-relaxed">
            Tuyệt đối cấm kiểu dữ liệu <code className="text-zinc-300 font-mono">float</code> hay <code className="text-zinc-300 font-mono">double</code>. Mọi phép toán sử dụng <code className="text-emerald-300 font-mono">java.math.BigDecimal</code> với kiểu cột <code className="text-emerald-300 font-mono">NUMERIC(19, 4)</code> và làm tròn ngân hàng <code className="text-emerald-300 font-mono">HALF_EVEN</code>.
          </p>
        </div>

        <div className="bento-card p-5 border-l-4 border-l-cyan-500 text-left">
          <div className="flex items-center gap-2 text-white font-bold text-sm mb-1.5">
            <span className="font-mono text-cyan-400">02.</span>
            <span>Bất biến Sổ cái Kép (Double-Entry Invariant)</span>
          </div>
          <p className="text-xs text-zinc-400 leading-relaxed">
            Mỗi bút toán giao dịch có tối thiểu 2 dòng hạch toán. Tổng đại số tất cả các chân trong cùng một bút toán bắt buộc phải bằng chính xác <code className="text-cyan-300 font-mono">0.0000 ₫</code> trước khi được commit vào cơ sở dữ liệu.
          </p>
        </div>

        <div className="bento-card p-5 border-l-4 border-l-indigo-500 text-left">
          <div className="flex items-center gap-2 text-white font-bold text-sm mb-1.5">
            <span className="font-mono text-indigo-400">03.</span>
            <span>Kiến trúc Bất biến Append-Only (Triggers Protected)</span>
          </div>
          <p className="text-xs text-zinc-400 leading-relaxed">
            Bảng <code className="text-zinc-300 font-mono">journal_entries</code> và <code className="text-zinc-300 font-mono">posting_legs</code> được bảo vệ bởi trigger PostgreSQL <code className="text-indigo-300 font-mono">trg_prevent_ledger_mutation</code>. Không bao giờ phát sinh câu lệnh <code className="text-rose-400 font-mono">UPDATE</code> hay <code className="text-rose-400 font-mono">DELETE</code>.
          </p>
        </div>

        <div className="bento-card p-5 border-l-4 border-l-emerald-400 text-left">
          <div className="flex items-center gap-2 text-white font-bold text-sm mb-1.5">
            <span className="font-mono text-emerald-400">04.</span>
            <span>Khóa Bi quan Chống Deadlock (Ordered Locks)</span>
          </div>
          <p className="text-xs text-zinc-400 leading-relaxed">
            Mọi giao dịch cập nhật số dư đều thực hiện khóa bi quan <code className="text-zinc-300 font-mono">SELECT ... FOR UPDATE</code> với danh sách các <code className="text-zinc-300 font-mono">account_id</code> luôn được sắp xếp theo thứ tự tăng dần cố định (<code className="text-emerald-300 font-mono">ORDER BY id ASC</code>) để triệt tiêu hoàn toàn deadlock đa luồng.
          </p>
        </div>
      </div>
    </div>
  );
}

// =============================================================================
// SLIDE 4: CALL TO ACTION & PRODUCTION LAUNCH
// =============================================================================
function CtaSlide() {
  return (
    <div className="py-4 text-center max-w-4xl mx-auto">
      <div className="bento-card p-8 sm:p-12 relative overflow-hidden">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-mono font-medium mb-3">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
          <span>PRODUCTION READY</span>
        </div>

        <h2 className="text-2xl sm:text-4xl font-extrabold text-white tracking-tight">
          Làm chủ sổ cái tài chính của riêng bạn ngay hôm nay.
        </h2>
        <p className="mt-3 text-xs sm:text-sm text-zinc-400 max-w-lg mx-auto leading-relaxed">
          Tham gia với tư cách Khách hàng #1. Tận hưởng trải nghiệm quản lý thu chi, phân tích ngân
          sách và chia tiền nhóm chuẩn mực nhất.
        </p>

        <div className="mt-7 flex justify-center">
          <Link
            to="/login"
            className="px-8 py-3.5 rounded-full bg-gradient-to-b from-white to-zinc-200 text-slate-950 font-bold text-sm sm:text-base shadow-[0_15px_35px_rgba(255,255,255,0.2)] hover:brightness-105 active:scale-95 transition-all flex items-center gap-2.5"
          >
            <span>Khởi tạo Tài khoản Khách hàng #1</span>
            <ArrowRight className="w-4 h-4 stroke-[2.4]" />
          </Link>
        </div>

        <div className="mt-8 pt-5 border-t border-white/[0.06] flex flex-col sm:flex-row items-center justify-between text-[11px] font-mono text-zinc-500 gap-2">
          <span>FinLedger VN • Double-Entry & VietQR Engine</span>
          <span>Thiết kế theo chuẩn VitalCare & Fintech Tier-1</span>
        </div>
      </div>
    </div>
  );
}

// =============================================================================
// MINI-UI WIDGETS
// =============================================================================

function LiveFinancialTelemetryConsole() {
  const [activeStep, setActiveStep] = useState(0);

  const steps = [
    {
      id: 'webhook',
      label: '01. Webhook Inbound',
      sub: 'Vietcombank Push',
      icon: Zap,
      status: 'VERIFIED',
      hash: 'sha256_e9a18..',
      detail: 'POST /api/v1/ingestion/webhook - Amount: +350.000 ₫',
    },
    {
      id: 'lock',
      label: '02. Redis Lock',
      sub: 'Pessimistic Order',
      icon: Lock,
      status: 'ACQUIRED',
      hash: 'key_acc:102,109',
      detail: 'SELECT ... FOR UPDATE ORDER BY a.id ASC (0ms contention)',
    },
    {
      id: 'ledger',
      label: '03. Double-Entry Split',
      sub: 'Zero-Float Sum',
      icon: Database,
      status: 'BALANCED',
      hash: 'delta: 0.000000',
      detail: 'Debit: Ăn uống (+350.000 ₫) | Credit: VCB (-350.000 ₫)',
    },
    {
      id: 'settle',
      label: '04. VietQR SSE',
      sub: 'Group Auto-Settle',
      icon: QrCode,
      status: 'DISPATCHED',
      hash: 'vietqr://napas247',
      detail: 'SSE Stream to 3 peers: Debt simplified to 1 transfer',
    },
  ];

  useEffect(() => {
    const timer = setInterval(() => {
      setActiveStep((prev) => (prev + 1) % steps.length);
    }, 3800);
    return () => clearInterval(timer);
  }, [steps.length]);

  const current = steps[activeStep];

  return (
    <div className="relative rounded-3xl p-1 bg-gradient-to-b from-white/[0.14] via-white/[0.04] to-transparent shadow-[0_25px_60px_-15px_rgba(0,0,0,0.8)]">
      <div className="rounded-[22px] bg-[#0C0F17]/95 border border-white/[0.08] p-5 sm:p-6 text-left relative overflow-hidden backdrop-blur-xl">
        {/* Console Header Bar */}
        <div className="flex items-center justify-between pb-3.5 border-b border-white/[0.06]">
          <div className="flex items-center gap-2">
            <div className="w-2.5 h-2.5 rounded-full bg-rose-500/80" />
            <div className="w-2.5 h-2.5 rounded-full bg-amber-500/80" />
            <div className="w-2.5 h-2.5 rounded-full bg-emerald-500/80" />
            <span className="text-[11px] font-mono text-zinc-400 ml-1.5 font-semibold">
              TELEMETRY.CONSOLE // LIVE
            </span>
          </div>
          <div className="flex items-center gap-1.5 text-[10px] font-mono text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-full border border-emerald-500/20">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
            <span>240ms LATENCY</span>
          </div>
        </div>

        {/* Pipeline Navigation Tabs */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-1.5 mt-3.5 p-1 rounded-xl bg-black/40 border border-white/[0.04]">
          {steps.map((st, i) => {
            const isSelected = activeStep === i;
            return (
              <button
                key={st.id}
                onClick={() => setActiveStep(i)}
                className={`p-2 rounded-lg text-left transition-all cursor-pointer ${
                  isSelected
                    ? 'bg-white/[0.08] border border-white/[0.12] shadow-sm'
                    : 'hover:bg-white/[0.02]'
                }`}
              >
                <div className="flex items-center gap-1.5">
                  <span
                    className={`w-1.5 h-1.5 rounded-full ${
                      isSelected ? 'bg-emerald-400' : 'bg-zinc-600'
                    }`}
                  />
                  <p className="text-[10px] font-mono font-bold text-white truncate">
                    0{i + 1}
                  </p>
                </div>
                <p className="text-[10px] text-zinc-400 truncate mt-0.5">{st.sub}</p>
              </button>
            );
          })}
        </div>

        {/* Dynamic Telemetry Stage Display */}
        <div className="mt-4 p-3.5 rounded-xl bg-[#080A10] border border-white/[0.06] relative">
          <AnimatePresence mode="wait">
            <motion.div
              key={current.id}
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -6 }}
              transition={{ duration: 0.2 }}
              className="space-y-2.5"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <current.icon className="w-4 h-4 text-emerald-400" />
                  <span className="text-xs font-bold text-white font-mono">{current.label}</span>
                </div>
                <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                  {current.status}
                </span>
              </div>

              <div className="p-2.5 rounded-lg bg-black/60 font-mono text-[11px] text-zinc-300 space-y-1">
                <p className="text-emerald-400 flex items-center justify-between">
                  <span>LOG: {current.detail}</span>
                </p>
                <p className="text-zinc-500 text-[10px] flex justify-between">
                  <span>HASH: {current.hash}</span>
                  <span className="text-zinc-400">Asia/Ho_Chi_Minh</span>
                </p>
              </div>
            </motion.div>
          </AnimatePresence>
        </div>

        {/* Live Terminal Log Stream */}
        <div className="mt-3.5 pt-3 border-t border-white/[0.06] font-mono text-[10px] text-zinc-400 space-y-1">
          <div className="flex items-center justify-between">
            <span className="text-zinc-500">[SYS.AUDIT] DoubleEntryValidator:</span>
            <span className="text-emerald-400 font-bold">ALGEBRAIC_SUM = 0.0000 ₫</span>
          </div>
          <div className="flex items-center justify-between text-zinc-500">
            <span>[DB.LOCK] target_ids=[102, 109] ORDER BY ASC:</span>
            <span className="text-zinc-400">NO_DEADLOCK</span>
          </div>
        </div>
      </div>
    </div>
  );
}

function LiveLedgerMiniUi() {
  const [balanceSample, setBalanceSample] = useState(150000);

  return (
    <div className="p-3.5 rounded-xl bg-[#090C12] border border-white/[0.06] text-left">
      <div className="flex items-center justify-between text-[11px] font-mono text-zinc-400 border-b border-white/[0.06] pb-2 mb-2.5">
        <span>BÚT TOÁN KÉP (2 LEGS)</span>
        <button
          onClick={() =>
            setBalanceSample((b) => (b === 150000 ? 320000 : b === 320000 ? 500000 : 150000))
          }
          className="text-emerald-400 hover:underline flex items-center gap-1 cursor-pointer"
        >
          <RefreshCw className="w-3 h-3" />
          <span>Thử đổi số tiền</span>
        </button>
      </div>

      <div className="space-y-1.5 font-mono text-xs">
        <div className="flex items-center justify-between p-2 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-300">
          <span>LEG 1: DEBIT [Ăn uống & Cà phê]</span>
          <span className="font-bold">+{balanceSample.toLocaleString('vi-VN')} ₫</span>
        </div>
        <div className="flex items-center justify-between p-2 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-300">
          <span>LEG 2: CREDIT [Ví Techcombank]</span>
          <span className="font-bold">-{balanceSample.toLocaleString('vi-VN')} ₫</span>
        </div>
      </div>

      <div className="mt-2.5 pt-2 border-t border-white/[0.05] flex items-center justify-between text-[10px] font-mono">
        <span className="text-zinc-500">TỔNG CÂN ĐỐI (ALGEBRAIC SUM):</span>
        <span className="font-bold text-emerald-400">0.0000 ₫ (PERFECT ZERO)</span>
      </div>
    </div>
  );
}

function LaserOcrMiniUi() {
  return (
    <div className="relative p-3.5 rounded-xl bg-[#090C12] border border-white/[0.06] text-left overflow-hidden h-36 flex flex-col justify-between">
      <div className="pointer-events-none absolute inset-x-0 h-0.5 bg-gradient-to-r from-transparent via-cyan-400 to-transparent shadow-[0_0_12px_#38bdf8] animate-laser z-20" />

      <div className="flex items-center justify-between text-[10px] font-mono text-zinc-400 border-b border-dashed border-white/10 pb-1">
        <span>WINMART // HÓA ĐƠN BÁN LẺ</span>
        <span className="text-cyan-400">OCR: 99.2%</span>
      </div>

      <div className="space-y-1 text-xs font-mono">
        <div className="p-1 rounded bg-white/[0.04] border border-cyan-500/30 flex items-center justify-between text-zinc-200 text-[11px]">
          <span>[X] Sữa tươi TH True Milk 1L</span>
          <span className="font-bold text-white">38.000 ₫</span>
        </div>
        <div className="p-1 rounded bg-white/[0.04] border border-cyan-500/30 flex items-center justify-between text-zinc-200 text-[11px]">
          <span>[X] Bánh mì Sandwich ngũ cốc</span>
          <span className="font-bold text-white">24.000 ₫</span>
        </div>
      </div>

      <div className="pt-1 border-t border-dashed border-white/10 flex items-center justify-between text-[10px] font-mono text-cyan-300">
        <span>TỰ ĐỘNG PHÂN LOẠI:</span>
        <span className="font-bold">Ăn uống (62.000 ₫)</span>
      </div>
    </div>
  );
}

function DebtGraphMiniUi() {
  const [simplified, setSimplified] = useState(false);

  return (
    <div className="p-3.5 rounded-xl bg-[#090C12] border border-white/[0.06] text-left">
      <div className="flex items-center justify-between text-[11px] font-mono text-zinc-400 border-b border-white/[0.06] pb-2 mb-2.5">
        <span>ĐỒ THỊ CÔNG NỢ NHÓM</span>
        <button
          onClick={() => setSimplified((s) => !s)}
          className="text-indigo-400 hover:underline flex items-center gap-1 cursor-pointer font-semibold text-[10px]"
        >
          <span>{simplified ? 'Xem nợ ban đầu (6 lượt)' : 'Tối giản nợ (1 lượt)'}</span>
        </button>
      </div>

      <div className="space-y-1.5 text-xs font-mono">
        {simplified ? (
          <div className="p-2 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-between text-emerald-300 text-[11px]">
            <span>An ➔ Châu (Chuyển 1 lần)</span>
            <span className="font-bold">120.000 ₫ (VietQR)</span>
          </div>
        ) : (
          <div className="space-y-1 text-zinc-400 text-[11px]">
            <div className="flex justify-between">
              <span>An nợ Bình: 70.000 ₫</span>
              <span className="text-zinc-500">Chéo</span>
            </div>
            <div className="flex justify-between">
              <span>Bình nợ Châu: 120.000 ₫</span>
              <span className="text-zinc-500">Chéo</span>
            </div>
            <div className="flex justify-between">
              <span>Châu nợ An: 50.000 ₫</span>
              <span className="text-zinc-500">Chéo</span>
            </div>
          </div>
        )}
      </div>

      <div className="mt-2.5 pt-2 border-t border-white/[0.05] flex items-center justify-between text-[10px] font-mono text-zinc-400">
        <span>KẾT QUẢ:</span>
        <span className="font-bold text-white">
          {simplified ? 'Đã triệt tiêu 83% số lượt' : '3 khoản nợ lòng vòng'}
        </span>
      </div>
    </div>
  );
}

function CashflowVelocityMiniUi() {
  return (
    <div className="p-3.5 rounded-xl bg-[#090C12] border border-white/[0.06] text-left">
      <div className="flex items-center justify-between text-[11px] font-mono text-zinc-400 border-b border-white/[0.06] pb-2 mb-2.5">
        <span>VẬN TỐC DÒNG TIỀN THÁNG</span>
        <span className="text-emerald-400">CÒN: 9.650.000 ₫</span>
      </div>

      <div className="grid grid-cols-2 gap-2.5 mb-2.5">
        <div className="p-2 rounded-lg bg-black/60">
          <p className="text-[10px] text-zinc-500">KHUYẾN NGHỊ AN TOÀN:</p>
          <p className="text-xs sm:text-sm font-bold font-mono text-white mt-0.5">420.000 ₫ / ngày</p>
        </div>
        <div className="p-2 rounded-lg bg-black/60">
          <p className="text-[10px] text-zinc-500">VẬN TỐC THỰC TẾ:</p>
          <p className="text-xs sm:text-sm font-bold font-mono text-emerald-400 mt-0.5">380.000 ₫ / ngày</p>
        </div>
      </div>

      <div className="h-1.5 rounded-full bg-white/[0.06] overflow-hidden">
        <div className="h-full rounded-full bg-gradient-to-r from-emerald-500 to-teal-400 w-[68%]" />
      </div>
      <div className="mt-1.5 flex justify-between text-[10px] font-mono text-zinc-500">
        <span>Tiến độ ngân sách: 68%</span>
        <span>Thời gian còn: 14 ngày</span>
      </div>
    </div>
  );
}
