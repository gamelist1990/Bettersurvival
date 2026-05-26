package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.mode;

import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.worker.CropHarvestWorker;

public class CropModeWorker implements ModeWorker {

    private final CropHarvestWorker cropHarvestWorker;

    public CropModeWorker(CropHarvestWorker cropHarvestWorker) {
        this.cropHarvestWorker = cropHarvestWorker;
    }

    @Override
    public int execute(ModeExecutionContext context) {
        return cropHarvestWorker.runCropWorker(
                context.profile(),
                context.golem(),
                context.harvestCapacity(),
                context.maxRange(),
                context.allowReplant(),
                context.allowBoneMeal());
    }
}
