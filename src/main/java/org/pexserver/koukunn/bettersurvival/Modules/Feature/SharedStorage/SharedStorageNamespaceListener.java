package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** SharedStorage IDを設置先/存在場所のOtherworld scopeへ透過的に正規化する。 */
public final class SharedStorageNamespaceListener implements Listener {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private final Loader plugin;
    private final NamespacedKey roleKey;
    private final NamespacedKey idKey;

    private SharedStorageNamespaceListener(Loader plugin) {
        this.plugin = plugin;
        this.roleKey = new NamespacedKey(plugin, "shared_storage_role");
        this.idKey = new NamespacedKey(plugin, "shared_storage_id");
    }

    public static void ensureRegistered(Loader plugin) {
        if (plugin == null || !REGISTERED.compareAndSet(false, true)) return;
        SharedStorageNamespaceListener listener = new SharedStorageNamespaceListener(plugin);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        Bukkit.getScheduler().runTask(plugin, listener::scanLoadedChunks);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        scopePdcItem(event.getItemInHand(), event.getBlockPlaced().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        ItemStack stack = entity.getItemStack();
        boolean changed = scopePdcItem(stack, entity.getLocation());
        changed |= scopeMinecartItem(stack, entity.getLocation());
        if (changed) entity.setItemStack(stack);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (event.getEntity() instanceof StorageMinecart minecart) scopeMinecartEntity(minecart);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        scanChunk(event.getChunk());
    }

    private void scanLoadedChunks() {
        for (var world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) scanChunk(chunk);
    }

    private void scanChunk(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) if (state instanceof Barrel barrel) scopeBarrel(barrel);
        for (Entity entity : chunk.getEntities()) if (entity instanceof StorageMinecart minecart) scopeMinecartEntity(minecart);
    }

    private boolean scopePdcItem(ItemStack stack, Location location) {
        if (stack == null || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        String role = meta.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
        String id = meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (role == null || id == null) return false;
        String desiredScope = SharedStorageScopedId.scopeFor(plugin, location);
        if (desiredScope.equals(SharedStorageScopedId.scope(id))) return false;
        String scoped = SharedStorageScopedId.encode(SharedStorageScopedId.raw(id), desiredScope);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, scoped);
        stack.setItemMeta(meta);
        return true;
    }

    private boolean scopeMinecartItem(ItemStack stack, Location location) {
        if (stack == null || stack.getType() != Material.CHEST_MINECART || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (!meta.hasDisplayName()) return false;
        String display = ComponentUtils.getDisplayName(meta);
        String scoped = scopeSubDisplay(display, location);
        if (display.equals(scoped)) return false;
        ComponentUtils.setDisplayName(meta, scoped);
        stack.setItemMeta(meta);
        return true;
    }

    private void scopeMinecartEntity(StorageMinecart minecart) {
        String name = ComponentUtils.legacyText(minecart.customName());
        if (name == null || name.isBlank()) return;
        String scoped = scopeSubDisplay(name, minecart.getLocation());
        if (!name.equals(scoped)) minecart.customName(ComponentUtils.legacy(scoped));
    }

    private String scopeSubDisplay(String display, Location location) {
        if (display == null) return null;
        String lower = display.toLowerCase(Locale.ROOT);
        int start = lower.indexOf("[sub:");
        if (start < 0) return display;
        int idStart = start + 5;
        int end = display.indexOf(']', idStart);
        if (end < 0) end = display.length();
        String id = display.substring(idStart, end).trim();
        if (id.isEmpty()) return display;
        String desiredScope = SharedStorageScopedId.scopeFor(plugin, location);
        if (desiredScope.equals(SharedStorageScopedId.scope(id))) return display;
        String scopedId = SharedStorageScopedId.encode(SharedStorageScopedId.raw(id), desiredScope);
        return display.substring(0, idStart) + scopedId + display.substring(end);
    }

    private void scopeBarrel(Barrel barrel) {
        String name = ComponentUtils.legacyText(barrel.customName());
        if (name == null || name.isBlank()) return;
        String prefix = "chestget-";
        if (!name.toLowerCase(Locale.ROOT).startsWith(prefix)) return;
        String id = name.substring(prefix.length()).trim();
        if (id.isEmpty()) return;
        String desiredScope = SharedStorageScopedId.scopeFor(plugin, barrel.getLocation());
        if (desiredScope.equals(SharedStorageScopedId.scope(id))) return;
        String scoped = SharedStorageScopedId.encode(SharedStorageScopedId.raw(id), desiredScope);
        barrel.customName(ComponentUtils.legacy(prefix + scoped));
        barrel.update(true, false);
    }
}
