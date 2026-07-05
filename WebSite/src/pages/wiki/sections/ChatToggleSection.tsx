import { SectionShell } from '../components/SectionShell';
import { ImagePlaceholder } from '../components/ImagePlaceholder';
import { CommandBox } from '../components/CommandBox';

export function ChatToggleSection() {
  return (
    <SectionShell
      eyebrow="Player"
      title="Chat Toggle"
      intro="全体チャットを自分の画面に流さないようにする toggle です。集中したい時や配信中の情報整理に便利です。"
      scope="player"
    >
      <ImagePlaceholder
        id="chat-toggle-hero"
        label="Chat をオフにした状態のチャット欄"
        hint="通常時とオフ時の見え方が比較できるスクショだと親切"
      />

      <h3>できること</h3>
      <ul className="wiki-bullets">
        <li>全体チャットの表示を自分の画面だけ抑制</li>
        <li>PM やシステム通知は引き続き受信</li>
        <li>切替はいつでも即時反映</li>
      </ul>

      <h3>コマンド</h3>
      <CommandBox command="/toggle chat" description="全体チャットの表示を反転。" />
      <CommandBox command="/toggle chat off" description="全体チャットを非表示。" />

      <ImagePlaceholder
        id="chat-toggle-notice"
        label="Chat オフ中に発言しようとしたときの通知"
        hint="送信不可メッセージが出るならその見え方"
        aspect="4/3"
      />

      <h3>使いどころ</h3>
      <p>ダンジョン攻略や録画・配信で会話をカットしたい時、通知を一時的に止めたい時に。</p>
    </SectionShell>
  );
}
