import type { ReactNode } from 'react';
import { wikiEntries } from './wikiIndex';

type WikiLayoutProps = {
  activeSlug: string | null;
  onNavigate: (href: string) => void;
  children: ReactNode;
};

export function WikiLayout({ activeSlug, onNavigate, children }: WikiLayoutProps) {
  const systemEntries = wikiEntries.filter((entry) => entry.scope === 'system');
  const adminEntries = wikiEntries.filter((entry) => entry.scope === 'admin');
  const playerEntries = wikiEntries.filter((entry) => entry.scope === 'player');

  const link = (slug: string | null, label: string, icon: string) => {
    const href = slug ? `/wiki/${slug}` : '/wiki';
    const isActive = slug === activeSlug || (slug === null && activeSlug === null);
    return (
      <a
        key={href}
        href={href}
        className={`wiki-nav-item${isActive ? ' is-active' : ''}`}
        onClick={(event) => {
          event.preventDefault();
          onNavigate(href);
        }}
      >
        <span className="wiki-nav-icon" aria-hidden>{icon}</span>
        <span className="wiki-nav-label">{label}</span>
      </a>
    );
  };

  return (
    <div className="wiki-shell">
      <nav className="wiki-side" aria-label="Wiki navigation">
        <p className="wiki-nav-group">概要</p>
        {link(null, 'Wiki トップ', '⌂')}
        {systemEntries.map((entry) => link(entry.slug, entry.title, entry.icon))}

        <p className="wiki-nav-group">管理者向け</p>
        {adminEntries.map((entry) => link(entry.slug, entry.title, entry.icon))}

        <p className="wiki-nav-group">プレイヤー向け</p>
        {playerEntries.map((entry) => link(entry.slug, entry.title, entry.icon))}
      </nav>
      <main className="wiki-main">{children}</main>
    </div>
  );
}
