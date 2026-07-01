export type AuthProfile = {
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

export type WebPostAttachment = {
  type: string;
  url: string;
  width: number;
  height: number;
};

export type WebPost = {
  id: string;
  uuid: string;
  username: string;
  displayName: string;
  nickname: string;
  faceUrl: string;
  source: 'minecraft' | 'web' | string;
  text: string;
  attachments: WebPostAttachment[];
  createdAt: number;
};

export type ProfileDraft = {
  nickname: string;
  bio: string;
  location: string;
  country: string;
  region: string;
  bannerUrl: string;
  website: string;
  xUrl: string;
  youtubeUrl: string;
  instagramUrl: string;
};

export type AuthResponse = {
  success?: boolean;
  message?: string;
  token?: string;
  profile?: AuthProfile;
  authenticated?: boolean;
  posts?: WebPost[];
  post?: WebPost;
  updatedAt?: number;
};
