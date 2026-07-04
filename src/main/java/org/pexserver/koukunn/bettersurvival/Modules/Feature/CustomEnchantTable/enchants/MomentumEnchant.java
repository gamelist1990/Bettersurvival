package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 採掘加速 (momentum)。
 *
 * 掘り続けるほどプレイヤー自身の採掘系属性を上げる。
 * ポーション効果は使わないため、画面右上に Haste 表示は出ない。
 * 3秒間掘らないと速度補正とスタックがリセットされる。
 */
public class MomentumEnchant extends CustomEnchant {

    /** この時間掘らないとスタックリセット */
    private static final long RESET_MS = 3_000L;
    /** 何ブロック連続で掘るごとに速度が1段階上がるか */
    private static final int BLOCKS_PER_TIER = 1;
    /** Lv5 最大時のブロック破壊速度倍率 */
    private static final double MAX_SPEED_MULTIPLIER = 10.0D;
    /** 1レベルごとの最大段階数 */
    private static final int TIERS_PER_LEVEL = 2;
    /** 1段階ごとの適正ツール採掘速度加算 */
    private static final double MINING_EFFICIENCY_BONUS_PER_TIER = 3.0D;

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public MomentumEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "momentum";
    }

    @Override
    public String displayName() {
        return "採掘加速";
    }

    @Override
    public String description() {
        return "§7掘り続けるほど採掘速度が上がる"
                + "\n§7(" + BLOCKS_PER_TIER + "ブロックごとに加速、上限はレベル依存)"
                + "\n§7Lv5最大時は採掘速度が約" + (int) MAX_SPEED_MULTIPLIER + "倍"
                + "\n§73秒間掘らないと元に戻る"
                + "\n§7ポーション効果ではなく採掘属性そのものを上げる";
    }

    @Override
    public Material icon() {
        return Material.GOLDEN_PICKAXE;
    }

    @Override
    public String vanillaParentName() {
        return "効率強化";
    }

    @Override
    public int maxLevel() {
        return 5;
    }

    @Override
    public boolean supports(Material type) {
        return isMiningTool(type);
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 16), new ItemStack(Material.IRON_INGOT, 8));
            case 2 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 32), new ItemStack(Material.GOLD_INGOT, 8));
            default -> List.of(new ItemStack(Material.LAPIS_LAZULI, 48), new ItemStack(Material.DIAMOND, 4));
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        int level = levelOf(tool);
        if (level <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        State state = states.computeIfAbsent(player.getUniqueId(), uuid -> new State());
        if (now - state.lastBreakMs > RESET_MS) {
            resetSpeed(player, state);
            state.stacks = 0;
            state.lastTier = 0;
        }
        state.stacks++;
        state.lastBreakMs = now;

        int cappedLevel = Math.min(level, maxLevel());
        int tierCap = tierCap(cappedLevel); // Lv1→2段階 / Lv2→4段階 / Lv3→6段階 / Lv4→8段階 / Lv5→10段階
        int tier = Math.min(state.stacks / BLOCKS_PER_TIER, tierCap);
        if (tier <= 0) {
            return;
        }
        applySpeed(player, state, cappedLevel, tier);
        if (tier != state.lastTier) {
            state.lastTier = tier;
            int bonusPercent = (int) Math.round((speedMultiplier(cappedLevel, tier) - 1.0D) * 100.0D);
            player.sendActionBar(ComponentUtils.legacy("§d✦ 採掘加速 §f×" + state.stacks + " §7(採掘速度 +" + bonusPercent + "%)"));
        }
    }

    private int tierCap(int level) {
        return Math.max(1, Math.min(maxLevel(), level) * TIERS_PER_LEVEL);
    }

    private double maxSpeedMultiplier(int level) {
        int cappedLevel = Math.max(1, Math.min(maxLevel(), level));
        return 1.0D + (MAX_SPEED_MULTIPLIER - 1.0D) * (double) cappedLevel / (double) maxLevel();
    }

    private double speedMultiplier(int level, int tier) {
        int cap = tierCap(level);
        int cappedTier = Math.max(0, Math.min(cap, tier));
        return 1.0D + (maxSpeedMultiplier(level) - 1.0D) * (double) cappedTier / (double) cap;
    }

    private void applySpeed(Player player, State state, int level, int tier) {
        AttributeInstance blockBreakSpeed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        AttributeInstance miningEfficiency = player.getAttribute(Attribute.MINING_EFFICIENCY);
        if (blockBreakSpeed == null && miningEfficiency == null) {
            return;
        }
        if (blockBreakSpeed != null && Double.isNaN(state.originalBlockBreakSpeed)) {
            state.originalBlockBreakSpeed = blockBreakSpeed.getBaseValue();
        }
        if (miningEfficiency != null && Double.isNaN(state.originalMiningEfficiency)) {
            state.originalMiningEfficiency = miningEfficiency.getBaseValue();
        }
        double multiplier = speedMultiplier(level, tier);
        if (blockBreakSpeed != null) {
            blockBreakSpeed.setBaseValue(state.originalBlockBreakSpeed * multiplier);
        }
        if (miningEfficiency != null) {
            miningEfficiency.setBaseValue(state.originalMiningEfficiency + MINING_EFFICIENCY_BONUS_PER_TIER * tier);
        }
        state.speedApplied = true;
    }

    private void resetSpeed(Player player, State state) {
        if (!state.speedApplied) {
            state.originalBlockBreakSpeed = Double.NaN;
            state.originalMiningEfficiency = Double.NaN;
            state.speedApplied = false;
            return;
        }
        AttributeInstance blockBreakSpeed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (blockBreakSpeed != null && !Double.isNaN(state.originalBlockBreakSpeed)) {
            blockBreakSpeed.setBaseValue(state.originalBlockBreakSpeed);
        }
        AttributeInstance miningEfficiency = player.getAttribute(Attribute.MINING_EFFICIENCY);
        if (miningEfficiency != null && !Double.isNaN(state.originalMiningEfficiency)) {
            miningEfficiency.setBaseValue(state.originalMiningEfficiency);
        }
        state.originalBlockBreakSpeed = Double.NaN;
        state.originalMiningEfficiency = Double.NaN;
        state.speedApplied = false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        State state = states.remove(event.getPlayer().getUniqueId());
        if (state != null) {
            resetSpeed(event.getPlayer(), state);
        }
    }

    @Override
    public void shutdown() {
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                resetSpeed(player, entry.getValue());
            }
        }
        states.clear();
    }

    private static final class State {
        int stacks;
        int lastTier;
        long lastBreakMs;
        double originalBlockBreakSpeed = Double.NaN;
        double originalMiningEfficiency = Double.NaN;
        boolean speedApplied;
    }
}
