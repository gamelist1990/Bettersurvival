import { useEffect, useMemo, useState } from 'react';
import { SectionHeader } from '../components/common/SectionHeader';
import { AuthPanel } from '../features/auth/AuthPanel';
import { ProfileEditor } from '../features/profile/ProfileEditor';
import { profileToDraft } from '../features/webservice/api';
import { MarkdownText } from '../features/feed/MarkdownText';
import { IconArrowLeft, IconPin, IconLink, IconCalendar, IconTrend, IconRepost, IconHeart, IconHeartFilled, IconShare, IconViews } from '../components/common/Icons';
import type { AuthProfile, ProfileDraft, WebPost } from '../features/webservice/types';

type ProfilePageProps = {
  busy: boolean;
  message: string;
  profile: AuthProfile | null;
  posts: WebPost[];
  onLogin: (username: string, password: string) => Promise<boolean>;
  onRegister: (code: string, email: string, password: string) => Promise<boolean>;
  onLogout: () => Promise<void>;
  onSave: (draft: ProfileDraft) => Promise<boolean>;
  onLike: (postId: string) => Promise<boolean>;
  onRepost: (postId: string) => Promise<boolean>;
  onNavigate: (href: string) => void;
};

type PublicProfile = {
  uuid: string;
  username: string;
  displayName: string;
  nickname: string;
  faceUrl: string;
  bannerUrl: string;
  bio: string;
  country: string;
  region: string;
  location: string;
  website: string;
  xUrl: string;
  youtubeUrl: string;
  instagramUrl: string;
  createdAt?: number;
};

type ProfileTab = 'posts' | 'media';

type ProfileDisplayPost = {
  post: WebPost;
  repostedBy?: PublicProfile;
};

function publicName(profile: PublicProfile) {
  return profile.nickname || profile.displayName || profile.username;
}

function joinedLabel(createdAt?: number) {
  if (!createdAt) return '';
  return new Date(createdAt).toLocaleDateString('ja-JP', { year: 'numeric', month: 'long' });
}

function compactCount(value: number) {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(value >= 10_000_000 ? 0 : 1)}M`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(value >= 10_000 ? 0 : 1)}K`;
  return String(value);
}

