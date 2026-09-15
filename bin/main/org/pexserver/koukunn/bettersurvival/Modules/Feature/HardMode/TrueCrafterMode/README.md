# TrueCrafterMode

TrueCrafterMode v3.0.0-beta 12 を参照し、Datapack の function 処理を Paper Plugin の API で再現する HardMode 機能です。

本家の command / function / NBT ベースの実装をイベント・tick 処理・PersistentDataContainer へ置き換えています。そのため、数値、発動条件、攻撃の流れ、パーティクル、サウンドをできる限り合わせていますが、Minecraft 本体の AI や Datapack 固有の実行順とは完全には同一にならない箇所があります。

本家: <https://github.com/Chuzume/True-Crafter-Mode>

## 有効化と設定

```
/hardmode list
/hardmode truecrafter enabled
/hardmode truecrafter disabled
/hardmode truecrafter heat <1-5>
```

設定は `plugins/Bettersurvival/hardmode.yml` に保存されます。

| 項目 | 内容 |
| --- | --- |
| `truecrafter` | TrueCrafterMode の有効・無効 |
| `heat-level` | 火の熱量。1 から 5 の範囲 |

熱量 4 以降では一部 Mob の追加装備、体力補正、水中移動補正、特殊効果が有効になります。熱量 5 ではベッドによる夜スキップを禁止します。


## 改善要望

