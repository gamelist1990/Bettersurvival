package org.pexserver.koukunn.bettersurvival.Modules.Feature.Tpa;

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

import java.util.*;

/**
 * TPA UI - Bedrock/Java 両対応
 * BedrockプレイヤーにはCumulus (Floodgate) フォーム、JavaプレイヤーにはChest GUIを表示
 */
public class TpaUI {

    public static final String TITLE_MAIN = "§8TPA リクエスト";
    public static final String TITLE_SEND = "§8TPA 送信先を選択";

    // ========== カスタム InventoryHolder ==========

    public static class TpaHolder implements InventoryHolder {
        private Inventory inventory;
        private final UIType uiType;
        private final TpaModule module;
        private final List<TpaRequest> requests;
        private final List<Player> candidates;

        public TpaHolder(UIType uiType, TpaModule module, List<TpaRequest> requests, List<Player> candidates) {
            this.uiType = uiType;
            this.module = module;
            this.requests = requests;
            this.candidates = candidates;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public UIType getUIType() { return uiType; }
        public TpaModule getModule() { return module; }
        public List<TpaRequest> getRequests() { return requests; }
        public List<Player> getCandidates() { return candidates; }
    }

    public enum UIType {
        MAIN,          // リクエスト一覧
        SEND_SELECT    // 送信先選択
    }

    // ========== Holderからの状態取得 ==========

    public static TpaHolder getHolder(Inventory inv) {
        if (inv == null) return null;
        if (inv.getHolder() instanceof TpaHolder) {
            return (TpaHolder) inv.getHolder();
        }
        return null;
    }

    public static boolean isTpaUI(Inventory inv) {
        return getHolder(inv) != null;
    }

    // ========== メイン画面（リクエスト一覧） ==========

    @SuppressWarnings("deprecation")
    public static void openMainUI(Player p, TpaModule module) {
        List<TpaRequest> requests = module.getStore().getRequestsFor(p.getUniqueId().toString());

        // Bedrockプレイヤーの場合は専用フォームを開く
        if (FloodgateUtil.isBedrock(p)) {
            openBedrockMainForm(p, module, requests);
            return;
        }

        // Java用 Chest GUI
        int size = Math.max(27, 9 * ((requests.size() + 8) / 9 + 1));
        size = Math.min(size, 54);

        TpaHolder holder = new TpaHolder(UIType.MAIN, module, requests, null);
        Inventory inv = Bukkit.createInventory(holder, size, TITLE_MAIN);
        holder.setInventory(inv);

        // ガラス装飾
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < size; i++) {
            inv.setItem(i, border);
        }

        if (requests.isEmpty()) {
            inv.setItem(13, createItem(Material.BARRIER, "§cリクエストがありません",
                "§7現在、TPAリクエストはありません"));
        } else {
            int slot = 0;
            for (TpaRequest req : requests) {
                if (slot >= size - 9) break;
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    try {
                        OfflinePlayer sender = Bukkit.getOfflinePlayer(UUID.fromString(req.getSenderUuid()));
                        meta.setOwningPlayer(sender);
                    } catch (Exception ignored) {}
                    meta.setDisplayName("§a" + req.getSenderName());
                    meta.setLore(Arrays.asList(
                        "§7残り時間: §f" + req.getRemainingSeconds() + "秒",
                        "",
                        "§a左クリック: 承認",
                        "§c右クリック: 拒否"
                    ));
                    head.setItemMeta(meta);
                }
                inv.setItem(slot++, head);
            }
        }

        // 最後の行: ボタン
        int lastRow = size - 9;
        inv.setItem(lastRow + 2, createItem(Material.ENDER_PEARL, "§b§lリクエストを送信",
            "§7近くのプレイヤーにTPAリクエストを送信"));
        inv.setItem(lastRow + 6, createItem(Material.BARRIER, "§c閉じる"));

