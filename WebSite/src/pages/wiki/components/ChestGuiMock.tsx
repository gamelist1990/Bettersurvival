import { useState, type ReactNode } from 'react';
import {
  minecraftItemAsset,
  minecraftPlayerSkin,
} from './minecraftAssets';

export type ChestGuiSlot = {
  slot: number;
  label: string;
  item?: string;
  itemSrc?: string;
  lore?: string | string[];
  count?: number;
  active?: boolean;
  danger?: boolean;
  disabled?: boolean;
  onClick?: () => void;
};

type ChestGuiMockProps = {
  title: string;
  rows?: number;
  slots: ChestGuiSlot[];
  caption?: ReactNode;
  className?: string;
};

function normalizeLore(lore?: string | string[]) {
  if (!lore) return [];
  return Array.isArray(lore) ? lore : lore.split('\n');
}

function fallbackForItem(item?: string) {
  switch (item) {
    case 'player_head': return '☻';
    case 'clock': return '◷';
    case 'compass':
    case 'recovery_compass':
    case 'lodestone': return '✥';
    case 'chest':
    case 'ender_chest':
    case 'barrel': return '▣';
    case 'book':
    case 'writable_book':
    case 'paper': return '▤';
    case 'hopper': return '▽';
    case 'redstone': return '●';
    case 'barrier': return '⊘';
    case 'tnt': return '✹';
    case 'arrow':
    case 'spectral_arrow': return '➜';
    default: return '◆';
  }
}

function PlayerHeadIcon() {
  const texture = minecraftPlayerSkin();
  return (
    <svg
      className="web-chest-special-icon web-chest-player-head"
      viewBox="0 0 8 8"
      aria-hidden="true"
      focusable="false"
    >
      <image href={texture} x="-8" y="-8" width="64" height="64" />
      <image href={texture} x="-40" y="-8" width="64" height="64" />
    </svg>
  );
}

function SlotIcon({
  src,
  item,
}: {
  src?: string;
  item?: string;
}) {
  const [failed, setFailed] = useState(false);

  if (!src && item === 'player_head') return <PlayerHeadIcon />;

  const resolvedSrc = src ?? (item ? minecraftItemAsset(item) : undefined);
  if (!resolvedSrc || failed) {
    return (
      <span className="web-chest-fallback" aria-hidden="true">
        {fallbackForItem(item)}
      </span>
    );
  }

  return (
    <img
      src={resolvedSrc}
      alt=""
      draggable={false}
      data-chest-item={item ?? ''}
      onError={() => setFailed(true)}
    />
  );
}

export function ChestGuiMock({
  title,
  rows = 6,
  slots,
  caption,
  className,
}: ChestGuiMockProps) {
  const safeRows = Math.min(6, Math.max(1, rows));
  const size = safeRows * 9;
  const bySlot = new Map(slots.map((slot) => [slot.slot, slot]));

  return (
    <figure className={`web-chest-gui${className ? ` ${className}` : ''}`}>
      <div className="web-chest-window" role="group" aria-label={title}>
        <div className="web-chest-title">{title}</div>
        <div className="web-chest-grid" style={{ gridTemplateRows: `repeat(${safeRows}, 1fr)` }}>
          {Array.from({ length: size }, (_, index) => {
            const slot = bySlot.get(index);
            if (!slot) {
              return <div className="web-chest-slot web-chest-slot-empty" key={index} aria-hidden="true" />;
            }

            const lore = normalizeLore(slot.lore);
            const clickable = Boolean(slot.onClick) && !slot.disabled;
            return (
              <button
                type="button"
                key={index}
                className={[
                  'web-chest-slot',
                  clickable ? 'is-clickable' : '',
                  slot.active ? 'is-active' : '',
                  slot.danger ? 'is-danger' : '',
                ].filter(Boolean).join(' ')}
                onClick={slot.onClick}
                disabled={slot.disabled}
                aria-label={slot.label}
              >
                <SlotIcon src={slot.itemSrc} item={slot.item} />
                {slot.count && slot.count > 1 ? <span className="web-chest-count">{slot.count}</span> : null}
                <span className="web-chest-tooltip" role="tooltip">
                  <strong>{slot.label}</strong>
                  {lore.map((line, lineIndex) => <span key={lineIndex}>{line}</span>)}
                  {clickable ? <em>クリックして操作</em> : null}
                </span>
              </button>
            );
          })}
        </div>
      </div>
      {caption ? <figcaption>{caption}</figcaption> : null}
    </figure>
  );
}
