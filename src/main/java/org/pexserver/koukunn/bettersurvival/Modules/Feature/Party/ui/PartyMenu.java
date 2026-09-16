package org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.ui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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

/** /party (/p) から開くパーティー管理メニュー。 */
public class PartyMenu {

    private final Loader plugin;
    private final PartyModule parties;
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
        List<Party> publicParties = parties.getPublicParties(player);
        ChestUI.Builder builder = ChestUI.builder()
                .title("§dパーティー §8- §7" + parties.scope(player))
                .size(36)
                .addButtonAt(11, "§a§lパーティーを作成", Material.NETHER_STAR,
                        "§7新しいパーティーを作成します\n§7公開 / プライベートを選択できます")
                .addButtonAt(13, publicParties.isEmpty() ? "§7公開パーティーはありません" : "§b§l公開パーティーを検索 §7(" + publicParties.size() + ")",
                        publicParties.isEmpty() ? Material.GRAY_DYE : Material.COMPASS,
                        publicParties.isEmpty()
                                ? "§7このワールドグループには参加可能な公開パーティーがありません"
                                : "§7このワールドグループの公開パーティーを検索します\n§7説明を確認してそのまま参加できます")
                .addButtonAt(15, pending.isEmpty() ? "§7招待はありません" : "§e§l届いている招待 §7(" + pending.size() + ")",
                        pending.isEmpty() ? Material.GRAY_DYE : Material.WRITABLE_BOOK,
                        pending.isEmpty() ? "§7プライベートパーティー等からの招待がここに表示されます"
                                : "§7クリックして招待を確認します")
                .addButtonAt(35, "§c閉じる", Material.BARRIER);
        builder.then((result, p) -> {
            if (result.slot == null) return;
            switch (result.slot) {
                case 11 -> startCreation(p);
                case 13 -> {
                    if (!parties.getPublicParties(p).isEmpty()) openPublicParties(p);
                }
                case 15 -> {
                    if (!parties.getPendingInvites(p.getUniqueId()).isEmpty()) openInvites(p);
                }
                case 35 -> ChestUI.closeMenu(p);
                default -> { }
            }
        }).show(player);
    }

    // ================= 公開パーティー検索 =================

    public void openPublicParties(Player player) {
        if (parties.getPartyOf(player.getUniqueId()) != null) {
            openRoot(player);
            return;
        }
        List<Party> list = parties.getPublicParties(player);
        int rows = Math.max(3, Math.min(6, ((Math.max(1, list.size()) + 8) / 9) + 1));
        int size = rows * 9;
        ChestUI.Builder builder = ChestUI.builder()
                .title("§b公開パーティー検索 §8- §7" + parties.scope(player))
                .size(size);
        Map<Integer, UUID> slotMap = new LinkedHashMap<>();
        int slot = 0;
        for (Party party : list) {
            if (slot >= size - 9) break;
            String description = party.getDescription().isBlank() ? "§8説明なし" : "§f" + party.getDescription();
            String lore = "§7説明: " + description
                    + "\n§7リーダー: §e" + party.nameOf(party.getLeader())
                    + "\n§7メンバー: §e" + party.getAllMembers().size() + "人"
                    + "\n§7参加方式: §a公開"
                    + "\n\n§aクリックでこのパーティーに参加";
            builder.addButtonAt(slot, "§a[公開] " + party.getColoredName(), party.getColor().getIcon(), lore);
            slotMap.put(slot, party.getId());
            slot++;
        }
        if (list.isEmpty()) {
            builder.addButtonAt(4, "§7公開パーティーはありません", Material.GRAY_DYE,
                    "§7このワールドグループには現在公開パーティーがありません");
        }
        builder.addButtonAt(size - 1, "§7戻る", Material.ARROW);
        builder.then((result, p) -> {
            if (result.slot == null) return;
            if (result.slot == size - 1) {
                openRoot(p);
                return;
            }
            UUID id = slotMap.get(result.slot);
            if (id == null) return;
            Party target = parties.getParty(id);
            String error = parties.joinPublicParty(p, target);
            if (error != null) {
                p.sendMessage("§c" + error);
                openPublicParties(p);
                return;
            }
            p.sendMessage("§aパーティー " + target.getColoredName() + " §aに参加しました！");
            openMain(p, target);
        }).show(player);
    }

    // ================= 作成フロー =================

    private void startCreation(Player player) {
        if (parties.getPartyOf(player.getUniqueId()) != null) {
            player.sendMessage("§cこのワールドグループでは既にパーティーに所属しています");
            return;
        }
        ChestUI.closeMenu(player);
        ChestUI.openChat(player, "パーティー名を入力 (2〜" + PartyModule.MAX_NAME_LENGTH + "文字)", "", input -> runSync(() -> {
            String name = parties.sanitizeName(input);
            if (name == null) {
                player.sendMessage("§cパーティー名は2〜" + PartyModule.MAX_NAME_LENGTH + "文字で指定してください");
                return;
            }
            if (parties.isNameUsed(player, name)) {
                player.sendMessage("§cこのワールドグループではその名前は既に使われています");
                return;
            }
            PendingCreation creation = new PendingCreation();
            creation.name = name;
            creations.put(player.getUniqueId(), creation);
            openColorSelect(player, null);
        }));
    }

    public void openColorSelect(Player player, Party editing) {
        ChestUI.Builder builder = ChestUI.builder().title("§dイメージカラーを選択").size(27);
        Map<Integer, PartyColor> slotMap = new LinkedHashMap<>();
        int slot = 0;
        for (PartyColor color : PartyColor.values()) {
            boolean used = parties.isColorUsed(player, color, editing);
            if (used) {
                builder.addButtonAt(slot, "§8" + color.getDisplayName() + " (使用中)", Material.GRAY_STAINED_GLASS_PANE,
                        "§cこのワールドグループの別パーティーが使用中です");
            } else {
                builder.addButtonAt(slot, color.getLegacyCode() + "§l" + color.getDisplayName(), color.getIcon(),
                        "§7クリックでこのカラーを選択します");
                slotMap.put(slot, color);
            }
            slot++;
        }
        builder.addButtonAt(26, "§cキャンセル", Material.BARRIER);
        builder.then((result, p) -> {
            if (result.slot == null) return;
            if (result.slot == 26) {
                creations.remove(p.getUniqueId());
                openRoot(p);
                return;
            }
            PartyColor selected = slotMap.get(result.slot);
            if (selected == null) return;
            if (editing != null) {
                String error = parties.recolor(p, editing, selected);
                if (error != null) p.sendMessage("§c" + error);
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
        ChestUI.openChat(player, "パーティーの説明を入力 (公開検索にも表示されます)", "", input -> runSync(() -> {
            creation.description = input == null ? "" : input.trim();
            openCreationVisibility(player, creation);
        }));
    }

    private void openCreationVisibility(Player player, PendingCreation creation) {
        ChestUI.builder()
                .title("§d参加方式を選択")
                .size(27)
                .addButtonAt(11, "§a§l公開パーティー", Material.LIME_DYE,
                        "§7同じワールドグループの検索一覧に表示\n§7説明を見たプレイヤーが自由に参加できます")
                .addButtonAt(15, "§e§lプライベートパーティー", Material.RED_DYE,
                        "§7検索一覧には表示されません\n§7今まで通り招待されたプレイヤーのみ参加できます")
                .addButtonAt(26, "§cキャンセル", Material.BARRIER)
                .then((result, p) -> {
                    if (result.slot == null) return;
                    if (result.slot == 26) {
                        creations.remove(p.getUniqueId());
                        openRoot(p);
                        return;
                    }
                    if (result.slot != 11 && result.slot != 15) return;
                    boolean publicParty = result.slot == 11;
                    creations.remove(p.getUniqueId());
                    String error = parties.createParty(p, creation.name,
                            PartyColor.fromKey(creation.colorKey), creation.description);
                    if (error != null) {
                        p.sendMessage("§c" + error);
                        return;
                    }
                    Party party = parties.getPartyOf(p.getUniqueId());
                    if (party == null) return;
                    if (publicParty) parties.setPublicParty(p, party, true);
                    p.sendMessage("§aパーティー " + party.getColoredName() + " §aを作成しました！ §7("
                            + (publicParty ? "公開" : "プライベート") + ")");
                    openMain(p, party);
                }).show(player);
    }

    // ================= 招待 =================

    public void openInvites(Player player) {
        List<Party> pending = parties.getPendingInvites(player.getUniqueId());
        ChestUI.Builder builder = ChestUI.builder().title("§d届いている招待").size(27);
        Map<Integer, UUID> slotMap = new LinkedHashMap<>();
        int slot = 0;
        for (Party party : pending) {
            if (slot >= 18) break;
            builder.addButtonAt(slot, party.getColoredName(), party.getColor().getIcon(),
                    "§7リーダー: §e" + party.nameOf(party.getLeader())
                            + "\n§7メンバー: §e" + party.getAllMembers().size() + "人"
                            + "\n§7説明: §f" + (party.getDescription().isBlank() ? "説明なし" : party.getDescription())
                            + "\n\n§aクリック: 承認して加入");
            slotMap.put(slot, party.getId());
            slot++;
        }
        builder.addButtonAt(22, "§c全て拒否", Material.RED_DYE, "§7届いている招待を全て拒否します");
        builder.addButtonAt(26, "§7戻る", Material.ARROW);
        builder.then((result, p) -> {
            if (result.slot == null) return;
            if (result.slot == 26) { openRoot(p); return; }
            if (result.slot == 22) {
                for (Party party : parties.getPendingInvites(p.getUniqueId())) parties.declineInvite(p, party);
                p.sendMessage("§e全ての招待を拒否しました");
                openRoot(p);
                return;
            }
            UUID partyId = slotMap.get(result.slot);
            Party party = parties.getParty(partyId);
            if (party == null) { openInvites(p); return; }
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
        if (rank == null || !parties.scope(player).equals(party.getScope())) {
            openRoot(player);
            return;
        }
        boolean leader = rank == PartyRank.LEADER;
        boolean coLeaderUp = rank.isAtLeast(PartyRank.CO_LEADER);
        String desc = party.getDescription().isEmpty() ? "§8(説明なし)" : "§7" + party.getDescription();
        String visibility = party.isPublicParty() ? "§a公開" : "§eプライベート";

        ChestUI.Builder builder = ChestUI.builder()
                .title("§dパーティー: " + party.getName())
                .size(36)
                .addButtonAt(4, party.getColoredName(), party.getColor().getIcon(),
                        desc
                                + "\n§7参加方式: " + visibility
                                + "\n§7ワールドグループ: §f" + party.getScope()
                                + "\n§7リーダー: §e" + party.nameOf(party.getLeader())
                                + "\n§7メンバー数: §e" + party.getAllMembers().size() + "人"
                                + "\n§7あなたの階級: " + rank.getDisplayName())
                .addButtonAt(10, "§b§lメンバー一覧", Material.PLAYER_HEAD, "§7メンバーの確認と管理を行います")
                .addButtonAt(12, coLeaderUp ? "§a§lメンバーを招待" : "§8メンバーを招待", coLeaderUp ? Material.WRITABLE_BOOK : Material.GRAY_DYE,
                        coLeaderUp ? "§7公開/プライベートに関係なく直接招待できます" : "§cサブリーダー以上のみ使用できます")
                .addButtonAt(14, leader ? "§e§l名前を変更" : "§8名前を変更", leader ? Material.NAME_TAG : Material.GRAY_DYE,
                        leader ? "§7パーティー名を変更します" : "§cリーダーのみ使用できます")
                .addButtonAt(16, leader ? "§e§lカラーを変更" : "§8カラーを変更", leader ? party.getColor().getIcon() : Material.GRAY_DYE,
                        leader ? "§7イメージカラーを変更します" : "§cリーダーのみ使用できます")
                .addButtonAt(20, coLeaderUp ? "§e§l説明を変更" : "§8説明を変更", coLeaderUp ? Material.WRITABLE_BOOK : Material.GRAY_DYE,
                        coLeaderUp ? "§7公開検索にも表示される説明文を変更します" : "§cサブリーダー以上のみ使用できます")
                .addButtonAt(28, coLeaderUp ? "§b§lパーティー設定" : "§8パーティー設定", coLeaderUp ? Material.REDSTONE_TORCH : Material.GRAY_DYE,
                        coLeaderUp ? "§7公開設定・味方攻撃・名前表示を変更します" : "§cサブリーダー以上のみ使用できます")
                .addButtonAt(24, leader ? "§4§lパーティーを解散" : "§c§lパーティーを脱退",
                        leader ? Material.TNT : Material.OAK_DOOR,
                        leader ? "§c全メンバーが脱退し、パーティーが削除されます" : "§7このパーティーから脱退します")
                .addButtonAt(35, "§c閉じる", Material.BARRIER);

        builder.then((result, p) -> {
            if (result.slot == null) return;
            Party current = parties.getPartyOf(p.getUniqueId());
            if (current == null) { openRoot(p); return; }
            PartyRank currentRank = current.rankOf(p.getUniqueId());
            boolean isLeader = currentRank == PartyRank.LEADER;
            boolean isCoUp = currentRank != null && currentRank.isAtLeast(PartyRank.CO_LEADER);
            switch (result.slot) {
                case 10 -> openMembers(p, current);
                case 12 -> { if (isCoUp) openInviteTargets(p, current); }
                case 14 -> { if (isLeader) askRename(p, current); }
                case 16 -> { if (isLeader) openColorSelect(p, current); }
                case 20 -> { if (isCoUp) askRedescribe(p, current); }
                case 28 -> { if (isCoUp) openSettings(p, current); }
                case 24 -> {
                    if (isLeader) openDisbandConfirm(p, current);
                    else {
                        String error = parties.leave(p, current);
                        if (error != null) p.sendMessage("§c" + error);
                        else p.sendMessage("§eパーティーを脱退しました");
                        ChestUI.closeMenu(p);
                    }
                }
                case 35 -> ChestUI.closeMenu(p);
                default -> { }
            }
        }).show(player);
    }

    private void askRename(Player player, Party party) {
        ChestUI.closeMenu(player);
        ChestUI.openChat(player, "新しいパーティー名を入力", party.getName(), input -> runSync(() -> {
            String error = parties.rename(player, party, input);
            if (error != null) player.sendMessage("§c" + error);
            openMain(player, party);
        }));
    }

    private void askRedescribe(Player player, Party party) {
        ChestUI.closeMenu(player);
        ChestUI.openChat(player, "新しい説明を入力 (公開検索にも表示)", party.getDescription(), input -> runSync(() -> {
            String error = parties.redescribe(player, party, input);
            if (error != null) player.sendMessage("§c" + error);
            else player.sendMessage("§a説明を更新しました");
            openMain(player, party);
        }));
    }

    private void openDisbandConfirm(Player player, Party party) {
        ChestUI.builder().title("§4本当に解散しますか？").size(27)
                .addButtonAt(11, "§c§l解散する", Material.TNT, "§cこの操作は取り消せません")
                .addButtonAt(15, "§7キャンセル", Material.ARROW)
                .then((result, p) -> {
                    if (result.slot == null) return;
                    if (result.slot == 11) {
                        String error = parties.disband(p, party);
                        if (error != null) p.sendMessage("§c" + error);
                        else p.sendMessage("§eパーティー §f" + party.getName() + " §eを解散しました");
                        ChestUI.closeMenu(p);
                    } else if (result.slot == 15) openMain(p, party);
                }).show(player);
    }

    // ================= メンバー =================

    public void openMembers(Player player, Party party) {
        List<UUID> all = party.getAllMembers();
        int size = Math.max(18, Math.min(54, 9 * ((all.size() + 8) / 9) + 9));
        ChestUI.Builder builder = ChestUI.builder().title("§dメンバー一覧 (" + all.size() + "人)").size(size);
        Map<Integer, UUID> slotMap = new LinkedHashMap<>();
        PartyRank viewerRank = party.rankOf(player.getUniqueId());
        boolean canManage = viewerRank != null && viewerRank.isAtLeast(PartyRank.CO_LEADER);
        int slot = 0;
        for (UUID member : all) {
            if (slot >= size - 9) break;
            PartyRank memberRank = party.rankOf(member);
            Player onlinePlayer = Bukkit.getPlayer(member);
            boolean onlineHere = onlinePlayer != null && parties.scope(onlinePlayer).equals(party.getScope());
            String label = (onlineHere ? "§a● " : "§7○ ") + "§f" + party.nameOf(member);
            String lore = "§7階級: " + (memberRank == null ? "§8不明" : memberRank.getDisplayName())
                    + "\n§7状態: " + (onlineHere ? "§aこのワールドグループでオンライン" : "§7オフライン/別グループ");
            if (canManage && !member.equals(player.getUniqueId()) && memberRank != PartyRank.LEADER) {
                lore += "\n\n§eクリックで管理";
                slotMap.put(slot, member);
            }
            if (onlinePlayer != null) builder.addPlayerHeadAt(slot, label, onlinePlayer, lore);
            else builder.addButtonAt(slot, label, Material.PLAYER_HEAD, lore);
            slot++;
        }
        builder.addButtonAt(size - 1, "§7戻る", Material.ARROW);
        builder.then((result, p) -> {
            if (result.slot == null) return;
            if (result.slot == size - 1) { openMain(p, party); return; }
            UUID target = slotMap.get(result.slot);
            if (target != null) openMemberManage(p, party, target);
        }).show(player);
    }

    private void openMemberManage(Player player, Party party, UUID target) {
        PartyRank targetRank = party.rankOf(target);
        if (targetRank == null) { openMembers(player, party); return; }
        boolean viewerIsLeader = party.rankOf(player.getUniqueId()) == PartyRank.LEADER;
        String targetName = party.nameOf(target);
        ChestUI.Builder builder = ChestUI.builder().title("§d管理: " + targetName).size(27)
                .addButtonAt(4, "§f" + targetName, Material.PLAYER_HEAD, "§7階級: " + targetRank.getDisplayName());
        if (viewerIsLeader && targetRank == PartyRank.MEMBER)
            builder.addButtonAt(11, "§a§lサブリーダーへ昇格", Material.GOLDEN_HELMET, "§7このメンバーを昇格します");
        if (viewerIsLeader && targetRank == PartyRank.CO_LEADER)
            builder.addButtonAt(11, "§e§lメンバーへ降格", Material.LEATHER_HELMET, "§7このサブリーダーを降格します");
        builder.addButtonAt(15, "§c§lパーティーから追放", Material.IRON_AXE, "§cこのメンバーを追放します");
        builder.addButtonAt(26, "§7戻る", Material.ARROW);
        builder.then((result, p) -> {
            if (result.slot == null) return;
            switch (result.slot) {
                case 11 -> {
                    PartyRank nowRank = party.rankOf(target);
                    String error = nowRank == PartyRank.MEMBER ? parties.promote(p, party, target) : parties.demote(p, party, target);
                    if (error != null) p.sendMessage("§c" + error);
                    openMembers(p, party);
                }
                case 15 -> {
                    String error = parties.kick(p, party, target);
                    if (error != null) p.sendMessage("§c" + error);
                    openMembers(p, party);
                }
                case 26 -> openMembers(p, party);
                default -> { }
            }
        }).show(player);
    }

    // ================= 招待対象 =================

    private void openInviteTargets(Player player, Party party) {
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            if (!parties.scope(online).equals(party.getScope())) continue;
            if (parties.getPartyOf(online.getUniqueId(), party.getScope()) != null) continue;
            candidates.add(online);
        }
        int size = Math.max(18, Math.min(54, 9 * ((candidates.size() + 8) / 9) + 9));
        ChestUI.Builder builder = ChestUI.builder().title("§d招待するプレイヤーを選択").size(size);
        Map<Integer, UUID> slotMap = new LinkedHashMap<>();
        int slot = 0;
        for (Player candidate : candidates) {
            if (slot >= size - 9) break;
            builder.addPlayerHeadAt(slot, "§f" + candidate.getName(), candidate, "§7クリックで招待を送信します");
            slotMap.put(slot, candidate.getUniqueId());
            slot++;
        }
        if (candidates.isEmpty()) builder.addButtonAt(4, "§7招待できるプレイヤーがいません", Material.GRAY_DYE,
                "§7同じワールドグループのパーティー未所属プレイヤーが対象です");
        builder.addButtonAt(size - 1, "§7戻る", Material.ARROW);
        builder.then((result, p) -> {
            if (result.slot == null) return;
            if (result.slot == size - 1) { openMain(p, party); return; }
            UUID targetId = slotMap.get(result.slot);
            if (targetId == null) return;
            Player target = Bukkit.getPlayer(targetId);
            if (target == null) { p.sendMessage("§c対象プレイヤーはオフラインです"); openInviteTargets(p, party); return; }
            String error = parties.invite(p, party, target);
            if (error != null) p.sendMessage("§c" + error);
            else p.sendMessage("§a" + target.getName() + " に招待を送信しました (5分間有効)");
            openInviteTargets(p, party);
        }).show(player);
    }

    // ================= 設定 =================

    private void openSettings(Player player, Party party) {
        ChestUI.builder().title("§bパーティー設定").size(36)
                .addButtonAt(10, party.isPublicParty() ? "§a§l[公開] §f参加方式" : "§e§l[プライベート] §f参加方式",
                        party.isPublicParty() ? Material.LIME_DYE : Material.RED_DYE,
                        party.isPublicParty()
                                ? "§7検索一覧に表示中\n§7誰でも説明を見て参加できます\n§eクリックでプライベートへ"
                                : "§7検索一覧には表示されません\n§7参加には招待が必要です\n§eクリックで公開へ")
                .addButtonAt(12, toggleLabel("味方同士の攻撃", party.isFriendlyFire()), Material.IRON_SWORD,
                        toggleLore(party.isFriendlyFire()))
                .addButtonAt(14, toggleLabel("ネームタグカラー", party.isNameTagColor()), Material.LIME_DYE,
                        toggleLore(party.isNameTagColor()))
                .addButtonAt(16, toggleLabel("パーティープレフィックス", party.isNameTagPrefix()), Material.NAME_TAG,
                        toggleLore(party.isNameTagPrefix()))
                .addButtonAt(35, "§7戻る", Material.ARROW)
                .then((result, p) -> {
                    if (result.slot == null) return;
                    Party current = parties.getPartyOf(p.getUniqueId());
                    if (current == null || !current.getId().equals(party.getId())) { openRoot(p); return; }
                    String error = null;
                    switch (result.slot) {
                        case 10 -> error = parties.setPublicParty(p, current, !current.isPublicParty());
                        case 12 -> error = parties.setFriendlyFire(p, current, !current.isFriendlyFire());
                        case 14 -> error = parties.setNameTagColor(p, current, !current.isNameTagColor());
                        case 16 -> error = parties.setNameTagPrefix(p, current, !current.isNameTagPrefix());
                        case 35 -> { openMain(p, current); return; }
                        default -> { return; }
                    }
                    if (error != null) p.sendMessage("§c" + error);
                    openSettings(p, current);
                }).show(player);
    }

    private static String toggleLabel(String name, boolean enabled) {
        return (enabled ? "§a§l[有効] §f" : "§7[無効] §f") + name;
    }

    private static String toggleLore(boolean enabled) {
        return enabled ? "§a現在: 有効 §7(クリックで無効化)" : "§c現在: 無効 §7(クリックで有効化)";
    }

    private static final class PendingCreation {
        private String name;
        private String colorKey;
        private String description;
    }
}
