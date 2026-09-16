package org.pexserver.koukunn.bettersurvival.Modules.Feature.Invsee;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.FormsUtil;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Api.McApiClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** InvSee UI - Otherworldグループ単位でプレイヤーデータを閲覧・編集する。 */
@SuppressWarnings("deprecation")
public class InvseeUI {
    public static final String TITLE_PLAYER_SELECT = "§8InvSee - プレイヤー選択";
    public static final String TITLE_INVENTORY = "§8InvSee - ";
    public static final String TITLE_EQUIPMENT = "§8InvSee 装備 - ";
    public static final String TITLE_ENDERCHEST = "§8InvSee EC - ";

    public static class InvseeHolder implements InventoryHolder {
        private Inventory inventory;
        private final InvseeUIType uiType;
        private final OfflinePlayer targetPlayer;
        private final Loader plugin;
        private final String scope;
        private int page;
        private List<OfflinePlayer> playerList;

        public InvseeHolder(InvseeUIType uiType, OfflinePlayer targetPlayer, Loader plugin, String scope) {
            this.uiType = uiType;
            this.targetPlayer = targetPlayer;
            this.plugin = plugin;
            this.scope = scope == null || scope.isBlank() ? "default" : scope;
            this.page = 0;
        }

        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public InvseeUIType getUIType() { return uiType; }
        public OfflinePlayer getTargetPlayer() { return targetPlayer; }
        public Loader getPlugin() { return plugin; }
        public String getScope() { return scope; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public List<OfflinePlayer> getPlayerList() { return playerList; }
        public void setPlayerList(List<OfflinePlayer> playerList) { this.playerList = playerList; }
    }

    public enum InvseeUIType { PLAYER_SELECT, MAIN_INVENTORY, EQUIPMENT, ENDERCHEST }

    public static InvseeHolder getHolder(Inventory inv) {
        if (inv == null) return null;
        return inv.getHolder() instanceof InvseeHolder holder ? holder : null;
    }

    public static boolean isInvseeUI(Inventory inv) { return getHolder(inv) != null; }

    public static String resolveScope(Player viewer, Loader plugin) {
        if (viewer == null || plugin.getOtherworldModule() == null) return "default";
        return plugin.getOtherworldModule().getGroup(viewer);
    }

    public static void openPlayerSelectUI(Player viewer, Loader plugin, int page) {
        if (FloodgateUtil.isBedrock(viewer)) {
            openBedrockPlayerSelectForm(viewer, plugin, page);
            return;
        }

        String scope = resolveScope(viewer, plugin);
        List<OfflinePlayer> allPlayers = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(viewer.getUniqueId())
                    && (plugin.getOtherworldModule() == null || plugin.getOtherworldModule().sameGroup(viewer, p))) {
                allPlayers.add(p);
            }
        }

