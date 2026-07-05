package org.pexserver.koukunn.bettersurvival.Core.NMS;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * NMS ブリッジへの公開ファサード。
 *
 * すべての機能は {@link NMSBridge} 実装に委譲される。
 * Paper バージョンが上がった場合は {@link #BRIDGE} を新しい実装クラスに
 * 差し替えるだけでよい。呼び出し側コードの変更は不要。
 *
 * <pre>
 * 現在の実装: {@link Paper262NMSBridge} (Paper 26.2)
 * </pre>
 */
public final class NMSApi {

    /** プレイヤーインベントリ (コンテナID 0) のオフハンドの生スロット番号 */
    public static final int RAW_SLOT_OFFHAND = 45;

    /**
     * アクティブな {@link NMSBridge} 実装。
     * Paper のメジャーバージョンが変わった際にここを差し替える。
     */
    static final NMSBridge BRIDGE = new Paper262NMSBridge();

    private NMSApi() {
    }

    /** この環境で NMS ブリッジが利用可能か */
    public static boolean isAvailable() {
        return BRIDGE.isAvailable();
    }

    /**
     * クライアントにだけ見える偽のインベントリスロットを送る。
     *
     * @param rawSlot プレイヤーインベントリ (コンテナID 0) の生スロット番号
     * @return 送信に成功したか
     */
    public static boolean sendFakeSlot(Player player, int rawSlot, ItemStack item) {
        return BRIDGE.sendFakeSlot(player, rawSlot, item);
    }

    /**
     * 自分自身の「アイテム使用中」フラグをクライアントに偽装送信する。
     *
     * @param using   使用中にするか
     * @param offhand 使用する手をオフハンドにするか
     * @return 送信に成功したか
     */
    public static boolean sendUsingItemFlags(Player player, boolean using, boolean offhand) {
        return BRIDGE.sendUsingItemFlags(player, using, offhand);
    }

    /**
     * サーバー側で実際にアイテム使用を開始させる (偽装ではない本物の使用状態)。
     * 使用をやめた時は PlayerStopUsingItemEvent が発火する。
     *
     * @param offhand     オフハンドのアイテムを使用するか
     * @param forceUpdate 既に使用中でも使用時間をリセットするか
     * @return 成功したか
     */
    public static boolean startUsingItem(Player player, boolean offhand, boolean forceUpdate) {
        return BRIDGE.startUsingItem(player, offhand, forceUpdate);
    }

    /**
     * 望遠鏡を覗いた時の本物のスコープ UI を表示する。
     *
     * @return 表示に成功したか
     */
    public static boolean startSpyglassScope(Player player) {
        return BRIDGE.startSpyglassScope(player);
    }

    /** スコープの使用時間切れを防ぐため、使用状態を再スタートさせる */
    public static boolean refreshSpyglassScope(Player player) {
        return BRIDGE.refreshSpyglassScope(player);
    }

    /** オフハンドの偽装スロットだけ実インベントリへ戻す */
    public static void restoreSpyglassSlot(Player player) {
        BRIDGE.restoreSpyglassSlot(player);
    }

    /** スコープ UI を終了し、オフハンドの表示を実際のインベントリへ戻す */
    public static void stopSpyglassScope(Player player) {
        BRIDGE.stopSpyglassScope(player);
    }
}
