import { useState } from 'react';
import { ChestGuiMock, type ChestGuiSlot } from './ChestGuiMock';

function MockPanel({
  title,
  rows,
  slots,
  notice,
  caption,
}: {
  title: string;
  rows: number;
  slots: ChestGuiSlot[];
  notice: string;
  caption: string;
}) {
  return (
    <div className="protect-demo wiki-feature-gui-demo">
      <ChestGuiMock title={title} rows={rows} slots={slots} caption={caption} />
      <div className="protect-demo-console" aria-live="polite">
        <strong>Mock event</strong>
        <span>{notice}</span>
      </div>
    </div>
  );
}

const demoEnchants = [
  ['採掘加速', 'diamond_pickaxe', '採掘速度を強化'],
  ['範囲採掘', 'iron_pickaxe', '周囲をまとめて採掘'],
  ['自動回収', 'hopper', 'ドロップを自動回収'],
  ['オートスメルト', 'furnace', '採掘物を自動精錬'],
  ['経験値ブースト', 'experience_bottle', '経験値獲得を強化'],
  ['吸血', 'golden_apple', '攻撃時に回復'],
  ['俊足のブーツ', 'sugar', '移動速度を強化'],
  ['ナイトビジョン', 'ender_eye', '暗所での視認性を強化'],
] as const;

export function CustomEnchantGuiMock() {
  const [tool, setTool] = useState<'diamond_pickaxe' | 'book' | null>(null);
  const [levels, setLevels] = useState<Record<string, number>>({});
  const [page, setPage] = useState(1);
  const [notice, setNotice] = useState('まずslot 10へ道具を入れる操作を試してください。');

  const toolLabel = tool === null ? '空きスロット' : tool === 'book' ? '本' : 'ダイヤモンドのツルハシ';
  const buttonSlots = [19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43];

  const slots: ChestGuiSlot[] = [
    { slot: 4, label: '✦ カスタムエンチャント管理 ✦', item: 'enchanting_table', lore: ['実装: CustomEnchantTableUI', '54スロット'] },
    { slot: 9, label: '→ 道具をここへ', item: 'item_frame', lore: ['右のslot 10へ対象装備を入れます'] },
    {
      slot: 10,
      label: toolLabel,
      item: tool ?? undefined,
      lore: ['クリックで: 空 → ツルハシ → 本 → 空', 'ゲーム内では実際のItemStackを置くスロット'],
      onClick: () => {
        const next = tool === null ? 'diamond_pickaxe' : tool === 'diamond_pickaxe' ? 'book' : null;
        setTool(next);
        setNotice(next === null ? '道具を取り出しました。' : next === 'book' ? '本を入れました。全エンチャント候補を表示する想定です。' : 'ダイヤモンドのツルハシを入れました。');
      },
    },
    { slot: 45, label: '← 前ページ', item: 'arrow', lore: ['前の一覧へ'], onClick: () => { setPage(Math.max(1, page - 1)); setNotice('前ページへ移動しました。'); } },
    { slot: 49, label: '使い方', item: 'writable_book', lore: ['① 道具を入れる', '② エンチャントをクリック', '③ 素材を消費して強化'] },
    { slot: 50, label: `ページ ${page}/2`, item: 'paper', lore: ['1ページ最大21個'] },
    { slot: 53, label: '次ページ →', item: 'arrow', lore: ['次の一覧へ'], onClick: () => { setPage(Math.min(2, page + 1)); setNotice('次ページへ移動しました。'); } },
  ];

  buttonSlots.forEach((slot, index) => {
    if (index >= demoEnchants.length) {
      slots.push({ slot, label: '－', item: 'gray_stained_glass_pane', lore: [tool ? 'この道具の候補はここまでです' : '先に道具を入れてください'] });
      return;
    }
    const [name, item, description] = demoEnchants[index];
    const level = levels[name] ?? 0;
    slots.push({
      slot,
      label: tool ? `${name} ${level > 0 ? `Lv.${level}` : 'を付与'}` : name,
      item,
      active: Boolean(tool),
      disabled: !tool,
      lore: tool
        ? [description, `現在: ${level || 'なし'}`, 'クリックで付与/強化']
        : [description, '先に道具を入れてください'],
      onClick: tool ? () => {
        const next = Math.min(3, level + 1);
        setLevels((current) => ({ ...current, [name]: next }));
        setNotice(`${name} を Lv.${next} に強化しました（Webモック）。`);
      } : undefined,
    });
  });

  return (
    <MockPanel
      title="✦ カスタムエンチャント管理 ✦"
      rows={6}
      slots={slots}
      notice={notice}
      caption="CustomEnchantTableUI.java の実スロット配置をWeb上で再現。slot 10の道具状態とエンチャントボタンを実際に操作できます。"
    />
  );
}

