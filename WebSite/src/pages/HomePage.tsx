import { SectionHeader } from '../components/common/SectionHeader';
import type { AuthProfile, WebPost } from '../features/webservice/types';
import { displayName } from '../features/webservice/useWebService';

type HomePageProps = {
  profile: AuthProfile | null;
  posts: WebPost[];
  onNavigate: (href: string) => void;
};

export function HomePage({ profile, posts, onNavigate }: HomePageProps) {
  const latestPost = posts[0];
  return (
    <div className="page-grid page-grid-home">
      <section className="hero-panel">
        <div className="hero-visual">
          <div className="hero-cover" aria-hidden="true"><span className="block block-grass" /><span className="block block-stone" /><span className="block block-diamond" /></div>
          <div className="hero-overlay">
            <p className="eyebrow">Official Portal</p>
            <h1>BetterSurvival</h1>
            <p>サーバーの最新情報、ワールドマップ、プロフィール、コミュニティ投稿をまとめて楽しめます。</p>
          </div>
        </div>
        <div className="hero-actions">
          <button className="primary-button" type="button" onClick={() => onNavigate('/profile')}>プロフィールを作成</button>
          <button className="secondary-button" type="button" onClick={() => onNavigate('/feed')}>投稿を見る</button>
          <button className="secondary-button" type="button" onClick={() => onNavigate('/webmap/')}>WebMap を開く</button>
        </div>
      </section>
      <aside className="panel spotlight-panel">
        <SectionHeader eyebrow="Community" title="Minecraft Twitter" text="サーバーの話題やプレイヤーの投稿を確認できます。" />
        <div className="spotlight-list">
          <div className="spotlight-item"><span>Profile</span><strong>{profile ? displayName(profile) : '未ログイン'}</strong></div>
          <div className="spotlight-item"><span>Latest</span><strong>{latestPost ? `${displayName(latestPost)}: ${latestPost.text || '画像投稿'}` : '投稿待ち'}</strong></div>
          <div className="spotlight-item"><span>Map</span><strong>ワールドマップ公開中</strong></div>
        </div>
      </aside>
    </div>
  );
}
