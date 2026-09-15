import { SectionShell } from '../components/SectionShell';
import { CommandBox } from '../components/CommandBox';

export function YouTubeSection() {
  return (
    <SectionShell eyebrow="YouTube" title="YouTube ライブ連携" intro="ライブ配信のコメントや支援イベントをMinecraftへ中継する管理者向け機能です。" scope="admin">
      <h3>使い始める</h3>
      <ol>
        <li>配布版に共通OAuth Client IDが組み込まれている場合は、認証情報の入力は不要です。</li>
        <li><code>/youtube login</code>を実行し、表示されたURLでコードを入力してチャンネル所有者のGoogleアカウントを認証します。</li>
        <li><code>/youtube settings</code>を開き、表示する通知とBossBarの時間を設定します。</li>
        <li><code>/youtube &lt;配信URL&gt;</code>で監視を開始します。開始前の過去コメントは流れません。</li>
      </ol>
      <CommandBox command="/youtube" description="使い方を説明するDialogを開きます。" />
      <CommandBox command="/youtube settings" description="通知種別とBossBarを設定します。" />
      <CommandBox command="/youtube login" description="Google OAuth認証を開始します。ログイン後はAPIキー不要です。" />
      <CommandBox command="/youtube <配信URL>" description="ライブコメントの中継を開始します。" />

      <h3>配布者向けOAuth設定</h3>
      <p>Google CloudでYouTube Data API v3を有効にし、「TVと入力機能が限られたデバイス」タイプのOAuth Client IDを作成します。Client Secretは省略できます。ビルド時に<code>youtubeOAuthClientId</code> Gradleプロパティ、または<code>YOUTUBE_OAUTH_CLIENT_ID</code>環境変数を指定すると、Client IDがJARへ組み込まれます。サーバー管理者はClient IDを入力せずに<code>/youtube login</code>を利用できます。</p>
      <p>共通Client IDが組み込まれていない開発ビルドでは、従来どおり<code>/youtube settings</code>からClient IDを設定できます。</p>

      <h3>設定できる通知</h3>
      <ul className="wiki-bullets">
        <li>通常コメントのチャット中継</li>
        <li>スーパーチャット、スーパーステッカー</li>
        <li>新規メンバー、継続マイルストーン、メンバーシップギフト</li>
        <li>公開チャンネル登録者数の増減</li>
        <li>特別通知のBossBar表示と3～30秒の表示時間</li>
      </ul>
      <p>BossBarは満タンから始まり、設定時間に合わせて滑らかに減少して消えます。OAuthログイン中は、登録を公開している新規登録者を「○○さんがチャンネル登録しました」と通知します。非公開登録者の名前と、登録解除した個人名は取得できません。その分は公開登録者数の増減で補います。公開値は3桁の有効数字に丸められるため、小さな増減はすぐに見えない場合があります。</p>

      <h3>管理コマンド</h3>
      <CommandBox command="/youtube status" description="連携中の配信を確認します。" />
      <CommandBox command="/youtube logout" description="保存済みOAuthログイン情報を削除します。" />
      <CommandBox command="/youtube stop" description="コメント連携を停止します。" />
      <CommandBox command="/youtube setkey <APIキー>" description="Dialogを使わずAPIキーを更新します。" />
    </SectionShell>
  );
}
