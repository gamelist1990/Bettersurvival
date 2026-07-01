import { displayName } from '../webservice/useWebService';
import type { AuthProfile } from '../webservice/types';

type ProfileCardProps = {
  profile: AuthProfile | null;
  onLogout: () => Promise<void>;
};

export function ProfileCard({ profile, onLogout }: ProfileCardProps) {
  return (
    <section className="profile-card">
      <div className="profile-banner" style={profile?.bannerUrl ? { backgroundImage: `url(${profile.bannerUrl})` } : undefined} />
      <div className="profile-body">
        <img className="profile-avatar" src={profile?.faceUrl || '/images/clear.png'} alt="" />
        <div className="profile-actions">
          {profile ? <button type="button" onClick={onLogout}>ログアウト</button> : <a href="/account">登録</a>}
        </div>
        <p className="panel-label">Player Profile</p>
        <h2>{displayName(profile)}</h2>
        <p className="profile-handle">@{profile?.username ?? 'minecraft'}</p>
        <p className="profile-bio">{profile?.bio ?? '登録すると Minecraft アイコン付きプロフィールが表示されます。'}</p>
        <div className="profile-meta">
          <span>{profile?.country || 'Country 未設定'}</span>
          <span>{profile?.region || 'Region 未設定'}</span>
          <span>Email: {profile?.email ? '登録済み' : '任意'}</span>
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
    </section>
  );
}
