package org.pexserver.koukunn.bettersurvival.Modules.Feature.Tournament;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.DuelSession;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.RingModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.RingRegion;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Tournament.ui.TournamentMenu;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.time.Duration;
import java.util.Random;
import java.util.UUID;

/**
 * 「頂点決定戦」トーナメントモジュール。
 *
 * 参加受付 → シード抽選 → シングルエリミネーション式ブラケットを組み、
 * リングモジュールの Duel を使って試合を順番に自動消化する。
 * 呼び出された試合に出てこないプレイヤーは猶予時間経過後に不戦敗となる。
 * 優勝者にはタイトル・花火・全体アナウンスの優勝演出が入る。
 * 大会は同時に 1 つまで。状態は JSON に永続化され再起動をまたいで継続できる。
 */
public class TournamentModule implements Listener {

    public static final String FEATURE_KEY = "tournament";

    private static final long TICK_PERIOD_TICKS = 20L;
    /** 試合呼び出しから不戦敗が確定するまでの猶予 (ms) */
    private static final long CALL_GRACE_MILLIS = 60_000L;
    /** 試合間のインターバル (ms)。勝者の帰還テレポート(70tick)より長くとる */
    private static final long MATCH_INTERVAL_MILLIS = 8_000L;
    private static final String PREFIX = "§6§l[頂点決定戦] §r";

    private final Loader plugin;
    private final ToggleModule toggle;
    private final RingModule ringModule;
    private final TournamentStore store;
    private final TournamentMenu menu;
    private final Random random = new Random();
    private final BukkitTask tickTask;

    /** 現在のトーナメント（開催していなければ null。FINISHED は次の大会作成まで残す） */
    private Tournament tournament;
    /** 次の進行アクションを行ってよい時刻 (ms) */
    private long nextActionAt;
    /** リング空き待ちアナウンスのクールダウン */
    private long lastBusyNotice;

