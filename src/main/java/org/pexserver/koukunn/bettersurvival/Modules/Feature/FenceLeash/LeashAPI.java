package org.pexserver.koukunn.bettersurvival.Modules.Feature.FenceLeash;

import org.bukkit.Location;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.util.Vector;

import java.util.*;

public class LeashAPI {

    public static final String HITCH_TAG = "bettersurvival:fence_leash_hitch";
    public static final String CONNECTION_TAG = "bettersurvival:fence_leash_connection";
    public static final String TETHER_TAG = "bettersurvival:fence_leash_tether";
    public static final String COUNTED_TAG = "bettersurvival:fence_leash_counted";

    // state owned by the API
    private final Map<UUID, Location> pendingFirst = new HashMap<>();
    private final Map<UUID, Rabbit> tetherRabbits = new HashMap<>();
    private final Map<Location, Integer> hitchLeadCounts = new HashMap<>();

    public boolean hasPending(java.util.UUID id) { return pendingFirst.containsKey(id); }
    public Location getPending(java.util.UUID id) { return pendingFirst.get(id); }

    public void cancelPending(java.util.UUID id) {
        Location pos = pendingFirst.remove(id);
        if (pos != null) {
            Location blockKey = pos.getBlock().getLocation();
            if (hitchLeadCounts.containsKey(blockKey)) {
                hitchLeadCounts.put(blockKey, Math.max(0, hitchLeadCounts.get(blockKey) - 1));
                if (hitchLeadCounts.get(blockKey) <= 0) {
                    LeashHitch h = findHitchAtBlock(blockKey);
                    if (h != null) h.remove();
                    hitchLeadCounts.remove(blockKey);
                }
            }
        }

        Rabbit r = tetherRabbits.remove(id);
        if (r != null) r.remove();
    }

