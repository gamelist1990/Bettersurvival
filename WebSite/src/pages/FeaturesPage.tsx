import { SectionHeader } from '../components/common/SectionHeader';

export function FeaturesPage() {
  return (
    <div className="section-block">
      <SectionHeader eyebrow="Features" title="BetterSurvival" text="サーバーの世界をもっと見やすく、もっと楽しみやすくするためのページです。" />
      <div className="feature-grid">
        <article className="feature-card"><p className="card-eyebrow">Profile</p><h3>プロフィール</h3><p>プレイヤー情報や自己紹介を見やすくまとめられます。</p></article>
        <article className="feature-card"><p className="card-eyebrow">Timeline</p><h3>Minecraft Twitter</h3><p>サーバーの話題や投稿をタイムラインで楽しめます。</p></article>
        <article className="feature-card"><p className="card-eyebrow">Map</p><h3>ワールドマップ</h3><p>公開中のワールドをブラウザから確認できます。</p></article>
      </div>
    </div>
  );
}
