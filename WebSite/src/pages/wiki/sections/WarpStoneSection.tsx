import { SectionShell } from '../components/SectionShell';
import { WikiLink } from '../components/WikiNavContext';

export function WarpStoneSection() {
  return (
    <SectionShell
      eyebrow="WarpStone"
      title="ワープストーン"
      intro="Waystones 風のワープ地点。設置して名前を付けると、それを見つけたプレイヤーがいつでもそこへ飛べるようになります。座標本や GTA 風カメラ演出まで用意された作り込みの深い機能です。"
      scope="player"
    >
      <h3>ざっくり何ができるか</h3>
      <ul className="wiki-bullets">
        <li>ワープストーンを設置すると <strong>ワープ地点</strong>になります。</li>
        <li>他プレイヤーが <strong>そのストーンを右クリック</strong>すると「発見済み」に登録され、以後どこからでもそこへワープできます。</li>
        <li>ワープストーンごとに<strong>名前</strong>が付き、頭上に浮くテキストで表示されます。</li>
        <li>移動時のカメラ演出 (GTA 風) はプレイヤー単位で ON/OFF 可能。</li>
        <li>座標を紙面化する「<strong>座標本</strong>」機能もあります。</li>
      </ul>

      <h3>設置して名前を付ける</h3>
      <ol>
        <li>合成レシピ (エンダーパール + 石レンガ) でワープストーンを作ります。詳細は <WikiLink to="/wiki/recipes">合成レシピ一覧</WikiLink>。</li>
        <li>設置すると、<strong>自動で「(あなたの名前)のワープストーン」</strong>という仮名が付きます。</li>
        <li>設置直後にチャット入力ダイアログが開きます。好きな名前 (<strong>24 文字まで</strong>) を入力して確定。</li>
        <li>入力を空のまま閉じた場合は仮名がそのまま採用されます。</li>
      </ol>

      <h3>後から名前を変える</h3>
      <p>オーナーはワープ選択 UI (右クリックで開く画面) から<strong>いつでも改名</strong>できます:</p>
      <ol>
        <li>自分のワープストーンを右クリックして UI を開く。</li>
        <li>左上の <strong>「✎ 名前を変更」</strong> (書きかけの本アイコン) をクリック。</li>
        <li>チャット入力ダイアログに新しい名前を入力して確定 (24 文字まで)。</li>
      </ol>
      <p><strong>ショートカット</strong>: 素手のまま <strong>スニーク + 右クリック</strong>でも同じ入力ダイアログが直接開きます。慣れてきたらこちらの方が速いです。</p>
      <p>他プレイヤーの UI にはこのボタンは表示されず、他人からの名前変更はできません。</p>

      <h3>他プレイヤーに発見してもらう</h3>
      <ol>
        <li>他プレイヤーがそのワープストーンを <strong>普通に右クリック</strong>すると、
          <em>「XX を発見しました！」</em>のメッセージ + レベルアップ音 + パーティクル演出が入り、</li>
        <li>その人の「発見済みリスト」に登録されます。</li>
        <li>以後、その人はどのワープストーンからでも、あなたのストーンをワープ先として選べます。</li>
      </ol>

      <h3>ワープする</h3>
      <ol>
        <li>発見済みのワープストーンを右クリック → 選択 UI が開きます。</li>
        <li>行きたい先を選ぶと即座にワープします (自分自身のストーンは選択肢から除外)。</li>
        <li>GTA 風演出が有効な人は、飛行カメラの演出が入ってからワープします。</li>
      </ol>

      <h3>GTA 風カメラ演出</h3>
      <ul className="wiki-bullets">
        <li>ワープ選択画面の設定から <strong>プレイヤー単位で ON/OFF</strong> を切り替えられます。</li>
        <li>既定は ON。ワープ元から目的地までカメラが飛行する派手めの演出。</li>
        <li>OFF にすれば普通のインスタントワープ。演出が邪魔なときや低スペック端末ではこちらを推奨。</li>
      </ul>

      <h3>座標本 (ワープ地点の共有 / 解放)</h3>
      <ol>
        <li>ワープストーンに向かって <strong>本を持って右クリック</strong>すると、<strong>「座標本」</strong>が渡されます。</li>
        <li>座標本を <strong>スニーク + 右クリック</strong>（地面などに向けて）すると、本を 1 冊消費してそのワープ地点を<strong>解放</strong>します（＝発見済みとして登録）。以後どこからでもワープ先として選べるようになります。</li>
        <li><strong>通常の右クリックでは何も起こりません</strong>（画面は開きません）。解放は<strong>スニーク + 右クリックのみ</strong>で、誤爆を防ぐための仕様です。</li>
        <li>他の人に座標本を渡せば、その人も同じ手順でワープ地点を解放できます。地点の共有アイテムとして使えます。</li>
      </ol>

      <h3>他の人のワープストーンとの関係</h3>
      <ul className="wiki-bullets">
        <li>ワープストーンの所有者情報は保持されていて、名前変更は<strong>オーナーだけ</strong>ができます。</li>
        <li>他人が壊しても、破壊時に本体アイテムがドロップします (クリエイティブ以外)。</li>
        <li>爆発でも失われるので、大事なワープ地点は<strong>土地保護の中に置く</strong>のがおすすめ。</li>
      </ul>

      <h3>よくあるハマりどころ</h3>
      <ul className="wiki-bullets">
        <li><strong>ワープ先が出てこない</strong>: そのワープストーンをまだ発見していません。持ち主にたどり着いて 1 度右クリックしてください。</li>
        <li><strong>名前入力ダイアログがすぐ閉じた</strong>: 設置直後の 1 tick 遅延を挟んで開きます。それでも閉じてしまう場合は、スニーク + 素手右クリックで再表示できます。</li>
        <li><strong>演出が長い</strong>: 選択画面の GTA トグルで OFF に。</li>
        <li><strong>他人の土地で使えない</strong>: 目的地が保護されていて、その保護のルール上あなたが弾かれる可能性があります。土地保護の設定を確認してください。</li>
      </ul>

      <h3>関連ページ</h3>
      <ul className="wiki-bullets">
        <li><WikiLink to="/wiki/recipes"><strong>合成レシピ一覧</strong></WikiLink>: ワープストーン本体の作り方。</li>
        <li><WikiLink to="/wiki/custom-blocks"><strong>カスタムブロック / 設置物</strong></WikiLink>: 他の合成系設備との位置づけ。</li>
        <li><WikiLink to="/wiki/land-protection"><strong>土地保護</strong></WikiLink>: ワープストーンを守る運用。</li>
      </ul>
    </SectionShell>
  );
}
