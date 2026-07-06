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
    private static final String MENU_TITLE = "§8✦ §bBetterSurvival §8✦";
    private static final String MENU_TOGGLE_TITLE = "§8✦ §6個別トグル設定 §8✦";
    private static final String MENU_SHORTCUT_TITLE = "§8✦ §b有効機能ショートカット §8✦";
    private static final String ITEM_NAME = "§b§lBetterSurvival §aGUI";
    private static final int JAVA_MENU_SIZE = 54;
    private static final Material BORDER_ICON = Material.GRAY_STAINED_GLASS_PANE;
    private static final String BORDER_LABEL = "§7 ";

    private static final int ROOT_TITLE_SLOT = 4;
    private static final int ROOT_STATUS_SLOT = 13;
    private static final int ROOT_TOGGLE_SLOT = 20;
    private static final int ROOT_SHORTCUT_SLOT = 24;
    private static final int ROOT_CLOSE_SLOT = 49;

    private static final int SUB_TITLE_SLOT = 4;
    private static final int CONTENT_SLOT_START = 9;
    private static final int CONTENT_SLOT_END = 44;
    private static final int CONTENT_PAGE_SIZE = CONTENT_SLOT_END - CONTENT_SLOT_START + 1;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int BACK_SLOT = 47;
    private static final int PAGE_INFO_SLOT = 49;
    private static final int CLOSE_SLOT = 51;
    private static final int NEXT_PAGE_SLOT = 53;

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
                FormsUtil.ButtonSpec.ofText("§6個別トグル設定"),
                FormsUtil.ButtonSpec.ofText("§b有効機能UIショートカット"),
                FormsUtil.ButtonSpec.ofText("§c閉じる")
        );
        String content = "§b§lBetterSurvival GUI\n§7" + player.getName() + " さん、ようこそ\n\n"
                + "§e有効なトグル機能: §f" + features.size() + "\n"
                + "§e利用可能なショートカット: §f" + shortcuts.size();
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
            String state = enabled ? "§a§l[ON] §f" : "§c§l[OFF] §f";
            buttons.add(FormsUtil.ButtonSpec.ofText(state + feature.getDisplayName()));
        }
        buttons.add(FormsUtil.ButtonSpec.ofText("§e戻る"));
        FormsUtil.openSimpleForm(player, MENU_TOGGLE_TITLE, "§7クリックで各機能の ON/OFF を切り替えます", buttons, index -> {
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
            buttons.add(FormsUtil.ButtonSpec.ofText("§b▶ §f" + shortcut.label() + " を開く"));
        }
        buttons.add(FormsUtil.ButtonSpec.ofText("§e戻る"));
        String content = shortcuts.isEmpty()
                ? "§7有効状態かつUIコマンド対応の機能のみ表示されます"
                : "§7押すと0.5秒後に対応UIコマンドを実行します";
        FormsUtil.openSimpleForm(player, MENU_SHORTCUT_TITLE, content, buttons, index -> {
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
                .size(JAVA_MENU_SIZE);
        fillBorder(builder);
        builder.addButtonAt(ROOT_TITLE_SLOT, "§b§l✦ BetterSurvival ✦", Material.NETHER_STAR,
                "§7ようこそ、§f" + player.getName() + " §7さん\n§7右クリックでいつでも呼び出せます");
        builder.addPlayerHeadAt(ROOT_STATUS_SLOT, "§e§l状態サマリー", player,
                "§7有効なトグル機能: §a" + features.size()
                        + "\n§7利用可能なショートカット: §b" + shortcuts.size());
        builder.addButtonAt(ROOT_TOGGLE_SLOT, "§6§l個別トグル設定", Material.LEVER,
                "§7各機能の ON/OFF を切り替える\n\n§e▶ クリックで開く");
        builder.addButtonAt(ROOT_SHORTCUT_SLOT, "§b§l有効機能ショートカット", Material.BOOK,
                "§70.5秒後に対応UIコマンドを実行\n\n§e▶ クリックで開く");
        builder.addButtonAt(ROOT_CLOSE_SLOT, "§c§l閉じる", Material.BARRIER, "§7GUIを閉じます");

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
                playClick(p, 0.8F);
                ChestUI.closeMenu(p);
                return;
            }
            if (action.type() == MenuActionType.OPEN_TOGGLE_MENU) {
                playClick(p, 1.0F);
                openJavaToggleMenu(p, 0);
                return;
            }
            if (action.type() == MenuActionType.OPEN_SHORTCUT_MENU) {
                playClick(p, 1.0F);
                openJavaShortcutMenu(p, 0);
            }
        }).show(player);
    }

    private void openJavaToggleMenu(Player player, int page) {
        List<ToggleFeature> features = getVisibleUserFeatures();
        int maxPage = features.isEmpty() ? 0 : (features.size() - 1) / CONTENT_PAGE_SIZE;
        int currentPage = Math.max(0, Math.min(page, maxPage));

        Map<Integer, MenuAction> actions = new LinkedHashMap<>();
        ChestUI.Builder builder = ChestUI.builder()
                .title(MENU_TOGGLE_TITLE)
                .size(JAVA_MENU_SIZE);
        fillBorder(builder);
        builder.addButtonAt(SUB_TITLE_SLOT, "§6§l⚙ 個別トグル設定 ⚙", Material.LEVER,
                "§7クリックで各機能の ON/OFF を切り替えます\n§7設定は自動的に保存されます");

        int startIndex = currentPage * CONTENT_PAGE_SIZE;
        for (int slot = CONTENT_SLOT_START; slot <= CONTENT_SLOT_END; slot++) {
            int featureIndex = startIndex + (slot - CONTENT_SLOT_START);
            if (featureIndex >= features.size())
                break;
            ToggleFeature feature = features.get(featureIndex);
            boolean enabled = toggle.isEnabledFor(player.getUniqueId().toString(), feature.getKey());
            String label = (enabled ? "§a§l[ON] §f" : "§c§l[OFF] §f") + feature.getDisplayName();
            String description = toggle.getFeatureDescription(feature.getKey());
            String lore = (enabled ? "§7状態: §a有効" : "§7状態: §c無効")
                    + (description == null || description.isEmpty() ? "" : "\n§8" + description)
                    + "\n\n§eクリックで切り替え";
            builder.addButtonAt(slot, label, feature.getIcon(), lore);
            actions.put(slot, MenuAction.toggleFeature(feature.getKey()));
        }
        if (features.isEmpty()) {
            builder.addButtonAt((CONTENT_SLOT_START + CONTENT_SLOT_END) / 2, "§7切り替え可能な機能がありません",
                    Material.BARRIER, "§7対象の機能が追加されるとここに表示されます");
        }

        builder.addButtonAt(BACK_SLOT, "§e§l戻る", Material.ARROW, "§7トップへ戻る");
        builder.addButtonAt(CLOSE_SLOT, "§c§l閉じる", Material.BARRIER, "§7GUIを閉じます");
        builder.addButtonAt(PAGE_INFO_SLOT, "§dページ §f" + (currentPage + 1) + "§7/§f" + (maxPage + 1), Material.PAPER,
                "§7表示中の機能数: §f" + features.size());
        addPageNavigation(builder, actions, currentPage, maxPage);

        actions.put(BACK_SLOT, MenuAction.backToRoot());
        actions.put(CLOSE_SLOT, MenuAction.close());

        builder.then((result, p) -> {
            if (!result.success || result.slot == null)
                return;
            MenuAction action = actions.get(result.slot);
            if (action == null)
                return;
            if (action.type() == MenuActionType.BACK_TO_ROOT) {
                playClick(p, 1.0F);
                openJavaRootMenu(p);
                return;
            }
            if (action.type() == MenuActionType.CLOSE) {
                playClick(p, 0.8F);
                ChestUI.closeMenu(p);
                return;
            }
            if (action.type() == MenuActionType.PREV_PAGE) {
                playClick(p, 0.8F);
                openJavaToggleMenu(p, currentPage - 1);
                return;
            }
            if (action.type() == MenuActionType.NEXT_PAGE) {
                playClick(p, 1.2F);
                openJavaToggleMenu(p, currentPage + 1);
                return;
            }
            if (action.type() != MenuActionType.TOGGLE_FEATURE)
                return;
            ToggleFeature feature = findFeatureByKey(features, action.featureKey());
            if (feature == null)
                return;
            togglePlayerFeature(p, feature);
            boolean nowEnabled = toggle.isEnabledFor(p.getUniqueId().toString(), feature.getKey());
            playClick(p, nowEnabled ? 1.2F : 0.8F);
            openJavaToggleMenu(p, currentPage);
        }).show(player);
    }

    private void openJavaShortcutMenu(Player player, int page) {
        List<CommandShortcut> shortcuts = getEnabledShortcuts(player);
        int maxPage = shortcuts.isEmpty() ? 0 : (shortcuts.size() - 1) / CONTENT_PAGE_SIZE;
        int currentPage = Math.max(0, Math.min(page, maxPage));

        Map<Integer, MenuAction> actions = new LinkedHashMap<>();
        ChestUI.Builder builder = ChestUI.builder()
                .title(MENU_SHORTCUT_TITLE)
                .size(JAVA_MENU_SIZE);
        fillBorder(builder);
        builder.addButtonAt(SUB_TITLE_SLOT, "§b§l有効機能ショートカット", Material.BOOK,
                "§7押すとGUIを閉じて0.5秒後に対応UIコマンドを実行します");

        if (shortcuts.isEmpty()) {
            builder.addButtonAt((CONTENT_SLOT_START + CONTENT_SLOT_END) / 2, "§cショートカット対象がありません",
                    Material.BARRIER, "§7有効状態かつUIコマンド対応の機能のみ表示されます");
        } else {
            int startIndex = currentPage * CONTENT_PAGE_SIZE;
            for (int slot = CONTENT_SLOT_START; slot <= CONTENT_SLOT_END; slot++) {
                int shortcutIndex = startIndex + (slot - CONTENT_SLOT_START);
                if (shortcutIndex >= shortcuts.size())
                    break;
                CommandShortcut shortcut = shortcuts.get(shortcutIndex);
                builder.addButtonAt(slot, "§b▶ §f" + shortcut.label() + " を開く", shortcut.icon(),
                        "§7押すとGUIを閉じて0.5秒後に実行されます");
                actions.put(slot, MenuAction.openCommand(shortcut.command()));
            }
        }

        builder.addButtonAt(BACK_SLOT, "§e§l戻る", Material.ARROW, "§7トップへ戻る");
        builder.addButtonAt(CLOSE_SLOT, "§c§l閉じる", Material.BARRIER, "§7GUIを閉じます");
        builder.addButtonAt(PAGE_INFO_SLOT, "§dページ §f" + (currentPage + 1) + "§7/§f" + (maxPage + 1), Material.PAPER,
                "§7表示中のショートカット数: §f" + shortcuts.size());
        addPageNavigation(builder, actions, currentPage, maxPage);

        actions.put(BACK_SLOT, MenuAction.backToRoot());
        actions.put(CLOSE_SLOT, MenuAction.close());

        builder.then((result, p) -> {
            if (!result.success || result.slot == null)
                return;
            MenuAction action = actions.get(result.slot);
            if (action == null)
                return;
            if (action.type() == MenuActionType.BACK_TO_ROOT) {
                playClick(p, 1.0F);
                openJavaRootMenu(p);
                return;
            }
            if (action.type() == MenuActionType.CLOSE) {
                playClick(p, 0.8F);
                ChestUI.closeMenu(p);
                return;
            }
            if (action.type() == MenuActionType.PREV_PAGE) {
                playClick(p, 0.8F);
                openJavaShortcutMenu(p, currentPage - 1);
                return;
            }
            if (action.type() == MenuActionType.NEXT_PAGE) {
                playClick(p, 1.2F);
                openJavaShortcutMenu(p, currentPage + 1);
                return;
            }
            if (action.type() != MenuActionType.OPEN_COMMAND)
                return;
            playClick(p, 1.0F);
            ChestUI.closeMenu(p);
            executeShortcutWithDelay(p, action.command(), EXEC_DELAY_TICKS);
        }).show(player);
    }

    private void fillBorder(ChestUI.Builder builder) {
        for (int slot = 0; slot < JAVA_MENU_SIZE; slot++) {
            builder.addButtonAt(slot, BORDER_LABEL, BORDER_ICON, "");
        }
    }

    private void addPageNavigation(ChestUI.Builder builder, Map<Integer, MenuAction> actions, int currentPage, int maxPage) {
        if (currentPage > 0) {
            builder.addButtonAt(PREV_PAGE_SLOT, "§e← 前ページ", Material.ARROW, "§7前のページを表示します");
            actions.put(PREV_PAGE_SLOT, MenuAction.prevPage());
        } else {
            builder.addButtonAt(PREV_PAGE_SLOT, "§8← 前ページ", BORDER_ICON, "§7これ以上前のページはありません");
        }
        if (currentPage < maxPage) {
            builder.addButtonAt(NEXT_PAGE_SLOT, "§e次ページ →", Material.ARROW, "§7次のページを表示します");
            actions.put(NEXT_PAGE_SLOT, MenuAction.nextPage());
        } else {
            builder.addButtonAt(NEXT_PAGE_SLOT, "§8次ページ →", BORDER_ICON, "§7これ以上次のページはありません");
        }
    }

    private void playClick(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, pitch);
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
        PREV_PAGE,
        NEXT_PAGE,
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

        private static MenuAction prevPage() {
            return new MenuAction(MenuActionType.PREV_PAGE, null, null);
        }

        private static MenuAction nextPage() {
            return new MenuAction(MenuActionType.NEXT_PAGE, null, null);
        }

        private static MenuAction close() {
            return new MenuAction(MenuActionType.CLOSE, null, null);
        }
    }
}
