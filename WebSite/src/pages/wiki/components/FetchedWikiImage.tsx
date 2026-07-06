import { useEffect, useState } from 'react';

type FetchedWikiImageProps = {
  src: string;
  alt: string;
  caption?: string;
  className?: string;
};

/**
 * Wiki 用の画像表示。
 *
 * 以前は fetch → Blob → object URL (blob:) を <img> に渡していたが、
 * 本番の CSP が `img-src 'self' data: https:` (blob: を含まない) のため
 * blob URL の画像がすべてブロックされ、画像が一切表示されなかった。
 * 同一オリジンのアセットパスを直接 <img src> に渡す (= 'self' で許可される) ことで解決している。
 */
export function FetchedWikiImage({ src, alt, caption, className }: FetchedWikiImageProps) {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [src]);

  return (
    <figure className={`wiki-real-image${className ? ` ${className}` : ''}`}>
      {failed ? (
        <div className="wiki-image-fetch-state">画像を読み込めませんでした。</div>
      ) : (
        <img src={src} alt={alt} loading="lazy" onError={() => setFailed(true)} />
      )}
      {caption ? <figcaption>{caption}</figcaption> : null}
    </figure>
  );
}
