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
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.FormsUtil;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.*;

/**
 * InvSee UI - プレイヤーインベントリ閲覧・編集UI
 * 
 * 機能:
 * - プレイヤー選択UI（オンライン/オフライン両対応）
 * - インベントリ閲覧UI（複数ページ式）
 *   - メインインベントリ
 *   - 装備スロット
 *   - オフハンド
 *   - エンダーチェスト
 * - アイテムの取得・入れ替え対応
 * - オフラインプレイヤー対応
 */
public class InvseeUI {

    public static final String TITLE_PLAYER_SELECT = "§8InvSee - プレイヤー選択";
    public static final String TITLE_INVENTORY = "§8InvSee - ";
    public static final String TITLE_EQUIPMENT = "§8InvSee 装備 - ";
    public static final String TITLE_ENDERCHEST = "§8InvSee EC - ";

    // ========== カスタム InventoryHolder ==========

    /**
     * InvSee UI用のHolder
     */
    public static class InvseeHolder implements InventoryHolder {
        private Inventory inventory;
        private final InvseeUIType uiType;
        private final OfflinePlayer targetPlayer;
        private final Loader plugin;
        private int page;
        private List<OfflinePlayer> playerList;

        public InvseeHolder(InvseeUIType uiType, OfflinePlayer targetPlayer, Loader plugin) {
            this.uiType = uiType;
            this.targetPlayer = targetPlayer;
            this.plugin = plugin;
            this.page = 0;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public InvseeUIType getUIType() { return uiType; }
        public OfflinePlayer getTargetPlayer() { return targetPlayer; }
        public Loader getPlugin() { return plugin; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public List<OfflinePlayer> getPlayerList() { return playerList; }
        public void setPlayerList(List<OfflinePlayer> playerList) { this.playerList = playerList; }
    }

    public enum InvseeUIType {
        PLAYER_SELECT,      // プレイヤー選択画面
        MAIN_INVENTORY,     // メインインベントリ
        EQUIPMENT,          // 装備スロット
        ENDERCHEST          // エンダーチェスト
    }

    // ========== Holderからの状態取得 ==========

    public static InvseeHolder getHolder(Inventory inv) {
        if (inv == null) return null;
        if (inv.getHolder() instanceof InvseeHolder) {
            return (InvseeHolder) inv.getHolder();
        }
        return null;
    }

    public static boolean isInvseeUI(Inventory inv) {
        return getHolder(inv) != null;
    }

    // ========== プレイヤー選択画面 ==========

    @SuppressWarnings("deprecation")
    public static void openPlayerSelectUI(Player viewer, Loader plugin, int page) {
        // Bedrockプレイヤーの場合は専用フォームを開く
        if (FloodgateUtil.isBedrock(viewer)) {
            openBedrockPlayerSelectForm(viewer, plugin, page);
            return;
        }

        // プレイヤーリストを作成（オンライン優先、その後オフライン）
        List<OfflinePlayer> allPlayers = new ArrayList<>();
        
        // オンラインプレイヤーを追加
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(viewer.getUniqueId())) {
                allPlayers.add(p);
            }
        }
        
