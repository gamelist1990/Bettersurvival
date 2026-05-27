package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.model;

public enum PlacementResult {
    SUCCESS,
    MAIN_ALREADY_EXISTS,
    MAIN_NOT_FOUND,
    TOO_FAR;

    public String message(String id) {
        return switch (this) {
            case MAIN_ALREADY_EXISTS -> "§c共有ストレージ " + id + " には既に別の主チェストがあります";
            case MAIN_NOT_FOUND -> "§c共有ストレージ " + id + " の主チェストが見つかりません";
            case TOO_FAR -> "§csubチェストは主チェストから接続範囲以内に設置してください";
            case SUCCESS -> "";
        };
    }
}
