package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem;

/** Forge版 v1.7 の RegistryPassives を Paper 側で表現する。 */
public enum LevelingPassive {
    ATTACK_DAMAGE("attack_damage", LevelingAptitude.STRENGTH, true),
    ATTACK_KNOCKBACK("attack_knockback", LevelingAptitude.STRENGTH, false),
    MAX_HEALTH("max_health", LevelingAptitude.CONSTITUTION, true),
    KNOCKBACK_RESISTANCE("knockback_resistance", LevelingAptitude.CONSTITUTION, false),
    MOVEMENT_SPEED("movement_speed", LevelingAptitude.DEXTERITY, true),
    PROJECTILE_DAMAGE("projectile_damage", LevelingAptitude.DEXTERITY, false),
    ARMOR("armor", LevelingAptitude.DEFENSE, true),
    ARMOR_TOUGHNESS("armor_toughness", LevelingAptitude.DEFENSE, false),
    ATTACK_SPEED("attack_speed", LevelingAptitude.INTELLIGENCE, false),
    ENTITY_REACH("entity_reach", LevelingAptitude.INTELLIGENCE, false),
    BLOCK_REACH("block_reach", LevelingAptitude.BUILDING, false),
    BREAK_SPEED("break_speed", LevelingAptitude.BUILDING, false),
    BENEFICIAL_EFFECT("beneficial_effect", LevelingAptitude.MAGIC, true),
    MAGIC_RESIST("magic_resist", LevelingAptitude.MAGIC, false),
    CRITICAL_DAMAGE("critical_damage", LevelingAptitude.LUCK, true),
    LUCK("luck", LevelingAptitude.LUCK, true);

    private final String key;
    private final LevelingAptitude aptitude;
    private final boolean tenTiers;

    LevelingPassive(String key, LevelingAptitude aptitude, boolean tenTiers) {
        this.key = key;
        this.aptitude = aptitude;
        this.tenTiers = tenTiers;
    }

    public String key() { return key; }
    public LevelingAptitude aptitude() { return aptitude; }
    public int levelAt(int aptitudeLevel) {
        return tenTiers ? aptitude.passiveTier10(aptitudeLevel) : aptitude.passiveTier5(aptitudeLevel);
    }
}