        // オフラインプレイヤーを追加（最終ログイン順でソート）
        List<OfflinePlayer> offlinePlayers = new ArrayList<>();
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.isOnline()) continue;
            if (op.getName() == null) continue;
            offlinePlayers.add(op);
        }
        
        // 最終ログイン日時でソート（新しい順）
        offlinePlayers.sort((a, b) -> Long.compare(b.getLastPlayed(), a.getLastPlayed()));
        
        // 上位50人のオフラインプレイヤーを追加
        for (int i = 0; i < Math.min(50, offlinePlayers.size()); i++) {
            allPlayers.add(offlinePlayers.get(i));
        }

        int itemsPerPage = 45;
        int totalPages = Math.max(1, (allPlayers.size() + itemsPerPage - 1) / itemsPerPage);
        page = Math.max(0, Math.min(page, totalPages - 1));

        InvseeHolder holder = new InvseeHolder(InvseeUIType.PLAYER_SELECT, null, plugin);
        holder.setPage(page);
        holder.setPlayerList(allPlayers);

        Inventory inv = Bukkit.createInventory(holder, 54, TITLE_PLAYER_SELECT + " (" + (page + 1) + "/" + totalPages + ")");
        holder.setInventory(inv);

        // プレイヤーヘッド配置
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
                meta.setDisplayName((isOnline ? "§a" : "§7") + name);
                
                List<String> lore = new ArrayList<>();
                lore.add(isOnline ? "§a● オンライン" : "§7● オフライン");
                if (!isOnline && target.getLastPlayed() > 0) {
                    long lastPlayed = target.getLastPlayed();
                    long diff = System.currentTimeMillis() - lastPlayed;
                    String timeAgo = formatTimeAgo(diff);
                    lore.add("§7最終ログイン: §f" + timeAgo);
                }
                lore.add("");
                lore.add("§eクリックでインベントリを表示");
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        // プレイヤーがいない場合
        if (allPlayers.isEmpty()) {
            inv.setItem(22, createItem(Material.BARRIER, "§c対象プレイヤーがいません"));
        }

        // ナビゲーション（最後の行）
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, border);
        }

        if (page > 0) {
            inv.setItem(45, createItem(Material.ARROW, "§e前のページ"));
        }

        inv.setItem(49, createItem(Material.BARRIER, "§c閉じる"));

        if (page < totalPages - 1) {
            inv.setItem(53, createItem(Material.ARROW, "§e次のページ"));
        }

        viewer.openInventory(inv);
    }

    // ========== インベントリ表示画面（メイン） ==========

    public static void openInventoryUI(Player viewer, OfflinePlayer target, Loader plugin) {
        // Bedrockプレイヤーの場合は専用フォームを開く
        if (FloodgateUtil.isBedrock(viewer)) {
            openBedrockInventoryForm(viewer, target, plugin);
            return;
        }

        // オンラインプレイヤーの場合はリアルタイム同期用のUIを作成
        boolean isOnline = target.isOnline();
        
        InvseeHolder holder = new InvseeHolder(InvseeUIType.MAIN_INVENTORY, target, plugin);
        
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        String statusPrefix = isOnline ? "§a" : "§7";
        
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE_INVENTORY + statusPrefix + targetName);
        holder.setInventory(inv);

        // インベントリ内容を取得（オンラインならリアルタイム、オフラインならファイルから）
        ItemStack[] contents = getPlayerInventoryContents(target);
        
        // メインインベントリ（スロット0-35を表示）
        // Minecraftのインベントリ: 0-8がホットバー、9-35がメインインベントリ
        // UIでは上から順に表示
        for (int i = 0; i < 36 && i < contents.length; i++) {
            // 9-35を上に、0-8を下に配置
            int uiSlot;
            if (i < 9) {
                uiSlot = i + 27; // ホットバー → 下3行目
            } else {
                uiSlot = i - 9;  // メインインベ → 上3行
            }
            ItemStack item = contents[i];
            if (item != null) {
                inv.setItem(uiSlot, item.clone());
            }
        }

        // 区切り線（スロット36-44）
        ItemStack separator = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 36; i < 45; i++) {
            inv.setItem(i, separator);
        }

        // ナビゲーションボタン（スロット45-53）
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, border);
        }

        // スロット45: 戻る
        inv.setItem(45, createItem(Material.ARROW, "§e戻る（プレイヤー選択）"));

        // スロット47: 装備スロット
        inv.setItem(47, createItem(Material.DIAMOND_CHESTPLATE, "§b装備スロット",
            "§7ヘルメット、チェストプレート、",
            "§7レギンス、ブーツ、オフハンドを表示"));

        // スロット49: プレイヤー情報
        ItemStack infoHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta infoMeta = (SkullMeta) infoHead.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setOwningPlayer(target);
            infoMeta.setDisplayName(statusPrefix + "§l" + targetName);
            List<String> lore = new ArrayList<>();
            lore.add(isOnline ? "§a● オンライン" : "§7● オフライン");
            lore.add("§7UUID: §f" + target.getUniqueId().toString().substring(0, 8) + "...");
            infoMeta.setLore(lore);
            infoHead.setItemMeta(infoMeta);
        }
        inv.setItem(49, infoHead);

        // スロット51: エンダーチェスト
        inv.setItem(51, createItem(Material.ENDER_CHEST, "§dエンダーチェスト",
            "§7プレイヤーのエンダーチェストを表示"));

        // スロット53: 閉じる
        inv.setItem(53, createItem(Material.BARRIER, "§c閉じる"));

        viewer.openInventory(inv);
    }

    // ========== 装備スロット画面 ==========

    public static void openEquipmentUI(Player viewer, OfflinePlayer target, Loader plugin) {
        InvseeHolder holder = new InvseeHolder(InvseeUIType.EQUIPMENT, target, plugin);
        
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        boolean isOnline = target.isOnline();
        String statusPrefix = isOnline ? "§a" : "§7";
        
        Inventory inv = Bukkit.createInventory(holder, 27, TITLE_EQUIPMENT + statusPrefix + targetName);
        holder.setInventory(inv);

        // 背景
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        // 装備アイテムを取得
        ItemStack[] armor = getPlayerArmorContents(target);
        ItemStack offhand = getPlayerOffhand(target);

        // スロット10: ヘルメット
        inv.setItem(10, armor[3] != null ? armor[3].clone() : createPlaceholder(Material.GLASS_PANE, "§7ヘルメット", "§8空きスロット"));
        
        // スロット11: チェストプレート
        inv.setItem(11, armor[2] != null ? armor[2].clone() : createPlaceholder(Material.GLASS_PANE, "§7チェストプレート", "§8空きスロット"));
        
        // スロット12: レギンス
        inv.setItem(12, armor[1] != null ? armor[1].clone() : createPlaceholder(Material.GLASS_PANE, "§7レギンス", "§8空きスロット"));
        
        // スロット13: ブーツ
        inv.setItem(13, armor[0] != null ? armor[0].clone() : createPlaceholder(Material.GLASS_PANE, "§7ブーツ", "§8空きスロット"));

        // スロット15: オフハンド
        inv.setItem(15, offhand != null ? offhand.clone() : createPlaceholder(Material.GLASS_PANE, "§7オフハンド", "§8空きスロット"));

        // スロット22: 戻る
        inv.setItem(22, createItem(Material.ARROW, "§e戻る（インベントリ）"));

        viewer.openInventory(inv);
    }

    // ========== エンダーチェスト画面 ==========

    public static void openEnderchestUI(Player viewer, OfflinePlayer target, Loader plugin) {
        // オンラインプレイヤーの場合は直接エンダーチェストを開く（リアルタイム同期）
        if (target.isOnline()) {
            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null) {
                viewer.openInventory(onlineTarget.getEnderChest());
                return;
            }
        }
        
        // オフラインプレイヤーの場合はコピーを表示
        InvseeHolder holder = new InvseeHolder(InvseeUIType.ENDERCHEST, target, plugin);
        
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        String statusPrefix = "§7";
        
        Inventory inv = Bukkit.createInventory(holder, 36, TITLE_ENDERCHEST + statusPrefix + targetName);
        holder.setInventory(inv);

        // エンダーチェストの内容を取得
        ItemStack[] ecContents = getPlayerEnderchestContents(target);
        
        // エンダーチェスト（27スロット）
        for (int i = 0; i < 27 && i < ecContents.length; i++) {
            if (ecContents[i] != null) {
                inv.setItem(i, ecContents[i].clone());
            }
        }

        // ナビゲーション（最後の行）
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 27; i < 36; i++) {
            inv.setItem(i, border);
        }

        // スロット31: 戻る
        inv.setItem(31, createItem(Material.ARROW, "§e戻る（インベントリ）"));

        viewer.openInventory(inv);
    }

    // ========== インベントリ取得ヘルパー ==========

    /**
     * プレイヤーのメインインベントリ内容を取得
     * オンライン/オフライン両対応
     */
    public static ItemStack[] getPlayerInventoryContents(OfflinePlayer target) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) {
                return p.getInventory().getStorageContents();
            }
        }
        
        // オフラインプレイヤーの場合
        return InvseeOfflineData.getInventoryContents(target);
    }

    /**
     * プレイヤーの装備内容を取得
     */
    public static ItemStack[] getPlayerArmorContents(OfflinePlayer target) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) {
                return p.getInventory().getArmorContents();
            }
        }
        
        return InvseeOfflineData.getArmorContents(target);
    }

    /**
     * プレイヤーのオフハンドアイテムを取得
     */
    public static ItemStack getPlayerOffhand(OfflinePlayer target) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) {
                return p.getInventory().getItemInOffHand();
            }
        }
        
        return InvseeOfflineData.getOffhandItem(target);
    }

    /**
     * プレイヤーのエンダーチェスト内容を取得
     */
    public static ItemStack[] getPlayerEnderchestContents(OfflinePlayer target) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) {
                return p.getEnderChest().getContents();
            }
        }
        
        return InvseeOfflineData.getEnderchestContents(target);
    }

    // ========== インベントリ更新ヘルパー ==========

    /**
     * プレイヤーのインベントリを更新
     */
    public static void setPlayerInventoryContents(OfflinePlayer target, ItemStack[] contents) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) {
                p.getInventory().setStorageContents(contents);
                return;
            }
        }
        
        InvseeOfflineData.setInventoryContents(target, contents);
    }

    /**
     * プレイヤーの装備を更新
     */
    public static void setPlayerArmorContents(OfflinePlayer target, ItemStack[] armor) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) {
                p.getInventory().setArmorContents(armor);
                return;
            }
        }
        
        InvseeOfflineData.setArmorContents(target, armor);
    }

    /**
     * プレイヤーのオフハンドを更新
     */
    public static void setPlayerOffhand(OfflinePlayer target, ItemStack item) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) {
                p.getInventory().setItemInOffHand(item);
                return;
            }
        }
        
        InvseeOfflineData.setOffhandItem(target, item);
    }

    /**
     * プレイヤーのエンダーチェストを更新
     */
    public static void setPlayerEnderchestContents(OfflinePlayer target, ItemStack[] contents) {
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) {
                p.getEnderChest().setContents(contents);
                return;
            }
        }
        
        InvseeOfflineData.setEnderchestContents(target, contents);
    }

    // ========== Bedrock対応 ==========

    private static void openBedrockPlayerSelectForm(Player viewer, Loader plugin, int page) {
        List<FormsUtil.ButtonSpec> buttons = new ArrayList<>();
        List<OfflinePlayer> allPlayers = new ArrayList<>();
        
        // オンラインプレイヤー
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(viewer.getUniqueId())) {
                allPlayers.add(p);
            }
        }
        
        // オフラインプレイヤー（最近のみ）
        List<OfflinePlayer> offlinePlayers = new ArrayList<>();
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.isOnline() || op.getName() == null) continue;
            offlinePlayers.add(op);
        }
        offlinePlayers.sort((a, b) -> Long.compare(b.getLastPlayed(), a.getLastPlayed()));
        for (int i = 0; i < Math.min(20, offlinePlayers.size()); i++) {
            allPlayers.add(offlinePlayers.get(i));
        }

        for (OfflinePlayer target : allPlayers) {
            String name = target.getName() != null ? target.getName() : "Unknown";
            boolean isOnline = target.isOnline();
            String prefix = isOnline ? "●" : "○";
            String sanitized = sanitizeName(name);
            String url = "https://minotar.net/avatar/" + sanitized + "/64";
            buttons.add(FormsUtil.ButtonSpec.ofUrl(prefix + " " + name, url));
        }
        buttons.add(FormsUtil.ButtonSpec.ofText("閉じる"));

        FormsUtil.openSimpleForm(viewer, "InvSee - プレイヤー選択", buttons, idx -> {
            if (idx < 0 || idx >= allPlayers.size()) return;
            OfflinePlayer target = allPlayers.get(idx);
            openBedrockInventoryForm(viewer, target, plugin);
        });
    }

    private static void openBedrockInventoryForm(Player viewer, OfflinePlayer target, Loader plugin) {
        String name = target.getName() != null ? target.getName() : "Unknown";
        List<String> options = Arrays.asList(
            "メインインベントリ",
            "装備スロット",
            "エンダーチェスト",
            "戻る"
        );

        FormsUtil.openSimpleForm(viewer, "InvSee - " + name, options, idx -> {
            if (idx < 0) return;
            switch (idx) {
                case 0:
                    // メインインベントリ - Java UIを開く
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        openInventoryUIForBedrock(viewer, target, plugin);
                    });
                    break;
                case 1:
                    // 装備
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        openEquipmentUI(viewer, target, plugin);
                    });
                    break;
                case 2:
                    // エンダーチェスト
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        openEnderchestUI(viewer, target, plugin);
                    });
                    break;
                case 3:
                    // 戻る
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        openPlayerSelectUI(viewer, plugin, 0);
                    });
                    break;
            }
        });
    }

    /**
     * Bedrock用のインベントリUI（チェストUIを使用）
     */
    private static void openInventoryUIForBedrock(Player viewer, OfflinePlayer target, Loader plugin) {
        InvseeHolder holder = new InvseeHolder(InvseeUIType.MAIN_INVENTORY, target, plugin);
        
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        boolean isOnline = target.isOnline();
        String statusPrefix = isOnline ? "§a" : "§7";
        
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE_INVENTORY + statusPrefix + targetName);
        holder.setInventory(inv);

        ItemStack[] contents = getPlayerInventoryContents(target);
        
        for (int i = 0; i < 36 && i < contents.length; i++) {
            int uiSlot;
            if (i < 9) {
                uiSlot = i + 27;
            } else {
                uiSlot = i - 9;
            }
            ItemStack item = contents[i];
            if (item != null) {
                inv.setItem(uiSlot, item.clone());
            }
        }

        ItemStack separator = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 36; i < 45; i++) {
            inv.setItem(i, separator);
        }

        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, border);
        }

        inv.setItem(45, createItem(Material.ARROW, "§e戻る"));
        inv.setItem(47, createItem(Material.DIAMOND_CHESTPLATE, "§b装備スロット"));
        inv.setItem(51, createItem(Material.ENDER_CHEST, "§dエンダーチェスト"));
        inv.setItem(53, createItem(Material.BARRIER, "§c閉じる"));

        viewer.openInventory(inv);
    }

    // ========== ユーティリティ ==========

    private static ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createPlaceholder(Material mat, String name, String... lore) {
        return createItem(mat, name, lore);
    }

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

    private static String sanitizeName(String name) {
        if (name == null) return "";
        return name.replaceAll("[^A-Za-z0-9_]", "");
    }
}
