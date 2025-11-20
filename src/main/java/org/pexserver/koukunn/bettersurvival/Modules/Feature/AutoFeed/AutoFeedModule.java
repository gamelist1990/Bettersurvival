package org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoFeed;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.*;

/**
 * AutoFeed: 半自動餌やり
 * プレイヤーが動物に餌をあげたときに、同種の周辺の動物にも持っている餌を消費して自動で繁殖入力（餌）を行う。
 */
public class AutoFeedModule implements Listener {

    private final ToggleModule toggle;
    private final int radius = 6; // 探索範囲

    public AutoFeedModule(ToggleModule toggle) {
        this.toggle = toggle;
    }

    private static final Map<EntityType, List<Material>> acceptedFoods = new HashMap<>();

    static {
        acceptedFoods.put(EntityType.COW, Collections.singletonList(Material.WHEAT));
        acceptedFoods.put(EntityType.SHEEP, Collections.singletonList(Material.WHEAT));
        acceptedFoods.put(EntityType.MOOSHROOM, Collections.singletonList(Material.WHEAT));
        acceptedFoods.put(EntityType.PIG, Arrays.asList(Material.CARROT, Material.POTATO, Material.BEETROOT));
        acceptedFoods.put(EntityType.CHICKEN, Arrays.asList(Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS));
    }

    @EventHandler
    public void onPlayerFeed(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Animals))
            return;
        Player player = e.getPlayer();

        // Toggle are respected
        if (!toggle.getGlobal("autofeed"))
            return;
        if (!toggle.isEnabledFor(player.getUniqueId().toString(), "autofeed"))
            return;

        Entity target = e.getRightClicked();
        EntityType type = target.getType();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null)
            return;

        // まず Animals#isBreedItem を使って判定
        boolean isBreedItem = false;
        try {
            isBreedItem = target instanceof org.bukkit.entity.Animals
                    && ((org.bukkit.entity.Animals) target).isBreedItem(hand);
        } catch (Throwable ignored) {
        }

        // API が使えない場合は acceptedFoods の fallback を利用
        if (!isBreedItem) {
            List<Material> foods = acceptedFoods.get(type);
            if (foods == null || foods.isEmpty())
                return;
            Material mat = hand.getType();
            if (!foods.contains(mat))
                return;
        }

        // delay one tick to allow vanilla feed action first
        Material mat = hand.getType();
        Bukkit.getScheduler().runTaskLater(org.pexserver.koukunn.bettersurvival.Loader
                .getPlugin(org.pexserver.koukunn.bettersurvival.Loader.class), () -> {
                    ItemStack current = player.getInventory().getItemInMainHand();
                    if (current == null || current.getType() != mat)
                        return; // no more food
                    // Search nearby animals, filter candidates then feed sequentially
                    List<Entity> nearby = target.getNearbyEntities(radius, radius, radius);
                    List<Animals> candidates = new ArrayList<>();
                    for (Entity ent : nearby) {
                        if (!(ent instanceof Animals))
                            continue;
                        Animals animal = (Animals) ent;
                        // Don't feed the same as target
                        if (ent.equals(target))
                            continue;
                        // Ageable -> must be adult
                        if (animal instanceof Ageable) {
                            Ageable age = (Ageable) animal;
                            if (!age.isAdult())
                                continue;
                        }
                        // Check if animal already in love mode
                        // 正式APIが使えるなら isLoveMode を利用
                        boolean canBreed = false;
                        try {
                            canBreed = animal.canBreed();
                        } catch (Throwable ignored) {
                            canBreed = false;
                        }
                        // 既にlove状態ならスキップ
                        try {
                            if (animal.getLoveModeTicks() > 0) continue;
                        } catch (Throwable ignored) {}
                        if (!canBreed)
                            continue;

                        // Ensure this animal accepts same food (prefer API isBreedItem)
                        boolean accepts = false;
                        try {
                            accepts = animal.isBreedItem(player.getInventory().getItemInMainHand());
                        } catch (Throwable ignored) {}
                        if (!accepts) {
                            List<Material> destFoods = acceptedFoods.get(ent.getType());
                            if (destFoods == null || !destFoods.contains(mat)) continue;
                        }

                        candidates.add(animal);
                    }

                    // Feed candidates one by one until items run out
                    for (Animals animal : candidates) {
                        if (consumeOne(player)) {
                            try {
                                // 6000 ticks = 5 minutes (20 ticks = 1 second)
                                animal.setLoveModeTicks(6000);
                            } catch (Throwable t) {
                            }
                        } else break;
                    }
                }, 1L);
    }

    private boolean consumeOne(Player player) {
        ItemStack it = player.getInventory().getItemInMainHand();
        if (it == null)
            return false;
        int amount = it.getAmount();
        if (amount <= 0)
            return false;
        it.setAmount(amount - 1);
        if (it.getAmount() <= 0)
            player.getInventory().setItemInMainHand(null);
        else
            player.getInventory().setItemInMainHand(it);
        return true;
    }
}
