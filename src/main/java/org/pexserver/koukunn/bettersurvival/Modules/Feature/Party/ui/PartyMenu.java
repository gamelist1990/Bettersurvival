package org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.ui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.Party;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.PartyColor;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.PartyModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.PartyRank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /party (/p) から開くパーティー管理メニュー。
 * 作成フロー（名前 -> カラー -> 説明）、招待の承認/拒否、
 * メンバー管理（昇格・降格・追放）、名前/カラー/説明の変更を提供する。
 */
public class PartyMenu {

    private final Loader plugin;
    private final PartyModule parties;
    /** 作成フロー中の一時データ */
    private final Map<UUID, PendingCreation> creations = new ConcurrentHashMap<>();

    public PartyMenu(Loader plugin, PartyModule parties) {
        this.plugin = plugin;
        this.parties = parties;
    }

    private void runSync(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    // ================= ルート =================

    public void openRoot(Player player) {
        Party party = parties.getPartyOf(player.getUniqueId());
        if (party != null) {
            openMain(player, party);
            return;
        }
        List<Party> pending = parties.getPendingInvites(player.getUniqueId());
        ChestUI.Builder builder = ChestUI.builder()
                .title("§dパーティー")
                .size(27)
                .addButtonAt(11, "§a§lパーティーを作成", Material.NETHER_STAR,
                        "§7新しいパーティーを作成します\n§7名前・カラー・説明を設定できます")
                .addButtonAt(15, pending.isEmpty() ? "§7招待はありません" : "§e§l届いている招待 (" + pending.size() + ")",
                        pending.isEmpty() ? Material.GRAY_DYE : Material.WRITABLE_BOOK,
                        pending.isEmpty() ? "§7パーティーからの招待が届くとここに表示されます"
                                : "§7クリックして招待を確認します")
                .addButtonAt(26, "§c閉じる", Material.BARRIER);
        builder.then((result, p) -> {
            if (result.slot == null) {
                return;
            }
            switch (result.slot) {
                case 11 -> startCreation(p);
                case 15 -> {
                    if (!parties.getPendingInvites(p.getUniqueId()).isEmpty()) {
                        openInvites(p);
                    }
                }
                case 26 -> ChestUI.closeMenu(p);
                default -> {
                }
            }
        }).show(player);
    }

    // ================= 作成フロー =================

    private void startCreation(Player player) {
        if (parties.getPartyOf(player.getUniqueId()) != null) {
            player.sendMessage("§c既にパーティーに所属しています");
            return;
        }
        ChestUI.closeMenu(player);
        ChestUI.openChat(player, "パーティー名を入力 (2〜" + PartyModule.MAX_NAME_LENGTH + "文字)", "", input -> runSync(() -> {
            String name = parties.sanitizeName(input);
            if (name == null) {
                player.sendMessage("§cパーティー名は2〜" + PartyModule.MAX_NAME_LENGTH + "文字で指定してください");
                return;
            }
            if (parties.isNameUsed(name)) {
                player.sendMessage("§cその名前は既に使われています");
                return;
            }
            PendingCreation creation = new PendingCreation();
            creation.name = name;
            creations.put(player.getUniqueId(), creation);
            openColorSelect(player, null);
        }));
    }

    /**
     * カラー選択メニュー。
     *
     * @param editing 変更対象のパーティー。null の場合は作成フロー用。
     */
    public void openColorSelect(Player player, Party editing) {
        ChestUI.Builder builder = ChestUI.builder()
                .title("§dイメージカラーを選択")
                .size(27);
        Map<Integer, PartyColor> slotMap = new LinkedHashMap<>();
        int slot = 0;
        for (PartyColor color : PartyColor.values()) {
            boolean used = parties.isColorUsed(color, editing);
            if (used) {
                builder.addButtonAt(slot, "§8" + color.getDisplayName() + " (使用中)", Material.GRAY_STAINED_GLASS_PANE,
                        "§c他のパーティーが使用中のため選択できません");
            } else {
                builder.addButtonAt(slot, color.getLegacyCode() + "§l" + color.getDisplayName(), color.getIcon(),
                        "§7クリックでこのカラーを選択します");
                slotMap.put(slot, color);
            }
            slot++;
        }
        builder.addButtonAt(26, "§cキャンセル", Material.BARRIER);
        builder.then((result, p) -> {
            if (result.slot == null) {
                return;
            }
            if (result.slot == 26) {
                creations.remove(p.getUniqueId());
                openRoot(p);
                return;
            }
            PartyColor selected = slotMap.get(result.slot);
            if (selected == null) {
                return;
            }
            if (editing != null) {
                String error = parties.recolor(p, editing, selected);
                if (error != null) {
                    p.sendMessage("§c" + error);
                }
                openMain(p, editing);
                return;
            }
            PendingCreation creation = creations.get(p.getUniqueId());
            if (creation == null) {
                openRoot(p);
                return;
            }
            creation.colorKey = selected.name();
            askDescription(p, creation);
        }).show(player);
    }

    private void askDescription(Player player, PendingCreation creation) {
        ChestUI.closeMenu(player);
        ChestUI.openChat(player, "パーティーの説明を入力 (空欄可)", "", input -> runSync(() -> {
            creations.remove(player.getUniqueId());
            String error = parties.createParty(player, creation.name,
                    PartyColor.fromKey(creation.colorKey), input == null ? "" : input.trim());
            if (error != null) {
                player.sendMessage("§c" + error);
                return;
            }
            Party party = parties.getPartyOf(player.getUniqueId());
            player.sendMessage("§aパーティー " + (party != null ? party.getColoredName() : creation.name) + " §aを作成しました！");
            if (party != null) {
                openMain(player, party);
            }
        }));
    }

    // ================= 招待 =================

    public void openInvites(Player player) {
        List<Party> pending = parties.getPendingInvites(player.getUniqueId());
        ChestUI.Builder builder = ChestUI.builder()
                .title("§d届いている招待")
                .size(27);
        Map<Integer, UUID> slotMap = new LinkedHashMap<>();
        int slot = 0;
        for (Party party : pending) {
            if (slot >= 18) {
                break;
            }
            builder.addButtonAt(slot, party.getColoredName(), party.getColor().getIcon(),
                    "§7リーダー: §e" + party.nameOf(party.getLeader())
                            + "\n§7メンバー: §e" + party.getAllMembers().size() + "人"
                            + (party.getDescription().isEmpty() ? "" : "\n§7" + party.getDescription())
                            + "\n\n§aクリック: 承認して加入"
                            + "\n§7承認しない場合は「全て拒否」を押してください");
            slotMap.put(slot, party.getId());
            slot++;
        }
        builder.addButtonAt(22, "§c全て拒否", Material.RED_DYE, "§7届いている招待を全て拒否します");
        builder.addButtonAt(26, "§7戻る", Material.ARROW);
        builder.then((result, p) -> {
            if (result.slot == null) {
                return;
            }
            if (result.slot == 26) {
                openRoot(p);
                return;
            }
            if (result.slot == 22) {
                for (Party party : parties.getPendingInvites(p.getUniqueId())) {
                    parties.declineInvite(p, party);
                }
                p.sendMessage("§e全ての招待を拒否しました");
                openRoot(p);
                return;
            }
            UUID partyId = slotMap.get(result.slot);
            Party party = parties.getParty(partyId);
            if (party == null) {
                openInvites(p);
                return;
            }
            String error = parties.acceptInvite(p, party);
            if (error != null) {
                p.sendMessage("§c" + error);
                openInvites(p);
                return;
            }
            p.sendMessage("§aパーティー " + party.getColoredName() + " §aに加入しました！");
            openMain(p, party);
        }).show(player);
    }

    // ================= メイン =================

    public void openMain(Player player, Party party) {
        PartyRank rank = party.rankOf(player.getUniqueId());
        if (rank == null) {
            openRoot(player);
            return;
        }
        boolean leader = rank == PartyRank.LEADER;
        boolean coLeaderUp = rank.isAtLeast(PartyRank.CO_LEADER);
        String desc = party.getDescription().isEmpty() ? "§8(説明なし)" : "§7" + party.getDescription();

        ChestUI.Builder builder = ChestUI.builder()
                .title("§dパーティー: " + party.getName())
                .size(36)
                .addButtonAt(4, party.getColoredName(), party.getColor().getIcon(),
                        desc
                                + "\n§7カラー: " + party.getColor().getLegacyCode() + party.getColor().getDisplayName()
                                + "\n§7リーダー: §e" + party.nameOf(party.getLeader())
                                + "\n§7メンバー数: §e" + party.getAllMembers().size() + "人"
                                + "\n§7あなたの階級: " + rank.getDisplayName())
                .addButtonAt(10, "§b§lメンバー一覧", Material.PLAYER_HEAD,
                        "§7メンバーの確認と管理を行います" + (coLeaderUp ? "\n§7(クリックで昇格/降格/追放)" : ""))
                .addButtonAt(12, coLeaderUp ? "§a§lメンバーを招待" : "§8メンバーを招待", coLeaderUp ? Material.WRITABLE_BOOK : Material.GRAY_DYE,
                        coLeaderUp ? "§7オンラインのプレイヤーを招待します" : "§cサブリーダー以上のみ使用できます")
                .addButtonAt(14, leader ? "§e§l名前を変更" : "§8名前を変更", leader ? Material.NAME_TAG : Material.GRAY_DYE,
                        leader ? "§7パーティー名を変更します" : "§cリーダーのみ使用できます")
                .addButtonAt(16, leader ? "§e§lカラーを変更" : "§8カラーを変更", leader ? party.getColor().getIcon() : Material.GRAY_DYE,
                        leader ? "§7イメージカラーを変更します\n§7他パーティーと同じ色は選べません" : "§cリーダーのみ使用できます")
                .addButtonAt(20, coLeaderUp ? "§e§l説明を変更" : "§8説明を変更", coLeaderUp ? Material.WRITABLE_BOOK : Material.GRAY_DYE,
                        coLeaderUp ? "§7パーティーの説明文を変更します" : "§cサブリーダー以上のみ使用できます")
                .addButtonAt(28, coLeaderUp ? "§b§lパーティー設定" : "§8パーティー設定", coLeaderUp ? Material.REDSTONE_TORCH : Material.GRAY_DYE,
                        coLeaderUp ? "§7味方攻撃・ネームタグカラー・プレフィックスを設定します" : "§cサブリーダー以上のみ使用できます")
                .addButtonAt(24, leader ? "§4§lパーティーを解散" : "§c§lパーティーを脱退",
                        leader ? Material.TNT : Material.OAK_DOOR,
                        leader ? "§c全メンバーが脱退し、パーティーが削除されます\n§c(共有中の土地保護は個人所有に戻ります)"
                                : "§7このパーティーから脱退します")
                .addButtonAt(35, "§c閉じる", Material.BARRIER);

        builder.then((result, p) -> {
            if (result.slot == null) {
                return;
            }
            Party current = parties.getPartyOf(p.getUniqueId());
            if (current == null) {
                openRoot(p);
                return;
            }
            PartyRank currentRank = current.rankOf(p.getUniqueId());
            boolean isLeader = currentRank == PartyRank.LEADER;
            boolean isCoUp = currentRank != null && currentRank.isAtLeast(PartyRank.CO_LEADER);
            switch (result.slot) {
                case 10 -> openMembers(p, current);
                case 12 -> {
                    if (isCoUp) {
                        openInviteTargets(p, current);
                    }
                }
                case 14 -> {
                    if (isLeader) {
                        askRename(p, current);
                    }
                }
                case 16 -> {
                    if (isLeader) {
                        openColorSelect(p, current);
                    }
                }
                case 20 -> {
                    if (isCoUp) {
                        askRedescribe(p, current);
                    }
                }
                case 28 -> {
                    if (isCoUp) {
                        openSettings(p, current);
                    }
                }
                case 24 -> {
                    if (isLeader) {
                        openDisbandConfirm(p, current);
                    } else {
                        String error = parties.leave(p, current);
                        if (error != null) {
                            p.sendMessage("§c" + error);
                        } else {
                            p.sendMessage("§eパーティーを脱退しました");
                        }
                        ChestUI.closeMenu(p);
                    }
                }
                case 35 -> ChestUI.closeMenu(p);
                default -> {
                }
            }
        }).show(player);
    }

    private void askRename(Player player, Party party) {
        ChestUI.closeMenu(player);
        ChestUI.openChat(player, "新しいパーティー名を入力", party.getName(), input -> runSync(() -> {
            String error = parties.rename(player, party, input);
            if (error != null) {
                player.sendMessage("§c" + error);
            }
            openMain(player, party);
        }));
    }

    private void askRedescribe(Player player, Party party) {
        ChestUI.closeMenu(player);
        ChestUI.openChat(player, "新しい説明を入力 (空欄可)", party.getDescription(), input -> runSync(() -> {
            String error = parties.redescribe(player, party, input);
            if (error != null) {
                player.sendMessage("§c" + error);
            } else {
                player.sendMessage("§a説明を更新しました");
            }
            openMain(player, party);
        }));
    }

    private void openDisbandConfirm(Player player, Party party) {
        ChestUI.builder()
                .title("§4本当に解散しますか？")
                .size(27)
                .addButtonAt(11, "§c§l解散する", Material.TNT,
                        "§cこの操作は取り消せません\n§c共有中の土地保護は個人所有に戻ります")
                .addButtonAt(15, "§7キャンセル", Material.ARROW)
                .then((result, p) -> {
                    if (result.slot == null) {
                        return;
                    }
                    if (result.slot == 11) {
                        String error = parties.disband(p, party);
                        if (error != null) {
                            p.sendMessage("§c" + error);
                        } else {
                            p.sendMessage("§eパーティー §f" + party.getName() + " §eを解散しました");
                        }
                        ChestUI.closeMenu(p);
                        return;
                    }
                    if (result.slot == 15) {
                        openMain(p, party);
                    }
                })
                .show(player);
    }

    // ================= メンバー =================

    public void openMembers(Player player, Party party) {
        List<UUID> all = party.getAllMembers();
        int size = Math.min(54, 9 * ((all.size() + 8) / 9) + 9);
        ChestUI.Builder builder = ChestUI.builder()
                .title("§dメンバー一覧 (" + all.size() + "人)")
                .size(size);
        Map<Integer, UUID> slotMap = new LinkedHashMap<>();
        PartyRank viewerRank = party.rankOf(player.getUniqueId());
        boolean canManage = viewerRank != null && viewerRank.isAtLeast(PartyRank.CO_LEADER);
        int slot = 0;
        for (UUID member : all) {
            if (slot >= size - 9) {
                break;
            }
            PartyRank rank = party.rankOf(member);
            boolean online = Bukkit.getPlayer(member) != null;
            String label = (online ? "§a● " : "§7○ ") + "§f" + party.nameOf(member);
            String lore = "§7階級: " + (rank == null ? "§8不明" : rank.getDisplayName())
                    + "\n§7状態: " + (online ? "§aオンライン" : "§7オフライン");
            if (canManage && !member.equals(player.getUniqueId()) && rank != PartyRank.LEADER) {
                lore += "\n\n§eクリックで管理 (昇格/降格/追放)";
                slotMap.put(slot, member);
            }
            Player onlinePlayer = Bukkit.getPlayer(member);
            if (onlinePlayer != null) {
                builder.addPlayerHeadAt(slot, label, onlinePlayer, lore);
            } else {
                builder.addButtonAt(slot, label, Material.PLAYER_HEAD, lore);
            }
            slot++;
        }
        builder.addButtonAt(size - 1, "§7戻る", Material.ARROW);
        builder.then((result, p) -> {
            if (result.slot == null) {
                return;
            }
            if (result.slot == size - 1) {
                openMain(p, party);
                return;
            }
            UUID target = slotMap.get(result.slot);
            if (target != null) {
                openMemberManage(p, party, target);
            }
        }).show(player);
    }

    private void openMemberManage(Player player, Party party, UUID target) {
        PartyRank targetRank = party.rankOf(target);
        if (targetRank == null) {
            openMembers(player, party);
            return;
        }
        boolean viewerIsLeader = party.rankOf(player.getUniqueId()) == PartyRank.LEADER;
        String targetName = party.nameOf(target);
        ChestUI.Builder builder = ChestUI.builder()
                .title("§d管理: " + targetName)
                .size(27)
                .addButtonAt(4, "§f" + targetName, Material.PLAYER_HEAD,
                        "§7階級: " + targetRank.getDisplayName());
        if (viewerIsLeader && targetRank == PartyRank.MEMBER) {
            builder.addButtonAt(11, "§a§lサブリーダーへ昇格", Material.GOLDEN_HELMET,
                    "§7このメンバーをサブリーダーにします");
        }
        if (viewerIsLeader && targetRank == PartyRank.CO_LEADER) {
            builder.addButtonAt(11, "§e§lメンバーへ降格", Material.LEATHER_HELMET,
                    "§7このサブリーダーをメンバーに戻します");
        }
        builder.addButtonAt(15, "§c§lパーティーから追放", Material.IRON_AXE,
                "§cこのメンバーをパーティーから追放します");
        builder.addButtonAt(26, "§7戻る", Material.ARROW);
        builder.then((result, p) -> {
            if (result.slot == null) {
                return;
            }
            switch (result.slot) {
                case 11 -> {
                    PartyRank nowRank = party.rankOf(target);
                    String error = nowRank == PartyRank.MEMBER
                            ? parties.promote(p, party, target)
                            : parties.demote(p, party, target);
                    if (error != null) {
                        p.sendMessage("§c" + error);
                    }
                    openMembers(p, party);
                }
                case 15 -> {
                    String error = parties.kick(p, party, target);
                    if (error != null) {
                        p.sendMessage("§c" + error);
                    }
                    openMembers(p, party);
                }
                case 26 -> openMembers(p, party);
                default -> {
                }
            }
        }).show(player);
    }

    // ================= 招待対象選択 =================

    private void openInviteTargets(Player player, Party party) {
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (parties.getPartyOf(online.getUniqueId()) != null) {
                continue;
            }
            candidates.add(online);
        }
        final int size = Math.max(18, Math.min(54, 9 * ((candidates.size() + 8) / 9) + 9));
        ChestUI.Builder builder = ChestUI.builder()
                .title("§d招待するプレイヤーを選択")
                .size(size);
        Map<Integer, UUID> slotMap = new LinkedHashMap<>();
        int slot = 0;
        for (Player candidate : candidates) {
            if (slot >= size - 9) {
                break;
            }
            builder.addPlayerHeadAt(slot, "§f" + candidate.getName(), candidate, "§7クリックで招待を送信します");
            slotMap.put(slot, candidate.getUniqueId());
            slot++;
        }
        if (candidates.isEmpty()) {
            builder.addButtonAt(4, "§7招待できるプレイヤーがいません", Material.GRAY_DYE,
                    "§7パーティー未所属のオンラインプレイヤーが対象です");
        }
        builder.addButtonAt(size - 1, "§7戻る", Material.ARROW);
        builder.then((result, p) -> {
            if (result.slot == null) {
                return;
            }
            if (result.slot == size - 1) {
                openMain(p, party);
                return;
            }
            UUID targetId = slotMap.get(result.slot);
            if (targetId == null) {
                return;
            }
            Player target = Bukkit.getPlayer(targetId);
            if (target == null) {
                p.sendMessage("§c対象プレイヤーはオフラインです");
                openInviteTargets(p, party);
                return;
            }
            String error = parties.invite(p, party, target);
            if (error != null) {
                p.sendMessage("§c" + error);
            } else {
                p.sendMessage("§a" + target.getName() + " に招待を送信しました (5分間有効)");
            }
            openInviteTargets(p, party);
        }).show(player);
    }

