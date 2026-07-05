import { SectionShell } from '../components/SectionShell';
import { ImagePlaceholder } from '../components/ImagePlaceholder';
import { CommandBox } from '../components/CommandBox';

export function PvpToggleSection() {
  return (
    <SectionShell
      eyebrow="Player"
      title="PvP Toggle"
      intro="プレイヤー同士のダメージ判定を、自分の側だけオン／オフにできる toggle です。"
      scope="player"
    >
      <ImagePlaceholder
        id="pvp-toggle-hero"
        label="PvP をオフにしている場面"
        hint="剣で殴っても『PvP 無効です』の表示が出ているスクショ"
      />

      <h3>できること</h3>
      <ul className="wiki-bullets">
        <li>他プレイヤーからの攻撃を受け付けない状態にする</li>
        <li>自分から攻撃した瞬間、一時的にオン扱いになる場合あり（実装依存）</li>
        <li>PvP がオンのプレイヤー同士でのみダメージが成立</li>
      </ul>

      <h3>コマンド</h3>
      <CommandBox command="/toggle pvp" description="現在の PvP 状態を反転。" />
      <CommandBox command="/toggle pvp on" description="PvP を明示的に有効化。" />
      <CommandBox command="/toggle pvp off" description="PvP を明示的に無効化。" />

      <ImagePlaceholder
        id="pvp-toggle-effect"
        label="オン／オフ切替時のアクションバー表示"
        hint="切替直後に出る通知の見え方"
        aspect="4/3"
      />

      <h3>使いどころ</h3>
      <p>建築中やイベント外の時間帯に、事故的な PvP を避けたい場合に有効です。イベント中は逆にオンにしておくと参加意思の表明にもなります。</p>
    </SectionShell>
  );
}
