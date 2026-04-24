毎回最新のソースコードを調べてから作業して下さい


## 依存関係及び目的

- Paper Plugin
- Geyser
- Floodgate


## 環境について
- 基本的にはWindows環境ですが、WSL2のLinux環境で作業することも可能です。ただし、WSL2環境で作業する場合は、Windows環境でのファイルパスやコマンドを使用してください。特に、Gradleのビルドやローカルキャッシュの確認などはWindows環境で行うことを推奨します。
- またLinuxのネイティブの環境の場合もあるのでその都度適切に見極めて下さい
**目的** Minecraft PEX鯖で使うオリジナルプラグインの開発  


- Buildはコンソールでgradle buildを行って下さい、gradlw.batでも構いません
- 生成するコードには余計な// コメントを入れないで下さい正し 関数等の説明であるJava docsに限り許可します。
- またユーザーの言語で回答を行いなさい (Japanese)
- 基本リフレクションが絶対禁止です。サポートが厳しくなる為API等が分からなければユーザーに聞いて下さい教えてくれます(Classのコードを提示したりAPIの使い方等を教えます。)
- コードを生成する際に既存のプロジェクトで既に同様の動作をしていないか？等をCheckし最小差分で変更することで安全性と安定性を確保して下さい。
- 生成するコードは既存のコードと同様のスタイルで書いて下さい。
- 生成する際にtry catchを多用しないで下さい、例外処理は必要な場合にのみ行って下さい。(try catchを多用すると肝心な部分でエラーを見逃す為確実な安定性を確保する為に止めて下さい。またほぼ意味のないtry catchを見つけたら削除して下さい(前後関係をよく見て消しても問題ない場合に消すように))
- コードの部分的な物やコードがうまく分からない時とか不明な場合によりしっかりと取得したい場合はコマンドを使っていいよ例 ```Select-String -Path "src/main/java/org/example/koukunn/pexserver/Project/customItem/Duel/*.java" -Pattern "getActiveMapsIds\(\)\.contains\(map\.getId\(\)\)" | ForEach-Object { "{0}:{1}: {2}" -f $_.Path,$_.LineNumber,$_.Line.Trim() }``` みたいな感じのコマンドでしっかりと確認して認識すべきコードを確認して下さい。
- またコード変更後に、IDEやツールのエラー確認ツールを呼び出したり、コマンドでビルドエラーや警告がないか確認して下さい。ビルドエラーや警告がある場合は、コードを修正してから次の作業に進んで下さい。



# Paper API 調査手順

この手順書は、`C:\Users\PC_User\IdeaProjects\bettersurvival` で別の AI や別の開発者が Paper API を正しく調べ、間違った API や古い API を使わずに実装するための固定手順です。

2026年3月13日時点では、このプロジェクトの `build.gradle` は `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` を参照しています。まず毎回ここを確認し、バージョンが変わっていたら以後のコマンド中のバージョン文字列も合わせて変えてください。

## 目的

- 今このプロジェクトが実際に使っている Paper API バージョンを確認する
- ローカルの Gradle キャッシュにある `sources.jar` と `javadoc.jar` を使って API を調べる
- 公式 Paper ドキュメントで最終確認する
- 既存コードの実装例を調べて最小差分で実装する
- 最後に `gradle build` でエラーを潰す

## 絶対に守る順番

1. `build.gradle` で現在の Paper API バージョンを確認する
2. ローカルの `.gradle` キャッシュから、そのバージョンの `sources.jar` と `javadoc.jar` を探す
3. まず `sources.jar` で型・メソッド・引数・deprecated 状態を確認する
4. 次に `javadoc.jar` または公式 Javadoc で説明文を確認する
5. その API をこのプロジェクト内で既に使っていないか `rg` で確認する
6. 既存スタイルに合わせて最小差分で実装する
7. `gradle build` を実行してエラーや警告を確認する

この順番を飛ばさないでください。特に、記憶だけで Paper API を使わないことが重要です。

## 1. まず build.gradle を確認する

PowerShell:

```powershell
Get-Content .\build.gradle
```

このプロジェクトでは次を確認します。

```gradle
compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
```

