# DialogUI 完全ガイド

PaperMC Dialog API を使いやすくラップした DialogUI システムの完全なドキュメントです。



---

## 概要

DialogUI は PaperMC 1.21.7+ の Dialog API をラップした、使いやすいダイアログシステムです。

### 主な機能

- ✅ **通知ダイアログ** - シンプルなメッセージ表示
- ✅ **確認ダイアログ** - はい/いいえの選択
- ✅ **入力フォーム** - テキスト、数値、ブール値の入力
- ✅ **カスタムアクション** - 複数のボタンを持つダイアログ
- ✅ **アイテム表示** - ダイアログ内にアイテムを表示
- ✅ **Builder パターン** - 直感的で読みやすいAPI

### システム要件

- **PaperMC 1.21.7 以上**
- **Java 17 以上**

---

## セットアップ

### 1. リスナーの登録

プラグインの `onEnable()` メソッドで一度だけ登録してください：

```java
@Override
public void onEnable() {
    DialogUI.register();
}
```

### 2. 使用準備完了

これで DialogUI が使用可能になります！

---

## 基本的な使い方

### シンプルな通知

```java
DialogUI.showNotice(player, "タイトル", "メッセージ内容");
```

### 確認ダイアログ

```java
DialogUI.showConfirmation(player, "確認", "本当に実行しますか?", confirmedPlayer -> {
    confirmedPlayer.sendMessage("実行しました！");
});
```

### テキスト入力

```java
DialogUI.showTextInput(player, "名前入力", "あなたの名前を入力してください", (p, text) -> {
    p.sendMessage("入力された名前: " + text);
});
```

---

## 詳細なAPI

### Builder の作成

```java
DialogUI.builder()
    .title("ダイアログタイトル")
    // ... 設定を追加
    .show(player);
```

### タイトルとボディ

#### タイトル設定

```java
.title("タイトル")  // String版
.title(Component.text("タイトル").color(NamedTextColor.GOLD))  // Component版
```

#### ボディ（本文）の追加

```java
// テキストメッセージ
.body("これはメッセージです")

// Component版
.body(Component.text("カラフルなメッセージ").color(NamedTextColor.AQUA))

// 複数行
.body("1行目")
.body("2行目")
.body("3行目")
```

#### アイテムの表示

```java
// ItemStack を表示
.bodyItem(new ItemStack(Material.DIAMOND))

// Material から表示
.bodyItem(Material.GOLD_INGOT)
```

### 入力フィールド

#### テキスト入力

```java
.addTextInput("key", "ラベル")
```

**詳細設定版：**
```java
DialogInput.text("key", Component.text("ラベル"))
    .initial("初期値")
    .maxLength(50)
    .width(300)
    .build()
```

#### 数値入力（スライダー）

```java
.addNumberInput("level", "レベル", 1, 100)
```

**詳細設定版：**
```java
.addNumberInput("exp", "経験値", 0, 1000, 10, 500)
// キー, ラベル, 最小値, 最大値, ステップ, 初期値
```

#### ブール値入力（チェックボックス）

```java
.addBoolInput("vip", "VIP会員")
```

### ダイアログタイプ

#### 通知タイプ（1つのボタン）

```java
.notice("OK")
```

#### 確認タイプ（2つのボタン）

```java
.confirmation("はい", "いいえ")
```

#### カスタムアクション（複数ボタン）

```java
.addAction("オプション1", 0x00FF00)           // 緑
.addAction("オプション2", 0xFFFF00)           // 黄
.addAction("オプション3", 0xFF0000, "説明")  // 赤 + ツールチップ
```

### 応答ハンドリング

```java
.onResponse((result, player) -> {
    // 確認/キャンセルの判定
    if (result.isConfirmed()) {
        player.sendMessage("確認されました");
    } else {
        player.sendMessage("キャンセルされました");
    }
    
    // 入力値の取得
    String name = result.getText("name");
    int level = result.getNumber("level").intValue();
    boolean vip = result.getBool("vip");
})
```

