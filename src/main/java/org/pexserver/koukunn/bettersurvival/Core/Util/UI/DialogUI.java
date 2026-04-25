package org.pexserver.koukunn.bettersurvival.Core.Util.UI;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.FormsUtil;
import org.pexserver.koukunn.bettersurvival.Loader;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * ユーザーにダイアログを表示するためのユーティリティクラス。
 * <p>
 * Paper の {@link io.papermc.paper.dialog.Dialog} API による GUI ダイアログを抽象化し、
 * Java 版と Bedrock 版の両方に対応した統合インターフェースを提供します。
 * 内部的には Paper ダイアログと Geyser/Floodgate 用のフォームを切り替えて使用します。
 * </p>
 * <p>
 * 使用例:
 * <pre>
 * DialogUI.builder()
 *         .title("確認")
 *         .body("本当に削除しますか？")
 *         .confirmation("はい","いいえ")
 *         .onResponse((result, player) -> {
 *             if (result.isConfirmed()) {
 *                 // 処理
 *             }
 *         })
 *         .show(player);
 * </pre>
 * </p>
 */
public class DialogUI {

    private static final Map<UUID, HandlerData> responseHandlers = new ConcurrentHashMap<>();
    private static int keyCounter = 0;

    /**
     * 新しいダイアログ構築用 {@link Builder} を作成します。
     *
     * @return ダイアログ作成用ビルダー
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * ダイアログを段階的に構築するためのビルダークラス。
     * <p>
     * Java 版と Bedrock 版のフォーム生成処理を一手に引き受け、
     * 最終的に {@link #show(Player)} でプレイヤーに表示します。
     * デフォルトはタイトル"Dialog"で、閉じるボタンはエスケープ可。
     * </p>
     */
    public static class Builder {

        private Component title = Component.text("Dialog");
        private final List<DialogBody> bodyComponents = new ArrayList<>();
        private final List<DialogInput> inputs = new ArrayList<>();
        private Component externalTitle = null;
        private boolean canCloseWithEscape = true;
        private BiConsumer<DialogResult, Player> responseHandler = null;

        private String yesButtonLabel = null;
        private String noButtonLabel = null;
        private final List<ActionButtonData> actionButtons = new ArrayList<>();

        /**
         * ダイアログのタイトルを設定します（文字列版）。
         *
         * @param title 表示するタイトル
         * @return このビルダー自身
         */
        public Builder title(String title) {
            this.title = Component.text(title);
            return this;
        }

        /**
         * ダイアログのタイトルを {@link Component} で設定します。
         *
         * @param title 表示するタイトルコンポーネント
         * @return このビルダー自身
         */
        public Builder title(Component title) {
            this.title = title;
            return this;
        }

        /**
         * 外部タイトル（ダイアログ枠とは別に表示されるタイトル）を設定します。
         *
         * @param externalTitle 外部タイトル文字列
         * @return このビルダー自身
         */
        public Builder externalTitle(String externalTitle) {
            this.externalTitle = Component.text(externalTitle);
            return this;
        }

        /**
         * ESCキーなどでダイアログを閉じることを許可するかどうかを設定します。
         *
         * @param canClose true ならエスケープで閉じられる
         * @return このビルダー自身
         */
        public Builder canCloseWithEscape(boolean canClose) {
            this.canCloseWithEscape = canClose;
            return this;
        }

        /**
         * 本文テキストを1行追加します。
         *
         * @param text 表示する文字列
         * @return このビルダー自身
         */
        public Builder body(String text) {
            this.bodyComponents.add(DialogBody.plainMessage(Component.text(text)));
            return this;
        }

        /**
         * 本文に {@link Component} を直接追加します。
         *
         * @param component 表示するコンポーネント
         * @return このビルダー自身
         */
        public Builder body(Component component) {
            this.bodyComponents.add(DialogBody.plainMessage(component));
            return this;
        }

        /**
         * 本文にアイテム表示を追加します。
         *
         * @param itemStack 表示する {@link ItemStack}
         * @return このビルダー自身
         */
        public Builder bodyItem(ItemStack itemStack) {
            this.bodyComponents.add(DialogBody.item(itemStack).build());
            return this;
        }

        /**
         * 本文にマテリアルのアイテム表示を追加します。
         *
         * @param material 表示する {@link Material}
         * @return このビルダー自身
         */
        public Builder bodyItem(Material material) {
            this.bodyComponents.add(DialogBody.item(new ItemStack(material)).build());
            return this;
        }

