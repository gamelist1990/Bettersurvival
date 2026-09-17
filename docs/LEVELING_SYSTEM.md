# Just Leveling / レベリングシステム

このページは `Modules/Feature/HardMode/LeveingSystem` の現行実装を基準にしています。Forge版 Just Leveling v1.7 を参考に Paper 側で再構成された機能で、**8能力値 + パッシブ + 24スキル + 41称号 + Otherworld ごとのデータ分離**を扱います。

> ブラウザ上で ChestGUI を触りたい場合は [Leveling ChestGUI Interactive Mock](leveling-gui-demo.html) を開いてください。

## 開き方

通常プレイヤーは **レベリングの書**を右クリックして GUI を開きます。管理者は `/hardmode leveling open` でも開けます。

### レベリングの書のレシピ

```text
[   ][Emerald][   ]
[Lapis][ Book ][Lapis]
[   ][Emerald][   ]
```

機能が `disabled` のときはレシピ自体が解除され、既存の書を右クリックしても GUI は開きません。

## GUI

新GUIは 9×6 ChestGUI を「能力値」「プレイヤー概要」「進行」「称号」の4領域として整理します。

```text
 0  1  2  3  4  5  6  7  8
 9 [筋][ ][体][ ][敏][ ][防] 17
18 [知][ ][建][ ][魔][ ][運] 26
27 28 29 [Player][XP][Title] 33 34 35
36    [======= overall progress =======]   44
45 [Help]                       [Close] 53
```

能力値アイコンをクリックすると現在レベルに応じた Minecraft 経験値レベルを消費して +1 します。GUI はアップグレード後に再描画されるため、必要コスト・ランク・進行率が即時更新されます。

## レベルとコスト

- 各能力値の最大レベル: **32**
- 初回コスト: **5 XP levels**
- 現在 Lv.`n` → 次 Lv. の必要コスト: `5 + floor(n / 2)`
- 能力値データは Minecraft の通常 XP バーとは別に `leveling-data.yml` へ保存
- 支払いは XP point ではなく **プレイヤーの経験値レベル**を直接消費

例:

| 現在Lv | 次レベルのコスト |
| ---: | ---: |
| 0 | 5 |
| 1 | 5 |
| 2 | 6 |
| 8 | 9 |
| 16 | 13 |
| 24 | 17 |
| 31 | 20 |

## 8能力値

| 能力値 | 略称 | 主な実装済みパッシブ |
| --- | --- | --- |
| 筋力 | STR | 攻撃力、攻撃ノックバック。片手条件や低HP時の追加ダメージ系スキル |
| 体力 | CON | 最大体力、ノックバック耐性、呼吸量、負の効果短縮 |
| 敏捷 | DEX | 移動速度、飛び道具ダメージ、弓命中時の移動補助・隠密 |
| 防御 | DEF | 防具値、防具強度、粉雪耐性、反撃、Resistance |
| 知力 | INT | 攻撃速度、Entity interaction range、エンチャント閲覧、取引値引き |
| 建築 | BLD | Block interaction range、破壊速度、採掘系報酬、クラフト素材還元 |
| 魔力 | MAG | 有益Potionの持続延長、魔法系ダメージ耐性、転移・吸収・EnderChest |
| 幸運 | LCK | Luck、クリティカル系補正、ドロップ増加、低確率の限界突破 |

### パッシブ段階

10段階系の閾値: `5, 8, 11, 14, 17, 20, 23, 26, 29, 32`

5段階系の閾値: `8, 14, 20, 26, 32`

GUI の `パッシブ: x/10 / y/5` はこの2系列の現在段階を表します。

## ランク

| レベル | ランク |
| ---: | --- |
| 0–3 | 初心者 |
| 4–7 | 駆け出し |
| 8–11 | 見習い |
| 12–15 | 一人前 |
| 16–19 | 中級者 |
| 20–23 | 上級者 |
| 24–27 | 熟練者 |
| 28–31 | 達人 |
| 32 | 超越者 |

## 24スキル

スキルはポイントを別途振る方式ではなく、対応する能力値が必要レベルに達すると `JustLevelingRuntime` が自動的に有効として扱います。

