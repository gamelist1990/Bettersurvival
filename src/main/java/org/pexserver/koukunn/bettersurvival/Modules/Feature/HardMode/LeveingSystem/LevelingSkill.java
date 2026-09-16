package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem;

/** Forge版 v1.7 の RegistrySkills を Paper 側で表現する。 */
public enum LevelingSkill {
    ONE_HANDED("one_handed", "片手の達人", LevelingAptitude.STRENGTH, 10),
    FIGHTING_SPIRIT("fighting_spirit", "闘志", LevelingAptitude.STRENGTH, 16),
    BERSERKER("berserker", "狂戦士", LevelingAptitude.STRENGTH, 30),

    ATHLETICS("athletics", "運動能力", LevelingAptitude.CONSTITUTION, 10),
    TURTLE_SHIELD("turtle_shield", "亀の守り", LevelingAptitude.CONSTITUTION, 20),
    LION_HEART("lion_heart", "獅子の心", LevelingAptitude.CONSTITUTION, 32),

    QUICK_REPOSITION("quick_reposition", "高速再配置", LevelingAptitude.DEXTERITY, 10),
    STEALTH_MASTERY("stealth_mastery", "隠密の達人", LevelingAptitude.DEXTERITY, 16),
    CAT_EYES("cat_eyes", "猫の目", LevelingAptitude.DEXTERITY, 32),

    SNOW_WALKER("snow_walker", "雪上歩行", LevelingAptitude.DEFENSE, 10),
    COUNTER_ATTACK("counter_attack", "反撃", LevelingAptitude.DEFENSE, 18),
    DIAMOND_SKIN("diamond_skin", "金剛の皮膚", LevelingAptitude.DEFENSE, 30),

    SCHOLAR("scholar", "学者", LevelingAptitude.INTELLIGENCE, 8),
    HAGGLER("haggler", "値切り上手", LevelingAptitude.INTELLIGENCE, 16),
    ALCHEMY_MANIPULATION("alchemy_manipulation", "錬金術操作", LevelingAptitude.INTELLIGENCE, 30),

    OBSIDIAN_SMASHER("obsidian_smasher", "黒曜石破砕", LevelingAptitude.BUILDING, 12),
    TREASURE_HUNTER("treasure_hunter", "トレジャーハンター", LevelingAptitude.BUILDING, 20),
    CONVERGENCE("convergence", "収束", LevelingAptitude.BUILDING, 30),

    SAFE_PORT("safe_port", "安全転移", LevelingAptitude.MAGIC, 12),
    LIFE_EATER("life_eater", "生命吸収", LevelingAptitude.MAGIC, 18),
    WORMHOLE_STORAGE("wormhole_storage", "ワームホール倉庫", LevelingAptitude.MAGIC, 32),

    CRITICAL_ROLL("critical_roll", "運命のダイス", LevelingAptitude.LUCK, 12),
    LUCKY_DROP("lucky_drop", "幸運ドロップ", LevelingAptitude.LUCK, 22),
    LIMIT_BREAKER("limit_breaker", "限界突破", LevelingAptitude.LUCK, 32);

    private final String key;
    private final String displayName;
    private final LevelingAptitude aptitude;
    private final int requiredLevel;

    LevelingSkill(String key, String displayName, LevelingAptitude aptitude, int requiredLevel) {
        this.key = key;
        this.displayName = displayName;
        this.aptitude = aptitude;
        this.requiredLevel = requiredLevel;
    }

    public String key() { return key; }
    public String displayName() { return displayName; }
    public LevelingAptitude aptitude() { return aptitude; }
    public int requiredLevel() { return requiredLevel; }
}
