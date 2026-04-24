# ChestUI 完全ガイド

インベントリベースのメニューシステム ChestUI の完全なドキュメントです。



---

## 概要

ChestUI は Bukkit/Spigot のインベントリシステムをラップした、使いやすいメニューシステムです。

### 主な機能

- ✅ **カスタムメニュー** - チェスト風のGUIメニュー
- ✅ **クリック可能なボタン** - アイテムをボタンとして配置
- ✅ **動的更新** - メニューを動的に更新可能
- ✅ **Builder パターン** - 直感的で読みやすいAPI
- ✅ **チャット入力** - テキスト入力をサポート
- ✅ **互換性** - 古いバージョンでも動作

### システム要件

- **Spigot/Paper 1.16 以上**
- **Java 17 以上**

---

## セットアップ

### 1. リスナーの登録

プラグインの `onEnable()` メソッドで一度だけ登録してください：

```java
@Override
public void onEnable() {
    ChestUI.register();
}
```

### 2. 使用準備完了

これで ChestUI が使用可能になります！

---

## 基本的な使い方

### シンプルなメニュー

```java
ChestUI.builder()
    .title("メニュー")
    .size(27)  // 3行 (9 * 3)
    .addButtonAt(13, "ボタン", Material.DIAMOND)
    .then((result, player) -> {
        if (result.slot == 13) {
            player.sendMessage("ダイヤモンドボタンがクリックされました");
        }
    })
    .show(player);
```

### ボタンの配置

```java
ChestUI.builder()
    .title("アイテムショップ")
    .size(27)
    .addButtonAt(10, "剣を購入", Material.DIAMOND_SWORD, "100コイン")
    .addButtonAt(12, "防具を購入", Material.DIAMOND_CHESTPLATE, "200コイン")
    .addButtonAt(14, "食料を購入", Material.COOKED_BEEF, "10コイン")
    .addButtonAt(16, "閉じる", Material.BARRIER)
    .then((result, player) -> {
        switch (result.slot) {
            case 10:
                purchaseItem(player, "sword", 100);
                break;
            case 12:
                purchaseItem(player, "armor", 200);
                break;
            case 14:
                purchaseItem(player, "food", 10);
                break;
            case 16:
                player.closeInventory();
                break;
        }
    })
    .show(player);
```

---

## 詳細なAPI

### Builder の作成

```java
ChestUI.builder()
    .title("タイトル")
    .size(54)  // 6行 (9 * 6)
    // ... 設定を追加
    .then((result, player) -> {
        // クリックハンドラ
    })
    .show(player);
```

### サイズ設定

インベントリのサイズは9の倍数で、最大54（6行）まで設定できます：

```java
.size(9)   // 1行
.size(18)  // 2行
.size(27)  // 3行 (デフォルト)
.size(36)  // 4行
.size(45)  // 5行
.size(54)  // 6行
```

### ボタンの追加

#### 基本的なボタン

```java
.addButtonAt(slot, "ラベル", Material.DIAMOND)
```

#### 説明（Lore）付きボタン

```java
.addButtonAt(slot, "ラベル", Material.DIAMOND, "説明文")
```

#### ItemStack を使用

```java
ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
ItemMeta meta = item.getItemMeta();
meta.setDisplayName("カスタム剣");
meta.setLore(Arrays.asList("攻撃力 +10", "レア度: レジェンダリー"));
item.setItemMeta(meta);

.addButtonAt(slot, "カスタムボタン", item)
```

### スロット番号の計算

```
 0  1  2  3  4  5  6  7  8
 9 10 11 12 13 14 15 16 17
18 19 20 21 22 23 24 25 26
27 28 29 30 31 32 33 34 35
36 37 38 39 40 41 42 43 44
45 46 47 48 49 50 51 52 53
```

**中央のボタン（3行メニュー）:** スロット 13
**中央のボタン（6行メニュー）:** スロット 22

### クリックハンドラ

```java
.then((result, player) -> {
    // result.success - 常に true
    // result.cancelled - キャンセルされたか
    // result.slot - クリックされたスロット番号
    
    if (result.slot != null) {
        player.sendMessage("スロット " + result.slot + " がクリックされました");
    }
})
```

### 動的更新

#### ボタンの更新

```java
// MenuHandler を保存
ChestUI.MenuHandler handler = ChestUI.builder()
    .title("更新可能メニュー")
    .size(27)
    .addButtonAt(13, "クリック数: 0", Material.STONE)
    .then((result, player) -> {
        // ハンドラ処理
    })
    .show(player);

// 後で更新
int clicks = 0;
handler.getMenu().updateButton(13, "クリック数: " + (++clicks), Material.DIAMOND, "");
```

