import { SectionShell } from '../components/SectionShell';
import { WikiLink } from '../components/WikiNavContext';
import { wikiEntries, type WikiEntry, type WikiScope } from '../wikiIndex';

const SCOPE_LABEL: Record<WikiScope, string> = {
  system: '概要',
  admin: '管理者向け',
  player: 'プレイヤー向け',
};

/** wikiEntries から scope + category ごとにグループ化して返す */
function groupEntries() {
  const scopes: WikiScope[] = ['admin', 'player'];
  return scopes.map((scope) => {
    const entries = wikiEntries.filter((e) => e.scope === scope && e.slug !== 'overview');
    const categoryOrder: (string | '__root__')[] = [];
    const grouped = new Map<string | '__root__', WikiEntry[]>();
    for (const entry of entries) {
      const key = entry.category ?? '__root__';
      if (!grouped.has(key)) {
        grouped.set(key, []);
        categoryOrder.push(key);
      }
      grouped.get(key)!.push(entry);
    }
    return {
      scope,
      groups: categoryOrder.map((key) => ({
        name: key === '__root__' ? 'その他' : key,
        entries: grouped.get(key) ?? [],
      })),
      total: entries.length,
    };
  });
}

export function OverviewSection() {
  const scoped = groupEntries();
  const total = wikiEntries.length;
  const adminCount = wikiEntries.filter((e) => e.scope === 'admin').length;
  const playerCount = wikiEntries.filter((e) => e.scope === 'player').length;

  return (
    <SectionShell
      eyebrow="Overview"
      title="BetterSurvival Wiki の全体像"
      intro={`この Wiki には現在 ${total} ページ (管理者向け ${adminCount} / プレイヤー向け ${playerCount}) が登録されています。ここは wikiIndex に沿って自動で組み立てられる目次ページなので、ページが増えたり整理されたりすると、この一覧も自動で追従します。`}
      scope="op"
    >
      <h3>この Wiki の歩き方</h3>
      <p>
        左のサイドナビは <strong>概要 / 管理者向け / プレイヤー向け</strong> の 3 層に分かれていて、プレイヤー向けはさらにテーマごとの折りたたみカテゴリに整理されています。ここでは全ページを俯瞰できるように、同じカテゴリ分けをそのまま並べています。
      </p>

      {scoped.map(({ scope, groups, total: scopeTotal }) => (
        <div key={scope} style={{ marginTop: 24 }}>
          <h3 style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}>
            <span>{SCOPE_LABEL[scope]}</span>
            <span style={{ fontSize: 12, opacity: 0.7, fontWeight: 700 }}>{scopeTotal} ページ</span>
          </h3>

          {groups.map((group) => (
            <div key={group.name} style={{ marginTop: 12 }}>
              <h4 style={{ margin: '0 0 8px' }}>{group.name}</h4>
              <div className="wiki-feature-list">
                {group.entries.map((entry) => (
                  <article key={entry.slug} className="wiki-feature-row wiki-guide-card">
                    <div className="wiki-guide-head">
                      <span className="wiki-guide-tag">{entry.scope === 'admin' ? 'OP' : entry.scope === 'player' ? 'Player' : 'System'}</span>
                      <h4 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span aria-hidden style={{ opacity: 0.75 }}>{entry.icon}</span>
                        <WikiLink to={`/wiki/${entry.slug}`}>{entry.title}</WikiLink>
                      </h4>
                    </div>
                    <p>{entry.summary}</p>
                  </article>
                ))}
              </div>
            </div>
          ))}
        </div>
      ))}

      <h3>この一覧はどうやって作られている?</h3>
      <p>
        タイトル、要約、アイコン、カテゴリはすべて <code>WebSite/src/pages/wiki/wikiIndex.ts</code> に集約された <code>wikiEntries</code> 配列を読んで生成しています。新しいセクションを追加したり、カテゴリを付け替えたりすると、サイドナビとこのページの両方に自動で反映されます。手作業でここを書き換える必要はありません。
      </p>
    </SectionShell>
  );
}