### その他の設定

#### ESCキーで閉じる設定

```java
.canCloseWithEscape(false)  // ESCキーで閉じられなくする
```

#### 外部タイトル

```java
.externalTitle("ボタンに表示されるタイトル")
```

---

## 実践的なサンプル

### サンプル1: プレイヤー情報登録フォーム

```java
public void showPlayerRegistrationForm(Player player) {
    DialogUI.builder()
        .title("プレイヤー登録")
        .body("あなたの情報を入力してください")
        .addTextInput("name", "プレイヤー名")
        .addTextInput("email", "メールアドレス")
        .addNumberInput("age", "年齢", 1, 100)
        .addBoolInput("newsletter", "ニュースレターを受け取る")
        .confirmation("登録", "キャンセル")
        .onResponse((result, p) -> {
            if (result.isConfirmed()) {
                String name = result.getText("name");
                String email = result.getText("email");
                int age = result.getNumber("age").intValue();
                boolean newsletter = result.getBool("newsletter");
                
                // データベースに保存する処理
                savePlayerData(p, name, email, age, newsletter);
                
                p.sendMessage("§a登録完了: " + name);
            } else {
                p.sendMessage("§c登録をキャンセルしました");
            }
        })
        .show(player);
}
```

### サンプル2: アイテムショップ

```java
public void showItemShop(Player player) {
    DialogUI.builder()
        .title("アイテムショップ")
        .body("購入するアイテムを選択してください")
        .bodyItem(Material.DIAMOND)
        .body("ダイヤモンド - 100コイン")
        .bodyItem(Material.GOLD_INGOT)
        .body("金インゴット - 50コイン")
        .addAction("ダイヤを購入", 0x00FFFF)
        .addAction("金を購入", 0xFFD700)
        .addAction("キャンセル", 0xFF0000)
        .onResponse((result, p) -> {
            String action = result.getClickedKey().value();
            if (action.contains("action0")) {
                purchaseItem(p, Material.DIAMOND, 100);
            } else if (action.contains("action1")) {
                purchaseItem(p, Material.GOLD_INGOT, 50);
            }
        })
        .show(player);
}
```

### サンプル3: ゲーム設定メニュー

```java
public void showGameSettings(Player player) {
    DialogUI.builder()
        .title("ゲーム設定")
        .body("ゲームの設定を変更します")
        .addNumberInput("difficulty", "難易度", 1, 10, 1, 5)
        .addNumberInput("rounds", "ラウンド数", 1, 20, 1, 10)
        .addBoolInput("pvp", "PvPを有効にする")
        .addBoolInput("friendly_fire", "フレンドリーファイア")
        .addBoolInput("auto_start", "自動開始")
        .confirmation("設定を保存", "キャンセル")
        .onResponse((result, p) -> {
            if (result.isConfirmed()) {
                int difficulty = result.getNumber("difficulty").intValue();
                int rounds = result.getNumber("rounds").intValue();
                boolean pvp = result.getBool("pvp");
                boolean friendlyFire = result.getBool("friendly_fire");
                boolean autoStart = result.getBool("auto_start");
                
                // 設定を保存
                GameConfig config = new GameConfig();
                config.setDifficulty(difficulty);
                config.setRounds(rounds);
                config.setPvpEnabled(pvp);
                config.setFriendlyFire(friendlyFire);
                config.setAutoStart(autoStart);
                
                saveGameConfig(config);
                p.sendMessage("§a設定を保存しました");
            }
        })
        .show(player);
}
```

### サンプル4: 確認ダイアログ（破壊的な操作）

