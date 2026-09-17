# BetterSurvival Wiki

> この `docs/` ディレクトリを BetterSurvival の仕様書・Wiki の正本として扱います。記述は `master` の実装を基準にしています。

BetterSurvival は Paper 向けの大型サバイバル拡張プラグインです。木・鉱石の一括採掘から、チェスト保護、ショップ、共有ストレージ、Party / LandProtect、WebMap、Otherworld、HardMode / Just Leveling まで複数の機能を一つにまとめています。

## 最初に読む

| ページ | 内容 |
| --- | --- |
| [機能一覧](FEATURES.md) | 実装されている機能をカテゴリ別に網羅 |
| [コマンド一覧](COMMANDS.md) | Loader で登録されるコマンドと用途 |
| [Just Leveling](LEVELING_SYSTEM.md) | 能力値、必要経験値、スキル、称号、Book、Otherworld 連携 |
| [ChestGUI デモ](leveling-gui-demo.html) | 実際の 9×6 ChestGUI をブラウザ上で操作できるモック |
| [Floodgate / Bedrock](Floodgate.md) | Floodgate / Bedrock 関連設定 |
| [YouTube OAuth](YOUTUBE_OAUTH_SETUP.md) | YouTube 連携のセットアップ |
| [TrueCrafter attribution](TRUECRAFTER_ATTRIBUTION.md) | TrueCrafter 由来部分の帰属情報 |

## 動作要件

- Java **25**
- Paper **26.2 系**（`build.gradle` の `paperDevBundle("26.2.build.+")` / `runServer.minecraftVersion("26.2")` が基準）
- Geyser / Floodgate は関連機能を利用する場合のみ

> README に残っていた `Java 17+` バッジおよび `Paper/Spigot 26.1+` の記述は現行ビルド設定と一致しないため、この Wiki では Java 25 / Paper 26.2 を基準に統一しています。

## 機能の有効化

多くのゲームプレイ機能は `/toggle` から個人設定またはグローバル設定を変更できます。管理系・統合系の機能には専用コマンドがあります。HardMode 系の Just Leveling / TrueCrafter は `/hardmode` で管理します。

## Just Leveling をすぐ試す

1. 管理者が `/hardmode leveling enabled` を実行
2. `/hardmode leveling book` で **レベリングの書** を受け取る、または通常レシピで作成
3. レベリングの書を右クリックして ChestGUI を開く
4. 8能力値のアイコンをクリックし、Minecraft の経験値レベルを消費して強化
5. `称号` から解放済みタイトルを選択

GUI の見た目とスロット配置は [Just Leveling](LEVELING_SYSTEM.md) と [ChestGUI デモ](leveling-gui-demo.html) を参照してください。

## ドキュメント更新ルール

- コマンド名・設定値・GUI のスロット・数式は **Java 実装を正** とする
- 機能追加時は [FEATURES.md](FEATURES.md) と、必要なら [COMMANDS.md](COMMANDS.md) も同時更新する
- Just Leveling の能力値 / スキル / 称号を変更した場合は [LEVELING_SYSTEM.md](LEVELING_SYSTEM.md) と ChestGUI デモも同時更新する
- 実装と Wiki が食い違う場合は、推測で埋めず実装を確認して Wiki を直す
