# Bettersurvival

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.oracle.com/java/)
[![Gradle](https://img.shields.io/badge/Gradle-8.x-green.svg)](https://gradle.org/)

Minecraft サーバー（Paper/Spigot）向けの機能拡張プラグイン。サーバー運営を支援する様々な機能を備えています。

## 目次

- [概要](#概要)
- [機能](#機能)
- [プロジェクト構造](#プロジェクト構造)
- [インストール手順](#インストール手順)
- [使用方法](#使用方法)
- [貢献ガイドライン](#貢献ガイドライン)
- [ライセンス](#ライセンス)
- [連絡先](#連絡先)

## 概要

Bettersurvival は、Minecraft サーバーの運営をより快適にするためのプラグインです。プレイヤーのブロックリスト管理だけでなく、自動化機能や便利なコマンドを提供し、サーバー管理者の負担を軽減します。

## 機能

- **チェストロック**: チェストのロックと保護
- **チェストソート**: チェスト内のアイテム自動整理
- **自動餌やり**: 動物への自動餌やり機能
- **何でも餌やり**: 任意のアイテムでの餌やり
- **自動植え付け**: 種子の自動植え付け
- **鉱石採掘**: 鉱石ブロックの効率的な採掘
- **木採掘**: 木の効率的な伐採
- **機能トグル**: 各機能のオン/オフ切り替え

## プロジェクト構造

```
bettersurvival/
├── .github/                    # GitHub 関連設定
├── .vscode/                    # VSCode 設定
├── gradle/                     # Gradle ラッパー
│   └── wrapper/
├── src/
│   └── main/
│       ├── java/
│       │   └── org/pexserver/koukunn/bettersurvival/
│       │       ├── Loader.java                    # メインのプラグインクラス
│       │       ├── Commands/                      # コマンド関連クラス
│       │       │   ├── chest/ChestCommand.java   # チェスト関連コマンド
│       │       │   ├── help/HelpCommand.java     # ヘルプコマンド
│       │       │   └── toggle/ToggleCommand.java # トグルコマンド
│       │       ├── Core/                         # コア機能クラス
│       │       │   ├── Command/                  # コマンド管理システム
│       │       │   │   ├── BaseCommand.java      # 基本コマンドクラス
│       │       │   │   ├── CommandGuide.java     # コマンドガイド
│       │       │   │   ├── CommandManager.java   # コマンドマネージャー
│       │       │   │   ├── CompletionUtils.java  # コマンド補完ユーティリティ
│       │       │   │   └── PermissionLevel.java  # 権限レベル定義
│       │       │   ├── Config/                   # 設定管理
│       │       │   │   ├── ConfigManager.java    # 設定マネージャー
│       │       │   │   ├── JsonUtils.java        # JSON ユーティリティ
│       │       │   │   └── PEXConfig.java        # PEX 設定クラス
│       │       │   └── Util/                     # ユーティリティクラス
│       │       │       ├── FloodgateUtil.java    # Floodgate 統合ユーティリティ
│       │       │       └── FormsUtil.java        # フォームユーティリティ
│       │       └── Modules/                      # 機能モジュール
│       │           ├── ToggleListener.java       # トグル機能リスナー
│       │           ├── ToggleModule.java         # トグル機能モジュール
│       │           └── Feature/                  # 個別機能モジュール
│       │               ├── AnythingFeed/         # 何でも餌やり機能
│       │               ├── AutoFeed/             # 自動餌やり機能
│       │               ├── AutoPlant/            # 自動植え付け機能
│       │               ├── ChestLock/            # チェストロック機能
│       │               ├── ChestSort/            # チェストソート機能
│       │               ├── OreMine/              # 鉱石採掘機能
│       │               └── TreeMine/             # 木採掘機能
│       └── resources/
│           └── plugin.yml                        # プラグイン設定ファイル
├── build.gradle                  # Gradle ビルドスクリプト
├── gradle.properties             # Gradle プロパティ
├── gradlew                       # Gradle ラッパー (Unix)
├── gradlew.bat                   # Gradle ラッパー (Windows)
├── settings.gradle               # Gradle 設定
└── README.md                     # このファイル
```

### 主要ファイルの役割

- **Loader.java**: プラグインのエントリーポイント。サーバー起動時の初期化とコマンド登録を担当。
- **Commands/**: ユーザーからのコマンド入力を処理するクラス群。
- **Core/**: プラグインの基盤となる機能を提供。コマンド管理、設定管理、ユーティリティを含む。
- **Modules/**: 個別の機能を実装したモジュール。トグル機能と各種自動化機能を管理。
- **plugin.yml**: プラグインのメタ情報（名前、バージョン、メインクラスなど）を定義。

## インストール手順

### 前提条件
- Java 17 以上
- Minecraft サーバー (Paper または Spigot 1.21+)

### 手順
1. **JDK のインストール**:
   - [Oracle JDK](https://www.oracle.com/java/technologies/javase-downloads.html) または [OpenJDK](https://openjdk.java.net/) をインストールしてください。

2. **プロジェクトのクローン**:
   ```bash
   git clone https://github.com/your-username/bettersurvival.git
   cd bettersurvival
   ```

3. **ビルド**:
   ```bash
   # Windows
   ./gradlew.bat build

   # macOS/Linux
   ./gradlew build

   # 開発用 Paper サーバーを起動 (Windows)
   ./gradlew.bat runServer
   ```
   ビルドが成功すると、`build/libs/Bettersurvival-<version>.jar` が生成されます。

4. **サーバーへの配置**:
   - Minecraft サーバーの `plugins/` ディレクトリに JAR ファイルをコピーします。
   - サーバーを再起動するか、`/reload` コマンドを実行します。

5. **設定ファイルの確認**:
   - 初回起動時に設定ファイルが自動生成されます。
   - 必要に応じて `plugins/Bettersurvival/config.yml` を編集してください。

## 使用方法

### 基本的な使用
プラグインがインストールされると、以下のコマンドが利用可能になります：

- `/help`: 利用可能なコマンド一覧を表示
- `/toggle`: 機能のオン/オフを切り替え
- `/chest`: チェスト関連の操作



### 機能の有効化/無効化
各機能は `/toggle` コマンドで個別にオン/オフを切り替えられます。管理者モードではグローバル設定も可能です

## 貢献ガイドライン

プロジェクトへの貢献を歓迎します！以下の手順に従ってください：

### 開発環境のセットアップ
1. リポジトリをフォークしてください。
2. ローカルにクローンし、上記のインストール手順に従ってビルドしてください。

### コードの変更
1. 新しいブランチを作成してください：
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. 変更を加え、テストしてください。
3. コミットメッセージは明確に記述してください。

### プルリクエスト
1. 変更をプッシュしてください。
2. GitHub からプルリクエストを作成してください。
3. レビューを待ち、必要に応じて修正してください。

### バグ報告・機能リクエスト
- [GitHub Issues](https://github.com/gamelist1990/Bettersurvival/issues) を使用してください。
- バグ報告には以下の情報を含めてください：
  - Minecraft バージョン
  - サーバーソフトウェア (Paper/Spigot)
  - エラーログ
  - 再現手順

### コーディング標準
- Java コーディング規約に従ってください。
- コメントは日本語または英語で記述してください。
- 新しい機能には適切なテストを追加してください。

## ライセンス

このプロジェクトは MIT License の下で公開されています。

```
MIT License

Copyright (c) 2025 Koukunn

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 連絡先

- **作者**: Koukunn
- **GitHub**: [https://github.com/gamelist1990/Bettersurvival](https://github.com/gamelist1990/Bettersurvival)
- **Issues**: [バグ報告・機能リクエスト](https://github.com/gamelist1990/Bettersurvival/issues)

---

ご質問やフィードバックがありましたら、GitHub Issues からお気軽にお問い合わせください。