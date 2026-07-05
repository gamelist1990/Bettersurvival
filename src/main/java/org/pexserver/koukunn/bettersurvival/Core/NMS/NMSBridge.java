package org.pexserver.koukunn.bettersurvival.Core.NMS;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Paper NMS へのブリッジインターフェース。
 *
 * バージョンごとに実装を差し替えることで NMS API の変化に対応する。
 * 新しい Paper バージョンに対応する場合は、このインターフェースを実装した
 * クラスを追加し、{@link NMSApi#BRIDGE} に差し替えるだけでよい。
 */
public interface NMSBridge {

    /** この環境でブリッジが利用可能か */
    boolean isAvailable();

    /**
     * クライアントにだけ見える偽のインベントリスロットを送る。
     *
     * @param player  対象プレイヤー
     * @param rawSlot プレイヤーインベントリ (コンテナID 0) の生スロット番号
     * @param item    表示するアイテム
     * @return 送信に成功したか
     */
    boolean sendFakeSlot(Player player, int rawSlot, ItemStack item);

    /**
     * 自分自身の「アイテム使用中」フラグをクライアントに偽装送信する。
     *
     * @param player  対象プレイヤー
     * @param using   使用中にするか
     * @param offhand 使用する手をオフハンドにするか
     * @return 送信に成功したか
     */
    boolean sendUsingItemFlags(Player player, boolean using, boolean offhand);

    /**
     * サーバー側で実際にアイテム使用を開始させる (偽装ではない本物の使用状態)。
     * クライアントへは Vanilla の正規同期で伝わり、使用をやめた時は
     * PlayerStopUsingItemEvent が発火する。
     *
     * @param player      対象プレイヤー
     * @param offhand     オフハンドのアイテムを使用するか
     * @param forceUpdate 既に使用中でも使用時間をリセットするか
     * @return 成功したか
     */
    boolean startUsingItem(Player player, boolean offhand, boolean forceUpdate);

    /**
     * 望遠鏡スコープ UI を表示する。
     *
     * @return 表示に成功したか
     */
    boolean startSpyglassScope(Player player);

    /**
     * スコープの使用時間切れを防ぐため、使用状態を再スタートさせる。
     *
     * @return 再開に成功したか
     */
    boolean refreshSpyglassScope(Player player);

    /** オフハンドの偽装スロットだけ実インベントリへ戻す */
    void restoreSpyglassSlot(Player player);

    /** スコープ UI を終了し、オフハンドの表示を実際のインベントリへ戻す */
    void stopSpyglassScope(Player player);
}
