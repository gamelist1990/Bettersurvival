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
        // OreMine モジュール登録
        org.pexserver.koukunn.bettersurvival.Modules.Feature.OreMine.OreMineModule oremine = new org.pexserver.koukunn.bettersurvival.Modules.Feature.OreMine.OreMineModule(toggleModule);
        getServer().getPluginManager().registerEvents(oremine, this);
        // Toggle 機能として登録
        toggleModule.registerFeature(new org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.ToggleFeature("treemine", "TreeMine", "木を一括で伐採・破壊します(スニーク必須)", org.bukkit.Material.DIAMOND_AXE));
        toggleModule.registerFeature(new org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.ToggleFeature("oremine", "OreMine", "近接する鉱石を一括で破壊します（スニーク必須）", org.bukkit.Material.DIAMOND_PICKAXE));
        toggleModule.registerFeature(new org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.ToggleFeature("autofeed", "AutoFeed", "餌を与えると周辺の動物にも自動で餌を与えます", org.bukkit.Material.WHEAT));
        if (!toggleModule.hasGlobal("treemine")) {
            toggleModule.setGlobal("treemine", true);
        }
        if (!toggleModule.hasGlobal("oremine")) {
            toggleModule.setGlobal("oremine", true);
        }
        if (!toggleModule.hasGlobal("autofeed")) {
            toggleModule.setGlobal("autofeed", true);
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

