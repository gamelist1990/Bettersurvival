package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ClaimLevel;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ClaimRegion;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ClaimSettings;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.LandProtectionModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.Party;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 土地保護コアの管理メニュー。
 * メイン / 燃料投入 / レベルアップ / ホワイトリスト / 保護設定 を提供する。
 */
public class LandMenu {

    public static final String FUEL_MENU_TYPE = "bs_land_fuel";
    /** 燃料メニューでアイテムを置ける編集可能スロット */
    public static final int[] FUEL_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};

    private final Loader plugin;
    private final LandProtectionModule module;

    public LandMenu(Loader plugin, LandProtectionModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    private void runSync(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    /** 操作の度に領域が存在するか確認する。 */
    private ClaimRegion resolve(Player player, String claimKey) {
        ClaimRegion claim = module.getClaimByKey(claimKey);
        if (claim == null) {
            player.sendMessage("§cこの保護コアは既に存在しません");
            ChestUI.closeMenu(player);
        }
        return claim;
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "§c0分 (燃料切れ)";
        }
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("日");
        }
        if (hours > 0) {
            sb.append(hours).append("時間");
        }
        sb.append(minutes).append("分");
        return sb.toString();
    }

    private static String formatRequirements(int level) {
        List<ClaimLevel.Requirement> requirements = ClaimLevel.upgradeRequirements(level);
        if (requirements.isEmpty()) {
            return "§aなし";
        }
        StringBuilder sb = new StringBuilder();
        for (ClaimLevel.Requirement req : requirements) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("§7- §e").append(LandProtectionModule.materialDisplayName(req.material()))
                    .append(" ×").append(req.amount());
        }
        return sb.toString();
    }

    // ================= メイン =================

    public void openMain(Player player, ClaimRegion claim) {
        String claimKey = claim.key();
        boolean active = claim.isActive();
        boolean debugOn = module.getVisualizer().isEnabled(player);
        String partyLine;
        if (claim.getPartyId() != null) {
            Party party = module.getPartyModule().getParty(claim.getPartyId());
            partyLine = party == null ? "§7共有: §8なし" : "§7共有: " + party.getColoredName();
        } else {
            partyLine = "§7共有: §8なし (個人所有)";
        }

        ChestUI.builder()
                .title("§6土地保護コア Lv." + claim.getLevel())
                .size(36)
                .addButtonAt(4, LandProtectionModule.ITEM_NAME, Material.LODESTONE,
                        "§7状態: " + (active ? "§a保護有効" : "§c保護無効(燃料切れ)")
                                + "\n§7レベル: §eLv." + claim.getLevel() + " §7(半径 " + claim.getRadius() + " ブロック)"
                                + "\n§7オーナー: §e" + (claim.getOwnerName().isEmpty() ? "未登録" : claim.getOwnerName())
                                + "\n" + partyLine
                                + "\n§7燃料: §e" + String.format("%.1f", claim.getFuelUnits()) + " ユニット"
                                + "\n§7残り維持期間: §e" + formatDuration(claim.remainingSeconds())
                                + "\n§7消費速度: §e" + String.format("%.0f", ClaimLevel.upkeepPerHour(claim.getLevel())) + " ユニット/時")
                .addButtonAt(10, "§e§l燃料を投入", Material.COAL,
                        "§7原木・板材・木炭・石炭などを投入して\n§7保護の維持期間を延長します"
                                + "\n§8板材=1 / 原木=4 / 石炭・木炭=8 / 石炭ブロック=72")
                .addButtonAt(12, "§b§lレベルアップ", Material.DIAMOND,
                        ClaimLevel.canUpgrade(claim.getLevel())
                                ? "§7鉱石を対価に保護範囲を拡大します"
                                + "\n§7次のレベル: §eLv." + (claim.getLevel() + 1)
                                + " §7(半径 " + ClaimLevel.radius(claim.getLevel() + 1) + ")"
                                + "\n§7必要素材:\n" + formatRequirements(claim.getLevel())
                                + (claim.getPartyId() == null && claim.getLevel() + 1 > ClaimLevel.PERSONAL_MAX_LEVEL
                                        ? "\n§c個人所有は Lv." + ClaimLevel.PERSONAL_MAX_LEVEL + " までです"
                                        : "")
                                : "§a最大レベルに到達しています")
                .addButtonAt(14, "§a§lホワイトリスト", Material.PLAYER_HEAD,
                        "§7登録したプレイヤーはこのエリア内で\n§7制限を受けずに行動できます"
                                + "\n§7登録数: §e" + claim.getWhitelist().size() + "人")
                .addButtonAt(16, "§d§l保護設定", Material.COMPARATOR,
                        "§7部外者への制限内容や\n§7侵入通知をカスタマイズします")
                .addButtonAt(20, (debugOn ? "§a§lデバッグ境界線: ON" : "§7デバッグ境界線: OFF"), Material.GLOWSTONE_DUST,
                        "§7保護範囲の境界線をパーティクルで表示します\n§7(自分にのみ見えます)")
                .addButtonAt(22, "§6§lパーティー共有", Material.WHITE_BANNER,
                        claim.getPartyId() != null
                                ? "§7クリックで個人所有に戻します"
                                : "§7所属パーティーの共有所有物にします\n§7(メンバーが制限を受けなくなり、\n§7 サブリーダー以上が管理できるようになります)")
                .addButtonAt(35, "§c閉じる", Material.BARRIER)
                .then((result, p) -> {
                    if (result.slot == null) {
                        return;
                    }
                    ClaimRegion current = resolve(p, claimKey);
                    if (current == null) {
                        return;
                    }
                    switch (result.slot) {
                        case 10 -> openFuel(p, current);
                        case 12 -> openUpgrade(p, current);
                        case 14 -> openWhitelist(p, current);
                        case 16 -> openSettings(p, current);
                        case 20 -> {
                            boolean enabled = module.getVisualizer().toggle(p);
                            p.sendMessage(enabled
                                    ? "§aデバッグ境界線を表示します (緑=アクセス可 / 赤=他人 / 灰=無効)"
                                    : "§eデバッグ境界線を非表示にしました");
                            openMain(p, current);
                        }
                        case 22 -> {
                            String error = module.togglePartyShare(p, current);
                            if (error != null) {
                                p.sendMessage("§c" + error);
                            } else {
                                p.sendMessage(current.getPartyId() != null
                                        ? "§aこの保護エリアをパーティー共有にしました"
                                        : "§eこの保護エリアを個人所有に戻しました");
                            }
                            openMain(p, current);
                        }
                        case 35 -> ChestUI.closeMenu(p);
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    // ================= 燃料 =================

    public void openFuel(Player player, ClaimRegion claim) {
        String claimKey = claim.key();
        ChestUI.builder()
                .title("§6燃料投入 (残り: " + formatDuration(claim.remainingSeconds()) + ")")
                .size(27)
                .type(FUEL_MENU_TYPE)
                .editableSlots(FUEL_SLOTS)
                .addButtonAt(4, "§e燃料を下の段に置いて「投入」を押してください", Material.OAK_SIGN,
                        "§7燃料になるもの: 原木系 / 板材系 / 木炭 / 石炭 / 石炭ブロック"
                                + "\n§8板材=1 / 原木=4 / 石炭・木炭=8 / 石炭ブロック=72 ユニット"
                                + "\n§7現在の燃料: §e" + String.format("%.1f", claim.getFuelUnits()) + " ユニット")
                .addButtonAt(21, "§a§l投入する", Material.LIME_CONCRETE,
                        "§7置いたアイテムを燃料に変換します\n§7燃料にならないアイテムは返却されます")
                .addButtonAt(23, "§7戻る", Material.ARROW)
                .then((result, p) -> {
                    if (result.slot == null) {
                        return;
                    }
                    ClaimRegion current = resolve(p, claimKey);
                    if (current == null) {
                        return;
                    }
                    if (result.slot == 21) {
                        ChestUI open = ChestUI.getOpenMenu(p);
                        if (open == null) {
                            return;
                        }
                        Inventory inventory = open.getInventory();
                        double added = module.depositFuel(p, current, inventory);
                        if (added > 0) {
                            p.sendMessage("§a燃料を §e" + String.format("%.1f", added)
                                    + " ユニット §a投入しました (残り維持期間: " + formatDuration(current.remainingSeconds()) + ")");
                        } else {
                            p.sendMessage("§c燃料になるアイテムがありません");
                        }
                        openFuel(p, current);
                        return;
                    }
                    if (result.slot == 23) {
                        openMain(p, current);
                    }
                })
                .show(player);
    }

    // ================= レベルアップ =================

    public void openUpgrade(Player player, ClaimRegion claim) {
        String claimKey = claim.key();
        int level = claim.getLevel();
        ChestUI.Builder builder = ChestUI.builder()
                .title("§bレベルアップ")
                .size(27);
        if (!ClaimLevel.canUpgrade(level)) {
            builder.addButtonAt(13, "§a最大レベルです", Material.NETHER_STAR,
                    "§7このコアは既に最大レベル (Lv." + ClaimLevel.MAX_LEVEL + ") です");
        } else {
            builder.addButtonAt(11, "§bLv." + level + " §f→ §bLv." + (level + 1), Material.EXPERIENCE_BOTTLE,
                    "§7保護半径: §e" + ClaimLevel.radius(level) + " §f→ §e" + ClaimLevel.radius(level + 1)
                            + "\n§7燃料消費: §e" + String.format("%.0f", ClaimLevel.upkeepPerHour(level))
                            + " §f→ §e" + String.format("%.0f", ClaimLevel.upkeepPerHour(level + 1)) + " ユニット/時"
                            + "\n\n§7必要素材:\n" + formatRequirements(level)
                            + (claim.getPartyId() == null && level + 1 > ClaimLevel.PERSONAL_MAX_LEVEL
                                    ? "\n§c個人所有は Lv." + ClaimLevel.PERSONAL_MAX_LEVEL + " までです"
                                    : "")
                            + (level + 1 > ClaimLevel.PERSONAL_MAX_LEVEL
                                    ? "\n§eLv." + ClaimLevel.PERSONAL_MAX_LEVEL + "以降はオーナー/副リーダーのみ"
                                    : ""));
            builder.addButtonAt(15, "§a§lレベルアップする", Material.NETHER_STAR,
                    "§7インベントリから必要素材を消費します");
        }
        builder.addButtonAt(26, "§7戻る", Material.ARROW);
        builder.then((result, p) -> {
            if (result.slot == null) {
                return;
            }
            ClaimRegion current = resolve(p, claimKey);
            if (current == null) {
                return;
            }
            if (result.slot == 15 && ClaimLevel.canUpgrade(current.getLevel())) {
                String error = module.tryUpgrade(p, current);
                if (error != null) {
                    p.sendMessage("§c" + error);
                } else {
                    p.sendMessage("§a保護コアが §eLv." + current.getLevel()
                            + " §aになりました！ (半径 " + current.getRadius() + " ブロック)");
                }
                openUpgrade(p, current);
                return;
            }
            if (result.slot == 26) {
                openMain(p, current);
            }
        }).show(player);
    }

    // ================= ホワイトリスト =================

    public void openWhitelist(Player player, ClaimRegion claim) {
        String claimKey = claim.key();
        Map<UUID, String> whitelist = new LinkedHashMap<>(claim.getWhitelist());
        int entries = whitelist.size();
        int size = Math.min(54, Math.max(27, 9 * ((entries + 8) / 9) + 18));

        ChestUI.Builder builder = ChestUI.builder()
                .title("§aホワイトリスト (" + entries + "人)")
                .size(size);
        Map<Integer, UUID> slotMap = new LinkedHashMap<>();
        int slot = 0;
        for (Map.Entry<UUID, String> entry : whitelist.entrySet()) {
            if (slot >= size - 9) {
                break;
            }
            Player online = Bukkit.getPlayer(entry.getKey());
            String label = "§f" + entry.getValue();
            String lore = "§7このエリア内で制限を受けません\n§7(コアの操作・破壊は不可)\n\n§cクリックで登録解除";
            if (online != null) {
                builder.addPlayerHeadAt(slot, label, online, lore);
            } else {
                builder.addButtonAt(slot, label, Material.PLAYER_HEAD, lore);
            }
            slotMap.put(slot, entry.getKey());
            slot++;
        }
        builder.addButtonAt(size - 7, "§a§lオンラインから追加", Material.EMERALD,
                "§7オンラインのプレイヤーを選んで追加します");
        builder.addButtonAt(size - 5, "§e§l名前で追加", Material.NAME_TAG,
                "§7プレイヤー名を入力して追加します\n§7(過去にサーバーへ参加したプレイヤーのみ)");
        builder.addButtonAt(size - 1, "§7戻る", Material.ARROW);
        builder.then((result, p) -> {
            if (result.slot == null) {
                return;
            }
            ClaimRegion current = resolve(p, claimKey);
            if (current == null) {
                return;
            }
            if (result.slot == size - 1) {
                openMain(p, current);
                return;
            }
            if (result.slot == size - 7) {
                openWhitelistAdd(p, current);
                return;
            }
            if (result.slot == size - 5) {
                askWhitelistName(p, current);
                return;
            }
            UUID target = slotMap.get(result.slot);
            if (target != null) {
                String name = current.getWhitelist().get(target);
                module.removeWhitelist(current, target);
                p.sendMessage("§e" + name + " をホワイトリストから削除しました");
                openWhitelist(p, current);
            }
        }).show(player);
    }

    private void openWhitelistAdd(Player player, ClaimRegion claim) {
        String claimKey = claim.key();
        ChestUI.Builder builder = ChestUI.builder()
                .title("§a追加するプレイヤーを選択")
                .size(54);
        Map<Integer, UUID> slotMap = new LinkedHashMap<>();
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) {
                break;
            }
            if (online.getUniqueId().equals(player.getUniqueId())
                    || claim.getWhitelist().containsKey(online.getUniqueId())) {
                continue;
            }
            builder.addPlayerHeadAt(slot, "§f" + online.getName(), online, "§7クリックでホワイトリストに追加");
            slotMap.put(slot, online.getUniqueId());
            slot++;
        }
        if (slotMap.isEmpty()) {
            builder.addButtonAt(4, "§7追加できるプレイヤーがいません", Material.GRAY_DYE, "");
        }
        builder.addButtonAt(53, "§7戻る", Material.ARROW);
        builder.then((result, p) -> {
            if (result.slot == null) {
                return;
            }
            ClaimRegion current = resolve(p, claimKey);
            if (current == null) {
                return;
            }
            if (result.slot == 53) {
                openWhitelist(p, current);
                return;
            }
            UUID target = slotMap.get(result.slot);
            if (target == null) {
                return;
            }
            Player online = Bukkit.getPlayer(target);
            if (online == null) {
                openWhitelistAdd(p, current);
                return;
            }
            module.addWhitelist(current, online.getUniqueId(), online.getName());
            p.sendMessage("§a" + online.getName() + " をホワイトリストに追加しました");
            openWhitelist(p, current);
        }).show(player);
    }

    private void askWhitelistName(Player player, ClaimRegion claim) {
        String claimKey = claim.key();
        ChestUI.closeMenu(player);
        ChestUI.openChat(player, "追加するプレイヤー名を入力", "", input -> runSync(() -> {
            ClaimRegion current = module.getClaimByKey(claimKey);
            if (current == null) {
                player.sendMessage("§cこの保護コアは既に存在しません");
                return;
            }
            String name = input == null ? "" : input.trim();
            if (name.isEmpty()) {
                openWhitelist(player, current);
                return;
            }
            org.bukkit.OfflinePlayer target = Bukkit.getPlayerExact(name);
            if (target == null) {
                target = Bukkit.getOfflinePlayerIfCached(name);
            }
            if (target == null || target.getName() == null) {
                player.sendMessage("§cプレイヤー「" + name + "」が見つかりません (サーバー参加歴が必要です)");
                openWhitelist(player, current);
                return;
            }
            module.addWhitelist(current, target.getUniqueId(), target.getName());
            player.sendMessage("§a" + target.getName() + " をホワイトリストに追加しました");
            openWhitelist(player, current);
        }));
    }

    // ================= 保護設定 =================

    public void openSettings(Player player, ClaimRegion claim) {
        String claimKey = claim.key();
        ClaimSettings settings = claim.getSettings();
        ChestUI.builder()
                .title("§d保護設定")
                .size(45)
                .addButtonAt(10, toggleLabel("コンテナを開けなくする", settings.isBlockContainers()), Material.CHEST,
                        "§7チェスト・かまど・シュルカーボックスなど\n" + toggleLore(settings.isBlockContainers()))
                .addButtonAt(11, toggleLabel("ドアを開けなくする", settings.isBlockDoors()), Material.OAK_DOOR,
                        "§7ドア・トラップドア・フェンスゲート\n" + toggleLore(settings.isBlockDoors()))
                .addButtonAt(12, toggleLabel("ボタン等を押せなくする", settings.isBlockSwitches()), Material.STONE_BUTTON,
                        "§7ボタン・レバー・感圧板\n" + toggleLore(settings.isBlockSwitches()))
                .addButtonAt(13, toggleLabel("ブロック設置を禁止", settings.isBlockPlace()), Material.BRICKS,
                        toggleLore(settings.isBlockPlace()))
                .addButtonAt(14, toggleLabel("ブロック破壊を禁止", settings.isBlockBreak()), Material.IRON_PICKAXE,
                        toggleLore(settings.isBlockBreak()))
                .addButtonAt(15, toggleLabel("領地内PVP", settings.isPvpEnabled()), Material.DIAMOND_SWORD,
                        toggleLore(settings.isPvpEnabled()) + "\n§7無効時は領地内でのプレイヤー同士の攻撃ができません")
                .addButtonAt(28, "§e侵入通知: " + settings.getNotifyMode().getDisplayName(), Material.BELL,
                        "§7部外者がエリアに入った時の通知方法\n§7クリックで切替: タイトル → アクションバー → なし")
                .addButtonAt(30, "§eタイトル文を編集", Material.PAPER,
                        "§7現在: §f" + settings.getTitleText()
                                + "\n§7プレースホルダー: §e" + ClaimSettings.PLACEHOLDER_OWNER + " §7= オーナー/パーティー名")
                .addButtonAt(31, "§eサブタイトル文を編集", Material.PAPER,
                        "§7現在: §f" + settings.getSubtitleText())
                .addButtonAt(32, "§eアクションバー文を編集", Material.PAPER,
                        "§7現在: §f" + settings.getActionbarText())
                .addButtonAt(44, "§7戻る", Material.ARROW)
                .then((result, p) -> {
                    if (result.slot == null) {
                        return;
                    }
                    ClaimRegion current = resolve(p, claimKey);
                    if (current == null) {
                        return;
                    }
                    ClaimSettings s = current.getSettings();
                    switch (result.slot) {
                        case 10 -> {
                            s.setBlockContainers(!s.isBlockContainers());
                            module.saveAll();
                            openSettings(p, current);
                        }
                        case 11 -> {
                            s.setBlockDoors(!s.isBlockDoors());
                            module.saveAll();
                            openSettings(p, current);
                        }
                        case 12 -> {
                            s.setBlockSwitches(!s.isBlockSwitches());
                            module.saveAll();
                            openSettings(p, current);
                        }
                        case 13 -> {
                            s.setBlockPlace(!s.isBlockPlace());
                            module.saveAll();
                            openSettings(p, current);
                        }
                        case 14 -> {
                            s.setBlockBreak(!s.isBlockBreak());
                            module.saveAll();
                            openSettings(p, current);
                        }
                        case 15 -> {
                            s.setPvpEnabled(!s.isPvpEnabled());
                            module.saveAll();
                            openSettings(p, current);
                        }
                        case 28 -> {
                            s.setNotifyMode(s.getNotifyMode().next());
                            module.saveAll();
                            openSettings(p, current);
                        }
                        case 30 -> askText(p, claimKey, "タイトル文を入力", s.getTitleText(),
                                (cl, text) -> cl.getSettings().setTitleText(text));
                        case 31 -> askText(p, claimKey, "サブタイトル文を入力", s.getSubtitleText(),
                                (cl, text) -> cl.getSettings().setSubtitleText(text));
                        case 32 -> askText(p, claimKey, "アクションバー文を入力", s.getActionbarText(),
                                (cl, text) -> cl.getSettings().setActionbarText(text));
                        case 44 -> openMain(p, current);
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    private void askText(Player player, String claimKey, String prompt, String current,
                         java.util.function.BiConsumer<ClaimRegion, String> apply) {
        ChestUI.closeMenu(player);
        ChestUI.openChat(player, prompt + " (%owner% が使えます)", current, input -> runSync(() -> {
            ClaimRegion claim = module.getClaimByKey(claimKey);
            if (claim == null) {
                player.sendMessage("§cこの保護コアは既に存在しません");
                return;
            }
            apply.accept(claim, input == null ? "" : input);
            module.saveAll();
            player.sendMessage("§a通知文を更新しました");
            openSettings(player, claim);
        }));
    }

    private static String toggleLabel(String name, boolean enabled) {
        return (enabled ? "§a§l[有効] §f" : "§7[無効] §f") + name;
    }

    private static String toggleLore(boolean enabled) {
        return enabled ? "§a現在: 有効 §7(クリックで無効化)" : "§c現在: 無効 §7(クリックで有効化)";
    }
}
