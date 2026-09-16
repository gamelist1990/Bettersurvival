package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem;

/** Forge版 v1.7 の RegistryTitles を Paper 側で表現する。 */
public enum LevelingTitle {
    TITLELESS("titleless", "称号なし"),
    ROCKIE("rockie", "新人"),
    FIGHTER("fighter", "戦士"),
    FIGHTER_GREAT("fighter_great", "大戦士"),
    WARRIOR("warrior", "武人"),
    WARRIOR_GREAT("warrior_great", "大武人"),
    RANGER("ranger", "レンジャー"),
    RANGER_GREAT("ranger_great", "大レンジャー"),
    TANK("tank", "守護者"),
    TANK_GREAT("tank_great", "鉄壁の守護者"),
    ALCHEMIST("alchemist", "錬金術師"),
    ALCHEMIST_GREAT("alchemist_great", "大錬金術師"),
    MINER("miner", "採掘師"),
    MINER_GREAT("miner_great", "大採掘師"),
    MAGICIAN("magician", "魔術師"),
    MAGICIAN_GREAT("magician_great", "大魔術師"),
    LUCKY_ONE("lucky_one", "幸運の持ち主"),
    LUCKY_ONE_GREAT("lucky_one_great", "豪運の持ち主"),
    DRAGON_SLAYER("dragon_slayer", "竜殺し"),
    PLAYER_KILLER("player_killer", "対人の覇者"),
    MOB_KILLER("mob_killer", "魔物狩り"),
    MOB_KILLER_GREAT("mob_killer_great", "熟練の魔物狩り"),
    MOB_KILLER_MASTER("mob_killer_master", "魔物狩りの達人"),
    HERO("hero", "英雄"),
    VILLAIN("villain", "悪名高き者"),
    FISHERMAN("fisherman", "釣り人"),
    FISHERMAN_GREAT("fisherman_great", "熟練の釣り人"),
    FISHERMAN_MASTER("fisherman_master", "釣りの達人"),
    ENCHANTER("enchanter", "付与術師"),
    ENCHANTER_GREAT("enchanter_great", "熟練の付与術師"),
    ENCHANTER_MASTER("enchanter_master", "付与術の達人"),
    SURVIVOR("survivor", "生存者"),
    BUSINESSMAN("businessman", "商人"),
    DRIVER_BOAT("driver_boat", "船乗り"),
    DRIVER_CART("driver_cart", "トロッコ運転手"),
    RIDER_HORSE("rider_horse", "騎手"),
    RIDER_PIG("rider_pig", "豚乗り"),
    RIDER_STRIDER("rider_strider", "ストライダー乗り"),
    TRAVELER_NETHER("traveler_nether", "地獄の旅人"),
    TRAVELER_END("traveler_end", "果ての旅人"),
    ADMINISTRATOR("administrator", "管理者");

    private final String key;
    private final String displayName;

    LevelingTitle(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() { return key; }
    public String displayName() { return displayName; }
}