#### ボタンの削除

```java
menu.removeButton(13);
```

#### すべてのボタンを更新

```java
menu.refreshAll();
```

---

## 実践的なサンプル

### サンプル1: ページ付きメニュー

```java
public void showPagedMenu(Player player, int page) {
    List<String> items = getAllItems();
    int itemsPerPage = 28;  // 4行 * 7列
    int startIndex = page * itemsPerPage;
    int endIndex = Math.min(startIndex + itemsPerPage, items.size());
    
    ChestUI.Builder builder = ChestUI.builder()
        .title("アイテム一覧 (ページ " + (page + 1) + ")")
        .size(54);
    
    // アイテムを配置
    int slot = 10;
    for (int i = startIndex; i < endIndex; i++) {
        String item = items.get(i);
        builder.addButtonAt(slot, item, Material.PAPER);
        slot++;
        if ((slot + 1) % 9 == 0) slot += 2;  // 端をスキップ
    }
    
    // 前へボタン
    if (page > 0) {
        builder.addButtonAt(45, "前のページ", Material.ARROW);
    }
    
    // 次へボタン
    if (endIndex < items.size()) {
        builder.addButtonAt(53, "次のページ", Material.ARROW);
    }
    
    // 閉じるボタン
    builder.addButtonAt(49, "閉じる", Material.BARRIER);
    
    builder.then((result, p) -> {
        if (result.slot == 45 && page > 0) {
            showPagedMenu(p, page - 1);
        } else if (result.slot == 53 && endIndex < items.size()) {
            showPagedMenu(p, page + 1);
        } else if (result.slot == 49) {
            p.closeInventory();
        } else if (result.slot != null && result.slot >= 10) {
            // アイテムをクリック
            int index = calculateItemIndex(result.slot, page, itemsPerPage);
            if (index < items.size()) {
                handleItemClick(p, items.get(index));
            }
        }
    }).show(player);
}
```

### サンプル2: 確認メニュー

```java
public void showConfirmationMenu(Player player, String action) {
    ChestUI.builder()
        .title("確認: " + action)
        .size(27)
        .addButtonAt(11, "確認", Material.GREEN_WOOL, "クリックして確認")
        .addButtonAt(15, "キャンセル", Material.RED_WOOL, "クリックしてキャンセル")
        .then((result, p) -> {
            if (result.slot == 11) {
                performAction(p, action);
                p.sendMessage("§a実行しました");
            } else if (result.slot == 15) {
                p.sendMessage("§cキャンセルしました");
            }
            p.closeInventory();
        })
        .show(player);
}
```

### サンプル3: 設定メニュー

```java
public void showSettingsMenu(Player player) {
    PlayerSettings settings = getPlayerSettings(player);
    
    ChestUI.Builder builder = ChestUI.builder()
        .title("設定")
        .size(27);
    
    // PvP設定
    Material pvpMaterial = settings.isPvpEnabled() ? Material.GREEN_WOOL : Material.RED_WOOL;
    String pvpStatus = settings.isPvpEnabled() ? "有効" : "無効";
    builder.addButtonAt(10, "PvP: " + pvpStatus, pvpMaterial);
    
    // チャット設定
    Material chatMaterial = settings.isChatEnabled() ? Material.GREEN_WOOL : Material.RED_WOOL;
    String chatStatus = settings.isChatEnabled() ? "有効" : "無効";
    builder.addButtonAt(12, "チャット: " + chatStatus, chatMaterial);
    
    // 音楽設定
    Material musicMaterial = settings.isMusicEnabled() ? Material.GREEN_WOOL : Material.RED_WOOL;
    String musicStatus = settings.isMusicEnabled() ? "有効" : "無効";
    builder.addButtonAt(14, "音楽: " + musicStatus, musicMaterial);
    
    builder.then((result, p) -> {
        if (result.slot == 10) {
            settings.setPvpEnabled(!settings.isPvpEnabled());
            showSettingsMenu(p);  // メニューを再表示
        } else if (result.slot == 12) {
            settings.setChatEnabled(!settings.isChatEnabled());
            showSettingsMenu(p);
        } else if (result.slot == 14) {
            settings.setMusicEnabled(!settings.isMusicEnabled());
            showSettingsMenu(p);
        }
    }).show(player);
}
```

### サンプル4: テレポートメニュー

