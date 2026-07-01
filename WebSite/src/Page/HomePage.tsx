import { useEffect, useState } from "react";

type AuthProfile = {
  uuid: string;
  username: string;
  displayName: string;
  faceUrl: string;
  email: string;
  bio: string;
  location: string;
  website: string;
  nickname: string;
  country: string;
  region: string;
  bannerUrl: string;
  xUrl: string;
  youtubeUrl: string;
  instagramUrl: string;
  passkeyEnabled: boolean;
  createdAt: number;
  updatedAt: number;
};

type WebPostAttachment = {
  type: string;
  url: string;
  width: number;
  height: number;
};

type WebPost = {
  id: string;
  uuid: string;
  username: string;
  displayName: string;
  nickname: string;
  faceUrl: string;
  source: "minecraft" | "web" | string;
  text: string;
  attachments: WebPostAttachment[];
  createdAt: number;
};

type AuthResponse = {
  success?: boolean;
  message?: string;
  token?: string;
  profile?: AuthProfile;
  authenticated?: boolean;
  posts?: WebPost[];
  post?: WebPost;
  updatedAt?: number;
};

const TOKEN_KEY = "bettersurvival.website.token";

function storedToken() {
  try {
    return window.localStorage.getItem(TOKEN_KEY) ?? "";
  } catch {
    return "";
  }
}

function saveToken(token: string) {
  try {
    if (token) {
      window.localStorage.setItem(TOKEN_KEY, token);
    } else {
      window.localStorage.removeItem(TOKEN_KEY);
    }
  } catch {
    // ignore private storage failures
  }
}

async function postJson(path: string, body: Record<string, unknown>, token = "") {
  const response = await fetch(path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });
  return (await response.json()) as AuthResponse;
}

