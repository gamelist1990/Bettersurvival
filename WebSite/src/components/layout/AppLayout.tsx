import { type ReactNode } from "react";
import { type PageKey } from "../../app/navigation";
import type { AuthProfile } from "../../features/webservice/types";
import { Sidebar } from "./Sidebar";

type AppLayoutProps = {
  activePage: PageKey;
  children: ReactNode;
  profile: AuthProfile | null;
  wide?: boolean;
  onNavigate: (href: string) => void;
  onLogout: () => Promise<void>;
};

export function AppLayout({
  activePage,
  children,
  profile,
  wide,
  onNavigate,
  onLogout,
}: AppLayoutProps) {
  return (
    <div className="app-shell">
      <a className="portal-skip-link" href="#portal-content">
        本文へスキップ
      </a>
      <a
        className="portal-mobile-brand"
        href="/"
        onClick={(event) => {
          event.preventDefault();
          onNavigate("/");
        }}
      >
        <img src="/images/brand/bettersurvival-logo.svg" alt="" />
        <span>BetterSurvival</span>
      </a>
      <Sidebar
        activePage={activePage}
        profile={profile}
        onNavigate={onNavigate}
        onLogout={onLogout}
      />
      <main
        id="portal-content"
        className={`app-main${wide ? " app-main-wide" : ""}`}
      >
        {children}
      </main>
      {activePage !== "webmap" ? (
        <footer className="site-footer">
          <a href="https://pexserver.com/">PEXserver / BetterSurvival ↗</a>
          <nav aria-label="法的情報">
            <a
              href="/privacy"
              onClick={(event) => {
                event.preventDefault();
                onNavigate("/privacy");
              }}
            >
              利用規約・プライバシーポリシー
            </a>
            <a
              href="/privacy/request"
              onClick={(event) => {
                event.preventDefault();
                onNavigate("/privacy/request");
              }}
            >
              開示・削除申請
            </a>
          </nav>
        </footer>
      ) : null}
    </div>
  );
}
