package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Forge Mod "Just Leveling" のサーバー側コアを Paper API で再構成する。 */
public final class LevelingSystemModule implements Listener {
    private static final String GUI_TITLE = "§8Just Leveling · Progression";
    private static final String TITLE_GUI_TITLE = "§8Just Leveling · Titles";
    private static final int FIRST_COST = 5;
    private static final int[] APTITUDE_SLOTS = {10, 12, 14, 16, 28, 30, 32, 34};
    private static final int[] PROGRESS_SLOTS = {37, 38, 39, 40, 41, 42, 43};

    private final Loader plugin;
    private final File dataFile;
    private final YamlConfiguration data;
    private final NamespacedKey bookKey;
    private final NamespacedKey recipeKey;
    private LevelingTitleSystem titleSystem;

    public LevelingSystemModule(Loader plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "leveling-data.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        this.bookKey = new NamespacedKey(plugin, "just_leveling_book");
        this.recipeKey = new NamespacedKey(plugin, "leveling_book");
        if (!data.contains("settings.enabled")) {
            data.set("settings.enabled", true);
            save();
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (isEnabled()) registerBookRecipe(); else removeBookRecipe();
        Bukkit.getOnlinePlayers().forEach(this::applyPassives);
    }

    public boolean isEnabled() { return data.getBoolean("settings.enabled", true); }

    public void setEnabled(boolean enabled) {
        if (isEnabled() == enabled) return;
        data.set("settings.enabled", enabled);
        save();
        if (enabled) {
            registerBookRecipe();
            Bukkit.getOnlinePlayers().forEach(this::applyPassives);
        } else {
            removeBookRecipe();
            Bukkit.getOnlinePlayers().forEach(this::resetPassives);
        }
    }

    public String dataScope(Player player) {
        if (plugin.getOtherworldModule() == null) return "default";
        String group = plugin.getOtherworldModule().getGroup(player);
        return group == null || group.isBlank() ? "default" : group.toLowerCase(Locale.ROOT);
    }

    public void setTitleSystem(LevelingTitleSystem titleSystem) {
        this.titleSystem = titleSystem;
    }

    public ItemStack createLevelingBook() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        ComponentUtils.setDisplayName(meta, "§dレベリングの書");
        ComponentUtils.setLore(meta, List.of(
                "§7能力値画面を開きます",
                "§7右クリックで使用",
                "§8Just Leveling - Paper版"));
        meta.getPersistentDataContainer().set(bookKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public void open(Player player) {
        if (!isEnabled()) {
            player.sendMessage("§cJust Leveling は現在無効です。");
            return;
        }

        ChestUI.Builder builder = ChestUI.builder()
                .title(GUI_TITLE)
                .size(54)
                .type("leveling-main");

        LevelingAptitude[] aptitudes = LevelingAptitude.values();
        for (int i = 0; i < aptitudes.length; i++) {
            LevelingAptitude aptitude = aptitudes[i];
            int level = getLevel(player, aptitude);
            int cost = levelCost(level);
            boolean maxed = level >= LevelingAptitude.MAX_LEVEL;
            boolean affordable = !maxed && player.getLevel() >= cost;
            String status = maxed ? "§a✓ 最大レベル" : affordable ? "§e▶ クリックでレベルアップ" : "§c✕ XPレベル不足";
            List<String> lore = List.of(
                    "§8━━━━━━━━━━━━━━━━",
                    "§7Level  §a" + level + "§7/§a" + LevelingAptitude.MAX_LEVEL,
                    "§7Rank   §b" + aptitude.rank(level),
                    "§7Passive §f" + aptitude.passiveTier10(level) + "/10 §8• §f" + aptitude.passiveTier5(level) + "/5",
                    maxed ? "§7Cost   §aMAX" : "§7Cost   §6" + cost + " XP levels",
                    "§8━━━━━━━━━━━━━━━━",
                    status);
            builder.addButtonAt(APTITUDE_SLOTS[i],
                    (maxed ? "§a" : affordable ? "§e" : "§c") + aptitude.displayName() + " §7[" + aptitude.abbreviation() + "]",
                    aptitude.icon(),
                    String.join("\n", lore));
        }

        int total = totalAptitudeLevel(player);
        int maxTotal = LevelingAptitude.values().length * LevelingAptitude.MAX_LEVEL;
        LevelingTitle selectedTitle = titleSystem == null ? LevelingTitle.TITLELESS : titleSystem.selected(player);
        int unlockedTitles = titleSystem == null ? 0 : titleSystem.unlocked(player).size();

        builder.addPlayerHeadAt(20,
                "§b" + player.getName(),
                player,
                String.join("\n", List.of(
                        "§7能力値合計: §f" + total + "§7/§f" + maxTotal,
                        "§7プロフィール: §b" + dataScope(player),
                        "§7選択称号: §e" + selectedTitle.displayName())));

        ItemStack experience = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta experienceMeta = experience.getItemMeta();
        ComponentUtils.setDisplayName(experienceMeta, "§a経験値レベル: §f" + player.getLevel());
        experience.setItemMeta(experienceMeta);
        builder.addCustomItemAt(22,
                "§aXP Level §7• §f" + player.getLevel(),
                experience,
                "§7能力値アップに使う現在の経験値レベル\n§8XP point ではなく level を消費します");

        builder.addButtonAt(24,
                "§d称号 §7• §f" + selectedTitle.displayName(),
                Material.NAME_TAG,
                titleSystem == null
                        ? "§7称号システム準備中"
                        : "§7解放済み: §f" + unlockedTitles + "§7/§f" + LevelingTitle.values().length + "\n§eクリックで称号一覧を開く");

        addOverallProgress(builder, total, maxTotal);

        builder.addButtonAt(45,
                "§b使い方",
                Material.WRITABLE_BOOK,
                "§7能力値をクリックしてレベルアップ\n§7必要コストは現在Lvで変化\n§7スキルは必要Lv到達で自動解放");
        builder.addButtonAt(47,
                "§dレベリングの書",
                Material.ENCHANTED_BOOK,
                "§7Emerald / Lapis / Book でクラフト\n§7右クリックでこの画面を開けます");
        builder.addButtonAt(49,
                "§c閉じる",
                Material.BARRIER,
                "§7メニューを閉じる");
        builder.addButtonAt(51,
                "§3Profile Scope §7• §b" + dataScope(player),
                Material.ENDER_EYE,
                "§7Otherworld の world group ごとに\n§7能力値・称号・統計が分離されます");

        builder.then((result, clicked) -> {
            if (result.slot == 49) {
                ChestUI.closeMenu(clicked);
                return;
            }
            if (result.slot == 24) {
                openTitles(clicked);
                return;
            }
            for (int i = 0; i < APTITUDE_SLOTS.length; i++) {
                if (result.slot != APTITUDE_SLOTS[i]) continue;
                levelUp(clicked, aptitudes[i]);
                open(clicked);
                return;
            }
        }).show(player);
    }

    private void addOverallProgress(ChestUI.Builder builder, int total, int maxTotal) {
        int completed = (int) Math.round((double) total / (double) maxTotal * PROGRESS_SLOTS.length);
        completed = Math.max(0, Math.min(PROGRESS_SLOTS.length, completed));
        for (int i = 0; i < PROGRESS_SLOTS.length; i++) {
            boolean done = i < completed;
            builder.addButtonAt(
                    PROGRESS_SLOTS[i],
                    done ? "§a◆ Overall Progress" : "§8◇ Overall Progress",
                    done ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    "§7全能力値: §f" + total + "§7/§f" + maxTotal + "\n§7進行率: §a" + Math.round((double) total / maxTotal * 100.0D) + "%");
        }
    }

    private int totalAptitudeLevel(Player player) {
        int total = 0;
        for (LevelingAptitude aptitude : LevelingAptitude.values()) total += getLevel(player, aptitude);
        return total;
    }

    private void openTitles(Player player) {
        if (titleSystem == null) {
            player.sendMessage("§c称号システムを初期化中です。");
            return;
        }

        ChestUI.Builder builder = ChestUI.builder()
                .title(TITLE_GUI_TITLE)
                .size(54)
                .type("leveling-titles");
        List<LevelingTitle> unlocked = titleSystem.unlocked(player);
        LevelingTitle selected = titleSystem.selected(player);

        int slot = 0;
        for (LevelingTitle title : LevelingTitle.values()) {
            boolean available = unlocked.contains(title);
            boolean active = title == selected;
            String lore = active
                    ? "§a✓ 現在選択中"
                    : available ? "§eクリックで装備" : "§c未解放\n§7Wikiで解放条件を確認できます";
            builder.addButtonAt(
                    slot++,
                    active ? "§a▶ " + title.displayName() : available ? "§e" + title.displayName() : "§8" + title.displayName(),
                    active ? Material.LIME_DYE : available ? Material.NAME_TAG : Material.GRAY_DYE,
                    lore);
        }

        builder.addButtonAt(45,
                "§6称号コレクション",
                Material.NETHER_STAR,
                "§7解放済み: §f" + unlocked.size() + "§7/§f" + LevelingTitle.values().length + "\n§7選択中: §e" + selected.displayName());
        builder.addButtonAt(49,
                "§b能力値へ戻る",
                Material.ARROW,
                "§7クリックで Progression へ戻る");
        builder.addButtonAt(53,
                "§3Profile Scope §7• §b" + dataScope(player),
                Material.ENDER_EYE,
                "§7称号の解放状況も scope ごとに保存されます");

        builder.then((result, clicked) -> {
            if (result.slot == 49) {
                open(clicked);
                return;
            }
            if (result.slot >= 0 && result.slot < LevelingTitle.values().length) {
                LevelingTitle title = LevelingTitle.values()[result.slot];
                if (titleSystem.select(clicked, title.key())) openTitles(clicked);
                else clicked.sendMessage("§cその称号は未解放です。");
            }
        }).show(player);
    }

    public int getLevel(Player player, LevelingAptitude aptitude) {
        if (!isEnabled()) return 0;
        return Math.max(0, Math.min(LevelingAptitude.MAX_LEVEL, data.getInt(path(player, aptitude), 0)));
    }

    public boolean levelUp(Player player, LevelingAptitude aptitude) {
        if (!isEnabled()) {
            player.sendMessage("§cJust Leveling は現在無効です。");
            return false;
        }
        int current = getLevel(player, aptitude);
        if (current >= LevelingAptitude.MAX_LEVEL) {
            player.sendMessage("§c" + aptitude.displayName() + " は最大レベルです。");
            return false;
        }
        int cost = levelCost(current);
        if (player.getLevel() < cost) {
            player.sendMessage("§c経験値レベルが足りません。必要: " + cost + " / 所持: " + player.getLevel());
            return false;
        }
        player.setLevel(player.getLevel() - cost);
        setLevel(player, aptitude, current + 1);
        player.sendMessage("§a" + aptitude.displayName() + " が Lv." + (current + 1) + " になりました。 §7[" + dataScope(player) + "]");
        return true;
    }

    private void setLevel(Player player, LevelingAptitude aptitude, int level) {
        data.set(path(player, aptitude), level);
        save();
        applyPassives(player);
    }

    private int levelCost(int currentLevel) { return FIRST_COST + Math.max(0, currentLevel / 2); }

    private String path(Player player, LevelingAptitude aptitude) {
        String base = "players." + player.getUniqueId() + ".aptitudes." + aptitude.key();
        String scope = dataScope(player);
        return scope.equals("default") ? base : "otherworld." + scope + "." + base;
    }

    private void registerBookRecipe() {
        try {
            Bukkit.removeRecipe(recipeKey);
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, createLevelingBook());
            recipe.shape(" E ", "LBL", " E ");
            recipe.setIngredient('E', Material.EMERALD);
            recipe.setIngredient('L', Material.LAPIS_LAZULI);
            recipe.setIngredient('B', Material.BOOK);
            Bukkit.addRecipe(recipe);
        } catch (IllegalArgumentException ignored) { }
    }

    private void removeBookRecipe() { Bukkit.removeRecipe(recipeKey); }

    @EventHandler public void onJoin(PlayerJoinEvent event) { applyPassives(event.getPlayer()); }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        applyPassives(player);
        player.sendMessage("§7レベリングプロフィール切替: §b" + dataScope(player));
    }

