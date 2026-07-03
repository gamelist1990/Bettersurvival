package org.pexserver.koukunn.bettersurvival.Modules.Feature.Tournament;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * トーナメントの 1 試合。
 * playerB が null の場合は不戦勝（BYE）で playerA が自動的に勝ち上がる。
 */
public class TournamentMatch {

    public enum State {
        /** まだ呼び出されていない */
        PENDING,
        /** 試合が呼び出され、両者の準備待ち */
        CALLED,
        /** リングで Duel 進行中 */
        IN_DUEL,
        /** 決着済み */
        DONE,
    }

    private final UUID playerA;
    private final UUID playerB;
    private UUID winner;
    private State state = State.PENDING;
    /** 呼び出し時刻 (ms)。猶予時間の判定に使う（永続化しない） */
    private transient long calledAt;

    public TournamentMatch(UUID playerA, UUID playerB) {
        this.playerA = playerA;
        this.playerB = playerB;
    }

    public UUID getPlayerA() {
        return playerA;
    }

    public UUID getPlayerB() {
        return playerB;
    }

    public boolean isBye() {
        return playerB == null;
    }

    public UUID getWinner() {
        return winner;
    }

    public UUID getLoser() {
        if (winner == null || playerB == null) {
            return null;
        }
        return winner.equals(playerA) ? playerB : playerA;
    }

    public void setWinner(UUID winner) {
        this.winner = winner;
        this.state = State.DONE;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public long getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(long calledAt) {
        this.calledAt = calledAt;
    }

    public boolean isParticipant(UUID uuid) {
        return uuid != null && (uuid.equals(playerA) || uuid.equals(playerB));
    }

    // ================= 永続化 =================

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", playerA.toString());
        map.put("b", playerB == null ? null : playerB.toString());
        map.put("winner", winner == null ? null : winner.toString());
        // 進行中の試合は再起動で消えるため、DONE 以外は PENDING として保存する
        map.put("state", state == State.DONE ? State.DONE.name() : State.PENDING.name());
        return map;
    }

    public static TournamentMatch fromMap(Map<String, Object> map) {
        UUID a = readUuid(map.get("a"));
        if (a == null) {
            return null;
        }
        TournamentMatch match = new TournamentMatch(a, readUuid(map.get("b")));
        UUID winner = readUuid(map.get("winner"));
        if (winner != null && "DONE".equals(map.get("state"))) {
            match.setWinner(winner);
        }
        return match;
    }

    private static UUID readUuid(Object value) {
        if (!(value instanceof String s) || s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