type GolemScreen = 'main' | 'targets';
type GolemMode = '作物採取' | '戦闘' | 'チェスト整理';

export function CopperGolemGuiMock() {
  const [screen, setScreen] = useState<GolemScreen>('main');
  const [mode, setMode] = useState<GolemMode>('作物採取');
  const [range, setRange] = useState(16);
  const [harvest, setHarvest] = useState(2);
  const [replant, setReplant] = useState(true);
  const [boneMeal, setBoneMeal] = useState(false);
  const [till, setTill] = useState(true);
  const [targets, setTargets] = useState(2);
  const [notice, setNotice] = useState('Copper Golemの設定をクリックして変更できます。');

  if (screen === 'targets') {
    const targetSlots: ChestGuiSlot[] = [];
    for (let i = 0; i < 45; i++) {
      if (i < targets) {
        targetSlots.push({
          slot: i,
          label: `保管先 ${i + 1}`,
          item: 'chest',
          lore: [`world ${120 + i},64,-35`, 'クリックで再設定'],
          onClick: () => setNotice(`保管先 ${i + 1} の再設定待ちを再現しました。`),
        });
      } else if (i === targets) {
        targetSlots.push({
          slot: i,
          label: '保管先を追加',
          item: 'lime_stained_glass_pane',
          lore: ['クリック後、対象チェストを左クリック'],
          onClick: () => { setTargets(Math.min(8, targets + 1)); setNotice('保管先を1件追加しました（モック）。'); },
        });
      }
    }
    targetSlots.push(
      { slot: 45, label: '戻る', item: 'arrow', onClick: () => setScreen('main') },
      { slot: 52, label: '全解除', item: 'barrier', danger: true, lore: ['登録済みの保管先を全削除'], onClick: () => { setTargets(0); setNotice('保管先を全解除しました。'); } },
      { slot: 53, label: '閉じる', item: 'red_stained_glass_pane', onClick: () => setScreen('main') },
    );
    return <MockPanel title="保管先設定: farm1" rows={6} slots={targetSlots} notice={notice} caption="CopperGolemTargetMenuUI の45件一覧 + 戻る/全解除/閉じるを再現。" />;
  }

  const nextMode = () => {
    const order: GolemMode[] = ['作物採取', '戦闘', 'チェスト整理'];
    const next = order[(order.indexOf(mode) + 1) % order.length];
    setMode(next);
    setNotice(`モードを ${next} に切り替えました。`);
  };

  const slots: ChestGuiSlot[] = [
    { slot: 4, label: 'ステータス', item: 'copper_block', lore: ['ID: farm1', 'Level: 8', `Mode: ${mode}`, `行動範囲: ${range}`, `保管先: ${targets}件`] },
    { slot: 10, label: 'モード切替', item: 'comparator', lore: [`現在: ${mode}`], onClick: nextMode },
    { slot: 11, label: `行動範囲: ${range}`, item: 'spyglass', lore: ['クリックで値を変更'], onClick: () => { const next = range >= 32 ? 10 : range + 2; setRange(next); setNotice(`行動範囲を ${next} に変更しました。`); } },
    { slot: 13, label: '範囲拡張 +1', item: 'compass', lore: ['必要ポイント: 1'], onClick: () => { setRange((value) => Math.min(50, value + 1)); setNotice('範囲拡張ポイントを使用しました。'); } },
    { slot: 20, label: mode === '戦闘' ? '成果物保管先' : '保管先設定', item: 'chest', lore: [`登録: ${targets}件`], onClick: () => setScreen('targets') },
    { slot: 53, label: '閉じる', item: 'barrier', onClick: () => setNotice('GUIを閉じる操作です。') },
  ];

  if (mode === '作物採取') {
    slots.push(
      { slot: 12, label: `採取速度強化 +1 (${harvest})`, item: 'iron_hoe', onClick: () => { setHarvest((v) => v + 1); setNotice('採取速度を強化しました。'); } },
      { slot: 19, label: '作物フィルタ', item: 'wheat', lore: ['採取対象をスロット式で設定'], onClick: () => setNotice('作物フィルタ画面を開く操作です。') },
      { slot: 21, label: '骨粉供給元設定', item: 'bone_meal', onClick: () => setNotice('骨粉供給元の設定画面を開く操作です。') },
      { slot: 23, label: `自動植え直し: ${replant ? 'ON' : 'OFF'}`, item: 'wheat_seeds', active: replant, onClick: () => { setReplant(!replant); setNotice('自動植え直しを切り替えました。'); } },
      { slot: 24, label: `自動骨粉: ${boneMeal ? 'ON' : 'OFF'}`, item: 'bone_meal', active: boneMeal, onClick: () => { setBoneMeal(!boneMeal); setNotice('自動骨粉を切り替えました。'); } },
      { slot: 49, label: '行動アルゴリズム', item: 'recovery_compass', lore: ['密集地優先 / 巡回モード'], onClick: () => setNotice('行動アルゴリズムを切り替えました。') },
      { slot: 51, label: `自動耕し: ${till ? 'ON' : 'OFF'}`, item: 'farmland', active: till, onClick: () => { setTill(!till); setNotice('自動耕しを切り替えました。'); } },
    );
  } else if (mode === '戦闘') {
    slots.push(
      { slot: 14, label: '戦闘HP強化 +5', item: 'golden_apple', onClick: () => setNotice('戦闘HPを+5強化しました。') },
      { slot: 19, label: '戦闘装備設定', item: 'netherite_chestplate', onClick: () => setNotice('武器/防具設定画面を開く操作です。') },
    );
  } else {
    slots.push({ slot: 19, label: '利用可能モード', item: 'hopper', lore: ['作物採取 / 戦闘 / チェスト整理 / 回収 / 追従'] });
  }

  return <MockPanel title={`Copper Golem [${mode}]: farm1`} rows={6} slots={slots} notice={notice} caption="CopperGolemMainMenuUI の実スロットに合わせた操作モック。モード切替・範囲・保管先・作物系トグルを試せます。" />;
}

