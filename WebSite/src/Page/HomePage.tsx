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
  passkeyEnabled: boolean;
  createdAt: number;
  updatedAt: number;
};

type AuthResponse = {
  success?: boolean;
  message?: string;
  token?: string;
  profile?: AuthProfile;
  authenticated?: boolean;
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
  const [passkeyRequested, setPasskeyRequested] = useState(true);
  const [token, setToken] = useState(storedToken());
  const [profile, setProfile] = useState<AuthProfile | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    document.title = "BetterSurvival WebSite";
    const previousOverflow = document.body.style.overflow;
    const previousUserSelect = document.body.style.userSelect;
    document.body.style.overflow = "auto";
    document.body.style.userSelect = "text";
    return () => {
      document.body.style.overflow = previousOverflow;
      document.body.style.userSelect = previousUserSelect;
    };
  }, []);

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

  const applyAuth = (payload: AuthResponse) => {
    if (!payload.success || !payload.token || !payload.profile) {
      setMessage(payload.message ?? "認証に失敗しました");
      return;
    }
    saveToken(payload.token);
    setToken(payload.token);
    setProfile(payload.profile);
    setMessage("ログインしました");
  };

  const submit = async () => {
    setBusy(true);
    setMessage("");
    try {
      if (mode === "register") {
        applyAuth(await postJson("/api/v1/auth/register", {
          username,
          code,
          email,
          password,
          passkeyRequested,
        }));
      } else {
        applyAuth(await postJson("/api/v1/auth/login", { username, password }));
      }
    } catch {
      setMessage("WebService に接続できません");
    } finally {
      setBusy(false);
    }
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

  return (
    <main className="website-shell">
      <section className="website-hero">
        <div>
          <p className="website-kicker">BetterSurvival WebSite</p>
          <h1>サーバーのホームページとプロフィールをひとつに。</h1>
          <p>
            Minecraft 内の <strong>/hotp</strong> で発行したワンタイムコードを使って登録できます。
            WebMap は <strong>/webmap/</strong> からアクセスします。
          </p>
          <div className="website-actions">
            <a className="website-primary" href="/webmap/">WebMap を開く</a>
            <a className="website-secondary" href="#account">アカウント設定</a>
          </div>
        </div>
        <div className="website-card website-status-card">
          <span className="website-live-dot" />
          <h2>WebService</h2>
          <p>ホーム、登録、ログイン、Twitter-like プロフィール UI を提供します。</p>
        </div>
      </section>

      <section id="account" className="website-grid">
        <div className="website-card auth-card">
          <div className="auth-tabs">
            <button type="button" className={mode === "register" ? "active" : ""} onClick={() => setMode("register")}>登録</button>
            <button type="button" className={mode === "login" ? "active" : ""} onClick={() => setMode("login")}>ログイン</button>
          </div>
          <label>
            Minecraft ユーザー名
            <input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="PlayerName" />
          </label>
          {mode === "register" ? (
            <>
              <label>
                ワンタイムコード
                <input value={code} onChange={(event) => setCode(event.target.value)} placeholder="/hotp で表示された6桁" inputMode="numeric" />
              </label>
              <label>
                Email 任意
                <input value={email} onChange={(event) => setEmail(event.target.value)} placeholder="mail@example.com" type="email" />
              </label>
              <label className="auth-check">
                <input type="checkbox" checked={passkeyRequested} onChange={(event) => setPasskeyRequested(event.target.checked)} />
                OAuth / パスキー対応を有効化する
              </label>
            </>
          ) : null}
          <label>
            パスワード
            <input value={password} onChange={(event) => setPassword(event.target.value)} placeholder="8文字以上" type="password" />
          </label>
          <button type="button" className="submit-button" disabled={busy} onClick={submit}>
            {busy ? "処理中..." : mode === "register" ? "登録する" : "ログイン"}
          </button>
          {message ? <p className="auth-message">{message}</p> : null}
        </div>

        <div className="profile-card">
          <div className="profile-banner" />
          <div className="profile-body">
            <img className="profile-avatar" src={profile?.faceUrl || "/images/clear.png"} alt="" />
            <div className="profile-actions">
              {profile ? <button type="button" onClick={logout}>ログアウト</button> : <button type="button" onClick={() => setMode("register")}>登録</button>}
            </div>
            <h2>{profile?.displayName ?? "Guest Player"}</h2>
            <p className="profile-handle">@{profile?.username ?? "minecraft"}</p>
            <p className="profile-bio">{profile?.bio ?? "登録すると Minecraft アイコン付きプロフィールが表示されます。"}</p>
            <div className="profile-meta">
              <span>Passkey: {profile?.passkeyEnabled ? "ON" : "OFF"}</span>
              <span>Email: {profile?.email ? "登録済み" : "任意"}</span>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}

