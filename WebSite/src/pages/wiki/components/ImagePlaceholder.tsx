type ImagePlaceholderProps = {
  id: string;
  label: string;
  hint?: string;
  aspect?: '16/9' | '4/3' | '1/1' | '3/1';
};

export function ImagePlaceholder({ id, label, hint, aspect = '16/9' }: ImagePlaceholderProps) {
  return (
    <figure
      className="wiki-image-slot"
      data-slot-id={id}
      style={{ aspectRatio: aspect }}
    >
      <div className="wiki-image-frame">
        <span className="wiki-image-tag">IMAGE</span>
        <span className="wiki-image-label">{label}</span>
        {hint ? <span className="wiki-image-hint">{hint}</span> : null}
        <span className="wiki-image-id">slot id: {id}</span>
      </div>
    </figure>
  );
}
