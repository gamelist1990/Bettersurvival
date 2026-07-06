const NODE_WIDTH = 300;
const NODE_HEIGHT = 70;
const CHIP_WIDTH = 138;
const CHIP_HEIGHT = 30;
const CHIP_GAP_X = 12;
const CHIP_GAP_Y = 10;
const CHIP_COLUMNS = 2;

type Scope = 'system' | 'player';

type CategoryNode = {
  key: string;
  x: number;
  title: string;
  subtitle: string;
  scope: Scope;
  chips: string[];
};

const HUB_CENTER_X = 520;
const HUB_Y = 120;
const HUB_HEIGHT = 90;
const NODE_Y = 300;
const CHIPS_START_Y = NODE_Y + NODE_HEIGHT + 16;

const categoryNodes: CategoryNode[] = [
  {
    key: 'blocks',
    x: 40,
    title: 'カスタムブロック / 設置物',
    subtitle: '合成・設置で作る独自設備',
    scope: 'system',
    chips: ['WarpStone', 'ChunkLoader', 'Recycler', 'ParallelFurnace', 'LandProtect Core'],
  },
  {
    key: 'enchants',
    x: 370,
    title: 'カスタムエンチャント',
    subtitle: '専用テーブルから道具へ付与',
    scope: 'system',
    chips: ['専用テーブル', 'EnchantSplit', '装備へ付与'],
  },
  {
    key: 'utility',
    x: 700,
    title: '便利機能 (Utility)',
    subtitle: '採掘・保管・移動を補助',
    scope: 'player',
    chips: ['TreeMine', 'ChestLock', 'Home', 'TPA', 'Party', 'DeathChest'],
  },
];

const scopeColors: Record<Scope, { bg: string; text: string; border: string }> = {
  system: { bg: 'rgba(206, 104, 65, 0.12)', text: '#9f4426', border: 'rgba(206, 104, 65, 0.34)' },
  player: { bg: 'rgba(58, 134, 89, 0.12)', text: '#257047', border: 'rgba(58, 134, 89, 0.32)' },
};

function chipPosition(index: number, nodeX: number) {
  const rowStartX = nodeX + (NODE_WIDTH - (CHIP_COLUMNS * CHIP_WIDTH + (CHIP_COLUMNS - 1) * CHIP_GAP_X)) / 2;
  const col = index % CHIP_COLUMNS;
  const row = Math.floor(index / CHIP_COLUMNS);
  return {
    x: rowStartX + col * (CHIP_WIDTH + CHIP_GAP_X),
    y: CHIPS_START_Y + row * (CHIP_HEIGHT + CHIP_GAP_Y),
  };
}