function postDateLabel(createdAt: number) {
  const diff = Date.now() - createdAt;
  if (diff < 60_000) return 'now';
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h`;
  return new Date(createdAt).toLocaleDateString('ja-JP', { month: 'short', day: 'numeric' });
}

function viewCount(post: WebPost) {
  return post.views ?? 0;
}

function topLikedPosts(posts: WebPost[]) {
  return posts
    .slice()
    .sort((a, b) => (b.likes ?? 0) - (a.likes ?? 0) || (b.reposts ?? 0) - (a.reposts ?? 0) || viewCount(b) - viewCount(a) || b.createdAt - a.createdAt)
    .slice(0, 3);
}

function profileFromAuth(profile: AuthProfile): PublicProfile {
  return {
    uuid: profile.uuid,
    username: profile.username,
    displayName: profile.displayName,
    nickname: profile.nickname,
    faceUrl: profile.faceUrl,
    bannerUrl: profile.bannerUrl,
    bio: profile.bio,
    country: profile.country,
    region: profile.region,
    location: profile.location,
    website: profile.website,
    xUrl: profile.xUrl,
    youtubeUrl: profile.youtubeUrl,
    instagramUrl: profile.instagramUrl,
    createdAt: profile.createdAt,
  };
}

function profileFromPost(post: WebPost): PublicProfile {
  return {
    uuid: post.uuid,
    username: post.username,
    displayName: post.displayName,
    nickname: post.nickname,
    faceUrl: post.faceUrl,
    bannerUrl: '',
    bio: '',
    country: '',
    region: '',
    location: '',
    website: '',
    xUrl: '',
    youtubeUrl: '',
    instagramUrl: '',
  };
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

function requestedProfileName() {
  const match = window.location.pathname.match(/^\/profile\/@?([^/?#]+)/);
  return match ? decodeURIComponent(match[1]).replace(/^@/, '').toLowerCase() : '';
}

export function ProfilePage({ busy, message, profile, posts, onLogin, onRegister, onLogout, onSave, onLike, onRepost, onNavigate }: ProfilePageProps) {
  const [draft, setDraft] = useState(profileToDraft(profile));
  const [copied, setCopied] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<ProfileTab>('posts');
  useEffect(() => setDraft(profileToDraft(profile)), [profile]);

  const requestedUsername = requestedProfileName();
  const profileIndex = useMemo(() => {
    const map = new Map<string, PublicProfile>();
    for (const post of posts) {
      if (post.username) map.set(post.username.toLowerCase(), profileFromPost(post));
    }
    if (profile?.username) map.set(profile.username.toLowerCase(), profileFromAuth(profile));
    return map;
  }, [posts, profile]);

  const isOwnProfile = Boolean(profile && (!requestedUsername || requestedUsername === profile.username.toLowerCase()));
  const viewedProfile = isOwnProfile && profile ? profileFromAuth(profile) : requestedUsername ? profileIndex.get(requestedUsername) ?? null : profile ? profileFromAuth(profile) : null;
  const ownRepostProfile = isOwnProfile && profile ? profileFromAuth(profile) : null;
  const profilePosts = viewedProfile ? posts.filter((post) => post.username.toLowerCase() === viewedProfile.username.toLowerCase()) : [];
  const profileDisplayPostsRaw: ProfileDisplayPost[] = viewedProfile ? [
    ...profilePosts.map((post): ProfileDisplayPost => ({ post })),
    ...(ownRepostProfile ? posts
      .filter((post) => post.repostedByMe && post.username.toLowerCase() !== ownRepostProfile.username.toLowerCase())
      .map((post): ProfileDisplayPost => ({ post, repostedBy: ownRepostProfile })) : []),
  ] : [];
  const profileDisplayPosts = profileDisplayPostsRaw.sort((a, b) => (b.repostedBy ? b.post.createdAt + 1 : b.post.createdAt) - (a.repostedBy ? a.post.createdAt + 1 : a.post.createdAt));
  const mediaProfilePosts = profileDisplayPosts.filter(({ post }) => post.attachments?.some((attachment) => attachment.type === 'image' || attachment.url));
  const visibleProfilePosts = activeTab === 'media' ? mediaProfilePosts : profileDisplayPosts;
  const emptyMessage = activeTab === 'media' ? 'まだ画像投稿はありません。' : 'まだ投稿はありません。';
  const trendingPosts = useMemo(() => topLikedPosts(posts), [posts]);

  useEffect(() => setActiveTab('posts'), [viewedProfile?.username]);

  if (!profile && !requestedUsername) {
    return (
      <div className="profile-login-grid x-scope">
        <section className="panel profile-login-intro">
          <SectionHeader eyebrow="Profile" title="ログインが必要です" text="ログインするとプロフィールの編集やコミュニティ投稿を利用できます。" />
          <div className="profile-register-steps" aria-label="登録手順">
            <p>登録の流れ</p>
            <ol>
              <li>Minecraftサーバー内で確認コードを発行します。</li>
              <li>この画面で確認コードとパスワードを入力します。</li>
              <li>「登録する」を押した後、利用規約とプライバシーポリシーを確認します。</li>
              <li>同意すると登録が完了し、プロフィール編集と投稿が使えるようになります。</li>
            </ol>
          </div>
          <div className="profile-login-points">
            <span>安全なログイン</span>
            <span>プロフィール編集</span>
            <span>コミュニティ投稿</span>
          </div>
        </section>
        <AuthPanel busy={busy} message={message} onLogin={onLogin} onRegister={onRegister} />
      </div>
    );
  }

  if (!viewedProfile) {
    return (
      <section className="twitter-profile-shell profile-empty-state x-scope">
        <div className="twitter-profile-topbar">
          <button className="x-icon-btn" type="button" aria-label="戻る" onClick={() => onNavigate('/feed')}><IconArrowLeft /></button>
          <div><strong>プロフィール</strong><span>見つかりません</span></div>
        </div>
        <div className="profile-not-found-card">
          <h1>このプロフィールは表示できません</h1>
          <p>投稿がまだない、またはURLが正しくない可能性があります。</p>
          <button className="primary-button" type="button" onClick={() => onNavigate('/feed')}>投稿へ戻る</button>
        </div>
      </section>
    );
  }

  const profileUrl = `${window.location.origin}/profile/@${viewedProfile.username}`;
  const bannerStyle = viewedProfile.bannerUrl ? { backgroundImage: `url(${viewedProfile.bannerUrl})` } : undefined;

  return (
    <div className="twitter-profile-layout x-scope">
      <main className="twitter-profile-shell">
        <div className="twitter-profile-topbar twitter-profile-topbar--profile">
          <div><strong>{publicName(viewedProfile)}</strong><span>{profileDisplayPosts.length} posts</span></div>
          <button className="x-icon-btn" type="button" onClick={() => onNavigate('/feed')}>Feedへ</button>
        </div>
        <div className="twitter-profile-banner" style={bannerStyle} />
        <section className="twitter-profile-main">
          <div className="twitter-profile-avatar-row">
            <img className="twitter-profile-avatar" src={viewedProfile.faceUrl || '/images/clear.png'} alt="" />
            <div className="twitter-profile-actions">
              <button className="x-pill" type="button" onClick={async () => { await copyText(profileUrl); setCopied(true); window.setTimeout(() => setCopied(false), 1400); }}>{copied ? 'コピー済み' : 'URLコピー'}</button>
              <button className="x-pill" type="button" onClick={() => onNavigate(`/feed/@${viewedProfile.username}`)}>投稿を見る</button>
              {isOwnProfile ? <button className="x-pill" type="button" onClick={onLogout}>ログアウト</button> : null}
              {isOwnProfile ? <button className="x-pill x-pill-outline-strong" type="button" onClick={() => setEditOpen(true)}>Edit profile</button> : null}
            </div>
          </div>
          <h1>{publicName(viewedProfile)}</h1>
          <p className="twitter-profile-handle">@{viewedProfile.username}</p>
          {viewedProfile.bio ? <p className="twitter-profile-bio">{viewedProfile.bio}</p> : <p className="twitter-profile-bio muted">まだ自己紹介はありません。</p>}
          <div className="twitter-profile-meta">
            {viewedProfile.location ? <span><IconPin size={14} /> {viewedProfile.location}</span> : null}
            {viewedProfile.region || viewedProfile.country ? <span>{[viewedProfile.region, viewedProfile.country].filter(Boolean).join(' / ')}</span> : null}
            {viewedProfile.website ? <a href={viewedProfile.website} target="_blank" rel="noreferrer"><IconLink size={14} /> Website</a> : null}
            {viewedProfile.xUrl ? <a href={viewedProfile.xUrl} target="_blank" rel="noreferrer"><IconLink size={14} /> X</a> : null}
            {viewedProfile.youtubeUrl ? <a href={viewedProfile.youtubeUrl} target="_blank" rel="noreferrer"><IconLink size={14} /> YouTube</a> : null}
            {viewedProfile.instagramUrl ? <a href={viewedProfile.instagramUrl} target="_blank" rel="noreferrer"><IconLink size={14} /> Instagram</a> : null}
            {viewedProfile.createdAt ? <span className="twitter-profile-joined"><IconCalendar size={14} /> {joinedLabel(viewedProfile.createdAt)}から利用</span> : null}
          </div>
          <div className="twitter-profile-stats"><strong>{profileDisplayPosts.length}</strong><span>Posts</span><strong>{new Set(posts.map((post) => post.username)).size}</strong><span>Players</span></div>
        </section>
        <nav className="twitter-profile-tabs" aria-label="プロフィール投稿フィルター">
          <button className={activeTab === 'posts' ? 'is-active' : ''} type="button" aria-pressed={activeTab === 'posts'} onClick={() => setActiveTab('posts')}>投稿</button>
          <button className={activeTab === 'media' ? 'is-active' : ''} type="button" aria-pressed={activeTab === 'media'} onClick={() => setActiveTab('media')}>画像</button>
        </nav>
        <div className="twitter-profile-posts">
          {visibleProfilePosts.length ? visibleProfilePosts.map(({ post, repostedBy }) => (
            <article className="post-card profile-post-row" key={repostedBy ? `${repostedBy.username}-repost-${post.id}` : post.id}>
              {repostedBy ? <div className="post-reposted-by profile-reposted-by"><IconRepost size={15} /> {publicName(repostedBy)} reposted</div> : null}
              <button className="post-avatar-link" type="button" onClick={() => onNavigate(`/profile/@${post.username}`)}>
                <img className="post-avatar" src={post.faceUrl || '/images/clear.png'} alt="" />
              </button>
              <div className="post-body">
                <div className="post-head">
                  <button className="post-head-name" type="button" onClick={() => onNavigate(`/profile/@${post.username}`)}>
                    <strong>{post.nickname || post.displayName || post.username}</strong>
                    <span className="post-handle">@{post.username}</span>
                  </button>
                  <span className="post-head-dot">·</span>
                  <button className="post-time-button" type="button" onClick={() => onNavigate(`/feed/post/${post.id}`)}>{postDateLabel(post.createdAt)}</button>
                  <span className={`post-source-badge post-source-${post.source}`}>{post.source === 'web' ? 'Web' : post.source === 'discord' ? 'Discord' : 'Minecraft'}</span>
                </div>
                <div className="post-click-body" role="link" tabIndex={0} onClick={(event) => { if (!(event.target as HTMLElement).closest('a')) onNavigate(`/feed/post/${post.id}`); }}>
                  <div className="post-text">{post.text ? <MarkdownText text={post.text} /> : '画像投稿'}</div>
                  {post.attachments?.length ? <div className="post-images profile-post-images">{post.attachments.map((attachment, index) => <img key={`${post.id}-${index}`} src={attachment.url} alt={attachment.name ?? ''} title={attachment.name ?? ''} loading="lazy" />)}</div> : null}
                </div>
                <div className="post-actions profile-post-stats">
                  <button className={`post-action post-action-repost${post.repostedByMe ? ' is-active' : ''}`} type="button" onClick={() => onRepost(post.id)}>
                    <span className="post-action-icon"><IconRepost size={17} /></span>
                    <span className="post-action-count">{compactCount(post.reposts ?? 0)}</span>
                  </button>
                  <button className={`post-action post-action-like${post.likedByMe ? ' is-active' : ''}`} type="button" onClick={() => onLike(post.id)}>
                    <span className="post-action-icon">{post.likedByMe ? <IconHeartFilled size={17} /> : <IconHeart size={17} />}</span>
                    <span className="post-action-count">{compactCount(post.likes ?? 0)}</span>
                  </button>
                  <button className="post-action post-action-share" type="button" onClick={async () => { await copyText(`${window.location.origin}/feed/post/${post.id}`); setCopied(true); window.setTimeout(() => setCopied(false), 1400); }}>
                    <span className="post-action-icon"><IconShare size={17} /></span>
                    {copied ? <span className="post-action-copied">コピー済み</span> : null}
                  </button>
                  <span className="post-action post-action-view" title="表示回数">
                    <span className="post-action-icon"><IconViews size={17} /></span>
                    <span className="post-action-count">{compactCount(viewCount(post))}</span>
                  </span>
                </div>
              </div>
            </article>
          )) : <div className="profile-empty-posts">{emptyMessage}</div>}
        </div>
      </main>
      <aside className="feed-right-rail" aria-label="Minecraft Twitter side panel">
        <section className="feed-user-summary" onClick={() => onNavigate(`/feed/@${viewedProfile.username}`)}>
          <img src={viewedProfile.faceUrl || '/images/clear.png'} alt="" />
          <div>
            <strong>{publicName(viewedProfile)}</strong>
            <span>@{viewedProfile.username} · {profileDisplayPosts.length} posts</span>
          </div>
        </section>
        <section className="feed-widget">
          <h2><IconLink size={18} /> クイックリンク</h2>
          <div className="feed-trend-list">
            <button type="button" onClick={() => onNavigate(`/feed/@${viewedProfile.username}`)}>
              <strong>このユーザーの投稿</strong>
              <span>フィードで @{viewedProfile.username} の投稿だけを表示</span>
            </button>
            <button type="button" onClick={() => onNavigate('/feed')}>
              <strong>みんなのタイムライン</strong>
              <span>Minecraft Twitter のホームへ</span>
            </button>
          </div>
        </section>
        <section className="feed-widget">
          <h2><IconTrend size={18} /> トレンド</h2>
          <div className="feed-trend-list">
            {trendingPosts.map((post, index) => (
              <button key={post.id} type="button" onClick={() => onNavigate(`/feed/post/${post.id}`)}>
                <strong>#{index + 1} {post.nickname || post.displayName || post.username}</strong>
                <span>{post.text || '画像投稿'}</span>
                <small>❤ {compactCount(post.likes ?? 0)} · ↻ {compactCount(post.reposts ?? 0)} · {compactCount(viewCount(post))} views</small>
              </button>
            ))}
          </div>
        </section>
      </aside>
      {isOwnProfile && editOpen ? (
        <ProfileEditor
          draft={draft}
          busy={busy}
          faceUrl={viewedProfile.faceUrl}
          onChange={setDraft}
          onClose={() => setEditOpen(false)}
          onSave={() => onSave(draft).then(() => setEditOpen(false))}
        />
      ) : null}
    </div>
  );
}
