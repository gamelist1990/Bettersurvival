import { useEffect, useState } from 'react';
import { postJson, saveToken, storedToken } from './api';
import type { AuthProfile, AuthResponse, ProfileDraft, WebPost, WebPostAttachment } from './types';

function mergePosts(current: WebPost[], incoming: WebPost[]) {
  const merged = new Map<string, WebPost>();
  [...incoming, ...current].forEach((post) => merged.set(post.id, { ...post, attachments: post.attachments ?? [] }));
  return [...merged.values()].sort((a, b) => b.createdAt - a.createdAt).slice(0, 120);
}

export function displayName(target?: AuthProfile | WebPost | null) {
  if (!target) return 'Guest Player';
  return target.nickname || target.displayName || target.username;
}

export function useWebService() {
  const [token, setToken] = useState(storedToken());
  const [profile, setProfile] = useState<AuthProfile | null>(null);
  const [feedPosts, setFeedPosts] = useState<WebPost[]>([]);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  const applyAuth = (payload: AuthResponse) => {
    if (!payload.success || !payload.token || !payload.profile) {
      setMessage(payload.message ?? '認証に失敗しました');
      return false;
    }
    saveToken(payload.token);
    setToken(payload.token);
    setProfile(payload.profile);
    setMessage('ログインしました');
    return true;
  };

  const register = async (code: string, email: string, password: string) => {
    setBusy(true);
    setMessage('');
    try {
      return applyAuth(await postJson('/api/v1/auth/register', { code, email, password }));
    } catch {
      setMessage('サービスに接続できません。しばらくしてからもう一度お試しください。');
      return false;
    } finally {
      setBusy(false);
    }
  };

  const login = async (username: string, password: string) => {
    setBusy(true);
    setMessage('');
    try {
      return applyAuth(await postJson('/api/v1/auth/login', { username, password }));
    } catch {
      setMessage('サービスに接続できません。しばらくしてからもう一度お試しください。');
      return false;
    } finally {
      setBusy(false);
    }
  };

  const logout = async () => {
    try {
      await postJson('/api/v1/auth/logout', {}, token);
    } catch {
      // local logout still succeeds
    }
    saveToken('');
    setToken('');
    setProfile(null);
    setMessage('ログアウトしました');
  };

  const updateProfile = async (draft: ProfileDraft) => {
    if (!token) {
      setMessage('プロフィール編集にはログインが必要です');
      return false;
    }
    setBusy(true);
    try {
      const payload = await postJson('/api/v1/profile/update', draft, token);
      if (payload.success && payload.profile) {
        setProfile(payload.profile);
        setMessage('プロフィールを保存しました');
        return true;
      }
      setMessage(payload.message ?? 'プロフィール保存に失敗しました');
      return false;
    } catch {
      setMessage('サービスに接続できません。しばらくしてからもう一度お試しください。');
      return false;
    } finally {
      setBusy(false);
    }
  };

  const loadFeed = async (since = 0) => {
    try {
      const response = await fetch(`/api/v1/feed?since=${since}&limit=80`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        cache: 'no-store',
      });
      const payload = (await response.json()) as AuthResponse;
      if (payload.posts) setFeedPosts((current) => mergePosts(current, payload.posts ?? []));
    } catch {
      // feed is optional while the service is unavailable
    }
  };

  const postFeed = async (text: string, attachments: WebPostAttachment[]) => {
    if (!token) {
      setMessage('投稿にはログインが必要です');
      return false;
    }
    if (!text.trim() && attachments.length === 0) {
      setMessage('投稿内容を入力してください');
      return false;
    }
    setBusy(true);
    try {
      const payload = await postJson('/api/v1/feed/post', { text, attachments }, token);
      if (payload.success && payload.post) {
        setFeedPosts((current) => mergePosts(current, [payload.post as WebPost]));
        setMessage('投稿しました');
        return true;
      }
      setMessage(payload.message ?? '投稿に失敗しました');
      return false;
    } catch {
      setMessage('サービスに接続できません。しばらくしてからもう一度お試しください。');
      return false;
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    if (!token) return;
    const loadMe = async () => {
      try {
        const response = await fetch('/api/v1/auth/me', {
          headers: { Authorization: `Bearer ${token}` },
          cache: 'no-store',
        });
        const payload = (await response.json()) as AuthResponse;
        if (payload.authenticated && payload.profile) setProfile(payload.profile);
        else {
          saveToken('');
          setToken('');
        }
      } catch {
        // keep local UI usable
      }
    };
    void loadMe();
  }, [token]);

  useEffect(() => {
    void loadFeed();
  }, [token]);

  useEffect(() => {
    let cancelled = false;
    const timer = window.setInterval(() => {
      if (cancelled) return;
      const latest = feedPosts.reduce((max, post) => Math.max(max, post.createdAt), 0);
      void loadFeed(latest);
    }, 3500);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [feedPosts, token]);

  return { token, profile, feedPosts, message, busy, setMessage, register, login, logout, updateProfile, postFeed, loadFeed };
}
