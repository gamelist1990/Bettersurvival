# カスタムネームタグ

頭上のネームタグに役職、個人タグ、既存のパーティー表示とプレイヤー名を表示します。表示エンジンは PEXServer の PacketEvents ベースの nametag 実装を移植したものです。PacketEvents が必要です。

- `/nametag set <タグ>`: 自分のタグを設定（1〜4 Unicodeコードポイント）。
- `/nametag clear`: 自分のタグを消去。
- `/nametag role <オンラインプレイヤー> <役職>`: 役職を設定（OPまたは `bettersurvival.nametag.role` 権限）。
- `/nametag role <オンラインプレイヤー> clear`: 個別役職を消去。OPは個別役職がなければ自動的に Admin と表示。

個人タグと役職は `PEXConfig/NameTag/players.json` に保存します。パーティー名と色は既存のパーティー設定に従い、ワールド変更などによる表示名の変化は定期的に反映します。表示内容はJava版と統合版のクライアントから確認してください。
