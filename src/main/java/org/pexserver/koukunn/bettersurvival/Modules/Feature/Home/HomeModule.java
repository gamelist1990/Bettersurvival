package org.pexserver.koukunn.bettersurvival.Modules.Feature.Home;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.List;
import java.util.Locale;

public class HomeModule {
    public static final String FEATURE_KEY = "home";

    private final Loader plugin;
    private final ToggleModule toggleModule;
    private final HomeStore store;

    public HomeModule(Loader plugin) {
        this.plugin = plugin;
        this.toggleModule = plugin.getToggleModule();
        this.store = new HomeStore(plugin.getConfigManager());
    }

    public HomeStore getStore() {
        return store;
    }

    public boolean canUse(Player player) {
        if (!toggleModule.getGlobal(FEATURE_KEY)) return false;
        return toggleModule.isEnabledFor(player.getUniqueId().toString(), FEATURE_KEY);
    }

    public void teleportDefault(Player player) {
        if (!checkEnabled(player)) return;
        List<HomePoint> homes = store.getHomes(player);
        if (homes.isEmpty()) {
            player.sendMessage("§cHomeが登録されていません。§e/home add <名前> §cで登録できます。");
            return;
        }
        if (homes.size() == 1) {
            teleportTo(player, homes.get(0));
            return;
        }
        openUI(player);
    }

    public void teleportNamed(Player player, String name) {
        if (!checkEnabled(player)) return;
        HomePoint home = store.getHome(player, name).orElse(null);
        if (home == null) {
            player.sendMessage("§cHome '" + name + "' が見つかりません");
            return;
        }
        teleportTo(player, home);
    }

    public void addHome(Player player, String name) {
        if (!checkEnabled(player)) return;
        String normalized = store.normalizeName(name);
        if (!isValidName(normalized)) {
            player.sendMessage("§cHome名は1〜32文字で、空白と / は使えません");
            return;
        }
        if (isReservedName(normalized)) {
            player.sendMessage("§cこの名前はサブコマンドと被るため使えません");
            return;
        }

        HomeStore.SaveResult result = store.saveHome(player, normalized, player.getLocation());
        if (result == HomeStore.SaveResult.LIMIT) {
            player.sendMessage("§cHomeは最大" + HomeStore.MAX_HOMES + "個まで登録できます");
            return;
        }
        if (result == HomeStore.SaveResult.FAILED) {
            player.sendMessage("§cHomeの保存に失敗しました");
            return;
        }
        player.sendMessage("§aHome '" + normalized + "' を現在位置に登録しました");
    }

    public void removeHome(Player player, String name) {
        if (!checkEnabled(player)) return;
        String normalized = store.normalizeName(name);
        if (normalized.isEmpty()) {
            player.sendMessage("§c削除するHome名を指定してください");
            return;
        }
        if (store.removeHome(player, normalized)) {
            player.sendMessage("§aHome '" + normalized + "' を削除しました");
        } else {
            player.sendMessage("§cHome '" + normalized + "' が見つかりません");
        }
    }

    public void listHomes(Player player) {
        if (!checkEnabled(player)) return;
        List<HomePoint> homes = store.getHomes(player);
        if (homes.isEmpty()) {
            player.sendMessage("§cHomeが登録されていません");
            return;
        }
        player.sendMessage("§e--- Home一覧 (" + homes.size() + "/" + HomeStore.MAX_HOMES + ") ---");
        for (HomePoint home : homes) {
            player.sendMessage("§a" + home.getName() + " §7- §f" + home.formatLocation());
        }
    }

    public void openUI(Player player) {
        if (!checkEnabled(player)) return;
        List<HomePoint> homes = store.getHomes(player);
        DialogUI.builder()
                .title("Home")
                .body("登録数: " + homes.size() + "/" + HomeStore.MAX_HOMES)
                .addAction("作成", 0x57F287)
                .addAction("移動", 0x5865F2)
                .addAction("削除", 0xED4245)
                .onResponse((result, p) -> {
                    int index = result.getActionIndex();
                    if (index == 0) {
                        openCreateUI(p);
                    } else if (index == 1) {
                        openMoveUI(p);
                    } else if (index == 2) {
                        openRemoveUI(p);
                    }
                })
                .show(player);
    }

