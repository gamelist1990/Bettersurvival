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
        id="admin-toggle-command"
        label="/toggle 機能一覧のスクショ"
        hint="登録済み機能キーと ON/OFF 状態が分かる画面"
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
          'OP 権限を持ったアカウントでサーバーにログイン',
          '/toggle を実行して登録済み機能と状態を確認',
          '/toggle <機能キー> で該当機能を切り替え',
          '必要に応じて /toggle customenchant や /toggle landprotect のように対象を指定',
        ]}
      />

      <h3>代表的なコマンド</h3>
      <CommandBox op command="/toggle" description="全機能の現在状態を一覧表示。" />
      <CommandBox op command="/toggle customenchant" description="カスタムエンチャントテーブル機能を切り替え。" />
      <CommandBox op command="/toggle landprotect" description="土地保護コア機能を切り替え。" />
      <CommandBox op command="/toggle webmap" description="軽量 Web マップと ChunkGen を切り替え。" />

      <ImagePlaceholder
        id="admin-toggle-result"
        label="toggle 実行後の反映結果"
        hint="対象機能が ON/OFF されたことが分かるメッセージや GUI"
        aspect="4/3"
      />

      <h3>注意点</h3>
      <ul className="wiki-bullets">
        <li>このページは Loader に登録されている機能を基準にしています。</li>
        <li>カスタムブロック系やエンチャント系も /toggle の管理対象に含まれます。</li>
        <li>本番運用中の切り替えはアナウンス後に行うと混乱を防げます。</li>
      </ul>
    </SectionShell>
  );
}
