package org.pexserver.koukunn.bettersurvival.Commands.rename;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * /rename コマンド
 * 使い方: /rename <名前...>
 * 引数内の `/n` を改行として扱います。
 */
public class RenameCommand extends BaseCommand {

    @Override
    public String getName() {
        return "rename";
    }

    @Override
    public String getDescription() {
        return "手持ちのアイテムの名前を変更します (/n で改行)";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.ANY; // 誰でも使える（必要なら変更可）
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sendError(sender, "プレイヤーのみ実行可能です");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendError(sender, "使用方法: " + getUsage());
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            sendError(sender, "手にアイテムを持っている必要があります");
            return true;
        }

        String name = String.join(" ", args);
        // ユーザーが要求した /n を改行に変換
        name = name.replace("/n", "\n");

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            sendError(sender, "このアイテムは名前を変更できません");
            return true;
        }

        meta.setDisplayName(name);
        item.setItemMeta(meta);

        sendSuccess(sender, "手持ちのアイテム名を変更しました: " + name.replace('\n', '/'));
        return true;
    }

    @Override
    public String getUsage() {
        return "/rename <名前>";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        // 補完は特に実装せず空を返す
        return new ArrayList<>();
    }
}
