package org.pexserver.koukunn.bettersurvival;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;
import org.pexserver.koukunn.bettersurvival.Core.Command.CommandManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleListener;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.TreeMine.TreeMineModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoFeed.AutoFeedModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.AnythingFeed.AnythingFeedModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.OreMine.OreMineModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestSort.ChestSortModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopModule;
import org.pexserver.koukunn.bettersurvival.Commands.help.HelpCommand;
import org.pexserver.koukunn.bettersurvival.Commands.toggle.ToggleCommand;
import org.pexserver.koukunn.bettersurvival.Commands.chest.ChestCommand;
import org.pexserver.koukunn.bettersurvival.Commands.rename.RenameCommand;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.ToggleFeature;

public final class Loader extends JavaPlugin {

    private CommandManager commandManager;
    private ConfigManager configManager;
    private ToggleModule toggleModule;

    @Override
    public void onEnable() {
        // マネージャーを初期化
        commandManager = new CommandManager(this);
        configManager = new ConfigManager(this);
        // ConfigManager を初期化（PEXConfig フォルダを作成）

        // コマンドを登録
        registerCommands();

        // Toggle module (GUI)
        toggleModule = new ToggleModule(this);
        getServer().getPluginManager().registerEvents(new ToggleListener(toggleModule), this);

        // TreeMine モジュール登録
        TreeMineModule treemine = new TreeMineModule(toggleModule);
        getServer().getPluginManager().registerEvents(treemine, this);
        getServer().getPluginManager().registerEvents(new AutoFeedModule(toggleModule), this);
        // AnythingFeed module (allow edible items on non-breedable mobs)
        getServer().getPluginManager().registerEvents(new AnythingFeedModule(toggleModule), this);
        // AutoPlant モジュール登録
        getServer().getPluginManager().registerEvents(new AutoPlantModule(toggleModule), this);
        // OreMine モジュール登録
        OreMineModule oremine = new OreMineModule(toggleModule);
        getServer().getPluginManager().registerEvents(oremine, this);
        // ChestSort モジュール登録
        ChestSortModule chestSort = new ChestSortModule(toggleModule);
        getServer().getPluginManager().registerEvents(chestSort, this);
        // ChestLock module registration
        ChestLockModule chestLock = new ChestLockModule(toggleModule, configManager);
        getServer().getPluginManager().registerEvents(chestLock, this);
        // ChestShop module registration
        ChestShopModule chestShop = new ChestShopModule(toggleModule, configManager, chestLock);
        getServer().getPluginManager().registerEvents(chestShop, this);
        // FenceLeash module registration (allow placing a leash knot on fence by
        // right-clicking while holding a lead)
        // Toggle 機能として登録
        toggleModule.registerFeature(
                new ToggleFeature("treemine", "TreeMine", "木を一括で伐採・破壊します(スニーク必須)", Material.DIAMOND_AXE));
        toggleModule.registerFeature(
                new ToggleFeature("oremine", "OreMine", "近接する鉱石を一括で破壊します（スニーク必須）", Material.DIAMOND_PICKAXE));
        toggleModule
                .registerFeature(new ToggleFeature("autofeed", "AutoFeed", "餌を与えると周辺の動物にも自動で餌を与えます", Material.WHEAT));
        toggleModule.registerFeature(
                new ToggleFeature("anythingfeed", "AnythingFeed", "非繁殖動物に任意の食料で反応するようにします", Material.APPLE));
        toggleModule.registerFeature(new ToggleFeature("autoplant", "AutoPlant",
                "オフハンドに植えたいアイテムを持ちながら耕した土の近くに行くと自動で植え・収穫します", Material.WHEAT_SEEDS));
        toggleModule.registerFeature(
                new ToggleFeature("chestlock", "ChestLock", "チェスト保護を有効/無効にします（破壊・移動・取得を制限）", Material.CHEST, false));
        toggleModule.registerFeature(
                new ToggleFeature("chestshop", "ChestShop", "看板でチェストをショップ化します(>>Shop 名前)", Material.OAK_SIGN, false));
        toggleModule
                .registerFeature(new ToggleFeature("chestsort", "ChestSort", "スニーク+木の棒でチェスト内を整理します", Material.STICK));
        if (!toggleModule.hasGlobal("treemine")) {
            toggleModule.setGlobal("treemine", true);
        }
        if (!toggleModule.hasGlobal("oremine")) {
            toggleModule.setGlobal("oremine", true);
        }
        if (!toggleModule.hasGlobal("autofeed")) {
            toggleModule.setGlobal("autofeed", true);
        }
        if (!toggleModule.hasGlobal("anythingfeed")) {
            toggleModule.setGlobal("anythingfeed", true);
        }
        if (!toggleModule.hasGlobal("autoplant")) {
            toggleModule.setGlobal("autoplant", true);
        }
        if (!toggleModule.hasGlobal("chestlock")) {
            toggleModule.setGlobal("chestlock", true);
        }
        if (!toggleModule.hasGlobal("chestsort")) {
            toggleModule.setGlobal("chestsort", true);
        }
        if (!toggleModule.hasGlobal("chestshop")) {
            toggleModule.setGlobal("chestshop", true);
        }
        if (!toggleModule.hasGlobal("oremine")) {
            toggleModule.setGlobal("oremine", true);
        }

        getLogger().info("Better Survival Plugin が有効になりました");
    }

    /**
     * すべてのコマンドを登録
     */
    private void registerCommands() {
        // ヘルプコマンド
        commandManager.register(new HelpCommand(commandManager));
        // Toggle command
        commandManager.register(new ToggleCommand(this));
        // Chest command (chest lock & member management)
        commandManager.register(new ChestCommand(this));
        // Rename command: 手持ちアイテムの名前変更
        commandManager.register(new RenameCommand());
        // 他のコマンドはここに追加できます
    }

    /**
     * CommandManager を取得
     * 
     * @return CommandManager インスタンス
     */
    public CommandManager getCommandManager() {
        return commandManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ToggleModule getToggleModule() {
        return toggleModule;
    }

    @Override
    public void onDisable() {

        getLogger().info("Better Survival Plugin が無効になりました");
    }
}