この文字列が、今読むべきローカルキャッシュの対象です。

## 2. ローカルの Gradle キャッシュから Paper API を探す

## Windows環境です WSL2 の環境の場合はWindows環境で探して下さい。
PowerShell:

```powershell
Get-ChildItem -Recurse -Filter 'paper-api-*.jar' "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\io.papermc.paper\paper-api"
```

今の環境では、たとえば以下のようなものがあります。

- `paper-api-1.21.11-R0.1-SNAPSHOT.jar`
- `paper-api-1.21.11-R0.1-SNAPSHOT-sources.jar`
- `paper-api-1.21.11-R0.1-SNAPSHOT-javadoc.jar`

最重要なのは次の2つです。

- `sources.jar`
- `javadoc.jar`

## 3. 使う jar の場所を PowerShell 変数に入れる

PowerShell:

```powershell
$paperVersion = '1.21.11-R0.1-SNAPSHOT'
$paperCacheRoot = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\io.papermc.paper\paper-api\$paperVersion"

$paperSources = Get-ChildItem -Recurse -Filter "paper-api-$paperVersion-sources.jar" $paperCacheRoot |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 -ExpandProperty FullName

$paperJavadoc = Get-ChildItem -Recurse -Filter "paper-api-$paperVersion-javadoc.jar" $paperCacheRoot |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 -ExpandProperty FullName

$paperBinary = Get-ChildItem -Recurse -Filter "paper-api-$paperVersion.jar" $paperCacheRoot |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 -ExpandProperty FullName

$paperSources
$paperJavadoc
$paperBinary
```

複数候補が出ることがありますが、基本は `LastWriteTime` が新しいものを使います。

## 4. sources.jar の中を見る

### 4-1. クラス名だけ探す

PowerShell:

```powershell
jar tf $paperSources | Select-String 'PotionMeta|PotionEffectType|Registry'
```

または:

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::OpenRead($paperSources).Entries |
    Where-Object { $_.FullName -match 'PotionMeta|PotionEffectType|Registry' } |
    Select-Object -ExpandProperty FullName
```

### 4-2. 一時展開して中身を検索する

PowerShell:

```powershell
$tmp = Join-Path $env:TEMP "paper-api-src-$paperVersion"
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
New-Item -ItemType Directory -Path $tmp | Out-Null
Push-Location $tmp
jar xf $paperSources
Pop-Location
```

展開後は `rg` で検索します。

```powershell
rg -n "addCustomEffect|setBasePotionType|getColor|Registry" $tmp
rg -n "class PotionMeta|interface PotionMeta" $tmp
rg -n "class PotionEffectType|interface Registry" $tmp
```

### 4-3. 単一ファイルを読む

PowerShell:

```powershell
Get-Content "$tmp\org\bukkit\inventory\meta\PotionMeta.java"
Get-Content "$tmp\org\bukkit\potion\PotionEffectType.java"
Get-Content "$tmp\org\bukkit\Registry.java"
```

ここで確認する内容:

- メソッド名が今のバージョンで本当に存在するか
- 引数の型が何か
- 戻り値が何か
- `@Deprecated` かどうか
- static 取得か registry 取得か

## 5. javadoc.jar を確認する

ソースだけでは説明不足のことがあるので、Javadoc も見ます。

PowerShell:

```powershell
jar tf $paperJavadoc | Select-String 'PotionMeta|PotionEffectType|Registry'
```

必要なら一時展開します。

```powershell
$tmpDoc = Join-Path $env:TEMP "paper-api-doc-$paperVersion"
if (Test-Path $tmpDoc) { Remove-Item $tmpDoc -Recurse -Force }
New-Item -ItemType Directory -Path $tmpDoc | Out-Null
Push-Location $tmpDoc
jar xf $paperJavadoc
Pop-Location
```

対象 HTML を探します。

```powershell
rg -n "PotionMeta|PotionEffectType|Registry" $tmpDoc
```

## 6. 公式 Paper ドキュメントで最終確認する

ローカルキャッシュ確認の後、最後に必ず公式資料も見ます。

優先順位:

1. 公式 Javadoc
2. 公式 Docs
3. 必要なら Bukkit/Paper の source

主な参照先:

- Javadoc: `https://jd.papermc.io/`
- Docs: `https://docs.papermc.io/`

