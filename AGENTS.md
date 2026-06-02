# Paper API 調査手順書

> **目的**: Paper Plugin 開発において、間違った API・古い API を使わずに  
> 正しく実装するための固定手順書。  
> Windows / WSL2 / Ubuntu (Linux ネイティブ) すべての環境に対応。

---

## 目次

1. [環境別コマンド対応表](#0-環境別コマンド対応表)
2. [build.gradle の確認](#1-buildgradle-の確認)
3. [Gradle キャッシュから Paper API を探す](#2-gradle-キャッシュから-paper-api-を探す)
4. [Temp キャッシュの活用](#3-temp-キャッシュの活用)
5. [sources.jar の中を見る](#4-sourcesjar-の中を見る)
6. [javadoc.jar の確認](#5-javadocjar-の確認)
7. [公式 Paper ドキュメントで最終確認](#6-公式-paper-ドキュメントで最終確認)
8. [プロジェクト内の既存実装を探す](#7-プロジェクト内の既存実装を探す)
9. [API を読む時の判断基準](#8-api-を読む時の判断基準)
10. [よく使うコマンド集](#9-よく使うコマンド集)
11. [実装後の最終確認チェックリスト](#10-実装後の最終確認チェックリスト)

---

## 0. 環境別コマンド対応表

> 作業前に自分がどの環境にいるかを確認してから読み進めてください。

| 項目 | Windows (PowerShell) | WSL2 | Ubuntu / Linux ネイティブ |
|------|----------------------|------|--------------------------|
| シェル | `powershell` / `pwsh` | `bash` (Gradle キャッシュは **Windows 側**を参照) | `bash` |
| Gradle キャッシュ | `$env:USERPROFILE\.gradle\...` | `/mnt/c/Users/<ユーザー名>/.gradle/...` | `~/.gradle/...` |
| Temp フォルダ | `<project>\Temp\` | `<project>/Temp/` | `<project>/Temp/` |
| ファイル検索 | `Get-ChildItem -Recurse` | `find` | `find` |
| テキスト検索 | `Select-String` / `rg` | `rg` / `grep` | `rg` / `grep` |
| jar 展開 | `jar xf` (JDK の jar コマンド) | `jar xf` / `unzip` | `jar xf` / `unzip` |
| ビルド | `.\gradlew.bat build` | `./gradlew build` | `./gradlew build` |

> ⚠️ **WSL2 の注意点**  
> Gradle ビルドや `.gradle` キャッシュの確認は **Windows 側** で行うことを推奨。  
> WSL2 から参照する場合は `/mnt/c/Users/<ユーザー名>/.gradle/` を使う。

---

## 絶対に守る順番

```
1. build.gradle で現在の Paper API バージョンを確認する
2. Temp/<version>/ に既にキャッシュがあればそれを使う (再展開不要)
3. キャッシュがなければ Gradle キャッシュから sources.jar / javadoc.jar を展開して Temp に保存する
4. sources.jar で型・メソッド・引数・@Deprecated を確認する
5. javadoc.jar または公式 Javadoc で説明文を確認する
6. プロジェクト内に既存実装がないか rg で確認する
7. 既存スタイルに合わせて最小差分で実装する
8. gradle build を実行してエラーや警告を確認・修正する
```

> ❌ 記憶だけで Paper API を書かない。必ずキャッシュと公式資料を確認する。

---

## 1. build.gradle の確認

**毎回最初に確認する。バージョンが変わっていたら以後のコマンドのバージョン文字列も変える。**

### Windows / PowerShell

```powershell
Get-Content .\build.gradle
```

### WSL2 / Linux

```bash
cat build.gradle
```

### 確認するべき行の例

```gradle
compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
```

> ここの `1.21.4-R0.1-SNAPSHOT` の部分が、以後の手順で使うバージョン文字列になる。

---

## 2. Gradle キャッシュから Paper API を探す

### 2-1. バージョン変数のセット

#### Windows / PowerShell

```powershell
$paperVersion = '1.21.4-R0.1-SNAPSHOT'   # build.gradle に合わせて変える
$paperCacheRoot = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\io.papermc.paper\paper-api\$paperVersion"

$paperSources = Get-ChildItem -Recurse -Filter "paper-api-$paperVersion-sources.jar" $paperCacheRoot |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName

$paperJavadoc = Get-ChildItem -Recurse -Filter "paper-api-$paperVersion-javadoc.jar" $paperCacheRoot |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName

$paperBinary = Get-ChildItem -Recurse -Filter "paper-api-$paperVersion.jar" $paperCacheRoot |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName

$paperSources
$paperJavadoc
$paperBinary
```

#### WSL2 (Windows キャッシュを参照)

```bash
PAPER_VERSION="1.21.4-R0.1-SNAPSHOT"
WIN_USER=$(cmd.exe /c "echo %USERNAME%" 2>/dev/null | tr -d '\r')
PAPER_CACHE_ROOT="/mnt/c/Users/${WIN_USER}/.gradle/caches/modules-2/files-2.1/io.papermc.paper/paper-api/${PAPER_VERSION}"

PAPER_SOURCES=$(find "$PAPER_CACHE_ROOT" -name "paper-api-${PAPER_VERSION}-sources.jar" | head -1)
PAPER_JAVADOC=$(find "$PAPER_CACHE_ROOT" -name "paper-api-${PAPER_VERSION}-javadoc.jar" | head -1)
PAPER_BINARY=$(find "$PAPER_CACHE_ROOT" -name "paper-api-${PAPER_VERSION}.jar" ! -name "*sources*" ! -name "*javadoc*" | head -1)

echo "$PAPER_SOURCES"
echo "$PAPER_JAVADOC"
echo "$PAPER_BINARY"
```

#### Ubuntu / Linux ネイティブ

```bash
PAPER_VERSION="1.21.4-R0.1-SNAPSHOT"
PAPER_CACHE_ROOT="${HOME}/.gradle/caches/modules-2/files-2.1/io.papermc.paper/paper-api/${PAPER_VERSION}"

PAPER_SOURCES=$(find "$PAPER_CACHE_ROOT" -name "paper-api-${PAPER_VERSION}-sources.jar" | head -1)
PAPER_JAVADOC=$(find "$PAPER_CACHE_ROOT" -name "paper-api-${PAPER_VERSION}-javadoc.jar" | head -1)
PAPER_BINARY=$(find "$PAPER_CACHE_ROOT" -name "paper-api-${PAPER_VERSION}.jar" ! -name "*sources*" ! -name "*javadoc*" | head -1)

echo "$PAPER_SOURCES"
echo "$PAPER_JAVADOC"
echo "$PAPER_BINARY"
```

### 2-2. キャッシュに jar がない場合

```bash
# Linux / WSL2
./gradlew dependencies --configuration compileClasspath
```

```powershell
# Windows
.\gradlew.bat dependencies --configuration compileClasspath
```

---

## 3. Temp キャッシュの活用

> 毎回 sources.jar / javadoc.jar を展開し直すのは非効率。  
> **プロジェクトフォルダ内の `Temp/` に展開済みデータを保存**し、次回以降はそれを使う。

---

### 3-1. Temp ディレクトリ構造

```
<project_root>/
└── Temp/
    └── paper-api-1.21.4-R0.1-SNAPSHOT/
        ├── sources/        # sources.jar を展開したもの
        └── javadoc/        # javadoc.jar を展開したもの
```

バージョンごとにフォルダを分けることで、複数バージョンが混在しても安全に管理できる。

---

### 3-2. 初回: Temp へ展開する

#### Windows / PowerShell

```powershell
$projectRoot = Get-Location   # プロジェクトルートで実行すること
$tempBase    = Join-Path $projectRoot "Temp\paper-api-$paperVersion"
$tempSrc     = Join-Path $tempBase "sources"
$tempDoc     = Join-Path $tempBase "javadoc"

if (-not (Test-Path $tempSrc)) {
    New-Item -ItemType Directory -Path $tempSrc | Out-Null
    Push-Location $tempSrc
    jar xf $paperSources
    Pop-Location
    Write-Host "sources 展開完了: $tempSrc"
} else {
    Write-Host "sources キャッシュ済み: $tempSrc"
}

if (-not (Test-Path $tempDoc)) {
    New-Item -ItemType Directory -Path $tempDoc | Out-Null
    Push-Location $tempDoc
    jar xf $paperJavadoc
    Pop-Location
    Write-Host "javadoc 展開完了: $tempDoc"
} else {
    Write-Host "javadoc キャッシュ済み: $tempDoc"
}
```

#### WSL2 / Linux

```bash
PROJECT_ROOT=$(pwd)   # プロジェクトルートで実行すること
TEMP_BASE="${PROJECT_ROOT}/Temp/paper-api-${PAPER_VERSION}"
TEMP_SRC="${TEMP_BASE}/sources"
TEMP_DOC="${TEMP_BASE}/javadoc"

if [ ! -d "$TEMP_SRC" ]; then
    mkdir -p "$TEMP_SRC"
    cd "$TEMP_SRC" && jar xf "$PAPER_SOURCES" && cd "$PROJECT_ROOT"
    echo "sources 展開完了: $TEMP_SRC"
else
    echo "sources キャッシュ済み: $TEMP_SRC"
fi

if [ ! -d "$TEMP_DOC" ]; then
    mkdir -p "$TEMP_DOC"
    cd "$TEMP_DOC" && jar xf "$PAPER_JAVADOC" && cd "$PROJECT_ROOT"
    echo "javadoc 展開完了: $TEMP_DOC"
else
    echo "javadoc キャッシュ済み: $TEMP_DOC"
fi
```

---

### 3-3. 2回目以降: Temp を使って検索する

#### Windows / PowerShell

```powershell
$projectRoot = Get-Location
$tempSrc     = Join-Path $projectRoot "Temp\paper-api-$paperVersion\sources"
$tempDoc     = Join-Path $projectRoot "Temp\paper-api-$paperVersion\javadoc"

rg -n "PotionMeta|Registry|NamespacedKey" $tempSrc
```

#### WSL2 / Linux

```bash
PROJECT_ROOT=$(pwd)
TEMP_SRC="${PROJECT_ROOT}/Temp/paper-api-${PAPER_VERSION}/sources"
TEMP_DOC="${PROJECT_ROOT}/Temp/paper-api-${PAPER_VERSION}/javadoc"

rg -n "PotionMeta|Registry|NamespacedKey" "$TEMP_SRC"
```

---

### 3-4. .gitignore への追加

Temp フォルダはリポジトリに含める必要はない。`.gitignore` に追加する。

```gitignore
# Paper API 解析用一時キャッシュ
Temp/
```

---

### 3-5. Temp の削除 (バージョン更新時)

バージョンが上がった場合は古い Temp を削除してから再展開する。

#### Windows / PowerShell

```powershell
Remove-Item -Recurse -Force "Temp\paper-api-<旧バージョン>"
```

#### WSL2 / Linux

```bash
rm -rf Temp/paper-api-<旧バージョン>
```

---

## 4. sources.jar の中を見る

> **2回目以降は Temp/paper-api-\<version\>/sources/ を直接参照する。**

### 4-1. クラス名を探す

#### Windows / PowerShell

```powershell
# Temp キャッシュを使う場合
rg -n "class PotionMeta|interface PotionMeta" $tempSrc

# jar から直接確認する場合
jar tf $paperSources | Select-String 'PotionMeta|PotionEffectType|Registry'
```

#### WSL2 / Linux

```bash
# Temp キャッシュを使う場合
rg -n "class PotionMeta|interface PotionMeta" "$TEMP_SRC"

# jar から直接確認する場合
jar tf "$PAPER_SOURCES" | grep -E 'PotionMeta|PotionEffectType|Registry'
```

---

### 4-2. 単一ファイルを直接読む

#### Windows / PowerShell

```powershell
Get-Content "$tempSrc\org\bukkit\inventory\meta\PotionMeta.java"
Get-Content "$tempSrc\org\bukkit\potion\PotionEffectType.java"
Get-Content "$tempSrc\org\bukkit\Registry.java"
```

#### WSL2 / Linux

```bash
cat "$TEMP_SRC/org/bukkit/inventory/meta/PotionMeta.java"
cat "$TEMP_SRC/org/bukkit/potion/PotionEffectType.java"
cat "$TEMP_SRC/org/bukkit/Registry.java"
```

### ✅ sources で確認するポイント

- メソッドが今のバージョンで **本当に存在するか**
- 引数の **型・数**
- 戻り値の **型**
- `@Deprecated` が付いていないか
- static 取得か registry 取得か

---

## 5. javadoc.jar の確認

sources だけでは意図がわからない場合に使う。

#### Windows / PowerShell

```powershell
rg -n "PotionMeta|Registry" $tempDoc
```

#### WSL2 / Linux

```bash
rg -n "PotionMeta|Registry" "$TEMP_DOC"
```

---

## 6. 公式 Paper ドキュメントで最終確認

ローカルキャッシュ確認後、**必ず公式資料も確認する**。

| 優先順位 | 参照先 | 用途 |
|----------|--------|------|
| 1 | https://jd.papermc.io/ | API 仕様・メソッドシグネチャの最終確認 |
| 2 | https://docs.papermc.io/ | 概念・新しい使い方・ガイド |
| 3 | Bukkit/Paper の GitHub ソース | 上記で不明な場合のみ |

> ⚠️ Paper 独自 API は Bukkit の古い記事より **Paper 公式を優先** する。

---

## 7. プロジェクト内の既存実装を探す

**いきなり新規実装せず、まず既存コードを確認する。**

### 7-1. API 名で検索

```bash
rg -n "PotionMeta|PotionEffectType|Registry\.MOB_EFFECT|NamespacedKey" src/main/java -S
```

### 7-2. 特定パターンを探す

```bash
# タブ補完・インベントリ付与パターン
rg -n "getInventory\(\)\.addItem|tab\(" src/main/java -S

# イベント実装パターン
rg -n "@EventHandler" src/main/java -S

# コマンド実装パターン
rg -n "implements CommandExecutor|implements TabCompleter" src/main/java -S
```

### ✅ 既存コード確認のポイント

- 同じことをしているコードがないか
- 命名規則が揃っているか
- Adventure `Component` を使うべきか
- タブ補完・イベントの書き方が揃っているか

---

## 8. API を読む時の判断基準

### Registry 系 API

最近の Paper/Bukkit は static getter より **registry アクセスが正しい** ことがある。

```java
// 古い書き方 (非推奨になっている場合がある)
PotionEffectType.getByName("speed");

// 新しい書き方 (バージョンによる)
Registry.EFFECT.get(NamespacedKey.minecraft("speed"));
```

確認対象:
- `org.bukkit.Registry`
- `io.papermc.paper.registry.RegistryAccess`
- `NamespacedKey`

---

### ItemMeta / PotionMeta 系

```java
ItemMeta meta = item.getItemMeta();
if (meta instanceof PotionMeta potionMeta) {
    // ...
}
item.setItemMeta(meta);   // 最後に必ず戻す
```

確認ポイント:
- そのメタが専用型かどうか
- `getItemMeta()` の戻り値をどこで cast するか
- `setItemMeta()` を最後に呼んでいるか

---

### 時間・数値の単位

| 項目 | 確認事項 |
|------|----------|
| 時間 | 秒なのか tick なのか |
| 数値 | 小数を許可するか |
| タブ補完 | 単位候補を出すか |

---

## 9. よく使うコマンド集

### Paper API バージョン確認

```powershell
# Windows
Select-String -Path .\build.gradle -Pattern 'paper-api'
```

```bash
# Linux / WSL2
grep 'paper-api' build.gradle
```

### Temp の存在確認

```powershell
# Windows
Test-Path "Temp\paper-api-$paperVersion\sources"
```

```bash
# Linux / WSL2
[ -d "Temp/paper-api-${PAPER_VERSION}/sources" ] && echo "キャッシュあり" || echo "キャッシュなし"
```

### Temp から特定クラスを検索

```powershell
# Windows
rg -n "TargetClass" $tempSrc
```

```bash
# Linux / WSL2
rg -n "TargetClass" "$TEMP_SRC"
```

### 特定コードの行番号付き検索 (Windows)

```powershell
Select-String -Path "src/main/java/**/*.java" -Pattern "targetMethodName\(\)" |
    ForEach-Object { "{0}:{1}: {2}" -f $_.Path, $_.LineNumber, $_.Line.Trim() }
```

### ビルド

```powershell
# Windows
.\gradlew.bat build
```

```bash
# Linux / WSL2
./gradlew build
```

---

## 10. 実装後の最終確認チェックリスト

```
[ ] gradle build が通る (エラー・警告なし)
[ ] タブ補完が実際の引数仕様と一致している
[ ] API の取得方法が現在の Paper バージョンに合っている
[ ] @Deprecated な API を使っていない
[ ] 既存の設計・命名規則に反していない
[ ] リフレクションを使っていない
[ ] 不要な try-catch を追加していない
[ ] コメントは Javadoc 形式のみ (// コメントなし)
[ ] 既存コードと同じスタイルで書かれている
[ ] Temp/ が .gitignore に追加されている
```

---

> **この手順書の目的**: AI や開発者が「雰囲気で API を書かないこと」。  
> 必ず Temp キャッシュ → ローカルキャッシュ → 公式 Paper 資料の順に確認してから実装する。
