import { useEffect, useState } from 'react';
import { useMotionValue, useSpring, motion } from 'framer-motion';

interface AnimatedCurrencyProps {
  value: number;
  hideBalance?: boolean;
  prefix?: string;
  showSign?: boolean; // '+' for positive, '-' for negative
  className?: string;
  currencySymbolClassName?: string;
}

export function AnimatedCurrency({
  value,
  hideBalance = false,
  prefix = '',
  showSign = false,
  className = '',
  currencySymbolClassName = '',
}: AnimatedCurrencyProps) {
  if (hideBalance) {
    return (
      <span className={`inline-flex items-center mono-num font-mono tabular-nums tracking-tight ${className}`}>
        <span>••••••</span>
        <span className={`text-[0.72em] font-sans font-normal text-zinc-400 ml-1 select-none ${currencySymbolClassName}`}>
          ₫
        </span>
      </span>
    );
  }

  return (
    <AnimatedNumber
      value={value}
      prefix={prefix}
      showSign={showSign}
      className={className}
      currencySymbolClassName={currencySymbolClassName}
    />
  );
}

function AnimatedNumber({
  value,
  prefix,
  showSign,
  className,
  currencySymbolClassName,
}: {
  value: number;
  prefix: string;
  showSign: boolean;
  className: string;
  currencySymbolClassName: string;
}) {
  const motionVal = useMotionValue(value);
  const springVal = useSpring(motionVal, {
    stiffness: 140,
    damping: 24,
    mass: 0.8,
  });

  const [displayNumber, setDisplayNumber] = useState<number>(value);

  useEffect(() => {
    motionVal.set(value);
  }, [value, motionVal]);

  useEffect(() => {
    const unsubscribe = springVal.on('change', (latest) => {
      setDisplayNumber(Math.round(latest));
    });
    return () => unsubscribe();
  }, [springVal]);

  const absVal = Math.abs(displayNumber);
  const formattedAbs = absVal.toLocaleString('vi-VN');

  let signText = '';
  if (showSign) {
    if (value > 0) signText = '+';
    else if (value < 0) signText = '-';
  } else if (displayNumber < 0) {
    signText = '-';
  }

  return (
    <motion.span
      className={`inline-flex items-baseline mono-num font-mono tabular-nums tracking-tight ${className}`}
      initial={{ opacity: 0.9, y: 1 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2 }}
    >
      {prefix && <span className="mr-0.5">{prefix}</span>}
      {signText && <span className="mr-0.5">{signText}</span>}
      <span>{formattedAbs}</span>
      <span
        className={`text-[0.72em] font-sans font-normal text-zinc-400 ml-1.5 select-none ${currencySymbolClassName}`}
        aria-hidden="true"
      >
        ₫
      </span>
    </motion.span>
  );
}