        /**
         * テキスト入力欄を追加します。
         *
         * @param key   入力値を取得するための識別キー
         * @param label ラベル文字列
         * @return このビルダー自身
         */
        public Builder addTextInput(String key, String label) {
            this.inputs.add(DialogInput.text(key, Component.text(label)).build());
            return this;
        }

        public Builder addTextInput(String key, String label, String initialValue) {
            try {
                this.inputs.add(DialogInput.text(key, Component.text(label))
                        .initial(initialValue != null ? initialValue : "")
                        .build());
            } catch (Exception e) {
                this.inputs.add(DialogInput.text(key, Component.text(label)).build());
            }
            return this;
        }

        /**
         * テキスト入力欄を追加します（最大長と複数行指定付き）。
         *
         * @param key       入力値を取得する識別キー
         * @param label     ラベル文字列
         * @param maxLength 最大文字数
         * @param multiline true なら複数行入力を許可
         * @return このビルダー自身
         */
        public Builder addTextInput(String key, String label, int maxLength, boolean multiline) {
            var builder = DialogInput.text(key, Component.text(label));
            builder.maxLength(maxLength);
            builder.multiline(io.papermc.paper.registry.data.dialog.DialogInstancesProvider.instance()
                    .multilineOptions(multiline ? null : 1, multiline ? null : 20));
            this.inputs.add(builder.build());
            return this;
        }

        /**
         * 初期値つきのテキスト入力欄を追加します。
         *
         * @param key          識別キー
         * @param label        ラベル文字列
         * @param initialValue 初期文字列（null で空）
         * @param maxLength    最大文字数
         * @param multiline    複数行を許可するか
         * @return このビルダー自身
         */
        public Builder addTextInput(String key, String label, String initialValue, int maxLength, boolean multiline) {
            var builder = DialogInput.text(key, Component.text(label));
            builder.initial(initialValue != null ? initialValue : "");
            builder.maxLength(maxLength);
            builder.multiline(io.papermc.paper.registry.data.dialog.DialogInstancesProvider.instance()
                    .multilineOptions(multiline ? null : 1, multiline ? null : 20));
            this.inputs.add(builder.build());
            return this;
        }

        /**
         * {@link Component} ラベル付きのテキスト入力欄を追加します。
         *
         * @param key   識別キー
         * @param label ラベルコンポーネント
         * @return このビルダー自身
         */
        public Builder addTextInput(String key, Component label) {
            this.inputs.add(DialogInput.text(key, label).build());
            return this;
        }

        public Builder addTextInput(String key, Component label, int maxLength, boolean multiline) {
            var builder = DialogInput.text(key, label);
            builder.maxLength(maxLength);
            builder.multiline(io.papermc.paper.registry.data.dialog.DialogInstancesProvider.instance()
                    .multilineOptions(multiline ? null : 1, multiline ? null : 20));
            this.inputs.add(builder.build());
            return this;
        }

        /**
         * 真偽値入力（トグル）を追加します。
         *
         * @param key   識別キー
         * @param label ラベル文字列
         * @return このビルダー自身
         */
        public Builder addBoolInput(String key, String label) {
            this.inputs.add(DialogInput.bool(key, Component.text(label)).build());
            return this;
        }

        /**
         * デフォルト値付きの真偽値入力を追加します。
         *
         * @param key          識別キー
         * @param label        ラベル文字列
         * @param defaultValue 初期値
         * @return このビルダー自身
         */
        public Builder addBoolInput(String key, String label, boolean defaultValue) {
            try {
                this.inputs.add(DialogInput.bool(key, Component.text(label))
                        .initial(defaultValue)
                        .build());
            } catch (Exception e) {
                this.inputs.add(DialogInput.bool(key, Component.text(label)).build());
            }
            return this;
        }

        public Builder addBoolInput(String key, Component label) {
            this.inputs.add(DialogInput.bool(key, label).build());
            return this;
        }

        public Builder addBoolInput(String key, Component label, boolean defaultValue) {
            try {
                this.inputs.add(DialogInput.bool(key, label)
                        .initial(defaultValue)
                        .build());
            } catch (Exception e) {
                this.inputs.add(DialogInput.bool(key, label).build());
            }
            return this;
        }

