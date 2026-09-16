package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem;

/** Forge版 v1.7 の RegistryTitles を Paper 側で表現する。 */
public enum LevelingTitle {
    TITLELESS("titleless", "Titleless"),
    ROCKIE("rockie", "Rockie"),
    FIGHTER("fighter", "Fighter"),
    FIGHTER_GREAT("fighter_great", "Great Fighter"),
    WARRIOR("warrior", "Warrior"),
    WARRIOR_GREAT("warrior_great", "Great Warrior"),
    RANGER("ranger", "Ranger"),
    RANGER_GREAT("ranger_great", "Great Ranger"),
    TANK("tank", "Tank"),
    TANK_GREAT("tank_great", "Great Tank"),
    ALCHEMIST("alchemist", "Alchemist"),
    ALCHEMIST_GREAT("alchemist_great", "Great Alchemist"),
    MINER("miner", "Miner"),
    MINER_GREAT("miner_great", "Great Miner"),
    MAGICIAN("magician", "Magician"),
    MAGICIAN_GREAT("magician_great", "Great Magician"),
    LUCKY_ONE("lucky_one", "Lucky One"),
    LUCKY_ONE_GREAT("lucky_one_great", "Great Lucky One"),
    DRAGON_SLAYER("dragon_slayer", "DragonSlayer"),
    PLAYER_KILLER("player_killer", "PlayerKiller"),
    MOB_KILLER("mob_killer", "Mob Killer"),
    MOB_KILLER_GREAT("mob_killer_great", "Great Mob Killer"),
    MOB_KILLER_MASTER("mob_killer_master", "Master Mob Killer"),
    HERO("hero", "Hero"),
    VILLAIN("villain", "Villain"),
    FISHERMAN("fisherman", "Fisherman"),
    FISHERMAN_GREAT("fisherman_great", "Great Fisherman"),
    FISHERMAN_MASTER("fisherman_master", "Master Fisherman"),
    ENCHANTER("enchanter", "Enchanter"),
    ENCHANTER_GREAT("enchanter_great", "Great Enchanter"),
    ENCHANTER_MASTER("enchanter_master", "Master Enchanter"),
    SURVIVOR("survivor", "Survivor"),
    BUSINESSMAN("businessman", "Dealer Maker"),
    DRIVER_BOAT("driver_boat", "Boat Driver"),
    DRIVER_CART("driver_cart", "Cart Driver"),
    RIDER_HORSE("rider_horse", "Horse Rider"),
    RIDER_PIG("rider_pig", "Pig Rider"),
    RIDER_STRIDER("rider_strider", "Strider Rider"),
    TRAVELER_NETHER("traveler_nether", "Infernal Traveler"),
    TRAVELER_END("traveler_end", "End Traveler"),
    ADMINISTRATOR("administrator", "Administrator");

    private final String key;
    private final String displayName;

    LevelingTitle(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() { return key; }
    public String displayName() { return displayName; }
}