        p.openInventory(inv);
    }

    // ========== 送信先選択画面 ==========

    @SuppressWarnings("deprecation")
    public static void openSendUI(Player p, TpaModule module) {
        List<Player> candidates = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.getUniqueId().equals(p.getUniqueId())) continue;
            // 同じワールドのプレイヤーのみ（オプション: 全ワールドも可）
            candidates.add(pl);
        }

        // Bedrockプレイヤーの場合
        if (FloodgateUtil.isBedrock(p)) {
            openBedrockSendForm(p, module, candidates);
            return;
        }

        // Java用 Chest GUI
        int size = Math.max(27, 9 * ((candidates.size() + 8) / 9 + 1));
        size = Math.min(size, 54);

        TpaHolder holder = new TpaHolder(UIType.SEND_SELECT, module, null, candidates);
        Inventory inv = Bukkit.createInventory(holder, size, TITLE_SEND);
        holder.setInventory(inv);

        if (candidates.isEmpty()) {
            ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int i = 0; i < size; i++) {
                inv.setItem(i, border);
            }
            inv.setItem(13, createItem(Material.BARRIER, "§c送信可能なプレイヤーがいません",
                "§7他にオンラインのプレイヤーがいません"));
        } else {
            int slot = 0;
            for (Player pl : candidates) {
                if (slot >= size - 9) break;
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(pl);
                    meta.setDisplayName("§a" + pl.getName());
                    
                    // TPA受信が無効かどうかをチェック
                    boolean canReceive = module.canReceiveTpa(pl);
                    if (canReceive) {
                        meta.setLore(Arrays.asList(
                            "§7クリックでTPAリクエストを送信"
                        ));
                    } else {
                        meta.setDisplayName("§7" + pl.getName());
                        meta.setLore(Arrays.asList(
                            "§cこのプレイヤーはTPA受信を無効にしています"
                        ));
                    }
                    head.setItemMeta(meta);
                }
                inv.setItem(slot++, head);
            }
        }

        // 最後の行: 戻る
        int lastRow = size - 9;
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = lastRow; i < size; i++) {
            inv.setItem(i, border);
        }
        inv.setItem(lastRow + 4, createItem(Material.ARROW, "§e戻る"));

        p.openInventory(inv);
    }

    // ========== Bedrock対応 ==========

    private static void openBedrockMainForm(Player p, TpaModule module, List<TpaRequest> requests) {
        List<FormsUtil.ButtonSpec> buttons = new ArrayList<>();

        for (TpaRequest req : requests) {
            String senderName = req.getSenderName();
            String sanitized = sanitizeName(senderName);
            String url = "https://minotar.net/avatar/" + sanitized + "/64";
            buttons.add(FormsUtil.ButtonSpec.ofUrl(senderName + " (" + req.getRemainingSeconds() + "秒)", url));
        }
        buttons.add(FormsUtil.ButtonSpec.ofText("リクエストを送信"));
        buttons.add(FormsUtil.ButtonSpec.ofText("閉じる"));

        String title = "TPA リクエスト (" + requests.size() + "件)";
        FormsUtil.openSimpleForm(p, title, buttons, idx -> {
            if (idx < 0) return;
            
            if (idx < requests.size()) {
                // リクエスト選択 -> 承認/拒否選択
                TpaRequest req = requests.get(idx);
                openBedrockActionForm(p, module, req);
                return;
            }
            
            if (idx == requests.size()) {
                // リクエスト送信
                List<Player> candidates = new ArrayList<>();
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    if (!pl.getUniqueId().equals(p.getUniqueId())) {
                        candidates.add(pl);
                    }
                }
                openBedrockSendForm(p, module, candidates);
                return;
            }
            // 閉じる
        });
    }

    private static void openBedrockActionForm(Player p, TpaModule module, TpaRequest req) {
        String content = req.getSenderName() + " からのTPAリクエスト\n残り時間: " + req.getRemainingSeconds() + "秒";
        FormsUtil.openModalForm(p, "TPAリクエスト", content, "承認", "拒否", accepted -> {
            if (accepted) {
                module.acceptRequest(p, req.getSenderName());
            } else {
                module.denyRequest(p, req.getSenderName());
            }
        });
    }

    private static void openBedrockSendForm(Player p, TpaModule module, List<Player> candidates) {
        List<FormsUtil.ButtonSpec> buttons = new ArrayList<>();
        List<Player> validCandidates = new ArrayList<>();

        for (Player pl : candidates) {
            String name = pl.getName();
            String sanitized = sanitizeName(name);
            String url = "https://minotar.net/avatar/" + sanitized + "/64";
            
            if (module.canReceiveTpa(pl)) {
                buttons.add(FormsUtil.ButtonSpec.ofUrl(name, url));
                validCandidates.add(pl);
            }
        }
        buttons.add(FormsUtil.ButtonSpec.ofText("戻る"));

        FormsUtil.openSimpleForm(p, "TPA 送信先を選択", buttons, idx -> {
            if (idx < 0) return;
            
            if (idx < validCandidates.size()) {
                Player target = validCandidates.get(idx);
                module.sendRequest(p, target);
                return;
            }
            // 戻る
            openBedrockMainForm(p, module, module.getStore().getRequestsFor(p.getUniqueId().toString()));
        });
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

    private static String sanitizeName(String name) {
        if (name == null) return "";
        return name.replaceAll("[^A-Za-z0-9_]", "");
    }
}
