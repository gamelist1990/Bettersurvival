import { SectionShell } from '../components/SectionShell';
import { CommandBox } from '../components/CommandBox';
import { WikiLink } from '../components/WikiNavContext';

// ClaimLevel.java の実装をそのまま反映
function radius(level: number): number {
  if (level <= 5) return [0, 8, 12, 16, 24, 32][level];
  return 32 + 2 * (level - 5);
}
function upkeep(level: number): number {
  if (level <= 5) return [0, 16, 32, 48, 64, 96][level];
  return 96 * Math.pow(1.05, level - 5);
}
function upgradeCost(level: number): string {
  if (level >= 100) return '—';
  if (level === 1) return '鉄インゴット × 32';
  if (level === 2) return '金インゴット × 24';
  if (level === 3) return 'ダイヤ × 16';
  if (level === 4) return 'ネザライトインゴット × 2';
  // Lv5+
  const tier = Math.floor((level - 5) / 10);
  const step = (level - 5) % 10;
  const nether = 4 + tier * 4 + step;
  const dia = 32 + tier * 16 + step * 4;
  const eme = 32 + tier * 16;
  return `ネザライト × ${nether} / ダイヤ × ${dia} / エメラルド × ${eme}`;
}

const sampleLevels = [1, 2, 3, 4, 5, 10, 20, 50, 100];

