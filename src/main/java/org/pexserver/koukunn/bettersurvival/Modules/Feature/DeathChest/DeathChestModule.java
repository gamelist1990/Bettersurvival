package org.pexserver.koukunn.bettersurvival.Modules.Feature.DeathChest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.LandProtectionModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class DeathChestModule implements Listener {
    private static final String FEATURE_KEY = "deathchest";
    private static final int SEARCH_RADIUS = 4;
    private static final int[] Y_OFFSETS = {0, 1, -1, 2, -2, 3, -3};
    private static final int GUI_SIZE = 54; // ラージチェスト同サイズ
    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    // プレイヤーインベントリスロット (Bukkit標準: 0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand)
    private static final int PLAYER_INV_SIZE = 41;
    private static final int PLAYER_SLOT_BOOTS = 36;
    private static final int PLAYER_SLOT_LEGGINGS = 37;
    private static final int PLAYER_SLOT_CHESTPLATE = 38;
    private static final int PLAYER_SLOT_HELMET = 39;
    private static final int PLAYER_SLOT_OFFHAND = 40;

    // GUIレイアウト (54スロット / 6行)
    // Row 0 [ 0- 8] : ホットバー
    // Row 1-3 [ 9-35] : メインインベントリ
    // Row 4 [36-44] : 仕切り (ガラス)
    // Row 5 [45-53] : 装備
    private static final int GUI_HOTBAR_START = 0;
    private static final int GUI_MAIN_START = 9;
    private static final int GUI_SEPARATOR_START = 36;
    private static final int GUI_EQUIP_LABEL_LEFT = 45;
    private static final int GUI_EQUIP_HELMET = 46;
    private static final int GUI_EQUIP_CHESTPLATE = 47;
    private static final int GUI_EQUIP_LEGGINGS = 48;
    private static final int GUI_EQUIP_BOOTS = 49;
    private static final int GUI_EQUIP_MID_SEP = 50;
    private static final int GUI_EQUIP_OFFHAND = 51;
    private static final int GUI_EQUIP_FILL_1 = 52;
    private static final int GUI_EQUIP_FILL_2 = 53;

    // 飾りスロット (取り出し不可)
    private static final Set<Integer> LOCKED_SLOTS = buildLockedSlots();

    private static Set<Integer> buildLockedSlots() {
        Set<Integer> s = new HashSet<>();
        for (int i = 36; i <= 44; i++) s.add(i); // 仕切り行
        s.add(GUI_EQUIP_LABEL_LEFT);
        s.add(GUI_EQUIP_MID_SEP);
        s.add(GUI_EQUIP_FILL_1);
        s.add(GUI_EQUIP_FILL_2);
        return s;
    }

    private final Loader plugin;
    private final ToggleModule toggle;
    private final LandProtectionModule landProtection;
    private final NamespacedKey deathChestKey;
    private final NamespacedKey contentsKey;

    public DeathChestModule(Loader plugin, ToggleModule toggle, LandProtectionModule landProtection) {
        this.plugin = plugin;
        this.toggle = toggle;
        this.landProtection = landProtection;
        this.deathChestKey = new NamespacedKey(plugin, "death_chest");
        this.contentsKey = new NamespacedKey(plugin, "death_chest_contents");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!toggle.getGlobal(FEATURE_KEY)) return;
        if (!toggle.isEnabledFor(event.getPlayer().getUniqueId().toString(), FEATURE_KEY)) return;

        Player player = event.getPlayer();
        Location deathLocation = player.getLocation();
        if (landProtection != null && landProtection.getActiveClaimAt(deathLocation) != null) return;
        if (event.getEntity().getKiller() != null) return;
        if (event.getKeepInventory() || event.getDrops().isEmpty()) return;

        sendDeathLocation(event, deathLocation);

        // プレイヤーインベントリのスロット構造を保ってスナップショット
        PlayerInventory playerInv = player.getInventory();
        ItemStack[] snapshot = new ItemStack[PLAYER_INV_SIZE];
        boolean anyCaptured = false;
        for (int i = 0; i < PLAYER_INV_SIZE; i++) {
            ItemStack item = playerInv.getItem(i);
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                snapshot[i] = item.clone();
                anyCaptured = true;
            }
        }
        if (!anyCaptured) return;

        Block placement = findPlacement(deathLocation);
        if (placement == null) {
            player.sendMessage("§cDeathChestを設置できませんでした。アイテムは通常通りドロップします。");
            return;
        }

        if (!createDeathChest(placement, snapshot)) {
            player.sendMessage("§cDeathChestを作成できませんでした。アイテムは通常通りドロップします。");
            return;
        }

        // 全てスナップショットに退避したので元 drops は消す
        event.getDrops().clear();

        Location chestLocation = placement.getLocation();
        player.sendMessage("§aDeathChestを設置しました: §f" + formatLocation(chestLocation));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) return;
        for (BlockFace face : HORIZONTAL_FACES) {
            if (isDeathChestBlock(event.getBlockPlaced().getRelative(face))) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cDeathChestの隣にチェストを設置することはできません。");
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isDeathChestBlock(block)) return;
        event.setDropItems(false);
        event.setExpToDrop(0);
        BlockState state = block.getState(false);
        if (state instanceof Chest chest) {
            // PDC から復元してドロップ
            String encoded = chest.getPersistentDataContainer().get(contentsKey, PersistentDataType.STRING);
            ItemStack[] items = decodeItems(encoded);
            Location dropLocation = block.getLocation().add(0.5, 0.5, 0.5);
            World world = block.getWorld();
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir() || item.getAmount() <= 0) continue;
                world.dropItemNaturally(dropLocation, item);
            }
            chest.getInventory().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!isDeathChestBlock(block)) return;
        event.setCancelled(true);
        openDeathChestGui(event.getPlayer(), block);
    }

    private void openDeathChestGui(Player player, Block block) {
        BlockState state = block.getState(false);
        if (!(state instanceof Chest chest)) return;

        String encoded = chest.getPersistentDataContainer().get(contentsKey, PersistentDataType.STRING);
        ItemStack[] items = decodeItems(encoded);

        DeathChestHolder holder = new DeathChestHolder(block);
        Inventory gui = Bukkit.createInventory(holder, GUI_SIZE, ComponentUtils.legacy("§8☠ §cDeathChest §8☠"));

        // ホットバー (player 0..8 -> gui 0..8)
        for (int i = 0; i < 9; i++) {
            gui.setItem(GUI_HOTBAR_START + i, items[i]);
        }
        // メイン (player 9..35 -> gui 9..35)
        for (int i = 9; i < 36; i++) {
            gui.setItem(GUI_MAIN_START + (i - 9), items[i]);
        }
        // 仕切り (36..44)
        ItemStack separator = createGlass(Material.BLACK_STAINED_GLASS_PANE, "§8");
        for (int i = 0; i < 9; i++) {
            gui.setItem(GUI_SEPARATOR_START + i, separator);
        }
        // 装備行の飾り
        gui.setItem(GUI_EQUIP_LABEL_LEFT, createGlass(Material.ORANGE_STAINED_GLASS_PANE, "§6装備"));
        gui.setItem(GUI_EQUIP_MID_SEP,    createGlass(Material.BLACK_STAINED_GLASS_PANE, "§8"));
        gui.setItem(GUI_EQUIP_FILL_1,     createGlass(Material.GRAY_STAINED_GLASS_PANE, "§7"));
        gui.setItem(GUI_EQUIP_FILL_2,     createGlass(Material.GRAY_STAINED_GLASS_PANE, "§7"));
        // 装備の実アイテム
        gui.setItem(GUI_EQUIP_HELMET,     items[PLAYER_SLOT_HELMET]);
        gui.setItem(GUI_EQUIP_CHESTPLATE, items[PLAYER_SLOT_CHESTPLATE]);
        gui.setItem(GUI_EQUIP_LEGGINGS,   items[PLAYER_SLOT_LEGGINGS]);
        gui.setItem(GUI_EQUIP_BOOTS,      items[PLAYER_SLOT_BOOTS]);
        gui.setItem(GUI_EQUIP_OFFHAND,    items[PLAYER_SLOT_OFFHAND]);

        holder.setInventory(gui);
        player.openInventory(gui);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof DeathChestHolder)) return;
        InventoryAction action = event.getAction();

        // 上インベントリのクリック
        if (event.getClickedInventory() == top) {
            int slot = event.getSlot();
            // 飾りスロットは完全ロック
            if (LOCKED_SLOTS.contains(slot)) {
                event.setCancelled(true);
                return;
            }
            // 取り出しは許可、置き入れ系はキャンセル
            switch (action) {
                case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR,
                     HOTBAR_SWAP -> event.setCancelled(true);
                default -> {}
            }
            return;
        }
        // 下からの shift-click ブロック
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof DeathChestHolder)) return;
        int topSize = top.getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory gui = event.getInventory();
        if (!(gui.getHolder() instanceof DeathChestHolder holder)) return;
        Block block = holder.block();
        if (block.getType() != Material.CHEST) return;
        BlockState state = block.getState(false);
        if (!(state instanceof Chest chest)) return;

        // GUI -> プレイヤーインベントリ配置 (41 スロット) へ変換
        ItemStack[] items = new ItemStack[PLAYER_INV_SIZE];
        for (int i = 0; i < 9; i++) {
            items[i] = cleanItem(gui.getItem(GUI_HOTBAR_START + i));
        }
        for (int i = 9; i < 36; i++) {
            items[i] = cleanItem(gui.getItem(GUI_MAIN_START + (i - 9)));
        }
        items[PLAYER_SLOT_HELMET]     = cleanItem(gui.getItem(GUI_EQUIP_HELMET));
        items[PLAYER_SLOT_CHESTPLATE] = cleanItem(gui.getItem(GUI_EQUIP_CHESTPLATE));
        items[PLAYER_SLOT_LEGGINGS]   = cleanItem(gui.getItem(GUI_EQUIP_LEGGINGS));
        items[PLAYER_SLOT_BOOTS]      = cleanItem(gui.getItem(GUI_EQUIP_BOOTS));
        items[PLAYER_SLOT_OFFHAND]    = cleanItem(gui.getItem(GUI_EQUIP_OFFHAND));

        boolean anyLeft = false;
        for (ItemStack it : items) {
            if (it != null) { anyLeft = true; break; }
        }

        if (!anyLeft) {
            // 空 -> チェスト消滅
            block.setType(Material.AIR, false);
            return;
        }

        // PDC に再保存
        String encoded = encodeItems(items);
        PersistentDataContainer pdc = chest.getPersistentDataContainer();
        if (encoded != null) {
            pdc.set(contentsKey, PersistentDataType.STRING, encoded);
        }
        // 実チェストのインベントリは常に空 (単一チェストの見た目維持)
        chest.getInventory().clear();
        chest.update(true);
    }

    private ItemStack cleanItem(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return null;
        return item.clone();
    }

    private void sendDeathLocation(PlayerDeathEvent event, Location location) {
        event.getPlayer().sendMessage("§e死亡地点: §f" + formatLocation(location));
    }

    private String formatLocation(Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "unknown" : world.getName();
        return worldName + " X:" + location.getBlockX() + " Y:" + location.getBlockY() + " Z:" + location.getBlockZ();
    }

    private Block findPlacement(Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();
        for (int yOffset : Y_OFFSETS) {
            int y = baseY + yOffset;
            if (y < world.getMinHeight() || y >= world.getMaxHeight() - 1) continue;
            for (int radius = 0; radius <= SEARCH_RADIUS; radius++) {
                for (int x = baseX - radius; x <= baseX + radius; x++) {
                    for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                        if (Math.max(Math.abs(x - baseX), Math.abs(z - baseZ)) != radius) continue;
                        Block candidate = world.getBlockAt(x, y, z);
                        if (!canPlaceChest(candidate)) continue;
                        if (!canPlaceAbove(candidate)) continue;
                        if (hasAdjacentChest(candidate)) continue;
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean canPlaceChest(Block block) {
        return block.getType().isAir();
    }

    private boolean canPlaceAbove(Block block) {
        return block.getRelative(BlockFace.UP).getType().isAir();
    }

    private boolean hasAdjacentChest(Block block) {
        for (BlockFace face : HORIZONTAL_FACES) {
            Material type = block.getRelative(face).getType();
            if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
                return true;
            }
        }
        return false;
    }

    private boolean createDeathChest(Block block, ItemStack[] snapshot) {
        block.setType(Material.CHEST, false);
        BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Chest chestData) {
            chestData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
            block.setBlockData(chestData, true);
        }
        BlockState state = block.getState(false);
        if (!(state instanceof Chest chest)) {
            block.setType(Material.AIR, false);
            return false;
        }
        String encoded = encodeItems(snapshot);
        if (encoded == null) {
            block.setType(Material.AIR, false);
            return false;
        }
        PersistentDataContainer container = chest.getPersistentDataContainer();
        container.set(deathChestKey, PersistentDataType.BYTE, (byte) 1);
        container.set(contentsKey, PersistentDataType.STRING, encoded);
        chest.update(true);
        return true;
    }

    // 1.21+ 推奨: ItemStack.serializeAsBytes / deserializeBytes
    // フォーマット: int length | 各要素 [int len (-1=null) | byte[len]]
    private String encodeItems(ItemStack[] items) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(items.length);
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                    dos.writeInt(-1);
                } else {
                    byte[] bytes = item.serializeAsBytes();
                    dos.writeInt(bytes.length);
                    dos.write(bytes);
                }
            }
            dos.flush();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to encode DeathChest contents", e);
            return null;
        }
    }

    private ItemStack[] decodeItems(String encoded) {
        if (encoded == null || encoded.isEmpty()) return new ItemStack[PLAYER_INV_SIZE];
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             DataInputStream dis = new DataInputStream(bais)) {
            int length = dis.readInt();
            int size = Math.max(length, PLAYER_INV_SIZE);
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < length; i++) {
                int len = dis.readInt();
                if (len < 0) {
                    items[i] = null;
                } else {
                    byte[] bytes = new byte[len];
                    dis.readFully(bytes);
                    items[i] = ItemStack.deserializeBytes(bytes);
                }
            }
            return items;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to decode DeathChest contents", e);
            return new ItemStack[PLAYER_INV_SIZE];
        }
    }

    private ItemStack createGlass(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ComponentUtils.legacy(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isDeathChestBlock(Block block) {
        if (block.getType() != Material.CHEST) return false;
        BlockState state = block.getState(false);
        if (!(state instanceof Chest chest)) return false;
        return chest.getPersistentDataContainer().has(deathChestKey, PersistentDataType.BYTE);
    }

    private static final class DeathChestHolder implements InventoryHolder {
        private final Block block;
        private Inventory inventory;

        private DeathChestHolder(Block block) {
            this.block = block;
        }

        private Block block() {
            return block;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
