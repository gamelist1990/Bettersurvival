package org.pexserver.koukunn.bettersurvival.Modules.Feature.Party;

/**
 * パーティー内の階級。
 * リーダー > 副リーダー > メンバー の3階級で構成される。
 */
public enum PartyRank {
    LEADER("§6リーダー", 3),
    CO_LEADER("§eサブリーダー", 2),
    MEMBER("§7メンバー", 1);

    private final String displayName;
    private final int weight;

    PartyRank(String displayName, int weight) {
        this.displayName = displayName;
        this.weight = weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isAtLeast(PartyRank other) {
        return weight >= other.weight;
    }
}