export function LandProtectionSection() {
  return (
    <SectionShell
      eyebrow="LandProtect"
      title="土地保護 詳細ガイド"
      intro="Rust 風のツールキャビネット (土地保護コア) の詳細。100 段階のレベル成長、燃料維持、パーティ / リング / レイド連携までを一気に把握できるように 1 ページにまとめます。"
      scope="player"
    >
      <h3>基本の流れ</h3>
      <ol>
        <li><code>ロデストーン</code> + <code>ダイヤ</code> を近くにドロップして<strong>土地保護コア</strong>を作ります。</li>
        <li>守りたい拠点の中心に設置。設置した瞬間、自分がオーナーとして登録されます。</li>
        <li>右クリックで管理 UI を開き、<strong>燃料</strong>を投入 (原木 / 板材 / 木炭 / 石炭など)。</li>
        <li>燃料がある間だけ保護が維持されます。切れると<strong>保護が消えます</strong>。</li>
      </ol>

      <h3>保護レベルと成長</h3>
      <p>コアには <strong>Lv1 ～ Lv100</strong> のレベルがあります (個人所有は上限 <strong>Lv10</strong>)。レベルが上がると保護半径が広がり、そのぶん燃料消費速度も上がります。</p>

      <div style={{ overflowX: 'auto', marginTop: 8 }}>
        <table style={{ borderCollapse: 'collapse', width: '100%', fontSize: 13 }}>
          <thead>
            <tr style={{ background: 'rgba(255,255,255,0.06)' }}>
              <th style={{ padding: '6px 10px', textAlign: 'left', border: '1px solid rgba(255,255,255,0.1)' }}>Lv</th>
              <th style={{ padding: '6px 10px', textAlign: 'left', border: '1px solid rgba(255,255,255,0.1)' }}>保護半径 (ブロック)</th>
              <th style={{ padding: '6px 10px', textAlign: 'left', border: '1px solid rgba(255,255,255,0.1)' }}>維持コスト (ユニット/時)</th>
              <th style={{ padding: '6px 10px', textAlign: 'left', border: '1px solid rgba(255,255,255,0.1)' }}>次レベル強化素材</th>
            </tr>
          </thead>
          <tbody>
            {sampleLevels.map((lv) => (
              <tr key={lv}>
                <td style={{ padding: '6px 10px', border: '1px solid rgba(255,255,255,0.1)' }}>{lv}</td>
                <td style={{ padding: '6px 10px', border: '1px solid rgba(255,255,255,0.1)' }}>{radius(lv)}</td>
                <td style={{ padding: '6px 10px', border: '1px solid rgba(255,255,255,0.1)' }}>{upkeep(lv).toFixed(1)}</td>
                <td style={{ padding: '6px 10px', border: '1px solid rgba(255,255,255,0.1)' }}>{upgradeCost(lv)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ul className="wiki-bullets" style={{ marginTop: 12 }}>
        <li><strong>半径</strong>: Lv1-5 は 8 / 12 / 16 / 24 / 32、Lv6 以降は <code>32 + 2 × (Lv - 5)</code> で 2 ブロックずつ拡大。最大 Lv100 で半径 222 ブロック。</li>
        <li><strong>維持コスト</strong>: Lv5 まで直線的、Lv6 以降は <code>96 × 1.05^(Lv - 5)</code> で指数的に増えます。高レベルほど燃料が凶暴に減ります。</li>
        <li><strong>個人上限は Lv10</strong>: それ以上は、より大規模な (パーティ / 特殊) 用途向けです。</li>
        <li><strong>Lv5 以上</strong>の強化にはネザライト + ダイヤ + エメラルドの 3 種同時投入が必要。10 段階ごと (tier) にコストが上がります。</li>
      </ul>

      <h3>燃料 (Fuel)</h3>
      <ul className="wiki-bullets">
        <li>可燃物 (原木、板材、木炭、石炭、石炭ブロックなど) が燃料になります。素材ごとにユニット数が違います。</li>
        <li>維持コスト以上のユニットが入っている限り保護は継続。</li>
        <li>燃料が切れると <strong>保護が停止し</strong>、外部から破壊 / チェストアクセスできる状態になります。長期不在時は多めに補充を。</li>
      </ul>

      <h3>管理 UI と権限</h3>
      <ul className="wiki-bullets">
        <li>コアを右クリックで管理 UI。ここから <strong>PvP の可否</strong>、<strong>メンバー / ホワイトリスト</strong>、燃料補充などを行えます。</li>
        <li><strong>オーナー本人</strong>と<strong>副リーダー (パーティ機能連携)</strong> が管理操作を行えます。</li>
        <li><strong>メンバー</strong>はチェストや扉などのアクセスが許可されますが、コアの設定変更はできません。</li>
        <li><strong>ホワイトリスト</strong>: 特定のプレイヤーを一時的に許可したい場合の別枠。</li>
      </ul>

      <h3>コマンド</h3>
      <CommandBox command="/land info" description="現在地の保護状況 (オーナー / レベル / 半径 / 消費速度 / 燃料残 / 領地 PvP) を表示します。" />
      <CommandBox command="/land debug" description="保護境界線をパーティクルでデバッグ表示。緑 = アクセス可 / 赤 = 他人の領地 / 灰 = 無効。" />
      <CommandBox command="/land ring" description="リング (闘技場) 設定 UI をこの領地で開く。オーナー / 副リーダーのみ。" />

      <h3>連携する他機能</h3>
      <ul className="wiki-bullets">
        <li><strong>Party</strong>: リーダー / 副リーダーが管理でき、メンバーが自動的にアクセス許可されます。</li>
        <li><strong>Ring (闘技場)</strong>: 領地内にリングを定義して 1v1 デュエルなどが行えます。<code>/land ring</code> から設定。</li>
        <li><strong>Tournament</strong>: リングを使ったトーナメント大会。土地保護のリング設定が前提。</li>
        <li><strong>DeathChest</strong>: 死亡時の保管チェストは土地保護の中でのみ守られます。</li>
        <li><strong>ParallelFurnace</strong> / <strong>ChestLock</strong> / <strong>ChestShop</strong>: 領地内では所有者以外の操作を制限します。</li>
      </ul>

      <h3>レイド (Raid) について</h3>
      <ul className="wiki-bullets">
        <li>特定条件下で他プレイヤーが領地に攻撃を仕掛けられる <strong>レイドセッション</strong> が発生することがあります (<code>RaidSession</code>)。</li>
        <li>レイド中は保護の一部が緩和されるなどの効果があります。詳細はゲーム内のアナウンスに従ってください。</li>
      </ul>

      <h3>よくあるハマりどころ</h3>
      <ul className="wiki-bullets">
        <li><strong>燃料が思ったより早く切れた</strong>: 高レベルは維持コストが指数的に増えます。長期外出前に必要量を <code>/land info</code> で確認。</li>
        <li><strong>拠点の端が守れていない</strong>: 保護半径は<strong>コアを中心とした水平方向の正方形</strong> (高さは全域)。拠点の中心に置いてください。</li>
        <li><strong>メンバーがチェストを開けない</strong>: パーティ機能でメンバー登録されているか、コア UI のメンバー欄に入っているかを確認。</li>
        <li><strong>Lv10 の壁</strong>: 個人所有では Lv10 が上限。より大きい保護が欲しい場合はサーバー運営に相談を。</li>
      </ul>

      <h3>関連ページ</h3>
      <ul className="wiki-bullets">
        <li><WikiLink to="/wiki/recipes"><strong>合成レシピ一覧</strong></WikiLink>: 土地保護コアの合成手順まとめ。</li>
        <li><WikiLink to="/wiki/custom-blocks"><strong>カスタムブロック / 設置物</strong></WikiLink>: 他の合成系設備との位置づけ。</li>
        <li><WikiLink to="/wiki/commands"><strong>コマンド一覧</strong></WikiLink>: <code>/land</code> の詳細と関連コマンド。</li>
        <li><WikiLink to="/wiki/copper-golem"><strong>カッパーゴーレム 詳細ガイド</strong></WikiLink>: 領地内でゴーレムを運用するときの注意。</li>
      </ul>
    </SectionShell>
  );
}
