import { WikiLayout } from './WikiLayout';
import { WikiHome } from './WikiHome';
import { findWikiEntry, wikiEntries } from './wikiIndex';
import { WikiNavProvider } from './components/WikiNavContext';

type WikiPageProps = {
  slug: string | null;
  onNavigate: (href: string) => void;
};

export function WikiPage({ slug, onNavigate }: WikiPageProps) {
  const entry = findWikiEntry(slug);

  return (
    <WikiNavProvider navigate={onNavigate}>
      <WikiLayout activeSlug={entry?.slug ?? null} onNavigate={onNavigate}>
        {entry ? <entry.component /> : <WikiHome onNavigate={onNavigate} entries={wikiEntries} />}
      </WikiLayout>
    </WikiNavProvider>
  );
}