    @EventHandler(ignoreCancelled = true)
    public void onUseBook(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isLevelingBook(event.getItem())) return;
        event.setCancelled(true);
        if (!isEnabled()) {
            event.getPlayer().sendMessage("§cJust Leveling は現在無効です。");
            return;
        }
        open(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker != null) {
            if (event.getDamager() instanceof Projectile) {
                int dex = getLevel(attacker, LevelingAptitude.DEXTERITY);
                event.setDamage(event.getDamage() + LevelingAptitude.DEXTERITY.passiveTier5(dex));
            } else {
                int strength = getLevel(attacker, LevelingAptitude.STRENGTH);
                if (strength >= 10 && attacker.getInventory().getItemInOffHand().getType().isAir()) event.setDamage(event.getDamage() * 1.5D);
                AttributeInstance maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH);
                if (strength >= 30 && maxHealth != null && attacker.getHealth() <= maxHealth.getValue() * 0.30D) event.setDamage(event.getDamage() * 1.5D);
                int luck = getLevel(attacker, LevelingAptitude.LUCK);
                if (luck >= 12 && attacker.getFallDistance() > 0.0F) {
                    int roll = ThreadLocalRandom.current().nextInt(1, 7);
                    if (roll == 6) event.setDamage(event.getDamage() * 1.25D);
                    else if (roll == 1) event.setDamage(event.getDamage() * 0.75D);
                }
            }
        }
        if (event.getEntity() instanceof Player victim) {
            int magic = getLevel(victim, LevelingAptitude.MAGIC);
            int tiers = LevelingAptitude.MAGIC.passiveTier5(magic);
            if (tiers > 0 && isMagicLike(event.getDamager())) event.setDamage(event.getDamage() * Math.max(0.0D, 1.0D - tiers * 0.10D));
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        if (!isEnabled()) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        int magic = getLevel(killer, LevelingAptitude.MAGIC);
        if (magic >= 18) {
            AttributeInstance max = killer.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) killer.setHealth(Math.min(max.getValue(), killer.getHealth() + 1.0D));
        }
        int luck = getLevel(killer, LevelingAptitude.LUCK);
        if (luck >= 22 && ThreadLocalRandom.current().nextInt(100) < 10 && !event.getDrops().isEmpty()) {
            List<ItemStack> original = new ArrayList<>(event.getDrops());
            for (ItemStack stack : original) {
                ItemStack extra = stack.clone();
                extra.setAmount(stack.getAmount());
                event.getDrops().add(extra);
            }
        }
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private boolean isMagicLike(Entity damager) { return damager instanceof Projectile && !(damager instanceof AbstractArrow); }

    private boolean isLevelingBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(bookKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private void applyPassives(Player player) {
        if (!isEnabled()) {
            resetPassives(player);
            return;
        }
        int strength = getLevel(player, LevelingAptitude.STRENGTH);
        int constitution = getLevel(player, LevelingAptitude.CONSTITUTION);
        int dexterity = getLevel(player, LevelingAptitude.DEXTERITY);
        int defense = getLevel(player, LevelingAptitude.DEFENSE);
        int intelligence = getLevel(player, LevelingAptitude.INTELLIGENCE);
        int luck = getLevel(player, LevelingAptitude.LUCK);
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

    private void resetPassives(Player player) {
        setBase(player, Attribute.ATTACK_DAMAGE, 1.0D);
        setBase(player, Attribute.MAX_HEALTH, 20.0D);
        setBase(player, Attribute.KNOCKBACK_RESISTANCE, 0.0D);
        setBase(player, Attribute.MOVEMENT_SPEED, 0.10D);
        setBase(player, Attribute.ARMOR, 0.0D);
        setBase(player, Attribute.ARMOR_TOUGHNESS, 0.0D);
        setBase(player, Attribute.ATTACK_SPEED, 4.0D);
        setBase(player, Attribute.LUCK, 0.0D);
        setBase(player, Attribute.ATTACK_KNOCKBACK, 0.0D);
        setBase(player, Attribute.ENTITY_INTERACTION_RANGE, 3.0D);
        setBase(player, Attribute.BLOCK_INTERACTION_RANGE, 4.5D);
        setBase(player, Attribute.BLOCK_BREAK_SPEED, 1.0D);
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