type SharedScreen = 'main' | 'range' | 'chestpage';

export function SharedStorageGuiMock() {
  const [screen, setScreen] = useState<SharedScreen>('main');
  const [range, setRange] = useState(20);
  const [filter, setFilter] = useState(true);
  const [filterMode, setFilterMode] = useState<'EXACT' | 'MATERIAL' | 'ENCHANT_STATE'>('MATERIAL');
  const [chestPage, setChestPage] = useState(true);
  const [particles, setParticles] = useState(true);
  const [manualInsert, setManualInsert] = useState(true);
  const [manualExtract, setManualExtract] = useState(true);
  const [notice, setNotice] = useState('SharedStorageの設定を実際に切り替えられます。');

  if (screen === 'range') {
    const buttons: ChestGuiSlot[] = [
      { slot: 10, label: '-10', item: 'red_stained_glass_pane', onClick: () => setRange(Math.max(1, range - 10)) },
      { slot: 11, label: '-5', item: 'red_stained_glass_pane', onClick: () => setRange(Math.max(1, range - 5)) },
      { slot: 12, label: '-1', item: 'pink_stained_glass_pane', onClick: () => setRange(Math.max(1, range - 1)) },
      { slot: 13, label: `現在: ${range}ブロック`, item: 'spyglass' },
      { slot: 14, label: '+1', item: 'lime_stained_glass_pane', onClick: () => setRange(Math.min(50, range + 1)) },
      { slot: 15, label: '+5', item: 'green_stained_glass_pane', onClick: () => setRange(Math.min(50, range + 5)) },
      { slot: 16, label: '+10', item: 'green_stained_glass_pane', onClick: () => setRange(Math.min(50, range + 10)) },
      { slot: 18, label: '10 に設定', item: 'paper', onClick: () => setRange(10) },
      { slot: 19, label: '15 に設定', item: 'paper', onClick: () => setRange(15) },
      { slot: 20, label: '20 に設定', item: 'paper', onClick: () => setRange(20) },
      { slot: 21, label: '30 に設定', item: 'paper', onClick: () => setRange(30) },
      { slot: 22, label: '50 に設定', item: 'paper', onClick: () => setRange(50) },
      { slot: 23, label: '最大値に設定', item: 'compass', onClick: () => setRange(50) },
      { slot: 26, label: '戻る', item: 'arrow', onClick: () => setScreen('main') },
    ];
    return <MockPanel title={`sub接続範囲 [base1] 現在: ${range}`} rows={3} slots={buttons} notice={`現在の接続範囲: ${range}ブロック`} caption="SharedStorageSettingsUi.openSubRangeMenu の27スロットを再現。" />;
  }

  if (screen === 'chestpage') {
    const categories: ChestGuiSlot[] = [
      { slot: 0, label: '剣 / combat', item: 'iron_sword', lore: ['カテゴリ内sub: 2', '合計: 384個'], onClick: () => setNotice('剣カテゴリのsub一覧を開く操作です。') },
      { slot: 1, label: '鉱石 / ore', item: 'diamond_pickaxe', lore: ['カテゴリ内sub: 3', '合計: 912個'], onClick: () => setNotice('鉱石カテゴリを選択しました。') },
      { slot: 2, label: '食料 / food', item: 'golden_apple', lore: ['カテゴリ内sub: 1', 'クリックで直接開く'], onClick: () => setNotice('食料subを直接開く操作です。') },
      { slot: 49, label: '戻る', item: 'barrier', onClick: () => setScreen('main') },
    ];
    return <MockPanel title="ChestPage: base1 (1/1)" rows={6} slots={categories} notice={notice} caption="SharedStorageChestPageUi のカテゴリ/サブ選択を簡略再現。実際のUIと同じ54スロット構成です。" />;
  }

  const toggle = (value: boolean, setValue: (v: boolean) => void, label: string) => () => {
    setValue(!value);
    setNotice(`${label} を ${!value ? '許可' : '禁止'} に変更しました。`);
  };

  const slots: ChestGuiSlot[] = [
    { slot: 4, label: '共有ストレージ base1', item: 'ender_chest', lore: ['ID: base1', '接続中のsub: 6個', `接続範囲: ${range}ブロック`] },
    { slot: 9, label: '■ 手動操作', item: 'player_head', lore: ['プレイヤーがsubを直接開いた時のルール'] },
    { slot: 10, label: `subへ入れる: ${manualInsert ? '許可' : '禁止'}`, item: manualInsert ? 'lime_dye' : 'gray_dye', active: manualInsert, onClick: toggle(manualInsert, setManualInsert, 'subへ入れる') },
    { slot: 11, label: `subから取る: ${manualExtract ? '許可' : '禁止'}`, item: manualExtract ? 'lime_dye' : 'gray_dye', active: manualExtract, onClick: toggle(manualExtract, setManualExtract, 'subから取る') },
    { slot: 18, label: '■ ホッパー搬送', item: 'hopper', lore: ['自動搬送ルール'] },
    { slot: 19, label: 'sub搬入: 許可', item: 'lime_dye', active: true, onClick: () => setNotice('sub搬入を切り替える操作です。') },
    { slot: 20, label: 'sub搬出: 許可', item: 'lime_dye', active: true, onClick: () => setNotice('sub搬出を切り替える操作です。') },
    { slot: 21, label: 'main搬入: 許可', item: 'lime_dye', active: true, onClick: () => setNotice('main搬入を切り替える操作です。') },
    { slot: 22, label: 'main搬出: 禁止', item: 'gray_dye', onClick: () => setNotice('main搬出を切り替える操作です。') },
    { slot: 27, label: '■ 機能設定', item: 'redstone_torch' },
    { slot: 28, label: `額縁フィルタ: ${filter ? '許可' : '禁止'}`, item: filter ? 'lime_dye' : 'gray_dye', active: filter, onClick: () => { setFilter(!filter); setNotice('額縁フィルタを切り替えました。'); } },
    { slot: 29, label: `一致モード: ${filterMode}`, item: filter ? 'comparator' : 'gray_dye', onClick: () => { const order = ['EXACT','MATERIAL','ENCHANT_STATE'] as const; const next = order[(order.indexOf(filterMode) + 1) % order.length]; setFilterMode(next); setNotice(`一致モードを ${next} に変更しました。`); } },
    { slot: 30, label: `ChestPage: ${chestPage ? '許可' : '禁止'}`, item: chestPage ? 'lime_dye' : 'gray_dye', active: chestPage, onClick: () => { if (chestPage) setScreen('chestpage'); else setChestPage(true); } },
    { slot: 31, label: `搬送演出: ${particles ? '許可' : '禁止'}`, item: particles ? 'lime_dye' : 'gray_dye', active: particles, onClick: () => { setParticles(!particles); setNotice('搬送演出を切り替えました。'); } },
    { slot: 38, label: 'sub接続範囲を変更', item: 'spyglass', lore: [`現在: ${range} / 最大50`], onClick: () => setScreen('range') },
    { slot: 40, label: '今すぐ再仕分け', item: 'chest_minecart', onClick: () => setNotice('main/subを再仕分けしました（モック）。') },
    { slot: 44, label: '閉じる', item: 'barrier', onClick: () => setNotice('GUIを閉じる操作です。') },
  ];
  return <MockPanel title="共有ストレージ設定 [base1]" rows={5} slots={slots} notice={notice} caption="SharedStorageSettingsUi.java の45スロットをそのままWeb化。Toggle・一致モード・範囲・ChestPageを操作できます。" />;
}

