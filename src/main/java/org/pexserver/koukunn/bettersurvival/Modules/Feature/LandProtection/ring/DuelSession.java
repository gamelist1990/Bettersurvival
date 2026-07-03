package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * リング内で行われる 1 対 1 の Duel セッション。
 * カウントダウン中は移動と攻撃が禁止され、FIGHT 表示後に試合が始まる。
 */
public class DuelSession {

    public enum State {
        COUNTDOWN,
        FIGHTING,
        ENDED,
    }

    private final RingRegion ring;
    private final UUID playerA;
    private final UUID playerB;
    /** 対戦ボタン（自動マッチング）経由かどうか */
    private final boolean matchmade;
    /** 試合終了後に戻す位置（マッチング経由の場合のみ） */
    private final Map<UUID, Location> returnLocations = new LinkedHashMap<>();

    private State state = State.COUNTDOWN;
    /** カウントダウン残り秒（3,2,1 → 0 で FIGHT） */
    private int countdown = 3;

    public DuelSession(RingRegion ring, UUID playerA, UUID playerB, boolean matchmade) {
        this.ring = ring;
        this.playerA = playerA;
        this.playerB = playerB;
        this.matchmade = matchmade;
    }

    public RingRegion getRing() {
        return ring;
    }

    public UUID getPlayerA() {
        return playerA;
    }

    public UUID getPlayerB() {
        return playerB;
    }

    public boolean isMatchmade() {
        return matchmade;
    }

    public Map<UUID, Location> getReturnLocations() {
        return returnLocations;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public int getCountdown() {
        return countdown;
    }

    /** カウントダウンを 1 進めて残り秒を返す。 */
    public int tickCountdown() {
        return --countdown;
    }

    public boolean isParticipant(UUID uuid) {
        return playerA.equals(uuid) || playerB.equals(uuid);
    }

    /** 相手側の UUID を返す。参加者でなければ null。 */
    public UUID opponentOf(UUID uuid) {
        if (playerA.equals(uuid)) {
            return playerB;
        }
        if (playerB.equals(uuid)) {
            return playerA;
        }
        return null;
    }
}
