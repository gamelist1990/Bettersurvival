import { useState } from 'react';
import { SectionShell } from '../components/SectionShell';
import { FetchedWikiImage } from '../components/FetchedWikiImage';
import { WikiLink } from '../components/WikiNavContext';
import { ChestGuiMock } from '../components/ChestGuiMock';
import { minecraftItemAsset } from '../components/minecraftAssets';

const aptitudes = [
  ['STR', '筋力', 'iron_sword', '攻撃力とノックバックを高めます。', 'Lv.10 片手の達人 / Lv.16 闘志 / Lv.30 狂戦士'],
  ['CON', '体力', 'golden_apple', '最大体力・耐性・呼吸を強化します。', 'Lv.10 運動能力 / Lv.20 亀の守り / Lv.32 獅子の心'],
  ['DEX', '敏捷', 'bow', '移動、弓、隠密に関わる能力です。', 'Lv.10 高速再配置 / Lv.16 隠密の達人 / Lv.32 猫の目'],
  ['DEF', '防御', 'iron_chestplate', '防具・粉雪耐性・反撃を強化します。', 'Lv.10 雪上歩行 / Lv.18 反撃 / Lv.30 金剛の皮膚'],
  ['INT', '知力', 'enchanted_book', '取引とエンチャント閲覧を補助します。', 'Lv.8 学者 / Lv.16 値切り上手 / Lv.30 錬金術操作'],
  ['BLD', '建築', 'diamond_pickaxe', '採掘・破壊速度・クラフト還元に関わります。', 'Lv.12 黒曜石破砕 / Lv.20 トレジャーハンター / Lv.30 収束'],
  ['MAG', '魔力', 'brewing_stand', 'ポーション、転移、吸収を補助します。', 'Lv.12 安全転移 / Lv.18 生命吸収 / Lv.32 ワームホール倉庫'],
  ['LCK', '幸運', 'emerald', 'クリティカルやドロップに影響します。', 'Lv.12 運命のダイス / Lv.22 幸運ドロップ / Lv.32 限界突破'],
] as const;

const guiItems = [
  [10, 'STR', 'iron_sword'], [12, 'CON', 'golden_apple'], [14, 'DEX', 'bow'], [16, 'DEF', 'iron_chestplate'],
  [20, 'YOU', 'filled_map'], [22, 'XP', 'experience_bottle'], [24, 'TITLE', 'name_tag'],
  [28, 'INT', 'enchanted_book'], [30, 'BLD', 'diamond_pickaxe'], [32, 'MAG', 'brewing_stand'], [34, 'LCK', 'emerald'],
  [45, 'HELP', 'writable_book'], [47, 'BOOK', 'enchanted_book'], [49, 'CLOSE', 'barrier'], [51, 'SCOPE', 'ender_eye'],
] as const;

function LevelingGuiMock() {
  const [notice, setNotice] = useState('能力値や補助ボタンをクリックできます。');
  return (
    <div className="protect-demo">
      <ChestGuiMock
        title="Just Leveling"
        rows={6}
        slots={guiItems.map(([slot, label, item]) => ({
          slot,
          label,
          item,
          lore: label === 'CLOSE'
            ? ['GUIを閉じる']
            : label === 'TITLE'
              ? ['称号一覧を開く']
              : label === 'XP'
                ? ['現在の経験値Levelを確認']
                : ['Webモック用クリック操作'],
          onClick: () => setNotice(
            label === 'CLOSE'
              ? 'GUIを閉じる操作を再現しました。'
              : `${label} をクリックしました。`
          ),
        }))}
        caption="共通Web ChestGUIコンポーネントを使った54スロットモックです。Hover/FocusでLore、クリックで操作イベントを確認できます。"
      />
      <div className="protect-demo-console" aria-live="polite">
        <strong>Mock event</strong><span>{notice}</span>
      </div>
    </div>
  );
}

