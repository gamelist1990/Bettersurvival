package org.pexserver.koukunn.bettersurvival.Modules.Feature.Pet;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ItemCombineModule;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

/** 友好モブを専用の合成おやつで仲間にし、行動モードを管理する。 */
public final class PetModule implements Listener {
    private enum Mode { FOLLOW, STAY, ROAM }
    private record Recipe(Material first, Material second) {}

    private static final Map<EntityType, Recipe> RECIPES = createRecipes();
    private final Loader plugin;
    private final NamespacedKey treatKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey modeKey;
    private final Set<UUID> petIds = ConcurrentHashMap.newKeySet();
    private final BukkitTask task;

    public PetModule(Loader plugin, ItemCombineModule itemCombineModule) {
        this.plugin = plugin;
        treatKey = new NamespacedKey(plugin, "pet_treat");
        ownerKey = new NamespacedKey(plugin, "pet_owner");
        modeKey = new NamespacedKey(plugin, "pet_mode");
        registerRecipes(itemCombineModule);
        Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(Mob.class).stream()
                .filter(this::isPet).forEach(mob -> petIds.add(mob.getUniqueId())));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public void shutdown() {
        task.cancel();
    }

    public List<String> supportedTypes() {
        return RECIPES.keySet().stream().map(type -> type.key().value()).sorted().toList();
    }

