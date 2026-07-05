import { SectionShell } from '../components/SectionShell';
import { ImagePlaceholder } from '../components/ImagePlaceholder';
import { CommandBox } from '../components/CommandBox';

export function VisibilityToggleSection() {
  return (
    <SectionShell
      eyebrow="Player"
      title="Visibility Toggle"
      intro="他プレイヤーの描画を自分の画面から隠す toggle です。負荷軽減や建築時の見通し確保に。"
      scope="player"
    >
      <ImagePlaceholder
        id="visibility-toggle-before"
        label="他プレイヤーが見えている通常表示"
        hint="スポーンや街中の人が多い場面"
      />

      <h3>できること</h3>
      <ul className="wiki-bullets">
        <li>他プレイヤーモデルの非表示</li>
        <li>FPS 改善が期待できる</li>
        <li>当たり判定は残る場合が多い（実装依存）</li>
      </ul>

      <h3>コマンド</h3>
      <CommandBox command="/toggle visibility" description="他プレイヤーの表示を反転。" />
      <CommandBox command="/toggle visibility off" description="他プレイヤーを非表示に。" />

      <ImagePlaceholder
        id="visibility-toggle-after"
        label="Visibility オフ後の同じ場所"
        hint="Before/After の比較で使うと効果が伝わりやすい"
        aspect="16/9"
      />

      <h3>使いどころ</h3>
      <p>スポーン地点や街エリアでの動作改善、建築時に他人が視界に入るのを避けたい時に。</p>
    </SectionShell>
  );
}
