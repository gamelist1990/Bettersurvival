import { SectionShell } from '../components/SectionShell';
import { ImagePlaceholder } from '../components/ImagePlaceholder';
import { CommandBox } from '../components/CommandBox';

export function MessageToggleSection() {
  return (
    <SectionShell
      eyebrow="Player"
      title="Message Toggle"
      intro="プライベートメッセージ（/msg, /w 系）の受信をオン／オフにする toggle です。"
      scope="player"
    >
      <ImagePlaceholder
        id="msg-toggle-hero"
        label="PM を受信している通常時のチャット欄"
        hint="PM フォーマット (差出人 → 自分) がわかるカット"
      />

      <h3>できること</h3>
      <ul className="wiki-bullets">
        <li>特定チャンネル外からの PM 受信を停止</li>
        <li>送信者には『受信していません』などのフィードバック</li>
        <li>スタッフからの重要通知は例外扱いされる場合あり（実装依存）</li>
      </ul>

      <h3>コマンド</h3>
      <CommandBox command="/toggle msg" description="PM 受信可否を反転。" />
      <CommandBox command="/toggle msg off" description="PM を非受信に。" />

      <ImagePlaceholder
        id="msg-toggle-blocked"
        label="送信側に返るブロックメッセージ"
        hint="送信できなかった時の表示"
        aspect="4/3"
      />

      <h3>使いどころ</h3>
      <p>迷惑 PM を一時的にシャットしたい時、集中して作業したい時に。</p>
    </SectionShell>
  );
}
