const DIMENSION_ZH: Record<string, string> = {
  'minecraft:overworld': '主世界',
  'minecraft:the_nether': '下界',
  'minecraft:the_end': '末地',
  'twilightforest:twilight_forest': '暮色森林',
  'aether:the_aether': '天境',
  'the_bumblezone:the_bumblezone': '黄蜂领域',
  'undergarden:undergarden': '深暗之园',
  'blue_skies:everbright': '永昼之地',
  'blue_skies:everdawn': '永夜之地',
  'deeperdarker:otherside': '彼岸'
}

const DIMENSION_EN: Record<string, string> = {
  'minecraft:overworld': 'Overworld',
  'minecraft:the_nether': 'The Nether',
  'minecraft:the_end': 'The End'
}

const CATEGORY_ZH: Record<string, string> = {
  player: '玩家',
  waypoint: '路径点',
  structure: '结构',
  entity: '实体',
  hostile: '敌对生物',
  passive: '友好生物',
  boss: '首领',
  other: '其他实体'
}

const CATEGORY_EN: Record<string, string> = {
  player: 'Player',
  waypoint: 'Waypoint',
  structure: 'Structure',
  entity: 'Entity',
  hostile: 'Hostile Mob',
  passive: 'Passive Mob',
  boss: 'Boss',
  other: 'Other Entity'
}

const STRUCTURE_ZH: Record<string, string> = {
  village: '村庄',
  village_plains: '平原村庄',
  village_desert: '沙漠村庄',
  village_savanna: '热带草原村庄',
  village_snowy: '雪原村庄',
  village_taiga: '针叶林村庄',
  end_city: '末地城',
  endcity: '末地城',
  stronghold: '要塞',
  fortress: '下界要塞',
  bastion_remnant: '堡垒遗迹',
  nether_fossil: '下界化石',
  mansion: '林地府邸',
  woodland_mansion: '林地府邸',
  monument: '海底神殿',
  ocean_monument: '海底神殿',
  ancient_city: '远古城市',
  trail_ruins: '古迹废墟',
  trial_chambers: '试炼密室',
  ruined_portal: '废弃传送门',
  ruined_portal_desert: '沙漠废弃传送门',
  ruined_portal_jungle: '丛林废弃传送门',
  ruined_portal_mountain: '山地废弃传送门',
  ruined_portal_nether: '下界废弃传送门',
  ruined_portal_ocean: '海洋废弃传送门',
  ruined_portal_swamp: '沼泽废弃传送门',
  pillager_outpost: '掠夺者前哨站',
  mineshaft: '废弃矿井',
  mineshaft_mesa: '恶地废弃矿井',
  desert_pyramid: '沙漠神殿',
  jungle_pyramid: '丛林神庙',
  jungle_temple: '丛林神庙',
  igloo: '雪屋',
  swamp_hut: '女巫小屋',
  witch_hut: '女巫小屋',
  shipwreck: '沉船',
  shipwreck_beached: '搁浅沉船',
  ocean_ruin_cold: '冷水海底废墟',
  ocean_ruin_warm: '暖水海底废墟',
  buried_treasure: '埋藏的宝藏'
}

const ENTITY_ZH: Record<string, string> = {
  player: '玩家',
  zombie: '僵尸',
  zombie_villager: '僵尸村民',
  husk: '尸壳',
  drowned: '溺尸',
  skeleton: '骷髅',
  stray: '流浪者',
  bogged: '沼骸',
  wither_skeleton: '凋灵骷髅',
  creeper: '苦力怕',
  spider: '蜘蛛',
  cave_spider: '洞穴蜘蛛',
  enderman: '末影人',
  endermite: '末影螨',
  silverfish: '蠹虫',
  slime: '史莱姆',
  magma_cube: '岩浆怪',
  witch: '女巫',
  phantom: '幻翼',
  pillager: '掠夺者',
  vindicator: '卫道士',
  evoker: '唤魔者',
  vex: '恼鬼',
  ravager: '劫掠兽',
  blaze: '烈焰人',
  ghast: '恶魂',
  hoglin: '疣猪兽',
  zoglin: '僵尸疣猪兽',
  piglin: '猪灵',
  piglin_brute: '猪灵蛮兵',
  zombified_piglin: '僵尸猪灵',
  shulker: '潜影贝',
  guardian: '守卫者',
  elder_guardian: '远古守卫者',
  warden: '监守者',
  breeze: '旋风人',
  ender_dragon: '末影龙',
  wither: '凋灵',
  villager: '村民',
  wandering_trader: '流浪商人',
  iron_golem: '铁傀儡',
  snow_golem: '雪傀儡',
  cow: '牛',
  mooshroom: '哞菇',
  pig: '猪',
  sheep: '绵羊',
  chicken: '鸡',
  horse: '马',
  donkey: '驴',
  mule: '骡',
  skeleton_horse: '骷髅马',
  zombie_horse: '僵尸马',
  wolf: '狼',
  cat: '猫',
  ocelot: '豹猫',
  fox: '狐狸',
  bee: '蜜蜂',
  axolotl: '美西螈',
  frog: '青蛙',
  tadpole: '蝌蚪',
  goat: '山羊',
  camel: '骆驼',
  sniffer: '嗅探兽',
  armadillo: '犰狳',
  allay: '悦灵',
  rabbit: '兔子',
  turtle: '海龟',
  squid: '鱿鱼',
  glow_squid: '发光鱿鱼',
  dolphin: '海豚',
  llama: '羊驼',
  trader_llama: '行商羊驼',
  panda: '熊猫',
  polar_bear: '北极熊',
  strider: '炽足兽',
  bat: '蝙蝠',
  parrot: '鹦鹉',
  cod: '鳕鱼',
  salmon: '鲑鱼',
  tropical_fish: '热带鱼',
  pufferfish: '河豚',
  item: '掉落物',
  experience_orb: '经验球',
  minecart: '矿车',
  chest_minecart: '运输矿车',
  hopper_minecart: '漏斗矿车',
  furnace_minecart: '动力矿车',
  tnt_minecart: 'TNT 矿车',
  boat: '船',
  chest_boat: '运输船',
  armor_stand: '盔甲架',
  painting: '画',
  item_frame: '物品展示框',
  glow_item_frame: '荧光物品展示框',
  arrow: '箭',
  tnt: '点燃的 TNT',
  falling_block: '下落的方块'
}

