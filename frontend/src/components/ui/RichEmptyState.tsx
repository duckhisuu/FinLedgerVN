import { motion } from 'framer-motion';
import { Plus, Sparkles } from 'lucide-react';

interface RichEmptyStateProps {
  title: string;
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
  secondaryActionLabel?: string;
  onSecondaryAction?: () => void;
  variant?: 'donut' | 'bars' | 'trend';
  className?: string;
}

export function RichEmptyState({
  title,
  description,
  actionLabel,
  onAction,
  secondaryActionLabel,
  onSecondaryAction,
  variant = 'donut',
  className = '',
}: RichEmptyStateProps) {
  return (
    <div className={`relative flex flex-col items-center justify-center p-8 text-center select-none ${className}`}>
      {/* Background radial glow */}
      <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
        <div className="w-56 h-56 rounded-full bg-emerald-500/[0.04] blur-3xl" />
        <div className="w-40 h-40 rounded-full bg-cyan-500/[0.03] blur-2xl" />
      </div>

      {/* Ghost Graphic SVG */}
      <div className="relative mb-5 flex items-center justify-center">
        {variant === 'donut' && <GhostDonut />}
        {variant === 'bars' && <GhostBars />}
        {variant === 'trend' && <GhostTrend />}
      </div>

      {/* Text Info */}
      <h3 className="text-sm font-semibold text-slate-200 tracking-tight">{title}</h3>
      {description && (
        <p className="mt-1.5 max-w-sm text-xs leading-relaxed text-slate-400">
          {description}
        </p>
      )}

      {/* CTA Buttons */}
      {(actionLabel || secondaryActionLabel) && (
        <div className="mt-5 flex flex-wrap items-center justify-center gap-2.5">
          {actionLabel && onAction && (
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={onAction}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-semibold text-slate-950 bg-gradient-to-r from-emerald-400 to-emerald-500 hover:from-emerald-300 hover:to-emerald-400 shadow-lg shadow-emerald-500/20 transition-all cursor-pointer"
            >
              <Plus className="w-3.5 h-3.5 stroke-[2.5]" />
              <span>{actionLabel}</span>
            </motion.button>
          )}

          {secondaryActionLabel && onSecondaryAction && (
            <button
              onClick={onSecondaryAction}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-medium text-slate-300 bg-white/[0.05] hover:bg-white/[0.09] border border-white/[0.08] hover:border-white/[0.14] transition-all cursor-pointer"
            >
              <Sparkles className="w-3.5 h-3.5 text-cyan-400" />
              <span>{secondaryActionLabel}</span>
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function GhostDonut() {
  return (
    <div className="relative w-28 h-28 flex items-center justify-center">
      <svg className="w-full h-full -rotate-90 animate-pulse-subtle" viewBox="0 0 100 100">
        {/* Background track */}
        <circle
          cx="50"
          cy="50"
          r="38"
          stroke="rgba(255, 255, 255, 0.05)"
          strokeWidth="8"
          fill="none"
        />
        {/* Ghost Segment 1 (Emerald) */}
        <circle
          cx="50"
          cy="50"
          r="38"
          stroke="rgba(16, 185, 129, 0.35)"
          strokeWidth="8"
          strokeDasharray="60 180"
          strokeDashoffset="0"
          strokeLinecap="round"
          fill="none"
        />
        {/* Ghost Segment 2 (Cyan) */}
        <circle
          cx="50"
          cy="50"
          r="38"
          stroke="rgba(56, 189, 248, 0.3)"
          strokeWidth="8"
          strokeDasharray="40 200"
          strokeDashoffset="-70"
          strokeLinecap="round"
          fill="none"
        />
        {/* Ghost Segment 3 (Rose) */}
        <circle
          cx="50"
          cy="50"
          r="38"
          stroke="rgba(251, 113, 133, 0.25)"
          strokeWidth="8"
          strokeDasharray="30 210"
          strokeDashoffset="-120"
          strokeLinecap="round"
          fill="none"
        />
        {/* Inner dashed ring */}
        <circle
          cx="50"
          cy="50"
          r="26"
          stroke="rgba(255, 255, 255, 0.08)"
          strokeWidth="1.5"
          strokeDasharray="3 3"
          fill="none"
        />
      </svg>
      {/* Center icon */}
      <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
        <span className="text-[11px] font-mono text-zinc-500 font-semibold">0 ₫</span>
      </div>
    </div>
  );
}

function GhostBars() {
  return (
    <div className="w-48 h-24 flex items-end justify-between px-4 pb-2 border-b border-white/[0.08] relative">
      {/* Ghost dashed grid line */}
      <div className="absolute inset-x-0 top-6 border-b border-dashed border-white/[0.05]" />
      <div className="w-4 h-12 rounded-t-md bg-emerald-500/20 animate-pulse-subtle" />
      <div className="w-4 h-8 rounded-t-md bg-rose-500/20 animate-pulse-subtle" />
      <div className="w-4 h-16 rounded-t-md bg-emerald-500/15 animate-pulse-subtle" />
      <div className="w-4 h-10 rounded-t-md bg-rose-500/15 animate-pulse-subtle" />
      <div className="w-4 h-14 rounded-t-md bg-emerald-500/20 animate-pulse-subtle" />
      <div className="w-4 h-6 rounded-t-md bg-rose-500/10 animate-pulse-subtle" />
    </div>
  );
}

function GhostTrend() {
  return (
    <div className="w-44 h-20 flex items-center justify-center">
      <svg className="w-full h-full" viewBox="0 0 160 60">
        <path
          d="M 10 45 Q 40 20, 80 35 T 150 15"
          fill="none"
          stroke="rgba(16, 185, 129, 0.35)"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeDasharray="4 4"
          className="animate-pulse-subtle"
        />
        <circle cx="150" cy="15" r="3.5" fill="#34d399" />
      </svg>
    </div>
  );
}
