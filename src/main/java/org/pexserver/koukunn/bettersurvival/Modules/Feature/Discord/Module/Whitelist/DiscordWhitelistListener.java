package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Whitelist;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.ServerInfoUtil;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Api.McApiClient;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot.DiscordBotSettings;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.OfflineAccess.OfflineAccessManager;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Whitelist.PendingWhitelistModule;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Discord ホワイトリスト申請フロー用の JDA イベントリスナー。
 * ボタンクリック、モーダル送信、リアクション承認を担当する。
 */
public class DiscordWhitelistListener extends ListenerAdapter {
    static final String BUTTON_ID = "whitelist_apply";
    static final String MODAL_ID = "whitelist_modal";
    private static final String INPUT_USERNAME = "username";
    private static final String INPUT_EDITION = "edition";
    private static final String APPROVE_EMOJI = "✅";
    private static final String DENY_EMOJI = "❌";
    private static final String STATUS_PENDING = "承認待ち";
    private static final String STATUS_APPROVED = "承認済み";
    private static final String STATUS_DENIED = "拒否";
    private static final String STATUS_FAILED = "失敗";
    private static final String FIELD_USERNAME = "ゲーマータグ";
    private static final String FIELD_PLATFORM = "プラットフォーム";
    private static final String FIELD_APPLICANT = "申請者";
    private static final String FIELD_STATUS = "状態";
    private static final String FIELD_APPROVER = "対応者";
    private static final String APPROVAL_FOOTER = "Whitelist Approval";
    private static final int COLOR_PENDING = 0xF39C12;
    private static final int COLOR_SUCCESS = 0x57F287;
    private static final int COLOR_DENIED = 0xED4245;
    private static final int APPLICATION_HISTORY_LOOKUP_LIMIT = 50;

    private final Plugin plugin;
    private final PendingWhitelistModule whitelistModule;
    private final OfflineAccessManager offlineAccessManager;
    private final McApiClient mcApiClient;
    private final Supplier<DiscordBotSettings> settingsSupplier;

    public DiscordWhitelistListener(
            Plugin plugin,
            PendingWhitelistModule whitelistModule,
            OfflineAccessManager offlineAccessManager,
            McApiClient mcApiClient,
            Supplier<DiscordBotSettings> settingsSupplier
    ) {
        this.plugin = plugin;
        this.whitelistModule = whitelistModule;
        this.offlineAccessManager = offlineAccessManager;
        this.mcApiClient = mcApiClient;
        this.settingsSupplier = settingsSupplier;
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
        String rawName = mapping != null ? mapping.getAsString().trim() : "";
        if (rawName.isEmpty()) {
            event.reply("❌ ゲーマータグが入力されていません").setEphemeral(true).queue();
            return;
        }
        if (!isValidName(rawName)) {
            event.reply("❌ 無効なゲーマータグです。使用できる文字を確認してください").setEphemeral(true).queue();
            return;
        }

        ModalMapping editionMapping = event.getValue(INPUT_EDITION);
        String editionRaw = editionMapping != null ? editionMapping.getAsString().trim().toLowerCase(Locale.ROOT) : "";
        boolean isBedrock = editionRaw.startsWith("b");
        if (!isBedrock && !editionRaw.startsWith("j")) {
            event.reply("❌ エディションは \"java\" または \"bedrock\" で入力してください").setEphemeral(true).queue();
            return;
        }

        String lookupName = FloodgateUtil.stripPrefix(rawName);
        String prefix = FloodgateUtil.getBedrockPrefix();
        if (prefix == null || prefix.isEmpty()) {
            prefix = ".";
        }
        String floodgateName = isBedrock ? prefix + lookupName.replace(" ", "_") : lookupName;

        // オフラインアカウントは Java 版として扱う
        if (!isBedrock && offlineAccessManager != null && offlineAccessManager.isAllowed(lookupName)) {
            event.deferReply(true).queue();
            handleValidatedApplication(event, floodgateName, lookupName, false);
            return;
        }

        event.deferReply(true).queue();
        if (isBedrock) {
            mcApiClient.validateBedrockPlayer(lookupName).thenAccept(valid -> {
                if (valid) {
                    handleValidatedApplication(event, floodgateName, lookupName, true);
                } else {
                    event.getHook().sendMessage("❌ Bedrock プレイヤーが見つかりませんでした: `" + lookupName + "`").queue();
                }
            });
            return;
        }

        mcApiClient.validateJavaPlayer(lookupName).thenAccept(valid -> {
            if (valid) {
                handleValidatedApplication(event, floodgateName, lookupName, false);
            } else {
                event.getHook().sendMessage("❌ Java プレイヤーが見つかりませんでした: `" + lookupName + "`\nオフラインアカウントの場合は /offline から許可してください。").queue();
            }
        });
    }

