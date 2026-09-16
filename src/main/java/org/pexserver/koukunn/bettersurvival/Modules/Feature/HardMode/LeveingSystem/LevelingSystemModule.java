package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Forge Mod "Just Leveling" のサーバー側コアを Paper API で再構成した実装。
 * クライアント Mod 固有の描画・キー入力は、チェスト GUI と Leveling Book に置き換える。
 */
public final class LevelingSystemModule implements Listener {
    private static final String GUI_TITLE = "§8Just Leveling";
    private static final int FIRST_COST = 5;

    private final Loader plugin;
    private final File dataFile;
    private final YamlConfiguration data;
    private final NamespacedKey bookKey;

    public LevelingSystemModule(Loader plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "leveling-data.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        this.bookKey = new NamespacedKey(plugin, "just_leveling_book");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerBookRecipe();
        Bukkit.getOnlinePlayers().forEach(this::applyPassives);
    }

    public ItemStack createLevelingBook() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§dLeveling Book");
        meta.setLore(List.of(
                "§7Just Leveling の能力値を開く",
                "§7右クリックで使用",
                "§8Paper port"
        ));
        meta.getPersistentDataContainer().set(bookKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 27, GUI_TITLE);
        int[] slots = {9, 10, 11, 12, 14, 15, 16, 17};
        LevelingAptitude[] values = LevelingAptitude.values();
        for (int i = 0; i < values.length; i++) {
            LevelingAptitude aptitude = values[i];
            int level = getLevel(player, aptitude);
            ItemStack icon = new ItemStack(aptitude.icon());
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName("§e" + aptitude.displayName() + " §7[" + aptitude.abbreviation() + "]");
            List<String> lore = new ArrayList<>();
            lore.add("§fLevel: §a" + level + "§7/§a" + LevelingAptitude.MAX_LEVEL);
            lore.add("§fRank: §b" + aptitude.rank(level));
            lore.add("§fNext cost: §6" + levelCost(level) + " vanilla levels");
            lore.add("");
            lore.add("§7Passive I: " + aptitude.passiveTier10(level) + "/10");
            lore.add("§7Passive II: " + aptitude.passiveTier5(level) + "/5");
            lore.add("");
            lore.add(level >= LevelingAptitude.MAX_LEVEL ? "§aMAX LEVEL" : "§eClick to level up");
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(slots[i], icon);
        }
        ItemStack info = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§aExperience");
        infoMeta.setLore(List.of("§7Vanilla level: §f" + player.getLevel(), "§7Level-up consumes vanilla levels."));
        info.setItemMeta(infoMeta);
        inventory.setItem(22, info);
        player.openInventory(inventory);
    }

    public int getLevel(Player player, LevelingAptitude aptitude) {
        return Math.max(0, Math.min(LevelingAptitude.MAX_LEVEL,
                data.getInt(path(player.getUniqueId(), aptitude), 0)));
    }

    public boolean levelUp(Player player, LevelingAptitude aptitude) {
        int current = getLevel(player, aptitude);
        if (current >= LevelingAptitude.MAX_LEVEL) {
            player.sendMessage("§c" + aptitude.displayName() + " は最大レベルです。");
            return false;
        }
        int cost = levelCost(current);
        if (player.getLevel() < cost) {
            player.sendMessage("§cレベルが足りません。必要: " + cost + " / 所持: " + player.getLevel());
            return false;
        }
        player.setLevel(player.getLevel() - cost);
        setLevel(player, aptitude, current + 1);
        player.sendMessage("§a" + aptitude.displayName() + " が Lv." + (current + 1) + " になりました。");
        return true;
    }

    private void setLevel(Player player, LevelingAptitude aptitude, int level) {
        data.set(path(player.getUniqueId(), aptitude), level);
        save();
        applyPassives(player);
    }

    private int levelCost(int currentLevel) {
        // 本家デフォルト aptitudeFirstCostLevel=5 を起点に、進行に合わせて段階的に増加。
        return FIRST_COST + Math.max(0, currentLevel / 2);
    }

    private String path(UUID uuid, LevelingAptitude aptitude) {
        return "players." + uuid + ".aptitudes." + aptitude.key();
    }

    private void registerBookRecipe() {
        try {
            NamespacedKey recipeKey = new NamespacedKey(plugin, "leveling_book");
            Bukkit.removeRecipe(recipeKey);
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, createLevelingBook());
            recipe.shape(" E ", "LBL", " E ");
            recipe.setIngredient('E', Material.EMERALD);
            recipe.setIngredient('L', Material.LAPIS_LAZULI);
            recipe.setIngredient('B', Material.BOOK);
            Bukkit.addRecipe(recipe);
        } catch (IllegalArgumentException ignored) {
            // reload 等で既に登録されているケースは無視する。
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyPassives(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onUseBook(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        if (!isLevelingBook(item)) return;
        event.setCancelled(true);
        open(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onGuiClick(InventoryClickEvent event) {
        if (!GUI_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot();
        int[] slots = {9, 10, 11, 12, 14, 15, 16, 17};
        for (int i = 0; i < slots.length; i++) {
            if (raw == slots[i]) {
                if (levelUp(player, LevelingAptitude.values()[i])) open(player);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker != null) {
            if (event.getDamager() instanceof Projectile) {
                int dex = getLevel(attacker, LevelingAptitude.DEXTERITY);
                event.setDamage(event.getDamage() + LevelingAptitude.DEXTERITY.passiveTier5(dex));
            } else {
                int strength = getLevel(attacker, LevelingAptitude.STRENGTH);
                if (strength >= 16 && attacker.getInventory().getItemInOffHand().getType().isAir()) {
                    event.setDamage(event.getDamage() * 1.5D); // One Handed
                }
                AttributeInstance maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH);
                if (strength >= 32 && maxHealth != null && attacker.getHealth() <= maxHealth.getValue() * 0.30D) {
                    event.setDamage(event.getDamage() * 1.5D); // Berserker
                }
                int luck = getLevel(attacker, LevelingAptitude.LUCK);
                if (luck >= 20 && attacker.getFallDistance() > 0.0F && Math.random() < 0.10D) {
                    event.setDamage(event.getDamage() * 1.25D); // Critical Roll 6 相当のボーナス
                }
            }
        }

        if (event.getEntity() instanceof Player victim) {
            int magic = getLevel(victim, LevelingAptitude.MAGIC);
            int tiers = LevelingAptitude.MAGIC.passiveTier5(magic);
            if (tiers > 0 && isMagicLike(event.getDamager())) {
                event.setDamage(event.getDamage() * (1.0D - tiers * 0.10D));
            }
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        int magic = getLevel(killer, LevelingAptitude.MAGIC);
        if (magic >= 20) {
            AttributeInstance max = killer.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) killer.setHealth(Math.min(max.getValue(), killer.getHealth() + 1.0D)); // Life Eater
        }
        int luck = getLevel(killer, LevelingAptitude.LUCK);
        if (luck >= 20 && Math.random() < 0.10D && !event.getDrops().isEmpty()) {
            ItemStack extra = event.getDrops().getFirst().clone();
            extra.setAmount(Math.min(extra.getMaxStackSize(), extra.getAmount() * 2));
            event.getDrops().add(extra); // Lucky Drop
        }
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private boolean isMagicLike(Entity damager) {
        return damager instanceof Projectile && !(damager instanceof AbstractArrow);
    }

    private boolean isLevelingBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(bookKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private void applyPassives(Player player) {
        int strength = getLevel(player, LevelingAptitude.STRENGTH);
        int constitution = getLevel(player, LevelingAptitude.CONSTITUTION);
        int dexterity = getLevel(player, LevelingAptitude.DEXTERITY);
        int defense = getLevel(player, LevelingAptitude.DEFENSE);
        int intelligence = getLevel(player, LevelingAptitude.INTELLIGENCE);
        int luck = getLevel(player, LevelingAptitude.LUCK);

        // 本家デフォルト値を passive level 数に分割して適用する。
        setBase(player, Attribute.ATTACK_DAMAGE, 1.0D + LevelingAptitude.STRENGTH.passiveTier10(strength) * 0.15D);
        setBase(player, Attribute.MAX_HEALTH, 20.0D + LevelingAptitude.CONSTITUTION.passiveTier10(constitution) * 2.0D);
        setBase(player, Attribute.KNOCKBACK_RESISTANCE, LevelingAptitude.CONSTITUTION.passiveTier5(constitution) * 0.10D);
        setBase(player, Attribute.MOVEMENT_SPEED, 0.10D + LevelingAptitude.DEXTERITY.passiveTier10(dexterity) * 0.005D);
        setBase(player, Attribute.ARMOR, LevelingAptitude.DEFENSE.passiveTier10(defense) * 0.40D);
        setBase(player, Attribute.ARMOR_TOUGHNESS, LevelingAptitude.DEFENSE.passiveTier5(defense) * 0.20D);
        setBase(player, Attribute.ATTACK_SPEED, 4.0D + LevelingAptitude.INTELLIGENCE.passiveTier10(intelligence) * 0.04D);
        setBase(player, Attribute.LUCK, LevelingAptitude.LUCK.passiveTier10(luck) * 0.20D);

        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && player.getHealth() > max.getValue()) player.setHealth(max.getValue());
    }

    private void setBase(Player player, Attribute attribute, double value) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private void save() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("leveling-data.yml の保存に失敗しました: " + exception.getMessage());
        }
    }
}