export function JustLevelingSection() {
  return (
    <SectionShell
      eyebrow="Just Leveling"
      title="Just Leveling（レベリング）"
      intro="Minecraft の経験値レベルを8種類の能力値へ振り分け、24スキルと41称号を解放していく BetterSurvival の成長システムです。"
      scope="player"
    >
      <div className="just-leveling-callout">
        <strong>利用条件</strong>
        <p>管理者がレベリング機能を有効にすると「レベリングの書」のレシピが登録されます。作成した書を右クリックすると、プレイヤー全員が専用 GUI を利用できます。</p>
      </div>

      <h3>成長システムを始める</h3>
      <div className="just-leveling-path" aria-label="Just Leveling の利用経路">
        <div className="just-leveling-path-step"><span>1</span><strong>機能を有効化</strong><p>管理者が Just Leveling を有効にします。</p><b aria-hidden="true">→</b></div>
        <div className="just-leveling-path-step"><span>2</span><strong>書を作成</strong><p><WikiLink to="/wiki/recipes">合成レシピ一覧</WikiLink>を確認し、作業台でレベリングの書を作ります。</p><b aria-hidden="true">→</b></div>
        <div className="just-leveling-path-step"><span>3</span><strong>右クリックで開く</strong><p>レベリングの書を手に持って右クリックします。</p><b aria-hidden="true">→</b></div>
        <div className="just-leveling-path-step"><span>4</span><strong>能力値を選択</strong><p>経験値レベルを消費し、目的に合う能力値を成長させます。</p></div>
      </div>

      <h3>ゲーム内 GUI の見方</h3>
      <p>下の図は、実際の54スロットのラージチェスト構成を再現した操作モックです。上段と下段に8能力値、中央にプレイヤー情報・経験値・称号、最下段に補助操作が配置されます。</p>
      <LevelingGuiMock />

      <div className="just-leveling-legend">
        <div><strong>能力値アイコン</strong><p>現在レベル、次のレベルで得る効果、必要経験値を確認し、クリックでレベルアップします。</p></div>
        <div><strong>YOU / XP</strong><p>総合状態と、能力値へ使用できる現在の Minecraft 経験値レベルを確認します。</p></div>
        <div><strong>TITLE</strong><p>解放済みの称号を一覧表示し、使用する称号を選択します。</p></div>
        <div><strong>SCOPE</strong><p>Otherworld が有効な環境では、現在のワールドグループに保存されるプロフィール範囲を確認できます。</p></div>
      </div>

      <h3>レベルアップと必要経験値</h3>
      <div className="just-leveling-cost">
        <div><span>最大レベル</span><strong>Lv.32</strong></div>
        <div><span>消費するもの</span><strong>経験値 Level</strong></div>
        <div><span>必要コスト</span><strong>5 + ⌊現在Lv ÷ 2⌋</strong></div>
      </div>
      <p>経験値ポイントではなく、画面下の緑色の数字で表示される経験値レベルを直接消費します。レベルが高くなるほど、次の成長に必要な経験値レベルも段階的に増加します。</p>

      <h3>8種類の能力値</h3>
      <div className="just-leveling-aptitudes">
        {aptitudes.map(([code, name, item, description, milestones]) => (
          <article className="just-leveling-aptitude" key={code}>
            <img src={minecraftItemAsset(item)} alt="" />
            <div><h4><span>{code}</span> {name}</h4><p>{description}</p><small>主な解放: {milestones}</small></div>
          </article>
        ))}
      </div>

      <h3>スキルとランク</h3>
      <p>24種類のスキルは、スキルポイントを手動で割り振る方式ではありません。対応する能力値が指定レベルに到達すると自動的に有効になります。各能力値を伸ばすと基本効果が強化され、節目のレベルでは戦闘・探索・採掘・移動などを補助する固有能力が追加されます。</p>
      <div className="just-leveling-note"><strong>育成の考え方</strong><p>最初は普段の役割に直結する能力値を選び、必要経験値が増えてきたら複数の能力値へ分散すると、各系統の自動解放スキルを広く利用できます。</p></div>

      <h3>41種類の称号</h3>
      <p>称号は能力値の成長やゲーム内実績によって解放されます。GUI の <strong>TITLE</strong> から解放状況を確認し、取得済みの称号へ切り替えられます。称号は成長の記録であり、現在のプレイスタイルや達成内容を示すプロフィール要素です。</p>

      <h3>Otherworld 使用時のプロフィール</h3>
      <p>Otherworld 機能が有効な場合、能力値・称号・統計はワールドグループ単位で分離されます。別グループへ移動したときは、そのグループに対応する成長データへ切り替わります。GUI 最下段の <strong>SCOPE</strong> で現在の保存範囲を確認してください。</p>

      <h3>操作早見表</h3>
      <div className="just-leveling-table-wrap">
        <table>
          <thead><tr><th>したいこと</th><th>操作</th></tr></thead>
          <tbody>
            <tr><td>GUIを開く</td><td>レベリングの書を手に持って右クリック</td></tr>
            <tr><td>能力値を上げる</td><td>上げたい能力値のアイコンをクリック</td></tr>
            <tr><td>称号を変更する</td><td>TITLE を開き、解放済み称号を選択</td></tr>
            <tr><td>プロフィール範囲を確認する</td><td>SCOPE にカーソルを合わせて確認</td></tr>
            <tr><td>GUIを閉じる</td><td>中央下の CLOSE をクリック</td></tr>
          </tbody>
        </table>
      </div>

      <small className="just-leveling-source">アイテム画像: <a href="https://github.com/Mojang/bedrock-samples/tree/main/resource_pack/textures" target="_blank" rel="noreferrer">Mojang/bedrock-samples（main）</a>。Minecraft アセット側に26.1より新しいデータが追加された場合は、利用バージョンに合わせて参照先を更新します。</small>
    </SectionShell>
  );
}
