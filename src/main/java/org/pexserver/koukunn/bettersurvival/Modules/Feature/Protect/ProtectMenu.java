package org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Commands.protect.ProtectQuery;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Protect の OP 向けラージチェストGUI。
 */
public final class ProtectMenu {
    private static final int PAGE_SIZE = 45;
    private static final Map<UUID, AdvancedFilter> ADVANCED_FILTERS = new ConcurrentHashMap<>();

    private ProtectMenu() {
    }

    private static AdvancedFilter filterFor(Player player) {
        return ADVANCED_FILTERS.computeIfAbsent(
                player.getUniqueId(),
                ignored -> new AdvancedFilter(player.getLocation().clone()));
    }

    private static final class AdvancedFilter {
        private String actor = "*";
        private String timeText = "24h";
        private long durationMs = TimeUnit.HOURS.toMillis(24);
        private int radius = 100;
        private EnumSet<ProtectAction> actions = EnumSet.allOf(ProtectAction.class);
        private Location origin;
        private int limit = 10_000;

        private AdvancedFilter(Location origin) {
            this.origin = origin;
        }

        private void reset(Player player) {
            actor = "*";
            timeText = "24h";
            durationMs = TimeUnit.HOURS.toMillis(24);
            radius = 100;
            actions = EnumSet.allOf(ProtectAction.class);
            origin = player.getLocation().clone();
            limit = 10_000;
        }
    }

