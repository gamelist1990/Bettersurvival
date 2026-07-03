package org.pexserver.koukunn.bettersurvival.Core.Util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class OfflineUUIDUtil {

    public static UUID getUUID(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}

