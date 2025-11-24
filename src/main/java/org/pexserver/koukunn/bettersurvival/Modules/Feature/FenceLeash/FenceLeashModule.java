package org.pexserver.koukunn.bettersurvival.Modules.Feature.FenceLeash;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

public class FenceLeashModule implements Listener {

    private final ToggleModule toggle;
    private static final String KEY = "fenceleash";

    // プレイヤーごとの Pos1 フェンス座標のみ保持
    private final Map<UUID, Location> pendingFirst = new HashMap<>();
    // Pos1 選択中の視覚テザー Rabbit（プレイヤー毎）
    private final Map<UUID, Rabbit> tetherRabbits = new HashMap<>();

    // 各ヒッチ（ブロック）に対して消費したリード数を簡易追跡（回収時に返却するため）
    private final Map<Location, Integer> hitchLeadCounts = new HashMap<>();

    public FenceLeashModule(ToggleModule toggle) {
        this.toggle = toggle;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;

        Material btype = e.getClickedBlock().getType();
        if (!btype.name().contains("_FENCE")) return;

        Player player = e.getPlayer();
        Material main = player.getInventory().getItemInMainHand().getType();
        Material off = player.getInventory().getItemInOffHand().getType();
        boolean hasLead = (main == Material.LEAD) || (off == Material.LEAD);

        if (!toggle.getGlobal(KEY)) return;
        if (!toggle.isEnabledFor(player.getUniqueId().toString(), KEY)) return;

        // ブロック位置と中心位置を分けて扱う
        Location blockLoc = e.getClickedBlock().getLocation();
        Location fencePos = blockLoc.clone().add(0.5, 0.5, 0.5);
        UUID pid = player.getUniqueId();

        // まず: もしこのフェンスに当プラグインのヒッチが既に存在するなら「回収」または「移動」を試みる
        LeashHitch existingHitch = findHitchAtBlock(blockLoc);
        if (existingHitch != null) {


            // 回収／移動処理: まずこのヒッチにつながっているコネクタ Rabbit を探す
            java.util.List<Rabbit> connectors = findConnectorsAttachedTo(existingHitch);

            // コネクタがあればプレイヤーに繋ぎ直して移動（Pos1 -> 持ち歩き）
            if (!connectors.isEmpty()) {
                Rabbit chosen = chooseNearest(connectors, player.getLocation());
                try {
                    chosen.addScoreboardTag("bettersurvival:fence_leash_tether");
                    chosen.removeScoreboardTag("bettersurvival:fence_leash_connection");
                    chosen.setLeashHolder(player);
                } catch (NoSuchMethodError | AbstractMethodError ignored) {}

                // もしヒッチに残るコネクタがもうなければヒッチ自体を削除
                if (findConnectorsAttachedTo(existingHitch).isEmpty()) {
                    existingHitch.remove();
                    hitchLeadCounts.remove(blockLoc);
                }

                // 各プレイヤーの一時テザーがあれば削除
                java.util.List<UUID> toClear = new java.util.ArrayList<>();
                for (Map.Entry<UUID, Location> entry : pendingFirst.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().getBlock().equals(blockLoc.getBlock())) toClear.add(entry.getKey());
                }
                for (UUID id : toClear) {
                    pendingFirst.remove(id);
                    Rabbit tr = tetherRabbits.remove(id);
                    if (tr != null) tr.remove();
                }

                player.sendMessage("§a▸ Pos1 の接続先を持ち帰りました。Pos3 を選んでください。");
                e.setCancelled(true);
                return;
            }

            // コネクタが見つからない場合は従来どおりヒッチ削除 + リード返却
            int refund = hitchLeadCounts.getOrDefault(blockLoc, 1);

            // pendingFirst にあればキャンセルしてテザーを削除
            java.util.List<UUID> pendingToRemove = new java.util.ArrayList<>();
            for (Map.Entry<UUID, Location> entry : pendingFirst.entrySet()) {
                if (entry.getValue() != null && entry.getValue().getBlock().equals(blockLoc.getBlock())) {
                    pendingToRemove.add(entry.getKey());
                }
            }
            for (UUID rem : pendingToRemove) {
                pendingFirst.remove(rem);
                Rabbit t = tetherRabbits.remove(rem);
                if (t != null) t.remove();
            }

            // このヒッチに繋がっているコネクター Rabbit を全削除
            for (Rabbit r : findConnectorsAttachedTo(existingHitch)) r.remove();

            // ヒッチ本体を削除
            existingHitch.remove();
            hitchLeadCounts.remove(blockLoc);

            // リードを返却（クリエイティブ除外）
            if (!player.getGameMode().name().equals("CREATIVE") && refund > 0) {
                java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(Material.LEAD, refund));
                if (!leftovers.isEmpty()) {
                    // インベントリが満杯なら地面にドロップ
                    for (ItemStack s : leftovers.values()) {
                        existingHitch.getWorld().dropItemNaturally(player.getLocation(), s);
                    }
                }
            }

