import { SectionShell } from '../components/SectionShell';
import { CommandBox } from '../components/CommandBox';

export function HardModeSection() {
  return (
    <SectionShell eyebrow="HardMode" title="真クラモード" intro="Paper APIで独自AIと戦闘能力を再現した、設定不要の高難易度モードです。" scope="admin">
      <h3>有効化と無効化</h3>
      <CommandBox command="/hardmode list" description="利用できるHardMode機能と現在の状態を表示します。" />
      <CommandBox command="/hardmode truecrafter enabled" description="真クラを有効化します。既に読み込まれている敵にも即時適用されます。" />
      <CommandBox command="/hardmode truecrafter disabled" description="真クラを無効化し、読み込まれている敵からプラグインの属性強化を外します。" />
      <CommandBox command="/hardmode truecrafter heat &lt;1-5&gt;" description="熱量を直接変更します。不吉な焚き火のUIからも変更できます。" />

      <h3>熱量と不吉な焚き火</h3>
      <p>熱量は1から5までで、初期値は1です。熱量2から特殊AI、3から強化個体、4から敵の体力強化と強化武器、5ではさらに体力が増加してベッドによる時間スキップが無効になります。</p>
      <p>不吉な焚き火は、焚き火と石の剣をクラフト欄へ置く不定形レシピで作成します。設置後に右クリックすると、熱量1～5を選ぶ専用UIが開きます。破壊すると専用アイテムのまま戻ります。</p>

      <h3>収録内容</h3>
      <ul className="wiki-bullets">
        <li>ゾンビ、スケルトン、クリーパー、クモ、洞窟グモ、ドラウンド、ハスク、ピグリン、略奪者系などの独自AIと特殊攻撃。</li>
        <li>ゾンビブルート、エリートスケルトン、エリートストレイ、エリートボグド、ウィザー騎士などの強化個体。</li>
        <li>ウィザーとエンダードラゴンの複数フェーズ、突進、誘導弾、トラップレーザー、召喚攻撃。</li>
        <li>跳躍、突進、回避テレポート、遠距離攻撃、衝撃波などの敵別AI。</li>
        <li>Web弾、毒弾、Void弾、強化装備、パーティクルと効果音。</li>
        <li>クモ、洞窟グモ、ウィッチ、ヴィンディケーター、ピグリンブルート、エンダーマンなどの固有行動。</li>
        <li>追跡中の敵による自然ブロックの採掘と、穴を越えるための足場構築。チェスト、鉱石、建築用の主要ブロックは破壊対象外です。</li>
        <li>ウィザーが戦闘外のまま一定時間経過した時の専用消滅演出と頭蓋骨ドロップ。</li>
      </ul>
      <p>細かな設定ファイル操作は不要です。状態は<code>plugins/BetterSurvival/hardmode.yml</code>へ自動保存され、サーバー再起動後も維持されます。</p>

      <h3>再現元</h3>
      <p>提供されたTrueCrafterModeの挙動を調査し、Paper APIのイベント、属性、エンティティ、投射物、PersistentDataContainer、スケジューラで再実装しています。データパック、functionコマンド、reloadコマンドには依存しません。</p>
    </SectionShell>
  );
}