```java
public void showTeleportMenu(Player player) {
    Map<String, Location> warps = getWarps();
    
    ChestUI.Builder builder = ChestUI.builder()
        .title("テレポート先を選択")
        .size(27);
    
    int slot = 10;
    for (Map.Entry<String, Location> entry : warps.entrySet()) {
        String name = entry.getKey();
        Location loc = entry.getValue();
        
        builder.addButtonAt(slot, name, Material.ENDER_PEARL,
            String.format("X: %d, Y: %d, Z: %d", 
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        slot++;
    }
    
    builder.then((result, p) -> {
        if (result.slot != null && result.slot >= 10) {
            int index = result.slot - 10;
            String warpName = new ArrayList<>(warps.keySet()).get(index);
            Location destination = warps.get(warpName);
            
            p.teleport(destination);
            p.sendMessage("§a" + warpName + " にテレポートしました");
            p.closeInventory();
        }
    }).show(player);
}
```

### サンプル5: チャット入力との組み合わせ

```java
public void showRenameMenu(Player player, ItemStack item) {
    ChestUI.builder()
        .title("アイテムの名前変更")
        .size(27)
        .addButtonAt(11, "現在の名前", item.clone())
        .addButtonAt(15, "名前を変更", Material.NAME_TAG, "クリックして新しい名前を入力")
        .then((result, p) -> {
            if (result.slot == 15) {
                p.closeInventory();
                
                // チャット入力を開く
                ChestUI.openChat(p, "新しい名前を入力してください:", "", newName -> {
                    if (newName != null && !newName.isEmpty()) {
                        ItemMeta meta = item.getItemMeta();
                        meta.setDisplayName(newName);
                        item.setItemMeta(meta);
                        
                        p.sendMessage("§aアイテム名を変更しました: " + newName);
                    } else {
                        p.sendMessage("§cキャンセルしました");
                    }
                });
            }
        })
        .show(player);
}
```

### サンプル6: 動的に更新されるメニュー

```java
public void showCountdownMenu(Player player) {
    ChestUI.MenuHandler handler = ChestUI.builder()
        .title("カウントダウン")
        .size(27)
        .addButtonAt(13, "残り時間: 10", Material.CLOCK)
        .then((result, p) -> {
            // クリック処理
        })
        .show(player);
    
    // カウントダウンタスク
    new BukkitRunnable() {
        int countdown = 10;
        
        @Override
        public void run() {
            if (countdown <= 0) {
                player.closeInventory();
                player.sendMessage("§a時間切れ！");
                cancel();
                return;
            }
            
            // メニューを更新
            ChestUI menu = handler.getMenu();
            if (menu != null) {
                menu.updateButton(13, "残り時間: " + countdown, Material.CLOCK, "");
            }
            
            countdown--;
        }
    }.runTaskTimer(plugin, 0L, 20L);
}
```

---

## ベストプラクティス

### 1. スロット番号の定数化

```java
public class MenuSlots {
    public static final int CONFIRM = 11;
    public static final int CANCEL = 15;
    public static final int BACK = 45;
    public static final int NEXT = 53;
    public static final int CLOSE = 49;
}

// 使用例
ChestUI.builder()
    .addButtonAt(MenuSlots.CONFIRM, "確認", Material.GREEN_WOOL)
    .addButtonAt(MenuSlots.CANCEL, "キャンセル", Material.RED_WOOL)
    .then((result, player) -> {
        if (result.slot == MenuSlots.CONFIRM) {
            // 確認処理
        }
    })
    .show(player);
```

### 2. メニューの再利用

```java
public class MenuManager {
    private final Map<UUID, ChestUI.MenuHandler> activeMenus = new HashMap<>();
    
    public void openMenu(Player player, String menuType) {
        // 既存のメニューを閉じる
        closeMenu(player);
        
        // 新しいメニューを開く
        ChestUI.MenuHandler handler = createMenu(menuType, player);
        activeMenus.put(player.getUniqueId(), handler);
    }
    
    public void closeMenu(Player player) {
        ChestUI.MenuHandler handler = activeMenus.remove(player.getUniqueId());
        if (handler != null) {
            handler.close(player);
        }
    }
    
    public void updateMenu(Player player) {
        ChestUI.MenuHandler handler = activeMenus.get(player.getUniqueId());
        if (handler != null) {
            handler.getMenu().refreshAll();
        }
    }
}
```

### 3. エラーハンドリング

```java
.then((result, player) -> {
    try {
        if (result.slot == null) {
            return;
        }
        
        // 処理
        handleClick(player, result.slot);
        
    } catch (Exception e) {
        player.sendMessage("§cエラーが発生しました");
        ChestUI.closeMenu(player);
        getLogger().severe("Menu error: " + e.getMessage());
        e.printStackTrace();
    }
})
```

