import type { AuthProfile, AuthResponse, ProfileDraft, WebPost, WebPostAttachment } from './types';
import users from '../../../debug/users.json';
import posts from '../../../debug/posts.json';
import { maybeShowKnownApiError, showApiError } from './errorBus';

export const MOCK_MODE = import.meta.env.VITE_USE_MOCK_DATA === 'true';

const mockUsers = users as AuthProfile[];
const mockPosts = posts as WebPost[];

export const TOKEN_KEY = 'bettersurvival.website.token';
export const CSRF_KEY = 'bettersurvival.website.csrf';

export function storedToken() {
  try {
    return window.localStorage.getItem(TOKEN_KEY) ?? '';
  } catch {
    return '';
  }
}

export function storedCsrfToken() {
  try {
    return window.localStorage.getItem(CSRF_KEY) ?? '';
  } catch {
    return '';
  }
}

export function saveToken(token: string) {
  try {
    if (token) window.localStorage.setItem(TOKEN_KEY, token);
    else window.localStorage.removeItem(TOKEN_KEY);
  } catch {
    // ignore private storage failures
  }
}

export function saveCsrfToken(token: string) {
  try {
    if (token) window.localStorage.setItem(CSRF_KEY, token);
    else window.localStorage.removeItem(CSRF_KEY);
  } catch {
    // ignore private storage failures
  }
}

export async function postJson(path: string, body: Record<string, unknown>, token = '') {
  if (MOCK_MODE) {
    const profile = mockUsers[0];

    if (path === '/api/v1/auth/login' || path === '/api/v1/auth/register') {
      return {
        success: true,
        authenticated: true,
        message: 'モックログインしました',
        token: 'mock-token',
        csrfToken: 'mock-csrf',
        profile,
      } as AuthResponse;
    }

    if (path === '/api/v1/auth/logout') {
      return { success: true, message: 'モックログアウトしました' } as AuthResponse;
    }

    if (path === '/api/v1/feed/post') {
      return {
        success: true,
        message: 'モック投稿しました',
        post: {
          id: `mock-post-${Date.now()}`,
          uuid: profile.uuid,
          username: profile.username,
          displayName: profile.displayName,
          nickname: profile.nickname,
          faceUrl: profile.faceUrl,
          source: 'web',
          text: String(body.text ?? ''),
          attachments: (body.attachments as WebPostAttachment[] | undefined) ?? [],
          createdAt: Date.now(),
          likes: 0,
          replies: 0,
          reposts: 0,
          likedByMe: false,
          repostedByMe: false,
        },
      } as AuthResponse;
    }

    if (path === '/api/v1/feed/like' || path === '/api/v1/feed/repost') {
      const postId = String(body.postId ?? '');
      const post = mockPosts.find((item) => item.id === postId);
      if (!post) return { success: false, message: '投稿が見つかりません' } as AuthResponse;

      return {
        success: true,
        post: path === '/api/v1/feed/like'
          ? {
              ...post,
              likedByMe: !post.likedByMe,
              likes: (post.likes ?? 0) + (post.likedByMe ? -1 : 1),
            }
          : {
              ...post,
              repostedByMe: !post.repostedByMe,
              reposts: (post.reposts ?? 0) + (post.repostedByMe ? -1 : 1),
            },
      } as AuthResponse;
    }

    return { success: false, message: '未対応のモックPOSTです' } as AuthResponse;
  }

  const csrfToken = token ? storedCsrfToken() : '';
  let response: Response;
  try {
    response = await fetch(path, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(csrfToken ? { 'X-BetterSurvival-CSRF': csrfToken } : {}),
      },
      body: JSON.stringify(body),
    });
  } catch (networkError) {
    // ブラウザレベルで CORS プリフライトが弾かれた等、fetch 自体が失敗したケース。
    showApiError({
      title: 'サーバーに接続できません',
      severity: 'error',
      message:
        'サーバーへ接続できませんでした。\n通信環境を確認して、少し時間をおいてからもう一度お試しください。',
      detail: networkError instanceof Error ? networkError.message : String(networkError),
    });
    return { success: false, message: 'ネットワークエラー' } as AuthResponse;
  }

  let payload: AuthResponse;
  try {
    payload = (await response.json()) as AuthResponse;
  } catch {
    // JSON でない応答 (プロキシの HTML エラーページ等)
    if (!response.ok) {
      showApiError({
        title: 'サーバーエラー',
        severity: 'error',
        message: '予期しないサーバー応答を受け取りました。時間をおいてから再度お試しください。',
        status: response.status,
      });
    }
    return { success: false, message: 'サーバー応答を解析できませんでした' } as AuthResponse;
  }

  // 既知のセキュリティ系エラー (CORS / CSRF / rate-limit …) はグローバルモーダルで通知
  maybeShowKnownApiError(payload as { success?: boolean; message?: string }, response.status);
  return payload;
}

export function mockFeedResponse(since = 0): AuthResponse {
  return {
    success: true,
    posts: mockPosts.filter((post) => post.createdAt > since),
  };
}

export function profileToDraft(profile: Partial<ProfileDraft> | null | undefined): ProfileDraft {
  return {
    nickname: profile?.nickname ?? '',
    bio: profile?.bio ?? '',
    location: profile?.location ?? '',
    country: profile?.country ?? '',
    region: profile?.region ?? '',
    bannerUrl: profile?.bannerUrl ?? '',
    website: profile?.website ?? '',
    xUrl: profile?.xUrl ?? '',
    youtubeUrl: profile?.youtubeUrl ?? '',
    instagramUrl: profile?.instagramUrl ?? '',
  };
}

export function mergeAttachments(current: WebPostAttachment[], next: WebPostAttachment[]) {
  return [...current, ...next].slice(0, 4);
}
