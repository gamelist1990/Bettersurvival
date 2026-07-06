import { useCallback, useEffect, useState } from 'react';
import { postJson, storedToken } from '../features/webservice/api';
import type { AuthProfile } from '../features/webservice/types';

type AdminPrivacyRequestItem = {
  id: string;
  username: string;
  type: string;
  typeLabel: string;
  detail: string;
  status: 'open' | 'resolved' | string;
  createdAt: number;
  resolvedAt: number;
};

type AdminPageProps = {
  profile: AuthProfile | null;
  onNavigate: (href: string) => void;
};

function dateLabel(value: number) {
  if (!value) return '';
  return new Date(value).toLocaleString('ja-JP', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

/**
 * 連携しているMinecraftアカウントが現在OPの場合のみ操作できる管理ページ。
 * ナビゲーションやフッターにはリンクを置かず、直接URLでのみ到達させる。
 */
export function AdminPage({ profile, onNavigate }: AdminPageProps) {
  const [adminChecked, setAdminChecked] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);
  const [requests, setRequests] = useState<AdminPrivacyRequestItem[]>([]);
  const [message, setMessage] = useState('');
  const [resolvingId, setResolvingId] = useState<string | null>(null);

  const loadRequests = useCallback(async () => {
    const token = storedToken();
    if (!token) return;
    try {
      const response = await fetch('/api/v1/admin/privacy/requests', {
        headers: { Authorization: `Bearer ${token}` },
        credentials: 'include',
        cache: 'no-store',
      });
      const payload = await response.json();
      if (payload.success && Array.isArray(payload.requests)) {
        setRequests(payload.requests as AdminPrivacyRequestItem[]);
      }
    } catch {
      // 一覧取得の失敗はゲート表示自体には影響させない
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    const checkAdmin = async () => {
      const token = storedToken();
      if (!profile || !token) {
        setAdminChecked(true);
        setIsAdmin(false);
        return;
      }
      try {
        const response = await fetch('/api/v1/admin/me', {
          headers: { Authorization: `Bearer ${token}` },
          credentials: 'include',
          cache: 'no-store',
        });
        const payload = await response.json();
        if (cancelled) return;
        setIsAdmin(Boolean(payload.isAdmin));
      } catch {
        if (!cancelled) setIsAdmin(false);
      } finally {
        if (!cancelled) setAdminChecked(true);
      }
    };
    setAdminChecked(false);
    void checkAdmin();
    return () => {
      cancelled = true;
    };
  }, [profile]);

  useEffect(() => {
    if (isAdmin) void loadRequests();
  }, [isAdmin, loadRequests]);

  const resolve = async (id: string) => {
    if (!window.confirm('この申請を対応済みにしますか？')) return;
    setResolvingId(id);
    setMessage('');
    try {
      const payload = await postJson('/api/v1/admin/privacy/requests/resolve', { id }, storedToken());
      if (payload.success) {
        void loadRequests();
      } else {
        setMessage(payload.message ?? '対応に失敗しました');
      }
    } catch {
      setMessage('サービスに接続できません。しばらくしてからもう一度お試しください。');
    } finally {
      setResolvingId(null);
    }
  };

  if (!profile) {
    return (
      <div className="legal-page">
        <header className="legal-hero section-block">
          <p className="eyebrow">Admin</p>
          <h1>管理者ページ</h1>
        </header>
        <section className="legal-section section-block request-login-gate">
          <h2>ログインが必要です</h2>
          <p className="section-text">
            本人確認のため、このページの利用にはログインが必要です。
          </p>
          <button className="primary-button" type="button" onClick={() => onNavigate('/profile')}>ログインページへ</button>
        </section>
      </div>
    );
  }

  if (!adminChecked) {
    return (
      <div className="legal-page">
        <section className="legal-section section-block">
          <p className="section-text">確認中…</p>
        </section>
      </div>
    );
  }

  if (!isAdmin) {
    return (
      <div className="legal-page">
        <section className="legal-section section-block">
          <h2>権限がありません</h2>
          <p className="section-text">このページを表示する権限がありません。</p>
        </section>
      </div>
    );
  }

  return (
    <div className="legal-page">
      <header className="legal-hero section-block">
        <p className="eyebrow">Admin</p>
        <h1>個人情報請求の対応</h1>
        <p className="section-text">
          訂正・削除・利用停止の本人請求を確認し、対応が完了したものを「対応済み」にできます。
          開示請求は本人がその場でダウンロードできるため、ここには表示されません。
        </p>
      </header>

      <section className="legal-section section-block">
        <h2>申請一覧</h2>
        {message ? <p className="request-message" role="status">{message}</p> : null}
        {requests.length ? (
          <div className="request-history">
            {requests.map((item) => (
              <article key={item.id} className="request-history-item">
                <div className="request-history-head">
                  <span className={`request-status request-status-${item.status}`}>
                    {item.status === 'resolved' ? '対応済み' : '対応待ち'}
                  </span>
                  <strong>{item.typeLabel}申請</strong>
                  <span className="request-form-user">申請者: <strong>{item.username}</strong></span>
                  <time>{dateLabel(item.createdAt)}</time>
                </div>
                <p>{item.detail}</p>
                {item.status === 'resolved' && item.resolvedAt ? (
                  <small>対応日時: {dateLabel(item.resolvedAt)}</small>
                ) : (
                  <button
                    className="primary-button"
                    type="button"
                    disabled={resolvingId === item.id}
                    onClick={() => void resolve(item.id)}
                  >
                    {resolvingId === item.id ? '処理中…' : '対応済みにする'}
                  </button>
                )}
              </article>
            ))}
          </div>
        ) : (
          <p className="section-text">未対応の申請はありません。</p>
        )}
      </section>
    </div>
  );
}