- 苔丸石をドロップしないようにして欲しい。(パフォーマンスに問題がある為)
- クリーパの爆破範囲が私の思う限り本家と差がある気がするため確認を
- 熱量を変える不吉な焚き火はOP持ち限定(クラフト等も)
- スライムのロジックで敵対時の膨張が機能してない気がするここら辺本家と確認を
- ウィザースケルトンの背負ってる剣がめちゃ小さい問題が大きくして
- 現状クラッシュバグがあるためこれを修正:  ```[08:44:34] [Paper Watchdog Thread/ERROR]: --- DO NOT REPORT THIS TO PAPER - THIS IS NOT A BUG OR A CRASH  - 26.2-43-fab8887 (MC: 26.2) ---
[08:44:34] [Paper Watchdog Thread/ERROR]: The server has not responded for 10 seconds! Creating thread dump
[08:44:34] [Paper Watchdog Thread/ERROR]: ------------------------------
[08:44:34] [Paper Watchdog Thread/ERROR]: Server thread dump (Look for plugins here before reporting to Paper!):
[08:44:34] [Paper Watchdog Thread/ERROR]: ------------------------------
[08:44:34] [Paper Watchdog Thread/ERROR]: Current Thread: Server thread
[08:44:34] [Paper Watchdog Thread/ERROR]: 	PID: 81 | Suspended: false | Native: true | State: RUNNABLE
[08:44:34] [Paper Watchdog Thread/ERROR]: 	Stack:
[08:44:34] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.WinNTFileSystem.getBooleanAttributes0(Native Method)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.WinNTFileSystem.getBooleanAttributes(WinNTFileSystem.java:510)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.FileSystem.hasBooleanAttributes(FileSystem.java:141)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.File.exists(File.java:790)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager.loadConfig(ConfigManager.java:38)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.getGlobal(ToggleModule.java:160)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule.runForPlayer(AutoPlantModule.java:114)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule.runIntervalTask(AutoPlantModule.java:109)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule.lambda$new$0(AutoPlantModule.java:101)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule$$Lambda/0x000000000c52a748.run(Unknown Source)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		org.bukkit.craftbukkit.scheduler.CraftTask.run(CraftTask.java:78)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		org.bukkit.craftbukkit.scheduler.CraftScheduler.mainThreadHeartbeat(CraftScheduler.java:474)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.tickChildren(MinecraftServer.java:1768)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.tickServer(MinecraftServer.java:1621)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.dedicated.DedicatedServer.tickServer(DedicatedServer.java:404)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.processPacketsAndTick(MinecraftServer.java:1679)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.runServer(MinecraftServer.java:1349)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.lambda$spin$0(MinecraftServer.java:303)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer$$Lambda/0x000000000c160220.run(Unknown Source)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.lang.Thread.runWith(Thread.java:1487)
[08:44:34] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.lang.Thread.run(Thread.java:1474)
[08:44:34] [Paper Watchdog Thread/ERROR]: ------------------------------
[08:44:34] [Paper Watchdog Thread/ERROR]: --- DO NOT REPORT THIS TO PAPER - THIS IS NOT A BUG OR A CRASH ---
[08:44:34] [Paper Watchdog Thread/ERROR]: ------------------------------
[08:44:39] [Paper Watchdog Thread/ERROR]: --- DO NOT REPORT THIS TO PAPER - THIS IS NOT A BUG OR A CRASH  - 26.2-43-fab8887 (MC: 26.2) ---
[08:44:39] [Paper Watchdog Thread/ERROR]: The server has not responded for 15 seconds! Creating thread dump
[08:44:39] [Paper Watchdog Thread/ERROR]: ------------------------------
[08:44:39] [Paper Watchdog Thread/ERROR]: Server thread dump (Look for plugins here before reporting to Paper!):
[08:44:39] [Paper Watchdog Thread/ERROR]: ------------------------------
[08:44:39] [Paper Watchdog Thread/ERROR]: Current Thread: Server thread
[08:44:39] [Paper Watchdog Thread/ERROR]: 	PID: 81 | Suspended: false | Native: true | State: RUNNABLE
[08:44:39] [Paper Watchdog Thread/ERROR]: 	Stack:
[08:44:39] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.WinNTFileSystem.getBooleanAttributes0(Native Method)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.WinNTFileSystem.getBooleanAttributes(WinNTFileSystem.java:510)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.FileSystem.hasBooleanAttributes(FileSystem.java:141)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.File.exists(File.java:790)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager.loadConfig(ConfigManager.java:38)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.getGlobal(ToggleModule.java:160)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule.runForPlayer(AutoPlantModule.java:114)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule.runIntervalTask(AutoPlantModule.java:109)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule.lambda$new$0(AutoPlantModule.java:101)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule$$Lambda/0x000000000c52a748.run(Unknown Source)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		org.bukkit.craftbukkit.scheduler.CraftTask.run(CraftTask.java:78)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		org.bukkit.craftbukkit.scheduler.CraftScheduler.mainThreadHeartbeat(CraftScheduler.java:474)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.tickChildren(MinecraftServer.java:1768)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.tickServer(MinecraftServer.java:1621)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.dedicated.DedicatedServer.tickServer(DedicatedServer.java:404)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.processPacketsAndTick(MinecraftServer.java:1679)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.runServer(MinecraftServer.java:1349)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.lambda$spin$0(MinecraftServer.java:303)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer$$Lambda/0x000000000c160220.run(Unknown Source)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.lang.Thread.runWith(Thread.java:1487)
[08:44:39] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.lang.Thread.run(Thread.java:1474)
[08:44:39] [Paper Watchdog Thread/ERROR]: ------------------------------
[08:44:39] [Paper Watchdog Thread/ERROR]: --- DO NOT REPORT THIS TO PAPER - THIS IS NOT A BUG OR A CRASH ---
[08:44:39] [Paper Watchdog Thread/ERROR]: ------------------------------
[08:44:44] [Paper Watchdog Thread/ERROR]: --- DO NOT REPORT THIS TO PAPER - THIS IS NOT A BUG OR A CRASH  - 26.2-43-fab8887 (MC: 26.2) ---
[08:44:44] [Paper Watchdog Thread/ERROR]: The server has not responded for 20 seconds! Creating thread dump
[08:44:44] [Paper Watchdog Thread/ERROR]: ------------------------------
[08:44:44] [Paper Watchdog Thread/ERROR]: Server thread dump (Look for plugins here before reporting to Paper!):
[08:44:44] [Paper Watchdog Thread/ERROR]: ------------------------------
[08:44:44] [Paper Watchdog Thread/ERROR]: Current Thread: Server thread
[08:44:44] [Paper Watchdog Thread/ERROR]: 	PID: 81 | Suspended: false | Native: true | State: RUNNABLE
[08:44:44] [Paper Watchdog Thread/ERROR]: 	Stack:
[08:44:44] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.WinNTFileSystem.getBooleanAttributes0(Native Method)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.WinNTFileSystem.getBooleanAttributes(WinNTFileSystem.java:510)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.FileSystem.hasBooleanAttributes(FileSystem.java:141)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.File.exists(File.java:790)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager.loadConfig(ConfigManager.java:38)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.getGlobal(ToggleModule.java:160)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule.runForPlayer(AutoPlantModule.java:114)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule.runIntervalTask(AutoPlantModule.java:109)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule.lambda$new$0(AutoPlantModule.java:101)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule$$Lambda/0x000000000c52a748.run(Unknown Source)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		org.bukkit.craftbukkit.scheduler.CraftTask.run(CraftTask.java:78)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		org.bukkit.craftbukkit.scheduler.CraftScheduler.mainThreadHeartbeat(CraftScheduler.java:474)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.tickChildren(MinecraftServer.java:1768)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.tickServer(MinecraftServer.java:1621)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.dedicated.DedicatedServer.tickServer(DedicatedServer.java:404)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.processPacketsAndTick(MinecraftServer.java:1679)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.runServer(MinecraftServer.java:1349)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.lambda$spin$0(MinecraftServer.java:303)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer$$Lambda/0x000000000c160220.run(Unknown Source)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.lang.Thread.runWith(Thread.java:1487)
[08:44:44] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.lang.Thread.run(Thread.java:1474)
[08:44:44] [Paper Watchdog Thread/ERROR]: ------------------------------
[08:44:44] [Paper Watchdog Thread/ERROR]: --- DO NOT REPORT THIS TO PAPER - THIS IS NOT A BUG OR A CRASH ---
[08:44:44] [Paper Watchdog Thread/ERROR]: ------------------------------
[08:44:49] [Paper Watchdog Thread/ERROR]: --- DO NOT REPORT THIS TO PAPER - THIS IS NOT A BUG OR A CRASH  - 26.2-43-fab8887 (MC: 26.2) ---
[08:44:49] [Paper Watchdog Thread/ERROR]: The server has not responded for 25 seconds! Creating thread dump
[08:44:49] [Paper Watchdog Thread/ERROR]: ------------------------------
[08:44:49] [Paper Watchdog Thread/ERROR]: Server thread dump (Look for plugins here before reporting to Paper!):
[08:44:49] [Paper Watchdog Thread/ERROR]: ------------------------------
[08:44:49] [Paper Watchdog Thread/ERROR]: Current Thread: Server thread
[08:44:49] [Paper Watchdog Thread/ERROR]: 	PID: 81 | Suspended: false | Native: true | State: RUNNABLE
[08:44:49] [Paper Watchdog Thread/ERROR]: 	Stack:
[08:44:49] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.WinNTFileSystem.getBooleanAttributes0(Native Method)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.WinNTFileSystem.getBooleanAttributes(WinNTFileSystem.java:510)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.FileSystem.hasBooleanAttributes(FileSystem.java:141)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.io.File.exists(File.java:790)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager.loadConfig(ConfigManager.java:38)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.getGlobal(ToggleModule.java:160)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule.runForPlayer(AutoPlantModule.java:114)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule.runIntervalTask(AutoPlantModule.java:109)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule.lambda$new$0(AutoPlantModule.java:101)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		Bettersurvival-1.5.jar//org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule$$Lambda/0x000000000c52a748.run(Unknown Source)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		org.bukkit.craftbukkit.scheduler.CraftTask.run(CraftTask.java:78)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		org.bukkit.craftbukkit.scheduler.CraftScheduler.mainThreadHeartbeat(CraftScheduler.java:474)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.tickChildren(MinecraftServer.java:1768)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.tickServer(MinecraftServer.java:1621)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.dedicated.DedicatedServer.tickServer(DedicatedServer.java:404)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.processPacketsAndTick(MinecraftServer.java:1679)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.runServer(MinecraftServer.java:1349)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer.lambda$spin$0(MinecraftServer.java:303)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		net.minecraft.server.MinecraftServer$$Lambda/0x000000000c160220.run(Unknown Source)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.lang.Thread.runWith(Thread.java:1487)
[08:44:49] [Paper Watchdog Thread/ERROR]: 		java.base@25.0.2/java.lang.Thread.run(Thread.java:1474)
[08:44:49] [Paper Watchdog Thread/ERROR]: ------------------------------ ```
## 実装エンティティ