    public String describeRecipe(String typeName) {
        if (typeName == null) return null;
        return RECIPES.entrySet().stream()
                .filter(entry -> entry.getKey().key().value().equalsIgnoreCase(typeName))
                .map(entry -> entry.getValue().first().key().value() + " + " + entry.getValue().second().key().value())
                .findFirst().orElse(null);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Mob mob)) {
            return;
        }
        Player player = event.getPlayer();
        String owner = mob.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (owner == null) {
            EntityType targetType = treatType(player.getInventory().getItemInMainHand());
            if (targetType == null || targetType != mob.getType() || !RECIPES.containsKey(mob.getType())) {
                return;
            }
            event.setCancelled(true);
            consumeOne(player);
            mob.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            petIds.add(mob.getUniqueId());
            setMode(mob, Mode.FOLLOW);
            mob.setPersistent(true);
            mob.setRemoveWhenFarAway(false);
            if (mob instanceof Tameable tameable) {
                tameable.setOwner(player);
            }
            mob.customName(Component.text(player.getName() + "のPet"));
            mob.getWorld().spawnParticle(Particle.HEART, mob.getLocation().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0.05);
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.5F);
            player.sendMessage("§a" + mob.getName() + " が仲間になりました。スニーク右クリックで行動を切り替えられます");
            return;
        }
        if (!owner.equals(player.getUniqueId().toString()) || !player.isSneaking()) {
            return;
        }
        event.setCancelled(true);
        Mode next = switch (getMode(mob)) {
            case FOLLOW -> Mode.STAY;
            case STAY -> Mode.ROAM;
            case ROAM -> Mode.FOLLOW;
        };
        setMode(mob, next);
        player.sendMessage("§6Petモード: §f" + display(next));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onOwnerDamagePet(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(event.getDamager() instanceof Player player)) {
            return;
        }
        String owner = mob.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (owner != null && owner.equals(player.getUniqueId().toString())) {
            event.setCancelled(true);
            player.sendMessage("§c自分のPetにはダメージを与えられません");
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        event.getEntities().stream().filter(Mob.class::isInstance).map(Mob.class::cast)
                .filter(this::isPet).forEach(mob -> petIds.add(mob.getUniqueId()));
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        petIds.remove(event.getEntity().getUniqueId());
    }

    private void tick() {
        for (UUID petId : java.util.List.copyOf(petIds)) {
            if (!(Bukkit.getEntity(petId) instanceof Mob mob) || !mob.isValid()) {
                continue;
            }
                String owner = mob.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                if (owner == null) {
                    petIds.remove(petId);
                    continue;
                }
                Player player;
                try {
                    player = Bukkit.getPlayer(UUID.fromString(owner));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                Mode mode = getMode(mob);
                boolean staying = mode == Mode.STAY;
                mob.setAware(!staying);
                if (mob instanceof Sittable sittable) {
                    sittable.setSitting(staying);
                }
                if (staying || mode == Mode.ROAM || player == null || player.isDead() || mob.isLeashed()) {
                    if (staying) mob.getPathfinder().stopPathfinding();
                    continue;
                }
                if (!mob.getWorld().equals(player.getWorld())) {
                    mob.teleport(player.getLocation());
                } else if (mob.getLocation().distanceSquared(player.getLocation()) > 256) {
                    mob.teleport(player.getLocation());
                } else if (mob.getLocation().distanceSquared(player.getLocation()) > 9) {
                    mob.getPathfinder().moveTo(player, 1.2);
                }
        }
    }

    private void registerRecipes(ItemCombineModule itemCombineModule) {
        RECIPES.forEach((type, recipe) -> {
            String base = "pet_treat_" + type.key().value();
            itemCombineModule.recipe(base).first(stack -> is(stack, recipe.first())).second(stack -> is(stack, recipe.second()))
                    .allowAirCombine(true).then(match -> craft(match, type));
            itemCombineModule.recipe(base + "_rev").first(stack -> is(stack, recipe.second())).second(stack -> is(stack, recipe.first()))
                    .allowAirCombine(true).then(match -> craft(match, type));
        });
    }

    private void craft(ItemCombineModule.CombineMatch match, EntityType type) {
        match.consumeMatchedItems(1, 1);
        ItemStack treat = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = treat.getItemMeta();
        meta.displayName(Component.text(type.key().value() + "用 なかよし素材"));
        meta.lore(java.util.List.of(Component.text("対象のモブを右クリックすると仲間になります")));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(treatKey, PersistentDataType.STRING, type.key().value());
        treat.setItemMeta(meta);
        match.center().getWorld().dropItem(match.center(), treat);
        match.center().getWorld().playSound(match.center(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.3F);
    }

    private EntityType treatType(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String value = stack.getItemMeta().getPersistentDataContainer().get(treatKey, PersistentDataType.STRING);
        if (value == null) return null;
        return RECIPES.keySet().stream().filter(type -> type.key().value().equals(value)).findFirst().orElse(null);
    }

    private void setMode(Mob mob, Mode mode) {
        mob.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode.name());
        mob.setAware(mode != Mode.STAY);
        if (mob instanceof Sittable sittable) sittable.setSitting(mode == Mode.STAY);
    }

    private Mode getMode(Mob mob) {
        String raw = mob.getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        try { return raw == null ? Mode.FOLLOW : Mode.valueOf(raw); }
        catch (IllegalArgumentException ignored) { return Mode.FOLLOW; }
    }

    private String display(Mode mode) {
        return switch (mode) { case FOLLOW -> "一緒に行動"; case STAY -> "待機"; case ROAM -> "放し飼い"; };
    }

    private boolean is(ItemStack stack, Material material) {
        return stack != null && stack.getType() == material;
    }

    private boolean isPet(Mob mob) {
        return mob.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
    }

    private void consumeOne(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        hand.setAmount(hand.getAmount() - 1);
    }

    private static Map<EntityType, Recipe> createRecipes() {
        Map<EntityType, Recipe> recipes = new LinkedHashMap<>();
        recipes.put(EntityType.COW, new Recipe(Material.WHEAT, Material.LEATHER));
        recipes.put(EntityType.SHEEP, new Recipe(Material.WHEAT, Material.WHITE_WOOL));
        recipes.put(EntityType.PIG, new Recipe(Material.CARROT, Material.PORKCHOP));
        recipes.put(EntityType.CHICKEN, new Recipe(Material.WHEAT_SEEDS, Material.FEATHER));
        recipes.put(EntityType.RABBIT, new Recipe(Material.CARROT, Material.RABBIT_HIDE));
        recipes.put(EntityType.GOAT, new Recipe(Material.WHEAT, Material.GOAT_HORN));
        recipes.put(EntityType.FOX, new Recipe(Material.SWEET_BERRIES, Material.RABBIT_FOOT));
        recipes.put(EntityType.PANDA, new Recipe(Material.BAMBOO, Material.SLIME_BALL));
        recipes.put(EntityType.TURTLE, new Recipe(Material.SEAGRASS, Material.TURTLE_SCUTE));
        recipes.put(EntityType.AXOLOTL, new Recipe(Material.TROPICAL_FISH, Material.CLAY_BALL));
        recipes.put(EntityType.DOLPHIN, new Recipe(Material.COD, Material.PRISMARINE_CRYSTALS));
        recipes.put(EntityType.SNIFFER, new Recipe(Material.TORCHFLOWER_SEEDS, Material.MOSS_BLOCK));
        recipes.put(EntityType.CAMEL, new Recipe(Material.CACTUS, Material.SADDLE));
        recipes.put(EntityType.HAPPY_GHAST, new Recipe(Material.SNOWBALL, Material.GHAST_TEAR));
        recipes.put(EntityType.WOLF, new Recipe(Material.BONE, Material.RABBIT_FOOT));
        recipes.put(EntityType.CAT, new Recipe(Material.COD, Material.STRING));
        recipes.put(EntityType.PARROT, new Recipe(Material.WHEAT_SEEDS, Material.COOKIE));
        recipes.put(EntityType.HORSE, new Recipe(Material.APPLE, Material.SADDLE));
        recipes.put(EntityType.DONKEY, new Recipe(Material.CARROT, Material.SADDLE));
        recipes.put(EntityType.MULE, new Recipe(Material.GOLDEN_CARROT, Material.SADDLE));
        recipes.put(EntityType.LLAMA, new Recipe(Material.HAY_BLOCK, Material.LEAD));
        recipes.put(EntityType.ALLAY, new Recipe(Material.AMETHYST_SHARD, Material.COOKIE));
        recipes.put(EntityType.BEE, new Recipe(Material.HONEY_BOTTLE, Material.DANDELION));
        recipes.put(EntityType.FROG, new Recipe(Material.SLIME_BALL, Material.LILY_PAD));
        recipes.put(EntityType.ARMADILLO, new Recipe(Material.SPIDER_EYE, Material.ARMADILLO_SCUTE));
        recipes.put(EntityType.MOOSHROOM, new Recipe(Material.RED_MUSHROOM, Material.WHEAT));
        recipes.put(EntityType.OCELOT, new Recipe(Material.SALMON, Material.FEATHER));
        recipes.put(EntityType.POLAR_BEAR, new Recipe(Material.SALMON, Material.SNOWBALL));
        recipes.put(EntityType.IRON_GOLEM, new Recipe(Material.POPPY, Material.IRON_INGOT));
        recipes.put(EntityType.SNOW_GOLEM, new Recipe(Material.SNOWBALL, Material.CARVED_PUMPKIN));
        recipes.put(EntityType.VILLAGER, new Recipe(Material.EMERALD, Material.BREAD));
        return Map.copyOf(recipes);
    }
}
