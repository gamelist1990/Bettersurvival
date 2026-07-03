import { useCallback, useEffect, useState } from 'react';
import { postJson, storedToken } from '../features/webservice/api';
import type { AuthProfile } from '../features/webservice/types';

type PrivacyRequestItem = {
  id: string;
  type: string;
  typeLabel: string;
  detail: string;
  status: 'open' | 'resolved' | string;
  createdAt: number;
  resolvedAt: number;
};

type RequestPageProps = {
  profile: AuthProfile | null;
  onNavigate: (href: string) => void;
};

const REQUEST_TYPES = [
  { value: 'disclosure', label: '開示', description: '保存されている自分の情報を確認したい' },
  { value: 'correction', label: '訂正', description: '登録されている情報を修正してほしい' },
  { value: 'deletion', label: '削除', description: '投稿やアカウント情報を削除してほしい' },
  { value: 'suspension', label: '利用停止', description: '自分の情報の利用を停止してほしい' },
] as const;

function dateLabel(value: number) {
  if (!value) return '';
  return new Date(value).toLocaleString('ja-JP', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

/**
 * 個人情報の開示・訂正・削除・利用停止の申請ページ。
 * ログイン (Minecraft アカウント連携) を本人確認として利用する。
 */
export function RequestPage({ profile, onNavigate }: RequestPageProps) {
  const [type, setType] = useState<string>('disclosure');
  const [detail, setDetail] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [requests, setRequests] = useState<PrivacyRequestItem[]>([]);

  const loadRequests = useCallback(async () => {
    const token = storedToken();
    if (!token) return;
    try {
      const response = await fetch('/api/v1/privacy/requests', {
        headers: { Authorization: `Bearer ${token}` },
        credentials: 'include',
        cache: 'no-store',
      });
      const payload = await response.json();
      if (payload.success && Array.isArray(payload.requests)) {
        setRequests(payload.requests as PrivacyRequestItem[]);
      }
    } catch {
      // 履歴の取得失敗は致命的ではない
    }
  }, []);

  useEffect(() => {
    if (profile) void loadRequests();
  }, [profile, loadRequests]);

  const submit = async () => {
    if (busy) return;
    if (detail.trim().length < 5) {
      setMessage('申請内容を5文字以上で入力してください');
      return;
    }
    setBusy(true);
    setMessage('');
    try {
      const payload = await postJson('/api/v1/privacy/request', { type, detail: detail.trim() }, storedToken());
      if (payload.success) {
        setDetail('');
        setMessage('申請を受け付けました。原則14日以内に対応します。');
        void loadRequests();
      } else {
        setMessage(payload.message ?? '申請に失敗しました');
      }
    } catch {
      setMessage('サービスに接続できません。しばらくしてからもう一度お試しください。');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="legal-page">
      <header className="legal-hero section-block">
        <p className="eyebrow">Privacy Request</p>
        <h1>開示・削除などの申請</h1>
        <p className="section-text">
          ご自身の個人情報についての開示・訂正・削除・利用停止を申請できます。
          なりすまし防止のため、Minecraft アカウント連携によるログインを本人確認としています。
          詳しくは<button className="legal-inline-link" type="button" onClick={() => onNavigate('/privacy')}>プライバシーポリシー</button>をご覧ください。
        </p>
      </header>

      {!profile ? (
        <section className="legal-section section-block request-login-gate">
          <h2>ログインが必要です</h2>
          <p className="section-text">
            本人確認のため、申請にはログインが必要です。アカウントをお持ちでない場合は、
            Minecraft サーバー内で <code>/hotp</code> を実行して表示されるコードで登録できます。
          </p>
          <button className="primary-button" type="button" onClick={() => onNavigate('/profile')}>ログインページへ</button>
        </section>
      ) : (
        <>
          <section className="legal-section section-block">
            <h2>申請フォーム</h2>
            <p className="request-form-user">申請者: <strong>{profile.username}</strong> (Minecraft アカウント連携済み)</p>
            <div className="request-type-grid" role="radiogroup" aria-label="申請種別">
              {REQUEST_TYPES.map((item) => (
                <button
                  key={item.value}
                  type="button"
                  role="radio"
                  aria-checked={type === item.value}
                  className={`request-type-card${type === item.value ? ' is-selected' : ''}`}
                  onClick={() => setType(item.value)}
                >
                  <strong>{item.label}</strong>
                  <span>{item.description}</span>
                </button>
              ))}
            </div>
            <label className="request-detail-field">
              <span>申請内容の詳細</span>
              <textarea
                value={detail}
                maxLength={1000}
                rows={5}
                placeholder={type === 'deletion'
                  ? '例: 2026年6月30日に投稿した「〜」という投稿を削除してください / アカウントごと削除してください'
                  : '例: 保存されている自分の情報の内容を確認したいです'}
                onChange={(event) => setDetail(event.target.value)}
              />
              <small>{detail.length}/1000</small>
            </label>
            {message ? <p className="request-message" role="status">{message}</p> : null}
            <button className="primary-button" type="button" disabled={busy || detail.trim().length < 5} onClick={() => void submit()}>
              {busy ? '送信中…' : 'この内容で申請する'}
            </button>
          </section>

          <section className="legal-section section-block">
            <h2>申請履歴</h2>
            {requests.length ? (
              <div className="request-history">
                {requests.map((item) => (
                  <article key={item.id} className="request-history-item">
                    <div className="request-history-head">
                      <span className={`request-status request-status-${item.status}`}>
                        {item.status === 'resolved' ? '対応済み' : '対応待ち'}
                      </span>
                      <strong>{item.typeLabel}申請</strong>
                      <time>{dateLabel(item.createdAt)}</time>
                    </div>
                    <p>{item.detail}</p>
                    {item.status === 'resolved' && item.resolvedAt ? (
                      <small>対応日時: {dateLabel(item.resolvedAt)}</small>
                    ) : null}
                  </article>
                ))}
              </div>
            ) : (
              <p className="section-text">まだ申請はありません。</p>
            )}
          </section>
        </>
      )}
    </div>
  );
}
