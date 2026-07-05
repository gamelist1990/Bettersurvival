import { SectionHeader } from '../../components/common/SectionHeader';
import { FetchedWikiImage } from './components/FetchedWikiImage';
import type { WikiEntry } from './wikiIndex';

const wikiHomeHeroImage = '/images/wiki/wiki-home-hero.png';

type WikiHomeProps = {
  entries: WikiEntry[];
  onNavigate: (href: string) => void;
};

export function WikiHome({ entries, onNavigate }: WikiHomeProps) {
  const scopeLabel = (scope: WikiEntry['scope']) => {
    if (scope === 'admin') return 'OP / 管理者向け';
    if (scope === 'player') return 'プレイヤー向け';
    return '全体ガイド';
  };

  return (
    <div className="section-block wiki-home">
      <SectionHeader
        eyebrow="Wiki"
        title="BetterSurvival 機能 Wiki"
        text="/toggle だけに限定せず、実装済みのカスタムブロック、カスタムエンチャント、便利機能、管理者向けスイッチをまとめたガイドです。"
      />

      <FetchedWikiImage
        src={wikiHomeHeroImage}
        alt="BetterSurvival Wiki トップのヒーロー画像"
        caption="BetterSurvival の代表機能を紹介する Wiki トップ画像。カスタムブロック、エンチャント UI、便利機能などを案内する入口です。"
      />

      <div className="wiki-card-grid">
        {entries.map((entry) => (
          <a
            key={entry.slug}
            href={`/wiki/${entry.slug}`}
            className="wiki-card"
            onClick={(event) => {
              event.preventDefault();
              onNavigate(`/wiki/${entry.slug}`);
            }}
          >
            <span className="wiki-card-icon" aria-hidden>{entry.icon}</span>
            <span className="wiki-card-scope">{scopeLabel(entry.scope)}</span>
            <h3 className="wiki-card-title">{entry.title}</h3>
            <p className="wiki-card-summary">{entry.summary}</p>
          </a>
        ))}
      </div>
    </div>
  );
}
