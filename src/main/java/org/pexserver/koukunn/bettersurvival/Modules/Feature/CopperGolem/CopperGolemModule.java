package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import io.papermc.paper.world.WeatheringCopperState;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.craft.CopperGolemCraftItems;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.mode.CombatModeWorker;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.mode.CropModeWorker;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.mode.ModeExecutionContext;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.mode.ModeWorker;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.ContainerTarget;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.CropRouteMode;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.GolemMode;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.GolemProfile;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.PendingTargetSelection;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.TargetSelectionType;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.ui.CopperGolemBoneMealSourceMenuUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.ui.CopperGolemCombatMainMenuUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.ui.CopperGolemCombatWeaponMenuUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.ui.CopperGolemCropFilterMenuUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.ui.CopperGolemMainMenuUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.ui.CopperGolemTargetMenuUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.worker.CombatWorker;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.worker.CropHarvestWorker;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ClaimRegion;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.LandProtectionModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.SharedStorageModule;
import org.pexserver.koukunn.bettersurvival.Modules.ItemCombineModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class CopperGolemModule implements Listener {

    private static final String FEATURE_KEY = "coppergolem";
    private static final String CORE_RECIPE_KEY = "copper_golem_core";
    private static final String SUMMON_RECIPE_KEY = "copper_golem_spawn";
    private static final int MAX_LEVEL = 100;
    private static final int REPLANT_UNLOCK_COST = 3;
    private static final int BONE_MEAL_UNLOCK_COST = 3;
    private static final int COMBAT_HEALTH_PER_POINT = 5;
    private static final int COMBAT_MAX_HEALTH = 100;
    private static final double COMBAT_REGEN_AMOUNT = 1.0D;
    private static final long COMBAT_REGEN_INTERVAL_TICKS = 20L;
    private static final long MISSING_CONFIRM_MILLIS = 10_000L;
    private static final long RESPAWN_RETRY_MILLIS = 15_000L;

    private final Loader plugin;
    private final ToggleModule toggle;
    private final ChestLockModule chestLockModule;
    private final SharedStorageModule sharedStorageModule;
    private final CopperGolemStore store;
    private final CopperGolemCraftItems craftItems;
    private final CropHarvestWorker cropHarvestWorker;
    private final CombatWorker combatWorker;
    private final Map<GolemMode, ModeWorker> modeWorkers = new LinkedHashMap<>();

    private final org.bukkit.NamespacedKey summonCoreKey;
    private final org.bukkit.NamespacedKey golemIdKey;

    private final Map<String, GolemProfile> profiles = new LinkedHashMap<>();
    private final Map<UUID, PendingTargetSelection> pendingSelections = new LinkedHashMap<>();
    private final Map<UUID, String> combatEquipmentMenuEditors = new LinkedHashMap<>();
    private final Map<UUID, Long> lastCombatActivityTickByGolem = new LinkedHashMap<>();
    private final Map<String, Location> lastKnownLocationByGolemId = new LinkedHashMap<>();
    private final Map<String, Long> missingSinceMillisByGolemId = new LinkedHashMap<>();
    private final Map<String, Long> lastRespawnAttemptMillisByGolemId = new LinkedHashMap<>();

    private BukkitTask workerTask;

    public CopperGolemModule(Loader plugin, ToggleModule toggle, ItemCombineModule itemCombineModule) {
        this.plugin = plugin;
        this.toggle = toggle;
        this.chestLockModule = plugin.getChestLockModule();
        this.sharedStorageModule = plugin.getSharedStorageModule();
        this.store = new CopperGolemStore(plugin.getConfigManager());
        this.summonCoreKey = new org.bukkit.NamespacedKey(plugin, "copper_golem_core");
        this.golemIdKey = new org.bukkit.NamespacedKey(plugin, "copper_golem_id");
        this.craftItems = new CopperGolemCraftItems(this.summonCoreKey);
        this.cropHarvestWorker = new CropHarvestWorker();
        this.combatWorker = new CombatWorker();
        this.modeWorkers.put(GolemMode.CROP, new CropModeWorker(this.cropHarvestWorker));
        this.modeWorkers.put(GolemMode.COMBAT, new CombatModeWorker(this.combatWorker));
        loadProfiles();
        registerRecipes(itemCombineModule);
        this.workerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickWorkers, 20L, 20L);
    }

    public void shutdown() {
        if (workerTask != null) {
            workerTask.cancel();
            workerTask = null;
        }
        saveProfiles();
    }

    private void registerRecipes(ItemCombineModule itemCombineModule) {
        itemCombineModule.recipe(CORE_RECIPE_KEY)
                .first(stack -> stack != null && stack.getType() == Material.COPPER_BLOCK)
                .second(stack -> stack != null && stack.getType() == Material.CARVED_PUMPKIN)
                .groundRadius(0.5D)
                .airRadius(1.5D)
                .verticalRadius(2.0D)
                .allowAirCombine(true)
                .then(this::craftSummonCore);

        itemCombineModule.recipe(SUMMON_RECIPE_KEY)
                .first(craftItems::isSummonCore)
                .second(craftItems::isGolemIdTag)
                .groundRadius(0.5D)
                .airRadius(1.5D)
                .verticalRadius(2.0D)
                .allowAirCombine(true)
                .then(this::summonCopperGolem);

        itemCombineModule.recipe(SUMMON_RECIPE_KEY + "_rev")
                .first(craftItems::isGolemIdTag)
                .second(craftItems::isSummonCore)
                .groundRadius(0.5D)
                .airRadius(1.5D)
                .verticalRadius(2.0D)
                .allowAirCombine(true)
                .then(this::summonCopperGolem);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof CopperGolem golem)) {
            return;
        }
        String golemId = golem.getPersistentDataContainer().get(golemIdKey, PersistentDataType.STRING);
        if (golemId == null || golemId.isBlank()) {
            return;
        }
        GolemProfile profile = profiles.get(golemId);
        if (profile == null) {
            return;
        }
        if (!event.getPlayer().isSneaking()) {
            return;
        }

        event.setCancelled(true);
        openMainMenu(event.getPlayer(), profile);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        PendingTargetSelection pending = pendingSelections.get(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        GolemProfile profile = profiles.get(pending.golemId());
        if (profile == null) {
            pendingSelections.remove(event.getPlayer().getUniqueId());
            return;
        }

        ContainerTarget target = resolveContainerTarget(clicked);
        if (target == null) {
            event.getPlayer().sendMessage("§cチェスト/ラージチェスト/樽を左クリックしてください");
            event.setCancelled(true);
            return;
        }
        if (!canRegisterContainerTarget(event.getPlayer(), target)) {
            event.setCancelled(true);
            return;
        }

        if (pending.type() == TargetSelectionType.BONE_MEAL_SOURCE) {
            setContainerTarget(profile.boneMealSources(), pending.slot(), target);
        } else {
            setContainerTarget(profile.targets(), pending.slot(), target);
        }
        pendingSelections.remove(event.getPlayer().getUniqueId());
        saveProfiles();

        spawnContainerOutline(target.footprint());
        event.getPlayer().playSound(target.anchor(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.2F);
        if (pending.type() == TargetSelectionType.BONE_MEAL_SOURCE) {
            event.getPlayer().sendMessage("§a骨粉供給元スロット " + (pending.slot() + 1) + " に設定しました");
        } else {
            event.getPlayer().sendMessage("§a保管先スロット " + (pending.slot() + 1) + " に設定しました");
        }
        event.setCancelled(true);
        if (pending.type() == TargetSelectionType.BONE_MEAL_SOURCE) {
            openBoneMealSourceMenu(event.getPlayer(), profile);
        } else {
            openTargetMenu(event.getPlayer(), profile);
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onCombatEquipmentMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String profileId = combatEquipmentMenuEditors.get(player.getUniqueId());
        if (profileId == null || profileId.isBlank()) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof ChestUI menu)) {
            return;
        }
        if (!CopperGolemCombatWeaponMenuUI.MENU_TYPE.equals(menu.getType())) {
            return;
        }
        if (event.getView().getTopInventory() != menu.getInventory()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshCombatEquipmentFromMenu(player, profileId, true));
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onCombatEquipmentMenuDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String profileId = combatEquipmentMenuEditors.get(player.getUniqueId());
        if (profileId == null || profileId.isBlank()) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof ChestUI menu)) {
            return;
        }
        if (!CopperGolemCombatWeaponMenuUI.MENU_TYPE.equals(menu.getType())) {
            return;
        }
        int topSize = menu.getInventory().getSize();
        boolean touchesTop = false;
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot != null && rawSlot >= 0 && rawSlot < topSize) {
                touchesTop = true;
                break;
            }
        }
        if (!touchesTop) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshCombatEquipmentFromMenu(player, profileId, true));
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onCombatEquipmentMenuClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        String profileId = combatEquipmentMenuEditors.get(player.getUniqueId());
        if (profileId == null || profileId.isBlank()) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ChestUI menu)) {
            return;
        }
        if (!CopperGolemCombatWeaponMenuUI.MENU_TYPE.equals(menu.getType())) {
            return;
        }
        combatEquipmentMenuEditors.remove(player.getUniqueId());
        refreshCombatEquipmentFromInventory(player, profileId, event.getInventory(), true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCopperGolemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof CopperGolem golem)) {
            return;
        }
        String golemId = golem.getPersistentDataContainer().get(golemIdKey, PersistentDataType.STRING);
        if (golemId == null || golemId.isBlank() || !profiles.containsKey(golemId)) {
            return;
        }
        lastCombatActivityTickByGolem.put(golem.getUniqueId(), golem.getWorld().getGameTime());
        spawnFloatingCombatText(golem.getLocation().clone().add(0.0D, 1.5D, 0.0D), "-" + formatCombatValue(event.getFinalDamage()), NamedTextColor.RED);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCopperGolemSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof CopperGolem golem)) {
            return;
        }
        String golemId = golem.getPersistentDataContainer().get(golemIdKey, PersistentDataType.STRING);
        if (golemId == null || golemId.isBlank()) {
            return;
        }
        GolemProfile profile = profiles.get(golemId);
        if (profile == null) {
            return;
        }

        UUID previousEntityUuid = profile.entityUuid();
        if (previousEntityUuid != null && previousEntityUuid.equals(golem.getUniqueId())) {
            return;
        }

        clearTransientTracking(previousEntityUuid);
        profile.setEntityUuid(golem.getUniqueId());
        applyProfileToEntity(golem, profile);
        saveProfiles();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onContainerBreak(BlockBreakEvent event) {
        if (!isTargetContainerMaterial(event.getBlock().getType())) {
            return;
        }
        if (removeRegisteredTargetsByLocation(event.getBlock().getLocation())) {
            saveProfiles();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onContainerExplode(EntityExplodeEvent event) {
        boolean changed = removeRegisteredTargetsByLocations(event.blockList().stream().map(Block::getLocation).toList());
        if (changed) {
            saveProfiles();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onContainerExplode(BlockExplodeEvent event) {
        boolean changed = removeRegisteredTargetsByLocations(event.blockList().stream().map(Block::getLocation).toList());
        if (changed) {
            saveProfiles();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof CopperGolem golem) {
            String id = golem.getPersistentDataContainer().get(golemIdKey, PersistentDataType.STRING);
            if (id == null || id.isBlank()) {
                return;
            }
            if (removeProfile(id)) {
                saveProfiles();
            }
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        Entity causing = event.getDamageSource().getCausingEntity();
        if (!(causing instanceof CopperGolem golem)) {
            return;
        }
        String golemId = golem.getPersistentDataContainer().get(golemIdKey, PersistentDataType.STRING);
        if (golemId == null || golemId.isBlank()) {
            return;
        }
        GolemProfile profile = profiles.get(golemId);
        if (profile == null || profile.mode() != GolemMode.COMBAT) {
            return;
        }
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) {
                continue;
            }
            drops.add(drop.clone());
        }
        event.getDrops().clear();
        if (!drops.isEmpty()) {
            combatWorker.storeDropsToTargets(profile, golem, drops);
        }
        int remainedExp = combatWorker.applyMendingAndReduceExp(golem, event.getDroppedExp());
        event.setDroppedExp(remainedExp);
        gainProgress(profile, 1);
        saveProfiles();
    }

    private void craftSummonCore(ItemCombineModule.CombineMatch match) {
        match.consumeMatchedItems(1, 1);
        ItemStack core = craftItems.createSummonCoreItem();
        Item dropped = match.center().getWorld().dropItem(match.center(), core);
        dropped.setVelocity(dropped.getVelocity().setY(0.2D));
        match.center().getWorld().spawnParticle(Particle.WAX_ON, match.center(), 25, 0.4D, 0.2D, 0.4D, 0.02D);
        match.center().getWorld().playSound(match.center(), Sound.BLOCK_COPPER_BULB_TURN_ON, 1.0F, 1.0F);
    }

    private void summonCopperGolem(ItemCombineModule.CombineMatch match) {
        ItemStack first = match.first().getItemStack();
        ItemStack second = match.second().getItemStack();
        ItemStack tagStack = craftItems.isGolemIdTag(first) ? first : second;
        String golemId = craftItems.extractGolemId(tagStack);
        if (golemId == null || golemId.isBlank()) {
            return;
        }
        if (profiles.containsKey(golemId)) {
            match.center().getWorld().playSound(match.center(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8F, 0.8F);
            return;
        }

        match.consumeMatchedItems(1, 1);
        Location spawnLocation = match.center().clone().add(0.0D, 0.5D, 0.0D);
        CopperGolem golem = spawnLocation.getWorld().spawn(spawnLocation, CopperGolem.class);
        golem.setPersistent(true);

        GolemProfile profile = new GolemProfile(golemId, golem.getUniqueId(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, false, false, true, CropRouteMode.NEAR_ORIGIN, GolemMode.IDLE);
        profiles.put(golemId, profile);
        applyProfileToEntity(golem, profile);

        spawnLocation.getWorld().spawnParticle(Particle.GLOW, spawnLocation, 30, 0.3D, 0.6D, 0.3D, 0.03D);
        spawnLocation.getWorld().playSound(spawnLocation, Sound.ENTITY_GOAT_HORN_BREAK, 0.8F, 1.3F);
        saveProfiles();
    }

    private void openMainMenu(Player player, GolemProfile profile) {
        if (profile.mode() == GolemMode.COMBAT) {
            CopperGolemCombatMainMenuUI.open(player, profile, maxRangeByPoints(profile), resolveCombatMaxHealth(profile), new CopperGolemCombatMainMenuUI.ActionHandler() {
                @Override
                public void onToggleMode(Player p) {
                    if (!profiles.containsKey(profile.id())) {
                        return;
                    }
                    profile.setMode(nextMode(profile.mode()));
                    saveProfiles();
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.2F);
                    openMainMenu(p, profile);
                }

                @Override
                public void onEditRange(Player p) {
                    if (!profiles.containsKey(profile.id())) {
                        return;
                    }
                    playUiClick(p);
                    openRangeMenu(p, profile);
                }

                @Override
                public void onUpgradeRange(Player p) {
                    if (!profiles.containsKey(profile.id())) {
                        return;
                    }
                    playUiClick(p);
                    if (!spendPoints(profile, 1)) {
                        p.sendMessage("§cポイントが不足しています");
                    } else {
                        profile.setRangePoints(profile.rangePoints() + 1);
                        int newMaxRange = maxRangeByPoints(profile);
                        if (profile.range() > newMaxRange) {
                            profile.setRange(newMaxRange);
                        }
                        saveProfiles();
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.4F);
                    }
                    openMainMenu(p, profile);
                }

                @Override
                public void onUpgradeCombatHealth(Player p) {
                    if (!profiles.containsKey(profile.id())) {
                        return;
                    }
                    playUiClick(p);
                    if (resolveCombatMaxHealth(profile) >= COMBAT_MAX_HEALTH) {
                        p.sendMessage("§c戦闘HPはこれ以上強化できません");
                        openMainMenu(p, profile);
                        return;
                    }
                    if (!spendPoints(profile, 1)) {
                        p.sendMessage("§cポイントが不足しています");
                        openMainMenu(p, profile);
                        return;
                    }
                    int nextPoints = profile.combatHealthPoints() + 1;
                    profile.setCombatHealthPoints(nextPoints);
                    CopperGolem target = resolveGolem(profile);
                    if (target != null) {
                        applyProfileToEntity(target, profile);
                    }
                    saveProfiles();
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.4F);
                    openMainMenu(p, profile);
                }

                @Override
                public void onConfigureCombatWeapon(Player p) {
                    if (!profiles.containsKey(profile.id())) {
                        return;
                    }
                    playUiClick(p);
                    openCombatWeaponMenu(p, profile);
                }

                @Override
                public void onOpenTargets(Player p) {
                    playUiClick(p);
                    openTargetMenu(p, profile);
                }

                @Override
                public void onClose(Player p) {
                    playUiClick(p);
                    p.closeInventory();
                }
            });
            return;
        }

        CopperGolemMainMenuUI.open(
                player,
                profile,
                maxRangeByPoints(profile),
                isReplantUnlocked(profile),
                isBoneMealUnlocked(profile),
                REPLANT_UNLOCK_COST,
                BONE_MEAL_UNLOCK_COST,
                new CopperGolemMainMenuUI.ActionHandler() {
                    @Override
                    public void onToggleMode(Player p) {
                        if (!profiles.containsKey(profile.id())) {
                            return;
                        }
                        profile.setMode(nextMode(profile.mode()));
                        saveProfiles();
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.2F);
                        openMainMenu(p, profile);
                    }

                    @Override
                    public void onEditRange(Player p) {
                        if (!profiles.containsKey(profile.id())) {
                            return;
                        }
                        playUiClick(p);
                        openRangeMenu(p, profile);
                    }

                    @Override
                    public void onUpgradeHarvest(Player p) {
                        if (!profiles.containsKey(profile.id())) {
                            return;
                        }
                        playUiClick(p);
                        if (!spendPoints(profile, 1)) {
                            p.sendMessage("§cポイントが不足しています");
                        } else {
                            profile.setHarvestPoints(profile.harvestPoints() + 1);
                            saveProfiles();
                            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.4F);
                        }
                        openMainMenu(p, profile);
                    }

                    @Override
                    public void onUpgradeRange(Player p) {
                        if (!profiles.containsKey(profile.id())) {
                            return;
                        }
                        playUiClick(p);
                        if (!spendPoints(profile, 1)) {
                            p.sendMessage("§cポイントが不足しています");
                        } else {
                            profile.setRangePoints(profile.rangePoints() + 1);
                            int newMaxRange = maxRangeByPoints(profile);
                            if (profile.range() > newMaxRange) {
                                profile.setRange(newMaxRange);
                            }
                            saveProfiles();
                            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.4F);
                        }
                        openMainMenu(p, profile);
                    }

                    @Override
                    public void onUpgradeMoveSpeed(Player p) {
                        if (!profiles.containsKey(profile.id())) {
                            return;
                        }
                        playUiClick(p);
                        if (!spendPoints(profile, 1)) {
                            p.sendMessage("§cポイントが不足しています");
                        } else {
                            profile.setMoveSpeedPoints(profile.moveSpeedPoints() + 1);
                            saveProfiles();
                            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.4F);
                        }
                        openMainMenu(p, profile);
                    }

                    @Override
                    public void onToggleCropRouteMode(Player p) {
                        if (!profiles.containsKey(profile.id())) {
                            return;
                        }
                        playUiClick(p);
                        profile.setCropRouteMode(profile.cropRouteMode().next());
                        saveProfiles();
                        openMainMenu(p, profile);
                    }

                    @Override
                    public void onConfigureCombatWeapon(Player p) {
                        if (!profiles.containsKey(profile.id())) {
                            return;
                        }
                        playUiClick(p);
                        openCombatWeaponMenu(p, profile);
                    }

                    @Override
                    public void onUnlockReplant(Player p) {
                        if (!profiles.containsKey(profile.id())) {
                            return;
                        }
                        playUiClick(p);
                        if (isReplantUnlocked(profile)) {
                            p.sendMessage("§a既に植え直し機能は解放済みです");
                        } else if (!spendPoints(profile, REPLANT_UNLOCK_COST)) {
                            p.sendMessage("§cポイントが不足しています");
                        } else {
                            profile.setReplantPoints(1);
                            saveProfiles();
                            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.2F);
                        }
                        openMainMenu(p, profile);
                    }

                    @Override
                    public void onUnlockBoneMeal(Player p) {
                        if (!profiles.containsKey(profile.id())) {
                            return;
                        }
                        playUiClick(p);
                        if (isBoneMealUnlocked(profile)) {
                            p.sendMessage("§a既に骨粉機能は解放済みです");
                        } else if (!spendPoints(profile, BONE_MEAL_UNLOCK_COST)) {
                            p.sendMessage("§cポイントが不足しています");
                        } else {
                            profile.setBoneMealPoints(1);
                            saveProfiles();
                            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.2F);
                        }
                        openMainMenu(p, profile);
                    }

                    @Override
                    public void onToggleReplant(Player p) {
                        if (!profiles.containsKey(profile.id())) {
                            return;
                        }
                        playUiClick(p);
                        if (!isReplantUnlocked(profile)) {
                            p.sendMessage("§c植え直し機能が未解放です");
                        } else {
                            profile.setAutoReplant(!profile.autoReplant());
                            saveProfiles();
                        }
                        openMainMenu(p, profile);
                    }

                    @Override
                    public void onToggleBoneMeal(Player p) {
                        if (!profiles.containsKey(profile.id())) {
                            return;
                        }
                        playUiClick(p);
                        if (!isBoneMealUnlocked(profile)) {
                            p.sendMessage("§c骨粉機能が未解放です");
                        } else {
                            profile.setAutoBoneMeal(!profile.autoBoneMeal());
                            saveProfiles();
                        }
                        openMainMenu(p, profile);
                    }

                    @Override
                    public void onToggleTill(Player p) {
                        if (!profiles.containsKey(profile.id())) {
                            return;
                        }
                        playUiClick(p);
                        profile.setAutoTill(!profile.autoTill());
                        saveProfiles();
                        openMainMenu(p, profile);
                    }

                    @Override
                    public void onOpenCropFilter(Player p) {
                        playUiClick(p);
                        openCropFilterMenu(p, profile);
                    }

                    @Override
                    public void onOpenTargets(Player p) {
                        playUiClick(p);
                        openTargetMenu(p, profile);
                    }

                    @Override
                    public void onOpenBoneMealSources(Player p) {
                        playUiClick(p);
                        if (!isBoneMealUnlocked(profile)) {
                            p.sendMessage("§c骨粉機能が未解放です");
                            openMainMenu(p, profile);
                            return;
                        }
                        openBoneMealSourceMenu(p, profile);
                    }

                    @Override
                    public void onClose(Player p) {
                        playUiClick(p);
                        p.closeInventory();
                    }
                });
    }

    private void openTargetMenu(Player player, GolemProfile profile) {
        CopperGolemTargetMenuUI.open(player, profile, new CopperGolemTargetMenuUI.ActionHandler() {
            @Override
            public void onBack(Player p) {
                playUiClick(p);
                openMainMenu(p, profile);
            }

            @Override
            public void onClear(Player p) {
                playUiClick(p);
                profile.targets().clear();
                saveProfiles();
                openTargetMenu(p, profile);
            }

            @Override
            public void onClose(Player p) {
                playUiClick(p);
                p.closeInventory();
            }

            @Override
            public void onSelectSlot(Player p, int slot) {
                playUiClick(p);
                pendingSelections.put(p.getUniqueId(), new PendingTargetSelection(profile.id(), slot, TargetSelectionType.STORAGE));
                p.closeInventory();
                p.sendMessage("§e設定したいチェスト/ラージチェスト/樽を左クリックしてください");
            }
        });
    }

    private void openBoneMealSourceMenu(Player player, GolemProfile profile) {
        CopperGolemBoneMealSourceMenuUI.open(player, profile, new CopperGolemBoneMealSourceMenuUI.ActionHandler() {
            @Override
            public void onBack(Player p) {
                playUiClick(p);
                openMainMenu(p, profile);
            }

            @Override
            public void onClear(Player p) {
                playUiClick(p);
                profile.boneMealSources().clear();
                saveProfiles();
                openBoneMealSourceMenu(p, profile);
            }

            @Override
            public void onClose(Player p) {
                playUiClick(p);
                p.closeInventory();
            }

            @Override
            public void onSelectSlot(Player p, int slot) {
                playUiClick(p);
                pendingSelections.put(p.getUniqueId(), new PendingTargetSelection(profile.id(), slot, TargetSelectionType.BONE_MEAL_SOURCE));
                p.closeInventory();
                p.sendMessage("§e骨粉供給元にするチェスト/ラージチェスト/樽を左クリックしてください");
            }
        });
    }

    private void openCropFilterMenu(Player player, GolemProfile profile) {
        CopperGolemCropFilterMenuUI.open(player, profile, new CopperGolemCropFilterMenuUI.ActionHandler() {
            @Override
            public void onBack(Player p) {
                playUiClick(p);
                openMainMenu(p, profile);
            }

            @Override
            public void onClear(Player p) {
                playUiClick(p);
                profile.cropFilters().clear();
                saveProfiles();
                openCropFilterMenu(p, profile);
            }

            @Override
            public void onClose(Player p) {
                playUiClick(p);
                p.closeInventory();
            }

            @Override
            public void onSelectSlot(Player p, int slot) {
                playUiClick(p);
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    if (slot < profile.cropFilters().size()) {
                        profile.cropFilters().remove(slot);
                    }
                    saveProfiles();
                    openCropFilterMenu(p, profile);
                    return;
                }

                Material filterMaterial = resolveCropFilterMaterial(hand.getType());
                if (filterMaterial == null) {
                    p.sendMessage("§cそのアイテムは作物フィルタに設定できません");
                    return;
                }

                if (slot < profile.cropFilters().size()) {
                    profile.cropFilters().set(slot, filterMaterial);
                } else {
                    while (profile.cropFilters().size() < slot) {
                        profile.cropFilters().add(Material.WHEAT);
                    }
                    profile.cropFilters().add(filterMaterial);
                }
                saveProfiles();
                openCropFilterMenu(p, profile);
            }
        });
    }

    private void openCombatWeaponMenu(Player player, GolemProfile profile) {
        combatEquipmentMenuEditors.put(player.getUniqueId(), profile.id());
        CopperGolemCombatWeaponMenuUI.open(
                player,
                profile,
                resolveCombatWeapon(profile),
                null,
                profile.combatHelmet(),
                profile.combatChestplate(),
                profile.combatLeggings(),
                profile.combatBoots(),
                new CopperGolemCombatWeaponMenuUI.ActionHandler() {
                    @Override
                    public void onBack(Player p) {
                        playUiClick(p);
                        refreshCombatEquipmentFromMenu(p, profile.id(), true);
                        combatEquipmentMenuEditors.remove(p.getUniqueId());
                        openMainMenu(p, profile);
                    }

                    @Override
                    public void onClose(Player p) {
                        playUiClick(p);
                        refreshCombatEquipmentFromMenu(p, profile.id(), true);
                        combatEquipmentMenuEditors.remove(p.getUniqueId());
                        p.closeInventory();
                    }

                    @Override
                    public void onClearInputs(Player p) {
                        playUiClick(p);
                        clearCombatEquipmentInputs(p, profile.id());
                    }

                    @Override
                    public void onTakeCurrent(Player p, int slot) {
                        playUiClick(p);
                        takeCurrentCombatEquipment(p, profile.id(), slot);
                    }
                });
    }

    private void clearCombatEquipmentInputs(Player player, String profileId) {
        ChestUI menu = ChestUI.getOpenMenu(player);
        if (menu == null || !CopperGolemCombatWeaponMenuUI.MENU_TYPE.equals(menu.getType())) {
            return;
        }
        int[] slots = {
                CopperGolemCombatWeaponMenuUI.INPUT_MAIN_HAND,
                CopperGolemCombatWeaponMenuUI.INPUT_HELMET,
                CopperGolemCombatWeaponMenuUI.INPUT_CHESTPLATE,
                CopperGolemCombatWeaponMenuUI.INPUT_LEGGINGS,
                CopperGolemCombatWeaponMenuUI.INPUT_BOOTS
        };
        for (int slot : slots) {
            ItemStack stack = menu.getInventory().getItem(slot);
            if (isAir(stack)) {
                continue;
            }
            menu.getInventory().setItem(slot, null);
            giveOrDrop(player, stack);
        }
        refreshCombatEquipmentFromMenu(player, profileId, false);
        player.updateInventory();
    }

    private void takeCurrentCombatEquipment(Player player, String profileId, int slot) {
        GolemProfile profile = profiles.get(profileId);
        if (profile == null) {
            return;
        }
        ItemStack returned = null;
        if (slot == CopperGolemCombatWeaponMenuUI.CURRENT_WEAPON_SLOT) {
            returned = resolveCombatWeapon(profile);
            profile.setCombatMainHand(null);
            profile.setCombatOffHand(null);
        } else if (slot == CopperGolemCombatWeaponMenuUI.CURRENT_HELMET_SLOT) {
            returned = profile.combatHelmet();
            profile.setCombatHelmet(null);
        } else if (slot == CopperGolemCombatWeaponMenuUI.CURRENT_CHESTPLATE_SLOT) {
            returned = profile.combatChestplate();
            profile.setCombatChestplate(null);
        } else if (slot == CopperGolemCombatWeaponMenuUI.CURRENT_LEGGINGS_SLOT) {
            returned = profile.combatLeggings();
            profile.setCombatLeggings(null);
        } else if (slot == CopperGolemCombatWeaponMenuUI.CURRENT_BOOTS_SLOT) {
            returned = profile.combatBoots();
            profile.setCombatBoots(null);
        }
        if (isAir(returned)) {
            return;
        }
        giveOrDrop(player, returned);
        CopperGolem golem = resolveGolem(profile);
        if (golem != null) {
            applyProfileToEntity(golem, profile);
        }
        ChestUI menu = ChestUI.getOpenMenu(player);
        if (menu != null && CopperGolemCombatWeaponMenuUI.MENU_TYPE.equals(menu.getType())) {
            updateCombatEquipmentMenuCurrentDisplay(menu.getInventory(), profile);
            player.updateInventory();
        }
        saveProfiles();
    }

    private void refreshCombatEquipmentFromMenu(Player player, String profileId, boolean notifyInvalid) {
        if (player == null || !player.isOnline() || profileId == null || profileId.isBlank()) {
            return;
        }
        ChestUI menu = ChestUI.getOpenMenu(player);
        if (menu == null || !CopperGolemCombatWeaponMenuUI.MENU_TYPE.equals(menu.getType())) {
            return;
        }
        refreshCombatEquipmentFromInventory(player, profileId, menu.getInventory(), notifyInvalid);
    }

    private void refreshCombatEquipmentFromInventory(Player player, String profileId, Inventory inventory, boolean notifyInvalid) {
        if (player == null || profileId == null || profileId.isBlank() || inventory == null) {
            return;
        }
        GolemProfile profile = profiles.get(profileId);
        if (profile == null) {
            return;
        }

        ItemStack weapon = consumeCombatMenuSlot(
                player,
                inventory,
                CopperGolemCombatWeaponMenuUI.INPUT_MAIN_HAND,
                combatWorker::isCombatWeapon,
                "§c武器(オフハンド)には耐久値付きの武器/ツールのみ設定できます",
                notifyInvalid);
        consumeCombatMenuSlot(
                player,
                inventory,
                CopperGolemCombatWeaponMenuUI.INPUT_OFF_HAND,
                stack -> false,
                "§cオフハンドスロットにはアイテムを置けません",
                notifyInvalid);
        ItemStack helmet = consumeCombatMenuSlot(
                player,
                inventory,
                CopperGolemCombatWeaponMenuUI.INPUT_HELMET,
                stack -> isValidEquipmentSlot(stack, EquipmentSlot.HEAD),
                "§cヘルメット欄には頭装備のみ設定できます",
                notifyInvalid);
        ItemStack chestplate = consumeCombatMenuSlot(
                player,
                inventory,
                CopperGolemCombatWeaponMenuUI.INPUT_CHESTPLATE,
                stack -> isValidEquipmentSlot(stack, EquipmentSlot.CHEST),
                "§c胸当て欄には胴装備のみ設定できます",
                notifyInvalid);
        ItemStack leggings = consumeCombatMenuSlot(
                player,
                inventory,
                CopperGolemCombatWeaponMenuUI.INPUT_LEGGINGS,
                stack -> isValidEquipmentSlot(stack, EquipmentSlot.LEGS),
                "§cレギンス欄には脚装備のみ設定できます",
                notifyInvalid);
        ItemStack boots = consumeCombatMenuSlot(
                player,
                inventory,
                CopperGolemCombatWeaponMenuUI.INPUT_BOOTS,
                stack -> isValidEquipmentSlot(stack, EquipmentSlot.FEET),
                "§cブーツ欄には足装備のみ設定できます",
                notifyInvalid);

        boolean changed = false;
        if (replaceCombatEquipment(player, weapon, profile.combatMainHand(), profile::setCombatMainHand)) {
            changed = true;
        }
        if (!isAir(profile.combatOffHand())) {
            profile.setCombatOffHand(null);
            changed = true;
        }
        if (replaceCombatEquipment(player, helmet, profile.combatHelmet(), profile::setCombatHelmet)) {
            changed = true;
        }
        if (replaceCombatEquipment(player, chestplate, profile.combatChestplate(), profile::setCombatChestplate)) {
            changed = true;
        }
        if (replaceCombatEquipment(player, leggings, profile.combatLeggings(), profile::setCombatLeggings)) {
            changed = true;
        }
        if (replaceCombatEquipment(player, boots, profile.combatBoots(), profile::setCombatBoots)) {
            changed = true;
        }
        if (!changed) {
            updateCombatEquipmentMenuCurrentDisplay(inventory, profile);
            return;
        }

        CopperGolem golem = resolveGolem(profile);
        if (golem != null) {
            applyProfileToEntity(golem, profile);
        }
        updateCombatEquipmentMenuCurrentDisplay(inventory, profile);
        player.updateInventory();
        saveProfiles();
    }

    private boolean replaceCombatEquipment(Player player, ItemStack input, ItemStack current, Consumer<ItemStack> setter) {
        if (isAir(input) || setter == null) {
            return false;
        }
        if (isSameItem(current, input)) {
            giveOrDrop(player, input);
            return false;
        }
        setter.accept(input);
        if (!isAir(current)) {
            giveOrDrop(player, current);
        }
        return true;
    }

    private ItemStack consumeCombatMenuSlot(
            Player player,
            Inventory inventory,
            int slot,
            Predicate<ItemStack> validator,
            String invalidMessage,
            boolean notifyInvalid) {
        ItemStack stack = inventory.getItem(slot);
        if (isAir(stack)) {
            return null;
        }

        ItemStack single = normalizeSingleItem(stack);
        if (stack.getAmount() > 1) {
            ItemStack remainder = stack.clone();
            remainder.setAmount(stack.getAmount() - 1);
            giveOrDrop(player, remainder);
        }

        if (validator != null && !validator.test(single)) {
            inventory.setItem(slot, null);
            giveOrDrop(player, single);
            if (notifyInvalid && invalidMessage != null && !invalidMessage.isBlank()) {
                player.sendMessage(invalidMessage);
            }
            return null;
        }
        inventory.setItem(slot, null);
        return single;
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        if (isAir(stack)) {
            return;
        }
        ItemStack toGive = stack.clone();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(toGive);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void updateCombatEquipmentMenuCurrentDisplay(Inventory inventory, GolemProfile profile) {
        if (inventory == null || profile == null) {
            return;
        }
        inventory.setItem(CopperGolemCombatWeaponMenuUI.CURRENT_WEAPON_SLOT,
                createCurrentEquipmentDisplay(resolveCombatWeapon(profile), Material.IRON_SWORD, "§e現在: 武器(オフハンド)"));
        inventory.setItem(CopperGolemCombatWeaponMenuUI.CURRENT_DISABLED_SLOT,
                createInfoItem(Material.BARRIER, "§8現在: 使用不可"));
        inventory.setItem(CopperGolemCombatWeaponMenuUI.CURRENT_HELMET_SLOT,
                createCurrentEquipmentDisplay(profile.combatHelmet(), Material.IRON_HELMET, "§e現在: ヘルメット"));
        inventory.setItem(CopperGolemCombatWeaponMenuUI.CURRENT_CHESTPLATE_SLOT,
                createCurrentEquipmentDisplay(profile.combatChestplate(), Material.IRON_CHESTPLATE, "§e現在: 胸当て"));
        inventory.setItem(CopperGolemCombatWeaponMenuUI.CURRENT_LEGGINGS_SLOT,
                createCurrentEquipmentDisplay(profile.combatLeggings(), Material.IRON_LEGGINGS, "§e現在: レギンス"));
        inventory.setItem(CopperGolemCombatWeaponMenuUI.CURRENT_BOOTS_SLOT,
                createCurrentEquipmentDisplay(profile.combatBoots(), Material.IRON_BOOTS, "§e現在: ブーツ"));
    }

    private ItemStack createCurrentEquipmentDisplay(ItemStack current, Material fallback, String label) {
        ItemStack item = normalizeSingleItem(current);
        if (item == null) {
            return createInfoItem(fallback, "§c未装備");
        }
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ComponentUtils.legacy(label));
            meta.lore(List.of(ComponentUtils.legacy("§7クリックで取り外し")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem(Material material, String label) {
        ItemStack item = new ItemStack(material);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ComponentUtils.legacy(label));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void spawnFloatingCombatText(Location location, String text, NamedTextColor color) {
        if (location == null || location.getWorld() == null || text == null || text.isBlank()) {
            return;
        }
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.text(Component.text(text, color));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(false);
            entity.setShadowed(false);
            entity.setDefaultBackground(false);
            entity.setInterpolationDuration(1);
            entity.setTeleportDuration(1);
            entity.setViewRange(12.0F);
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.8F, 0.8F, 0.8F), new AxisAngle4f()));
        });
        new BukkitRunnable() {
            private int tick = 0;
            private final Location base = location.clone();

            @Override
            public void run() {
                if (!display.isValid() || tick >= 12) {
                    display.remove();
                    cancel();
                    return;
                }
                double offsetX = Math.sin(tick * 0.45D) * 0.06D;
                double offsetZ = Math.cos(tick * 0.45D) * 0.03D;
                Location next = base.clone().add(offsetX, tick * 0.055D, offsetZ);
                display.teleport(next);
                tick++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private String formatCombatValue(double value) {
        double rounded = Math.round(value * 10.0D) / 10.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001D) {
            return Integer.toString((int) Math.rint(rounded));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", rounded);
    }

    private void openRangeMenu(Player player, GolemProfile profile) {
        int max = maxRangeByPoints(profile);
        String status = "§7現在値: §f" + profile.range() + " / " + max;
        ChestUI.builder()
                .title("行動範囲設定: " + profile.id())
                .size(27)
                .addButtonAt(10, "§c-10", Material.RED_STAINED_GLASS_PANE, status)
                .addButtonAt(11, "§c-5", Material.ORANGE_STAINED_GLASS_PANE, status)
                .addButtonAt(12, "§c-1", Material.PINK_STAINED_GLASS_PANE, status)
                .addButtonAt(13, "§6現在値", Material.SPYGLASS, status)
                .addButtonAt(14, "§a+1", Material.LIME_STAINED_GLASS_PANE, status)
                .addButtonAt(15, "§a+5", Material.GREEN_STAINED_GLASS_PANE, status)
                .addButtonAt(16, "§a+10", Material.EMERALD_BLOCK, status)
                .addButtonAt(18, "§b5に設定", Material.WHEAT_SEEDS, status)
                .addButtonAt(19, "§b10に設定", Material.CARROT, status)
                .addButtonAt(20, "§b20に設定", Material.POTATO, status)
                .addButtonAt(21, "§b30に設定", Material.BEETROOT_SEEDS, status)
                .addButtonAt(22, "§b50に設定", Material.NETHER_WART, status)
                .addButtonAt(23, "§b最大値に設定", Material.COMPASS, status)
                .addButtonAt(26, "§e戻る", Material.ARROW, "")
                .then((result, p) -> {
                    if (result.slot == null) {
                        return;
                    }
                    playUiClick(p);
                    Integer slot = result.slot;
                    int value = profile.range();
                    switch (slot) {
                        case 10 -> value -= 10;
                        case 11 -> value -= 5;
                        case 12 -> value -= 1;
                        case 14 -> value += 1;
                        case 15 -> value += 5;
                        case 16 -> value += 10;
                        case 18 -> value = 5;
                        case 19 -> value = 10;
                        case 20 -> value = 20;
                        case 21 -> value = 30;
                        case 22 -> value = 50;
                        case 23 -> value = max;
                        case 26 -> {
                            openMainMenu(p, profile);
                            return;
                        }
                        default -> {
                            openRangeMenu(p, profile);
                            return;
                        }
                    }
                    value = Math.max(1, Math.min(max, value));
                    profile.setRange(value);
                    saveProfiles();
                    openRangeMenu(p, profile);
                })
                .show(player);
    }

    private void tickWorkers() {
        if (!toggle.getGlobal(FEATURE_KEY)) {
            return;
        }
        long now = System.currentTimeMillis();

        for (GolemProfile profile : profiles.values()) {
            CopperGolem golem = resolveGolem(profile);
            if (golem == null) {
                if (!shouldAttemptRespawn(profile, now)) {
                    continue;
                }
                lastRespawnAttemptMillisByGolemId.put(profile.id(), now);
                if (respawnMissingGolem(profile)) {
                    missingSinceMillisByGolemId.remove(profile.id());
                    saveProfiles();
                }
                continue;
            }
            lastKnownLocationByGolemId.put(profile.id(), golem.getLocation().clone());
            missingSinceMillisByGolemId.remove(profile.id());
            applyProfileToEntity(golem, profile);
            if (profile.mode() == GolemMode.IDLE) {
                golem.getPathfinder().stopPathfinding();
                golem.setGolemState(CopperGolem.State.IDLE);
                continue;
            }
            ModeWorker modeWorker = modeWorkers.get(profile.mode());
            if (modeWorker == null) {
                golem.getPathfinder().stopPathfinding();
                continue;
            }
            int gained = modeWorker.execute(new ModeExecutionContext(
                    profile,
                    golem,
                    harvestCapacityByPoints(profile),
                    maxRangeByPoints(profile),
                    isReplantUnlocked(profile),
                    isBoneMealUnlocked(profile)));
            if (profile.mode() == GolemMode.COMBAT && golem.getTarget() != null && golem.getTarget().isValid() && !golem.getTarget().isDead()) {
                lastCombatActivityTickByGolem.put(golem.getUniqueId(), golem.getWorld().getGameTime());
            }
            if (profile.mode() == GolemMode.COMBAT) {
                tryNaturalCombatRegen(golem, profile);
            }
            if (gained > 0) {
                gainProgress(profile, gained);
                saveProfiles();
            }
        }
    }

    private void gainProgress(GolemProfile profile, int amount) {
        if (amount <= 0) {
            return;
        }
        profile.setProgress(profile.progress() + amount);
        while (profile.level() < MAX_LEVEL) {
            int need = expToNextLevel(profile.level());
            if (profile.progress() < need) {
                break;
            }
            profile.setProgress(profile.progress() - need);
            profile.setLevel(profile.level() + 1);
            profile.setAvailablePoints(profile.availablePoints() + 1);
        }
    }

    private int expToNextLevel(int level) {
        return 20 + (level * 5);
    }


    private Material resolveCropFilterMaterial(Material material) {
        if (material == null) {
            return null;
        }
        Material converted = cropBlockFromSeedOrHarvest(material);
        if (converted != null) {
            return converted;
        }
        if (material == Material.SUGAR_CANE) {
            return Material.SUGAR_CANE;
        }
        if (!material.isBlock()) {
            return null;
        }
        try {
            return material.createBlockData() instanceof Ageable ? material : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Material cropBlockFromSeedOrHarvest(Material material) {
        return switch (material) {
            case WHEAT_SEEDS, WHEAT -> Material.WHEAT;
            case BEETROOT_SEEDS, BEETROOT -> Material.BEETROOTS;
            case CARROT, CARROTS -> Material.CARROTS;
            case POTATO, POTATOES -> Material.POTATOES;
            case NETHER_WART -> Material.NETHER_WART;
            case TORCHFLOWER_SEEDS, TORCHFLOWER_CROP -> Material.TORCHFLOWER_CROP;
            case COCOA_BEANS, COCOA -> Material.COCOA;
            case SUGAR_CANE -> Material.SUGAR_CANE;
            default -> null;
        };
    }

    private void tryNaturalCombatRegen(CopperGolem golem, GolemProfile profile) {
        double maxHealth = resolveCombatMaxHealth(profile);
        if (golem.getHealth() >= maxHealth) {
            return;
        }
        long gameTime = golem.getWorld().getGameTime();
        long lastCombatTick = lastCombatActivityTickByGolem.getOrDefault(golem.getUniqueId(), Long.MIN_VALUE);
        if ((gameTime - lastCombatTick) < COMBAT_REGEN_INTERVAL_TICKS) {
            return;
        }
        double healed = Math.min(COMBAT_REGEN_AMOUNT, maxHealth - golem.getHealth());
        if (healed <= 0.0D) {
            return;
        }
        golem.setHealth(Math.min(maxHealth, golem.getHealth() + healed));
        spawnFloatingCombatText(golem.getLocation().clone().add(0.0D, 1.6D, 0.0D), "+" + formatCombatValue(healed), NamedTextColor.GREEN);
    }

    private void applyProfileToEntity(CopperGolem golem, GolemProfile profile) {
        golem.getPersistentDataContainer().set(golemIdKey, PersistentDataType.STRING, profile.id());
        golem.customName(Component.text(profile.id() + " - Level: " + profile.level()));
        golem.setCustomNameVisible(false);
        golem.setPersistent(true);
        golem.setCanPickupItems(false);
        applyNoRustSettings(golem);
        syncEquipment(golem, EquipmentSlot.HAND, null);
        syncEquipment(golem, EquipmentSlot.OFF_HAND, resolveCombatWeapon(profile));
        syncEquipment(golem, EquipmentSlot.HEAD, profile.combatHelmet());
        syncEquipment(golem, EquipmentSlot.CHEST, profile.combatChestplate());
        syncEquipment(golem, EquipmentSlot.LEGS, profile.combatLeggings());
        syncEquipment(golem, EquipmentSlot.FEET, profile.combatBoots());
        golem.getEquipment().setItemInMainHandDropChance(0.0F);
        golem.getEquipment().setItemInOffHandDropChance(0.0F);
        golem.getEquipment().setHelmetDropChance(0.0F);
        golem.getEquipment().setChestplateDropChance(0.0F);
        golem.getEquipment().setLeggingsDropChance(0.0F);
        golem.getEquipment().setBootsDropChance(0.0F);
        applyCombatHealth(golem, profile);
    }

    private ItemStack resolveCombatWeapon(GolemProfile profile) {
        if (profile == null) {
            return null;
        }
        ItemStack mainStored = profile.combatMainHand();
        if (!isAir(mainStored)) {
            return mainStored;
        }
        ItemStack oldOffHandStored = profile.combatOffHand();
        if (!isAir(oldOffHandStored)) {
            return oldOffHandStored;
        }
        return null;
    }

    private void syncEquipment(CopperGolem golem, EquipmentSlot slot, ItemStack desired) {
        ItemStack normalized = normalizeSingleItem(desired);
        ItemStack current = normalizeSingleItem(golem.getEquipment().getItem(slot));
        if (isSameItem(current, normalized)) {
            return;
        }
        golem.getEquipment().setItem(slot, normalized, true);
    }

    private boolean isSameItem(ItemStack first, ItemStack second) {
        if (isAir(first) && isAir(second)) {
            return true;
        }
        if (isAir(first) || isAir(second)) {
            return false;
        }
        return first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    private boolean isAir(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    private ItemStack normalizeSingleItem(ItemStack stack) {
        if (isAir(stack)) {
            return null;
        }
        ItemStack cloned = stack.clone();
        cloned.setAmount(1);
        return cloned;
    }

    private boolean isValidEquipmentSlot(ItemStack stack, EquipmentSlot slot) {
        if (isAir(stack) || slot == null) {
            return false;
        }
        if (slot == EquipmentSlot.OFF_HAND) {
            return true;
        }
        return stack.getType().getEquipmentSlot() == slot;
    }

    private void applyCombatHealth(CopperGolem golem, GolemProfile profile) {
        org.bukkit.attribute.AttributeInstance attribute = golem.getAttribute(Attribute.MAX_HEALTH);
        Attributable defaultAttributes = EntityType.COPPER_GOLEM.getDefaultAttributes();
        if (attribute == null) {
            return;
        }
        double base = Math.min(COMBAT_MAX_HEALTH, Math.max(1.0D, defaultAttributes.getAttribute(Attribute.MAX_HEALTH).getBaseValue()));
        double target = resolveCombatMaxHealth(profile, base);
        if (Math.abs(attribute.getBaseValue() - target) > 0.0001D) {
            attribute.setBaseValue(target);
        }
        if (golem.getHealth() > target) {
            golem.setHealth(target);
        }
    }

    private double resolveCombatMaxHealth(GolemProfile profile) {
        if (profile == null) {
            return 20.0D;
        }
        CopperGolem golem = resolveGolem(profile);
        double base = 20.0D;
        if (golem != null) {
            org.bukkit.attribute.AttributeInstance attribute = golem.getAttribute(Attribute.MAX_HEALTH);
            Attributable defaultAttributes = EntityType.COPPER_GOLEM.getDefaultAttributes();
            if (attribute != null) {
                base = Math.min(COMBAT_MAX_HEALTH, Math.max(1.0D, defaultAttributes.getAttribute(Attribute.MAX_HEALTH).getBaseValue()));
            }
        }
        return resolveCombatMaxHealth(profile, base);
    }

    private double resolveCombatMaxHealth(GolemProfile profile, double baseHealth) {
        int points = profile == null ? 0 : profile.combatHealthPoints();
        return resolveCombatMaxHealth(points, baseHealth);
    }

    private double resolveCombatMaxHealth(int healthPoints, double baseHealth) {
        double safeBase = Math.min(COMBAT_MAX_HEALTH, Math.max(1.0D, baseHealth));
        int points = Math.max(0, healthPoints);
        return Math.min(COMBAT_MAX_HEALTH, safeBase + (points * COMBAT_HEALTH_PER_POINT));
    }

    private CopperGolem resolveGolem(GolemProfile profile) {
        CopperGolem selected = findAndConvergeGolemsById(profile.id(), profile.entityUuid());
        if (selected != null && !selected.getUniqueId().equals(profile.entityUuid())) {
            clearTransientTracking(profile.entityUuid());
            profile.setEntityUuid(selected.getUniqueId());
        }
        return selected;
    }

    private CopperGolem findAndConvergeGolemsById(String golemId, UUID preferredUuid) {
        if (golemId == null || golemId.isBlank()) {
            return null;
        }
        List<CopperGolem> candidates = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (CopperGolem candidate : world.getEntitiesByClass(CopperGolem.class)) {
                if (!candidate.isValid() || candidate.isDead()) {
                    continue;
                }
                String candidateId = candidate.getPersistentDataContainer().get(golemIdKey, PersistentDataType.STRING);
                if (Objects.equals(candidateId, golemId)) {
                    candidates.add(candidate);
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        CopperGolem selected = null;
        if (preferredUuid != null) {
            for (CopperGolem candidate : candidates) {
                if (preferredUuid.equals(candidate.getUniqueId())) {
                    selected = candidate;
                    break;
                }
            }
        }
        if (selected == null) {
            selected = candidates.get(0);
            for (CopperGolem candidate : candidates) {
                if (candidate.getTicksLived() < selected.getTicksLived()) {
                    selected = candidate;
                }
            }
        }

        for (CopperGolem candidate : candidates) {
            if (candidate.getUniqueId().equals(selected.getUniqueId())) {
                continue;
            }
            clearTransientTracking(candidate.getUniqueId());
            candidate.remove();
        }
        return selected;
    }

    private boolean shouldAttemptRespawn(GolemProfile profile, long nowMillis) {
        if (profile == null || profile.id() == null || profile.id().isBlank()) {
            return false;
        }
        String golemId = profile.id();
        long missingSince = missingSinceMillisByGolemId.computeIfAbsent(golemId, ignored -> nowMillis);
        if ((nowMillis - missingSince) < MISSING_CONFIRM_MILLIS) {
            return false;
        }
        Long lastAttempt = lastRespawnAttemptMillisByGolemId.get(golemId);
        if (lastAttempt != null && (nowMillis - lastAttempt) < RESPAWN_RETRY_MILLIS) {
            return false;
        }

        Location lastKnown = lastKnownLocationByGolemId.get(golemId);
        if (lastKnown != null && lastKnown.getWorld() != null) {
            int chunkX = lastKnown.getBlockX() >> 4;
            int chunkZ = lastKnown.getBlockZ() >> 4;
            if (!lastKnown.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                return false;
            }
        }
        return true;
    }

    private boolean respawnMissingGolem(GolemProfile profile) {
        Location anchor = resolveRespawnAnchor(profile);
        if (anchor == null || anchor.getWorld() == null) {
            return false;
        }
        Location spawnLocation = findRespawnLocation(anchor);
        if (spawnLocation == null || spawnLocation.getWorld() == null) {
            return false;
        }

        UUID previousEntityUuid = profile.entityUuid();
        CopperGolem golem = spawnLocation.getWorld().spawn(spawnLocation, CopperGolem.class, spawned -> {
            spawned.setPersistent(true);
            spawned.getPersistentDataContainer().set(golemIdKey, PersistentDataType.STRING, profile.id());
            applyNoRustSettings(spawned);
        });
        clearTransientTracking(previousEntityUuid);
        profile.setEntityUuid(golem.getUniqueId());
        lastKnownLocationByGolemId.put(profile.id(), golem.getLocation().clone());
        applyProfileToEntity(golem, profile);
        return true;
    }

    private Location resolveRespawnAnchor(GolemProfile profile) {
        if (profile == null) {
            return null;
        }
        for (ContainerTarget target : profile.boneMealSources()) {
            if (target != null && target.anchor() != null && target.anchor().getWorld() != null) {
                return target.anchor().clone();
            }
        }
        for (ContainerTarget target : profile.targets()) {
            if (target != null && target.anchor() != null && target.anchor().getWorld() != null) {
                return target.anchor().clone();
            }
        }
        return null;
    }

    private Location findRespawnLocation(Location anchor) {
        World world = anchor.getWorld();
        if (world == null) {
            return null;
        }
        int baseX = anchor.getBlockX();
        int baseY = anchor.getBlockY() + 1;
        int baseZ = anchor.getBlockZ();
        for (int radius = 0; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Location candidate = new Location(world, baseX + dx + 0.5D, baseY, baseZ + dz + 0.5D);
                    if (canSpawnGolemAt(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean canSpawnGolemAt(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        return feet.isPassable() && head.isPassable() && ground.getType().isSolid();
    }

    private void applyNoRustSettings(CopperGolem golem) {
        if (golem == null) {
            return;
        }
        golem.setOxidizing(CopperGolem.Oxidizing.waxed());
        if (golem.getWeatheringState() != WeatheringCopperState.UNAFFECTED) {
            golem.setWeatheringState(WeatheringCopperState.UNAFFECTED);
        }
    }

    private void loadProfiles() {
        profiles.clear();
        lastKnownLocationByGolemId.clear();
        missingSinceMillisByGolemId.clear();
        lastRespawnAttemptMillisByGolemId.clear();
        Map<String, CopperGolemStore.StoredGolem> stored = store.loadAll();
        for (CopperGolemStore.StoredGolem entry : stored.values()) {
            UUID entityUuid;
            try {
                entityUuid = UUID.fromString(entry.getEntityUuid());
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            GolemMode mode = GolemMode.from(entry.getMode());
            GolemProfile profile = new GolemProfile(
                    entry.getId(),
                    entityUuid,
                    Math.max(0, Math.min(MAX_LEVEL, entry.getLevel())),
                    Math.max(0, entry.getProgress()),
                    Math.max(0, entry.getAvailablePoints()),
                    Math.max(0, entry.getHarvestPoints()),
                    Math.max(0, entry.getRangePoints()),
                    Math.max(0, entry.getReplantPoints()),
                    Math.max(0, entry.getBoneMealPoints()),
                    Math.max(0, entry.getCombatHealthPoints()),
                    Math.max(0, entry.getMoveSpeedPoints()),
                    Math.max(1, entry.getRange()),
                    entry.isAutoReplant(),
                    entry.isAutoBoneMeal(),
                    entry.isAutoTill(),
                    CropRouteMode.from(entry.getCropRouteMode()),
                    mode);
            profile.setRange(Math.max(1, Math.min(profile.range(), maxRangeByPoints(profile))));
            profile.setCombatMainHand(entry.getCombatMainHand());
            profile.setCombatOffHand(entry.getCombatOffHand());
            profile.setCombatHelmet(entry.getCombatHelmet());
            profile.setCombatChestplate(entry.getCombatChestplate());
            profile.setCombatLeggings(entry.getCombatLeggings());
            profile.setCombatBoots(entry.getCombatBoots());
            if (isAir(profile.combatMainHand()) && !isAir(profile.combatOffHand())) {
                profile.setCombatMainHand(profile.combatOffHand());
            }
            profile.setCombatOffHand(null);
            if (!isReplantUnlocked(profile)) {
                profile.setAutoReplant(false);
            }
            if (!isBoneMealUnlocked(profile)) {
                profile.setAutoBoneMeal(false);
            }

            for (List<String> containerKeys : entry.getTargetContainers()) {
                List<Location> footprint = new ArrayList<>();
                for (String key : containerKeys) {
                    Location location = CopperGolemStore.fromKey(key);
                    if (location != null) {
                        footprint.add(location);
                    }
                }
                if (!footprint.isEmpty()) {
                    footprint.sort(locationComparator());
                    profile.targets().add(new ContainerTarget(footprint.get(0), footprint));
                }
            }
            for (List<String> containerKeys : entry.getBoneMealSourceContainers()) {
                List<Location> footprint = new ArrayList<>();
                for (String key : containerKeys) {
                    Location location = CopperGolemStore.fromKey(key);
                    if (location != null) {
                        footprint.add(location);
                    }
                }
                if (!footprint.isEmpty()) {
                    footprint.sort(locationComparator());
                    profile.boneMealSources().add(new ContainerTarget(footprint.get(0), footprint));
                }
            }

            for (String rawFilter : entry.getCropFilters()) {
                Material material = Material.matchMaterial(rawFilter);
                Material resolved = resolveCropFilterMaterial(material);
                if (resolved != null) {
                    profile.cropFilters().add(resolved);
                }
            }

            profiles.put(profile.id(), profile);

            CopperGolem golem = resolveGolem(profile);
            if (golem != null) {
                lastKnownLocationByGolemId.put(profile.id(), golem.getLocation().clone());
                applyProfileToEntity(golem, profile);
            }
        }
    }

    private void saveProfiles() {
        Map<String, CopperGolemStore.StoredGolem> serialized = new LinkedHashMap<>();
        for (GolemProfile profile : profiles.values()) {
            List<List<String>> containers = new ArrayList<>();
            for (ContainerTarget target : profile.targets()) {
                List<String> keys = new ArrayList<>();
                for (Location location : target.footprint()) {
                    keys.add(CopperGolemStore.toKey(location));
                }
                containers.add(keys);
            }
            List<List<String>> boneMealContainers = new ArrayList<>();
            for (ContainerTarget target : profile.boneMealSources()) {
                List<String> keys = new ArrayList<>();
                for (Location location : target.footprint()) {
                    keys.add(CopperGolemStore.toKey(location));
                }
                boneMealContainers.add(keys);
            }
            List<String> filters = new ArrayList<>();
            for (Material filter : profile.cropFilters()) {
                filters.add(filter.name());
            }

            serialized.put(profile.id(), new CopperGolemStore.StoredGolem(
                    profile.id(),
                    profile.entityUuid().toString(),
                    profile.level(),
                    profile.progress(),
                    profile.availablePoints(),
                    profile.harvestPoints(),
                    profile.rangePoints(),
                    profile.replantPoints(),
                    profile.boneMealPoints(),
                    profile.combatHealthPoints(),
                    profile.moveSpeedPoints(),
                    profile.range(),
                    profile.autoReplant(),
                    profile.autoBoneMeal(),
                    profile.autoTill(),
                    profile.cropRouteMode().name(),
                    profile.mode().name(),
                    containers,
                    boneMealContainers,
                    filters,
                    profile.combatMainHand(),
                    profile.combatOffHand(),
                    profile.combatHelmet(),
                    profile.combatChestplate(),
                    profile.combatLeggings(),
                    profile.combatBoots()));
        }
        store.saveAll(serialized);
    }

    private ContainerTarget resolveContainerTarget(Block clicked) {
        Material type = clicked.getType();
        if (type != Material.CHEST && type != Material.BARREL) {
            return null;
        }

        BlockState state = clicked.getState();
        if (!(state instanceof Container container)) {
            return null;
        }

        Inventory inventory = container.getInventory();
        if (inventory == null) {
            return null;
        }

        List<Location> footprint = new ArrayList<>();
        InventoryHolder holder = inventory.getHolder();

        if (holder instanceof DoubleChest doubleChest) {
            InventoryHolder leftHolder = doubleChest.getLeftSide();
            InventoryHolder rightHolder = doubleChest.getRightSide();
            if (leftHolder instanceof Chest leftChest) {
                footprint.add(leftChest.getLocation());
            }
            if (rightHolder instanceof Chest rightChest) {
                footprint.add(rightChest.getLocation());
            }
        }

        if (footprint.isEmpty()) {
            footprint.add(clicked.getLocation());
        }

        footprint.sort(locationComparator());
        return new ContainerTarget(footprint.get(0), footprint);
    }

    private void setContainerTarget(List<ContainerTarget> targets, int slot, ContainerTarget target) {
        if (slot < 0) {
            return;
        }
        if (slot < targets.size()) {
            targets.set(slot, target);
            return;
        }
        while (targets.size() < slot) {
            targets.add(target);
        }
        targets.add(target);
    }

    private void spawnContainerOutline(List<Location> footprint) {
        for (Location location : footprint) {
            World world = location.getWorld();
            if (world == null) {
                continue;
            }
            double x = location.getBlockX();
            double y = location.getBlockY();
            double z = location.getBlockZ();
            for (int step = 0; step <= 10; step++) {
                double t = step / 10.0D;
                world.spawnParticle(Particle.END_ROD, x + t, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                world.spawnParticle(Particle.END_ROD, x + t, y + 1.0D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                world.spawnParticle(Particle.END_ROD, x + t, y, z + 1.0D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                world.spawnParticle(Particle.END_ROD, x + t, y + 1.0D, z + 1.0D, 1, 0.0D, 0.0D, 0.0D, 0.0D);

                world.spawnParticle(Particle.END_ROD, x, y + t, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                world.spawnParticle(Particle.END_ROD, x + 1.0D, y + t, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                world.spawnParticle(Particle.END_ROD, x, y + t, z + 1.0D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                world.spawnParticle(Particle.END_ROD, x + 1.0D, y + t, z + 1.0D, 1, 0.0D, 0.0D, 0.0D, 0.0D);

                world.spawnParticle(Particle.END_ROD, x, y, z + t, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                world.spawnParticle(Particle.END_ROD, x + 1.0D, y, z + t, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                world.spawnParticle(Particle.END_ROD, x, y + 1.0D, z + t, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                world.spawnParticle(Particle.END_ROD, x + 1.0D, y + 1.0D, z + t, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }


    private int maxRangeByPoints(GolemProfile profile) {
        return Math.min(100, 1 + (profile.rangePoints() * 2));
    }

    private int harvestCapacityByPoints(GolemProfile profile) {
        return Math.min(5000, 5 + (profile.level() * 3) + (profile.harvestPoints() * 25));
    }

    private boolean isReplantUnlocked(GolemProfile profile) {
        return profile.replantPoints() > 0;
    }

    private boolean isBoneMealUnlocked(GolemProfile profile) {
        return profile.boneMealPoints() > 0;
    }

    private boolean spendPoints(GolemProfile profile, int cost) {
        if (profile.availablePoints() < cost) {
            return false;
        }
        profile.setAvailablePoints(profile.availablePoints() - cost);
        return true;
    }

    private void playUiClick(Player player) {
        if (player == null) {
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.2F);
    }

    private GolemMode nextMode(GolemMode mode) {
        if (mode == null) {
            return GolemMode.IDLE;
        }
        return switch (mode) {
            case IDLE -> GolemMode.CROP;
            case CROP -> GolemMode.COMBAT;
            case COMBAT -> GolemMode.IDLE;
        };
    }

    private boolean canRegisterContainerTarget(Player player, ContainerTarget target) {
        for (Location location : target.footprint()) {
            if (isChestLocked(location)) {
                player.sendMessage("§cChestLock 保護チェストは登録できません");
                return false;
            }
            if (isSharedStorageContainer(location)) {
                player.sendMessage("§cSharedStorage 管理チェストは登録できません");
                return false;
            }
            if (isProtectedByOthers(player, location)) {
                player.sendMessage("§c他人の土地保護内のチェストは登録できません");
                return false;
            }
        }
        return true;
    }

    /** 他人の土地保護内 (バイパス不可) の位置なら true */
    private boolean isProtectedByOthers(Player player, Location location) {
        LandProtectionModule land = plugin.getLandProtectionModule();
        if (land == null || !land.isFeatureEnabled()) {
            return false;
        }
        ClaimRegion claim = land.getActiveClaimAt(location);
        return claim != null && !land.canBypass(player, claim);
    }

    private boolean isChestLocked(Location location) {
        return chestLockModule != null && chestLockModule.getEffectiveLockLocation(location).isPresent();
    }

    private boolean isSharedStorageContainer(Location location) {
        return sharedStorageModule != null && sharedStorageModule.isSharedStorageContainer(location);
    }

    private boolean removeProfile(String golemId) {
        if (golemId == null || golemId.isBlank()) {
            return false;
        }
        GolemProfile removed = profiles.remove(golemId);
        if (removed == null) {
            return false;
        }
        clearTransientTracking(removed.entityUuid());
        lastKnownLocationByGolemId.remove(golemId);
        missingSinceMillisByGolemId.remove(golemId);
        lastRespawnAttemptMillisByGolemId.remove(golemId);
        pendingSelections.entrySet().removeIf(entry -> Objects.equals(entry.getValue().golemId(), golemId));
        combatEquipmentMenuEditors.entrySet().removeIf(entry -> Objects.equals(entry.getValue(), golemId));
        return true;
    }

    private void clearTransientTracking(UUID entityUuid) {
        if (entityUuid == null) {
            return;
        }
        lastCombatActivityTickByGolem.remove(entityUuid);
        combatWorker.clearTracking(entityUuid);
        cropHarvestWorker.clearTracking(entityUuid);
    }

    private boolean isTargetContainerMaterial(Material material) {
        return material == Material.CHEST || material == Material.BARREL;
    }

    private boolean removeRegisteredTargetsByLocations(List<Location> locations) {
        boolean changed = false;
        for (Location location : locations) {
            if (!isTargetContainerMaterial(location.getBlock().getType()) && !hasAnyTargetAtLocation(location)) {
                continue;
            }
            changed |= removeRegisteredTargetsByLocation(location);
        }
        return changed;
    }

    private boolean removeRegisteredTargetsByLocation(Location brokenLocation) {
        boolean changed = false;
        for (GolemProfile profile : profiles.values()) {
            changed |= removeTargetByLocation(profile.targets(), brokenLocation);
            changed |= removeTargetByLocation(profile.boneMealSources(), brokenLocation);
        }
        return changed;
    }

    private boolean hasAnyTargetAtLocation(Location location) {
        for (GolemProfile profile : profiles.values()) {
            if (containsTargetLocation(profile.targets(), location) || containsTargetLocation(profile.boneMealSources(), location)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsTargetLocation(List<ContainerTarget> targets, Location location) {
        for (ContainerTarget target : targets) {
            for (Location footprintLocation : target.footprint()) {
                if (isSameBlock(footprintLocation, location)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean removeTargetByLocation(List<ContainerTarget> targets, Location brokenLocation) {
        return targets.removeIf(target -> {
            for (Location location : target.footprint()) {
                if (isSameBlock(location, brokenLocation)) {
                    return true;
                }
            }
            return false;
        });
    }

    private boolean isSameBlock(Location first, Location second) {
        return Objects.equals(first.getWorld(), second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private Comparator<Location> locationComparator() {
        return Comparator
                .comparing((Location location) -> location.getWorld() == null ? "" : location.getWorld().getName())
                .thenComparingInt(Location::getBlockX)
                .thenComparingInt(Location::getBlockY)
                .thenComparingInt(Location::getBlockZ);
    }

}
