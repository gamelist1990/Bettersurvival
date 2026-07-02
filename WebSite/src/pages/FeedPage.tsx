import { useMemo, useState } from 'react';
import { ComposeCard } from '../features/feed/ComposeCard';
import { displayName } from '../features/webservice/useWebService';
import { IconArrowLeft, IconComment, IconRepost, IconHeart, IconHeartFilled, IconShare, IconBell, IconTrend, IconSearch, IconViews } from '../components/common/Icons';
import type { AuthProfile, WebPost, WebPostAttachment } from '../features/webservice/types';

type SearchUser = {
  username: string;
  name: string;
  faceUrl: string;
  posts: number;
};

type FeedNotification = {
  id: string;
  label: string;
  text: string;
  href: string;
  createdAt: number;
};

type FeedDisplayPost = {
  post: WebPost;
  repostedBy?: AuthProfile;
};

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

function compactCount(value: number) {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(value >= 10_000_000 ? 0 : 1)}M`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(value >= 10_000 ? 0 : 1)}K`;
  return String(value);
}

function viewCount(post: WebPost) {
  return post.views ?? Math.max(1, (post.likes ?? 0) * 48 + (post.reposts ?? 0) * 76 + (post.replies ?? 0) * 24 + 32);
}

function topLikedPosts(posts: WebPost[]) {
  return posts
    .filter((post) => !post.replyToId)
    .slice()
    .sort((a, b) => (b.likes ?? 0) - (a.likes ?? 0) || (b.reposts ?? 0) - (a.reposts ?? 0) || viewCount(b) - viewCount(a) || b.createdAt - a.createdAt)
    .slice(0, 3);
}

function timelineDisplayPosts(posts: WebPost[], profile: AuthProfile): FeedDisplayPost[] {
  const displays: FeedDisplayPost[] = posts.map((post) => ({ post }));
  for (const post of posts) {
    if (!post.repostedByMe || post.username.toLowerCase() === profile.username.toLowerCase()) continue;
    displays.push({ post, repostedBy: profile });
  }
  return displays.sort((a, b) => (b.repostedBy ? b.post.createdAt + 1 : b.post.createdAt) - (a.repostedBy ? a.post.createdAt + 1 : a.post.createdAt));
}

function uniqueUsers(posts: WebPost[], profile: AuthProfile): SearchUser[] {
  const users = new Map<string, SearchUser>();
  users.set(profile.username.toLowerCase(), {
    username: profile.username,
    name: displayName(profile),
    faceUrl: profile.faceUrl,
    posts: 0,
  });
  for (const post of posts) {
    const key = post.username.toLowerCase();
    const current = users.get(key);
    users.set(key, {
      username: post.username,
      name: displayName(post),
      faceUrl: post.faceUrl,
      posts: (current?.posts ?? 0) + 1,
    });
  }
  return Array.from(users.values()).sort((a, b) => b.posts - a.posts || a.username.localeCompare(b.username));
}

function notificationItems(posts: WebPost[], profile: AuthProfile): FeedNotification[] {
  const username = profile.username.toLowerCase();
  const items: FeedNotification[] = [];
  for (const post of posts) {
    const isOwnPost = post.username.toLowerCase() === username;
    const mentionsMe = post.text.toLowerCase().includes(`@${username}`) && !isOwnPost;
    const repliesToMe = post.replyToUsername?.toLowerCase() === username;
    if (repliesToMe) {
      items.push({ id: `reply-${post.id}`, label: '返信', text: `${displayName(post)} さんが返信しました`, href: `/feed/post/${post.id}`, createdAt: post.createdAt });
    } else if (mentionsMe) {
      items.push({ id: `mention-${post.id}`, label: 'メンション', text: `${displayName(post)} さんがあなたをメンションしました`, href: `/feed/post/${post.id}`, createdAt: post.createdAt });
    }
    if (isOwnPost && (post.likes ?? 0) > 0) {
      items.push({ id: `likes-${post.id}`, label: 'いいね', text: `投稿に ${post.likes} 件のいいねがあります`, href: `/feed/post/${post.id}`, createdAt: post.createdAt });
    }
    if (isOwnPost && (post.reposts ?? 0) > 0) {
      items.push({ id: `reposts-${post.id}`, label: 'リポスト', text: `投稿に ${post.reposts} 件のリポストがあります`, href: `/feed/post/${post.id}`, createdAt: post.createdAt });
    }
  }
  return items.sort((a, b) => b.createdAt - a.createdAt);
}

