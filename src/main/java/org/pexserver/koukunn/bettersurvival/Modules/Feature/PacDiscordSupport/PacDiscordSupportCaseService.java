package org.pexserver.koukunn.bettersurvival.Modules.Feature.PacDiscordSupport;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.bukkit.Bukkit;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

final class PacDiscordSupportCaseService extends ListenerAdapter {
    private static final String DASHBOARD_HISTORY_BUTTON = "pac_staff_history";
    private static final String DASHBOARD_DETECTIONS_BUTTON = "pac_staff_detections";
    private static final String DASHBOARD_BANS_BUTTON = "pac_staff_bans";
    private static final String HISTORY_SEARCH_MODAL = "pac_staff_history_search";
    private static final String HISTORY_SEARCH_INPUT = "pac_history_identity";
    private static final String DETECTION_SEARCH_MODAL = "pac_staff_detection_search";
    private static final String DETECTION_SEARCH_INPUT = "pac_detection_identity";
    private static final String FINISH_NOTE_INPUT = "pac_case_finish_note";
    private static final String CASE_PANEL_PREFIX = "pac_case_";
    private static final String CASE_HISTORY_BUTTON_PREFIX = "pac_case_history:";
    private static final String CASE_DETECTIONS_BUTTON_PREFIX = "pac_case_detections:";
    private static final String CASE_BANS_BUTTON_PREFIX = "pac_case_bans:";
    private static final String CASE_UNBAN_BUTTON_PREFIX = "pac_case_unban:";
    private static final String CASE_RESOLVE_BUTTON_PREFIX = "pac_case_resolve:";
    private static final String CASE_CLOSE_BUTTON_PREFIX = "pac_case_close:";
    private static final String STAFF_CASE_LOG_PREFIX = "pac_staff_case_log:";
    private static final String STAFF_CASE_LOG_PAGE_PREFIX = "pac_staff_case_log_page:";
    private static final String STAFF_BAN_PAGE_PREFIX = "pac_staff_ban_page:";
    private static final String BAN_APPEAL_BUTTON_PREFIX = "pac_ban_appeal:";
    private static final String FINISH_MODAL_PREFIX = "pac_case_finish:";
    private static final String UNBAN_MODAL_PREFIX = "pac_case_unban_finish:";
    private static final String UNBAN_NOTE_INPUT = "pac_case_unban_note";
    private static final String API_CONTROL_AUTHORITY_DISABLED_MESSAGE =
            "PAC の api.control-authority が無効です。plugins/PAC/config.yml で "
                    + "api.control-authority: true に変更し、/pac reload を実行してください。"
                    + "この設定は BAN 解除を含む外部 API の変更操作を許可します。";
    private static final int HISTORY_PAGE_SIZE = 5;
    private static final int HISTORY_SEARCH_LIMIT = 5;
    private static final int BAN_PAGE_SIZE = 10;
    private static final long CLOSED_CHANNEL_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(1);
    private static final EnumSet<Permission> CASE_VIEW_PERMISSIONS = EnumSet.of(
            Permission.VIEW_CHANNEL,
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_EMBED_LINKS,
            Permission.MESSAGE_ATTACH_FILES);
    private static final EnumSet<Permission> CASE_STAFF_PERMISSIONS = EnumSet.of(
            Permission.VIEW_CHANNEL,
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_EMBED_LINKS,
            Permission.MESSAGE_ATTACH_FILES,
            Permission.MESSAGE_MANAGE,
            Permission.PIN_MESSAGES);
    private static final EnumSet<Permission> CASE_BOT_PERMISSIONS = EnumSet.of(
            Permission.VIEW_CHANNEL,
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_EMBED_LINKS,
            Permission.MESSAGE_ATTACH_FILES,
            Permission.MANAGE_CHANNEL,
            Permission.MESSAGE_MANAGE,
            Permission.PIN_MESSAGES);
    private static final EnumSet<Permission> VIEW_CHANNEL_DENIED = EnumSet.of(Permission.VIEW_CHANNEL);

    private final Loader plugin;
    private final PacSupportCaseStore store;
    private final Supplier<PacApi> apiSupplier;
    private final Supplier<DiscordBotSettings> settingsSupplier;
    private final Supplier<JDA> jdaSupplier;
    private final Predicate<String> dashboardMessageIdSaver;
    private final AtomicBoolean dashboardUpdateInProgress = new AtomicBoolean();
    private final Set<String> banNoticeChecksInFlight = ConcurrentHashMap.newKeySet();
    private final Object categoryLock = new Object();
    private volatile String cachedSupportGuildId = "";
    private volatile JDA cachedSupportJda;
    private volatile CompletableFuture<Category> supportCategoryFuture;

    PacDiscordSupportCaseService(Loader plugin, PacSupportCaseStore store,
                                 Supplier<PacApi> apiSupplier,
                                 Supplier<DiscordBotSettings> settingsSupplier,
                                 Supplier<JDA> jdaSupplier,
                                 Predicate<String> dashboardMessageIdSaver) {
        this.plugin = plugin;
        this.store = store;
        this.apiSupplier = apiSupplier;
        this.settingsSupplier = settingsSupplier;
        this.jdaSupplier = jdaSupplier;
        this.dashboardMessageIdSaver = dashboardMessageIdSaver;
    }

    void shutdown() {
        synchronized (categoryLock) {
            supportCategoryFuture = null;
            cachedSupportGuildId = "";
            cachedSupportJda = null;
        }
    }

