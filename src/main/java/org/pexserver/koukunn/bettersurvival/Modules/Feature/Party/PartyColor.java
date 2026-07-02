package org.pexserver.koukunn.bettersurvival.Modules.Feature.Party;

import org.bukkit.Material;

/**
 * パーティーのイメージカラー。
 * 他のパーティーと同じ色は選択できない（PartyModule 側で検証）。
 */
public enum PartyColor {
    WHITE("§f", "白", Material.WHITE_WOOL, 0xFFFFFF),
    GRAY("§7", "灰色", Material.LIGHT_GRAY_WOOL, 0xAAAAAA),
    DARK_GRAY("§8", "濃灰色", Material.GRAY_WOOL, 0x555555),
    BLACK("§0", "黒", Material.BLACK_WOOL, 0x1D1D21),
    RED("§c", "赤", Material.RED_WOOL, 0xFF5555),
    DARK_RED("§4", "暗赤色", Material.RED_TERRACOTTA, 0xAA0000),
    GOLD("§6", "金色", Material.ORANGE_WOOL, 0xFFAA00),
    YELLOW("§e", "黄色", Material.YELLOW_WOOL, 0xFFFF55),
    GREEN("§a", "黄緑", Material.LIME_WOOL, 0x55FF55),
    DARK_GREEN("§2", "緑", Material.GREEN_WOOL, 0x00AA00),
    AQUA("§b", "水色", Material.LIGHT_BLUE_WOOL, 0x55FFFF),
    DARK_AQUA("§3", "青緑", Material.CYAN_WOOL, 0x00AAAA),
    BLUE("§9", "青", Material.BLUE_WOOL, 0x5555FF),
    DARK_BLUE("§1", "紺色", Material.BLUE_TERRACOTTA, 0x0000AA),
    LIGHT_PURPLE("§d", "桃色", Material.PINK_WOOL, 0xFF55FF),
    DARK_PURPLE("§5", "紫", Material.PURPLE_WOOL, 0xAA00AA);

    private final String legacyCode;
    private final String displayName;
    private final Material icon;
    private final int rgb;

    PartyColor(String legacyCode, String displayName, Material icon, int rgb) {
        this.legacyCode = legacyCode;
        this.displayName = displayName;
        this.icon = icon;
        this.rgb = rgb;
    }

    public String getLegacyCode() {
        return legacyCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public int getRgb() {
        return rgb;
    }

    /** 名前からカラーを解決する。見つからない場合は WHITE。 */
    public static PartyColor fromKey(String key) {
        if (key == null) {
            return WHITE;
        }
        try {
            return valueOf(key.toUpperCase());
        } catch (IllegalArgumentException e) {
            return WHITE;
        }
    }
}
