package org.pexserver.koukunn.bettersurvival.Modules.Feature.Tpa;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

/**
 * TPA モジュール
 * テレポートリクエストの送信・承認・拒否を処理
 * Toggle機能と連携してグローバル/ユーザー単位での有効/無効を管理
 */
public class TpaModule implements Listener {

    public static final String FEATURE_KEY = "tpa";
    
    private final Loader plugin;
    private final ToggleModule toggleModule;
    private final TpaRequestStore store;

    public TpaModule(Loader plugin) {
        this.plugin = plugin;
        this.toggleModule = plugin.getToggleModule();
        this.store = new TpaRequestStore(plugin.getConfigManager());
    }

    public TpaRequestStore getStore() {
        return store;
    }

    /**
     * TPA機能がグローバルで有効かどうか
     */
    public boolean isGlobalEnabled() {
        return toggleModule.getGlobal(FEATURE_KEY);
    }

    /**
     * プレイヤーがTPA受信を有効にしているか
     * - グローバルで有効 AND ユーザー設定で有効
     */
    public boolean canReceiveTpa(Player player) {
        if (!isGlobalEnabled()) return false;
        return toggleModule.isEnabledFor(player.getUniqueId().toString(), FEATURE_KEY);
    }

    /**
     * TPAリクエストを送信
     */
    public void sendRequest(Player sender, Player target) {
        // グローバルチェック
        if (!isGlobalEnabled()) {
            sendError(sender, "TPA機能は無効化されています");
            return;
        }

        // 送信者自身のチェック
        if (!toggleModule.isEnabledFor(sender.getUniqueId().toString(), FEATURE_KEY)) {
            sendError(sender, "あなたはTPA機能を無効にしています。/toggle で有効にしてください");
            return;
        }

        // ターゲットのチェック
        if (!canReceiveTpa(target)) {
            sendError(sender, target.getName() + " はTPA受信を無効にしています");
            return;
        }

        // 自分自身への送信チェック
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sendError(sender, "自分自身にTPAリクエストを送信することはできません");
            return;
        }

        // 既存リクエストチェック
        if (store.hasPendingRequest(sender.getUniqueId().toString(), target.getUniqueId().toString())) {
            sendError(sender, "既に " + target.getName() + " へのリクエストが送信済みです");
            return;
        }

        // リクエスト作成
        TpaRequest request = new TpaRequest(
            sender.getUniqueId().toString(),
            sender.getName(),
            target.getUniqueId().toString(),
            target.getName()
        );
        store.addRequest(request);

