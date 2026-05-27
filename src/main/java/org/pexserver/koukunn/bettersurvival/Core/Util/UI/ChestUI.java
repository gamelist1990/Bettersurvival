package org.pexserver.koukunn.bettersurvival.Core.Util.UI;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;


/**
 * カスタムチェストUIを管理するユーティリティクラス。
 * <p>
 * インベントリの生成、ボタン管理、クリックイベントのハンドリングなどを
 * 包括的に提供し、プレイヤーごとに開いているメニューを追跡します。
 * </p>
 */
public class ChestUI implements InventoryHolder {
    // プレイヤーID -> 開いているメニューインスタンス
    private static final Map<UUID, ChestUI> openMenus = new ConcurrentHashMap<>();
    // プレイヤーID -> メニュークリック時のハンドラ
    private static final Map<UUID, BiConsumer<FormResult, Player>> handlers = new ConcurrentHashMap<>();
    // Bedrock プレイヤーの直近テレポート時刻
    private static final Map<UUID, Long> recentTeleportTimes = new ConcurrentHashMap<>();
    // プレイヤーごとの遅延オープンタスク
    private static final Map<UUID, BukkitTask> pendingOpenTasks = new ConcurrentHashMap<>();
    private static final long BEDROCK_TELEPORT_UI_DELAY_MS = 350L;
    private static final long BEDROCK_RESPAWN_UI_DELAY_MS = 500L;
    private static final long MILLIS_PER_TICK = 50L;
    private static final long MENU_TRANSITION_DELAY_TICKS = 1L;

    private final int size;
    private final Map<Integer, ButtonInfo> buttons = new HashMap<>();
    private final Set<Integer> editableSlots = new HashSet<>();
    private Inventory inventory;
    private final Material defaultIcon;
    private String title;
    private String type;
    private boolean preserveInventoryOnTransition;

    /**
     * 内部用コンストラクタ。ビルダー経由でのみ生成される。
     *
     * @param title       メニュータイトル
     * @param size        インベントリスロット数（9の倍数、最大54）
     * @param defaultIcon ボタンアイコンが未指定のときのデフォルトマテリアル
     */
    private ChestUI(String title, int size, Material defaultIcon) {
        this.size = size;
        this.title = title;
        this.type = null;
        this.defaultIcon = defaultIcon;
        this.inventory = null;
    }


    /**
     * 指定スロットのボタンを更新または追加します。
     *
     * @param slot  スロット番号（0始まり）
     * @param label 表示名
     * @param icon  アイコンマテリアル
     * @param lore  説明文（1行目のみ表示）
     */
    public void updateButton(int slot, String label, Material icon, String lore) {
        if (slot >= 0 && slot < size) {
            buttons.put(slot, new ButtonInfo(label, icon, lore));
            refreshSlot(slot);
        }
    }


    /**
     * 指定スロットのボタンを削除します。
     *
     * @param slot スロット番号
     */
    public void removeButton(int slot) {
        if (slot >= 0 && slot < size) {
            buttons.remove(slot);
            inventory.setItem(slot, null);
        }
    }


