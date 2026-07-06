import { SectionShell } from '../components/SectionShell';
import { CommandBox } from '../components/CommandBox';
import { WikiLink } from '../components/WikiNavContext';

export function CopperGolemSection() {
  return (
    <SectionShell
      eyebrow="CopperGolem"
      title="カッパーゴーレム 詳細ガイド"
      intro="作物の自動収穫と拠点防衛を担う独自モブ。召喚から作物採取モード / 戦闘モードの設定、そして GUI 操作までを 1 ページに集約します。"
      scope="player"
    >
      <h3>何ができるモブか</h3>
      <ul className="wiki-bullets">
        <li>プレイヤーが召喚する銅製ゴーレム。<strong>作物採取モード</strong>で農場を自動化したり、<strong>戦闘モード</strong>で拠点周辺を巡回させたりできます。</li>
        <li>1 体ごとに固有 ID を持ち、複数召喚できます。各個体に別々のプロファイル (対象チェスト / 装備 / モード) を保存できます。</li>
        <li>スニーク + 右クリックでその個体の管理メニューが開きます。</li>
        <li>死んでも <strong>プロファイル (対象・装備の登録情報) は残り</strong>、同じ ID で再召喚すれば設定を引き継げます。</li>
      </ul>

      <h3>召喚は 2 段階</h3>
      <ol>
        <li><strong>Copper Golem Core を作る</strong>: <code>銅ブロック</code> + <code>くり抜かれたカボチャ</code> を近くにドロップして合成 (地上 0.5 マス / 空中 1.5 マス以内)。</li>
        <li><strong>ゴーレム召喚</strong>: 金床で名札を <code>golem-&lt;任意ID&gt;</code> にしてから、Core と一緒にドロップ。ドロップ順は逆でも OK。</li>
      </ol>
      <CommandBox command="golem-farm1" description="名札の名前例。ID は英数字なら何でも OK。個体ごとに変えると複数管理できます。" />

      <h3>管理メニューを開く</h3>
      <p>召喚したカッパーゴーレムに向かって <strong>スニーク + 右クリック</strong>すると、そのゴーレム専用の管理 GUI が開きます。ここから以下を設定します:</p>
      <ul className="wiki-bullets">
        <li>モード切替 (作物採取 / 戦闘)</li>
        <li>作物採取モードの <strong>保管先チェスト</strong> と <strong>骨粉供給元チェスト</strong> の登録</li>
        <li>戦闘モードの武器・装備の設定</li>
      </ul>

      <h3>作物採取モード (CROP)</h3>
      <p>周囲の畑を巡回し、成熟した作物を収穫します。骨粉を持たせて成長を促進させることもできます。</p>

      <h4>保管先チェストの登録</h4>
      <ol>
        <li>管理メニューから「保管先」を開き、登録したいスロットを選びます。</li>
        <li>GUI が閉じるので、収穫物をしまいたい <strong>チェスト / ラージチェスト / 樽</strong> を<strong>左クリック</strong>します。</li>
        <li>成功すると <em>「保管先スロット N に設定しました」</em> と表示され、アメジストの音が鳴ります。</li>
        <li>スロットは複数登録できます。使わなくなったらメニューから解除できます。</li>
      </ol>

      <h4>骨粉供給元チェストの登録</h4>
      <ol>
        <li>管理メニューの「骨粉供給元」を選び、スロットを選びます。</li>
        <li>骨粉が入っているチェスト / 樽を左クリックして登録。</li>
        <li>ゴーレムはこの供給元から骨粉を取り出して作物にまきます。</li>
      </ol>

      <h4>踏み荒らされた畑の耕し直し (自動耕し)</h4>
      <p>
        プレイヤーやモブに踏まれて土に戻ってしまった畑を、ゴーレムが自動で耕し直して耕地へ戻します。
        畑に穴 (土) ができても放置されず、農場の見た目と再植の下地が保たれます。
      </p>
      <ul className="wiki-bullets">
        <li>作物採取メニューの <strong>「自動耕し」ボタン</strong>で ON / OFF を切り替えます (<strong>既定は ON</strong>)。植え直しや骨粉とは独立した専用トグルです。</li>
        <li>荒らされた箇所を見つけると、<strong>収穫よりも優先して即座にそこへ向かい</strong>耕し直します。畑に穴が空いても放置されません。</li>
        <li>誤って畑以外の土を耕さないよう、<strong>耕地に隣接し・上が開いている土</strong>だけを対象にします。</li>
        <li>クワを振る動作・耕す音・土のパーティクルで、耕している様子が分かります。</li>
      </ul>

      <h4>登録時のよくあるエラー</h4>
      <ul className="wiki-bullets">
        <li><em>「チェスト/ラージチェスト/樽を左クリックしてください」</em>: 対応していないブロックを叩いています。対象は <strong>Chest / Large Chest / Barrel</strong> のみ。</li>
        <li>他人の土地保護 / チェストロックが有効な位置は登録できません。</li>
        <li>登録済みチェストを壊すと <strong>自動でスロットから外れます</strong> (爆発も含む)。</li>
      </ul>

      <h3>戦闘モード (COMBAT)</h3>
      <p>敵対 Mob を索敵して攻撃します。プレイヤー拠点の防衛や、危険な採掘場の護衛に。</p>

      <h4>武器・装備の設定</h4>
      <ol>
        <li>管理メニューから「戦闘装備」を開くと、専用の武器メニュー UI (ChestUI) が表示されます。</li>
        <li>この GUI 内の対応スロットに<strong>武器や防具を直接ドラッグ&amp;ドロップ</strong>します。</li>
        <li>クリック / ドラッグ / GUI を閉じた瞬間に、その内容が<strong>即座にゴーレムに反映</strong>されます (実装: <code>refreshCombatEquipmentFromMenu</code> / <code>refreshCombatEquipmentFromInventory</code>)。</li>
        <li>取り出すときも同じ GUI で。ドラッグして自分のインベントリへ戻すと解除されます。</li>
      </ol>

      <h4>戦闘の視覚フィードバック</h4>
      <ul className="wiki-bullets">
        <li>ゴーレムがダメージを受けると、頭上に赤い <strong>「-N」の被ダメージ表示</strong>が浮かびます。</li>
        <li>戦闘中は最終アクティビティ時刻が記録され、モードや索敵の判断材料になります。</li>
      </ul>

      <h3>複数体運用のコツ (ID 命名)</h3>
      <ul className="wiki-bullets">
        <li>名札の <code>golem-</code> 以降の部分がそのゴーレムの<strong>ユニーク ID</strong>になります。</li>
        <li>同じ ID で 2 体作ろうとしても、後から召喚したほうがプロファイルを乗っ取ります。役割ごとに ID を分けてください (例: <code>golem-farm1</code> / <code>golem-guard-east</code>)。</li>
        <li>プロファイルはゴーレムのエンティティ UUID と紐付いています。死亡後に再召喚すると自動で新しいエンティティに<strong>プロファイルが再バインド</strong>されます。</li>
      </ul>

      <h3>死亡・リスポーン時の挙動</h3>
      <ul className="wiki-bullets">
        <li>ゴーレムが死ぬと、プロファイル (保管先 / 骨粉源 / 装備) は<strong>そのまま残ります</strong>。</li>
        <li>同じ ID の名札 + Core で再召喚すれば、以前の設定を引き継いだ状態で復活します。</li>
        <li>装備は死亡時にゴーレムがドロップする可能性があります。大事な装備は消耗品と割り切って使うのが安全です。</li>
      </ul>

      <h3>よくあるハマりどころ</h3>
      <ul className="wiki-bullets">
        <li><strong>名札が反応しない</strong>: 名前が <code>golem-</code> で始まっているか確認 (前後にスペースや余計な文字を入れない)。</li>
        <li><strong>チェストを叩いても登録されない</strong>: 事前に管理メニューから「保管先」または「骨粉供給元」のスロットを開き、"チェスト待ち" 状態にする必要があります。</li>
        <li><strong>他人の土地で動かない</strong>: 保管先 / 骨粉源が他人の土地保護内だと登録できません。</li>
        <li><strong>装備が反映されない</strong>: 戦闘装備専用 GUI 以外 (自分のインベントリなど) からは同期されません。必ず専用メニューから設定します。</li>
      </ul>

      <h3>関連ページ</h3>
      <ul className="wiki-bullets">
        <li><WikiLink to="/wiki/recipes"><strong>合成レシピ一覧</strong></WikiLink>: Core の作り方と名札の合成手順まとめ。</li>
        <li><WikiLink to="/wiki/custom-blocks"><strong>カスタムブロック / 設置物</strong></WikiLink>: 他の合成系設備との位置づけ。</li>
        <li><WikiLink to="/wiki/land-protection"><strong>土地保護 詳細ガイド</strong></WikiLink>: ゴーレムの活動エリアが他人の保護と重なる時の挙動。</li>
        <li><WikiLink to="/wiki/commands"><strong>コマンド一覧</strong></WikiLink>: /land info など関連コマンド。</li>
      </ul>
    </SectionShell>
  );
}
