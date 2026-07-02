type IconProps = { size?: number; className?: string };

function strokeProps(size: number) {
  return {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none' as const,
    stroke: 'currentColor',
    strokeWidth: 1.8,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
  };
}

export function IconArrowLeft({ size = 20, className }: IconProps) {
  return (
    <svg {...strokeProps(size)} className={className}>
      <path d="M19 12H5" />
      <path d="M11 18l-6-6 6-6" />
    </svg>
  );
}

export function IconComment({ size = 19, className }: IconProps) {
  return (
    <svg {...strokeProps(size)} className={className}>
      <path d="M4 5.4h16v10.2H9.3L5 19.4v-3.8H4Z" />
    </svg>
  );
}

export function IconRepost({ size = 19, className }: IconProps) {
  return (
    <svg {...strokeProps(size)} className={className}>
      <path d="M6.2 7.2H17v6" />
      <path d="M9.6 4.4 6.2 7.2l3.4 2.7" />
      <path d="M17.8 16.8H7v-6" />
      <path d="M14.4 19.6l3.4-2.8-3.4-2.7" />
    </svg>
  );
}

export function IconHeart({ size = 19, className }: IconProps) {
  return (
    <svg {...strokeProps(size)} className={className}>
      <path d="M12 20.1s-7.4-4.5-9.7-8.9C.7 7.8 2 4.6 5.2 3.8c2-.5 3.8.4 4.8 1.9 1-1.5 2.9-2.4 4.8-1.9 3.2.8 4.6 4 3 7.4-2.3 4.4-9.8 8.9-9.8 8.9Z" />
    </svg>
  );
}

export function IconHeartFilled({ size = 19, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" className={className}>
      <path d="M12 20.1s-7.4-4.5-9.7-8.9C.7 7.8 2 4.6 5.2 3.8c2-.5 3.8.4 4.8 1.9 1-1.5 2.9-2.4 4.8-1.9 3.2.8 4.6 4 3 7.4-2.3 4.4-9.8 8.9-9.8 8.9Z" />
    </svg>
  );
}

export function IconShare({ size = 19, className }: IconProps) {
  return (
    <svg {...strokeProps(size)} className={className}>
      <path d="M12 15.2V4.2" />
      <path d="M7.6 8.4 12 4.2l4.4 4.2" />
      <path d="M5 13.6v4.8A1.6 1.6 0 0 0 6.6 20h10.8a1.6 1.6 0 0 0 1.6-1.6v-4.8" />
    </svg>
  );
}

export function IconViews({ size = 19, className }: IconProps) {
  return (
    <svg {...strokeProps(size)} className={className}>
      <path d="M4 19V12" />
      <path d="M10 19V7" />
      <path d="M16 19V10" />
      <path d="M22 19V4" />
    </svg>
  );
}

export function IconImagePlus({ size = 19, className }: IconProps) {
  return (
    <svg {...strokeProps(size)} className={className}>
      <rect x="3" y="5" width="18" height="14" rx="2.6" />
      <circle cx="9" cy="10.4" r="1.6" />
      <path d="m4 17 4.6-4.6a1.6 1.6 0 0 1 2.3 0l1.5 1.5" />
      <path d="m13 17 2.7-2.8a1.6 1.6 0 0 1 2.3 0L21 17" />
    </svg>
  );
}

export function IconClose({ size = 15, className }: IconProps) {
  return (
    <svg {...strokeProps(size)} className={className}>
      <path d="M6 6l12 12" />
      <path d="M18 6 6 18" />
    </svg>
  );
}

export function IconPin({ size = 15, className }: IconProps) {
  return (
    <svg {...strokeProps(size)} className={className}>
      <path d="M12 21s-6.4-5.7-6.4-11A6.4 6.4 0 0 1 18.4 10c0 5.3-6.4 11-6.4 11Z" />
      <circle cx="12" cy="10" r="2.3" />
    </svg>
  );
}

export function IconLink({ size = 15, className }: IconProps) {
  return (
    <svg {...strokeProps(size)} className={className}>
      <path d="M9.6 14.4 14.4 9.6" />
      <path d="M10.9 6.7 12.4 5.2a3.6 3.6 0 1 1 5.1 5.1l-1.6 1.6" />
      <path d="M13.1 17.3 11.6 18.8a3.6 3.6 0 1 1-5.1-5.1l1.6-1.6" />
    </svg>
  );
}

export function IconCalendar({ size = 15, className }: IconProps) {
  return (
    <svg {...strokeProps(size)} className={className}>
      <rect x="3.5" y="5" width="17" height="15" rx="2.2" />
      <path d="M3.5 9.6h17" />
      <path d="M8 3v4" />
      <path d="M16 3v4" />
    </svg>
  );
}

export function IconBell({ size = 20, className }: IconProps) {
  return (
    <svg {...strokeProps(size)} className={className}>
      <path d="M6 9a6 6 0 0 1 12 0c0 4 1.5 5.5 2 6H4c.5-.5 2-2 2-6Z" />
      <path d="M10 19a2 2 0 0 0 4 0" />
    </svg>
  );
}

export function IconTrend({ size = 18, className }: IconProps) {
  return (
    <svg {...strokeProps(size)} className={className}>
      <path d="M4 16 9.5 10.2l3.5 3.6L20 6" />
      <path d="M15 6h5v5" />
    </svg>
  );
}

export function IconSearch({ size = 18, className }: IconProps) {
  return (
    <svg {...strokeProps(size)} className={className}>
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="m19.5 19.5-4.4-4.4" />
    </svg>
  );
}

export function IconMore({ size = 18, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" className={className}>
      <circle cx="5" cy="12" r="1.8" />
      <circle cx="12" cy="12" r="1.8" />
      <circle cx="19" cy="12" r="1.8" />
    </svg>
  );
}