function replyCountFor(posts: WebPost[], postId: string) {
  return posts.filter((post) => post.replyToId === postId).length;
}

function threadPostsFor(posts: WebPost[], rootId: string) {
  const root = posts.find((post) => post.id === rootId);
  if (!root) return [];
  const children = new Map<string, WebPost[]>();
  for (const post of posts) {
    if (!post.replyToId) continue;
    const list = children.get(post.replyToId) ?? [];
    list.push(post);
    children.set(post.replyToId, list);
  }
  const result: WebPost[] = [];
  const append = (post: WebPost) => {
    result.push(post);
    const replies = (children.get(post.id) ?? []).slice().sort((a, b) => a.createdAt - b.createdAt);
    for (const reply of replies) append(reply);
  };
  append(root);
  return result;
}

function syntheticRepliesFor(post: WebPost): WebPost[] {
  const count = post.replies ?? 0;
  if (count <= 0) return [];
  return Array.from({ length: count }, (_, index) => ({
    id: `${post.id}-reply-placeholder-${index + 1}`,
    uuid: `${post.uuid}-reply-placeholder-${index + 1}`,
    username: post.username,
    displayName: post.displayName,
    nickname: post.nickname,
    faceUrl: post.faceUrl,
    source: post.source,
    text: `返信 ${index + 1} 件目の表示用データです。実際の返信本文はまだ取得されていません。`,
    attachments: [],
    createdAt: post.createdAt + (index + 1) * 60_000,
    replyToId: post.id,
    replyToUsername: post.username,
    likes: 0,
    replies: 0,
    reposts: 0,
    likedByMe: false,
    repostedByMe: false,
  }));
}

function FeedPostCard({ post, profile, busy, replyCount, showThreadLink, isThreadReply, repostedBy, onPost, onLike, onRepost, onNavigate }: {
  post: WebPost;
  profile: AuthProfile;
  busy: boolean;
  replyCount: number;
  showThreadLink: boolean;
  isThreadReply: boolean;
  repostedBy?: AuthProfile;
  onPost: (text: string, attachments: WebPostAttachment[], replyToId?: string) => Promise<boolean>;
  onLike: (postId: string) => Promise<boolean>;
  onRepost: (postId: string) => Promise<boolean>;
  onNavigate: (href: string) => void;
}) {
  const [replying, setReplying] = useState(false);
  const [copied, setCopied] = useState(false);
  const isReplyPost = Boolean(post.replyToId);

  const copyUrl = async () => {
    await copyText(`${window.location.origin}/feed/post/${post.id}`);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1400);
  };

  return (
    <article className={`post-card${isThreadReply ? ' is-thread-reply' : ''}`}>
      {repostedBy ? <div className="post-reposted-by"><IconRepost size={15} /> {displayName(repostedBy)} reposted</div> : null}
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
          {isThreadReply ? <span className="post-reply-badge">返信</span> : null}
          <span className="post-source-badge">{post.source === 'web' ? 'Web' : 'Minecraft'}</span>
        </div>
        {post.replyToUsername ? <button className="reply-context" type="button" onClick={() => onNavigate(`/profile/@${post.replyToUsername}`)}>返信先 <span>@{post.replyToUsername}</span></button> : null}
        <button className="post-click-body" type="button" onClick={() => onNavigate(`/feed/post/${post.id}`)}>
          <p>{post.text || '画像投稿'}</p>
          {post.attachments?.length ? <div className="post-images">{post.attachments.map((attachment, index) => <img key={`${post.id}-${index}`} src={attachment.url} alt="" />)}</div> : null}
        </button>
        <div className="post-actions">
          {isThreadReply ? (
            <span className="post-action post-action-reply-disabled" title="返信の中では返信できません">
              <span className="post-action-icon"><IconComment size={17} /></span>
              <span className="post-action-count">返信不可</span>
            </span>
          ) : (
            <button className="post-action post-action-reply" type="button" onClick={() => setReplying((value) => !value)}>
              <span className="post-action-icon"><IconComment size={17} /></span>
              <span className="post-action-count">{Math.max(replyCount, post.replies ?? 0)}</span>
            </button>
          )}
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
          <span className="post-action post-action-view" title="表示回数">
            <span className="post-action-icon"><IconViews size={17} /></span>
            <span className="post-action-count">{compactCount(viewCount(post))}</span>
          </span>
        </div>
        {!isReplyPost && showThreadLink && Math.max(replyCount, post.replies ?? 0) > 0 ? (
          <button className="post-thread-link" type="button" onClick={() => onNavigate(`/feed/post/${post.id}`)}>返信を表示</button>
        ) : null}
        {!isThreadReply && replying ? <ComposeCard compact busy={busy} disabled={false} profile={profile} submitLabel="返信" placeholder={`@${post.username} に返信`} onCancel={() => setReplying(false)} onPost={(text, attachments) => onPost(text, attachments, post.id).then((ok) => { if (ok) setReplying(false); return ok; })} /> : null}
      </div>
    </article>
  );
}