    public boolean createPos1(Player player, Location fenceCenter, Location blockLocation) {
        UUID pid = player.getUniqueId();

        // cleanup any previous tether for this player
        Rabbit old = tetherRabbits.remove(pid);
        if (old != null && !old.isDead()) old.remove();

        // find existing hitch at block; if none create and count
        Location blockKey = blockLocation.getBlock().getLocation();
        LeashHitch hitch = findHitchAtBlock(blockKey);
        if (hitch == null) {
            hitch = fenceCenter.getWorld().spawn(fenceCenter, LeashHitch.class);
            try { hitch.setPersistent(true); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
            hitch.addScoreboardTag(HITCH_TAG);
            hitchLeadCounts.put(blockKey, hitchLeadCounts.getOrDefault(blockKey, 0) + 1);
        } else {
            hitchLeadCounts.put(blockKey, hitchLeadCounts.getOrDefault(blockKey, 0) + 1);
        }

        // spawn tether rabbit and attach to player for visual tether
        Rabbit tether = fenceCenter.getWorld().spawn(fenceCenter, Rabbit.class);
        tether.setInvisible(false);
        tether.setInvulnerable(true);
        tether.setSilent(true);
        tether.addScoreboardTag(TETHER_TAG);
        try { tether.setAI(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        try { tether.setGravity(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        try { tether.setCollidable(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        tether.setVelocity(new Vector(0, 0, 0));
        try { tether.setLeashHolder(player); } catch (NoSuchMethodError | AbstractMethodError ignored) {}

        tetherRabbits.put(pid, tether);
        pendingFirst.put(pid, fenceCenter);

        return true;
    }

    public boolean connectPendingTo(Player player, Location pos2Center, Location pos2BlockLocation, boolean consumeLead) {
        UUID pid = player.getUniqueId();
        Location firstPos = pendingFirst.get(pid);
        if (firstPos == null) return false;

        // cancel if user clicked the same block
        if (firstPos.getBlock().equals(pos2BlockLocation.getBlock())) {
            cancelPending(pid);
            return false;
        }

        // remove visual tether
        Rabbit oldTether = tetherRabbits.remove(pid);
        if (oldTether != null) oldTether.remove();

        // ensure Pos1 hitch exists
        Location firstBlockLoc = firstPos.getBlock().getLocation();
        LeashHitch hitch1 = findHitchAtBlock(firstBlockLoc);
        if (hitch1 == null) {
            hitch1 = firstPos.getWorld().spawn(firstPos, LeashHitch.class);
            try { hitch1.setPersistent(true); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
            hitch1.addScoreboardTag(HITCH_TAG);
            if (consumeLead) hitchLeadCounts.put(firstBlockLoc, hitchLeadCounts.getOrDefault(firstBlockLoc, 0) + 1);
        } else {
            if (consumeLead) hitchLeadCounts.put(firstBlockLoc, hitchLeadCounts.getOrDefault(firstBlockLoc, 0) + 1);
        }

        // ensure there is a hitch at Pos2 (so the fence shows the knot as well)
        Location secondBlockLoc = pos2BlockLocation.getBlock().getLocation();
        LeashHitch hitch2 = findHitchAtBlock(secondBlockLoc);
        if (hitch2 == null) {
            hitch2 = pos2Center.getWorld().spawn(pos2Center, LeashHitch.class);
            try { hitch2.setPersistent(true); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
            hitch2.addScoreboardTag(HITCH_TAG);
        }

        // spawn connector at Pos2 and attach to hitch1
        Rabbit connector = pos2Center.getWorld().spawn(pos2Center, Rabbit.class);
        connector.setInvisible(false);
        connector.setInvulnerable(true);
        connector.setSilent(true);
        connector.addScoreboardTag(CONNECTION_TAG);
        if (consumeLead) connector.addScoreboardTag(COUNTED_TAG);
        try { connector.setAI(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        try { connector.setGravity(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        try { connector.setCollidable(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        connector.setVelocity(new Vector(0, 0, 0));
        try { connector.setLeashHolder(hitch1); } catch (NoSuchMethodError | AbstractMethodError ignored) {}

        pendingFirst.remove(pid);
        return true;
    }

    public void onEntityDeath(EntityDeathEvent e) {
        // custom rabbit tags are non-dropping
        if (e.getEntity() instanceof Rabbit && (e.getEntity().getScoreboardTags().contains(CONNECTION_TAG) ||
                e.getEntity().getScoreboardTags().contains(TETHER_TAG))) {

            if (e.getEntity().getScoreboardTags().contains(CONNECTION_TAG) && e.getEntity().getScoreboardTags().contains(COUNTED_TAG)) {
                try {
                    org.bukkit.entity.Entity holder = ((Rabbit) e.getEntity()).getLeashHolder();
                    if (holder instanceof LeashHitch) {
                        Location b = holder.getLocation().getBlock().getLocation();
                        if (hitchLeadCounts.containsKey(b)) {
                            hitchLeadCounts.put(b, Math.max(0, hitchLeadCounts.get(b) - 1));
                            if (hitchLeadCounts.get(b) <= 0) {
                                LeashHitch h = findHitchAtBlock(b);
                                if (h != null) h.remove();
                                hitchLeadCounts.remove(b);
                            }
                        }
                    }
                } catch (NoSuchMethodError | AbstractMethodError ignored) {}
            }

            // remove tether mapping if any
                if (e.getEntity().getScoreboardTags().contains(TETHER_TAG)) {
                UUID id = e.getEntity().getUniqueId();
                List<UUID> toRemove = new ArrayList<>();
                for (Map.Entry<UUID, Rabbit> entry : tetherRabbits.entrySet()) {
                    Rabbit r = entry.getValue();
                    if (r != null && r.getUniqueId().equals(id)) toRemove.add(entry.getKey());
                }
                for (UUID k : toRemove) tetherRabbits.remove(k);
            }

            e.getDrops().clear();
        }

        // Hitch cleanup
        if (e.getEntity() instanceof LeashHitch && e.getEntity().getScoreboardTags().contains(HITCH_TAG)) {
            Location blockLoc = e.getEntity().getLocation().getBlock().getLocation();
            hitchLeadCounts.remove(blockLoc);

            for (org.bukkit.entity.Entity ent : e.getEntity().getWorld().getEntitiesByClass(org.bukkit.entity.Entity.class)) {
                if (ent instanceof Rabbit && ent.getScoreboardTags().contains(CONNECTION_TAG)) {
                    try {
                        org.bukkit.entity.Entity holder = ((Rabbit) ent).getLeashHolder();
                        if (holder != null && holder.equals(e.getEntity())) {
                            ent.remove();
                        }
                    } catch (NoSuchMethodError | AbstractMethodError ignored) {}
                }
            }
        }
    }

    public void onEntityUnleash(EntityUnleashEvent e) {
        if (e.getEntity() instanceof Rabbit && (e.getEntity().getScoreboardTags().contains(CONNECTION_TAG) ||
                e.getEntity().getScoreboardTags().contains(TETHER_TAG))) {
            UUID id = e.getEntity().getUniqueId();

            if (e.getEntity().getScoreboardTags().contains(CONNECTION_TAG) && e.getEntity().getScoreboardTags().contains(COUNTED_TAG)) {
                try {
                    org.bukkit.entity.Entity holder = ((Rabbit) e.getEntity()).getLeashHolder();
                    if (holder instanceof LeashHitch) {
                        Location b = holder.getLocation().getBlock().getLocation();
                        if (hitchLeadCounts.containsKey(b)) {
                            hitchLeadCounts.put(b, Math.max(0, hitchLeadCounts.get(b) - 1));
                            if (hitchLeadCounts.get(b) <= 0) {
                                LeashHitch h = findHitchAtBlock(b);
                                if (h != null) h.remove();
                                hitchLeadCounts.remove(b);
                            }
                        }
                    }
                } catch (NoSuchMethodError | AbstractMethodError ignored) {}
            }

            // clear player's tether if it's our visual tether
            List<UUID> toRemove = new ArrayList<>();
            for (Map.Entry<UUID, Rabbit> entry : tetherRabbits.entrySet()) {
                Rabbit ent = entry.getValue();
                if (ent != null && ent.getUniqueId().equals(id)) toRemove.add(entry.getKey());
            }
            for (UUID key : toRemove) {
                Rabbit removed = tetherRabbits.remove(key);
                if (removed != null) removed.remove();
            }

            e.getEntity().remove();
        }
    }

    // helper utilities used by the API
    public LeashHitch findHitchAtBlock(Location blockLoc) {
        for (LeashHitch h : blockLoc.getWorld().getEntitiesByClass(LeashHitch.class)) {
            if (h.getLocation().getBlock().equals(blockLoc.getBlock()) && h.getScoreboardTags().contains(HITCH_TAG)) {
                return h;
            }
        }
        return null;
    }

    public java.util.List<Rabbit> findConnectorsAttachedTo(LeashHitch hitch) {
        java.util.List<Rabbit> out = new java.util.ArrayList<>();
        if (hitch == null) return out;
        for (Rabbit r : hitch.getWorld().getEntitiesByClass(Rabbit.class)) {
            if (!r.getScoreboardTags().contains(CONNECTION_TAG)) continue;
            try {
                org.bukkit.entity.Entity holder = r.getLeashHolder();
                if (holder != null && holder.equals(hitch)) out.add(r);
            } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        }
        return out;
    }

    public java.util.List<Rabbit> findConnectorsAtBlock(Location blockLoc) {
        java.util.List<Rabbit> out = new java.util.ArrayList<>();
        for (Rabbit r : blockLoc.getWorld().getEntitiesByClass(Rabbit.class)) {
            if (!r.getScoreboardTags().contains(CONNECTION_TAG)) continue;
            try {
                if (r.getLocation().getBlock().equals(blockLoc.getBlock())) out.add(r);
            } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        }
        return out;
    }

    /**
     * Player picks up an existing connector at the clicked block (pos2).
     * This will remove the connector at pos2 and create a visual tether at pos1
     * attached to the player so the rope appears to come from pos1 -> player.
     */
    public boolean pickupConnectorAt(Player player, Location blockLoc) {
        java.util.List<Rabbit> connectors = findConnectorsAtBlock(blockLoc);
        if (connectors.isEmpty()) return false;

        Rabbit chosen = chooseNearest(connectors, player.getLocation());
        if (chosen == null) return false;

        // Get original hitch (pos1) this connector was attached to
        try {
            org.bukkit.entity.Entity holder = chosen.getLeashHolder();
            if (!(holder instanceof LeashHitch)) {
                // not a plugin hitch we know how to handle
                return false;
            }

            Location pos1Block = holder.getLocation().getBlock().getLocation();
            Location pos1Center = pos1Block.clone().add(0.5, 0.5, 0.5);

            // If this connector counted as consumed lead, decrement pos1 counts
            if (chosen.getScoreboardTags().contains(COUNTED_TAG)) {
                if (hitchLeadCounts.containsKey(pos1Block)) {
                    hitchLeadCounts.put(pos1Block, Math.max(0, hitchLeadCounts.get(pos1Block) - 1));
                    if (hitchLeadCounts.get(pos1Block) <= 0) {
                        LeashHitch h = findHitchAtBlock(pos1Block);
                        if (h != null) h.remove();
                        hitchLeadCounts.remove(pos1Block);
                    }
                }
            }

            // remove connector entity at pos2
            chosen.remove();

            // if no more connectors remain at pos2, remove the pos2 hitch we created earlier
            if (findConnectorsAtBlock(blockLoc).isEmpty()) {
                LeashHitch hitchAt2 = findHitchAtBlock(blockLoc);
                if (hitchAt2 != null) {
                    hitchAt2.remove();
                    hitchLeadCounts.remove(blockLoc);
                }
            }

            // spawn visual tether at pos1 attached to player
            Rabbit tether = pos1Center.getWorld().spawn(pos1Center, Rabbit.class);
            tether.setInvisible(false);
            tether.setInvulnerable(true);
            tether.setSilent(true);
            tether.addScoreboardTag(TETHER_TAG);
            try { tether.setAI(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
            try { tether.setGravity(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
            try { tether.setCollidable(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
            tether.setVelocity(new Vector(0, 0, 0));
            try { tether.setLeashHolder(player); } catch (NoSuchMethodError | AbstractMethodError ignored) {}

            // set pending state for this player to the pos1 center
            UUID pid = player.getUniqueId();
            // cleanup any previous tether for this player
            Rabbit old = tetherRabbits.remove(pid);
            if (old != null && !old.isDead()) old.remove();

            tetherRabbits.put(pid, tether);
            pendingFirst.put(pid, pos1Center);

            return true;
        } catch (NoSuchMethodError | AbstractMethodError ignored) {
            return false;
        }
    }

    public Rabbit chooseNearest(java.util.List<Rabbit> list, Location loc) {
        if (list == null || list.isEmpty()) return null;
        Rabbit best = list.get(0);
        double bestd = best.getLocation().distanceSquared(loc);
        for (Rabbit r : list) {
            double d = r.getLocation().distanceSquared(loc);
            if (d < bestd) { bestd = d; best = r; }
        }
        return best;
    }

}
