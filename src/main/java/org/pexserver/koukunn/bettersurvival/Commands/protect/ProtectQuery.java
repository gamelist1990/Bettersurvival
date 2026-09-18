package org.pexserver.koukunn.bettersurvival.Commands.protect;

import org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect.ProtectAction;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /protect の key:value 引数を解析する。
 */
public record ProtectQuery(
        String user,
        long durationMs,
        int radius,
        Set<ProtectAction> actions,
        int limit,
        int page,
        boolean preview,
        boolean confirm,
        String world,
        Integer x,
        Integer y,
        Integer z) {

    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)(mo|[smhdw])", Pattern.CASE_INSENSITIVE);

    public static ProtectQuery parse(String[] args, int start, long defaultDurationMs) {
        String user = null;
        long duration = defaultDurationMs;
        int radius = 10;
        Set<ProtectAction> actions = EnumSet.allOf(ProtectAction.class);
        int limit = 10_000;
        int page = 1;
        boolean preview = false;
        boolean confirm = false;
        String world = null;
        Integer x = null;
        Integer y = null;
        Integer z = null;

        for (int i = start; i < args.length; i++) {
            String token = args[i];
            int colon = token.indexOf(':');
            if (colon <= 0 || colon == token.length() - 1) {
                throw new IllegalArgumentException("key:value 形式ではありません: " + token);
            }

            String key = token.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = token.substring(colon + 1);
            switch (key) {
                case "user", "u", "player", "p" -> user = normalizeUser(value);
                case "time", "t" -> duration = parseDuration(value);
                case "radius", "r" -> radius = parseInt(value, 0, 256, "radius");
                case "action", "a" -> actions = parseActions(value);
                case "limit", "l" -> limit = parseInt(value, 1, 10_000, "limit");
                case "page" -> page = parseInt(value, 1, 10_000, "page");
                case "preview", "dryrun" -> preview = parseBoolean(value);
                case "confirm" -> confirm = parseBoolean(value);
                case "world", "w" -> world = value;
                case "x" -> x = parseCoordinate(value, "x");
                case "y" -> y = parseCoordinate(value, "y");
                case "z" -> z = parseCoordinate(value, "z");
                default -> throw new IllegalArgumentException("不明なキー: " + key);
            }
        }

        if ((x != null || y != null || z != null) && (x == null || y == null || z == null)) {
            throw new IllegalArgumentException("座標指定は x:, y:, z: をすべて指定してください");
        }

        return new ProtectQuery(user, duration, radius, Set.copyOf(actions), limit, page,
                preview, confirm, world, x, y, z);
    }

    public static long parseDuration(String raw) {
        String value = raw.toLowerCase(Locale.ROOT).replace(" ", "");
        Matcher matcher = DURATION_PART.matcher(value);
        long total = 0L;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                throw new IllegalArgumentException("time の形式が不正です: " + raw);
            }
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("time が大きすぎます: " + raw);
            }
            long unit = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "s" -> 1_000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                case "w" -> 604_800_000L;
                case "mo" -> 2_592_000_000L;
                default -> throw new IllegalArgumentException("time の単位が不正です: " + raw);
            };
            try {
                total = Math.addExact(total, Math.multiplyExact(amount, unit));
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("time が大きすぎます: " + raw);
            }
            end = matcher.end();
        }
        if (end != value.length() || total <= 0L) {
            throw new IllegalArgumentException("time は 30m / 2h / 7d / 1w2d の形式で指定してください");
        }
        return total;
    }

    private static Set<ProtectAction> parseActions(String raw) {
        EnumSet<ProtectAction> result = EnumSet.noneOf(ProtectAction.class);
        for (String part : raw.split(",")) {
            String value = part.trim().toLowerCase(Locale.ROOT);
            switch (value) {
                case "all", "*" -> result.addAll(EnumSet.allOf(ProtectAction.class));
                case "block", "blocks", "world" -> {
                    for (ProtectAction action : ProtectAction.values()) {
                        if (action.isBlockMutation()) result.add(action);
                    }
                }
                case "container", "containers", "chest" -> {
                    for (ProtectAction action : ProtectAction.values()) {
                        if (action.isContainerAction()) result.add(action);
                    }
                }
                case "item", "items" -> {
                    for (ProtectAction action : ProtectAction.values()) {
                        if (action.isItemAction()) result.add(action);
                    }
                }
                case "break" -> result.add(ProtectAction.BLOCK_BREAK);
                case "place" -> result.add(ProtectAction.BLOCK_PLACE);
                case "explosion", "tnt" -> result.add(ProtectAction.EXPLOSION);
                case "liquid", "water", "lava" -> {
                    result.add(ProtectAction.LIQUID_PLACE);
                    result.add(ProtectAction.LIQUID_REMOVE);
                    result.add(ProtectAction.LIQUID_FLOW);
                }
                case "fire" -> {
                    result.add(ProtectAction.FIRE_IGNITE);
                    result.add(ProtectAction.FIRE_BURN);
                    result.add(ProtectAction.FIRE_FADE);
                }
                case "growth", "grow" -> {
                    result.add(ProtectAction.GROWTH);
                    result.add(ProtectAction.LEAF_DECAY);
                    result.add(ProtectAction.SCULK_SPREAD);
                }
                default -> {
                    try {
                        result.add(ProtectAction.valueOf(value.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("不明なaction: " + part);
                    }
                }
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("action が空です");
        }
        return result;
    }

    private static String normalizeUser(String value) {
        String trimmed = value.trim();
        return trimmed.equals("*") || trimmed.equalsIgnoreCase("all") ? null : trimmed;
    }

    private static int parseInt(String raw, int min, int max, String name) {
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " は " + min + "～" + max + " で指定してください");
        }
    }

    private static Integer parseCoordinate(String raw, String name) {
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " は整数で指定してください");
        }
    }

    private static boolean parseBoolean(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> throw new IllegalArgumentException("preview は true/false で指定してください");
        };
    }
}
