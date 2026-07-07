import { useEffect, useState } from 'react';
import { SectionShell } from '../components/SectionShell';

type Scope = 'ANY' | 'MEMBER' | 'ADMIN' | 'ADMIN_OR_CONSOLE';

type CommandDoc = {
  name: string;
  aliases?: string[];
  scope: Scope;
  summary: string;
  usage: string;
  subCommands?: { name: string; text: string; scope?: Scope }[];
  examples?: string[];
  notes?: string[];
};

const commands: CommandDoc[] = [
  {
    name: '/help',
    scope: 'ANY',
    summary: '登録されているコマンド一覧を表示します。使えるコマンドだけがハイライトされます。',
    usage: '/help',
  },
  {
    name: '/list',
    scope: 'ANY',
    summary: '現在オンラインのプレイヤー一覧を表示します。',
    usage: '/list',
  },
  {
    name: '/ping',
    scope: 'ANY',
    summary: 'サーバーとの通信にかかる時間 (RTT) を計測して表示します。判定ラベルと色で目安がひと目でわかります。',
    usage: '/ping [プレイヤー名]',
    subCommands: [
      { name: '/ping', text: '自分の ping を表示します。プレイヤー専用。' },
      { name: '/ping <名前>', text: '指定プレイヤーの ping を表示します。OP 専用。', scope: 'ADMIN' },
    ],
    notes: [
      '通信遅延 (RTT): クライアント↔サーバー間のラウンドトリップ時間 (ms)。',
      'サーバー応答: コマンド受信から次 tick 実行までの遅延の目安 (ms)。TPS が下がっていると増加します。',
      '判定は 60ms 未満=非常に良好 / 120ms 未満=良好 / 200ms 未満=やや遅延 / それ以上=高遅延 の目安です。',
    ],
  },
  {
    name: '/rename',
    scope: 'ANY',
    summary: '手に持っているアイテムの名前を変更します。金床を使わずに命名できます。',
    usage: '/rename <新しい名前>',
    notes: ['&r や §r などのカラーコードは実装によって扱いが変わります。詳細はゲーム内メッセージを確認してください。'],
  },
  {
    name: '/toggle',
    scope: 'MEMBER',
    summary: '個人向けのトグル設定 UI を開きます。OP は追加サブコマンドで全体設定にアクセスできます。',
    usage: '/toggle [op|normal]',
    subCommands: [
      { name: '/toggle', text: '自分のトグル UI を開く。' },
      { name: '/toggle op', text: 'サーバー全体 (グローバル) のトグル UI を開く。OP 専用。', scope: 'ADMIN' },
      { name: '/toggle normal', text: '新規プレイヤーのデフォルト設定 UI を開く。OP 専用。', scope: 'ADMIN' },
    ],
  },
  {
    name: '/home',
    scope: 'MEMBER',
    summary: 'Home の登録・移動・削除・拡張スロット解放を行います。',
    usage: '/home [add|remove|list|ui|unlock|名前] [名前]',
    subCommands: [
      { name: '/home', text: 'デフォルトの Home へテレポートします。' },
      { name: '/home add <名前>', text: '現在地を Home として登録します。' },
      { name: '/home remove <名前>', text: '登録済み Home を削除します (delete / del も同じ)。' },
      { name: '/home list', text: '登録済み Home 一覧を表示します。' },
      { name: '/home ui', text: 'Home 管理 UI を開きます。' },
      { name: '/home unlock', text: '次の Home スロットを解放します (expand も同じ)。' },
      { name: '/home <名前>', text: '指定した名前の Home へテレポートします。' },
    ],
  },
  {
    name: '/tpa',
    scope: 'MEMBER',
    summary: '他プレイヤーへテレポートリクエストを送ります。',
    usage: '/tpa <-r|-a|-d|-l|ui> [プレイヤー名]',
    subCommands: [
      { name: '/tpa -r <名前>', text: '相手にリクエストを送信 (/tpa <名前> でも同じ)。' },
      { name: '/tpa -a <名前>', text: '相手からのリクエストを承認。' },
      { name: '/tpa -d <名前>', text: '相手からのリクエストを拒否。' },
      { name: '/tpa -l', text: '受信中リクエスト一覧を表示。' },
      { name: '/tpa ui', text: 'TPA 用の UI を開く。' },
    ],
  },
  {
    name: '/party',
    aliases: ['/p'],
    scope: 'MEMBER',
    summary: 'パーティー (ギルド) を作成・管理する UI を開きます。',
    usage: '/party [info|list]',
    subCommands: [
      { name: '/party', text: 'パーティーメニューを開きます (/p も同じ)。' },
      { name: '/party info', text: '自分が所属するパーティー情報を表示。' },
      { name: '/party list', text: '全パーティー一覧を表示。' },
    ],
  },
  {
    name: '/chest',
    scope: 'MEMBER',
    summary: 'チェストのロック / 共有メンバー管理を行います。対象チェストを見ながら実行します。',
    usage: '/chest <lock|unlock|member|ui|op>',
    subCommands: [
      { name: '/chest lock <名前>', text: '見ているチェストを名前付きでロック。' },
      { name: '/chest unlock', text: '自分がロックしたチェストを解除。' },
      { name: '/chest member add|remove|list <名前>', text: 'ロックしたチェストのメンバー管理。' },
      { name: '/chest ui', text: 'ChestLock 用の UI を開きます。' },
      { name: '/chest op unlock|see|toggle', text: 'OP 用: 強制解除 / インベントリ閲覧 / 機能 ON/OFF。', scope: 'ADMIN' },
    ],
  },
  {
    name: '/land',
    scope: 'MEMBER',
    summary: '土地保護コアの情報表示・境界線デバッグ・リング設定を行います。',
    usage: '/land <debug|info|ring>',
    subCommands: [
      { name: '/land debug', text: '保護境界線のデバッグ表示を切り替え。' },
      { name: '/land info', text: '現在地の保護状況を表示。' },
      { name: '/land ring', text: 'リング (闘技場) の設定メニューを開く。オーナー / 副リーダーのみ。' },
    ],
  },
  {
    name: '/tournament',
    scope: 'MEMBER',
    summary: 'リングを使ったトーナメント大会に参加・管理します。詳細はゲーム内 UI に従ってください。',
    usage: '/tournament',
  },
  {
    name: '/hotp',
    scope: 'MEMBER',
    summary: 'WebSite にアカウント連携するためのワンタイムコードを表示します (期限 5 分)。',
    usage: '/hotp',
    notes: ['WebService が有効なときのみ使えます。'],
  },
  {
    name: '/invsee',
    scope: 'ADMIN',
    summary: '対象プレイヤーのインベントリを閲覧・編集します。',
    usage: '/invsee [プレイヤー名]',
    subCommands: [
      { name: '/invsee', text: 'プレイヤー選択 UI を開きます。' },
      { name: '/invsee <名前>', text: '指定プレイヤーのインベントリを直接開きます。オフラインでも可能。' },
    ],
  },
  {
    name: '/discord',
    scope: 'ADMIN',
    summary: 'Discord Webhook / Bot 通知の設定 UI を開きます。',
    usage: '/discord',
  },
  {
    name: '/webservice',
    scope: 'ADMIN',
    summary: 'WebService / WebMap の統合管理 UI を開きます。',
    usage: '/webservice [menu|status|enable|disable|…]',
    subCommands: [
      { name: '/webservice', text: '管理 UI を開きます (menu / settings と同じ)。' },
      { name: '/webservice status', text: '現在の稼働状況を表示。' },
      { name: '/webservice enable', text: 'WebService を有効化。' },
      { name: '/webservice disable', text: 'WebService を無効化。' },
    ],
  },
  {
    name: '/offline',
    scope: 'ADMIN',
    summary: 'オフラインアカウントのログイン許可リストを管理します。',
    usage: '/offline <サブコマンド>',
  },
  {
    name: '/w',
    scope: 'ADMIN_OR_CONSOLE',
    summary: '初回参加前のプレイヤーを接続待機ホワイトリストに登録します。',
    usage: '/w <サブコマンド>',
    notes: ['コンソールからも実行可能な、ホワイトリスト運用向けの管理コマンドです。'],
  },
  {
    name: '/command',
    scope: 'MEMBER',
    summary: 'サーバー内で使えるコマンドの有効 / 無効を細かく管理する UI を開きます。実際の変更は OP のみ可能です。',
    usage: '/command [サブコマンド]',
    notes: ['UI を開くこと自体はメンバーでも可能ですが、実際の切替やレベル変更操作は管理者権限が要求されます。'],
  },
];

