package org.pexserver.koukunn.bettersurvival.Modules.Feature.Party;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * パーティー（ギルド）機能の中核モジュール。
 *
 * リーダー / サブリーダー / メンバー の3階級、
 * パーティー名・イメージカラー（他パーティーと重複不可）・説明を管理し、
 * 土地保護のパーティー共有所有の基盤となる。
 */
public class PartyModule implements Listener {

    public static final String FEATURE_KEY = "party";
    public static final int MAX_NAME_LENGTH = 16;
    private static final long INVITE_EXPIRE_MILLIS = 5L * 60L * 1000L;

    private final ToggleModule toggle;
    private final PartyStore store;
    private final Map<UUID, Party> parties = new LinkedHashMap<>();
    /** プレイヤー -> 所属パーティー */
    private final Map<UUID, UUID> memberIndex = new LinkedHashMap<>();
    /** 招待: 招待されたプレイヤー -> (パーティーID -> 有効期限ミリ秒) */
    private final Map<UUID, Map<UUID, Long>> invites = new ConcurrentHashMap<>();

    public PartyModule(Loader plugin, ToggleModule toggle) {
        this.toggle = toggle;
        this.store = new PartyStore(plugin.getConfigManager());
        parties.putAll(store.loadAll());
        rebuildIndex();
    }

    public boolean isFeatureEnabled() {
        return toggle.getGlobal(FEATURE_KEY);
    }

    private void rebuildIndex() {
        memberIndex.clear();
        for (Party party : parties.values()) {
            for (UUID member : party.getAllMembers()) {
                memberIndex.put(member, party.getId());
            }
        }
    }

    private void save() {
        store.saveAll(parties.values());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 名前変更を追従してオフライン表示名を更新する
        Party party = getPartyOf(player.getUniqueId());
        if (party != null) {
            party.rememberName(player.getUniqueId(), player.getName());
            updateDisplayName(player, party);
            save();
        } else {
            resetDisplayName(player);
        }
    }

    // ================= 参照系 =================

    public Party getParty(UUID partyId) {
        return partyId == null ? null : parties.get(partyId);
    }

    public Party getPartyOf(UUID playerUuid) {
        UUID partyId = memberIndex.get(playerUuid);
        if (partyId == null) {
            return null;
        }
        Party party = parties.get(partyId);
        if (party == null || !party.isMember(playerUuid)) {
            memberIndex.remove(playerUuid);
            return null;
        }
        return party;
    }

    public List<Party> getParties() {
        return new ArrayList<>(parties.values());
    }

