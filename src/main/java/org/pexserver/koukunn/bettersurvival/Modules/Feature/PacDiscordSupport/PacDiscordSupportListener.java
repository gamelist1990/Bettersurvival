package org.pexserver.koukunn.bettersurvival.Modules.Feature.PacDiscordSupport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot.DiscordBotSettings;
import org.pexserver.pac.api.BanInfo;
import org.pexserver.pac.api.DetectionHistoryPage;
import org.pexserver.pac.api.DetectionRecord;
import org.pexserver.pac.api.PacApi;
import org.pexserver.pac.api.SupportCaseInfo;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

final class PacDiscordSupportListener extends ListenerAdapter {
    private static final String BUTTON_ID = "pac_appeal_start";
    private static final String MODAL_ID = "pac_appeal_modal";
    private static final String INPUT_SUPPORT_ID = "support_id";
    private static final String INPUT_PLAYER_NAME = "player_name";
    private static final String INPUT_APPEAL_REASON = "appeal_reason";
    private static final String BAN_DETAILS_BUTTON_PREFIX = "pac_ban_details:";
    private static final String BAN_APPEAL_BUTTON_PREFIX = "pac_ban_appeal:";
    private static final String BAN_LIST_PREVIOUS_BUTTON = "pac_ban_list:previous";
    private static final String BAN_LIST_NEXT_BUTTON = "pac_ban_list:next";
    private static final int BAN_LIST_PAGE_SIZE = 10;
    private static final int BAN_DETAILS_RECORD_COUNT = 5;
    private static final long BAN_LIST_IDLE_RESET_TICKS = 3 * 60 * 20L;

    private final Loader plugin;
    private final Supplier<PacApi> apiSupplier;
    private final Supplier<DiscordBotSettings> settingsSupplier;
    private final Supplier<JDA> jdaSupplier;
    private final PacDiscordSupportCaseService caseService;
    private final Predicate<List<String>> banListMessageIdsSaver;
    private final AtomicBoolean banListUpdateInProgress = new AtomicBoolean();
    private volatile BanListPaginationState banListState;