const scopeStyle: Record<Scope, { label: string; color: string; bg: string; border: string }> = {
  ANY:              { label: 'ANY',           color: '#3a4f8a', bg: 'rgba(58, 79, 138, 0.12)',  border: 'rgba(58, 79, 138, 0.32)' },
  MEMBER:           { label: 'MEMBER',        color: '#257047', bg: 'rgba(58, 134, 89, 0.12)',  border: 'rgba(58, 134, 89, 0.32)' },
  ADMIN:            { label: 'OP / ADMIN',    color: '#9f4426', bg: 'rgba(206, 104, 65, 0.14)', border: 'rgba(206, 104, 65, 0.36)' },
  ADMIN_OR_CONSOLE: { label: 'OP or CONSOLE', color: '#7a5a20', bg: 'rgba(206, 135, 45, 0.14)', border: 'rgba(206, 135, 45, 0.34)' },
};

function ScopeBadge({ scope }: { scope: Scope }) {
  const s = scopeStyle[scope];
  return (
    <span
      style={{
        display: 'inline-block',
        padding: '2px 10px',
        borderRadius: 999,
        fontSize: 12,
        fontWeight: 800,
        color: s.color,
        background: s.bg,
        border: `1px solid ${s.border}`,
        letterSpacing: 0.5,
      }}
    >
      {s.label}
    </span>
  );
}

