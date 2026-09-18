import { useEffect, useRef, useState, type ReactNode } from 'react';
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
  return (
    <span
      className="web-chest-player-head"
      aria-hidden="true"
      style={{ backgroundImage: `url("${minecraftPlayerSkin()}")` }}
    />
  );
}

function TgaTextureIcon({
  src,
  onError,
}: {
  src: string;
  onError: () => void;
}) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    let cancelled = false;

    const decode = async () => {
      try {
        const response = await fetch(src);
        if (!response.ok) throw new Error(`TGA HTTP ${response.status}`);
        const bytes = new Uint8Array(await response.arrayBuffer());
        if (bytes.length < 18) throw new Error('Invalid TGA header');

        const idLength = bytes[0];
        const colorMapType = bytes[1];
        const imageType = bytes[2];
        const colorMapLength = bytes[5] | (bytes[6] << 8);
        const colorMapDepth = bytes[7];
        const width = bytes[12] | (bytes[13] << 8);
        const height = bytes[14] | (bytes[15] << 8);
        const pixelDepth = bytes[16];
        const descriptor = bytes[17];

        if (colorMapType !== 0 || (imageType !== 2 && imageType !== 10)) {
          throw new Error('Unsupported TGA encoding');
        }
        if ((pixelDepth !== 24 && pixelDepth !== 32) || width <= 0 || height <= 0) {
          throw new Error('Unsupported TGA pixel format');
        }

        const bytesPerPixel = pixelDepth / 8;
        let offset = 18 + idLength
          + Math.ceil((colorMapLength * colorMapDepth) / 8);
        const rgba = new Uint8ClampedArray(width * height * 4);
        let pixelIndex = 0;

        const writePixel = (sourceOffset: number) => {
          if (sourceOffset + bytesPerPixel > bytes.length) throw new Error('Truncated TGA');
          const target = pixelIndex * 4;
          rgba[target] = bytes[sourceOffset + 2];
          rgba[target + 1] = bytes[sourceOffset + 1];
          rgba[target + 2] = bytes[sourceOffset];
          rgba[target + 3] = bytesPerPixel === 4 ? bytes[sourceOffset + 3] : 255;
          pixelIndex++;
        };

        if (imageType === 2) {
          while (pixelIndex < width * height) {
            writePixel(offset);
            offset += bytesPerPixel;
          }
        } else {
          while (pixelIndex < width * height) {
            if (offset >= bytes.length) throw new Error('Truncated TGA RLE');
            const packet = bytes[offset++];
            const count = (packet & 0x7f) + 1;
            if (packet & 0x80) {
              const sourceOffset = offset;
              for (let i = 0; i < count && pixelIndex < width * height; i++) {
                writePixel(sourceOffset);
              }
              offset += bytesPerPixel;
            } else {
              for (let i = 0; i < count && pixelIndex < width * height; i++) {
                writePixel(offset);
                offset += bytesPerPixel;
              }
            }
          }
        }

        // TGA bit 5 = top-left origin. Canvas always expects top-left.
        if ((descriptor & 0x20) === 0) {
          const row = width * 4;
          const flipped = new Uint8ClampedArray(rgba.length);
          for (let y = 0; y < height; y++) {
            flipped.set(rgba.subarray(y * row, (y + 1) * row), (height - 1 - y) * row);
          }
          rgba.set(flipped);
        }

        if (cancelled || !canvasRef.current) return;
        const canvas = canvasRef.current;
        canvas.width = width;
        canvas.height = height;
        const context = canvas.getContext('2d');
        if (!context) throw new Error('Canvas unavailable');
        context.putImageData(new ImageData(rgba, width, height), 0, 0);
      } catch {
        if (!cancelled) onError();
      }
    };

    decode();
    return () => {
      cancelled = true;
    };
  }, [src, onError]);

  return <canvas ref={canvasRef} className="web-chest-tga-icon" aria-hidden="true" />;
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

  if (!src && !item) {
    return null;
  }

  const resolvedSrc = src ?? (item ? minecraftItemAsset(item) : undefined);
  if (!resolvedSrc || failed) {
    return (
      <span className="web-chest-fallback" aria-hidden="true">
        {fallbackForItem(item)}
      </span>
    );
  }

  if (/\.tga(?:$|\?)/i.test(resolvedSrc)) {
    return <TgaTextureIcon src={resolvedSrc} onError={() => setFailed(true)} />;
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