            player.sendMessage("§a▸ Pos1 のリードを回収しました。");
            e.setCancelled(true);
            return;
        }

        // ここからはリードを持っていることが前提
        if (!hasLead) return;

        // Pos1 が既に選択済みの場合: Pos2 として接続を作成
        if (pendingFirst.containsKey(pid)) {
            Location firstPos = pendingFirst.get(pid); // defer removal until connection succeeds

            // 古い視覚的テザーを削除（複数エンティティ対応）
            Rabbit oldTether = tetherRabbits.remove(pid);
            if (oldTether != null) oldTether.remove();

            // 同じフェンスへの接続は禁止（クリックでキャンセルとして扱う）
            if (firstPos.getBlock().equals(e.getClickedBlock())) {
                pendingFirst.remove(pid);
                // nothing else to clean up
                player.sendMessage("§c▸ 同じフェンスには接続できません。Pos1 をキャンセルしました。");
                e.setCancelled(true);
                return;
            }

            // Pos1 のヒッチを探す／作成
            Location firstBlockLoc = firstPos.getBlock().getLocation();
            LeashHitch hitch1 = findHitchAtBlock(firstBlockLoc);
            // pos2 のブロック位置
            // (not used; connector will spawn at fencePos)

            // Pos2 に対するコネクタ作成（シンプルにプラグインで生成）
            if (hitch1 == null) {
                hitch1 = firstPos.getWorld().spawn(firstPos, LeashHitch.class);
                try { hitch1.setPersistent(true); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
                hitch1.addScoreboardTag("bettersurvival:fence_leash_hitch");
                // 所有者とリード消費カウントを初期化
                hitchLeadCounts.put(firstBlockLoc, hitchLeadCounts.getOrDefault(firstBlockLoc, 0) + 1);
            } else {
                // 既にある場合はリード消費カウントを増やす
                hitchLeadCounts.put(firstBlockLoc, hitchLeadCounts.getOrDefault(firstBlockLoc, 0) + 1);
            }
            // pending を取り除き、以降は接続処理を仕上げる
            pendingFirst.remove(pid);

            // ここからは Pos2 に対するコネクタ作成処理
            // Pos1 ヒッチと Pos2 ヒッチの間に Rabbit を使ってリード接続
            // Rabbit を Pos2 に生成し、Pos1 のヒッチに繋ぐ
            Rabbit connector = fencePos.getWorld().spawn(fencePos, Rabbit.class);
            connector.setInvisible(true);
            connector.setInvulnerable(true);
            connector.setSilent(true);
            connector.addScoreboardTag("bettersurvival:fence_leash_connection");
            try { connector.setAI(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
            try { connector.setGravity(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
            try { connector.setCollidable(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
            connector.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

            // Pos1 ヒッチに繋ぐ
            connector.setLeashHolder(hitch1);

            // リード消費（Pos2側）
            consumeLead(player, main, off);

            player.sendMessage("§a▸ フェンスを接続しました。");
            e.setCancelled(true);
            return;
        }

        // Pos1 を新たに選択
        // 既存の視覚的テザーがあれば削除（複数エンティティ対応）
        Rabbit oldTethers2 = tetherRabbits.remove(pid);
        if (oldTethers2 != null) oldTethers2.remove();

        pendingFirst.put(pid, fencePos);

        // Pos1 側に LeashHitch を作成
        LeashHitch hitch = fencePos.getWorld().spawn(fencePos, LeashHitch.class);
        try { hitch.setPersistent(true); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        hitch.addScoreboardTag("bettersurvival:fence_leash_hitch");

        // ブロックキーごとに消費リード数を記録
        Location blockKey = e.getClickedBlock().getLocation();
        hitchLeadCounts.put(blockKey, hitchLeadCounts.getOrDefault(blockKey, 0) + 1);



        // プレイヤーへの視覚的なテザー Rabbit を生成
        Rabbit tether = fencePos.getWorld().spawn(fencePos, Rabbit.class);
        tether.setInvisible(false); //実験の為可視化しています
        tether.setInvulnerable(true);
        tether.setSilent(true);
        tether.addScoreboardTag("bettersurvival:fence_leash_tether");
        try { tether.setAI(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        try { tether.setGravity(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        try { tether.setCollidable(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        tether.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

        // LeashHitch に繋ぐ

        // tether はプレイヤーに繋いでリード表示を作る（anchor がヒッチを維持）
        tether.setLeashHolder(player);

        // テザー Rabbit を記録 (プレイヤーが Pos2 を選択するまでビジュアル目的で保持)
        tetherRabbits.put(pid, tether);

        // リード消費
        consumeLead(player, main, off);

        player.sendMessage("§e▸ Pos1 を選択しました。別のフェンスを右クリックしてください。");
        e.setCancelled(true);
    }

    private void consumeLead(Player player, Material main, Material off) {
        if (!player.getGameMode().name().equals("CREATIVE")) {
            if (main == Material.LEAD) {
                int amt = player.getInventory().getItemInMainHand().getAmount();
                if (amt <= 1) player.getInventory().setItemInMainHand(null);
                else player.getInventory().getItemInMainHand().setAmount(amt - 1);
            } else if (off == Material.LEAD) {
                int amt = player.getInventory().getItemInOffHand().getAmount();
                if (amt <= 1) player.getInventory().setItemInOffHand(null);
                else player.getInventory().getItemInOffHand().setAmount(amt - 1);
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        // Rabbit ドロップを禁止: 自作のタグを持つ Rabbit はドロップなし
            if (e.getEntity() instanceof Rabbit && (e.getEntity().getScoreboardTags().contains("bettersurvival:fence_leash_connection") ||
            e.getEntity().getScoreboardTags().contains("bettersurvival:fence_leash_tether"))) {

            // Pos2 側のコネクター Rabbit が死んだ場合は、関連する Pos1 ヒッチのリード消費カウントを減らす
            if (e.getEntity().getScoreboardTags().contains("bettersurvival:fence_leash_connection")) {
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

            e.getDrops().clear();
        }

        // LeashHitch（当プラグインが作成したもの）が壊れた場合のクリーンアップ
        if (e.getEntity() instanceof LeashHitch && e.getEntity().getScoreboardTags().contains("bettersurvival:fence_leash_hitch")) {
            Location blockLoc = e.getEntity().getLocation().getBlock().getLocation();
            hitchLeadCounts.remove(blockLoc);

            // そのヒッチに繋がっているコネクター Rabbit を削除
            for (org.bukkit.entity.Entity ent : e.getEntity().getWorld().getEntitiesByClass(org.bukkit.entity.Entity.class)) {
                if (ent instanceof Rabbit && ent.getScoreboardTags().contains("bettersurvival:fence_leash_connection")) {
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

    @EventHandler
    public void onEntityUnleash(EntityUnleashEvent e) {
        // 自作のコネクター/テザー Rabbit はリード解除で自動削除する
        if (e.getEntity() instanceof Rabbit && (e.getEntity().getScoreboardTags().contains("bettersurvival:fence_leash_connection") ||
            e.getEntity().getScoreboardTags().contains("bettersurvival:fence_leash_tether"))) {
            UUID id = e.getEntity().getUniqueId();

            // コネクター Rabbit（Pos2 側）のリード解除/死亡は、対応する Pos1 のリード消費カウントをデクリメントする
            if (e.getEntity().getScoreboardTags().contains("bettersurvival:fence_leash_connection")) {
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

            // もし tetherRabbits に含まれるエンティティなら、そのプレイヤーの全ての関連エンティティを削除
            java.util.List<UUID> toRemoveKeys = new java.util.ArrayList<>();
            for (Map.Entry<UUID, Rabbit> entry : tetherRabbits.entrySet()) {
                Rabbit ent = entry.getValue();
                if (ent != null && ent.getUniqueId().equals(id)) toRemoveKeys.add(entry.getKey());
            }

            for (UUID key : toRemoveKeys) {
                Rabbit removed = tetherRabbits.remove(key);
                if (removed != null) removed.remove();
            }

            // 最後に、このイベントのエンティティ自体も削除
            e.getEntity().remove();
        }
    }

    // 指定ブロック位置に当プラグインが作成した LeashHitch があれば返す
    private LeashHitch findHitchAtBlock(Location blockLoc) {
        for (LeashHitch h : blockLoc.getWorld().getEntitiesByClass(LeashHitch.class)) {
            if (h.getLocation().getBlock().equals(blockLoc.getBlock()) && h.getScoreboardTags().contains("bettersurvival:fence_leash_hitch")) {
                return h;
            }
        }
        return null;
    }

    // 指定ヒッチに繋がっているコネクター Rabbit を探す
    private java.util.List<Rabbit> findConnectorsAttachedTo(LeashHitch hitch) {
        java.util.List<Rabbit> out = new java.util.ArrayList<>();
        if (hitch == null) return out;
        for (Rabbit r : hitch.getWorld().getEntitiesByClass(Rabbit.class)) {
            if (!r.getScoreboardTags().contains("bettersurvival:fence_leash_connection")) continue;
            try {
                org.bukkit.entity.Entity holder = r.getLeashHolder();
                if (holder != null && holder.equals(hitch)) out.add(r);
            } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        }
        return out;
    }

    private Rabbit chooseNearest(java.util.List<Rabbit> list, Location loc) {
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