## 本家照合の記録

チェックは `TrueCrafterMode (1)/data/asset/function/mob` の実ファイルと現在の Java 実装を照合した結果です。`[x]` は発動条件・tick 時刻・主要な状態遷移を確認済み、`[ ]` は細かな演出または実機での連続戦闘確認が残っています。

| 本家 Mob | 照合状況 | 対応する実装 |
| --- | --- | --- |
| ゾンビ / ゾンビブルート / クモ / 洞窟グモ | [x] 個別攻撃タイマー、ゾンビの移動中限定の跳躍準備・空中の向き固定・着地復帰、ゾンビブルートの跳躍後カウント保持、前方2ブロック判定、着地範囲、クモ系の本家マーカー弾道 | `StandardEnemyAiSystem` |
| スライム / マグマキューブ | [x] 敵対時の160/100tick膨張、スライム数上限、サイズごとの能力値。マグマキューブは本家どおり同じ継承処理 | `StandardEnemyAiSystem` |
| クリーパー | [x] 停止中だけ進む1〜25tick、25tick後の自動進行、30tick爆発、通常／追跡採掘で共有する3回上限 | `StandardEnemyAiSystem` / `TrueCrafterModeModule` |
| クリーパー追跡採掘 | [x] 壁越し・近距離・同高度での50tick大爆発 | `TrueCrafterModeModule` |
| ピグリン剣 / クロスボウ / ブルート | [x] 回復・耐火飲用・後退先の足場判定、予備動作後も継続する衝撃波タイマー | `StandardEnemyAiSystem` |
| ウィッチ / ヴィンディケーター / エヴォーカー | [x] ワープ失敗時のカウント維持、前後の退避経路、ヴィンディケーターの40/60/100tick遷移と非破壊、エヴォーカーの単体最終召喚 | `StandardEnemyAiSystem` / `EvokerAiSystem` |
| スケルトン系 / 溺死ゾンビ / ピリジャー | [x] 本家の初期化・共通 AI・後退処理。エリート矢の無重力、100tick後の落下、盾での停止 | `MobProfileInitializer` / `TrueCrafterModeModule` |
| 遠距離切替共通 AI | [x] 近距離5、遠距離5〜16、40tick後退、後方1ブロックの通路と3ブロック先の足場判定、蓄積カウント保持 | `TrueCrafterModeModule` |
| 追跡共通 AI | [x] 48ブロック索敵、40tick掘削、前方の眼・足元・胴体・頭上破壊、対象が大きく下にいる場合の真下掘削、20tick立ち往生後の足場生成 | `TrueCrafterModeModule` / `TemporaryEnemyBlockSystem` |
| 追跡用の仮設足場 | [x] バイオーム別材質、敵が乗る間の寿命停止、60tick消滅 | `TemporaryEnemyBlockSystem` |
| ジーロット / ドラゴン / ウィザー投射物 | [x] 初速、20〜35tickの加減速、追尾距離、400tick寿命 | `ProjectileMotionSystem` |
| ピグリンブルート衝撃波 | [x] 前方の叩きつけ、ブロックを通過するmarker、段差追従、40tick移動、4tickごとの12ダメージ | `StandardEnemyAiSystem` / `ProjectileMotionSystem` |
| ゾンビピグリン | [x] 48ブロック内の強制敵対 | `TrueCrafterModeModule` |
| 通常 / 弱体 / 中立 / 外縁エンダーマン | [x] かぼちゃ別の敵対距離、能力値、エンド／ネザーでの中立75%・外縁追跡25%抽選、近距離30tickのブロック破壊。本家にない共通ランダムテレポートは除去 | `MobProfileInitializer` / `TrueCrafterModeModule` |
| エリートスケルトン / ストレイ / ボグド | [x] 装備、遠近切替、矢の変換、休止周期 | `TrueCrafterModeModule` |
| ウィザーの騎士 / しもべ | [x] 60tick解放、4射ごとの140tick休止中も通常射撃を維持、装備と能力値 | `WitherBossSystem` / `TrueCrafterModeModule` |
| エンダージーロット | [x] スライム本体、NoAI、弾幕周期、眼球処理 | `EnderZealotAiSystem` / `EnderDragonBossSystem` |
| ウィザー | [x] 75%・50%遷移、騎士、近距離雷撃、4スキル | `WitherBossSystem` |
| ウィザー雷撃 | [x] 30tick警告、10ダメージ、ウィザー10tick、複合範囲判定 | `WitherFieldSystem` |
| エンダードラゴン | [x] クリスタル判定、第二形態、足場、3スキル、着地 | `EnderDragonBossSystem` |
| ドラゴン雷柱 | [x] 30tick警告、10ダメージ、縦方向の打ち上げ、複合範囲判定 | `EnderDragonBossSystem` |
| 全 Mob | [ ] 実機での複数人・長時間戦闘の演出確認 | 継続確認中 |

