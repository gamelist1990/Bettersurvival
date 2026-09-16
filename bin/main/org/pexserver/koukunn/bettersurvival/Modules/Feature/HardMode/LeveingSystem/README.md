## プロジェクト

Just Leveling Forge 1.20.x v1.7 を Paper Plugin で可能な範囲まで再現するプロジェクトです。

### 実装済み

- 8能力値: Strength / Constitution / Dexterity / Defense / Intelligence / Building / Magic / Luck
- 最大能力レベル 32
- Vanilla XP レベルを消費する能力レベルアップ
- 本家のパッシブ解放閾値
  - 10段階: 5 / 8 / 11 / 14 / 17 / 20 / 23 / 26 / 29 / 32
  - 5段階: 8 / 14 / 20 / 26 / 32
- 16パッシブをPaperで再現可能なAttribute/Eventへマッピング
  - attack damage / knockback / health / knockback resistance
  - movement speed / projectile damage / armor / armor toughness
  - attack speed / entity reach / block reach / break speed
  - beneficial effect / magic resist / critical damage / luck
- Forge版で登録されている24スキルを全て定義し、サーバー側で成立する挙動へ置換
  - STR: One Handed / Fighting Spirit / Berserker
  - CON: Athletics / Turtle Shield / Lion Heart
  - DEX: Quick Reposition / Stealth Mastery / Cat Eyes
  - DEF: Snow Walker / Counter Attack / Diamond Skin
  - INT: Scholar / Haggler / Alchemy Manipulation
  - BLD: Obsidian Smasher / Treasure Hunter / Convergence
  - MAG: Safe Port / Life Eater / Wormhole Storage
  - LCK: Critical Roll Dice / Lucky Drop / Limit Breaker
- 41称号（Titleless含む）の登録・解放・選択・永続保存
- Dragon/Mob/Player/Villager kill、Raid、釣り、エンチャント、取引、生存日数を追跡
- Boat / Minecart / Horse / Pig / Strider の移動距離称号
- Nether / End 到達称号
- Leveling Book とラージチェスト式ChestUI
- 能力値のレベルアップ、経験値表示、称号一覧・解放状態表示・称号装備を同一UIで操作
- `leveling-data.yml` と `leveling-titles.yml` による永続保存
- `/hardmode leveling open`
- `/hardmode leveling book`
- `/hardmode leveling titles`
- `/hardmode leveling title <key>`

### Paper版での置換

Forge Mod 固有のクライアントGUI、Mixin、独自Attribute、キー入力、Curios連携はPaperサーバー単体では同じ方式を使えません。そのため、以下のようにサーバーAPIへ置換しています。

- 独自Reach Attribute -> Paperの `ENTITY_INTERACTION_RANGE` / `BLOCK_INTERACTION_RANGE`
- 独自Break Speed -> Paperの `BLOCK_BREAK_SPEED`
- Leveling画面 -> チェストGUI + Leveling Book
- Wormhole Storage -> スニークしながらEnder Chestを右クリックしてEnder Chest inventoryを開く
- 称号表示 -> PlayerList名へ表示
- クライアント側の視界/描画処理 -> Mob target範囲・Potion Effectなどのサーバー側挙動で近似

### 再現上の注意

PaperだけではForge側の完全なクライアント挙動やMixin内部処理を1:1では再現できません。確率・解放レベル・主要設定値はv1.7 JARから抽出した値を優先し、API上同一表現がない部分のみPaper向けに近似しています。
