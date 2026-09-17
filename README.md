# BetterSurvival

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-blue.svg)](https://www.oracle.com/java/)
[![Paper](https://img.shields.io/badge/Paper-26.2-4b8bbe.svg)](https://papermc.io/)

Minecraft Paper サーバー向けの大型サバイバル拡張プラグインです。採集・生活QoL、チェスト保護とショップ、Party / LandProtect、Bedrock連携、WebMap / WebService、Otherworld、HardMode / Just Leveling などを一つのプラグインで提供します。

## Documentation / Wiki

実装と同期したドキュメントは [`docs/README.md`](docs/README.md) を入口にしています。

- **[Wiki Home](docs/README.md)**
- **[実装済み機能一覧](docs/FEATURES.md)**
- **[コマンド一覧](docs/COMMANDS.md)**
- **[Just Leveling 完全ガイド](docs/LEVELING_SYSTEM.md)**
- **[Just Leveling ChestGUI Interactive Mock](docs/leveling-gui-demo.html)**
- **[Floodgate / Bedrock](docs/Floodgate.md)**
- **[YouTube OAuth Setup](docs/YOUTUBE_OAUTH_SETUP.md)**

## 主な機能

### Survival / QoL
TreeMine、OreMine、AutoFeed、AutoFishing、AnythingFeed、AutoPlant、DeathChest、Home、AirDash、Sit など。

### Storage / Equipment
ChestLock、ChestShop、ChestSort、SharedStorage、EnchantSplit、ChunkLoader、ParallelFurnace、Recycler、CustomEnchant、WarpStone など。

### Community / Protection
TPA、Party、LandProtect、Pet、CopperGolem、JPCh、InvSee、OfflineAccess、PendingWhitelist など。

### Integrations / Server
BedrockSkin、Geyser向け金床・鍛冶台、WebMap、WebService、Discord、YouTube Live Chat、Performance、MOTD、KeepAliveGuard など。

### HardMode
TrueCrafter と **Just Leveling** を搭載。Just Leveling は8能力値、パッシブ、24スキル、41称号、Otherworldごとのプロフィール分離を持ちます。

> 網羅的な一覧と各 Toggle key は [docs/FEATURES.md](docs/FEATURES.md) を参照してください。

## Just Leveling ChestGUI

レベリングの書を右クリックすると 9×6 ChestGUI を開き、Minecraft の経験値レベルを消費して能力値を成長させられます。GUI は能力値・プレイヤー概要・XP・称号・全体進行を分離したレイアウトです。

ブラウザ上でも [Interactive ChestGUI Mock](docs/leveling-gui-demo.html) を開き、XP値やサンプル進行度を変えながら操作感を確認できます。デモの Minecraft item texture は外部の [PrismarineJS/minecraft-assets](https://github.com/PrismarineJS/minecraft-assets) を参照しています。

## Requirements

現行 `build.gradle` を基準とします。

- **Java 25**
- **Paper 26.2**
- Geyser / Floodgate: 関連機能を使用する場合のみ

> 旧 README の Java 17+ / Paper・Spigot 26.1+ 表記は現行ビルド設定と一致していなかったため更新しました。

## Install

1. [Releases](https://github.com/gamelist1990/Bettersurvival/releases) から JAR を取得します。
2. Paper サーバーの `plugins` へ配置します。
3. Java 25 でサーバーを起動します。
4. `/toggle` や各管理コマンドで必要な機能を設定します。

開発ビルドでは次で検証できます。

```bash
./gradlew build --stacktrace
```

## Contributing

変更時はコードだけでなく、ユーザーから見える仕様が変わる場合に `docs/` も更新してください。特に以下は実装とドキュメントを同じPRで同期させます。

- `Loader` の Feature / Command 登録
- `/toggle` key と初期値
- Just Leveling の能力値・スキル・称号・GUI slot
- 対応 Java / Paper version
- Web / Bedrock / 外部サービス設定

## License

[MIT License](LICENSE)

Copyright (c) 2025 Koukunn

## Issues

バグ報告・機能リクエスト: [GitHub Issues](https://github.com/gamelist1990/Bettersurvival/issues)
