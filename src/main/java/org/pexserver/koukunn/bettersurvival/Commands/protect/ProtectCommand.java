package org.pexserver.koukunn.bettersurvival.Commands.protect;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect.ProtectMenu;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect.ProtectAction;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect.ProtectModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;

/**
 * /protect - OP向け監査・検索・ロールバック。
 */
public final class ProtectCommand extends BaseCommand {
    private static final long SUGGESTION_REFRESH_INTERVAL_MS = TimeUnit.SECONDS.toMillis(10);
    private static final List<String> SUBCOMMANDS = List.of(
        "lookup", "rollback", "restore", "undo", "redo", "inspect", "status", "purge", "retention", "help");
    private static final List<String> ACTION_VALUES = Arrays.stream(ProtectAction.values())
        .map(action -> action.name().toLowerCase(Locale.ROOT))
        .sorted()
        .toList();
    private static final List<String> ACTION_GROUPS = List.of(
        "all", "block", "container", "item", "break", "place", "explosion", "liquid", "fire", "growth");

    private final ProtectModule module;
    private final Set<String> actorSuggestions = new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Set<String> worldSuggestions = new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);
    private volatile long lastSuggestionRefresh;

    public ProtectCommand(ProtectModule module) {
        this.module = module;
        refreshSuggestions(true);
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
        refreshSuggestions(false);
        String current = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) {
            return matching(SUBCOMMANDS, current);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("lookup") || sub.equals("l")
                || sub.equals("rollback") || sub.equals("rb")
                || sub.equals("restore") || sub.equals("rs")) {
            return completeFilterArguments(args, current, false);
        } else if (sub.equals("purge")) {
            return completeFilterArguments(args, current, true);
        } else if (sub.equals("retention")) {
            return matching(List.of("days:30", "30", "90"), current);
        }
        return List.of();
    }

    private List<String> completeFilterArguments(String[] args, String current, boolean purge) {
        int colon = current.indexOf(':');
        if (colon > 0) {
            String key = current.substring(0, colon).toLowerCase(Locale.ROOT);
            String valuePrefix = current.substring(colon + 1);
            List<String> values = switch (key) {
                case "user", "u", "player", "p" -> prefixed("user:", actorSuggestions, valuePrefix, true);
                case "world", "w" -> prefixed("world:", worldSuggestions, valuePrefix, false);
                case "action", "a" -> prefixed("action:", actionSuggestions(), valuePrefix, false);
                case "time", "t" -> matching(List.of("time:30m", "time:2h", "time:24h", "time:7d", "time:1w2d"), current);
                case "radius", "r" -> matching(List.of("radius:10", "radius:50", "radius:100"), current);
                case "limit", "l" -> matching(List.of("limit:100", "limit:1000", "limit:10000"), current);
                case "page" -> matching(List.of("page:1", "page:2", "page:3"), current);
                case "preview", "dryrun", "confirm" -> matching(List.of(key + ":true", key + ":false"), current);
                case "x", "y", "z" -> List.of(current);
                default -> List.of();
            };
            return values;
        }

        Set<String> usedKeys = usedKeys(args);
        List<String> result = new ArrayList<>();
        addIfUnused(result, usedKeys, "user:" + (actorSuggestions.isEmpty() ? "" : firstSuggestion(actorSuggestions)));
        addIfUnused(result, usedKeys, "time:24h");
        addIfUnused(result, usedKeys, "radius:10");
        addIfUnused(result, usedKeys, "action:block");
        addIfUnused(result, usedKeys, "limit:10000");
        addIfUnused(result, usedKeys, "page:1");
        addIfUnused(result, usedKeys, "world:" + (worldSuggestions.isEmpty() ? "" : firstSuggestion(worldSuggestions)));
        if (!purge) {
            addIfUnused(result, usedKeys, "x:");
            addIfUnused(result, usedKeys, "y:");
            addIfUnused(result, usedKeys, "z:");
            addIfUnused(result, usedKeys, "preview:true");
        } else {
            addIfUnused(result, usedKeys, "confirm:true");
        }
        return matching(result, current);
    }

    private List<String> actionSuggestions() {
        List<String> values = new ArrayList<>(ACTION_GROUPS.size() + ACTION_VALUES.size());
        values.addAll(ACTION_GROUPS);
        values.addAll(ACTION_VALUES);
        return values;
    }

    private List<String> prefixed(String key, Iterable<String> values, String prefix, boolean allowAll) {
        List<String> result = new ArrayList<>();
        if (allowAll && matches("*", prefix)) result.add(key + "*");
        for (String value : values) {
            if (matches(value, prefix)) result.add(key + value);
        }
        return result;
    }

    private List<String> matching(Iterable<String> candidates, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return java.util.stream.StreamSupport.stream(candidates.spliterator(), false)
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private boolean matches(String value, String prefix) {
        return value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private Set<String> usedKeys(String[] args) {
        Set<String> keys = new HashSet<>();
        for (int i = 1; i < args.length - 1; i++) {
            int colon = args[i].indexOf(':');
            if (colon > 0) keys.add(args[i].substring(0, colon).toLowerCase(Locale.ROOT));
        }
        return keys;
    }

    private void addIfUnused(List<String> result, Set<String> usedKeys, String candidate) {
        int colon = candidate.indexOf(':');
        if (colon <= 0 || !usedKeys.contains(candidate.substring(0, colon).toLowerCase(Locale.ROOT))) {
            result.add(candidate);
        }
    }

    private String firstSuggestion(Set<String> values) {
        return values.stream().findFirst().orElse("");
    }

    private void refreshSuggestions(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastSuggestionRefresh < SUGGESTION_REFRESH_INTERVAL_MS) return;
        lastSuggestionRefresh = now;
        Bukkit.getOnlinePlayers().forEach(player -> actorSuggestions.add(player.getName()));
        Bukkit.getWorlds().forEach(world -> worldSuggestions.add(world.getName()));
        module.getDatabase().findActorNames().thenAccept(names -> actorSuggestions.addAll(names));
        module.getDatabase().findWorldNames().thenAccept(names -> worldSuggestions.addAll(names));
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
