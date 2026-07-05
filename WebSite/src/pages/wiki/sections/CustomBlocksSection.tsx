import { SectionShell } from '../components/SectionShell';
import { ImagePlaceholder } from '../components/ImagePlaceholder';

const customBlocks = [
  {
    name: 'カスタムエンチャントテーブル',
    imageId: 'custom-block-custom-enchant-table',
    material: 'エンチャントテーブル + ラピスラズリ',
    kind: '装備強化用の専用設備',
    what: '通常の Minecraft エンチャントとは別に、BetterSurvival 独自のカスタムエンチャントを道具や装備へ付けるための専用テーブルです。採掘、戦闘、移動、生存を強化する特殊効果を選んで付与できます。',
    goodFor: '採掘を楽にしたい人、装備を強くしたい人、通常エンチャントだけでは物足りない人。',
    how: [
      'エンチャントテーブルとラピスラズリを用意します。',
      'アイテム合成で専用の「カスタムエンチャントテーブル」を作ります。',
      '完成したテーブルを安全な場所に設置します。',
      '設置したテーブルを右クリックして専用 UI を開きます。',
      '強化したい道具や装備を入れ、付けたいカスタムエンチャントを選びます。',
    ],
    beginner: '最初は、壊れても困らない予備のツルハシや斧で試すのがおすすめです。効果を理解してから本命装備に付けると失敗しにくいです。',
    notes: [
      '/toggle customenchant が無効だと作成・使用できません。',
      '見た目が通常のエンチャントテーブルに近くても、専用データを持つ別アイテムです。',
      '設置済みテーブルは壊したり移動したりする前に、周囲の保護設定やサーバールールを確認してください。',
    ],
  },
  {
    name: 'ParallelFurnace / 並列かまど',
    imageId: 'custom-block-parallel-furnace',
    material: 'かまど + 石炭ブロック',
    kind: '精錬効率を上げる設備',
    what: '通常のかまどより効率よく精錬するための BetterSurvival 独自設備です。大量の鉱石や食料を処理したいときに役立ちます。',
    goodFor: '採掘後の鉱石処理、拠点の生産設備、燃料をまとめて使いたい場面。',
    how: [
      'かまどと石炭ブロックを用意します。',
      '指定された組み合わせで専用の並列かまどを作ります。',
      '拠点など使いやすい場所に設置します。',
      '精錬したいアイテムと燃料を入れて使います。',
    ],
    beginner: '通常のかまどに慣れてから使うと分かりやすいです。まず少量の鉱石で動作を確認してください。',
    notes: [
      '/toggle parallelfurnace が有効な場合のみ使えます。',
      '燃料や投入アイテムの細かい仕様はゲーム内 UI の表示に従ってください。',
      '共有拠点で使う場合は、誰が燃料や素材を入れるか決めておくとトラブルを防げます。',
    ],
  },
  {
    name: 'Recycler / リサイクラー',
    imageId: 'custom-block-recycler',
    material: '砥石 + 鉄ブロック',
    kind: '不要品を処理する設備',
    what: '不要になったアイテムを素材へ戻したり、処分したりするための Rust 風リサイクラーです。倉庫整理や余った装備の処理に使います。',
    goodFor: '不要アイテムの整理、チェスト圧迫の解消、素材回収を試したい場面。',
    how: [
      '砥石と鉄ブロックを用意します。',
      '専用のリサイクラーを作成します。',
      '設置したリサイクラーを開きます。',
      '不要なアイテムを入れて処理します。',
    ],
    beginner: '大事な装備を入れる前に、石の道具や余った素材などで動作を試してください。',
    notes: [
      '/toggle recycler が有効な場合のみ使えます。',
      'すべてのアイテムが必ず素材へ戻るとは限りません。',
      '一度処理したアイテムは元に戻せない可能性があるため、確認してから使ってください。',
    ],
  },
  {
    name: 'ChunkLoader / チャンクローダー',
    imageId: 'custom-block-chunkloader',
    material: 'コンパス + 名札',
    kind: '離れていても周辺処理を維持する設備',
    what: 'プレイヤーが近くにいない場所でも、指定した周辺チャンクを維持するための設備です。農場、かまど設備、遠隔拠点などを動かし続けたいときに使います。',
    goodFor: '自動農場、精錬設備、遠くの作業拠点を運用したい場面。',
    how: [
      'コンパスと名札を用意します。',
      'チャンクローダー用アイテムを作ります。',
      '動かし続けたい場所に設置または登録します。',
      '対象範囲が意図した場所になっているか確認します。',
    ],
    beginner: 'サーバー負荷に関係する機能です。最初は本当に必要な場所に 1 つだけ置いて使い方を覚えるのがおすすめです。',
    notes: [
      '/toggle chunkloader が有効な場合のみ使えます。',
      '大量設置はサーバー負荷につながるため、管理者のルールに従ってください。',
      '使わなくなった場所のチャンクローダーは撤去してください。',
    ],
  },
  {
    name: 'WarpStone / ワープストーン',
    imageId: 'custom-block-warpstone',
    material: 'エンダーパール + 石レンガ',
    kind: '拠点間移動用の設備',
    what: 'Waystones 風に、登録した地点へ移動するためのワープ設備です。拠点、村、採掘場、共同施設などへの移動を楽にできます。',
    goodFor: '遠い拠点同士の移動、共同施設へのアクセス、探索後の帰還。',
    how: [
      'エンダーパールと石レンガを用意します。',
      'ワープストーンを作成します。',
      '移動先にしたい場所へ設置します。',
      'ゲーム内の表示に従って地点登録や移動を行います。',
    ],
    beginner: '最初は自分の拠点など、迷っても困らない場所で登録を試してください。',
    notes: [
      '/toggle warpstone が有効な場合のみ使えます。',
      '他人の土地や保護区域で使う場合は、土地保護設定に注意してください。',
      '移動演出や制限がある場合は、ゲーム内メッセージを確認してください。',
    ],
  },
  {
    name: 'LandProtect / 土地保護コア',
    imageId: 'custom-block-landprotect-core',
    material: 'ロデストーン + ダイヤ',
    kind: '拠点や土地を守る中核ブロック',
    what: 'Rust 風に土地を保護し、チェスト・建築・破壊などのアクセスを管理するための中核ブロックです。拠点荒らしや誤操作から大事な場所を守る目的で使います。',
    goodFor: '個人拠点、共同拠点、ショップ周辺、重要施設の保護。',
    how: [
      'ロデストーンとダイヤを用意します。',
      '土地保護コアを作成します。',
      '守りたい拠点や土地に設置します。',
      '保護範囲やメンバー設定を確認します。',
      '共有拠点では、誰をメンバーにするか相談してから設定します。',
    ],
    beginner: '最初に覚えるべき重要設備です。チェストや建物を作る前に、保護コアの使い方を確認しておくと安全です。',
    notes: [
      '/toggle landprotect が有効な場合のみ使えます。',
      'ChestLock、ChestShop、DeathChest、Ring、Tournament など他機能と関係する場合があります。',
      '共有拠点では管理者を決めてから使うと、権限トラブルを防げます。',
    ],
  },
];

