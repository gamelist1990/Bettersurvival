import { useEffect, useState } from 'react';
import { MOCK_MODE, mockFeedResponse, postJson, saveCsrfToken, saveToken, storedToken } from './api';
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
    saveCsrfToken(payload.csrfToken ?? "");
    setToken(payload.token);
    setProfile(payload.profile);
    setMessage('ログインしました');
    return true;
  };

  const register = async (code: string, password: string) => {
    setBusy(true);
    setMessage('');
    try {
      return applyAuth(await postJson('/api/v1/auth/register', { code, password }));
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
      if (MOCK_MODE) {
        const payload = mockFeedResponse(since);
        if (payload.posts) setFeedPosts((current) => mergePosts(current, payload.posts ?? []));
        return;
      }

      const response = await fetch(`/api/v1/feed?since=${since}&limit=80`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        credentials: 'include',
        cache: 'no-store',
      });
      const payload = (await response.json()) as AuthResponse;
      if (payload.posts) setFeedPosts((current) => mergePosts(current, payload.posts ?? []));
    } catch {
      // feed is optional while the service is unavailable
    }
  };

  const likePost = async (postId: string) => {
    if (!token) return false;
    try {
      const payload = await postJson('/api/v1/feed/like', { postId }, token);
      if (payload.success && payload.post) {
        setFeedPosts((current) => mergePosts(current.filter((post) => post.id !== payload.post!.id), [payload.post as WebPost]));
        return true;
      }
      return false;
    } catch {
      return false;
    }
  };

  const repostPost = async (postId: string) => {
    if (!token) return false;
    try {
      const payload = await postJson('/api/v1/feed/repost', { postId }, token);
      if (payload.success && payload.post) {
        setFeedPosts((current) => mergePosts(current.filter((post) => post.id !== payload.post!.id), [payload.post as WebPost]));
        return true;
      }
      return false;
    } catch {
      return false;
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
    const loadMe = async () => {
      try {
        if (MOCK_MODE) return;

        const response = await fetch('/api/v1/auth/me', {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
          credentials: 'include',
          cache: 'no-store',
        });
        const payload = (await response.json()) as AuthResponse;
        if (payload.authenticated && payload.profile) {
          setProfile(payload.profile);
          saveCsrfToken(payload.csrfToken ?? "");
          if (payload.token && payload.token !== token) {
            saveToken(payload.token);
            setToken(payload.token);
          }
        } else if (token) {
          saveToken('');
          setToken('');
        }
      } catch {
        // keep local UI usable
      }
    };
    void loadMe();
    // runs once on mount: the session cookie (if any) restores login even without a stored token
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  return { token, profile, feedPosts, message, busy, setMessage, register, login, logout, updateProfile, postFeed, likePost, repostPost, loadFeed };
}

