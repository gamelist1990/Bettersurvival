package org.pexserver.koukunn.bettersurvival.Core.Command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.lang.reflect.Field;
import java.util.*;

/**
 * コマンドを動的に登録・管理するマネージャー
 * Paper 1.21+ 対応
 */
public class CommandManager {

    private final Plugin plugin;
    private final Map<String, BaseCommand> commands = new HashMap<>();
    private final CommandMap commandMap;
    private CommandBlockManager blockManager;

    public CommandManager(Plugin plugin) {
        this.plugin = plugin;
        this.commandMap = getCommandMap();
    }

    public void setBlockManager(CommandBlockManager blockManager) {
        this.blockManager = blockManager;
    }

    /**
     * Bukkit の CommandMap を取得
     * @return CommandMap インスタンス
     */
    private CommandMap getCommandMap() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        } catch (Exception e) {
            plugin.getLogger().warning("CommandMap の取得に失敗しました: " + e.getMessage());
            return null;
        }
    }

    /**
     * コマンドクラスを登録
     * @param commandClass BaseCommand を継承したクラス
     */
    public void register(Class<? extends BaseCommand> commandClass) {
        try {
            BaseCommand command = commandClass.getDeclaredConstructor().newInstance();
            registerCommand(command);
        } catch (Exception e) {
            plugin.getLogger().warning("コマンド登録失敗: " + commandClass.getName());
            e.printStackTrace();
        }
    }

    /**
     * コマンドインスタンスを登録
     * @param command BaseCommand インスタンス
     */
    public void register(BaseCommand command) {
        registerCommand(command);
    }

    /**
     * 複数のコマンドクラスを一括登録
     * @param commandClasses コマンドクラスの配列
     */
    @SafeVarargs
    public final void registerAll(Class<? extends BaseCommand>... commandClasses) {
        for (Class<? extends BaseCommand> commandClass : commandClasses) {
            register(commandClass);
        }
    }

    /**
     * 複数のコマンドインスタンスを一括登録
     * @param commands コマンドインスタンスの配列
     */
    public void registerAll(BaseCommand... commands) {
        for (BaseCommand command : commands) {
            register(command);
        }
    }

    /**
     * コマンドを内部的に登録
     */
    private void registerCommand(BaseCommand command) {
        String commandName = command.getName();
        commands.put(commandName.toLowerCase(), command);

        // Bukkit CommandMap に登録
        if (commandMap != null) {
            commandMap.register(plugin.getName(), new CommandWrapper(commandName, command, plugin));
        }

        plugin.getLogger().info("コマンド登録: /" + commandName);
    }

    /**
     * コマンドを取得
     * @param name コマンド名
     * @return コマンドまたはnull
     */
    public BaseCommand getCommand(String name) {
        return commands.get(name.toLowerCase());
    }

    /**
     * すべてのコマンドを取得
     * @return コマンドマップ
     */
    public Map<String, BaseCommand> getCommands() {
        return new HashMap<>(commands);
    }

    /**
     * Bukkit Command のラッパークラス
     */
    private class CommandWrapper extends Command {
        private final BaseCommand command;
        private final Plugin plugin;

        public CommandWrapper(String name, BaseCommand command, Plugin plugin) {
            super(name);
            this.command = command;
            this.plugin = plugin;
            this.setDescription(command.getDescription());
            this.setUsage(command.getUsage());
        }

        @Override
        public boolean testPermissionSilent(CommandSender sender) {
            // コマンドが無効な場合は非表示
            if (!command.isEnabled()) {
                return false;
            }
            // グローバル無効化に該当する場合は非表示（非表示化）
            if (blockManager != null) {
                try {
                    if (blockManager.matches(sender, this.getName())) {
                        return false;  // 権限がない場合、コマンドを完全に非表示にする（Lumplus方式）
                    }
                } catch (Exception ignored) {}
            }
            // Brigadier / Bukkit の候補表示などはこのメソッドで権限チェックされることがあるため
            // カスタム権限 (getPermission) がある場合はそれを優先し、無ければ PermissionLevel を利用する
            PermissionLevel permLevel = command.getPermissionLevel();
            // 権限チェック - 権限がない場合はコマンドを完全に非表示にする（Lumplus方式）
            return permLevel.hasAccess(sender, command.getPermission());
        }

        @Override
        public boolean testPermission(CommandSender sender) {
            // Lumplus 方式: 権限がない場合、ユーザーには存在しないコマンドとして扱う
            // この結果は help コマンドなどで使用される
            return testPermissionSilent(sender);
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            // 権限チェック（PermissionLevel + カスタム権限）
            PermissionLevel permLevel = command.getPermissionLevel();
            if (!permLevel.hasAccess(sender, command.getPermission())) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§c権限がありません: " + permLevel.getDescription()));
                return true;
            }

                // グローバル無効化チェック
                if (blockManager != null) {
                    try {
                        if (blockManager.matches(sender, this.getName())) {
                            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cこのコマンドは無効化されています"));
                            return true;
                        }
                    } catch (Exception ignored) {}
                }

                // コマンド実行
                try {
                    return command.execute(sender, args);
                } catch (Exception e) {
                    plugin.getLogger().warning("コマンド実行エラー: " + command.getName());
                    e.printStackTrace();
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cコマンド実行中にエラーが発生しました"));
                    return true;
                }
            }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            // 権限チェック
            PermissionLevel permLevel = command.getPermissionLevel();
            if (!permLevel.hasAccess(sender, command.getPermission())) {
                return new ArrayList<>();
            }

            // コマンドのTab補完を呼び出し
            return command.getTabCompletions(sender, args);
        }
    }
}
