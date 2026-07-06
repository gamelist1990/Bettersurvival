import { SectionShell } from '../components/SectionShell';
import { FetchedWikiImage } from '../components/FetchedWikiImage';

const customEnchantImage = '/images/wiki/custom-enchants-ui.png';

const enchantCategories = [
  {
    title: '採掘を便利にするエンチャント',
    target: 'ツルハシ、シャベル、斧など',
    summary: 'ブロック破壊、回収、精錬、経験値獲得を楽にする系統です。序盤から効果を体感しやすく、初心者にもおすすめです。',
    enchants: [
      '採掘加速',
      '範囲採掘',
      '土砂掘削',
      '自動回収',
      'オートスメルト',
      'マグネット',
      '経験値ブースト',
      'ヘイスト',
      'リーフ・フォーチュン',
      '自動再植',
    ],
    beginnerTip: '最初は「自動回収」「採掘加速」「オートスメルト」から試すと、何が便利になったか分かりやすいです。',
  },
  {
    title: '戦闘を強化するエンチャント',
    target: '剣、弓、戦闘用装備など',
    summary: '敵との戦闘、遠距離攻撃、回復、特定 Mob へのダメージを強化する系統です。探索やボス戦の準備に向いています。',
    enchants: [
      '戦利修復',
      '連射',
      '狙撃',
      'スナイパー',
      '吸血',
      '不死特効',
      '虫殺し',
      '点火の一撃',
    ],
    beginnerTip: '火力だけでなく、耐久や回復につながる効果も確認すると安全です。大事な武器に付ける前に予備武器で試してください。',
  },
  {
    title: '生存・移動を助けるエンチャント',
    target: '防具、ブーツ、普段使い装備など',
    summary: '移動、落下・奈落対策、水中行動、満腹度維持など、探索中の事故を減らす系統です。冒険が苦手な人ほど価値があります。',
    enchants: [
      '耐火のブーツ',
      '奈落の保護',
      '奈落救出',
      '水中呼吸',
      '満腹維持',
      '棘無効',
      '跳躍',
      '俊足のブーツ',
      '黒曜石の盾',
    ],
    beginnerTip: 'ネザーや洞窟探索が多い人は、防御・移動系を優先すると死亡リスクを下げやすいです。',
  },
  {
    title: '特殊な遊び方を増やすエンチャント',
    target: '用途ごとの専用装備',
    summary: '農業、視界、蘇生、特殊アクションなど、通常プレイに追加の選択肢を増やす系統です。慣れてきたプレイヤー向けの楽しい効果が多めです。',
    enchants: [
      'ナイトビジョン',
      'エンダーチェスト',
      '知恵',
      '成長加速',
      '泥棒の手',
      '連鎖蘇生',
      '養蜂家',
    ],
    beginnerTip: '名前だけで効果を決めつけず、まずゲーム内 UI の説明を読んでから付けるのがおすすめです。',
  },
];

