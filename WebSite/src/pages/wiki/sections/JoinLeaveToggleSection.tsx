import { SectionShell } from '../components/SectionShell';
import { ImagePlaceholder } from '../components/ImagePlaceholder';
import { CommandBox } from '../components/CommandBox';

export function JoinLeaveToggleSection() {
  return (
    <SectionShell
      eyebrow="Player"
      title="Join / Leave Toggle"
      intro="プレイヤーの参加・退出メッセージを自分のチャットに表示するかどうかを切り替える toggle です。"
      scope="player"
    >
      <ImagePlaceholder
        id="joinleave-toggle-hero"
        label="参加・退出通知が流れるチャット欄"
        hint="通常表示とオフ表示の比較"
      />

      <h3>できること</h3>
      <ul className="wiki-bullets">
        <li>Join/Leave 通知の表示制御</li>
        <li>大人数サーバーでノイズを減らす</li>
        <li>個人設定のため他プレイヤーには影響しない</li>
      </ul>

      <h3>コマンド</h3>
      <CommandBox command="/toggle joinleave" description="参加・退出通知の表示を反転。" />
      <CommandBox command="/toggle joinleave off" description="通知を非表示に。" />

      <ImagePlaceholder
        id="joinleave-toggle-off"
        label="Join/Leave オフ時のチャット欄"
        hint="通知が抑制されている状態"
        aspect="4/3"
      />

      <h3>使いどころ</h3>
      <p>建築配信、大人数イベント、AFK 放置時に。</p>
    </SectionShell>
  );
}
