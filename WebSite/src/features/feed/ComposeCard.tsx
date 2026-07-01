import { useState } from 'react';
import { resizeImage } from './imageResize';
import type { AuthProfile, WebPostAttachment } from '../webservice/types';

type ComposeCardProps = {
  busy: boolean;
  disabled: boolean;
  profile: AuthProfile;
  onPost: (text: string, attachments: WebPostAttachment[]) => Promise<boolean>;
};

export function ComposeCard({ busy, disabled, profile, onPost }: ComposeCardProps) {
  const [text, setText] = useState('');
  const [attachments, setAttachments] = useState<WebPostAttachment[]>([]);

  const onImagesSelected = async (files: FileList | null) => {
    if (!files) return;
    const selected = Array.from(files).filter((file) => file.type.startsWith('image/')).slice(0, 4 - attachments.length);
    const resized = await Promise.all(selected.map(resizeImage));
    setAttachments((current) => [...current, ...resized].slice(0, 4));
  };

  const submit = async () => {
    if (await onPost(text, attachments)) {
      setText('');
      setAttachments([]);
    }
  };

  return (
    <section className="panel compose-card">
      <div className="compose-row">
        <img className="compose-avatar" src={profile.faceUrl || '/images/clear.png'} alt="" />
        <div className="compose-main">
          <textarea value={text} onChange={(event) => setText(event.target.value.slice(0, 280))} placeholder="いまどうしてる？" rows={3} />
          {attachments.length ? <div className="attachment-preview-grid">{attachments.map((attachment, index) => <img key={`${attachment.url}-${index}`} src={attachment.url} alt="" />)}</div> : null}
          <div className="compose-tools">
            <label className="image-picker">画像<input type="file" accept="image/*" multiple onChange={(event) => void onImagesSelected(event.target.files)} /></label>
            <span>{text.length}/280</span>
            <button className="primary-button" disabled={busy || disabled || (!text.trim() && attachments.length === 0)} onClick={submit} type="button">投稿</button>
          </div>
        </div>
      </div>
    </section>
  );
}
