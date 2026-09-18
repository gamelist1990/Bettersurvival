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