    public boolean isNameUsed(String name) {
        for (Party party : parties.values()) {
            if (party.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public boolean isColorUsed(PartyColor color, Party except) {
        for (Party party : parties.values()) {
            if (except != null && party.getId().equals(except.getId())) {
                continue;
            }
            if (party.getColor() == color) {
                return true;
            }
        }
        return false;
    }

    /** パーティー名として使える形へ正規化・検証する。不正なら null。 */
    public String sanitizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.replaceAll("(?i)§[0-9A-FK-ORX]", "").replace("§", "").trim();
        if (name.length() < 2 || name.length() > MAX_NAME_LENGTH) {
            return null;
        }
        return name;
    }

    // ================= 操作系 =================

    /** パーティーを作成する。失敗時はエラーメッセージ、成功時は null を返す。 */
    public String createParty(Player leader, String name, PartyColor color, String description) {
        if (getPartyOf(leader.getUniqueId()) != null) {
            return "既にパーティーに所属しています";
        }
        String sanitized = sanitizeName(name);
        if (sanitized == null) {
            return "パーティー名は2〜" + MAX_NAME_LENGTH + "文字で指定してください";
        }
        if (isNameUsed(sanitized)) {
            return "その名前は既に使われています";
        }
        if (isColorUsed(color, null)) {
            return "そのカラーは他のパーティーが使用中です";
        }
        Party party = new Party(UUID.randomUUID(), sanitized, color.name(), description, leader.getUniqueId());
        party.rememberName(leader.getUniqueId(), leader.getName());
        parties.put(party.getId(), party);
        memberIndex.put(leader.getUniqueId(), party.getId());
        updateDisplayName(leader, party);
        save();
        return null;
    }

    /** パーティーを解散する（リーダーのみ）。 */
    public String disband(Player actor, Party party) {
        if (party.rankOf(actor.getUniqueId()) != PartyRank.LEADER) {
            return "解散はリーダーのみ実行できます";
        }
        for (UUID member : party.getAllMembers()) {
            memberIndex.remove(member);
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                resetDisplayName(online);
                if (!online.getUniqueId().equals(actor.getUniqueId())) {
                    online.sendMessage("§cパーティー " + party.getColoredName() + " §cは解散されました");
                }
            }
        }
        parties.remove(party.getId());
        save();
        return null;
    }

    /** 招待を送る（サブリーダー以上）。 */
    public String invite(Player inviter, Party party, Player target) {
        PartyRank rank = party.rankOf(inviter.getUniqueId());
        if (rank == null || !rank.isAtLeast(PartyRank.CO_LEADER)) {
            return "招待はサブリーダー以上のみ実行できます";
        }
        if (target.getUniqueId().equals(inviter.getUniqueId())) {
            return "自分自身は招待できません";
        }
        if (getPartyOf(target.getUniqueId()) != null) {
            return target.getName() + " は既に別のパーティーに所属しています";
        }
        Map<UUID, Long> targetInvites = invites.computeIfAbsent(target.getUniqueId(), k -> new ConcurrentHashMap<>());
        Long existing = targetInvites.get(party.getId());
        if (existing != null && existing > System.currentTimeMillis()) {
            return "既に招待を送信済みです";
        }
        targetInvites.put(party.getId(), System.currentTimeMillis() + INVITE_EXPIRE_MILLIS);
        target.sendMessage("§d[パーティー] " + party.getColoredName() + " §fから招待が届きました。§e/party §fで確認できます (5分間有効)");
        return null;
    }

    /** 指定プレイヤーへの有効な招待一覧（期限切れは掃除される）。 */
    public List<Party> getPendingInvites(UUID target) {
        List<Party> out = new ArrayList<>();
        Map<UUID, Long> map = invites.get(target);
        if (map == null) {
            return out;
        }
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(e -> e.getValue() < now || !parties.containsKey(e.getKey()));
        for (UUID partyId : map.keySet()) {
            Party party = parties.get(partyId);
            if (party != null) {
                out.add(party);
            }
        }
        return out;
    }

    /** 招待を承認して加入する。 */
    public String acceptInvite(Player player, Party party) {
        Map<UUID, Long> map = invites.get(player.getUniqueId());
        Long expiry = map == null ? null : map.get(party.getId());
        if (expiry == null || expiry < System.currentTimeMillis()) {
            return "有効な招待がありません";
        }
        if (getPartyOf(player.getUniqueId()) != null) {
            return "既にパーティーに所属しています";
        }
        map.remove(party.getId());
        party.getMembers().add(player.getUniqueId());
        party.rememberName(player.getUniqueId(), player.getName());
        memberIndex.put(player.getUniqueId(), party.getId());
        updateDisplayName(player, party);
        save();
        broadcast(party, "§d[パーティー] §e" + player.getName() + " §fが加入しました");
        return null;
    }

    public void declineInvite(Player player, Party party) {
        Map<UUID, Long> map = invites.get(player.getUniqueId());
        if (map != null) {
            map.remove(party.getId());
        }
    }

    /** 脱退する。リーダーは脱退不可（解散を促す）。 */
    public String leave(Player player, Party party) {
        PartyRank rank = party.rankOf(player.getUniqueId());
        if (rank == PartyRank.LEADER) {
            return "リーダーは脱退できません。解散するか運営に相談してください";
        }
        if (rank == null) {
            return "パーティーに所属していません";
        }
        party.removeMember(player.getUniqueId());
        memberIndex.remove(player.getUniqueId());
        resetDisplayName(player);
        save();
        broadcast(party, "§d[パーティー] §e" + player.getName() + " §fが脱退しました");
        return null;
    }

    /** メンバーを追放する。 */
    public String kick(Player actor, Party party, UUID target) {
        PartyRank actorRank = party.rankOf(actor.getUniqueId());
        PartyRank targetRank = party.rankOf(target);
        if (actorRank == null || targetRank == null) {
            return "対象がパーティーに所属していません";
        }
        if (!actorRank.isAtLeast(PartyRank.CO_LEADER)) {
            return "追放はサブリーダー以上のみ実行できます";
        }
        if (targetRank.isAtLeast(actorRank)) {
            return "自分と同格以上のメンバーは追放できません";
        }
        String targetName = party.nameOf(target);
        party.removeMember(target);
        memberIndex.remove(target);
        save();
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            resetDisplayName(online);
            online.sendMessage("§cパーティー " + party.getColoredName() + " §cから追放されました");
        }
        broadcast(party, "§d[パーティー] §e" + targetName + " §fが追放されました");
        return null;
    }

    /** メンバーをサブリーダーへ昇格（リーダーのみ）。 */
    public String promote(Player actor, Party party, UUID target) {
        if (party.rankOf(actor.getUniqueId()) != PartyRank.LEADER) {
            return "昇格はリーダーのみ実行できます";
        }
        if (party.rankOf(target) != PartyRank.MEMBER) {
            return "メンバーのみ昇格できます";
        }
        party.getMembers().remove(target);
        party.getCoLeaders().add(target);
        save();
        broadcast(party, "§d[パーティー] §e" + party.nameOf(target) + " §fがサブリーダーに昇格しました");
        return null;
    }

