import { SectionShell } from '../components/SectionShell';
import { WikiLink } from '../components/WikiNavContext';

const tableRows: { label: string; role: string; slots: string }[] = [
  { label: '0 (上端)', role: '操作パネル', slots: '[情報] [状態✦] [－コア] [コア数] [＋コア] [搬出チェスト]' },
  { label: '1', role: '≫ 素材投入口', slots: '素材 8 スロット (編集可)' },
  { label: '2', role: '≫ 焼成ライン', slots: 'ライン進捗 7 スロット (アニメ) + 燃料ゲージ' },
  { label: '3', role: '≫ 燃料投入口', slots: '燃料 8 スロット (編集可)' },
  { label: '4-5', role: '≪ 回収口 (2 行分)', slots: '完成品 16 スロット (編集可、取り出し用)' },
];

const td: React.CSSProperties = { padding: '6px 10px', border: '1px solid rgba(74, 55, 38, 0.16)' };
const th: React.CSSProperties = { ...td, textAlign: 'left', background: 'rgba(74, 55, 38, 0.06)' };

export function ParallelFurnaceSection() {
  return (
    <SectionShell
      eyebrow="ParallelFurnace"
      title="並列かまど"
      intro="かまど × 石炭ブロックで作る、最大 200 ライン同時精錬できる作業コア。素材・燃料・完成品をそれぞれ仮想ストレージで持ち、複数人で共有できる 54 スロット UI が特徴です。"
      scope="player"
    >
      <h3>どんな機能か</h3>
      <ul className="wiki-bullets">
        <li>本体 1 台で <strong>最大 200 個の焼成ラインが同時稼働</strong>します (基本 1 + 追加コア最大 199)。</li>
        <li>素材・燃料・完成品はそれぞれ<strong>仮想ストレージ</strong>で管理され、UI を閉じても状態が持続します。</li>
        <li>1 台につき<strong>共有 UI が 1 つ</strong>。同じ画面を複数プレイヤーで同時に開けます。</li>
        <li><strong>搬出チェスト</strong>を指定できて、完成品を外部の保管場所へ自動で流せます。</li>
        <li>本体を壊すと<strong>内部のアイテムと追加したかまどコアが全部ドロップ</strong>されるので、素材が消える心配は少なめです。</li>
      </ul>

      <h3>設置と最初の起動</h3>
      <ol>
        <li>合成レシピ (かまど + 石炭ブロック) で「並列かまど」を作成 (詳細は <WikiLink to="/wiki/recipes">合成レシピ一覧</WikiLink>)。</li>
        <li>好きな場所に設置。</li>
        <li>右クリックで<strong>作業コア UI (54 スロット)</strong> を開きます。</li>
      </ol>

      <h3>UI レイアウト (54 スロット)</h3>
      <p>UI は 6 行のグリッドで、行ごとに役割が固定されています。</p>
      <div style={{ overflowX: 'auto', marginTop: 8 }}>
        <table style={{ borderCollapse: 'collapse', width: '100%', fontSize: 13 }}>
          <thead>
            <tr>
              <th style={th}>行</th>
              <th style={th}>役割</th>
              <th style={th}>スロット構成</th>
            </tr>
          </thead>
          <tbody>
            {tableRows.map((r) => (
              <tr key={r.label}>
                <td style={{ ...td, fontWeight: 800 }}>{r.label}</td>
                <td style={td}>{r.role}</td>
                <td style={td}>{r.slots}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h3>使い方 (最短ルート)</h3>
      <ol>
        <li><strong>素材</strong>を Row 1 (素材投入口) に入れます (普通のインベントリと同じ操作)。</li>
        <li><strong>燃料</strong>を Row 3 (燃料投入口) に入れます (石炭、木炭、板材、原木など)。</li>
        <li>あとは待つだけ。Row 2 のアニメーションで焼成中のラインが可視化されます。</li>
        <li>完成品は Row 4-5 に自動で溜まるので、そこから取り出します。</li>
      </ol>

      <h3>コアを追加して並列度を上げる</h3>
      <ul className="wiki-bullets">
        <li>Row 0 の <strong>[＋コア]</strong> ボタンを押すたびに、<strong>手持ちのかまどを 1 個消費</strong>して追加コアを 1 個増やせます。</li>
        <li>追加コア 1 個 = ライン 1 本追加。基本 1 + 追加コア <strong>最大 199</strong>、合計で <strong>最大 200 ライン同時稼働</strong>。</li>
        <li>減らしたい場合は <strong>[－コア]</strong> でかまどを 1 個回収できます。</li>
        <li>ライン数は素材と燃料が続く限り、全ラインが同時に焼成されます。ライン増設は生産速度に直結します。</li>
      </ul>

      <h3>搬出チェスト</h3>
      <ul className="wiki-bullets">
        <li>Row 0 右端の <strong>[搬出チェスト]</strong> ボタンで、外部の完成品出力先を指定できます。</li>
        <li>指定するとかまど内 (Row 4-5) が満タンでも、完成品が指定チェストへ自動で流れます。</li>
        <li>自動選別チェストや ChestShop の在庫チェストと組み合わせると、本格的な生産拠点になります。</li>
      </ul>

      <h3>複数プレイヤーで同時に使う</h3>
      <p>並列かまどは<strong>共有インベントリ</strong>を持っていて、同じ画面を複数人が同時に開けます。誰かが入れた素材はリアルタイムで全員に反映され、パーティー拠点の共同作業でとても便利です。</p>

      <h3>破壊されたとき</h3>
      <ul className="wiki-bullets">
        <li>並列かまど本体を壊すと、以下がまとめてドロップします:</li>
        <li>‣ 素材・燃料・完成品スロットの中身</li>
        <li>‣ 各ラインで<strong>焼成途中のアイテム</strong>も回収</li>
        <li>‣ 追加した<strong>かまどコア</strong>も、その数だけ普通のかまどとしてドロップ</li>
        <li>‣ 並列かまど本体もアイテム化してドロップ</li>
      </ul>

      <h3>よくあるハマりどころ</h3>
      <ul className="wiki-bullets">
        <li><strong>ラインが動かない</strong>: 燃料切れです。Row 3 に石炭など燃料を入れてください。</li>
        <li><strong>完成品が Row 4-5 に溜まらない</strong>: 搬出チェストを指定していて、そちらが満杯だと詰まります。搬出先も確認を。</li>
        <li><strong>[＋コア] を押してもコアが増えない</strong>: 手持ちに<strong>普通のかまど</strong>が必要です。合成した並列かまどではなく素の Furnace アイテム。</li>
        <li><strong>他人が触れてしまう</strong>: 現在の実装では共有 UI です。<WikiLink to="/wiki/land-protection">土地保護</WikiLink>の中に設置して外部アクセスをブロックしてください。</li>
      </ul>

      <h3>関連ページ</h3>
      <ul className="wiki-bullets">
        <li><WikiLink to="/wiki/recipes"><strong>合成レシピ一覧</strong></WikiLink>: 並列かまど本体の作り方。</li>
        <li><WikiLink to="/wiki/custom-blocks"><strong>カスタムブロック / 設置物</strong></WikiLink>: 他の合成系設備との位置づけ。</li>
        <li><WikiLink to="/wiki/land-protection"><strong>土地保護</strong></WikiLink>: 拠点保護の設定。</li>
      </ul>
    </SectionShell>
  );
}
