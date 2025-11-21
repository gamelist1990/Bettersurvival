package org.pexserver.koukunn.bettersurvival.Core.Util;

import org.bukkit.entity.Player;
import java.util.List;
import java.util.function.Consumer;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.response.ModalFormResponse;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.cumulus.component.Component;
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * Bedrock 向けのフォーム（Geyser/Cumulus）と、Java 向けのフォールバックユーティリティ。
 *
 * <p>このクラスは Floodgate が導入された Bedrock プレイヤー向けに Cumulus の
 * {@code SimpleForm}, {@code ModalForm}, {@code CustomForm} を送信するヘルパーを提供します。
 * Java クライアント（PC）には通常通りインベントリ GUI を使うため、本ユーティリティは
 * Bedrock 判定に成功した場合のみフォームを送信して true を返します。フォールバックとして
 * Java プレイヤーには何も表示されず false を返すため、呼び出し側は返り値でどちらの UI が
 * 表示されたかを判定できます。
 *
 * 重要: Cumulus/Floodgate はオプションの依存関係です。サーバーに Geyser/Floodgate がない
 * 場合はフォーム送信は行われず false が返ります。
 */
public final class FormsUtil {

    private FormsUtil() {}

    public static boolean openSimpleForm(Player p, String title, List<String> buttons, Consumer<Integer> callback) {
        if (p == null) return false;

        // Bedrock check
        if (FloodgateUtil.isBedrock(p)) {
            FloodgateApi api = FloodgateApi.getInstance();
            if (api == null) return false;

            try {
                SimpleForm.Builder b = SimpleForm.builder().title(title).content("");
                for (String btn : buttons) {
                    b.button(btn);
                }
                b.validResultHandler((form, resp) -> {
                    if (resp == null) {
                        if (callback != null) callback.accept(-1);
                        return;
                    }
                    int idx = ((SimpleFormResponse) resp).clickedButtonId();
                    if (callback != null) callback.accept(idx);
                });
                SimpleForm form = b.build();
                boolean ok = api.sendForm(p.getUniqueId(), form);
                return ok;
            } catch (NoClassDefFoundError | Exception e) {
                return false;
            }
        }

        // Fallback: Not Bedrock or forms not available. Return false to indicate not displayed as SimpleForm.
        return false;
    }

    /**
     * モーダル（2ボタン）を表示します。
     *
     * @param p 対象プレイヤー
     * @param title フォームのタイトル
     * @param content 説明文（中央に表示されるテキスト）
     * @param buttonA 左側のボタンテキスト
     * @param buttonB 右側のボタンテキスト
     * @param callback ユーザーがどちらを押したかを受け取るコールバック。押されたら {@code true} (A) / {@code false} (B)
     * @return Bedrock プレイヤーにフォームが表示された場合は {@code true}、そうでなければ {@code false}
     *
     * 戻り値が {@code false} の場合、Java クライアントでは何も表示されていないため
     * 呼び出し側でインベントリ GUI を用意するなどのフォールバック処理を行ってください。
     */

    public static boolean openModalForm(Player p, String title, String content, String buttonA, String buttonB, Consumer<Boolean> callback) {
        if (p == null) return false;
        if (FloodgateUtil.isBedrock(p)) {
            FloodgateApi api = FloodgateApi.getInstance();
            if (api == null) return false;
            try {
                ModalForm.Builder b = ModalForm.builder().title(title).content(content).button1(buttonA).button2(buttonB);
                b.validResultHandler((form, resp) -> {
                    if (resp == null) {
                        if (callback != null) callback.accept(false);
                        return;
                    }
                    boolean clickedA = ((ModalFormResponse) resp).clickedFirst();
                    if (callback != null) callback.accept(clickedA);
                });
                ModalForm form = b.build();
                return api.sendForm(p.getUniqueId(), form);
            } catch (NoClassDefFoundError | Exception ignored) {
                return false;
            }
        }
        return false;
    }

    public static boolean openSingleInputForm(Player p, String title, String label, String placeholder, String defaultText, Consumer<String> callback) {
        if (p == null) return false;
        if (FloodgateUtil.isBedrock(p)) {
            FloodgateApi api = FloodgateApi.getInstance();
            if (api == null) return false;
            try {
                CustomForm.Builder b = CustomForm.builder().title(title).input(label, placeholder, defaultText);
                b.validResultHandler((form, resp) -> {
                    if (resp == null) {
                        if (callback != null) callback.accept(null);
                        return;
                    }
                    String input = ((CustomFormResponse) resp).asInput(0);
                    if (callback != null) callback.accept(input);
                });
                CustomForm form = b.build();
                return api.sendForm(p.getUniqueId(), form);
            } catch (NoClassDefFoundError | Exception ignored) {
                return false;
            }
        }
        return false;
    }

    /**
     * 任意のコンポーネント群を持つ {@link org.geysermc.cumulus.form.CustomForm} を表示します。
     *
     * @param p 対象プレイヤー
     * @param title フォームのタイトル
     * @param components {@link org.geysermc.cumulus.component.Component} のリスト（入力、スライダー等）
     * @param callback {@link org.geysermc.cumulus.response.CustomFormResponse} を受け取るコールバック
     * @return フォーム送信に成功して Bedrock プレイヤーに表示された場合は {@code true}、それ以外は {@code false}
     *
     * CustomForm のレスポンスは {@link org.geysermc.cumulus.response.CustomFormResponse} で
     * 取得できます。テキスト入力は {@link org.geysermc.cumulus.response.CustomFormResponse#asInput(int)}
     * を使って読み取ってください。
     */

    public static boolean openCustomForm(Player p, String title, List<Component> components, Consumer<CustomFormResponse> callback) {
        if (p == null) return false;
        if (FloodgateUtil.isBedrock(p)) {
            FloodgateApi api = FloodgateApi.getInstance();
            if (api == null) return false;
            try {
                CustomForm.Builder b = CustomForm.builder().title(title);
                for (Component c : components) b.component(c);
                b.validResultHandler((form, resp) -> {
                    if (callback != null) callback.accept((CustomFormResponse) resp);
                });
                CustomForm form = b.build();
                return api.sendForm(p.getUniqueId(), form);
            } catch (NoClassDefFoundError | Exception ignored) {
                return false;
            }
        }
        return false;
    }
}
