import { useMemo, useState } from 'react';
import { SectionShell } from '../components/SectionShell';
import { CommandBox } from '../components/CommandBox';
import { ChestGuiMock, type ChestGuiSlot } from '../components/ChestGuiMock';

type ProtectScreen = 'main' | 'advanced' | 'history' | 'status' | 'settings' | 'reset';

const historyEntries = [
  ['Steve', 'BLOCK_BREAK', 'diamond_pickaxe', '12s前 · 120,64,-35 · diamond_block → air'],
  ['Steve', 'BLOCK_PLACE', 'grass_block', '18s前 · 121,64,-35 · air → tnt'],
  ['Steve', 'EXPLOSION', 'tnt', '25s前 · 123,64,-36 · stone → air'],
  ['Alex', 'CONTAINER_CHANGE', 'chest', '2m前 · 118,64,-31 · WITHDRAW diamond ×12'],
  ['#liquid', 'LIQUID_FLOW', 'water_bucket', '3m前 · 116,63,-30 · water[level=4]'],
] as const;

function ProtectGuiDemo() {
  const [screen, setScreen] = useState<ProtectScreen>('main');
  const [inspector, setInspector] = useState(false);
  const [actor, setActor] = useState('Steve');
  const [hours, setHours] = useState(2);
  const [radius, setRadius] = useState(100);
  const [actionMode, setActionMode] = useState('BLOCK');
  const [retention, setRetention] = useState(30);
  const [notice, setNotice] = useState('ボタンをクリックして Protect GUI の流れを試せます。');
  const [records, setRecords] = useState(128420);

  const title = useMemo(() => {
    switch (screen) {
      case 'advanced': return 'Protect - 詳細調査 / 復旧';
      case 'history': return 'Protect Detail Lookup [1]';
      case 'status': return 'Protect - Status';
      case 'settings': return 'Protect - Settings';
      case 'reset': return 'Protect DB 初期化';
      default: return 'Protect - Audit & Rollback';
    }
  }, [screen]);

  const go = (next: ProtectScreen, message: string) => {
    setScreen(next);
    setNotice(message);
  };

  const main: ChestGuiSlot[] = [
    { slot: 10, label: '周辺の履歴', item: 'spyglass', lore: ['半径10ブロック', '保持期間内の監査ログ'], onClick: () => go('history', '周辺10ブロックの履歴を表示しました。') },
    { slot: 12, label: 'コンテナ履歴', item: 'chest', lore: ['OPEN / CLOSE / CHANGE / TRANSFER'], onClick: () => go('history', 'コンテナ系Actionだけで履歴を絞り込みました。') },
    { slot: 14, label: `Inspector: ${inspector ? 'ON' : 'OFF'}`, item: 'recovery_compass', active: inspector, lore: ['ゲーム内ではブロックをクリックして調査'], onClick: () => { setInspector((value) => !value); setNotice(`Inspectorを${inspector ? 'OFF' : 'ON'}に切り替えました。`); } },
    { slot: 16, label: '詳細調査 / 復旧', item: 'recovery_compass', lore: ['Player / Time / Radius / Action', 'Lookup / Rollback / Restore'], onClick: () => go('advanced', '詳細フィルタ画面を開きました。') },
    { slot: 20, label: '世界変化', item: 'grass_block', lore: ['Block / Liquid / Fire / Explosion / Growth'], onClick: () => go('history', 'World系Actionのみ表示しました。') },
    { slot: 22, label: 'アイテム履歴', item: 'bundle', lore: ['Drop / Pickup / Craft / Trade / Interact'], onClick: () => go('history', 'Item系Actionのみ表示しました。') },
    { slot: 24, label: '高度な検索条件', item: 'comparator', lore: ['CoreProtect風フィルタ'], onClick: () => go('advanced', '高度な検索条件へ移動しました。') },
    { slot: 30, label: 'Protect Settings', item: 'writable_book', lore: ['Retention / DB初期化 / Cleanup / Status'], onClick: () => go('settings', 'Protect Settingsを開きました。') },
    { slot: 32, label: 'Protect Status', item: 'book', lore: ['件数 / DB容量 / WAL / Queue / 空き容量'], onClick: () => go('status', 'Protect Statusを開きました。') },
    { slot: 49, label: '閉じる', item: 'barrier', lore: ['モックではMainへ戻ります'], onClick: () => { setScreen('main'); setNotice('GUIを閉じる操作を再現しました。'); } },
  ];

  const advanced: ChestGuiSlot[] = [
    { slot: 10, label: `Player: ${actor}`, item: 'player_head', lore: ['クリックで Steve / Alex / * を切替'], onClick: () => { const next = actor === 'Steve' ? 'Alex' : actor === 'Alex' ? '*' : 'Steve'; setActor(next); setNotice(`Player filter = ${next}`); } },
    { slot: 11, label: `Time: ${hours}h`, item: 'clock', lore: ['クリックで 2h / 24h / 168h'], onClick: () => { const next = hours === 2 ? 24 : hours === 24 ? 168 : 2; setHours(next); setNotice(`Time filter = ${next}h`); } },
    { slot: 12, label: `Radius: ${radius}`, item: 'compass', lore: ['クリックで 50 / 100 / 256'], onClick: () => { const next = radius === 50 ? 100 : radius === 100 ? 256 : 50; setRadius(next); setNotice(`Radius = ${next}`); } },
    { slot: 13, label: `Action: ${actionMode}`, item: 'comparator', lore: ['BLOCK / CONTAINER / ITEM / ALL'], onClick: () => { const order = ['BLOCK', 'CONTAINER', 'ITEM', 'ALL']; const next = order[(order.indexOf(actionMode) + 1) % order.length]; setActionMode(next); setNotice(`Action = ${next}`); } },
    { slot: 14, label: 'Location', item: 'lodestone', lore: ['world 120,64,-35', 'ゲーム内ではWorld + XYZ指定'], onClick: () => setNotice('検索中心を world 120,64,-35 に設定した想定です。') },
    { slot: 15, label: 'Limit: 10000', item: 'hopper', lore: ['最大処理件数'], onClick: () => setNotice('Limit設定ダイアログを開く操作です。') },
    { slot: 16, label: '条件をリセット', item: 'redstone', danger: true, lore: ['Player=* / Time=24h / Radius=100 / ALL'], onClick: () => { setActor('*'); setHours(24); setRadius(100); setActionMode('ALL'); setNotice('詳細条件を初期値へ戻しました。'); } },
    { slot: 20, label: 'Lookup', item: 'spyglass', lore: [`user:${actor} time:${hours}h radius:${radius}`, `action:${actionMode.toLowerCase()}`], onClick: () => go('history', '同じ条件で履歴を検索しました。') },
    { slot: 22, label: 'Rollback Preview', item: 'tnt', danger: true, lore: ['実際には変更せず対象件数を確認'], onClick: () => setNotice('PREVIEW: 37件がRollback対象です。ワールドは変更していません。') },
    { slot: 24, label: 'Restore Preview', item: 'slime_ball', lore: ['Rollback済みログを再適用'], onClick: () => setNotice('PREVIEW: 12件がRestore対象です。') },
    { slot: 29, label: 'Undo', item: 'arrow', lore: ['最後のRollbackを取り消す'], onClick: () => setNotice('最後のRollbackをUndoする操作です。') },
    { slot: 31, label: 'Redo', item: 'spectral_arrow', lore: ['Undoした内容を再Rollback'], onClick: () => setNotice('直前のUndoをRedoする操作です。') },
    { slot: 33, label: 'DB Stats', item: 'book', lore: ['保存容量とQueue状態'], onClick: () => go('status', 'Status画面へ移動しました。') },
    { slot: 40, label: `Retention: ${retention}日`, item: 'writable_book', onClick: () => go('settings', 'SettingsのRetention管理へ移動しました。') },
    { slot: 42, label: 'Purge', item: 'lava_bucket', danger: true, lore: ['古いログの完全削除', 'ゲーム内では最終確認あり'], onClick: () => setNotice('Purgeは不可逆のため、実ゲームでは確認ダイアログが入ります。') },
    { slot: 45, label: 'メインへ', item: 'arrow', onClick: () => go('main', 'Mainへ戻りました。') },
    { slot: 49, label: '閉じる', item: 'barrier', onClick: () => go('main', 'GUIを閉じる操作を再現しました。') },
  ];

  const history: ChestGuiSlot[] = historyEntries.map(([name, action, item, lore], index) => ({
    slot: index,
    label: `${action} ${name}`,
    item,
    lore,
    onClick: () => setNotice(`Log #${42001 + index}: ${action} / actor=${name} を選択しました。`),
  }));
  history.push(
    { slot: 45, label: '条件画面へ', item: 'comparator', onClick: () => go('advanced', '詳細条件へ戻りました。') },
    { slot: 49, label: 'Page 1', item: 'paper', lore: [`Player=${actor}`, `Time=${hours}h / Radius=${radius}`, `Action=${actionMode}`] },
    { slot: 50, label: '次のページ', item: 'arrow', onClick: () => setNotice('Page 2へ移動する操作です。') },
    { slot: 53, label: '再検索', item: 'spyglass', onClick: () => setNotice('同じ条件で最新ログを再検索しました。') },
  );

  const status: ChestGuiSlot[] = [
    { slot: 10, label: 'ONLINE', item: 'lime_dye', active: true, lore: ['SQLite Ready', 'WAL / synchronous=NORMAL'] },
    { slot: 12, label: `保存ログ: ${records.toLocaleString()}`, item: 'book', lore: ['直近24時間: 2,812', 'Rollback済み: 86'] },
    { slot: 14, label: records === 0 ? '保存容量: 96 KiB' : '保存容量: 42.8 MiB', item: 'chest', lore: ['DB: 38.4 MiB', 'WAL: 4.3 MiB', 'SHM: 32 KiB'] },
    { slot: 16, label: 'ディスク空き: 84.2 GiB', item: 'ender_chest', lore: ['Total: 128 GiB'] },
    { slot: 20, label: 'Write Queue: 14 / 20000', item: 'hopper', lore: ['使用率 0.1%', '非同期書き込み待機'] },
    { slot: 22, label: 'Dropped: 0', item: 'redstone', lore: ['0が正常です'] },
    { slot: 24, label: `Retention: ${retention}日`, item: 'writable_book', onClick: () => go('settings', 'Retention設定へ移動しました。') },
    { slot: 29, label: '24h Activity: 2,812', item: 'clock' },
    { slot: 31, label: 'Storage Engine', item: 'comparator', lore: ['SQLite / WAL', 'Batch INSERT max 256', 'Flush 250ms'] },
    { slot: 33, label: 'Status更新', item: 'spyglass', onClick: () => setNotice('最新のDB Statusを再取得しました。') },
    { slot: 40, label: 'Settings', item: 'writable_book', onClick: () => go('settings', 'Settingsを開きました。') },
    { slot: 45, label: 'メインへ戻る', item: 'arrow', onClick: () => go('main', 'Mainへ戻りました。') },
    { slot: 49, label: '閉じる', item: 'barrier', onClick: () => go('main', 'GUIを閉じる操作を再現しました。') },
  ];

  const settings: ChestGuiSlot[] = [
    { slot: 11, label: `Retention: ${retention}日`, item: 'writable_book', lore: ['クリックで 30 / 90 / 365日'], onClick: () => { const next = retention === 30 ? 90 : retention === 90 ? 365 : 30; setRetention(next); setNotice(`Retentionを${next}日に変更した想定です。`); } },
    { slot: 13, label: 'Status', item: 'book', onClick: () => go('status', 'Statusを開きました。') },
    { slot: 15, label: 'Cleanup Now', item: 'brush', lore: ['期限切れログ削除を即時要求'], onClick: () => setNotice('Retention cleanupを要求しました。') },
    { slot: 22, label: 'DBを初期化', item: 'tnt', danger: true, lore: ['全監査ログを削除', '実ゲームではRESET入力が必要'], onClick: () => go('reset', 'DB初期化の警告画面へ進みました。') },
    { slot: 45, label: 'メインへ', item: 'arrow', onClick: () => go('main', 'Mainへ戻りました。') },
    { slot: 49, label: '閉じる', item: 'barrier', onClick: () => go('main', 'GUIを閉じる操作を再現しました。') },
  ];

  const reset: ChestGuiSlot[] = [
    { slot: 12, label: 'キャンセル', item: 'barrier', onClick: () => go('settings', 'DB初期化をキャンセルしました。') },
    { slot: 14, label: 'RESETを入力済みとして初期化', item: 'tnt', danger: true, lore: ['モック用の疑似操作です', '実ゲームでは文字入力が必須'], onClick: () => { setRecords(0); setNotice('モックDBを初期化しました。実ゲームではWAL TRUNCATE + VACUUMも実行します。'); setScreen('status'); } },
  ];

  const slots = screen === 'advanced' ? advanced
    : screen === 'history' ? history
    : screen === 'status' ? status
    : screen === 'settings' ? settings
    : screen === 'reset' ? reset
    : main;

  return (
    <div className="protect-demo">
      <ChestGuiMock
        title={title}
        rows={screen === 'reset' ? 3 : 6}
        slots={slots}
        caption="ゲーム内のスロット構成をWeb向けに再現した操作モックです。アイコンへHover/FocusするとLore、クリックすると画面遷移や設定変更を試せます。"
      />
      <div className="protect-demo-console" aria-live="polite">
        <strong>Mock event</strong>
        <span>{notice}</span>
      </div>
    </div>
  );
}

