package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.settings.ToolSettingsStore;

import java.util.List;

/**
 * 採掘穴埋め (autoreplace)。
 *
 * ツール設定で ON にし、かつオフハンドにブロックを持って掘ると、
 * 掘った場所をオフハンドのブロックで即座に置き換える。
 * オフハンドのブロックは 1 個ずつ消費される (クリエイティブは消費なし)。
 *
 * ON/OFF は「左クリック → 右クリック → 左クリック」で開くツール設定メニューから切り替える。
 */
public class AutoReplaceEnchant extends CustomEnchant {

    /** ツール設定の判定キー */
    public static final String SETTING_ID = "autoreplace";

    private final ToolSettingsStore settings;

    public AutoReplaceEnchant(Loader plugin, ToolSettingsStore settings) {
        super(plugin);
        this.settings = settings;
    }

    @Override
    public String id() {
        return "autoreplace";
    }

    @Override
    public String displayName() {
        return "採掘穴埋め";
    }

    @Override
    public String description() {
        return "§7オフハンドにブロックを持って掘ると"
                + "\n§7掘った場所をそのブロックで即座に埋める"
                + "\n§7ブロックは1個ずつ消費される"
                + "\n§e左クリック→右クリック→左クリック の"
                + "\n§eツール設定メニューで ON/OFF を切り替え";
    }

    @Override
    public Material icon() {
        return Material.DISPENSER;
    }

    @Override
    public String vanillaParentName() {
        return null;
    }

    @Override
    public int maxLevel() {
        return 1;
    }

    @Override
    public boolean supports(Material type) {
        String name = type.name();
        return name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_AXE");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return List.of(new ItemStack(Material.LAPIS_LAZULI, 32), new ItemStack(Material.DISPENSER, 1));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (levelOf(player.getInventory().getItemInMainHand()) <= 0) {
            return;
        }
        if (!settings.isEnabled(player.getUniqueId(), SETTING_ID)) {
            return;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand == null || !offhand.getType().isBlock() || offhand.getType().isAir()) {
            return; // オフハンドが設置可能なブロックでない
        }
        Material fill = offhand.getType();
        Location loc = event.getBlock().getLocation();
        // 破壊が確定した後 (次tick) に埋める。掘ったブロックが air になってから設置する
        Bukkit.getScheduler().runTask(plugin, () -> tryPlace(player, loc, fill));
    }

    private void tryPlace(Player player, Location loc, Material fill) {
        if (!player.isOnline()) {
            return;
        }
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        Block block = loc.getBlock();
        if (!block.getType().isAir() && !block.isLiquid()) {
            return; // 既に何か置かれている
        }
        // 他のエンティティの中には埋めない — 窒息・閉じ込め防止。
        // ただし採掘者自身は「足元や目の前」を掘ると必ず重なるので除外する
        if (!world.getNearbyEntities(BoundingBox.of(block),
                entity -> entity instanceof LivingEntity
                        && !entity.getUniqueId().equals(player.getUniqueId())).isEmpty()) {
            return;
        }
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (!creative) {
            if (offhand == null || offhand.getType() != fill || offhand.getAmount() <= 0) {
                return; // 途中で持ち替えた/使い切った
            }
        }
        block.setType(fill);
        world.playSound(block.getLocation(), block.getBlockData().getSoundGroup().getPlaceSound(),
                SoundCategory.BLOCKS, 0.7F, 1.0F);
        if (!creative) {
            offhand.setAmount(offhand.getAmount() - 1);
            player.getInventory().setItemInOffHand(offhand.getAmount() > 0 ? offhand : null);
        }
    }
}
