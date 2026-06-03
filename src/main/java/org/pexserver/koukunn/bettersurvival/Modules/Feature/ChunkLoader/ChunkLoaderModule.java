package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChunkLoader;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ItemCombineModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ChunkLoaderModule implements Listener {

    public static final String FEATURE_KEY = "chunkloader";
    private static final String ITEM_NAME = "§bチャンクローダー";
    private static final String DISPLAY_LABEL = "§bチャンクローダー";
    private static final int DISPLAY_RADIUS_SQUARED = 16 * 16;

    private final Loader plugin;
    private final ToggleModule toggle;
    private final ChunkLoaderStore store;
    private final NamespacedKey itemKey;
    private final NamespacedKey displayKey;
    private final Map<String, ChunkLoaderRecord> loaders = new LinkedHashMap<>();
    private final Map<UUID, String> ownerIndex = new LinkedHashMap<>();
    private final Map<String, UUID> displayIds = new LinkedHashMap<>();
    private final BukkitTask featureSyncTask;
    private final BukkitTask displayTask;

    private boolean activeChunkTickets;

    public ChunkLoaderModule(Loader plugin, ToggleModule toggle, ItemCombineModule itemCombineModule) {
        this.plugin = plugin;
        this.toggle = toggle;
        this.store = new ChunkLoaderStore(plugin.getConfigManager());
        this.itemKey = new NamespacedKey(plugin, "chunk_loader_sensor");
        this.displayKey = new NamespacedKey(plugin, "chunk_loader_display");

        itemCombineModule.recipe("chunk_loader_sensor")
                .first(this::isPlainCompass)
                .second(this::isChunkLoaderNameTag)
                .groundRadius(0.5D)
                .airRadius(1.5D)
                .verticalRadius(2.0D)
                .allowAirCombine(true)
                .then(this::craftChunkLoader);

        restoreLoaders();
        activeChunkTickets = isFeatureEnabled();
        if (activeChunkTickets) {
            applyChunkTicketsForAll(true);
        }
        tickDisplays();
        featureSyncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncFeatureState, 40L, 40L);
        displayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickDisplays, 40L, 40L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        if (!isChunkLoaderItem(hand)) {
            return;
        }
        if (!isFeatureEnabled()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cチャンクローダー機能は現在無効です");
            return;
        }
        Player player = event.getPlayer();
        String existingKey = ownerIndex.get(player.getUniqueId());
        if (existingKey != null && loaders.containsKey(existingKey)) {
            ChunkLoaderRecord existing = loaders.get(existingKey);
            Location location = existing.location();
            event.setCancelled(true);
            player.sendMessage("§c既に設置済みです。座標 " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()
                    + " にある チャンクローダーを壊してください");
            return;
        }

        Location location = event.getBlockPlaced().getLocation();
        String key = ChunkLoaderStore.toKey(location);
        ChunkLoaderRecord record = new ChunkLoaderRecord(player.getUniqueId(), location);
        loaders.put(key, record);
        ownerIndex.put(player.getUniqueId(), key);
        store.save(location, player.getUniqueId());
        if (activeChunkTickets) {
            setChunkLoaderTickets(location, true);
        }
        ensureDisplayState(location);
        player.sendMessage("§aチャンクローダーを設置しました (3x3 chunk)");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        ChunkLoaderRecord record = removeChunkLoader(block);
        if (record == null) {
            return;
        }

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.1D, 0.5D), createChunkLoaderItem());
        }
        event.getPlayer().sendMessage("§eチャンクローダーを解除しました");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : new ArrayList<>(event.blockList())) {
            removeChunkLoader(block);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : new ArrayList<>(event.blockList())) {
            removeChunkLoader(block);
        }
    }

    private void craftChunkLoader(ItemCombineModule.CombineMatch match) {
        if (!isFeatureEnabled()) {
            return;
        }
        if (!match.first().isValid() || !match.second().isValid()) {
            return;
        }
        Location center = match.center();
        if (center.getWorld() == null) {
            return;
        }
        match.consumeMatchedItems(1, 1);
        center.getWorld().dropItemNaturally(center, createChunkLoaderItem());
        center.getWorld().playSound(center, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.8F, 1.0F);
    }

    private void syncFeatureState() {
        boolean enabled = isFeatureEnabled();
        if (enabled == activeChunkTickets) {
            return;
        }
        activeChunkTickets = enabled;
        applyChunkTicketsForAll(enabled);
    }

    private void restoreLoaders() {
        for (Map.Entry<Location, UUID> entry : store.loadAll().entrySet()) {
            Location location = entry.getKey();
            UUID owner = entry.getValue();
            if (location.getWorld() == null) {
                continue;
            }
            String key = ChunkLoaderStore.toKey(location);
            if (ownerIndex.containsKey(owner)) {
                store.removeByKey(key);
                continue;
            }
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            if (location.getWorld().isChunkLoaded(chunkX, chunkZ) && location.getBlock().getType() != Material.CALIBRATED_SCULK_SENSOR) {
                store.removeByKey(key);
                continue;
            }
            ChunkLoaderRecord record = new ChunkLoaderRecord(owner, location);
            loaders.put(key, record);
            ownerIndex.put(owner, key);
        }
    }

    private void applyChunkTicketsForAll(boolean enable) {
        for (ChunkLoaderRecord record : new ArrayList<>(loaders.values())) {
            setChunkLoaderTickets(record.location(), enable);
        }
    }

    private void tickDisplays() {
        for (Map.Entry<String, ChunkLoaderRecord> entry : new ArrayList<>(loaders.entrySet())) {
            String key = entry.getKey();
            ChunkLoaderRecord record = entry.getValue();
            Location location = record.location();
            World world = location.getWorld();
            if (world == null) {
                removeDisplay(key);
                continue;
            }
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                removeDisplay(key);
                continue;
            }
            if (location.getBlock().getType() != Material.CALIBRATED_SCULK_SENSOR) {
                removeChunkLoaderByKey(key);
                continue;
            }
            ensureDisplayState(location);
        }
    }

    private void ensureDisplayState(Location location) {
        String key = ChunkLoaderStore.toKey(location);
        if (!hasNearbyPlayer(location)) {
            removeDisplay(key);
            return;
        }
        TextDisplay display = getTrackedDisplay(key);
        if (display == null) {
            display = findNearbyDisplay(location, key);
            if (display == null) {
                display = spawnDisplay(location, key);
            }
            displayIds.put(key, display.getUniqueId());
        }
    }

    private boolean hasNearbyPlayer(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= DISPLAY_RADIUS_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private TextDisplay getTrackedDisplay(String key) {
        UUID uuid = displayIds.get(key);
        if (uuid == null) {
            return null;
        }
        if (!(Bukkit.getEntity(uuid) instanceof TextDisplay display) || !display.isValid()) {
            displayIds.remove(key);
            return null;
        }
        return display;
    }

    private TextDisplay findNearbyDisplay(Location location, String key) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(location.clone().add(0.5D, 1.1D, 0.5D), 0.8D, 1.2D, 0.8D)) {
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            PersistentDataContainer container = display.getPersistentDataContainer();
            String stored = container.get(displayKey, PersistentDataType.STRING);
            if (key.equals(stored)) {
                return display;
            }
        }
        return null;
    }

    private TextDisplay spawnDisplay(Location location, String key) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        Location spawn = location.clone().add(0.5D, 1.15D, 0.5D);
        return world.spawn(spawn, TextDisplay.class, display -> {
            display.text(ComponentUtils.legacy(DISPLAY_LABEL));
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(false);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9F, 0.9F, 0.9F), new AxisAngle4f()));
            display.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, key);
        });
    }

    private void removeDisplay(String key) {
        TextDisplay display = getTrackedDisplay(key);
        if (display != null) {
            display.remove();
        }
        displayIds.remove(key);
    }

    private void removeChunkLoaderByKey(String key) {
        ChunkLoaderRecord record = loaders.remove(key);
        if (record == null) {
            return;
        }
        ownerIndex.remove(record.owner());
        setChunkLoaderTickets(record.location(), false);
        store.remove(record.location());
        removeDisplay(key);
    }

    private ChunkLoaderRecord removeChunkLoader(Block block) {
        String key = ChunkLoaderStore.toKey(block.getLocation());
        ChunkLoaderRecord record = loaders.remove(key);
        if (record == null) {
            return null;
        }
        ownerIndex.remove(record.owner());
        setChunkLoaderTickets(record.location(), false);
        store.remove(record.location());
        removeDisplay(key);
        return record;
    }

    private void setChunkLoaderTickets(Location center, boolean enable) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int chunkX = center.getBlockX() >> 4;
        int chunkZ = center.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int targetX = chunkX + dx;
                int targetZ = chunkZ + dz;
                if (enable) {
                    world.addPluginChunkTicket(targetX, targetZ, plugin);
                } else {
                    world.removePluginChunkTicket(targetX, targetZ, plugin);
                }
            }
        }
    }

    private ItemStack createChunkLoaderItem() {
        ItemStack stack = new ItemStack(Material.CALIBRATED_SCULK_SENSOR);
        ItemMeta meta = stack.getItemMeta();
        ComponentUtils.setDisplayName(meta, ITEM_NAME);
        ComponentUtils.setLore(meta,
                "§73x3 chunk を常時ロードします",
                "§71プレイヤー1個まで設置可能",
                "§7破壊するとチャンクロードが停止します");
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "true");
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isChunkLoaderItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.CALIBRATED_SCULK_SENSOR || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING);
    }

    private boolean isPlainCompass(ItemStack stack) {
        return stack != null && stack.getType() == Material.COMPASS;
    }

    private boolean isChunkLoaderNameTag(ItemStack stack) {
        if (stack == null || stack.getType() != Material.NAME_TAG || !stack.hasItemMeta()) {
            return false;
        }
        String displayName = ComponentUtils.getDisplayName(stack.getItemMeta());
        if (displayName == null) {
            return false;
        }
        String plain = stripLegacyColors(displayName).trim();
        return plain.equalsIgnoreCase("chunkloader");
    }

    private String stripLegacyColors(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?i)§[0-9A-FK-ORX]", "");
    }

    private boolean isFeatureEnabled() {
        return toggle.getGlobal(FEATURE_KEY);
    }

    public void shutdown() {
        featureSyncTask.cancel();
        displayTask.cancel();
        applyChunkTicketsForAll(false);
        for (String key : new ArrayList<>(displayIds.keySet())) {
            removeDisplay(key);
        }
    }

    private record ChunkLoaderRecord(UUID owner, Location location) {
    }
}
