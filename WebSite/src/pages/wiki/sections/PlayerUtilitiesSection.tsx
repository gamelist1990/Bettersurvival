import { SectionShell } from '../components/SectionShell';
import { ImagePlaceholder } from '../components/ImagePlaceholder';
import { CommandBox } from '../components/CommandBox';

const utilityGroups = [
  {
    title: '採掘・農業を楽にする機能',
    summary: '資源集め、木こり、農業、釣り、動物の世話を楽にする機能です。初心者が最初に覚えると便利です。',
    items: [
      { name: 'TreeMine', toggle: 'treemine', text: '木をまとめて伐採・破壊します。説明上はスニーク必須なので、誤爆を防ぎながら使えます。' },
      { name: 'OreMine', toggle: 'oremine', text: '近接する鉱石をまとめて破壊します。鉱脈を掘るときに便利ですが、スニーク必須なので使うタイミングを意識してください。' },
      { name: 'AutoFeed', toggle: 'autofeed', text: '餌を与えると周辺の動物にも自動で餌を与えます。動物繁殖をまとめて行いたいときに便利です。' },
      { name: 'AutoFishing', toggle: 'autofishing', text: '釣り開始後、動いたり視点を大きく変えるまで自動で釣りを続けます。放置に近い釣りを補助します。' },
      { name: 'AnythingFeed', toggle: 'anythingfeed', text: '非繁殖動物に任意の食料で反応するようにします。通常より自由な餌やり挙動になります。' },
      { name: 'AutoPlant', toggle: 'autoplant', text: 'オフハンドに植えたいアイテムを持ちながら耕した土の近くに行くと、自動で植え・収穫します。農地管理を楽にします。' },
      { name: 'AirDash', toggle: 'airdash', text: '空中でジャンプキーを再入力するとエアダッシュします。移動や戦闘の立ち回りを広げます。' },
    ],
  },
  {
    title: 'チェスト・ショップ・保管機能',
    summary: 'アイテムを守る、整理する、売買する、死亡時のロストを防ぐための機能です。拠点生活の基本になります。',
    items: [
      { name: 'ChestLock', toggle: 'chestlock', text: 'チェスト保護を有効/無効にします。破壊・移動・取得などを制限し、大事なアイテムを守ります。' },
      { name: 'ChestShop', toggle: 'chestshop', text: '看板でチェストをショップ化します。「>>Shop 名前」のような形式でショップを作る機能です。' },
      { name: 'ChestSort', toggle: 'chestsort', text: 'スニーク + 木の棒でチェスト内を整理します。倉庫が散らかりやすい初心者にも便利です。' },
      { name: 'SharedStorage', toggle: 'sharedstorage', text: '主チェストと sub チェストを使った共有ストレージです。複数の保管場所を連携させたいときに使います。' },
      { name: 'DeathChest', toggle: 'deathchest', text: '死亡時に所持品を死亡地点付近のラージチェストへ保管し、座標を通知します。アイテムロスト対策になります。' },
    ],
  },
  {
    title: '移動・生活・コミュニティ機能',
    summary: '拠点移動、プレイヤー同士の移動、パーティー、Web 連携など、サーバー生活を便利にする機能です。',
    items: [
      { name: 'Home', toggle: 'home', text: '/home で登録済みの家へ移動します。登録は最大 3 個までです。' },
      { name: 'TPA', toggle: 'tpa', text: '他プレイヤーへテレポートリクエストを送る機能です。無効にすると受信拒否できます。' },
      { name: 'Party', toggle: 'party', text: 'パーティー、またはギルド風のグループ機能です。/party や /p から利用します。' },
      { name: 'WarpStone', toggle: 'warpstone', text: 'エンダーパール × 石レンガで作るワープストーンです。Waystones 風のワープと演出があります。' },
      { name: 'WebMap', toggle: 'webmap', text: '軽量な Web マップと ChunkGen を有効/無効にします。ブラウザからワールド確認を行う機能です。' },
      { name: 'WebService', toggle: 'webservice', text: 'ホームページ、ログイン、プロフィール機能を有効/無効にします。Web 側のコミュニティ機能に関係します。' },
    ],
  },
  {
    title: '保護・大会・特別システム',
    summary: '土地保護、リング、トーナメントなど、サーバー内イベントや拠点防衛に関係する機能です。',
    items: [
      { name: 'LandProtect', toggle: 'landprotect', text: 'ロデストーン + ダイヤで作る土地保護コアを有効/無効にします。拠点や土地を守る中心機能です。' },
      { name: 'Tournament', toggle: 'tournament', text: 'リングを使った頂点決定戦、つまりトーナメント大会機能です。/tournament から利用します。' },
      { name: 'CopperGolem', toggle: 'coppergolem', text: 'カッパーゴーレムの召喚と作物採取 AI を有効/無効にします。農業や拠点作業を補助します。' },
      { name: 'BetterMenu', toggle: 'bettermenu', text: '木の斧 GUI ツールの生成・起動を有効/無効にします。便利メニュー系の入口です。' },
      { name: 'KeepAliveGuard', toggle: 'keepaliveguard', text: 'OP の keepalive timeout kick を可能な範囲でキャンセルします。管理者向け寄りの安定化機能です。' },
    ],
  },
  {
    title: 'Bedrock / Geyser 対応機能',
    summary: '統合版プレイヤーや Geyser 環境向けの補助機能です。Java 版と統合版が混ざるサーバーで役立ちます。',
    items: [
      { name: 'BedrockSkin', toggle: 'bedrockskin', text: 'Bedrock ユーザーのスキンを Java クライアントに自動反映します。' },
      { name: 'Geyser金床', toggle: 'geyseranvil', text: 'Geyser / Bedrock 対応の金床 UI を有効/無効にします。' },
      { name: 'Geyser鍛冶台', toggle: 'geysersmithing', text: 'Geyser / Bedrock 対応の鍛冶台 UI を有効/無効にします。' },
      { name: 'OfflineAccess', toggle: 'offlineaccess', text: 'オフラインアカウントのログインを許可/拒否します。管理方針に関わる機能です。' },
    ],
  },
];

