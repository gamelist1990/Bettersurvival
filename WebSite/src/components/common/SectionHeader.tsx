type SectionHeaderProps = {
  eyebrow: string;
  title: string;
  text?: string;
};

export function SectionHeader({ eyebrow, title, text }: SectionHeaderProps) {
  return (
    <header className="section-heading">
      <p className="eyebrow">{eyebrow}</p>
      <h1>{title}</h1>
      {text ? <p className="section-text">{text}</p> : null}
    </header>
  );
}
