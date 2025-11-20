package org.pexserver.koukunn.bettersurvival;

import org.bukkit.plugin.java.JavaPlugin;
import org.pexserver.koukunn.bettersurvival.Core.Command.CommandManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;

public final class Loader extends JavaPlugin {

    private CommandManager commandManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        // マネージャーを初期化
        commandManager = new CommandManager(this);
        configManager = new ConfigManager(this);
        // ConfigManager を初期化（PEXConfig フォルダを作成）

        // コマンドを登録
        registerCommands();


        getLogger().info("Better Survival Plugin が有効になりました");
    }


    /**
     * すべてのコマンドを登録
     */
    private void registerCommands() {
        // ヘルプコマンド
        commandManager.register(new org.pexserver.koukunn.bettersurvival.Commands.help.HelpCommand(commandManager));
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

    @Override
    public void onDisable() {
        
        getLogger().info("Better Survival Plugin が無効になりました");
    }
}

