package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;

import java.util.List;

public final class WebMapSettingsMenu {
    private WebMapSettingsMenu() {
    }

    public static void openMainMenu(Player player, WebMapModule module) {
        WebMapSettings settings = module.getSettings();
        ChestUI.builder()
                .title("WebMap Settings")
                .size(36)
                .addButtonAt(10,
                        module.isServerRunning() ? "§aWebMap: ONLINE" : "§cWebMap: OFFLINE",
                        module.isServerRunning() ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                        "§7URL: " + module.getPublicUrl())
                .addButtonAt(11,
                        settings.isPaused() ? "§c一時停止中" : "§a自動反映中",
                        settings.isPaused() ? Material.BARRIER : Material.CLOCK,
                        "§7クリックで一時停止 / 再開")
                .addButtonAt(12,
                        settings.isPublicAccess() ? "§e公開範囲: Public" : "§b公開範囲: Local",
                        settings.isPublicAccess() ? Material.GLOBE_BANNER_PATTERN : Material.ENDER_PEARL,
                        "§70.0.0.0 / 127.0.0.1 を切替")
                .addButtonAt(13,
                        "§6Port: " + settings.getPort(),
                        Material.REPEATER,
                        "§7クリックでポートを変更")
                .addButtonAt(14,
                        settings.isAutoTrackPlayers() ? "§a探索追従: ON" : "§c探索追従: OFF",
                        settings.isAutoTrackPlayers() ? Material.MAP : Material.FILLED_MAP,
                        "§7プレイヤーの到達チャンクを自動反映")
                .addButtonAt(15,
                        "§e再起動",
                        Material.BLAZE_ROD,
                        "§7Web サーバーを再起動")
                .addButtonAt(16,
                        settings.isShowTpsBar() ? "§aTPSバー表示: ON" : "§cTPSバー表示: OFF",
                        Material.TARGET,
                        "§7画面上部にサーバーTPS/メモリを表示する")
                .addButtonAt(20,
                        "§bディメンション設定",
                        Material.COMPASS,
                        "§7表示・追跡・ChunkGen の個別設定")
                .addButtonAt(21,
                        "§9イベント設定",
                        Material.LECTERN,
                        "§7squaremap 風の更新イベント設定")
                .addButtonAt(22,
                        "§cChunkGen 全停止",
                        Material.BARRIER,
                        "§7全ディメンションの ChunkGen を停止")
                .addButtonAt(23,
                        "§3URL 情報",
                        Material.NAME_TAG,
                        "§7" + module.getPublicUrl())
                .addButtonAt(31, "§7閉じる", Material.BARRIER, "")
                .then((result, p) -> {
                    if (!result.success || result.slot == null) {
                        return;
                    }
                    switch (result.slot) {
                        case 11 -> {
                            module.togglePaused();
                            openMainMenu(p, module);
                        }
                        case 12 -> {
                            module.togglePublicAccess();
                            openMainMenu(p, module);
                        }
                        case 13 -> openPortDialog(p, module);
                        case 14 -> {
                            module.toggleAutoTrackPlayers();
                            openMainMenu(p, module);
                        }
                        case 15 -> {
                            module.restartServer();
                            openMainMenu(p, module);
                        }
                        case 16 -> {
                            module.toggleShowTpsBar();
                            openMainMenu(p, module);
                        }
                        case 20 -> openWorldListMenu(p, module);
                        case 21 -> openEventMenu(p, module);
                        case 22 -> {
                            module.stopAllChunkGen();
                            openMainMenu(p, module);
                        }
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    public static void openEventMenu(Player player, WebMapModule module) {
        WebMapSettings.EventSettings events = module.getSettings().getEvents();
        ChestUI.builder()
                .title("WebMap Events")
                .size(27)
                .addButtonAt(10,
                        events.isPlayerMove() ? "§aPlayerMove: ON" : "§cPlayerMove: OFF",
                        Material.LEATHER_BOOTS,
                        "§7プレイヤー移動時に足元の chunk を反映")
                .addButtonAt(11,
                        events.isChunkLoad() ? "§aChunkLoad: ON" : "§cChunkLoad: OFF",
                        Material.MINECART,
                        "§7通常の chunk load を dirty 化")
                .addButtonAt(12,
                        events.isChunkPopulate() ? "§aChunkPopulate: ON" : "§cChunkPopulate: OFF",
                        Material.GRASS_BLOCK,
                        "§7新規生成 chunk の populate を dirty 化")
                .addButtonAt(13,
                        events.isBlockUpdate() ? "§aBlockUpdate: ON" : "§cBlockUpdate: OFF",
                        Material.BRICKS,
                        "§7BlockPlace / BlockBreak を dirty 化")
                .addButtonAt(26, "§e戻る", Material.ARROW, "")
                .then((result, p) -> {
                    if (!result.success || result.slot == null) {
                        return;
                    }
                    switch (result.slot) {
                        case 10 -> module.toggleEventPlayerMove();
                        case 11 -> module.toggleEventChunkLoad();
                        case 12 -> module.toggleEventChunkPopulate();
                        case 13 -> module.toggleEventBlockUpdate();
                        case 26 -> {
                            openMainMenu(p, module);
                            return;
                        }
                        default -> {
                        }
                    }
                    openEventMenu(p, module);
                })
                .show(player);
    }

    public static void openWorldListMenu(Player player, WebMapModule module) {
        List<WebMapDimensionSettings> dimensions = module.getDimensionSettingsList();
        int size = Math.min(54, Math.max(27, ((dimensions.size() + 8) / 9 + 1) * 9));
        int backSlot = size - 1;
        ChestUI.Builder builder = ChestUI.builder()
                .title("WebMap Dimensions")
                .size(size);
        int slot = 0;
        for (WebMapDimensionSettings dimension : dimensions) {
            World world = module.getPlugin().getServer().getWorlds().stream()
                    .filter(entry -> entry.getKey().toString().equals(dimension.getWorldKey()))
                    .findFirst()
                    .orElse(null);
            String environment = world == null ? "UNKNOWN" : world.getEnvironment().name();
            builder.addButtonAt(slot++,
                    (dimension.isVisible() ? "§a" : "§c") + dimension.getDisplayName(),
                    dimension.isVisible() ? Material.FILLED_MAP : Material.MAP,
                    "§7" + environment
                            + "\n§7AutoTrack: " + onOff(dimension.isAutoTrack())
                            + "\n§7ChunkGen: " + onOff(dimension.isChunkGenEnabled())
                            + "\n§7Saved Chunks: " + module.getChunkCount(dimension.getWorldKey()));
        }
        builder.addButtonAt(backSlot, "§e戻る", Material.ARROW, "");
        builder.then((result, p) -> {
            if (!result.success || result.slot == null) {
                return;
            }
            if (result.slot == backSlot) {
                openMainMenu(p, module);
                return;
            }
            if (result.slot >= 0 && result.slot < dimensions.size()) {
                openWorldDetailMenu(p, module, dimensions.get(result.slot).getWorldKey());
            }
        }).show(player);
    }

    public static void openWorldDetailMenu(Player player, WebMapModule module, String worldKey) {
        WebMapDimensionSettings dimension = module.getSettings().getDimensions().get(worldKey);
        if (dimension == null) {
            openWorldListMenu(player, module);
            return;
        }
        ChestUI.builder()
                .title("World: " + dimension.getDisplayName())
                .size(27)
                .addButtonAt(10,
                        dimension.isVisible() ? "§a表示: ON" : "§c表示: OFF",
                        dimension.isVisible() ? Material.LIME_DYE : Material.GRAY_DYE,
                        "§7WebMap 一覧に表示する")
                .addButtonAt(11,
                        dimension.isAutoTrack() ? "§a探索追跡: ON" : "§c探索追跡: OFF",
                        dimension.isAutoTrack() ? Material.COMPASS : Material.BARRIER,
                        "§7プレイヤー到達時の自動反映")
                .addButtonAt(12,
                        dimension.isChunkGenEnabled() ? "§aChunkGen: ON" : "§cChunkGen: OFF",
                        dimension.isChunkGenEnabled() ? Material.DIAMOND_PICKAXE : Material.WOODEN_PICKAXE,
                        "§7スポーン起点から外側へ広げて生成")
                .addButtonAt(13,
                        "§cChunkGen 全停止",
                        Material.BARRIER,
                        "§7全ディメンションの ChunkGen を停止")
                .addButtonAt(15,
                        "§7保存済み: " + module.getChunkCount(worldKey) + " chunks",
                        Material.CHEST,
                        "§7WebMap/" + worldKey)
                .addButtonAt(26, "§e戻る", Material.ARROW, "")
                .then((result, p) -> {
                    if (!result.success || result.slot == null) {
                        return;
                    }
                    switch (result.slot) {
                        case 10 -> module.toggleWorldVisible(worldKey);
                        case 11 -> module.toggleWorldTracking(worldKey);
                        case 12 -> module.toggleChunkGen(worldKey);
                        case 13 -> module.stopAllChunkGen();
                        case 26 -> {
                            openWorldListMenu(p, module);
                            return;
                        }
                        default -> {
                        }
                    }
                    openWorldDetailMenu(p, module, worldKey);
                })
                .show(player);
    }

    private static void openPortDialog(Player player, WebMapModule module) {
        DialogUI.builder()
                .title("WebMap Port")
                .body("1024-65535 の範囲でポートを設定します")
                .addTextInput("port", "Port", String.valueOf(module.getSettings().getPort()), 5, false)
                .confirmation("保存", "戻る")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openMainMenu(p, module);
                        return;
                    }
                    String rawValue = result.getText("port").trim();
                    if (!rawValue.matches("\\d+")) {
                        p.sendMessage("§cポートは数字で入力してください");
                        openMainMenu(p, module);
                        return;
                    }
                    int port = Integer.parseInt(rawValue);
                    if (port < 1024 || port > 65535) {
                        p.sendMessage("§cポートは 1024-65535 の範囲で指定してください");
                        openMainMenu(p, module);
                        return;
                    }
                    module.updatePort(port);
                    p.sendMessage("§aWebMap のポートを " + port + " に変更しました");
                    openMainMenu(p, module);
                })
                .show(player);
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }
}
