package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChestLock {

    private String ownerName;
    private List<String> ownerUuids = new ArrayList<>();
    private String name;
    private List<String> memberUuids = new ArrayList<>();
    private List<String> memberNames = new ArrayList<>();

    public ChestLock() {}

    public ChestLock(String owner, String name) {
        this.name = name;
        addOwnerUuid(owner);
    }

    public ChestLock(String owner, String ownerName, String name) {
        this(owner, name);
        setOwnerName(ownerName);
    }

    public String getOwner() {
        return ownerUuids.isEmpty() ? null : ownerUuids.get(0);
    }

    public void setOwner(String owner) {
        addOwnerUuid(owner);
    }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = normalizeName(ownerName); }

    public List<String> getOwnerUuids() { return ownerUuids; }
    public void setOwnerUuids(List<String> ownerUuids) {
        this.ownerUuids = unique(ownerUuids);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getMembers() { return memberUuids; }
    public void setMembers(List<String> members) { this.memberUuids = unique(members); }
    public List<String> getMemberUuids() { return memberUuids; }
    public void setMemberUuids(List<String> memberUuids) { this.memberUuids = unique(memberUuids); }

    public List<String> getMemberNames() { return memberNames; }
    public void setMemberNames(List<String> memberNames) { this.memberNames = uniqueNames(memberNames); }

    public void addOwner(Player player) {
        if (player == null) return;
        addOwnerUuid(player.getUniqueId().toString());
        setOwnerName(player.getName());
    }

    public boolean isOwner(Player player) {
        if (player == null) return false;
        String uuid = player.getUniqueId().toString();
        if (ownerUuids.contains(uuid)) return true;
        return sameName(ownerName, player.getName());
    }

    public void addMember(String uuid) {
        addUnique(memberUuids, uuid);
    }

    public void addMember(Player player) {
        if (player == null) return;
        addMember(player.getUniqueId().toString());
        addMemberName(player.getName());
    }

    public void removeMember(String uuid) {
        memberUuids.remove(uuid);
    }

    public void removeMember(Player player) {
        if (player == null) return;
        memberUuids.remove(player.getUniqueId().toString());
        removeMemberName(player.getName());
    }

    public boolean isMember(String uuid) {
        return memberUuids.contains(uuid);
    }

    public boolean isMember(Player player) {
        if (player == null) return false;
        if (isMember(player.getUniqueId().toString())) return true;
        return hasMemberName(player.getName());
    }

    public void addMemberName(String name) {
        addUniqueName(memberNames, name);
    }

    public void removeMemberName(String name) {
        String normalized = normalizeName(name);
        if (normalized != null) memberNames.removeIf(memberName -> sameName(memberName, normalized));
    }

    public boolean hasMemberName(String name) {
        String normalized = normalizeName(name);
        if (normalized == null) return false;
        for (String memberName : memberNames) {
            if (sameName(memberName, normalized)) return true;
        }
        return false;
    }

    public void remember(Player player) {
        if (isOwner(player)) addOwner(player);
        if (isMember(player)) addMember(player);
    }

    public String getOwnerDisplayName() {
        String owner = getOwner();
        return ownerName != null ? ownerName : owner;
    }

    public String getOwnerKey() {
        if (ownerName != null) return ownerName.toLowerCase(Locale.ROOT);
        String owner = getOwner();
        return owner == null ? "" : owner;
    }

    public String getMemberDisplayName(String uuid) {
        int index = memberUuids.indexOf(uuid);
        if (index >= 0 && index < memberNames.size()) return memberNames.get(index);
        return uuid;
    }

    private void addOwnerUuid(String uuid) {
        addUnique(ownerUuids, uuid);
    }

    private static List<String> unique(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        for (String value : values) addUnique(result, value);
        return result;
    }

    private static List<String> uniqueNames(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        for (String value : values) addUniqueName(result, value);
        return result;
    }

    private static void addUnique(List<String> values, String value) {
        if (value == null || value.isBlank()) return;
        if (!values.contains(value)) values.add(value);
    }

    private static void addUniqueName(List<String> values, String value) {
        String normalized = normalizeName(value);
        if (normalized == null) return;
        for (String existing : values) {
            if (sameName(existing, normalized)) return;
        }
        values.add(normalized);
    }

    private static String normalizeName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean sameName(String left, String right) {
        String a = normalizeName(left);
        String b = normalizeName(right);
        if (a == null || b == null) return false;
        return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }
}
