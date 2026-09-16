package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Strider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.raid.RaidFinishEvent;
import org.bukkit.inventory.MerchantInventory;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Just Leveling v1.7 の称号条件をOtherworldグループ単位で永続追跡する。 */
public final class LevelingTitleSystem implements Listener {
    private static final double TRAVEL_REQUIREMENT = 10_000.0D;
    private static final long SURVIVOR_TICKS = 100L * 24_000L;

    private final Loader plugin;
    private final LevelingSystemModule leveling;
    private final File file;
    private final YamlConfiguration data;

    public LevelingTitleSystem(Loader plugin, LevelingSystemModule leveling) {
        this.plugin = plugin;
        this.leveling = leveling;
        this.file = new File(plugin.getDataFolder(), "leveling-titles.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
        Bukkit.getOnlinePlayers().forEach(this::initialize);
    }

    public boolean isUnlocked(Player player, LevelingTitle title) {
        return title == LevelingTitle.TITLELESS || data.getBoolean(path(player, "unlocked." + title.key()), false);
    }

    public List<LevelingTitle> unlocked(Player player) {
        List<LevelingTitle> result = new ArrayList<>();
        for (LevelingTitle title : LevelingTitle.values()) if (isUnlocked(player, title)) result.add(title);
        return result;
    }

    public LevelingTitle selected(Player player) {
        String key = data.getString(path(player, "selected"), LevelingTitle.TITLELESS.key());
        for (LevelingTitle title : LevelingTitle.values()) if (title.key().equals(key)) return title;
        return LevelingTitle.TITLELESS;
    }

    public boolean select(Player player, String key) {
        for (LevelingTitle title : LevelingTitle.values()) {
            if (!title.key().equalsIgnoreCase(key) && !title.name().equalsIgnoreCase(key)) continue;
            if (!isUnlocked(player, title)) return false;
            data.set(path(player, "selected"), title.key());
            save();
            applySelected(player);
            return true;
        }
        return false;
    }

    private void initialize(Player player) {
        unlock(player, LevelingTitle.ROCKIE);
        if (player.isOp()) unlock(player, LevelingTitle.ADMINISTRATOR);
        checkAptitudeTitles(player);
        checkCounterTitles(player);
        checkWorldTitles(player);
        applySelected(player);
    }

    private void tick() {
        boolean changed = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            initialize(player);
            long ticks = data.getLong(path(player, "stats.survival_ticks"), 0L) + 100L;
            data.set(path(player, "stats.survival_ticks"), ticks);
            if (ticks >= SURVIVOR_TICKS) unlock(player, LevelingTitle.SURVIVOR);
            changed = true;
        }
        if (changed) save();
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { initialize(event.getPlayer()); }

    @EventHandler public void onDeath(PlayerDeathEvent event) {
        data.set(path(event.getEntity(), "stats.survival_ticks"), 0L);
        save();
    }

    @EventHandler public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        increment(killer, "mob_kills", 1L);
        if (event.getEntityType() == EntityType.ENDER_DRAGON) increment(killer, "dragon_kills", 1L);
        if (event.getEntityType() == EntityType.PLAYER) increment(killer, "player_kills", 1L);
        if (event.getEntityType() == EntityType.VILLAGER) increment(killer, "villager_kills", 1L);
        checkCounterTitles(killer);
    }

