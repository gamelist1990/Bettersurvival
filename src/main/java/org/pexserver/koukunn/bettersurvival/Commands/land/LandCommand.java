package org.pexserver.koukunn.bettersurvival.Commands.land;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ClaimLevel;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ClaimRegion;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.LandProtectionModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.RingModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.RingRegion;

import java.util.ArrayList;
import java.util.List;

/**
 * 土地保護コマンド
 * /land debug : 保護境界線のデバッグ表示を切り替え
 * /land info  : 現在地の保護エリア情報を表示
 */
public class LandCommand extends BaseCommand {

    private final Loader plugin;

    public LandCommand(Loader plugin) {
        this.plugin = plugin;
    }

    private LandProtectionModule getModule() {
        return plugin.getLandProtectionModule();
    }

    @Override
    public String getName() {
        return "land";
    }

    @Override
    public String getDescription() {
        return "土地保護のデバッグ表示・情報を確認するコマンド";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.MEMBER;
    }

    @Override
    public String getUsage() {
        return "/land <debug|info|ring>";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "プレイヤーのみ使用できます");
            return true;
        }
        LandProtectionModule module = getModule();
        if (module == null) {
            sendError(sender, "土地保護機能が初期化されていません");
            return true;
        }
        if (!module.isFeatureEnabled()) {
            sendError(sender, "土地保護機能は現在無効です");
            return true;
        }
        String sub = args.length >= 1 ? args[0].toLowerCase() : "debug";
        switch (sub) {
            case "debug" -> {
                boolean enabled = module.getVisualizer().toggle(player);
                if (enabled) {
                    sendSuccess(player, "デバッグ境界線を表示します (緑=アクセス可 / 赤=他人 / 灰=無効)");
                } else {
                    sendInfo(player, "デバッグ境界線を非表示にしました");
                }
            }
            case "info" -> showInfo(player, module);
            case "ring" -> handleRing(player, module, args);
            default -> {
                sendInfo(player, "使用法: " + getUsage());
            }
        }
        return true;
    }

    private void showInfo(Player player, LandProtectionModule module) {
        ClaimRegion claim = module.getActiveClaimAt(player.getLocation());
        if (claim == null) {
            sendInfo(player, "現在地は保護されていません");
            return;
        }
        player.sendMessage("§6====== 土地保護情報 ======");
        player.sendMessage("§7オーナー: §e" + module.resolveOwnerDisplay(claim));
        player.sendMessage("§7レベル: §eLv." + claim.getLevel() + " §7(半径 " + claim.getRadius() + " ブロック)");
        player.sendMessage("§7コア座標: §e" + claim.getX() + ", " + claim.getY() + ", " + claim.getZ());
        player.sendMessage("§7消費速度: §e" + String.format("%.0f", ClaimLevel.upkeepPerHour(claim.getLevel())) + " ユニット/時");
        player.sendMessage("§7領地内PVP: " + (claim.getSettings().isPvpEnabled() ? "§a有効" : "§c無効"));
        if (module.isUnderRaid(claim)) {
            player.sendMessage("§c§l現在この領地はレイド中です");
        }
        if (module.canManage(player, claim)) {
            player.sendMessage("§7燃料: §e" + String.format("%.1f", claim.getFuelUnits()) + " ユニット");
            player.sendMessage("§7あなたはこのエリアを管理できます");
        } else if (module.canBypass(player, claim)) {
            player.sendMessage("§aあなたはこのエリアで制限を受けません");
        } else {
            player.sendMessage("§cあなたはこのエリアで制限を受けます");
        }
    }

    /** 現在地の保護領域のリング設定メニューを開く（オーナー/副リーダーのみ）。 */
    private void handleRing(Player player, LandProtectionModule module, String[] args) {
        if (module.getRingModule() == null) {
            sendError(player, "リング機能が初期化されていません");
            return;
        }
        ClaimRegion claim = module.getActiveClaimAt(player.getLocation());
        if (claim == null) {
            sendError(player, "保護された土地の中で実行してください");
            return;
        }
        if (!module.canManage(player, claim)) {
            sendError(player, "リングの設定はオーナー又は副リーダーのみ操作できます");
            return;
        }
        RingModule ringModule = module.getRingModule();
        if (args.length == 1) {
            ringModule.getMenu().openRing(player, claim);
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "create" -> {
                if (args.length < 3) {
                    sendError(player, "使用法: /land ring create <name>");
                    return;
                }
                String error = ringModule.beginSelection(player, claim, args[2]);
                if (error != null) {
                    sendError(player, error);
                }
            }
            case "settings" -> {
                if (args.length < 3) {
                    ringModule.getMenu().openRing(player, claim);
                    return;
                }
                RingRegion ring = ringModule.getRing(claim.key(), args[2]);
                if (ring == null) {
                    sendError(player, "リング「" + args[2] + "」は存在しません");
                    return;
                }
                ringModule.getMenu().openRing(player, claim, ring);
            }
            case "set" -> {
                if (args.length < 3 || !(args[2].equalsIgnoreCase("pos1") || args[2].equalsIgnoreCase("pos2"))) {
                    sendError(player, "使用法: /land ring set pos1|pos2");
                    return;
                }
                Block target = player.getTargetBlockExact(100);
                if (target == null) {
                    sendError(player, "視線の先にブロックがありません");
                    return;
                }
                ringModule.setSelectionPoint(player, args[2].equalsIgnoreCase("pos1"), target.getLocation());
            }
            case "cancel" -> {
                if (ringModule.isSelecting(player)) {
                    ringModule.cancelSelection(player);
                    sendInfo(player, "リング範囲選択を中止しました");
                } else {
                    sendError(player, "範囲選択モードではありません");
                }
            }
            default -> sendInfo(player, "使用法: /land ring create <name> | settings <name> | set pos1|pos2 | cancel");
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String candidate : List.of("debug", "info", "ring")) {
                if (candidate.startsWith(prefix)) {
                    out.add(candidate);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("ring")) {
            String prefix = args[1].toLowerCase();
            for (String candidate : List.of("create", "settings", "set", "cancel")) {
                if (candidate.startsWith(prefix)) {
                    out.add(candidate);
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("ring")) {
            LandProtectionModule module = getModule();
            if (module == null || module.getRingModule() == null) {
                return out;
            }
            if (args[1].equalsIgnoreCase("set")) {
                String prefix = args[2].toLowerCase();
                for (String candidate : List.of("pos1", "pos2")) {
                    if (candidate.startsWith(prefix)) {
                        out.add(candidate);
                    }
                }
            } else if (sender instanceof Player player && args[1].equalsIgnoreCase("settings")) {
                ClaimRegion claim = module.getActiveClaimAt(player.getLocation());
                if (claim != null) {
                    String prefix = args[2].toLowerCase();
                    for (RingRegion ring : module.getRingModule().getRingsForClaim(claim.key())) {
                        if (ring.getName().toLowerCase().startsWith(prefix)) {
                            out.add(ring.getName());
                        }
                    }
                }
            }
        }
        return out;
    }
}
