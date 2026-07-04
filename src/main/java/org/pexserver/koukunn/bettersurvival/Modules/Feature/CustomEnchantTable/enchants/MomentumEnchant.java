package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
 * 掘り続けるほど採掘速度が上がる。実装は Haste ポーション効果の付与で、
 * Minecraft の採掘速度計算は「(ツール速度 + 効率強化ボーナス) × Haste 倍率」
 * のため、効率強化エンチャントと自然に重複する（効率付きの速度が基準になる）。
 * 3秒間掘らないと効果が切れてスタックもリセットされる。
 */
public class MomentumEnchant extends CustomEnchant {

    /** この時間掘らないとスタックリセット */
    private static final long RESET_MS = 3_000L;
    /** Haste 効果の持続 (3秒 + 猶予) */
    private static final int EFFECT_DURATION_TICKS = 70;
    /** 何ブロック連続で掘るごとに Haste が1段階上がるか */
    private static final int BLOCKS_PER_TIER = 4;

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
                + "\n§73秒間掘らないと元に戻る"
                + "\n§7効率強化と重複可 (効率込みの速度が基準)";
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
        return 3;
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
            state.stacks = 0;
            state.lastTier = 0;
        }
        state.stacks++;
        state.lastBreakMs = now;

        int tierCap = level + 1; // Lv1→Haste II, Lv2→III, Lv3→IV まで
        int tier = Math.min(state.stacks / BLOCKS_PER_TIER, tierCap);
        if (tier <= 0) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, EFFECT_DURATION_TICKS, tier - 1, true, false, true));
        if (tier != state.lastTier) {
            state.lastTier = tier;
            player.sendActionBar(ComponentUtils.legacy("§d✦ 採掘加速 §f×" + state.stacks + " §7(Haste " + roman(tier) + ")"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void shutdown() {
        states.clear();
    }

    private static final class State {
        int stacks;
        int lastTier;
        long lastBreakMs;
    }
}
