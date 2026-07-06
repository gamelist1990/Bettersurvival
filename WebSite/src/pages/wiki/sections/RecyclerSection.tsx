import { SectionShell } from '../components/SectionShell';
import { WikiLink } from '../components/WikiNavContext';

const tableRows: { label: string; role: string; slots: string }[] = [
  { label: '0 (上端)', role: '情報パネル', slots: '[情報] [状態⚙] [統計] [還元率] [使い方]' },
  { label: '1', role: '≫ 投入口', slots: '投入 8 スロット (編集可)' },
  { label: '2-5', role: '≪ 回収口 (4 行分)', slots: '回収 32 スロット (編集可、取り出し用)' },
];

const td: React.CSSProperties = { padding: '6px 10px', border: '1px solid rgba(74, 55, 38, 0.16)' };
const th: React.CSSProperties = { ...td, textAlign: 'left', background: 'rgba(74, 55, 38, 0.06)' };

export function RecyclerSection() {
  return (
    <SectionShell
      eyebrow="Recycler"
      title="リサイクラー"
      intro="砥石 × 鉄ブロックで作る Rust 風の分解装置。不要なアイテムを 50% の還元率で素材へ戻し、分解できない物は少量の経験値へ変換します。"
      scope="player"
    >
      <h3>どんな機能か</h3>
      <ul className="wiki-bullets">
        <li>投入したアイテムを<strong>クラフトレシピを逆算</strong>し、<strong>素材の約 50%</strong>へ分解します (Rust のリサイクラーと同じ還元率)。</li>
        <li>クラフトレシピが無い物は<strong>精錬レシピの逆算</strong> (ガラス → 砂 など) にフォールバック。</li>
        <li>どちらも無い物は<strong>完全処分</strong>されますが、消えるのはもったいないので<strong>少量の経験値オーブ</strong>に変換されます (1 個あたり約 0.25 XP)。</li>
        <li>耐久が減った道具は<strong>残り耐久に応じて還元量が減ります</strong>。ボロボロの武器を入れても大した素材にはなりません。</li>
        <li><strong>中身入りのシュルカーボックスは分解されず</strong>そのまま回収口へ流されます (誤消滅防止)。</li>
        <li><strong>上ホッパーで自動搬入</strong> / <strong>下ホッパーで自動搬出</strong>が可能。生産ラインに組み込めます。</li>
        <li><strong>複数プレイヤーで同時に UI を開けます</strong>。共有 UI 設計。</li>
      </ul>

      <h3>合成と設置</h3>
      <ol>
        <li>合成レシピ (砥石 + 鉄ブロック) で「リサイクラー」を作成 (詳細は <WikiLink to="/wiki/recipes">合成レシピ一覧</WikiLink>)。</li>
        <li>好きな場所に設置。緑色の名前が付いた砥石として見えます。</li>
        <li>右クリックで<strong>分解装置 UI (54 スロット)</strong>を開きます。</li>
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
        <li>分解したいアイテムを Row 1 (投入口) に放り込みます。プレイヤーインベントリから<strong>シフトクリック</strong>で一括搬入できます。</li>
        <li>1 秒ごとに<strong>最大 8 個ずつ</strong>処理されます。処理中は上部の状態表示 ⚙ が回転アニメーション。</li>
        <li>完成した素材は Row 2-5 (回収口、32 スロット) に自動で溜まります。</li>
        <li>取り出したいときは、そのまま UI から拾うか、下側にホッパーを設置。</li>
      </ol>

      <h3>分解の 3 パターン</h3>
      <ul className="wiki-bullets">
        <li><strong>クラフト可能なアイテム</strong> (例: 鉄のツルハシ → 鉄インゴット + 棒) — 素材の約 50% を回収。端数は確率で切り上げされる場合あり。</li>
        <li><strong>クラフト不可 + 精錬可能なアイテム</strong> (例: ガラス → 砂) — 精錬レシピを逆算して回収。</li>
        <li><strong>どちらも不可のアイテム</strong> (例: エンドポータルフレームなど) — 完全に消えますが、代わりに<strong>少量の経験値オーブ</strong>が発生。</li>
      </ul>

      <h3>ホッパー連携で自動生産ライン</h3>
      <ul className="wiki-bullets">
        <li><strong>上に接触するホッパー</strong> → 投入口に自動搬入されます。「不要品自動回収チェスト → ホッパー → リサイクラー」の流れが作れます。</li>
        <li><strong>下に接触するホッパー</strong> → 回収口から自動搬出されます。「リサイクラー → ホッパー → 素材保管チェスト」で素材を分別。</li>
        <li>1 サイクル (1 秒) で<strong>最大 8 個</strong>のホッパー移動が行われます。</li>
      </ul>

      <h3>破壊されたとき</h3>
      <ul className="wiki-bullets">
        <li>本体を壊すと <strong>投入口と回収口の中身は全てドロップ</strong>されます。</li>
        <li>本体自体も (クリエイティブ以外) アイテム化してドロップされるので、拠点間の引っ越しに便利。</li>
        <li>爆発でも同じくアイテムと中身がドロップ。</li>
      </ul>

      <h3>よくあるハマりどころ</h3>
      <ul className="wiki-bullets">
        <li><strong>大事なアイテムを間違えて入れた</strong> — 処理は 1 秒 8 個ペースなので、気付いたらすぐ回収してください。一度分解されると元には戻せません。</li>
        <li><strong>思ったより素材が少ない</strong> — 耐久が減った道具は還元量が減ります。修繕してから入れた方がお得です。</li>
        <li><strong>大量の岩盤や特殊ブロックを入れても XP しか出ない</strong> — クラフト・精錬レシピが無いアイテムは完全処分になります。仕様通り。</li>
        <li><strong>ホッパーで搬入されない</strong> — 上端が「回収口」ではなく「投入口」に繋がるように、リサイクラー本体の<strong>上面</strong>にホッパーを設置してください。</li>
        <li><strong>他人が触れてしまう</strong> — 現在の実装では共有 UI です。<WikiLink to="/wiki/land-protection">土地保護</WikiLink>の中に設置して外部アクセスをブロックしてください。</li>
      </ul>

      <h3>関連ページ</h3>
      <ul className="wiki-bullets">
        <li><WikiLink to="/wiki/recipes"><strong>合成レシピ一覧</strong></WikiLink>: リサイクラー本体の作り方。</li>
        <li><WikiLink to="/wiki/custom-blocks"><strong>カスタムブロック / 設置物</strong></WikiLink>: 他の合成系設備との位置づけ。</li>
        <li><WikiLink to="/wiki/parallel-furnace"><strong>並列かまど</strong></WikiLink>: 精錬側の生産ラインと組み合わせると強力。</li>
        <li><WikiLink to="/wiki/land-protection"><strong>土地保護</strong></WikiLink>: 拠点保護の設定。</li>
      </ul>
    </SectionShell>
  );
}
