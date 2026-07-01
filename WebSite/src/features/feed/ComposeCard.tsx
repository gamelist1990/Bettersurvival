import { useEffect, useId, useRef, useState, type CSSProperties } from 'react';
import { resizeImage } from './imageResize';
import { IconImagePlus, IconClose } from '../../components/common/Icons';
import type { AuthProfile, WebPostAttachment } from '../webservice/types';

type ComposeCardProps = {
  busy: boolean;
  disabled: boolean;
  profile: AuthProfile;
  compact?: boolean;
  submitLabel?: string;
  placeholder?: string;
  onCancel?: () => void;
  onPost: (text: string, attachments: WebPostAttachment[]) => Promise<boolean>;
};

export function ComposeCard({
  busy,
  disabled,
  profile,
  compact = false,
  submitLabel = '投稿',
  placeholder = 'いまどうしてる？',
  onCancel,
  onPost,
}: ComposeCardProps) {
  const inputId = useId();
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [text, setText] = useState('');
  const [attachments, setAttachments] = useState<WebPostAttachment[]>([]);
  const remaining = 280 - text.length;
  const canSubmit = !busy && !disabled && remaining >= 0 && (!!text.trim() || attachments.length > 0);
  const ringPct = Math.min(100, (text.length / 280) * 100);

  const autoGrow = (element: HTMLTextAreaElement | null) => {
    if (!element) return;
    element.style.height = 'auto';
    element.style.height = `${element.scrollHeight}px`;
  };

  useEffect(() => autoGrow(textareaRef.current), [text]);

  const onImagesSelected = async (files: FileList | null) => {
    if (!files) return;
    const selected = Array.from(files).filter((file) => file.type.startsWith('image/')).slice(0, 4 - attachments.length);
    const resized = await Promise.all(selected.map(resizeImage));
    setAttachments((current) => [...current, ...resized].slice(0, 4));
  };

  const submit = async () => {
    if (!canSubmit) return;
    if (await onPost(text.trim(), attachments)) {
      setText('');
      setAttachments([]);
    }
  };

  return (
    <section className={`compose-card x-scope${compact ? ' compose-card-compact' : ''}`}>
      <div className="compose-row">
        <img className="compose-avatar" src={profile.faceUrl || '/images/clear.png'} alt="" />
        <div className="compose-main">
          <textarea
            ref={textareaRef}
            value={text}
            onChange={(event) => setText(event.target.value.slice(0, 280))}
            onKeyDown={(event) => {
              if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
                event.preventDefault();
                void submit();
              }
            }}
            placeholder={placeholder}
            rows={1}
          />
          {attachments.length ? (
            <div className="attachment-preview-grid">
              {attachments.map((attachment, index) => (
                <figure key={`${attachment.url}-${index}`}>
                  <img src={attachment.url} alt="" />
                  <button type="button" aria-label="画像を削除" onClick={() => setAttachments((current) => current.filter((_, itemIndex) => itemIndex !== index))}><IconClose size={14} /></button>
                </figure>
              ))}
            </div>
          ) : null}
          <div className="compose-toolbar">
            <div className="compose-tools-left">
              <label className="x-icon-btn compose-image-btn" htmlFor={inputId}>
                <IconImagePlus size={19} />
                <input id={inputId} type="file" accept="image/*" multiple onChange={(event) => void onImagesSelected(event.target.files)} />
              </label>
            </div>
            <div className="compose-tools-right">
              {text.length > 0 ? (
                <div
                  className={remaining < 20 ? 'compose-ring is-warning' : 'compose-ring'}
                  style={{ '--ring-pct': `${ringPct}%` } as CSSProperties}
                >
                  {remaining < 20 ? <span>{remaining}</span> : null}
                </div>
              ) : null}
              <span className="compose-divider" aria-hidden="true" />
              {onCancel ? <button className="x-pill" type="button" onClick={onCancel}>キャンセル</button> : null}
              <button className="x-pill x-pill-accent compose-submit" disabled={!canSubmit} onClick={submit} type="button">{submitLabel}</button>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