### 通常 Mob

| エンティティ | 主な処理 |
| --- | --- |
| ゾンビ / ハスク / ゾンビ村人 | 移動・段差補正、槍型、ジャンプ攻撃、ゾンビブルート派生 |
| 溺死ゾンビ | 水色の革ヘルメット、移動速度・段差補正 |
| スケルトン / ストレイ / ボグド / パーチド / ウィザースケルトン | 弓と近接武器の切替、後退、エリート派生、鞘表示 |
| クモ / 洞窟グモ | 糸玉または毒玉の射撃、近距離拡散射撃 |
| スライム / マグマキューブ | 敵対中の段階的な膨張、サイズごとの能力値、周囲個体数上限 |
| クリーパー | 透明化、強化爆発、爆発後の増殖・復帰処理 |
| ウィッチ | 近距離の後退テレポート、クリーパー強化、状態異常の解除 |
| ヴィンディケーター | 強歩行、進行方向のブロック破壊 |
| エヴォーカー | 近距離ワープとファング、体力半分以下での最終召喚 |
| ピリジャー | 体力・ノックバック・段差補正、熱量時の装備抽選 |
| ピグリン | 剣・槍・クロスボウの派生、回復、火炎耐性の飲用、衝撃波 |
| ピグリンブルート | 溜めからの地面衝撃波と範囲ダメージ |
| ゾンビピグリン | 周辺プレイヤーへの強制敵対化 |