    /**
     * 内部: 単一スロットの表示を再構築します。
     *
     * @param slot スロット番号
     */
    private void refreshSlot(int slot) {
        ButtonInfo button = buttons.get(slot);
        if (button != null && slot >= 0 && slot < size) {
            ItemStack item;
            if (button.customItem != null) {
                item = button.customItem.clone();
            } else {
                item = new ItemStack(button.icon, 1);
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (hasCustomLabel(button.label)) {
                    meta.displayName(ComponentUtils.legacy(button.label));
                }
                if (button.lore != null && !button.lore.isEmpty()) {
                    meta.lore(toLoreComponents(button.lore));
                }
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
        }
    }


    /**
     * 全スロットの内容を再描画します。
     */
    public void refreshAll() {
        inventory.clear();
        for (Map.Entry<Integer, ButtonInfo> entry : buttons.entrySet()) {
            int slot = entry.getKey();
            refreshSlot(slot);
        }
    }


    /**
     * メニュー作成用ビルダーを取得します。
     *
     * @return 新規ビルダーインスタンス
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * ChestUIを段階的に構築するためのビルダークラス。
     */
    public static class Builder {
        private String title = "Menu";
        private int size = 27;
        private Material defaultIcon = Material.PAPER;
        private final Map<Integer, ButtonInfo> buttons = new HashMap<>();
        private final Set<Integer> editableSlots = new HashSet<>();
        private String type = null;
        private boolean preserveInventoryOnTransition = false;

        /**
         * メニュータイトルを設定します。
         *
         * @param title メニュー名
         * @return このビルダー
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * インベントリサイズを設定します。9の倍数に丸め、最大54に制限。
         *
         * @param size スロット数
         * @return このビルダー
         */
        public Builder size(int size) {
            if (size <= 0)
                size = 27;
            size = ((size + 8) / 9) * 9;
            if (size > 54)
                size = 54;
            this.size = size;
            return this;
        }

        /**
         * デフォルトのアイコンマテリアルを設定します。
         *
         * @param icon マテリアル
         * @return このビルダー
         */
        public Builder defaultIcon(Material icon) {
            if (icon != null) {
                this.defaultIcon = icon;
            }
            return this;
        }


        /**
         * メニューに任意のタイプ識別子を設定します。
         *
         * @param type 識別文字列
         * @return このビルダー
         */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder preserveInventoryOnTransition(boolean preserveInventoryOnTransition) {
            this.preserveInventoryOnTransition = preserveInventoryOnTransition;
            return this;
        }

        /**
         * 編集可能スロットを設定します。
         */
        public Builder editableSlots(int... slots) {
            if (slots == null) {
                return this;
            }
            for (int slot : slots) {
                if (slot >= 0) {
                    this.editableSlots.add(slot);
                }
            }
            return this;
        }

        /**
         * 保護されたスロットを設定します。
         */
        public Builder protectedSlots(java.util.Set<Integer> slots) {
            if (slots == null) {
                return this;
            }
            this.editableSlots.removeAll(slots);
            return this;
        }

        /**
         * 汎用ボタンを追加します。
         *
         * @param slot  スロット番号
         * @param label 表示名
         * @param icon  アイコンマテリアル
         * @param lore  説明文
         * @return このビルダー
         */
        public Builder addButtonAt(int slot, String label, Material icon, String lore) {
            buttons.put(slot, new ButtonInfo(label, icon, lore));
            return this;
        }

        /**
         * アイテムスタックからボタンを作成して追加します。
         *
         * @param slot  スロット番号
         * @param label 表示名
         * @param item  アイテムスタック（lore抽出）
         * @return このビルダー
         */
        public Builder addButtonAt(int slot, String label, org.bukkit.inventory.ItemStack item) {
            Material mat = item != null ? item.getType() : Material.PAPER;
            String lore = "";
            if (item != null && item.getItemMeta() != null) {
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta.hasLore()) {
                    java.util.List<Component> loreList = meta.lore();
                    if (loreList != null && !loreList.isEmpty()) {
                        lore = PlainTextComponentSerializer.plainText().serialize(loreList.get(0));
                    }
                }
            }
            buttons.put(slot, new ButtonInfo(label, mat, lore));
            return this;
        }

        /**
         * アイコンのみ指定してボタンを追加します。
         *
         * @param slot  スロット番号
         * @param label 表示名
         * @param icon  アイコンマテリアル
         * @return このビルダー
         */
        public Builder addButtonAt(int slot, String label, Material icon) {
            return addButtonAt(slot, label, icon, "");
        }

        /**
         * dummyフラグ付きの互換メソッド (内部で無視).
         */
        public Builder addButtonAt(int slot, String label, Material icon, boolean dummy, String lore) {
            return addButtonAt(slot, label, icon, lore);
        }


        /**
         * プレイヤーヘッドアイテムをボタンとして追加します。
         *
         * @param slot   スロット番号
         * @param label  表示名
         * @param player プレイヤー（頭に反映）
         * @param lore   説明文
         * @return このビルダー
         */
        public Builder addPlayerHeadAt(int slot, String label, Player player, String lore) {
            ItemStack head = createPlayerHead(player);
            buttons.put(slot, new ButtonInfo(label, head, lore));
            return this;
        }


        /**
         * 任意のItemStackを使ったカスタムボタンを追加します。
         *
         * @param slot       スロット番号
         * @param label      表示名
         * @param customItem 表示するアイテムスタック
         * @param lore       説明文
         * @return このビルダー
         */
        public Builder addCustomItemAt(int slot, String label, ItemStack customItem, String lore) {
            buttons.put(slot, new ButtonInfo(label, customItem, lore));
            return this;
        }


        private ItemStack createPlayerHead(Player player) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
                if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
                    skullMeta.setOwningPlayer(player);
                    head.setItemMeta(skullMeta);
                }
            return head;
        }

        public MenuHandler then(BiConsumer<FormResult, Player> handler) {
            return new MenuHandler(this, handler);
        }

        /**
         * 設定内容からChestUIインスタンスを構築します。
         *
         * @return 構築されたChestUI
         */
        protected ChestUI build() {
            ChestUI menu = new ChestUI(title, size, defaultIcon);
            menu.type = this.type;
            menu.preserveInventoryOnTransition = this.preserveInventoryOnTransition;
            menu.buttons.putAll(buttons);
            for (Integer slot : this.editableSlots) {
                if (slot != null && slot >= 0 && slot < size) {
                    menu.editableSlots.add(slot);
                }
            }

            menu.inventory = Loader.getPlugin(Loader.class).getServer().createInventory(menu, menu.size,
                    ComponentUtils.legacy(menu.title));

            for (Map.Entry<Integer, ButtonInfo> entry : menu.buttons.entrySet()) {
                int slot = entry.getKey();
                ButtonInfo button = entry.getValue();
                if (slot >= 0 && slot < size) {
                    ItemStack item;
                    if (button.customItem != null) {
                        item = button.customItem.clone();
                    } else {
                        Material mat = button.icon != null ? button.icon : menu.defaultIcon;
                        item = new ItemStack(mat, 1);
                    }

                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        if (hasCustomLabel(button.label)) {
                            meta.displayName(ComponentUtils.legacy(button.label));
                        }
                        if (button.lore != null && !button.lore.isEmpty()) {
                            meta.lore(toLoreComponents(button.lore));
                        }
                        item.setItemMeta(meta);
                    }
                    menu.inventory.setItem(slot, item);
                }
            }

            return menu;
        }
    }


    /**
     * メニューを表示・操作し、結果をハンドルするためのラッパークラス。
     */
    public static class MenuHandler {
        private final Builder builder;
        private final BiConsumer<FormResult, Player> handler;
        private ChestUI builtMenu;

        /**
         * 初期化。
         *
         * @param builder メニュー構築用ビルダー
         * @param handler ボタンクリック処理ハンドラ
         */
        public MenuHandler(Builder builder, BiConsumer<FormResult, Player> handler) {
            this.builder = builder;
            this.handler = handler;
        }

        /**
         * プレイヤーにメニューを開かせます。既存のメニューがあれば閉じます。
         *
         * @param player 対象プレイヤー
         */
        public void show(Player player) {
            builtMenu = builder.build();
            UUID playerId = player.getUniqueId();
            ChestUI previousMenu = openMenus.get(playerId);
            if (canReuseInventory(player, previousMenu, builtMenu)) {
                cancelPendingOpen(playerId);
                builtMenu.inventory = previousMenu.inventory;
                openMenus.put(playerId, builtMenu);
                handlers.put(playerId, handler);
                builtMenu.refreshAll();
                try {
                    player.updateInventory();
                } catch (Throwable ignored) {
                }
                return;
            }

            boolean hadOpenTopInventory = hasOpenTopInventory(player);
            try { ChestUI.closeMenu(player); } catch (Throwable ignored) {}

            openMenus.put(playerId, builtMenu);
            handlers.put(playerId, handler);

            cancelPendingOpen(playerId);
            long delayTicks = Math.max(calculateOpenDelayTicks(player), hadOpenTopInventory ? MENU_TRANSITION_DELAY_TICKS : 0L);
            BukkitTask task = Loader.getPlugin(Loader.class).getServer().getScheduler().runTaskLater(
                    Loader.getPlugin(Loader.class),
                    () -> {
                        pendingOpenTasks.remove(playerId);
                        openMenuInventory(player, builtMenu);
                    },
                    delayTicks);
            pendingOpenTasks.put(playerId, task);
        }


        /**
         * 表示中のメニューインスタンスを返します。
         *
         * @return 現在のChestUI、未表示時はnull
         */
        public ChestUI getMenu() {
            return builtMenu;
        }

        private boolean canReuseInventory(Player player, ChestUI previousMenu, ChestUI nextMenu) {
            if (player == null || previousMenu == null || nextMenu == null) {
                return false;
            }
            if (!nextMenu.preserveInventoryOnTransition || previousMenu.size != nextMenu.size) {
                return false;
            }
            if (player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() != previousMenu.getInventory()) {
                return false;
            }
            return true;
        }


        /**
         * プレイヤーが見ているメニューを閉じます。
         *
         * @param player 対象プレイヤー
         * @return 閉じた場合true
         */
        public boolean close(Player player) {
            if (builtMenu == null) return false;
            return builtMenu.close(player);
        }
    }

    /**
     * {@link InventoryHolder} 実装。内部在庫を返す。
     */
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * ビルダーで設定されたカスタムタイプ識別子を取得します。
     *
     * @return タイプ文字列、未設定時はnull
     */
    public String getType() {
        return type;
    }

    public boolean hasEditableSlots() {
        return !editableSlots.isEmpty();
    }

    public boolean isEditableSlot(int slot) {
        return editableSlots.contains(slot);
    }


    /**
     * メニューの操作結果を表すデータオブジェクト。
     */
    public static class FormResult {
        public final boolean success;
        public final boolean cancelled;
        public final Integer slot;

        /**
         * @param success   クリックが有効とみなされたか
         * @param cancelled プレイヤーによるキャンセルか
         * @param slot      クリックされたスロット番号
         */
        public FormResult(boolean success, boolean cancelled, Integer slot) {
            this.success = success;
            this.cancelled = cancelled;
            this.slot = slot;
        }
    }


    /**
     * ボタンの内部情報を保持するクラス。
     */
    private static class ButtonInfo {
        final String label;
        final Material icon;
        final String lore;
        final ItemStack customItem; 

        ButtonInfo(String label, Material icon, String lore) {
            this.label = label;
            this.icon = icon;
            this.lore = lore;
            this.customItem = null;
        }

        ButtonInfo(String label, ItemStack customItem, String lore) {
            this.label = label;
            this.icon = customItem != null ? customItem.getType() : Material.PAPER;
            this.lore = lore;
            this.customItem = customItem;
        }
    }


    /**
     * メニューイベントを監視するリスナー。
     */
    public static class MenuListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
        public void onPlayerTeleport(PlayerTeleportEvent event) {
            recordTeleport(event.getPlayer(), BEDROCK_TELEPORT_UI_DELAY_MS);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
        public void onPlayerRespawn(PlayerRespawnEvent event) {
            recordTeleport(event.getPlayer(), BEDROCK_RESPAWN_UI_DELAY_MS);
        }

        /**
         * インベントリクリックイベントを処理し、メニューボタンを有効化する。
         */
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            UUID playerId = player.getUniqueId();
            ChestUI menu = openMenus.get(playerId);

            if (menu == null) {
                return;
            }

            if (event.getView().getTopInventory() != menu.getInventory()) {
                return;
            }

            int rawSlot = event.getRawSlot();
            int topSize = menu.getInventory().getSize();
            boolean inTop = rawSlot >= 0 && rawSlot < topSize;
            boolean hasEditableSlots = menu.hasEditableSlots();

            org.bukkit.inventory.Inventory clickedInventory = event.getClickedInventory();
            if (clickedInventory == null) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                return;
            }

            if (inTop) {
                if (menu.isEditableSlot(rawSlot)) {
                    event.setCancelled(false);
                    event.setResult(Event.Result.ALLOW);
                    return;
                }

                event.setCancelled(true);
                event.setResult(Event.Result.DENY);

                BiConsumer<FormResult, Player> handler = handlers.get(playerId);
                if (handler != null && menu.buttons.containsKey(rawSlot)) {
                    FormResult result = new FormResult(true, false, rawSlot);
                    try {
                        handler.accept(result, player);
                    } catch (Exception e) {
                        Loader.getPlugin(Loader.class).getLogger().log(Level.WARNING, "Error handling ChestUI click", e);
                    }
                }
                return;
            }

            if (!hasEditableSlots) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                return;
            }

            if (event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                ItemStack source = event.getCurrentItem();
                if (source == null || source.getType().isAir() || source.getAmount() <= 0) {
                    event.setCancelled(true);
                    event.setResult(Event.Result.DENY);
                    return;
                }

                int moved = moveToEditableSlots(menu.getInventory(), menu.editableSlots, source);
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                if (moved <= 0) {
                    return;
                }

                int remain = source.getAmount() - moved;
                if (remain <= 0) {
                    clickedInventory.setItem(event.getSlot(), null);
                } else {
                    source.setAmount(remain);
                    clickedInventory.setItem(event.getSlot(), source);
                }
                player.updateInventory();
                return;
            }

            event.setCancelled(false);
            event.setResult(Event.Result.ALLOW);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
        public void onInventoryDrag(InventoryDragEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            ChestUI menu = openMenus.get(player.getUniqueId());
            if (menu == null || event.getView().getTopInventory() != menu.getInventory()) {
                return;
            }

            int topSize = menu.getInventory().getSize();
            for (Integer rawSlot : event.getRawSlots()) {
                if (rawSlot >= 0 && rawSlot < topSize) {
                    if (!menu.isEditableSlot(rawSlot)) {
                        event.setCancelled(true);
                        event.setResult(Event.Result.DENY);
                        return;
                    }
                }
            }
        }

        private int moveToEditableSlots(Inventory topInventory, Set<Integer> editableSlots, ItemStack source) {
            int remaining = source.getAmount();
            for (Integer slot : editableSlots) {
                if (remaining <= 0) {
                    break;
                }
                if (slot == null || slot < 0 || slot >= topInventory.getSize()) {
                    continue;
                }
                ItemStack target = topInventory.getItem(slot);
                if (target == null || target.getType().isAir()) {
                    ItemStack placed = source.clone();
                    placed.setAmount(Math.min(remaining, source.getMaxStackSize()));
                    topInventory.setItem(slot, placed);
                    remaining -= placed.getAmount();
                    continue;
                }
                if (!target.isSimilar(source)) {
                    continue;
                }
                int maxStack = target.getMaxStackSize();
                if (target.getAmount() >= maxStack) {
                    continue;
                }
                int canMove = Math.min(remaining, maxStack - target.getAmount());
                target.setAmount(target.getAmount() + canMove);
                topInventory.setItem(slot, target);
                remaining -= canMove;
            }
            return source.getAmount() - remaining;
        }

        /**
         * メニュー閉鎖を検知し状態をクリーンアップする。
         */
        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            if (!(event.getPlayer() instanceof Player)) {
                return;
            }

            Player player = (Player) event.getPlayer();
            UUID playerId = player.getUniqueId();
            ChestUI menu = openMenus.get(playerId);
            if (menu != null && event.getInventory() == menu.getInventory()) {
                openMenus.remove(playerId);
                handlers.remove(playerId);
            }
            cancelPendingOpen(playerId);
        }
    }


    /**
     * プラグイン初期化時にリスナーを登録します。
     */
    public static void register() {
        Loader.getPlugin(Loader.class).getServer().getPluginManager().registerEvents(new MenuListener(), Loader.getPlugin(Loader.class));
    }


    /**
     * 指定プレイヤーが開いているメニューを閉じ、状態を破棄します。
     *
     * @param player 対象プレイヤー
     * @return メニューが存在して閉じられた場合true
     */
    public static boolean closeMenu(Player player) {
        UUID playerId = player.getUniqueId();
        ChestUI menu = openMenus.remove(playerId);
        handlers.remove(playerId);
        cancelPendingOpen(playerId);
        if (menu != null) {
            try {
                player.closeInventory();
            } catch (Throwable ignored) {}
            return true;
        }
        return false;
    }


    public boolean close(Player player) {
        UUID playerId = player.getUniqueId();
        ChestUI menu = openMenus.get(playerId);
        if (menu == this) {
            player.closeInventory();
            return true;
        }
        return false;
    }


    /**
     * プレイヤーがメニューを開いているかどうかを判定します。
     *
     * @param player 対象プレイヤー
     * @return 開いていればtrue
     */
    public static boolean hasOpenMenu(Player player) {
        return openMenus.containsKey(player.getUniqueId());
    }


    /**
     * プレイヤーが開いているメニューインスタンスを取得します。
     *
     * @param player 対象プレイヤー
     * @return メニュー、存在しない場合はnull
     */
    public static ChestUI getOpenMenu(Player player) {
        return openMenus.get(player.getUniqueId());
    }

    private static List<Component> toLoreComponents(String lore) {
        if (lore == null || lore.isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedLore = lore.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalizedLore.split("\\n", -1);
        java.util.List<Component> components = new java.util.ArrayList<>(lines.length);
        for (String line : lines) {
            components.add(ComponentUtils.legacy(line));
        }
        return components;
    }

    private static boolean hasCustomLabel(String label) {
        return label != null && !label.isBlank();
    }

    /**
     * チャット入力ダイアログを開き、ユーザーの入力をコールバックで受け取ります。
     *
     * @param player       対象プレイヤー
     * @param prompt       ダイアログタイトル（null/空の場合は「入力」）
     * @param defaultValue 初期値
     * @param callback     入力結果を受け取るコールバック
     */
    public static void openChat(Player player, String prompt, String defaultValue, Consumer<String> callback) {
        String title = (prompt == null || prompt.isEmpty()) ? "入力" : prompt;
        String initialValue = (defaultValue == null) ? "" : defaultValue;
        DialogUI.showTextInput(player, title, "", initialValue, (p, input) -> {
            try {
                callback.accept(input == null ? "" : input);
            } catch (Throwable ignored) {}
        });
    }

    private static void recordTeleport(Player player, long protectionMillis) {
        if (player == null || !FloodgateUtil.isBedrock(player)) {
            return;
        }
        recentTeleportTimes.put(player.getUniqueId(), System.currentTimeMillis() + protectionMillis);
    }

    private static long calculateOpenDelayTicks(Player player) {
        if (player == null || !FloodgateUtil.isBedrock(player)) {
            return 0L;
        }

        long now = System.currentTimeMillis();
        long protectedUntil = recentTeleportTimes.getOrDefault(player.getUniqueId(), 0L);
        long remainingMillis = Math.max(0L, protectedUntil - now);
        long delayTicks = (remainingMillis + MILLIS_PER_TICK - 1L) / MILLIS_PER_TICK;
        return Math.max(1L, delayTicks);
    }

    private static boolean hasOpenTopInventory(Player player) {
        if (player == null || player.getOpenInventory() == null) {
            return false;
        }
        Inventory topInventory = player.getOpenInventory().getTopInventory();
        return topInventory != null && topInventory.getType() != org.bukkit.event.inventory.InventoryType.PLAYER;
    }

    private static void openMenuInventory(Player player, ChestUI menu) {
        if (player == null || menu == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (openMenus.get(playerId) != menu) {
            return;
        }

        Loader.getPlugin(Loader.class).getServer().getScheduler().runTask(Loader.getPlugin(Loader.class), () -> {
            if (!player.isOnline() || openMenus.get(playerId) != menu) {
                return;
            }

            try {
                player.openInventory(menu.inventory);
                player.updateInventory();
                if (FloodgateUtil.isBedrock(player)) {
                    Loader.getPlugin(Loader.class).getServer().getScheduler().runTaskLater(Loader.getPlugin(Loader.class), () -> {
                        if (!player.isOnline() || openMenus.get(playerId) != menu) {
                            return;
                        }
                        try {
                            player.updateInventory();
                        } catch (Throwable ignored) {}
                    }, 2L);
                }
            } catch (Throwable ignored) {}
        });
    }

    private static void cancelPendingOpen(UUID playerId) {
        BukkitTask task = pendingOpenTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
}
