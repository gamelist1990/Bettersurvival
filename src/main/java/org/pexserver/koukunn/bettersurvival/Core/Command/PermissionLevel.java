package org.pexserver.koukunn.bettersurvival.Core.Command;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

/**
 * コマンドの権限レベルを定義する列挙型
 * 
 * 0: ANY      - 誰でも実行可能
 * 1: MEMBER   - メンバー以上（通常プレイヤー）
 * 2: ADMIN    - 管理者のみ（管理者権限必須）
 * 3: CONSOLE  - コンソールのみ
 * 4: ADMIN_OR_CONSOLE - 管理者またはコンソール
 */
public enum PermissionLevel {
    ANY(0, "誰でも実行可能"),
    MEMBER(1, "メンバー以上"),
    ADMIN(2, "管理者のみ"),
    CONSOLE(3, "コンソールのみ"),
    ADMIN_OR_CONSOLE(4, "管理者またはコンソール");

    private final int level;
    private final String description;

    PermissionLevel(int level, String description) {
        this.level = level;
        this.description = description;
    }

    /**
     * レベル番号を取得
     * @return レベル番号
     */
    public int getLevel() {
        return level;
    }

    /**
     * 説明を取得
     * @return 説明テキスト
     */
    public String getDescription() {
        return description;
    }

    /**
     * コマンド実行者が該当する権限レベルかチェック
     * @param sender コマンド実行者
     * @param customPermission カスタム権限キー（nullの場合はレベルチェックのみ）
     * @return 実行可能な場合true
     */
    public boolean hasAccess(CommandSender sender, String customPermission) {
        // カスタム権限がある場合はそれを優先
        if (customPermission != null && !customPermission.isEmpty()) {
            return sender.hasPermission(customPermission);
        }

        // 権限レベルチェック
        switch (this) {
            case ANY:
                return true;

            case MEMBER:
                // プレイヤーなら実行可能
                return sender instanceof Player || sender instanceof ConsoleCommandSender;

            case ADMIN:
                // Op 権限を持つプレイヤーのみ
                return sender instanceof Player && sender.isOp();

            case CONSOLE:
                // コンソールのみ
                return sender instanceof ConsoleCommandSender;

            case ADMIN_OR_CONSOLE:
                // Op 権限を持つプレイヤーまたはコンソール
                if (sender instanceof ConsoleCommandSender) {
                    return true;
                }
                return sender instanceof Player && sender.isOp();

            default:
                return false;
        }
    }

    /**
     * 番号から PermissionLevel を取得
     * @param level レベル番号
     * @return PermissionLevel
     */
    public static PermissionLevel fromLevel(int level) {
        for (PermissionLevel perm : PermissionLevel.values()) {
            if (perm.level == level) {
                return perm;
            }
        }
        return ANY;  // デフォルトは ANY
    }
}
