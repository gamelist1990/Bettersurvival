import { SectionShell } from '../components/SectionShell';
import { CommandBox } from '../components/CommandBox';
import { WikiLink } from '../components/WikiNavContext';

export function PartySection() {
  return (
    <SectionShell
      eyebrow="Party"
      title="パーティー (ギルド)"
      intro="複数プレイヤーで色付きの名前・共通土地保護を持つグループを作る仕組み。招待制で参加し、3 階級の権限が用意されています。"
      scope="player"
    >
      <h3>どんな機能か</h3>
      <ul className="wiki-bullets">
        <li>プレイヤー同士でパーティー (ギルド) を作り、共通の <strong>名前 / カラー / 説明</strong>を持ちます。</li>
        <li>階級は <strong>リーダー / サブリーダー / メンバー</strong> の 3 段階。土地保護コアはパーティーで共有できます。</li>
        <li>ネームタグや Tab リストにパーティーカラーの接頭辞を付けられます (設定で ON/OFF)。</li>
        <li>味方同士のダメージ (フレンドリーファイア) は既定で OFF、リーダーが切り替え可能。</li>
      </ul>

      <h3>パーティーを作る</h3>
      <ol>
        <li><CommandBox command="/party" description="パーティーメニューを開きます (/p も同じ)。" /></li>
        <li>「新規作成」から、以下を指定します:
          <ul className="wiki-bullets" style={{ marginTop: 8 }}>
            <li><strong>名前</strong>: 2〜16 文字。§ カラーコードは自動で剥がれます。<strong>他パーティーと重複不可</strong>。</li>
            <li><strong>カラー</strong>: パレットから 1 色。<strong>他パーティーと重複不可</strong>。</li>
            <li><strong>説明</strong>: 任意。</li>
          </ul>
        </li>
        <li>作成した瞬間、あなたが <strong>リーダー</strong>になります。</li>
      </ol>

      <h3>3 段階の権限</h3>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ borderCollapse: 'collapse', width: '100%', fontSize: 13 }}>
          <thead>
            <tr style={{ background: 'rgba(255,255,255,0.06)' }}>
              <th style={{ padding: '6px 10px', textAlign: 'left', border: '1px solid rgba(255,255,255,0.1)' }}>階級</th>
              <th style={{ padding: '6px 10px', textAlign: 'left', border: '1px solid rgba(255,255,255,0.1)' }}>できること</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td style={{ padding: '6px 10px', border: '1px solid rgba(255,255,255,0.1)', color: '#c98a3a', fontWeight: 800 }}>リーダー</td>
              <td style={{ padding: '6px 10px', border: '1px solid rgba(255,255,255,0.1)' }}>解散 / メンバー除名 / 階級変更 / 名前・色・説明・FF・表示設定 / 土地保護の管理</td>
            </tr>
            <tr>
              <td style={{ padding: '6px 10px', border: '1px solid rgba(255,255,255,0.1)', color: '#c9b03a', fontWeight: 800 }}>サブリーダー</td>
              <td style={{ padding: '6px 10px', border: '1px solid rgba(255,255,255,0.1)' }}>招待 / メンバー除名 (自分より下の階級) / 土地保護の設定変更</td>
            </tr>
            <tr>
              <td style={{ padding: '6px 10px', border: '1px solid rgba(255,255,255,0.1)', color: 'var(--wiki-muted)', fontWeight: 800 }}>メンバー</td>
              <td style={{ padding: '6px 10px', border: '1px solid rgba(255,255,255,0.1)' }}>パーティーチャットの利用、土地保護内でのアクセス、脱退</td>
            </tr>
          </tbody>
        </table>
      </div>

      <h3>招待とメンバー管理</h3>
      <ul className="wiki-bullets">
        <li>リーダー / サブリーダーが GUI から <strong>招待</strong>を送ります。招待は <strong>5 分で自動失効</strong>します。</li>
        <li>招待された側は GUI またはチャットの案内から <strong>承諾 / 拒否</strong>を選びます。</li>
        <li>1 人のプレイヤーは同時に <strong>1 パーティーにしか所属できません</strong>。</li>
        <li>メンバーが名前を変えても、再ログイン時に自動で表示名が更新されます。</li>
      </ul>

      <h3>表示設定 (ネームタグ / チャット)</h3>
      <ul className="wiki-bullets">
        <li><strong>ネームタグ色</strong>: プレイヤーの頭上の名前をパーティーカラーで染めるか。既定 ON。</li>
        <li><strong>接頭辞</strong>: 名前の前に <code>[パーティー名]</code> を付けるか。既定 ON。</li>
        <li><strong>フレンドリーファイア</strong>: 味方に攻撃が通るか。既定 OFF。PvP イベント時などに切替。</li>
      </ul>

      <h3>他機能との連携</h3>
      <ul className="wiki-bullets">
        <li><strong>土地保護</strong>: コアの管理者にパーティー全体を紐付けできます。リーダー / サブリーダーがコア設定を触れ、メンバーは自動でアクセス許可。</li>
        <li><strong>Ring / Tournament</strong>: パーティー単位で参加する運用も可能 (現在地の設定に依存)。</li>
        <li><strong>DeathChest / ChestLock</strong>: パーティーメンバーに個別で共有権限を付ける運用ができます。</li>
      </ul>

      <h3>コマンド</h3>
      <CommandBox command="/party" description="パーティーメニューを開きます (/p も同じ)。作成 / 招待 / 階級変更などは全てここから。" />
      <CommandBox command="/party info" description="自分が所属するパーティーの情報を表示。" />
      <CommandBox command="/party list" description="全パーティーの一覧を表示。" />

      <h3>よくあるハマりどころ</h3>
      <ul className="wiki-bullets">
        <li><strong>名前が使えない</strong>: 他のパーティーが同じ名前 (大文字小文字無視) を使っています。少し変えて再試行を。</li>
        <li><strong>色が選べない</strong>: そのカラーが他パーティーに取られています。カラーは <strong>先着 1 組</strong>のみ。</li>
        <li><strong>招待が消えた</strong>: 招待の有効期限は 5 分。時間内に承諾しないと自動失効します。</li>
        <li><strong>2 つ入れない</strong>: 現在のパーティーを脱退してから新しいパーティーに入ってください。</li>
      </ul>

      <h3>関連ページ</h3>
      <ul className="wiki-bullets">
        <li><WikiLink to="/wiki/land-protection"><strong>土地保護</strong></WikiLink>: パーティー共有の保護コアと権限。</li>
        <li><WikiLink to="/wiki/commands"><strong>コマンド一覧</strong></WikiLink>: <code>/party</code> の詳細。</li>
      </ul>
    </SectionShell>
  );
}
