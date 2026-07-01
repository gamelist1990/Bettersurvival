package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService;

public record WebSession(String token, String uuid, long expiresAt) {
    public boolean isExpired(long now) {
        return expiresAt <= now;
    }
}