```java
public void confirmDeleteWorld(Player player, String worldName) {
    DialogUI.builder()
        .title("⚠ 警告")
        .body("ワールド「" + worldName + "」を削除しようとしています")
        .body("この操作は取り消せません")
        .canCloseWithEscape(false)  // ESCで閉じられない
        .confirmation("削除する", "キャンセル")
        .onResponse((result, p) -> {
            if (result.isConfirmed()) {
                // 最終確認
                DialogUI.builder()
                    .title("最終確認")
                    .body("本当に削除しますか？")
                    .confirmation("はい、削除します", "いいえ")
                    .onResponse((result2, p2) -> {
                        if (result2.isConfirmed()) {
                            deleteWorld(worldName);
                            p2.sendMessage("§cワールドを削除しました");
                        }
                    })
                    .show(p);
            } else {
                p.sendMessage("§aキャンセルしました");
            }
        })
        .show(player);
}
```

### サンプル5: ダイナミック選択肢

```java
public void showDifficultySelector(Player player) {
    DialogUI.builder()
        .title("難易度選択")
        .body("ゲームの難易度を選択してください")
        .addAction("イージー", 0x00FF00, "初心者向け")
        .addAction("ノーマル", 0xFFFF00, "通常の難易度")
        .addAction("ハード", 0xFF8800, "上級者向け")
        .addAction("エクストリーム", 0xFF0000, "最高難易度")
        .onResponse((result, p) -> {
            String key = result.getClickedKey().value();
            
            String difficulty;
            if (key.contains("action0")) {
                difficulty = "Easy";
            } else if (key.contains("action1")) {
                difficulty = "Normal";
            } else if (key.contains("action2")) {
                difficulty = "Hard";
            } else {
                difficulty = "Extreme";
            }
            
            setPlayerDifficulty(p, difficulty);
            p.sendMessage("§a難易度を " + difficulty + " に設定しました");
        })
        .show(player);
}
```

---

## ベストプラクティス

### 1. エラーハンドリング

```java
.onResponse((result, player) -> {
    try {
        String name = result.getText("name");
        if (name == null || name.isEmpty()) {
            player.sendMessage("§c名前を入力してください");
            return;
        }
        
        // 処理を続行
        processName(player, name);
    } catch (Exception e) {
        player.sendMessage("§cエラーが発生しました");
        getLogger().severe("Dialog error: " + e.getMessage());
    }
})
```

### 2. 入力値のバリデーション

```java
.onResponse((result, player) -> {
    if (!result.isConfirmed()) {
        return;  // キャンセルされた場合は何もしない
    }
    
    String email = result.getText("email");
    if (!isValidEmail(email)) {
        player.sendMessage("§c無効なメールアドレスです");
        // 再度ダイアログを表示
        showRegistrationForm(player);
        return;
    }
    
    // 有効な場合は処理を続行
})
```

### 3. 複数ステップのダイアログ

```java
public void showMultiStepWizard(Player player) {
    // ステップ1: 基本情報
    DialogUI.builder()
        .title("セットアップ (1/3)")
        .body("基本情報を入力してください")
        .addTextInput("name", "名前")
        .confirmation("次へ", "キャンセル")
        .onResponse((result1, p) -> {
            if (!result1.isConfirmed()) return;
            
            String name = result1.getText("name");
            
            // ステップ2: 詳細設定
            DialogUI.builder()
                .title("セットアップ (2/3)")
                .body("詳細設定を行います")
                .addNumberInput("level", "レベル", 1, 100)
                .confirmation("次へ", "戻る")
                .onResponse((result2, p2) -> {
                    if (!result2.isConfirmed()) {
                        showMultiStepWizard(p2);  // 戻る
                        return;
                    }
                    
                    int level = result2.getNumber("level").intValue();
                    
                    // ステップ3: 確認
                    showFinalConfirmation(p2, name, level);
                })
                .show(p);
        })
        .show(player);
}
```

### 4. 非同期処理との組み合わせ

```java
.onResponse((result, player) -> {
    if (!result.isConfirmed()) return;
    
    String username = result.getText("username");
    
    // 非同期でデータベース処理
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        boolean exists = checkUserExists(username);
        
        // メインスレッドに戻して結果を表示
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (exists) {
                player.sendMessage("§cそのユーザー名は既に使用されています");
            } else {
                registerUser(player, username);
                player.sendMessage("§a登録完了！");
            }
        });
    });
})
```

---

## トラブルシューティング

