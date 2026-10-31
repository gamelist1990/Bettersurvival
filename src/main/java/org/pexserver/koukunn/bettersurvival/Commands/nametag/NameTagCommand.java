package org.pexserver.koukunn.bettersurvival.Commands.nametag;

import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.NameTag.NameTagModule;

public final class NameTagCommand extends BaseCommand {
    private final NameTagModule module;
    public NameTagCommand(NameTagModule module) { this.module = module; }
    @Override public String getName() { return "nametag"; }
    @Override public String getDescription() { return "頭上に表示するタグを設定"; }
    @Override public String getUsage() { return "/nametag set <1〜4文字> | clear | role <プレイヤー> <役職|clear>"; }
    @Override public boolean execute(CommandSender sender, String[] args) {
        try {
            if (args.length == 2 && args[0].equalsIgnoreCase("set") && sender instanceof Player p) {
                if (module.setTag(p, args[1])) sendSuccess(sender, "タグを設定しました: " + args[1]);
                else sendError(sender, "保存できませんでした");
            } else if (args.length == 1 && args[0].equalsIgnoreCase("clear") && sender instanceof Player p) {
                if (module.setTag(p, null)) sendSuccess(sender, "タグを解除しました");
                else sendError(sender, "保存できませんでした");
            } else if (args.length == 3 && args[0].equalsIgnoreCase("role")) {
                if (!sender.isOp() && !sender.hasPermission("bettersurvival.nametag.role")) { sendError(sender, "権限がありません"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sendError(sender, "オンラインのプレイヤーを指定してください"); return true; }
                String role = args[2].equalsIgnoreCase("clear") ? null : args[2];
                if (module.setRole(target, role)) sendSuccess(sender, target.getName() + " の役職を更新しました");
                else sendError(sender, "保存できませんでした");
            } else sendInfo(sender, getUsage());
        } catch (IllegalArgumentException ex) { sendError(sender, ex.getMessage()); }
        return true;
    }
    @Override public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) return (sender.isOp() || sender.hasPermission("bettersurvival.nametag.role") ? List.of("set", "clear", "role") : List.of("set", "clear")).stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("role") && (sender.isOp() || sender.hasPermission("bettersurvival.nametag.role"))) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