    PacDiscordSupportListener(
            Loader plugin,
            Supplier<PacApi> apiSupplier,
            Supplier<DiscordBotSettings> settingsSupplier,
            Supplier<JDA> jdaSupplier,
            PacDiscordSupportCaseService caseService,
            Predicate<List<String>> banListMessageIdsSaver) {
        this.plugin = plugin;
        this.apiSupplier = apiSupplier;
        this.settingsSupplier = settingsSupplier;
        this.jdaSupplier = jdaSupplier;
        this.caseService = caseService;
        this.banListMessageIdsSaver = banListMessageIdsSaver;
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (componentId.startsWith(BAN_APPEAL_BUTTON_PREFIX)) {
            caseService.handleAppealNoticeButton(event,
                    componentId.substring(BAN_APPEAL_BUTTON_PREFIX.length()));
            return;
        }
        if (componentId.startsWith(BAN_DETAILS_BUTTON_PREFIX)) {
            showBanDetails(event, componentId.substring(BAN_DETAILS_BUTTON_PREFIX.length()));
            return;
        }
        if (BAN_LIST_PREVIOUS_BUTTON.equals(componentId) || BAN_LIST_NEXT_BUTTON.equals(componentId)) {
            handleBanListNavigation(event, BAN_LIST_NEXT_BUTTON.equals(componentId));
            return;
        }
        if (!BUTTON_ID.equals(componentId)) return;

        DiscordBotSettings settings = settingsSupplier.get();
        if (settings == null || !settings.getPacAppealChannelId().equals(event.getChannel().getId())) {
            event.reply("❌ この異議申し立てフォームは現在の受付チャンネルでは使用できません")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TextInput supportIdInput = TextInput.create(INPUT_SUPPORT_ID, TextInputStyle.SHORT)
                .setPlaceholder("BAN 時に表示された PAC サポート ID")
                .setMinLength(1)
                .setMaxLength(32)
                .setRequired(true)
                .build();
        TextInput playerNameInput = TextInput.create(INPUT_PLAYER_NAME, TextInputStyle.SHORT)
                .setPlaceholder("BAN された Minecraft プレイヤー名")
                .setMinLength(1)
                .setMaxLength(50)
                .setRequired(true)
                .build();
        TextInput reasonInput = TextInput.create(INPUT_APPEAL_REASON, TextInputStyle.PARAGRAPH)
                .setPlaceholder("異議申し立ての内容を入力してください")
                .setMinLength(10)
                .setMaxLength(1000)
                .setRequired(true)
                .build();

        Modal modal = Modal.create(MODAL_ID, "PAC 異議申し立て")
                .addComponents(
                        Label.of("PAC サポート ID", supportIdInput),
                        Label.of("Minecraft プレイヤー名", playerNameInput),
                        Label.of("異議申し立ての理由", reasonInput))
                .build();
        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
        if (!MODAL_ID.equals(event.getModalId())) return;

        String supportId = getValue(event, INPUT_SUPPORT_ID);
        String playerName = getValue(event, INPUT_PLAYER_NAME);
        String appealReason = getValue(event, INPUT_APPEAL_REASON);
        if (supportId.isBlank() || playerName.isBlank() || appealReason.length() < 10) {
            event.reply("PAC サポート ID、プレイヤー名、10文字以上の申立て理由を入力してください")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply(true).queue(hook -> submitAppeal(event, hook, supportId, playerName, appealReason));
    }

    boolean publishAppealPanel() {
        DiscordBotSettings settings = settingsSupplier.get();
        JDA jda = jdaSupplier.get();
        if (settings == null || jda == null || settings.getPacAppealChannelId().isBlank()) {
            plugin.getLogger().warning("[PAC Discord] 申立てフォームを投稿できません。Bot またはチャンネル ID が未設定です");
            return false;
        }
        TextChannel channel = jda.getTextChannelById(settings.getPacAppealChannelId());
        if (channel == null) {
            plugin.getLogger().warning("[PAC Discord] 公開受付チャンネルが見つかりません。Bot の接続状態とチャンネル ID を確認してください");
            return false;
        }

        MessageEmbed embed = new EmbedBuilder()
                .setTitle("PAC BAN 異議申し立て")
                .setDescription("PAC の BAN に心当たりがない場合は、下のボタンから申請してください。\n"
                        + "BAN 時に表示された PAC サポート ID と、BAN された Minecraft プレイヤー名が必要です。\n"
                        + "申立てはスタッフが確認します。送信しても BAN は自動解除されません。")
                .setColor(0x5865F2)
                .setTimestamp(Instant.now())
                .build();
        channel.sendMessageEmbeds(embed)
                .addComponents(ActionRow.of(Button.primary(BUTTON_ID, "📝 異議申し立て")))
                .queue(
                        message -> plugin.getLogger().info("[PAC Discord] 異議申し立てフォームを投稿しました"),
                        error -> plugin.getLogger().log(Level.WARNING,
                                "[PAC Discord] 異議申し立てフォームの投稿に失敗しました: " + error.getMessage()));
        return true;
    }

    boolean publishBanList() {
        return updatePublishedBanList(true);
    }

    void refreshPublishedBanList() {
        updatePublishedBanList(false);
    }

    void shutdown() {
        BanListPaginationState state = banListState;
        banListState = null;
        if (state != null && state.idleResetTask != null) {
            state.idleResetTask.cancel();
        }
    }

    void sendBanNotice(BanInfo ban) {
        DiscordBotSettings settings = settingsSupplier.get();
        JDA jda = jdaSupplier.get();
        if (settings == null || jda == null || settings.getPacAppealInboxChannelId().isBlank()) return;

        TextChannel channel = jda.getTextChannelById(settings.getPacAppealInboxChannelId());
        if (channel == null) {
            plugin.getLogger().warning("[PAC Discord] スタッフ用チャンネルが見つからないため BAN 通知を送信できません");
            return;
        }
        String expires = ban.permanent() ? "永久" : discordTimestamp(ban.expiresAt(), "F")
                + " (" + discordTimestamp(ban.expiresAt(), "R") + ")";
        String reason = ban.reason() == null || ban.reason().isBlank() ? "理由未設定" : ban.reason();
        MessageEmbed embed = new EmbedBuilder()
                .setTitle("PAC BAN 通知")
                .setColor(0xED4245)
                .addField("プレイヤー", safeField(ban.playerName()), true)
                .addField("UUID", ban.playerId().toString(), true)
                .addField("PAC サポート ID", safeField(ban.supportId()), true)
                .addField("期限", expires, true)
                .addField("理由", safeField(reason), false)
                .addField("BAN 時刻", discordTimestamp(ban.createdAt(), "F")
                        + " (" + discordTimestamp(ban.createdAt(), "R") + ")", false)
                .addField("BAN状態", "有効", true)
                .addField("異議申し立て", "なし", true)
                .setTimestamp(Instant.ofEpochMilli(ban.createdAt()))
                .build();
        channel.sendMessageEmbeds(embed)
                .addComponents(ActionRow.of(Button.secondary(
                        BAN_DETAILS_BUTTON_PREFIX + ban.playerId(), "📊 直近ログ・検知数値")))
                .queue(
                        message -> {
                            if (!caseService.recordBanNotice(ban.supportId(), channel.getId(), message.getId(),
                                    ban.expiresAt(), ban.permanent())) {
                                plugin.getLogger().warning("[PAC Discord] BAN 通知の参照を保存できませんでした");
                            }
                        },
                        error -> plugin.getLogger().log(Level.WARNING,
                                "[PAC Discord] BAN 通知の送信に失敗しました: " + error.getMessage()));
    }

    private boolean updatePublishedBanList(boolean force) {
        PacApi api = apiSupplier.get();
        DiscordBotSettings settings = settingsSupplier.get();
        JDA jda = jdaSupplier.get();
        if (api == null || settings == null || jda == null || settings.getPacBanListChannelId().isBlank()) {
            return false;
        }
        TextChannel channel = jda.getTextChannelById(settings.getPacBanListChannelId());
        if (channel == null || !banListUpdateInProgress.compareAndSet(false, true)) {
            return false;
        }

        try {
            List<BanInfo> bans = api.activeBans().stream()
                    .sorted(Comparator.comparingLong(BanInfo::createdAt).reversed())
                    .toList();
            List<String> oldIds = settings.getPacBanListMessageIds();
            BanListPaginationState current = banListState;
            if (!force && current != null && current.channelId.equals(channel.getId())
                    && current.message.getJDA() == jda && current.bans.equals(bans)
                    && oldIds.contains(current.messageId)) {
                banListUpdateInProgress.set(false);
                return true;
            }

            List<MessageEmbed> pages = createBanListPages(bans);
            if (oldIds.isEmpty()) {
                sendNewBanListMessage(channel, pages, bans, oldIds);
            } else {
                String oldId = oldIds.get(0);
                channel.retrieveMessageById(oldId).queue(
                        message -> editBanListMessage(channel, message, pages, bans, oldIds),
                        error -> sendNewBanListMessage(channel, pages, bans, oldIds));
            }
            return true;
        } catch (RuntimeException error) {
            banListUpdateInProgress.set(false);
            plugin.getLogger().log(Level.WARNING, "[PAC Discord] BAN 一覧の取得に失敗しました", error);
            return false;
        }
    }

    private void editBanListMessage(TextChannel channel, Message message, List<MessageEmbed> pages,
                                    List<BanInfo> bans, List<String> oldIds) {
        message.editMessageEmbeds(pages.get(0))
                .setComponents(banListComponents(0, pages.size()))
                .queue(
                        updated -> completeBanListUpdate(channel, updated, pages, bans, oldIds),
                        error -> sendNewBanListMessage(channel, pages, bans, oldIds));
    }

    private void sendNewBanListMessage(TextChannel channel, List<MessageEmbed> pages,
                                       List<BanInfo> bans, List<String> oldIds) {
        channel.sendMessageEmbeds(pages.get(0))
                .addComponents(banListComponents(0, pages.size()))
                .queue(
                        message -> completeBanListUpdate(channel, message, pages, bans, oldIds),
                        error -> {
                            plugin.getLogger().log(Level.WARNING,
                                    "[PAC Discord] BAN 一覧 Embed の送信に失敗しました: " + error.getMessage());
                            banListUpdateInProgress.set(false);
                        });
    }

    private void completeBanListUpdate(TextChannel channel, Message message, List<MessageEmbed> pages,
                                       List<BanInfo> bans, List<String> oldIds) {
        DiscordBotSettings currentSettings = settingsSupplier.get();
        boolean sameChannel = currentSettings != null
                && channel.getId().equals(currentSettings.getPacBanListChannelId());
        boolean saved = false;
        try {
            if (sameChannel) {
                saved = banListMessageIdsSaver.test(List.of(message.getId()));
            }
            if (!saved) {
                plugin.getLogger().warning("[PAC Discord] BAN 一覧メッセージ ID の保存に失敗しました");
                if (!oldIds.contains(message.getId())) {
                    message.delete().queue(
                            success -> { },
                            error -> plugin.getLogger().log(Level.FINE,
                                    "[PAC Discord] 未保存の BAN 一覧 Embed を削除できませんでした: " + error.getMessage()));
                }
                return;
            }

            BanListPaginationState previousState = banListState;
            if (previousState != null && previousState.idleResetTask != null) {
                previousState.idleResetTask.cancel();
            }
            BanListPaginationState updatedState = new BanListPaginationState(
                    channel.getId(), message.getId(), pages, bans, message);
            banListState = updatedState;
            scheduleBanListIdleReset(updatedState);
            deleteRemovedBanListMessages(channel, oldIds, message.getId());
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "[PAC Discord] BAN 一覧メッセージ ID の保存に失敗しました", error);
        } finally {
            banListUpdateInProgress.set(false);
        }
    }

    private void deleteRemovedBanListMessages(TextChannel channel, List<String> oldIds, String retainedId) {
        for (String oldId : oldIds) {
            if (retainedId.equals(oldId)) continue;
            channel.retrieveMessageById(oldId).queue(
                    message -> message.delete().queue(
                            success -> { },
                            error -> plugin.getLogger().log(Level.FINE,
                                    "[PAC Discord] 古い BAN 一覧メッセージを削除できませんでした: " + error.getMessage())),
                    error -> { });
        }
    }

    private List<MessageEmbed> createBanListPages(List<BanInfo> bans) {
        int pageCount = Math.max(1, (bans.size() + BAN_LIST_PAGE_SIZE - 1) / BAN_LIST_PAGE_SIZE);
        List<MessageEmbed> pages = new ArrayList<>(pageCount);
        for (int page = 0; page < pageCount; page++) {
            int start = page * BAN_LIST_PAGE_SIZE;
            int end = Math.min(start + BAN_LIST_PAGE_SIZE, bans.size());
            StringBuilder description = new StringBuilder();
            if (bans.isEmpty()) {
                description.append("現在、PAC BAN 中のプレイヤーはいません。");
            } else {
                for (int index = start; index < end; index++) {
                    BanInfo ban = bans.get(index);
                    String playerName = truncate(ban.playerName(), 64).replace("`", "ˋ");
                    String reason = truncateSingleLine(ban.reason(), 100);
                    String expiry = ban.permanent() ? "永久" : discordTimestamp(ban.expiresAt(), "F")
                            + " (" + discordTimestamp(ban.expiresAt(), "R") + ")";
                    description.append("• **").append(playerName).append("** — ").append(expiry)
                            .append("\n理由: ").append(reason).append("\n\n");
                }
            }
            pages.add(new EmbedBuilder()
                    .setTitle("PAC 現在の BAN 一覧 (" + (page + 1) + "/" + pageCount + ")")
                    .setDescription(description.toString())
                    .setColor(0xED4245)
                    .setFooter("現在 BAN 中: " + bans.size() + " 人 | ボタンでページ切替 | 3分無操作で1ページ目に戻ります")
                    .setTimestamp(Instant.now())
                    .build());
        }
        return pages;
    }

    private List<ActionRow> banListComponents(int page, int pageCount) {
        return List.of(ActionRow.of(
                Button.secondary(BAN_LIST_PREVIOUS_BUTTON, "◀ 前へ").withDisabled(page <= 0),
                Button.secondary(BAN_LIST_NEXT_BUTTON, "次へ ▶").withDisabled(page >= pageCount - 1)));
    }

    private void handleBanListNavigation(ButtonInteractionEvent event, boolean next) {
        BanListPaginationState state = banListState;
        DiscordBotSettings settings = settingsSupplier.get();
        if (state == null || settings == null || !state.messageId.equals(event.getMessageId())
                || !state.channelId.equals(event.getChannel().getId())
                || !state.channelId.equals(settings.getPacBanListChannelId())) {
            event.reply("この BAN 一覧は更新されています。最新の一覧をご利用ください")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferEdit().queue(hook -> Bukkit.getScheduler().runTask(plugin,
                () -> changeBanListPage(event, hook, state, next)),
                error -> plugin.getLogger().log(Level.FINE,
                        "[PAC Discord] BAN 一覧のページ操作を受け付けられませんでした: " + error.getMessage()));
    }

    private void changeBanListPage(ButtonInteractionEvent event, InteractionHook hook,
                                   BanListPaginationState state, boolean next) {
        if (banListState != state || !state.messageId.equals(event.getMessageId())) {
            hook.editOriginal("この BAN 一覧は更新されています。最新の一覧をご利用ください").queue();
            return;
        }
        int requestedPage = state.currentPage + (next ? 1 : -1);
        state.currentPage = Math.max(0, Math.min(requestedPage, state.pages.size() - 1));
        scheduleBanListIdleReset(state);
        int page = state.currentPage;
        hook.editOriginalEmbeds(state.pages.get(page))
                .setComponents(banListComponents(page, state.pages.size()))
                .queue(
                        updated -> state.message = updated,
                        error -> plugin.getLogger().log(Level.WARNING,
                                "[PAC Discord] BAN 一覧のページを更新できませんでした: " + error.getMessage()));
    }

    private void scheduleBanListIdleReset(BanListPaginationState state) {
        if (state.idleResetTask != null) {
            state.idleResetTask.cancel();
        }
        long generation = ++state.idleResetGeneration;
        state.idleResetTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (banListState != state || state.idleResetGeneration != generation || state.currentPage == 0) return;
            state.currentPage = 0;
            Message message = state.message;
            if (message == null) return;
            message.editMessageEmbeds(state.pages.get(0))
                    .setComponents(banListComponents(0, state.pages.size()))
                    .queue(
                            updated -> state.message = updated,
                            error -> plugin.getLogger().log(Level.WARNING,
                                    "[PAC Discord] BAN 一覧を1ページ目に戻せませんでした: " + error.getMessage()));
        }, BAN_LIST_IDLE_RESET_TICKS);
    }