### Q: ダイアログが表示されない

**A:** 以下を確認してください：

1. `DialogUI.register()` を `onEnable()` で呼び出していますか？
2. PaperMC 1.21.7 以上を使用していますか？
3. プレイヤーはログイン済みですか？

```java
// 確認コード
if (!player.isOnline()) {
    return;
}
```

### Q: 入力値が取得できない

**A:** 正しいキー名を使用していますか？

```java
// 入力フィールド定義時
.addTextInput("player_name", "名前")

// 取得時（同じキーを使用）
String name = result.getText("player_name");  // ✅ 正しい
String name = result.getText("name");         // ❌ 間違い
```

### Q: responseView が null

**A:** 入力フィールドがないダイアログでは null になります。

```java
.onResponse((result, player) -> {
    // 入力フィールドがある場合のみ取得
    if (result.getResponseView() != null) {
        String text = result.getText("key");
    }
})
```

### Q: ダイアログが勝手に閉じる

**A:** ESCキーで閉じられている可能性があります。

```java
.canCloseWithEscape(false)  // ESCで閉じられないようにする
```

### Q: エラー: "Failed to build dialog"

**A:** 以下を確認してください：

1. 通知/確認ダイアログの場合、`.notice()` または `.confirmation()` を呼び出していますか？
2. カスタムアクションの場合、少なくとも1つのアクションを追加していますか？
3. タイトルを設定していますか？

```java
// ❌ 間違い（ダイアログタイプが未設定）
DialogUI.builder()
    .title("Test")
    .show(player);

// ✅ 正しい
DialogUI.builder()
    .title("Test")
    .notice("OK")  // ダイアログタイプを設定
    .show(player);
```

---

## API リファレンス

### DialogUI クラス

#### 静的メソッド

| メソッド | 説明 |
|---------|------|
| `builder()` | 新しいビルダーを作成 |
| `register()` | リスナーを登録（onEnableで呼び出す） |
| `showNotice(player, title, message)` | シンプルな通知を表示 |
| `showConfirmation(player, title, message, callback)` | 確認ダイアログを表示 |
| `showTextInput(player, title, prompt, callback)` | テキスト入力ダイアログを表示 |
| `closeDialog(player)` | プレイヤーのダイアログを閉じる |

### Builder クラス

#### メソッド

| メソッド | 説明 |
|---------|------|
| `title(String)` | タイトルを設定 |
| `body(String)` | 本文を追加 |
| `bodyItem(ItemStack)` | アイテムを表示 |
| `addTextInput(key, label)` | テキスト入力を追加 |
| `addNumberInput(key, label, min, max)` | 数値入力を追加 |
| `addBoolInput(key, label)` | チェックボックスを追加 |
| `notice(label)` | 通知タイプに設定 |
| `confirmation(yes, no)` | 確認タイプに設定 |
| `addAction(label, color)` | カスタムアクションを追加 |
| `onResponse(handler)` | 応答ハンドラを設定 |
| `show(player)` | ダイアログを表示 |

### DialogResult クラス

#### メソッド

| メソッド | 戻り値 | 説明 |
|---------|--------|------|
| `isConfirmed()` | boolean | 確認されたか |
| `getClickedKey()` | Key | クリックされたボタンのキー |
| `getText(key)` | String | テキスト入力の値 |
| `getNumber(key)` | Float | 数値入力の値 |
| `getBool(key)` | Boolean | ブール値入力の値 |
| `getChoiceIndex(key)` | Integer | 選択肢のインデックス |
| `getResponseView()` | DialogResponseView | 生の応答ビュー |

---

## まとめ

DialogUI は PaperMC の Dialog API を簡単に使えるようにするラッパーです。

- ✅ Builder パターンで直感的
- ✅ 豊富な入力タイプ
- ✅ 柔軟なカスタマイズ
- ✅ エラーハンドリングが簡単

詳細な情報は [PaperMC Dialog API ドキュメント](https://docs.papermc.io/paper/dev/dialogs/) を参照してください。