    /** サブリーダーをメンバーへ降格（リーダーのみ）。 */
    public String demote(Player actor, Party party, UUID target) {
        if (party.rankOf(actor.getUniqueId()) != PartyRank.LEADER) {
            return "降格はリーダーのみ実行できます";
        }
        if (party.rankOf(target) != PartyRank.CO_LEADER) {
            return "サブリーダーのみ降格できます";
        }
        party.getCoLeaders().remove(target);
        party.getMembers().add(target);
        save();
        broadcast(party, "§d[パーティー] §e" + party.nameOf(target) + " §fがメンバーに降格しました");
        return null;
    }

    /** パーティー名を変更（リーダーのみ）。 */
    public String rename(Player actor, Party party, String newName) {
        if (party.rankOf(actor.getUniqueId()) != PartyRank.LEADER) {
            return "名前変更はリーダーのみ実行できます";
        }
        String sanitized = sanitizeName(newName);
        if (sanitized == null) {
            return "パーティー名は2〜" + MAX_NAME_LENGTH + "文字で指定してください";
        }
        if (!sanitized.equalsIgnoreCase(party.getName()) && isNameUsed(sanitized)) {
            return "その名前は既に使われています";
        }
        party.setName(sanitized);
        save();
        refreshDisplayNames(party);
        broadcast(party, "§d[パーティー] §fパーティー名が " + party.getColoredName() + " §fに変更されました");
        return null;
    }

    /** イメージカラーを変更（リーダーのみ、他パーティーと重複不可）。 */
    public String recolor(Player actor, Party party, PartyColor color) {
        if (party.rankOf(actor.getUniqueId()) != PartyRank.LEADER) {
            return "カラー変更はリーダーのみ実行できます";
        }
        if (isColorUsed(color, party)) {
            return "そのカラーは他のパーティーが使用中です";
        }
        party.setColorKey(color.name());
        save();
        refreshDisplayNames(party);
        broadcast(party, "§d[パーティー] §fイメージカラーが " + color.getLegacyCode() + color.getDisplayName() + " §fに変更されました");
        return null;
    }

    /** 説明を変更（サブリーダー以上）。 */
    public String redescribe(Player actor, Party party, String description) {
        PartyRank rank = party.rankOf(actor.getUniqueId());
        if (rank == null || !rank.isAtLeast(PartyRank.CO_LEADER)) {
            return "説明変更はサブリーダー以上のみ実行できます";
        }
        party.setDescription(description == null ? "" : description.trim());
        save();
        return null;
    }

    /** パーティー全員（オンライン）へメッセージを送る。 */
    public void broadcast(Party party, String message) {
        for (UUID member : party.getAllMembers()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                online.sendMessage(message);
            }
        }
    }

    // ================= 表示名・チャット =================

    /**
     * パーティー設定に応じてプレイヤーの表示名（チャット・ネームタグ・タブ）を更新する。
     */
    public void updateDisplayName(Player player, Party party) {
        String name = player.getName();
        String prefix = party.isNameTagPrefix() ? "[" + party.getName() + "] " : "";
        String color = party.isNameTagColor() ? party.getColor().getLegacyCode() : "§f";
        String display = color + prefix + name;
        Component component = ComponentUtils.legacy(display);
        player.displayName(component);
        player.playerListName(component);
    }

    public void resetDisplayName(Player player) {
        Component component = ComponentUtils.legacy("§f" + player.getName());
        player.displayName(component);
        player.playerListName(component);
    }

    public void refreshDisplayNames(Party party) {
        for (UUID member : party.getAllMembers()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                updateDisplayName(online, party);
            }
        }
    }

    // ================= パーティー設定 =================

    public String setFriendlyFire(Player actor, Party party, boolean enabled) {
        if (!party.rankOf(actor.getUniqueId()).isAtLeast(PartyRank.CO_LEADER)) {
            return "設定変更はサブリーダー以上のみ実行できます";
        }
        party.setFriendlyFire(enabled);
        save();
        broadcast(party, "§d[パーティー] §f味方同士の攻撃: " + (enabled ? "§a有効" : "§c無効"));
        return null;
    }

    public String setNameTagColor(Player actor, Party party, boolean enabled) {
        if (!party.rankOf(actor.getUniqueId()).isAtLeast(PartyRank.CO_LEADER)) {
            return "設定変更はサブリーダー以上のみ実行できます";
        }
        party.setNameTagColor(enabled);
        save();
        refreshDisplayNames(party);
        broadcast(party, "§d[パーティー] §fネームタグカラー: " + (enabled ? "§a有効" : "§c無効"));
        return null;
    }

    public String setNameTagPrefix(Player actor, Party party, boolean enabled) {
        if (!party.rankOf(actor.getUniqueId()).isAtLeast(PartyRank.CO_LEADER)) {
            return "設定変更はサブリーダー以上のみ実行できます";
        }
        party.setNameTagPrefix(enabled);
        save();
        refreshDisplayNames(party);
        broadcast(party, "§d[パーティー] §fパーティープレフィックス: " + (enabled ? "§a有効" : "§c無効"));
        return null;
    }
}