export function FeedPage({ busy, profile, posts, onPost, onLike, onRepost, onNavigate }: FeedPageProps) {
  const [search, setSearch] = useState('');
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const route = feedRoute();
  const isMainTimeline = !route.postId && !route.username;
  const term = search.trim().toLowerCase();
  const users = useMemo(() => profile ? uniqueUsers(posts, profile) : [], [posts, profile]);
  const userSuggestions = term.startsWith('@')
    ? users.filter((user) => user.username.toLowerCase().includes(term.slice(1)) || user.name.toLowerCase().includes(term.slice(1))).slice(0, 6)
    : [];
  const displayTimelinePosts: FeedDisplayPost[] = profile ? timelineDisplayPosts(posts, profile) : posts.map((post) => ({ post }));
  const visibleDisplayPosts: FeedDisplayPost[] = route.postId
    ? (() => {
        const thread = threadPostsFor(posts, route.postId);
        const root = thread[0] ?? posts.find((post) => post.id === route.postId);
        if (!root) return [];
        return (thread.length > 1 ? thread : [root, ...syntheticRepliesFor(root)]).map((post) => ({ post }));
      })()
    : route.username
      ? displayTimelinePosts.filter(({ post, repostedBy }) => post.username.toLowerCase() === route.username || repostedBy?.username.toLowerCase() === route.username)
      : term
        ? displayTimelinePosts.filter(({ post, repostedBy }) => post.text.toLowerCase().includes(term) || post.username.toLowerCase().includes(term) || displayName(post).toLowerCase().includes(term) || (repostedBy ? displayName(repostedBy).toLowerCase().includes(term) : false))
        : displayTimelinePosts;
  const visiblePosts = visibleDisplayPosts.map(({ post }) => post);
  const viewedUser = route.username ? visiblePosts[0] : null;
  const notifications = useMemo(() => profile ? notificationItems(posts, profile) : [], [posts, profile]);
  const previewNotifications = notifications.slice(0, 3);
  const trendingPosts = useMemo(() => topLikedPosts(posts), [posts]);

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
              <button className="feed-mobile-notification-button" type="button" aria-label="通知を表示" onClick={() => setNotificationsOpen(true)}>
                <IconBell size={18} />
                {notifications.length ? <span>{notifications.length}</span> : null}
              </button>
            </div>
            {isMainTimeline ? (
              <div className="feed-mobile-search-panel">
                <label className="feed-search-box">
                  <IconSearch size={17} />
                  <input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="検索 / @ユーザー" />
                </label>
                {userSuggestions.length ? (
                  <div className="feed-search-suggestions" role="listbox" aria-label="ユーザー候補">
                    {userSuggestions.map((user) => (
                      <button key={user.username} type="button" onClick={() => { setSearch(''); onNavigate(`/profile/@${user.username}`); }}>
                        <img src={user.faceUrl || '/images/clear.png'} alt="" />
                        <span><strong>{user.name}</strong><small>@{user.username}</small></span>
                        <em>{user.posts} posts</em>
                      </button>
                    ))}
                  </div>
                ) : null}
              </div>
            ) : null}
          </header>
          {!route.username && !route.postId ? <ComposeCard busy={busy} disabled={false} profile={profile} onPost={onPost} /> : null}
          {route.postId && visiblePosts.length > 1 ? <div className="thread-replies-label">返信 {visiblePosts.length - 1} 件</div> : null}
          <div className="timeline">
            {visibleDisplayPosts.length ? visibleDisplayPosts.map(({ post, repostedBy }) => <FeedPostCard key={repostedBy ? `${repostedBy.username}-repost-${post.id}` : post.id} post={post} profile={profile} busy={busy} replyCount={replyCountFor(posts, post.id)} showThreadLink={!route.postId} isThreadReply={Boolean(route.postId && post.id !== route.postId)} repostedBy={repostedBy} onPost={onPost} onLike={onLike} onRepost={onRepost} onNavigate={onNavigate} />) : <div className="empty-feed">{term ? `「${search}」に一致する投稿は見つかりませんでした。` : 'まだ投稿はありません。最初の投稿を開始しましょう！！'}</div>}
          </div>
        </main>
        <aside className="feed-right-rail" aria-label="Minecraft Twitter side panel">
          {isMainTimeline ? (
            <div className="feed-search-panel">
              <label className="feed-search-box">
                <IconSearch size={17} />
                <input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="検索 / @ユーザー" />
              </label>
              {userSuggestions.length ? (
                <div className="feed-search-suggestions" role="listbox" aria-label="ユーザー候補">
                  {userSuggestions.map((user) => (
                    <button key={user.username} type="button" onClick={() => { setSearch(''); onNavigate(`/profile/@${user.username}`); }}>
                      <img src={user.faceUrl || '/images/clear.png'} alt="" />
                      <span><strong>{user.name}</strong><small>@{user.username}</small></span>
                      <em>{user.posts} posts</em>
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
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
            {previewNotifications.length ? (
              <div className="feed-notification-list">
                {previewNotifications.map((item) => (
                  <button key={item.id} type="button" onClick={() => onNavigate(item.href)}>
                    <strong>{item.label}</strong>
                    <span>{item.text}</span>
                  </button>
                ))}
              </div>
            ) : <p>新しい返信、いいね、Minecraft連携通知がここに表示されます。</p>}
            {notifications.length > previewNotifications.length ? (
              <button className="feed-widget-more" type="button" onClick={() => setNotificationsOpen(true)}>通知をすべて表示 =&gt;</button>
            ) : null}
          </section>
          <section className="feed-widget">
            <h2><IconTrend size={18} /> トレンド</h2>
            <div className="feed-trend-list">
              {trendingPosts.map((post, index) => (
                <button key={post.id} type="button" onClick={() => onNavigate(`/feed/post/${post.id}`)}>
                  <strong>#{index + 1} {displayName(post)}</strong>
                  <span>{post.text || '画像投稿'}</span>
                  <small>❤ {compactCount(post.likes ?? 0)} · ↻ {compactCount(post.reposts ?? 0)} · {compactCount(viewCount(post))} views</small>
                </button>
              ))}
            </div>
          </section>
        </aside>
      </div>
      {notificationsOpen ? (
        <div className="feed-modal-overlay" role="presentation" onClick={() => setNotificationsOpen(false)}>
          <section className="feed-modal" role="dialog" aria-modal="true" aria-label="通知一覧" onClick={(event) => event.stopPropagation()}>
            <header>
              <h2><IconBell size={18} /> 通知</h2>
              <button className="x-icon-btn" type="button" aria-label="閉じる" onClick={() => setNotificationsOpen(false)}>×</button>
            </header>
            <div className="feed-modal-list">
              {notifications.map((item) => (
                <button key={item.id} type="button" onClick={() => { setNotificationsOpen(false); onNavigate(item.href); }}>
                  <strong>{item.label}</strong>
                  <span>{item.text}</span>
                  <small>{postDateLabel(item.createdAt)}</small>
                </button>
              ))}
            </div>
          </section>
        </div>
      ) : null}
    </div>
  );
}
