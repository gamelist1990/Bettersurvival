package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.IntConsumer;

/** 不吉な焚き火のレシピ、永続識別、熱量UIを管理する。 */
public final class OminousCampfireSystem implements Listener {
    private static final Vector3f SWORD_TRANSLATION = new Vector3f(0.05F, 0.20F, -0.05F);
    private static final Quaternionf SWORD_ROTATION = new Quaternionf(-0.16F, 0.112F, 0.803F, 0.563F);
    private static final Vector3f SWORD_SCALE = new Vector3f(0.55F, 0.55F, 0.55F);
    private static final Transformation SWORD_TRANSFORMATION = new Transformation(SWORD_TRANSLATION, SWORD_ROTATION,
            SWORD_SCALE, new Quaternionf());

    @SuppressWarnings("unused")
    private final Loader plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey displayKey;
    private final NamespacedKey recipeKey;
    private final TrueCrafterSettings settings;
    private final IntConsumer heatChange;
    private final BukkitTask particleTask;

    public OminousCampfireSystem(Loader plugin, TrueCrafterSettings settings, IntConsumer heatChange) {
        this.plugin = plugin;
        this.settings = settings;
        this.heatChange = heatChange;
        itemKey = new NamespacedKey(plugin, "ominous_campfire");
        displayKey = new NamespacedKey(plugin, "ominous_campfire_sword");
        recipeKey = new NamespacedKey(plugin, "ominous_campfire_recipe");
        Bukkit.removeRecipe(recipeKey);
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, createItem());
        recipe.addIngredient(Material.CAMPFIRE);
        recipe.addIngredient(Material.STONE_SWORD);
        if (!Bukkit.addRecipe(recipe)) plugin.getLogger().warning("不吉な焚き火のレシピを登録できませんでした。");
        Bukkit.getOnlinePlayers().forEach(player -> player.discoverRecipe(recipeKey));
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCampfires, 1L, 1L);
    }

    public void shutdown() {
        particleTask.cancel();
        Bukkit.removeRecipe(recipeKey);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(recipeKey);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!isItem(event.getItemInHand())) return;
        if (event.getBlockPlaced().getState() instanceof TileState state) {
            state.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            state.update(true);
            spawnSword(event.getBlockPlaced());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof TileState state)
                || !state.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        if (!event.getPlayer().isOp()) {
            event.getPlayer().sendMessage(Component.text("熱量を変更できるのはOPのみです。", NamedTextColor.RED));
            return;
        }
        openMenu(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof TileState state)
                || !state.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)) return;
        event.setDropItems(false);
        removeSword(event.getBlock());
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), createItem());
    }

    private ItemStack createItem() {
        ItemStack item = new ItemStack(Material.CAMPFIRE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("不吉な焚き火", NamedTextColor.AQUA));
        meta.lore(List.of(Component.text("石の剣を焚き火に刺しただけの、簡素な儀式の道具。", NamedTextColor.GRAY),
                Component.text("炎に触れることで、世界の「むずかしさ」を変えることができる。", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    private void openMenu(org.bukkit.entity.Player player) {
        ChestUI.Builder builder = ChestUI.builder().title("TrueCrafter 熱量: " + settings.heatLevel()).size(27);
        Material[] icons = {Material.COAL, Material.COPPER_INGOT, Material.IRON_INGOT, Material.GOLD_INGOT, Material.NETHERITE_INGOT};
        int[] slots = {11, 12, 13, 14, 15};
        for (int index = 0; index < 5; index++) {
            int level = index + 1;
            builder.addButtonAt(slots[index], "§6熱量 " + level, icons[index],
                    level == settings.heatLevel() ? "§a現在の熱量" : "§7クリックして変更");
        }
        builder.then((result, viewer) -> {
            for (int index = 0; index < slots.length; index++) {
                if (result.slot != slots[index]) continue;
                int level = index + 1;
                heatChange.accept(level);
                viewer.closeInventory();
                broadcastHeat(level);
                break;
            }
        }).show(player);
    }

    private void tickCampfires() {
        for (org.bukkit.World world : Bukkit.getWorlds()) for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
            for (org.bukkit.block.BlockState rawState : chunk.getTileEntities()) {
                if (!(rawState instanceof TileState state)
                        || !state.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)) continue;
                Block block = state.getBlock();
                if (!(block.getBlockData() instanceof Campfire campfire) || !campfire.isLit()) {
                    removeSword(block);
                    block.setType(Material.AIR, true);
                    world.dropItemNaturally(block.getLocation(), createItem());
                    world.playSound(block.getLocation(), Sound.ENTITY_ALLAY_ITEM_TAKEN, 1.0F, 0.0F);
                    continue;
                }
                spawnSword(block);
                particle(block, settings.heatLevel());
            }
        }
    }

    private void particle(Block block, int level) {
        Color[] colors = {Color.fromRGB(85, 255, 85), Color.fromRGB(255, 255, 85), Color.fromRGB(255, 85, 85),
                Color.fromRGB(170, 0, 0), Color.fromRGB(170, 0, 170)};
        spawnNearbyDust(block, colors[level - 1]);
        if (level == 3 && Math.random() < 0.30D)
            block.getWorld().spawnParticle(org.bukkit.Particle.FLAME, block.getLocation().add(0.5, 1, 0.5), 1, 0.3, 0.5, 0.3, 0, null, true);
        if (level == 4 && Math.random() < 0.10D)
            block.getWorld().spawnParticle(org.bukkit.Particle.LAVA, block.getLocation().add(0.5, 0.5, 0.5), 1, 0.5, 0, 0.5, 1, null, true);
        if (level == 4 && Math.random() < 0.50D)
            block.getWorld().spawnParticle(org.bukkit.Particle.FLAME, block.getLocation().add(0.5, 1, 0.5), 1, 0.3, 0.5, 0.3, 0.01D, null, true);
        if (level == 5) {
            if (Math.random() < 0.30D) block.getWorld().spawnParticle(org.bukkit.Particle.LAVA, block.getLocation().add(0.5, 0.5, 0.5), 1, 0.5, 0, 0.5, 1, null, true);
            block.getWorld().spawnParticle(org.bukkit.Particle.TRIAL_SPAWNER_DETECTION,
                    block.getLocation().add(0.5, 1.5, 0.5), 1, 0.3, 0.5, 0.3, 0, null, true);
        }
    }

    private void spawnNearbyDust(Block block, Color color) {
        org.bukkit.Location location = block.getLocation().add(0.5D, 0.5D, 0.5D);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(block.getWorld()) || player.getLocation().distanceSquared(location) > 64.0D) continue;
            player.spawnParticle(org.bukkit.Particle.DUST, location, 1, 0.5D, 0.5D, 0.5D, 0.0D,
                    new org.bukkit.Particle.DustOptions(color, 1.0F));
        }
    }

    private void spawnSword(Block block) {
        ItemDisplay existing = block.getWorld().getNearbyEntities(block.getLocation().add(0.5D, 0.5D, 0.5D), 1.0D, 1.0D, 1.0D).stream()
                .filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast)
                .filter(display -> display.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE))
                .findFirst().orElse(null);
        if (existing != null) {
            configureSwordDisplay(existing, block);
            return;
        }
        block.getWorld().spawn(swordAnchor(block), ItemDisplay.class, display -> {
            ItemStack sword = new ItemStack(Material.STONE_SWORD);
            sword.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
            display.setItemStack(sword);
            configureSwordDisplay(display, block);
        });
    }

    private org.bukkit.Location swordAnchor(Block block) {
        org.bukkit.util.BoundingBox box = block.getBoundingBox();
        return new org.bukkit.Location(block.getWorld(), (box.getMinX() + box.getMaxX()) / 2.0D,
                box.getMaxY() + 0.02D, (box.getMinZ() + box.getMaxZ()) / 2.0D);
    }

    private void configureSwordDisplay(ItemDisplay display, Block block) {
        display.teleport(swordAnchor(block));
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setTransformation(SWORD_TRANSFORMATION);
        display.setPersistent(true);
        display.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void removeSword(Block block) {
        for (org.bukkit.entity.Entity entity : block.getWorld().getNearbyEntities(block.getLocation().add(0.5D, 0.5D, 0.5D), 1.5D, 1.5D, 1.5D)) {
            if (entity instanceof ItemDisplay && entity.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE)) entity.remove();
        }
    }

    private void broadcastHeat(int level) {
        String[] text = {"炎は静かに揺らめいている…", "薪がぱちりと弾けた…", "炎が勢いを増していく…",
                "火花が荒々しく宙を舞う…", "火勢は留まることを知らない…！"};
        NamedTextColor[] colors = {NamedTextColor.GREEN, NamedTextColor.YELLOW, NamedTextColor.RED,
                NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE};
        Bukkit.broadcast(Component.text(text[level - 1], colors[level - 1]));
        Bukkit.broadcast(Component.text("現在の難易度は[火の熱: ", NamedTextColor.WHITE)
                .append(Component.text(level, colors[level - 1])).append(Component.text("]です", NamedTextColor.WHITE)));
    }
}
