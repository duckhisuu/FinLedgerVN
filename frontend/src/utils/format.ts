export function formatVnd(amount: number | undefined | null, hideBalance = false): string {
  if (hideBalance) {
    return '•••••• ₫';
  }
  const num = Number(amount || 0);
  const rounded = Math.round(num);
  return `${rounded.toLocaleString('vi-VN')} ₫`;
}

export function formatSignedVnd(
  amount: number | undefined | null,
  type: string,
  hideBalance = false
): string {
  if (hideBalance) {
    return '•••••• ₫';
  }
  const absVal = Math.abs(Math.round(Number(amount || 0)));
  const formatted = `${absVal.toLocaleString('vi-VN')} ₫`;
  if (type === 'INCOME') return `+${formatted}`;
  if (type === 'EXPENSE' || type === 'RECEIPT_SCAN') return `-${formatted}`;
  if (type === 'ADJUSTMENT' || type === 'OPENING_BALANCE') {
    const raw = Math.round(Number(amount || 0));
    return `${raw >= 0 ? '+' : '-'}${absVal.toLocaleString('vi-VN')} ₫`;
  }
  return formatted;
}

export function formatCompactVnd(amount: number): string {
  const abs = Math.abs(amount);
  const sign = amount < 0 ? '-' : '';
  if (abs >= 1_000_000_000) {
    return `${sign}${(abs / 1_000_000_000).toFixed(1).replace('.0', '')} tỷ`;
  }
  if (abs >= 1_000_000) {
    return `${sign}${(abs / 1_000_000).toFixed(1).replace('.0', '')} tr`;
  }
  if (abs >= 1_000) {
    return `${sign}${Math.round(abs / 1_000)}k`;
  }
  return `${sign}${Math.round(abs)}`;
}

export function getCurrentYearMonth(): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  return `${y}-${m}`;
}

export function shiftYearMonth(ym: string, deltaMonths: number): string {
  const [yStr, mStr] = ym.split('-');
  const d = new Date(Number(yStr), Number(mStr) - 1 + deltaMonths, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

export function formatYearMonthLabel(ym: string): string {
  const [yStr, mStr] = ym.split('-');
  const current = getCurrentYearMonth();
  if (ym === current) {
    return `Tháng này (${mStr}/${yStr})`;
  }
  return `Tháng ${mStr}/${yStr}`;
}

export function formatDateHeaderVi(isoDate: string): string {
  const d = new Date(isoDate);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);

  const isSameDay = (a: Date, b: Date) =>
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate();

  const datePart = d.toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });

  if (isSameDay(d, today)) return `Hôm nay, ${datePart}`;
  if (isSameDay(d, yesterday)) return `Hôm qua, ${datePart}`;

  const weekday = d.toLocaleDateString('vi-VN', { weekday: 'long' });
  return `${weekday.charAt(0).toUpperCase() + weekday.slice(1)}, ${datePart}`;
}

