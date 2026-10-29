import React from 'react';
import {
  Wallet,
  Landmark,
  Smartphone,
  CreditCard,
  PiggyBank,
  Utensils,
  Home,
  ShoppingBag,
  Car,
  ShoppingCart,
  HeartPulse,
  Film,
  GraduationCap,
  Receipt,
  Briefcase,
  Award,
  TrendingUp,
  Laptop,
  Gift,
  PlusCircle,
  MoreHorizontal,
  Layers,
  Tag,
  ArrowLeftRight,
  SlidersHorizontal,
} from 'lucide-react';

const ICON_MAP: Record<string, React.ComponentType<{ className?: string }>> = {
  wallet: Wallet,
  landmark: Landmark,
  smartphone: Smartphone,
  'credit-card': CreditCard,
  'piggy-bank': PiggyBank,
  utensils: Utensils,
  home: Home,
  'shopping-bag': ShoppingBag,
  car: Car,
  'shopping-cart': ShoppingCart,
  'heart-pulse': HeartPulse,
  film: Film,
  'graduation-cap': GraduationCap,
  receipt: Receipt,
  briefcase: Briefcase,
  award: Award,
  'trending-up': TrendingUp,
  laptop: Laptop,
  gift: Gift,
  'plus-circle': PlusCircle,
  'more-horizontal': MoreHorizontal,
  layers: Layers,
  tag: Tag,
  transfer: ArrowLeftRight,
  adjustment: SlidersHorizontal,
};

export const AVAILABLE_ICONS = [
  { key: 'utensils', label: 'Ăn uống' },
  { key: 'home', label: 'Nhà cửa' },
  { key: 'shopping-bag', label: 'Mua sắm' },
  { key: 'shopping-cart', label: 'Siêu thị' },
  { key: 'car', label: 'Di chuyển' },
  { key: 'heart-pulse', label: 'Sức khỏe' },
  { key: 'film', label: 'Giải trí' },
  { key: 'graduation-cap', label: 'Giáo dục' },
  { key: 'receipt', label: 'Hóa đơn' },
  { key: 'briefcase', label: 'Công việc' },
  { key: 'trending-up', label: 'Đầu tư' },
  { key: 'gift', label: 'Quà tặng' },
  { key: 'laptop', label: 'Freelance' },
  { key: 'award', label: 'Thưởng' },
  { key: 'wallet', label: 'Ví tiền' },
  { key: 'landmark', label: 'Ngân hàng' },
  { key: 'smartphone', label: 'Ví điện tử' },
  { key: 'credit-card', label: 'Thẻ tín dụng' },
  { key: 'piggy-bank', label: 'Tiết kiệm' },
  { key: 'tag', label: 'Khác' },
];

export const AVAILABLE_COLORS = [
  '#22c55e',
  '#ef4444',
  '#f97316',
  '#f59e0b',
  '#3b82f6',
  '#06b6d4',
  '#8b5cf6',
  '#ec4899',
  '#10b981',
  '#6366f1',
];

interface CategoryIconProps {
  icon?: string;
  color?: string;
  size?: 'sm' | 'md' | 'lg';
}

export function CategoryIcon({ icon = 'tag', color = '#22c55e', size = 'md' }: CategoryIconProps) {
  const IconComp = ICON_MAP[icon] || Tag;
  const dims =
    size === 'sm'
      ? 'w-8 h-8 rounded-xl'
      : size === 'lg'
        ? 'w-12 h-12 rounded-2xl'
        : 'w-10 h-10 rounded-full';
  const iconDims = size === 'sm' ? 'w-4 h-4' : size === 'lg' ? 'w-6 h-6' : 'w-5 h-5';

  return (
    <div
      className={`${dims} flex items-center justify-center shrink-0 transition-transform`}
      style={{
        backgroundColor: `${color}20`,
        color: color,
      }}
    >
      <IconComp className={iconDims} />
    </div>
  );
}

