package org.pexserver.koukunn.bettersurvival.Commands.hotp;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService.WebServiceModule;

public class HotpCommand extends BaseCommand {
    private final Loader plugin;

    public HotpCommand(Loader plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "hotp";
    }

    @Override
    public String getDescription() {
        return "Web 登録用ワンタイムコードを表示";
    }

    @Override
    public boolean isEnabled() {
        WebServiceModule module = plugin.getWebServiceModule();
        return module != null && module.isGloballyEnabled();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "このコマンドはプレイヤーのみ使用できます");
            return true;
        }
        WebServiceModule module = plugin.getWebServiceModule();
        if (module == null || !module.isGloballyEnabled()) {
            sendError(sender, "WebService は現在無効です");
            return true;
        }
        String code = module.issueHotp(player);
        DialogUI.builder()
            .title("Web 登録コード")
            .body("ワンタイムコード")
            .body(code)
            .body("このコードを WebSite の登録画面に入力してください。")
            .body("期限は5分です。")
            .notice("閉じる")
            .show(player);
        player.sendMessage("§aWeb 登録用ワンタイムコードをダイアログで表示しました: §e" + code);
        return true;
    }

    @Override
    public String getUsage() {
        return "/hotp";
    }
}