const enchantDetails = [
  { name: '採掘加速', group: '採掘', target: 'ツルハシなど', text: '採掘作業を速くするためのエンチャントです。鉱石掘りや整地で使いやすい基本系です。' },
  { name: '範囲採掘', group: '採掘', target: 'ツルハシなど', text: '一度に広い範囲を掘るためのエンチャントです。大量採掘向けですが、壊したくないブロックが近くにある場所では注意が必要です。' },
  { name: '土砂掘削', group: '採掘', target: 'シャベルなど', text: '土、砂、砂利のような土砂系ブロックを掘る作業を助けるエンチャントです。整地や道作りで便利です。' },
  { name: '戦利修復', group: '戦闘', target: '武器・防具', text: '戦闘で得た成果を装備の維持に活かすタイプのエンチャントです。長く使いたい装備向けです。' },
  { name: '自動回収', group: '採掘', target: '道具', text: '壊したブロックやドロップ品の回収を楽にするエンチャントです。初心者が最初に試す候補として分かりやすい効果です。' },
  { name: 'オートスメルト', group: '採掘', target: 'ツルハシなど', text: '採掘したものを精錬結果として扱いやすくする系統です。鉱石処理の手間を減らしたいときに向いています。' },
  { name: 'マグネット', group: '回収', target: '道具・装備', text: '周囲のアイテム回収を補助するエンチャントです。採掘場や農場でアイテムを取り逃しにくくします。' },
  { name: '経験値ブースト', group: '成長', target: '道具・装備', text: '経験値獲得を強化する系統です。エンチャントや修繕のために経験値を集めたい人向けです。' },
  { name: 'ナイトビジョン', group: '探索', target: '装備', text: '暗い場所での視界を助けるエンチャントです。洞窟、夜間作業、地下拠点で便利です。' },
  { name: 'エンダーチェスト', group: '特殊', target: 'エンダーアイ', text: 'エンダーアイを右クリックするとその場でエンダーチェストを開けます。ワールドに投げる本来の動作はキャンセルされます。' },
  { name: 'ヘイスト', group: '採掘', target: '道具・装備', text: '作業速度を上げる系統です。採掘や整地をテンポよく進めたいときに向いています。' },
  { name: '知恵', group: '成長', target: '装備', text: '経験や成長に関係する特殊系エンチャントです。長期的に育成を進めたいプレイヤー向けです。' },
  { name: 'リーフ・フォーチュン', group: '採集', target: '斧・道具', text: '葉や植物系の収集を助けるエンチャントです。木材集めや農業寄りの作業と相性があります。' },
  { name: '成長加速', group: '農業', target: '農業用装備・道具', text: '作物や成長要素を補助するエンチャントです。農業を中心に遊ぶ人に向いています。' },
  { name: '耐火のブーツ', group: '防御', target: 'ブーツ', text: '火や熱に関係する危険を軽減するためのブーツ向けエンチャントです。ネザー探索で役立ちます。' },
  { name: '泥棒の手', group: '特殊', target: '道具・装備', text: '通常とは違う特殊な操作や入手に関係するエンチャントです。使う前にゲーム内説明を確認してください。' },
  { name: '奈落の保護', group: '防御', target: '防具', text: '奈落のような危険な落下・消失リスクに備えるエンチャントです。エンド探索や高所作業で安心材料になります。' },
  { name: '連射', group: '弓', target: '弓', text: '弓や遠距離攻撃の手数を増やす系統です。複数の敵を相手にするときに便利です。' },
  { name: '狙撃', group: '弓', target: '弓', text: '遠距離から狙って攻撃するプレイ向けのエンチャントです。距離を取って戦う人に向いています。' },
  { name: '奈落救出', group: '防御', target: '装備', text: '奈落に落ちたときの救済に関係するエンチャントです。危険な探索に行く前の保険になります。' },
  { name: '連鎖蘇生', group: '支援', target: '装備・特殊アイテム', text: '蘇生や復帰に関係する支援系エンチャントです。複数人で遊ぶ環境で価値が出やすいです。' },
  { name: 'スナイパー', group: '弓', target: '弓', text: '遠距離攻撃をより専門的に強化するエンチャントです。弓を主力にする人向けです。' },
  { name: '水中呼吸', group: '探索', target: '防具', text: '水中での行動を助けるエンチャントです。海底探索や水中建築で便利です。' },
  { name: '満腹維持', group: '生活', target: '装備', text: '空腹管理を楽にするエンチャントです。長時間の採掘や探索で食料消費を気にしたくない人向けです。' },
  { name: '棘無効', group: '防御', target: '防具', text: '棘のような反射・接触ダメージ系の不利を抑えるためのエンチャントです。戦闘時の事故を減らします。' },
  { name: '跳躍', group: '移動', target: 'ブーツ', text: 'ジャンプや段差移動を助けるエンチャントです。山岳地帯や建築作業で便利です。' },
  { name: '俊足のブーツ', group: '移動', target: 'ブーツ', text: '移動速度を強化するブーツ向けエンチャントです。探索、移動、逃走で使いやすい効果です。' },
  { name: '自動再植', group: '農業', target: '農業用道具', text: '収穫後の植え直しを助けるエンチャントです。畑の管理を楽にしたい人向けです。' },
  { name: '黒曜石の盾', group: '防御', target: '防具・盾系', text: '強固な防御をイメージしたエンチャントです。危険な探索や戦闘前に確認したい防御系です。' },
  { name: '養蜂家', group: '農業', target: '装備・道具', text: '蜂や養蜂に関係する特殊エンチャントです。ハチミツ、養蜂場、農業拠点と相性があります。' },
  { name: '吸血', group: '戦闘', target: '武器', text: '攻撃と回復を結びつけるタイプの戦闘向けエンチャントです。近接戦闘で生存力を上げたい人向けです。' },
  { name: '不死特効', group: '戦闘', target: '武器', text: 'ゾンビやスケルトンなど、不死系の敵に強くなるタイプのエンチャントです。夜や洞窟探索で役立ちます。' },
  { name: '虫殺し', group: '戦闘', target: '武器', text: 'クモなど虫系の敵に強くなるタイプのエンチャントです。特定 Mob 対策として使います。' },
  { name: '点火の一撃', group: '戦闘', target: '武器', text: '攻撃時に炎や点火に関係する効果を与えるエンチャントです。周囲への延焼や状況には注意してください。' },
];