        /**
         * 数値スライダー入力を追加します。
         *
         * @param key   識別キー
         * @param label ラベル文字列
         * @param min   最小値
         * @param max   最大値
         * @return このビルダー自身
         */
        public Builder addNumberInput(String key, String label, float min, float max) {
            this.inputs.add(DialogInput.numberRange(key, Component.text(label), min, max).build());
            return this;
        }

        public Builder addNumberInput(String key, String label, float min, float max, float step, float initial) {
            float clampedInitial = Math.max(min, Math.min(max, initial));

            this.inputs.add(DialogInput.numberRange(key, Component.text(label), min, max)
                    .step(step)
                    .initial(clampedInitial)
                    .build());
            return this;
        }

        /**
         * 通知ダイアログ（OKボタン）を設定します。
         *
         * @param buttonLabel ボタンの文字列
         * @return このビルダー自身
         */
        public Builder notice(String buttonLabel) {
            this.yesButtonLabel = buttonLabel;
            return this;
        }

        public Builder confirmation(String yesLabel, String noLabel) {
            this.yesButtonLabel = yesLabel;
            this.noButtonLabel = noLabel;
            return this;
        }

        public Builder addAction(String label, int color) {
            this.actionButtons.add(new ActionButtonData(label, color, null));
            return this;
        }

        public Builder addAction(String label, int color, String tooltip) {
            this.actionButtons.add(new ActionButtonData(label, color, tooltip));
            return this;
        }

        public Builder onResponse(BiConsumer<DialogResult, Player> handler) {
            this.responseHandler = handler;
            return this;
        }

        /**
         * 構築済みのダイアログを指定プレイヤーに表示します。
         * <p>内部で Java / Bedrock を判別し、自動的に適切な
         * フォーム API を呼び出します。</p>
         *
         * @param player 表示対象のプレイヤー
         */
        public void show(Player player) {
            showWithResult(player);
        }

        public boolean showWithResult(Player player) {
            if (FloodgateUtil.isBedrock(player)) {
                return showBedrockForm(player);
            }

            Dialog dialog = build();
            if (dialog != null) {
                if (responseHandler != null) {
                    responseHandlers.put(player.getUniqueId(), new HandlerData(responseHandler, inputKeys()));
                }
                player.showDialog(dialog);
                return true;
            }
            return false;
        }

        private List<String> inputKeys() {
            List<String> keys = new ArrayList<>();
            for (DialogInput input : inputs) {
                String key = extractInputKey(input);
                if (key != null && !key.isEmpty()) {
                    keys.add(key);
                }
            }
            return keys;
        }

        private boolean showBedrockForm(Player player) {
            try {
                if (!inputs.isEmpty()) {
                    return showBedrockCustomForm(player);
                } else if (yesButtonLabel != null && noButtonLabel != null) {
                    return showBedrockModalForm(player);
                } else {
                    return showBedrockSimpleForm(player);
                }
            } catch (Throwable ex) {
                player.sendMessage("§c統合版フォームの表示に失敗しました");
                Loader.getPlugin(Loader.class).getLogger().log(Level.WARNING, "Failed to show Bedrock form: {0}",
                        ex.getMessage());
                return false;
            }
        }

        private boolean showBedrockSimpleForm(Player player) {
            String titleStr = extractText(title);
            String contentStr = bodyComponents.stream()
                    .map(body -> extractBodyText(body))
                    .reduce("", (a, b) -> a + "\n" + b);

            java.util.List<String> buttons = new java.util.ArrayList<>();
            if (yesButtonLabel != null) {
                buttons.add(yesButtonLabel);
            }
            if (!actionButtons.isEmpty()) {
                for (ActionButtonData data : actionButtons) {
                    buttons.add(data.label);
                }
            }
            if (buttons.isEmpty()) {
                buttons.add("OK");
            }

            return FormsUtil.openSimpleForm(player, titleStr, contentStr, buttons, clickedIndex -> {
                if (clickedIndex >= 0 && responseHandler != null) {
                    boolean confirmed = (yesButtonLabel != null && clickedIndex == 0);
                    int actionIndex = yesButtonLabel != null ? clickedIndex - 1 : clickedIndex;
                    DialogResult result = new DialogResult(null, null, confirmed, actionIndex);
                    responseHandler.accept(result, player);
                }
            });
        }

