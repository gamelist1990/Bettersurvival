package org.pexserver.koukunn.bettersurvival.Modules.Feature.Tournament.ui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ClaimRegion;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.RingRegion;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Tournament.Tournament;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Tournament.TournamentMatch;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Tournament.TournamentModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 頂点決定戦のメニュー。
 * 一般プレイヤーは参加登録・ブラケット確認、OP は大会の作成・開始・中止ができる。
 */
public class TournamentMenu {

    private final Loader plugin;
    private final TournamentModule module;

    public TournamentMenu(Loader plugin, TournamentModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    private void runSync(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private static boolean isAdmin(Player player) {
        return player.isOp();
    }

    // ================= メイン =================

    public void openMain(Player player) {
        Tournament tournament = module.getTournament();
        if (tournament == null || tournament.getState() == Tournament.State.FINISHED) {
            openIdle(player, tournament);
            return;
        }
        if (tournament.getState() == Tournament.State.REGISTRATION) {
            openRegistration(player, tournament);
            return;
        }
        openBracket(player, tournament);
    }

    /** 大会がない / 終了済みのとき。 */
    private void openIdle(Player player, Tournament finished) {
        ChestUI.Builder builder = ChestUI.builder()
                .title("§6§l頂点決定戦")
                .size(27);
        if (finished != null && finished.getChampion() != null) {
            builder.addButtonAt(4, "§e§l👑 前回の優勝者: " + finished.nameOf(finished.getChampion()),
                    Material.GOLDEN_APPLE,
                    "§7大会: §e" + finished.getName()
                            + "\n§7参加者: §e" + finished.getParticipants().size() + " 人");
        } else {
            builder.addButtonAt(4, "§7現在開催中の大会はありません", Material.GRAY_DYE,
                    "§7大会が始まるとここから参加登録できます");
        }
        if (isAdmin(player)) {
            builder.addButtonAt(13, "§a§l大会を作成する", Material.WRITABLE_BOOK,
                    "§7会場にするリングを選んで\n§7新しい頂点決定戦の受付を開始します"
                            + "\n\n§8(OP 専用)");
        }
        builder.addButtonAt(26, "§c閉じる", Material.BARRIER)
                .then((result, p) -> {
                    if (result.slot == null) {
                        return;
                    }
                    if (result.slot == 13 && isAdmin(p)) {
                        openRingSelect(p);
                        return;
                    }
                    if (result.slot == 26) {
                        ChestUI.closeMenu(p);
                    }
                })
                .show(player);
    }

    /** 参加受付中のメニュー。 */
    private void openRegistration(Player player, Tournament tournament) {
        boolean joined = tournament.isParticipant(player.getUniqueId());
        ChestUI.Builder builder = ChestUI.builder()
                .title("§6§l頂点決定戦 - 参加受付中")
                .size(54)
                .addButtonAt(4, "§e§l『" + tournament.getName() + "』参加受付中！", Material.BELL,
                        "§7参加者: §e" + tournament.getParticipants().size() + " 人"
                                + "\n§7会場: §e" + ringLabel(tournament.getRingClaimKey())
                                + "\n\n§7受付終了後、シード抽選でトーナメント表が組まれ"
                                + "\n§7試合が自動で順番に呼び出されます")
                .addButtonAt(joined ? 2 : 6,
                        joined ? "§c参加を取り消す" : "§a§l参加登録する",
                        joined ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
                        joined ? "§7クリックで参加登録を取り消します"
                                : "§7クリックで大会に参加登録します"
                                        + "\n§7試合はリングの Duel 形式（1対1）です");

        int slot = 18;
        for (Map.Entry<UUID, String> entry : tournament.getParticipants().entrySet()) {
            if (slot >= 45) {
                break;
            }
            Player online = Bukkit.getPlayer(entry.getKey());
            if (online != null) {
                builder.addPlayerHeadAt(slot, "§f" + entry.getValue(), online, "§a参加登録済み (オンライン)");
            } else {
                builder.addButtonAt(slot, "§7" + entry.getValue(), Material.SKELETON_SKULL,
                        "§8参加登録済み (オフライン)");
            }
            slot++;
        }

        if (isAdmin(player)) {
            builder.addButtonAt(47, "§a§l受付を締め切り開始！", Material.DIAMOND_SWORD,
                    "§7シード抽選をしてトーナメントを開始します"
                            + "\n§7(2 人以上の参加が必要 / OP 専用)");
            builder.addButtonAt(51, "§c大会を中止", Material.TNT, "§7この大会を中止します (OP 専用)");
        }
        builder.addButtonAt(49, "§e更新", Material.SUNFLOWER, "§7表示を最新の状態にします")
                .addButtonAt(53, "§c閉じる", Material.BARRIER)
                .then((result, p) -> {
                    if (result.slot == null) {
                        return;
                    }
                    Tournament current = module.getTournament();
                    if (current == null || current.getState() != Tournament.State.REGISTRATION) {
                        p.sendMessage("§c受付は既に終了しています");
                        openMain(p);
                        return;
                    }
                    switch (result.slot) {
                        case 2, 6 -> {
                            module.toggleJoin(p);
                            openRegistration(p, current);
                        }
                        case 47 -> {
                            if (!isAdmin(p)) {
                                return;
                            }
                            String error = module.startTournament();
                            if (error != null) {
                                p.sendMessage("§c" + error);
                                openRegistration(p, current);
                                return;
                            }
                            ChestUI.closeMenu(p);
                        }
                        case 51 -> {
                            if (!isAdmin(p)) {
                                return;
                            }
                            module.cancelTournament("管理者による中止");
                            ChestUI.closeMenu(p);
                        }
                        case 49 -> openRegistration(p, current);
                        case 53 -> ChestUI.closeMenu(p);
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    /** 進行中のブラケット（トーナメント表）表示。 */
    private void openBracket(Player player, Tournament tournament) {
        ChestUI.Builder builder = ChestUI.builder()
                .title("§6§l頂点決定戦 - トーナメント表")
                .size(54)
                .addButtonAt(4, "§e§l『" + tournament.getName() + "』進行中", Material.DRAGON_HEAD,
                        "§7参加者: §e" + tournament.getParticipants().size() + " 人"
                                + "\n§7現在: §b" + tournament.roundLabel(tournament.getCurrentRound())
                                + "\n§7会場: §e" + ringLabel(tournament.getRingClaimKey())
                                + "\n\n§7試合は自動で順番に呼び出されます"
                                + "\n§7呼び出されたら会場付近で待機してください");

        int slot = 9;
        List<List<TournamentMatch>> rounds = tournament.getRounds();
        for (int r = 0; r < rounds.size() && slot < 45; r++) {
            String label = tournament.roundLabel(r);
            for (int m = 0; m < rounds.get(r).size() && slot < 45; m++) {
                TournamentMatch match = rounds.get(r).get(m);
                builder.addButtonAt(slot, matchTitle(tournament, label, m, match),
                        matchIcon(match), matchLore(tournament, match));
                slot++;
            }
        }

        if (isAdmin(player)) {
            builder.addButtonAt(51, "§c大会を中止", Material.TNT,
                    "§7この大会を中止します (OP 専用)\n§7進行中の試合はそのまま終了します");
        }
        builder.addButtonAt(49, "§e更新", Material.SUNFLOWER, "§7表示を最新の状態にします")
                .addButtonAt(53, "§c閉じる", Material.BARRIER)
                .then((result, p) -> {
                    if (result.slot == null) {
                        return;
                    }
                    switch (result.slot) {
                        case 49 -> openMain(p);
                        case 51 -> {
                            if (!isAdmin(p)) {
                                return;
                            }
                            module.cancelTournament("管理者による中止");
                            ChestUI.closeMenu(p);
                        }
                        case 53 -> ChestUI.closeMenu(p);
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    private static String matchTitle(Tournament tournament, String label, int index, TournamentMatch match) {
        String state = switch (match.getState()) {
            case DONE -> "§a[済]";
            case IN_DUEL -> "§c[試合中]";
            case CALLED -> "§6[呼出中]";
            default -> "§7[待機]";
        };
        return state + " §f" + label + " 第" + (index + 1) + "試合";
    }

    private static Material matchIcon(TournamentMatch match) {
        return switch (match.getState()) {
            case DONE -> Material.LIME_CONCRETE;
            case IN_DUEL -> Material.RED_CONCRETE;
            case CALLED -> Material.ORANGE_CONCRETE;
            default -> Material.LIGHT_GRAY_CONCRETE;
        };
    }

    private static String matchLore(Tournament tournament, TournamentMatch match) {
        if (match.isBye()) {
            return "§e" + tournament.nameOf(match.getPlayerA()) + " §7のシード（不戦勝）";
        }
        StringBuilder lore = new StringBuilder("§e" + tournament.nameOf(match.getPlayerA())
                + " §7vs §e" + tournament.nameOf(match.getPlayerB()));
        if (match.getWinner() != null) {
            lore.append("\n§6勝者: ").append(tournament.nameOf(match.getWinner()));
        }
        return lore.toString();
    }

    // ================= 大会作成（リング選択 → 名前入力） =================

    /** 会場にするリングの選択メニュー (OP 専用)。 */
    public void openRingSelect(Player player) {
        List<RingRegion> candidates = new ArrayList<>();
        for (RingRegion ring : module.getRingModule().getRings()) {
            if (module.getRingModule().isRingActive(ring) && ring.getWorld() != null) {
                candidates.add(ring);
            }
        }
        ChestUI.Builder builder = ChestUI.builder()
                .title("§6§l会場のリングを選択")
                .size(54)
                .addButtonAt(4, "§e会場にするリングを選んでください", Material.IRON_SWORD,
                        "§7大会中このリングは占有され\n§7通常の Duel・マッチングには使えなくなります");
        Map<Integer, String> slotMap = new LinkedHashMap<>();
        int slot = 9;
        for (RingRegion ring : candidates) {
            if (slot >= 45) {
                break;
            }
            ClaimRegion claim = module.getRingModule().getLandModule().getClaimByKey(ring.getClaimKey());
            String owner = claim == null ? "?"
                    : module.getRingModule().getLandModule().resolveOwnerDisplay(claim);
            builder.addButtonAt(slot, "§e" + owner + " §7のリング", Material.NETHER_STAR,
                    "§7ワールド: §e" + ring.getWorldName()
                            + "\n§7中心: §e" + ring.getCenterX() + ", " + ring.getCenterY() + ", " + ring.getCenterZ()
                            + "\n§7半径: §e" + ring.getRadius() + " ブロック"
                            + "\n\n§aクリックでこのリングを会場にします");
            slotMap.put(slot, ring.getClaimKey());
            slot++;
        }
        if (candidates.isEmpty()) {
            builder.addButtonAt(22, "§c利用できるリングがありません", Material.BARRIER,
                    "§7先に土地保護内にリングを作成してください\n§7(/land ring)");
        }
        builder.addButtonAt(45, "§7戻る", Material.ARROW)
                .addButtonAt(53, "§c閉じる", Material.BARRIER)
                .then((result, p) -> {
                    if (result.slot == null) {
                        return;
                    }
                    if (result.slot == 45) {
                        openMain(p);
                        return;
                    }
                    if (result.slot == 53) {
                        ChestUI.closeMenu(p);
                        return;
                    }
                    String claimKey = slotMap.get(result.slot);
                    if (claimKey == null || !isAdmin(p)) {
                        return;
                    }
                    askName(p, claimKey);
                })
                .show(player);
    }

    /** 大会名をチャットで入力して受付を開始する。 */
    private void askName(Player player, String claimKey) {
        ChestUI.closeMenu(player);
        ChestUI.openChat(player, "大会の名前を入力してください", "頂点決定戦", input -> runSync(() -> {
            RingRegion ring = module.getRingModule().getRing(claimKey);
            if (ring == null) {
                player.sendMessage("§cリングが見つかりません");
                return;
            }
            String name = input == null || input.trim().isEmpty() ? "頂点決定戦" : input.trim();
            String error = module.createTournament(name, ring);
            if (error != null) {
                player.sendMessage("§c" + error);
                return;
            }
            openMain(player);
        }));
    }

    private String ringLabel(String claimKey) {
        RingRegion ring = module.getRingModule().getRing(claimKey);
        if (ring == null) {
            return "不明";
        }
        return ring.getWorldName() + " (" + ring.getCenterX() + ", " + ring.getCenterZ() + ")";
    }
}