export function CustomBlocksSection() {
  return (
    <SectionShell
      eyebrow="Custom Blocks"
      title="カスタムブロック / 設置物"
      intro="BetterSurvival には、通常の Minecraft にはない専用設備があります。初心者向けに「何のための物か」「何を用意するか」「どう使うか」「注意点」をまとめます。"
      scope="player"
    >
      <ImagePlaceholder
        id="custom-blocks-hero"
        label="カスタムブロック設置例"
        hint="カスタムエンチャントテーブル、並列かまど、リサイクラー、ワープストーン、土地保護コアなどを並べたスクリーンショット"
        aspect="16/9"
      />

      <h3>最初に知っておくこと</h3>
      <ul className="wiki-bullets">
        <li>カスタムブロックは、普通のブロックに BetterSurvival 独自のデータや機能を付けたものです。</li>
        <li>見た目が通常ブロックと似ていても、内部的には専用アイテムとして扱われる場合があります。</li>
        <li>多くのカスタムブロックは /toggle の対象です。使えない場合は、まず対象機能が有効か確認してください。</li>
        <li>大事な拠点や共有施設で使う前に、まず安全な場所で動作確認すると失敗しにくいです。</li>
      </ul>

      <h3>カスタムブロック一覧</h3>
      <div className="wiki-feature-list">
        {customBlocks.map((block) => (
          <article className="wiki-feature-row wiki-guide-card" key={block.name}>
            <div className="wiki-guide-head">
              <span className="wiki-guide-tag">{block.kind}</span>
              <h4>{block.name}</h4>
            </div>
            <p><strong>必要なもの:</strong> {block.material}</p>
            <p><strong>どういうもの:</strong> {block.what}</p>
            <p><strong>おすすめ用途:</strong> {block.goodFor}</p>

            <ImagePlaceholder
              id={block.imageId}
              label={`${block.name} の画像`}
              hint="ここにアイテム画像、設置例、UI、クラフト素材が分かるスクリーンショットを配置してください。"
              aspect="16/9"
            />

            <div className="wiki-mini-section">
              <strong>使い方</strong>
              <ol>
                {block.how.map((step) => <li key={step}>{step}</li>)}
              </ol>
            </div>

            <div className="wiki-mini-section wiki-mini-section-tip">
              <strong>初心者向けポイント</strong>
              <p>{block.beginner}</p>
            </div>

            <div className="wiki-mini-section">
              <strong>注意点</strong>
              <ul>
                {block.notes.map((note) => <li key={note}>{note}</li>)}
              </ul>
            </div>
          </article>
        ))}
      </div>
    </SectionShell>
  );
}
