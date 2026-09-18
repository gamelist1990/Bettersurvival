export const MINECRAFT_ASSET_SOURCE =
  'Mojang/bedrock-samples main';

const ROOT =
  'https://raw.githubusercontent.com/Mojang/bedrock-samples/main/resource_pack/textures';

/**
 * Mojang公式 bedrock-samples/resource_pack/textures を正本にした
 * Wiki / interactive mock 用texture resolver。
 *
 * BetterSurvivalはJava/Paper向けだが、Wikiのvisual assetはユーザー指定により
 * Mojang公式サンプルの最新mainを使用する。Java Material名とBedrock texture名の
 * 差だけここで吸収する。
 */
const TEXTURE_PATHS: Record<string, string> = {
  arrow: 'items/arrow.png',
  bell: 'items/villagebell.png',
  bone_meal: 'items/dye_powder_white.png',
  brush: 'items/brush.png',
  copper_block: 'blocks/copper_block.png',
  diamond: 'items/diamond.png',
  elytra: 'items/elytra.png',
  farmland: 'blocks/farmland_wet.png',
  glowstone_dust: 'items/glowstone_dust.png',
  gold_ingot: 'items/gold_ingot.png',
  iron_hoe: 'items/iron_hoe.png',
  iron_ingot: 'items/iron_ingot.png',
  iron_ore: 'blocks/iron_ore.png',
  knowledge_book: 'items/book_normal.png',
  netherite_chestplate: 'items/netherite_chestplate.png',
  netherite_upgrade_smithing_template: 'items/netherite_upgrade_smithing_template.png',
  oak_sign: 'items/sign.png',
  stick: 'items/stick.png',
  sugar: 'items/sugar.png',
  wheat: 'items/wheat.png',
  wheat_seeds: 'items/seeds_wheat.png',
  white_banner: 'items/banner_pattern.png',

  barrier: 'blocks/barrier.png',
  book: 'items/book_normal.png',
  writable_book: 'items/book_writable.png',
  enchanted_book: 'items/book_enchanted.png',
  bow: 'items/bow_standby.png',
  brewing_stand: 'items/brewing_stand.png',
  bundle: 'items/bundle.png',
  campfire: 'items/campfire.png',
  clock: 'items/clock_item.png',
  comparator: 'items/comparator.png',
  compass: 'items/compass_item.png',
  recovery_compass: 'items/recovery_compass_item.png',
  diamond_pickaxe: 'items/diamond_pickaxe.png',
  emerald: 'items/emerald.png',
  ender_eye: 'items/ender_eye.png',
  experience_bottle: 'items/experience_bottle.png',
  filled_map: 'items/map_filled.png',
  golden_apple: 'items/apple_golden.png',
  hopper: 'items/hopper.png',
  iron_chestplate: 'items/iron_chestplate.png',
  iron_sword: 'items/iron_sword.png',
  lapis_lazuli: 'items/dye_powder_blue.png',
  lava_bucket: 'items/bucket_lava.png',
  water_bucket: 'items/bucket_water.png',
  name_tag: 'items/name_tag.png',
  paper: 'items/paper.png',
  redstone: 'items/redstone_dust.png',
  slime_ball: 'items/slimeball.png',
  spectral_arrow: 'items/arrow.png',
  spyglass: 'items/spyglass.png',
  stone_sword: 'items/stone_sword.png',

  chest: 'blocks/chest_front.png',
  trapped_chest: 'blocks/trapped_chest_front.png',
  ender_chest: 'blocks/ender_chest_front.png',
  grass_block: 'blocks/grass_side.png',
  diamond_block: 'blocks/diamond_block.png',
  lodestone: 'blocks/lodestone_side.png',
  tnt: 'blocks/tnt_side.png',
  enchanting_table: 'blocks/enchanting_table_side.png',

  player_head: 'entity/steve.png',

  lime_dye: 'items/dye_powder_lime.png',
  gray_dye: 'items/dye_powder_gray.png',
  purple_dye: 'items/dye_powder_purple.png',
  red_dye: 'items/dye_powder_red.png',
  green_dye: 'items/dye_powder_green.png',
  light_blue_dye: 'items/dye_powder_light_blue.png',

  redstone_torch: 'blocks/redstone_torch_on.png',
  chest_minecart: 'items/minecart_chest.png',
  item_frame: 'items/item_frame.png',
  furnace: 'blocks/furnace_front_off.png',
  blast_furnace: 'blocks/blast_furnace_front_off.png',
  grindstone: 'blocks/grindstone_side.png',
  coal: 'items/coal.png',
  coal_block: 'blocks/coal_block.png',
  oak_log: 'blocks/log_oak.png',
  oak_planks: 'blocks/planks_oak.png',
  oak_sign: 'items/sign.png',
  oak_door: 'items/door_wood.png',
  stone_button: 'blocks/stone.png',
  bricks: 'blocks/brick.png',
  iron_pickaxe: 'items/iron_pickaxe.png',
  diamond_sword: 'items/diamond_sword.png',
  bell: 'items/bell.png',
  nether_star: 'items/nether_star.png',
  lime_concrete: 'blocks/concrete_lime.png',

  red_stained_glass_pane: 'blocks/glass_red.png',
  pink_stained_glass_pane: 'blocks/glass_pink.png',
  lime_stained_glass_pane: 'blocks/glass_lime.png',
  green_stained_glass_pane: 'blocks/glass_green.png',
  purple_stained_glass_pane: 'blocks/glass_purple.png',
  gray_stained_glass_pane: 'blocks/glass_gray.png',
};

export function minecraftAssetPath(path: string) {
  return `${ROOT}/${path.replace(/^\/+/, '')}`;
}

export function minecraftItemAsset(name: string) {
  return minecraftAssetPath(TEXTURE_PATHS[name] ?? `items/${name}.png`);
}

export function minecraftBlockAsset(name: string) {
  return minecraftAssetPath(TEXTURE_PATHS[name] ?? `blocks/${name}.png`);
}

export const minecraftChestTexture = (ender = false) =>
  minecraftAssetPath(ender ? 'blocks/ender_chest_front.png' : 'blocks/chest_front.png');

export const minecraftPlayerSkin = () =>
  minecraftAssetPath('entity/steve.png');