### エンダーマン系

| 種類 | 主な処理 |
| --- | --- |
| 通常エンダーマン | 視線・かぼちゃ判定による敵対、近距離のブロック破壊 |
| 外縁追跡者 | ネザーまたは外縁域で出現。範囲内のプレイヤーを追跡し、地形破壊と仮設足場を使用 |
| 中立エンダーマン | 自動敵対処理を行わない通常個体 |
| 弱体エンダーマン | ドラゴン戦周辺で出現。小型化・能力低下・経験値とドロップなし |
| エンダージーロット | 眼球表示、予備動作、エンダーパール弾幕、死亡時の眼球破壊とドラゴンへの遅延ダメージ |

### ボス

| ボス | 主な処理 |
| --- | --- |
| ウィザー | 最大体力 600、フェーズ移行、騎士召喚、しもべ召喚、追尾弾、突進、雷、トラップレーザー、戦闘離脱時の消滅 |
| ウィザーの騎士 | 準備中の無敵・透明化、解放後の弓攻撃、専用頭装備と鞘表示 |
| ウィザーのしもべ | 小型化、近接装備、ウィザーのスキルでの召喚・爆発処理 |
| エンダードラゴン | クリスタル解除までの無敵、第二形態、ジーロット召喚、足場生成、突進、照準の目、追尾弾、着地・落雷 |