        private boolean showBedrockModalForm(Player player) {
            String titleStr = extractText(title);
            String contentStr = bodyComponents.stream()
                    .map(body -> extractBodyText(body))
                    .reduce("", (a, b) -> a + "\n" + b);

            return FormsUtil.openModalForm(player, titleStr, contentStr, yesButtonLabel, noButtonLabel, clickedFirst -> {
                if (responseHandler != null) {
                    DialogResult result = new DialogResult(null, null, clickedFirst);
                    responseHandler.accept(result, player);
                }
            });
        }

        private boolean showBedrockCustomForm(Player player) {
            try {
                String titleStr = extractText(title);
                org.geysermc.cumulus.form.CustomForm.Builder builder = org.geysermc.cumulus.form.CustomForm.builder()
                        .title(titleStr);

                for (DialogBody body : bodyComponents) {
                    String text = extractBodyText(body);
                    if (!text.isEmpty()) {
                        builder.label(text);
                    }
                }

                java.util.Map<String, Integer> inputIndexMap = new java.util.HashMap<>();
                int componentIndex = 0;

                for (DialogInput input : inputs) {
                    try {
                        String key = extractInputKey(input);
                        String label = extractInputLabel(input);

                        if (isTextInput(input)) {
                            String initial = extractTextInitial(input);
                            builder.input(label, "", initial == null ? "" : initial);
                            inputIndexMap.put(key, componentIndex++);
                        } else if (isNumberInput(input)) {
                            float min = extractMin(input);
                            float max = extractMax(input);
                            float step = extractStep(input);
                            float initial = extractInitial(input);
                            builder.slider(label, min, max, step, initial);
                            inputIndexMap.put(key, componentIndex++);
                        } else if (isBoolInput(input)) {
                            boolean initial = extractBoolInitial(input);
                            builder.toggle(label, initial);
                            inputIndexMap.put(key, componentIndex++);
                        }
                    } catch (Throwable ex) {
                        Loader.getPlugin(Loader.class).getLogger().log(Level.WARNING, "Failed to add input component: {0}",
                                ex.getMessage());
                    }
                }

                builder.validResultHandler((form, response) -> {
                    if (response != null && responseHandler != null) {
                        Loader.getPlugin(Loader.class).getLogger().info(
                                "[DialogUI] Bedrock form response player=" + player.getName()
                                        + " inputIndexes=" + inputIndexMap);
                        DialogResult result = new DialogResult(
                                true,
                                (org.geysermc.cumulus.response.CustomFormResponse) response,
                                inputIndexMap);
                        responseHandler.accept(result, player);
                    }
                });

                org.geysermc.cumulus.form.CustomForm form = builder.build();
                org.geysermc.floodgate.api.FloodgateApi api = org.geysermc.floodgate.api.FloodgateApi.getInstance();
                if (api != null) {
                    return api.sendForm(player.getUniqueId(), form);
                }
                return false;
            } catch (Throwable ex) {
                Loader.getPlugin(Loader.class).getLogger().log(Level.WARNING, "Failed to show Bedrock custom form: {0}",
                        ex.getMessage());
                return false;
            }
        }

        private String extractText(Component comp) {
            if (comp == null) {
                return "";
            }
            try {
                return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(comp);
            } catch (Throwable ex) {
                return comp.toString();
            }
        }

        private String extractBodyText(DialogBody body) {
            if (body == null) {
                return "";
            }

            return switch (body) {
                case io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody plainBody -> {
                    try {
                        Component component = plainBody.contents();
                        yield component != null ? extractText(component) : "";
                    } catch (Throwable ex) {
                        Loader.getPlugin(Loader.class).getLogger().log(Level.WARNING,
                                "Failed to extract PlainMessage body text: {0}", ex.getMessage());
                        yield "";
                    }
                }
                case io.papermc.paper.registry.data.dialog.body.ItemDialogBody itemBody -> {
                    try {
                        ItemStack item = itemBody.item();
                        if (item != null && item.getItemMeta() != null && item.getItemMeta().displayName() != null) {
                            yield extractText(item.getItemMeta().displayName());
                        }
                        yield item != null ? item.getType().name() : "Item";
                    } catch (Throwable ex) {
                        Loader.getPlugin(Loader.class).getLogger().log(Level.WARNING, "Failed to extract Item body text: {0}",
                                ex.getMessage());
                        yield "";
                    }
                }
                default ->
                    "";
            };
        }

        private String extractInputKey(DialogInput input) {
            if (input == null) {
                return "";
            }
            return input.key();
        }