    private void openCreateUI(Player player) {
        if (!checkEnabled(player)) return;
        List<HomePoint> homes = store.getHomes(player);
        if (homes.size() >= HomeStore.MAX_HOMES) {
            player.sendMessage("§cHomeは最大" + HomeStore.MAX_HOMES + "個まで登録できます");
            openUI(player);
            return;
        }

        DialogUI.builder()
                .title("Home作成")
                .body("現在位置をHomeとして登録します")
                .addTextInput("name", "Home名", "", 32, false)
                .confirmation("作成", "戻る")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openUI(p);
                        return;
                    }
                    addHome(p, result.getText("name"));
                    openUI(p);
                })
                .show(player);
    }

    private void openMoveUI(Player player) {
        if (!checkEnabled(player)) return;
        List<HomePoint> homes = store.getHomes(player);
        if (homes.isEmpty()) {
            player.sendMessage("§cHomeが登録されていません。§e/home add <名前> §cで登録できます。");
            openUI(player);
            return;
        }

        DialogUI.Builder builder = DialogUI.builder()
                .title("Home移動")
                .body("移動先を選択してください");
        for (HomePoint home : homes) {
            builder.body(home.getName() + " - " + home.formatLocation());
            builder.addAction(home.getName(), 0x57F287);
        }
        builder.onResponse((result, p) -> {
                    int index = result.getActionIndex();
                    if (index < 0 || index >= homes.size()) return;
                    teleportTo(p, homes.get(index));
                })
                .show(player);
    }

    private void openRemoveUI(Player player) {
        if (!checkEnabled(player)) return;
        List<HomePoint> homes = store.getHomes(player);
        if (homes.isEmpty()) {
            player.sendMessage("§cHomeが登録されていません");
            openUI(player);
            return;
        }

        DialogUI.Builder builder = DialogUI.builder()
                .title("Home削除")
                .body("削除するHomeを選択してください");
        for (HomePoint home : homes) {
            builder.body(home.getName() + " - " + home.formatLocation());
            builder.addAction(home.getName(), 0xED4245);
        }
        builder.onResponse((result, p) -> {
                    int index = result.getActionIndex();
                    if (index < 0 || index >= homes.size()) return;
                    openRemoveConfirmUI(p, homes.get(index));
                })
                .show(player);
    }

    private void openRemoveConfirmUI(Player player, HomePoint home) {
        DialogUI.builder()
                .title("Home削除")
                .body(home.getName() + " を削除しますか？")
                .body(home.formatLocation())
                .confirmation("削除", "戻る")
                .onResponse((result, p) -> {
                    if (result.isConfirmed()) {
                        removeHome(p, home.getName());
                    }
                    openUI(p);
                })
                .show(player);
    }

    private boolean checkEnabled(Player player) {
        if (toggleModule.getGlobal(FEATURE_KEY) && toggleModule.isEnabledFor(player.getUniqueId().toString(), FEATURE_KEY)) {
            return true;
        }
        player.sendMessage("§cHome機能は無効です。/toggle で有効にしてください");
        return false;
    }

    private void teleportTo(Player player, HomePoint home) {
        Location location = home.toLocation();
        if (location == null || location.getWorld() == null) {
            player.sendMessage("§cHome '" + home.getName() + "' のワールドが見つかりません");
            return;
        }
        player.teleportAsync(location).thenAccept(success -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (success) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
                player.sendMessage("§aHome '" + home.getName() + "' に移動しました");
            } else {
                player.sendMessage("§cHome '" + home.getName() + "' への移動に失敗しました");
            }
        }));
    }

    private boolean isValidName(String name) {
        if (name == null || name.isBlank()) return false;
        if (name.length() > 32) return false;
        return !name.contains(" ") && !name.contains("/") && !name.contains("\\");
    }

    private boolean isReservedName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("add") || lower.equals("remove") || lower.equals("list") || lower.equals("ui");
    }
}
