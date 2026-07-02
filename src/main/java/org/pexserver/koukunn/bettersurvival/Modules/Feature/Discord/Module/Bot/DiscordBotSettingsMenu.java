package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;
import org.pexserver.koukunn.bettersurvival.Loader;

class DiscordBotSettingsMenu {
    private final Loader plugin;
    private final DiscordBotModule module;

    DiscordBotSettingsMenu(Loader plugin, DiscordBotModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    void openMenu(Player player) {
        String botStatus = module.isBotOnline() ? "§aオンライン" : "§cオフライン";

        ChestUI.builder()
                .title("Discord 設定")
                .size(27)
                .addButtonAt(11, "§6Webhook 設定", Material.PAPER, "Join/Leave/Status 通知の設定")
                .addButtonAt(15, "§bDiscord Bot 設定", Material.NETHER_STAR, "Bot 状態: " + botStatus)
                .then((result, p) -> {
                    if (result.cancelled || result.slot == null) return;
                    if (result.slot == 11) {
                        plugin.getDiscordWebhookModule().openMenu(p);
                    } else if (result.slot == 15) {
                        openBotSettings(p);
                    }
                })
                .show(player);
    }

    private void openBotSettings(Player player) {
        DiscordBotSettings current = module.getSettings();
        DialogUI.builder()
                .title("Discord Bot 設定")
                .body("Bot Token を設定します（Discord Developer Portal で取得）。長いトークンは複数行入力欄に貼り付けできます。")
                .addTextInput("token", "Bot Token", current.getToken(), 2048, true)
                .confirmation("保存", "キャンセル")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openMenu(p);
                        return;
                    }
                    DiscordBotSettings updated = module.createUpdatedSettings(
                            result.getText("token"),
                            current.getWhitelistChannelId()
                    );
                    if (module.saveSettings(updated)) {
                        p.sendMessage("§aDiscord Bot 設定を保存しました");
                    } else {
                        p.sendMessage("§cDiscord Bot 設定の保存に失敗しました");
                    }
                    openMenu(p);
                })
                .show(player);
    }
}
