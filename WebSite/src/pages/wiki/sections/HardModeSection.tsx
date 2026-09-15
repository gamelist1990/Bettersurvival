import { SectionShell } from '../components/SectionShell';
import { CommandBox } from '../components/CommandBox';

const heatLevels = [
  { level: 1, name: '警戒', detail: '真クラの基本状態。通常のサバイバルより手強い敵AIが戦闘へ加わります。' },
  { level: 2, name: '激化', detail: '特殊行動が増え、敵ごとの攻撃パターンを意識した立ち回りが必要になります。' },
  { level: 3, name: '強敵', detail: 'エリート個体や派生個体が戦場へ加わり、集団戦の危険度が上昇します。' },
  { level: 4, name: '灼熱', detail: '一部Mobの追加装備、体力補正、水中移動補正、特殊効果が有効になります。' },
  { level: 5, name: '極限', detail: '最高難度。さらに強い体力補正が加わり、ベッドによる夜のスキップもできません。' },
];

const mobFamilies = [
  {
    image: '/images/wiki/truecrafter/zombie.png',
    name: 'ゾンビ系',
    members: 'ゾンビ、ハスク、ゾンビ村人、ドラウンド、ゾンビピグリン',
    behavior: '段差を越える移動補助、跳躍攻撃、槍型やブルート型への派生が追加されます。ゾンビピグリンは周囲のプレイヤーを積極的に追跡します。',
  },
  {
    image: '/images/wiki/truecrafter/skeleton.png',
    name: 'スケルトン系',
    members: 'スケルトン、ストレイ、ボグド、パーチド、ウィザースケルトン',
    behavior: '距離に応じて弓と近接武器を切り替え、射線や退路を確保しながら戦います。エリートの矢は通常より危険ですが、盾で防ぐと停止できます。',
  },
  {
    image: '/images/wiki/truecrafter/spider.png',
    name: 'クモ系',
    members: 'クモ、洞窟グモ',
    behavior: '跳躍に加えてWeb弾や毒弾を発射します。近距離では拡散するため、狭い通路で接近を許さないことが重要です。',
  },
  {
    image: '/images/wiki/truecrafter/creeper.png',
    name: 'クリーパー',
    members: 'クリーパー、追跡採掘型',
    behavior: '停止状態から攻撃を準備し、壁越しでも近距離のプレイヤーへ大きな爆発を狙います。同じ個体が繰り返し地形を破壊する回数には上限があります。',
  },
  {
    image: '/images/wiki/truecrafter/slime.png',
    name: 'スライム系',
    members: 'スライム、マグマキューブ',
    behavior: 'プレイヤーへ敵対している間に膨張カウンターが進み、一定時間ごとに大型化します。周囲にスライムが増えすぎると膨張は停止します。',
  },
  {
    image: '/images/wiki/truecrafter/witch.png',
    name: '襲撃者・魔法系',
    members: 'ウィッチ、ヴィンディケーター、エヴォーカー、ピリジャー',
    behavior: '回避テレポート、強歩行、ファング、騎乗召喚、強化装備などを使います。単体ではなく、周囲の敵との連携を優先して対処してください。',
  },
  {
    image: '/images/wiki/truecrafter/piglin.png',
    name: 'ピグリン系',
    members: '剣型、槍型、クロスボウ型、ピグリンブルート',
    behavior: '武器ごとに能力と間合いが異なります。回復や火炎耐性を使い、ブルートは地面を伝う衝撃波で遠距離まで攻撃します。',
  },
  {
    image: '/images/wiki/truecrafter/enderman.png',
    name: 'エンダーマン系',
    members: '通常、中立、外縁追跡者、エンダージーロット',
    behavior: '視線とかぼちゃを判定する通常個体に加え、地形を越えて追跡する外縁個体が出現します。ジーロットはドラゴン戦で弾幕を展開します。',
  },
];