        private String extractInputLabel(DialogInput input) {
            if (input == null) {
                return "入力";
            }

            Component label = switch (input) {
                case io.papermc.paper.registry.data.dialog.input.BooleanDialogInput booleanInput ->
                    booleanInput.label();
                case io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput numberRangeInput ->
                    numberRangeInput.label();
                case io.papermc.paper.registry.data.dialog.input.TextDialogInput textInput ->
                    textInput.label();
                case io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput singleOptionInput ->
                    singleOptionInput.label();
                default ->
                    null;
            };

            return label != null ? extractText(label) : "入力";
        }

        private String extractTextInitial(DialogInput input) {
            if (input == null) {
                return "";
            }
            if (input instanceof io.papermc.paper.registry.data.dialog.input.TextDialogInput textInput) {
                String initial = textInput.initial();
                return initial != null ? initial : "";
            }
            return "";
        }

        public static void showLongTextInput(Player player, String title, String prompt, String initialValue,
                int maxLength, BiConsumer<Player, String> onInput) {
            if (player == null) {
                return;
            }
            try {
                Builder b = DialogUI.builder()
                        .title(title)
                        .body(prompt)
                        .addTextInput("input", "入力", initialValue == null ? "" : initialValue,
                                maxLength <= 0 ? 8192 : maxLength, true)
                        .confirmation("保存", "キャンセル")
                        .onResponse((result, p) -> {
                            if (result.isConfirmed()) {
                                if (onInput != null) {
                                    onInput.accept(p, result.getText("input"));
                                }
                            }
                        });
                b.show(player);
            } catch (Throwable ex) {
                Loader.getPlugin(Loader.class).getLogger().log(Level.WARNING, "showLongTextInput failed: {0}",
                        ex.getMessage());
                DialogUI.showTextInput(player, title, prompt, (p, s) -> {
                    if (onInput != null) {
                        onInput.accept(p, s);
                    }
                });
            }
        }

        private boolean isTextInput(DialogInput input) {
            return input instanceof io.papermc.paper.registry.data.dialog.input.TextDialogInput;
        }

        private boolean isNumberInput(DialogInput input) {
            return input instanceof io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput;
        }

        private boolean isBoolInput(DialogInput input) {
            return input instanceof io.papermc.paper.registry.data.dialog.input.BooleanDialogInput;
        }

        private float extractMin(DialogInput input) {
            if (input instanceof io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput numberRangeInput) {
                return numberRangeInput.start();
            }
            return 0f;
        }

        private float extractMax(DialogInput input) {
            if (input instanceof io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput numberRangeInput) {
                return numberRangeInput.end();
            }
            return 100f;
        }

        private float extractStep(DialogInput input) {
            if (input instanceof io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput numberRangeInput) {
                Float step = numberRangeInput.step();
                return step != null ? step : 1f;
            }
            return 1f;
        }

        private float extractInitial(DialogInput input) {
            if (input instanceof io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput numberRangeInput) {
                Float initial = numberRangeInput.initial();
                return initial != null ? initial : extractMin(input);
            }
            return 0f;
        }

        private boolean extractBoolInitial(DialogInput input) {
            if (input instanceof io.papermc.paper.registry.data.dialog.input.BooleanDialogInput booleanInput) {
                return booleanInput.initial();
            }
            return false;
        }

