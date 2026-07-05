import { SectionShell } from '../components/SectionShell';
import { ImagePlaceholder } from '../components/ImagePlaceholder';
import { CommandBox } from '../components/CommandBox';

export function TpaToggleSection() {
  return (
    <SectionShell
      eyebrow="Player"
      title="TPA Toggle"
      intro="他プレイヤーからのテレポートリクエスト（/tpa 系）を、自分が受け取るかどうかを切り替える toggle です。"
      scope="player"
    >
      <ImagePlaceholder
        id="tpa-toggle-hero"
        label="TPA リクエストを受け取っている場面"
        hint="通知チャットまたはアクションバーの表示例"
      />

      <h3>できること</h3>
      <ul className="wiki-bullets">
        <li>TPA リクエストの受信可否を切り替え</li>
        <li>オフ時は送信者側にも『受付不可』の旨が伝わる</li>
        <li>再オンにすると即座に受信可能</li>
      </ul>

      <h3>コマンド</h3>
      <CommandBox command="/toggle tpa" description="TPA 受信可否を反転。" />
      <CommandBox command="/toggle tpa off" description="TPA を受け付けない。" />

      <ImagePlaceholder
        id="tpa-toggle-blocked"
        label="TPA オフ中に他プレイヤーが送信した時の応答"
        hint="送信者側の失敗メッセージ"
        aspect="4/3"
      />

      <h3>使いどころ</h3>
      <p>ソロ探索や建築中に呼び出しを避けたい時、AFK 中の意図しないテレポートを防ぎたい時に。</p>
    </SectionShell>
  );
}
