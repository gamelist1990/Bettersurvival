package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap.WebMapModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap.WebMapSettingsMenu;

public final class WebServiceSettingsMenu {
    private WebServiceSettingsMenu() {
    }

    public static void openMainMenu(Player player, WebServiceModule service) {
        WebMapModule webMap = service.getPlugin().getWebMapModule();
        boolean webServiceEnabled = service.isGloballyEnabled();
        boolean webMapToggleEnabled = webMap != null && webMap.isGloballyEnabled();
        boolean webMapFeatureEnabled = service.isWebMapFeatureEnabled();
        boolean webMapAccessEnabled = service.isWebMapAccessEnabled();
        boolean feedEnabled = service.isFeedEnabled();
        boolean minecraftRelayEnabled = service.isMinecraftChatRelayEnabled();
        boolean webPostRelayEnabled = service.isWebPostToMinecraftEnabled();
        boolean discordIntegrationEnabled = service.isDiscordIntegrationEnabled();
        boolean imageUploadEnabled = service.isImageUploadEnabled();
        String url = webMap == null ? "N/A" : webMap.getPublicUrl();
        boolean running = webMap != null && webMap.isServerRunning();

        ChestUI.builder()
            .title("WebService Control Center")
            .size(54)
                .addButtonAt(10,
                        webServiceEnabled ? "§aWebService: 有効" : "§cWebService: 停止中",
                        webServiceEnabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                        "§7ホームページ・ログイン・プロフィールの入口\n§7クリックで有効/停止")
                .addButtonAt(11,
                        running ? "§aHTTP: ONLINE" : "§cHTTP: OFFLINE",
                        running ? Material.LIME_DYE : Material.GRAY_DYE,
                        "§7URL: " + url + "\n§7クリックで再起動")
                .addButtonAt(12,
                        webMap == null ? "§cPort: N/A" : "§6Port: " + webMap.getSettings().getPort(),
                        Material.REPEATER,
                        "§7WebService / WebMap 共通 HTTP ポートを変更")
                .addButtonAt(13,
                        webMap != null && webMap.getSettings().isPublicAccess() ? "§e公開範囲: Public" : "§b公開範囲: Local",
                        webMap != null && webMap.getSettings().isPublicAccess() ? Material.GLOBE_BANNER_PATTERN : Material.ENDER_PEARL,
                        "§70.0.0.0 / 127.0.0.1 を切替")
                .addButtonAt(14,
                        webMapFeatureEnabled ? "§aWebMap 機能: 有効" : "§cWebMap 機能: 無効",
                        webMapFeatureEnabled ? Material.FILLED_MAP : Material.MAP,
                        "§7WebService 側の WebMap 公開ゲート\n§7Toggle OP の WebMap と両方ONで表示")
                .addButtonAt(15,
                        webMapToggleEnabled ? "§aToggle WebMap: ON" : "§cToggle WebMap: OFF",
                        webMapToggleEnabled ? Material.COMPARATOR : Material.LEVER,
                        "§7/toggle OP 側の WebMap グローバル状態\n§7ここでは状態表示のみ")
                .addButtonAt(16,
                        webMapAccessEnabled ? "§aWebMap Access: 許可" : "§cWebMap Access: ブロック",
                        webMapAccessEnabled ? Material.EMERALD_BLOCK : Material.BARRIER,
                        "§7保全用の最終判定\n§7WebService有効 + WebMap機能有効 + Toggle WebMap ON")
                .addButtonAt(20,
                        "§eWebMap 詳細設定",
                        Material.COMPASS,
                        "§7ディメンション・イベント・ChunkGen など")
                .addButtonAt(21,
                        webMap != null && webMap.getSettings().isPaused() ? "§c自動反映: 停止中" : "§a自動反映: 動作中",
                        webMap != null && webMap.getSettings().isPaused() ? Material.BARRIER : Material.CLOCK,
                        "§7WebMap の一時停止 / 再開")
                .addButtonAt(22,
                        "§cChunkGen 全停止",
                        Material.BARRIER,
                        "§7全ディメンションの ChunkGen を停止")
                .addButtonAt(28,
                    feedEnabled ? "§aSocial Feed: 有効" : "§cSocial Feed: 無効",
                    feedEnabled ? Material.WRITABLE_BOOK : Material.BOOK,
                    "§7Web版 Minecraft Twitter のメイン機能\n§7投稿・プロフィール")
                .addButtonAt(29,
                    minecraftRelayEnabled ? "§aMinecraft → Web: ON" : "§cMinecraft → Web: OFF",
                    minecraftRelayEnabled ? Material.OAK_SIGN : Material.PAPER,
                    "§7Minecraft チャットを Web へ保存\n§7JSON履歴にも反映")
                .addButtonAt(30,
                    webPostRelayEnabled ? "§aWeb → Minecraft: ON" : "§cWeb → Minecraft: OFF",
                    webPostRelayEnabled ? Material.EMERALD : Material.REDSTONE,
                    "§7Web 投稿を Minecraft チャットへ中継\n§7Webからサーバー内会話へ参加")
                .addButtonAt(31,
                    discordIntegrationEnabled ? "§aDiscord ↔ Web: ON" : "§cDiscord ↔ Web: OFF",
                    discordIntegrationEnabled ? Material.ECHO_SHARD : Material.GRAY_DYE,
                    "§7Discord BotMode 側の連携設定と両方ONで\n§7Discord投稿・返信・添付をWebServiceへ保存")
                .addButtonAt(32,
                    imageUploadEnabled ? "§aImage Upload: ON" : "§cImage Upload: OFF",
                    imageUploadEnabled ? Material.PAINTING : Material.BARRIER,
                    "§7Web投稿画像の添付を許可\n§7ブラウザ側で720p程度に軽量化")
                .addButtonAt(33,
                    "§e履歴保存: " + service.getFeedRetentionDays() + "日",
                    Material.CLOCK,
                    "§7ローカルJSONの保存期間\n§7範囲: 1-30日 / 標準: 7日")
                .addButtonAt(34,
                    "§bProfile Custom",
                    Material.PLAYER_HEAD,
                    "§7IDとアイコンはMinecraft固定\n§7ニックネーム・壁紙・所在地・URL・自己紹介をWebで編集")
                .addButtonAt(35,
                    "§dRealtime Mode: Long Poll",
                    Material.REPEATER,
                    "§7WebSocket非依存のリアルタイム風通信\n§7非WebSocket環境でもHTTPロングポーリングで更新")
                .addButtonAt(49, "§7閉じる", Material.BARRIER, "")
                .then((result, p) -> {
                    if (!result.success || result.slot == null) {
                        return;
                    }
                    switch (result.slot) {
                        case 10 -> {
                            service.toggleGloballyEnabled();
                            openMainMenu(p, service);
                        }
                        case 11 -> {
                            service.restartSharedHttpServer();
                            openMainMenu(p, service);
                        }
                        case 12 -> openPortDialog(p, service);
                        case 13 -> {
                            if (webMap != null) {
                                webMap.togglePublicAccess();
                            }
                            openMainMenu(p, service);
                        }
                        case 14 -> {
                            service.toggleWebMapFeatureEnabled();
                            openMainMenu(p, service);
                        }
                        case 20 -> {
                            if (webMap != null) {
                                WebMapSettingsMenu.openMainMenu(p, webMap);
                            } else {
                                openMainMenu(p, service);
                            }
                        }
                        case 21 -> {
                            if (webMap != null) {
                                webMap.togglePaused();
                            }
                            openMainMenu(p, service);
                        }
                        case 22 -> {
                            if (webMap != null) {
                                webMap.stopAllChunkGen();
                            }
                            openMainMenu(p, service);
                        }
                        case 28 -> {
                            service.toggleFeedEnabled();
                            openMainMenu(p, service);
                        }
                        case 29 -> {
                            service.toggleMinecraftChatRelayEnabled();
                            openMainMenu(p, service);
                        }
                        case 30 -> {
                            service.toggleWebPostToMinecraftEnabled();
                            openMainMenu(p, service);
                        }
                        case 31 -> {
                            service.toggleDiscordIntegrationEnabled();
                            openMainMenu(p, service);
                        }
                        case 32 -> {
                            service.toggleImageUploadEnabled();
                            openMainMenu(p, service);
                        }
                        case 33 -> openRetentionDialog(p, service);
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    private static void openRetentionDialog(Player player, WebServiceModule service) {
        DialogUI.builder()
                .title("Social Feed Retention")
                .body("Web版 Minecraft Twitter の投稿履歴をローカルJSONに保存する日数を設定します")
                .body("1-30日の範囲で指定できます。標準は7日です")
                .addTextInput("days", "保存日数", String.valueOf(service.getFeedRetentionDays()), 2, false)
                .confirmation("保存", "戻る")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openMainMenu(p, service);
                        return;
                    }
                    String rawValue = result.getText("days").trim();
                    if (!rawValue.matches("\\d+")) {
                        p.sendMessage("§c保存日数は数字で入力してください");
                        openMainMenu(p, service);
                        return;
                    }
                    int days = Integer.parseInt(rawValue);
                    if (days < 1 || days > 30) {
                        p.sendMessage("§c保存日数は 1-30 の範囲で指定してください");
                        openMainMenu(p, service);
                        return;
                    }
                    service.updateFeedRetentionDays(days);
                    p.sendMessage("§aWebService Social Feed の履歴保存期間を " + days + " 日に変更しました");
                    openMainMenu(p, service);
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
                        openMainMenu(p, service);
                        return;
                    }
                    String rawValue = result.getText("port").trim();
                    if (!rawValue.matches("\\d+")) {
                        p.sendMessage("§cポートは数字で入力してください");
                        openMainMenu(p, service);
                        return;
                    }
                    int port = Integer.parseInt(rawValue);
                    if (port < 1024 || port > 65535) {
                        p.sendMessage("§cポートは 1024-65535 の範囲で指定してください");
                        openMainMenu(p, service);
                        return;
                    }
                    if (service.getPlugin().getWebMapModule() != null) {
                        service.getPlugin().getWebMapModule().updatePort(port);
                    }
                    p.sendMessage("§aWebService HTTP ポートを " + port + " に変更しました");
                    openMainMenu(p, service);
                })
                .show(player);
    }
}
