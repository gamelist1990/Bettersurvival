package org.pexserver.koukunn.bettersurvival.Core.Command;

/**
 * コマンド実装ガイド
 * 
 * ===== 基本的な使い方 =====
 * 
 * 1. BaseCommand を継承したクラスを作成
 * 2. 必要なメソッドを実装
 * 3. Loader.java の registerCommands() メソッドで登録
 * 
 * ===== 実装例（権限レベルを使用） =====
 * 
 * public class MyCommand extends BaseCommand {
 * 
 *     @Override
 *     public String getName() {
 *         return "mycommand";  // ① コマンド名
 *     }
 * 
 *     @Override
 *     public String getDescription() {
 *         return "これは私のコマンドです";  // ② 説明
 *     }
 * 
 *     @Override
 *     public PermissionLevel getPermissionLevel() {
 *         return PermissionLevel.ADMIN;  // ③ 権限レベルを指定
 *     }
 * 
 *     @Override
 *     public boolean execute(CommandSender sender, String[] args) {
 *         sendSuccess(sender, "コマンド実行成功");
 *         return true;
 *     }
 * }
 * 
 * ===== 権限レベルについて =====
 * 
 * PermissionLevel の種類:
 * 
 * 0: ANY
 *    - 誰でも実行可能
 *    - 例: 情報表示コマンド
 * 
 * 1: MEMBER
 *    - メンバー以上（通常プレイヤー）
 *    - コンソールでも実行可能
 *    - 例: プレイヤー情報コマンド
 * 
 * 2: ADMIN
 *    - 管理者（Op 権限）のみ実行可能
 *    - 例: サーバー設定変更コマンド
 * 
 * 3: CONSOLE
 *    - コンソール専用
 *    - プレイヤーは実行不可
 *    - 例: サーバーシャットダウンコマンド
 * 
 * 4: ADMIN_OR_CONSOLE
 *    - Op 権限を持つプレイヤーまたはコンソール
 *    - 例: 危険な設定変更
 * 
 * ===== カスタム権限の使用 =====
 * 
 * getPermissionLevel() に加えて、getPermission() でカスタム権限を指定できます。
 * カスタム権限が優先されます。
 * 
 * @Override
 * public PermissionLevel getPermissionLevel() {
 *     return PermissionLevel.ADMIN;
 * }
 * 
 * @Override
 * public String getPermission() {
 *     return "pexsurvival.custom.permission";  // カスタム権限（こちらが優先）
 * }
 * 
 * ===== 登録方法 =====
 * 
 * Loader.java の registerCommands() メソッド内で:
 * 
 * // クラスから直接登録
 * commandManager.register(MyCommand.class);
 * 
 * // インスタンスから登録
 * commandManager.register(new MyCommand());
 * 
 * // 複数のコマンドを一括登録
 * commandManager.registerAll(
 *     MyCommand.class,
 *     MyCommand2.class,
 *     MyCommand3.class
 * );
 * 
 * ===== 利用可能なメソッド =====
 * 
 * protected boolean hasPermission(CommandSender sender)
 *   - 権限チェック（権限レベル + カスタム権限の両方を確認）
 * 
 * protected void sendError(CommandSender sender, String message)
 *   - エラーメッセージ送信（赤色）
 * 
 * protected void sendSuccess(CommandSender sender, String message)
 *   - 成功メッセージ送信（緑色）
 * 
 * protected void sendInfo(CommandSender sender, String message)
 *   - 情報メッセージ送信（青色）
 * 
 * ===== 完全な実装例 =====
 * 
 * public class StatusCommand extends BaseCommand {
 *     @Override
 *     public String getName() {
 *         return "status";
 *     }
 * 
 *     @Override
 *     public String getDescription() {
 *         return "ステータス情報を表示";
 *     }
 * 
 *     @Override
 *     public PermissionLevel getPermissionLevel() {
 *         return PermissionLevel.ANY;
 *     }
 * 
 *     @Override
 *     public boolean execute(CommandSender sender, String[] args) {
 *         sendInfo(sender, "プラグインは正常に動作しています");
 *         return true;
 *     }
 * }
 * 
 * ===== 権限の設定 =====
 * 
 * デフォルト設定（権限レベルのみ使用）:
 * - Op コマンドはサーバーの ops.json で設定
 * - 権限レベル ANY: 全員
 * - 権限レベル MEMBER: 全員 + コンソール
 * - 権限レベル ADMIN: Op のみ
 * - 権限レベル CONSOLE: コンソール専用
 * - 権限レベル ADMIN_OR_CONSOLE: Op またはコンソール
 * 
 * カスタム権限設定（permission.yml）:
 * permissions:
 *   pexsurvival.custom.permission:
 *     description: カスタムコマンド権限
 *     default: op
 */
public class CommandGuide {
    // これはガイドファイルです
}
