import { CSSProperties, ReactNode } from 'react';
import { FetchedWikiImage } from './FetchedWikiImage';

type TextureLayoutItem = {
  id: string;
  src: string;
  alt?: string;
  label?: ReactNode;
  className?: string;
  left: number;
  top: number;
  size?: number;
  imageSize?: number;
};

type TextureLayoutProps = {
  src: string;
  alt: string;
  className?: string;
  items: TextureLayoutItem[];
  children?: ReactNode;
};

export function TextureLayout({ src, alt, className, items, children }: TextureLayoutProps) {
  return (
    <div className={`texture-layout${className ? ` ${className}` : ''}`}>
      <FetchedWikiImage src={src} alt={alt} />
      <div className="texture-layout-overlay">
        {items.map((item) => {
          const style = {
            left: `${item.left}%`,
            top: `${item.top}%`,
            width: item.size ? `${item.size}%` : undefined,
            aspectRatio: item.size ? '1' : undefined,
          } as CSSProperties;
          return (
            <div className={`texture-layout-item${item.className ? ` ${item.className}` : ''}`} key={item.id} style={style}>
              <img className="texture-layout-item-image" src={item.src} alt={item.alt ?? ''} style={item.imageSize ? { width: `${item.imageSize}%`, height: `${item.imageSize}%` } : undefined} />
              {item.label}
            </div>
          );
        })}
        {children}
      </div>
    </div>
  );
}