export function HardModeSection() {
  return (
    <SectionShell
      eyebrow="TrueCrafter HardMode"
      title="真クラモード 完全攻略"
      intro="敵が考え、追い詰め、地形さえ越えてくる高難易度サバイバル。熱量、強化Mob、特殊弾、ウィザーとエンダードラゴンの攻略要点をまとめます。"
      scope="op"
    >
      <div className="truecrafter-alert">
        <strong>通常のハードモードとは別物です</strong>
        <p>敵の数値を上げるだけでなく、独自AI、武器切替、回避、追尾弾、地形突破、ボスの複数フェーズを追加します。安全だった壁や高所が必ずしも安全とは限りません。</p>
      </div>

      <h3>管理者向けクイックスタート</h3>
      <p>コマンド操作はOP権限が必要です。有効化すると、すでに読み込まれている対象Mobにも真クラの能力が適用されます。</p>
      <CommandBox command="/hardmode list" description="真クラの有効状態と現在の熱量を確認します。" op />
      <CommandBox command="/hardmode truecrafter enabled" description="真クラモードを有効化します。" op />
      <CommandBox command="/hardmode truecrafter disabled" description="真クラモードを無効化し、管理中の属性強化を対象Mobから外します。" op />
      <CommandBox command="/hardmode truecrafter heat &lt;1-5&gt;" description="火の熱量を1から5の範囲で直接設定します。" op />

      <h3>火の熱量</h3>
      <p>熱量は世界全体の危険度を示す1から5の段階です。状態はサーバー再起動後も保存されます。高い熱量へ変更する前に、装備、退路、盾、回復手段を準備してください。</p>
      <div className="truecrafter-heat-grid">
        {heatLevels.map((heat) => (
          <article className={`truecrafter-heat-card heat-${heat.level}`} key={heat.level}>
            <span className="truecrafter-heat-level">HEAT {heat.level}</span>
            <strong>{heat.name}</strong>
            <p>{heat.detail}</p>
          </article>
        ))}
      </div>

      <h3>不吉な焚き火</h3>
      <ol className="truecrafter-steps">
        <li><strong>作成</strong><span>焚き火と石の剣をクラフト欄へ置く不定形レシピで作成します。</span></li>
        <li><strong>設置</strong><span>通常の焚き火と同じように、管理しやすい場所へ設置します。</span></li>
        <li><strong>熱量変更</strong><span>設置した焚き火をOPが右クリックすると、熱量1から5を選択する専用UIが開きます。一般プレイヤーは変更できません。</span></li>
        <li><strong>回収</strong><span>破壊すると、不吉な焚き火の専用アイテムとして戻ります。</span></li>
      </ol>

      <h3>冒険前に知っておくこと</h3>
      <div className="truecrafter-guide-grid">
        <article>
          <span>🛡</span>
          <h4>盾を携帯する</h4>
          <p>エリート系の矢は強力ですが、盾で正面から受けると停止できます。遠距離戦では盾の耐久値にも注意してください。</p>
        </article>
        <article>
          <span>⛏</span>
          <h4>壁だけに頼らない</h4>
          <p>追跡型の敵は自然ブロックを掘り、穴には一時的な足場を作ります。葉やツタに阻まれても登ってくるため、複数の退路を用意してください。</p>
        </article>
        <article>
          <span>↔</span>
          <h4>間合いを変える</h4>
          <p>一部の敵は距離に応じて弓と近接武器を切り替えます。同じ距離に留まらず、攻撃の予備動作を見て移動してください。</p>
        </article>
        <article>
          <span>👥</span>
          <h4>集団を分断する</h4>
          <p>敵は複数いても個別に追跡、登攀、攻撃を続けます。狭い場所へ全員を誘導せず、射線を切って一体ずつ処理するのが安全です。</p>
        </article>
      </div>

      <h3>強化されるMob</h3>
      <p>真クラではMobごとに異なる行動が追加されます。見た目だけでなく、武器、距離、予備動作から次の攻撃を判断してください。</p>
      <div className="truecrafter-mob-list">
        {mobFamilies.map((family) => (
          <article key={family.name}>
            <span className="truecrafter-mob-icon">
              <img src={family.image} alt="" width="32" height="32" loading="lazy" />
            </span>
            <div>
              <h4>{family.name}</h4>
              <small>{family.members}</small>
              <p>{family.behavior}</p>
            </div>
          </article>
        ))}
      </div>

      <h3>エリート・派生個体</h3>
      <ul className="wiki-bullets">
        <li><strong>ゾンビブルート</strong>: 通常ゾンビより攻撃的な派生。跳躍攻撃後も継続してプレイヤーを追います。</li>
        <li><strong>エリートスケルトン / ストレイ / ボグド</strong>: 強化装備、専用矢、近接と遠距離の切替、攻撃休止周期を持ちます。</li>
        <li><strong>パーチド</strong>: スケルトン系の派生で、固有の後退行動と効果音を持ちます。</li>
        <li><strong>外縁追跡エンダーマン</strong>: プレイヤーを長距離から追跡し、地形破壊と仮設足場で接近します。</li>
        <li><strong>ウィザーの騎士 / しもべ</strong>: ウィザー戦で召喚される専用個体です。準備時間、専用装備、弓または近接攻撃を持ちます。</li>
      </ul>

      <h3>地形を使った追跡</h3>
      <div className="truecrafter-alert truecrafter-alert-warning">
        <strong>敵は地形で完全には止まりません</strong>
        <p>対象の敵は48ブロック以内のプレイヤーを追跡し、進路を塞ぐ自然ブロックの採掘、真下への掘削、上方向への移動、穴を越える橋の設置を行います。</p>
      </div>
      <ul className="wiki-bullets">
        <li>前方の足元、胴体、頭上にある破壊可能なブロックを取り除きます。</li>
        <li>葉、ツタ、苔カーペット、マングローブの根に妨害された場合も、取り除いて上へ進みます。</li>
        <li>複数のMobが押し合っていても、それぞれの登攀判定は継続します。</li>
        <li>穴や段差では、バイオームに応じた苔丸石などの一時ブロックを足場として配置します。</li>
        <li>一時ブロックは敵が乗っていない時間が続くと自動消滅し、アイテムとしてドロップしません。</li>
        <li>チェスト、鉱石、主要な建築用ブロックなど、保護対象に分類されたブロックは破壊しません。</li>
      </ul>

      <h3>特殊な投射物</h3>
      <div className="truecrafter-table-wrap">
        <table className="truecrafter-table">
          <thead>
            <tr><th>種類</th><th>主な効果</th><th>対処</th></tr>
          </thead>
          <tbody>
            <tr><td>Web弾</td><td>移動速度と採掘速度を低下させ、クモの巣の演出を発生させます。</td><td>横移動で避け、閉所でクモへ接近しすぎない。</td></tr>
            <tr><td>毒弾</td><td>毒と移動速度低下を付与します。</td><td>食料と回復手段を確保し、連続被弾を避ける。</td></tr>
            <tr><td>Void弾</td><td>暗闇とダメージを与えます。</td><td>予備動作を見たら射線を切る。</td></tr>
            <tr><td>ブルート衝撃波</td><td>地面を進み、範囲内へ大ダメージとノックバックを与えます。</td><td>正面から離れ、横方向か高低差で回避する。</td></tr>
            <tr><td>ジーロット弾</td><td>加速しながら対象を追尾します。</td><td>直線で逃げず、方向転換して誘導を外す。</td></tr>
            <tr><td>ドラゴン追尾弾</td><td>長距離追尾し、着弾地点へ継続ダメージを与えるブレス球を生成します。</td><td>着弾地点から離れ、同じ場所へ留まらない。</td></tr>
            <tr><td>ウィザー追尾弾</td><td>ウィザー戦でプレイヤーを追尾します。</td><td>遮蔽物と横移動を組み合わせる。</td></tr>
            <tr><td>エリート矢</td><td>落下挙動や専用効果を持つ強化矢です。</td><td>盾で受けると矢を停止できます。</td></tr>
          </tbody>
        </table>
      </div>

      <h3>ウィザー攻略</h3>
      <p>真クラのウィザーは最大体力600の複数フェーズ戦です。体力75%と50%を目安に戦況が変化し、騎士、しもべ、追尾弾、突進、雷撃、トラップレーザーを組み合わせます。</p>
      <div className="truecrafter-boss-grid">
        <article>
          <span>PHASE 1</span>
          <h4>射線を管理する</h4>
          <p>通常攻撃に加えて専用行動が始まります。広く平坦な場所を確保し、追尾弾を味方へ誘導しないよう散開します。</p>
        </article>
        <article>
          <span>PHASE 2</span>
          <h4>召喚個体を処理する</h4>
          <p>ウィザーの騎士やしもべが戦場へ加わります。ボスだけを攻撃せず、射線を作る騎士から優先して数を減らします。</p>
        </article>
        <article>
          <span>PHASE 3</span>
          <h4>突進と範囲攻撃を避ける</h4>
          <p>左右へのサイドダッシュ、近距離雷撃、レーザー領域が重なります。床の警告演出を確認し、一方向へ逃げ続けないでください。</p>
        </article>
      </div>
      <ul className="wiki-bullets">
        <li>雷撃には約30tickの警告があり、命中するとダメージとウィザー効果を受けます。</li>
        <li>壁際では突進の回避先を失います。中央付近で戦い、退路を複数確保してください。</li>
        <li>戦闘対象がいない状態が長く続くと、専用の消滅処理と頭蓋骨ドロップが発生します。</li>
      </ul>

      <h3>エンダードラゴン攻略</h3>
      <p>エンドクリスタルが残っている間はドラゴンへ十分な攻撃を通せません。クリスタルを破壊した後は第二形態へ進み、ジーロット、仮設足場、突進、照準の目、追尾弾、落雷柱が戦場を分断します。</p>
      <div className="truecrafter-boss-grid truecrafter-dragon-grid">
        <article>
          <span>STEP 1</span>
          <h4>クリスタルを破壊</h4>
          <p>通常戦と同じく塔を回りますが、地上の敵にも注意が必要です。全員が同じ塔へ集まらないよう役割を分けます。</p>
        </article>
        <article>
          <span>STEP 2</span>
          <h4>ジーロットを優先</h4>
          <p>エンダージーロットは眼球表示と弾幕を持ちます。放置すると回避空間が狭くなるため、出現を確認したら処理します。</p>
        </article>
        <article>
          <span>STEP 3</span>
          <h4>地面の警告を見る</h4>
          <p>突進と落雷柱は広い範囲を移動させます。照準演出や警告を見て、ブレス球が残る場所へ逃げ込まないようにします。</p>
        </article>
      </div>
      <ul className="wiki-bullets">
        <li>ドラゴンの突進は長距離を高速で進み、進路上のプレイヤーを狙います。正面ではなく横へ避けてください。</li>
        <li>追尾弾の着弾地点にはブレス球が残り、一定間隔で周囲へダメージを与えます。</li>
        <li>落雷柱には事前警告があり、命中するとダメージに加えて上方向へ打ち上げられます。</li>
        <li>落下対策として低速落下、エンダーパール、水入りバケツなどを準備すると安全です。</li>
      </ul>

      <h3>設定の保存と無効化</h3>
      <ul className="wiki-bullets">
        <li>有効状態と熱量は<code>plugins/BetterSurvival/hardmode.yml</code>へ自動保存されます。</li>
        <li>サーバー再起動後も設定は維持されます。通常は設定ファイルを直接編集する必要はありません。</li>
        <li>無効化時はプラグインが管理する属性補正を外し、専用のボス補助エンティティや背中の武器表示を後片付けします。</li>
        <li>Mobが別ワールドの古い対象を保持していても、安全に処理を終了するよう設計されています。</li>
      </ul>

      <h3>再現元と実装方式</h3>
      <p>TrueCrafterMode v3.0.0-beta 12のcommand、function、NBTによる処理を参照し、Paper APIのイベント、属性、エンティティ、投射物、PersistentDataContainer、スケジューラを使って再実装しています。データパックの導入や<code>/reload</code>には依存しません。</p>
      <p>発動条件、主要な状態遷移、攻撃の流れ、パーティクル、サウンドを可能な限り合わせていますが、Minecraft本体のAIとデータパック固有のコマンド実行順により、細かな挙動が完全には一致しない場合があります。</p>
      <p className="truecrafter-source">
        再現元: <a href="https://github.com/Chuzume/True-Crafter-Mode" target="_blank" rel="noreferrer">True-Crafter-Mode</a>
      </p>
    </SectionShell>
  );
}