        List<OfflinePlayer> offlinePlayers = new ArrayList<>();
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.isOnline() || op.getName() == null) continue;
            if (!InvseeOfflineData.hasData(op, scope)) continue;
            offlinePlayers.add(op);
        }
        offlinePlayers.sort((a, b) -> Long.compare(b.getLastPlayed(), a.getLastPlayed()));
        for (int i = 0; i < Math.min(50, offlinePlayers.size()); i++) allPlayers.add(offlinePlayers.get(i));

        int itemsPerPage = 45;
        int totalPages = Math.max(1, (allPlayers.size() + itemsPerPage - 1) / itemsPerPage);
        page = Math.max(0, Math.min(page, totalPages - 1));

        InvseeHolder holder = new InvseeHolder(InvseeUIType.PLAYER_SELECT, null, plugin, scope);
        holder.setPage(page);
        holder.setPlayerList(allPlayers);
        Inventory inv = ComponentUtils.createInventory(holder, 54,
                TITLE_PLAYER_SELECT + " [" + scope + "] (" + (page + 1) + "/" + totalPages + ")");
        holder.setInventory(inv);

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, allPlayers.size());
        int slot = 0;
        for (int i = start; i < end; i++) {
            OfflinePlayer target = allPlayers.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                String name = target.getName() != null ? target.getName() : "Unknown";
                boolean isOnline = target.isOnline();
                ComponentUtils.setDisplayName(meta, (isOnline ? "§a" : "§7") + name);
                List<String> lore = new ArrayList<>();
                lore.add(isOnline ? "§a● オンライン" : "§7● オフライン");
                lore.add("§7グループ: §f" + scope);
                if (!isOnline && target.getLastPlayed() > 0) {
                    lore.add("§7最終ログイン: §f" + formatTimeAgo(System.currentTimeMillis() - target.getLastPlayed()));
                }
                lore.add("");
                lore.add("§eクリックでインベントリを表示");
                ComponentUtils.setLore(meta, lore);
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        if (allPlayers.isEmpty()) inv.setItem(22, createItem(Material.BARRIER, "§cこのグループに対象プレイヤーがいません"));
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        if (page > 0) inv.setItem(45, createItem(Material.ARROW, "§e前のページ"));
        inv.setItem(49, createItem(Material.BARRIER, "§c閉じる"));
        if (page < totalPages - 1) inv.setItem(53, createItem(Material.ARROW, "§e次のページ"));
        viewer.openInventory(inv);
    }

    public static void openInventoryUI(Player viewer, OfflinePlayer target, Loader plugin) {
        String scope = resolveScope(viewer, plugin);
        if (target.isOnline()) {
            Player online = target.getPlayer();
            if (online != null && plugin.getOtherworldModule() != null && !plugin.getOtherworldModule().sameGroup(viewer, online)) {
                viewer.sendMessage("§c[InvSee] 異なるOtherworldグループのプレイヤーは閲覧できません");
                return;
            }
        } else if (!InvseeOfflineData.hasData(target, scope)) {
            viewer.sendMessage("§c[InvSee] §f" + (target.getName() != null ? target.getName() : "Unknown")
                    + " §7の §f" + scope + " §7用オフライン保存データがありません");
            return;
        }

        if (FloodgateUtil.isBedrock(viewer)) {
            openBedrockInventoryForm(viewer, target, plugin);
            return;
        }

        boolean isOnline = target.isOnline();
        InvseeHolder holder = new InvseeHolder(InvseeUIType.MAIN_INVENTORY, target, plugin, scope);
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        String statusPrefix = isOnline ? "§a" : "§7";
        Inventory inv = ComponentUtils.createInventory(holder, 54, TITLE_INVENTORY + statusPrefix + targetName + " §8[" + scope + "]");
        holder.setInventory(inv);

        ItemStack[] contents = getPlayerInventoryContents(target, scope);
        for (int i = 0; i < 36 && i < contents.length; i++) {
            int uiSlot = i < 9 ? i + 27 : i - 9;
            ItemStack item = contents[i];
            if (item != null) inv.setItem(uiSlot, item.clone());
        }

        ItemStack separator = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 36; i < 45; i++) inv.setItem(i, separator);
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(45, createItem(Material.ARROW, "§e戻る（プレイヤー選択）"));
        inv.setItem(47, createItem(Material.DIAMOND_CHESTPLATE, "§b装備スロット",
                "§7ヘルメット、チェストプレート、", "§7レギンス、ブーツ、オフハンドを表示"));

        ItemStack infoHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta infoMeta = (SkullMeta) infoHead.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setOwningPlayer(target);
            ComponentUtils.setDisplayName(infoMeta, statusPrefix + "§l" + targetName);
            ComponentUtils.setLore(infoMeta, List.of(
                    isOnline ? "§a● オンライン" : "§7● オフライン",
                    "§7グループ: §f" + scope,
                    "§7UUID: §f" + target.getUniqueId().toString().substring(0, 8) + "..."));
            infoHead.setItemMeta(infoMeta);
        }
        inv.setItem(49, infoHead);
        inv.setItem(51, createItem(Material.ENDER_CHEST, "§dエンダーチェスト", "§7プレイヤーのエンダーチェストを表示"));
        inv.setItem(53, createItem(Material.BARRIER, "§c閉じる"));
        viewer.openInventory(inv);
    }

    public static void openEquipmentUI(Player viewer, OfflinePlayer target, Loader plugin) {
        String scope = resolveScope(viewer, plugin);
        InvseeHolder holder = new InvseeHolder(InvseeUIType.EQUIPMENT, target, plugin, scope);
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        boolean isOnline = target.isOnline();
        String statusPrefix = isOnline ? "§a" : "§7";
        Inventory inv = ComponentUtils.createInventory(holder, 27, TITLE_EQUIPMENT + statusPrefix + targetName + " §8[" + scope + "]");
        holder.setInventory(inv);

        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, border);
        ItemStack[] armor = getPlayerArmorContents(target, scope);
        ItemStack offhand = getPlayerOffhand(target, scope);
        inv.setItem(10, armor[3] != null ? armor[3].clone() : createPlaceholder(Material.GLASS_PANE, "§7ヘルメット", "§8空きスロット"));
        inv.setItem(11, armor[2] != null ? armor[2].clone() : createPlaceholder(Material.GLASS_PANE, "§7チェストプレート", "§8空きスロット"));
        inv.setItem(12, armor[1] != null ? armor[1].clone() : createPlaceholder(Material.GLASS_PANE, "§7レギンス", "§8空きスロット"));
        inv.setItem(13, armor[0] != null ? armor[0].clone() : createPlaceholder(Material.GLASS_PANE, "§7ブーツ", "§8空きスロット"));
        inv.setItem(15, offhand != null ? offhand.clone() : createPlaceholder(Material.GLASS_PANE, "§7オフハンド", "§8空きスロット"));
        inv.setItem(22, createItem(Material.ARROW, "§e戻る（インベントリ）"));
        viewer.openInventory(inv);
    }

    public static void openEnderchestUI(Player viewer, OfflinePlayer target, Loader plugin) {
        String scope = resolveScope(viewer, plugin);
        if (target.isOnline()) {
            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null && (plugin.getOtherworldModule() == null || plugin.getOtherworldModule().sameGroup(viewer, onlineTarget))) {
                viewer.openInventory(onlineTarget.getEnderChest());
                return;
            }
        }

        InvseeHolder holder = new InvseeHolder(InvseeUIType.ENDERCHEST, target, plugin, scope);
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        Inventory inv = ComponentUtils.createInventory(holder, 36, TITLE_ENDERCHEST + "§7" + targetName + " §8[" + scope + "]");
        holder.setInventory(inv);
        ItemStack[] ecContents = getPlayerEnderchestContents(target, scope);
        for (int i = 0; i < 27 && i < ecContents.length; i++) if (ecContents[i] != null) inv.setItem(i, ecContents[i].clone());
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 27; i < 36; i++) inv.setItem(i, border);
        inv.setItem(31, createItem(Material.ARROW, "§e戻る（インベントリ）"));
        viewer.openInventory(inv);
    }

    public static ItemStack[] getPlayerInventoryContents(OfflinePlayer target) { return getPlayerInventoryContents(target, "default"); }
    public static ItemStack[] getPlayerInventoryContents(OfflinePlayer target, String scope) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) return p.getInventory().getStorageContents();
        }
        return InvseeOfflineData.getInventoryContents(target, scope);
    }

    public static ItemStack[] getPlayerArmorContents(OfflinePlayer target) { return getPlayerArmorContents(target, "default"); }
    public static ItemStack[] getPlayerArmorContents(OfflinePlayer target, String scope) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) return p.getInventory().getArmorContents();
        }
        return InvseeOfflineData.getArmorContents(target, scope);
    }

    public static ItemStack getPlayerOffhand(OfflinePlayer target) { return getPlayerOffhand(target, "default"); }
    public static ItemStack getPlayerOffhand(OfflinePlayer target, String scope) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) return p.getInventory().getItemInOffHand();
        }
        return InvseeOfflineData.getOffhandItem(target, scope);
    }

    public static ItemStack[] getPlayerEnderchestContents(OfflinePlayer target) { return getPlayerEnderchestContents(target, "default"); }
    public static ItemStack[] getPlayerEnderchestContents(OfflinePlayer target, String scope) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) return p.getEnderChest().getContents();
        }
        return InvseeOfflineData.getEnderchestContents(target, scope);
    }

    public static void setPlayerInventoryContents(OfflinePlayer target, ItemStack[] contents) { setPlayerInventoryContents(target, "default", contents); }
    public static void setPlayerInventoryContents(OfflinePlayer target, String scope, ItemStack[] contents) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) { p.getInventory().setStorageContents(contents); return; }
        }
        InvseeOfflineData.setInventoryContents(target, scope, contents);
    }

    public static void setPlayerArmorContents(OfflinePlayer target, ItemStack[] armor) { setPlayerArmorContents(target, "default", armor); }
    public static void setPlayerArmorContents(OfflinePlayer target, String scope, ItemStack[] armor) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) { p.getInventory().setArmorContents(armor); return; }
        }
        InvseeOfflineData.setArmorContents(target, scope, armor);
    }

    public static void setPlayerOffhand(OfflinePlayer target, ItemStack item) { setPlayerOffhand(target, "default", item); }
    public static void setPlayerOffhand(OfflinePlayer target, String scope, ItemStack item) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) { p.getInventory().setItemInOffHand(item); return; }
        }
        InvseeOfflineData.setOffhandItem(target, scope, item);
    }

    public static void setPlayerEnderchestContents(OfflinePlayer target, ItemStack[] contents) { setPlayerEnderchestContents(target, "default", contents); }
    public static void setPlayerEnderchestContents(OfflinePlayer target, String scope, ItemStack[] contents) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) { p.getEnderChest().setContents(contents); return; }
        }
        InvseeOfflineData.setEnderchestContents(target, scope, contents);
    }

    private static void openBedrockPlayerSelectForm(Player viewer, Loader plugin, int page) {
        String scope = resolveScope(viewer, plugin);
        List<FormsUtil.ButtonSpec> buttons = new ArrayList<>();
        List<OfflinePlayer> allPlayers = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(viewer.getUniqueId())
                    && (plugin.getOtherworldModule() == null || plugin.getOtherworldModule().sameGroup(viewer, p))) allPlayers.add(p);
        }
        List<OfflinePlayer> offlinePlayers = new ArrayList<>();
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.isOnline() || op.getName() == null || !InvseeOfflineData.hasData(op, scope)) continue;
            offlinePlayers.add(op);
        }
        offlinePlayers.sort((a, b) -> Long.compare(b.getLastPlayed(), a.getLastPlayed()));
        for (int i = 0; i < Math.min(20, offlinePlayers.size()); i++) allPlayers.add(offlinePlayers.get(i));

        for (OfflinePlayer target : allPlayers) {
            String name = target.getName() != null ? target.getName() : "Unknown";
            boolean isOnline = target.isOnline();
            String prefix = isOnline ? "●" : "○";
            boolean isBedrock = FloodgateUtil.isBedrock(target.getUniqueId());
            String displayName = isBedrock ? FloodgateUtil.stripPrefix(name).replace("_", " ") : name;
            String url = McApiClient.getFaceUrl(target.getUniqueId(), name, isBedrock);
            buttons.add(FormsUtil.ButtonSpec.ofUrl(prefix + " " + displayName, url));
        }
        buttons.add(FormsUtil.ButtonSpec.ofText("閉じる"));
        FormsUtil.openSimpleForm(viewer, "InvSee [" + scope + "] - プレイヤー選択", buttons, idx -> {
            if (idx < 0 || idx >= allPlayers.size()) return;
            openBedrockInventoryForm(viewer, allPlayers.get(idx), plugin);
        });
    }

    private static void openBedrockInventoryForm(Player viewer, OfflinePlayer target, Loader plugin) {
        String name = target.getName() != null ? target.getName() : "Unknown";
        List<String> options = Arrays.asList("メインインベントリ", "装備スロット", "エンダーチェスト", "戻る");
        FormsUtil.openSimpleForm(viewer, "InvSee - " + name, options, idx -> {
            if (idx < 0) return;
            switch (idx) {
                case 0 -> Bukkit.getScheduler().runTask(plugin, () -> openInventoryUIForBedrock(viewer, target, plugin));
                case 1 -> Bukkit.getScheduler().runTask(plugin, () -> openEquipmentUI(viewer, target, plugin));
                case 2 -> Bukkit.getScheduler().runTask(plugin, () -> openEnderchestUI(viewer, target, plugin));
                case 3 -> Bukkit.getScheduler().runTask(plugin, () -> openPlayerSelectUI(viewer, plugin, 0));
                default -> { }
            }
        });
    }

    private static void openInventoryUIForBedrock(Player viewer, OfflinePlayer target, Loader plugin) {
        String scope = resolveScope(viewer, plugin);
        InvseeHolder holder = new InvseeHolder(InvseeUIType.MAIN_INVENTORY, target, plugin, scope);
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        boolean isOnline = target.isOnline();
        String statusPrefix = isOnline ? "§a" : "§7";
        Inventory inv = ComponentUtils.createInventory(holder, 54, TITLE_INVENTORY + statusPrefix + targetName + " §8[" + scope + "]");
        holder.setInventory(inv);
        ItemStack[] contents = getPlayerInventoryContents(target, scope);
        for (int i = 0; i < 36 && i < contents.length; i++) {
            int uiSlot = i < 9 ? i + 27 : i - 9;
            ItemStack item = contents[i];
            if (item != null) inv.setItem(uiSlot, item.clone());
        }
        ItemStack separator = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 36; i < 45; i++) inv.setItem(i, separator);
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(45, createItem(Material.ARROW, "§e戻る"));
        inv.setItem(47, createItem(Material.DIAMOND_CHESTPLATE, "§b装備スロット"));
        inv.setItem(51, createItem(Material.ENDER_CHEST, "§dエンダーチェスト"));
        inv.setItem(53, createItem(Material.BARRIER, "§c閉じる"));
        viewer.openInventory(inv);
    }

    private static ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ComponentUtils.setDisplayName(meta, name);
            if (lore.length > 0) ComponentUtils.setLore(meta, Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createPlaceholder(Material mat, String name, String... lore) { return createItem(mat, name, lore); }

    private static String formatTimeAgo(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "秒前";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "分前";
        long hours = minutes / 60;
        if (hours < 24) return hours + "時間前";
        long days = hours / 24;
        if (days < 30) return days + "日前";
        long months = days / 30;
        if (months < 12) return months + "ヶ月前";
        return (days / 365) + "年前";
    }
}
