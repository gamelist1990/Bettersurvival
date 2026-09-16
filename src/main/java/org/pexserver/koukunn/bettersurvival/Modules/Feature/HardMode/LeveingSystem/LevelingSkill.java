package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem;

/** Forge版 v1.7 の RegistrySkills を Paper 側で表現する。 */
public enum LevelingSkill {
    ONE_HANDED("one_handed", "One Handed", LevelingAptitude.STRENGTH, 10),
    FIGHTING_SPIRIT("fighting_spirit", "Fighting Spirit", LevelingAptitude.STRENGTH, 16),
    BERSERKER("berserker", "Berserker", LevelingAptitude.STRENGTH, 30),

    ATHLETICS("athletics", "Athletics", LevelingAptitude.CONSTITUTION, 10),
    TURTLE_SHIELD("turtle_shield", "Turtle Shield", LevelingAptitude.CONSTITUTION, 20),
    LION_HEART("lion_heart", "Lion Heart", LevelingAptitude.CONSTITUTION, 32),

    QUICK_REPOSITION("quick_reposition", "Quick Reposition", LevelingAptitude.DEXTERITY, 10),
    STEALTH_MASTERY("stealth_mastery", "Stealth Mastery", LevelingAptitude.DEXTERITY, 16),
    CAT_EYES("cat_eyes", "Cat Eyes", LevelingAptitude.DEXTERITY, 32),

    SNOW_WALKER("snow_walker", "Snow Walker", LevelingAptitude.DEFENSE, 10),
    COUNTER_ATTACK("counter_attack", "Counter Attack", LevelingAptitude.DEFENSE, 18),
    DIAMOND_SKIN("diamond_skin", "Diamond Skin", LevelingAptitude.DEFENSE, 30),

    SCHOLAR("scholar", "Scholar", LevelingAptitude.INTELLIGENCE, 8),
    HAGGLER("haggler", "Haggler", LevelingAptitude.INTELLIGENCE, 16),
    ALCHEMY_MANIPULATION("alchemy_manipulation", "Alchemy Manipulation", LevelingAptitude.INTELLIGENCE, 30),

    OBSIDIAN_SMASHER("obsidian_smasher", "Obsidian Smasher", LevelingAptitude.BUILDING, 12),
    TREASURE_HUNTER("treasure_hunter", "Treasure Hunter", LevelingAptitude.BUILDING, 20),
    CONVERGENCE("convergence", "Convergence", LevelingAptitude.BUILDING, 30),

    SAFE_PORT("safe_port", "Safe Port", LevelingAptitude.MAGIC, 12),
    LIFE_EATER("life_eater", "Life Eater", LevelingAptitude.MAGIC, 18),
    WORMHOLE_STORAGE("wormhole_storage", "Wormhole Storage", LevelingAptitude.MAGIC, 32),

    CRITICAL_ROLL("critical_roll", "Critical Roll Dice", LevelingAptitude.LUCK, 12),
    LUCKY_DROP("lucky_drop", "Lucky Drop", LevelingAptitude.LUCK, 22),
    LIMIT_BREAKER("limit_breaker", "Limit Breaker", LevelingAptitude.LUCK, 32);

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
