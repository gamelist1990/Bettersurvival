import { SectionShell } from '../components/SectionShell';
import { ImagePlaceholder } from '../components/ImagePlaceholder';
import { CommandBox } from '../components/CommandBox';

export function DeathMessageToggleSection() {
  return (
    <SectionShell
      eyebrow="Player"
      title="Death Message Toggle"
      intro="自分が死亡したときの死亡メッセージを、全体に流すか自分だけに留めるかを切り替える toggle です。"
      scope="player"
    >
      <ImagePlaceholder
        id="death-toggle-hero"
        label="全体に流れる死亡メッセージ例"
        hint="通常時のブロードキャスト表示"
      />

      <h3>できること</h3>
      <ul className="wiki-bullets">
        <li>死亡メッセージのブロードキャスト可否</li>
        <li>オフ時も本人には死因が表示される</li>
        <li>統計・ログには通常通り記録される（実装依存）</li>
      </ul>

      <h3>コマンド</h3>
      <CommandBox command="/toggle death" description="死亡メッセージ表示を反転。" />
      <CommandBox command="/toggle death off" description="死亡メッセージを非公開に。" />

      <ImagePlaceholder
        id="death-toggle-private"
        label="Death オフ時の本人だけに出るメッセージ"
        hint="他人視点では表示されないことがわかる比較"
        aspect="4/3"
      />

      <h3>使いどころ</h3>
      <p>実況・配信中に死因を晒したくない時、ネタバレを避けたい時に。</p>
    </SectionShell>
  );
}
