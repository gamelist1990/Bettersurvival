# コマンド一覧

このページは `Loader#registerCommands()` で実際に登録されるコマンドを基準にしています。サブコマンドや権限は各 Command 実装が正です。

| コマンド | 主な用途 |
| --- | --- |
| `/BetterHelp` | BetterSurvival のコマンドヘルプ |
| `/toggle` | プレイヤー / グローバルの機能Toggle GUI・設定 |
| `/chest` | ChestLock のロック・共有・メンバー管理 |
| `/rename` | 手持ちアイテムの名前変更 |
| `/tpa` | テレポートリクエスト |
| `/otherworld` | Otherworld / world group 管理 |
| `/home` | Home の登録・移動 |
| `/invsee` | プレイヤーインベントリ閲覧・編集 |
| `/list` | オンラインプレイヤー一覧 |
| `/ping` | サーバーとの通信遅延確認 |
| `/hotp` | WebSite 登録用ワンタイムコード |
| `/discord` | Discord 通知 / 設定 UI |
| `/w` | 初回参加前ユーザー向け pending whitelist 管理 |
| `/webservice` | WebService / WebMap の統合管理 |
| `/status` | サーバー稼働状況・統計、`reset` |
| `/performance` | 省電力モード管理 |
| `/party` / `/p` | Party（ギルド）管理・GUI |
| `/land` | LandProtect の情報・デバッグ |
| `/offline` | OfflineAccess 許可リスト管理 |
| `/command` | グローバルなコマンド無効化管理 |
| `/youtube` | YouTube Live Chat 連携 |
| `/sit` | その場に座る / 立つ |
| `/pet` | Pet 対象Mob・合成素材等の確認 / 管理 |
| `/hardmode` | TrueCrafter / Just Leveling 管理 |

## `/w`

`/w` は `ADMIN_OR_CONSOLE` です。

```text
/w add <username>
/w remove <username>
/w list
/w discord
```

`discord` はプレイヤーから実行し、Discord Bot の whitelist channel menu を開きます。

## `/hardmode`

`/hardmode` は `ADMIN_OR_CONSOLE` です。

```text
/hardmode list
/hardmode truecrafter <enabled|disabled>
/hardmode truecrafter heat <1-5>
/hardmode leveling <enabled|disabled>
/hardmode leveling open
/hardmode leveling book
/hardmode leveling profile
/hardmode leveling skills
/hardmode leveling top
/hardmode leveling titles
/hardmode leveling title <key>
```

コード上は `/hardmode leveling stats` が `profile`、`/hardmode leveling ranking` が `top` の別名としても処理されます。

一般プレイヤーが Just Leveling を利用する通常導線は管理コマンドではなく、**レベリングの書を右クリックして ChestGUI を開く**方法です。詳細は [LEVELING_SYSTEM.md](LEVELING_SYSTEM.md) を参照してください。

## Toggle 対象機能

`/toggle` から扱う機能 key と機能名は [FEATURES.md](FEATURES.md) にまとめています。新しい `ToggleFeature` を `Loader` に登録した場合は、同時にそちらの表も更新してください。

## 権限について

BetterSurvival は `BaseCommand` / `PermissionLevel` でコマンドごとの利用レベルを判定しています。管理系コマンドは OP / console 向けのものが多いため、Wiki のコマンド例だけを根拠に一般プレイヤーへ開放しないでください。実運用時は各 Command の `getPermissionLevel()` を基準にしてください。
