package org.pexserver.koukunn.bettersurvival.Modules.Feature.BetterMenu;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.FormsUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ItemCombineModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.ToggleFeature;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BetterMenuModule implements Listener {

    public static final String FEATURE_KEY = "bettermenu";
    private static final String MENU_TITLE = "§8BetterSurvival GUI";
    private static final String MENU_TOGGLE_TITLE = "§8BetterSurvival Toggle";
    private static final String MENU_SHORTCUT_TITLE = "§8有効機能ショートカット";
    private static final String ITEM_NAME = "§b§lBetterSurvival §aGUI";
    private static final int JAVA_MENU_SIZE = 54;
    private static final int ROOT_TOGGLE_SLOT = 20;
    private static final int ROOT_SHORTCUT_SLOT = 24;
    private static final int ROOT_CLOSE_SLOT = 49;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;
    private static final long EXEC_DELAY_TICKS = 10L;

    private static final Map<String, String> FEATURE_SHORTCUT_COMMANDS = Map.of(
            "tpa", "tpa ui",
            "home", "home ui",
            "chestlock", "chest ui"
    );

    private final Loader plugin;
    private final ToggleModule toggle;
    private final NamespacedKey itemKey;

    public BetterMenuModule(Loader plugin, ToggleModule toggle, ItemCombineModule itemCombineModule) {
        this.plugin = plugin;
        this.toggle = toggle;
        this.itemKey = new NamespacedKey(plugin, "better_menu_axe");
        itemCombineModule.recipe("better_menu_axe")
                .first(this::isPlainWoodenAxe)
                .second(this::isBMenuNameTag)
                .groundRadius(0.5D)
                .airRadius(1.5D)
                .verticalRadius(2.0D)
                .allowAirCombine(true)
                .then(this::craftBetterMenuAxe);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
            return;
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (!isBetterMenuAxe(held))
            return;
        event.setCancelled(true);
        if (!toggle.getGlobal(FEATURE_KEY)) {
            event.getPlayer().sendMessage("§cBetterMenu機能は現在無効です");
            return;
        }
        openMenu(event.getPlayer());
    }

    private void openMenu(Player player) {
        if (FloodgateUtil.isBedrock(player)) {
            try {
                if (openBedrockRootMenu(player))
                    return;
            } catch (NoClassDefFoundError ignored) {
            } catch (Throwable ignored) {
            }
        }
        openJavaRootMenu(player);
    }

    private boolean openBedrockRootMenu(Player player) {
        List<ToggleFeature> features = getVisibleUserFeatures();
        List<CommandShortcut> shortcuts = getEnabledShortcuts(player);
        List<FormsUtil.ButtonSpec> buttons = List.of(
                FormsUtil.ButtonSpec.ofText("個別トグル設定"),
                FormsUtil.ButtonSpec.ofText("有効機能UIショートカット"),
                FormsUtil.ButtonSpec.ofText("閉じる")
        );
        String content = "トグル機能: " + features.size() + "\nショートカット: " + shortcuts.size();
        return FormsUtil.openSimpleForm(player, MENU_TITLE, content, buttons, index -> {
            if (index == 0) {
                openBedrockToggleMenu(player);
                return;
            }
            if (index == 1) {
                openBedrockShortcutMenu(player);
            }
        });
    }

    private void openBedrockToggleMenu(Player player) {
        List<ToggleFeature> features = getVisibleUserFeatures();
        List<FormsUtil.ButtonSpec> buttons = new ArrayList<>();
        for (ToggleFeature feature : features) {
            boolean enabled = toggle.isEnabledFor(player.getUniqueId().toString(), feature.getKey());
            String state = enabled ? "§a[ON] " : "§c[OFF] ";
            buttons.add(FormsUtil.ButtonSpec.ofText(state + feature.getDisplayName()));
        }
        buttons.add(FormsUtil.ButtonSpec.ofText("戻る"));
        FormsUtil.openSimpleForm(player, MENU_TOGGLE_TITLE, buttons, index -> {
            if (index < 0)
                return;
            if (index >= features.size()) {
                openBedrockRootMenu(player);
                return;
            }
            togglePlayerFeature(player, features.get(index));
            openBedrockToggleMenu(player);
        });
    }

    private void openBedrockShortcutMenu(Player player) {
        List<CommandShortcut> shortcuts = getEnabledShortcuts(player);
        List<FormsUtil.ButtonSpec> buttons = new ArrayList<>();
        for (CommandShortcut shortcut : shortcuts) {
            buttons.add(FormsUtil.ButtonSpec.ofText("§b▶ " + shortcut.label() + " を開く"));
        }
        buttons.add(FormsUtil.ButtonSpec.ofText("戻る"));
        FormsUtil.openSimpleForm(player, MENU_SHORTCUT_TITLE, buttons, index -> {
            if (index < 0)
                return;
            if (index >= shortcuts.size()) {
                openBedrockRootMenu(player);
                return;
            }
            executeShortcutWithDelay(player, shortcuts.get(index).command(), EXEC_DELAY_TICKS);
        });
    }

    private void openJavaRootMenu(Player player) {
        List<ToggleFeature> features = getVisibleUserFeatures();
        List<CommandShortcut> shortcuts = getEnabledShortcuts(player);
        Map<Integer, MenuAction> actions = new LinkedHashMap<>();

        ChestUI.Builder builder = ChestUI.builder()
                .title(MENU_TITLE)
                .size(JAVA_MENU_SIZE)
                .addButtonAt(ROOT_TOGGLE_SLOT, "§6個別トグル設定", Material.LEVER, "§7各機能の ON/OFF を切り替える")
                .addButtonAt(ROOT_SHORTCUT_SLOT, "§b有効機能UIショートカット", Material.BOOK, "§70.5秒後に対応UIコマンドを実行")
                .addButtonAt(13, "§b状態サマリー", Material.PAPER,
                        "§7ショートカット: " + shortcuts.size() + "\n§7トグル機能: " + features.size())
                .addButtonAt(ROOT_CLOSE_SLOT, "§7閉じる", Material.BARRIER, "§7GUIを閉じます");

        actions.put(ROOT_TOGGLE_SLOT, MenuAction.openToggleMenu());
        actions.put(ROOT_SHORTCUT_SLOT, MenuAction.openShortcutMenu());
        actions.put(ROOT_CLOSE_SLOT, MenuAction.close());

        builder.then((result, p) -> {
            if (!result.success || result.slot == null)
                return;
            MenuAction action = actions.get(result.slot);
            if (action == null)
                return;
            if (action.type() == MenuActionType.CLOSE) {
                ChestUI.closeMenu(p);
                return;
            }
            if (action.type() == MenuActionType.OPEN_TOGGLE_MENU) {
                openJavaToggleMenu(p);
                return;
            }
            if (action.type() == MenuActionType.OPEN_SHORTCUT_MENU) {
                openJavaShortcutMenu(p);
            }
        }).show(player);
    }

    private void openJavaToggleMenu(Player player) {
        List<ToggleFeature> features = getVisibleUserFeatures();
        Map<Integer, MenuAction> actions = new LinkedHashMap<>();
        ChestUI.Builder builder = ChestUI.builder()
                .title(MENU_TOGGLE_TITLE)
                .size(JAVA_MENU_SIZE)
                .addButtonAt(0, "§6個別トグル設定", Material.LEVER, "§7クリックで ON/OFF 切替")
                .addButtonAt(BACK_SLOT, "§e戻る", Material.ARROW, "§7トップへ戻る")
                .addButtonAt(CLOSE_SLOT, "§7閉じる", Material.BARRIER, "§7GUIを閉じます");

        actions.put(BACK_SLOT, MenuAction.backToRoot());
        actions.put(CLOSE_SLOT, MenuAction.close());

        int slot = 9;
        for (ToggleFeature feature : features) {
            if (slot > 44)
                break;
            boolean enabled = toggle.isEnabledFor(player.getUniqueId().toString(), feature.getKey());
            String label = (enabled ? "§a[ON] " : "§c[OFF] ") + feature.getDisplayName();
            String description = toggle.getFeatureDescription(feature.getKey());
            builder.addButtonAt(slot, label, feature.getIcon(),
                    "§7クリックで切り替え\n" + (description == null ? "" : "§8" + description));
            actions.put(slot, MenuAction.toggleFeature(feature.getKey()));
            slot++;
        }

        builder.then((result, p) -> {
            if (!result.success || result.slot == null)
                return;
            MenuAction action = actions.get(result.slot);
            if (action == null)
                return;
            if (action.type() == MenuActionType.BACK_TO_ROOT) {
                openJavaRootMenu(p);
                return;
            }
            if (action.type() == MenuActionType.CLOSE) {
                ChestUI.closeMenu(p);
                return;
            }
            if (action.type() != MenuActionType.TOGGLE_FEATURE)
                return;
            ToggleFeature feature = findFeatureByKey(features, action.featureKey());
            if (feature == null)
                return;
            togglePlayerFeature(p, feature);
            openJavaToggleMenu(p);
        }).show(player);
    }

    private void openJavaShortcutMenu(Player player) {
        List<CommandShortcut> shortcuts = getEnabledShortcuts(player);
        Map<Integer, MenuAction> actions = new LinkedHashMap<>();
        ChestUI.Builder builder = ChestUI.builder()
                .title(MENU_SHORTCUT_TITLE)
                .size(JAVA_MENU_SIZE)
                .addButtonAt(0, "§b有効機能UIショートカット", Material.BOOK, "§7押すとGUIを閉じて0.5秒後に実行")
                .addButtonAt(BACK_SLOT, "§e戻る", Material.ARROW, "§7トップへ戻る")
                .addButtonAt(CLOSE_SLOT, "§7閉じる", Material.BARRIER, "§7GUIを閉じます");

        actions.put(BACK_SLOT, MenuAction.backToRoot());
        actions.put(CLOSE_SLOT, MenuAction.close());

        if (shortcuts.isEmpty()) {
            builder.addButtonAt(22, "§cショートカット対象がありません", Material.BARRIER,
                    "§7有効状態かつUIコマンド対応の機能のみ表示されます");
        } else {
            int slot = 9;
            for (CommandShortcut shortcut : shortcuts) {
                if (slot > 44)
                    break;
                builder.addButtonAt(slot, "§b▶ " + shortcut.label() + " を開く", shortcut.icon(),
                        "§7押すとGUIを閉じて0.5秒後に実行");
                actions.put(slot, MenuAction.openCommand(shortcut.command()));
                slot++;
            }
        }

        builder.then((result, p) -> {
            if (!result.success || result.slot == null)
                return;
            MenuAction action = actions.get(result.slot);
            if (action == null)
                return;
            if (action.type() == MenuActionType.BACK_TO_ROOT) {
                openJavaRootMenu(p);
                return;
            }
            if (action.type() == MenuActionType.CLOSE) {
                ChestUI.closeMenu(p);
                return;
            }
            if (action.type() != MenuActionType.OPEN_COMMAND)
                return;
            ChestUI.closeMenu(p);
            executeShortcutWithDelay(p, action.command(), EXEC_DELAY_TICKS);
        }).show(player);
    }

    private ToggleFeature findFeatureByKey(List<ToggleFeature> features, String key) {
        for (ToggleFeature feature : features) {
            if (feature.getKey().equals(key))
                return feature;
        }
        return null;
    }

    private List<ToggleFeature> getVisibleUserFeatures() {
        return new ArrayList<>(toggle.getVisibleFeatures(false));
    }

    private List<CommandShortcut> getEnabledShortcuts(Player player) {
        List<CommandShortcut> shortcuts = new ArrayList<>();
        for (ToggleFeature feature : toggle.getFeatures()) {
            String command = FEATURE_SHORTCUT_COMMANDS.get(feature.getKey());
            if (command == null)
                continue;
            if (!toggle.isEnabledFor(player.getUniqueId().toString(), feature.getKey()))
                continue;
            shortcuts.add(new CommandShortcut(feature.getDisplayName(), feature.getIcon(), command));
        }
        return shortcuts;
    }

    private void togglePlayerFeature(Player player, ToggleFeature feature) {
        boolean current = toggle.isEnabledFor(player.getUniqueId().toString(), feature.getKey());
        if (!toggle.setEnabledFor(player.getUniqueId().toString(), feature.getKey(), !current))
            return;
        player.sendMessage((!current ? "§a『" + feature.getDisplayName() + "』を有効にしました" : "§c『" + feature.getDisplayName() + "』を無効にしました"));
    }

    private void executeShortcutWithDelay(Player player, String command, long delayTicks) {
        if (command == null || command.isBlank())
            return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline())
                return;
            if (!player.performCommand(command)) {
                player.sendMessage("§cコマンド実行に失敗しました: /" + command);
            }
        }, Math.max(1L, delayTicks));
    }

    private void craftBetterMenuAxe(ItemCombineModule.CombineMatch match) {
        if (!toggle.getGlobal(FEATURE_KEY))
            return;
        if (!match.first().isValid() || !match.second().isValid())
            return;
        Location center = match.center();
        if (center.getWorld() == null)
            return;
        match.consumeMatchedItems(1, 1);
        center.getWorld().dropItemNaturally(center, createBetterMenuAxe());
        center.getWorld().playSound(center, Sound.BLOCK_ANVIL_USE, 0.8F, 1.2F);
    }

    private ItemStack createBetterMenuAxe() {
        ItemStack stack = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = stack.getItemMeta();
        ComponentUtils.setDisplayName(meta, ITEM_NAME);
        ComponentUtils.setLore(meta,
                "§7右クリックで BetterSurvival GUI を開く",
                "§7有効機能のショートカットとトグル切替");
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "true");
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isBMenuNameTag(ItemStack stack) {
        if (stack == null || stack.getType() != Material.NAME_TAG || !stack.hasItemMeta())
            return false;
        String displayName = ComponentUtils.getDisplayName(stack.getItemMeta());
        if (displayName == null)
            return false;
        String plain = stripLegacyColors(displayName);
        return plain != null && plain.equalsIgnoreCase("b-menu");
    }

    private boolean isPlainWoodenAxe(ItemStack stack) {
        return stack != null && stack.getType() == Material.WOODEN_AXE && !isBetterMenuAxe(stack);
    }

    private boolean isBetterMenuAxe(ItemStack stack) {
        if (stack == null || stack.getType() != Material.WOODEN_AXE || !stack.hasItemMeta())
            return false;
        return stack.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING);
    }

    private String stripLegacyColors(String text) {
        if (text == null)
            return null;
        return text.replaceAll("(?i)§[0-9A-FK-ORX]", "");
    }

    private record CommandShortcut(String label, Material icon, String command) {
    }

    private enum MenuActionType {
        OPEN_COMMAND,
        OPEN_TOGGLE_MENU,
        OPEN_SHORTCUT_MENU,
        TOGGLE_FEATURE,
        BACK_TO_ROOT,
        CLOSE
    }

    private record MenuAction(MenuActionType type, String command, String featureKey) {
        private static MenuAction openCommand(String command) {
            return new MenuAction(MenuActionType.OPEN_COMMAND, command, null);
        }

        private static MenuAction openToggleMenu() {
            return new MenuAction(MenuActionType.OPEN_TOGGLE_MENU, null, null);
        }

        private static MenuAction openShortcutMenu() {
            return new MenuAction(MenuActionType.OPEN_SHORTCUT_MENU, null, null);
        }

        private static MenuAction toggleFeature(String featureKey) {
            return new MenuAction(MenuActionType.TOGGLE_FEATURE, null, featureKey);
        }

        private static MenuAction backToRoot() {
            return new MenuAction(MenuActionType.BACK_TO_ROOT, null, null);
        }

        private static MenuAction close() {
            return new MenuAction(MenuActionType.CLOSE, null, null);
        }
    }
}
