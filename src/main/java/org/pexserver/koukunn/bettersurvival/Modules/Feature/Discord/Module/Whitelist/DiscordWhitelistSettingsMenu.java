package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Whitelist;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot.DiscordBotModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot.DiscordBotSettings;

public class DiscordWhitelistSettingsMenu {
    private final DiscordBotModule module;
    private final DiscordWhitelistMessageService whitelistMessageService;

    public DiscordWhitelistSettingsMenu(DiscordBotModule module, DiscordWhitelistMessageService whitelistMessageService) {
        this.module = module;
        this.whitelistMessageService = whitelistMessageService;
    }

    public void openMenu(Player player) {
        DiscordBotSettings current = module.getSettings();
        String channelId = current.getWhitelistChannelId();
        String channelLore = channelId.isEmpty() ? "未設定" : channelId;
        String approvalMode = current.getWhitelistApprovalMode() == DiscordWhitelistApprovalMode.REACTION
                ? "リアクション承認"
                : "デフォルト";
        String approverSummary = current.getWhitelistApproverUserIds().isEmpty()
                ? "追加許可ユーザー: なし"
                : "追加許可ユーザー: " + current.getWhitelistApproverUserIds().size() + "件";

        ChestUI.builder()
                .title("Discord ホワイトリスト設定")
                .size(27)
                .addButtonAt(11, "§e申請設定", Material.NAME_TAG,
                        "チャンネル: " + channelLore + "\n認証形式: " + approvalMode + "\n" + approverSummary)
                .addButtonAt(15, "§a申請 Embed 送信", Material.PAPER, "設定済みチャンネルに Embed を送信")
                .then((result, p) -> {
                    if (result.cancelled || result.slot == null) return;
                    if (result.slot == 11) {
                        openSettingsInput(p);
                    } else if (result.slot == 15) {
                        sendWhitelistEmbed(p);
                    }
                })
                .show(player);
    }

    private void sendWhitelistEmbed(Player player) {
        String channelId = module.getSettings().getWhitelistChannelId();
        if (channelId.isEmpty()) {
            player.sendMessage("§cチャンネル ID が未設定です。先に設定してください");
            return;
        }
        if (!module.isBotOnline()) {
            player.sendMessage("§cDiscord Bot が起動していません。Token を設定してください");
            return;
        }
        whitelistMessageService.sendWhitelistEmbed(channelId);
        player.sendMessage("§aDiscord にホワイトリスト申請 Embed を送信しました");
    }

    private void openSettingsInput(Player player) {
        DiscordBotSettings current = module.getSettings();
        DialogUI.builder()
                .title("ホワイトリスト申請設定")
                .body("チャンネル ID、認証形式、追加で許可する Discord ユーザー ID を設定します")
                .addTextInput("channelId", "チャンネル ID", current.getWhitelistChannelId(), 30, false)
                .addBoolInput("reactionApprovalEnabled", "リアクション承認を使う", current.getWhitelistApprovalMode() == DiscordWhitelistApprovalMode.REACTION)
                .addTextInput("approverUserIds", "追加許可ユーザー ID (カンマ/改行区切り)", current.getWhitelistApproverUserIdsText(), 500, true)
                .confirmation("保存", "キャンセル")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openMenu(p);
                        return;
                    }
                    String newChannelId = result.getText("channelId").trim();
                    if (!newChannelId.isEmpty() && !newChannelId.matches("\\d+")) {
                        p.sendMessage("§cチャンネル ID は数字のみで指定してください");
                        openMenu(p);
                        return;
                    }
                    String approverUserIdsText = result.getText("approverUserIds");
                    if (!isValidApproverUserIdsText(approverUserIdsText)) {
                        p.sendMessage("§c追加許可ユーザー ID は数字のみをカンマまたは改行区切りで入力してください");
                        openMenu(p);
                        return;
                    }
                    DiscordBotSettings updated = module.createUpdatedSettings(current.getToken(), newChannelId);
                    updated.setWhitelistApprovalMode(result.getBool("reactionApprovalEnabled")
                            ? DiscordWhitelistApprovalMode.REACTION
                            : DiscordWhitelistApprovalMode.DEFAULT);
                    updated.setWhitelistApproverUserIdsFromText(approverUserIdsText);
                    if (module.saveSettings(updated)) {
                        p.sendMessage("§aDiscord ホワイトリスト申請設定を保存しました");
                    } else {
                        p.sendMessage("§c保存に失敗しました");
                    }
                    openMenu(p);
                })
                .show(player);
    }

    private boolean isValidApproverUserIdsText(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return true;
        }
        String[] parts = rawValue.split("[,\\s]+");
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!part.trim().matches("\\d+")) {
                return false;
            }
        }
        return true;
    }
}
