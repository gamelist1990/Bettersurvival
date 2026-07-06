import { SectionShell } from '../components/SectionShell';
import { WikiLink } from '../components/WikiNavContext';

type Recipe = {
  name: string;
  category: string;
  itemA: string;
  itemB: string;
  result: string;
  toggleKey: string;
  defaultOn: boolean;
  note?: string;
};

const recipes: Recipe[] = [
  {
    name: 'BetterSurvival GUI (b-menu 木の斧)',
    category: 'GUI ツール',
    itemA: '木の斧',
    itemB: '名前を「b-menu」にした名札',
    result: '専用の木の斧 (右クリックで GUI が開く)',
    toggleKey: 'bettermenu',
    defaultOn: true,
    note: 'GUI からトグル切替 / TPA・Home・ChestLock のショートカットにアクセスできます。',
  },
  {
    name: 'カスタムエンチャントテーブル',
    category: 'エンチャント',
    itemA: 'エンチャントテーブル',
    itemB: 'ラピスラズリ',
    result: 'カスタムエンチャントテーブル',
    toggleKey: 'customenchant',
    defaultOn: true,
    note: '設置して右クリックで独自エンチャント付与 UI を開きます。',
  },
  {
    name: 'エンチャント分離砥石',
    category: 'エンチャント',
    itemA: '砥石',
    itemB: '分離条件を満たすエンチャント本',
    result: 'エンチャント分離砥石',
    toggleKey: 'enchantsplit',
    defaultOn: false,
    note: 'デフォルト OFF。複数エンチャント本を 1 つずつに分離します。',
  },
  {
    name: 'Copper Golem Core',
    category: 'モブ召喚 (1/2)',
    itemA: '銅ブロック',
    itemB: 'くり抜かれたカボチャ',
    result: 'Copper Golem Core (召喚用)',
    toggleKey: 'coppergolem',
    defaultOn: true,
    note: 'このコア単体では召喚できません。次の名札レシピと組み合わせます。',
  },
  {
    name: 'Copper Golem 召喚',
    category: 'モブ召喚 (2/2)',
    itemA: 'Copper Golem Core',
    itemB: '名前を「golem-<任意ID>」にした名札',
    result: 'カッパーゴーレム 1 体',
    toggleKey: 'coppergolem',
    defaultOn: true,
    note: '例: golem-farm1、golem-guard2。ID ごとに個別プロファイルを持ちます。',
  },
  {
    name: '共有ストレージ 主チェスト',
    category: '共有ストレージ',
    itemA: 'チェスト',
    itemB: '名前を「chest-<ID>」にした名札 (例: chest-base1)',
    result: '主 (main) チェスト',
    toggleKey: 'sharedstorage',
    defaultOn: false,
    note: '1 系統につき 1 個のみ。ラージチェスト不可。隣にチェスト設置不可。詳細は「共有ストレージ 詳細ガイド」参照。',
  },
  {
    name: '共有ストレージ sub チェスト',
    category: '共有ストレージ',
    itemA: 'チェスト',
    itemB: '名前を「chestsub-<ID>」にした名札 (main と同じ ID)',
    result: 'sub チェスト',
    toggleKey: 'sharedstorage',
    defaultOn: false,
    note: '主チェストと同じ ID で連携する保管場所。複数設置できます。',
  },
  {
    name: '共有ストレージ sub トロッコ',
    category: '共有ストレージ',
    itemA: 'チェスト付きトロッコ',
    itemB: '名前を「chestsub-<ID>」にした名札',
    result: 'sub チェスト (トロッコ版)',
    toggleKey: 'sharedstorage',
    defaultOn: false,
    note: 'レール上でも sub として動作します。',
  },
  {
    name: 'ParallelFurnace / 並列かまど',
    category: '生産',
    itemA: 'かまど',
    itemB: '石炭ブロック',
    result: '並列かまど (最大複数ライン同時精錬)',
    toggleKey: 'parallelfurnace',
    defaultOn: true,
    note: '右クリックで作業コア UI。ホッパー搬入出と搬出チェスト指定に対応。',
  },
  {
    name: 'Recycler / リサイクラー',
    category: '生産',
    itemA: '砥石',
    itemB: '鉄ブロック',
    result: 'リサイクラー',
    toggleKey: 'recycler',
    defaultOn: true,
    note: '不要品を分解。約 50% 素材返却、残りは経験値で得られます。',
  },
  {
    name: 'ChunkLoader / チャンクローダー',
    category: 'ワールド',
    itemA: 'コンパス',
    itemB: '名前を「chunkloader」にした名札',
    result: 'チャンクローダー',
    toggleKey: 'chunkloader',
    defaultOn: true,
    note: '設置チャンク周辺 3x3 チャンクを維持。1 プレイヤー 1 個まで。',
  },
  {
    name: 'WarpStone / ワープストーン',
    category: '移動',
    itemA: 'エンダーパール',
    itemB: '石レンガ',
    result: 'ワープストーン',
    toggleKey: 'warpstone',
    defaultOn: true,
    note: '設置して名前を付ける → 他のワープストーンを右クリックで発見 → 発見済みへワープ。',
  },
  {
    name: 'LandProtect / 土地保護コア',
    category: '保護',
    itemA: 'ロデストーン',
    itemB: 'ダイヤモンド (アイテム)',
    result: '土地保護コア',
    toggleKey: 'landprotect',
    defaultOn: true,
    note: '設置して右クリックでオーナー登録。燃料を入れている間だけ保護が維持されます。',
  },
];

