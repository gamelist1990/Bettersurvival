import { createPortal } from 'react-dom';
import { IconClose } from '../../components/common/Icons';
import type { ProfileDraft } from '../webservice/types';

type ProfileEditorProps = {
  draft: ProfileDraft;
  busy: boolean;
  faceUrl: string;
  onChange: (draft: ProfileDraft) => void;
  onSave: () => Promise<void>;
  onClose: () => void;
};

export function ProfileEditor({ draft, busy, faceUrl, onChange, onSave, onClose }: ProfileEditorProps) {
  const update = (key: keyof ProfileDraft, value: string) => onChange({ ...draft, [key]: value });

  return createPortal(
    <div className="edit-profile-overlay x-scope" onClick={onClose}>
      <div className="edit-profile-modal" role="dialog" aria-modal="true" aria-label="プロフィールを編集" onClick={(event) => event.stopPropagation()}>
        <header className="edit-profile-head">
          <button className="x-icon-btn" type="button" aria-label="閉じる" onClick={onClose}><IconClose size={18} /></button>
          <h2>プロフィールを編集</h2>
          <button className="x-pill x-pill-solid" disabled={busy} onClick={onSave} type="button">保存</button>
        </header>
        <div className="edit-profile-body">
          <div className="edit-profile-banner" style={draft.bannerUrl ? { backgroundImage: `url(${draft.bannerUrl})` } : undefined}>
            <img className="edit-profile-avatar" src={faceUrl || '/images/clear.png'} alt="" />
          </div>
          <div className="edit-profile-fields">
            <label>ニックネーム<input value={draft.nickname} onChange={(event) => update('nickname', event.target.value)} maxLength={32} /></label>
            <label>自己紹介<textarea value={draft.bio} onChange={(event) => update('bio', event.target.value.slice(0, 160))} rows={3} /><span className="edit-profile-count">{draft.bio.length}/160</span></label>
            <label>所在地<input value={draft.location} onChange={(event) => update('location', event.target.value)} maxLength={80} /></label>
            <label>国<input value={draft.country} onChange={(event) => update('country', event.target.value)} maxLength={40} /></label>
            <label>県 / 地域<input value={draft.region} onChange={(event) => update('region', event.target.value)} maxLength={60} /></label>
            <label>壁紙URL<input value={draft.bannerUrl} onChange={(event) => update('bannerUrl', event.target.value)} placeholder="https://..." /></label>
            <label>Website<input value={draft.website} onChange={(event) => update('website', event.target.value)} placeholder="https://..." /></label>
            <label>X<input value={draft.xUrl} onChange={(event) => update('xUrl', event.target.value)} placeholder="https://x.com/..." /></label>
            <label>YouTube<input value={draft.youtubeUrl} onChange={(event) => update('youtubeUrl', event.target.value)} placeholder="https://youtube.com/..." /></label>
            <label>Instagram<input value={draft.instagramUrl} onChange={(event) => update('instagramUrl', event.target.value)} placeholder="https://instagram.com/..." /></label>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  );
}
