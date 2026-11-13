import React, { useRef, useState, useCallback } from 'react';
import { motion, HTMLMotionProps } from 'framer-motion';
import { cn } from '../../utils/cn';

interface BentoCardProps extends HTMLMotionProps<'div'> {
  children: React.ReactNode;
  className?: string;
  spotlightColor?: string; // e.g. "rgba(16, 185, 129, 0.15)"
  enableSpotlight?: boolean;
}

export function BentoCard({
  children,
  className,
  spotlightColor = 'rgba(16, 185, 129, 0.12)',
  enableSpotlight = true,
  ...motionProps
}: BentoCardProps) {
  const cardRef = useRef<HTMLDivElement>(null);
  const [mousePos, setMousePos] = useState<{ x: number; y: number } | null>(null);
  const [isHovered, setIsHovered] = useState(false);

  const handleMouseMove = useCallback(
    (e: React.MouseEvent<HTMLDivElement>) => {
      if (!enableSpotlight || !cardRef.current) return;
      const rect = cardRef.current.getBoundingClientRect();
      setMousePos({
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
      });
    },
    [enableSpotlight]
  );

  const handleMouseEnter = useCallback(() => {
    if (enableSpotlight) setIsHovered(true);
  }, [enableSpotlight]);

  const handleMouseLeave = useCallback(() => {
    if (enableSpotlight) {
      setIsHovered(false);
      setMousePos(null);
    }
  }, [enableSpotlight]);

  return (
    <motion.div
      ref={cardRef}
      onMouseMove={handleMouseMove}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.38, ease: [0.16, 1, 0.3, 1] }}
      className={cn(
        'relative overflow-hidden rounded-2xl bg-[#12141C]/80 backdrop-blur-xl',
        'border border-white/[0.07] hover:border-white/[0.14]',
        'shadow-[inset_0_1px_0_0_rgba(255,255,255,0.09),0_10px_30px_-10px_rgba(0,0,0,0.5)]',
        'transition-colors duration-300',
        className
      )}
      {...motionProps}
    >
      {/* Spotlight highlight follows cursor */}
      {enableSpotlight && isHovered && mousePos && (
        <div
          className="pointer-events-none absolute -inset-px rounded-2xl opacity-100 transition-opacity duration-300"
          style={{
            background: `radial-gradient(420px circle at ${mousePos.x}px ${mousePos.y}px, ${spotlightColor}, transparent 75%)`,
          }}
        />
      )}

      {/* Inner top specular highlight */}
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-white/10 to-transparent" />

      {/* Content wrapper */}
      <div className="relative z-10 h-full">{children}</div>
    </motion.div>
  );
}
