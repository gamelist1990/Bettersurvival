import { SectionShell } from '../components/SectionShell';
import { CommandBox } from '../components/CommandBox';
import { WikiLink } from '../components/WikiNavContext';

export function ChestShopSection() {
  return (
    <SectionShell
      eyebrow="ChestShop"
      title="チェストショップ"
      intro="看板を使って自分のチェストをそのままお店にする機能。通貨アイテムを指定して、複数の商品スロットを 1 台のチェストで販売できます。"
      scope="player"
    >
      <h3>どんな機能か</h3>
      <ul className="wiki-bullets">
        <li>チェスト / トラップチェスト / 樽の近くに <strong>看板</strong>を立てて、そのチェストをショップに変えます。</li>
        <li>通貨は自由指定 (エメラルドでもダイヤでも独自アイテムでも OK)。</li>
        <li>1 台につき最大 <strong>26 スロット分の商品</strong>を並べられます。それぞれ個別に価格・個数・説明を設定可能。</li>
        <li>オーナーは <strong>収益をまとめて回収</strong>できるインベントリを持ちます。</li>
        <li>ChestLock との整合が取られていて、他人にロックされたチェストはショップにできません。</li>
      </ul>

      <h3>ショップを作る (最短手順)</h3>
      <ol>
        <li>ショップにしたい <strong>空のチェスト</strong>を用意します (中身が入っていると作成不可)。</li>
        <li>そのチェストの隣接ブロックか近く (半径 3 以内) に <strong>看板</strong>を置きます。</li>
        <li>看板のどこかの行に <code>&gt;&gt;shop</code> と書いて確定 (大文字小文字は問いません)。</li>
        <li>末尾に <strong>ショップ名</strong>を書くと、その名前で登録されます。例:
          <ul className="wiki-bullets" style={{ marginTop: 8 }}>
            <li><code>&gt;&gt;shop</code>  → 自動で <code>shop-XXXXXX</code> という名前になる</li>
            <li><code>&gt;&gt;shop はちみつ屋</code> → 「はちみつ屋」で登録</li>
          </ul>
        </li>
        <li>成功すると看板の 1 行目が <code>&gt;&gt;Shop</code>、2 行目にショップ名が上書きされます。<em>「ショップを作成しました」</em>のメッセージが出れば完了。</li>
      </ol>

      <h3>商品を並べる (オーナー UI)</h3>
      <ol>
        <li>ショップにしたチェストを<strong>右クリック</strong>して開きます。オーナー用の GUI が出ます。</li>
        <li>左上の <strong>「編集ページを開く」</strong>(書きかけの本アイコン) をクリック。</li>
        <li>エディタ画面の 26 スロットに <strong>売りたいアイテムをそのままドラッグ</strong>します。</li>
        <li>各スロットは:
          <ul className="wiki-bullets" style={{ marginTop: 8 }}>
            <li><strong>個数</strong>: そのスロットに置いた数がそのまま「1 回の販売単位」。</li>
            <li><strong>価格</strong>: 別途 GUI から設定 (最大 64)。</li>
            <li><strong>説明</strong>: 任意で商品説明を入力。<code>&lt;br&gt;</code> で改行できます。</li>
            <li><strong>エンチャントや NBT も保持</strong>されます。</li>
          </ul>
        </li>
      </ol>

      <h3>通貨の指定</h3>
      <ul className="wiki-bullets">
        <li>オーナー UI の <strong>通貨スロット (スロット 12)</strong> に、<strong>通貨として使いたいアイテムを 1 個</strong>入れます。</li>
        <li>そのアイテムが今後の支払いに使われます (Material 名で保存)。</li>
        <li>通貨未設定の商品には「通貨未設定 (販売不可)」と表示され、購入できません。</li>
        <li>カスタム名を付けた特殊アイテムを通貨にすることも可能。</li>
      </ul>

      <h3>在庫の補充と収益</h3>
      <ul className="wiki-bullets">
        <li>オーナー UI の <strong>供給スロット (スロット 10)</strong> に商品を入れると、その商品の在庫として吸われます。</li>
        <li>売り切れた商品はバイヤー側に「品切れ中」と赤字で表示され、購入不可になります。</li>
        <li><strong>収益スロット (スロット 15、金インゴットアイコン)</strong> をクリックすると、たまった通貨アイテムを回収できます。</li>
        <li>売買状況は <strong>Info スロット (スロット 19、紙)</strong> に総在庫と売り切れ商品の一覧が出ます。</li>
      </ul>

      <h3>お客さん (バイヤー) の使い方</h3>
      <ol>
        <li>ショップのチェストを右クリック → バイヤー用 GUI が開きます。</li>
        <li>並んでいる商品をクリックすると <strong>1 単位分の購入</strong>が試行されます。</li>
        <li>あなたのインベントリから通貨が引かれ、商品が渡されます。通貨が足りないと拒否されます。</li>
        <li>オーナー自身も、スニーク + 右クリックで「バイヤー視点」を確認できます (プレビュー)。</li>
      </ol>

      <h3>看板とチェストの保護</h3>
      <ul className="wiki-bullets">
        <li>ショップ登録済みの看板は <strong>他プレイヤーが編集・破壊できません</strong> (OP は例外)。</li>
        <li>チェスト側もピストン・爆発などから内部的に保護されます。</li>
        <li><strong>ロック済み (ChestLock) のチェストにはショップを作れません</strong>。先にロックを外すか、別チェストで作ってください。</li>
      </ul>

      <h3>よくあるハマりどころ</h3>
      <ul className="wiki-bullets">
        <li><strong>看板を書いても反応しない</strong>: 近く (半径 3 以内) に <strong>空のチェスト / 樽</strong>が必要です。既に他のショップ or ロック済みチェストは対象外。</li>
        <li><strong>「既に使われている名前」と出る</strong>: サーバー内でショップ名は <strong>大文字小文字を無視して一意</strong>。別の名前を試してください。</li>
        <li><strong>買えない</strong>: 通貨が未設定、または価格が 64 超で「販売不可」になっている可能性。オーナー UI で確認を。</li>
        <li><strong>商品が消える</strong>: 供給スロットに入れたアイテムは在庫として管理されます。バグではなく、Info スロットで在庫を確認してください。</li>
      </ul>

      <h3>コマンド</h3>
      <p>ChestShop 自体には専用コマンドは用意されていません。看板とチェスト GUI で全ての操作が完結します。関連する範囲では:</p>
      <CommandBox command="/chest" description="チェストの ChestLock 管理 (ロックしたチェストはショップにできないので注意)。" />

      <h3>関連ページ</h3>
      <ul className="wiki-bullets">
        <li><WikiLink to="/wiki/shared-storage"><strong>共有ストレージ</strong></WikiLink>: 主チェストの隣にはショップ用チェストを置けません。設計時の注意。</li>
        <li><WikiLink to="/wiki/custom-blocks"><strong>カスタムブロック / 設置物</strong></WikiLink>: 他の合成系設備との位置づけ。</li>
        <li><WikiLink to="/wiki/commands"><strong>コマンド一覧</strong></WikiLink>: <code>/chest</code> の詳細。</li>
      </ul>
    </SectionShell>
  );
}
