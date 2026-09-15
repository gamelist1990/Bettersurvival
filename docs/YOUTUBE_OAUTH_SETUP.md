# YouTube OAuth の配布者向け設定

BetterSurvivalは、OAuth Client IDをJARへ組み込んで配布できます。組み込み済みのJARでは、サーバー管理者がClient IDやClient Secretを入力する必要はありません。

## Google Cloud側の準備

1. [Google Cloud Console](https://console.cloud.google.com/)でプロジェクトを作成または選択します。
2. YouTube Data API v3を有効にします。
3. OAuth同意画面を設定します。テスト中はログインするGoogleアカウントをテストユーザーへ追加します。
4. OAuthクライアントを作成し、アプリケーションの種類に「TVと入力機能が限られたデバイス」を選びます。
5. 発行されたClient IDを控えます。Client Secretは省略可能です。

Googleの仕様上、OAuthには登録済みClient IDが必須です。BetterSurvivalはこの値を配布JARへ組み込むことで、各サーバーでの入力を不要にします。

## Client IDを組み込んでビルドする

PowerShellでは環境変数を設定してビルドします。

```powershell
$env:YOUTUBE_OAUTH_CLIENT_ID = '発行されたClient ID'
.\gradlew.bat clean build
Remove-Item Env:YOUTUBE_OAUTH_CLIENT_ID
```

またはGradleプロパティとして渡せます。

```powershell
.\gradlew.bat clean build -PyoutubeOAuthClientId='発行されたClient ID'
```

生成物は `build/libs/Bettersurvival-<version>.jar` です。Client Secretも必要なクライアントの場合のみ、`YOUTUBE_OAUTH_CLIENT_SECRET` または `-PyoutubeOAuthClientSecret=...` を指定できます。

## Minecraftでログインする

1. 組み込み済みJARをサーバーへ導入します。
2. OP権限で `/youtube login` を実行します。
3. 表示されたGoogle認証URLを開き、画面のコードを入力します。
4. `/youtube status` でログイン状態を確認します。
5. `/youtube <配信URL>` でコメント連携を開始します。

OAuthログイン後はYouTube Data APIキーが不要です。トークンはプラグインの `youtube.yml` に保存されるため、このファイルを公開しないでください。

## 開発ビルドと上書き設定

Client IDを組み込まずにビルドした場合は、`/youtube settings` のOAuth Client ID欄から従来どおり設定できます。設定画面で入力した値は、組み込み値より優先されます。

参考資料:

- [YouTube Data API OAuth認証](https://developers.google.com/youtube/v3/guides/authentication)
- [TV・入力制限デバイス向けOAuth](https://developers.google.com/youtube/v3/guides/auth/devices)
