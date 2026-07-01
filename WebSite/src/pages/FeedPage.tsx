import { ComposeCard } from '../features/feed/ComposeCard';
import { FeedTimeline } from '../features/feed/FeedTimeline';
import { displayName } from '../features/webservice/useWebService';
import type { AuthProfile, WebPost, WebPostAttachment } from '../features/webservice/types';

type FeedPageProps = {
  busy: boolean;
  profile: AuthProfile | null;
  posts: WebPost[];
  onPost: (text: string, attachments: WebPostAttachment[]) => Promise<boolean>;
  onNavigate: (href: string) => void;
};

function feedRoute() {
  const postMatch = window.location.pathname.match(/^\/feed\/post\/([^/?#]+)/);
  if (postMatch) return { postId: decodeURIComponent(postMatch[1]), username: '' };
  const userMatch = window.location.pathname.match(/^\/feed\/@?([^/?#]+)/);
  return { postId: '', username: userMatch ? decodeURIComponent(userMatch[1]).replace(/^@/, '').toLowerCase() : '' };
}

async function copyText(text: string) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return;
    }
  } catch {
    // fallback below
  }
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.focus();
  textarea.select();
  document.execCommand('copy');
  textarea.remove();
}

export function FeedPage({ busy, profile, posts, onPost, onNavigate }: FeedPageProps) {
  const route = feedRoute();
  const visiblePosts = route.postId
    ? posts.filter((post) => post.id === route.postId)
    : route.username
      ? posts.filter((post) => post.username.toLowerCase() === route.username)
      : posts;
  const viewedUser = route.username ? visiblePosts[0] : null;

  if (!profile) {
    return (
      <section className="feed-auth-gate">
        <div className="feed-auth-card">
          <p className="panel-label">Minecraft Twitter</p>
          <h1>ログイン済みユーザー専用です</h1>
          <p>Web版 Minecraft Twitter は、プロフィールへログインしたプレイヤーだけが閲覧・投稿できます。</p>
          <button className="primary-button" type="button" onClick={() => onNavigate('/profile')}>プロフィールでログイン</button>
        </div>
      </section>
    );
  }

  return (
    <div className="feed-page">
      <div className="feed-layout feed-twitter-shell">
        <main className="feed-timeline-column">
          <header className="feed-sticky-head">
            <h1>{route.postId ? '投稿' : route.username ? `@${route.username}` : 'Minecraft Twitter'}</h1>
            <div className="feed-tabs" role="tablist" aria-label="タイムライン表示">
              <button className="is-active" type="button">投稿</button>
              <button type="button" onClick={() => onNavigate('/feed')}>タイムライン</button>
            </div>
          </header>
          {!route.username && !route.postId ? <ComposeCard busy={busy} disabled={false} profile={profile} onPost={onPost} /> : null}
          <FeedTimeline posts={visiblePosts} onNavigate={onNavigate} onCopyPostUrl={(post) => copyText(`${window.location.origin}/feed/post/${post.id}`)} />
        </main>
        <aside className="feed-right-rail" aria-label="Minecraft Twitter side panel">
          <section className="feed-user-summary" onClick={() => onNavigate(`/profile/@${viewedUser?.username ?? profile.username}`)}>
            <img src={viewedUser?.faceUrl || profile.faceUrl || '/images/clear.png'} alt="" />
            <div>
              <strong>{viewedUser ? displayName(viewedUser) : displayName(profile)}</strong>
              <span>@{viewedUser?.username ?? profile.username}</span>
            </div>
          </section>
          <section className="feed-widget">
            <h2>通知</h2>
            <p>新しい返信、いいね、Minecraft連携通知がここに表示されます。</p>
          </section>
          <section className="feed-widget">
            <h2>トレンド</h2>
            <a href="/webmap/">#WebMap</a>
            <a href="/profile">#Profile</a>
            <a href="/features">#BetterSurvival</a>
          </section>
        </aside>
      </div>
    </div>
  );
}
