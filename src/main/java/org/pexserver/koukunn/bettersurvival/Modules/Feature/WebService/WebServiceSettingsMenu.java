package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap.WebMapModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap.WebMapSettingsMenu;

/**
 * /webservice の設定 GUI。
 *
 * メインメニューはカテゴリの入口だけに絞り、
 * 「フィード・連携」「WebMap」「ネットワーク」のサブメニューへ分割している。
 * 新しい機能はカテゴリ内にボタンを足すだけで拡張できる。
 */
public final class WebServiceSettingsMenu {

    private WebServiceSettingsMenu() {
    }

    private static String onOff(boolean value) {
        return value ? "§aON" : "§cOFF";
    }

    // ================= メイン =================

    public static void openMainMenu(Player player, WebServiceModule service) {
        WebMapModule webMap = service.getPlugin().getWebMapModule();
        boolean enabled = service.isGloballyEnabled();
        boolean running = webMap != null && webMap.isServerRunning();
        String url = webMap == null ? "N/A" : webMap.getPublicUrl();

        ChestUI.builder()
                .title("§8WebService 設定")
                .size(27)
                .addButtonAt(4,
                        running ? "§a● WebService: 稼働中" : "§c● WebService: 停止中",
                        running ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                        "§7URL: §f" + url
                                + "\n§7機能: " + (enabled ? "§a有効" : "§c無効")
                                + "\n\n§eクリックでHTTPサーバーを再起動")
                .addButtonAt(10,
                        enabled ? "§aWebService: 有効" : "§cWebService: 無効",
                        enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                        "§7ホームページ・ログイン・フィードの全体スイッチ\n§7クリックで切替")
                .addButtonAt(12,
                        "§b§lフィード・連携",
                        Material.WRITABLE_BOOK,
                        "§7Social Feed / Minecraft / Discord / 画像\n§7の連携設定")
                .addButtonAt(14,
                        "§e§lWebMap",
                        Material.FILLED_MAP,
                        "§7Web地図の公開設定と詳細設定")
                .addButtonAt(16,
                        "§d§lネットワーク",
                        Material.REPEATER,
                        "§7ポート / 公開範囲 / HAProxy PROXY protocol v2")
                .addButtonAt(26, "§7閉じる", Material.BARRIER, "")
                .then((result, p) -> {
                    if (!result.success || result.slot == null) {
                        return;
                    }
                    switch (result.slot) {
                        case 4 -> {
                            service.restartSharedHttpServer();
                            p.sendMessage("§aWebService HTTP サーバーを再起動しました");
                            openMainMenu(p, service);
                        }
                        case 10 -> {
                            service.toggleGloballyEnabled();
                            openMainMenu(p, service);
                        }
                        case 12 -> openFeedMenu(p, service);
                        case 14 -> openWebMapMenu(p, service);
                        case 16 -> openNetworkMenu(p, service);
                        case 26 -> ChestUI.closeMenu(p);
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    // ================= フィード・連携 =================

    private static void openFeedMenu(Player player, WebServiceModule service) {
        ChestUI.builder()
                .title("§8フィード・連携")
                .size(36)
                .addButtonAt(4, "§b§lフィード・連携", Material.WRITABLE_BOOK,
                        "§7Web・Minecraft・Discord の3面で\n§7投稿が同じように共有されます")
                .addButtonAt(10,
                        "§fSocial Feed: " + onOff(service.isFeedEnabled()),
                        service.isFeedEnabled() ? Material.WRITABLE_BOOK : Material.BOOK,
                        "§7Web版フィードのメイン機能\n§7クリックで切替")
                .addButtonAt(11,
                        "§fMinecraft → Web: " + onOff(service.isMinecraftChatRelayEnabled()),
                        Material.OAK_SIGN,
                        "§7Minecraft チャットをフィードへ保存\n§7クリックで切替")
                .addButtonAt(12,
                        "§fWeb → Minecraft: " + onOff(service.isWebPostToMinecraftEnabled()),
                        Material.EMERALD,
                        "§7Web投稿をゲーム内チャットへ中継\n§7URLと画像はクリック可能テキストになります\n§7クリックで切替")
                .addButtonAt(13,
                        "§fDiscord ↔ Web: " + onOff(service.isDiscordIntegrationEnabled()),
                        Material.ECHO_SHARD,
                        "§7Discord BotMode との双方向連携\n§7Web投稿(画像含む)はDiscordへ、\n§7Discord投稿はWeb/Minecraftへ反映\n§7クリックで切替")
                .addButtonAt(14,
                        "§f画像アップロード: " + onOff(service.isImageUploadEnabled()),
                        Material.PAINTING,
                        "§7Web投稿への画像添付を許可\n§7クリックで切替")
                .addButtonAt(16,
                        "§f履歴保存: §e" + service.getFeedRetentionDays() + "日",
                        Material.CLOCK,
                        "§7投稿履歴の保存期間 (1〜30日)\n§7クリックで変更")
                .addButtonAt(31, "§7戻る", Material.ARROW, "")
                .then((result, p) -> {
                    if (!result.success || result.slot == null) {
                        return;
                    }
                    switch (result.slot) {
                        case 10 -> {
                            service.toggleFeedEnabled();
                            openFeedMenu(p, service);
                        }
                        case 11 -> {
                            service.toggleMinecraftChatRelayEnabled();
                            openFeedMenu(p, service);
                        }
                        case 12 -> {
                            service.toggleWebPostToMinecraftEnabled();
                            openFeedMenu(p, service);
                        }
                        case 13 -> {
                            service.toggleDiscordIntegrationEnabled();
                            openFeedMenu(p, service);
                        }
                        case 14 -> {
                            service.toggleImageUploadEnabled();
                            openFeedMenu(p, service);
                        }
                        case 16 -> openRetentionDialog(p, service);
                        case 31 -> openMainMenu(p, service);
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    // ================= WebMap =================

    private static void openWebMapMenu(Player player, WebServiceModule service) {
        WebMapModule webMap = service.getPlugin().getWebMapModule();
        boolean toggleOn = webMap != null && webMap.isGloballyEnabled();
        boolean gateOn = service.isWebMapFeatureEnabled();
        boolean accessOn = service.isWebMapAccessEnabled();
        boolean paused = webMap != null && webMap.getSettings().isPaused();

        ChestUI.builder()
                .title("§8WebMap 設定")
                .size(36)
                .addButtonAt(4,
                        accessOn ? "§a● WebMap: 公開中" : "§c● WebMap: 非公開",
                        accessOn ? Material.FILLED_MAP : Material.MAP,
                        "§7公開には下の2つのスイッチが両方ONで\n§7かつ WebService 本体が有効な必要があります")
                .addButtonAt(10,
                        "§fWebMap 公開ゲート: " + onOff(gateOn),
                        gateOn ? Material.EMERALD_BLOCK : Material.BARRIER,
                        "§7WebService 側の公開スイッチ\n§7クリックで切替")
                .addButtonAt(11,
                        "§fWebMap 機能 (Toggle): " + onOff(toggleOn),
                        toggleOn ? Material.COMPARATOR : Material.LEVER,
                        "§7/toggle 側のグローバルスイッチ\n§7クリックで切替")
                .addButtonAt(13,
                        paused ? "§c自動反映: 停止中" : "§a自動反映: 動作中",
                        paused ? Material.BARRIER : Material.CLOCK,
                        "§7地図の自動更新を一時停止/再開\n§7クリックで切替")
                .addButtonAt(14,
                        "§cChunkGen 全停止",
                        Material.TNT,
                        "§7全ディメンションの ChunkGen を停止")
                .addButtonAt(16,
                        "§e§l詳細設定",
                        Material.COMPASS,
                        "§7ディメンション・イベント・ChunkGen など\n§7WebMap 専用の詳細メニューを開く")
                .addButtonAt(31, "§7戻る", Material.ARROW, "")
                .then((result, p) -> {
                    if (!result.success || result.slot == null) {
                        return;
                    }
                    WebMapModule map = service.getPlugin().getWebMapModule();
                    switch (result.slot) {
                        case 10 -> {
                            service.toggleWebMapFeatureEnabled();
                            openWebMapMenu(p, service);
                        }
                        case 11 -> {
                            if (service.getPlugin().getToggleModule() != null) {
                                boolean current = service.getPlugin().getToggleModule().getGlobal("webmap");
                                service.getPlugin().getToggleModule().setGlobal("webmap", !current);
                            }
                            openWebMapMenu(p, service);
                        }
                        case 13 -> {
                            if (map != null) {
                                map.togglePaused();
                            }
                            openWebMapMenu(p, service);
                        }
                        case 14 -> {
                            if (map != null) {
                                map.stopAllChunkGen();
                                p.sendMessage("§eChunkGen を全て停止しました");
                            }
                            openWebMapMenu(p, service);
                        }
                        case 16 -> {
                            if (map != null) {
                                WebMapSettingsMenu.openMainMenu(p, map);
                            } else {
                                openWebMapMenu(p, service);
                            }
                        }
                        case 31 -> openMainMenu(p, service);
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    // ================= ネットワーク =================

    private static void openNetworkMenu(Player player, WebServiceModule service) {
        WebMapModule webMap = service.getPlugin().getWebMapModule();
        int port = webMap == null ? 8123 : webMap.getSettings().getPort();
        boolean publicAccess = webMap != null && webMap.getSettings().isPublicAccess();
        boolean haproxy = service.isHaproxyProtocolEnabled();

        ChestUI.builder()
                .title("§8ネットワーク設定")
                .size(36)
                .addButtonAt(4, "§d§lネットワーク", Material.REPEATER,
                        "§7URL: §f" + (webMap == null ? "N/A" : webMap.getPublicUrl())
                                + "\n§7変更は保存後に自動で再起動されます")
                .addButtonAt(10,
                        "§fポート: §e" + port,
                        Material.HOPPER,
                        "§7WebService / WebMap 共通の HTTP ポート\n§7クリックで変更 (1024-65535)")
                .addButtonAt(12,
                        publicAccess ? "§f公開範囲: §ePublic (0.0.0.0)" : "§f公開範囲: §bLocal (127.0.0.1)",
                        publicAccess ? Material.GLOBE_BANNER_PATTERN : Material.ENDER_PEARL,
                        "§7外部公開するかローカル限定にするか\n§7クリックで切替")
                .addButtonAt(14,
                        "§fHAProxy Proxy Protocol v2: " + onOff(haproxy),
                        haproxy ? Material.LIGHTNING_ROD : Material.GRAY_DYE,
                        "§7リバースプロキシ(HAProxy等)経由の接続で\n§7実クライアントIPを取得します"
                                + "\n§7(レート制限・表示回数の判定に使用)"
                                + "\n§eHAProxy側: server 行に send-proxy-v2 を指定"
                                + "\n§c有効中は PROXY ヘッダーの無い直接接続を拒否"
                                + "\n§7クリックで切替 (自動で再起動)")
                .addButtonAt(16,
                        "§fHTTP 再起動",
                        Material.LIME_DYE,
                        "§7HTTP サーバーを再起動します")
                .addButtonAt(31, "§7戻る", Material.ARROW, "")
                .then((result, p) -> {
                    if (!result.success || result.slot == null) {
                        return;
                    }
                    switch (result.slot) {
                        case 10 -> openPortDialog(p, service);
                        case 12 -> {
                            if (service.getPlugin().getWebMapModule() != null) {
                                service.getPlugin().getWebMapModule().togglePublicAccess();
                            }
                            openNetworkMenu(p, service);
                        }
                        case 14 -> {
                            service.toggleHaproxyProtocolEnabled();
                            p.sendMessage(service.isHaproxyProtocolEnabled()
                                    ? "§aHAProxy PROXY protocol v2 を有効にしました (HTTP再起動済み)"
                                    : "§eHAProxy PROXY protocol v2 を無効にしました (HTTP再起動済み)");
                            openNetworkMenu(p, service);
                        }
                        case 16 -> {
                            service.restartSharedHttpServer();
                            p.sendMessage("§aWebService HTTP サーバーを再起動しました");
                            openNetworkMenu(p, service);
                        }
                        case 31 -> openMainMenu(p, service);
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    // ================= ダイアログ =================

    private static void openRetentionDialog(Player player, WebServiceModule service) {
        DialogUI.builder()
                .title("Social Feed Retention")
                .body("投稿履歴をローカルJSONに保存する日数を設定します")
                .body("1-30日の範囲で指定できます。標準は7日です")
                .addTextInput("days", "保存日数", String.valueOf(service.getFeedRetentionDays()), 2, false)
                .confirmation("保存", "戻る")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openFeedMenu(p, service);
                        return;
                    }
                    String rawValue = result.getText("days").trim();
                    if (!rawValue.matches("\\d+")) {
                        p.sendMessage("§c保存日数は数字で入力してください");
                        openFeedMenu(p, service);
                        return;
                    }
                    int days = Integer.parseInt(rawValue);
                    if (days < 1 || days > 30) {
                        p.sendMessage("§c保存日数は 1-30 の範囲で指定してください");
                        openFeedMenu(p, service);
                        return;
                    }
                    service.updateFeedRetentionDays(days);
                    p.sendMessage("§a履歴保存期間を " + days + " 日に変更しました");
                    openFeedMenu(p, service);
                })
                .show(player);
    }

    private static void openPortDialog(Player player, WebServiceModule service) {
        WebMapModule webMap = service.getPlugin().getWebMapModule();
        int currentPort = webMap == null ? 8123 : webMap.getSettings().getPort();
        DialogUI.builder()
                .title("WebService Port")
                .body("1024-65535 の範囲で WebService / WebMap 共通ポートを設定します")
                .addTextInput("port", "Port", String.valueOf(currentPort), 5, false)
                .confirmation("保存", "戻る")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openNetworkMenu(p, service);
                        return;
                    }
                    String rawValue = result.getText("port").trim();
                    if (!rawValue.matches("\\d+")) {
                        p.sendMessage("§cポートは数字で入力してください");
                        openNetworkMenu(p, service);
                        return;
                    }
                    int port = Integer.parseInt(rawValue);
                    if (port < 1024 || port > 65535) {
                        p.sendMessage("§cポートは 1024-65535 の範囲で指定してください");
                        openNetworkMenu(p, service);
                        return;
                    }
                    if (service.getPlugin().getWebMapModule() != null) {
                        service.getPlugin().getWebMapModule().updatePort(port);
                    }
                    p.sendMessage("§aWebService HTTP ポートを " + port + " に変更しました");
                    openNetworkMenu(p, service);
                })
                .show(player);
    }
}
