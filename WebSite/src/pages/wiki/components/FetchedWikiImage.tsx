import { useEffect, useState } from 'react';

type FetchedWikiImageProps = {
  src: string;
  alt: string;
  caption?: string;
  className?: string;
};

export function FetchedWikiImage({ src, alt, caption, className }: FetchedWikiImageProps) {
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let objectUrl: string | null = null;
    let cancelled = false;

    setImageUrl(null);
    setFailed(false);

    fetch(src)
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Failed to fetch image: ${response.status}`);
        }
        return response.blob();
      })
      .then((blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setImageUrl(objectUrl);
      })
      .catch(() => {
        if (!cancelled) {
          setFailed(true);
        }
      });

    return () => {
      cancelled = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [src]);

  return (
    <figure className={`wiki-real-image${className ? ` ${className}` : ''}`}>
      {imageUrl ? (
        <img src={imageUrl} alt={alt} />
      ) : (
        <div className="wiki-image-fetch-state">
          {failed ? '画像を読み込めませんでした。' : '画像を読み込み中...'}
        </div>
      )}
      {caption ? <figcaption>{caption}</figcaption> : null}
    </figure>
  );
}