export function CommandsSection() {
  const [selected, setSelected] = useState<CommandDoc | null>(null);

  useEffect(() => {
    if (!selected) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setSelected(null);
    };
    window.addEventListener('keydown', onKey);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = previousOverflow;
    };
  }, [selected]);

  const grouped: { title: string; scopes: Scope[]; note: string }[] = [
    { title: 'メンバー (通常プレイヤー) が使えるコマンド', scopes: ['ANY', 'MEMBER'], note: '誰でも / メンバー権限で実行できるコマンドです。日常的に使う入口はここに集中しています。' },
    { title: 'OP / 管理者専用コマンド', scopes: ['ADMIN', 'ADMIN_OR_CONSOLE'], note: '管理者権限 (またはコンソール) が必要なコマンドです。サーバー管理・保全用途で使います。' },
  ];

  return (
    <SectionShell
      eyebrow="Commands"
      title="コマンド一覧"
      intro="プラグインが登録している全コマンドを、メンバー向け / 管理者向けに分けて紹介します。カードをクリックすると詳しい使い方をポップアップで確認できます。"
      scope="player"
    >
      <div className="wiki-cmd-legend">
        <p style={{ margin: 0 }}>
          カードの右上にあるラベルは、そのコマンドを誰が実行できるかの目安です。
          <ScopeBadge scope="ANY" /> は誰でも、
          <ScopeBadge scope="MEMBER" /> は通常のプレイヤーなら OK、
          <ScopeBadge scope="ADMIN" /> は OP 権限が要ります。
          <ScopeBadge scope="ADMIN_OR_CONSOLE" /> はコンソールからも叩けるタイプです。
        </p>
      </div>

      {grouped.map((group) => {
        const items = commands.filter((c) => group.scopes.includes(c.scope));
        return (
          <div key={group.title} style={{ marginTop: 24 }}>
            <h3>{group.title}</h3>
            <p style={{ opacity: 0.8 }}>{group.note}</p>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
                gap: 12,
                marginTop: 12,
              }}
            >
              {items.map((cmd) => (
                <button
                  key={cmd.name}
                  type="button"
                  onClick={() => setSelected(cmd)}
                  className="wiki-cmd-card"
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                    <code style={{ fontSize: 16, fontWeight: 800 }}>{cmd.name}</code>
                    <ScopeBadge scope={cmd.scope} />
                  </div>
                  {cmd.aliases && cmd.aliases.length > 0 ? (
                    <div className="wiki-cmd-card-alias">エイリアス: {cmd.aliases.map((a) => <code key={a} style={{ marginRight: 6 }}>{a}</code>)}</div>
                  ) : null}
                  <div className="wiki-cmd-card-summary">{cmd.summary}</div>
                  <div className="wiki-cmd-card-hint">クリックで詳細</div>
                </button>
              ))}
            </div>
          </div>
        );
      })}

      {selected ? <CommandModal cmd={selected} onClose={() => setSelected(null)} /> : null}
    </SectionShell>
  );
}

function CommandModal({ cmd, onClose }: { cmd: CommandDoc; onClose: () => void }) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={`${cmd.name} の詳細`}
      onClick={onClose}
      className="wiki-cmd-modal-overlay"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="wiki-cmd-modal-panel"
      >
        <div className="wiki-cmd-modal-head">
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
            <code style={{ fontSize: 20, fontWeight: 900 }}>{cmd.name}</code>
            <ScopeBadge scope={cmd.scope} />
            {cmd.aliases && cmd.aliases.length > 0 ? (
              <span className="wiki-cmd-modal-alias">= {cmd.aliases.join(', ')}</span>
            ) : null}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="閉じる"
            className="wiki-cmd-modal-close"
          >
            閉じる
          </button>
        </div>

        <div className="wiki-cmd-modal-body">
          <p style={{ lineHeight: 1.75, margin: 0 }}>{cmd.summary}</p>

          <div>
            <h4 className="wiki-cmd-modal-h4">Usage</h4>
            <pre className="wiki-cmd-modal-pre">{cmd.usage}</pre>
          </div>

          {cmd.subCommands && cmd.subCommands.length > 0 ? (
            <div>
              <h4 className="wiki-cmd-modal-h4">サブコマンド</h4>
              <ul className="wiki-cmd-modal-list">
                {cmd.subCommands.map((sc) => (
                  <li key={sc.name}>
                    <code style={{ fontWeight: 800 }}>{sc.name}</code>
                    {sc.scope ? <span style={{ marginLeft: 8 }}><ScopeBadge scope={sc.scope} /></span> : null}
                    <div className="wiki-cmd-modal-subtext">{sc.text}</div>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}

          {cmd.examples && cmd.examples.length > 0 ? (
            <div>
              <h4 className="wiki-cmd-modal-h4">例</h4>
              <ul className="wiki-cmd-modal-list">
                {cmd.examples.map((ex) => <li key={ex}><code>{ex}</code></li>)}
              </ul>
            </div>
          ) : null}

          {cmd.notes && cmd.notes.length > 0 ? (
            <div>
              <h4 className="wiki-cmd-modal-h4">メモ</h4>
              <ul className="wiki-cmd-modal-list">
                {cmd.notes.map((n) => <li key={n}>{n}</li>)}
              </ul>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