    public TournamentModule(Loader plugin, ToggleModule toggle, RingModule ringModule) {
        this.plugin = plugin;
        this.toggle = toggle;
        this.ringModule = ringModule;
        this.store = new TournamentStore(plugin.getConfigManager());
        this.tournament = store.load();
        if (tournament != null && tournament.getState() == Tournament.State.RUNNING) {
            // 再起動をまたいだ場合、進行中だった試合は PENDING に戻して再開する
            ringModule.lockRing(tournament.getRingClaimKey());
        }
        this.menu = new TournamentMenu(plugin, this);
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick,
                TICK_PERIOD_TICKS, TICK_PERIOD_TICKS);
    }

    public void shutdown() {
        tickTask.cancel();
        save();
    }

    public boolean isFeatureEnabled() {
        return toggle.getGlobal(FEATURE_KEY);
    }

    public TournamentMenu getMenu() {
        return menu;
    }

    public RingModule getRingModule() {
        return ringModule;
    }

    public Tournament getTournament() {
        return tournament;
    }

    private void save() {
        store.save(tournament);
    }

    private void broadcast(String message) {
        Bukkit.broadcast(ComponentUtils.legacy(PREFIX + message));
    }

    // ================= 大会の作成 / 受付 =================

    /** 大会を作成して参加受付を開始する。失敗時はエラーメッセージ、成功時は null。 */
    public String createTournament(String name, RingRegion ring) {
        if (tournament != null && tournament.getState() != Tournament.State.FINISHED) {
            return "既に大会が開催中です（中止するか終了を待ってください）";
        }
        if (!ringModule.isRingActive(ring) || ring.getWorld() == null) {
            return "このリングは現在有効ではありません";
        }
        tournament = new Tournament(name, ring.getClaimKey());
        save();
        broadcast("§e『" + name + "』§fの参加受付を開始しました！");
        broadcast("§7/tournament から参加登録できます");
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.5F);
        }
        return null;
    }

    /** 参加登録 / 登録取消。 */
    public void toggleJoin(Player player) {
        if (tournament == null || tournament.getState() != Tournament.State.REGISTRATION) {
            player.sendMessage(PREFIX + "§c現在参加受付中の大会はありません");
            return;
        }
        UUID uuid = player.getUniqueId();
        if (tournament.getParticipants().remove(uuid) != null) {
            save();
            player.sendMessage(PREFIX + "§e参加登録を取り消しました");
            return;
        }
        tournament.getParticipants().put(uuid, player.getName());
        save();
        player.sendMessage(PREFIX + "§a参加登録しました！ §7(現在 "
                + tournament.getParticipants().size() + " 人)");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8F, 1.4F);
    }

    /** 受付を締め切りシード抽選をして大会を開始する。失敗時はエラーメッセージ。 */
    public String startTournament() {
        if (tournament == null || tournament.getState() != Tournament.State.REGISTRATION) {
            return "参加受付中の大会がありません";
        }
        if (tournament.getParticipants().size() < 2) {
            return "開始には 2 人以上の参加者が必要です（現在 "
                    + tournament.getParticipants().size() + " 人）";
        }
        RingRegion ring = ringModule.getRing(tournament.getRingClaimKey());
        if (ring == null || !ringModule.isRingActive(ring)) {
            return "会場のリングが利用できません";
        }
        tournament.drawBracket(random);
        ringModule.lockRing(tournament.getRingClaimKey());
        save();
        broadcast("§e『" + tournament.getName() + "』§f開幕！ シード抽選が完了しました §7(参加 "
                + tournament.getParticipants().size() + " 人 / 全 "
                + tournament.totalRounds() + " ラウンド)");
        announceBracket();
        nextActionAt = System.currentTimeMillis() + 5_000L;
        return null;
    }

    /** 大会を中止する。進行中の Duel はリングモジュール側の管理下でそのまま終わる。 */
    public void cancelTournament(String reason) {
        if (tournament == null) {
            return;
        }
        if (tournament.getState() != Tournament.State.FINISHED) {
            broadcast("§c大会『" + tournament.getName() + "』は中止されました"
                    + (reason == null || reason.isEmpty() ? "" : " §7(" + reason + ")"));
        }
        ringModule.unlockRing(tournament.getRingClaimKey());
        tournament = null;
        save();
    }

    private void announceBracket() {
        for (TournamentMatch match : tournament.currentMatches()) {
            if (match.isBye()) {
                broadcast("§7- §e" + tournament.nameOf(match.getPlayerA())
                        + " §7はシード（不戦勝）です");
            } else {
                broadcast("§7- §e" + tournament.nameOf(match.getPlayerA())
                        + " §7vs §e" + tournament.nameOf(match.getPlayerB()));
            }
        }
    }

    // ================= 自動進行 =================

    private void tick() {
        if (tournament == null || tournament.getState() != Tournament.State.RUNNING) {
            return;
        }
        RingRegion ring = ringModule.getRing(tournament.getRingClaimKey());
        if (ring == null) {
            cancelTournament("会場のリングが削除されました");
            return;
        }
        long now = System.currentTimeMillis();
        if (!ringModule.isRingActive(ring)) {
            // 保護の燃料切れなどで一時的に無効。復旧まで進行（不戦敗タイマー含む）を止める
            if (now - lastBusyNotice > 30_000L) {
                lastBusyNotice = now;
                broadcast("§c会場のリングが無効になっています。復旧を待っています…");
            }
            for (TournamentMatch match : tournament.currentMatches()) {
                if (match.getState() == TournamentMatch.State.CALLED) {
                    match.setCalledAt(now);
                }
            }
            return;
        }
        if (now < nextActionAt) {
            return;
        }

        // 進行中の試合があれば結果待ち
        for (TournamentMatch match : tournament.currentMatches()) {
            if (match.getState() == TournamentMatch.State.IN_DUEL) {
                return;
            }
        }

        // BYE を消化し、次に呼び出す試合を探す
        TournamentMatch next = null;
        for (TournamentMatch match : tournament.currentMatches()) {
            if (match.getState() == TournamentMatch.State.DONE) {
                continue;
            }
            if (match.isBye()) {
                match.setWinner(match.getPlayerA());
                save();
                continue;
            }
            next = match;
            break;
        }

        if (next == null) {
            if (tournament.isRoundComplete()) {
                advance();
            }
            return;
        }

        if (next.getState() == TournamentMatch.State.PENDING) {
            callMatch(next, now);
            return;
        }
        // CALLED: 両者が揃えば開始、猶予切れなら不戦敗
        Player a = Bukkit.getPlayer(next.getPlayerA());
        Player b = Bukkit.getPlayer(next.getPlayerB());
        if (a != null && b != null) {
            tryStartDuel(next, ring, a, b, now);
        } else if (now - next.getCalledAt() > CALL_GRACE_MILLIS) {
            resolveWalkover(next, a != null ? a : b);
        }
    }

    private void callMatch(TournamentMatch match, long now) {
        match.setState(TournamentMatch.State.CALLED);
        match.setCalledAt(now);
        String label = tournament.roundLabel(tournament.getCurrentRound());
        broadcast("§b" + label + "§f: §e" + tournament.nameOf(match.getPlayerA())
                + " §7vs §e" + tournament.nameOf(match.getPlayerB()) + " §fまもなく開始！");
        for (UUID uuid : new UUID[]{match.getPlayerA(), match.getPlayerB()}) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(PREFIX + "§a§lあなたの試合が呼び出されました！ §7まもなくリングへ移動します");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.8F);
            }
        }
        // 少し間を置いてから開始判定に入る
        nextActionAt = now + 3_000L;
    }

    private void tryStartDuel(TournamentMatch match, RingRegion ring, Player a, Player b, long now) {
        if (ringModule.getSessionInRing(ring) != null
                || ringModule.getSession(a.getUniqueId()) != null
                || ringModule.getSession(b.getUniqueId()) != null) {
            // リング又はプレイヤーが別の試合中（前の試合の残りなど）。空き待ち
            if (now - lastBusyNotice > 15_000L) {
                lastBusyNotice = now;
                broadcast("§7リングの空きを待っています…");
            }
            return;
        }
        Tournament current = tournament;
        boolean started = ringModule.startArrangedDuel(ring, a, b, new DuelSession.ResultCallback() {
            @Override
            public void onEnd(UUID winner, UUID loser) {
                onMatchEnd(current, match, winner);
            }

            @Override
            public void onAbort() {
                onMatchAbort(current, match);
            }
        });
        if (started) {
            match.setState(TournamentMatch.State.IN_DUEL);
        } else if (now - match.getCalledAt() > CALL_GRACE_MILLIS) {
            // 開始できないまま猶予が切れた場合も進行を止めない
            resolveWalkover(match, null);
        }
    }

    /** 猶予切れの不戦敗処理。online が null の場合は両者不在で playerA の勝ち扱い。 */
    private void resolveWalkover(TournamentMatch match, Player online) {
        UUID winner = online != null ? online.getUniqueId() : match.getPlayerA();
        UUID loser = winner.equals(match.getPlayerA()) ? match.getPlayerB() : match.getPlayerA();
        match.setWinner(winner);
        save();
        broadcast("§e" + tournament.nameOf(loser) + " §7が時間内に現れなかったため §e"
                + tournament.nameOf(winner) + " §fの不戦勝です");
        nextActionAt = System.currentTimeMillis() + MATCH_INTERVAL_MILLIS;
    }

    /** Duel の決着（リングモジュールからのコールバック）。 */
    private void onMatchEnd(Tournament source, TournamentMatch match, UUID winner) {
        if (tournament != source || match.getState() != TournamentMatch.State.IN_DUEL) {
            return;
        }
        match.setWinner(winner);
        save();
        String label = tournament.roundLabel(tournament.getCurrentRound());
        broadcast("§b" + label + "§f: §6§l" + tournament.nameOf(winner) + " §fが §e"
                + tournament.nameOf(match.getLoser()) + " §fに勝利！");
        nextActionAt = System.currentTimeMillis() + MATCH_INTERVAL_MILLIS;
    }

    /** Duel の中断（勝敗なし）。試合を未消化に戻して呼び出しからやり直す。 */
    private void onMatchAbort(Tournament source, TournamentMatch match) {
        if (tournament != source || match.getState() != TournamentMatch.State.IN_DUEL) {
            return;
        }
        match.setState(TournamentMatch.State.PENDING);
        broadcast("§e試合が中断されたため、再度呼び出します");
        nextActionAt = System.currentTimeMillis() + MATCH_INTERVAL_MILLIS;
    }

    /** ラウンド終了処理。次のラウンドへ進むか、優勝者を確定する。 */
    private void advance() {
        boolean finished = tournament.advanceRound();
        save();
        if (finished) {
            crownChampion();
            return;
        }
        broadcast("§b" + tournament.roundLabel(tournament.getCurrentRound())
                + " §fへ進みます！ §7(このラウンドは " + tournament.currentMatches().size() + " 試合)");
        announceBracket();
        nextActionAt = System.currentTimeMillis() + MATCH_INTERVAL_MILLIS;
    }

    // ================= 優勝演出 =================

    private void crownChampion() {
        ringModule.unlockRing(tournament.getRingClaimKey());
        UUID championId = tournament.getChampion();
        String name = tournament.nameOf(championId);
        broadcast("§6§l======================================");
        broadcast("§e§l👑 優勝: " + name + " §e§l👑");
        broadcast("§f『" + tournament.getName() + "』を制し頂点に立ちました！");
        broadcast("§6§l======================================");

        Title title = Title.title(
                ComponentUtils.legacy("§6§l👑 " + name + " §6§l👑"),
                ComponentUtils.legacy("§e『" + tournament.getName() + "』優勝！"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(4000), Duration.ofMillis(800)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(title);
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.3F, 1.8F);
        }

        Player champion = championId == null ? null : Bukkit.getPlayer(championId);
        if (champion != null) {
            launchChampionFireworks(champion);
        }
    }

    /** 優勝者に約8秒間豪華な花火を打ち上げる。 */
    private void launchChampionFireworks(Player champion) {
        for (int i = 0; i < 10; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!champion.isOnline()) {
                    return;
                }
                Location loc = champion.getLocation().add(
                        random.nextDouble() * 6 - 3, 0.5, random.nextDouble() * 6 - 3);
                Firework firework = champion.getWorld().spawn(loc, Firework.class);
                FireworkMeta meta = firework.getFireworkMeta();
                meta.setPower(1 + random.nextInt(2));
                meta.addEffect(FireworkEffect.builder()
                        .with(randomFireworkType())
                        .withColor(randomColor(), randomColor(), randomColor())
                        .withFade(randomColor())
                        .flicker(true)
                        .trail(true)
                        .build());
                firework.setFireworkMeta(meta);
            }, i * 16L);
        }
    }

    private FireworkEffect.Type randomFireworkType() {
        FireworkEffect.Type[] types = {FireworkEffect.Type.BALL_LARGE,
                FireworkEffect.Type.STAR, FireworkEffect.Type.BURST, FireworkEffect.Type.CREEPER};
        return types[random.nextInt(types.length)];
    }

    private Color randomColor() {
        return Color.fromRGB(random.nextInt(0xFFFFFF + 1));
    }

    // ================= イベント =================

    /** 進行中の大会に参加しているプレイヤーへログイン時に状況を知らせる。 */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (tournament == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!tournament.isParticipant(player.getUniqueId())) {
            return;
        }
        switch (tournament.getState()) {
            case REGISTRATION -> player.sendMessage(PREFIX + "§e『" + tournament.getName()
                    + "』の参加受付中です（登録済み）");
            case RUNNING -> player.sendMessage(PREFIX + "§e『" + tournament.getName()
                    + "』が進行中です。呼び出しに備えてください");
            default -> {
            }
        }
    }
}
