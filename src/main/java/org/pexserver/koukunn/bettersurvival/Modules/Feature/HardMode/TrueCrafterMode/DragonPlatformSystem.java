package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** asset:structure/end_platform/1～5.nbtをJava座標列として保持し、40/360tickの足場を再現する。 */
public final class DragonPlatformSystem {
    private static final String[] STRUCTURES = {
            "2,0,2;2,1,2;2,1,3;3,1,2;0,2,2;0,2,3;1,2,2;1,2,3;2,2,1;2,2,2;2,2,3;2,2,4;3,2,2;3,2,3;0,3,1;0,3,2;0,3,3;1,3,0;1,3,1;1,3,2;1,3,3;1,3,4;2,3,0;2,3,1;2,3,2;2,3,3;2,3,4;3,3,1;3,3,2;3,3,3;3,3,4;4,3,2;4,3,3;0,4,0;0,4,1;0,4,2;1,4,0;1,4,1;1,4,2;1,4,3;2,4,0;2,4,1;2,4,2;2,4,3;2,4,4;3,4,0;3,4,1;3,4,2;3,4,3;3,4,4;4,4,1;4,4,2;4,4,3",
            "2,0,2;1,1,2;2,1,1;2,1,2;2,1,3;3,1,2;1,2,1;1,2,2;1,2,3;1,2,4;2,2,1;2,2,2;2,2,3;2,2,4;3,2,2;3,2,3;0,3,0;0,3,1;0,3,2;0,3,3;0,3,4;1,3,0;1,3,1;1,3,2;1,3,3;1,3,4;2,3,0;2,3,1;2,3,2;2,3,3;2,3,4;3,3,1;3,3,2;3,3,3;3,3,4;4,3,2;4,3,3;0,4,2;0,4,3;1,4,3;1,4,4;2,4,3;2,4,4;3,4,2;3,4,3;3,4,4;4,4,2;4,4,3",
            "2,0,2;1,1,2;2,1,1;2,1,2;2,1,3;3,1,2;0,2,1;0,2,2;0,2,3;1,2,1;1,2,2;1,2,3;2,2,0;2,2,1;2,2,2;2,2,3;2,2,4;3,2,0;3,2,1;3,2,2;3,2,3;3,2,4;4,2,2;4,2,3;0,3,2;0,3,3;0,3,4;1,3,0;1,3,1;1,3,2;1,3,3;1,3,4;2,3,0;2,3,1;2,3,2;2,3,3;2,3,4;3,3,0;3,3,1;3,3,2;3,3,3;3,3,4;4,3,2;4,3,3;4,3,4;1,4,3;1,4,4;2,4,1;2,4,2;2,4,3;2,4,4;3,4,1;3,4,2;3,4,3;4,4,2",
            "1,0,3;2,0,2;2,0,3;3,0,2;0,1,1;0,1,2;0,1,3;1,1,0;1,1,1;1,1,2;1,1,3;1,1,4;2,1,0;2,1,1;2,1,2;2,1,3;2,1,4;3,1,0;3,1,1;3,1,2;3,1,3;4,1,1;0,2,4;1,2,2;1,2,3;1,2,4;2,2,0;2,2,1;2,2,2;2,2,3;2,2,4;3,2,0;3,2,1;3,2,2;3,2,3;4,2,1;4,2,2;1,3,4;2,3,2;2,3,3;2,3,4;3,3,1;3,3,2;3,3,3;4,3,1;4,3,2;4,3,3;2,4,3;2,4,4;3,4,2;3,4,3;3,4,4;4,4,1;4,4,2;4,4,3",
            "2,0,2;2,1,2;2,1,3;3,1,2;1,2,2;2,2,1;2,2,2;2,2,3;3,2,1;3,2,2;3,2,3;4,2,3;0,3,1;0,3,2;1,3,0;1,3,1;1,3,2;1,3,3;2,3,0;2,3,1;2,3,2;2,3,3;3,3,1;3,3,2;3,3,3;4,3,1;4,3,2;4,3,3;0,4,1;0,4,2;0,4,3;1,4,0;1,4,1;1,4,2;1,4,3;1,4,4;2,4,0;2,4,1;2,4,2;2,4,3;2,4,4;3,4,0;3,4,1;3,4,2;3,4,3;3,4,4;4,4,1;4,4,2;4,4,3"
    };
    private final List<Entry> entries = new ArrayList<>();
    private final BukkitTask task;

    public DragonPlatformSystem(Loader plugin) {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        task.cancel();
    }

    public void summon(World world, int y, int stage) {
        int selected = Math.max(0, Math.min(stage, STRUCTURES.length - 1));
        int bound = selected == 0 ? 24 : 32;
        entries.add(new Entry(world, ThreadLocalRandom.current().nextInt(-bound, bound + 1), y,
                ThreadLocalRandom.current().nextInt(-bound, bound + 1), selected,
                ThreadLocalRandom.current().nextInt(4)));
    }

    private void tick() {
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            e.age++;
            Location c = new Location(e.world, e.centerX + 0.5, e.y + 0.5, e.centerZ + 0.5);
            if (e.age <= 40) {
                e.world.spawnParticle(Particle.DUST, c, 2, 1, 1, 1, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(204, 51, 255), 1), true);
                for (Player p : e.world.getPlayers())
                    if (p.getLocation().distanceSquared(c) <= 16384.0D)
                        p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20, 0, false, false));
            }
            if (e.age == 40)
                place(e);
            if (e.age >= 300) {
                e.world.spawnParticle(Particle.DUST, c, 2, 2, 2, 2, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(204, 51, 255), 1), true);
                e.world.spawnParticle(Particle.BLOCK, c, 5, 1.5, 1.5, 1.5, 0, Material.END_STONE.createBlockData(),
                        true);
            }
            if (e.age >= 360) {
                e.world.spawnParticle(Particle.DUST, c, 25, 1.5, 1.5, 1.5, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(204, 51, 255), 2), true);
                for (Location l : e.blocks)
                    if (l.getBlock().getType() == Material.END_STONE)
                        l.getBlock().setType(Material.AIR, true);
                it.remove();
            }
        }
    }

    private void place(Entry e) {
        for (String s : STRUCTURES[e.type].split(";")) {
            String[] v = s.split(",");
            int x = Integer.parseInt(v[0]) - 2, z = Integer.parseInt(v[2]) - 2;
            int rx = e.rotation == 0 ? x : e.rotation == 1 ? -z : e.rotation == 2 ? z : -x;
            int rz = e.rotation == 0 ? z : e.rotation == 1 ? x : e.rotation == 2 ? -x : -z;
            Location l = new Location(e.world, e.centerX + rx, e.y + Integer.parseInt(v[1]) - 2, e.centerZ + rz);
            l.getBlock().setType(Material.END_STONE, true);
            e.blocks.add(l);
        }
        e.world.playSound(new Location(e.world, e.centerX, e.y, e.centerZ), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1, 2);
    }

    private static final class Entry {
        final World world;
        final int centerX, y, centerZ, type, rotation;
        final List<Location> blocks = new ArrayList<>();
        int age;

        Entry(World w, int x, int y, int z, int t, int r) {
            world = w;
            centerX = x;
            this.y = y;
            centerZ = z;
            type = t;
            rotation = r;
        }
    }
}