type LandScreen = 'main' | 'fuel' | 'upgrade' | 'settings';

export function LandProtectionGuiMock() {
  const [screen, setScreen] = useState<LandScreen>('main');
  const [level, setLevel] = useState(12);
  const [fuel, setFuel] = useState(240);
  const [debug, setDebug] = useState(false);
  const [party, setParty] = useState(false);
  const [containers, setContainers] = useState(true);
  const [doors, setDoors] = useState(true);
  const [breakBlock, setBreakBlock] = useState(true);
  const [pvp, setPvp] = useState(false);
  const [notice, setNotice] = useState('土地保護コアの各管理画面をクリックで移動できます。');

  if (screen === 'fuel') {
    const slots: ChestGuiSlot[] = [
      { slot: 4, label: '燃料を下の段へ', item: 'oak_sign', lore: [`現在: ${fuel}ユニット`, '原木/板材/木炭/石炭/石炭ブロック'] },
      ...Array.from({length:9}, (_,i) => ({ slot: 9+i, label: i === 0 ? '石炭 ×8' : '燃料スロット', item: i === 0 ? 'coal' : undefined, lore: ['ゲーム内では編集可能'] } satisfies ChestGuiSlot)),
      { slot: 21, label: '投入する', item: 'lime_concrete', onClick: () => { setFuel((v) => v + 64); setNotice('燃料を64ユニット投入しました。'); } },
      { slot: 23, label: '戻る', item: 'arrow', onClick: () => setScreen('main') },
    ];
    return <MockPanel title={`燃料投入 (残り: ${Math.floor(fuel / 8)}時間)`} rows={3} slots={slots} notice={notice} caption="LandMenu.openFuel の27スロット。燃料スロットと投入操作を再現。" />;
  }

  if (screen === 'upgrade') {
    const slots: ChestGuiSlot[] = [
      { slot: 11, label: `Lv.${level} → Lv.${level + 1}`, item: 'experience_bottle', lore: ['保護半径と燃料消費が増加', '必要素材を確認'] },
      { slot: 15, label: 'レベルアップする', item: 'nether_star', onClick: () => { setLevel((v) => Math.min(100, v + 1)); setNotice(`Lv.${Math.min(100, level + 1)}へ強化しました（モック）。`); } },
      { slot: 26, label: '戻る', item: 'arrow', onClick: () => setScreen('main') },
    ];
    return <MockPanel title="レベルアップ" rows={3} slots={slots} notice={notice} caption="LandMenu.openUpgrade の27スロットを再現。" />;
  }

  if (screen === 'settings') {
    const toggleSlot = (slot:number, label:string, value:boolean, set:(v:boolean)=>void, item:string):ChestGuiSlot => ({
      slot, label: `${value ? '[有効]' : '[無効]'} ${label}`, item, active:value,
      onClick:()=>{set(!value);setNotice(`${label}を${!value?'有効':'無効'}に変更しました。`);}
    });
    const slots: ChestGuiSlot[] = [
      toggleSlot(10,'コンテナを開けなくする',containers,setContainers,'chest'),
      toggleSlot(11,'ドアを開けなくする',doors,setDoors,'oak_door'),
      { slot:12,label:'[有効] ボタン等を押せなくする',item:'stone_button',active:true,onClick:()=>setNotice('スイッチ制限を切り替える操作です。') },
      { slot:13,label:'[有効] ブロック設置を禁止',item:'bricks',active:true,onClick:()=>setNotice('設置制限を切り替える操作です。') },
      toggleSlot(14,'ブロック破壊を禁止',breakBlock,setBreakBlock,'iron_pickaxe'),
      toggleSlot(15,'領地内PVP',pvp,setPvp,'diamond_sword'),
      { slot:28,label:'侵入通知: タイトル',item:'bell',onClick:()=>setNotice('通知モード: タイトル → アクションバー → なし を切替') },
      { slot:30,label:'タイトル文を編集',item:'paper',onClick:()=>setNotice('ゲーム内では入力UIを開きます。') },
      { slot:31,label:'サブタイトル文を編集',item:'paper',onClick:()=>setNotice('サブタイトル編集を開く操作です。') },
      { slot:32,label:'アクションバー文を編集',item:'paper',onClick:()=>setNotice('アクションバー編集を開く操作です。') },
      { slot:44,label:'戻る',item:'arrow',onClick:()=>setScreen('main') },
    ];
    return <MockPanel title="保護設定" rows={5} slots={slots} notice={notice} caption="LandMenu.openSettings の45スロット。主要な保護ルールを実際にON/OFFできます。" />;
  }

  const slots: ChestGuiSlot[] = [
    { slot:4,label:`土地保護コア Lv.${level}`,item:'lodestone',lore:['状態: 保護有効',`燃料: ${fuel}ユニット`,`共有: ${party?'パーティー':'なし'}`] },
    { slot:10,label:'燃料を投入',item:'coal',onClick:()=>setScreen('fuel') },
    { slot:12,label:'レベルアップ',item:'diamond',onClick:()=>setScreen('upgrade') },
    { slot:14,label:'ホワイトリスト',item:'player_head',onClick:()=>setNotice('ホワイトリスト管理画面を開く操作です。') },
    { slot:16,label:'保護設定',item:'comparator',onClick:()=>setScreen('settings') },
    { slot:20,label:`デバッグ境界線: ${debug?'ON':'OFF'}`,item:'glowstone_dust',active:debug,onClick:()=>{setDebug(!debug);setNotice('境界線表示を切り替えました。');} },
    { slot:22,label:`パーティー共有: ${party?'ON':'OFF'}`,item:'white_banner',active:party,onClick:()=>{setParty(!party);setNotice('パーティー共有を切り替えました。');} },
    { slot:35,label:'閉じる',item:'barrier',onClick:()=>setNotice('GUIを閉じる操作です。') },
  ];
  return <MockPanel title={`土地保護コア Lv.${level}`} rows={4} slots={slots} notice={notice} caption="LandMenu.openMain の36スロットから、燃料/レベル/保護設定へ実際に遷移できます。" />;
}

