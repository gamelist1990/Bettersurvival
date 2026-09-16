package org.pexserver.koukunn.bettersurvival.Modules.Feature.Party;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Party data is isolated by Otherworld group. */
public class PartyModule implements Listener {
    public static final String FEATURE_KEY = "party";
    public static final int MAX_NAME_LENGTH = 16;
    private static final long INVITE_EXPIRE_MILLIS = 5L * 60L * 1000L;

    private final Loader plugin;
    private final ToggleModule toggle;
    private final PartyStore store;
    /** Party UUID is globally unique; scope lives on each Party. */
    private final Map<UUID, Party> parties = new LinkedHashMap<>();
    /** scope -> (player -> party). The same player may belong to one party in every scope. */
    private final Map<String, Map<UUID, UUID>> memberIndex = new LinkedHashMap<>();
    /** invited player -> (party id -> expiry). Entries are filtered by current scope on read/accept. */
    private final Map<UUID, Map<UUID, Long>> invites = new ConcurrentHashMap<>();

    public PartyModule(Loader plugin, ToggleModule toggle) {
        this.plugin = plugin;
        this.toggle = toggle;
        this.store = new PartyStore(plugin.getConfigManager());
        parties.putAll(store.loadAll());
        rebuildIndex();
    }

    public boolean isFeatureEnabled() { return toggle.getGlobal(FEATURE_KEY); }

    public String scope(Player player) { return scope(player == null ? null : player.getWorld()); }

    public String scope(World world) {
        if (world == null || plugin.getOtherworldModule() == null) return "default";
        String group = plugin.getOtherworldModule().getGroup(world);
        return group == null || group.isBlank() ? "default" : group.toLowerCase(Locale.ROOT);
    }

    private String normalizeScope(String scope) {
        return scope == null || scope.isBlank() ? "default" : scope.toLowerCase(Locale.ROOT);
    }

    private Map<UUID, UUID> index(String scope) {
        return memberIndex.computeIfAbsent(normalizeScope(scope), ignored -> new LinkedHashMap<>());
    }

    private void rebuildIndex() {
        memberIndex.clear();
        for (Party party : parties.values()) {
            Map<UUID, UUID> scoped = index(party.getScope());
            for (UUID member : party.getAllMembers()) scoped.put(member, party.getId());
        }
    }

