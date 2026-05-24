package org.pexserver.koukunn.bettersurvival.Modules.Feature.Home;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.FormsUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeModule {
    public static final String FEATURE_KEY = "home";

    private static final String UI_TITLE = "§8Home";
    private static final String REMOVE_UI_TITLE = "§8Home削除";
    private static final int[] HOME_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
    private static final int ACTION_ADD_SLOT = 49;
    private static final int ACTION_REMOVE_SLOT = 50;
    private static final int ACTION_CLOSE_SLOT = 53;

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
        if (!toggleModule.getGlobal(FEATURE_KEY))
            return false;
        return toggleModule.isEnabledFor(player.getUniqueId().toString(), FEATURE_KEY);
    }

    public void teleportDefault(Player player) {
        if (!checkEnabled(player))
            return;
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
        if (!checkEnabled(player))
            return;
        HomePoint home = store.getHome(player, name).orElse(null);
        if (home == null) {
            player.sendMessage("§cHome '" + name + "' が見つかりません");
            return;
        }
        teleportTo(player, home);
    }

    public void addHome(Player player, String name) {
        if (!checkEnabled(player))
            return;
        String normalized = store.normalizeName(name);
        if (!isValidName(normalized)) {
            player.sendMessage("§cHome名は1〜32文字で、空白と / は使えません");
            return;
        }
        if (isReservedName(normalized)) {
            player.sendMessage("§cこの名前はサブコマンドと被るため使えません");
            return;
        }

        int unlocked = store.getUnlockedSlots(player);
        HomeStore.SaveResult result = store.saveHome(player, normalized, player.getLocation(), unlocked);
        if (result == HomeStore.SaveResult.LIMIT) {
            player.sendMessage("§cHomeは現在 " + unlocked + " 個までです。GUIの『枠を解放』で拡張できます");
            return;
        }
        if (result == HomeStore.SaveResult.FAILED) {
            player.sendMessage("§cHomeの保存に失敗しました");
            return;
        }
        player.sendMessage("§aHome '" + normalized + "' を現在位置に登録しました");
    }

    public void removeHome(Player player, String name) {
        if (!checkEnabled(player))
            return;
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

    public void unlockNextSlot(Player player) {
        if (!checkEnabled(player))
            return;
        unlockHomeSlot(player);
    }

    public void listHomes(Player player) {
        if (!checkEnabled(player))
            return;
        List<HomePoint> homes = store.getHomes(player);
        int unlocked = store.getUnlockedSlots(player);
        if (homes.isEmpty()) {
            player.sendMessage("§cHomeが登録されていません");
            player.sendMessage("§7登録枠: " + unlocked + "/" + HomeStore.MAX_HOME_SLOTS);
            return;
        }
        player.sendMessage("§e--- Home一覧 (" + homes.size() + "/" + unlocked + ") ---");
        for (HomePoint home : homes) {
            player.sendMessage("§a" + home.getName() + " §7- §f" + home.formatLocation());
        }
    }

    public void openUI(Player player) {
        if (!checkEnabled(player))
            return;
        if (FloodgateUtil.isBedrock(player)) {
            if (openBedrockMainUI(player))
                return;
        }
        openJavaMainUI(player);
    }

    private void openJavaMainUI(Player player) {
        List<HomePoint> homes = store.getHomes(player);
        int unlocked = store.getUnlockedSlots(player);
        int nextUnlockCost = getNextUnlockCost(unlocked);

        ChestUI.Builder builder = ChestUI.builder()
                .title(UI_TITLE)
                .size(54)
                .addButtonAt(4,
                        unlocked >= HomeStore.MAX_HOME_SLOTS ? "§bHome管理 §7(最大)" : "§eHome管理 §7(クリックで枠解放)",
                        unlocked >= HomeStore.MAX_HOME_SLOTS ? Material.NETHER_STAR : Material.GLOWSTONE_DUST,
                        buildUnlockLore(unlocked, nextUnlockCost))
                .addButtonAt(ACTION_ADD_SLOT, "§a現在地を登録", Material.LIME_DYE,
                        "§7空きがあるときに自動名(homeX)で登録")
                .addButtonAt(ACTION_REMOVE_SLOT, "§cHomeを削除", Material.RED_DYE,
                        "§7登録済みHomeを選んで削除")
                .addButtonAt(ACTION_CLOSE_SLOT, "§7閉じる", Material.BARRIER, "§7GUIを閉じます");

        int index = 0;
        for (int slot : HOME_SLOTS) {
            if (index < homes.size()) {
                HomePoint home = homes.get(index);
                builder.addButtonAt(slot, "§b" + home.getName(), Material.ENDER_PEARL,
                        "§7" + home.formatLocation() + "\n§aクリックで移動");
            } else if (index < unlocked) {
                builder.addButtonAt(slot, "§a開放済みスロット #" + (index + 1), Material.LIME_STAINED_GLASS_PANE,
                        "§7この枠は使えます\n§7下段の『現在地を登録』で追加");
            } else {
                builder.addButtonAt(slot, "§8未開放スロット #" + (index + 1), Material.RED_STAINED_GLASS_PANE,
                        buildLockedSlotLore(index + 1, unlocked, nextUnlockCost));
            }
            index++;
        }

        builder.then((result, p) -> {
            if (!result.success || result.slot == null)
                return;
            int slot = result.slot;
            if (slot == ACTION_CLOSE_SLOT) {
                playUiClick(p);
                ChestUI.closeMenu(p);
                return;
            }
            if (slot == ACTION_ADD_SLOT) {
                if (store.getHomes(p).size() >= store.getUnlockedSlots(p)) {
                    playUiFail(p);
                    p.sendMessage("§c登録枠がいっぱいです。枠を解放してください");
                    openJavaMainUI(p);
                    return;
                }
                playUiClick(p);
                registerAutoHome(p);
                openJavaMainUI(p);
                return;
            }
            if (slot == ACTION_REMOVE_SLOT) {
                playUiClick(p);
                openJavaRemoveUI(p);
                return;
            }
            if (slot == 4) {
                playUiClick(p);
                unlockHomeSlot(p);
                openJavaMainUI(p);
                return;
            }
            int homeIndex = slotToHomeIndex(slot);
            if (homeIndex < 0)
                return;
            List<HomePoint> currentHomes = store.getHomes(p);
            if (homeIndex >= currentHomes.size()) {
                int unlockedSlots = store.getUnlockedSlots(p);
                if (homeIndex < unlockedSlots) {
                    playUiFail(p);
                    p.sendMessage("§7この枠は開放済みです。下段の『現在地を登録』でHomeを追加できます");
                } else {
                    playUiClick(p);
                    unlockHomeSlot(p);
                    openJavaMainUI(p);
                }
                return;
            }
            playUiClick(p);
            ChestUI.closeMenu(p);
            teleportTo(p, currentHomes.get(homeIndex));
        }).show(player);
    }

    private void openJavaRemoveUI(Player player) {
        List<HomePoint> homes = store.getHomes(player);
        ChestUI.Builder builder = ChestUI.builder()
                .title(REMOVE_UI_TITLE)
                .size(54)
                .addButtonAt(4, "§c削除するHomeを選択", Material.RED_DYE, "§7クリックで削除")
                .addButtonAt(45, "§e戻る", Material.ARROW, "§7Homeメインへ戻る")
                .addButtonAt(53, "§7閉じる", Material.BARRIER, "§7GUIを閉じます");

        int index = 0;
        for (int slot : HOME_SLOTS) {
            if (index < homes.size()) {
                HomePoint home = homes.get(index);
                builder.addButtonAt(slot, "§c" + home.getName(), Material.RED_DYE,
                        "§7" + home.formatLocation() + "\n§cクリックで削除");
            } else {
                builder.addButtonAt(slot, " ", Material.GRAY_STAINED_GLASS_PANE);
            }
            index++;
        }

        builder.then((result, p) -> {
            if (!result.success || result.slot == null)
                return;
            int slot = result.slot;
            if (slot == 53) {
                playUiClick(p);
                ChestUI.closeMenu(p);
                return;
            }
            if (slot == 45) {
                playUiClick(p);
                openJavaMainUI(p);
                return;
            }
            int homeIndex = slotToHomeIndex(slot);
            if (homeIndex < 0)
                return;
            List<HomePoint> currentHomes = store.getHomes(p);
            if (homeIndex >= currentHomes.size())
                return;
            playUiClick(p);
            removeHome(p, currentHomes.get(homeIndex).getName());
            openJavaRemoveUI(p);
        }).show(player);
    }

    private boolean openBedrockMainUI(Player player) {
        List<HomePoint> homes = store.getHomes(player);
        int unlocked = store.getUnlockedSlots(player);
        int nextUnlockCost = getNextUnlockCost(unlocked);

        List<FormsUtil.ButtonSpec> buttons = new ArrayList<>();
        for (HomePoint home : homes) {
            buttons.add(FormsUtil.ButtonSpec.ofText("▶ " + home.getName() + " (移動)"));
        }
        buttons.add(FormsUtil.ButtonSpec.ofText("現在地を登録"));
        buttons.add(FormsUtil.ButtonSpec.ofText("Homeを削除"));
        buttons.add(FormsUtil.ButtonSpec.ofText(
                unlocked >= HomeStore.MAX_HOME_SLOTS ? "枠解放 (最大)" : "枠を解放 (必要: コンパスx1 + " + nextUnlockCost + "Lv)"));
        buttons.add(FormsUtil.ButtonSpec.ofText("閉じる"));

        String content = "登録数: " + homes.size() + "/" + unlocked + " (最大" + HomeStore.MAX_HOME_SLOTS + ")";
        return FormsUtil.openSimpleForm(player, "Home", content, buttons, idx -> {
            if (idx < 0)
                return;
            playUiClick(player);
            if (idx < homes.size()) {
                teleportTo(player, homes.get(idx));
                return;
            }
            int base = homes.size();
            if (idx == base) {
                if (homes.size() >= unlocked) {
                    playUiFail(player);
                    player.sendMessage("§c登録枠がいっぱいです。枠を解放してください");
                    openBedrockMainUI(player);
                    return;
                }
                registerAutoHome(player);
                openBedrockMainUI(player);
                return;
            }
            if (idx == base + 1) {
                openBedrockRemoveUI(player, homes);
                return;
            }
            if (idx == base + 2) {
                unlockHomeSlot(player);
                openBedrockMainUI(player);
            }
        });
    }

    private void openBedrockRemoveUI(Player player, List<HomePoint> homes) {
        List<FormsUtil.ButtonSpec> buttons = new ArrayList<>();
        for (HomePoint home : homes) {
            buttons.add(FormsUtil.ButtonSpec.ofText("削除: " + home.getName()));
        }
        buttons.add(FormsUtil.ButtonSpec.ofText("戻る"));
        FormsUtil.openSimpleForm(player, "Home削除", buttons, idx -> {
            if (idx < 0)
                return;
            playUiClick(player);
            if (idx >= homes.size()) {
                openBedrockMainUI(player);
                return;
            }
            removeHome(player, homes.get(idx).getName());
            openBedrockMainUI(player);
        });
    }

    private int slotToHomeIndex(int slot) {
        for (int i = 0; i < HOME_SLOTS.length; i++) {
            if (HOME_SLOTS[i] == slot)
                return i;
        }
        return -1;
    }

    private void unlockHomeSlot(Player player) {
        int unlocked = store.getUnlockedSlots(player);
        if (unlocked >= HomeStore.MAX_HOME_SLOTS) {
            playUiFail(player);
            player.sendMessage("§cHome枠はすでに最大です");
            return;
        }
        int costLevels = getNextUnlockCost(unlocked);
        if (!hasCompass(player)) {
            playUiFail(player);
            player.sendMessage("§c枠解放にはコンパスが1個必要です");
            return;
        }
        if (player.getLevel() < costLevels) {
            playUiFail(player);
            player.sendMessage("§c枠解放には " + costLevels + " レベル必要です（現在: " + player.getLevel() + "）");
            return;
        }

        if (!consumeCompass(player)) {
            playUiFail(player);
            player.sendMessage("§cコンパスの消費に失敗しました");
            return;
        }
        player.setLevel(player.getLevel() - costLevels);
        if (!store.setUnlockedSlots(player, unlocked + 1)) {
            playUiFail(player);
            player.sendMessage("§c枠解放の保存に失敗しました");
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.2f);
        player.sendMessage("§aHome枠を解放しました: " + (unlocked + 1) + "/" + HomeStore.MAX_HOME_SLOTS);
    }

    private void registerAutoHome(Player player) {
        String name = generateAutoHomeName(player);
        addHome(player, name);
    }

    private String generateAutoHomeName(Player player) {
        List<HomePoint> homes = store.getHomes(player);
        for (int i = 1; i <= 99; i++) {
            String candidate = "home" + i;
            boolean exists = false;
            for (HomePoint home : homes) {
                if (home.getName().equalsIgnoreCase(candidate)) {
                    exists = true;
                    break;
                }
            }
            if (!exists)
                return candidate;
        }
        return "home" + (homes.size() + 1);
    }

    private int getNextUnlockCost(int unlockedSlots) {
        int next = Math.max(HomeStore.DEFAULT_HOME_SLOTS + 1, unlockedSlots + 1);
        return Math.min(HomeStore.MAX_HOME_SLOTS, next);
    }

    private String buildUnlockLore(int unlocked, int currentCost) {
        if (unlocked >= HomeStore.MAX_HOME_SLOTS) {
            return "§7現在: " + unlocked + "/" + HomeStore.MAX_HOME_SLOTS + "\n§a全スロット解放済み";
        }
        int afterUnlock = unlocked + 1;
        StringBuilder lore = new StringBuilder();
        lore.append("§eクリックで次の枠を解放\n");
        lore.append("§7現在: ").append(unlocked).append("/").append(HomeStore.MAX_HOME_SLOTS).append("\n");
        lore.append("§7今回必要: コンパス x1 + ").append(currentCost).append(" レベル\n");
        lore.append("§7解放後: ").append(afterUnlock).append("/").append(HomeStore.MAX_HOME_SLOTS);
        if (afterUnlock < HomeStore.MAX_HOME_SLOTS) {
            int nextCost = getNextUnlockCost(afterUnlock);
            lore.append("\n§8次回必要: コンパス x1 + ").append(nextCost).append(" レベル");
        } else {
            lore.append("\n§8次で最大到達");
        }
        return lore.toString();
    }

    private String buildLockedSlotLore(int slotNumber, int unlocked, int currentCost) {
        if (unlocked >= HomeStore.MAX_HOME_SLOTS) {
            return "§7この枠はすでに開放済みです";
        }
        return "§7スロット #" + slotNumber + " は未開放\n§eクリックでこの枠を解放\n§7必要: コンパス x1 + " + currentCost + " レベル";
    }

    private void playUiClick(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
    }

    private void playUiFail(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
    }

    private boolean hasCompass(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() != Material.COMPASS)
                continue;
            if (stack.getAmount() > 0)
                return true;
        }
        return false;
    }

    private boolean consumeCompass(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() != Material.COMPASS)
                continue;
            if (stack.getAmount() <= 0)
                continue;
            stack.setAmount(stack.getAmount() - 1);
            return true;
        }
        return false;
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
        if (name == null || name.isBlank())
            return false;
        if (name.length() > 32)
            return false;
        return !name.contains(" ") && !name.contains("/") && !name.contains("\\");
    }

    private boolean isReservedName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("add") || lower.equals("remove") || lower.equals("list") || lower.equals("ui") || lower.equals("unlock") || lower.equals("expand");
    }
}
