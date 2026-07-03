package org.pexserver.koukunn.bettersurvival.Modules.Feature.Party;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * パーティー（ギルド）1件分のデータモデル。
 * リーダー1名・サブリーダー任意人数・メンバーで構成される。
 */
public class Party {

    private final UUID id;
    private String name;
    private String colorKey;
    private String description;
    private UUID leader;
    private final Set<UUID> coLeaders = new LinkedHashSet<>();
    private final Set<UUID> members = new LinkedHashSet<>();
    /** オフライン表示用に把握している名前 (uuid -> 最後に確認した名前) */
    private final Map<UUID, String> knownNames = new LinkedHashMap<>();
    private long createdAt;
    private boolean friendlyFire = false;
    private boolean nameTagColor = true;
    private boolean nameTagPrefix = true;

    public Party(UUID id, String name, String colorKey, String description, UUID leader) {
        this.id = id;
        this.name = name;
        this.colorKey = colorKey;
        this.description = description == null ? "" : description;
        this.leader = leader;
        this.createdAt = System.currentTimeMillis();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PartyColor getColor() {
        return PartyColor.fromKey(colorKey);
    }

    public String getColorKey() {
        return colorKey;
    }

    public void setColorKey(String colorKey) {
        this.colorKey = colorKey;
    }

    /** カラーコード付きのパーティー名を返す。 */
    public String getColoredName() {
        return getColor().getLegacyCode() + name + "§r";
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public Set<UUID> getCoLeaders() {
        return coLeaders;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public Map<UUID, String> getKnownNames() {
        return knownNames;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isFriendlyFire() {
        return friendlyFire;
    }

    public void setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
    }

    public boolean isNameTagColor() {
        return nameTagColor;
    }

    public void setNameTagColor(boolean nameTagColor) {
        this.nameTagColor = nameTagColor;
    }

    public boolean isNameTagPrefix() {
        return nameTagPrefix;
    }

    public void setNameTagPrefix(boolean nameTagPrefix) {
        this.nameTagPrefix = nameTagPrefix;
    }

    /** リーダー含む全所属メンバーを返す。 */
    public List<UUID> getAllMembers() {
        List<UUID> all = new ArrayList<>();
        all.add(leader);
        all.addAll(coLeaders);
        all.addAll(members);
        return all;
    }

    public boolean isMember(UUID uuid) {
        return leader.equals(uuid) || coLeaders.contains(uuid) || members.contains(uuid);
    }

    /** 指定プレイヤーの階級を返す。所属していない場合は null。 */
    public PartyRank rankOf(UUID uuid) {
        if (leader.equals(uuid)) {
            return PartyRank.LEADER;
        }
        if (coLeaders.contains(uuid)) {
            return PartyRank.CO_LEADER;
        }
        if (members.contains(uuid)) {
            return PartyRank.MEMBER;
        }
        return null;
    }

    public String nameOf(UUID uuid) {
        String known = knownNames.get(uuid);
        return known == null ? uuid.toString().substring(0, 8) : known;
    }

    public void rememberName(UUID uuid, String name) {
        if (uuid != null && name != null && !name.isBlank()) {
            knownNames.put(uuid, name);
        }
    }

    /** 所属から取り除く（リーダーは除去不可、false を返す）。 */
    public boolean removeMember(UUID uuid) {
        if (leader.equals(uuid)) {
            return false;
        }
        boolean removed = coLeaders.remove(uuid);
        removed |= members.remove(uuid);
        return removed;
    }
}
