import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { formatCompactVnd } from '../../utils/format';

export interface DonutItem {
  id: string;
  name: string;
  amount: number;
  percentage: number;
  color: string;
  icon?: string;
}

interface InteractiveDonutProps {
  items: DonutItem[];
  totalAmount: number;
  hideBalance?: boolean;
  size?: number;
  strokeWidth?: number;
  activeItemId?: string | null;
  onHoverItem?: (id: string | null) => void;
}

export function InteractiveDonut({
  items,
  totalAmount,
  hideBalance = false,
  size = 170,
  strokeWidth = 18,
  activeItemId,
  onHoverItem,
}: InteractiveDonutProps) {
  const [internalHoverId, setInternalHoverId] = useState<string | null>(null);

  const hoverId = activeItemId !== undefined ? activeItemId : internalHoverId;
  const setHover = (id: string | null) => {
    setInternalHoverId(id);
    onHoverItem?.(id);
  };

  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;

  // Compute strokeDasharray and strokeDashoffset for each slice
  let accumulatedAngle = 0;
  const slices = items.map((item) => {
    const ratio = totalAmount > 0 ? item.amount / totalAmount : 0;
    const strokeDash = ratio * circumference;
    const offset = -(accumulatedAngle * circumference);
    accumulatedAngle += ratio;

    return {
      ...item,
      strokeDash,
      offset,
    };
  });

  const activeSlice = items.find((it) => it.id === hoverId);

  return (
    <div
      className="relative flex items-center justify-center shrink-0"
      style={{ width: size, height: size }}
    >
      <svg
        width={size}
        height={size}
        viewBox={`0 0 ${size} ${size}`}
        className="-rotate-90 select-none overflow-visible"
      >
        {/* Background Track Circle */}
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="rgba(255, 255, 255, 0.04)"
          strokeWidth={strokeWidth}
        />

        {/* Donut Slices */}
        {slices.map((slice) => {
          const isHovered = hoverId === slice.id;
          const currentStroke = isHovered ? strokeWidth + 4 : strokeWidth;

          return (
            <motion.circle
              key={slice.id}
              cx={size / 2}
              cy={size / 2}
              r={radius}
              fill="none"
              stroke={slice.color}
              strokeWidth={currentStroke}
              strokeDasharray={`${Math.max(0, slice.strokeDash - 2)} ${circumference}`}
              strokeDashoffset={slice.offset}
              strokeLinecap="round"
              className="cursor-pointer transition-all duration-200"
              initial={{ strokeDasharray: `0 ${circumference}` }}
              animate={{
                strokeDasharray: `${Math.max(0, slice.strokeDash - 2)} ${circumference}`,
                strokeWidth: currentStroke,
                filter: isHovered ? `drop-shadow(0 0 8px ${slice.color})` : 'none',
              }}
              transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
              onMouseEnter={() => setHover(slice.id)}
              onMouseLeave={() => setHover(null)}
            />
          );
        })}
      </svg>

      {/* Center Display */}
      <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none px-2 text-center">
        <AnimatePresence mode="wait">
          {activeSlice ? (
            <motion.div
              key={activeSlice.id}
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.9 }}
              transition={{ duration: 0.15 }}
              className="flex flex-col items-center"
            >
              <span className="text-[10px] text-slate-400 font-medium truncate max-w-[90px]">
                {activeSlice.name}
              </span>
              <span className="text-xs font-bold text-white mono-num mt-0.5">
                {hideBalance ? '••••••' : formatCompactVnd(activeSlice.amount)}
              </span>
              <span
                className="text-[10px] font-mono font-semibold px-1.5 py-0.2 rounded-full mt-1"
                style={{
                  backgroundColor: `${activeSlice.color}20`,
                  color: activeSlice.color,
                }}
              >
                {activeSlice.percentage}%
              </span>
            </motion.div>
          ) : (
            <motion.div
              key="total"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="flex flex-col items-center"
            >
              <span className="text-[10px] uppercase tracking-wider text-slate-400 font-semibold">
                Tổng cộng
              </span>
              <span className="text-xs sm:text-sm font-bold text-white mono-num mt-0.5">
                {hideBalance ? '•••••• ₫' : formatCompactVnd(totalAmount)}
              </span>
              <span className="text-[10px] text-slate-400 mt-0.5">
                {items.length} danh mục
              </span>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}
