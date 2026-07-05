package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api;

import java.util.List;

/**
 * カスタムエンチャントの静的定義。
 *
 * サーバー bootstrap 段階 (Paper Registry API でのエンチャント登録) と
 * 実行時 (効果・UI) の両方から参照されるため、Bukkit のランタイム API に
 * 依存しない純粋なデータとして分離してある。
 *
 * 新しいエンチャントを追加するときは、ここに定義を1行足し、
 * enchants/ にクラスを作って CustomEnchantTableModule で register する。
 */
public final class CustomEnchantDefinitions {

    /** 登録名前空間 (Key.key(NAMESPACE, id)) */
    public static final String NAMESPACE = "bettersurvival";

    public record Definition(String id, String displayName, int maxLevel) {
    }

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition("momentum", "採掘加速", 5),
            new Definition("areabreak", "範囲採掘", 3),
            new Definition("sedimentbreak", "土砂掘削", 3),
            new Definition("battlerepair", "戦利修復", 3),
            new Definition("autocollect", "自動回収", 1),
            new Definition("autosmelt", "オートスメルト", 1),
            new Definition("magnet", "マグネット", 3),
            new Definition("xpboost", "経験値ブースト", 3),
            new Definition("nightvision", "ナイトビジョン", 1),
            new Definition("haste", "ヘイスト", 3),
            new Definition("wisdom", "知恵", 3),
            new Definition("leaffortune", "リーフ・フォーチュン", 3),
            new Definition("accelerategrowth", "成長加速", 3),
            new Definition("fireproofboots", "耐火のブーツ", 3),
            new Definition("handofthief", "泥棒の手", 1),
            new Definition("voidprotection", "奈落の保護", 3),
            new Definition("volleyshot", "連射", 3),
            new Definition("longshot", "狙撃", 3),
            new Definition("voidrescue", "奈落救出", 3),
            new Definition("chainresurrect", "連鎖蘇生", 3),
            new Definition("sniper", "スナイパー", 3),
            new Definition("aqualung", "水中呼吸", 1),
            new Definition("hungersaver", "満腹維持", 3),
            new Definition("thornless", "棘無効", 1),
            new Definition("jumpboost", "跳躍", 3),
            new Definition("swiftboots", "俊足のブーツ", 2),
            new Definition("autoreplant", "自動再植", 1),
            new Definition("obsidianshield", "黒曜石の盾", 3),
            new Definition("beekeeper", "養蜂家", 1));

    private CustomEnchantDefinitions() {
    }

    public static List<Definition> all() {
        return DEFINITIONS;
    }
}
