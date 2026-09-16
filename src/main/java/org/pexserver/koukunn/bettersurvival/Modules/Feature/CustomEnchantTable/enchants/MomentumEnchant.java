package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
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

    private static final long RESET_MS = 3_000L;
    private static final int BLOCKS_PER_TIER = 1;
    private static final double MAX_SPEED_MULTIPLIER = 10.0D;
    private static final int TIERS_PER_LEVEL = 2;
    private static final double MINING_EFFICIENCY_BONUS_PER_TIER = 3.0D;

    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private BukkitTask tickTask;

    public MomentumEnchant(Loader plugin) {
        super(plugin);
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    @Override public String id() { return "momentum"; }
    @Override public String displayName() { return "採掘加速"; }

    @Override
    public String description() {
        return "§7掘り続けるほど採掘速度が上がる"
                + "\n§7(" + BLOCKS_PER_TIER + "ブロックごとに加速、上限はレベル依存)"
                + "\n§7Lv5最大時は採掘速度が約" + (int) MAX_SPEED_MULTIPLIER + "倍"
                + "\n§73秒間掘らないと元に戻る"
                + "\n§7ポーション効果ではなく採掘属性そのものを上げる";
    }

    @Override public Material icon() { return Material.GOLDEN_PICKAXE; }
    @Override public String vanillaParentName() { return "効率強化"; }
    @Override public int maxLevel() { return 5; }
    @Override public boolean supports(Material type) { return isMiningTool(type); }

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
            State existingState = states.get(player.getUniqueId());
            if (existingState != null && existingState.speedApplied) {
                resetSpeed(player, existingState);
                existingState.stacks = 0;
                existingState.lastTier = 0;
            }
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
        int tierCap = tierCap(cappedLevel);
        int tier = Math.min(state.stacks / BLOCKS_PER_TIER, tierCap);
        if (tier <= 0) return;
        applySpeed(player, state, cappedLevel, tier);
        if (tier != state.lastTier) {
            state.lastTier = tier;
            int bonusPercent = (int) Math.round((speedMultiplier(cappedLevel, tier) - 1.0D) * 100.0D);
            player.sendActionBar(ComponentUtils.legacy("§d✦ 採掘加速 §f×" + state.stacks + " §7(採掘速度 +" + bonusPercent + "%)"));
        }
    }

    private int tierCap(int level) { return Math.max(1, Math.min(maxLevel(), level) * TIERS_PER_LEVEL); }

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
        if (blockBreakSpeed == null && miningEfficiency == null) return;
        if (blockBreakSpeed != null && Double.isNaN(state.originalBlockBreakSpeed)) state.originalBlockBreakSpeed = blockBreakSpeed.getBaseValue();
        if (miningEfficiency != null && Double.isNaN(state.originalMiningEfficiency)) state.originalMiningEfficiency = miningEfficiency.getBaseValue();
        double multiplier = speedMultiplier(level, tier);
        if (blockBreakSpeed != null) blockBreakSpeed.setBaseValue(state.originalBlockBreakSpeed * multiplier);
        if (miningEfficiency != null) miningEfficiency.setBaseValue(state.originalMiningEfficiency + MINING_EFFICIENCY_BONUS_PER_TIER * tier);
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
        if (blockBreakSpeed != null && !Double.isNaN(state.originalBlockBreakSpeed)) blockBreakSpeed.setBaseValue(state.originalBlockBreakSpeed);
        AttributeInstance miningEfficiency = player.getAttribute(Attribute.MINING_EFFICIENCY);
        if (miningEfficiency != null && !Double.isNaN(state.originalMiningEfficiency)) miningEfficiency.setBaseValue(state.originalMiningEfficiency);
        state.originalBlockBreakSpeed = Double.NaN;
        state.originalMiningEfficiency = Double.NaN;
        state.speedApplied = false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        State state = states.get(player.getUniqueId());
        if (state == null || !state.speedApplied) return;
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        if (newItem == null || levelOf(newItem) <= 0) {
            resetSpeed(player, state);
            state.stacks = 0;
            state.lastTier = 0;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        /*
         * Leveling は Otherworld グループごとに BLOCK_BREAK_SPEED を持つ。
         * 移動前に保存した original 値を数秒後に復元すると、旧グループの速度が新グループへ漏れる。
         * WorldChange 時は Momentum の一時状態だけ破棄し、属性値は先に実行される各プロフィール同期へ委ねる。
         */
        states.remove(event.getPlayer().getUniqueId());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            State state = entry.getValue();
            if (state.speedApplied && now - state.lastBreakMs > RESET_MS) {
                Player player = plugin.getServer().getPlayer(entry.getKey());
                if (player != null) {
                    resetSpeed(player, state);
                    state.stacks = 0;
                    state.lastTier = 0;
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        State state = states.remove(event.getPlayer().getUniqueId());
        if (state != null) resetSpeed(event.getPlayer(), state);
    }

    @Override
    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) resetSpeed(player, entry.getValue());
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