    private void save() { store.saveAll(parties.values()); }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) { refreshPlayerParty(event.getPlayer(), true); }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refreshPlayerParty(event.getPlayer(), false);
    }

    private void refreshPlayerParty(Player player, boolean rememberName) {
        Party party = getPartyOf(player.getUniqueId(), scope(player));
        if (party != null) {
            if (rememberName) {
                party.rememberName(player.getUniqueId(), player.getName());
                save();
            }
            updateDisplayName(player, party);
        } else {
            resetDisplayName(player);
        }
    }

    // ================= references =================

    public Party getParty(UUID partyId) { return partyId == null ? null : parties.get(partyId); }

    /** Backward-compatible lookup: online players use their current group; offline players use default. */
    public Party getPartyOf(UUID playerUuid) {
        Player online = Bukkit.getPlayer(playerUuid);
        return getPartyOf(playerUuid, online == null ? "default" : scope(online));
    }

    public Party getPartyOf(UUID playerUuid, World world) { return getPartyOf(playerUuid, scope(world)); }

    public Party getPartyOf(UUID playerUuid, String scope) {
        String normalized = normalizeScope(scope);
        UUID partyId = index(normalized).get(playerUuid);
        if (partyId == null) return null;
        Party party = parties.get(partyId);
        if (party == null || !normalized.equals(party.getScope()) || !party.isMember(playerUuid)) {
            index(normalized).remove(playerUuid);
            return null;
        }
        return party;
    }

    /** Legacy API returns only default-scope parties. */
    public List<Party> getParties() { return getParties("default"); }
    public List<Party> getParties(Player player) { return getParties(scope(player)); }
    public List<Party> getParties(String scope) {
        String normalized = normalizeScope(scope);
        return parties.values().stream().filter(p -> p.getScope().equals(normalized)).toList();
    }

    /** 現在のOtherworldグループで検索・自由参加できる公開Party。 */
    public List<Party> getPublicParties(Player player) {
        return getParties(player).stream()
                .filter(Party::isPublicParty)
                .sorted((a, b) -> Integer.compare(b.getAllMembers().size(), a.getAllMembers().size()))
                .toList();
    }

    public boolean isNameUsed(String name) { return isNameUsed("default", name, null); }
    public boolean isNameUsed(Player player, String name) { return isNameUsed(scope(player), name, null); }
    private boolean isNameUsed(String scope, String name, Party except) {
        for (Party party : getParties(scope)) {
            if (except != null && party.getId().equals(except.getId())) continue;
            if (party.getName().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public boolean isColorUsed(PartyColor color, Party except) {
        String scope = except == null ? "default" : except.getScope();
        return isColorUsed(scope, color, except);
    }
    public boolean isColorUsed(Player player, PartyColor color, Party except) {
        return isColorUsed(scope(player), color, except);
    }
    private boolean isColorUsed(String scope, PartyColor color, Party except) {
        for (Party party : getParties(scope)) {
            if (except != null && party.getId().equals(except.getId())) continue;
            if (party.getColor() == color) return true;
        }
        return false;
    }

    public String sanitizeName(String raw) {
        if (raw == null) return null;
        String name = raw.replaceAll("(?i)§[0-9A-FK-ORX]", "").replace("§", "").trim();
        return name.length() < 2 || name.length() > MAX_NAME_LENGTH ? null : name;
    }

    // ================= operations =================

    /** 新規Partyは安全側でプライベートから開始する。 */
    public String createParty(Player leader, String name, PartyColor color, String description) {
        String currentScope = scope(leader);
        if (getPartyOf(leader.getUniqueId(), currentScope) != null) return "このワールドグループでは既にパーティーに所属しています";
        String sanitized = sanitizeName(name);
        if (sanitized == null) return "パーティー名は2〜" + MAX_NAME_LENGTH + "文字で指定してください";
        if (isNameUsed(currentScope, sanitized, null)) return "このワールドグループではその名前は既に使われています";
        if (isColorUsed(currentScope, color, null)) return "このワールドグループではそのカラーは他のパーティーが使用中です";
        Party party = new Party(UUID.randomUUID(), currentScope, sanitized, color.name(), description, leader.getUniqueId());
        party.setPublicParty(false);
        party.rememberName(leader.getUniqueId(), leader.getName());
        parties.put(party.getId(), party);
        index(currentScope).put(leader.getUniqueId(), party.getId());
        updateDisplayName(leader, party);
        save();
        return null;
    }

    public String disband(Player actor, Party party) {
        if (!scope(actor).equals(party.getScope())) return "別のワールドグループのパーティーは操作できません";
        if (party.rankOf(actor.getUniqueId()) != PartyRank.LEADER) return "解散はリーダーのみ実行できます";
        for (UUID member : party.getAllMembers()) {
            index(party.getScope()).remove(member);
            Player online = Bukkit.getPlayer(member);
            if (online != null && scope(online).equals(party.getScope())) {
                resetDisplayName(online);
                if (!online.getUniqueId().equals(actor.getUniqueId())) online.sendMessage("§cパーティー " + party.getColoredName() + " §cは解散されました");
            }
        }
        parties.remove(party.getId());
        save();
        return null;
    }

    public String invite(Player inviter, Party party, Player target) {
        if (!scope(inviter).equals(party.getScope())) return "別のワールドグループのパーティーは操作できません";
        PartyRank rank = party.rankOf(inviter.getUniqueId());
        if (rank == null || !rank.isAtLeast(PartyRank.CO_LEADER)) return "招待はサブリーダー以上のみ実行できます";
        if (target.getUniqueId().equals(inviter.getUniqueId())) return "自分自身は招待できません";
        if (!scope(target).equals(party.getScope())) return "別のワールドグループのプレイヤーは招待できません";
        if (getPartyOf(target.getUniqueId(), party.getScope()) != null) return target.getName() + " はこのワールドグループで既にパーティーに所属しています";
        Map<UUID, Long> targetInvites = invites.computeIfAbsent(target.getUniqueId(), k -> new ConcurrentHashMap<>());
        Long existing = targetInvites.get(party.getId());
        if (existing != null && existing > System.currentTimeMillis()) return "既に招待を送信済みです";
        targetInvites.put(party.getId(), System.currentTimeMillis() + INVITE_EXPIRE_MILLIS);
        target.sendMessage("§d[パーティー] " + party.getColoredName() + " §fから招待が届きました。§e/party §fで確認できます (5分間有効)");
        return null;
    }

    public List<Party> getPendingInvites(UUID target) {
        Player online = Bukkit.getPlayer(target);
        String currentScope = online == null ? "default" : scope(online);
        List<Party> out = new ArrayList<>();
        Map<UUID, Long> map = invites.get(target);
        if (map == null) return out;
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(e -> e.getValue() < now || !parties.containsKey(e.getKey()));
        for (UUID partyId : map.keySet()) {
            Party party = parties.get(partyId);
            if (party != null && party.getScope().equals(currentScope)) out.add(party);
        }
        return out;
    }

    public String acceptInvite(Player player, Party party) {
        if (!scope(player).equals(party.getScope())) return "別のワールドグループのパーティーには加入できません";
        Map<UUID, Long> map = invites.get(player.getUniqueId());
        Long expiry = map == null ? null : map.get(party.getId());
        if (expiry == null || expiry < System.currentTimeMillis()) return "有効な招待がありません";
        if (getPartyOf(player.getUniqueId(), party.getScope()) != null) return "このワールドグループでは既にパーティーに所属しています";
        map.remove(party.getId());
        return addMember(player, party, "§d[パーティー] §e" + player.getName() + " §fが招待から加入しました");
    }

    /** 公開Partyへ検索画面から参加。プライベートPartyでは必ず拒否する。 */
    public String joinPublicParty(Player player, Party party) {
        if (party == null || !party.isPublicParty()) return "このパーティーは現在プライベートです";
        if (!scope(player).equals(party.getScope())) return "別のワールドグループのパーティーには加入できません";
        if (getPartyOf(player.getUniqueId(), party.getScope()) != null) return "このワールドグループでは既にパーティーに所属しています";
        return addMember(player, party, "§d[パーティー] §e" + player.getName() + " §fが公開検索から加入しました");
    }

    private String addMember(Player player, Party party, String joinMessage) {
        party.getMembers().add(player.getUniqueId());
        party.rememberName(player.getUniqueId(), player.getName());
        index(party.getScope()).put(player.getUniqueId(), party.getId());
        updateDisplayName(player, party);
        save();
        broadcast(party, joinMessage);
        return null;
    }

    public void declineInvite(Player player, Party party) {
        Map<UUID, Long> map = invites.get(player.getUniqueId());
        if (map != null) map.remove(party.getId());
    }

    public String leave(Player player, Party party) {
        if (!scope(player).equals(party.getScope())) return "別のワールドグループのパーティーは操作できません";
        PartyRank rank = party.rankOf(player.getUniqueId());
        if (rank == PartyRank.LEADER) return "リーダーは脱退できません。解散するか運営に相談してください";
        if (rank == null) return "パーティーに所属していません";
        party.removeMember(player.getUniqueId());
        index(party.getScope()).remove(player.getUniqueId());
        resetDisplayName(player);
        save();
        broadcast(party, "§d[パーティー] §e" + player.getName() + " §fが脱退しました");
        return null;
    }

    public String kick(Player actor, Party party, UUID target) {
        if (!scope(actor).equals(party.getScope())) return "別のワールドグループのパーティーは操作できません";
        PartyRank actorRank = party.rankOf(actor.getUniqueId());
        PartyRank targetRank = party.rankOf(target);
        if (actorRank == null || targetRank == null) return "対象がパーティーに所属していません";
        if (!actorRank.isAtLeast(PartyRank.CO_LEADER)) return "追放はサブリーダー以上のみ実行できます";
        if (targetRank.isAtLeast(actorRank)) return "自分と同格以上のメンバーは追放できません";
        String targetName = party.nameOf(target);
        party.removeMember(target);
        index(party.getScope()).remove(target);
        save();
        Player online = Bukkit.getPlayer(target);
        if (online != null && scope(online).equals(party.getScope())) {
            resetDisplayName(online);
            online.sendMessage("§cパーティー " + party.getColoredName() + " §cから追放されました");
        }
        broadcast(party, "§d[パーティー] §e" + targetName + " §fが追放されました");
        return null;
    }

    public String promote(Player actor, Party party, UUID target) {
        if (!scope(actor).equals(party.getScope())) return "別のワールドグループのパーティーは操作できません";
        if (party.rankOf(actor.getUniqueId()) != PartyRank.LEADER) return "昇格はリーダーのみ実行できます";
        if (party.rankOf(target) != PartyRank.MEMBER) return "メンバーのみ昇格できます";
        party.getMembers().remove(target); party.getCoLeaders().add(target); save();
        broadcast(party, "§d[パーティー] §e" + party.nameOf(target) + " §fがサブリーダーに昇格しました");
        return null;
    }

    public String demote(Player actor, Party party, UUID target) {
        if (!scope(actor).equals(party.getScope())) return "別のワールドグループのパーティーは操作できません";
        if (party.rankOf(actor.getUniqueId()) != PartyRank.LEADER) return "降格はリーダーのみ実行できます";
        if (party.rankOf(target) != PartyRank.CO_LEADER) return "サブリーダーのみ降格できます";
        party.getCoLeaders().remove(target); party.getMembers().add(target); save();
        broadcast(party, "§d[パーティー] §e" + party.nameOf(target) + " §fがメンバーに降格しました");
        return null;
    }

    public String rename(Player actor, Party party, String newName) {
        if (!scope(actor).equals(party.getScope())) return "別のワールドグループのパーティーは操作できません";
        if (party.rankOf(actor.getUniqueId()) != PartyRank.LEADER) return "名前変更はリーダーのみ実行できます";
        String sanitized = sanitizeName(newName);
        if (sanitized == null) return "パーティー名は2〜" + MAX_NAME_LENGTH + "文字で指定してください";
        if (!sanitized.equalsIgnoreCase(party.getName()) && isNameUsed(party.getScope(), sanitized, party)) return "このワールドグループではその名前は既に使われています";
        party.setName(sanitized); save(); refreshDisplayNames(party);
        broadcast(party, "§d[パーティー] §fパーティー名が " + party.getColoredName() + " §fに変更されました");
        return null;
    }

    public String recolor(Player actor, Party party, PartyColor color) {
        if (!scope(actor).equals(party.getScope())) return "別のワールドグループのパーティーは操作できません";
        if (party.rankOf(actor.getUniqueId()) != PartyRank.LEADER) return "カラー変更はリーダーのみ実行できます";
        if (isColorUsed(party.getScope(), color, party)) return "このワールドグループではそのカラーは他のパーティーが使用中です";
        party.setColorKey(color.name()); save(); refreshDisplayNames(party);
        broadcast(party, "§d[パーティー] §fイメージカラーが " + color.getLegacyCode() + color.getDisplayName() + " §fに変更されました");
        return null;
    }

    public String redescribe(Player actor, Party party, String description) {
        if (!scope(actor).equals(party.getScope())) return "別のワールドグループのパーティーは操作できません";
        PartyRank rank = party.rankOf(actor.getUniqueId());
        if (rank == null || !rank.isAtLeast(PartyRank.CO_LEADER)) return "説明変更はサブリーダー以上のみ実行できます";
        party.setDescription(description == null ? "" : description.trim()); save(); return null;
    }

    /** 公開/プライベート切替。サブリーダー以上が変更可能。 */
    public String setPublicParty(Player actor, Party party, boolean publicParty) {
        if (!scope(actor).equals(party.getScope())) return "別のワールドグループのパーティーは操作できません";
        PartyRank rank = party.rankOf(actor.getUniqueId());
        if (rank == null || !rank.isAtLeast(PartyRank.CO_LEADER)) return "公開設定の変更はサブリーダー以上のみ実行できます";
        party.setPublicParty(publicParty);
        save();
        broadcast(party, publicParty
                ? "§d[パーティー] §a公開パーティーになりました §7(同じワールドグループから検索・参加できます)"
                : "§d[パーティー] §eプライベートパーティーになりました §7(参加は招待制です)");
        return null;
    }

    public void broadcast(Party party, String message) {
        for (UUID member : party.getAllMembers()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null && scope(online).equals(party.getScope())) online.sendMessage(message);
        }
    }

    // ================= display =================

    public void updateDisplayName(Player player, Party party) {
        if (party == null || !scope(player).equals(party.getScope()) || !party.isMember(player.getUniqueId())) {
            resetDisplayName(player);
            return;
        }
        String prefix = party.isNameTagPrefix() ? "[" + party.getName() + "] " : "";
        String color = party.isNameTagColor() ? party.getColor().getLegacyCode() : "§f";
        Component component = ComponentUtils.legacy(color + prefix + player.getName());
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
            if (online != null && scope(online).equals(party.getScope())) updateDisplayName(online, party);
        }
    }

    // ================= settings =================

    public String setFriendlyFire(Player actor, Party party, boolean enabled) {
        if (!scope(actor).equals(party.getScope())) return "別のワールドグループのパーティーは操作できません";
        PartyRank rank = party.rankOf(actor.getUniqueId());
        if (rank == null || !rank.isAtLeast(PartyRank.CO_LEADER)) return "設定変更はサブリーダー以上のみ実行できます";
        party.setFriendlyFire(enabled); save();
        broadcast(party, "§d[パーティー] §f味方同士の攻撃: " + (enabled ? "§a有効" : "§c無効"));
        return null;
    }

    public String setNameTagColor(Player actor, Party party, boolean enabled) {
        if (!scope(actor).equals(party.getScope())) return "別のワールドグループのパーティーは操作できません";
        PartyRank rank = party.rankOf(actor.getUniqueId());
        if (rank == null || !rank.isAtLeast(PartyRank.CO_LEADER)) return "設定変更はサブリーダー以上のみ実行できます";
        party.setNameTagColor(enabled); save(); refreshDisplayNames(party);
        broadcast(party, "§d[パーティー] §fネームタグカラー: " + (enabled ? "§a有効" : "§c無効"));
        return null;
    }

    public String setNameTagPrefix(Player actor, Party party, boolean enabled) {
        if (!scope(actor).equals(party.getScope())) return "別のワールドグループのパーティーは操作できません";
        PartyRank rank = party.rankOf(actor.getUniqueId());
        if (rank == null || !rank.isAtLeast(PartyRank.CO_LEADER)) return "設定変更はサブリーダー以上のみ実行できます";
        party.setNameTagPrefix(enabled); save(); refreshDisplayNames(party);
        broadcast(party, "§d[パーティー] §fパーティープレフィックス: " + (enabled ? "§a有効" : "§c無効"));
        return null;
    }
}
