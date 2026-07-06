import { SectionShell } from '../components/SectionShell';
import { CommandBox } from '../components/CommandBox';
import { WikiLink } from '../components/WikiNavContext';

const manualSettings = [
  { name: 'subへ入れる', desc: '手で sub チェストにアイテムを入れられるか。' },
  { name: 'subから取る', desc: '手で sub チェストからアイテムを取り出せるか。' },
];

const hopperSettings = [
  { name: 'sub搬入', desc: 'ホッパーから sub チェストへアイテムを入れられるか。' },
  { name: 'sub搬出', desc: 'ホッパーで sub チェストから吸い出せるか。' },
  { name: 'main搬入', desc: 'ホッパーから主チェストへ入れられるか。入ったアイテムは自動で仕分けされます。' },
  { name: 'main搬出', desc: 'ホッパーで主チェストから吸い出せるか。' },
];

const functionSettings = [
  {
    name: '額縁フィルタ',
    desc: 'sub チェストに額縁でアイテムを貼っておくと、そのアイテムだけが自動で該当 sub に仕分けされます。',
  },
  {
    name: '一致モード',
    desc: '額縁フィルタの判定基準を選びます。EXACT (完全一致) / MATERIAL (種類のみ) / ENCHANT_STATE (エンチャ有無)。',
  },
  {
    name: 'ChestPage',
    desc: '主チェストをスニーク+左クリックすると、接続中の sub 一覧 UI を開けるようになります。',
  },
  {
    name: '搬送演出',
    desc: '仕分け時にパーティクルを出すかどうかの表示設定。',
  },
];

