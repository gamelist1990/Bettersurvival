package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.model;

public enum PlacementConflict {
    NONE,
    OTHER_NETWORK;

    public String message(String id) {
        return switch (this) {
            case OTHER_NETWORK -> "§cこの接続先には別の共有ストレージが存在します";
            case NONE -> "";
        };
    }
}