export function PlayerUtilitiesSection() {
  return (
    <SectionShell
      eyebrow="Utility"
      title="プレイヤー便利機能"
      intro="日常プレイで使う補助機能を、初心者でも分かるように「何ができるか」「いつ使うか」「どの toggle で管理されるか」に分けて説明します。"
      scope="player"
    >
      <ImagePlaceholder
        id="player-utilities-hero"
        label="便利機能の利用シーン"
        hint="TreeMine、ChestLock、Party、LandProtect、Home、TPA などが分かるスクリーンショット"
        aspect="16/9"
      />

      <h3>初心者が最初に覚えると良い機能</h3>
      <ul className="wiki-bullets">
        <li><strong>TreeMine / OreMine</strong>: 採掘や木こりを楽にします。スニーク条件を確認して使ってください。</li>
        <li><strong>ChestLock</strong>: 大事なアイテムを守る基本機能です。拠点を作ったら早めに確認してください。</li>
        <li><strong>DeathChest</strong>: 死亡時のアイテムロストを防ぐ助けになります。</li>
        <li><strong>Home / TPA</strong>: 移動を楽にする機能です。遠い場所へ行く前に使い方を覚えておくと便利です。</li>
        <li><strong>LandProtect</strong>: 土地や拠点を守る重要機能です。共同拠点では特に重要です。</li>
      </ul>

      <h3>機能カテゴリ</h3>
      <div className="wiki-feature-list">
        {utilityGroups.map((group) => (
          <article className="wiki-feature-row wiki-guide-card" key={group.title}>
            <div className="wiki-guide-head">
              <span className="wiki-guide-tag">Utility</span>
              <h4>{group.title}</h4>
            </div>
            <p>{group.summary}</p>
            <div className="wiki-utility-grid">
              {group.items.map((item) => (
                <div className="wiki-utility-card" key={item.name}>
                  <h5>{item.name}</h5>
                  <p>{item.text}</p>
                </div>
              ))}
            </div>
          </article>
        ))}
      </div>

      <h3>よく使う入口</h3>
      <CommandBox command="/toggle" description="利用できる機能の状態を確認します。" />
      <CommandBox command="/home" description="Home 機能が有効な場合、登録済みの家へ移動します。" />
      <CommandBox command="/tpa <player>" description="TPA 機能が有効な場合、対象プレイヤーへテレポート申請します。" />
      <CommandBox command="/party" description="Party 機能が有効な場合、パーティー関連操作を行います。" />

      <h3>注意点</h3>
      <ul className="wiki-bullets">
        <li>機能が使えない場合は、まず /toggle で対象機能が有効か確認してください。</li>
        <li>保護や共有に関係する機能は、他プレイヤーとの権限トラブルに注意してください。</li>
        <li>サーバー負荷に関わる機能は、必要な範囲で使ってください。</li>
      </ul>
    </SectionShell>
  );
}
