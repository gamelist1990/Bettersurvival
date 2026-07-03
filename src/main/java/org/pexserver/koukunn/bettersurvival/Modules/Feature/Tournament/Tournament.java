package org.pexserver.koukunn.bettersurvival.Modules.Feature.Tournament;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 頂点決定戦（シングルエリミネーション式トーナメント）の状態モデル。
 * 参加受付 → シード抽選（シャッフル）でブラケットを組み、
 * ラウンドごとに勝者を集めて次のラウンドを生成する。
 */
public class Tournament {

    public enum State {
        /** 参加受付中 */
        REGISTRATION,
        /** 大会進行中 */
        RUNNING,
        /** 優勝者決定済み */
        FINISHED,
    }

    private final String name;
    private final String ringClaimKey;
    /** 参加者 (uuid -> 表示名)。オフラインでも名前を表示できるよう保持する */
    private final Map<UUID, String> participants = new LinkedHashMap<>();
    /** ラウンドごとの試合。rounds.get(0) が 1 回戦 */
    private final List<List<TournamentMatch>> rounds = new ArrayList<>();

    private State state = State.REGISTRATION;
    private int currentRound;
    private UUID champion;

    public Tournament(String name, String ringClaimKey) {
        this.name = name;
        this.ringClaimKey = ringClaimKey;
    }

    public String getName() {
        return name;
    }

    public String getRingClaimKey() {
        return ringClaimKey;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Map<UUID, String> getParticipants() {
        return participants;
    }

    public String nameOf(UUID uuid) {
        if (uuid == null) {
            return "?";
        }
        return participants.getOrDefault(uuid, "?");
    }

    public boolean isParticipant(UUID uuid) {
        return participants.containsKey(uuid);
    }

    public List<List<TournamentMatch>> getRounds() {
        return rounds;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public List<TournamentMatch> currentMatches() {
        if (currentRound < 0 || currentRound >= rounds.size()) {
            return List.of();
        }
        return rounds.get(currentRound);
    }

    public UUID getChampion() {
        return champion;
    }

    public void setChampion(UUID champion) {
        this.champion = champion;
    }

    /** ラウンドの表示名（決勝 / 準決勝 / 第N回戦）。 */
    public String roundLabel(int roundIndex) {
        int remaining = rounds.get(roundIndex).size();
        if (remaining == 1) {
            return "決勝";
        }
        if (remaining == 2) {
            return "準決勝";
        }
        return "第" + (roundIndex + 1) + "回戦";
    }

    /** 総ラウンド数（参加人数から算出）。 */
    public int totalRounds() {
        int n = participants.size();
        int rounds = 0;
        int size = 1;
        while (size < n) {
            size <<= 1;
            rounds++;
        }
        return Math.max(1, rounds);
    }

    // ================= ブラケット生成 =================

    /**
     * シード抽選（シャッフル）をして 1 回戦のブラケットを組む。
     * 参加人数が 2 の累乗でない場合、抽選で選ばれたプレイヤーが不戦勝（BYE）になる。
     */
    public void drawBracket(Random random) {
        List<UUID> order = new ArrayList<>(participants.keySet());
        Collections.shuffle(order, random);

        int n = order.size();
        int bracketSize = 1;
        while (bracketSize < n) {
            bracketSize <<= 1;
        }
        int byes = bracketSize - n;

        List<TournamentMatch> firstRound = new ArrayList<>();
        int index = 0;
        // 先頭 byes 人は不戦勝
        for (int i = 0; i < byes; i++) {
            firstRound.add(new TournamentMatch(order.get(index++), null));
        }
        while (index + 1 < n) {
            firstRound.add(new TournamentMatch(order.get(index), order.get(index + 1)));
            index += 2;
        }
        rounds.clear();
        rounds.add(firstRound);
        currentRound = 0;
        state = State.RUNNING;
    }

    /** 現在のラウンドが全て決着していれば true。 */
    public boolean isRoundComplete() {
        for (TournamentMatch match : currentMatches()) {
            if (match.getState() != TournamentMatch.State.DONE) {
                return false;
            }
        }
        return true;
    }

    /**
     * 現在のラウンドの勝者から次のラウンドを生成する。
     * 勝者が 1 人なら優勝者を確定し true を返す。
     */
    public boolean advanceRound() {
        List<UUID> winners = new ArrayList<>();
        for (TournamentMatch match : currentMatches()) {
            if (match.getWinner() != null) {
                winners.add(match.getWinner());
            }
        }
        if (winners.size() <= 1) {
            champion = winners.isEmpty() ? null : winners.get(0);
            state = State.FINISHED;
            return true;
        }
        List<TournamentMatch> next = new ArrayList<>();
        for (int i = 0; i + 1 < winners.size(); i += 2) {
            next.add(new TournamentMatch(winners.get(i), winners.get(i + 1)));
        }
        // 奇数人が勝ち上がった場合（両者失格などのイレギュラー）は最後の 1 人が不戦勝
        if (winners.size() % 2 == 1) {
            next.add(new TournamentMatch(winners.get(winners.size() - 1), null));
        }
        rounds.add(next);
        currentRound = rounds.size() - 1;
        return false;
    }

    // ================= 永続化 =================

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("ring", ringClaimKey);
        map.put("state", state.name());
        map.put("currentRound", currentRound);
        map.put("champion", champion == null ? null : champion.toString());
        Map<String, Object> players = new LinkedHashMap<>();
        for (Map.Entry<UUID, String> entry : participants.entrySet()) {
            players.put(entry.getKey().toString(), entry.getValue());
        }
        map.put("participants", players);
        List<Object> roundList = new ArrayList<>();
        for (List<TournamentMatch> round : rounds) {
            List<Object> matchList = new ArrayList<>();
            for (TournamentMatch match : round) {
                matchList.add(match.toMap());
            }
            roundList.add(matchList);
        }
        map.put("rounds", roundList);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Tournament fromMap(Map<String, Object> map) {
        if (!(map.get("name") instanceof String name)
                || !(map.get("ring") instanceof String ring)) {
            return null;
        }
        Tournament tournament = new Tournament(name, ring);
        if (map.get("state") instanceof String s) {
            try {
                tournament.state = State.valueOf(s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (map.get("currentRound") instanceof Number n) {
            tournament.currentRound = n.intValue();
        }
        if (map.get("champion") instanceof String c && !c.isEmpty()) {
            try {
                tournament.champion = UUID.fromString(c);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (map.get("participants") instanceof Map<?, ?> players) {
            for (Map.Entry<?, ?> entry : players.entrySet()) {
                try {
                    tournament.participants.put(UUID.fromString(String.valueOf(entry.getKey())),
                            String.valueOf(entry.getValue()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (map.get("rounds") instanceof List<?> roundList) {
            for (Object roundObj : roundList) {
                if (!(roundObj instanceof List<?> matchList)) {
                    continue;
                }
                List<TournamentMatch> round = new ArrayList<>();
                for (Object matchObj : matchList) {
                    if (matchObj instanceof Map<?, ?> matchMap) {
                        TournamentMatch match = TournamentMatch.fromMap((Map<String, Object>) matchMap);
                        if (match != null) {
                            round.add(match);
                        }
                    }
                }
                tournament.rounds.add(round);
            }
        }
        if (tournament.currentRound >= tournament.rounds.size()) {
            tournament.currentRound = Math.max(0, tournament.rounds.size() - 1);
        }
        return tournament;
    }
}
