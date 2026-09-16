## プロジェクト

Just Leveling 本家を Paper Plugin で再現するプロジェクトです。

### 実装済み

- 8能力値: Strength / Constitution / Dexterity / Defense / Intelligence / Building / Magic / Luck
- 最大能力レベル 32
- Vanilla XP レベルを消費する能力レベルアップ
- 本家のパッシブ解放閾値 (5/8/11/.../32, 8/14/20/26/32)
- Leveling Book とチェスト GUI
- YAML によるプレイヤー能力値の永続保存
- Paper 属性に対応可能な主要パッシブ
- One Handed / Berserker / Life Eater / Lucky Drop など主要スキルのサーバー側再現
- `/hardmode leveling open`
- `/hardmode leveling book`

### 注意

Forge Mod 固有のクライアント GUI、Mixin、独自 Attribute、キー入力、Curios 連携などは Paper サーバーだけでは同一実装できないため、Paper API で成立する挙動へ置き換えています。
