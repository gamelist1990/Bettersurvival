package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.ui;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ClaimRegion;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.LandProtectionModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.DuelSession;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.RingModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.RingRegion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * リング（闘技場）の管理メニューと Duel 開始 UI。
 * 管理メニューは保護コアメニューから開き、オーナー又は副リーダーのみ操作できる。
 */
public class RingMenu {

    private final Loader plugin;
    private final RingModule module;

    public RingMenu(Loader plugin, RingModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    private void runSync(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private LandProtectionModule land() {
        return module.getLandModule();
    }

    /** 操作の度に保護領域と権限を確認する。 */
    private ClaimRegion resolveManaged(Player player, String claimKey) {
        ClaimRegion claim = land().getClaimByKey(claimKey);
        if (claim == null) {
            player.sendMessage("§cこの保護コアは既に存在しません");
            ChestUI.closeMenu(player);
            return null;
        }
        if (!land().canManage(player, claim)) {
            player.sendMessage("§cリングの設定はオーナー又は副リーダーのみ操作できます");
            ChestUI.closeMenu(player);
            return null;
        }
        return claim;
    }

    private static String formatLoc(Location loc) {
        if (loc == null) {
            return "§8未設定";
        }
        return "§e" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private static String toggleLore(boolean enabled) {
        return enabled ? "§a現在: 有効 §7(クリックで無効化)" : "§c現在: 無効 §7(クリックで有効化)";
    }

    private static String toggleLabel(String name, boolean enabled) {
        return (enabled ? "§a§l[有効] §f" : "§7[無効] §f") + name;
    }

    // ================= 管理メニュー =================

    public void openRing(Player player, ClaimRegion claim) {
        String claimKey = claim.key();
        if (!land().canManage(player, claim)) {
            player.sendMessage("§cリングの設定はオーナー又は副リーダーのみ操作できます");
            return;
        }
        List<RingRegion> claimRings = module.getRingsForClaim(claimKey);
        if (claimRings.size() != 1) {
            openRingList(player, claim);
            return;
        }
        openRing(player, claim, claimRings.get(0));
    }

    public void openRing(Player player, ClaimRegion claim, RingRegion ring) {
        String claimKey = claim.key();
        if (!land().canManage(player, claim)) {
            player.sendMessage("§cリングの設定はオーナー又は副リーダーのみ操作できます");
            return;
        }

        DuelSession session = module.getSessionInRing(ring);
        ChestUI.builder()
                .title("§c§lリング: " + ring.getName())
                .size(45)
                .addButtonAt(4, "§c§l" + ring.getName(), Material.NETHER_STAR,
                        "§7範囲: §e" + ring.getMinX() + ", " + ring.getMinY() + ", " + ring.getMinZ()
                                + " §7〜 §e" + ring.getMaxX() + ", " + ring.getMaxY() + ", " + ring.getMaxZ()
                                + "\n§7サイズ: §e" + ring.getSizeX() + "×" + ring.getSizeZ()
                                + "\n§7状態: " + (module.isRingActive(ring) ? "§a有効" : "§c無効(保護が無効)")
                                + "\n§7Duel: " + (session == null ? "§8進行中の試合なし"
                                        : (session.getState() == DuelSession.State.FIGHTING ? "§c試合中！" : "§e開始準備中"))
                                + "\n\n§7リング内では PVP が有効になります"
                                + "\n§7境界に近づくとパーティクルで境界線が見えます")
                .addButtonAt(10, toggleLabel("Keepインベントリ", ring.isKeepInventory()), Material.TOTEM_OF_UNDYING,
                        "§7リング内で死亡してもアイテムを失いません\n" + toggleLore(ring.isKeepInventory()))
                .addButtonAt(11, toggleLabel("即時復活", ring.isInstantRespawn()), Material.BEACON,
                        "§7死亡画面を挟まずすぐに復活します\n" + toggleLore(ring.isInstantRespawn()))
                .addButtonAt(12, toggleLabel("Duelモード", ring.isDuelMode()), Material.IRON_SWORD,
                        "§7有効時は Duel を開始するまで攻撃できません"
                                + "\n§7(リング内で素手右クリック → Duel 開始)"
                                + "\n" + toggleLore(ring.isDuelMode()))
                .addButtonAt(13, toggleLabel("自動マッチング", ring.isAutoMatch()), Material.COMPARATOR,
                        "§7対戦ボタンのマッチング先として\n§7このリングを使用します\n" + toggleLore(ring.isAutoMatch()))
                .addButtonAt(14, "§e開始位置: " + ring.getStartMode().getDisplayName(), Material.ENDER_PEARL,
                        "§7" + ring.getStartMode().getDescription()
                                + "\n§7クリックで切替: ランダム → 自由 → カスタム"
                                + "\n§8開始時は最低 " + RingRegion.DUEL_MIN_DISTANCE + " ブロック離れます")
                .addButtonAt(15, "§b復活地点を設定", Material.RESPAWN_ANCHOR,
                        "§7リング内で死亡した時の復活地点を\n§7現在地に設定します"
                                + "\n§7(リング中心から " + RingRegion.RESPAWN_MAX_DISTANCE + " ブロック以内の土地内)"
                                + "\n§7現在: " + formatLoc(ring.getRespawnPoint()))
                .addButtonAt(16, "§6決着時の処理: " + ring.getCleanupMode().getDisplayName(), Material.HOPPER,
                        "§7決着が付くとリング内に残ったアイテムと\n§7試合中の設置ブロックを片付けます"
                                + "\n§7" + ring.getCleanupMode().getDescription()
                                + "\n§7クリックで切替: 消去 ⇔ 返却"
                                + (ring.getCleanupMode() == RingRegion.CleanupMode.RETURN_CHEST
                                        && ring.getReturnChest() == null
                                        ? "\n§c返却チェストが未設定です！(未設定の場合は消去されます)"
                                        : ""))
                .addButtonAt(21, "§b返却チェストを指定", Material.CHEST,
                        "§7クリック後、ラージチェストを右クリックすると\n§7返却モード時の投入先として登録されます"
                                + "\n§7現在: " + formatLoc(ring.getReturnChest()))
                .addButtonAt(19, "§dpos1 を現在地に設定", Material.LIME_CONCRETE,
                        "§7カスタム開始位置 1\n§7現在: " + formatLoc(ring.getPos1()))
                .addButtonAt(20, "§dpos2 を現在地に設定", Material.RED_CONCRETE,
                        "§7カスタム開始位置 2\n§7現在: " + formatLoc(ring.getPos2()))
                .addButtonAt(22, "§6範囲を再選択", Material.WOODEN_AXE,
                    "§7左クリック=pos1 / 右クリック=pos2 で\n§7このリングの矩形範囲を選び直します")
                .addButtonAt(25, "§e§l対戦ボタンを登録", Material.STONE_BUTTON,
                        "§7クリック後、設置済みのボタンを右クリックすると"
                                + "\n§7そのボタンが「対戦待ち登録ボタン」になります"
                                + "\n§7(もう一度登録すると解除)"
                                + "\n§7現在の登録数: §e" + module.getButtonCount() + " 個")
                .addButtonAt(37, "§c§lリングを削除", Material.TNT,
                        "§7このリングを削除します（設定も消えます）")
                .addButtonAt(40, "§7コアメニューへ戻る", Material.ARROW)
                .addButtonAt(44, "§c閉じる", Material.BARRIER)
                .then((result, p) -> {
                    if (result.slot == null) {
                        return;
                    }
                    ClaimRegion currentClaim = resolveManaged(p, claimKey);
                    if (currentClaim == null) {
                        return;
                    }
                    RingRegion current = module.getRingById(ring.ringId());
                    if (current == null && result.slot != 40 && result.slot != 44) {
                        p.sendMessage("§cリングは既に削除されています");
                        ChestUI.closeMenu(p);
                        return;
                    }
                    switch (result.slot) {
                        case 10 -> {
                            current.setKeepInventory(!current.isKeepInventory());
                            module.saveAll();
                            openRing(p, currentClaim, current);
                        }
                        case 11 -> {
                            current.setInstantRespawn(!current.isInstantRespawn());
                            module.saveAll();
                            openRing(p, currentClaim, current);
                        }
                        case 12 -> {
                            current.setDuelMode(!current.isDuelMode());
                            module.saveAll();
                            p.sendMessage(current.isDuelMode()
                                    ? "§aDuelモードを有効にしました。Duel 開始まで攻撃できなくなります"
                                    : "§eDuelモードを無効にしました。リング内は常時 PVP になります");
                            openRing(p, currentClaim, current);
                        }
                        case 13 -> {
                            current.setAutoMatch(!current.isAutoMatch());
                            module.saveAll();
                            openRing(p, currentClaim, current);
                        }
                        case 14 -> {
                            current.setStartMode(current.getStartMode().next());
                            module.saveAll();
                            openRing(p, currentClaim, current);
                        }
                        case 15 -> setRespawnPoint(p, currentClaim, current);
                        case 16 -> {
                            current.setCleanupMode(current.getCleanupMode().next());
                            module.saveAll();
                            if (current.getCleanupMode() == RingRegion.CleanupMode.RETURN_CHEST
                                    && current.getReturnChest() == null) {
                                p.sendMessage("§e返却モードにしました。「返却チェストを指定」でラージチェストを登録してください");
                            }
                            openRing(p, currentClaim, current);
                        }
                        case 21 -> {
                            module.beginChestRegistration(p, current.ringId());
                            ChestUI.closeMenu(p);
                            p.sendMessage("§a登録したいラージチェストを右クリックしてください");
                        }
                        case 19 -> setDuelPos(p, currentClaim, current, true);
                        case 20 -> setDuelPos(p, currentClaim, current, false);
                        case 22 -> {
                            module.beginReselect(p, current);
                            ChestUI.closeMenu(p);
                        }
                        case 25 -> {
                            module.beginButtonRegistration(p);
                            ChestUI.closeMenu(p);
                            p.sendMessage("§a登録したいボタンを右クリックしてください");
                            p.sendMessage("§7(石のボタン・木のボタンなど設置済みのボタンが対象です)");
                        }
                        case 37 -> {
                            module.deleteRing(current);
                            p.sendMessage("§eリングを削除しました");
                            land().getMenu().openMain(p, currentClaim);
                        }
                        case 40 -> openRingList(p, currentClaim);
                        case 44 -> ChestUI.closeMenu(p);
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    private void openRingList(Player player, ClaimRegion claim) {
        String claimKey = claim.key();
        ChestUI.Builder builder = ChestUI.builder()
                .title("§c§lリング一覧")
                .size(45);
        Map<Integer, String> slotMap = new LinkedHashMap<>();
        int slot = 10;
        for (RingRegion ring : module.getRingsForClaim(claimKey)) {
            if (slot >= 35) {
                break;
            }
            DuelSession session = module.getSessionInRing(ring);
            builder.addButtonAt(slot, "§c§l" + ring.getName(), Material.IRON_SWORD,
                    "§7サイズ: §e" + ring.getSizeX() + "×" + ring.getSizeZ()
                            + "\n§7自動マッチング: " + (ring.isAutoMatch() ? "§a有効" : "§c無効")
                            + "\n§7Duel: " + (session == null ? "§8進行中なし" : "§e進行中")
                            + "\n\n§eクリックで設定を開く");
            slotMap.put(slot, ring.ringId());
            slot++;
        }
        builder.addButtonAt(40, "§a§l新しいリングを作成", Material.LIME_CONCRETE,
                        "§7チャットで名前を入力してから\n§7pos1/pos2 の範囲選択を開始します")
                .addButtonAt(44, "§7コアメニューへ戻る", Material.ARROW)
                .then((result, p) -> {
                    if (result.slot == null) {
                        return;
                    }
                    ClaimRegion currentClaim = resolveManaged(p, claimKey);
                    if (currentClaim == null) {
                        return;
                    }
                    if (result.slot == 40) {
                        askCreateName(p, currentClaim);
                        return;
                    }
                    if (result.slot == 44) {
                        land().getMenu().openMain(p, currentClaim);
                        return;
                    }
                    String ringId = slotMap.get(result.slot);
                    if (ringId == null) {
                        return;
                    }
                    RingRegion ring = module.getRingById(ringId);
                    if (ring == null) {
                        p.sendMessage("§cリングは既に削除されています");
                        openRingList(p, currentClaim);
                        return;
                    }
                    openRing(p, currentClaim, ring);
                })
                .show(player);
    }

    private void askCreateName(Player player, ClaimRegion claim) {
        ChestUI.closeMenu(player);
        ChestUI.openChat(player, "作成するリング名", "ring", input -> runSync(() -> {
            ClaimRegion current = resolveManaged(player, claim.key());
            if (current == null) {
                return;
            }
            String name = input == null ? "" : input.trim();
            String error = module.beginSelection(player, current, name);
            if (error != null) {
                player.sendMessage("§c" + error);
                openRingList(player, current);
            }
        }));
    }

    private void setRespawnPoint(Player player, ClaimRegion claim, RingRegion ring) {
        Location loc = player.getLocation().clone();
        if (!ring.isValidRespawnPoint(loc)) {
            player.sendMessage("§c復活地点はリングから " + RingRegion.RESPAWN_MAX_DISTANCE
                    + " ブロック以内に設定してください");
            return;
        }
        if (!claim.containsHorizontal(loc)) {
            player.sendMessage("§c復活地点は保護された土地の中に設定してください");
            return;
        }
        ring.setRespawnPoint(loc);
        module.saveAll();
        player.sendMessage("§a復活地点を現在地に設定しました " + formatLoc(loc));
        openRing(player, claim, ring);
    }

    private void setDuelPos(Player player, ClaimRegion claim, RingRegion ring, boolean first) {
        Location loc = player.getLocation().clone();
        if (!ring.contains(loc)) {
            player.sendMessage("§c開始位置はリングの中に設定してください");
            return;
        }
        if (first) {
            ring.setPos1(loc);
        } else {
            ring.setPos2(loc);
        }
        if (ring.getPos1() != null && ring.getPos2() != null
                && ring.getPos1().distance(ring.getPos2()) < RingRegion.DUEL_MIN_DISTANCE) {
            player.sendMessage("§epos1 と pos2 は " + RingRegion.DUEL_MIN_DISTANCE
                    + " ブロック以上離すことをおすすめします (現在 "
                    + String.format("%.1f", ring.getPos1().distance(ring.getPos2())) + " ブロック)");
        }
        module.saveAll();
        player.sendMessage("§a" + (first ? "pos1" : "pos2") + " を現在地に設定しました " + formatLoc(loc));
        openRing(player, claim, ring);
    }

    // ================= Duel 開始 UI =================

    /** リング内で素手右クリックすると開く Duel メニュー。 */
    public void openDuel(Player player, RingRegion ring) {
        DuelSession session = module.getSessionInRing(ring);
        UUID requester = module.getPendingRequester(player.getUniqueId(), ring);
        Player requesterPlayer = requester == null ? null : Bukkit.getPlayer(requester);

        ChestUI.Builder builder = ChestUI.builder()
                .title("§c§lDuel - 対戦相手を選択")
                .size(54);

        if (session != null) {
            builder.addButtonAt(4, "§cこのリングでは試合が進行中です", Material.BARRIER,
                    "§7試合が終わるまでお待ちください");
        } else if (requesterPlayer != null) {
            builder.addButtonAt(4, "§a§l承諾して Duel 開始！", Material.LIME_CONCRETE,
                    "§e" + requesterPlayer.getName() + " §7からの申し込みを承諾して\n§7試合を開始します");
        } else {
            builder.addButtonAt(4, "§7対戦したい相手を選択してください", Material.IRON_SWORD,
                    "§7リング内にいるプレイヤーへ Duel を申し込めます"
                            + "\n§7相手が承諾すると 3・2・1 → FIGHT で試合開始！");
        }

        Map<Integer, UUID> slotMap = new LinkedHashMap<>();
        int slot = 9;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) {
                break;
            }
            if (online.getUniqueId().equals(player.getUniqueId())
                    || !ring.contains(online.getLocation())
                    || module.getSession(online.getUniqueId()) != null) {
                continue;
            }
            builder.addPlayerHeadAt(slot, "§f" + online.getName(), online,
                    "§7クリックで Duel を申し込みます");
            slotMap.put(slot, online.getUniqueId());
            slot++;
        }
        if (slotMap.isEmpty() && session == null) {
            builder.addButtonAt(22, "§7リング内に対戦相手がいません", Material.GRAY_DYE,
                    "§7相手にもリングの中へ入ってもらってください");
        }
        builder.addButtonAt(53, "§c閉じる", Material.BARRIER);
        builder.then((result, p) -> {
            if (result.slot == null) {
                return;
            }
            RingRegion current = module.getRingById(ring.ringId());
            if (current == null || !module.isRingActive(current)) {
                p.sendMessage("§cこのリングは現在利用できません");
                ChestUI.closeMenu(p);
                return;
            }
            if (result.slot == 53) {
                ChestUI.closeMenu(p);
                return;
            }
            if (result.slot == 4) {
                if (module.getPendingRequester(p.getUniqueId(), current) != null) {
                    ChestUI.closeMenu(p);
                    module.acceptDuel(p, current);
                }
                return;
            }
            UUID target = slotMap.get(result.slot);
            if (target == null) {
                return;
            }
            Player targetPlayer = Bukkit.getPlayer(target);
            ChestUI.closeMenu(p);
            if (targetPlayer == null || !current.contains(targetPlayer.getLocation())) {
                p.sendMessage("§c相手が見つからないかリングの外にいます");
                return;
            }
            module.requestDuel(p, targetPlayer, current);
        }).show(player);
    }
}
