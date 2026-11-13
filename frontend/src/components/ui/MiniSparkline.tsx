interface MiniSparklineProps {
  data: number[];
  color?: 'emerald' | 'rose' | 'cyan';
  height?: number;
  className?: string;
}

export function MiniSparkline({
  data,
  color = 'emerald',
  height = 42,
  className = '',
}: MiniSparklineProps) {
  const strokeColor =
    color === 'emerald'
      ? '#34d399'
      : color === 'rose'
        ? '#fb7185'
        : '#38bdf8';

  const gradId = `spark-grad-${color}-${Math.random().toString(36).slice(2, 7)}`;

  // Default curve if data is empty or all zero
  const points = data.length >= 2 ? data : [10, 15, 12, 18, 24, 20, 28];
  const minVal = Math.min(...points);
  const maxVal = Math.max(...points);
  const range = maxVal - minVal || 1;

  const width = 160;
  const paddingY = 6;
  const usableHeight = height - paddingY * 2;

  const svgCoords = points.map((val, idx) => {
    const x = (idx / (points.length - 1)) * width;
    const y = height - paddingY - ((val - minVal) / range) * usableHeight;
    return { x, y };
  });

  // Construct smooth bezier path
  let pathD = `M ${svgCoords[0].x} ${svgCoords[0].y}`;
  for (let i = 0; i < svgCoords.length - 1; i++) {
    const current = svgCoords[i];
    const next = svgCoords[i + 1];
    const cpX = (current.x + next.x) / 2;
    pathD += ` C ${cpX} ${current.y}, ${cpX} ${next.y}, ${next.x} ${next.y}`;
  }

  const fillD = `${pathD} L ${width} ${height} L 0 ${height} Z`;
  const lastPoint = svgCoords[svgCoords.length - 1];

  return (
    <div className={`relative w-full overflow-hidden pointer-events-none select-none ${className}`}>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="w-full h-auto overflow-visible"
        preserveAspectRatio="none"
        style={{ height }}
      >
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={strokeColor} stopOpacity="0.22" />
            <stop offset="100%" stopColor={strokeColor} stopOpacity="0.0" />
          </linearGradient>
        </defs>

        {/* Gradient Area Fill */}
        <path d={fillD} fill={`url(#${gradId})`} />

        {/* Stroke Line */}
        <path
          d={pathD}
          fill="none"
          stroke={strokeColor}
          strokeWidth="2"
          strokeLinecap="round"
        />

        {/* Ending Glowing Dot */}
        <circle
          cx={lastPoint.x}
          cy={lastPoint.y}
          r="3"
          fill={strokeColor}
          className="animate-pulse"
        />
      </svg>
    </div>
  );
}
