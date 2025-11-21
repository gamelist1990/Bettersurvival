package org.pexserver.koukunn.bettersurvival.Commands.chest;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLock;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockStore;

import org.bukkit.block.Block;
import org.bukkit.Location;

import java.util.List;
import java.util.Optional;

public class ChestCommand extends BaseCommand {

    private final Loader plugin;
    private final ChestLockStore store;

    public ChestCommand(Loader plugin) {
        this.plugin = plugin;
        this.store = new ChestLockStore(plugin.getConfigManager());
    }

    @Override
    public String getName() { return "chest"; }

    @Override
    public String getDescription() { return "チェストをロック・共有するコマンド"; }

    @Override
    public PermissionLevel getPermissionLevel() { return PermissionLevel.MEMBER; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sendError(sender, "プレイヤーのみ使用できます");
            return true;
        }


        org.pexserver.koukunn.bettersurvival.Modules.ToggleModule toggle = plugin.getToggleModule();
        boolean current = toggle.getGlobal("chestlock");
        if (!current) {
            sendError(sender, "ChestLock 機能は無効化されています");
            return true;
        }

        Player p = (Player) sender;

        if (args.length == 0) {
            sendInfo(sender, "使用: /chest lock <name> | /chest unlock | /chest member add|remove|list <name> | /chest ui | /chest op unlock | /chest op see");
            return true;
        }

        String sub = args[0].toLowerCase();
        if ("lock".equals(sub)) {
            if (args.length < 2) {
                sendError(sender, "ロック名を指定してください");
                return true;
            }
            Block b = p.getTargetBlockExact(6);
            if (b == null) { sendError(sender, "チェストを見てください"); return true; }
            List<Location> locs = ChestLockModule.getChestRelatedLocations(b);
            if (locs.isEmpty()) { sendError(sender, "対象はチェスト/樽のみです"); return true; }
            // create lock
            // check if already locked by someone else
            for (Location loc : locs) {
                Optional<ChestLock> exist = store.get(loc);
                if (exist.isPresent() && !exist.get().getOwner().equals(p.getUniqueId().toString())) {
                    sendError(sender, "このチェストは既に他プレイヤーがロックしています");
                    return true;
                }
            }
            ChestLock lock = new ChestLock(p.getUniqueId().toString(), args[1]);
            for (Location loc : locs) store.save(loc, lock);
            sendSuccess(sender, "チェストをロックしました: " + args[1]);
            return true;
        }

        if ("unlock".equals(sub)) {
            Block b = p.getTargetBlockExact(6);
            if (b == null) { sendError(sender, "チェストを見てください"); return true; }
            List<Location> locs = ChestLockModule.getChestRelatedLocations(b);
            if (locs.isEmpty()) { sendError(sender, "対象はチェスト/樽のみです"); return true; }
            boolean removed = false;
            for (Location loc : locs) {
                Optional<ChestLock> opt = store.get(loc);
                if (!opt.isPresent()) continue;
                ChestLock lock = opt.get();
                if (lock.getOwner().equals(p.getUniqueId().toString())) {
                    store.remove(loc); removed = true; continue;
                }
                sendError(sender, "あなたはこのチェストのオーナーではありません");
                return true;
            }

            if (removed) sendSuccess(sender, "チェストのロックを解除しました");
            else sendInfo(sender, "このチェストはロックされていません");
            return true;
        }

        if ("op".equals(sub)) {
            if (!p.isOp()) { sendError(sender, "OP権限が必要です"); return true; }
            if (args.length < 2) { sendError(sender, "/chest op <toggle|unlock|see>"); return true; }
            String opSub = args[1].toLowerCase();
            Block b = p.getTargetBlockExact(6);
            if (b == null) { sendError(sender, "チェストを見てください"); return true; }
            List<Location> locs = ChestLockModule.getChestRelatedLocations(b);
            if (locs.isEmpty()) { sendError(sender, "対象はチェスト/樽のみです"); return true; }
            if ("unlock".equals(opSub)) {
                for (Location loc : locs) store.remove(loc);
                sendSuccess(sender, "強制的にロックを解除しました");
                return true;
            } else if ("see".equals(opSub)) {
                // open inventory
                Bukkit.getScheduler().runTask(plugin, () -> {
                    org.bukkit.block.BlockState st = b.getState();
                    if (st instanceof org.bukkit.block.Container) {
                        p.openInventory(((org.bukkit.block.Container) st).getInventory());
                    } else sendError(sender, "インベントリを開けません");
                });
                return true;
            }
            if ("toggle".equals(opSub)) {
                toggle.setGlobal("chestlock", !current);
                sendSuccess(sender, "ChestLock のグローバル設定を " + (!current ? "有効" : "無効") + " にしました");
                return true;
            }
            sendError(sender, "/chest op <unlock|see>");
            return true;
        }

