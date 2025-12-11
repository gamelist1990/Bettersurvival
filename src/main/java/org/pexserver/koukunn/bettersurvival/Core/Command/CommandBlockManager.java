package org.pexserver.koukunn.bettersurvival.Core.Command;

import org.bukkit.command.CommandSender;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.help.HelpTopic;
import java.lang.reflect.Field;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Globally disable commands by pattern and target permission level.
 */
public class CommandBlockManager {

    private final ConfigManager configManager;
    private final Loader plugin;
    private final Map<String, PermissionLevel> blocks = new HashMap<>();
    private final String configPath = "BlockCommand/disabled_commands.json";
    public CommandBlockManager(Loader plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        load();
    }

    private static Pattern wildcardToPattern(String wildcard) {
        // escape regex special chars except * then convert * -> .* and use case-insensitive
        StringBuilder sb = new StringBuilder();
        for (char c : wildcard.toCharArray()) {
            if (c == '*') sb.append(".*");
            else if (".\\+?^${}()|[]".indexOf(c) != -1) sb.append('\\').append(c);
            else sb.append(c);
        }
        return Pattern.compile("^" + sb.toString() + "$", Pattern.CASE_INSENSITIVE);
    }

    /**
     * ワイルドカードパターンがコマンド名にマッチするかチェック
     * 例：
     * - "floodgate*" → "floodgate", "floodgate:xxxxx", "floodgatexxx" にマッチ
     * - "bukkit*" → "bukkit:reload", "bukkit:reload:ask", "bukkitxxx" にマッチ
     * - "search" → "search" にマッチ（完全一致）
     * - "search*" → "search", "searchxxx", "search:lang" にマッチ
     */
    private static boolean matchesWildcard(String pattern, String command) {
        String patternLower = pattern.toLowerCase();
        String commandLower = command.toLowerCase();

        // Case 1: パターンに * が含まれない場合は完全一致
        if (!patternLower.contains("*")) {
            return patternLower.equals(commandLower);
        }

        // Case 2: パターンが "prefix*" の場合、より柔軟なマッチング
        if (patternLower.endsWith("*")) {
            String prefix = patternLower.substring(0, patternLower.length() - 1);

            // "prefix*" は以下のすべてにマッチ：
            // 1. "prefix" 完全一致
            if (commandLower.equals(prefix)) {
                return true;
            }
            // 2. "prefix:xxx" 形式（コロン区切り）
            if (commandLower.startsWith(prefix + ":")) {
                return true;
            }
            // 3. "prefixXXX" 形式（直接続き）
            if (commandLower.startsWith(prefix)) {
                return true;
            }
        }

        // Case 3: 通常のワイルドカードマッチング（正規表現）
        Pattern p = wildcardToPattern(pattern);
        return p.matcher(command).matches();
    }