    // ================= パーティー設定 =================

    private void openSettings(Player player, Party party) {
        ChestUI.builder()
                .title("§bパーティー設定")
                .size(27)
                .addButtonAt(10, toggleLabel("味方同士の攻撃", party.isFriendlyFire()), Material.IRON_SWORD,
                        toggleLore(party.isFriendlyFire()) + "\n§7無効時はパーティー内の攻撃が無効化されます")
                .addButtonAt(12, toggleLabel("ネームタグカラー", party.isNameTagColor()), Material.LIME_DYE,
                        toggleLore(party.isNameTagColor()) + "\n§7有効時はパーティーカラーが名前に反映されます")
                .addButtonAt(14, toggleLabel("パーティープレフィックス", party.isNameTagPrefix()), Material.NAME_TAG,
                        toggleLore(party.isNameTagPrefix()) + "\n§7有効時は名前の前に [パーティー名] が付きます")
                .addButtonAt(26, "§7戻る", Material.ARROW)
                .then((result, p) -> {
                    if (result.slot == null) {
                        return;
                    }
                    Party current = parties.getPartyOf(p.getUniqueId());
                    if (current == null || !current.getId().equals(party.getId())) {
                        openRoot(p);
                        return;
                    }
                    switch (result.slot) {
                        case 10 -> {
                            String error = parties.setFriendlyFire(p, current, !current.isFriendlyFire());
                            if (error != null) {
                                p.sendMessage("§c" + error);
                            }
                            openSettings(p, current);
                        }
                        case 12 -> {
                            String error = parties.setNameTagColor(p, current, !current.isNameTagColor());
                            if (error != null) {
                                p.sendMessage("§c" + error);
                            }
                            openSettings(p, current);
                        }
                        case 14 -> {
                            String error = parties.setNameTagPrefix(p, current, !current.isNameTagPrefix());
                            if (error != null) {
                                p.sendMessage("§c" + error);
                            }
                            openSettings(p, current);
                        }
                        case 26 -> openMain(p, current);
                        default -> {
                        }
                    }
                }).show(player);
    }

    private static String toggleLabel(String name, boolean enabled) {
        return (enabled ? "§a§l[有効] §f" : "§7[無効] §f") + name;
    }

    private static String toggleLore(boolean enabled) {
        return enabled ? "§a現在: 有効 §7(クリックで無効化)" : "§c現在: 無効 §7(クリックで有効化)";
    }

    /** OfflinePlayer 表示名の解決補助。 */
    @SuppressWarnings("unused")
    private String resolveName(UUID uuid) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() == null ? uuid.toString().substring(0, 8) : offline.getName();
    }

    private static final class PendingCreation {
        private String name;
        private String colorKey;
    }
}