export function PartyGuiMock() {
  const [publicParty, setPublicParty] = useState(true);
  const [friendlyFire, setFriendlyFire] = useState(false);
  const [nameTag, setNameTag] = useState(true);
  const [notice, setNotice] = useState('PartyMenu.openMain を再現。各管理項目をクリックできます。');
  const slots: ChestGuiSlot[] = [
    {slot:4,label:'§ Party: Builders',item:'lime_dye',lore:['参加方式: 公開','リーダー: Steve','メンバー: 6人','あなたの階級: リーダー']},
    {slot:10,label:'メンバー一覧',item:'player_head',onClick:()=>setNotice('メンバー一覧/階級管理を開く操作です。')},
    {slot:12,label:'メンバーを招待',item:'writable_book',onClick:()=>setNotice('オンラインプレイヤー招待画面を開く操作です。')},
    {slot:14,label:'名前を変更',item:'name_tag',onClick:()=>setNotice('パーティー名入力を開く操作です。')},
    {slot:16,label:'カラーを変更',item:'lime_dye',onClick:()=>setNotice('イメージカラー選択を開く操作です。')},
    {slot:20,label:'説明を変更',item:'writable_book',onClick:()=>setNotice('公開検索にも表示される説明を編集します。')},
    {slot:24,label:'パーティーを解散',item:'tnt',danger:true,onClick:()=>setNotice('実ゲームでは解散確認へ進みます。')},
    {slot:28,label:'パーティー設定',item:'redstone_torch',lore:[`公開: ${publicParty?'ON':'OFF'}`,`味方攻撃: ${friendlyFire?'ON':'OFF'}`,`名前表示: ${nameTag?'ON':'OFF'}`],onClick:()=>{setPublicParty(!publicParty);setFriendlyFire(!friendlyFire);setNameTag(!nameTag);setNotice('設定値を切り替えました。');}},
    {slot:35,label:'閉じる',item:'barrier',onClick:()=>setNotice('GUIを閉じる操作です。')},
  ];
  return <MockPanel title="パーティー: Builders" rows={4} slots={slots} notice={notice} caption="PartyMenu.openMain の36スロットを再現。管理項目と設定状態をクリックで試せます。" />;
}

