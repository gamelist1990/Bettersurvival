package org.pexserver.koukunn.bettersurvival.Commands.protect;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect.ProtectMenu;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect.ProtectModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * /protect - OP向け監査・検索・ロールバック。
 */
public final class ProtectCommand extends BaseCommand {
    private final ProtectModule module;

    public ProtectCommand(ProtectModule module) {
        this.module = module;
    }

    @Override
    public String getName() {
        return "protect";
    }

    @Override
    public String getDescription() {
        return "ブロック/世界/コンテナ/アイテム監査ログとロールバックを管理";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.ADMIN;
    }

    @Override
    public boolean isEnabled() {
        return module.isEnabled();
    }

    @Override
    public String getUsage() {
        return "/protect <lookup|rollback|restore|undo|redo|inspect|status|purge|retention> [key:value...]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "このコマンドはOPプレイヤーから実行してください");
            return true;
        }

        if (args.length == 0) {
            ProtectMenu.openMain(player, module);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "help", "?" -> sendHelp(player);
                case "inspect", "i" -> {
                    boolean enabled = module.toggleInspector(player);
                    sendInfo(player, "Inspector: " + (enabled ? "ON" : "OFF"));
                }
                case "lookup", "l", "history" -> runLookup(player, args);
                case "rollback", "rb" -> runRollback(player, args);
                case "restore", "rs" -> runRestore(player, args);
                case "undo", "u" -> module.undo(player);
                case "redo" -> module.redo(player);
                case "stats", "status" -> module.stats(player);
                case "purge" -> runPurge(player, args);
                case "retention" -> runRetention(player, args);
                default -> sendHelp(player);
            }
        } catch (IllegalArgumentException e) {
            sendError(player, e.getMessage());
            sendInfo(player, "例: /protect rollback user:Steve time:2h radius:100 action:block preview:true");
        }
        return true;
    }

    private void runLookup(Player player, String[] args) {
        if (usesLegacyPositional(args, 1)) {
            int radius = args.length >= 2 ? parseInt(args[1], 0, 256, 10) : 10;
            String actor = args.length >= 3 ? args[2] : null;
            ProtectMenu.openHistoryAt(player, module, player.getLocation(), radius, actor, null);
            return;
        }

        long defaultDuration = TimeUnit.DAYS.toMillis(Math.max(1, module.getRetentionDays()));
        ProtectQuery query = ProtectQuery.parse(args, 1, defaultDuration);
        Location origin = resolveOrigin(player, query);
        module.lookup(
                player,
                origin,
                System.currentTimeMillis() - query.durationMs(),
                query.user(),
                query.actions(),
                query.radius(),
                query.page(),
                query.limit());
    }

    private void runRollback(Player player, String[] args) {
        if (usesLegacyPositional(args, 1)) {
            int radius = args.length >= 2 ? parseInt(args[1], 0, 256, 10) : 10;
            int hours = args.length >= 3 ? parseInt(args[2], 1, 24 * 365, 24) : 24;
            String actor = args.length >= 4 ? args[3] : null;
            module.rollback(player, radius, hours, actor);
            return;
        }

        ProtectQuery query = ProtectQuery.parse(args, 1, TimeUnit.HOURS.toMillis(24));
        Location origin = resolveOrigin(player, query);
        module.rollbackFiltered(
                player,
                origin,
                System.currentTimeMillis() - query.durationMs(),
                query.user(),
                query.actions(),
                query.radius(),
                query.limit(),
                query.preview());
    }

    private void runRestore(Player player, String[] args) {
        ProtectQuery query = ProtectQuery.parse(args, 1, TimeUnit.HOURS.toMillis(24));
        Location origin = resolveOrigin(player, query);
        module.restoreFiltered(
                player,
                origin,
                System.currentTimeMillis() - query.durationMs(),
                query.user(),
                query.actions(),
                query.radius(),
                query.limit(),
                query.preview());
    }

    private void runPurge(Player player, String[] args) {
        if (!hasKey(args, 1, "time", "t")) {
            throw new IllegalArgumentException("purge は time: の指定が必須です");
        }
        ProtectQuery query = ProtectQuery.parse(args, 1, TimeUnit.DAYS.toMillis(30));
        if (!query.confirm()) {
            sendInfo(player, "Purgeは不可逆です。内容を確認後 confirm:true を付けて再実行してください");
            sendInfo(player, "例: /protect purge time:30d user:Steve confirm:true");
            return;
        }
        module.purge(
                player,
                System.currentTimeMillis() - query.durationMs(),
                query.user());
    }

    private void runRetention(Player player, String[] args) {
        if (args.length < 2) {
            sendInfo(player, "現在の保持期間: " + module.getRetentionDays() + "日");
            return;
        }

        String raw = args[1];
        if (raw.contains(":")) {
            String[] split = raw.split(":", 2);
            if (!split[0].equalsIgnoreCase("days")) {
                throw new IllegalArgumentException("retention は days:<日数> で指定してください");
            }
            raw = split[1];
        }

        int days = parseInt(raw, 1, 3650, -1);
        if (days < 1) {
            throw new IllegalArgumentException("保持日数は1～3650で指定してください");
        }
        module.setRetentionDays(days);
        sendSuccess(player, "保持期間を " + days + " 日に変更しました");
    }

    private Location resolveOrigin(Player player, ProtectQuery query) {
        World world = query.world() == null
                ? player.getWorld()
                : Bukkit.getWorld(query.world());
        if (world == null) {
            throw new IllegalArgumentException("world が見つかりません: " + query.world());
        }

        if (query.x() != null) {
            return new Location(world, query.x(), query.y(), query.z());
        }

        Location current = player.getLocation();
        return new Location(world, current.getX(), current.getY(), current.getZ());
    }

    private void sendHelp(Player player) {
        sendInfo(player, "Protect コマンド:");
        player.sendMessage("§f/protect §7- 管理GUI");
        player.sendMessage("§f/protect inspect §7- ブロッククリック調査 ON/OFF");
        player.sendMessage("§f/protect lookup user:Steve time:2h radius:50 action:block page:1");
        player.sendMessage("§f/protect rollback user:Steve time:2h radius:100 action:block preview:true");
        player.sendMessage("§f/protect rollback user:Steve time:2h radius:100 action:block");
        player.sendMessage("§f/protect restore user:Steve time:2h radius:100 action:block");
        player.sendMessage("§f/protect undo §7- 自分が最後に行ったrollbackを取り消す");
        player.sendMessage("§f/protect redo §7- この起動中に最後にundoした内容を再rollback");
        player.sendMessage("§f/protect status §7- DB容量・件数・WAL・Queue・空き容量");
        player.sendMessage("§f/protect purge time:30d [user:Steve] confirm:true");
        player.sendMessage("§f/protect retention days:30");
        player.sendMessage("§8Keys: user/u, time/t, radius/r, action/a, limit/l, page, world/w, x,y,z, preview");
        player.sendMessage("§8Time: 30m / 2h / 7d / 1w2d / 1mo");
        player.sendMessage("§8Action: all, block, container, item, break, place, explosion, liquid, fire, growth");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.add("lookup");
            result.add("rollback");
            result.add("restore");
            result.add("undo");
            result.add("redo");
            result.add("inspect");
            result.add("status");
            result.add("purge");
            result.add("retention");
            result.add("help");
            return result;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("lookup") || sub.equals("l")
                || sub.equals("rollback") || sub.equals("rb")
                || sub.equals("restore") || sub.equals("rs")) {
            result.add("user:");
            result.add("time:24h");
            result.add("radius:10");
            result.add("action:block");
            result.add("limit:10000");
            result.add("page:1");
            result.add("world:");
            result.add("x:");
            result.add("y:");
            result.add("z:");
            result.add("preview:true");
        } else if (sub.equals("purge")) {
            result.add("time:30d");
            result.add("user:");
            result.add("confirm:true");
        } else if (sub.equals("retention")) {
            result.add("days:30");
            result.add("30");
            result.add("90");
        }
        return result;
    }

    private boolean usesLegacyPositional(String[] args, int start) {
        return args.length > start && !args[start].contains(":");
    }

    private boolean hasKey(String[] args, int start, String... names) {
        for (int i = start; i < args.length; i++) {
            int colon = args[i].indexOf(':');
            if (colon <= 0) continue;
            String key = args[i].substring(0, colon).toLowerCase(Locale.ROOT);
            for (String name : names) {
                if (key.equals(name)) return true;
            }
        }
        return false;
    }

    private int parseInt(String raw, int min, int max, int fallback) {
        try {
            int value = Integer.parseInt(raw);
            return value >= min && value <= max ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
