package org.pexserver.koukunn.bettersurvival.Commands.list;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Core.Util.FormsUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.ArrayList;
import java.util.List;

/**
 * プレイヤー一覧コマンド
 * Java版: クリック可能なメッセージを表示
 * Bedrock版: ActionFormでプレイヤー一覧を表示（OP判定で画像付き）
 */
public class ListCommand extends BaseCommand {

    private static final String EMPTY_STAR_Path = "textures/ui/emptyStar.png";
    private static final String FILLED_STAR_Path = "textures/ui/filledStar.png";

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getDescription() {
        return "オンラインのプレイヤー一覧を表示";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.ANY;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "このコマンドはプレイヤーのみ実行可能です");
            return true;
        }

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());

        if (FloodgateUtil.isBedrock(player)) {
            // Bedrock版: ActionFormで表示
            List<FormsUtil.ButtonSpec> buttons = new ArrayList<>();
            for (Player p : onlinePlayers) {
                String imagePath = p.isOp() ? FILLED_STAR_Path : EMPTY_STAR_Path;
                buttons.add(FormsUtil.ButtonSpec.ofPath(p.getName(), imagePath));
            }

            boolean formOpened = FormsUtil.openSimpleForm(player, "プレイヤー一覧", buttons, index -> {
                // クリックしても何もしない（表示のみ）
            });

            if (!formOpened) {
                sendError(player, "フォームの表示に失敗しました");
            }
        } else {
            // Java版: クリック可能なメッセージを表示
            Component message = Component.text("=== オンラインのプレイヤー ===\n", NamedTextColor.GREEN);
            for (Player p : onlinePlayers) {
                NamedTextColor color = p.isOp() ? NamedTextColor.GOLD : NamedTextColor.WHITE;
                Component playerComponent = Component.text(p.getName(), color)
                    .clickEvent(ClickEvent.suggestCommand("/tell " + p.getName()));
                message = message.append(playerComponent).append(Component.text(" "));
            }
            Bukkit.getServer().getPlayer(player.getUniqueId()).sendMessage(message);
        }

        return true;
    }
}