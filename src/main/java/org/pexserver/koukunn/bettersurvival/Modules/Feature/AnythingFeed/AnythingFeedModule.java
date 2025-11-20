package org.pexserver.koukunn.bettersurvival.Modules.Feature.AnythingFeed;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Animals;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

/**
 * AnythingFeed: 非繁殖動物や繁殖対象外のアイテムを、食べ物判定で受け付ける機能
 */
public class AnythingFeedModule implements Listener {

    private final ToggleModule toggle;
    private final int loveTicks = 6000;

    public AnythingFeedModule(ToggleModule toggle) {
        this.toggle = toggle;
    }

    @EventHandler
    public void onPlayerFeed(PlayerInteractEntityEvent e) {
        Player player = e.getPlayer();

        // Toggle are respected
        if (!toggle.getGlobal("anythingfeed")) return;
        if (!toggle.isEnabledFor(player.getUniqueId().toString(), "anythingfeed")) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null) return;

        Material mat = hand.getType();
        // Only allow edible items
        boolean edible = false;
        try {
            edible = mat.isEdible();
        } catch (Throwable ignored) {}
        if (!edible) return;

        // If target is Animals and not accepting breed items, try to set love mode
        if (e.getRightClicked() instanceof Animals) {
            Animals animal = (Animals) e.getRightClicked();

            boolean acceptsAsBreed = false;
            try {
                acceptsAsBreed = animal.isBreedItem(hand);
            } catch (Throwable ignored) {}

            // If item is a breed item for this animal, skip (AutoFeed should handle)
            if (acceptsAsBreed) return;

            boolean canBreed = false;
            try {
                canBreed = animal.canBreed();
            } catch (Throwable ignored) {}

            if (!canBreed) return;

            try {
                animal.setLoveModeTicks(loveTicks);
            } catch (Throwable ignored) {}

            player.getWorld().spawnParticle(Particle.HEART, animal.getLocation().add(0, 1, 0), 5);
            player.playSound(animal.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
            consumeOne(player);
            return;
        }

        // For non-Animals living entities, just show hearts and consume one item
        if (e.getRightClicked() instanceof LivingEntity) {
            LivingEntity ent = (LivingEntity) e.getRightClicked();
            // Do not allow feeding hostile mobs (like Zombies) or villagers
            if (ent instanceof Monster) return;
            if (ent instanceof Villager) return;
            player.getWorld().spawnParticle(Particle.HEART, ent.getLocation().add(0, 1, 0), 3);
            player.playSound(ent.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
            consumeOne(player);
        }
    }

    private boolean consumeOne(Player player) {
        ItemStack it = player.getInventory().getItemInMainHand();
        if (it == null) return false;
        int amount = it.getAmount();
        if (amount <= 0) return false;
        it.setAmount(amount - 1);
        if (it.getAmount() <= 0) player.getInventory().setItemInMainHand(null);
        else player.getInventory().setItemInMainHand(it);
        return true;
    }
}
