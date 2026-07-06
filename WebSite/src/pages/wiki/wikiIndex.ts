import type { ComponentType } from 'react';
import { OverviewSection } from './sections/OverviewSection';
import { AdminToggleSection } from './sections/AdminToggleSection';
import { TpaToggleSection } from './sections/TpaToggleSection';
import { CustomBlocksSection } from './sections/CustomBlocksSection';
import { CustomEnchantsSection } from './sections/CustomEnchantsSection';
import { PlayerUtilitiesSection } from './sections/PlayerUtilitiesSection';
import { RecipesSection } from './sections/RecipesSection';
import { SharedStorageSection } from './sections/SharedStorageSection';
import { CommandsSection } from './sections/CommandsSection';
import { CopperGolemSection } from './sections/CopperGolemSection';
import { LandProtectionSection } from './sections/LandProtectionSection';
import { PartySection } from './sections/PartySection';
import { ChestShopSection } from './sections/ChestShopSection';
import { WarpStoneSection } from './sections/WarpStoneSection';
import { ParallelFurnaceSection } from './sections/ParallelFurnaceSection';

export type WikiScope = 'system' | 'admin' | 'player';

export type WikiEntry = {
  slug: string;
  title: string;
  summary: string;
  scope: WikiScope;
  /** サイドバーで折りたためるカテゴリ名。省略した場合は scope 直下に表示。 */
  category?: string;
  icon: string;
  component: ComponentType;
};

export const wikiEntries: WikiEntry[] = [
  { slug: 'overview',         title: 'BetterSurvival Wiki',      summary: '実装済みの /toggle、カスタムブロック、カスタムエンチャント、便利機能の全体像。', scope: 'system', icon: '❦', component: OverviewSection },

  { slug: 'feature-toggle',   title: '/toggle 機能一覧',          summary: 'Loader に登録されている実在の toggle キーと、管理者が切り替える対象。',          scope: 'admin',  icon: '⚙', component: AdminToggleSection },

  // --- 設備・アイテム ---
  { slug: 'custom-blocks',    title: 'カスタムブロック / 設置物', summary: '合成や設置で使う BetterSurvival 独自ブロック・設置アイテム。',              scope: 'player', category: '設備・アイテム', icon: '▣', component: CustomBlocksSection },
  { slug: 'recipes',          title: '合成レシピ一覧',            summary: 'BetterSurvival GUI 木の斧をはじめ、投げて作るアイテム系レシピを 1 ページに集約。',    scope: 'player', category: '設備・アイテム', icon: '⚒', component: RecipesSection },
  { slug: 'custom-enchants',  title: 'カスタムエンチャント',      summary: 'カスタムエンチャントテーブルで付与できる独自エンチャント一覧。',            scope: 'player', category: '設備・アイテム', icon: '✦', component: CustomEnchantsSection },

  // --- 詳細ガイド ---
  { slug: 'shared-storage',   title: '共有ストレージ',           summary: '主チェスト + sub チェストによる自動仕分けストレージの使い方と設定 UI 全項目。', scope: 'player', category: '詳細ガイド', icon: '⊞', component: SharedStorageSection },
  { slug: 'copper-golem',     title: 'カッパーゴーレム',         summary: '召喚 2 段階、作物採取 / 戦闘モードの設定、GUI 操作、複数体運用までを 1 ページに集約。', scope: 'player', category: '詳細ガイド', icon: '⚛', component: CopperGolemSection },
  { slug: 'land-protection',  title: '土地保護',                 summary: 'Lv1-100 の保護半径 / 維持コスト早見表、燃料、パーティ / リング / レイド連携までまとめて解説。', scope: 'player', category: '詳細ガイド', icon: '⛨', component: LandProtectionSection },
  { slug: 'party',            title: 'パーティー (ギルド)',      summary: '招待制の 3 階級グループ機能。名前 / カラー / 表示設定・土地保護連携まで解説。',              scope: 'player', category: '詳細ガイド', icon: '☰', component: PartySection },
  { slug: 'chestshop',        title: 'チェストショップ',         summary: '看板でチェストをショップ化。通貨指定・26 スロット商品・収益回収 UI までの流れ。',           scope: 'player', category: '詳細ガイド', icon: '$', component: ChestShopSection },
  { slug: 'warp-stone',       title: 'ワープストーン',           summary: '設置 → 発見 → ワープ、GTA 風演出、座標本、他人からの発見メカニクスまで。',                    scope: 'player', category: '詳細ガイド', icon: '✦', component: WarpStoneSection },
  { slug: 'parallel-furnace', title: '並列かまど',                summary: '最大 200 ライン同時精錬、6 行 54 スロットの共有 UI、コア増設・搬出チェスト・共有運用まで解説。', scope: 'player', category: '詳細ガイド', icon: '⚒', component: ParallelFurnaceSection },

  // --- コマンド ---
  { slug: 'commands',         title: 'コマンド一覧',              summary: 'メンバーが使えるコマンドと OP 専用コマンドを、詳細モーダル付きで一覧表示。',            scope: 'player', category: 'コマンド', icon: '⌘', component: CommandsSection },
  { slug: 'tpa',              title: 'TPA',                       summary: 'テレポートリクエスト (テレポート申請) 機能の使い方。',                          scope: 'player', category: 'コマンド', icon: '↹', component: TpaToggleSection },

  // --- 便利機能 (直下) ---
  { slug: 'player-utilities', title: 'プレイヤー便利機能',        summary: 'TreeMine、OreMine、AutoFeed、Chest 系、Party、LandProtect などの利用ガイド。', scope: 'player', icon: '✚', component: PlayerUtilitiesSection },
];

export function findWikiEntry(slug: string | null): WikiEntry | null {
  if (!slug) return null;
  return wikiEntries.find((entry) => entry.slug === slug) ?? null;
}
