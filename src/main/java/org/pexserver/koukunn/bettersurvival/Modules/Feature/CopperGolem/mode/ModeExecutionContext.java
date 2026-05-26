package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.mode;

import org.bukkit.entity.CopperGolem;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.GolemProfile;

public record ModeExecutionContext(
        GolemProfile profile,
        CopperGolem golem,
        int harvestCapacity,
        int maxRange,
        boolean allowReplant,
        boolean allowBoneMeal) {
}
