import { useEffect, useState } from 'react';
import { navigationItems, type PageKey } from '../../app/navigation';
import type { AuthProfile } from '../../features/webservice/types';
import { displayName } from '../../features/webservice/useWebService';

type SidebarProps = {
  activePage: PageKey;
  profile: AuthProfile | null;
  onNavigate: (href: string) => void;
  onLogout: () => Promise<void>;
};

export function Sidebar({ activePage, profile, onNavigate, onLogout }: SidebarProps) {
  const [open, setOpen] = useState(false);
  const [navOpen, setNavOpen] = useState(false);

  const go = (href: string) => {
    setOpen(false);
    setNavOpen(false);
    onNavigate(href);
  };

  // ドロワーを開いている間は背面ページのスクロールを固定する (iOS のスクロール貫通対策)。
  useEffect(() => {
    document.body.classList.toggle('sidebar-drawer-open', navOpen);
    return () => document.body.classList.remove('sidebar-drawer-open');
  }, [navOpen]);

  return (
    <aside className={`app-sidebar${navOpen ? ' is-open' : ''}`} aria-label="BetterSurvival navigation">
      <button
        className={`sidebar-drawer-toggle${navOpen ? ' is-open' : ''}`}
        type="button"
        aria-label={navOpen ? 'メニューを閉じる' : 'メニューを開く'}
        aria-expanded={navOpen}
        onClick={() => setNavOpen((value) => !value)}
      >
        <span />
        <span />
        <span />
      </button>
      <a className="sidebar-brand" href="/" onClick={(event) => { event.preventDefault(); go('/'); }}>
        <span className="sidebar-logo" aria-hidden="true">
          <img src="/images/brand/bettersurvival-logo.svg" alt="" />
        </span>
        <span className="sidebar-brand-copy">
          <strong>BetterSurvival</strong>
          <span>Official Portal</span>
        </span>
      </a>
      <nav className="sidebar-nav">
        {navigationItems.map((item) => (
          <a
            className={`sidebar-link${activePage === item.key ? ' is-active' : ''}`}
            href={item.href}
            key={item.key}
            onClick={(event) => { event.preventDefault(); go(item.href); }}
          >
            <span className="sidebar-icon">{item.icon}</span>
            <span>
              <strong>{item.label}</strong>
              <small>{item.subtitle}</small>
            </span>
          </a>
        ))}
      </nav>
      <div className="header-user-area">
        {profile ? (
          <div className="header-user-menu">
            <button className="header-user-button" type="button" onClick={() => setOpen((value) => !value)} aria-expanded={open}>
              <img src={profile.faceUrl || '/images/clear.png'} alt="" />
              <span>
                <strong>{displayName(profile)}</strong>
                <small>@{profile.username}</small>
              </span>
            </button>
            {open ? (
              <div className="header-user-popover">
                <button type="button" onClick={() => go('/profile')}>プロフィール</button>
                <button type="button">通知</button>
                <button type="button" onClick={() => go('/feed')}>Minecraft Twitter</button>
                <button type="button" className="header-user-popover-logout" onClick={() => { setOpen(false); void onLogout(); }}>ログアウト</button>
              </div>
            ) : null}
          </div>
        ) : (
          <button className="header-login-button" type="button" onClick={() => go('/profile')}>ログイン</button>
        )}
      </div>
    </aside>
  );
}