export function SharedStorageSection() {
  return (
    <SectionShell
      eyebrow="SharedStorage"
      title="共有ストレージ 詳細ガイド"
      intro="複数のチェストを 1 つの倉庫として扱う SharedStorage 機能の使い方と、設定 UI の全項目をまとめます。"
      scope="player"
    >
      <h3>ざっくり何ができるか</h3>
      <ul className="wiki-bullets">
        <li>1 つの <strong>主チェスト (main)</strong> と、複数の <strong>sub チェスト</strong> を "ID" で束ねます。</li>
        <li>主チェストに放り込んだアイテムは、接続中の sub へ<strong>自動で仕分け</strong>されます。</li>
        <li>sub には普通のチェストでも、<strong>チェスト付きトロッコ</strong>でもなれます。</li>
        <li>額縁を使えば「この sub にはこのアイテムだけ入れる」みたいなルールも作れます。</li>
        <li>設定は全部 GUI から。主チェストの隣に木の斧などの sort 棒アイテムを持って開くとメニューが出ます。</li>
      </ul>

      <h3>1. 名札を作る (最初の準備)</h3>
      <p>金床で名札を以下の形式に変更します。<code>&lt;ID&gt;</code> は好きな名前 (半角英数字が無難) にしてください。同じ ID を持つチェスト同士が同じ倉庫グループになります。</p>
      <CommandBox command="chest-<ID>" description="主 (main) チェスト用の名札。例: chest-base1" />
      <CommandBox command="chestsub-<ID>" description="sub チェスト用の名札。例: chestsub-base1 (main と同じ ID で揃える)" />

      <h3>2. 主チェストと sub チェストを作る</h3>
      <ol>
        <li><code>chest-&lt;ID&gt;</code> の名札を <strong>普通のチェスト</strong> と一緒にドロップ → 主チェストのアイテムが完成。</li>
        <li><code>chestsub-&lt;ID&gt;</code> の名札を <strong>普通のチェスト</strong> または <strong>チェスト付きトロッコ</strong> と一緒にドロップ → sub のアイテムが完成。</li>
        <li>完成したアイテムを設置します。</li>
      </ol>

      <h3>3. 設置時の重要ルール</h3>
      <ul className="wiki-bullets">
        <li><strong>主チェストは 1 系統につき 1 個</strong>だけ。同じ ID で 2 個目は置けません。</li>
        <li>主チェストは <strong>ラージチェスト不可</strong>。単体のチェストとして設置します。</li>
        <li>主チェストの <strong>真隣 (東西南北) にはチェストを置けません</strong>。うっかりラージチェスト化を防ぐためです。</li>
        <li>sub は <strong>同じ ID / 同じ役割</strong> なら隣接させてラージチェスト化 OK。</li>
        <li>sub は<strong>複数個</strong>置けます。同じ ID の sub は全部同じネットワークにまとまります。</li>
        <li>sub の <strong>初期の接続範囲</strong> は主チェストから一定ブロック以内。範囲は後述の UI で変更できます (最大 50 ブロック)。</li>
      </ul>

      <h3>4. 設定 UI を開く</h3>
      <p>主チェストの近くで、<strong>木の棒などの sort 棒</strong>を持ってスニーク右クリック、または主チェストを直接右クリックで設定メニューが開きます (実装の <code>SharedStorageSettingsUi</code>)。</p>

      <h3>設定 UI の項目一覧</h3>

      <h4>■ 情報表示</h4>
      <ul className="wiki-bullets">
        <li><strong>ID / 接続中の sub 数 / 現在の接続範囲</strong> が表示されます。ここは確認用です。</li>
      </ul>

      <h4>■ 手動操作 (プレイヤーが手で sub を開くとき)</h4>
      <ul className="wiki-bullets">
        {manualSettings.map((s) => (
          <li key={s.name}><strong>{s.name}</strong>: {s.desc}</li>
        ))}
      </ul>

      <h4>■ ホッパー搬送 (ホッパー・ドロッパーなど)</h4>
      <ul className="wiki-bullets">
        {hopperSettings.map((s) => (
          <li key={s.name}><strong>{s.name}</strong>: {s.desc}</li>
        ))}
      </ul>

      <h4>■ 機能設定</h4>
      <ul className="wiki-bullets">
        {functionSettings.map((s) => (
          <li key={s.name}><strong>{s.name}</strong>: {s.desc}</li>
        ))}
      </ul>

      <h4>■ アクション</h4>
      <ul className="wiki-bullets">
        <li><strong>sub 接続範囲を変更</strong>: 主チェストからどれだけ離れた sub まで接続するかを設定。上限は 50 ブロック。</li>
        <li><strong>今すぐ再仕分け</strong>: main / sub の全アイテムを再スキャンして仕分けし直します。中身がぐちゃぐちゃになったときのリセットボタン。</li>
        <li><strong>閉じる</strong>: UI を閉じるだけ。</li>
      </ul>

      <h3>額縁フィルタの使い方</h3>
      <ol>
        <li>「機能設定」の <strong>額縁フィルタ</strong> を許可にします。</li>
        <li>sub チェストの正面のブロック面に <strong>額縁</strong> を貼ります。</li>
        <li>額縁に「その sub に入れたいアイテム」を差し込みます (複数の額縁を貼れば複数種類を許可)。</li>
        <li>これで主チェストへ入れたアイテムのうち、額縁で指定したアイテムだけがその sub に流れます。</li>
        <li><strong>一致モード</strong>を変えると判定が変わります: EXACT は NBT まで完全一致 / MATERIAL は材料が同じなら OK / ENCHANT_STATE はエンチャの有無で判定。</li>
      </ol>

      <h3>ChestPage (sub 一覧 UI) の使い方</h3>
      <ul className="wiki-bullets">
        <li>「機能設定」の <strong>ChestPage</strong> を許可にします。</li>
        <li>主チェストを <strong>スニーク + 左クリック</strong>すると、接続中の sub がカテゴリ別・チェスト別に一覧表示されます。</li>
        <li>そこから直接目的の sub の中身を開けるので、大量の sub を並べている拠点で便利です。</li>
      </ul>

      <h3>チェスト付きトロッコを sub にする</h3>
      <p><code>chestsub-&lt;ID&gt;</code> の名札を <strong>チェスト付きトロッコ</strong> にも合成できます。設置 (レール上に配置) すると同様に sub として認識されます。移動する sub を作れるので、レール輸送との組み合わせに向いています。</p>

      <h3>よくあるハマりどころ</h3>
      <ul className="wiki-bullets">
        <li><strong>名前の prefix が違う</strong>: <code>main</code> / <code>sub</code> ではなく <code>chest-</code> / <code>chestsub-</code> です (前後にスペースや余計な文字を入れない)。</li>
        <li><strong>ID が一致していない</strong>: 主と sub は完全に同じ ID にしないと連携しません (大文字小文字は同一視されます)。</li>
        <li><strong>主チェストが接続範囲外</strong>: sub を遠くに置きすぎると認識されません。UI で範囲を広げてください (最大 50 ブロック)。</li>
        <li><strong>ホッパーで搬入・搬出が動かない</strong>: 「ホッパー搬送」の該当項目が禁止になっていないか確認。</li>
        <li><strong>手で sub から取り出せない</strong>: 「手動操作」の設定が禁止になっていないか確認。</li>
        <li><strong>ChestLock / ChestShop と衝突</strong>: 主チェストの隣にチェストを置けないのは、ChestLock/ChestShop のラージチェスト化を避けるためです。</li>
      </ul>

      <h3>関連ページ</h3>
      <ul className="wiki-bullets">
        <li><WikiLink to="/wiki/recipes"><strong>合成レシピ一覧</strong></WikiLink>: main / sub / sub トロッコの合成手順の要点。</li>
        <li><WikiLink to="/wiki/custom-blocks"><strong>カスタムブロック / 設置物</strong></WikiLink>: 他の合成系設備との位置づけ。</li>
        <li><WikiLink to="/wiki/commands"><strong>コマンド一覧</strong></WikiLink>: /chest など関連コマンドの詳細。</li>
      </ul>
    </SectionShell>
  );
}
