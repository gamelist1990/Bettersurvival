import { FetchedWikiImage } from './FetchedWikiImage';

type ImagePlaceholderProps = {
  id: string;
  label: string;
  hint?: string;
  aspect?: '16/9' | '4/3' | '1/1' | '3/1';
};

export function ImagePlaceholder({ id, label, hint }: ImagePlaceholderProps) {
  const src = `/images/wiki/${id}.png`;

  return (
    <FetchedWikiImage
      src={src}
      alt={label}
      caption={hint ?? label}
    />
  );
}