| 能力値 | Lv | スキル | 現行実装の効果 |
| --- | ---: | --- | --- |
| STR | 10 | 片手の達人 | 片手条件を使う戦闘強化の基礎条件 |
| STR | 16 | 闘志 | Mob撃破時に短時間 Strength |
| STR | 30 | 狂戦士 | 低HP時の攻撃強化条件 |
| CON | 10 | 運動能力 | 最大空気量 300 → 450 |
| CON | 20 | 亀の守り | Shulker Bullet の Levitation を解除 |
| CON | 32 | 獅子の心 | 対象の負のPotion効果時間を半減 |
| DEX | 10 | 高速再配置 | 弓系命中時に短時間 Speed |
| DEX | 16 | 隠密の達人 | スニーク射撃強化 + Mob索敵範囲抑制 |
| DEX | 32 | 猫の目 | Night Vision を維持 |
| DEF | 10 | 雪上歩行 | 粉雪の凍結を解除し沈み込みを抑制 |
| DEF | 18 | 反撃 | 被弾後3秒以内の近接攻撃に被ダメージの50%を加算 |
| DEF | 30 | 金剛の皮膚 | Resistance II を維持 |
| INT | 8 | 学者 | スニーク使用時に手持ちエンチャント情報を表示 |
| INT | 16 | 値切り上手 | Villager取引の第1材料を約20%相当値引き |
| INT | 30 | 錬金術操作 | Potion使用後の有益効果 amplifier を +1 |
| BLD | 12 | 黒曜石破砕 | Obsidian / Crying Obsidian 採掘時に強い Haste |
| BLD | 20 | トレジャーハンター | Dirt系破壊時 5% で素材系アイテムを追加ドロップ |
| BLD | 30 | 収束 | Craft時 8% で材料1個をランダム還元 |
| MAG | 12 | 安全転移 | Ender Pearl 転移直後の対象Fall damageを無効化 |
| MAG | 18 | 生命吸収 | Mob撃破時に体力を1回復 |
| MAG | 32 | ワームホール倉庫 | スニーク + Ender Chest 使用で自分のEnderChestを直接開く |
| LCK | 12 | 運命のダイス | クリティカル系のランダム補正 |
| LCK | 22 | 幸運ドロップ | 10% で Mob のドロップを追加複製 |
| LCK | 32 | 限界突破 | 近接攻撃時 1% で極大ダメージ + ActionBar演出 |

## 称号

称号は `leveling-titles.yml` に保存され、解放後は GUI または `/hardmode leveling title <key>` で選択できます。選択中の称号はプレイヤー表示フォーマッタへ反映されます。

主な解放条件:

- 初参加/初期化: `新人`
- 各能力値 Lv16: 能力別の中位称号
- 各能力値 Lv32: 能力別の上位称号
- Ender Dragon 10体: `竜殺し`
- Player 100人: `対人の覇者`
- Mob 100 / 1,000 / 10,000体: Mob Killer 3段階
- Raid 10回勝利: `英雄`
- Villager 100体撃破: `悪名高き者`
- Fishing 100 / 1,000 / 10,000回: 釣り称号3段階
- Enchant 100 / 1,000 / 10,000回: 付与術称号3段階
- Trade 100回: `商人`
- 死亡せず 100 Minecraft days: `生存者`
- Boat / Minecart / Horse / Pig / Strider で各10,000 blocks相当移動: 対応乗り物称号
- Nether / End 到達: 対応Traveler称号
- OP: `管理者`

称号総数は `称号なし` を含め **41** です。

## Otherworld 連携

Otherworld が有効な場合、能力値・称号・統計は `OtherworldModule#getGroup(player)` のグループ名を scope として分離されます。ワールド移動で scope が変わるとパッシブを再適用し、プレイヤーへプロフィール切替メッセージを表示します。

つまり、通常ワールドで育てた能力値が、設定された別ワールドグループへ自動的に共有されるとは限りません。

## 管理コマンド

`/hardmode` 自体が `ADMIN_OR_CONSOLE` 権限レベルです。一般プレイヤー向けの通常導線はレベリングの書です。

| コマンド | 内容 |
| --- | --- |
| `/hardmode leveling enabled` | Leveling を有効化、Book recipe登録、パッシブ適用 |
| `/hardmode leveling disabled` | 無効化、recipe削除、能力値由来Attributeを標準値へ戻す |
| `/hardmode leveling open` | 自分の能力値GUIを開く |
| `/hardmode leveling book` | レベリングの書を付与 |
| `/hardmode leveling profile` | profile表示 |
| `/hardmode leveling skills` | skills表示 |
| `/hardmode leveling top` | ranking表示 |
| `/hardmode leveling titles` | 解放済み称号と選択中称号を表示 |
| `/hardmode leveling title <key>` | 解放済み称号を選択 |

`stats`, `ranking` もコード上はそれぞれ `profile`, `top` の別名として受け付けます。

## 保存ファイル

- `leveling-data.yml`: enabled 状態・能力値
- `leveling-titles.yml`: 選択称号・解放称号・称号用統計

## 外部画像について

[ChestGUI デモ](leveling-gui-demo.html) のアイテム画像は、外部の [PrismarineJS/minecraft-assets](https://github.com/PrismarineJS/minecraft-assets) にある Minecraft asset 画像を URL 参照しています。リポジトリ内へ画像をコピーせず、モックの表示用途だけに使っています。接続できない環境では文字ラベルへフォールバックします。
