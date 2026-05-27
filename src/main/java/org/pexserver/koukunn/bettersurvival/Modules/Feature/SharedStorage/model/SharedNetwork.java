package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.model;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SharedNetwork {
    private static final int DEFAULT_SUB_DISTANCE = 15;
    private static final int MAX_SUB_DISTANCE_LIMIT = 50;
    private static final boolean DEFAULT_ALLOW_SUB_INSERT = false;
    private static final boolean DEFAULT_ALLOW_SUB_EXTRACT = true;
    private static final boolean DEFAULT_ALLOW_SUB_HOPPER_INSERT = false;
    private static final boolean DEFAULT_ALLOW_SUB_HOPPER_EXTRACT = true;
    private static final boolean DEFAULT_ALLOW_MAIN_INSERT = true;
    private static final boolean DEFAULT_ALLOW_MAIN_EXTRACT = true;
    private static final boolean DEFAULT_ENABLE_TRANSFER_PARTICLES = true;
    private static final boolean DEFAULT_ENABLE_SUB_FRAME_FILTER = false;
    private static final boolean DEFAULT_ENABLE_CHEST_PAGE = false;

    private final String id;
    private Location main;
    private final List<Location> subs = new ArrayList<>();
    private boolean allowSubInsert = DEFAULT_ALLOW_SUB_INSERT;
    private boolean allowSubExtract = DEFAULT_ALLOW_SUB_EXTRACT;
    private boolean allowSubHopperInsert = DEFAULT_ALLOW_SUB_HOPPER_INSERT;
    private boolean allowSubHopperExtract = DEFAULT_ALLOW_SUB_HOPPER_EXTRACT;
    private boolean allowMainInsert = DEFAULT_ALLOW_MAIN_INSERT;
    private boolean allowMainExtract = DEFAULT_ALLOW_MAIN_EXTRACT;
    private boolean enableTransferParticles = DEFAULT_ENABLE_TRANSFER_PARTICLES;
    private int subRange = DEFAULT_SUB_DISTANCE;
    private boolean enableSubFrameFilter = DEFAULT_ENABLE_SUB_FRAME_FILTER;
    private SubFrameFilterMode subFrameFilterMode = SubFrameFilterMode.EXACT;
    private boolean enableChestPage = DEFAULT_ENABLE_CHEST_PAGE;

    public SharedNetwork(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Location main() {
        return main;
    }

    public void setMain(Location main) {
        this.main = main;
    }

    public List<Location> subs() {
        return subs;
    }

    public void addSub(Location location) {
        if (!hasSub(location))
            subs.add(location);
    }

    public void removeSub(Location location) {
        subs.removeIf(existing -> Objects.equals(existing.getWorld(), location.getWorld())
                && existing.getBlockX() == location.getBlockX()
                && existing.getBlockY() == location.getBlockY()
                && existing.getBlockZ() == location.getBlockZ());
    }

    public void replaceSubs(List<Location> newSubs) {
        subs.clear();
        subs.addAll(newSubs);
    }

    public boolean hasSub(Location location) {
        for (Location existing : subs) {
            if (Objects.equals(existing.getWorld(), location.getWorld())
                    && existing.getBlockX() == location.getBlockX()
                    && existing.getBlockY() == location.getBlockY()
                    && existing.getBlockZ() == location.getBlockZ())
                return true;
        }
        return false;
    }

    public boolean allowSubInsert() {
        return allowSubInsert;
    }

    public void setAllowSubInsert(boolean allowSubInsert) {
        this.allowSubInsert = allowSubInsert;
    }

    public boolean allowSubExtract() {
        return allowSubExtract;
    }

    public void setAllowSubExtract(boolean allowSubExtract) {
        this.allowSubExtract = allowSubExtract;
    }

    public boolean allowSubHopperInsert() {
        return allowSubHopperInsert;
    }

    public void setAllowSubHopperInsert(boolean allowSubHopperInsert) {
        this.allowSubHopperInsert = allowSubHopperInsert;
    }

    public boolean allowSubHopperExtract() {
        return allowSubHopperExtract;
    }

    public void setAllowSubHopperExtract(boolean allowSubHopperExtract) {
        this.allowSubHopperExtract = allowSubHopperExtract;
    }

    public boolean allowMainInsert() {
        return allowMainInsert;
    }

    public void setAllowMainInsert(boolean allowMainInsert) {
        this.allowMainInsert = allowMainInsert;
    }

    public boolean allowMainExtract() {
        return allowMainExtract;
    }

    public void setAllowMainExtract(boolean allowMainExtract) {
        this.allowMainExtract = allowMainExtract;
    }

    public boolean enableTransferParticles() {
        return enableTransferParticles;
    }

    public void setEnableTransferParticles(boolean enableTransferParticles) {
        this.enableTransferParticles = enableTransferParticles;
    }

    public int subRange() {
        return subRange;
    }

    public void setSubRange(int subRange) {
        this.subRange = clampSubRange(subRange);
    }

    public boolean enableSubFrameFilter() {
        return enableSubFrameFilter;
    }

    public void setEnableSubFrameFilter(boolean enableSubFrameFilter) {
        this.enableSubFrameFilter = enableSubFrameFilter;
    }

    public SubFrameFilterMode subFrameFilterMode() {
        return subFrameFilterMode;
    }

    public void setSubFrameFilterMode(SubFrameFilterMode subFrameFilterMode) {
        this.subFrameFilterMode = subFrameFilterMode == null ? SubFrameFilterMode.EXACT : subFrameFilterMode;
    }

    public boolean enableChestPage() {
        return enableChestPage;
    }

    public void setEnableChestPage(boolean enableChestPage) {
        this.enableChestPage = enableChestPage;
    }

    private static int clampSubRange(int range) {
        return Math.max(1, Math.min(MAX_SUB_DISTANCE_LIMIT, range));
    }
}
