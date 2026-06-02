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




## インストール手順

### 前提条件
- Java 25 以上
- Minecraft サーバー PaperまたはSpigot 26.1 以上

### プラグインインストール

1. [リリースページ](https://github.com/gamelist1990/Bettersurvival/releases)から最新の JAR ファイルをダウンロードしてください。

2. ダウンロードした JAR ファイルをサーバーの `plugins` フォルダに移動してください。

3. サーバーを再起動してください。プラグインが正常に読み込まれると、コンソールに「Bettersurvival has been enabled!」というメッセージが表示されます。

## 使用方法

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