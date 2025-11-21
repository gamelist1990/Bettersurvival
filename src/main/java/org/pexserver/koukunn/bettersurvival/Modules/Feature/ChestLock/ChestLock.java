package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock;

import java.util.ArrayList;
import java.util.List;

public class ChestLock {

    private String owner;
    private String name;
    private List<String> members = new ArrayList<>();

    public ChestLock() {}

    public ChestLock(String owner, String name) {
        this.owner = owner;
        this.name = name;
    }


    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getMembers() { return members; }
    public void setMembers(List<String> members) { this.members = members; }

    public void addMember(String uuid) {
        if (!members.contains(uuid)) members.add(uuid);
    }

    public void removeMember(String uuid) {
        members.remove(uuid);
    }

    public boolean isMember(String uuid) {
        return members.contains(uuid);
    }
}