    public static void openMain(Player player, ProtectModule module) {
        String sizeText = humanBytes(module.getDatabaseSizeBytes());
        ChestUI.builder()
                .title("§3Protect §8- Audit & Rollback")
                .size(54)
                .type("protect_main")
                .addButtonAt(10, "§b周辺の履歴", Material.SPYGLASS,
                        "§7半径10ブロック / 保持期間内\n§7世界変化・爆発・液体・火災・アイテム操作を表示")
                .addButtonAt(12, "§6コンテナ履歴", Material.CHEST,
                        "§7周辺10ブロックのチェスト等の\n§7OPEN/CLOSE/中身変更を表示")
                .addButtonAt(14,
                        module.isInspector(player) ? "§aInspector: ON" : "§cInspector: OFF",
                        Material.RECOVERY_COMPASS,
                        "§7ON中にブロックを左右クリックすると\n§7その座標の履歴を直接表示")
                .addButtonAt(16, "§c詳細調査 / 復旧", Material.RECOVERY_COMPASS,
                        "§7プレイヤー・時間・半径・Action・座標を指定\n§7Lookup / Preview / Rollback / Restore")
                .addButtonAt(20, "§a世界変化", Material.GRASS_BLOCK,
                        "§7ブロック・液体・火・爆発・成長・\n§7Entity・ピストン・自然変化のみ表示")
                .addButtonAt(22, "§bアイテム履歴", Material.BUNDLE,
                        "§7ドロップ・拾得・破損・クラフト・\n§7発射・取引・特殊ブロック操作")
                .addButtonAt(24, "§d高度な検索条件", Material.COMPARATOR,
                        "§7CoreProtect風の詳細フィルタ画面を開きます")
                .addButtonAt(30, "§e保持期間: " + module.getRetentionDays() + "日", Material.WRITABLE_BOOK,
                        "§7既定30日。1～3650日で変更可能")
                .addButtonAt(32, "§dストレージ情報", Material.BOOK,
                        "§7DBサイズ: " + sizeText + "\n§7Queue drop: " + module.getDroppedRecords())
                .addButtonAt(49, "§7閉じる", Material.BARRIER, "")
                .then((result, p) -> {
                    if (!result.success || result.slot == null) {
                        return;
                    }
                    switch (result.slot) {
                        case 10 -> openHistoryAt(p, module, p.getLocation(), 10, 0, null, null);
                        case 12 -> openHistoryAt(
                                p,
                                module,
                                p.getLocation(),
                                10,
                                0,
                                null,
                                EnumSet.of(
                                        ProtectAction.CONTAINER_OPEN,
                                        ProtectAction.CONTAINER_CLOSE,
                                        ProtectAction.CONTAINER_CHANGE));
                        case 14 -> {
                            boolean enabled = module.toggleInspector(p);
                            p.sendMessage(enabled
                                    ? "§a[Protect] Inspectorを有効にしました"
                                    : "§e[Protect] Inspectorを無効にしました");
                            openMain(p, module);
                        }
                        case 16, 24 -> openAdvancedMenu(p, module);
                        case 20 -> openHistoryAt(p, module, p.getLocation(), 10, 0, null, worldActions());
                        case 22 -> openHistoryAt(p, module, p.getLocation(), 10, 0, null, itemActions());
                        case 30 -> openRetentionDialog(p, module);
                        case 32 -> showStorageStats(p, module);
                        case 49 -> ChestUI.closeMenu(p);
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    public static void openHistoryAt(
            Player player,
            ProtectModule module,
            Location origin,
            int radius,
            String actorName,
            Set<ProtectAction> actions) {
        openHistoryAt(player, module, origin, radius, 0, actorName, actions);
    }

    public static void openHistoryAt(
            Player player,
            ProtectModule module,
            Location origin,
            int radius,
            int page,
            String actorName,
            Set<ProtectAction> actions) {
        if (origin == null || origin.getWorld() == null) {
            return;
        }

        int safePage = Math.max(0, page);
        long since = System.currentTimeMillis()
                - TimeUnit.DAYS.toMillis(Math.max(1, module.getRetentionDays()));
        player.sendMessage("§7[Protect] 履歴を検索中...");

        module.getDatabase().queryNearby(
                        origin.getWorld().getUID().toString(),
                        origin.getBlockX(),
                        origin.getBlockY(),
                        origin.getBlockZ(),
                        radius,
                        since,
                        actorName,
                        actions,
                        PAGE_SIZE,
                        safePage * PAGE_SIZE)
                .whenComplete((records, throwable) -> Bukkit.getScheduler().runTask(
                        Loader.getPlugin(Loader.class),
                        () -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            if (throwable != null) {
                                player.sendMessage("§c[Protect] 履歴検索に失敗しました");
                                return;
                            }
                            showHistoryPage(
                                    player,
                                    module,
                                    origin.clone(),
                                    radius,
                                    safePage,
                                    actorName,
                                    actions,
                                    records == null ? List.of() : records);
                        }));
    }

    private static void showHistoryPage(
            Player player,
            ProtectModule module,
            Location origin,
            int radius,
            int page,
            String actorName,
            Set<ProtectAction> actions,
            List<ProtectRecord> records) {
        ChestUI.Builder builder = ChestUI.builder()
                .title("§3Protect History §8[" + (page + 1) + "]")
                .size(54)
                .type("protect_history");

        for (int i = 0; i < records.size() && i < PAGE_SIZE; i++) {
            ProtectRecord record = records.get(i);
            builder.addButtonAt(
                    i,
                    color(record.action()) + shortAction(record.action())
                            + " §f" + safeName(record.actorName()),
                    icon(record.action()),
                    historyLore(record));
        }

        builder.addButtonAt(45, "§eメインへ", Material.ARROW, "");
        if (page > 0) {
            builder.addButtonAt(48, "§e前のページ", Material.SPECTRAL_ARROW, "");
        }
        builder.addButtonAt(49, "§7Page " + (page + 1), Material.PAPER,
                "§7中心: " + origin.getBlockX() + "," + origin.getBlockY() + "," + origin.getBlockZ()
                        + "\n§7半径: " + radius);
        if (records.size() >= PAGE_SIZE) {
            builder.addButtonAt(50, "§e次のページ", Material.ARROW, "");
        }

        builder.then((result, p) -> {
            if (!result.success || result.slot == null) {
                return;
            }
            int slot = result.slot;
            if (slot >= 0 && slot < records.size() && slot < PAGE_SIZE) {
                openRecordDetail(p, module, records.get(slot), origin, radius, page, actorName, actions);
                return;
            }
            if (slot == 45) {
                openMain(p, module);
            } else if (slot == 48 && page > 0) {
                openHistoryAt(p, module, origin, radius, page - 1, actorName, actions);
            } else if (slot == 50 && records.size() >= PAGE_SIZE) {
                openHistoryAt(p, module, origin, radius, page + 1, actorName, actions);
            }
        }).show(player);
    }

    private static void openRecordDetail(
            Player player,
            ProtectModule module,
            ProtectRecord record,
            Location origin,
            int radius,
            int page,
            String actorName,
            Set<ProtectAction> actions) {
        ChestUI.Builder builder = ChestUI.builder()
                .title("§3Protect Log #" + record.id())
                .size(27)
                .type("protect_detail")
                .addButtonAt(11, "§b" + shortAction(record.action()), icon(record.action()), historyLore(record))
                .addButtonAt(15, "§e戻る", Material.ARROW, "");

        if (record.reversible() && !record.rolledBack()) {
            builder.addButtonAt(13, "§cこの1件をロールバック", Material.CLOCK,
                    "§7この操作だけ元に戻します");
        } else {
            builder.addButtonAt(13,
                    record.rolledBack() ? "§7ロールバック済み" : "§7復元対象外",
                    Material.GRAY_DYE,
                    "");
        }

        builder.then((result, p) -> {
            if (!result.success || result.slot == null) {
                return;
            }
            if (result.slot == 13 && record.reversible() && !record.rolledBack()) {
                module.rollbackSingle(p, record);
                openMain(p, module);
            } else if (result.slot == 15) {
                openHistoryAt(p, module, origin, radius, page, actorName, actions);
            }
        }).show(player);
    }

    private static void openAdvancedMenu(Player player, ProtectModule module) {
        AdvancedFilter filter = filterFor(player);
        String actionText = actionSummary(filter.actions);
        String locationText = locationSummary(filter.origin);

        ChestUI.builder()
                .title("§3Protect §8- 詳細調査 / 復旧")
                .size(54)
                .type("protect_advanced")
                .addButtonAt(10, "§bPlayer: §f" + displayActor(filter.actor), Material.PLAYER_HEAD,
                        "§7対象プレイヤーを指定\n§7* = 全プレイヤー")
                .addButtonAt(11, "§eTime: §f" + filter.timeText, Material.CLOCK,
                        "§7例: 30m / 2h / 7d / 1w2d / 1mo")
                .addButtonAt(12, "§aRadius: §f" + filter.radius, Material.COMPASS,
                        "§70～256ブロック")
                .addButtonAt(13, "§dAction: §f" + actionText, Material.COMPARATOR,
                        actionsLore(filter.actions))
                .addButtonAt(14, "§6Location", Material.LODESTONE,
                        "§7" + locationText + "\n§7現在地または手動座標を指定")
                .addButtonAt(15, "§fLimit: §b" + filter.limit, Material.HOPPER,
                        "§7検索/復旧の最大件数 1～10000")
                .addButtonAt(16, "§c条件をリセット", Material.REDSTONE,
                        "§7Player=* / Time=24h / Radius=100 / Action=ALL")
                .addButtonAt(20, "§bLookup", Material.SPYGLASS,
                        "§7この条件で履歴をGUI表示")
                .addButtonAt(22, "§cRollback Preview", Material.TNT,
                        "§7対象件数を確認してからRollback")
                .addButtonAt(24, "§aRestore Preview", Material.SLIME_BALL,
                        "§7Rollback済みの対象件数を確認してRestore")
                .addButtonAt(29, "§eUndo", Material.ARROW,
                        "§7自分が最後に実行したRollbackを取り消す")
                .addButtonAt(31, "§6Redo", Material.SPECTRAL_ARROW,
                        "§7この起動中にUndoした内容を再Rollback")
                .addButtonAt(33, "§dDB Stats", Material.BOOK,
                        "§7件数 / DBサイズ / Drop / 保持日数")
                .addButtonAt(40, "§eRetention", Material.WRITABLE_BOOK,
                        "§7現在: " + module.getRetentionDays() + "日")
                .addButtonAt(42, "§4Purge", Material.LAVA_BUCKET,
                        "§c古いログを完全削除\n§cRollbackではありません")
                .addButtonAt(45, "§eメインへ", Material.ARROW, "")
                .addButtonAt(49, "§7閉じる", Material.BARRIER, "")
                .then((result, p) -> {
                    if (!result.success || result.slot == null) return;
                    switch (result.slot) {
                        case 10 -> openAdvancedActorDialog(p, module);
                        case 11 -> openAdvancedTimeDialog(p, module);
                        case 12 -> openAdvancedRadiusDialog(p, module);
                        case 13 -> openActionSelector(p, module);
                        case 14 -> openAdvancedLocationMenu(p, module);
                        case 15 -> openAdvancedLimitDialog(p, module);
                        case 16 -> {
                            filterFor(p).reset(p);
                            p.sendMessage("§a[Protect] 詳細検索条件をリセットしました");
                            openAdvancedMenu(p, module);
                        }
                        case 20 -> openAdvancedHistory(p, module, 0);
                        case 22 -> openReplayPreview(p, module, false);
                        case 24 -> openReplayPreview(p, module, true);
                        case 29 -> module.undo(p);
                        case 31 -> module.redo(p);
                        case 33 -> showStorageStatsDialog(p, module);
                        case 40 -> openRetentionDialog(p, module);
                        case 42 -> openPurgeDialog(p, module);
                        case 45 -> openMain(p, module);
                        case 49 -> ChestUI.closeMenu(p);
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    private static void openAdvancedActorDialog(Player player, ProtectModule module) {
        AdvancedFilter filter = filterFor(player);
        ChestUI.closeMenu(player);
        Bukkit.getScheduler().runTask(Loader.getPlugin(Loader.class), () -> DialogUI.builder()
                .title("Protect - Player Filter")
                .body("荒らし復旧では対象プレイヤー名を指定します。* は全プレイヤーです。")
                .addTextInput("player", "Player", filter.actor, 32, false)
                .confirmation("保存", "戻る")
                .onResponse((result, p) -> {
                    if (result.isConfirmed()) {
                        String value = result.getText("player");
                        filter.actor = value == null || value.isBlank() ? "*" : value.trim();
                    }
                    openAdvancedMenu(p, module);
                })
                .show(player));
    }

    private static void openAdvancedTimeDialog(Player player, ProtectModule module) {
        AdvancedFilter filter = filterFor(player);
        ChestUI.closeMenu(player);
        Bukkit.getScheduler().runTask(Loader.getPlugin(Loader.class), () -> DialogUI.builder()
                .title("Protect - Time Filter")
                .body("どこまで過去を対象にするか指定します。例: 30m / 2h / 7d / 1w2d / 1mo")
                .addTextInput("time", "期間", filter.timeText, 24, false)
                .confirmation("保存", "戻る")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openAdvancedMenu(p, module);
                        return;
                    }
                    String raw = result.getText("time");
                    try {
                        long duration = ProtectQuery.parseDuration(raw == null ? "" : raw.trim());
                        filter.timeText = raw.trim();
                        filter.durationMs = duration;
                    } catch (IllegalArgumentException e) {
                        p.sendMessage("§c[Protect] " + e.getMessage());
                    }
                    openAdvancedMenu(p, module);
                })
                .show(player));
    }

    private static void openAdvancedRadiusDialog(Player player, ProtectModule module) {
        AdvancedFilter filter = filterFor(player);
        ChestUI.closeMenu(player);
        Bukkit.getScheduler().runTask(Loader.getPlugin(Loader.class), () -> DialogUI.builder()
                .title("Protect - Radius")
                .body("検索中心からの半径を0～256で指定します。")
                .addTextInput("radius", "Radius", String.valueOf(filter.radius), 3, false)
                .confirmation("保存", "戻る")
                .onResponse((result, p) -> {
                    if (result.isConfirmed()) {
                        Integer value = parseInt(result.getText("radius"), 0, 256);
                        if (value == null) {
                            p.sendMessage("§c[Protect] Radiusは0～256で指定してください");
                        } else {
                            filter.radius = value;
                        }
                    }
                    openAdvancedMenu(p, module);
                })
                .show(player));
    }

    private static void openAdvancedLimitDialog(Player player, ProtectModule module) {
        AdvancedFilter filter = filterFor(player);
        ChestUI.closeMenu(player);
        Bukkit.getScheduler().runTask(Loader.getPlugin(Loader.class), () -> DialogUI.builder()
                .title("Protect - Limit")
                .body("検索・Rollback・Restoreで扱う最大件数です。")
                .addTextInput("limit", "Limit (1-10000)", String.valueOf(filter.limit), 5, false)
                .confirmation("保存", "戻る")
                .onResponse((result, p) -> {
                    if (result.isConfirmed()) {
                        Integer value = parseInt(result.getText("limit"), 1, 10_000);
                        if (value == null) {
                            p.sendMessage("§c[Protect] Limitは1～10000で指定してください");
                        } else {
                            filter.limit = value;
                        }
                    }
                    openAdvancedMenu(p, module);
                })
                .show(player));
    }

    private static void openAdvancedLocationMenu(Player player, ProtectModule module) {
        AdvancedFilter filter = filterFor(player);
        ChestUI.builder()
                .title("§3Protect §8- 検索地点")
                .size(27)
                .type("protect_location")
                .addButtonAt(11, "§a現在地を使用", Material.COMPASS,
                        "§7" + locationSummary(player.getLocation()))
                .addButtonAt(13, "§6手動座標", Material.LODESTONE,
                        "§7現在: " + locationSummary(filter.origin))
                .addButtonAt(15, "§e戻る", Material.ARROW, "")
                .then((result, p) -> {
                    if (!result.success || result.slot == null) return;
                    if (result.slot == 11) {
                        filter.origin = p.getLocation().clone();
                        openAdvancedMenu(p, module);
                    } else if (result.slot == 13) {
                        openManualLocationDialog(p, module);
                    } else if (result.slot == 15) {
                        openAdvancedMenu(p, module);
                    }
                })
                .show(player);
    }

    private static void openManualLocationDialog(Player player, ProtectModule module) {
        AdvancedFilter filter = filterFor(player);
        Location origin = filter.origin == null ? player.getLocation() : filter.origin;
        ChestUI.closeMenu(player);
        Bukkit.getScheduler().runTask(Loader.getPlugin(Loader.class), () -> DialogUI.builder()
                .title("Protect - Manual Location")
                .body("WorldとX/Y/Zを指定します。")
                .addTextInput("world", "World", origin.getWorld().getName(), 64, false)
                .addTextInput("x", "X", String.valueOf(origin.getBlockX()), 16, false)
                .addTextInput("y", "Y", String.valueOf(origin.getBlockY()), 16, false)
                .addTextInput("z", "Z", String.valueOf(origin.getBlockZ()), 16, false)
                .confirmation("保存", "戻る")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openAdvancedMenu(p, module);
                        return;
                    }
                    World world = Bukkit.getWorld(result.getText("world"));
                    Integer x = parseSignedInt(result.getText("x"));
                    Integer y = parseSignedInt(result.getText("y"));
                    Integer z = parseSignedInt(result.getText("z"));
                    if (world == null || x == null || y == null || z == null) {
                        p.sendMessage("§c[Protect] Worldまたは座標が不正です");
                    } else {
                        filter.origin = new Location(world, x, y, z);
                    }
                    openAdvancedMenu(p, module);
                })
                .show(player));
    }

    private static void openActionSelector(Player player, ProtectModule module) {
        AdvancedFilter filter = filterFor(player);
        ChestUI.Builder builder = ChestUI.builder()
                .title("§3Protect §8- Action Filter")
                .size(54)
                .type("protect_actions")
                .addButtonAt(0, "§bALL", Material.NETHER_STAR, "§7全Actionを選択")
                .addButtonAt(1, "§aWORLD", Material.GRASS_BLOCK, "§7世界変化のみ")
                .addButtonAt(2, "§6CONTAINER", Material.CHEST, "§7コンテナ操作のみ")
                .addButtonAt(3, "§dITEM", Material.BUNDLE, "§7アイテム操作のみ")
                .addButtonAt(49, "§e条件画面へ", Material.ARROW,
                        "§7選択中: " + actionSummary(filter.actions));

        ProtectAction[] values = ProtectAction.values();
        for (int i = 0; i < values.length && 9 + i <= 44; i++) {
            ProtectAction action = values[i];
            boolean selected = filter.actions.contains(action);
            builder.addButtonAt(
                    9 + i,
                    (selected ? "§a✓ " : "§7") + shortAction(action),
                    icon(action),
                    "§8" + action.name() + "\n§7クリックでON/OFF");
        }

        builder.then((result, p) -> {
            if (!result.success || result.slot == null) return;
            int slot = result.slot;
            if (slot == 0) {
                filter.actions = EnumSet.allOf(ProtectAction.class);
                openActionSelector(p, module);
                return;
            }
            if (slot == 1) {
                filter.actions = EnumSet.copyOf(worldActions());
                openActionSelector(p, module);
                return;
            }
            if (slot == 2) {
                filter.actions = EnumSet.copyOf(containerActions());
                openActionSelector(p, module);
                return;
            }
            if (slot == 3) {
                filter.actions = EnumSet.copyOf(itemActions());
                openActionSelector(p, module);
                return;
            }
            if (slot == 49) {
                openAdvancedMenu(p, module);
                return;
            }
            int index = slot - 9;
            if (index >= 0 && index < values.length) {
                ProtectAction action = values[index];
                if (filter.actions.contains(action)) {
                    if (filter.actions.size() <= 1) {
                        p.sendMessage("§e[Protect] Actionは最低1つ必要です");
                    } else {
                        filter.actions.remove(action);
                    }
                } else {
                    filter.actions.add(action);
                }
                openActionSelector(p, module);
            }
        }).show(player);
    }

    private static void openAdvancedHistory(Player player, ProtectModule module, int page) {
        AdvancedFilter filter = filterFor(player);
        Location origin = filter.origin == null ? player.getLocation().clone() : filter.origin.clone();
        if (origin.getWorld() == null) {
            player.sendMessage("§c[Protect] 検索地点のWorldが不正です");
            openAdvancedMenu(player, module);
            return;
        }

        int safePage = Math.max(0, page);
        int offset = safePage * PAGE_SIZE;
        if (offset >= filter.limit) {
            openAdvancedMenu(player, module);
            return;
        }
        int queryLimit = Math.min(PAGE_SIZE, filter.limit - offset);
        long since = System.currentTimeMillis() - filter.durationMs;
        player.sendMessage("§7[Protect] 詳細条件で履歴を検索中...");

        module.getDatabase().queryNearby(
                        origin.getWorld().getUID().toString(),
                        origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(),
                        filter.radius,
                        since,
                        normalizeActor(filter.actor),
                        filter.actions,
                        queryLimit,
                        offset)
                .whenComplete((records, throwable) -> Bukkit.getScheduler().runTask(
                        Loader.getPlugin(Loader.class),
                        () -> {
                            if (!player.isOnline()) return;
                            if (throwable != null) {
                                player.sendMessage("§c[Protect] 詳細履歴検索に失敗しました");
                                return;
                            }
                            showAdvancedHistoryPage(
                                    player,
                                    module,
                                    safePage,
                                    records == null ? List.of() : records,
                                    queryLimit);
                        }));
    }

    private static void showAdvancedHistoryPage(
            Player player,
            ProtectModule module,
            int page,
            List<ProtectRecord> records,
            int queryLimit) {
        AdvancedFilter filter = filterFor(player);
        ChestUI.Builder builder = ChestUI.builder()
                .title("§3Protect Detail Lookup §8[" + (page + 1) + "]")
                .size(54)
                .type("protect_advanced_history");

        for (int i = 0; i < records.size() && i < PAGE_SIZE; i++) {
            ProtectRecord record = records.get(i);
            builder.addButtonAt(
                    i,
                    color(record.action()) + shortAction(record.action())
                            + " §f" + safeName(record.actorName()),
                    icon(record.action()),
                    historyLore(record));
        }

        builder.addButtonAt(45, "§e条件画面へ", Material.COMPARATOR, advancedFilterLore(filter));
        if (page > 0) {
            builder.addButtonAt(48, "§e前のページ", Material.SPECTRAL_ARROW, "");
        }
        builder.addButtonAt(49, "§7Page " + (page + 1), Material.PAPER, advancedFilterLore(filter));

        int nextOffset = (page + 1) * PAGE_SIZE;
        boolean hasNext = records.size() >= queryLimit && nextOffset < filter.limit;
        if (hasNext) {
            builder.addButtonAt(50, "§e次のページ", Material.ARROW, "");
        }
        builder.addButtonAt(53, "§b再検索", Material.SPYGLASS, "§7同じ条件で最新状態を再検索");

        builder.then((result, p) -> {
            if (!result.success || result.slot == null) return;
            int slot = result.slot;
            if (slot >= 0 && slot < records.size() && slot < PAGE_SIZE) {
                openAdvancedRecordDetail(p, module, records.get(slot), page);
            } else if (slot == 45) {
                openAdvancedMenu(p, module);
            } else if (slot == 48 && page > 0) {
                openAdvancedHistory(p, module, page - 1);
            } else if (slot == 50 && hasNext) {
                openAdvancedHistory(p, module, page + 1);
            } else if (slot == 53) {
                openAdvancedHistory(p, module, page);
            }
        }).show(player);
    }

    private static void openAdvancedRecordDetail(
            Player player,
            ProtectModule module,
            ProtectRecord record,
            int page) {
        ChestUI.Builder builder = ChestUI.builder()
                .title("§3Protect Log #" + record.id())
                .size(27)
                .type("protect_advanced_detail")
                .addButtonAt(11, "§b" + shortAction(record.action()), icon(record.action()), historyLore(record))
                .addButtonAt(15, "§e履歴へ戻る", Material.ARROW, "")
                .addButtonAt(17, "§d条件画面", Material.COMPARATOR, advancedFilterLore(filterFor(player)));

        if (record.reversible()) {
            if (record.rolledBack()) {
                builder.addButtonAt(13, "§aこの1件をRestore", Material.SLIME_BALL,
                        "§7Rollback済みの変更を再適用します");
            } else {
                builder.addButtonAt(13, "§cこの1件をRollback", Material.CLOCK,
                        "§7この変更だけ元に戻します");
            }
        } else {
            builder.addButtonAt(13, "§7復元対象外", Material.GRAY_DYE, "");
        }

        builder.then((result, p) -> {
            if (!result.success || result.slot == null) return;
            if (result.slot == 13 && record.reversible()) {
                if (record.rolledBack()) {
                    module.restoreSingle(p, record);
                } else {
                    module.rollbackSingle(p, record);
                }
                openAdvancedHistory(p, module, page);
            } else if (result.slot == 15) {
                openAdvancedHistory(p, module, page);
            } else if (result.slot == 17) {
                openAdvancedMenu(p, module);
            }
        }).show(player);
    }

    private static void openReplayPreview(Player player, ProtectModule module, boolean restore) {
        AdvancedFilter filter = filterFor(player);
        Location origin = filter.origin == null ? player.getLocation().clone() : filter.origin.clone();
        if (origin.getWorld() == null) {
            player.sendMessage("§c[Protect] 検索地点が不正です");
            return;
        }

        ChestUI.closeMenu(player);
        long since = System.currentTimeMillis() - filter.durationMs;
        var future = restore
                ? module.getDatabase().queryRestore(
                        origin.getWorld().getUID().toString(),
                        origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(),
                        filter.radius, since, normalizeActor(filter.actor), filter.actions, filter.limit)
                : module.getDatabase().queryRollback(
                        origin.getWorld().getUID().toString(),
                        origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(),
                        filter.radius, since, normalizeActor(filter.actor), filter.actions, filter.limit);

        future.whenComplete((records, throwable) -> Bukkit.getScheduler().runTask(
                Loader.getPlugin(Loader.class),
                () -> {
                    if (!player.isOnline()) return;
                    if (throwable != null) {
                        player.sendMessage("§c[Protect] Preview検索に失敗しました");
                        openAdvancedMenu(player, module);
                        return;
                    }
                    int count = records == null ? 0 : records.size();
                    String operation = restore ? "Restore" : "Rollback";
                    DialogUI.builder()
                            .title("Protect " + operation + " Preview")
                            .body("対象: " + count + "件")
                            .body(filterSummaryPlain(filter))
                            .body(count == 0
                                    ? "条件に一致する復元可能なログはありません。"
                                    : "実行するとtick分割でワールドへ反映します。")
                            .confirmation(count == 0 ? "戻る" : operation + "実行", "キャンセル")
                            .onResponse((result, p) -> {
                                if (!result.isConfirmed() || count == 0) {
                                    openAdvancedMenu(p, module);
                                    return;
                                }
                                if (restore) {
                                    module.restoreFiltered(
                                            p, origin,
                                            System.currentTimeMillis() - filter.durationMs,
                                            normalizeActor(filter.actor), filter.actions,
                                            filter.radius, filter.limit, false);
                                } else {
                                    module.rollbackFiltered(
                                            p, origin,
                                            System.currentTimeMillis() - filter.durationMs,
                                            normalizeActor(filter.actor), filter.actions,
                                            filter.radius, filter.limit, false);
                                }
                                openAdvancedMenu(p, module);
                            })
                            .show(player);
                }));
    }

    private static void showStorageStatsDialog(Player player, ProtectModule module) {
        ChestUI.closeMenu(player);
        module.getDatabase().countRecords().whenComplete((count, throwable) ->
                Bukkit.getScheduler().runTask(Loader.getPlugin(Loader.class), () -> {
                    if (!player.isOnline()) return;
                    if (throwable != null) {
                        player.sendMessage("§c[Protect] DB統計の取得に失敗しました");
                        openAdvancedMenu(player, module);
                        return;
                    }
                    DialogUI.builder()
                            .title("Protect DB Stats")
                            .body("Records: " + count)
                            .body("DB size: " + humanBytes(module.getDatabaseSizeBytes()))
                            .body("Queue dropped: " + module.getDroppedRecords())
                            .body("Retention: " + module.getRetentionDays() + " days")
                            .notice("戻る")
                            .onResponse((result, p) -> openAdvancedMenu(p, module))
                            .show(player);
                }));
    }

    private static void openPurgeDialog(Player player, ProtectModule module) {
        AdvancedFilter filter = filterFor(player);
        ChestUI.closeMenu(player);
        Bukkit.getScheduler().runTask(Loader.getPlugin(Loader.class), () -> DialogUI.builder()
                .title("Protect Purge")
                .body("指定期間より古いログを完全削除します。Rollbackでは戻せません。")
                .addTextInput("time", "これより古いログ (例: 30d)", "30d", 24, false)
                .addTextInput("player", "Player (* = 全員)", filter.actor, 32, false)
                .confirmation("次へ", "戻る")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openAdvancedMenu(p, module);
                        return;
                    }
                    String rawTime = result.getText("time");
                    String rawActor = result.getText("player");
                    final long duration;
                    try {
                        duration = ProtectQuery.parseDuration(rawTime == null ? "" : rawTime.trim());
                    } catch (IllegalArgumentException e) {
                        p.sendMessage("§c[Protect] " + e.getMessage());
                        openAdvancedMenu(p, module);
                        return;
                    }
                    String actor = normalizeActor(rawActor);
                    Bukkit.getScheduler().runTask(Loader.getPlugin(Loader.class), () -> DialogUI.builder()
                            .title("Protect Purge - 最終確認")
                            .body("削除条件: " + (rawTime == null ? "" : rawTime.trim())
                                    + " より古い / Player=" + displayActor(actor))
                            .body("この操作は不可逆です。")
                            .confirmation("完全削除", "キャンセル")
                            .onResponse((confirm, pp) -> {
                                if (confirm.isConfirmed()) {
                                    module.purge(pp, System.currentTimeMillis() - duration, actor);
                                }
                                openAdvancedMenu(pp, module);
                            })
                            .show(p));
                })
                .show(player));
    }

    private static String advancedFilterLore(AdvancedFilter filter) {
        return "§7Player: " + displayActor(filter.actor)
                + "\n§7Time: " + filter.timeText
                + "\n§7Radius: " + filter.radius
                + "\n§7Action: " + actionSummary(filter.actions)
                + "\n§7Limit: " + filter.limit
                + "\n§7Origin: " + locationSummary(filter.origin);
    }

    private static String filterSummaryPlain(AdvancedFilter filter) {
        return "Player=" + displayActor(filter.actor)
                + " / Time=" + filter.timeText
                + " / Radius=" + filter.radius
                + " / Action=" + actionSummary(filter.actions)
                + " / Limit=" + filter.limit
                + " / Origin=" + locationSummary(filter.origin);
    }

    private static String actionsLore(Set<ProtectAction> actions) {
        StringBuilder builder = new StringBuilder("§7選択数: ").append(actions.size());
        int shown = 0;
        for (ProtectAction action : actions) {
            if (shown++ >= 7) {
                builder.append("\n§8...");
                break;
            }
            builder.append("\n§8").append(action.name());
        }
        return builder.toString();
    }

    private static String actionSummary(Set<ProtectAction> actions) {
        if (actions == null || actions.isEmpty()) return "NONE";
        if (actions.size() == ProtectAction.values().length) return "ALL";
        if (actions.equals(worldActions())) return "WORLD";
        if (actions.equals(containerActions())) return "CONTAINER";
        if (actions.equals(itemActions())) return "ITEM";
        if (actions.size() == 1) return shortAction(actions.iterator().next());
        return actions.size() + " actions";
    }

    private static String locationSummary(Location location) {
        if (location == null || location.getWorld() == null) return "unknown";
        return location.getWorld().getName()
                + " " + location.getBlockX()
                + "," + location.getBlockY()
                + "," + location.getBlockZ();
    }

    private static String displayActor(String actor) {
        String normalized = normalizeActor(actor);
        return normalized == null ? "*" : normalized;
    }

    private static String normalizeActor(String actor) {
        if (actor == null) return null;
        String value = actor.trim();
        return value.isEmpty() || value.equals("*") || value.equalsIgnoreCase("all") ? null : value;
    }

    private static Integer parseSignedInt(String value) {
        if (value == null || !value.trim().matches("-?\\d+")) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void openRollbackDialog(Player player, ProtectModule module) {
        ChestUI.closeMenu(player);
        Bukkit.getScheduler().runTask(Loader.getPlugin(Loader.class), () -> DialogUI.builder()
                .title("Protect Rollback")
                .body("現在地を中心に、指定時間内の変更を新しい順から復元します。")
                .body("player は * で全プレイヤー。最大10,000件までをtick分割で処理します。")
                .addTextInput("radius", "半径 (0-256)", "10", 3, false)
                .addTextInput("hours", "何時間前まで", "24", 5, false)
                .addTextInput("player", "Player", "*", 16, false)
                .confirmation("ロールバック", "戻る")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openMain(p, module);
                        return;
                    }
                    Integer radius = parseInt(result.getText("radius"), 0, 256);
                    Integer hours = parseInt(result.getText("hours"), 1, 24 * 365);
                    if (radius == null || hours == null) {
                        p.sendMessage("§c[Protect] 入力値が不正です");
                        openMain(p, module);
                        return;
                    }
                    module.rollback(p, radius, hours, result.getText("player"));
                })
                .show(player));
    }

    private static void openRetentionDialog(Player player, ProtectModule module) {
        ChestUI.closeMenu(player);
        Bukkit.getScheduler().runTask(Loader.getPlugin(Loader.class), () -> DialogUI.builder()
                .title("Protect Retention")
                .body("監査ログの保持日数を変更します。既定は30日です。")
                .addTextInput("days", "保持日数 (1-3650)",
                        String.valueOf(module.getRetentionDays()), 4, false)
                .confirmation("保存", "戻る")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openMain(p, module);
                        return;
                    }
                    Integer days = parseInt(result.getText("days"), 1, 3650);
                    if (days == null) {
                        p.sendMessage("§c[Protect] 1～3650の数値を入力してください");
                        openMain(p, module);
                        return;
                    }
                    module.setRetentionDays(days);
                    p.sendMessage("§a[Protect] 保持期間を " + days + " 日に変更しました");
                    openMain(p, module);
                })
                .show(player));
    }

    private static void showStorageStats(Player player, ProtectModule module) {
        module.getDatabase().countRecords().whenComplete((count, throwable) ->
                Bukkit.getScheduler().runTask(Loader.getPlugin(Loader.class), () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (throwable != null) {
                        player.sendMessage("§c[Protect] DB統計の取得に失敗しました");
                        return;
                    }
                    player.sendMessage("§b[Protect] records=" + count
                            + ", size=" + humanBytes(module.getDatabaseSizeBytes())
                            + ", dropped=" + module.getDroppedRecords()
                            + ", retention=" + module.getRetentionDays() + "d");
                    openMain(player, module);
                }));
    }

    private static String historyLore(ProtectRecord record) {
        StringBuilder lore = new StringBuilder();
        lore.append("§7").append(formatAge(record.timeMs())).append("前")
                .append("\n§7World: ").append(record.worldName())
                .append("\n§7XYZ: ").append(record.x()).append(", ")
                .append(record.y()).append(", ").append(record.z());
        if (record.slot() != null) {
            lore.append("\n§7Slot: ").append(record.slot());
        }
        if ((record.action().isContainerAction() || record.action().isItemAction())
                && (record.itemBefore() != null || record.itemAfter() != null)) {
            lore.append("\n§7").append(ProtectModule.itemSummary(record.itemBefore()))
                    .append(" -> ").append(ProtectModule.itemSummary(record.itemAfter()));
        }
        if (record.blockBefore() != null || record.blockAfter() != null) {
            lore.append("\n§7")
                    .append(shortBlock(record.blockBefore()))
                    .append(" -> ")
                    .append(shortBlock(record.blockAfter()));
        }
        if (record.detail() != null && !record.detail().isBlank()) {
            lore.append("\n§8").append(record.detail());
        }
        if (record.rolledBack()) {
            lore.append("\n§8Rollback済み");
        }
        return lore.toString();
    }

    private static String shortBlock(String blockData) {
        if (blockData == null || blockData.isBlank()) {
            return "-";
        }
        int bracket = blockData.indexOf('[');
        String value = bracket >= 0 ? blockData.substring(0, bracket) : blockData;
        int colon = value.indexOf(':');
        return colon >= 0 ? value.substring(colon + 1) : value;
    }

    private static Material icon(ProtectAction action) {
        return switch (action) {
            case BLOCK_BREAK -> Material.IRON_PICKAXE;
            case BLOCK_PLACE -> Material.GRASS_BLOCK;
            case BLOCK_PHYSICS, BLOCK_MOVE -> Material.PISTON;
            case LIQUID_PLACE, LIQUID_REMOVE, LIQUID_FLOW -> Material.WATER_BUCKET;
            case FIRE_IGNITE, FIRE_BURN, FIRE_FADE -> Material.FLINT_AND_STEEL;
            case EXPLOSION -> Material.TNT;
            case ENTITY_CHANGE -> Material.ENDERMAN_SPAWN_EGG;
            case LEAF_DECAY -> Material.OAK_LEAVES;
            case GROWTH -> Material.OAK_SAPLING;
            case SCULK_SPREAD -> Material.SCULK;
            case PORTAL_CREATE -> Material.OBSIDIAN;
            case FARMLAND_TRAMPLE -> Material.DIRT;
            case NATURAL_FORM -> Material.COBBLESTONE;
            case POT_CHANGE -> Material.FLOWER_POT;
            case BRUSH -> Material.BRUSH;
            case CUSTOM_BLOCK -> Material.COMMAND_BLOCK;
            case CONTAINER_OPEN -> Material.CHEST;
            case CONTAINER_CLOSE -> Material.BARREL;
            case CONTAINER_CHANGE, CONTAINER_TRANSFER -> Material.HOPPER;
            case ITEM_DROP, ITEM_PICKUP -> Material.BUNDLE;
            case ITEM_BREAK -> Material.DAMAGED_ANVIL;
            case ITEM_CRAFT -> Material.CRAFTING_TABLE;
            case ITEM_SHOOT -> Material.BOW;
            case ITEM_TRADE -> Material.EMERALD;
            case ITEM_INTERACT -> Material.LEVER;
        };
    }

    private static String shortAction(ProtectAction action) {
        return switch (action) {
            case BLOCK_BREAK -> "破壊";
            case BLOCK_PLACE -> "設置";
            case BLOCK_PHYSICS -> "自然破損";
            case BLOCK_MOVE -> "ブロック移動";
            case LIQUID_PLACE -> "液体設置";
            case LIQUID_REMOVE -> "液体回収";
            case LIQUID_FLOW -> "液体流動";
            case FIRE_IGNITE -> "発火";
            case FIRE_BURN -> "燃焼";
            case FIRE_FADE -> "消火";
            case EXPLOSION -> "爆発";
            case ENTITY_CHANGE -> "Entity変更";
            case LEAF_DECAY -> "葉の腐敗";
            case GROWTH -> "成長";
            case SCULK_SPREAD -> "スカルク拡散";
            case PORTAL_CREATE -> "ポータル生成";
            case FARMLAND_TRAMPLE -> "踏み荒らし";
            case NATURAL_FORM -> "自然生成";
            case POT_CHANGE -> "植木鉢";
            case BRUSH -> "ブラシ";
            case CUSTOM_BLOCK -> "カスタムブロック";
            case CONTAINER_OPEN -> "開く";
            case CONTAINER_CLOSE -> "閉じる";
            case CONTAINER_CHANGE -> "中身変更";
            case CONTAINER_TRANSFER -> "自動移送";
            case ITEM_DROP -> "ドロップ";
            case ITEM_PICKUP -> "拾得";
            case ITEM_BREAK -> "アイテム破損";
            case ITEM_CRAFT -> "クラフト";
            case ITEM_SHOOT -> "発射";
            case ITEM_TRADE -> "取引";
            case ITEM_INTERACT -> "アイテム操作";
        };
    }

    private static String color(ProtectAction action) {
        if (action.isItemAction()) return "§b";
        if (action.isContainerAction()) return "§d";
        return switch (action) {
            case BLOCK_BREAK, EXPLOSION, FIRE_BURN -> "§c";
            case BLOCK_PLACE, GROWTH, NATURAL_FORM, PORTAL_CREATE -> "§a";
            case LIQUID_PLACE, LIQUID_REMOVE, LIQUID_FLOW -> "§9";
            case FIRE_IGNITE, FIRE_FADE -> "§6";
            case ENTITY_CHANGE, BLOCK_MOVE, BLOCK_PHYSICS, FARMLAND_TRAMPLE -> "§e";
            case LEAF_DECAY, SCULK_SPREAD, POT_CHANGE, BRUSH, CUSTOM_BLOCK -> "§2";
            default -> "§7";
        };
    }

    private static Set<ProtectAction> worldActions() {
        EnumSet<ProtectAction> result = EnumSet.noneOf(ProtectAction.class);
        for (ProtectAction action : ProtectAction.values()) {
            if (!action.isContainerAction() && !action.isItemAction()) {
                result.add(action);
            }
        }
        return result;
    }

    private static Set<ProtectAction> itemActions() {
        EnumSet<ProtectAction> result = EnumSet.noneOf(ProtectAction.class);
        for (ProtectAction action : ProtectAction.values()) {
            if (action.isItemAction()) {
                result.add(action);
            }
        }
        return result;
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "unknown" : name;
    }

    private static String formatAge(long timeMs) {
        long seconds = Math.max(0L, (System.currentTimeMillis() - timeMs) / 1000L);
        if (seconds < 60L) {
            return seconds + "秒";
        }
        long minutes = seconds / 60L;
        if (minutes < 60L) {
            return minutes + "分";
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return hours + "時間";
        }
        return (hours / 24L) + "日";
    }

    private static Integer parseInt(String value, int min, int max) {
        if (value == null || !value.trim().matches("\\d+")) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= min && parsed <= max ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kib = bytes / 1024.0;
        if (kib < 1024.0) {
            return String.format("%.1f KiB", kib);
        }
        double mib = kib / 1024.0;
        if (mib < 1024.0) {
            return String.format("%.1f MiB", mib);
        }
        return String.format("%.2f GiB", mib / 1024.0);
    }
}