        private Dialog build() {
            try {
                DialogBase.Builder baseBuilder = DialogBase.builder(title)
                        .canCloseWithEscape(canCloseWithEscape);

                if (!bodyComponents.isEmpty()) {
                    baseBuilder.body(bodyComponents);
                }

                if (!inputs.isEmpty()) {
                    baseBuilder.inputs(inputs);
                }

                if (externalTitle != null) {
                    baseBuilder.externalTitle(externalTitle);
                }

                DialogBase dialogBase = baseBuilder.build();

                DialogType type;
                String identifier = "dialog_" + (++keyCounter);
                if (yesButtonLabel != null && noButtonLabel == null) {
                    Key actionKey = Key.key("pexserver", identifier + "_yes");
                    ActionButton button = ActionButton.create(
                            Component.text(yesButtonLabel, TextColor.color(0x00FF00)),
                            null,
                            100,
                            DialogAction.customClick(actionKey, null));
                    type = DialogType.notice(button);
                } else if (yesButtonLabel != null && noButtonLabel != null) {
                    Key yesKey = Key.key("pexserver", identifier + "_yes");
                    Key noKey = Key.key("pexserver", identifier + "_no");
                    ActionButton yesButton = ActionButton.create(
                            Component.text(yesButtonLabel, TextColor.color(0x00FF00)),
                            null,
                            100,
                            DialogAction.customClick(yesKey, null));
                    ActionButton noButton = ActionButton.create(
                            Component.text(noButtonLabel, TextColor.color(0xFF0000)),
                            null,
                            100,
                            DialogAction.customClick(noKey, null));
                    type = DialogType.confirmation(yesButton, noButton);
                } else if (!actionButtons.isEmpty()) {
                    List<ActionButton> buttons = new ArrayList<>();
                    int index = 0;
                    for (ActionButtonData data : actionButtons) {
                        Key actionKey = Key.key("pexserver", identifier + "_action" + index);
                        ActionButton button = ActionButton.create(
                                Component.text(data.label, TextColor.color(data.color)),
                                data.tooltip != null ? Component.text(data.tooltip) : null,
                                100,
                                DialogAction.customClick(actionKey, null));
                        buttons.add(button);
                        index++;
                    }
                    type = DialogType.multiAction(buttons).build();
                } else {
                    Key actionKey = Key.key("pexserver", identifier + "_yes");
                    ActionButton button = ActionButton.create(
                            Component.text("OK", TextColor.color(0x00FF00)),
                            null,
                            100,
                            DialogAction.customClick(actionKey, null));
                    type = DialogType.notice(button);
                }

                return Dialog.create(builder -> builder
                        .empty()
                        .base(dialogBase)
                        .type(type));

            } catch (Exception e) {
                Loader.getPlugin(Loader.class).getLogger().log(Level.SEVERE, "Failed to build dialog", e);
                return null;
            }
        }

        private static class ActionButtonData {

            final String label;
            final int color;
            final String tooltip;

            ActionButtonData(String label, int color, String tooltip) {
                this.label = label;
                this.color = color;
                this.tooltip = tooltip;
            }
        }
    }

    private record HandlerData(BiConsumer<DialogResult, Player> handler, List<String> inputKeys) {
    }

    /**
     * ダイアログからの応答をカプセル化するオブジェクト。
     * <p>Java 版の {@link DialogResponseView} か、Bedrock 版の
     * {@code CustomFormResponse} のどちらかを保持し、
     * キーや入力値を抽象化して取得するための便利メソッドを提供します。</p>
     */
    public static class DialogResult {

        private final Key clickedKey;
        private final DialogResponseView responseView;
        private final boolean isConfirmed;
        private final org.geysermc.cumulus.response.CustomFormResponse bedrockResponse;
        private final Map<String, Integer> bedrockInputIndexMap;
        private final int actionIndex;

        public DialogResult(Key clickedKey, DialogResponseView responseView, boolean isConfirmed) {
            this(clickedKey, responseView, isConfirmed, -1);
        }

        public DialogResult(Key clickedKey, DialogResponseView responseView, boolean isConfirmed, int actionIndex) {
            this.clickedKey = clickedKey;
            this.responseView = responseView;
            this.isConfirmed = isConfirmed;
            this.bedrockResponse = null;
            this.bedrockInputIndexMap = null;
            this.actionIndex = actionIndex;
        }

        public DialogResult(boolean isConfirmed,
                org.geysermc.cumulus.response.CustomFormResponse bedrockResponse,
                Map<String, Integer> bedrockInputIndexMap) {
            this.clickedKey = null;
            this.responseView = null;
            this.isConfirmed = isConfirmed;
            this.bedrockResponse = bedrockResponse;
            this.bedrockInputIndexMap = bedrockInputIndexMap;
            this.actionIndex = -1;
        }

        /**
         * ユーザーが "Yes" 相当のボタンを押したかどうかを返します。
         *
         * @return 確認済みなら true
         */
        public boolean isConfirmed() {
            return isConfirmed;
        }

        /**
         * クリックされたボタンのキーを取得します（Java 版のみ）。
         *
         * @return クリックされたボタンキー、なければ null
         */
        public Key getClickedKey() {
            return clickedKey;
        }

