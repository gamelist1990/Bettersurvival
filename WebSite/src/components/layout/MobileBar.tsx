import { useEffect, useState } from 'react';
import { navigationItems, type PageKey } from '../../app/navigation';

type MobileBarProps = {
  activePage: PageKey;
  onNavigate: (href: string) => void;
};

export function MobileBar({ activePage, onNavigate }: MobileBarProps) {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    document.body.style.overflow = open ? 'hidden' : '';
    return () => { document.body.style.overflow = ''; };
  }, [open]);

  const go = (href: string) => {
    setOpen(false);
    onNavigate(href);
  };

  return (
    <header className="mobile-bar">
      <button className={`menu-toggle ${open ? 'is-open' : ''}`} onClick={() => setOpen((value) => !value)} type="button" aria-label={open ? 'メニューを閉じる' : 'メニューを開く'}>
        <span /><span /><span />
      </button>
      <a href="/" className="mobile-brand" onClick={(event) => { event.preventDefault(); go('/'); }}>BetterSurvival</a>
      <div className={`mobile-menu ${open ? 'is-open' : ''}`}>
        {navigationItems.map((item) => (
          <a key={item.key} className={`mobile-link${activePage === item.key ? ' is-active' : ''}`} href={item.href} onClick={(event) => { event.preventDefault(); go(item.href); }}>
            <span>{item.icon}</span>{item.label}
          </a>
        ))}
      </div>
    </header>
  );
}