export function ChestShopGuiMock() {
  const [ownerMode, setOwnerMode] = useState(false);
  const [stocks, setStocks] = useState([32, 5, 64, 1]);
  const [earnings, setEarnings] = useState(86);
  const [notice, setNotice] = useState('購入者画面です。商品をクリックすると在庫が減ります。');

  if (ownerMode) {
    const slots: ChestGuiSlot[] = [
      {slot:0,label:'編集ページを開く',item:'writable_book',onClick:()=>setNotice('26スロットの商品編集ページを開く操作です。')},
      {slot:10,label:'在庫補充スロット',item:'chest',lore:['ゲーム内では実アイテムを置けます']},
      {slot:12,label:'通貨: エメラルド',item:'emerald'},
      {slot:15,label:`収益: ${earnings}`,item:'gold_ingot',onClick:()=>{setEarnings(0);setNotice('収益を回収しました（モック）。');}},
      {slot:19,label:'在庫と売切れ表示',item:'paper',lore:[`総在庫: ${stocks.reduce((a,b)=>a+b,0)}`,'売切れ: なし']},
      {slot:26,label:'閉じる / 購入者表示へ',item:'red_stained_glass_pane',onClick:()=>{setOwnerMode(false);setNotice('購入者画面へ切り替えました。');}},
    ];
    return <MockPanel title="Shop Owner - BuilderMart" rows={3} slots={slots} notice={notice} caption="ChestShopUI のオーナー27スロット画面。収益回収や編集入口を操作できます。" />;
  }

  const goods = [
    ['Diamond','diamond',5],
    ['Iron Sword','iron_sword',3],
    ['Golden Apple','golden_apple',8],
    ['Name Tag','name_tag',2],
  ] as const;
  const slots: ChestGuiSlot[] = goods.map(([name,item,price],i)=>({
    slot:i,label:`${name} ×1`,item,lore:[`価格: Emerald ×${price}`,`在庫: ${stocks[i]}`],disabled:stocks[i] <= 0,
    onClick:()=>{setStocks(current=>current.map((v,index)=>index===i?Math.max(0,v-1):v));setNotice(`${name} を1セット購入しました。`);}
  }));
  slots.push(
    {slot:25,label:'Owner view',item:'writable_book',onClick:()=>{setOwnerMode(true);setNotice('オーナー画面へ切り替えました。');}},
    {slot:26,label:'購入方法',item:'book',lore:['クリックで1セット購入','表示価格は1セット分']},
  );
  return <MockPanel title="Shop UI - BuilderMart" rows={3} slots={slots} notice={notice} caption="ChestShopUI の購入者27スロットを操作可能に再現。商品クリックで在庫変化も確認できます。" />;
}

