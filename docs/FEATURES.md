# 機能一覧

このページは `Loader` が初期化・登録している機能を基準にした実装カタログです。README に載っていた少数の旧機能一覧だけでは現状をカバーできていなかったため、実装済み機能をカテゴリ別に整理しています。

## 採集・生活

| 機能 | 概要 | Toggle key |
| --- | --- | --- |
| TreeMine | スニーク中に木を一括伐採 | `treemine` |
| OreMine | スニーク中に近接鉱石を一括採掘 | `oremine` |
| AutoFeed | 餌やり時に周辺の動物にも自動給餌 | `autofeed` |
| AutoFishing | 移動・大きな視点変更まで自動釣りを継続 | `autofishing` |
| AnythingFeed | 非繁殖動物を任意の食料に反応させる | `anythingfeed` |
| AutoPlant | オフハンドの作物を耕地周辺で自動植え付け・収穫 | `autoplant` |
| DeathChest | 死亡時の所持品を付近のラージチェストに退避し座標通知 | `deathchest` |
| Home | 最大3件の Home 登録・移動 | `home` |
| AirDash | 空中でジャンプを再入力するとダッシュ、クールダウンを ActionBar 表示 | `airdash` |
| Sit | プレイヤーをその場に座らせるコマンド機能 | — |

## チェスト・アイテム・設備

| 機能 | 概要 | Toggle key |
| --- | --- | --- |
| ChestLock | チェストの破壊・移動・取得を制限する保護 | `chestlock` |
| ChestShop | 看板でチェストをショップ化 | `chestshop` |
| ChestSort | スニーク + 木の棒でチェストを整理 | `chestsort` |
| SharedStorage | 主チェストと sub チェストによる共有ストレージ | `sharedstorage` |
| EnchantSplit | 複数エンチャント本を分離する専用砥石 | `enchantsplit` |
| BetterMenu | 木の斧を使う GUI ツール | `bettermenu` |
| ChunkLoader | コンパス + 名札を材料にするチャンクローダー | `chunkloader` |
| ParallelFurnace | かまど + 石炭ブロックで作る並列かまど | `parallelfurnace` |
| Recycler | 不要品を素材へ分解・処分するリサイクラー | `recycler` |
| CustomEnchant | 専用カスタムエンチャント台 | `customenchant` |
| WarpStone | Waystone 風ワープ機能とカメラ演出 | `warpstone` |
| ItemCombine | 複数の特殊アイテム / 設備の合成・識別を支える内部モジュール | — |

## プレイヤー・コミュニティ

| 機能 | 概要 | Toggle key |
| --- | --- | --- |
| TPA | プレイヤー間のテレポートリクエスト | `tpa` |
| Party | ギルド風パーティー機能 (`/party`, `/p`) | `party` |
| LandProtect | ロデストーン + ダイヤで作る土地保護コア。Party / Otherworld と連携 | `landprotect` |
| Pet | 対象 Mob / 合成素材を扱う Pet 機能 | — |
| CopperGolem | カッパーゴーレムの召喚と作物採取 AI | `coppergolem` |
| JPCh | ローマ字チャットを検出して日本語へ自動変換 | `jpch` |
| InvSee | プレイヤーのインベントリを閲覧・編集し、オフライン snapshot も扱う | — |
| OfflineAccess | オフラインアカウントのログイン許可 / 拒否 | `offlineaccess` |
| PendingWhitelist | 初回参加前ユーザーの接続待機 / whitelist 補助 | — |

## Bedrock / Geyser

| 機能 | 概要 | Toggle key |
| --- | --- | --- |
| BedrockSkin | Bedrock ユーザーのスキンを Java クライアントへ反映 | `bedrockskin` |
| Geyser金床 | Geyser / Bedrock 向け金床 UI | `geyseranvil` |
| Geyser鍛冶台 | Geyser / Bedrock 向け鍛冶台 UI | `geysersmithing` |

詳細は [Floodgate.md](Floodgate.md) も参照してください。

## Web・運営・外部連携

| 機能 | 概要 | Toggle key |
| --- | --- | --- |
| WebMap | 軽量 Web マップと ChunkGen | `webmap` |
| WebService | ホームページ、ログイン、プロフィール機能 | `webservice` |
| DiscordWebhook | Discord 通知 | — |
| DiscordBot | Discord Bot 連携 | — |
| YouTube Live Chat | YouTube ライブチャット連携 | — |
| Performance | 無人時などの負荷を抑える省電力モード | — |
| MOTD | `motd/` の `icon.png` / `motd.json` によるサーバー表示カスタマイズ | — |
| KeepAliveGuard | OP の keepalive timeout kick を可能な範囲で抑止 | `keepaliveguard` |
| CommandBlocker | コマンドをグローバルに無効化する管理機能 | — |

## ワールド・HardMode

| 機能 | 概要 | 管理 |
| --- | --- | --- |
| Otherworld | ワールドグループ単位の別プロフィール / データスコープを提供 | `/otherworld` |
| TrueCrafter | HardMode のクラフト拡張。enabled/disabled と heat 1–5 を管理 | `/hardmode truecrafter ...` |
| Just Leveling | 8能力値、パッシブ、24スキル、称号、ランキング等を含むレベリングシステム | `/hardmode leveling ...` |

Just Leveling の正確な仕様は [LEVELING_SYSTEM.md](LEVELING_SYSTEM.md) を参照してください。

## Toggle の初期値

現行 `Loader` では多くのサバイバル機能を初期有効にし、`autofishing`, `sharedstorage`, `enchantsplit`, `webmap`, `offlineaccess` など一部を初期無効にしています。既存サーバーでは保存済みグローバル設定が優先されるため、アップデートだけで設定が強制的に上書きされるわけではありません。

## 実装との同期について

この一覧にない機能を `Loader` に追加した場合は、このページにも追加してください。逆にクラスが残っていても `Loader` から初期化・登録されていないものは「現在有効な機能」として扱わない方針です。
