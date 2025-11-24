package org.pexserver.koukunn.bettersurvival.Modules.Feature.FenceLeash;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.UUID;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

public class FenceLeashModule implements Listener {

    private final ToggleModule toggle;
    private static final String KEY = "fenceleash";

    // Leash 操作は API に委譲します
    private final LeashAPI api;

    public FenceLeashModule(ToggleModule toggle) {
        this.toggle = toggle;
        this.api = new LeashAPI();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (e.getClickedBlock() == null)
            return;

        Material btype = e.getClickedBlock().getType();
        if (!btype.name().contains("_FENCE"))
            return;

        Player player = e.getPlayer();
        Material main = player.getInventory().getItemInMainHand().getType();
        Material off = player.getInventory().getItemInOffHand().getType();
        boolean hasLead = (main == Material.LEAD) || (off == Material.LEAD);

        if (!toggle.getGlobal(KEY))
            return;
        if (!toggle.isEnabledFor(player.getUniqueId().toString(), KEY))
            return;

        // ブロック位置と中心位置を分けて扱う
        Location blockLoc = e.getClickedBlock().getLocation();
        Location fencePos = blockLoc.clone().add(0.5, 0.5, 0.5);
        UUID pid = player.getUniqueId();

        // ここからはリードを持っていることが前提
        if (!hasLead)
            return;

        // Pos1 が既に選択済みの場合: Pos2 として接続を作成
        if (api.hasPending(pid)) {
            // Already picked Pos1 — connect it to this clicked fence
            Location firstPos = api.getPending(pid);
            if (firstPos != null && firstPos.getBlock().equals(e.getClickedBlock())) {
                api.cancelPending(pid);
                player.sendMessage("§c▸ 同じフェンスには接続できません。Pos1 をキャンセルしました。");
                e.setCancelled(true);
                return;
            }

            if (!hasLead)
                return;

            boolean connected = api.connectPendingTo(player, fencePos, blockLoc);
            if (connected)
                consumeLead(player, main, off);
            player.sendMessage("§a▸ フェンスを接続しました。");
            e.setCancelled(true);
            return;
        }

        // Pos1 を新たに選択
        // Pos1 側に LeashHitch を作成（API に委譲）
        api.createPos1(player, fencePos, blockLoc);
        consumeLead(player, main, off);

        player.sendMessage("§e▸ Pos1 を選択しました。別のフェンスを右クリックしてください。");
        e.setCancelled(true);
    }

    private void consumeLead(Player player, Material main, Material off) {
        if (!player.getGameMode().name().equals("CREATIVE")) {
            if (main == Material.LEAD) {
                int amt = player.getInventory().getItemInMainHand().getAmount();
                if (amt <= 1)
                    player.getInventory().setItemInMainHand(null);
                else
                    player.getInventory().getItemInMainHand().setAmount(amt - 1);
            } else if (off == Material.LEAD) {
                int amt = player.getInventory().getItemInOffHand().getAmount();
                if (amt <= 1)
                    player.getInventory().setItemInOffHand(null);
                else
                    player.getInventory().getItemInOffHand().setAmount(amt - 1);
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        api.onEntityDeath(e);
    }

    @EventHandler
    public void onEntityUnleash(EntityUnleashEvent e) {
        api.onEntityUnleash(e);
    }
}
