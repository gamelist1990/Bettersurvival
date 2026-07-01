import type { ProfileDraft } from '../webservice/types';

type ProfileEditorProps = {
  draft: ProfileDraft;
  busy: boolean;
  onChange: (draft: ProfileDraft) => void;
  onSave: () => Promise<void>;
};

export function ProfileEditor({ draft, busy, onChange, onSave }: ProfileEditorProps) {
  const update = (key: keyof ProfileDraft, value: string) => onChange({ ...draft, [key]: value });

  return (
    <section className="panel profile-editor">
      <p className="panel-label">Edit Profile</p>
      <h2>プロフィールをカスタム</h2>
      <p className="section-text">Minecraft ID とアイコンは固定です。ニックネーム、壁紙、所在地、URL、自己紹介だけ自由に編集できます。</p>
      <div className="profile-edit-grid">
        <label>ニックネーム<input value={draft.nickname} onChange={(event) => update('nickname', event.target.value)} /></label>
        <label>国<input value={draft.country} onChange={(event) => update('country', event.target.value)} /></label>
        <label>県 / 地域<input value={draft.region} onChange={(event) => update('region', event.target.value)} /></label>
        <label>壁紙URL<input value={draft.bannerUrl} onChange={(event) => update('bannerUrl', event.target.value)} /></label>
        <label>Website<input value={draft.website} onChange={(event) => update('website', event.target.value)} /></label>
        <label>X<input value={draft.xUrl} onChange={(event) => update('xUrl', event.target.value)} /></label>
        <label>YouTube<input value={draft.youtubeUrl} onChange={(event) => update('youtubeUrl', event.target.value)} /></label>
        <label>Instagram<input value={draft.instagramUrl} onChange={(event) => update('instagramUrl', event.target.value)} /></label>
      </div>
      <label className="wide-input">自己紹介<textarea value={draft.bio} onChange={(event) => update('bio', event.target.value)} rows={4} /></label>
      <button className="primary-button full-button" disabled={busy} onClick={onSave} type="button">プロフィールを保存</button>
    </section>
  );
}
