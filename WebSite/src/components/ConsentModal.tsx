import { useState } from 'react';

export const CONSENT_STORAGE_KEY = 'bettersurvival.website.consent.v1';

export function hasConsented() {
  try {
    return window.localStorage.getItem(CONSENT_STORAGE_KEY) === 'true';
  } catch {
    // ストレージが使えない環境では毎回表示する
    return false;
  }
}

type ConsentModalProps = {
  onAgree: () => void;
  onNavigate: (href: string) => void;
};

/**
 * アカウント登録前に利用規約・プライバシーポリシーへの同意を求めるモーダル。
 * 同意チェックを入れるまで「同意して始める」は押せない（スキップ不可）。
 * ポリシー全文は同意前でも閲覧できる。
 */
export function ConsentModal({ onAgree, onNavigate }: ConsentModalProps) {
  const [checked, setChecked] = useState(false);

  const agree = () => {
    if (!checked) return;
    try {
      window.localStorage.setItem(CONSENT_STORAGE_KEY, 'true');
    } catch {
      // 保存できなくても今セッションは続行できるようにする
    }
    onAgree();
  };

  return (
    <div className="consent-overlay" role="presentation">
      <section className="consent-modal" role="dialog" aria-modal="true" aria-labelledby="consent-title">
        <p className="consent-eyebrow">アカウント登録の前に</p>
        <h1 id="consent-title">登録内容とデータの扱いを確認してください</h1>
        <p className="consent-lead">同意後、入力済みの内容でアカウント登録を続行します。</p>

        <div className="consent-points">
          <div className="consent-point">
            <span aria-hidden="true">🎯</span>
            <div>
              <strong>使いみちは明確に</strong>
              <p>Minecraft名・投稿・IPアドレスは、ログイン・フィード表示・荒らし対策だけに使います。</p>
            </div>
          </div>
          <div className="consent-point">
            <span aria-hidden="true">🔒</span>
            <div>
              <strong>第三者に渡しません</strong>
              <p>あなたの情報を外部へ売ったり提供したりすることはありません。</p>
            </div>
          </div>
          <div className="consent-point">
            <span aria-hidden="true">🗑️</span>
            <div>
              <strong>いつでも削除OK</strong>
              <p>自分の情報の開示・削除は、専用ページからいつでも申請できます。</p>
            </div>
          </div>
        </div>

        <p className="consent-doc-link">
          全文はこちら:
          <button type="button" onClick={() => onNavigate('/privacy')}>利用規約・プライバシーポリシー</button>
        </p>

        <label className="consent-check">
          <input type="checkbox" checked={checked} onChange={(event) => setChecked(event.target.checked)} />
          <span>利用規約とプライバシーポリシーを確認し、同意します</span>
        </label>

        <button className="consent-agree-button" type="button" disabled={!checked} onClick={agree}>
          同意して登録を続ける
        </button>
      </section>
    </div>
  );
}
