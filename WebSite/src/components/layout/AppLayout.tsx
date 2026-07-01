import { type ReactNode } from 'react';
import { type PageKey } from '../../app/navigation';
import type { AuthProfile } from '../../features/webservice/types';
import { Sidebar } from './Sidebar';

type AppLayoutProps = {
  activePage: PageKey;
  children: ReactNode;
  profile: AuthProfile | null;
  wide?: boolean;
  onNavigate: (href: string) => void;
};

export function AppLayout({ activePage, children, profile, wide, onNavigate }: AppLayoutProps) {
  return (
    <div className="app-shell">
      <div className="page-noise" />
      <Sidebar activePage={activePage} profile={profile} onNavigate={onNavigate} />
      <main className={`app-main${wide ? ' app-main-wide' : ''}`}>{children}</main>
    </div>
  );
}
