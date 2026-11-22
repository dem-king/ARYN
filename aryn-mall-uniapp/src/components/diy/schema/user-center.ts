export const userCenterServiceIds = [
  'message',
  'membership',
  'address',
  'review',
  'footprint',
  'favorite',
  'coupon',
  'support',
] as const
export const userCenterAssetIds = ['coupon', 'points', 'balance'] as const
export const userCenterWorkbenchIds = ['delivery', 'vessel', 'sales'] as const
export const userCenterOrderIds = ['payment', 'shipment', 'receipt', 'review', 'refund'] as const
export const userCenterIconIds = ['default', 'bookmark', 'pin', 'star', 'grid', 'sparkles'] as const

export type ServiceId = typeof userCenterServiceIds[number]
export type WorkbenchId = typeof userCenterWorkbenchIds[number]
export type OrderId = typeof userCenterOrderIds[number]
export type UserCenterIcon = typeof userCenterIconIds[number]
type AssetId = typeof userCenterAssetIds[number]
type CardStyle = 'default' | 'outline' | 'tinted'
interface LocalizedText { zh: string, en: string }
type IconOverrides<K extends string> = Partial<Record<K, UserCenterIcon>>

export interface UserCenterSettings {
  hero: {
    appearance: 'theme' | 'gradient' | 'image'
    startColor: string
    endColor: string
    imageUrl: string
    loginHint: LocalizedText
    memberStyle: 'glass' | 'solid'
    memberDescription: LocalizedText
  }
  assets: { order: AssetId[], style: CardStyle }
  orders: { title: LocalizedText, style: CardStyle, icons: IconOverrides<OrderId> }
  services: { order: ServiceId[], columns: 3 | 4 | 5, icons: IconOverrides<ServiceId> }
  workbenches: { order: WorkbenchId[], style: CardStyle, icons: IconOverrides<WorkbenchId> }
  recommendations: {
    visible: boolean
    title: LocalizedText
    source: 'recommended' | 'member' | 'clearance'
  }
  contentPosition: 'beforeOrders' | 'afterServices'
}

function record(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

function option<T>(value: unknown, options: readonly T[], fallback: T): T {
  return options.find(candidate => candidate === value) ?? fallback
}

function ordered<T extends string>(value: unknown, allowed: readonly T[], complete: boolean): T[] {
  if (!Array.isArray(value))
    return [...allowed]
  const selected = allowed.filter(id => value.includes(id)).sort((a, b) => value.indexOf(a) - value.indexOf(b))
  return complete ? [...selected, ...allowed.filter(id => !selected.includes(id))] : selected
}

function icons<K extends string>(value: unknown, ids: readonly K[]): IconOverrides<K> {
  const raw = record(value)
  const result: IconOverrides<K> = {}
  for (const id of ids) {
    const icon = option(raw[id], userCenterIconIds, 'default')
    if (icon !== 'default')
      result[id] = icon
  }
  return result
}

function text(value: unknown, limit: number): LocalizedText {
  const raw = record(value)
  const clean = (input: unknown) => typeof input === 'string'
    ? Array.from(input).slice(0, limit).join('')
    : ''
  return { zh: clean(raw.zh), en: clean(raw.en) }
}

function color(value: unknown, fallback: string): string {
  return typeof value === 'string' && /^#[\da-f]{6}$/i.test(value) ? value : fallback
}

function userCenterImageUrl(value: unknown): string {
  if (typeof value !== 'string')
    return ''
  const url = value.trim()
  // Images are shared by the browser preview and WeChat.
  return /^https?:\/\/[^/\s"'<>\\][^\s"'<>\\]*$/i.test(url) && url.length <= 2048 ? url : ''
}

const cardStyles = ['default', 'outline', 'tinted'] as const

export function normalizeUserCenter(value: unknown): UserCenterSettings {
  const raw = record(value)
  const hero = record(raw.hero)
  const assets = record(raw.assets)
  const orders = record(raw.orders)
  const services = record(raw.services)
  const workbenches = record(raw.workbenches)
  const recommendations = record(raw.recommendations)
  return {
    hero: {
      appearance: option(hero.appearance, ['theme', 'gradient', 'image'] as const, 'theme'),
      startColor: color(hero.startColor, '#173858'),
      endColor: color(hero.endColor, '#316584'),
      imageUrl: userCenterImageUrl(hero.imageUrl),
      loginHint: text(hero.loginHint, 72),
      memberStyle: option(hero.memberStyle, ['glass', 'solid'] as const, 'glass'),
      memberDescription: text(hero.memberDescription, 72),
    },
    assets: {
      order: ordered(assets.order, userCenterAssetIds, true),
      style: option(assets.style, cardStyles, 'default'),
    },
    orders: {
      title: text(orders.title, 28),
      style: option(orders.style, cardStyles, 'default'),
      icons: icons(orders.icons, userCenterOrderIds),
    },
    services: {
      order: ordered(services.order, userCenterServiceIds, false),
      columns: option(services.columns, [3, 4, 5] as const, 4),
      icons: icons(services.icons, userCenterServiceIds),
    },
    workbenches: {
      order: ordered(workbenches.order, userCenterWorkbenchIds, true),
      style: option(workbenches.style, cardStyles, 'default'),
      icons: icons(workbenches.icons, userCenterWorkbenchIds),
    },
    recommendations: {
      visible: recommendations.visible !== false,
      title: text(recommendations.title, 28),
      source: option(recommendations.source, ['recommended', 'member', 'clearance'] as const, 'recommended'),
    },
    contentPosition: option(raw.contentPosition, ['beforeOrders', 'afterServices'] as const, 'beforeOrders'),
  }
}

export function userCenterHeroStyle(hero: UserCenterSettings['hero']) {
  if (hero.appearance === 'theme')
    return {}
  const start = hero.appearance === 'image' ? '#172b42' : hero.startColor
  const end = hero.appearance === 'image' ? '#172b42' : hero.endColor
  const overlay = 'linear-gradient(180deg, rgba(0,0,0,.55), rgba(0,0,0,.55))'
  const background = hero.appearance === 'image' && hero.imageUrl
    ? `url("${hero.imageUrl}")`
    : `linear-gradient(180deg, ${start}, ${end})`
  const channels = start.slice(1).match(/../g) ?? []
  const navbarColor = `#${channels.map(channel => Math.round(Number.parseInt(channel, 16) * 0.45).toString(16).padStart(2, '0')).join('')}`
  return {
    backgroundColor: navbarColor,
    backgroundImage: `${overlay}, ${background}`,
    backgroundPosition: 'center',
    backgroundSize: 'cover',
  }
}