export function WarpStoneGuiMock() {
  const [gta, setGta] = useState(true);
  const [name, setName] = useState('中央拠点');
  const [notice, setNotice] = useState('発見済みワープストーンをクリックして移動先を選べます。');
  const destinations = [
    ['鉱山入口',120,64,-230],
    ['海上拠点',842,71,64],
    ['ネザーゲート',-95,68,311],
  ] as const;
  const slots: ChestGuiSlot[] = [
    {slot:0,label:'✎ 名前を変更',item:'writable_book',lore:[`現在: ${name}`],onClick:()=>{const next=name==='中央拠点'?'Main Base':'中央拠点';setName(next);setNotice(`名前を ${next} に変更しました。`);}},
    {slot:4,label:'◈ ワープストーン ◈',item:'lodestone',lore:[`発見済み: ${destinations.length}箇所`]},
    {slot:8,label:`✈ GTA Animation: ${gta?'ON':'OFF'}`,item:gta?'elytra':'gray_dye',active:gta,onClick:()=>{setGta(!gta);setNotice('GTA風カメラ演出を切り替えました。');}},
  ];
  destinations.forEach(([dest,x,y,z],i)=>slots.push({slot:9+i,label:`◈ ${dest}`,item:'lodestone',lore:[`world (${x}, ${y}, ${z})`,'クリックでワープ'],onClick:()=>setNotice(`${dest} へのワープを開始する操作です。`)}));
  return <MockPanel title="◈ ワープストーン ◈" rows={6} slots={slots} notice={notice} caption="WarpStoneUI.java の54スロット。名前変更・GTAトグル・発見済み地点選択を操作できます。" />;
}