const categoryOrder = [
  'GUI ツール',
  '保護',
  '共有ストレージ',
  '生産',
  'エンチャント',
  'モブ召喚 (1/2)',
  'モブ召喚 (2/2)',
  'ワールド',
  '移動',
];

const groupedRecipes = categoryOrder
  .map((category) => ({
    category,
    items: recipes.filter((r) => r.category === category),
  }))
  .filter((group) => group.items.length > 0);

export function RecipesSection() {
  return (
    <SectionShell
      eyebrow="Recipes"
      title="合成レシピ一覧 (ItemCombine)"
      intro="BetterSurvival の独自アイテムは「素材 A と素材 B を近くに一緒にドロップする」という共通ルールで合成します。ここではすべての合成レシピを 1 ページにまとめます。"
      scope="player"
    >
      <h3>合成の共通ルール</h3>
      <ul className="wiki-bullets">
        <li>素材 A と素材 B をアイテムエンティティ (ドロップされた状態) にします。プレイヤーから <code>Q</code> キーで投げるのが基本です。</li>
        <li>2 つのアイテムが <strong>地上で 0.5 マス以内</strong>、または <strong>空中で 1.5 マス以内</strong> に接近すると合成が発火します。</li>
        <li>Y 座標 (縦方向) は 2 マス以内まで許容されます。</li>
        <li>合成が成立するとその場に完成品がドロップし、素材はそれぞれ 1 個ずつ消費されます。</li>
        <li>対応する <code>/toggle &lt;キー&gt;</code> が <strong>無効</strong> のときは合成は起こりません。</li>
        <li>名札を使うレシピは、まず <strong>金床で名札の名前を指定文字列に変更</strong> してから投げてください。名前が違うと反応しません。</li>
      </ul>

      <h3>全レシピ (カテゴリ別)</h3>
      <div className="wiki-feature-list">
        {groupedRecipes.map((group) => (
          <div key={group.category}>
            <h4>{group.category}</h4>
            {group.items.map((recipe) => (
              <article className="wiki-feature-row wiki-guide-card" key={recipe.name}>
                <div className="wiki-guide-head">
                  <span className="wiki-guide-tag">{recipe.category}</span>
                  <h4>{recipe.name}</h4>
                </div>
                <p className="wiki-recipe-formula">
                  <code>{recipe.itemA}</code> <strong> + </strong>{' '}
                  <code>{recipe.itemB}</code> <strong> → </strong>{' '}
                  <code>{recipe.result}</code>
                </p>
                <p>
                  <strong>Toggle キー:</strong> <code>/toggle {recipe.toggleKey}</code>{' '}
                  <span className="wiki-guide-tag">
                    {recipe.defaultOn ? 'デフォルト ON' : 'デフォルト OFF'}
                  </span>
                </p>
                {recipe.note ? (
                  <div className="wiki-mini-section">
                    <strong>メモ</strong>
                    <p>{recipe.note}</p>
                  </div>
                ) : null}
              </article>
            ))}
          </div>
        ))}
      </div>

      <h3>関連ページ</h3>
      <ul className="wiki-bullets">
        <li><WikiLink to="/wiki/custom-blocks"><strong>カスタムブロック / 設置物</strong></WikiLink>: 完成品の使い方や UI の詳細はこちらのページで解説しています。</li>
        <li><WikiLink to="/wiki/shared-storage"><strong>共有ストレージ 詳細ガイド</strong></WikiLink>: SharedStorage の設定 UI と運用の詳細。</li>
        <li><WikiLink to="/wiki/custom-enchants"><strong>カスタムエンチャント</strong></WikiLink>: カスタムエンチャントテーブルで付けられるエンチャントの一覧。</li>
        <li><WikiLink to="/wiki/feature-toggle"><strong>/toggle 機能一覧</strong></WikiLink>: 各 toggle キーの現在状態や切替方法。</li>
      </ul>
    </SectionShell>
  );
}