    @EventHandler public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        increment(event.getPlayer(), "fish", 1L);
        checkCounterTitles(event.getPlayer());
    }

    @EventHandler public void onEnchant(EnchantItemEvent event) {
        increment(event.getEnchanter(), "enchants", 1L);
        checkCounterTitles(event.getEnchanter());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrade(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory() instanceof MerchantInventory)) return;
        if (event.getRawSlot() != 2 || event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) return;
        increment(player, "trades", 1L);
        checkCounterTitles(player);
    }

    @EventHandler public void onRaidFinish(RaidFinishEvent event) {
        for (Player player : event.getWinners()) {
            increment(player, "raids", 1L);
            checkCounterTitles(player);
        }
    }

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { initialize(event.getPlayer()); }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleTravel(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()) return;
        Player player = event.getPlayer();
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;
        double distance = event.getFrom().distance(event.getTo());
        if (distance <= 0.0D || distance > 25.0D) return;
        String stat = vehicleStat(vehicle);
        if (stat == null) return;
        double total = data.getDouble(path(player, "stats." + stat), 0.0D) + distance;
        data.set(path(player, "stats." + stat), total);
        if (total >= TRAVEL_REQUIREMENT) unlock(player, titleForVehicleStat(stat));
    }

    private void checkAptitudeTitles(Player player) {
        aptitudeTitle(player, LevelingAptitude.STRENGTH, LevelingTitle.FIGHTER, LevelingTitle.FIGHTER_GREAT);
        aptitudeTitle(player, LevelingAptitude.CONSTITUTION, LevelingTitle.WARRIOR, LevelingTitle.WARRIOR_GREAT);
        aptitudeTitle(player, LevelingAptitude.DEXTERITY, LevelingTitle.RANGER, LevelingTitle.RANGER_GREAT);
        aptitudeTitle(player, LevelingAptitude.DEFENSE, LevelingTitle.TANK, LevelingTitle.TANK_GREAT);
        aptitudeTitle(player, LevelingAptitude.INTELLIGENCE, LevelingTitle.ALCHEMIST, LevelingTitle.ALCHEMIST_GREAT);
        aptitudeTitle(player, LevelingAptitude.BUILDING, LevelingTitle.MINER, LevelingTitle.MINER_GREAT);
        aptitudeTitle(player, LevelingAptitude.MAGIC, LevelingTitle.MAGICIAN, LevelingTitle.MAGICIAN_GREAT);
        aptitudeTitle(player, LevelingAptitude.LUCK, LevelingTitle.LUCKY_ONE, LevelingTitle.LUCKY_ONE_GREAT);
    }

    private void aptitudeTitle(Player player, LevelingAptitude aptitude, LevelingTitle mid, LevelingTitle max) {
        int level = leveling.getLevel(player, aptitude);
        if (level >= 16) unlock(player, mid);
        if (level >= 32) unlock(player, max);
    }

    private void checkCounterTitles(Player player) {
        long dragons = stat(player, "dragon_kills"), players = stat(player, "player_kills"), mobs = stat(player, "mob_kills");
        long villagers = stat(player, "villager_kills"), raids = stat(player, "raids"), fish = stat(player, "fish");
        long enchants = stat(player, "enchants"), trades = stat(player, "trades");
        if (dragons >= 10) unlock(player, LevelingTitle.DRAGON_SLAYER);
        if (players >= 100) unlock(player, LevelingTitle.PLAYER_KILLER);
        if (mobs >= 100) unlock(player, LevelingTitle.MOB_KILLER);
        if (mobs >= 1_000) unlock(player, LevelingTitle.MOB_KILLER_GREAT);
        if (mobs >= 10_000) unlock(player, LevelingTitle.MOB_KILLER_MASTER);
        if (raids >= 10) unlock(player, LevelingTitle.HERO);
        if (villagers >= 100) unlock(player, LevelingTitle.VILLAIN);
        if (fish >= 100) unlock(player, LevelingTitle.FISHERMAN);
        if (fish >= 1_000) unlock(player, LevelingTitle.FISHERMAN_GREAT);
        if (fish >= 10_000) unlock(player, LevelingTitle.FISHERMAN_MASTER);
        if (enchants >= 100) unlock(player, LevelingTitle.ENCHANTER);
        if (enchants >= 1_000) unlock(player, LevelingTitle.ENCHANTER_GREAT);
        if (enchants >= 10_000) unlock(player, LevelingTitle.ENCHANTER_MASTER);
        if (trades >= 100) unlock(player, LevelingTitle.BUSINESSMAN);
    }

    private void checkWorldTitles(Player player) {
        if (player.getWorld().getEnvironment() == World.Environment.NETHER) unlock(player, LevelingTitle.TRAVELER_NETHER);
        if (player.getWorld().getEnvironment() == World.Environment.THE_END) unlock(player, LevelingTitle.TRAVELER_END);
    }

    private void unlock(Player player, LevelingTitle title) {
        String key = path(player, "unlocked." + title.key());
        if (data.getBoolean(key, false)) return;
        data.set(key, true);
        player.sendMessage("§6✦ 新しい称号を獲得しました: §e" + title.displayName() + " §7[" + leveling.dataScope(player) + "]");
        save();
    }

    private void applySelected(Player player) {
        LevelingTitle title = selected(player);
        if (title == LevelingTitle.TITLELESS) player.setPlayerListName(player.getName());
        else player.setPlayerListName("§7[§6" + title.displayName() + "§7] §f" + player.getName());
    }

    private long stat(Player player, String stat) { return data.getLong(path(player, "stats." + stat), 0L); }

    private void increment(Player player, String stat, long amount) {
        data.set(path(player, "stats." + stat), stat(player, stat) + amount);
        save();
    }

    private String vehicleStat(Entity vehicle) {
        if (vehicle instanceof Boat) return "boat_distance";
        if (vehicle instanceof Minecart) return "cart_distance";
        if (vehicle instanceof Pig) return "pig_distance";
        if (vehicle instanceof Strider) return "strider_distance";
        if (vehicle instanceof AbstractHorse) return "horse_distance";
        return null;
    }

    private LevelingTitle titleForVehicleStat(String stat) {
        return switch (stat) {
            case "boat_distance" -> LevelingTitle.DRIVER_BOAT;
            case "cart_distance" -> LevelingTitle.DRIVER_CART;
            case "pig_distance" -> LevelingTitle.RIDER_PIG;
            case "strider_distance" -> LevelingTitle.RIDER_STRIDER;
            default -> LevelingTitle.RIDER_HORSE;
        };
    }

    private String path(Player player, String suffix) {
        String base = "players." + player.getUniqueId() + "." + suffix.toLowerCase(Locale.ROOT);
        String scope = leveling.dataScope(player);
        return scope.equals("default") ? base : "otherworld." + scope + "." + base;
    }

    private void save() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("leveling-titles.yml の保存に失敗しました: " + exception.getMessage());
        }
    }
}
