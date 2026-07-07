import { useEffect, useState } from 'react';
import { subscribeApiError, type ApiErrorPayload, type ApiErrorSeverity } from '../features/webservice/errorBus';

type Displayed = ApiErrorPayload & { key: number };

const SEVERITY_META: Record<ApiErrorSeverity, { icon: string; accent: string; defaultTitle: string }> = {
  security: { icon: '🛡️', accent: '#c0392b', defaultTitle: 'セキュリティ通知' },
  error:    { icon: '⚠️', accent: '#c0392b', defaultTitle: 'エラーが発生しました' },
  warning:  { icon: '⚠️', accent: '#b8860b', defaultTitle: '注意' },
};

/**
 * グローバルエラーモーダル。
 *
 * `showApiError({...})` を呼び出すと、この 1 つのモーダルが最前面に出て、
 * ユーザーに状況を伝える。CORS ブロックや CSRF 失敗などの API エラーを、
 * 通知バーの代わりに目立つ形で確実に見せるためのコンポーネント。
 */
export function ErrorModal() {
  const [current, setCurrent] = useState<Displayed | null>(null);
  const [showDetail, setShowDetail] = useState(false);

  useEffect(() => {
    return subscribeApiError((payload) => {
      setCurrent({ ...payload, key: Date.now() });
      setShowDetail(false);
    });
  }, []);

  useEffect(() => {
    if (!current) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setCurrent(null);
    };
    window.addEventListener('keydown', onKey);
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = previous;
    };
  }, [current]);

  if (!current) return null;

  const severity = current.severity ?? 'error';
  const meta = SEVERITY_META[severity];
  const title = current.title ?? meta.defaultTitle;

  const close = () => setCurrent(null);
  const reload = () => {
    try {
      window.location.reload();
    } catch {
      // ignore
    }
  };

  return (
    <div
      role="presentation"
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(15, 20, 30, 0.55)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 10000,
        padding: 16,
      }}
      onClick={close}
    >
      <section
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="api-error-title"
        onClick={(event) => event.stopPropagation()}
        style={{
          width: 'min(520px, 100%)',
          background: '#fff',
          borderRadius: 14,
          padding: '22px 24px 20px',
          boxShadow: '0 22px 60px rgba(0,0,0,0.25)',
          borderTop: `4px solid ${meta.accent}`,
          fontFamily: 'inherit',
          color: '#222',
        }}
      >
        <header style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
          <span aria-hidden="true" style={{ fontSize: 28, lineHeight: 1 }}>{meta.icon}</span>
          <h2 id="api-error-title" style={{ margin: 0, fontSize: 20, color: meta.accent }}>{title}</h2>
        </header>

        <p style={{ margin: '0 0 14px', whiteSpace: 'pre-wrap', lineHeight: 1.6, fontSize: 14.5 }}>
          {current.message}
        </p>

        {(current.detail || typeof current.status === 'number') ? (
          <div style={{ marginBottom: 14 }}>
            <button
              type="button"
              onClick={() => setShowDetail((v) => !v)}
              style={{
                background: 'transparent',
                border: 'none',
                color: '#666',
                fontSize: 12,
                cursor: 'pointer',
                padding: 0,
                textDecoration: 'underline',
              }}
            >
              {showDetail ? '詳細を隠す' : '詳細を表示'}
            </button>
            {showDetail ? (
              <pre
                style={{
                  marginTop: 8,
                  padding: '10px 12px',
                  background: '#f5f6f8',
                  border: '1px solid #e0e2e6',
                  borderRadius: 8,
                  fontSize: 12,
                  lineHeight: 1.5,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-all',
                  color: '#444',
                  maxHeight: 160,
                  overflow: 'auto',
                }}
              >
                {typeof current.status === 'number' ? `HTTP ${current.status}\n` : ''}
                {current.detail ?? ''}
              </pre>
            ) : null}
          </div>
        ) : null}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
          <button
            type="button"
            onClick={close}
            style={{
              padding: '8px 16px',
              borderRadius: 8,
              border: '1px solid #d0d3d8',
              background: '#fff',
              color: '#333',
              cursor: 'pointer',
              fontSize: 14,
            }}
          >
            閉じる
          </button>
          <button
            type="button"
            onClick={reload}
            style={{
              padding: '8px 16px',
              borderRadius: 8,
              border: 'none',
              background: meta.accent,
              color: '#fff',
              cursor: 'pointer',
              fontSize: 14,
              fontWeight: 600,
            }}
          >
            ページを再読み込み
          </button>
        </div>
      </section>
    </div>
  );
}
