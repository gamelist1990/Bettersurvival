export const MINECRAFT_ASSET_VERSION = '26.1';

const ROOT =
  'https://raw.githubusercontent.com/PrismarineJS/minecraft-assets/master/data/26.1';

/**
 * PrismarineJS minecraft-assets の data/26.1/items_textures.json を基準にした
 * WebSite用のtexture path。
 *
 * Java Editionでは「item id = items/<id>.png」ではないものがあるため、
 * animation frame / block model / special rendererをここで明示的に解決する。
 */
const ITEM_TEXTURE_PATHS: Record<string, string> = {
  arrow: 'items/arrow.png',
  barrier: 'items/barrier.png',
  book: 'items/book.png',
  bow: 'items/bow.png',
  brewing_stand: 'items/brewing_stand.png',
  bundle: 'items/bundle.png',
  campfire: 'items/campfire.png',
  clock: 'items/clock_00.png',
  comparator: 'items/comparator.png',
  compass: 'items/compass_16.png',
  diamond_pickaxe: 'items/diamond_pickaxe.png',
  emerald: 'items/emerald.png',
  enchanted_book: 'items/enchanted_book.png',
  ender_eye: 'items/ender_eye.png',
  experience_bottle: 'items/experience_bottle.png',
  filled_map: 'items/filled_map.png',
  golden_apple: 'items/golden_apple.png',
  hopper: 'items/hopper.png',
  iron_chestplate: 'items/iron_chestplate.png',
  iron_sword: 'items/iron_sword.png',
  lapis_lazuli: 'items/lapis_lazuli.png',
  lava_bucket: 'items/lava_bucket.png',
  name_tag: 'items/name_tag.png',
  paper: 'items/paper.png',
  recovery_compass: 'items/recovery_compass_16.png',
  redstone: 'items/redstone.png',
  slime_ball: 'items/slime_ball.png',
  spectral_arrow: 'items/spectral_arrow.png',
  spyglass: 'items/spyglass.png',
  stone_sword: 'items/stone_sword.png',
  water_bucket: 'items/water_bucket.png',
  writable_book: 'items/writable_book.png',

  // items_textures.json上ではblock modelを参照するアイテム。
  grass_block: 'blocks/grass_block_side.png',
  diamond_block: 'blocks/diamond_block.png',
  lodestone: 'blocks/lodestone_side.png',
  tnt: 'blocks/tnt_side.png',

  // special renderer items。ChestGuiMockでは専用描画するが、
  // 通常imgとして要求された場合にも26.1実textureへfallbackできる。
  chest: 'entity/chest/normal.png',
  ender_chest: 'entity/chest/ender.png',
  player_head: 'entity/player/wide/steve.png',
};

export function minecraft26AssetPath(path: string) {
  return `${ROOT}/${path.replace(/^\/+/, '')}`;
}

export function minecraft26ItemAsset(name: string) {
  return minecraft26AssetPath(ITEM_TEXTURE_PATHS[name] ?? `items/${name}.png`);
}

export const minecraft26ChestTexture = (ender = false) =>
  minecraft26AssetPath(ender ? 'entity/chest/ender.png' : 'entity/chest/normal.png');

export const minecraft26PlayerSkin = () =>
  minecraft26AssetPath('entity/player/wide/steve.png');

export type Minecraft26BlockIcon = {
  top: string;
  side: string;
  front?: string;
};

const BLOCK_ICONS: Record<string, Minecraft26BlockIcon> = {
  grass_block: {
    top: minecraft26AssetPath('blocks/grass_block_top.png'),
    side: minecraft26AssetPath('blocks/grass_block_side.png'),
  },
  tnt: {
    top: minecraft26AssetPath('blocks/tnt_top.png'),
    side: minecraft26AssetPath('blocks/tnt_side.png'),
  },
  diamond_block: {
    top: minecraft26AssetPath('blocks/diamond_block.png'),
    side: minecraft26AssetPath('blocks/diamond_block.png'),
  },
  lodestone: {
    top: minecraft26AssetPath('blocks/lodestone_top.png'),
    side: minecraft26AssetPath('blocks/lodestone_side.png'),
  },
};

export function minecraft26BlockIcon(name: string) {
  return BLOCK_ICONS[name] ?? null;
}
