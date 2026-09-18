package org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect;

/**
 * Protect の監査ログ1件を表す不変データ。
 */
public record ProtectRecord(
        long id,
        long timeMs,
        String actorUuid,
        String actorName,
        String worldUuid,
        String worldName,
        int x,
        int y,
        int z,
        ProtectAction action,
        String blockBefore,
        String blockAfter,
        Integer slot,
        byte[] itemBefore,
        byte[] itemAfter,
        String detail,
        boolean rolledBack) {

    public boolean reversible() {
        return action != null && action.reversible();
    }
}