    void refreshOpenCaseStaffRoles(List<String> previousRoleIds) {
        List<String> previousRoles = List.copyOf(previousRoleIds);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> applyOpenCaseStaffRoles(previousRoles, 5), 100L);
    }

    private void applyOpenCaseStaffRoles(List<String> previousRoleIds, int retriesRemaining) {
        DiscordBotSettings settings = settingsSupplier.get();
        JDA jda = jdaSupplier.get();
        if (settings == null) return;
        if (jda == null) {
            retryOpenCaseStaffRoleRefresh(previousRoleIds, retriesRemaining);
            return;
        }
        Set<String> currentRoleIds = Set.copyOf(settings.getPacSupportStaffRoleIds());
        Set<String> removedRoleIds = previousRoleIds.stream()
                .filter(roleId -> !currentRoleIds.contains(roleId))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean missingChannel = false;
        for (PacSupportCaseRecord record : store.recent(Integer.MAX_VALUE)) {
            if (record.status() != PacSupportCaseStatus.STARTED) continue;
            Guild guild = jda.getGuildById(record.guildId());
            TextChannel channel = guild == null ? null : guild.getTextChannelById(record.channelId());
            if (channel == null) {
                missingChannel = true;
                continue;
            }
            for (String roleId : removedRoleIds) {
                Role role = guild.getRoleById(roleId);
                if (role != null && !role.isPublicRole()) {
                    channel.getManager().putPermissionOverride(role, List.of(), VIEW_CHANNEL_DENIED)
                            .queue(success -> { }, error -> plugin.getLogger().log(Level.WARNING,
                                    "[PAC Discord] 削除されたスタッフロールのアクセスを更新できませんでした: "
                                            + error.getMessage()));
                }
            }
            for (String roleId : currentRoleIds) {
                Role role = guild.getRoleById(roleId);
                if (role != null && !role.isPublicRole()) {
                    channel.getManager().putPermissionOverride(role, CASE_STAFF_PERMISSIONS, List.of())
                            .queue(success -> { }, error -> plugin.getLogger().log(Level.WARNING,
                                    "[PAC Discord] スタッフロールのアクセスを更新できませんでした: "
                                            + error.getMessage()));
                }
            }
        }
        if (missingChannel) retryOpenCaseStaffRoleRefresh(previousRoleIds, retriesRemaining);
    }

    private void retryOpenCaseStaffRoleRefresh(List<String> previousRoleIds, int retriesRemaining) {
        if (retriesRemaining <= 0) return;
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> applyOpenCaseStaffRoles(previousRoleIds, retriesRemaining - 1), 100L);
    }

    void createAppeal(SupportCaseInfo support, ModalInteractionEvent event,
                      InteractionHook hook, String appealReason) {
        DiscordBotSettings settings = settingsSupplier.get();
        JDA jda = jdaSupplier.get();
        Guild guild = event.getGuild();
        Member applicant = event.getMember();
        if (settings == null || jda == null || guild == null || applicant == null) {
            hook.editOriginal("申立てを開始できませんでした。Discord Bot とサーバー設定を確認してください").queue();
            return;
        }
        if (settings.getPacSupportStaffRoleIds().isEmpty()) {
            hook.editOriginal("PACスタッフのロールIDが未設定です。サーバー管理者に連絡してください").queue();
            return;
        }
        TextChannel staffChannel = jda.getTextChannelById(settings.getPacAppealInboxChannelId());
        if (staffChannel == null || !staffChannel.getGuild().getId().equals(guild.getId())) {
            hook.editOriginal("スタッフ用チャンネルが見つからないか、申立てチャンネルと別のサーバーにあります")
                    .queue();
            return;
        }
        if (!areStaffRolesAvailable(guild, settings)) {
            hook.editOriginal("設定されたPACスタッフロールがこのDiscordサーバーに見つかりません").queue();
            return;
        }
        if (!store.reserve(support.supportId())) {
            hook.editOriginal("この PAC サポート ID では、すでに異議申し立てが送信されています。以前のサポート履歴をご確認ください")
                    .queue();
            return;
        }

        String applicantId = applicant.getId();
        String applicantName = applicant.getUser().getName();
        String normalizedReason = appealReason == null ? "" : appealReason.trim();
        CompletableFuture<Category> categoryRequest = supportCategory(guild, settings);
        categoryRequest.whenComplete((category, categoryError) -> {
            if (categoryError != null || category == null) {
                synchronized (categoryLock) {
                    if (supportCategoryFuture == categoryRequest
                            && guild.getJDA() == cachedSupportJda
                            && guild.getId().equals(cachedSupportGuildId)) {
                        supportCategoryFuture = null;
                        cachedSupportGuildId = "";
                        cachedSupportJda = null;
                    }
                }
                store.releaseReservation(support.supportId());
                plugin.getLogger().log(Level.WARNING, "[PAC Discord] Support カテゴリを用意できませんでした", categoryError);
                hook.editOriginal("サポートチャンネルを作成できませんでした。Bot にチャンネル管理権限があるか確認してください")
                        .queue();
                return;
            }
            createPrivateCaseChannel(guild, category, applicant, support, normalizedReason,
                    applicantId, applicantName, hook, settings);
        });
    }

    void ensureStaffDashboard() {
        cleanupClosedCaseChannels();
        DiscordBotSettings settings = settingsSupplier.get();
        JDA jda = jdaSupplier.get();
        if (settings == null || jda == null || settings.getPacAppealInboxChannelId().isBlank()
                || !dashboardUpdateInProgress.compareAndSet(false, true)) return;
        TextChannel channel = jda.getTextChannelById(settings.getPacAppealInboxChannelId());
        if (channel == null) {
            dashboardUpdateInProgress.set(false);
            return;
        }
        String messageId = settings.getPacSupportDashboardMessageId();
        if (messageId.isBlank()) {
            sendDashboard(channel);
            return;
        }
        channel.retrieveMessageById(messageId).queue(
                message -> editDashboard(channel, message),
                error -> sendDashboard(channel));
    }

    private void editDashboard(TextChannel channel, Message message) {
        message.editMessageEmbeds(dashboardEmbed())
                .setComponents(dashboardComponents())
                .queue(updated -> pinAndSaveDashboard(channel, updated), error -> {
                    plugin.getLogger().log(Level.WARNING,
                            "[PAC Discord] スタッフ管理パネルを更新できませんでした: " + error.getMessage());
                    dashboardUpdateInProgress.set(false);
                });
    }

    private void sendDashboard(TextChannel channel) {
        channel.sendMessageEmbeds(dashboardEmbed())
                .addComponents(dashboardComponents())
                .queue(message -> pinAndSaveDashboard(channel, message), error -> {
                    plugin.getLogger().log(Level.WARNING,
                            "[PAC Discord] スタッフ管理パネルを投稿できませんでした: " + error.getMessage());
                    dashboardUpdateInProgress.set(false);
                });
    }

    private void pinAndSaveDashboard(TextChannel channel, Message message) {
        if (message.isPinned()) {
            saveDashboardId(channel, message);
        } else {
            message.pin().queue(
                    success -> saveDashboardId(channel, message),
                    error -> {
                        plugin.getLogger().log(Level.WARNING,
                                "[PAC Discord] スタッフ管理パネルをピン留めできませんでした: " + error.getMessage());
                        saveDashboardId(channel, message);
                    });
        }
    }

    private void saveDashboardId(TextChannel channel, Message message) {
        DiscordBotSettings current = settingsSupplier.get();
        try {
            boolean saved = current != null
                    && channel.getId().equals(current.getPacAppealInboxChannelId())
                    && dashboardMessageIdSaver.test(message.getId());
            if (!saved) plugin.getLogger().warning("[PAC Discord] スタッフ管理パネル ID を保存できませんでした");
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "[PAC Discord] スタッフ管理パネル ID を保存できませんでした", error);
        } finally {
            dashboardUpdateInProgress.set(false);
        }
    }

    private MessageEmbed dashboardEmbed() {
        DiscordBotSettings settings = settingsSupplier.get();
        String roleStatus = settings == null || settings.getPacSupportStaffRoleIds().isEmpty()
                ? "⚠ PACスタッフロール ID が未設定です"
                : "設定済み PACスタッフロール: " + settings.getPacSupportStaffRoleIds().size() + " 件";
        return new EmbedBuilder()
                .setTitle("PAC サポート管理パネル")
                .setDescription("異議申し立ての専用チャンネルはカテゴリ **Support** に作成されます。\n"
                        + "各案件には **開始 → 解決** または **開始 → 終了** の状態を記録します。\n"
                        + "ボタンから過去の申立て・会話履歴、PAC検知ログ、現在のBAN一覧を確認できます。\n\n"
                        + roleStatus)
                .setColor(0x5865F2)
                .setFooter("管理操作は設定されたスタッフロールとサーバー管理者に限定されます")
                .build();
    }

    private List<ActionRow> dashboardComponents() {
        return List.of(ActionRow.of(
                        Button.primary(DASHBOARD_HISTORY_BUTTON, "📚 サポート履歴"),
                        Button.secondary(DASHBOARD_DETECTIONS_BUTTON, "🔎 PAC検知ログ"),
                        Button.danger(DASHBOARD_BANS_BUTTON, "📋 BAN一覧")));
    }

    private CompletableFuture<Category> supportCategory(Guild guild, DiscordBotSettings settings) {
        synchronized (categoryLock) {
            if (guild.getJDA() == cachedSupportJda && guild.getId().equals(cachedSupportGuildId)
                    && supportCategoryFuture != null) {
                return supportCategoryFuture;
            }
            cachedSupportGuildId = guild.getId();
            cachedSupportJda = guild.getJDA();
            List<Category> existing = guild.getCategoriesByName("Support", true);
            if (!existing.isEmpty()) {
                Category category = existing.get(0);
                secureCategory(category, settings);
                supportCategoryFuture = CompletableFuture.completedFuture(category);
                return supportCategoryFuture;
            }

            CompletableFuture<Category> future = new CompletableFuture<>();
            supportCategoryFuture = future;
            var action = guild.createCategory("Support")
                    .addPermissionOverride(guild.getPublicRole(), List.of(), VIEW_CHANNEL_DENIED)
                    .addPermissionOverride(guild.getSelfMember(), CASE_BOT_PERMISSIONS, List.of());
            for (String roleId : settings.getPacSupportStaffRoleIds()) {
                Role role = guild.getRoleById(roleId);
                if (role != null) {
                    action.addPermissionOverride(role, CASE_STAFF_PERMISSIONS, List.of());
                }
            }
            action.queue(category -> future.complete(category), future::completeExceptionally);
            return future;
        }
    }

    private void secureCategory(Category category, DiscordBotSettings settings) {
        Guild guild = category.getGuild();
        category.getManager()
                .putPermissionOverride(guild.getPublicRole(), List.of(), VIEW_CHANNEL_DENIED)
                .queue(success -> { }, error -> plugin.getLogger().log(Level.WARNING,
                        "[PAC Discord] Support カテゴリの公開権限を制限できませんでした: " + error.getMessage()));
        Member self = guild.getSelfMember();
        if (self != null) {
            category.getManager()
                    .putPermissionOverride(self, CASE_BOT_PERMISSIONS, List.of())
                    .queue(success -> { }, error -> plugin.getLogger().log(Level.WARNING,
                            "[PAC Discord] Support カテゴリのBot権限を設定できませんでした: " + error.getMessage()));
        }
        for (String roleId : settings.getPacSupportStaffRoleIds()) {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                category.getManager()
                        .putPermissionOverride(role, CASE_STAFF_PERMISSIONS, List.of())
                        .queue(success -> { }, error -> plugin.getLogger().log(Level.WARNING,
                                "[PAC Discord] Support カテゴリのスタッフ権限を設定できませんでした: " + error.getMessage()));
            }
        }
    }

    private void createPrivateCaseChannel(Guild guild, Category category, Member applicant,
                                          SupportCaseInfo support, String appealReason,
                                          String applicantId, String applicantName,
                                          InteractionHook hook, DiscordBotSettings settings) {
        try {
            String channelName = supportChannelName(support.playerName(), support.supportId());
            var action = guild.createTextChannel(channelName, category)
                    .setTopic("PAC Support ID: " + support.supportId() + " | UUID: " + support.playerId()
                            + " | status: STARTED");
            action.addPermissionOverride(guild.getPublicRole(), List.of(), VIEW_CHANNEL_DENIED);
            for (PermissionOverride inherited : category.getPermissionOverrides()) {
                if (inherited.isRoleOverride()) {
                    Role role = inherited.getRole();
                    if (role == null || role.isPublicRole()) continue;
                    if (settings.getPacSupportStaffRoleIds().contains(role.getId())) {
                        action.addPermissionOverride(role, CASE_STAFF_PERMISSIONS, List.of());
                    } else if (inherited.getAllowed().contains(Permission.VIEW_CHANNEL)) {
                        action.addPermissionOverride(role, List.of(), VIEW_CHANNEL_DENIED);
                    }
                } else if (inherited.isMemberOverride()) {
                    Member member = inherited.getMember();
                    if (member == null || member.getId().equals(applicantId)
                            || member.getId().equals(guild.getSelfMember().getId())) continue;
                    boolean staffMember = member.getRoles().stream()
                            .anyMatch(role -> settings.getPacSupportStaffRoleIds().contains(role.getId()));
                    if (staffMember) {
                        action.addPermissionOverride(member, CASE_STAFF_PERMISSIONS, List.of());
                    } else if (inherited.getAllowed().contains(Permission.VIEW_CHANNEL)) {
                        action.addPermissionOverride(member, List.of(), VIEW_CHANNEL_DENIED);
                    }
                }
            }
            action.addPermissionOverride(applicant, CASE_VIEW_PERMISSIONS, List.of());
            action.addPermissionOverride(guild.getSelfMember(), CASE_BOT_PERMISSIONS, List.of());
            for (String roleId : settings.getPacSupportStaffRoleIds()) {
                Role role = guild.getRoleById(roleId);
                if (role != null) action.addPermissionOverride(role, CASE_STAFF_PERMISSIONS, List.of());
            }
            action.queue(channel -> sendCasePanel(channel, support, appealReason,
                    applicantId, applicantName, guild.getId(), hook), error -> {
                store.releaseReservation(support.supportId());
                plugin.getLogger().log(Level.WARNING,
                        "[PAC Discord] 専用サポートチャンネルを作成できませんでした", error);
                hook.editOriginal("専用サポートチャンネルを作成できませんでした。Bot のチャンネル管理権限を確認してください")
                        .queue();
            });
        } catch (RuntimeException error) {
            store.releaseReservation(support.supportId());
            plugin.getLogger().log(Level.WARNING, "[PAC Discord] 専用サポートチャンネルを準備できませんでした", error);
            hook.editOriginal("専用サポートチャンネルを準備できませんでした。設定を確認してください").queue();
        }
    }

    private void sendCasePanel(TextChannel channel, SupportCaseInfo support, String appealReason,
                               String applicantId, String applicantName, String guildId,
                               InteractionHook hook) {
        long createdAt = System.currentTimeMillis();
        MessageEmbed panel = casePanelEmbed(support.supportId(), support.playerId().toString(),
                support.playerName(), appealReason, PacSupportCaseStatus.STARTED, createdAt, "");
        channel.sendMessageEmbeds(panel)
                .addComponents(casePanelComponents(support.supportId(), true))
                .queue(message -> message.pin().queue(
                        success -> persistNewCase(channel, support, appealReason, applicantId,
                                applicantName, guildId, message, createdAt, hook),
                        error -> failNewCase(channel, support.supportId(), hook,
                                "管理パネルをピン留めできませんでした。Bot のメッセージ管理権限を確認してください")),
                        error -> failNewCase(channel, support.supportId(), hook,
                                "サポート管理パネルを投稿できませんでした"));
    }

    private void persistNewCase(TextChannel channel, SupportCaseInfo support, String appealReason,
                                String applicantId, String applicantName, String guildId,
                                Message panelMessage, long createdAt, InteractionHook hook) {
        PacSupportCaseRecord record = new PacSupportCaseRecord(
                support.supportId(), support.playerId().toString(), support.playerName(),
                applicantId, applicantName, guildId, channel.getId(), panelMessage.getId(),
                appealReason, createdAt, createdAt, PacSupportCaseStatus.STARTED,
                List.of(new PacSupportCaseLog(createdAt, applicantId, applicantName,
                        "APPEAL_SUBMITTED", appealReason)));
        if (!store.create(record)) {
            failNewCase(channel, support.supportId(), hook,
                    "申立て履歴を保存できませんでした。サーバー管理者に連絡してください");
            return;
        }
        updateBanNoticeAndNotifyStaff(record);
        channel.sendMessage("<@" + applicantId + "> PAC 異議申し立てを受け付けました。スタッフがこのチャンネルで対応します。")
                .queue();
        hook.editOriginal("✅ 異議申し立てを受け付けました。専用チャンネルはこちらです: " + channel.getAsMention())
                .queue();
    }

    boolean recordBanNotice(String supportId, String channelId, String messageId,
                            long expiresAt, boolean permanent) {
        return store.rememberBanNotice(supportId, channelId, messageId, expiresAt, permanent);
    }

    void refreshBanNoticeStatuses() {
        PacApi api = apiSupplier.get();
        JDA jda = jdaSupplier.get();
        if (api == null || jda == null) return;
        Set<String> activeSupportIds;
        try {
            activeSupportIds = api.activeBans().stream()
                    .map(BanInfo::supportId)
                    .filter(supportId -> supportId != null && !supportId.isBlank())
                    .map(supportId -> supportId.toUpperCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING,
                    "[PAC Discord] BAN 通知の状態確認に失敗しました", error);
            return;
        }

        long now = System.currentTimeMillis();
        for (PacSupportCaseStore.BanNoticeReference notice : store.banNotices()) {
            PacSupportCaseRecord appeal = store.bySupportId(notice.supportId());
            if (appeal != null && !appeal.status().name().equals(notice.appealStatus())) {
                updateBanNoticeStatus(notice.supportId(), null, appeal.status().name());
            }
            if (!"ACTIVE".equals(notice.banStatus())
                    || activeSupportIds.contains(notice.supportId().toUpperCase(Locale.ROOT))) continue;
            if (notice.permanent() || notice.expiresAt() > 0) {
                String status = !notice.permanent() && notice.expiresAt() <= now ? "EXPIRED" : "UNBANNED";
                updateBanNoticeStatus(notice.supportId(), status, null);
            } else {
                checkLegacyBanNoticeStatus(api, notice);
            }
        }
    }

    private void checkLegacyBanNoticeStatus(PacApi api, PacSupportCaseStore.BanNoticeReference notice) {
        String key = notice.supportId().toUpperCase(Locale.ROOT);
        if (!banNoticeChecksInFlight.add(key)) return;
        try {
            api.supportCase(notice.supportId()).whenComplete((supportCase, error) -> {
                banNoticeChecksInFlight.remove(key);
                if (error != null || supportCase == null || supportCase.isEmpty()) {
                    if (error != null) plugin.getLogger().log(Level.FINE,
                            "[PAC Discord] PAC BAN の期限を照会できませんでした: " + notice.supportId(), error);
                    return;
                }
                SupportCaseInfo details = supportCase.get();
                if (details.active()) return;
                String status = !details.permanent() && details.expiresAt() > 0
                        && details.expiresAt() <= System.currentTimeMillis() ? "EXPIRED" : "UNBANNED";
                updateBanNoticeStatus(notice.supportId(), status, null);
            });
        } catch (RuntimeException error) {
            banNoticeChecksInFlight.remove(key);
            plugin.getLogger().log(Level.FINE,
                    "[PAC Discord] PAC BAN の期限を照会できませんでした: " + notice.supportId(), error);
        }
    }

    private void updateBanNoticeStatus(String supportId, String banStatus, String appealStatus) {
        PacSupportCaseStore.BanNoticeReference notice = store.banNotice(supportId);
        JDA jda = jdaSupplier.get();
        TextChannel channel = jda == null || notice == null ? null : jda.getTextChannelById(notice.channelId());
        if (notice == null || channel == null) return;
        channel.retrieveMessageById(notice.messageId()).queue(message -> {
            MessageEmbed original = message.getEmbeds().stream()
                    .filter(embed -> "PAC BAN 通知".equals(embed.getTitle()))
                    .findFirst().orElse(null);
            if (original == null) return;
            MessageEmbed updated = withBanNoticeStatuses(original,
                    banStatus == null ? notice.banStatus() : banStatus,
                    appealStatus == null ? notice.appealStatus() : appealStatus);
            message.editMessageEmbeds(updated).queue(
                    success -> {
                        if (!store.updateBanNoticeStatus(supportId, banStatus, appealStatus)) {
                            plugin.getLogger().warning("[PAC Discord] BAN 通知の状態を保存できませんでした: "
                                    + supportId);
                        }
                    },
                    error -> plugin.getLogger().log(Level.WARNING,
                            "[PAC Discord] BAN 通知の状態を更新できませんでした: " + error.getMessage()));
        }, error -> plugin.getLogger().log(Level.FINE,
                "[PAC Discord] BAN 通知を取得できませんでした: " + error.getMessage()));
    }

    private void updateBanNoticeAndNotifyStaff(PacSupportCaseRecord record) {
        DiscordBotSettings settings = settingsSupplier.get();
        JDA jda = jdaSupplier.get();
        if (settings == null || jda == null) return;
        TextChannel staffChannel = jda.getTextChannelById(settings.getPacAppealInboxChannelId());
        if (staffChannel == null) {
            plugin.getLogger().warning("[PAC Discord] PAC 異議申し立て通知用チャンネルが見つかりません");
            return;
        }

        PacSupportCaseStore.BanNoticeReference notice = store.banNotice(record.supportId());
        if (notice != null && staffChannel.getId().equals(notice.channelId())) {
            staffChannel.retrieveMessageById(notice.messageId()).queue(
                    message -> addAppealButton(message, record),
                    error -> findAndUpdateBanNotice(staffChannel, record));
        } else {
            findAndUpdateBanNotice(staffChannel, record);
        }

        MessageEmbed notification = new EmbedBuilder()
                .setTitle("PAC BAN 異議申し立てを受け付けました")
                .setColor(0xF39C12)
                .addField("プレイヤー", truncate(record.playerName(), 100), true)
                .addField("PAC サポート ID", record.supportId(), true)
                .addField("申立者", "<@" + record.discordUserId() + "> (" + record.discordUserName() + ")", true)
                .addField("申立て内容", truncate(record.appealReason(), 1000), false)
                .addField("状態", record.status().displayName(), true)
                .addField("専用チャンネル", "<#" + record.channelId() + ">", true)
                .setTimestamp(Instant.ofEpochMilli(record.createdAt()))
                .build();
        String roleMentions = settings.getPacSupportStaffRoleIds().stream()
                .map(roleId -> "<@&" + roleId + ">")
                .collect(java.util.stream.Collectors.joining(" "));
        staffChannel.sendMessage(roleMentions.isBlank() ? "PAC スタッフへ通知" : roleMentions)
                .setAllowedMentions(List.of(Message.MentionType.ROLE))
                .mentionRoles(settings.getPacSupportStaffRoleIds())
                .setEmbeds(notification)
                .setComponents(ActionRow.of(Button.primary(
                        BAN_APPEAL_BUTTON_PREFIX + record.supportId(), "📨 申立てを確認")))
                .queue(message -> { }, error -> plugin.getLogger().log(Level.WARNING,
                        "[PAC Discord] 異議申し立てのスタッフ通知に失敗しました: " + error.getMessage()));
    }

    private void findAndUpdateBanNotice(TextChannel staffChannel, PacSupportCaseRecord record) {
        staffChannel.getHistory().retrievePast(100).queue(messages -> {
            for (Message message : messages) {
                if (hasBanNoticeSupportId(message, record.supportId())) {
                    store.rememberBanNotice(record.supportId(), staffChannel.getId(), message.getId(), 0L, false);
                    addAppealButton(message, record);
                    return;
                }
            }
            plugin.getLogger().info("[PAC Discord] 対応する既存 BAN Embed が直近100件にないため、スタッフ通知のみ投稿します: "
                    + record.supportId());
        }, error -> plugin.getLogger().log(Level.WARNING,
                "[PAC Discord] 既存 BAN Embed を検索できませんでした: " + error.getMessage()));
    }

    private boolean hasBanNoticeSupportId(Message message, String supportId) {
        for (MessageEmbed embed : message.getEmbeds()) {
            if (!"PAC BAN 通知".equals(embed.getTitle())) continue;
            for (MessageEmbed.Field field : embed.getFields()) {
                if ("PAC サポート ID".equals(field.getName())
                        && supportId.equalsIgnoreCase(field.getValue())) return true;
            }
        }
        return false;
    }

    private void addAppealButton(Message message, PacSupportCaseRecord record) {
        PacSupportCaseStore.BanNoticeReference notice = store.banNotice(record.supportId());
        String banStatus = notice == null ? "ACTIVE" : notice.banStatus();
        if (message.getEmbeds().isEmpty()) return;
        message.editMessageEmbeds(withBanNoticeStatuses(message.getEmbeds().getFirst(),
                        banStatus, record.status().name()))
                .setComponents(ActionRow.of(
                        Button.secondary("pac_ban_details:" + record.playerId(), "📊 直近ログ・検知数値"),
                        Button.primary(BAN_APPEAL_BUTTON_PREFIX + record.supportId(), "📨 申立て受付済み")))
                .queue(success -> {
                    if (!store.updateBanNoticeStatus(record.supportId(), null, record.status().name())) {
                        plugin.getLogger().warning("[PAC Discord] BAN 通知の申立て状態を保存できませんでした: "
                                + record.supportId());
                    }
                }, error -> plugin.getLogger().log(Level.WARNING,
                        "[PAC Discord] BAN Embed に申立てボタンを追加できませんでした: " + error.getMessage()));
    }

    private MessageEmbed withBanNoticeStatuses(MessageEmbed original,
                                               String banStatus, String appealStatus) {
        EmbedBuilder embed = new EmbedBuilder(original);
        embed.clearFields();
        for (MessageEmbed.Field field : original.getFields()) {
            if ("BAN状態".equals(field.getName()) || "異議申し立て".equals(field.getName())) continue;
            embed.addField(field);
        }
        String currentBanStatus = banStatus == null ? "ACTIVE" : banStatus;
        String currentAppealStatus = appealStatus == null ? "NONE" : appealStatus;
        embed.addField("BAN状態", banStatusLabel(currentBanStatus), true)
                .addField("異議申し立て", appealStatusLabel(currentAppealStatus), true)
                .setColor("EXPIRED".equals(currentBanStatus) ? 0x95A5A6
                        : "UNBANNED".equals(currentBanStatus) ? 0x57F287 : 0xED4245);
        return embed.build();
    }

    private String banStatusLabel(String status) {
        return switch (status) {
            case "EXPIRED" -> "期限切れ";
            case "UNBANNED" -> "解除済み";
            default -> "有効";
        };
    }

    private String appealStatusLabel(String status) {
        return switch (status) {
            case "STARTED" -> "開始";
            case "RESOLVED" -> "解決";
            case "CLOSED" -> "終了";
            default -> "なし";
        };
    }

    void handleAppealNoticeButton(ButtonInteractionEvent event, String supportId) {
        if (!requireStaff(event, true)) return;
        PacSupportCaseRecord record = store.bySupportId(supportId);
        if (record == null) {
            event.reply("この PAC サポート ID の異議申し立て履歴は見つかりません")
                    .setEphemeral(true).queue();
            return;
        }
        MessageEmbed summary = new EmbedBuilder()
                .setTitle("PAC 異議申し立て · " + record.status().displayName())
                .setColor(record.status() == PacSupportCaseStatus.STARTED ? 0xF39C12 : 0x57F287)
                .addField("プレイヤー", record.playerName(), true)
                .addField("UUID", record.playerId(), true)
                .addField("PAC サポート ID", record.supportId(), true)
                .addField("申立者", "<@" + record.discordUserId() + "> (" + record.discordUserName() + ")", true)
                .addField("申立て内容", truncate(record.appealReason(), 1000), false)
                .addField("専用チャンネル", record.status() == PacSupportCaseStatus.STARTED
                        ? "<#" + record.channelId() + ">" : "案件は終了済みです。固定管理パネルから履歴を確認してください", false)
                .setTimestamp(Instant.ofEpochMilli(record.createdAt()))
                .build();
        event.replyEmbeds(summary).setEphemeral(true).queue();
    }

    private void failNewCase(TextChannel channel, String supportId, InteractionHook hook, String message) {
        store.releaseReservation(supportId);
        channel.delete().queue(
                success -> { },
                error -> plugin.getLogger().log(Level.WARNING,
                        "[PAC Discord] 失敗したサポートチャンネルを削除できませんでした: " + error.getMessage()));
        hook.editOriginal(message).queue();
    }

    private String supportChannelName(String playerName, String supportId) {
        String player = playerName == null ? "player" : playerName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        if (player.isBlank()) player = "player";
        String id = supportId == null ? "appeal" : supportId.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "-");
        String name = "pac-" + player + "-" + id;
        return name.length() <= 90 ? name : name.substring(0, 90);
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (DASHBOARD_HISTORY_BUTTON.equals(componentId)) {
            openHistorySearch(event);
            return;
        }
        if (DASHBOARD_DETECTIONS_BUTTON.equals(componentId)) {
            openDetectionSearch(event);
            return;
        }
        if (DASHBOARD_BANS_BUTTON.equals(componentId)) {
            if (!requireStaff(event, true)) return;
            event.deferReply(true).queue(hook -> showBanList(hook, 0));
            return;
        }
        if (componentId.startsWith(STAFF_CASE_LOG_PREFIX)) {
            showCaseHistoryFromDashboard(event,
                    componentId.substring(STAFF_CASE_LOG_PREFIX.length()), 0, false);
            return;
        }
        if (componentId.startsWith(STAFF_CASE_LOG_PAGE_PREFIX)) {
            String[] parts = componentId.substring(STAFF_CASE_LOG_PAGE_PREFIX.length()).split(":", 2);
            if (parts.length != 2) return;
            int page = parsePage(parts[1]);
            showCaseHistoryFromDashboard(event, parts[0], page, true);
            return;
        }
        if (componentId.startsWith(STAFF_BAN_PAGE_PREFIX)) {
            if (!requireStaff(event, false)) return;
            int page = parsePage(componentId.substring(STAFF_BAN_PAGE_PREFIX.length()));
            event.deferEdit().queue(hook -> showBanList(hook, page));
            return;
        }
        if (!componentId.startsWith(CASE_PANEL_PREFIX)) return;
        handleCasePanelButton(event, componentId);
    }

    @Override
    public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
        if (HISTORY_SEARCH_MODAL.equals(event.getModalId())) {
            if (!requireStaff(event, true)) return;
            String query = getValue(event, HISTORY_SEARCH_INPUT);
            if (query.isBlank()) {
                event.reply("PAC サポート ID、UUID、Minecraft 名、または Discord ユーザー ID を入力してください")
                        .setEphemeral(true).queue();
                return;
            }
            event.deferReply(true).queue(hook -> showHistorySearchResults(hook, query));
            return;
        }
        if (DETECTION_SEARCH_MODAL.equals(event.getModalId())) {
            if (!requireStaff(event, true)) return;
            String identity = getValue(event, DETECTION_SEARCH_INPUT);
            if (identity.isBlank()) {
                event.reply("Minecraft UUID またはプレイヤー名を入力してください").setEphemeral(true).queue();
                return;
            }
            event.deferReply(true).queue(hook -> showDetectionHistory(hook, identity, "スタッフ管理パネル"));
            return;
        }
        if (event.getModalId().startsWith(UNBAN_MODAL_PREFIX)) {
            completeUnban(event);
            return;
        }
        if (event.getModalId().startsWith(FINISH_MODAL_PREFIX)) finishCase(event);
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;
        Message message = event.getMessage();
        StringBuilder details = new StringBuilder(message.getContentDisplay());
        for (var attachment : message.getAttachments()) {
            if (details.length() > 0) details.append('\n');
            details.append("添付: ").append(attachment.getFileName()).append(" ").append(attachment.getUrl());
        }
        if (details.isEmpty()) return;
        if (!store.recordMessage(event.getChannel().getId(), event.getAuthor().getId(),
                event.getAuthor().getName(), truncate(details.toString(), 1800),
                message.getTimeCreated().toInstant().toEpochMilli())) {
            PacSupportCaseRecord record = store.byChannelId(event.getChannel().getId());
            if (record != null && record.status() == PacSupportCaseStatus.STARTED) {
                plugin.getLogger().warning("[PAC Discord] サポート会話を履歴に保存できませんでした");
            }
        }
    }

    @Override
    public void onMessageUpdate(@Nonnull MessageUpdateEvent event) {
        if (!event.isFromGuild() || store.byChannelId(event.getChannel().getId()) == null) return;
        Message message = event.getMessage();
        if (message.getAuthor().isBot()) return;
        store.recordMessage(event.getChannel().getId(), event.getAuthor().getId(),
                event.getAuthor().getName(), "[編集後] " + truncate(message.getContentDisplay(), 1700),
                message.getTimeEdited() == null ? System.currentTimeMillis()
                        : message.getTimeEdited().toInstant().toEpochMilli());
    }

    @Override
    public void onMessageDelete(@Nonnull MessageDeleteEvent event) {
        if (!event.isFromGuild() || store.byChannelId(event.getChannel().getId()) == null) return;
        store.recordMessage(event.getChannel().getId(), "", "不明",
                "メッセージ削除: " + event.getMessageId(), System.currentTimeMillis());
    }

    private void openHistorySearch(ButtonInteractionEvent event) {
        if (!requireStaff(event, true)) return;
        TextInput input = TextInput.create(HISTORY_SEARCH_INPUT, TextInputStyle.SHORT)
                .setPlaceholder("recent / PAC ID / Minecraft UUID / Minecraft 名 / Discord ID")
                .setMaxLength(100)
                .setRequired(true)
                .build();
        event.replyModal(Modal.create(HISTORY_SEARCH_MODAL, "PAC サポート履歴")
                .addComponents(Label.of("検索するプレイヤーまたはケース", input))
                .build()).queue();
    }

    private void openDetectionSearch(ButtonInteractionEvent event) {
        if (!requireStaff(event, true)) return;
        TextInput input = TextInput.create(DETECTION_SEARCH_INPUT, TextInputStyle.SHORT)
                .setPlaceholder("Minecraft UUID またはプレイヤー名")
                .setMaxLength(100)
                .setRequired(true)
                .build();
        event.replyModal(Modal.create(DETECTION_SEARCH_MODAL, "PAC 検知ログ検索")
                .addComponents(Label.of("プレイヤー", input))
                .build()).queue();
    }

    private boolean requireStaff(ButtonInteractionEvent event, boolean requireDashboardChannel) {
        return requireStaff(event.getMember(), event.getChannel().getId(), event, requireDashboardChannel);
    }

    private boolean requireStaff(ModalInteractionEvent event, boolean requireDashboardChannel) {
        return requireStaff(event.getMember(), event.getChannel().getId(), event, requireDashboardChannel);
    }

    private boolean requireStaff(Member member, String channelId, ButtonInteractionEvent event,
                                 boolean requireDashboardChannel) {
        DiscordBotSettings settings = settingsSupplier.get();
        if (!isStaff(member, settings)) {
            event.reply("この操作は PAC スタッフ専用です").setEphemeral(true).queue();
            return false;
        }
        if (requireDashboardChannel && (settings == null
                || !settings.getPacAppealInboxChannelId().equals(channelId))) {
            event.reply("この管理パネルはスタッフ用チャンネルでのみ使用できます")
                    .setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    private boolean requireStaff(Member member, String channelId, ModalInteractionEvent event,
                                 boolean requireDashboardChannel) {
        DiscordBotSettings settings = settingsSupplier.get();
        if (!isStaff(member, settings)) {
            event.reply("この操作は PAC スタッフ専用です").setEphemeral(true).queue();
            return false;
        }
        if (requireDashboardChannel && (settings == null
                || !settings.getPacAppealInboxChannelId().equals(channelId))) {
            event.reply("この管理パネルはスタッフ用チャンネルでのみ使用できます")
                    .setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    private void handleCasePanelButton(ButtonInteractionEvent event, String componentId) {
        String supportId = caseSupportId(componentId);
        PacSupportCaseRecord record = store.byChannelId(event.getChannel().getId());
        if (record == null || !record.supportId().equalsIgnoreCase(supportId)) {
            event.reply("このサポート案件は終了済みか、履歴が見つかりません")
                    .setEphemeral(true).queue();
            return;
        }
        if (!isStaff(event.getMember(), settingsSupplier.get())) {
            event.reply("この管理パネルは PAC スタッフ専用です").setEphemeral(true).queue();
            return;
        }
        if (componentId.startsWith(CASE_HISTORY_BUTTON_PREFIX)) {
            showCaseHistory(event, record, 0, false);
        } else if (componentId.startsWith(CASE_DETECTIONS_BUTTON_PREFIX)) {
            showDetectionHistoryForCase(event, record);
        } else if (componentId.startsWith(CASE_BANS_BUTTON_PREFIX)) {
            event.deferReply(true).queue(hook -> showBanList(hook, 0));
        } else if (componentId.startsWith(CASE_UNBAN_BUTTON_PREFIX)) {
            unbanFromCase(event, record);
        } else if (componentId.startsWith(CASE_RESOLVE_BUTTON_PREFIX)) {
            openFinishModal(event, record, PacSupportCaseStatus.RESOLVED);
        } else if (componentId.startsWith(CASE_CLOSE_BUTTON_PREFIX)) {
            openFinishModal(event, record, PacSupportCaseStatus.CLOSED);
        }
    }

    private String caseSupportId(String componentId) {
        int separator = componentId.indexOf(':');
        return separator < 0 ? "" : componentId.substring(separator + 1);
    }

    private void showCaseHistoryFromDashboard(ButtonInteractionEvent event, String supportId,
                                              int page, boolean editOriginal) {
        if (!requireStaff(event, false)) return;
        PacSupportCaseRecord record = store.bySupportId(supportId);
        if (record == null) {
            event.reply("サポート履歴が見つかりません").setEphemeral(true).queue();
            return;
        }
        showCaseHistory(event, record, page, editOriginal);
    }

    private void showCaseHistory(ButtonInteractionEvent event, PacSupportCaseRecord record,
                                 int page, boolean editOriginal) {
        List<PacSupportCaseLog> logs = record.logs();
        int pages = Math.max(1, (logs.size() + HISTORY_PAGE_SIZE - 1) / HISTORY_PAGE_SIZE);
        int selected = Math.max(0, Math.min(page, pages - 1));
        MessageEmbed embed = caseHistoryEmbed(record, logs, selected, pages);
        List<ActionRow> components = caseHistoryComponents(record.supportId(), selected, pages);
        if (editOriginal) {
            event.deferEdit().queue(hook -> hook.editOriginalEmbeds(embed).setComponents(components).queue());
        } else {
            event.replyEmbeds(embed).addComponents(components).setEphemeral(true).queue();
        }
    }

    private MessageEmbed caseHistoryEmbed(PacSupportCaseRecord record,
                                          List<PacSupportCaseLog> logs, int page, int pageCount) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("PAC サポート履歴 · " + record.playerName())
                .setColor(record.status() == PacSupportCaseStatus.STARTED ? 0xF39C12 : 0x57F287)
                .setDescription("PAC ID: `" + record.supportId() + "` | 状態: **" + record.status().displayName()
                        + "**\nUUID: `" + record.playerId() + "`\n申立者: <@" + record.discordUserId() + ">\n"
                        + "作成: " + discordTimestamp(record.createdAt(), "F")
                        + "\n履歴 " + logs.size() + " 件 | ページ " + (page + 1) + "/" + pageCount)
                .setTimestamp(Instant.ofEpochMilli(record.updatedAt()));
        int start = page * HISTORY_PAGE_SIZE;
        int end = Math.min(start + HISTORY_PAGE_SIZE, logs.size());
        if (logs.isEmpty()) {
            embed.addField("履歴", "記録された会話・操作はありません。", false);
        } else {
            for (int index = start; index < end; index++) {
                PacSupportCaseLog log = logs.get(index);
                String actor = log.actorName().isBlank() ? "システム" : log.actorName();
                String name = truncate(log.action() + " · " + actor + " · "
                        + discordTimestamp(log.timestamp(), "R"), 256);
                embed.addField(name, truncate(log.details(), 900), false);
            }
        }
        return embed.setFooter("開始後の申立て・会話・スタッフ操作を保存しています").build();
    }

    private List<ActionRow> caseHistoryComponents(String supportId, int page, int pageCount) {
        if (pageCount <= 1) return List.of();
        return List.of(ActionRow.of(
                Button.secondary(STAFF_CASE_LOG_PAGE_PREFIX + supportId + ":" + Math.max(0, page - 1), "◀ 前へ")
                        .withDisabled(page <= 0),
                Button.secondary(STAFF_CASE_LOG_PAGE_PREFIX + supportId + ":" + Math.min(pageCount - 1, page + 1), "次へ ▶")
                        .withDisabled(page >= pageCount - 1)));
    }

    private void showHistorySearchResults(InteractionHook hook, String query) {
        List<PacSupportCaseRecord> matches = store.search(query, HISTORY_SEARCH_LIMIT);
        if (matches.isEmpty()) {
            hook.editOriginal("一致するPACサポート履歴はありません").queue();
            return;
        }
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("PAC サポート履歴検索")
                .setDescription("検索: `" + truncate(query, 80) + "` | 最大 " + HISTORY_SEARCH_LIMIT + " 件")
                .setColor(0x5865F2)
                .setTimestamp(Instant.now());
        List<Button> buttons = new ArrayList<>();
        for (PacSupportCaseRecord record : matches) {
            List<PacSupportCaseLog> logs = record.logs();
            PacSupportCaseLog latest = logs.isEmpty() ? null : logs.get(logs.size() - 1);
            String value = "状態: **" + record.status().displayName() + "** | PAC ID: `" + record.supportId()
                    + "`\nUUID: `" + record.playerId() + "` | 申立者: <@" + record.discordUserId() + ">"
                    + "\n申立て: " + truncate(record.appealReason(), 280)
                    + (latest == null ? "" : "\n最終履歴: " + truncate(latest.action() + " / " + latest.details(), 220));
            embed.addField(truncate(record.playerName() + " · " + discordTimestamp(record.createdAt(), "d"), 256),
                    truncate(value, 900), false);
            buttons.add(Button.secondary(STAFF_CASE_LOG_PREFIX + record.supportId(),
                    truncate("ログ: " + record.playerName(), 80)));
        }
        hook.editOriginalEmbeds(embed.build())
                .setComponents(List.of(ActionRow.of(buttons)))
                .queue();
    }

    private void openFinishModal(ButtonInteractionEvent event, PacSupportCaseRecord record,
                                 PacSupportCaseStatus next) {
        if (record.status() != PacSupportCaseStatus.STARTED) {
            event.reply("この案件はすでに解決または終了しています").setEphemeral(true).queue();
            return;
        }
        String modalId = FINISH_MODAL_PREFIX + next.name() + ":" + record.supportId();
        TextInput note = TextInput.create(FINISH_NOTE_INPUT, TextInputStyle.PARAGRAPH)
                .setPlaceholder(next == PacSupportCaseStatus.RESOLVED
                        ? "対応内容と解決理由を記録してください" : "終了理由を記録してください")
                .setMinLength(3)
                .setMaxLength(1000)
                .setRequired(true)
                .build();
        event.replyModal(Modal.create(modalId,
                next == PacSupportCaseStatus.RESOLVED ? "異議申し立てを解決" : "異議申し立てを終了")
                .addComponents(Label.of("対応内容 (履歴に保存)", note))
                .build()).queue();
    }

    private void finishCase(ModalInteractionEvent event) {
        String[] parts = event.getModalId().substring(FINISH_MODAL_PREFIX.length()).split(":", 2);
        if (parts.length != 2 || !requireStaff(event, false)) return;
        PacSupportCaseStatus next;
        try {
            next = PacSupportCaseStatus.valueOf(parts[0]);
        } catch (IllegalArgumentException error) {
            event.reply("状態を特定できませんでした").setEphemeral(true).queue();
            return;
        }
        if (next == PacSupportCaseStatus.STARTED) {
            event.reply("無効な状態です").setEphemeral(true).queue();
            return;
        }
        PacSupportCaseRecord record = store.byChannelId(event.getChannel().getId());
        if (record == null || !record.supportId().equalsIgnoreCase(parts[1])) {
            event.reply("このサポート案件はすでに終了しています").setEphemeral(true).queue();
            return;
        }
        String note = getValue(event, FINISH_NOTE_INPUT);
        if (note.length() < 3) {
            event.reply("解決または終了の内容を3文字以上で記載してください").setEphemeral(true).queue();
            return;
        }
        boolean saved = store.transition(record.supportId(), next,
                event.getUser().getId(), event.getUser().getName(), note);
        if (!saved) {
            event.reply("案件状態を保存できませんでした。状態が変更済みでないか確認してください")
                    .setEphemeral(true).queue();
            return;
        }
        updateBanNoticeStatus(record.supportId(), null, next.name());
        event.deferReply(true).queue(hook -> {
            hook.editOriginal("✅ ケースを「" + next.displayName() + "」にしました。記録はスタッフ履歴に保存されています")
                    .queue();
            archiveCaseChannel((TextChannel) event.getChannel(), record, next, note);
        });
    }

    private void archiveCaseChannel(TextChannel channel, PacSupportCaseRecord record,
                                    PacSupportCaseStatus status, String note) {
        MessageEmbed finalPanel = casePanelEmbed(record.supportId(), record.playerId(), record.playerName(),
                record.appealReason(), status, record.createdAt(), note);
        channel.retrieveMessageById(record.panelMessageId()).queue(
                message -> message.editMessageEmbeds(finalPanel).setComponents(List.of()).queue(),
                error -> plugin.getLogger().log(Level.FINE,
                        "[PAC Discord] 完了済み案件のパネルを更新できませんでした: " + error.getMessage()));
        channel.getManager().setTopic("PAC Support ID: " + record.supportId() + " | status: " + status.name())
                .queue(success -> { }, error -> plugin.getLogger().log(Level.FINE,
                        "[PAC Discord] 完了済み案件のチャンネルトピックを更新できませんでした: " + error.getMessage()));
        channel.sendMessage("案件は **" + status.displayName() + "** になりました。\n対応記録: " + note
                        + "\nこの一時チャンネルは30秒後に削除され、スタッフ管理パネルから履歴を確認できます.")
                .queue(message -> channel.delete().queueAfter(30, TimeUnit.SECONDS,
                                success -> { },
                                error -> plugin.getLogger().log(Level.WARNING,
                                        "[PAC Discord] 完了済みサポートチャンネルを削除できませんでした: " + error.getMessage())),
                        error -> plugin.getLogger().log(Level.WARNING,
                                "[PAC Discord] 完了通知を投稿できませんでした: " + error.getMessage()));
    }

    private MessageEmbed casePanelEmbed(String supportId, String playerId, String playerName,
                                        String appealReason, PacSupportCaseStatus status,
                                        long createdAt, String finalNote) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("PAC BAN 異議申し立て · " + status.displayName())
                .setColor(status == PacSupportCaseStatus.STARTED ? 0xF39C12 : 0x57F287)
                .addField("プレイヤー", playerName, true)
                .addField("PAC サポート ID", supportId, true)
                .addField("UUID", playerId, false)
                .addField("申立て内容", truncate(appealReason, 1000), false)
                .addField("状態", status.displayName(), true)
                .addField("開始", discordTimestamp(createdAt, "F"), true)
                .setFooter("スタッフの対応をお待ちください")
                .setTimestamp(Instant.ofEpochMilli(createdAt));
        if (finalNote != null && !finalNote.isBlank()) {
            embed.addField(status.displayName() + "の記録", truncate(finalNote, 1000), false);
        }
        return embed.build();
    }

    private List<ActionRow> casePanelComponents(String supportId, boolean started) {
        if (!started) return List.of();
        return List.of(
                ActionRow.of(
                        Button.secondary(CASE_HISTORY_BUTTON_PREFIX + supportId, "📚 履歴"),
                        Button.secondary(CASE_DETECTIONS_BUTTON_PREFIX + supportId, "🔎 PAC検知ログ"),
                        Button.secondary(CASE_BANS_BUTTON_PREFIX + supportId, "📋 BAN一覧"),
                        Button.danger(CASE_UNBAN_BUTTON_PREFIX + supportId, "BAN解除")),
                ActionRow.of(
                        Button.success(CASE_RESOLVE_BUTTON_PREFIX + supportId, "解決"),
                        Button.secondary(CASE_CLOSE_BUTTON_PREFIX + supportId, "終了")));
    }

    private void unbanFromCase(ButtonInteractionEvent event, PacSupportCaseRecord record) {
        if (record.status() != PacSupportCaseStatus.STARTED) {
            event.reply("開始中の案件でのみ BAN を解除できます").setEphemeral(true).queue();
            return;
        }
        TextInput note = TextInput.create(UNBAN_NOTE_INPUT, TextInputStyle.PARAGRAPH)
                .setPlaceholder("解除の判断理由を記録してください")
                .setMinLength(3)
                .setMaxLength(1000)
                .setRequired(true)
                .build();
        event.replyModal(Modal.create(UNBAN_MODAL_PREFIX + record.supportId(), "PAC BAN を解除")
                .addComponents(Label.of("解除理由 (履歴に保存)", note))
                .build()).queue();
    }

    private void completeUnban(ModalInteractionEvent event) {
        String supportId = event.getModalId().substring(UNBAN_MODAL_PREFIX.length());
        if (!requireStaff(event, false)) return;
        PacSupportCaseRecord record = store.byChannelId(event.getChannel().getId());
        if (record == null || !record.supportId().equalsIgnoreCase(supportId)
                || record.status() != PacSupportCaseStatus.STARTED) {
            event.reply("開始中のサポート案件が見つかりません").setEphemeral(true).queue();
            return;
        }
        String reason = getValue(event, UNBAN_NOTE_INPUT);
        if (reason.length() < 3) {
            event.reply("解除理由を3文字以上で記載してください").setEphemeral(true).queue();
            return;
        }
        PacApi api = apiSupplier.get();
        if (api == null) {
            event.reply("PAC is not available; PAC連携機能は停止しています").setEphemeral(true).queue();
            return;
        }
        if (!api.controlAuthorityEnabled()) {
            event.reply(API_CONTROL_AUTHORITY_DISABLED_MESSAGE)
                    .setEphemeral(true).queue();
            return;
        }
        unbanFromCase(event, record, reason);
    }

    private void unbanFromCase(ModalInteractionEvent event, PacSupportCaseRecord record, String reason) {
        PacApi api = apiSupplier.get();
        if (api == null) {
            event.reply("PAC is not available; PAC連携機能は停止しています").setEphemeral(true).queue();
            return;
        }
        if (!api.controlAuthorityEnabled()) {
            event.reply(API_CONTROL_AUTHORITY_DISABLED_MESSAGE)
                    .setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue(hook -> {
            try {
                api.unban(java.util.UUID.fromString(record.playerId())).whenComplete((removed, error) -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.WARNING, "[PAC Discord] PAC BAN 解除に失敗しました", error);
                        boolean logged = store.appendAction(record.supportId(), event.getUser().getId(),
                                event.getUser().getName(), "UNBAN_FAILED", reason + "\n失敗理由: " + safeError(error));
                        hook.editOriginal("PAC BAN を解除できませんでした: " + safeError(error)
                                + (logged ? "" : "\n解除失敗の履歴も保存できませんでした。サーバーログを確認してください"))
                                .queue();
                        return;
                    }
                    if (Boolean.TRUE.equals(removed)) {
                        updateBanNoticeStatus(record.supportId(), "UNBANNED", null);
                        boolean logged = store.appendAction(record.supportId(), event.getUser().getId(),
                                event.getUser().getName(), "UNBAN", reason);
                        if (!logged) {
                            plugin.getLogger().warning("[PAC Discord] PAC BAN 解除の履歴保存に失敗しました");
                        }
                        event.getChannel().sendMessage("✅ PAC BAN を解除しました (操作スタッフ: <@"
                                + event.getUser().getId() + ">)").queue();
                        hook.editOriginal(logged
                                ? "✅ PAC BAN を解除しました。操作記録をサポート履歴に保存しました"
                                : "✅ PAC BAN は解除しましたが、操作記録を保存できませんでした。サーバーログを確認してください")
                                .queue();
                    } else {
                        boolean logged = store.appendAction(record.supportId(), event.getUser().getId(),
                                event.getUser().getName(), "UNBAN_NO_ACTIVE_BAN",
                                reason + "\n解除対象の有効な BAN はありませんでした");
                        if (!logged) {
                            plugin.getLogger().warning("[PAC Discord] PAC BAN 確認操作の履歴保存に失敗しました");
                        }
                        hook.editOriginal("このプレイヤーに有効な PAC BAN はありません").queue();
                    }
                });
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING, "[PAC Discord] PAC BAN 解除に失敗しました", error);
                hook.editOriginal("PAC BAN を解除できませんでした: " + safeError(error)).queue();
            }
        });
    }

    private void showDetectionHistoryForCase(ButtonInteractionEvent event, PacSupportCaseRecord record) {
        PacApi api = apiSupplier.get();
        if (api == null) {
            event.reply("PAC is not available; PAC連携機能は停止しています").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue(hook -> showDetectionHistory(hook, record.playerId(), record.playerName()));
    }

    private void showDetectionHistory(InteractionHook hook, String identity, String label) {
        PacApi api = apiSupplier.get();
        if (api == null) {
            hook.editOriginal("PAC is not available; PAC連携機能は停止しています").queue();
            return;
        }
        try {
            api.detectionHistory(identity, 1, 6).whenComplete((history, error) -> {
                if (error != null || history == null) {
                    if (error != null) plugin.getLogger().log(Level.WARNING,
                            "[PAC Discord] PAC 検知ログを取得できませんでした", error);
                    hook.editOriginal("PAC 検知ログを取得できませんでした").queue();
                    return;
                }
                hook.editOriginalEmbeds(detectionHistoryEmbed(label, history)).queue();
            });
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "[PAC Discord] PAC 検知ログを取得できませんでした", error);
            hook.editOriginal("PAC 検知ログを取得できませんでした").queue();
        }
    }

    private MessageEmbed detectionHistoryEmbed(String label, DetectionHistoryPage history) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("PAC 検知ログ · " + truncate(label, 128))
                .setDescription("直近 " + history.records().size() + " 件 / 記録合計 " + history.total() + " 件")
                .setColor(0xED4245)
                .setTimestamp(Instant.now());
        if (history.records().isEmpty()) {
            embed.addField("検知", "このプレイヤーの検知ログはありません。", false);
        } else {
            for (DetectionRecord record : history.records()) {
                String name = "#" + record.id() + " · " + truncate(record.detectorKey(), 80)
                        + " · score " + record.score();
                String value = "時刻: " + discordTimestamp(record.createdAt(), "F")
                        + " (" + discordTimestamp(record.createdAt(), "R") + ")"
                        + "\n詳細: " + truncateSingleLine(record.detail(), 250)
                        + "\n数値: " + formatMetrics(record.metricsJson(), 300);
                embed.addField(truncate(name, 256), truncate(value, 800), false);
            }
        }
        return embed.setFooter("PAC の検知履歴を表示しています").build();
    }

    private void showBanList(InteractionHook hook, int requestedPage) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            PacApi api = apiSupplier.get();
            if (api == null) {
                hook.editOriginal("PAC is not available; PAC連携機能は停止しています").queue();
                return;
            }
            try {
                List<BanInfo> bans = api.activeBans().stream()
                        .sorted(Comparator.comparingLong(BanInfo::createdAt).reversed())
                        .toList();
                int pageCount = Math.max(1, (bans.size() + BAN_PAGE_SIZE - 1) / BAN_PAGE_SIZE);
                int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
                MessageEmbed embed = banListEmbed(bans, page, pageCount);
                List<ActionRow> components = pageCount <= 1 ? List.of() : List.of(ActionRow.of(
                        Button.secondary(STAFF_BAN_PAGE_PREFIX + (page - 1), "◀ 前へ")
                                .withDisabled(page <= 0),
                        Button.secondary(STAFF_BAN_PAGE_PREFIX + (page + 1), "次へ ▶")
                                .withDisabled(page >= pageCount - 1)));
                hook.editOriginalEmbeds(embed).setComponents(components).queue();
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING, "[PAC Discord] BAN 一覧を取得できませんでした", error);
                hook.editOriginal("PAC BAN 一覧を取得できませんでした").queue();
            }
        });
    }

    private MessageEmbed banListEmbed(List<BanInfo> bans, int page, int pageCount) {
        StringBuilder description = new StringBuilder();
        int start = page * BAN_PAGE_SIZE;
        int end = Math.min(start + BAN_PAGE_SIZE, bans.size());
        if (bans.isEmpty()) {
            description.append("現在、PAC BAN 中のプレイヤーはいません。");
        } else {
            for (int index = start; index < end; index++) {
                BanInfo ban = bans.get(index);
                description.append("• **").append(truncate(ban.playerName(), 64)).append("** — ")
                        .append(ban.permanent() ? "永久" : discordTimestamp(ban.expiresAt(), "R"))
                        .append("\nUUID: `").append(ban.playerId()).append("` | 理由: ")
                        .append(truncateSingleLine(ban.reason(), 100)).append("\n\n");
            }
        }
        return new EmbedBuilder()
                .setTitle("PAC 現在の BAN 一覧 (" + (page + 1) + "/" + pageCount + ")")
                .setDescription(description.toString())
                .setColor(0xED4245)
                .setFooter("現在 BAN 中: " + bans.size() + " 人")
                .setTimestamp(Instant.now())
                .build();
    }

    private boolean isStaff(Member member, DiscordBotSettings settings) {
        if (member == null) return false;
        if (member.isOwner() || member.hasPermission(Permission.ADMINISTRATOR)) return true;
        if (settings == null || settings.getPacSupportStaffRoleIds().isEmpty()) return false;
        Set<String> allowedRoles = Set.copyOf(settings.getPacSupportStaffRoleIds());
        String publicRoleId = member.getGuild().getPublicRole().getId();
        return member.getRoles().stream()
                .anyMatch(role -> !role.isPublicRole() && !role.getId().equals(publicRoleId)
                        && allowedRoles.contains(role.getId()));
    }

    private boolean areStaffRolesAvailable(Guild guild, DiscordBotSettings settings) {
        return settings != null && !settings.getPacSupportStaffRoleIds().isEmpty()
                && settings.getPacSupportStaffRoleIds().stream().allMatch(roleId -> {
                    Role role = guild.getRoleById(roleId);
                    return role != null && !role.isPublicRole();
                });
    }

    private void cleanupClosedCaseChannels() {
        JDA jda = jdaSupplier.get();
        if (jda == null) return;
        long cutoff = System.currentTimeMillis() - CLOSED_CHANNEL_RETENTION_MILLIS;
        for (PacSupportCaseRecord record : store.recent(Integer.MAX_VALUE)) {
            if (record.status() == PacSupportCaseStatus.STARTED || record.updatedAt() > cutoff) continue;
            Guild guild = jda.getGuildById(record.guildId());
            TextChannel channel = guild == null ? null : guild.getTextChannelById(record.channelId());
            if (channel != null) {
                channel.delete().queue(
                        success -> { },
                        error -> plugin.getLogger().log(Level.FINE,
                                "[PAC Discord] 期限切れサポートチャンネルを削除できませんでした: " + error.getMessage()));
            }
        }
    }

    private String formatMetrics(String metricsJson, int maxLength) {
        if (metricsJson == null || metricsJson.isBlank()) return "数値データなし";
        try {
            JsonElement parsed = JsonParser.parseString(metricsJson);
            if (!parsed.isJsonObject()) return truncate(metricsJson, maxLength);
            StringBuilder formatted = new StringBuilder("`");
            for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) continue;
                if (formatted.length() > 1) formatted.append(" | ");
                formatted.append(entry.getKey()).append('=').append(value.getAsString());
            }
            if (formatted.length() == 1) return "数値データなし";
            return truncate(formatted.append('`').toString(), maxLength);
        } catch (RuntimeException error) {
            return truncate(metricsJson, maxLength);
        }
    }

    private String getValue(ModalInteractionEvent event, String id) {
        ModalMapping mapping = event.getValue(id);
        return mapping == null ? "" : mapping.getAsString().trim();
    }

    private int parsePage(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private String discordTimestamp(long epochMillis, String style) {
        return "<t:" + Math.floorDiv(epochMillis, 1000L) + ":" + style + ">";
    }

    private String truncateSingleLine(String value, int maxLength) {
        if (value == null || value.isBlank()) return "理由未設定";
        return truncate(value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim(), maxLength);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) return "不明";
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }

    private String safeError(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return truncate(root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage(), 400);
    }

}

