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
        hint="チャットに表示されるメッセージ"
      />

      <h3>できること</h3>
      <ul className="wiki-bullets">
        <li>TPA リクエストの受信可否を切り替え</li>
        <li>再オンにすると即座に受信可能</li>
      </ul>

      <h3>コマンド</h3>
      <CommandBox command="/toggle" description="個人用トグル UI を開き、TPA アイコンをクリックして ON/OFF を切り替えます。" />
      <CommandBox command="/tpa ui" description="TPA 用の UI を開きます。" />
      <CommandBox command="/tpa -r <プレイヤー名>" description="相手にテレポートリクエストを送ります (/tpa <名前> でも同じ)。" />
      <CommandBox command="/tpa -a <プレイヤー名>" description="受信したリクエストを承認します。" />
      <CommandBox command="/tpa -d <プレイヤー名>" description="受信したリクエストを拒否します。" />
      <CommandBox command="/tpa -l" description="受信中のリクエスト一覧を表示します。" />

      <h3>使いどころ</h3>
      <p>ソロ探索や建築中に呼び出しを避けたい時、AFK 中の意図しないテレポートを防ぎたい時に。</p>
    </SectionShell>
  );
}