## 戦闘処理

### 投射物

`ProjectileMotionSystem` が PersistentDataContainer の種類ごとに処理します。

| 種類 | 効果 |
| --- | --- |
| `web` | 移動速度低下、採掘速度低下、クモの巣演出 |
| `poison` | 毒、移動速度低下 |
| `void` | 暗闇、ダメージ |
| `brute` | 大ダメージとノックバック |
| `zealot` | 加速・追尾するエンダージーロット弾 |
| `dragon_homing` | 長距離追尾。着弾地点にドラゴンブレス球を生成 |
| `wither_homing` | ウィザーの追尾弾 |
| `elite_arrow` / `elite_wither_arrow` | エリート弓矢。盾で受けた場合は矢を停止 |

ドラゴンブレス球は生成直後から毎tick演出を出し、10tick後から20tickごとに半径 3 ブロックへ即時ダメージを与え、140tickで消滅します。

### 地形処理

外縁追跡者や強歩行中の Mob は、破壊可能ブロックを壊したり、一時的な足場を設置したりします。仮設足場は `TemporaryEnemyBlockSystem` が寿命を管理して自動削除します。

### 鞘 ItemDisplay

スケルトン、ストレイ、ボグド、パーチド、ウィザースケルトン、およびウィザーの騎士には背中の武器表示を付けます。

- 所有者 UUID を PersistentDataContainer に保存
- 本体の yaw に追従
- カスタムモデルの実寸に合わせ、全対象で縮尺 `0.12`
- 本体の死亡、再装備、機能無効化時に削除
- 旧形式の石剣、石斧、ネザライト剣表示も近傍から回収

## クラス構成

| クラス | 担当 |
| --- | --- |
| `TrueCrafterModeModule` | 有効化、Mob 初期化、イベント受信、共通 AI、鞘管理 |
| `TrueCrafterSettings` | `hardmode.yml` の保存・読込 |
| `MobProfileInitializer` | Mob の能力値、装備、エンダーマンモード |
| `StandardEnemyAiSystem` | 通常 Mob の特殊 AI |
| `EvokerAiSystem` | エヴォーカーのワープと最終召喚 |
| `ProjectileMotionSystem` | 投射物の追尾・加速・命中効果 |
| `TimedLaserSystem` | エンダーアイ照準レーザー |
| `WitherBossSystem` / `WitherFieldSystem` | ウィザー戦と雷・レーザー領域 |
| `EnderDragonBossSystem` / `DragonPlatformSystem` | ドラゴン戦と一時足場 |
| `EnderZealotAiSystem` | エンダージーロットの弾幕・眼球表示 |
| `OminousCampfireSystem` | 火の熱量 UI と不吉な焚き火 |
| `HardModeLootFactory` | 熱量に応じた装備・エンチャント抽選 |
| `TemporaryEnemyBlockSystem` | 一時ブロックの設置・消去 |

## 動作確認時の注意

- 別ワールドに移動したプレイヤーを Mob が古いターゲットとして保持しても、距離計算は行わず安全に処理を終了します。
- ボス・家来・鞘表示は EntityDeathEvent と機能無効化時の両方で後片付けされます。
- 実装差や実行順の差を見つけた場合は、該当 Mob、場所、熱量、再現手順、可能ならログまたは画像を添えて報告してください。
