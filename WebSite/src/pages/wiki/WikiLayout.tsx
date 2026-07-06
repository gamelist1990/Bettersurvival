import { useEffect, useState, type ReactNode } from 'react';
import { wikiEntries, type WikiEntry, type WikiScope } from './wikiIndex';

type WikiLayoutProps = {
  activeSlug: string | null;
  onNavigate: (href: string) => void;
  children: ReactNode;
};

const SCOPE_LABELS: Record<WikiScope, string> = {
  system: '概要',
  admin: '管理者向け',
  player: 'プレイヤー向け',
};

const LS_KEY = 'wiki:openCategories';

function loadOpenCategories(): Set<string> {
  if (typeof window === 'undefined') return new Set();
  try {
    const raw = window.localStorage.getItem(LS_KEY);
    if (!raw) return new Set();
    const arr = JSON.parse(raw);
    if (Array.isArray(arr)) return new Set(arr.filter((x) => typeof x === 'string'));
  } catch {
    /* ignore */
  }
  return new Set();
}

function saveOpenCategories(set: Set<string>) {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(LS_KEY, JSON.stringify(Array.from(set)));
  } catch {
    /* ignore */
  }
}

function catKey(scope: WikiScope, category: string): string {
  return `${scope}::${category}`;
}

export function WikiLayout({ activeSlug, onNavigate, children }: WikiLayoutProps) {
  const [openCats, setOpenCats] = useState<Set<string>>(() => loadOpenCategories());
  const [drawerOpen, setDrawerOpen] = useState(false);

  const activeEntry = wikiEntries.find((e) => e.slug === activeSlug) ?? null;

  useEffect(() => {
    if (!activeEntry?.category) return;
    const key = catKey(activeEntry.scope, activeEntry.category);
    setOpenCats((prev) => {
      if (prev.has(key)) return prev;
      const next = new Set(prev);
      next.add(key);
      saveOpenCategories(next);
      return next;
    });
  }, [activeEntry?.slug, activeEntry?.category, activeEntry?.scope]);

  // ページ遷移が終わったらドロワーを閉じる
  useEffect(() => {
    setDrawerOpen(false);
  }, [activeSlug]);

  // 開いている間は body スクロールを止める + Esc で閉じる
  useEffect(() => {
    if (!drawerOpen) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setDrawerOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', onKey);
    };
  }, [drawerOpen]);

  // 右端からの左スワイプで開く / 開いている時は右スワイプで閉じる
  // iOS Safari の戻るジェスチャは左端なので、右端限定にすることで干渉を避ける
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const RIGHT_EDGE = 24; // 右端 24px 以内でスタートしたときだけ開くアクション扱い
    const OPEN_THRESHOLD = 40;
    const CLOSE_THRESHOLD = 50;
    let startX = 0;
    let startY = 0;
    let active = false;
    let armedForOpen = false;

    const onTouchStart = (e: TouchEvent) => {
      if (e.touches.length !== 1) return;
      const t = e.touches[0];
      startX = t.clientX;
      startY = t.clientY;
      active = true;
      armedForOpen = !drawerOpen && startX >= window.innerWidth - RIGHT_EDGE;
    };
    const onTouchMove = (e: TouchEvent) => {
      if (!active || e.touches.length !== 1) return;
      const t = e.touches[0];
      const dx = t.clientX - startX;
      const dy = t.clientY - startY;
      // 縦方向のスワイプはスクロール扱いで無視
      if (Math.abs(dy) > Math.abs(dx)) return;
      if (armedForOpen && dx <= -OPEN_THRESHOLD) {
        setDrawerOpen(true);
        active = false;
        armedForOpen = false;
      } else if (drawerOpen && dx >= CLOSE_THRESHOLD) {
        setDrawerOpen(false);
        active = false;
      }
    };
    const onTouchEnd = () => {
      active = false;
      armedForOpen = false;
    };

    document.addEventListener('touchstart', onTouchStart, { passive: true });
    document.addEventListener('touchmove', onTouchMove, { passive: true });
    document.addEventListener('touchend', onTouchEnd, { passive: true });
    document.addEventListener('touchcancel', onTouchEnd, { passive: true });
    return () => {
      document.removeEventListener('touchstart', onTouchStart);
      document.removeEventListener('touchmove', onTouchMove);
      document.removeEventListener('touchend', onTouchEnd);
      document.removeEventListener('touchcancel', onTouchEnd);
    };
  }, [drawerOpen]);

  const handleNavigate = (href: string) => {
    setDrawerOpen(false);
    onNavigate(href);
  };

  const toggleCategory = (scope: WikiScope, category: string) => {
    setOpenCats((prev) => {
      const next = new Set(prev);
      const key = catKey(scope, category);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      saveOpenCategories(next);
      return next;
    });
  };

  const renderLink = (entry: WikiEntry, isChild: boolean) => {
    const href = `/wiki/${entry.slug}`;
    const isActive = entry.slug === activeSlug;
    return (
      <a
        key={href}
        href={href}
        className={`wiki-nav-item${isActive ? ' is-active' : ''}${isChild ? ' is-child' : ''}`}
        onClick={(event) => {
          event.preventDefault();
          handleNavigate(href);
        }}
      >
        <span className="wiki-nav-icon" aria-hidden>{entry.icon}</span>
        <span className="wiki-nav-label">{entry.title}</span>
      </a>
    );
  };

  const renderHomeLink = () => {
    const href = '/wiki';
    const isActive = activeSlug === null;
    return (
      <a
        key={href}
        href={href}
        className={`wiki-nav-item${isActive ? ' is-active' : ''}`}
        onClick={(event) => {
          event.preventDefault();
          handleNavigate(href);
        }}
      >
        <span className="wiki-nav-icon" aria-hidden>⌂</span>
        <span className="wiki-nav-label">Wiki トップ</span>
      </a>
    );
  };

  const renderScope = (scope: WikiScope) => {
    const entries = wikiEntries.filter((e) => e.scope === scope);
    if (entries.length === 0 && scope !== 'system') return null;

    const categoryOrder: string[] = [];
    const grouped = new Map<string | undefined, WikiEntry[]>();
    for (const entry of entries) {
      const key = entry.category;
      if (!grouped.has(key)) {
        grouped.set(key, []);
        if (key !== undefined) categoryOrder.push(key);
      }
      grouped.get(key)!.push(entry);
    }
    const rootEntries = grouped.get(undefined) ?? [];

    return (
      <div key={scope} className="wiki-nav-scope">
        <p className="wiki-nav-group">{SCOPE_LABELS[scope]}</p>

        {scope === 'system' ? renderHomeLink() : null}
        {rootEntries.map((entry) => renderLink(entry, false))}

        {categoryOrder.map((category) => {
          const items = grouped.get(category) ?? [];
          const key = catKey(scope, category);
          const isOpen = openCats.has(key);
          const containsActive = items.some((e) => e.slug === activeSlug);

          return (
            <div key={key} className={`wiki-nav-cat${isOpen ? ' is-open' : ''}${containsActive ? ' has-active' : ''}`}>
              <button
                type="button"
                className="wiki-nav-cat-head"
                aria-expanded={isOpen}
                onClick={() => toggleCategory(scope, category)}
              >
                <span className="wiki-nav-cat-caret" aria-hidden>▸</span>
                <span className="wiki-nav-cat-label">{category}</span>
                <span className="wiki-nav-cat-count">{items.length}</span>
              </button>
              {isOpen ? (
                <div className="wiki-nav-cat-body">
                  {items.map((entry) => renderLink(entry, true))}
                </div>
              ) : null}
            </div>
          );
        })}
      </div>
    );
  };

  return (
    <div className={`wiki-shell${drawerOpen ? ' is-drawer-open' : ''}`}>
      <button
        type="button"
        className={`wiki-side-toggle${drawerOpen ? ' is-open' : ''}`}
        aria-label={drawerOpen ? 'Wiki メニューを閉じる' : 'Wiki メニューを開く'}
        aria-expanded={drawerOpen}
        onClick={() => setDrawerOpen((v) => !v)}
      >
        <span />
        <span />
        <span />
      </button>
      {drawerOpen ? (
        <div
          className="wiki-side-backdrop"
          onClick={() => setDrawerOpen(false)}
          aria-hidden
        />
      ) : null}
      <nav
        className={`wiki-side${drawerOpen ? ' is-open' : ''}`}
        aria-label="Wiki navigation"
        aria-hidden={typeof window !== 'undefined' && window.innerWidth <= 900 ? !drawerOpen : undefined}
      >
        {renderScope('system')}
        {renderScope('admin')}
        {renderScope('player')}
      </nav>
      <main className="wiki-main">{children}</main>
    </div>
  );
}
