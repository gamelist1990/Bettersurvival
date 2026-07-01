import type { AuthResponse, ProfileDraft, WebPostAttachment } from './types';

export const TOKEN_KEY = 'bettersurvival.website.token';

export function storedToken() {
  try {
    return window.localStorage.getItem(TOKEN_KEY) ?? '';
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

export async function postJson(path: string, body: Record<string, unknown>, token = '') {
  const response = await fetch(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });
  return (await response.json()) as AuthResponse;
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