export default function HomePage() {
  const [mode, setMode] = useState<"login" | "register">("register");
  const [username, setUsername] = useState("");
  const [code, setCode] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [token, setToken] = useState(storedToken());
  const [profile, setProfile] = useState<AuthProfile | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [feedPosts, setFeedPosts] = useState<WebPost[]>([]);
  const [postText, setPostText] = useState("");
  const [attachments, setAttachments] = useState<WebPostAttachment[]>([]);
  const [profileDraft, setProfileDraft] = useState({
    nickname: "",
    bio: "",
    location: "",
    country: "",
    region: "",
    bannerUrl: "",
    website: "",
    xUrl: "",
    youtubeUrl: "",
    instagramUrl: "",
  });

  const syncProfileDraft = (nextProfile: AuthProfile) => {
    setProfileDraft({
      nickname: nextProfile.nickname ?? "",
      bio: nextProfile.bio ?? "",
      location: nextProfile.location ?? "",
      country: nextProfile.country ?? "",
      region: nextProfile.region ?? "",
      bannerUrl: nextProfile.bannerUrl ?? "",
      website: nextProfile.website ?? "",
      xUrl: nextProfile.xUrl ?? "",
      youtubeUrl: nextProfile.youtubeUrl ?? "",
      instagramUrl: nextProfile.instagramUrl ?? "",
    });
  };

  const displayName = (target?: AuthProfile | WebPost | null) => {
    if (!target) {
      return "Guest Player";
    }
    return target.nickname || target.displayName || target.username;
  };

  const mergePosts = (current: WebPost[], incoming: WebPost[]) => {
    const merged = new Map<string, WebPost>();
    [...incoming, ...current].forEach((post) => merged.set(post.id, post));
    return [...merged.values()].sort((a, b) => b.createdAt - a.createdAt).slice(0, 120);
  };

  const loadFeed = async (since = 0) => {
    try {
      const response = await fetch(`/api/v1/feed?since=${since}&limit=80`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        cache: "no-store",
      });
      const payload = (await response.json()) as AuthResponse;
      if (payload.posts) {
        setFeedPosts((current) => mergePosts(current, payload.posts ?? []));
      }
    } catch {
      // feed is optional while the service is unavailable
    }
  };

  useEffect(() => {
    document.title = "BetterSurvival WebSite";
    const previousOverflow = document.body.style.overflow;
    const previousUserSelect = document.body.style.userSelect;
    document.body.style.overflow = menuOpen ? "hidden" : "auto";
    document.body.style.userSelect = "text";
    return () => {
      document.body.style.overflow = previousOverflow;
      document.body.style.userSelect = previousUserSelect;
    };
  }, [menuOpen]);

  useEffect(() => {
    if (!token) {
      return;
    }
    const loadMe = async () => {
      try {
        const response = await fetch("/api/v1/auth/me", {
          headers: { Authorization: `Bearer ${token}` },
          cache: "no-store",
        });
        const payload = (await response.json()) as AuthResponse;
        if (payload.authenticated && payload.profile) {
          setProfile(payload.profile);
          syncProfileDraft(payload.profile);
        } else {
          saveToken("");
          setToken("");
        }
      } catch {
        // keep local form usable when server is unavailable
      }
    };
    void loadMe();
  }, [token]);

  useEffect(() => {
    void loadFeed();
  }, [token]);

  useEffect(() => {
    let cancelled = false;
    const poll = async () => {
      if (cancelled) {
        return;
      }
      const latest = feedPosts.reduce((max, post) => Math.max(max, post.createdAt), 0);
      try {
        const response = await fetch(`/api/v1/feed/updates?since=${latest}`, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
          cache: "no-store",
        });
        const payload = (await response.json()) as AuthResponse;
        if (!cancelled && payload.posts) {
          setFeedPosts((current) => mergePosts(current, payload.posts ?? []));
        }
      } catch {
        // retry on next interval
      }
    };
    const timer = window.setInterval(() => void poll(), 3500);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [feedPosts, token]);

  const applyAuth = (payload: AuthResponse) => {
    if (!payload.success || !payload.token || !payload.profile) {
      setMessage(payload.message ?? "認証に失敗しました");
      return;
    }
    saveToken(payload.token);
    setToken(payload.token);
    setProfile(payload.profile);
    syncProfileDraft(payload.profile);
    setMessage("ログインしました");
  };

  const submit = async () => {
    setBusy(true);
    setMessage("");
    try {
      if (mode === "register") {
        applyAuth(await postJson("/api/v1/auth/register", { code, email, password }));
      } else {
        applyAuth(await postJson("/api/v1/auth/login", { username, password }));
      }
    } catch {
      setMessage("サービスに接続できません。しばらくしてからもう一度お試しください。");
    } finally {
      setBusy(false);
    }
  };

  const submitProfile = async () => {
    if (!token) {
      setMessage("プロフィール編集にはログインが必要です");
      return;
    }
    setBusy(true);
    try {
      const payload = await postJson("/api/v1/profile/update", profileDraft, token);
      if (payload.success && payload.profile) {
        setProfile(payload.profile);
        syncProfileDraft(payload.profile);
        setMessage("プロフィールを保存しました");
      } else {
        setMessage(payload.message ?? "プロフィール保存に失敗しました");
      }
    } catch {
      setMessage("サービスに接続できません。しばらくしてからもう一度お試しください。");
    } finally {
      setBusy(false);
    }
  };

  const submitPost = async () => {
    if (!token) {
      setMessage("投稿にはログインが必要です");
      return;
    }
    if (!postText.trim() && attachments.length === 0) {
      setMessage("投稿内容を入力してください");
      return;
    }
    setBusy(true);
    try {
      const payload = await postJson("/api/v1/feed/post", { text: postText, attachments }, token);
      if (payload.success && payload.post) {
        setFeedPosts((current) => mergePosts(current, [payload.post as WebPost]));
        setPostText("");
        setAttachments([]);
        setMessage("投稿しました");
      } else {
        setMessage(payload.message ?? "投稿に失敗しました");
      }
    } catch {
      setMessage("サービスに接続できません。しばらくしてからもう一度お試しください。");
    } finally {
      setBusy(false);
    }
  };

  const resizeImage = (file: File) => new Promise<WebPostAttachment>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const image = new Image();
      image.onload = () => {
        const maxWidth = 1280;
        const maxHeight = 720;
        const scale = Math.min(1, maxWidth / image.width, maxHeight / image.height);
        const width = Math.max(1, Math.round(image.width * scale));
        const height = Math.max(1, Math.round(image.height * scale));
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;
        canvas.getContext("2d")?.drawImage(image, 0, 0, width, height);
        resolve({ type: "image", url: canvas.toDataURL("image/jpeg", 0.82), width, height });
      };
      image.onerror = reject;
      image.src = String(reader.result);
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });

  const onImagesSelected = async (files: FileList | null) => {
    if (!files) {
      return;
    }
    const selected = Array.from(files).filter((file) => file.type.startsWith("image/")).slice(0, 4 - attachments.length);
    const resized = await Promise.all(selected.map(resizeImage));
    setAttachments((current) => [...current, ...resized].slice(0, 4));
  };

  const logout = async () => {
    try {
      await postJson("/api/v1/auth/logout", {}, token);
    } catch {
      // local logout still succeeds
    }
    saveToken("");
    setToken("");
    setProfile(null);
    setMessage("ログアウトしました");
  };

  const closeMenu = () => setMenuOpen(false);

  return (
    <main className="website-shell">
      <div className="website-noise" />
      <header className="site-header">
        <nav className="topbar" aria-label="BetterSurvival navigation">
          <a className="brand" href="/" onClick={closeMenu}>
            <span className="brand-logo" aria-hidden="true">BS</span>
            <span className="brand-copy">
              <span className="brand-title">BetterSurvival</span>
              <span className="brand-subtitle">Official Portal</span>
            </span>
          </a>
          <button
            aria-controls="site-menu"
            aria-expanded={menuOpen}
            aria-label={menuOpen ? "メニューを閉じる" : "メニューを開く"}
            className={`menu-toggle ${menuOpen ? "is-open" : ""}`}
            onClick={() => setMenuOpen((open) => !open)}
            type="button"
          >
            <span />
            <span />
            <span />
          </button>
          <div id="site-menu" className={`topbar-links ${menuOpen ? "is-open" : ""}`}>
            <a className="nav-link" href="/" onClick={closeMenu}>Home</a>
            <a className="nav-link" href="/webmap/" onClick={closeMenu}>WebMap</a>
            <a className="nav-link" href="#account" onClick={closeMenu}>Account</a>
            <a className="nav-link" href="#feed" onClick={closeMenu}>Feed</a>
            <a className="nav-link" href="#features" onClick={closeMenu}>Features</a>
          </div>
        </nav>
      </header>

      <section className="site-main page-grid page-grid-home">
        <section className="hero-copy">
          <div className="hero-visual">
            <div className="hero-cover" aria-hidden="true">
              <span className="block block-grass" />
              <span className="block block-stone" />
              <span className="block block-diamond" />
              <span className="hero-glow" />
            </div>
            <div className="hero-overlay">
              <p className="eyebrow">Official Portal</p>
              <h1>BetterSurvival</h1>
              <p className="hero-subtitle">サーバーの最新情報、ワールドマップ、プロフィール、コミュニティ投稿をまとめて楽しめます。</p>
            </div>
          </div>
          <div className="hero-meta">
            <div className="hero-meta-item"><span>Profile</span><strong>安全なプロフィール連携</strong></div>
            <div className="hero-meta-item"><span>Timeline</span><strong>Minecraft ↔ Web 投稿連携</strong></div>
            <div className="hero-meta-item"><span>Profile</span><strong>壁紙・ニックネーム・URL編集</strong></div>
          </div>
          <div className="hero-actions">
            <a className="primary-button" href="#account">アカウントを作成</a>
            <a className="secondary-button" href="#feed">タイムラインを見る</a>
            <a className="secondary-button" href="/webmap/">WebMap を開く</a>
          </div>
        </section>

        <aside className="panel spotlight-panel">
          <p className="panel-label">Quick Access</p>
          <h2 className="spotlight-title">Web版 Minecraft Twitter</h2>
          <div className="spotlight-list">
            <div className="spotlight-item"><span>Profile</span><strong>ID固定 / ニックネーム表示</strong></div>
            <div className="spotlight-item"><span>Chat</span><strong>Minecraft chat が Web に流れる</strong></div>
            <div className="spotlight-item"><span>Media</span><strong>画像は 720p 程度に縮小</strong></div>
          </div>
        </aside>

        <section id="account" className="account-section">
          <div className="panel auth-card">
            <p className="panel-label">Account</p>
            <h2>{mode === "register" ? "Minecraft と Web を接続" : "プロフィールへログイン"}</h2>
            <p className="section-text">登録は Minecraft 内で発行したワンタイムコードだけでユーザーを判定します。名前の打ち間違いを防ぎ、より安全に連携できます。</p>
            <div className="auth-tabs">
              <button type="button" className={mode === "register" ? "active" : ""} onClick={() => setMode("register")}>登録</button>
              <button type="button" className={mode === "login" ? "active" : ""} onClick={() => setMode("login")}>ログイン</button>
            </div>
            {mode === "register" ? (
              <>
                <p className="auth-help">Minecraftで発行された確認コードを入力してください。</p>
                <label>ワンタイムコード<input value={code} onChange={(event) => setCode(event.target.value)} placeholder="6桁コード" inputMode="numeric" /></label>
                <label>Email 任意<input value={email} onChange={(event) => setEmail(event.target.value)} placeholder="mail@example.com" type="email" /></label>
              </>
            ) : (
              <label>Minecraft ユーザー名<input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="PlayerName" /></label>
            )}
            <label>パスワード<input value={password} onChange={(event) => setPassword(event.target.value)} placeholder="8文字以上" type="password" /></label>
            <button type="button" className="submit-button" disabled={busy} onClick={submit}>{busy ? "処理中..." : mode === "register" ? "登録する" : "ログイン"}</button>
            {message ? <p className="auth-message">{message}</p> : null}
          </div>

          <div className="profile-card">
            <div className="profile-banner" style={profile?.bannerUrl ? { backgroundImage: `url(${profile.bannerUrl})` } : undefined} />
            <div className="profile-body">
              <img className="profile-avatar" src={profile?.faceUrl || "/images/clear.png"} alt="" />
              <div className="profile-actions">
                {profile ? <button type="button" onClick={logout}>ログアウト</button> : <button type="button" onClick={() => setMode("register")}>登録</button>}
              </div>
              <p className="panel-label">Player Profile</p>
              <h2>{displayName(profile)}</h2>
              <p className="profile-handle">@{profile?.username ?? "minecraft"}</p>
              <p className="profile-bio">{profile?.bio ?? "登録すると Minecraft アイコン付きプロフィールが表示されます。"}</p>
              <div className="profile-meta">
                <span>Email: {profile?.email ? "登録済み" : "任意"}</span>
                <span>{profile?.country || "Country 未設定"}</span>
                <span>{profile?.region || "Region 未設定"}</span>
                <span>HOTP linked</span>
              </div>
              {profile ? (
                <div className="social-links">
                  {profile.website ? <a href={profile.website} target="_blank" rel="noreferrer">Website</a> : null}
                  {profile.xUrl ? <a href={profile.xUrl} target="_blank" rel="noreferrer">X</a> : null}
                  {profile.youtubeUrl ? <a href={profile.youtubeUrl} target="_blank" rel="noreferrer">YouTube</a> : null}
                  {profile.instagramUrl ? <a href={profile.instagramUrl} target="_blank" rel="noreferrer">Instagram</a> : null}
                </div>
              ) : null}
            </div>
          </div>
        </section>

        {profile ? (
          <section className="profile-edit-section panel">
            <p className="panel-label">Edit Profile</p>
            <h2>プロフィールをカスタム</h2>
            <p className="section-text">Minecraft ID とアイコンは固定です。ニックネーム、壁紙、所在地、URL、自己紹介だけ自由に編集できます。</p>
            <div className="profile-edit-grid">
              <label>ニックネーム<input value={profileDraft.nickname} onChange={(event) => setProfileDraft({ ...profileDraft, nickname: event.target.value })} placeholder="表示用ニックネーム" /></label>
              <label>国<input value={profileDraft.country} onChange={(event) => setProfileDraft({ ...profileDraft, country: event.target.value })} placeholder="Japan" /></label>
              <label>県 / 地域<input value={profileDraft.region} onChange={(event) => setProfileDraft({ ...profileDraft, region: event.target.value })} placeholder="Tokyo" /></label>
              <label>壁紙URL<input value={profileDraft.bannerUrl} onChange={(event) => setProfileDraft({ ...profileDraft, bannerUrl: event.target.value })} placeholder="https://..." /></label>
              <label>Website<input value={profileDraft.website} onChange={(event) => setProfileDraft({ ...profileDraft, website: event.target.value })} placeholder="https://..." /></label>
              <label>X<input value={profileDraft.xUrl} onChange={(event) => setProfileDraft({ ...profileDraft, xUrl: event.target.value })} placeholder="https://x.com/..." /></label>
              <label>YouTube<input value={profileDraft.youtubeUrl} onChange={(event) => setProfileDraft({ ...profileDraft, youtubeUrl: event.target.value })} placeholder="https://youtube.com/..." /></label>
              <label>Instagram<input value={profileDraft.instagramUrl} onChange={(event) => setProfileDraft({ ...profileDraft, instagramUrl: event.target.value })} placeholder="https://instagram.com/..." /></label>
            </div>
            <label className="wide-input">自己紹介<textarea value={profileDraft.bio} onChange={(event) => setProfileDraft({ ...profileDraft, bio: event.target.value })} placeholder="自己紹介" rows={4} /></label>
            <button type="button" className="submit-button" disabled={busy} onClick={submitProfile}>プロフィールを保存</button>
          </section>
        ) : null}

        <section id="feed" className="feed-section section-block">
          <div className="section-heading">
            <p className="eyebrow">Minecraft Social</p>
            <h2>Web版 Minecraft Twitter</h2>
          </div>
          <div className="feed-layout">
            <div className="panel compose-card">
              <p className="panel-label">Post</p>
              <textarea value={postText} onChange={(event) => setPostText(event.target.value.slice(0, 280))} placeholder="Web から Minecraft へ投稿..." rows={4} />
              <div className="compose-tools">
                <label className="image-picker">画像を追加<input type="file" accept="image/*" multiple onChange={(event) => void onImagesSelected(event.target.files)} /></label>
                <span>{postText.length}/280</span>
              </div>
              {attachments.length ? (
                <div className="attachment-preview-grid">
                  {attachments.map((attachment, index) => <img key={`${attachment.url}-${index}`} src={attachment.url} alt="" />)}
                </div>
              ) : null}
              <button type="button" className="submit-button" disabled={busy || !profile} onClick={submitPost}>投稿する</button>
            </div>
            <div className="timeline">
              {feedPosts.length === 0 ? <div className="panel empty-feed">まだ投稿はありません。Minecraft チャットか Web 投稿で流れます。</div> : null}
              {feedPosts.map((post) => (
                <article className="post-card" key={post.id}>
                  <img className="post-avatar" src={post.faceUrl || "/images/clear.png"} alt="" />
                  <div className="post-body">
                    <div className="post-head"><strong>{displayName(post)}</strong><span>@{post.username}</span><span>{post.source === "web" ? "Web" : "Minecraft"}</span></div>
                    <p>{post.text}</p>
                    {post.attachments?.length ? <div className="post-images">{post.attachments.map((attachment, index) => <img key={`${post.id}-${index}`} src={attachment.url} alt="" />)}</div> : null}
                  </div>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section id="features" className="section-block">
          <div className="section-heading">
            <p className="eyebrow">Guide</p>
            <h2>一般公開向けの UX に整理</h2>
          </div>
          <div className="feature-grid">
            <article className="feature-card"><p className="card-eyebrow">Profile</p><h3>プロフィールを利用</h3><p>Minecraftと連携したプロフィールを安全に利用できます。</p></article>
            <article className="feature-card"><p className="card-eyebrow">Timeline</p><h3>Minecraft ↔ Web 投稿</h3><p>Minecraft のチャットは Web のタイムラインへ、Web の投稿は Minecraft へ流れます。</p></article>
            <article className="feature-card"><p className="card-eyebrow">Media</p><h3>画像は軽量化</h3><p>Web 画像は投稿前にブラウザで 720p 程度へリサイズされます。</p></article>
          </div>
        </section>
      </section>
    </main>
  );
}
