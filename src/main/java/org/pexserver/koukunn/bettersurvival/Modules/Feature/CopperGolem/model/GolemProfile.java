package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GolemProfile {
    private final String id;
    private String ownerUuid;
    private final UUID entityUuid;
    private int level;
    private int progress;
    private int availablePoints;
    private int harvestPoints;
    private int rangePoints;
    private int replantPoints;
    private int boneMealPoints;
    private int combatHealthPoints;
    private int moveSpeedPoints;
    private int range;
    private boolean autoReplant;
    private boolean autoBoneMeal;
    private CropRouteMode cropRouteMode;
    private GolemMode mode;
    private final List<ContainerTarget> targets = new ArrayList<>();
    private final List<ContainerTarget> boneMealSources = new ArrayList<>();
    private final List<Material> cropFilters = new ArrayList<>();
    private ItemStack combatMainHand;
    private ItemStack combatOffHand;
    private ItemStack combatHelmet;
    private ItemStack combatChestplate;
    private ItemStack combatLeggings;
    private ItemStack combatBoots;

    public GolemProfile(
            String id,
            String ownerUuid,
            UUID entityUuid,
            int level,
            int progress,
            int availablePoints,
            int harvestPoints,
            int rangePoints,
            int replantPoints,
            int boneMealPoints,
            int combatHealthPoints,
            int moveSpeedPoints,
            int range,
            boolean autoReplant,
            boolean autoBoneMeal,
            CropRouteMode cropRouteMode,
            GolemMode mode) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.entityUuid = entityUuid;
        this.level = level;
        this.progress = progress;
        this.availablePoints = availablePoints;
        this.harvestPoints = harvestPoints;
        this.rangePoints = rangePoints;
        this.replantPoints = replantPoints;
        this.boneMealPoints = boneMealPoints;
        this.combatHealthPoints = combatHealthPoints;
        this.moveSpeedPoints = moveSpeedPoints;
        this.range = range;
        this.autoReplant = autoReplant;
        this.autoBoneMeal = autoBoneMeal;
        this.cropRouteMode = cropRouteMode == null ? CropRouteMode.NEAR_ORIGIN : cropRouteMode;
        this.mode = mode;
    }

    public String id() {
        return id;
    }

    public String ownerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(String ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int progress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int availablePoints() {
        return availablePoints;
    }

    public void setAvailablePoints(int availablePoints) {
        this.availablePoints = availablePoints;
    }

    public int harvestPoints() {
        return harvestPoints;
    }

    public void setHarvestPoints(int harvestPoints) {
        this.harvestPoints = harvestPoints;
    }

    public int rangePoints() {
        return rangePoints;
    }

    public void setRangePoints(int rangePoints) {
        this.rangePoints = rangePoints;
    }

    public int replantPoints() {
        return replantPoints;
    }

    public void setReplantPoints(int replantPoints) {
        this.replantPoints = replantPoints;
    }

    public int range() {
        return range;
    }

    public void setRange(int range) {
        this.range = range;
    }

    public boolean autoReplant() {
        return autoReplant;
    }

    public void setAutoReplant(boolean autoReplant) {
        this.autoReplant = autoReplant;
    }

    public int boneMealPoints() {
        return boneMealPoints;
    }

    public void setBoneMealPoints(int boneMealPoints) {
        this.boneMealPoints = boneMealPoints;
    }

    public int combatHealthPoints() {
        return combatHealthPoints;
    }

    public void setCombatHealthPoints(int combatHealthPoints) {
        this.combatHealthPoints = combatHealthPoints;
    }

    public int moveSpeedPoints() {
        return moveSpeedPoints;
    }

    public void setMoveSpeedPoints(int moveSpeedPoints) {
        this.moveSpeedPoints = moveSpeedPoints;
    }

    public boolean autoBoneMeal() {
        return autoBoneMeal;
    }

    public void setAutoBoneMeal(boolean autoBoneMeal) {
        this.autoBoneMeal = autoBoneMeal;
    }

    public CropRouteMode cropRouteMode() {
        return cropRouteMode;
    }

    public void setCropRouteMode(CropRouteMode cropRouteMode) {
        this.cropRouteMode = cropRouteMode == null ? CropRouteMode.NEAR_ORIGIN : cropRouteMode;
    }

    public GolemMode mode() {
        return mode;
    }

    public void setMode(GolemMode mode) {
        this.mode = mode;
    }

    public List<ContainerTarget> targets() {
        return targets;
    }

    public List<ContainerTarget> boneMealSources() {
        return boneMealSources;
    }

    public List<Material> cropFilters() {
        return cropFilters;
    }

    public ItemStack combatMainHand() {
        return combatMainHand == null ? null : combatMainHand.clone();
    }

    public void setCombatMainHand(ItemStack combatMainHand) {
        this.combatMainHand = cloneSingle(combatMainHand);
    }

    public ItemStack combatOffHand() {
        return combatOffHand == null ? null : combatOffHand.clone();
    }

    public void setCombatOffHand(ItemStack combatOffHand) {
        this.combatOffHand = cloneSingle(combatOffHand);
    }

    public ItemStack combatHelmet() {
        return combatHelmet == null ? null : combatHelmet.clone();
    }

    public void setCombatHelmet(ItemStack combatHelmet) {
        this.combatHelmet = cloneSingle(combatHelmet);
    }

    public ItemStack combatChestplate() {
        return combatChestplate == null ? null : combatChestplate.clone();
    }

    public void setCombatChestplate(ItemStack combatChestplate) {
        this.combatChestplate = cloneSingle(combatChestplate);
    }

    public ItemStack combatLeggings() {
        return combatLeggings == null ? null : combatLeggings.clone();
    }

    public void setCombatLeggings(ItemStack combatLeggings) {
        this.combatLeggings = cloneSingle(combatLeggings);
    }

    public ItemStack combatBoots() {
        return combatBoots == null ? null : combatBoots.clone();
    }

    public void setCombatBoots(ItemStack combatBoots) {
        this.combatBoots = cloneSingle(combatBoots);
    }

    private ItemStack cloneSingle(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return null;
        }
        ItemStack cloned = stack.clone();
        cloned.setAmount(1);
        return cloned;
    }
}
