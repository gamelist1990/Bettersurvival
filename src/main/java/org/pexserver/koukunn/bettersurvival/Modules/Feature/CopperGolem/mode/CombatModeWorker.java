package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.mode;

import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.worker.CombatWorker;

public class CombatModeWorker implements ModeWorker {

    private final CombatWorker combatWorker;

    public CombatModeWorker(CombatWorker combatWorker) {
        this.combatWorker = combatWorker;
    }

    @Override
    public int execute(ModeExecutionContext context) {
        return combatWorker.runCombatWorker(
                context.profile(),
                context.golem(),
                context.maxRange());
    }
}