function normalizeIdPath(raw?: string | null): string {
  if (!raw) return ''
  const trimmed = String(raw).trim()
  const colon = trimmed.indexOf(':')
  const path = colon >= 0 ? trimmed.slice(colon + 1) : trimmed
  return path.toLowerCase().replace(/^minecraft:/, '')
}

function titleCaseFromId(raw?: string | null): string {
  const path = normalizeIdPath(raw)
  if (!path) return ''
  return path
    .split(/[/_-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function isZhLocale(locale: string): boolean {
  return String(locale || '').toLowerCase().startsWith('zh')
}

export function localizeDimensionName(id: string, fallbackName: string | undefined, locale: string): string {
  const key = String(id || '').trim()
  if (isZhLocale(locale)) {
    if (DIMENSION_ZH[key]) return DIMENSION_ZH[key]
    const pathKey = normalizeIdPath(key)
    for (const [k, v] of Object.entries(DIMENSION_ZH)) {
      if (normalizeIdPath(k) === pathKey) return v
    }
  } else if (DIMENSION_EN[key]) {
    return DIMENSION_EN[key]
  }
  return fallbackName && fallbackName.trim() ? fallbackName : titleCaseFromId(key) || key
}

export function localizeMarkerCategory(category: string | undefined, locale: string): string {
  const key = String(category || 'other').toLowerCase()
  if (isZhLocale(locale)) {
    return CATEGORY_ZH[key] || category || ''
  }
  return CATEGORY_EN[key] || titleCaseFromId(key) || category || ''
}

export function localizeMarkerName(
  marker: { kind?: string; category?: string; typeId?: string; subType?: string; name?: string },
  locale: string
): string {
  const rawName = String(marker.name || '').trim()
  const kind = String(marker.kind || marker.category || '').toLowerCase()
  const typeId = String(marker.typeId || marker.subType || '').trim()
  const pathKey = normalizeIdPath(typeId || rawName)

  if (kind === 'player' || marker.category === 'player') {
    return rawName || 'Player'
  }
  if (kind === 'waypoint' || marker.category === 'waypoint') {
    return rawName || (isZhLocale(locale) ? '路径点' : 'Waypoint')
  }

  if (isZhLocale(locale)) {
    if (kind === 'structure' || marker.category === 'structure') {
      if (STRUCTURE_ZH[pathKey]) return STRUCTURE_ZH[pathKey]
      const cleanNameKey = rawName.toLowerCase().replace(/\s+/g, '_')
      if (STRUCTURE_ZH[cleanNameKey]) return STRUCTURE_ZH[cleanNameKey]
    }
    if (ENTITY_ZH[pathKey]) {
      // Preserve custom mob name if different from default entity id/title
      const defaultEn = titleCaseFromId(pathKey)
      if (!rawName || rawName.toLowerCase() === typeId.toLowerCase() || rawName.toLowerCase() === defaultEn.toLowerCase() || rawName.toLowerCase() === pathKey) {
        return ENTITY_ZH[pathKey]
      }
      return `${rawName} (${ENTITY_ZH[pathKey]})`
    }
  }

  if (rawName && !rawName.includes(':')) {
    return rawName
  }
  return titleCaseFromId(typeId || rawName) || rawName || typeId
}
