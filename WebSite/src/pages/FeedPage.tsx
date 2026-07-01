import { useState } from 'react';
import { ComposeCard } from '../features/feed/ComposeCard';
import { displayName } from '../features/webservice/useWebService';
import { IconArrowLeft, IconComment, IconRepost, IconHeart, IconHeartFilled, IconShare, IconBell, IconTrend, IconSearch } from '../components/common/Icons';
import type { AuthProfile, WebPost, WebPostAttachment } from '../features/webservice/types';

type FeedPageProps = {
  busy: boolean;
  profile: AuthProfile | null;
  posts: WebPost[];
  onPost: (text: string, attachments: WebPostAttachment[], replyToId?: string) => Promise<boolean>;
  onLike: (postId: string) => Promise<boolean>;
  onRepost: (postId: string) => Promise<boolean>;
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

function postDateLabel(createdAt: number) {
  const diff = Date.now() - createdAt;
  if (diff < 60_000) return 'now';
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h`;
  return new Date(createdAt).toLocaleDateString('ja-JP', { month: 'short', day: 'numeric' });
}

function FeedPostCard({ post, profile, busy, onPost, onLike, onRepost, onNavigate }: {
  post: WebPost;
  profile: AuthProfile;
  busy: boolean;
  onPost: (text: string, attachments: WebPostAttachment[], replyToId?: string) => Promise<boolean>;
  onLike: (postId: string) => Promise<boolean>;
  onRepost: (postId: string) => Promise<boolean>;
  onNavigate: (href: string) => void;
}) {
  const [replying, setReplying] = useState(false);
  const [copied, setCopied] = useState(false);

  const copyUrl = async () => {
    await copyText(`${window.location.origin}/feed/post/${post.id}`);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1400);
  };

  return (
    <article className="post-card">
      <button className="post-avatar-link" type="button" onClick={() => onNavigate(`/profile/@${post.username}`)}>
        <img className="post-avatar" src={post.faceUrl || '/images/clear.png'} alt="" />
      </button>
      <div className="post-body">
        <div className="post-head">
          <button className="post-head-name" type="button" onClick={() => onNavigate(`/profile/@${post.username}`)}>
            <strong>{displayName(post)}</strong>
            <span className="post-handle">@{post.username}</span>
          </button>
          <span className="post-head-dot">·</span>
          <button className="post-time-button" type="button" onClick={() => onNavigate(`/feed/post/${post.id}`)}>{postDateLabel(post.createdAt)}</button>
          <span className="post-source-badge">{post.source === 'web' ? 'Web' : 'Minecraft'}</span>
        </div>
        {post.replyToUsername ? <button className="reply-context" type="button" onClick={() => onNavigate(`/profile/@${post.replyToUsername}`)}>返信先 <span>@{post.replyToUsername}</span></button> : null}
        <button className="post-click-body" type="button" onClick={() => onNavigate(`/feed/post/${post.id}`)}>
          <p>{post.text || '画像投稿'}</p>
          {post.attachments?.length ? <div className="post-images">{post.attachments.map((attachment, index) => <img key={`${post.id}-${index}`} src={attachment.url} alt="" />)}</div> : null}
        </button>
        <div className="post-actions">
          <button className="post-action post-action-reply" type="button" onClick={() => setReplying((value) => !value)}>
            <span className="post-action-icon"><IconComment size={17} /></span>
            <span className="post-action-count">{post.replies ?? 0}</span>
          </button>
          <button className={`post-action post-action-repost${post.repostedByMe ? ' is-active' : ''}`} type="button" onClick={() => onRepost(post.id)}>
            <span className="post-action-icon"><IconRepost size={17} /></span>
            <span className="post-action-count">{post.reposts ?? 0}</span>
          </button>
          <button className={`post-action post-action-like${post.likedByMe ? ' is-active' : ''}`} type="button" onClick={() => onLike(post.id)}>
            <span className="post-action-icon">{post.likedByMe ? <IconHeartFilled size={17} /> : <IconHeart size={17} />}</span>
            <span className="post-action-count">{post.likes ?? 0}</span>
          </button>
          <button className="post-action post-action-share" type="button" onClick={copyUrl}>
            <span className="post-action-icon"><IconShare size={17} /></span>
            {copied ? <span className="post-action-copied">コピー済み</span> : null}
          </button>
        </div>
        {replying ? <ComposeCard compact busy={busy} disabled={false} profile={profile} submitLabel="返信" placeholder={`@${post.username} に返信`} onCancel={() => setReplying(false)} onPost={(text, attachments) => onPost(text, attachments, post.id).then((ok) => { if (ok) setReplying(false); return ok; })} /> : null}
      </div>
    </article>
  );
}

export function FeedPage({ busy, profile, posts, onPost, onLike, onRepost, onNavigate }: FeedPageProps) {
  const [search, setSearch] = useState('');
  const route = feedRoute();
  const isMainTimeline = !route.postId && !route.username;
  const term = search.trim().toLowerCase();
  const visiblePosts = route.postId
    ? posts.filter((post) => post.id === route.postId || post.replyToId === route.postId)
    : route.username
      ? posts.filter((post) => post.username.toLowerCase() === route.username)
      : term
        ? posts.filter((post) => post.text.toLowerCase().includes(term) || post.username.toLowerCase().includes(term) || displayName(post).toLowerCase().includes(term))
        : posts;
  const viewedUser = route.username ? visiblePosts[0] : null;

  if (!profile) {
    return (
      <section className="feed-auth-gate x-scope">
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
    <div className="feed-page x-scope">
      <div className="feed-layout">
        <main className="feed-timeline-column">
          <header className="feed-sticky-head">
            <div className="feed-sticky-head-row">
              {route.postId || route.username ? (
                <button className="x-icon-btn feed-back-btn" type="button" aria-label="戻る" onClick={() => onNavigate('/feed')}><IconArrowLeft /></button>
              ) : null}
              <div className="feed-sticky-head-title">
                <h1>{route.postId ? '投稿' : route.username ? `@${route.username}` : 'ホーム'}</h1>
                {route.username ? <span>{visiblePosts.length}件の投稿</span> : null}
              </div>
            </div>
            <div className="feed-tabs" role="tablist" aria-label="タイムライン表示">
              <button className="is-active" type="button">投稿</button>
              <button type="button" onClick={() => onNavigate('/feed')}>タイムライン</button>
            </div>
          </header>
          {!route.username && !route.postId ? <ComposeCard busy={busy} disabled={false} profile={profile} onPost={onPost} /> : null}
          <div className="timeline">
            {visiblePosts.length ? visiblePosts.map((post) => <FeedPostCard key={post.id} post={post} profile={profile} busy={busy} onPost={onPost} onLike={onLike} onRepost={onRepost} onNavigate={onNavigate} />) : <div className="empty-feed">{term ? `「${search}」に一致する投稿は見つかりませんでした。` : 'まだ投稿はありません。最初の投稿をしてタイムラインを開始しましょう。'}</div>}
          </div>
        </main>
        <aside className="feed-right-rail" aria-label="Minecraft Twitter side panel">
          {isMainTimeline ? (
            <label className="feed-search-box">
              <IconSearch size={17} />
              <input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="検索" />
            </label>
          ) : null}
          <section className="feed-user-summary" onClick={() => onNavigate(`/profile/@${viewedUser?.username ?? profile.username}`)}>
            <img src={viewedUser?.faceUrl || profile.faceUrl || '/images/clear.png'} alt="" />
            <div>
              <strong>{viewedUser ? displayName(viewedUser) : displayName(profile)}</strong>
              <span>@{viewedUser?.username ?? profile.username}</span>
            </div>
          </section>
          <section className="feed-widget">
            <h2><IconBell size={18} /> 通知</h2>
            <p>新しい返信、いいね、Minecraft連携通知がここに表示されます。</p>
          </section>
          <section className="feed-widget">
            <h2><IconTrend size={18} /> トレンド</h2>
            <a href="/webmap/">#WebMap</a>
            <a href="/profile">#Profile</a>
            <a href="/features">#BetterSurvival</a>
          </section>
        </aside>
      </div>
    </div>
  );
}