    private void showBanDetails(ButtonInteractionEvent event, String playerIdValue) {
        if (!isStaff(event.getMember())) {
            event.reply("この BAN 詳細は PAC スタッフ専用です").setEphemeral(true).queue();
            return;
        }
        UUID playerId;
        try {
            playerId = UUID.fromString(playerIdValue);
        } catch (IllegalArgumentException error) {
            event.reply("BAN 詳細を特定できませんでした").setEphemeral(true).queue();
            return;
        }
        PacApi api = apiSupplier.get();
        if (api == null) {
            event.reply("PAC is not available; PAC連携機能は停止しています").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue(hook -> {
            try {
                api.detectionHistory(playerId.toString(), 1, BAN_DETAILS_RECORD_COUNT)
                        .whenComplete((history, error) -> completeBanDetails(hook, playerId, history, error));
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING, "[PAC Discord] BAN 詳細ログの取得に失敗しました", error);
                hook.editOriginal("PAC の検知ログを取得できませんでした。時間をおいて再度お試しください").queue();
            }
        });
    }

    private void completeBanDetails(InteractionHook hook, UUID playerId,
                                    DetectionHistoryPage history, Throwable error) {
        if (error != null || history == null) {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "[PAC Discord] BAN 詳細ログの取得に失敗しました", error);
            }
            hook.editOriginal("PAC の検知ログを取得できませんでした。時間をおいて再度お試しください").queue();
            return;
        }
        MessageEmbed embed = createBanDetailsEmbed(playerId, history);
        hook.editOriginalEmbeds(embed).queue();
    }

    private MessageEmbed createBanDetailsEmbed(UUID playerId, DetectionHistoryPage history) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("PAC BAN 詳細・直近の検知ログ")
                .setColor(0xF39C12)
                .setDescription("UUID: `" + playerId + "`\n直近 " + history.records().size()
                        + " 件 / 記録合計 " + history.total() + " 件")
                .setTimestamp(Instant.now());
        if (history.records().isEmpty()) {
            embed.addField("検知ログ", "このプレイヤーの検知ログはありません。", false);
        } else {
            for (DetectionRecord record : history.records()) {
                String fieldName = "#" + record.id() + " · " + truncate(record.detectorKey(), 80)
                        + " · score " + record.score();
                String value = "検知時刻: " + discordTimestamp(record.createdAt(), "F")
                        + " (" + discordTimestamp(record.createdAt(), "R") + ")"
                        + "\n詳細: " + truncateSingleLine(record.detail(), 360)
                        + "\n数値: " + formatMetrics(record.metricsJson());
                embed.addField(truncate(fieldName, 256), truncate(value, 1000), false);
            }
        }
        embed.setFooter("PAC の直近検知記録を表示しています");
        return embed.build();
    }

    private String formatMetrics(String metricsJson) {
        if (metricsJson == null || metricsJson.isBlank()) return "数値データなし";
        try {
            JsonElement parsed = JsonParser.parseString(metricsJson);
            if (!parsed.isJsonObject()) return truncate(metricsJson, 500);
            JsonObject object = parsed.getAsJsonObject();
            if (object.size() == 0) return "数値データなし";
            StringBuilder output = new StringBuilder("`");
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonElement value = entry.getValue();
                if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) continue;
                if (output.length() > 1) output.append(" | ");
                output.append(entry.getKey()).append("=").append(value.getAsString());
            }
            if (output.length() == 1) return "数値データなし";
            output.append('`');
            return truncate(output.toString(), 500);
        } catch (RuntimeException error) {
            return truncate(metricsJson, 500);
        }
    }

    private void submitAppeal(ModalInteractionEvent event, InteractionHook hook,
                              String supportId, String playerName, String appealReason) {
        PacApi api = apiSupplier.get();
        if (api == null) {
            hook.editOriginal("PAC is not available; PAC連携機能は停止しています").queue();
            return;
        }
        DiscordBotSettings settings = settingsSupplier.get();
        if (settings == null || settings.getPacAppealInboxChannelId().isBlank()) {
            hook.editOriginal("スタッフ用チャンネルが未設定のためサポートを開始できません").queue();
            return;
        }
        api.supportCase(supportId).whenComplete((result, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "[PAC Discord] サポート記録の検索に失敗しました", error);
                hook.editOriginal("PAC のサポート記録を確認できませんでした。時間をおいて再度お試しください").queue();
                return;
            }
            Optional<SupportCaseInfo> supportCase = result == null ? Optional.empty() : result;
            if (supportCase.isEmpty()) {
                hook.editOriginal("PAC サポート ID が見つかりません。BAN 時に表示された ID を確認してください").queue();
                return;
            }
            SupportCaseInfo support = supportCase.get();
            if (!support.active()) {
                hook.editOriginal("この PAC サポート ID に対応する有効な BAN はありません").queue();
                return;
            }
            if (support.playerName() == null || !support.playerName().equalsIgnoreCase(playerName.trim())) {
                hook.editOriginal("入力された Minecraft 名と PAC サポート ID が一致しません").queue();
                return;
            }
            caseService.createAppeal(support, event, hook, appealReason.trim());
        });
    }

    private String truncateSingleLine(String value, int maxLength) {
        if (value == null || value.isBlank()) return "理由未設定";
        String normalized = value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        return truncate(normalized, maxLength);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) return "不明";
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }

    private String discordTimestamp(long epochMillis, String style) {
        return "<t:" + Math.floorDiv(epochMillis, 1000L) + ":" + style + ">";
    }

    private String getValue(ModalInteractionEvent event, String id) {
        ModalMapping mapping = event.getValue(id);
        return mapping == null ? "" : mapping.getAsString().trim();
    }

    private String safeField(String value) {
        if (value == null || value.isBlank()) return "未設定";
        return value.length() > 1024 ? value.substring(0, 1021) + "..." : value;
    }

    private boolean isStaff(Member member) {
        if (member == null) return false;
        if (member.isOwner() || member.hasPermission(Permission.ADMINISTRATOR)) return true;
        DiscordBotSettings settings = settingsSupplier.get();
        if (settings == null) return false;
        String publicRoleId = member.getGuild().getPublicRole().getId();
        return member.getRoles().stream().anyMatch(role -> !role.isPublicRole()
                && !role.getId().equals(publicRoleId)
                && settings.getPacSupportStaffRoleIds().contains(role.getId()));
    }

    private static final class BanListPaginationState {
        private final String channelId;
        private final String messageId;
        private final List<MessageEmbed> pages;
        private final List<BanInfo> bans;
        private volatile Message message;
        private int currentPage;
        private long idleResetGeneration;
        private BukkitTask idleResetTask;

        private BanListPaginationState(String channelId, String messageId, List<MessageEmbed> pages,
                                       List<BanInfo> bans, Message message) {
            this.channelId = channelId;
            this.messageId = messageId;
            this.pages = List.copyOf(pages);
            this.bans = List.copyOf(bans);
            this.message = message;
        }
    }
}
