import { SectionShell } from '../components/SectionShell';
import { CommandBox } from '../components/CommandBox';

export function PetSitSection() {
  return (
    <SectionShell eyebrow="Pet & Sit" title="Pet・座る機能" intro="友好モブを仲間にして連れ歩くPetシステムと、その場に座るコマンドの使い方です。" scope="player">
      <h3>友好モブをPetにする</h3>
      <ol>
        <li><code>/pet list</code>で対応モブを確認します。</li>
        <li><code>/pet recipe &lt;モブ名&gt;</code>で、そのモブ専用の素材2種類を確認します。</li>
        <li>2種類のアイテムを近くへドロップして「なかよし素材」を合成します。</li>
        <li>なかよし素材を手に持ち、対象モブを右クリックするとPetになります。</li>
      </ol>
      <CommandBox command="/pet list" description="対応モブを一覧表示します。" />
      <CommandBox command="/pet recipe cow" description="例としてウシ用の合成素材を確認します。" />

      <h3>Petの行動</h3>
      <p>所有者がPetをスニーク右クリックするたびに、次の順番で切り替わります。</p>
      <ul className="wiki-bullets">
        <li><strong>一緒に行動</strong>: 所有者を追いかけ、遠く離れた場合はそばへ戻ります。</li>
        <li><strong>待機</strong>: その場で動かず待ちます。座れるモブは座る姿勢になります。</li>
        <li><strong>放し飼い</strong>: 通常のAIで自由に行動します。</li>
        <li><strong>散歩</strong>: リードを付けている間は各モードよりリード移動を優先します。</li>
      </ul>

      <h3>その場に座る</h3>
      <CommandBox command="/sit" description="足元のブロックへ座ります。もう一度実行するか降車キーで立ち上がります。" />
      <p>空中では使用できません。乗り物に乗っている状態で実行した場合は、先にその乗り物から降ります。</p>
    </SectionShell>
  );
}
