import { useEffect, useMemo, useState } from 'react';
import { SectionHeader } from '../components/common/SectionHeader';
import { AuthPanel } from '../features/auth/AuthPanel';
import { ProfileEditor } from '../features/profile/ProfileEditor';
import { profileToDraft } from '../features/webservice/api';
import { IconArrowLeft, IconPin, IconLink, IconCalendar, IconTrend } from '../components/common/Icons';
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

function publicName(profile: PublicProfile) {
  return profile.nickname || profile.displayName || profile.username;
}

function joinedLabel(createdAt?: number) {
  if (!createdAt) return '';
  return new Date(createdAt).toLocaleDateString('ja-JP', { year: 'numeric', month: 'long' });
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

export function ProfilePage({ busy, message, profile, posts, onLogin, onRegister, onLogout, onSave, onNavigate }: ProfilePageProps) {
  const [draft, setDraft] = useState(profileToDraft(profile));
  const [copied, setCopied] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
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
  const profilePosts = viewedProfile ? posts.filter((post) => post.username.toLowerCase() === viewedProfile.username.toLowerCase()) : [];

  if (!profile && !requestedUsername) {
    return (
      <div className="profile-login-grid x-scope">
        <section className="panel profile-login-intro">
          <SectionHeader eyebrow="Profile" title="ログインが必要です" text="ログインするとプロフィールの編集やコミュニティ投稿を利用できます。" />
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
          <div><strong>{publicName(viewedProfile)}</strong><span>{profilePosts.length} posts</span></div>
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
          <div className="twitter-profile-stats"><strong>{profilePosts.length}</strong><span>Posts</span><strong>{new Set(posts.map((post) => post.username)).size}</strong><span>Players</span></div>
        </section>
        <nav className="twitter-profile-tabs" aria-label="プロフィール投稿フィルター">
          <button className="is-active" type="button">投稿</button>
          <button type="button">画像</button>
          <button type="button">返信</button>
        </nav>
        <div className="twitter-profile-posts">
          {profilePosts.length ? profilePosts.map((post) => (
            <article className="profile-post-row" key={post.id} onClick={() => onNavigate(`/feed/post/${post.id}`)}>
              <img src={post.faceUrl || '/images/clear.png'} alt="" />
              <div>
                <p><strong>{publicName(viewedProfile)}</strong><span>@{post.username}</span><span>{post.source === 'web' ? 'Web' : 'Minecraft'}</span></p>
                <div>{post.text || '画像投稿'}</div>
                {post.attachments?.length ? <div className="profile-post-images">{post.attachments.map((attachment, index) => <img key={`${post.id}-${index}`} src={attachment.url} alt="" />)}</div> : null}
              </div>
            </article>
          )) : <div className="profile-empty-posts">まだ投稿はありません。</div>}
        </div>
      </main>
      <aside className="feed-right-rail" aria-label="Minecraft Twitter side panel">
        <section className="feed-widget">
          <h2><IconTrend size={18} /> トレンド</h2>
          <a href="/feed">#MinecraftTwitter</a>
          <a href="/webmap/">#WebMap</a>
          <a href="/features">#BetterSurvival</a>
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
