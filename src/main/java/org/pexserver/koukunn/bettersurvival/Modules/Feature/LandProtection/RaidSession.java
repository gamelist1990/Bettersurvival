package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.Party;

import java.util.ArrayList;
import java.util.UUID;

/**
 * ギルド vs ギルドの領地レイドセッション。
 * 30 分のタイムリミットがあり、ボスバーで残り時間を表示する。
 */
public class RaidSession {

    public static final long DURATION_MILLIS = 30L * 60L * 1000L;

    private final String claimKey;
    private final UUID attackerPartyId;
    private final UUID attackerLeaderId;
    private final long startTime;
    private final BossBar bossBar;
    private boolean ended;

    public RaidSession(String claimKey, UUID attackerPartyId, UUID attackerLeaderId,
                       String defenderDisplay, String attackerDisplay) {
        this.claimKey = claimKey;
        this.attackerPartyId = attackerPartyId;
        this.attackerLeaderId = attackerLeaderId;
        this.startTime = System.currentTimeMillis();
        this.ended = false;
        this.bossBar = Bukkit.createBossBar(
                "§c§lRaid: " + attackerDisplay + " §fvs " + defenderDisplay,
                BarColor.RED, BarStyle.SOLID);
        this.bossBar.setProgress(1.0);
    }

    public String getClaimKey() {
        return claimKey;
    }

    public UUID getAttackerPartyId() {
        return attackerPartyId;
    }

    public UUID getAttackerLeaderId() {
        return attackerLeaderId;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getRemainingMillis() {
        long remaining = startTime + DURATION_MILLIS - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public boolean isEnded() {
        return ended;
    }

    public void setEnded(boolean ended) {
        this.ended = ended;
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    public void updateProgress() {
        double progress = Math.max(0.0, Math.min(1.0, getRemainingMillis() / (double) DURATION_MILLIS));
        bossBar.setProgress(progress);
    }

    /**
     * 両パーティーのオンラインメンバーをボスバーに追加する。
     */
    public void syncPlayers(Party defender, Party attacker) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            boolean shouldSee = (defender != null && defender.isMember(online.getUniqueId()))
                    || (attacker != null && attacker.isMember(online.getUniqueId()));
            boolean seeing = bossBar.getPlayers().contains(online);
            if (shouldSee && !seeing) {
                bossBar.addPlayer(online);
            } else if (!shouldSee && seeing) {
                bossBar.removePlayer(online);
            }
        }
    }

    public void removeAllPlayers() {
        for (Player player : new ArrayList<>(bossBar.getPlayers())) {
            bossBar.removePlayer(player);
        }
    }
}