### 4. パーミッションチェック

```java
.then((result, player) -> {
    if (result.slot == 10) {
        if (!player.hasPermission("myplugin.admin")) {
            player.sendMessage("§c権限がありません");
            ChestUI.closeMenu(player);
            return;
        }
        
        // 管理者専用の処理
        performAdminAction(player);
    }
})
```

---

## トラブルシューティング

### Q: メニューが表示されない

**A:** 以下を確認してください：

1. `ChestUI.register()` を `onEnable()` で呼び出していますか？
2. プレイヤーはオンラインですか？
3. size は9の倍数ですか？

```java
// デバッグコード
if (!player.isOnline()) {
    getLogger().warning("Player is not online");
    return;
}
```

### Q: アイテムが動かせる

**A:** これは正常な動作です。ChestUI はすべてのクリックをキャンセルします。

### Q: メニューが勝手に閉じる

**A:** 以下の可能性があります：

1. 別のプラグインが `InventoryCloseEvent` をキャンセルしている
2. サーバーがラグっている
3. プレイヤーが死亡した

```java
// プレイヤーの状態をチェック
if (!player.isDead() && player.getOpenInventory() != null) {
    // メニューを再表示
}
```

### Q: ボタンが更新されない

**A:** `refreshAll()` を呼び出すか、メニューを再表示してください：

```java
// 方法1: ボタンを個別に更新
menu.updateButton(slot, newLabel, newMaterial, newLore);

// 方法2: すべて更新
menu.refreshAll();

// 方法3: メニューを再表示
ChestUI.closeMenu(player);
showMenu(player);
```

### Q: チャット入力が動作しない

**A:** プレイヤーがメニューを閉じていることを確認してください：

```java
.then((result, player) -> {
    if (result.slot == 15) {
        ChestUI.closeMenu(player);  // ← これが必要
        ChestUI.openChat(player, "入力してください:", "", text -> {
            // 処理
        });
    }
})
```

### Q: メモリリーク？

**A:** メニューハンドラーを適切にクリーンアップしてください：

```java
@EventHandler
public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    // クリーンアップ
    ChestUI.closeMenu(player);
}
```

---

## API リファレンス

### ChestUI クラス

#### 静的メソッド

| メソッド | 説明 |
|---------|------|
| `builder()` | 新しいビルダーを作成 |
| `register()` | リスナーを登録（onEnableで呼び出す） |
| `closeMenu(player)` | プレイヤーのメニューを閉じる |
| `hasOpenMenu(player)` | プレイヤーがメニューを開いているか |
| `getOpenMenu(player)` | プレイヤーのメニューを取得 |
| `openChat(player, prompt, default, callback)` | チャット入力を開く |

### Builder クラス

#### メソッド

| メソッド | 説明 |
|---------|------|
| `title(String)` | タイトルを設定 |
| `size(int)` | サイズを設定（9の倍数） |
| `addButtonAt(slot, label, material)` | ボタンを追加 |
| `addButtonAt(slot, label, material, lore)` | 説明付きボタンを追加 |
| `addButtonAt(slot, label, item)` | ItemStack でボタンを追加 |
| `then(handler)` | クリックハンドラを設定 |
| `show(player)` | メニューを表示 |

### MenuHandler クラス

#### メソッド

| メソッド | 説明 |
|---------|------|
| `getMenu()` | ChestUI インスタンスを取得 |
| `close(Player)` | このハンドラが保持するメニューを指定プレイヤーに対して閉じる |

### ChestUI インスタンスメソッド

| メソッド | 説明 |
|---------|------|
| `updateButton(slot, label, material, lore)` | ボタンを更新 |
| `removeButton(slot)` | ボタンを削除 |
| `refreshAll()` | すべてのボタンを更新 |
| `close(Player)` | 指定プレイヤーに対してこのメニューを閉じる（プレイヤーがこのメニューを開いている場合のみ） |

### FormResult クラス

#### フィールド

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `success` | boolean | 成功したか（常にtrue） |
| `cancelled` | boolean | キャンセルされたか |
| `slot` | Integer | クリックされたスロット |

---

## まとめ

ChestUI はインベントリベースの使いやすいメニューシステムです。

- ✅ Builder パターンで直感的
- ✅ 動的更新が可能
- ✅ チャット入力もサポート
- ✅ 古いバージョンでも動作

より高度な機能が必要な場合は、[DialogUI](DialogUI.md) の使用を検討してください。