確認のしかた:

- API 仕様は Javadoc で確認する
- 概念や新しい使い方は Docs で確認する
- Paper 独自 API は Bukkit の古い記事より Paper 公式を優先する

## 7. プロジェクト内の既存実装を先に探す

このプロジェクトは既に多くの Paper/Bukkit API を使っているので、いきなり新規実装しないで既存コードを確認します。

### 7-1. API 名で検索

PowerShell:

```powershell
rg -n "PotionMeta|PotionEffectType|Registry.MOB_EFFECT|NamespacedKey" src/main/java -S
```

### 7-2. ScriptEvent 実装パターンを探す

PowerShell:

```powershell
rg -n "new ScriptEvent\\(\\)\\.name\\(" src/main/java -S
```

### 7-3. インベントリ付与やタブ補完の既存パターンを探す

PowerShell:

```powershell
rg -n "getInventory\\(\\)\\.addItem|tab\\(" src/main/java -S
```

ここで見るポイント:

- 既に同じことをしているコードがないか
- 命名規則が揃っているか
- Adventure `Component` を使うべきか
- `ScriptEvent` の分岐やタブ補完の書き方が揃っているか

## 8. API を読む時の判断基準

### Registry 系 API

最近の Paper/Bukkit は static getter より registry アクセスが正しいことがあります。  
たとえば、ID から何かを引く時は次を優先確認します。

- `org.bukkit.Registry`
- `io.papermc.paper.registry.RegistryAccess`
- `NamespacedKey`

古い記事で `getByName()` が出てきても、今のバージョンでは registry の方が正しい可能性があります。

### ItemMeta / PotionMeta

アイテム系は `ItemMeta` のサブ型が増えているので、次を毎回確認します。

- そのメタが専用型かどうか
- `getItemMeta()` の戻り値をどこで cast すべきか
- `item.setItemMeta(meta)` を最後に戻しているか

### 時間や数値

ゲーム内コマンドの数値は、実装前に必ず単位を決めます。

- 秒なのか
- tick なのか
- 少数を許可するか
- 補完に単位候補を出すか

## 9. AI にやらせる時の最小プロンプト

他の AI を使う場合は、最低でも次を守らせてください。

```text
1. まず build.gradle の paper-api バージョンを確認する
2. .gradle キャッシュの paper-api sources.jar と javadoc.jar を確認する
3. 公式 Paper Javadoc / Docs で最終確認する
4. 既存コードで同様実装がないか rg で確認する
5. 最小差分で実装する
6. 最後に gradle build を実行してエラーを直す
```

## 10. 実際によく使う確認コマンド集

### Paper API バージョン確認

```powershell
Select-String -Path .\build.gradle -Pattern 'paper-api'
```

### sources.jar の場所確認

```powershell
Get-ChildItem -Recurse -Filter 'paper-api-*-sources.jar' "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\io.papermc.paper\paper-api"
```

### javadoc.jar の場所確認

```powershell
Get-ChildItem -Recurse -Filter 'paper-api-*-javadoc.jar' "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\io.papermc.paper\paper-api"
```

### sources.jar 内の検索

```powershell
jar tf $paperSources | Select-String 'PotionMeta'
jar tf $paperSources | Select-String 'Registry'
```

### 展開後の全文検索

```powershell
rg -n "addCustomEffect|Registry.MOB_EFFECT|NamespacedKey.minecraft" $tmp
```

### プロジェクト内既存使用例検索

```powershell
rg -n "Registry\\.MOB_EFFECT|PotionEffectType|PotionMeta" src/main/java -S
```

### 最終ビルド

```powershell
.\gradlew.bat build
```

## 11. 最後の確認

実装後は必ず次を確認します。

- コンパイルが通る
- タブ補完が実際の引数仕様と一致している
- API の取得方法が現在の Paper バージョンに合っている
- 既存の設計に反していない
- リフレクションを使っていない

この手順書の目的は、AI に「雰囲気で API を書かせないこと」です。  
必ずローカルキャッシュと公式 Paper 資料の両方を確認してから実装してください。
