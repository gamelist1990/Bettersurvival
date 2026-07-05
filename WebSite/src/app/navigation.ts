export type PageKey = 'home' | 'profile' | 'feed' | 'features' | 'webmap' | 'wiki' | 'privacy' | 'request';

export type NavigationItem = {
  key: PageKey;
  label: string;
  subtitle: string;
  href: string;
  icon: string;
};

export const navigationItems: NavigationItem[] = [
  { key: 'home', label: 'Home', subtitle: 'Portal', href: '/', icon: '⌂' },
  { key: 'profile', label: 'Profile', subtitle: 'Custom page', href: '/profile', icon: '▣' },
  { key: 'feed', label: 'Feed', subtitle: 'MC Twitter', href: '/feed', icon: '✦' },
  { key: 'webmap', label: 'WebMap', subtitle: 'Live map', href: '/webmap/', icon: '◇' },
  { key: 'wiki', label: 'Wiki', subtitle: 'Toggle guide', href: '/wiki', icon: '❦' },
  { key: 'features', label: 'Features', subtitle: 'Guide', href: '/features', icon: '✧' },
];

export function pageKeyFromPath(pathname: string): PageKey {
  if (pathname === '/webmap' || pathname.startsWith('/webmap/')) return 'webmap';
  if (pathname.startsWith('/account')) return 'profile';
  if (pathname.startsWith('/profile')) return 'profile';
  if (pathname.startsWith('/feed')) return 'feed';
  if (pathname.startsWith('/wiki')) return 'wiki';
  if (pathname.startsWith('/features')) return 'features';
  if (pathname.startsWith('/privacy/request')) return 'request';
  if (pathname.startsWith('/privacy')) return 'privacy';
  return 'home';
}

export function wikiSlugFromPath(pathname: string): string | null {
  if (!pathname.startsWith('/wiki')) return null;
  const rest = pathname.replace(/^\/wiki\/?/, '').replace(/\/+$/, '');
  return rest === '' ? null : rest;
}
