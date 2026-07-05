import type { ComponentType } from 'react';
import { OverviewSection } from './sections/OverviewSection';
import { AdminToggleSection } from './sections/AdminToggleSection';
import { TpaToggleSection } from './sections/TpaToggleSection';
import { CustomBlocksSection } from './sections/CustomBlocksSection';
import { CustomEnchantsSection } from './sections/CustomEnchantsSection';
import { PlayerUtilitiesSection } from './sections/PlayerUtilitiesSection';

export type WikiScope = 'system' | 'admin' | 'player';

export type WikiEntry = {
  slug: string;
  title: string;
  summary: string;
  scope: WikiScope;
  icon: string;
  component: ComponentType;
};

export const wikiEntries: WikiEntry[] = [
  { slug: 'overview',         title: 'BetterSurvival Wiki',      summary: '実装済みの /toggle、カスタムブロック、カスタムエンチャント、便利機能の全体像。', scope: 'system', icon: '❦', component: OverviewSection },
  { slug: 'feature-toggle',   title: '/toggle 機能一覧',          summary: 'Loader に登録されている実在の toggle キーと、管理者が切り替える対象。',          scope: 'admin',  icon: '⚙', component: AdminToggleSection },
  { slug: 'custom-blocks',    title: 'カスタムブロック / 設置物', summary: '合成や設置で使う BetterSurvival 独自ブロック・設置アイテム。',              scope: 'player', icon: '▣', component: CustomBlocksSection },
  { slug: 'custom-enchants',  title: 'カスタムエンチャント',      summary: 'カスタムエンチャントテーブルで付与できる独自エンチャント一覧。',            scope: 'player', icon: '✦', component: CustomEnchantsSection },
  { slug: 'player-utilities', title: 'プレイヤー便利機能',        summary: 'TreeMine、OreMine、AutoFeed、Chest 系、Party、LandProtect などの利用ガイド。', scope: 'player', icon: '✚', component: PlayerUtilitiesSection },
  { slug: 'tpa',              title: 'TPA',                       summary: 'テレポートリクエスト機能。/toggle tpa が有効なとき利用できます。',            scope: 'player', icon: '↹', component: TpaToggleSection },
];

export function findWikiEntry(slug: string | null): WikiEntry | null {
  if (!slug) return null;
  return wikiEntries.find((entry) => entry.slug === slug) ?? null;
}