export function ParallelFurnaceGuiMock() {
  const [cores,setCores]=useState(4);
  const [running,setRunning]=useState(true);
  const [notice,setNotice]=useState('素材/燃料/回収口と並列コア操作を再現しています。');
  const slots:ChestGuiSlot[]=[
    {slot:0,label:'並列かまど 情報',item:'book',lore:[`並列数: ${cores}`,`状態: ${running?'稼働中':'待機中'}`]},
    {slot:2,label:running?'✦ 稼働中':'待機中',item:running?'blast_furnace':'furnace',active:running,onClick:()=>{setRunning(!running);setNotice('稼働状態を切り替えたモック表示です。');}},
    {slot:4,label:'－コア',item:'furnace',onClick:()=>{setCores(v=>Math.max(1,v-1));setNotice('追加コアを1つ取り外しました。');}},
    {slot:5,label:`コア数: ${cores}`,item:'furnace'},
    {slot:6,label:'＋コア',item:'furnace',onClick:()=>{setCores(v=>Math.min(200,v+1));setNotice('かまどを1つ消費してコアを追加しました。');}},
    {slot:8,label:'搬出チェスト',item:'chest',onClick:()=>setNotice('搬出チェスト選択待ちを開始する操作です。')},
    {slot:9,label:'≫ 素材投入口',item:'hopper'},
    {slot:18,label:`≫ 焼成ライン (${Math.min(cores,3)}/${cores} 稼働中)`,item:'clock'},
    {slot:26,label:'燃料タンク: 72%',item:'lava_bucket'},
    {slot:27,label:'≫ 燃料投入口',item:'hopper'},
    {slot:36,label:'≪ 回収口',item:'chest_minecart'},
    {slot:45,label:'≪ 回収口 (続き)',item:'chest_minecart'},
  ];
  for(let i=10;i<=17;i++) slots.push({slot:i,label:i===10?'鉄鉱石 ×32':'素材スロット',item:i===10?'iron_ore':undefined,onClick:()=>setNotice('素材スロットはゲーム内では編集可能です。')});
  for(let i=19;i<=25;i++) slots.push({slot:i,label:i<22?`ライン${i-18}: 焼成中`:'ライン待機中',item:i<22?'iron_ore':'gray_stained_glass_pane',active:i<22});
  for(let i=28;i<=35;i++) slots.push({slot:i,label:i===28?'石炭 ×16':'燃料スロット',item:i===28?'coal':undefined,onClick:()=>setNotice('燃料スロットはゲーム内では編集可能です。')});
  for(let i=37;i<=44;i++) slots.push({slot:i,label:i===37?'鉄インゴット ×24':'回収スロット',item:i===37?'iron_ingot':undefined,onClick:()=>setNotice('完成品を取り出す操作です。')});
  for(let i=46;i<=53;i++) slots.push({slot:i,label:'回収スロット',onClick:()=>setNotice('完成品を取り出す操作です。')});
  return <MockPanel title="⚒ 並列かまど - 作業コア" rows={6} slots={slots} notice={notice} caption="ParallelFurnaceUI の54スロット構成を再現。コア数・稼働表示・素材/燃料/回収領域を確認できます。" />;
}

export function RecyclerGuiMock() {
  const [processed,setProcessed]=useState(12);
  const [busy,setBusy]=useState(true);
  const [notice,setNotice]=useState('投入口と回収口を持つRecyclerUIの共有54スロットを再現。');
  const slots:ChestGuiSlot[]=[
    {slot:0,label:'リサイクラー 情報',item:'book',lore:['処理速度: 毎秒最大8個','複数人で内容を共有']},
    {slot:2,label:busy?'⚙ 稼働中 ⚙':'● 待機中',item:busy?'grindstone':'gray_dye',active:busy,onClick:()=>{setBusy(!busy);setNotice('状態表示を切り替えました。');}},
    {slot:4,label:'統計',item:'experience_bottle',lore:[`分解した数: ${processed}`,'処分した数: 3']},
    {slot:6,label:'還元率: 50%',item:'comparator',lore:['耐久減少品は残り耐久で減額']},
    {slot:8,label:'使い方',item:'knowledge_book',lore:['投入口へ不要品','素材は回収口へ','レシピ無しはXPへ']},
    {slot:9,label:'≫ 投入口',item:'hopper'},
    {slot:18,label:'≪ 回収口',item:'chest_minecart'},
    {slot:27,label:'≪ 回収口 (続き)',item:'chest_minecart'},
    {slot:36,label:'≪ 回収口 (続き)',item:'chest_minecart'},
    {slot:45,label:'≪ 回収口 (続き)',item:'chest_minecart'},
  ];
  for(let i=10;i<=17;i++) slots.push({slot:i,label:i===10?'鉄のツルハシ':'投入スロット',item:i===10?'iron_pickaxe':undefined,onClick:()=>{setProcessed(v=>v+1);setBusy(true);setNotice('不要品を投入して分解処理を進めました。');}});
  const outputs=[19,20,28,29,37,46];
  outputs.forEach((slot,index)=>slots.push({slot,label:index%2===0?'鉄インゴット':'棒',item:index%2===0?'iron_ingot':'stick',count:index%2===0?2:1,onClick:()=>setNotice('回収口から素材を取り出す操作です。')}));
  return <MockPanel title="♻ リサイクラー - 分解装置" rows={6} slots={slots} notice={notice} caption="RecyclerUI.java の54スロットを再現。投入口/回収口、50%還元率、状態・統計を操作できます。" />;
}
