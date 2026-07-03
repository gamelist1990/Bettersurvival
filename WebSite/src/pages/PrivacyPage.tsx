type PrivacyPageProps = {
  onNavigate: (href: string) => void;
};

/**
 * 利用規約・プライバシーポリシーの専用ページ。
 * 個人情報保護法対応: 利用目的の明示 / 第三者提供の禁止 /
 * 開示・訂正・削除・利用停止請求への対応窓口を明記する。
 */
export function PrivacyPage({ onNavigate }: PrivacyPageProps) {
  return (
    <div className="legal-page">
      <header className="legal-hero section-block">
        <p className="eyebrow">Terms &amp; Privacy</p>
        <h1>利用規約・プライバシーポリシー</h1>
        <p className="section-text">
          BetterSurvival ポータル（以下「本サービス」）における利用条件と、個人情報の取り扱いについて定めたページです。
          本サービスを利用された時点で、本規約およびプライバシーポリシーに同意いただいたものとみなします。
        </p>
        <div className="legal-quick-points">
          <div className="legal-point">
            <span className="legal-point-icon">🎯</span>
            <strong>利用目的を明示</strong>
            <p>集めた情報はサービス提供のためだけに使います</p>
          </div>
          <div className="legal-point">
            <span className="legal-point-icon">🔒</span>
            <strong>第三者提供なし</strong>
            <p>法令に基づく場合を除き、外部へ提供しません</p>
          </div>
          <div className="legal-point">
            <span className="legal-point-icon">🗑️</span>
            <strong>開示・削除に対応</strong>
            <p>本人からの請求は申請ページから受け付けます</p>
          </div>
        </div>
        <button className="primary-button legal-request-cta" type="button" onClick={() => onNavigate('/privacy/request')}>
          開示・削除などの申請はこちら
        </button>
      </header>

      <section className="legal-section section-block" id="terms">
        <h2>利用規約</h2>
        <ol className="legal-list">
          <li><strong>サービス内容</strong> — 本サービスは Minecraft サーバー「BetterSurvival」のポータルとして、フィード（投稿共有）、プロフィール、Webマップなどを提供します。</li>
          <li><strong>アカウント</strong> — 登録には Minecraft アカウントとの連携（ワンタイムコード）が必要です。アカウントの管理は利用者本人の責任で行ってください。</li>
          <li><strong>禁止事項</strong> — 他者への誹謗中傷・嫌がらせ、個人情報の無断掲載、わいせつ・違法なコンテンツの投稿、サービスの運営を妨げる行為（過剰なリクエスト送信など）を禁止します。</li>
          <li><strong>投稿の取り扱い</strong> — 投稿はフィード機能を通じて Minecraft サーバー内および連携する Discord チャンネルにも表示されます。</li>
          <li><strong>免責</strong> — 本サービスは個人運営のコミュニティサービスであり、予告なく内容の変更・停止を行うことがあります。</li>
          <li><strong>違反時の対応</strong> — 規約違反があった場合、投稿の削除やアカウントの利用停止を行うことがあります。</li>
        </ol>
      </section>

      <section className="legal-section section-block" id="privacy">
        <h2>プライバシーポリシー</h2>

        <h3>1. 取得する情報</h3>
        <ul className="legal-list">
          <li>Minecraft アカウント情報（プレイヤー名・UUID・スキン画像）</li>
          <li>メールアドレス（登録時に任意入力した場合のみ）</li>
          <li>投稿内容・添付画像・プロフィール情報（ニックネーム、自己紹介、リンクなど）</li>
          <li>IP アドレスとアクセス記録</li>
          <li>Discord 連携が有効な場合、Discord上の表示名・アバター・投稿内容</li>
        </ul>

        <h3>2. 利用目的</h3>
        <p>取得した情報は、次の目的の範囲内でのみ利用します。</p>
        <ul className="legal-list">
          <li>アカウントの認証とログイン状態の維持</li>
          <li>フィード・プロフィールなど本サービスの機能提供</li>
          <li>Minecraft サーバー・Discord との投稿連携（本人の投稿の転送）</li>
          <li>不正アクセス・過剰リクエストの防止（IPアドレスによるレート制限）</li>
          <li>表示回数の集計（同一IPからの重複カウント防止のための一時利用）</li>
        </ul>

        <h3>3. 第三者への提供</h3>
        <p>
          取得した個人情報を第三者へ提供することはありません。
          ただし、法令に基づく開示請求があった場合、または人の生命・身体・財産の保護のために必要な場合はこの限りではありません。
          なお、Discord 連携は利用者本人が投稿した内容を本人の名義で転送するものであり、第三者提供には該当しません。
        </p>

        <h3>4. 開示・訂正・削除・利用停止の請求</h3>
        <p>
          利用者本人は、自己の個人情報について開示・訂正・削除・利用停止を請求できます。
          請求は<button className="legal-inline-link" type="button" onClick={() => onNavigate('/privacy/request')}>申請ページ</button>から行ってください
          （なりすまし防止のため、Minecraft アカウント連携によるログインを本人確認とします）。
          受け付けた請求には、原則として14日以内に対応します。
        </p>

        <h3>5. 安全管理措置</h3>
        <ul className="legal-list">
          <li>パスワードはソルト付きハッシュ（PBKDF2）で保存し、平文では保持しません</li>
          <li>セッションには CSRF 対策を実施しています</li>
          <li>データはサーバー内のローカルストレージにのみ保存し、外部サービスへ送信しません</li>
        </ul>

        <h3>6. Cookie・ローカルストレージ</h3>
        <p>
          ログイン状態の維持と本ポリシーへの同意状況の記録のために、Cookie およびブラウザのローカルストレージを利用します。
          広告・トラッキング目的では使用しません。
        </p>

        <h3>7. ポリシーの改定</h3>
        <p>本ポリシーは必要に応じて改定されることがあります。重要な変更がある場合は本ページでお知らせします。</p>

        <h3>8. お問い合わせ</h3>
        <p>
          本ポリシーに関するお問い合わせは、Minecraft サーバー内で運営者（OP）まで、
          または<button className="legal-inline-link" type="button" onClick={() => onNavigate('/privacy/request')}>申請ページ</button>からご連絡ください。
        </p>
      </section>

      <section className="legal-section section-block" id="telecom">
        <h2>法令上の位置づけ（電気通信事業法）</h2>
        <p>
          本サービスは、料金の徴収や広告収益などの営利を目的とせずに運営される、
          Minecraft サーバー参加者向けのコミュニティサービスです。
          電気通信事業法の登録・届出義務は「電気通信事業を営む者」（営利目的で反復継続して
          電気通信役務を提供する事業者）を対象としており、非営利で運営される本サービスは
          これに該当しないため、同法に基づく登録・届出は不要と整理しています。
        </p>
        <p>
          あわせて、本サービスの登録には Minecraft サーバー内で発行されるワンタイムコードが必要であり、
          サーバー参加者のみが利用できるクローズドなサービスです。
          不特定多数に対して電気通信役務を提供するものではない点も、この整理を補強するものです。
        </p>
        <p>
          なお、将来的に課金・広告掲載などの営利要素を導入する場合は、
          「他人の通信を媒介する電気通信事業」として届出が必要になる可能性があるため、
          その時点で改めて法令上の位置づけを見直します。
          また、届出の要否にかかわらず、利用者間の通信内容やプライバシーは本ポリシーに基づき保護します。
        </p>
      </section>
    </div>
  );
}