        if ("member".equals(sub)) {
            if (args.length < 2) { sendError(sender, "member add/remove/list <name>"); return true; }
            String cmd = args[1].toLowerCase();
            Block b = p.getTargetBlockExact(6);
            if (b == null) { sendError(sender, "チェストを見てください"); return true; }
            List<Location> locs = ChestLockModule.getChestRelatedLocations(b);
            if (locs.isEmpty()) { sendError(sender, "対象はチェスト/樽のみです"); return true; }
            Optional<ChestLock> css = store.get(locs.get(0));
            if (!css.isPresent()) { sendError(sender, "このチェストは保護されていません"); return true; }
            ChestLock lock = css.get();
            if (!lock.getOwner().equals(p.getUniqueId().toString()) && !p.isOp()) { sendError(sender, "あなたはオーナーではありません"); return true; }
            if ("add".equals(cmd)) {
                if (args.length < 3) { sendError(sender, "プレイヤー名を指定してください"); return true; }
                String name = args[2];
                // 名前部分一致で選択
                Player foundPlayer = null;
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    if (pl.getWorld().equals(p.getWorld()) && pl.getName().toLowerCase().contains(name.toLowerCase())) { foundPlayer = pl; break; }
                }
                if (foundPlayer == null) { sendError(sender, "プレイヤーが見つかりません"); return true; }
                lock.addMember(foundPlayer.getUniqueId().toString());
                    for (Location loc : locs) store.save(loc, lock);
                sendSuccess(sender, "メンバーを追加しました: " + foundPlayer.getName());
                return true;
            }
            if ("remove".equals(cmd)) {
                if (args.length < 3) { sendError(sender, "プレイヤー名を指定してください"); return true; }
                String name = args[2];
                Player foundPlayer = null;
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    if (pl.getWorld().equals(p.getWorld()) && pl.getName().toLowerCase().contains(name.toLowerCase())) { foundPlayer = pl; break; }
                }
                if (foundPlayer == null) { sendError(sender, "プレイヤーが見つかりません"); return true; }
                lock.removeMember(foundPlayer.getUniqueId().toString());
                    for (Location loc : locs) store.save(loc, lock);
                sendSuccess(sender, "メンバーを削除しました: " + foundPlayer.getName());
                return true;
            }
            if ("list".equals(cmd)) {
                StringBuilder sb = new StringBuilder();
                sb.append("メンバー: ");
                for (String m : lock.getMembers()) {
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(java.util.UUID.fromString(m));
                    sb.append(op.getName() == null ? m : op.getName()).append(", ");
                }
                sendInfo(sender, sb.toString());
                return true;
            }
            sendError(sender, "member add/remove/list <name>");
            return true;
        }

        if ("ui".equals(sub)) {
            // chest ui - open the ChestLock GUI
            Block b = p.getTargetBlockExact(6);
            if (b == null) { sendError(sender, "チェストを見てください"); return true; }
            List<Location> locs = ChestLockModule.getChestRelatedLocations(b);
            if (locs.isEmpty()) { sendError(sender, "対象はチェスト/樽のみです"); return true; }
            ChestLock lock = store.get(locs.get(0)).orElse(null);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                    org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockUI.openForPlayer(p, lock, locs.get(0), store);
                });
            return true;
        }

        sendError(sender, "不明なコマンド");
        return true;
    }

    @Override
    public java.util.List<String> getTabCompletions(CommandSender sender, String[] args) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (args.length == 1) {
            list.add("lock"); list.add("unlock"); list.add("member"); list.add("ui"); list.add("op");
        }
        if (args.length == 2 && "op".equalsIgnoreCase(args[0])) { list.add("unlock"); list.add("see"); list.add("toggle"); }
        return list;
    }
}