    @Override
    public void onMessageReactionAdd(@Nonnull MessageReactionAddEvent event) {
        if (!event.isFromGuild()) return;
        if (event.getUserIdLong() == event.getJDA().getSelfUser().getIdLong()) return;
        if (event.getMessageAuthorIdLong() != event.getJDA().getSelfUser().getIdLong()) return;

        String emoji = event.getEmoji().getName();
        if (!APPROVE_EMOJI.equals(emoji) && !DENY_EMOJI.equals(emoji)) return;

        event.retrieveMessage().queue(
                message -> handleReactionEvent(event, message, emoji),
                error -> plugin.getLogger().warning("[DiscordBot] 承認対象メッセージの取得に失敗しました: " + error.getMessage())
        );
    }

    private void handleValidatedApplication(ModalInteractionEvent event, String username, String lookupName, boolean isBedrock) {
        if (whitelistModule.isAlreadyWhitelisted(lookupName, isBedrock)) {
            event.getHook().sendMessage(buildAlreadyWhitelistedNotice(lookupName, isBedrock)).queue();
            return;
        }
        if (!isBedrock && offlineAccessManager != null && offlineAccessManager.isAllowed(lookupName)) {
            processWhitelistAdd(event, username, lookupName, false);
            return;
        }
        if (whitelistModule.getPendingNames().stream().anyMatch(name -> name.equalsIgnoreCase(username))) {
            event.getHook().sendMessage(buildAlreadyPendingNotice(username, isBedrock)).queue();
            return;
        }
        findExistingApplicationStatus(event.getChannel(), event.getUser().getAsMention(), username, isBedrock, existingStatus -> {
            if (!existingStatus.isEmpty()) {
                event.getHook().sendMessage(buildDuplicateApplicationNotice(username, isBedrock, existingStatus)).queue();
                return;
            }
            if (getSettings().getWhitelistApprovalMode() == DiscordWhitelistApprovalMode.REACTION) {
                sendPendingApprovalRecord(event, username, isBedrock);
                event.getHook().sendMessage("🟧 申請を受け付けました。Discord 承認待ちです").queue();
                return;
            }
            processWhitelistAdd(event, username, lookupName, isBedrock);
        });
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
                case ALREADY_WHITELISTED:
                    event.getHook().sendMessage(buildAlreadyWhitelistedNotice(lookupName, isBedrock)).queue();
                    break;
                default:
                    event.getHook().sendMessage("❌ ホワイトリストへの追加に失敗しました。管理者に問い合わせてください").queue();
                    break;
            }
        });
    }

    private void handleReactionEvent(MessageReactionAddEvent event, Message message, String emoji) {
        MessageEmbed embed = getApprovalEmbed(message.getEmbeds());
        if (embed == null) return;
        if (!STATUS_PENDING.equals(getFieldValue(embed, FIELD_STATUS))) return;

        User user = event.getUser();
        if (user == null) {
            event.retrieveUser().queue(
                resolvedUser -> {
                    if (resolvedUser != null) {
                        resolveAuthorizationAndProcess(event, message, embed, emoji, resolvedUser);
                    } else {
                        plugin.getLogger().warning("[DiscordBot] リアクションユーザーが null でした");
                    }
                },
                error -> plugin.getLogger().warning("[DiscordBot] リアクションユーザーの取得に失敗しました: " + error.getMessage())
            );
            return;
        }
        resolveAuthorizationAndProcess(event, message, embed, emoji, user);
    }

    private void resolveAuthorizationAndProcess(
        MessageReactionAddEvent event,
        Message message,
        MessageEmbed embed,
        String emoji,
        @Nonnull User user
    ) {
        if (isExplicitlyAllowedUser(user.getId())) {
            processApprovalReaction(message, embed, emoji, user);
            return;
        }

        Member member = event.getMember();
        if (member != null) {
            if (isGuildAdministrator(member)) {
                processApprovalReaction(message, embed, emoji, user);
            } else {
                rejectUnauthorizedReaction(event, user);
            }
            return;
        }

        event.retrieveMember().queue(
                resolvedMember -> {
                    if (isGuildAdministrator(resolvedMember)) {
                        processApprovalReaction(message, embed, emoji, user);
                    } else {
                        rejectUnauthorizedReaction(event, user);
                    }
                },
                error -> rejectUnauthorizedReaction(event, user)
        );
    }

    private void processApprovalReaction(Message message, MessageEmbed embed, String emoji, @Nonnull User user) {
        String username = stripCode(getFieldValue(embed, FIELD_USERNAME));
        if (username.isEmpty()) {
            return;
        }
        boolean isBedrock = "bedrock".equalsIgnoreCase(getFieldValue(embed, FIELD_PLATFORM));
        String lookupName = isBedrock ? FloodgateUtil.stripPrefix(username).replace("_", " ") : username;
        String approver = user.getAsMention() + " (" + user.getName() + ")";
        if (APPROVE_EMOJI.equals(emoji)) {
            if (whitelistModule.isAlreadyWhitelisted(lookupName, isBedrock)) {
                updateApprovalMessage(message, buildResolvedEmbed(
                        "✅ ホワイトリスト申請承認済み",
                        COLOR_SUCCESS,
                        username,
                        isBedrock,
                        getFieldValue(embed, FIELD_APPLICANT),
                        STATUS_APPROVED,
                        approver,
                        "すでに whitelist 登録済みでした。"
                ));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                PendingWhitelistModule.AddResult result = whitelistModule.addPending(username);
                switch (result) {
                    case PENDING_ADDED:
                        updateApprovalMessage(message, buildResolvedEmbed(
                                "✅ ホワイトリスト申請承認済み",
                                COLOR_SUCCESS,
                                username,
                                isBedrock,
                                getFieldValue(embed, FIELD_APPLICANT),
                                STATUS_APPROVED,
                                approver,
                                "Minecraft サーバーへの参加を許可しました。"
                        ));
                        break;
                    case ALREADY_PENDING:
                        updateApprovalMessage(message, buildResolvedEmbed(
                                "✅ ホワイトリスト申請承認済み",
                                COLOR_SUCCESS,
                                username,
                                isBedrock,
                                getFieldValue(embed, FIELD_APPLICANT),
                                STATUS_APPROVED,
                                approver,
                                "すでに承認待ち一覧に登録済みでした。"
                        ));
                        break;
                    case ALREADY_WHITELISTED:
                        updateApprovalMessage(message, buildResolvedEmbed(
                                "✅ ホワイトリスト申請承認済み",
                                COLOR_SUCCESS,
                                username,
                                isBedrock,
                                getFieldValue(embed, FIELD_APPLICANT),
                                STATUS_APPROVED,
                                approver,
                                "すでに whitelist 登録済みでした。"
                        ));
                        break;
                    default:
                        updateApprovalMessage(message, buildResolvedEmbed(
                                "❌ ホワイトリスト申請処理失敗",
                                COLOR_DENIED,
                                username,
                                isBedrock,
                                getFieldValue(embed, FIELD_APPLICANT),
                                STATUS_FAILED,
                                approver,
                                "ホワイトリスト追加に失敗しました。"
                        ));
                        break;
                }
            });
            return;
        }

        updateApprovalMessage(message, buildResolvedEmbed(
                "❌ ホワイトリスト申請拒否",
                COLOR_DENIED,
                username,
                isBedrock,
                getFieldValue(embed, FIELD_APPLICANT),
                STATUS_DENIED,
                approver,
                "申請を拒否しました。"
        ));
    }

    private void rejectUnauthorizedReaction(MessageReactionAddEvent event, @Nonnull User user) {
        event.getReaction().removeReaction(user).queue(
                success -> { },
                error -> { }
        );
    }

    private void sendApplicationRecord(ModalInteractionEvent event, String username, String lookupName, boolean isBedrock) {
        MessageEmbed embed = buildResolvedEmbed(
                "📋 ホワイトリスト申請受付",
                COLOR_SUCCESS,
                username,
                isBedrock,
                event.getUser().getAsMention() + " (" + event.getUser().getName() + ")",
                STATUS_APPROVED,
                "自動承認 (デフォルト)",
                "Minecraft サーバーへの参加を許可しました。"
        );

        if (embed == null) {
            plugin.getLogger().warning("[DiscordBot] 申請記録 Embed の構築に失敗しました");
            return;
        }
        event.getChannel().sendMessageEmbeds(embed).queue(
                msg -> triggerWhitelistReorder(event.getChannel().getId()),
                err -> plugin.getLogger().warning("[DiscordBot] 申請記録 Embed 送信失敗: " + err.getMessage())
        );
    }

    private void sendPendingApprovalRecord(ModalInteractionEvent event, String username, boolean isBedrock) {
        EmbedBuilder embed = createBaseEmbed(
                "🟧 ホワイトリスト申請承認待ち",
                COLOR_PENDING,
                username,
                isBedrock,
                event.getUser().getAsMention() + " (" + event.getUser().getName() + ")"
        );
        embed.addField(FIELD_STATUS, STATUS_PENDING, true);

        event.getChannel().sendMessageEmbeds(embed.build()).queue(
                message -> {
                    addApprovalReactions(message);
                    triggerWhitelistReorder(event.getChannel().getId());
                },
                err -> plugin.getLogger().warning("[DiscordBot] 承認待ち Embed 送信失敗: " + err.getMessage())
        );
    }

    private void addApprovalReactions(Message message) {
        message.addReaction(Emoji.fromUnicode(APPROVE_EMOJI)).queue(
                success -> message.addReaction(Emoji.fromUnicode(DENY_EMOJI)).queue(
                        next -> { },
                        error -> plugin.getLogger().warning("[DiscordBot] 拒否リアクション付与に失敗しました: " + error.getMessage())
                ),
                error -> plugin.getLogger().warning("[DiscordBot] 承認リアクション付与に失敗しました: " + error.getMessage())
        );
    }

    private void updateApprovalMessage(Message message, MessageEmbed updatedEmbed) {
        message.editMessageEmbeds(updatedEmbed).queue(
                success -> message.clearReactions().queue(
                        cleared -> { },
                        error -> { }
                ),
                error -> plugin.getLogger().warning("[DiscordBot] 承認結果 Embed 更新失敗: " + error.getMessage())
        );
    }

    private MessageEmbed buildResolvedEmbed(
            String title,
            int color,
            String username,
            boolean isBedrock,
            String applicant,
            String status,
            String approver,
            String description
    ) {
        EmbedBuilder embed = createBaseEmbed(title, color, username, isBedrock, applicant);
        embed.setDescription(description);
        if (status != null ) {
            embed.addField(FIELD_STATUS, status, true);
        }
        if (approver != null) {
            embed.addField(FIELD_APPROVER, approver, false);
        }
        return embed.build();
    }

    private EmbedBuilder createBaseEmbed(String title, int color, String username, boolean isBedrock, String applicant) {
        String platform = isBedrock ? "Bedrock" : "Java";
        if (applicant == null || applicant.isEmpty()) {
            applicant = "不明";
        }
        return new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setThumbnail(McApiClient.getFaceUrl(username, isBedrock))
                .addField(FIELD_USERNAME, "`" + username + "`", true)
                .addField(FIELD_PLATFORM, platform, true)
                .addField(FIELD_APPLICANT, applicant, false)
                .setTimestamp(Instant.now())
                .setFooter(ServerInfoUtil.getServerName() + " " + APPROVAL_FOOTER);
    }

    private MessageEmbed getApprovalEmbed(List<MessageEmbed> embeds) {
        for (MessageEmbed embed : embeds) {
            MessageEmbed.Footer footer = embed.getFooter();
            String footerText = footer != null ? footer.getText() : null;
            if (footerText == null || !footerText.endsWith(APPROVAL_FOOTER)) {
                continue;
            }
            if (!hasField(embed, FIELD_USERNAME) || !hasField(embed, FIELD_PLATFORM) || !hasField(embed, FIELD_STATUS)) {
                continue;
            }
            return embed;
        }
        return null;
    }

    private boolean hasField(MessageEmbed embed, String name) {
        return !getFieldValue(embed, name).isEmpty();
    }

    private String getFieldValue(MessageEmbed embed, String name) {
        for (MessageEmbed.Field field : embed.getFields()) {
            if (field != null && name.equals(field.getName()) && field.getValue() != null) {
                return field.getValue();
            }
        }
        return "";
    }

    private String stripCode(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("`", "").trim();
    }

    private boolean isGuildAdministrator(Member member) {
        return member.isOwner() || member.hasPermission(Permission.ADMINISTRATOR);
    }

    private boolean isExplicitlyAllowedUser(String userId) {
        return getSettings().getWhitelistApproverUserIds().contains(userId);
    }

    private DiscordBotSettings getSettings() {
        DiscordBotSettings settings = settingsSupplier.get();
        return settings != null ? settings : new DiscordBotSettings();
    }

    private void triggerWhitelistReorder(String channelId) {
        if (plugin instanceof org.pexserver.koukunn.bettersurvival.Loader loader) {
            var botModule = loader.getDiscordBotModule();
            if (botModule != null) {
                botModule.triggerWhitelistReorderDelay(channelId);
            }
        }
    }

    private boolean isValidName(String name) {
        if (name.length() > 50) return false;
        return name.matches("[\\w .\\-']+");
    }

    private void findExistingApplicationStatus(
            MessageChannel channel,
            String applicantMention,
            String username,
            boolean isBedrock,
            Consumer<String> callback
    ) {
        channel.getHistory().retrievePast(APPLICATION_HISTORY_LOOKUP_LIMIT).queue(messages -> {
            String targetPlatform = isBedrock ? "Bedrock" : "Java";
            for (Message message : messages) {
                MessageEmbed embed = getApprovalEmbed(message.getEmbeds());
                if (embed == null) {
                    continue;
                }
                if (!username.equalsIgnoreCase(stripCode(getFieldValue(embed, FIELD_USERNAME)))) {
                    continue;
                }
                if (!targetPlatform.equalsIgnoreCase(getFieldValue(embed, FIELD_PLATFORM))) {
                    continue;
                }
                String applicant = getFieldValue(embed, FIELD_APPLICANT);
                if (applicant == null || !applicant.startsWith(applicantMention)) {
                    continue;
                }
                callback.accept(getFieldValue(embed, FIELD_STATUS));
                return;
            }
            callback.accept("");
        }, error -> {
            plugin.getLogger().warning("[DiscordBot] 申請履歴の取得に失敗しました: " + error.getMessage());
            callback.accept("");
        });
    }

    private @Nonnull String buildAlreadyWhitelistedNotice(String lookupName, boolean isBedrock) {
        String platform = isBedrock ? "Bedrock" : "Java";
        return "ℹ️ " + platform + " プレイヤー `" + lookupName + "` はすでに whitelist に登録されています";
    }

    private @Nonnull String buildAlreadyPendingNotice(String username, boolean isBedrock) {
        String platform = isBedrock ? "Bedrock" : "Java";
        return "ℹ️ " + platform + " プレイヤー `" + username + "` はすでに接続待機 whitelist に登録されています";
    }

    private @Nonnull String buildDuplicateApplicationNotice(String username, boolean isBedrock, String status) {
        String platform = isBedrock ? "Bedrock" : "Java";
        if (STATUS_PENDING.equals(status)) {
            return "ℹ️ " + platform + " プレイヤー `" + username + "` の申請はすでに承認待ちです";
        }
        if (STATUS_APPROVED.equals(status)) {
            return "ℹ️ " + platform + " プレイヤー `" + username + "` はすでに申請承認済みです";
        }
        if (STATUS_DENIED.equals(status)) {
            return "ℹ️ " + platform + " プレイヤー `" + username + "` はすでに申請履歴があります。再申請が必要なら管理者に連絡してください";
        }
        return "ℹ️ " + platform + " プレイヤー `" + username + "` の申請履歴がすでに存在します";
    }
}
