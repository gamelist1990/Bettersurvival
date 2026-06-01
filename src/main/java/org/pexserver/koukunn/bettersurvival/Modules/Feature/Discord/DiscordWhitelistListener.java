package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.ServerInfoUtil;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Whitelist.PendingWhitelistModule;

import javax.annotation.Nonnull;

import java.time.Instant;

/**
 * Discord ホワイトリスト申請フロー用の JDA イベントリスナー。
 * ボタンクリック → モーダル表示 → ユーザー名検証 → ホワイトリスト追加 の流れを担当する。
 */
public class DiscordWhitelistListener extends ListenerAdapter {
    static final String BUTTON_ID = "whitelist_apply";
    static final String MODAL_ID = "whitelist_modal";
    private static final String INPUT_USERNAME = "username";
    private static final String INPUT_EDITION = "edition";

    private final Plugin plugin;
    private final PendingWhitelistModule whitelistModule;
    private final McApiClient mcApiClient;

    public DiscordWhitelistListener(Plugin plugin, PendingWhitelistModule whitelistModule, McApiClient mcApiClient) {
        this.plugin = plugin;
        this.whitelistModule = whitelistModule;
        this.mcApiClient = mcApiClient;
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        if (!BUTTON_ID.equals(event.getComponentId())) return;

        TextInput usernameInput = TextInput.create(INPUT_USERNAME, TextInputStyle.SHORT)
                .setPlaceholder("Java 版: username  /  Bedrock 版: Xbox ゲーマータグ")
                .setMinLength(1)
                .setMaxLength(50)
                .setRequired(true)
                .build();

        TextInput editionInput = TextInput.create(INPUT_EDITION, TextInputStyle.SHORT)
                .setPlaceholder("java または bedrock")
                .setMinLength(4)
                .setMaxLength(7)
                .setRequired(true)
                .build();

        Modal modal = Modal.create(MODAL_ID, "ホワイトリスト申請")
                .addComponents(
                        Label.of("Minecraft ゲーマータグ", usernameInput),
                        Label.of("エディション (java / bedrock)", editionInput)
                )
                .build();

        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
        if (!MODAL_ID.equals(event.getModalId())) return;

        ModalMapping mapping = event.getValue(INPUT_USERNAME);
        String rawName = (mapping != null) ? mapping.getAsString().trim() : "";

        if (rawName.isEmpty()) {
            event.reply("❌ ゲーマータグが入力されていません").setEphemeral(true).queue();
            return;
        }

        if (!isValidName(rawName)) {
            event.reply("❌ 無効なゲーマータグです。使用できる文字を確認してください").setEphemeral(true).queue();
            return;
        }

        ModalMapping editionMapping = event.getValue(INPUT_EDITION);
        String editionRaw = (editionMapping != null) ? editionMapping.getAsString().trim().toLowerCase() : "";
        boolean isBedrock = editionRaw.startsWith("b");

        if (!isBedrock && !editionRaw.startsWith("j")) {
            event.reply("❌ エディションは \"java\" または \"bedrock\" で入力してください").setEphemeral(true).queue();
            return;
        }

        // ユーザーが語った名前（ドットプレフィックスは不要）が mc-api.io の検証・顔画像 URL に使う名前
        String lookupName = FloodgateUtil.stripPrefix(rawName);

        // Floodgate に保存する形式: 　ドットプレフィックス + スペース → _
        String prefix = FloodgateUtil.getBedrockPrefix();
        if (prefix == null || prefix.isEmpty()) {
            prefix = ".";
        }
        String floodgateName = isBedrock ? prefix + lookupName.replace(" ", "_") : lookupName;

        event.deferReply(true).queue();

        if (isBedrock) {
            mcApiClient.validateBedrockPlayer(lookupName).thenAccept(valid -> {
                if (valid) {
                    processWhitelistAdd(event, floodgateName, lookupName, true);
                } else {
                    event.getHook().sendMessage("❌ Bedrock プレイヤーが見つかりませんでした: `" + lookupName + "`").queue();
                }
            });
        } else {
            mcApiClient.validateJavaPlayer(lookupName).thenAccept(valid -> {
                if (valid) {
                    processWhitelistAdd(event, floodgateName, lookupName, false);
                } else {
                    event.getHook().sendMessage("❌ Java プレイヤーが見つかりませんでした: `" + lookupName + "`").queue();
                }
            });
        }
    }

    private void processWhitelistAdd(ModalInteractionEvent event, String username, String lookupName, boolean isBedrock) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            PendingWhitelistModule.AddResult result = whitelistModule.addPending(username);
            switch (result) {
                case PENDING_ADDED:
                    event.getHook().sendMessage("✅ ホワイトリストに追加しました！！参加をお待ちしております").queue();
                    sendApplicationRecord(event, username, lookupName, isBedrock);
                    break;
                case ALREADY_PENDING:
                    event.getHook().sendMessage("ℹ️ すでにホワイトリスト申請済みです: `" + username + "`").queue();
                    break;
                default:
                    event.getHook().sendMessage("❌ ホワイトリストへの追加に失敗しました。管理者に問い合わせてください").queue();
                    break;
            }
        });
    }

    private void sendApplicationRecord(ModalInteractionEvent event, String username, String lookupName, boolean isBedrock) {
        String platform = isBedrock ? "Bedrock" : "Java";
        String faceUrl = McApiClient.getFaceUrl(lookupName, isBedrock);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 ホワイトリスト申請受付")
                .setColor(0x57F287)
                .setThumbnail(faceUrl)
                .addField("ゲーマータグ", "`" + username + "`", true)
                .addField("プラットフォーム", platform, true)
                .addField("申請者", event.getUser().getAsMention() + " (" + event.getUser().getName() + ")", false)
                .setTimestamp(Instant.now())
                .setFooter(ServerInfoUtil.getServerName() + " Whitelist");

        event.getChannel().sendMessageEmbeds(embed.build()).queue(
                msg -> {
                    if (plugin instanceof org.pexserver.koukunn.bettersurvival.Loader) {
                        var botModule = ((org.pexserver.koukunn.bettersurvival.Loader) plugin).getDiscordBotModule();
                        if (botModule != null) {
                            botModule.triggerWhitelistReorderDelay(event.getChannel().getId());
                        }
                    }
                },
                err -> plugin.getLogger().warning("[DiscordBot] 申請記録 Embed 送信失敗: " + err.getMessage())
        );
    }

    /**
     * ゲーマータグのセキュリティチェック。
     * 制御文字・HTMLタグ等を排除し、許可された文字のみ通す。
     */
    private boolean isValidName(String name) {
        if (name.length() > 50) return false;
        return name.matches("[\\w .\\-']+");
    }
}