export function ProtectSection() {
  return (
    <SectionShell
      eyebrow="Protect"
      title="Protect 監査・荒らし復旧ガイド"
      intro="CoreProtect系の考え方で、ブロック・自然変化・コンテナ・アイテム操作をSQLiteへ記録し、GUIまたはコマンドから調査・Rollback・RestoreできるOP向け監査機能です。"
      scope="op"
    >
      <div className="protect-callout">
        <strong>荒らし対応の基本</strong>
        <p>まず対象プレイヤー・時間・半径・Actionで履歴を絞り、Previewで件数を確認してからRollbackします。間違えた場合はUndo / Restoreで再適用できます。</p>
      </div>

      <h3>実際に触れる Protect ChestGUI</h3>
      <p>Main、詳細調査、履歴、Status、Settings、DB初期化確認までをブラウザ上で試せます。ゲーム内と同じ54スロット番号を使って配置しています。</p>
      <ProtectGuiDemo />

      <h3>荒らしをプレイヤー単位で戻す</h3>
      <CommandBox command="/protect lookup user:Steve time:2h radius:100 action:block" description="Steveによる直近2時間・半径100ブロックの世界変更を調査。" op />
      <CommandBox command="/protect rollback user:Steve time:2h radius:100 action:block preview:true" description="実際には変更せずRollback対象件数だけを確認。" op />
      <CommandBox command="/protect rollback user:Steve time:2h radius:100 action:block" description="確認した同じ条件でSteveによる復元可能な変更だけをRollback。" op />
      <CommandBox command="/protect restore user:Steve time:2h radius:100 action:block" description="Rollback済みの変更を再適用。" op />

      <h3>記録する主なAction</h3>
      <div className="protect-feature-grid">
        <article><strong>World / Block</strong><p>設置・破壊、Physics、Piston、液体、火災、爆発、Entity変更、成長、葉、Sculk、Portal、踏み荒らしなど。</p></article>
        <article><strong>Container</strong><p>Open / Close / Deposit / Withdraw / Replace / Hopper・Dropper・Dispenserの自動搬送。</p></article>
        <article><strong>Item</strong><p>Drop / Pickup / Break / Craft / Shoot / Trade / ItemFrame・Painting・特殊ブロック操作。</p></article>
        <article><strong>BlockEntity</strong><p>Rollback時はContainer内容、TileState PDC、看板の両面テキスト・色・発光・waxもsnapshotから復元します。</p></article>
      </div>

      <h3>Rollbackの安全対策</h3>
      <ul className="wiki-bullets">
        <li>Piston、Dragon Egg、ベッド等の複数ブロック操作は <strong>operation ID</strong> でまとめ、片側だけ選択されても同じ操作全体を復旧します。</li>
        <li>未ロードChunkはメインスレッドで同期ロードせず、Paperの非同期Chunk loadを利用します。</li>
        <li>Rollback / Restoreは同時多重実行を防止し、Replay中に発生したPhysicsを新しい監査ログとして再記録しません。</li>
        <li>Undo / Redo、1ログ単位のRollback / Restoreにも対応します。</li>
      </ul>

      <h3>保存・Status</h3>
      <p>SQLite WALを使い、書き込みは非同期Queue + batchで処理します。既定の保持期間は30日で、Settingsから変更できます。</p>
      <div className="protect-status-cards">
        <div><span>Storage</span><strong>DB + WAL + SHM</strong><small>総保存容量をGUI表示</small></div>
        <div><span>Queue</span><strong>最大 20,000</strong><small>使用率・Drop数を監視</small></div>
        <div><span>Retention</span><strong>30日</strong><small>1～3650日へ変更可能</small></div>
        <div><span>Cleanup</span><strong>自動 + 手動</strong><small>期限切れログを小分け削除</small></div>
      </div>
      <CommandBox command="/protect status" description="保存件数、直近24h、Rollback済み件数、DB/WAL/SHM、空きディスク、Queue、Dropped、Retentionを表示。" op />

      <h3>DB初期化</h3>
      <p>Settingsから全監査ログを初期化できます。誤操作防止のため、ゲーム内では警告 → 最終確認 → <code>RESET</code>入力が必要です。初期化後はSQLiteのschemaを維持したままWAL checkpoint(TRUNCATE)、VACUUM、optimizeを実行し、再起動なしで監査を再開します。</p>

      <h3>コマンド早見表</h3>
      <div className="protect-command-table">
        <table>
          <thead><tr><th>コマンド</th><th>用途</th></tr></thead>
          <tbody>
            <tr><td><code>/protect</code></td><td>管理ChestGUIを開く</td></tr>
            <tr><td><code>/protect inspect</code></td><td>ブロッククリックInspector ON/OFF</td></tr>
            <tr><td><code>/protect lookup ...</code></td><td>user / time / radius / action / world / xyz等で検索</td></tr>
            <tr><td><code>/protect rollback ...</code></td><td>条件に一致する復元可能ログを逆順適用</td></tr>
            <tr><td><code>/protect restore ...</code></td><td>Rollback済み変更を再適用</td></tr>
            <tr><td><code>/protect undo / redo</code></td><td>管理者が行った復旧操作を戻す / 再実行</td></tr>
            <tr><td><code>/protect status</code></td><td>DB・Queue・容量・Retentionを確認</td></tr>
            <tr><td><code>/protect purge time:30d confirm:true</code></td><td>指定期間より古いログを完全削除</td></tr>
          </tbody>
        </table>
      </div>
    </SectionShell>
  );
}
