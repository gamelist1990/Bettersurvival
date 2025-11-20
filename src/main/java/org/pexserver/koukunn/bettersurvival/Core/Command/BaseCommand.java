package org.pexserver.koukunn.bettersurvival.Core.Command;

// Component is not used directly; use LegacyComponentSerializer for legacy-formatted messages
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import java.util.*;

/**
 * コマンドテンプレート基底クラス
 * すべてのカスタムコマンドはこのクラスを継承します
 */
public abstract class BaseCommand {

    /**
     * コマンド名を返します
     * @return コマンド名
     */
    public abstract String getName();

    /**
     * コマンドの説明を返します
     * @return コマンドの説明
     */
    public abstract String getDescription();

    /**
     * 権限レベルを返します（デフォルト）
     * getPermission() で指定されたカスタム権限がある場合は、そちらが優先されます
     * @return 権限レベル
     */
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.ANY;  // デフォルトは誰でも実行可能
    }

    /**
     * カスタム権限を返します
     * getPermissionLevel() より優先されます
     * @return 権限キー（例：pexsurvival.admin）、不要な場合はnull
     */
    public String getPermission() {
        return null;
    }

    /**
     * コマンド実行処理
     * @param sender コマンド実行者
     * @param args コマンド引数
     * @return 実行成功フラグ
     */
    public abstract boolean execute(CommandSender sender, String[] args);

    /**
     * 使用法を返します（オプション）
     * @return 使用法テキスト
     */
    public String getUsage() {
        return "/" + getName();
    }

    /**
     * Tab補完の候補を返します
     * @param sender コマンド実行者
     * @param args コマンド引数
     * @return 補完候補のリスト
     */
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }

    /**
     * 権限を持っているかチェック
     * カスタム権限 → 権限レベル の順で確認します
     * @param sender コマンド実行者
     * @return 権限を持っている場合true
     */
    protected boolean hasPermission(CommandSender sender) {
        return getPermissionLevel().hasAccess(sender, getPermission());
    }

    /**
     * エラーメッセージを送信
     * @param sender コマンド実行者
     * @param message メッセージ
     */
    protected void sendError(CommandSender sender, String message) {
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§c[エラー] " + message));
    }

    /**
     * 成功メッセージを送信
     * @param sender コマンド実行者
     * @param message メッセージ
     */
    protected void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§a[成功] " + message));
    }

    /**
     * 情報メッセージを送信
     * @param sender コマンド実行者
     * @param message メッセージ
     */
    protected void sendInfo(CommandSender sender, String message) {
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§b[情報] " + message));
    }
}
