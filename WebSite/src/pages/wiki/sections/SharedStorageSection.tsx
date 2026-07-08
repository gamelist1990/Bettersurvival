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

interface CategoryTag {
  category: string;
  desc: string;
  keywords: string[];
}

const categoryTags: CategoryTag[] = [
  {
    category: '戦闘 (combat)',
    desc: '剣・斧・弓・クロスボウ・トライデント・メイス・盾・矢・防具一式などをまとめて。',
    keywords: ['combat', '戦闘'],
  },
  {
    category: '道具と実用品 (tools / utilities)',
    desc: 'ピッケル・斧・シャベル・クワ・ハサミ・釣り竿・火打石・時計・コンパス・望遠鏡・ブラシ・全種類のバケツ・名札・リード・エリトラ・鞍・にんじん棒・歪んだキノコ棒。',
    keywords: ['tools', 'tool', 'utilities', 'utility', '道具', '実用品', '道具と実用品'],
  },
  {
    category: '機能性ブロック (functional)',
    desc: 'ドア/トラップドア/フェンスゲート/ボタン/レール/シュルカー/看板/バナー/ベッド、チェスト・樽・かまど・作業台系ブロックなど。',
    keywords: ['functional', '機能性', '機能性ブロック'],
  },
  {
    category: 'レッドストーン系ブロック (redstone)',
    desc: 'RS 回路まわり (レッドストーン・反復・比較・観測・ピストン・粘着ピストン・ホッパー・ドロッパー・ディスペンサー・オブザーバーなど)。',
    keywords: ['redstone', 'レッドストーン', 'レッドストーン系ブロック'],
  },
  {
    category: '食料 (food)',
    desc: '「食べられるアイテム」を判定してまとめて仕分け (毒のクモの目は除外)。',
    keywords: ['food', 'edible', '食べ物', '食料'],
  },
  {
    category: '防具全部 (armor)',
    desc: 'ヘルメット/チェスト/レギンス/ブーツを種類問わずまとめて 1 つの sub に。',
    keywords: ['armor', 'armour', '防具'],
  },
  { category: 'ヘルメットだけ', desc: 'カブトのみ。防具を種類ごとに分けたいときに。', keywords: ['helmet', 'ヘルメット'] },
  { category: 'チェストプレートだけ', desc: '胸当てのみ。', keywords: ['chestplate', 'チェストプレート'] },
  { category: 'レギンスだけ', desc: '脚防具のみ。', keywords: ['leggings', 'レギンス'] },
  { category: 'ブーツだけ', desc: '足防具のみ。', keywords: ['boots', 'ブーツ'] },
  { category: '剣 (sword)', desc: '全種類の剣。', keywords: ['sword', '剣'] },
  { category: '斧 (axe)', desc: '全種類の斧。', keywords: ['axe', '斧'] },
  {
    category: 'ピッケル (pickaxe)',
    desc: '全種類のツルハシ。表記ゆれ (ピッケル/つるはし/ツルハシ) 全部認識。',
    keywords: ['pickaxe', 'ピッケル', 'つるはし', 'ツルハシ'],
  },
  { category: 'シャベル (shovel)', desc: '全種類のシャベル。', keywords: ['shovel', 'spade', 'シャベル'] },
  { category: 'クワ (hoe)', desc: '全種類のクワ。', keywords: ['hoe', 'クワ', 'くわ'] },
  { category: '作物 (crop)', desc: '農作物系 (小麦/にんじん/ジャガイモ 等)。', keywords: ['crop', 'farm', '作物', '農作物'] },
  { category: '鉱石 (ore)', desc: '鉱石ブロック系全般。', keywords: ['ore', 'mineral', '鉱物', '鉱石'] },
  {
    category: '素材 (material)',
    desc: 'クラフト素材系。',
    keywords: ['ingredients', 'ingredient', 'materials', 'material', '材料', '素材'],
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

      <h3>額縁フィルタの使い方 (基本)</h3>
      <ol>
        <li>「機能設定」の <strong>額縁フィルタ</strong> を許可にします。</li>
        <li>sub チェストの正面のブロック面に <strong>額縁</strong> を貼ります。</li>
        <li>額縁に「その sub に入れたいアイテム」を差し込みます (複数の額縁を貼れば複数種類を許可)。</li>
        <li>これで主チェストへ入れたアイテムのうち、額縁で指定したアイテムだけがその sub に流れます。</li>
        <li><strong>一致モード</strong>を変えると判定が変わります: EXACT は NBT まで完全一致 / MATERIAL は材料が同じなら OK / ENCHANT_STATE はエンチャの有無で判定。</li>
      </ol>

      <h3>額縁フィルタの応用 ①: <strong>名札に名前をつけてカテゴリ指定</strong></h3>
      <p>
        額縁にアイテムを差す代わりに、<strong>金床で名前を付けた「名札 (Name Tag)」</strong>を額縁に差し込むと、
        その名前に含まれるキーワードから<strong>アイテム種類のまとまり (カテゴリ)</strong>で仕分けができます。
        「剣 sub」「防具 sub」「食料 sub」みたいなアバウトな倉庫を組みたいときに便利です。
      </p>
      <ul className="wiki-bullets">
        <li>認識する対象は <strong>額縁に差した名札の表示名</strong>のみ (色コードは無視されます)。</li>
        <li>大文字/小文字・日本語/英語の表記ゆれはある程度吸収されます。</li>
        <li>1 枚の名札に複数キーワードは書けます。<strong>最初に一致したカテゴリ</strong>が採用されます (下の表の上から順に判定)。</li>
        <li>名札のカテゴリ判定は、通常アイテム額縁より <strong>優先度が高く</strong>扱われます (詳細は下の「優先度」節)。</li>
      </ul>

      <h4>使えるカテゴリキーワード一覧</h4>
      <div className="wiki-table-wrapper">
        <table className="wiki-table">
          <thead>
            <tr>
              <th>カテゴリ</th>
              <th>名札に含める文字 (どれか)</th>
              <th>仕分け対象</th>
            </tr>
          </thead>
          <tbody>
            {categoryTags.map((tag) => (
              <tr key={tag.category}>
                <td>{tag.category}</td>
                <td>
                  {tag.keywords.map((k) => (
                    <code key={k} style={{ marginRight: '0.4em' }}>{k}</code>
                  ))}
                </td>
                <td>{tag.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <p>
        たとえば額縁に「<code>剣</code>」という名札を差せばその sub は<strong>剣専用倉庫</strong>に、
        「<code>防具</code>」なら<strong>ヘルメ〜ブーツ全部を集める倉庫</strong>になります。
        「<code>ピッケル用</code>」のように余分な文字が付いていても、キーワード<code>ピッケル</code>が含まれていれば認識されます。
      </p>

      <h3>額縁フィルタの応用 ②: <strong>「all」修飾子で系統ごと拾う</strong></h3>
      <p>
        額縁に差したアイテムの表示名 (金床で付けたもの) に <code>all</code> という単語が含まれていると、
        そのアイテムと<strong>同じ系統 (マテリアルファミリー) 全部</strong>を仕分け対象にします。
      </p>
      <ul className="wiki-bullets">
        <li>例: 名前に <code>all</code> を含む <strong>オークの木材</strong> を額縁に差すと、
            <strong>木材系ブロック全般</strong>がその sub にまとまります。</li>
        <li>単語の切れ目で認識するので、<code>all wood</code> / <code>木材 all</code> のような書き方も OK。</li>
        <li>優先度は「名札カテゴリ」より下、「通常のアイテム額縁」より上です。</li>
      </ul>

      <h3>額縁フィルタの応用 ③: <strong>「chest-clear」で"素の状態だけ"を拾う</strong></h3>
      <p>
        額縁に差したアイテムの表示名 (金床で付けたもの) が<strong>ちょうど </strong><code>chest-clear</code><strong> と一致</strong>していると、
        「そのアイテムと同じ種類」で、かつ<strong>特別な状態のもの</strong>だけを sub に流します。
      </p>
      <ul className="wiki-bullets">
        <li>マテリアル (=アイテムの種類) は額縁のアイテムと完全一致が必要。</li>
        <li>一致モードが <strong>ENCHANT_STATE</strong> の場合はさらに<strong>エンチャの有無まで一致</strong>する物だけを取ります。
            (例: エンチャ付きだけを別 sub に集める / エンチャ無しの素の物だけ集める、みたいな運用)</li>
        <li>雑多に流れてきたアイテムから<strong>綺麗な状態のものだけを分別</strong>したいときに便利です。</li>
      </ul>

      <h3>フィルタの<strong>優先度</strong> (複数の sub が候補になったとき)</h3>
      <p>
        同じ ID の中に「剣が入る sub」が複数あった場合、以下の優先度でどこに入れるかが決まります。
        <strong>数字が小さいほど優先</strong>されます。
      </p>
      <ol>
        <li><strong>名札カテゴリ額縁</strong> (例: 名札「剣」を額縁) — もっとも具体的とみなされ最優先。</li>
        <li><strong>all 修飾子額縁</strong> (例: 名前に <code>all</code> を含む木材) — 系統ごと拾う中間層。</li>
        <li><strong>通常アイテム額縁</strong> (例: 素の鉄インゴットを額縁に差す) — 一番具体的だが優先度は最下位。</li>
      </ol>
      <p>
        「大分類の sub と細かい sub を混在させたい」という運用でも、細かいほうに優先で流れないので、
        意図した通りの倉庫構成になります。
      </p>

      <h3>ChestPage (sub 一覧 UI) の使い方</h3>
      <ul className="wiki-bullets">
        <li>「機能設定」の <strong>ChestPage</strong> を許可にします。</li>
        <li>主チェストを <strong>スニーク + 左クリック</strong>すると、接続中の sub がカテゴリ別・チェスト別に一覧表示されます。</li>
        <li>一覧では<strong>フィルタ内容 + #番号</strong>で並びます (例: <code>剣 #1</code> / <code>剣 #2</code>)。フィルタ未設定の sub は <code>sub #1</code> の形式で表示されます。</li>
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
        <li><strong>カテゴリ名札が効かない</strong>: 額縁に差した<strong>名札</strong>に、上の表のキーワードが含まれているか確認 (色コードは無視されます)。名札以外のアイテムでは「名札カテゴリ」判定はされません。</li>
        <li><strong><code>chest-clear</code> がただの空チェストを吸わない</strong>: <code>chest-clear</code> は「その額縁アイテムと同じ種類だけを拾う」機能です。全アイテムを吸うフィルタではありません。</li>
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
