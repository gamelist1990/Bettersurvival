import { SectionShell } from '../components/SectionShell';
import { ImagePlaceholder } from '../components/ImagePlaceholder';
import { CommandBox } from '../components/CommandBox';
import { StepList } from '../components/StepList';

export function AdminToggleSection() {
  return (
    <SectionShell
      eyebrow="OP"
      title="/toggle 機能一覧"
      intro="Loader に登録されている実在の toggle 機能を、管理しやすいようカテゴリ別に整理しています。PvP や Chat のような未確認機能はここから外しています。"
      scope="op"
    >
      <ImagePlaceholder
        id="toggle-features-overview"
        label="/toggle 機能一覧"
        hint="登録済み機能キーと ON/OFF 状態"
      />

      <h3>管理対象のカテゴリ</h3>
      <ul className="wiki-bullets">
        <li><strong>採掘・農業</strong>: treemine、oremine、autofeed、autofishing、anythingfeed、autoplant</li>
        <li><strong>チェスト・保管</strong>: chestlock、chestshop、chestsort、sharedstorage、deathchest</li>
        <li><strong>カスタム要素</strong>: enchantsplit、customenchant、parallelfurnace、recycler、chunkloader、warpstone</li>
        <li><strong>ワールド・コミュニティ</strong>: home、tpa、party、landprotect、tournament、webmap、webservice</li>
        <li><strong>環境対応</strong>: bedrockskin、geyseranvil、geysersmithing、offlineaccess、keepaliveguard</li>
      </ul>

      <h3>使い方</h3>
      <StepList
        steps={[
          'サーバーにログイン (通常プレイヤーでも UI は開けます)',
          '/toggle を実行して個人用トグル UI を開き、状態を確認',
          'OP の場合は /toggle op でグローバル (サーバー全体) 用の管理 UI を開く',
          'UI 上でアイコンをクリックし、対象機能の ON / OFF を切り替える',
        ]}
      />

      <h3>代表的なコマンド</h3>
      <CommandBox command="/toggle" description="個人用トグル UI (自分に対する ON/OFF) を開きます。" />
      <CommandBox op command="/toggle op" description="OP 用のグローバル設定 UI を開きます。全プレイヤーに影響します。" />
      <CommandBox op command="/toggle normal" description="OP が通常プレイヤー用のデフォルト設定 UI を開きます。"/>
      <CommandBox op command="/chest op toggle" description="チェスト対象を見ながら ChestLock 機能のグローバル ON/OFF を切り替えます。" />

     

      <h3>注意点</h3>
      <ul className="wiki-bullets">
        <li>このページは Loader に登録されている機能を基準にしています。</li>
        <li>カスタムブロック系やエンチャント系も /toggle の管理対象に含まれます。</li>
        <li>本番運用中の切り替えはアナウンス後に行うと混乱を防げます。</li>
      </ul>
    </SectionShell>
  );
}
