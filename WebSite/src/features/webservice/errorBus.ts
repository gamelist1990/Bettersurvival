// グローバルなエラーモーダル通知バス
// どこからでも `showApiError({...})` を呼び出せる。ErrorModal がこれを購読して描画する。

export type ApiErrorSeverity = 'error' | 'warning' | 'security';

export type ApiErrorPayload = {
  /** モーダルの上部に表示するタイトル。省略時は severity ごとの既定値を使う。 */
  title?: string;
  /** ユーザー向けの日本語メッセージ。 */
  message: string;
  /** 開発者向け詳細 (原文の英語メッセージ / HTTP ステータス等)。折りたたみで表示。 */
  detail?: string;
  /** ステータスコード。表示補助用。 */
  status?: number;
  /** モーダル種別。CSS の色分けに使う。 */
  severity?: ApiErrorSeverity;
};

const API_ERROR_EVENT = 'bettersurvival:api-error';

const bus: EventTarget = typeof window !== 'undefined' ? window : new EventTarget();

/** グローバルエラーモーダルを開く。 */
export function showApiError(payload: ApiErrorPayload) {
  try {
    bus.dispatchEvent(new CustomEvent<ApiErrorPayload>(API_ERROR_EVENT, { detail: payload }));
  } catch {
    // 環境によっては dispatch 不可 (SSR 等) — その場合はコンソールにフォールバック
    // eslint-disable-next-line no-console
    console.error('[API ERROR]', payload);
  }
}

/** ErrorModal 用: エラー通知を購読する。 */
export function subscribeApiError(handler: (payload: ApiErrorPayload) => void): () => void {
  const listener = (event: Event) => {
    const custom = event as CustomEvent<ApiErrorPayload>;
    if (custom.detail) handler(custom.detail);
  };
  bus.addEventListener(API_ERROR_EVENT, listener);
  return () => bus.removeEventListener(API_ERROR_EVENT, listener);
}

/**
 * サーバー応答 (`{ success: false, message: '...' }`) が既知のセキュリティ系エラーかを判定し、
 * 該当する場合はグローバルエラーモーダルを開く。
 *
 * 対応:
 *  - Cross-origin request blocked (Origin/Referer 検証失敗)
 *  - CSRF validation failed (CSRF トークン不一致)
 *  - JSON request required (Content-Type 不一致)
 *  - Rate limit exceeded 系
 *
 * 呼び出し側は `if (maybeShowKnownApiError(payload, status)) return;` のように使うと便利。
 */
export function maybeShowKnownApiError(payload: { success?: boolean; message?: string } | null | undefined, status?: number): boolean {
  if (!payload || payload.success !== false || !payload.message) return false;
  const raw = String(payload.message);
  const lower = raw.toLowerCase();

  if (lower.includes('cross-origin request blocked')) {
    showApiError({
      title: 'アクセスがブロックされました',
      severity: 'security',
      message:
        'サーバーが別ドメインからのリクエストとして拒否しました。\n' +
        'ブックマークやリンク経由でアクセスしている場合は、正しい URL からもう一度開いてください。\n' +
        'ページを再読み込みしても直らない場合は、ブラウザを再起動するか管理者へご連絡ください。',
      detail: raw,
      status,
    });
    return true;
  }

  if (lower.includes('csrf validation failed') || lower.includes('csrf token')) {
    showApiError({
      title: 'セッションの有効期限が切れました',
      severity: 'security',
      message:
        'セキュリティトークン (CSRF) の検証に失敗しました。\n' +
        'ページを再読み込みして、もう一度ログインからやり直してください。',
      detail: raw,
      status,
    });
    return true;
  }

  if (lower.includes('json request required')) {
    showApiError({
      title: 'リクエスト形式エラー',
      severity: 'error',
      message:
        'リクエストが正しい形式で送信できませんでした。\nページを再読み込みして、もう一度お試しください。',
      detail: raw,
      status,
    });
    return true;
  }

  if (lower.includes('rate limit') || status === 429) {
    showApiError({
      title: 'アクセスが多すぎます',
      severity: 'warning',
      message:
        '短時間に多くのリクエストが送信されました。\n少し時間をおいてから、もう一度お試しください。',
      detail: raw,
      status,
    });
    return true;
  }

  return false;
}
