import { createContext, useContext, type ReactNode, type MouseEvent } from 'react';

type NavFn = (href: string) => void;

const WikiNavContext = createContext<NavFn | null>(null);

export function WikiNavProvider({ navigate, children }: { navigate: NavFn; children: ReactNode }) {
  return <WikiNavContext.Provider value={navigate}>{children}</WikiNavContext.Provider>;
}

export function useWikiNav(): NavFn {
  const nav = useContext(WikiNavContext);
  return nav ?? ((href) => { if (typeof window !== 'undefined') window.location.href = href; });
}

type WikiLinkProps = {
  to: string;
  children: ReactNode;
  className?: string;
};

export function WikiLink({ to, children, className }: WikiLinkProps) {
  const navigate = useWikiNav();
  return (
    <a
      href={to}
      className={className}
      onClick={(event: MouseEvent<HTMLAnchorElement>) => {
        event.preventDefault();
        navigate(to);
      }}
    >
      {children}
    </a>
  );
}