    /**
     * HelpMap からすべての登録コマンドを取得
     * @return コマンド名のリスト
     */
    private synchronized List<String> getAllRegisteredCommands() {
        List<String> commands = new ArrayList<>();
        try {
            Collection<HelpTopic> helpTopics = Bukkit.getHelpMap().getHelpTopics();
            
            for (HelpTopic topic : helpTopics) {
                String commandName = topic.getName();
                if (commandName == null || commandName.isEmpty()) continue;
                
                // コマンド名から「/」を除去
                if (commandName.startsWith("/")) {
                    commandName = commandName.substring(1);
                }
                
                commands.add(commandName);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("getAllRegisteredCommands error: " + ex.getMessage());
        }
        return commands;
    }

    public synchronized boolean add(String pattern, PermissionLevel level) {
        if (blocks.containsKey(pattern)) return false; // avoid duplicates
        // Special handling: if pattern equals "Vanilla*", add all server (non-plugin) commands
        if (pattern.equalsIgnoreCase("Vanilla*") || pattern.equalsIgnoreCase("vanilla*")) {
            try {
                // get CommandMap and known commands
                Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
                field.setAccessible(true);
                CommandMap commandMap = (CommandMap) field.get(Bukkit.getServer());
                Field knownField = commandMap.getClass().getDeclaredField("knownCommands");
                knownField.setAccessible(true);
                Map<String, Command> known = (Map<String, Command>) knownField.get(commandMap);
                for (Map.Entry<String, Command> e : known.entrySet()) {
                    if (e.getValue() instanceof PluginCommand) continue; // skip plugin commands
                    String name = e.getKey();
                    if (!blocks.containsKey(name)) blocks.put(name, level);
                }
                save();
                return true;
            } catch (Exception ex) {
                // fallback to adding the vanilla* pattern if any error
                blocks.put(pattern, level);
                save();
                return true;
            }
        }
        blocks.put(pattern, level);
        save();
        return true;
    }

    /**
     * プラグイン名を指定して、そのプラグインのすべてのコマンドを無効化
     * 例：addPlugin("floodgate", PermissionLevel.ADMIN) で floodgate のすべてのコマンドを ADMIN のみに制限
     */
    public synchronized boolean addPlugin(String pluginName, PermissionLevel level) {
        try {
            // プラグイン名でコマンドを検索
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            CommandMap commandMap = (CommandMap) field.get(Bukkit.getServer());
            Field knownField = commandMap.getClass().getDeclaredField("knownCommands");
            knownField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> known = (Map<String, Command>) knownField.get(commandMap);

            int count = 0;
            for (Map.Entry<String, Command> e : known.entrySet()) {
                // PluginCommand の場合、プラグイン名をチェック
                if (e.getValue() instanceof PluginCommand) {
                    PluginCommand pCmd = (PluginCommand) e.getValue();
                    if (pCmd.getPlugin().getName().equalsIgnoreCase(pluginName)) {
                        if (!blocks.containsKey(e.getKey())) {
                            blocks.put(e.getKey(), level);
                            count++;
                        }
                    }
                }
            }
            if (count > 0) {
                save();
            }
            return count > 0;
        } catch (Exception ex) {
            // fallback: add pattern like "floodgate*"
            blocks.put(pluginName + "*", level);
            save();
            return true;
        }
    }

    public synchronized boolean remove(String pattern, PermissionLevel level) {
        if (blocks.containsKey(pattern) && blocks.get(pattern).equals(level)) {
            blocks.remove(pattern);
            save();
            return true;
        }
        return false;
    }

    public synchronized void removeAll(PermissionLevel level) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, PermissionLevel> entry : blocks.entrySet()) {
            if (entry.getValue() == level) toRemove.add(entry.getKey());
        }
        toRemove.forEach(blocks::remove);
        save();
    }

    public synchronized List<String> list(PermissionLevel level) {
        return blocks.entrySet().stream()
                .filter(e -> e.getValue() == level)
                .map(e -> e.getKey())
                .collect(Collectors.toList());
    }

    /**
     * 条件に合うコマンドを検索して一覧表示
     * 例：searchCommands("floodgate") で floodgate に関連するすべてのコマンドを表示
     */
    public synchronized List<String> searchCommands(String keyword) {
        List<String> results = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();
        
        for (String command : getAllRegisteredCommands()) {
            if (command.toLowerCase().contains(lowerKeyword)) {
                results.add(command);
            }
        }
        
        plugin.getLogger().info("searchCommands: keyword=" + keyword + ", found " + results.size() + " results");
        return results;
    }

    public synchronized boolean matches(CommandSender sender, String command) {
        PermissionLevel senderLevel = computeSenderLevel(sender);
        
        for (Map.Entry<String, PermissionLevel> entry : blocks.entrySet()) {
            if (entry.getValue() == PermissionLevel.ANY || entry.getValue() == senderLevel) {
                String pattern = entry.getKey();
                
                // パターンマッチング
                if (matchesWildcard(pattern, command)) {
                    plugin.getLogger().info("[matches] BLOCKED by pattern: " + pattern + " (matches: " + command + ", Sender: " + sender.getName() + ")");
                    return true;
                }
                
                // フォールバック1: コロン形式のコマンドのプレフィックス対応
                // 例: command = "bukkit:version", pattern = "bukkit*"
                // → "bukkit:version" から "bukkit" を抽出して matchesWildcard する
                if (command.contains(":")) {
                    String[] parts = command.split(":", 2);
                    String prefix = parts[0];
                    if (matchesWildcard(pattern, prefix)) {
                        plugin.getLogger().info("[matches] BLOCKED by pattern (colon prefix): " + pattern + " (matches prefix: " + prefix + " from " + command + ", Sender: " + sender.getName() + ")");
                        return true;
                    }
                    
                    // フォールバック2: コロン形式のコマンドのサブコマンド部分対応
                    // 例: command = "floodgate:linkaccount", pattern = "linkaccount"
                    // → "floodgate:linkaccount" から "linkaccount" を抽出して matchesWildcard する
                    String subcommand = parts[1];
                    if (matchesWildcard(pattern, subcommand)) {
                        plugin.getLogger().info("[matches] BLOCKED by pattern (colon subcommand): " + pattern + " (matches subcommand: " + subcommand + " from " + command + ", Sender: " + sender.getName() + ")");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private PermissionLevel computeSenderLevel(CommandSender sender) {
        if (sender instanceof org.bukkit.command.ConsoleCommandSender) return PermissionLevel.CONSOLE;
        if (sender instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player p = (org.bukkit.entity.Player) sender;
            if (p.isOp()) return PermissionLevel.ADMIN;
            return PermissionLevel.MEMBER;
        }
        return PermissionLevel.ANY;
    }

    private void load() {
        Optional<PEXConfig> cfg = configManager.loadConfig(configPath);
        if (cfg.isEmpty()) return;
        Object raw = cfg.get().get("blocks");
        if (!(raw instanceof List)) return;
        for (Object o : (List<?>) raw) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            Object pat = m.get("pattern");
            Object level = m.get("level");
            if (pat instanceof String && level instanceof Number) {
                try {
                    PermissionLevel pl = PermissionLevel.fromLevel(((Number) level).intValue());
                    blocks.put((String) pat, pl);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * パターンに一致するコマンドをデバッグ表示
     * 例：debugMatchesPattern("bukkit*") で bukkit:* コマンドが一覧表示される
     */
    public synchronized List<String> debugMatchesPattern(String pattern) {
        List<String> matches = new ArrayList<>();
        
        for (String cmdName : getAllRegisteredCommands()) {
            if (matchesWildcard(pattern, cmdName)) {
                matches.add(cmdName);
            }
        }
        
        return matches;
    }

    /**
     * すべてのコマンド一覧を表示（デバッグ用）
     */
    public synchronized List<String> listAllCommands() {
        List<String> commands = new ArrayList<>(getAllRegisteredCommands());
        commands.sort(String::compareTo);
        plugin.getLogger().info("listAllCommands: total commands=" + commands.size());
        return commands;
    }

    private void save() {
        PEXConfig cfg = new PEXConfig();
        List<Map<String, Object>> arr = new ArrayList<>();
        for (Map.Entry<String, PermissionLevel> entry : blocks.entrySet()) {
            Map<String, Object> m = new HashMap<>();
            m.put("pattern", entry.getKey());
            m.put("level", entry.getValue().getLevel());
            arr.add(m);
        }
        cfg.put("blocks", arr);
        configManager.saveConfig(configPath, cfg);
    }
}
