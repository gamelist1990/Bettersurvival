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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

public class FenceLeashModule implements Listener {

    private final ToggleModule toggle;
    private static final String KEY = "fenceleash";

    // プレイヤーごとの Pos1 フェンス座標のみ保持
    private final Map<UUID, Location> pendingFirst = new HashMap<>();
    // Pos1選択中の視覚的テザー Rabbit を記録（複数のエンティティを保持）
    private final Map<UUID, java.util.List<org.bukkit.entity.Entity>> tetherRabbits = new HashMap<>();

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
        if (!hasLead) return;

        if (!toggle.getGlobal(KEY)) return;
        if (!toggle.isEnabledFor(player.getUniqueId().toString(), KEY)) return;

        Location fencePos = e.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);
        UUID pid = player.getUniqueId();

        // Pos1 が既に選択済みの場合: Pos2 として接続を作成
        if (pendingFirst.containsKey(pid)) {
            Location firstPos = pendingFirst.remove(pid);

            // 古い視覚的テザーを削除（複数エンティティ対応）
            java.util.List<org.bukkit.entity.Entity> oldTethers = tetherRabbits.remove(pid);
            if (oldTethers != null) {
                for (org.bukkit.entity.Entity ent : oldTethers) ent.remove();
            }

            // 同じフェンスへの接続は禁止
            if (firstPos.getBlock().equals(e.getClickedBlock())) {
                player.sendMessage("§c▸ 同じフェンスには接続できません。");
                e.setCancelled(true);
                return;
            }

            // Pos1 と Pos2 の両方に LeashHitch を作成
            LeashHitch hitch1 = firstPos.getWorld().spawn(firstPos, LeashHitch.class);

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

            // リード消費
            consumeLead(player, main, off);

            player.sendMessage("§a▸ フェンスを接続しました。");
            e.setCancelled(true);
            return;
        }

        // Pos1 を新たに選択
        // 既存の視覚的テザーがあれば削除（複数エンティティ対応）
        java.util.List<org.bukkit.entity.Entity> oldTethers2 = tetherRabbits.remove(pid);
        if (oldTethers2 != null) {
            for (org.bukkit.entity.Entity ent : oldTethers2) ent.remove();
        }

        pendingFirst.put(pid, fencePos);

        // Pos1 側に LeashHitch を作成
        LeashHitch hitch = fencePos.getWorld().spawn(fencePos, LeashHitch.class);
        // Hitch を永続化しておく（アンカーに繋がずにフェンス上ノットを常に表示するため）
        try { hitch.setPersistent(true); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        hitch.addScoreboardTag("bettersurvival:fence_leash_hitch");

        // Hitch を維持するためのアンカー Rabbit を生成（ヒッチに繋いでおく）
        Rabbit anchor = fencePos.getWorld().spawn(fencePos, Rabbit.class);
        anchor.setInvisible(true);
        anchor.setInvulnerable(true);
        anchor.setSilent(true);
        anchor.addScoreboardTag("bettersurvival:fence_leash_anchor");
        try { anchor.setAI(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        try { anchor.setGravity(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        try { anchor.setCollidable(false); } catch (NoSuchMethodError | AbstractMethodError ignored) {}
        anchor.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        // アンカーはヒッチに繋がない（見えないウサギとヒッチを結ばない）

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

        // テザー Rabbit とアンカー Rabbit を記録（Pos2選択時または キャンセル時に削除）
        java.util.List<org.bukkit.entity.Entity> list = new java.util.ArrayList<>();
        list.add(anchor);
        list.add(tether);
        tetherRabbits.put(pid, list);

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
            e.getEntity().getScoreboardTags().contains("bettersurvival:fence_leash_tether") ||
            e.getEntity().getScoreboardTags().contains("bettersurvival:fence_leash_anchor"))) {
            e.getDrops().clear();
        }
    }

    @EventHandler
    public void onEntityUnleash(EntityUnleashEvent e) {
        // 自作のコネクター/テザー Rabbit はリード解除で自動削除する
        if (e.getEntity() instanceof Rabbit && (e.getEntity().getScoreboardTags().contains("bettersurvival:fence_leash_connection") ||
                e.getEntity().getScoreboardTags().contains("bettersurvival:fence_leash_tether") ||
                e.getEntity().getScoreboardTags().contains("bettersurvival:fence_leash_anchor"))) {
            UUID id = e.getEntity().getUniqueId();

            // もし tetherRabbits に含まれるエンティティなら、そのプレイヤーの全ての関連エンティティを削除
            java.util.List<UUID> toRemoveKeys = new java.util.ArrayList<>();
            for (Map.Entry<UUID, java.util.List<org.bukkit.entity.Entity>> entry : tetherRabbits.entrySet()) {
                for (org.bukkit.entity.Entity ent : entry.getValue()) {
                    if (ent != null && ent.getUniqueId().equals(id)) {
                        toRemoveKeys.add(entry.getKey());
                        break;
                    }
                }
            }

            for (UUID key : toRemoveKeys) {
                java.util.List<org.bukkit.entity.Entity> removed = tetherRabbits.remove(key);
                if (removed != null) {
                    for (org.bukkit.entity.Entity ent : removed) if (ent != null) ent.remove();
                }
            }

            // 最後に、このイベントのエンティティ自体も削除
            e.getEntity().remove();
        }
    }
}