export function FeatureRelationDiagram() {
  return (
    <figure className="wiki-real-image wiki-diagram-figure">
      <svg viewBox="0 0 1040 560" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="BetterSurvival 機能分類図">
        <defs>
          <marker id="fr-arrow" viewBox="0 0 10 10" refX="8.5" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
            <path d="M0,0 L10,5 L0,10 z" fill="var(--wiki-accent, #ce6841)" />
          </marker>
        </defs>

        <rect x="0" y="0" width="1040" height="560" rx="20" fill="var(--wiki-soft, #fff8f0)" />

        {/* OP command */}
        <rect x={HUB_CENTER_X - 110} y="10" width="220" height="46" rx="12" fill="#201713" />
        <text x={HUB_CENTER_X} y="38" textAnchor="middle" fontSize="14" fontWeight="700" fill="#fff3d7" fontFamily="ui-monospace, SFMono-Regular, Menlo, monospace">
          OP: /toggle &lt;機能キー&gt;
        </text>
        <line x1={HUB_CENTER_X} y1="56" x2={HUB_CENTER_X} y2={HUB_Y} stroke="var(--wiki-accent, #ce6841)" strokeWidth="2.5" markerEnd="url(#fr-arrow)" />

        {/* Hub */}
        <rect
          x={HUB_CENTER_X - 160}
          y={HUB_Y}
          width="320"
          height={HUB_HEIGHT}
          rx="22"
          fill="var(--wiki-accent-soft, rgba(206,104,65,0.14))"
          stroke="var(--wiki-accent, #ce6841)"
          strokeWidth="2"
        />
        <text x={HUB_CENTER_X} y={HUB_Y + 38} textAnchor="middle" fontSize="22" fontWeight="800" fill="var(--wiki-text, #211b17)">
          Loader
        </text>
        <text x={HUB_CENTER_X} y={HUB_Y + 62} textAnchor="middle" fontSize="13" fill="var(--wiki-muted, #65584f)">
          機能レジストリ / ON・OFF 管理
        </text>

        {/* Connections + category nodes */}
        {categoryNodes.map((node) => {
          const centerX = node.x + NODE_WIDTH / 2;
          const colors = scopeColors[node.scope];
          const hubBottom = HUB_Y + HUB_HEIGHT;
          const path =
            centerX === HUB_CENTER_X
              ? `M${HUB_CENTER_X},${hubBottom} L${centerX},${NODE_Y}`
              : `M${HUB_CENTER_X},${hubBottom} C${HUB_CENTER_X},${hubBottom + 50} ${centerX},${NODE_Y - 40} ${centerX},${NODE_Y}`;

          return (
            <g key={node.key}>
              <path d={path} fill="none" stroke="var(--wiki-accent, #ce6841)" strokeWidth="2.5" markerEnd="url(#fr-arrow)" />

              <rect
                x={node.x}
                y={NODE_Y}
                width={NODE_WIDTH}
                height={NODE_HEIGHT}
                rx="16"
                fill="rgba(255, 255, 255, 0.82)"
                stroke="var(--wiki-border, rgba(74,55,38,0.16))"
              />
              <rect x={node.x + 14} y={NODE_Y + 10} width="64" height="20" rx="10" fill={colors.bg} stroke={colors.border} />
              <text x={node.x + 46} y={NODE_Y + 24} textAnchor="middle" fontSize="10" fontWeight="800" letterSpacing="0.06em" fill={colors.text}>
                {node.scope === 'system' ? 'SYSTEM' : 'PLAYER'}
              </text>
              <text x={centerX} y={NODE_Y + 44} textAnchor="middle" fontSize="15" fontWeight="800" fill="var(--wiki-text, #211b17)">
                {node.title}
              </text>
              <text x={centerX} y={NODE_Y + 61} textAnchor="middle" fontSize="11.5" fill="var(--wiki-muted, #65584f)">
                {node.subtitle}
              </text>

              {node.chips.map((chip, index) => {
                const pos = chipPosition(index, node.x);
                return (
                  <g key={chip}>
                    <rect
                      x={pos.x}
                      y={pos.y}
                      width={CHIP_WIDTH}
                      height={CHIP_HEIGHT}
                      rx="999"
                      fill="rgba(255, 255, 255, 0.9)"
                      stroke="var(--wiki-border, rgba(74,55,38,0.16))"
                    />
                    <text
                      x={pos.x + CHIP_WIDTH / 2}
                      y={pos.y + CHIP_HEIGHT / 2 + 4}
                      textAnchor="middle"
                      fontSize="11.5"
                      fontWeight="700"
                      fill="var(--wiki-text, #211b17)"
                    >
                      {chip}
                    </text>
                  </g>
                );
              })}
            </g>
          );
        })}

        <text x={HUB_CENTER_X} y="524" textAnchor="middle" fontSize="13" fill="var(--wiki-muted, #65584f)">
          カスタムブロック・カスタムエンチャント・便利機能は、すべて Loader に登録された機能キーとして
        </text>
        <text x={HUB_CENTER_X} y="544" textAnchor="middle" fontSize="13" fill="var(--wiki-muted, #65584f)">
          /toggle &lt;機能キー&gt; から個別に ON・OFF できます。
        </text>
      </svg>
      <figcaption>BetterSurvival 機能分類図: /toggle を中心に、カスタムブロック・カスタムエンチャント・便利機能が同じ仕組みで管理されています。</figcaption>
    </figure>
  );
}