export function CustomEnchantsSection() {
  return (
    <SectionShell
      eyebrow="Enchant"
      title="カスタムエンチャント"
      intro="カスタムエンチャントは、BetterSurvival 独自の強化要素です。通常エンチャントとは別に、専用テーブルから道具や装備へ付与します。"
      scope="player"
    >
      <FetchedWikiImage
        src={customEnchantImage}
        alt="カスタムエンチャント管理 UI"
        caption="カスタムエンチャント管理 UI。中央にエンチャントテーブル、下部に本と素材枠が見える画面です。"
      />

      <h3>カスタムエンチャントとは</h3>
      <ul className="wiki-bullets">
        <li>通常の Minecraft エンチャントとは別に、BetterSurvival が追加する独自エンチャントです。</li>
        <li>カスタムエンチャントテーブルを使って、道具や装備に付与します。</li>
        <li>一部のエンチャントにはレベルがあり、レベルが高いほど効果が強くなる場合があります。</li>
        <li>効果はエンチャントごとに違うため、付与前に UI の説明を確認してください。</li>
      </ul>

      <h3>使い方の流れ</h3>
      <div className="wiki-feature-list">
        <article className="wiki-feature-row">
          <h4>1. カスタムエンチャントテーブルを用意</h4>
          <p>まず、エンチャントテーブルとラピスラズリから専用テーブルを作ります。普通のエンチャントテーブルとは別の専用設備として扱われます。</p>
        </article>
        <article className="wiki-feature-row">
          <h4>2. テーブルを設置して右クリック</h4>
          <p>設置したテーブルを右クリックすると、カスタムエンチャント用の UI が開きます。</p>
        </article>
        <article className="wiki-feature-row">
          <h4>3. 道具と素材を入れる</h4>
          <p>強化したい道具や装備を入れ、必要な素材や条件を確認します。</p>
        </article>
        <article className="wiki-feature-row">
          <h4>4. 付与したい効果を選ぶ</h4>
          <p>目的に合うエンチャントを選んで付与します。初心者は採掘・回収系から試すと分かりやすいです。</p>
        </article>
      </div>

      <h3>カテゴリ別の見方</h3>
      <div className="wiki-feature-list">
        {enchantCategories.map((category) => (
          <article className="wiki-feature-row wiki-guide-card" key={category.title}>
            <div className="wiki-guide-head">
              <span className="wiki-guide-tag">{category.target}</span>
              <h4>{category.title}</h4>
            </div>
            <p>{category.summary}</p>

            <div className="wiki-mini-section">
              <strong>含まれるエンチャント</strong>
              <ul className="wiki-chip-list">
                {category.enchants.map((enchant) => <li key={enchant}>{enchant}</li>)}
              </ul>
            </div>

            <div className="wiki-mini-section wiki-mini-section-tip">
              <strong>初心者向けポイント</strong>
              <p>{category.beginnerTip}</p>
            </div>
          </article>
        ))}
      </div>

      <h3>各種エンチャントの個別紹介</h3>
      <div className="wiki-enchant-grid">
        {enchantDetails.map((enchant) => (
          <article className="wiki-enchant-card" key={enchant.name}>
            <span className="wiki-guide-tag">{enchant.group}</span>
            <h4>{enchant.name}</h4>
            <p className="wiki-enchant-target">主な対象: {enchant.target}</p>
            <p>{enchant.text}</p>
          </article>
        ))}
      </div>

      <h3>注意点</h3>
      <ul className="wiki-bullets">
        <li>大事な装備に付ける前に、まず予備の道具で試すのがおすすめです。</li>
        <li>カスタムエンチャントは通常エンチャントと違う仕様を持つ場合があります。</li>
        <li>サーバー設定で機能が無効になっていると、テーブルや効果が使えないことがあります。</li>
        <li>効果の組み合わせによっては使い方が変わるため、ゲーム内 UI の説明をよく読んでください。</li>
      </ul>
    </SectionShell>
  );
}