        public int getActionIndex() {
            if (actionIndex >= 0) {
                return actionIndex;
            }
            if (clickedKey == null) {
                return -1;
            }
            String value = clickedKey.value();
            int marker = value.lastIndexOf("_action");
            if (marker < 0) {
                return -1;
            }
            try {
                return Integer.parseInt(value.substring(marker + "_action".length()));
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        /**
         * 入力欄のテキスト値を取得します。
         *
         * @param key ビルダーで指定した入力欄キー
         * @return 入力された文字列、存在しなければ空文字
         */
        public String getText(String key) {
            // Bedrock
            if (bedrockResponse != null && bedrockInputIndexMap != null) {
                Integer index = bedrockInputIndexMap.get(key);
                if (index != null) {
                    try {
                        return bedrockResponse.asInput(index);
                    } catch (RuntimeException e) {
                        return "";
                    }
                }
            }
            // Java
            if (responseView == null) {
                return "";
            }
            String value = responseView.getText(key);
            return value != null ? value : "";
        }

        /**
         * スライダーなど数値入力の結果を取得します。
         *
         * @param key 入力欄のキー
         * @return 選択値、存在しない場合は 0f
         */
        public Float getNumber(String key) {
            // Bedrock対応
            if (bedrockResponse != null && bedrockInputIndexMap != null) {
                Integer index = bedrockInputIndexMap.get(key);
                if (index != null) {
                    try {
                        return bedrockResponse.asSlider(index);
                    } catch (RuntimeException e) {
                        return 0f;
                    }
                }
            }
            // Java版
            if (responseView == null) {
                return 0f;
            }
            return responseView.getFloat(key);
        }

        /**
         * トグル入力の結果を取得します。
         *
         * @param key 入力欄のキー
         * @return true/false、存在しない場合は false
         */
        public Boolean getBool(String key) {
            // Bedrock対応
            if (bedrockResponse != null && bedrockInputIndexMap != null) {
                Integer index = bedrockInputIndexMap.get(key);
                if (index != null) {
                    try {
                        return bedrockResponse.asToggle(index);
                    } catch (RuntimeException e) {
                        return false;
                    }
                }
            }
            // Java版
            if (responseView == null) {
                return false;
            }
            return responseView.getBoolean(key);
        }

        public Integer getChoiceIndex(String key) {
            if (responseView == null) {
                return 0;
            }
            try {
                Float value = responseView.getFloat(key);
                return value != null ? value.intValue() : 0;
            } catch (RuntimeException e) {
                return 0;
            }
        }

        /**
         * 内部で保持している {@link DialogResponseView} を直接返します。
         * Java 版専用。
         *
         * @return レスポンスビュー、無ければ null
         */
        public DialogResponseView getResponseView() {
            return responseView;
        }
    }

    /**
     * Paper のイベントリスナー。カスタムダイアログクリックを受け取り
     * {@link DialogResult} に変換して登録済みハンドラへ渡します。
     */
    public static class DialogListener implements Listener {

        @EventHandler
        /**
         * ダイアログ上のボタンがクリックされたときに呼び出されます。
         * 内部でプレイヤーを取得し、対応する登録ハンドラがあれば処理します。
         *
         * @param event クリックイベント
         */
        public void onDialogClick(PlayerCustomClickEvent event) {
            Player player = null;
            try {
                if (event
                        .getCommonConnection() instanceof io.papermc.paper.connection.PlayerGameConnection playerConnection) {
                    player = playerConnection.getPlayer();
                }
            } catch (Exception e) {
                Loader.getPlugin(Loader.class).getLogger().log(Level.WARNING, "Failed to get player from dialog event: {0}",
                        e.getMessage());
            }

            if (player == null) {
                return;
            }

            UUID playerId = player.getUniqueId();
            HandlerData handlerData = responseHandlers.get(playerId);
            if (handlerData == null) {
                return;
            }

            Key clickedKey = event.getIdentifier();
            DialogResponseView responseView = event.getDialogResponseView();
            boolean isConfirmed = clickedKey != null && clickedKey.value().contains("_yes");
            DialogResult result = new DialogResult(clickedKey, responseView, isConfirmed);
            logJavaDialogResponse(player, clickedKey, responseView, isConfirmed, handlerData.inputKeys());

            try {
                handlerData.handler().accept(result, player);
            } catch (Exception e) {
                Loader.getPlugin(Loader.class).getLogger().log(Level.WARNING, "Error handling dialog response: {0}",
                        e.getMessage());
            }
            if (responseHandlers.get(playerId) == handlerData) {
                responseHandlers.remove(playerId);
            }
        }

        private void logJavaDialogResponse(Player player, Key clickedKey, DialogResponseView responseView,
                boolean confirmed, List<String> keys) {
            StringBuilder builder = new StringBuilder("[DialogUI] Java dialog response player=")
                    .append(player.getName())
                    .append(" clicked=")
                    .append(clickedKey == null ? "null" : clickedKey.asString())
                    .append(" confirmed=")
                    .append(confirmed)
                    .append(" payload=")
                    .append(responseView == null ? "null" : responseView.payload());
            if (keys != null && !keys.isEmpty() && responseView != null) {
                for (String key : keys) {
                    builder.append(" key[").append(key).append("]={text='")
                            .append(responseView.getText(key))
                            .append("', float=")
                            .append(responseView.getFloat(key))
                            .append(", bool=")
                            .append(responseView.getBoolean(key))
                            .append("}");
                }
            }
            Loader.getPlugin(Loader.class).getLogger().info(builder.toString());
        }
    }

    /**
     * プラグイン初期化時に呼び出してイベントリスナーを登録します。
     */
    public static void register() {
        Loader.getPlugin(Loader.class).getServer().getPluginManager().registerEvents(new DialogListener(), Loader.getPlugin(Loader.class));
    }

    /**
     * 指定プレイヤーに開いているダイアログを閉じさせ、
     * 登録済みの応答ハンドラを削除します。
     *
     * @param player 対象プレイヤー
     */
    public static void closeDialog(Player player) {
        player.closeDialog();
        responseHandlers.remove(player.getUniqueId());
    }

    /**
     * 短い通知ダイアログを表示します (OK ボタンのみ)。
     *
     * @param player  表示対象プレイヤー
     * @param title   タイトル
     * @param message 本文メッセージ
     */
    public static void showNotice(Player player, String title, String message) {
        builder()
                .title(title)
                .body(message)
                .notice("OK")
                .show(player);
    }

    /**
     * 確認ダイアログを表示します。
     * "はい" が押された場合にコールバックが呼び出されます。
     *
     * @param player    対象プレイヤー
     * @param title     タイトル
     * @param message   本文メッセージ
     * @param onConfirm 確認時の処理（null可）
     */
    public static void showConfirmation(Player player, String title, String message, Consumer<Player> onConfirm) {
        builder()
                .title(title)
                .body(message)
                .confirmation("はい", "いいえ")
                .onResponse((result, p) -> {
                    if (result.isConfirmed() && onConfirm != null) {
                        onConfirm.accept(p);
                    }
                })
                .show(player);
    }

    /**
     * 単一行テキスト入力ダイアログを表示します。
     *
     * @param player  対象プレイヤー
     * @param title   タイトル
     * @param prompt  表示するプロンプト
     * @param onInput 入力後のコールバック
     */
    public static void showTextInput(Player player, String title, String prompt, BiConsumer<Player, String> onInput) {
        showTextInput(player, title, prompt, "", onInput);
    }

    public static void showTextInput(Player player, String title, String prompt, String initialValue,
            BiConsumer<Player, String> onInput) {
        builder()
                .title(title)
                .body(prompt)
                .addTextInput("input", "入力してください", initialValue == null ? "" : initialValue)
                .confirmation("送信", "キャンセル")
                .onResponse((result, p) -> {
                    if (result.isConfirmed() && onInput != null) {
                        String text = result.getText("input");
                        onInput.accept(p, text);
                    }
                })
                .show(player);
    }

    /**
     * 長文テキスト入力ダイアログを表示します。
     * 初期値・最大長・複数行表示に対応しています。
     *
     * @param player       対象プレイヤー
     * @param title        タイトル
     * @param prompt       表示するプロンプト
     * @param initialValue 初期文字列
     * @param maxLength    最大文字数（0 以下で規定値8192）
     * @param onInput      入力後のコールバック
     */
    public static void showLongTextInput(Player player, String title, String prompt, String initialValue, int maxLength,
            BiConsumer<Player, String> onInput) {
        builder()
                .title(title)
                .body(prompt)
                .addTextInput("input", "入力", initialValue == null ? "" : initialValue,
                        maxLength <= 0 ? 8192 : maxLength, true)
                .confirmation("保存", "キャンセル")
                .onResponse((result, p) -> {
                    if (result.isConfirmed() && onInput != null) {
                        String text = result.getText("input");
                        onInput.accept(p, text == null ? "" : text);
                    }
                })
                .show(player);
    }
}
