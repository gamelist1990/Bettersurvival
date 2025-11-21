package org.pexserver.koukunn.bettersurvival;

import org.bukkit.plugin.java.JavaPlugin;
import org.pexserver.koukunn.bettersurvival.Core.Command.CommandManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;

public final class Loader extends JavaPlugin {

    private CommandManager commandManager;
    private ConfigManager configManager;
    private org.pexserver.koukunn.bettersurvival.Modules.ToggleModule toggleModule;

    @Override
    public void onEnable() {
        // マネージャーを初期化
        commandManager = new CommandManager(this);
        configManager = new ConfigManager(this);
        // ConfigManager を初期化（PEXConfig フォルダを作成）

        // コマンドを登録
        registerCommands();

        // Toggle module (GUI)
        toggleModule = new org.pexserver.koukunn.bettersurvival.Modules.ToggleModule(this);
        getServer().getPluginManager().registerEvents(new org.pexserver.koukunn.bettersurvival.Modules.ToggleListener(toggleModule), this);

        // TreeMine モジュール登録
        org.pexserver.koukunn.bettersurvival.Modules.Feature.TreeMine.TreeMineModule treemine = new org.pexserver.koukunn.bettersurvival.Modules.Feature.TreeMine.TreeMineModule(toggleModule);
        getServer().getPluginManager().registerEvents(treemine, this);
        getServer().getPluginManager().registerEvents(new org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoFeed.AutoFeedModule(toggleModule), this);
        // AnythingFeed module (allow edible items on non-breedable mobs)
        getServer().getPluginManager().registerEvents(new org.pexserver.koukunn.bettersurvival.Modules.Feature.AnythingFeed.AnythingFeedModule(toggleModule), this);
        // AutoPlant モジュール登録
        getServer().getPluginManager().registerEvents(new org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule(toggleModule), this);
        // OreMine モジュール登録
        org.pexserver.koukunn.bettersurvival.Modules.Feature.OreMine.OreMineModule oremine = new org.pexserver.koukunn.bettersurvival.Modules.Feature.OreMine.OreMineModule(toggleModule);
        getServer().getPluginManager().registerEvents(oremine, this);
        // ChestSort モジュール登録
        org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestSort.ChestSortModule chestSort = new org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestSort.ChestSortModule(toggleModule);
        getServer().getPluginManager().registerEvents(chestSort, this);
        // Toggle 機能として登録
        toggleModule.registerFeature(new org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.ToggleFeature("treemine", "TreeMine", "木を一括で伐採・破壊します(スニーク必須)", org.bukkit.Material.DIAMOND_AXE));
        toggleModule.registerFeature(new org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.ToggleFeature("oremine", "OreMine", "近接する鉱石を一括で破壊します（スニーク必須）", org.bukkit.Material.DIAMOND_PICKAXE));
        toggleModule.registerFeature(new org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.ToggleFeature("autofeed", "AutoFeed", "餌を与えると周辺の動物にも自動で餌を与えます", org.bukkit.Material.WHEAT));
        toggleModule.registerFeature(new org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.ToggleFeature("anythingfeed", "AnythingFeed", "非繁殖動物に任意の食料で反応するようにします", org.bukkit.Material.APPLE));
        toggleModule.registerFeature(new org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.ToggleFeature("autoplant", "AutoPlant", "オフハンドに植えたいアイテムを持ちながら耕した土の近くに行くと自動で植え・収穫します", org.bukkit.Material.WHEAT_SEEDS));
        toggleModule.registerFeature(new org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.ToggleFeature("chestsort", "ChestSort", "スニーク+木の棒でチェスト内を整理します", org.bukkit.Material.STICK));
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
        if (!toggleModule.hasGlobal("chestsort")) {
            toggleModule.setGlobal("chestsort", true);
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
        commandManager.register(new org.pexserver.koukunn.bettersurvival.Commands.help.HelpCommand(commandManager));
        // Toggle command
        commandManager.register(new org.pexserver.koukunn.bettersurvival.Commands.toggle.ToggleCommand(this));
        // 他のコマンドはここに追加できます
    }

    /**
     * CommandManager を取得
     * @return CommandManager インスタンス
     */
    public CommandManager getCommandManager() {
        return commandManager;
    }

  
    public org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager getConfigManager() {
        return configManager;
    }

    public org.pexserver.koukunn.bettersurvival.Modules.ToggleModule getToggleModule() {
        return toggleModule;
    }

    @Override
    public void onDisable() {
        
        getLogger().info("Better Survival Plugin が無効になりました");
    }
}

