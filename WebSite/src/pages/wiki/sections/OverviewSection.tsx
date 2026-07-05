import { SectionShell } from '../components/SectionShell';
import { ImagePlaceholder } from '../components/ImagePlaceholder';
import { CommandBox } from '../components/CommandBox';

export function OverviewSection() {
  return (
    <SectionShell
      eyebrow="Overview"
      title="BetterSurvival Wiki の全体像"
      intro="この Wiki は /toggle だけではなく、実装済みのカスタムブロック、カスタムエンチャント、便利機能、管理機能を事実ベースで整理します。"
      scope="op"
    >
      <ImagePlaceholder
        id="overview-diagram"
        label="BetterSurvival 機能分類図"
        hint="/toggle、カスタムブロック、カスタムエンチャント、便利機能の関係が分かる図やスクショ"
        aspect="16/9"
      />

      <h3>主なカテゴリ</h3>
      <ul className="wiki-bullets">
        <li><strong>/toggle 機能</strong>: Loader に登録された機能キーをサーバー全体または利用者側で切り替えます。</li>
        <li><strong>カスタムブロック / 設置物</strong>: アイテム合成や設置で作る独自設備です。</li>
        <li><strong>カスタムエンチャント</strong>: 専用テーブルから道具へ付与する独自エンチャントです。</li>
        <li><strong>便利機能</strong>: 採掘、チェスト、TPA、Party、LandProtect、WebMap などのプレイ補助です。</li>
      </ul>

      <h3>代表的な入口</h3>
      <CommandBox command="/toggle" description="登録済み機能の状態確認や切り替えに使います。" />
      <CommandBox command="/tpa <player>" description="TPA 機能が有効なとき、テレポートリクエストを送ります。" />
      <CommandBox command="/home" description="Home 機能が有効なとき、登録済みの家へ移動します。" />

      <ImagePlaceholder
        id="overview-command-list"
        label="/toggle と機能画面の一覧表示"
        hint="チャット欄または GUI で機能一覧が見えているスクショ"
        aspect="4/3"
      />

      <h3>読み方のポイント</h3>
      <p>左サイドナビで、管理者向けのスイッチ、プレイヤー向けの使い方、カスタム要素を切り替えて確認できます。</p>
    </SectionShell>
  );
}
