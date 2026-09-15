package org.pexserver.koukunn.bettersurvival.Commands.youtube;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.YouTube.YouTubeLiveChatModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

/** YouTubeライブコメント連携を操作する。 */
public final class YouTubeCommand extends BaseCommand {
    private final YouTubeLiveChatModule module;

    public YouTubeCommand(YouTubeLiveChatModule module) {
        this.module = module;
    }

    @Override public String getName() { return "youtube"; }
    @Override public String getDescription() { return "YouTubeライブコメントをゲーム内へ中継"; }
    @Override public PermissionLevel getPermissionLevel() { return PermissionLevel.ADMIN_OR_CONSOLE; }
    @Override public String getUsage() { return "/youtube [配信URL|login|logout|stop|status|settings|setkey キー]"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) openHelp(player);
            else showHelp(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("settings")) {
            if (sender instanceof Player player) openSettings(player);
            else sendError(sender, "設定Dialogはプレイヤー専用です。youtube.yml または setkey を使用してください");
            return true;
        }
        if (args[0].equalsIgnoreCase("login")) {
            if (!(sender instanceof Player player)) {
                sendError(sender, "OAuthログインはプレイヤーから実行してください");
                return true;
            }
            sendSuccess(sender, "Google認証コードを取得しています...");
            module.beginOAuthLogin(player.getUniqueId()).whenComplete((prompt, error) -> module.onMain(() -> {
                if (error != null) {
                    String message = error.getCause() == null ? error.getMessage() : error.getCause().getMessage();
                    sendError(player, message == null ? "OAuthログインを開始できませんでした" : message);
                    return;
                }
                openLoginPrompt(player, prompt);
            }));
            return true;
        }
        if (args[0].equalsIgnoreCase("logout")) {
            module.logoutOAuth();
            sendSuccess(sender, "保存されていたYouTube OAuthログイン情報を削除しました");
            return true;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            if (module.stop()) sendSuccess(sender, "YouTubeコメント連携を停止しました");
            else sendError(sender, "コメント連携は動作していません");
            return true;
        }
        if (args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(module.isActive() ? "§a配信連携中: https://youtu.be/" + module.getVideoId() : "§7配信連携停止中");
            sender.sendMessage(module.isOAuthLoggedIn()
                    ? "§aOAuth: ログイン済み §f" + module.oauthChannelDisplay() + " §7（APIキー不要）"
                    : "§7OAuth: 未ログイン " + (module.isOAuthConfigured() ? "§e（ログイン可能）" : "§c（Client ID未設定）"));
            sender.sendMessage(module.hasApiKey() ? "§aAPIキー: 設定済み" : "§7APIキー: 未設定");
            return true;
        }
        if (args[0].equalsIgnoreCase("setkey")) {
            if (args.length != 2) {
                sendError(sender, "/youtube setkey <YouTube Data APIキー>");
            } else {
                module.setApiKey(args[1]);
                sendSuccess(sender, "APIキーを保存しました");
            }
            return true;
        }
        String error = module.start(args[0]);
        if (error == null) sendSuccess(sender, "配信を確認しています。接続後にコメントを中継します");
        else sendError(sender, error);
        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return args.length == 1 ? List.of("login", "logout", "stop", "status", "settings", "setkey") : List.of();
    }

    private void openHelp(Player player) {
        DialogUI.builder()
                .title("YouTube ライブ連携")
                .body("YouTube配信の通常コメント、スーパーチャット、スーパーステッカー、メンバー通知をMinecraftへ中継します。\n\n"
                        + "1. 名前付き登録通知やAPIキーなし運用は /youtube login\n"
                        + "2. /youtube settings で通知内容を設定\n"
                        + "3. /youtube <配信URL> で連携開始\n"
                        + "4. /youtube status で状態確認\n"
                        + "5. /youtube stop で停止\n\n"
                        + "登録・解除通知は個人名ではなく、公開登録者数の増減として検出します。")
                .notice("閉じる")
                .show(player);
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6YouTube連携: §f/youtube <配信URL> | login | logout | stop | status | settings | setkey <APIキー>");
    }

    private void openSettings(Player player) {
        DialogUI.builder()
                .title("YouTube 通知設定")
                .body("認証情報欄を空にすると組み込み値または現在値を使用します。BossBar表示時間は3～30秒です。")
                .addTextInput("apiKey", "YouTube Data APIキー（変更時のみ）", "", 256, false)
                .addTextInput("clientId", "OAuth Client ID（任意の上書き）", "", 256, false)
                .addTextInput("clientSecret", "OAuth Client Secret（通常は不要）", "", 256, false)
                .addBoolInput("normalChat", "通常コメントをチャットへ表示", module.setting("relay.normal-chat", true))
                .addBoolInput("superChat", "スーパーチャット通知", module.setting("notifications.super-chat", true))
                .addBoolInput("superSticker", "スーパーステッカー通知", module.setting("notifications.super-sticker", true))
                .addBoolInput("membership", "メンバーシップ通知", module.setting("notifications.membership", true))
                .addBoolInput("subscriberChange", "チャンネル登録者数の増減通知", module.setting("notifications.subscriber-change", true))
                .addBoolInput("bossBar", "特別通知をBossBarで表示", module.setting("bossbar.enabled", true))
                .addNumberInput("duration", "BossBar表示時間（秒）", 3F, 30F, 1F,
                        module.setting("bossbar.duration-seconds", 10))
                .confirmation("保存", "キャンセル")
                .onResponse((result, target) -> {
                    if (!result.isConfirmed()) return;
                    module.saveUiSettings(result.getText("apiKey"), result.getText("clientId"), result.getText("clientSecret"), result.getBool("normalChat"),
                            result.getBool("superChat"), result.getBool("superSticker"),
                            result.getBool("membership"), result.getBool("subscriberChange"),
                            result.getBool("bossBar"), Math.round(result.getNumber("duration")));
                    sendSuccess(target, "YouTube通知設定を保存しました");
                })
                .show(player);
    }

    private void openLoginPrompt(Player player, YouTubeLiveChatModule.OAuthPrompt prompt) {
        Component link = Component.text(prompt.verificationUrl(), NamedTextColor.AQUA)
                .clickEvent(ClickEvent.openUrl(prompt.verificationUrl()));
        DialogUI.builder()
                .title("YouTube OAuth Login")
                .body("下のURLを開き、Googleアカウントでログインしてコードを入力してください。\nコード: " + prompt.userCode()
                        + "\n有効時間: 約" + Math.max(1, prompt.expiresInSeconds() / 60) + "分")
                .body(link)
                .notice("認証を待つ")
                .show(player);
        player.sendMessage(Component.text("認証URL: ", NamedTextColor.YELLOW).append(link));
        player.sendMessage(Component.text("認証コード: " + prompt.userCode(), NamedTextColor.GOLD));
    }
}