        sendSuccess(sender, target.getName() + " にTPAリクエストを送信しました (5分間有効)");
        sendInfo(target, sender.getName() + " からTPAリクエストが届いています");
        sendInfo(target, "§7承認: §a/tpa -a " + sender.getName() + " §7| 拒否: §c/tpa -d " + sender.getName());
        sendInfo(target, "§7または §e/tpa ui §7でUIを開いて操作できます");
    }

    /**
     * TPAリクエストを承認
     */
    public void acceptRequest(Player target, String senderName) {
        if (!isGlobalEnabled()) {
            sendError(target, "TPA機能は無効化されています");
            return;
        }

        TpaRequest request = store.getRequestBySenderName(target.getUniqueId().toString(), senderName).orElse(null);
        if (request == null) {
            sendError(target, senderName + " からのリクエストが見つかりません（期限切れの可能性があります）");
            return;
        }

        if (request.isExpired()) {
            store.removeRequest(target.getUniqueId().toString(), request.getSenderUuid());
            sendError(target, "このリクエストは期限切れです");
            return;
        }

        // 送信者がオンラインかチェック
        Player sender = Bukkit.getPlayer(java.util.UUID.fromString(request.getSenderUuid()));
        if (sender == null || !sender.isOnline()) {
            store.removeRequest(target.getUniqueId().toString(), request.getSenderUuid());
            sendError(target, request.getSenderName() + " はオフラインです");
            return;
        }

        // テレポート実行
        store.removeRequest(target.getUniqueId().toString(), request.getSenderUuid());
        
        // テレポート（次のtickで実行）
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            sender.teleport(target.getLocation());
            sendSuccess(sender, target.getName() + " にテレポートしました");
            sendSuccess(target, sender.getName() + " のTPAリクエストを承認しました");
        });
    }

    /**
     * TPAリクエストを拒否
     */
    public void denyRequest(Player target, String senderName) {
        TpaRequest request = store.getRequestBySenderName(target.getUniqueId().toString(), senderName).orElse(null);
        if (request == null) {
            sendError(target, senderName + " からのリクエストが見つかりません");
            return;
        }

        store.removeRequest(target.getUniqueId().toString(), request.getSenderUuid());

        // 送信者に通知
        Player sender = Bukkit.getPlayer(java.util.UUID.fromString(request.getSenderUuid()));
        if (sender != null && sender.isOnline()) {
            sendError(sender, target.getName() + " にTPAリクエストを拒否されました");
        }
        sendInfo(target, request.getSenderName() + " のTPAリクエストを拒否しました");
    }

    /**
     * リクエスト一覧を表示
     */
    public void listRequests(Player player) {
        java.util.List<TpaRequest> received = store.getRequestsFor(player.getUniqueId().toString());
        java.util.List<TpaRequest> sent = store.getSentRequestsBy(player.getUniqueId().toString());

        if (received.isEmpty() && sent.isEmpty()) {
            sendInfo(player, "TPAリクエストはありません");
            return;
        }

        if (!received.isEmpty()) {
            sendInfo(player, "§e--- 受信したリクエスト ---");
            for (TpaRequest req : received) {
                String msg = "§a" + req.getSenderName() + " §7(残り " + req.getRemainingSeconds() + "秒)";
                sendRaw(player, msg);
            }
        }

        if (!sent.isEmpty()) {
            sendInfo(player, "§e--- 送信したリクエスト ---");
            for (TpaRequest req : sent) {
                String msg = "§b→ " + req.getTargetName() + " §7(残り " + req.getRemainingSeconds() + "秒)";
                sendRaw(player, msg);
            }
        }
    }

    /**
     * UIを開く
     */
    public void openUI(Player player) {
        if (!isGlobalEnabled()) {
            sendError(player, "TPA機能は無効化されています");
            return;
        }
        TpaUI.openMainUI(player, this);
    }

    // ========== イベントハンドラ ==========

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Inventory inv = e.getInventory();
        TpaUI.TpaHolder holder = TpaUI.getHolder(inv);
        if (holder == null) return;

        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        TpaUI.UIType uiType = holder.getUIType();

        if (uiType == TpaUI.UIType.MAIN) {
            handleMainUIClick(p, e, holder, clicked);
        } else if (uiType == TpaUI.UIType.SEND_SELECT) {
            handleSendUIClick(p, e, holder, clicked);
        }
    }

    private void handleMainUIClick(Player p, InventoryClickEvent e, TpaUI.TpaHolder holder, ItemStack clicked) {
        // 閉じるボタン
        if (clicked.getType() == Material.BARRIER) {
            p.closeInventory();
            return;
        }

        // リクエスト送信ボタン
        if (clicked.getType() == Material.ENDER_PEARL) {
            TpaUI.openSendUI(p, this);
            return;
        }

        // プレイヤーヘッド（リクエスト）
        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta == null || meta.getDisplayName() == null) return;
            
            String displayName = meta.getDisplayName();
            // §a で始まるプレイヤー名を取得
            if (displayName.startsWith("§a")) {
                String senderName = displayName.substring(2);
                
                // 左クリック: 承認, 右クリック: 拒否
                if (e.isLeftClick()) {
                    p.closeInventory();
                    acceptRequest(p, senderName);
                } else if (e.isRightClick()) {
                    p.closeInventory();
                    denyRequest(p, senderName);
                }
            }
        }
    }

    private void handleSendUIClick(Player p, InventoryClickEvent e, TpaUI.TpaHolder holder, ItemStack clicked) {
        // 戻るボタン
        if (clicked.getType() == Material.ARROW) {
            TpaUI.openMainUI(p, this);
            return;
        }

        // プレイヤーヘッド
        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta == null || meta.getDisplayName() == null) return;

            String displayName = meta.getDisplayName();
            // §a で始まる（受信可能）プレイヤー名のみ処理
            if (displayName.startsWith("§a")) {
                String targetName = displayName.substring(2);
                Player target = Bukkit.getPlayer(targetName);
                if (target != null && target.isOnline()) {
                    p.closeInventory();
                    sendRequest(p, target);
                }
            } else if (displayName.startsWith("§7")) {
                // TPA受信無効のプレイヤー
                sendError(p, "このプレイヤーはTPA受信を無効にしています");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        // プレイヤーがログアウトしたら関連リクエストをクリア
        store.removeAllFor(e.getPlayer().getUniqueId().toString());
    }

    // ========== メッセージユーティリティ ==========

    private void sendError(Player p, String message) {
        p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§c[TPA] " + message));
    }

    private void sendSuccess(Player p, String message) {
        p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§a[TPA] " + message));
    }

    private void sendInfo(Player p, String message) {
        p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§b[TPA] " + message));
    }

    private void sendRaw(Player p, String message) {
        p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message));
    }
}
