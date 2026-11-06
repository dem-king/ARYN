/**
 * 类目角标（纯函数，便于单测）。
 *
 * 分类页左栏在二级类目名前置一个小角标（参考图：「荐」= 时令推荐、「热」= 热卖）。
 *
 * 两代数据来源：
 * - 旧后端 / 未迁移数据：`goods_category.badge_type` 只存语义枚举（'1' 荐 / '2' 热），
 *   文案与配色内置在 `CATEGORY_BADGES`，同一语义在全租户下样式一致，尺寸走默认值；
 * - 类目角标配置化：`badge_type` 改存 `goods_category_badge.badge_key`，类目树
 *   `extra.badge` 直接下发 `{ text, bgColor, color }`；配置化后追加 `iconUrl`（图片角标）、
 *   `height`（rpx）与 `borderRadius`（rpx），运营在管理后台自由配置文案、配色与尺寸。
 *
 * 解析口径：服务端下发的 badge 合法时以服务端为准（新建角标的 key 是雪花 id，
 * 本地没有映射）；服务端未下发或字段非法时回退旧语义映射（兼容旧后端 / 旧数据）；
 * `'0'` 是显式的「无角标」语义，一律返回 null。
 *
 * 兼容口径：新增字段缺省或非法时一律回退默认（无图、28rpx 高、4rpx 圆角、字号 19、
 * 内边距 5），旧后端 / 旧数据渲染出的角标与改造前完全一致；返回值始终带齐全部字段，
 * 调用方只读不写，无需逐字段判空。
 *
 * 与 [[category-icon]] 的分工：那个管「类目没有配图时用什么兜底」，
 * 这个管「类目有没有角标、角标长什么样」，互不影响。
 */

import { translate } from '@/locales'

/** 旧版角标语义 → 文案与配色。key 与旧后端 badge_type 取值一一对应 */
export const CATEGORY_BADGES = {
  /** 推荐（参考图「荐」，绿色） */
  1: { text: '荐', bgColor: '#00B578', color: '#FFFFFF' },
  /** 热卖（参考图「热」，红色） */
  2: { text: '热', bgColor: '#FF4D2D', color: '#FFFFFF' },
} as const

/**
 * 角标的默认形状：旧后端 / 旧数据 / 缺省或非法的配置统一回退到这里。
 *
 * 数值与改造前 CSS 里写死的「28rpx 高、4rpx 圆角、19rpx 字号、5rpx 左右内边距」等价：
 * 字号与内边距由高度推导，28rpx 代入公式正好得到 19 与 5。
 */
const BADGE_DEFAULTS = {
  /** 角标高度（rpx） */
  height: 28,
  /** 角标圆角（rpx） */
  borderRadius: 4,
  /** 图片角标缺省底色（图片角标可以只配图，配色回退默认值） */
  bgColor: '#00B578',
  /** 图片角标缺省文字色 */
  color: '#FFFFFF',
} as const

export interface CategoryBadge {
  /** 角标文案（1–2 字；图片角标可以为空串） */
  text: string
  /** 角标底色 */
  bgColor: string
  /** 角标文字色 */
  color: string
  /** 图片角标地址；null 表示无图，渲染文字角标 */
  iconUrl: string | null
  /** 角标高度（rpx，合法 16–96，缺省 28） */
  height: number
  /** 角标圆角（rpx，合法 0–48，缺省 4） */
  borderRadius: number
  /** 文字角标字号（rpx），由 height 推导：clamp(round(height * 0.68), 10, 48) */
  fontSize: number
  /** 文字角标左右内边距（rpx），由 height 推导：clamp(round(height * 0.18), 3, 16) */
  paddingX: number
}

/**
 * 服务端下发的角标形状（类目树 `extra.badge`）。
 *
 * 字段来自运营配置，调用方按不可信输入透传给 `resolveCategoryBadge`，
 * 由它逐字段校验（类型、去空白后非空、数值范围）后才渲染。
 * 图片角标可以只下发 `iconUrl`，此时文案与配色都允许缺省。
 */
export interface ServerCategoryBadge {
  /** 角标文案；图片角标可为空 */
  text?: string
  /** 角标底色；图片角标缺省 #00B578 */
  bgColor?: string
  /** 角标文字色；图片角标缺省 #FFFFFF */
  color?: string
  /** 图片角标地址；空串 / 缺省 = 无图，走文字角标（图片角标优先于文字） */
  iconUrl?: string
  /** 角标高度（rpx，16–96），缺省 28 */
  height?: number
  /** 角标圆角（rpx，0–48），缺省 4 */
  borderRadius?: number
}

/** 旧映射条目的静态部分：图片与尺寸不是旧语义的一部分，解析时统一补默认值 */
type LegacyCategoryBadge = Pick<CategoryBadge, 'text' | 'bgColor' | 'color'>

/**
 * 按字符串键查找的视图。
 *
 * 后端 JSON 里 badge_type 可能是字符串 '1' 或数字 1，统一转成字符串再查；
 * 直接对 `CATEGORY_BADGES` 做 `as keyof typeof` 断言会被 TS 判为不安全的收窄。
 */
const BADGE_LOOKUP: Record<string, LegacyCategoryBadge> = CATEGORY_BADGES

/**
 * 旧语义角标文案的词条 key（旧数据兜底用；运营新配置的文案原样下发，不翻译）。
 *
 * 文案在 `resolveLegacyBadge` 调用时再取，避免模块加载时把语言固化；
 * `CATEGORY_BADGES` 保留中文默认值供调试与向后兼容。
 */
const BADGE_TEXT_KEYS: Record<string, string> = {
  1: 'labels.categoryBadgeRecommend',
  2: 'labels.categoryBadgeHot',
}

/** 文字角标字号 ≈ 高度的 68%（28rpx → 19rpx，与改造前的写死值一致） */
const FONT_SIZE_RATIO = 0.68
/** 文字角标左右内边距 ≈ 高度的 18%（28rpx → 5rpx，与改造前的写死值一致） */
const PADDING_X_RATIO = 0.18
/** 推导字号的合法区间（rpx）：高度极端时字号也不能失控 */
const FONT_SIZE_RANGE = { min: 10, max: 48 } as const
/** 推导左右内边距的合法区间（rpx） */
const PADDING_X_RANGE = { min: 3, max: 16 } as const
/** 角标高度合法区间（rpx），越界视为配置脏数据 */
const HEIGHT_RANGE = { min: 16, max: 96 } as const
/** 角标圆角合法区间（rpx），0 表示方角 */
const BORDER_RADIUS_RANGE = { min: 0, max: 48 } as const
/** iconUrl 最大长度：正常对象存储地址远小于此，超长视为脏数据 */
const ICON_URL_MAX_LENGTH = 512

/** 只接受去空白后非空的字符串；其它类型 / 空串一律视为脏值 */
function readText(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

/** 只接受有限数值；非数字 / NaN / Infinity 一律视为脏值 */
function readNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

/**
 * 读 rpx 尺寸：缺省 / 非数字 / 越界都回退默认值，合法值四舍五入到整数。
 *
 * 越界不 clamp 而是回退：这类值多半是配置脏数据，硬压到边界会渲染出与运营
 * 预期不符的角标，回退默认至少保证与改造前观感一致。
 */
function readRpx(value: unknown, fallback: number, range: { min: number, max: number }): number {
  const parsed = readNumber(value)
  if (parsed === null || parsed < range.min || parsed > range.max)
    return fallback
  return Math.round(parsed)
}

/** 读图片角标地址：空白 / 非字符串 / 超长都视为无图 */
function readIconUrl(value: unknown): string | null {
  if (typeof value !== 'string')
    return null
  const url = value.trim()
  return url && url.length <= ICON_URL_MAX_LENGTH ? url : null
}

/** 把数值夹到闭区间内 */
function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

/** 由高度推导文字角标的字号与左右内边距（保留整数，极端高度被区间夹住） */
function deriveTextMetrics(height: number): Pick<CategoryBadge, 'fontSize' | 'paddingX'> {
  return {
    fontSize: clamp(Math.round(height * FONT_SIZE_RATIO), FONT_SIZE_RANGE.min, FONT_SIZE_RANGE.max),
    paddingX: clamp(Math.round(height * PADDING_X_RATIO), PADDING_X_RANGE.min, PADDING_X_RANGE.max),
  }
}

/**
 * 补齐尺寸派生字段后返回完整角标。
 *
 * 所有解析分支（服务端配置 / 旧语义兜底）都经过这里，调用方拿到的字段不会缺，
 * 模板也不需要为「没配置尺寸」写额外兜底。
 */
function buildBadge(
  base: Pick<CategoryBadge, 'text' | 'bgColor' | 'color' | 'iconUrl'>,
  height: number,
  borderRadius: number,
): CategoryBadge {
  return { ...base, height, borderRadius, ...deriveTextMetrics(height) }
}

/**
 * 校验并规整服务端下发的角标。
 *
 * 有合法 iconUrl 时文字可以留空（图片角标），配色缺省用默认值；
 * 没有图时仍要求 text / bgColor / color 三字段齐全，避免运营配置半成品时
 * 左栏渲染出半个空方块。字符串字段一并去掉首尾空白，防止渲染出空白占位。
 */
function readServerBadge(value: unknown): CategoryBadge | null {
  if (!value || typeof value !== 'object' || Array.isArray(value))
    return null
  const record = value as Record<string, unknown>
  const iconUrl = readIconUrl(record.iconUrl)
  const text = readText(record.text)
  const bgColor = readText(record.bgColor)
  const color = readText(record.color)
  if (!iconUrl && (!text || !bgColor || !color))
    return null
  return buildBadge(
    {
      // 图片角标可以没有文案，此时保留空串让调用方统一读字段
      text: text ?? '',
      bgColor: bgColor ?? BADGE_DEFAULTS.bgColor,
      color: color ?? BADGE_DEFAULTS.color,
      iconUrl,
    },
    readRpx(record.height, BADGE_DEFAULTS.height, HEIGHT_RANGE),
    readRpx(record.borderRadius, BADGE_DEFAULTS.borderRadius, BORDER_RADIUS_RANGE),
  )
}

/**
 * 旧语义兜底：badgeType '1'/'2' 命中内置映射，其余（未知 key / 空）返回 null。
 *
 * 旧语义没有图片与尺寸配置，按默认值补齐后返回，调用方拿到的仍是完整角标。
 */
function resolveLegacyBadge(badgeType: string | number | null | undefined): CategoryBadge | null {
  if (badgeType === null || badgeType === undefined)
    return null
  // 后端可能返回字符串 '1' 或数字 1（JSON 序列化差异），统一按字符串取
  const key = String(badgeType).trim()
  if (!key)
    return null
  const badge = BADGE_LOOKUP[key]
  if (!badge)
    return null
  const textKey = BADGE_TEXT_KEYS[key]
  return buildBadge(
    {
      text: textKey ? translate(textKey) : badge.text,
      bgColor: badge.bgColor,
      color: badge.color,
      iconUrl: null,
    },
    BADGE_DEFAULTS.height,
    BADGE_DEFAULTS.borderRadius,
  )
}

/**
 * 解析类目角标；无角标时返回 null。
 *
 * 返回 null 而不是「返回一个空角标」：调用方用 `v-if` 控制渲染，
 * 无角标时**不产生任何节点**，避免左栏多出一层空盒子改变对齐。
 *
 * 判定顺序（前两步的顺序是有意的，避免脏数据渲染出错误角标）：
 * 1. `badgeType` 为 `'0'`（含数字 0）→ null：这是显式的「无角标」语义，
 *    即使服务端误下发 badge 也不渲染（正常不会下发，纯防御）；
 * 2. 服务端 badge 合法 → 采用服务端配置（新角标 key 本地没有映射，只能信服务端）；
 *    「只配了图片」的角标同样合法，渲染端按 `iconUrl` 是否存在分派图片/文字；
 * 3. 回退旧 `CATEGORY_BADGES` 映射（兼容旧后端与未迁移数据）；
 * 4. 服务端未下发合法 badge 且本地映射未命中（`badgeType` 为空 / 未知 key）→ null，
 *    不再猜语义 —— 与 C 端「不造假数据」的一贯口径一致。
 */
export function resolveCategoryBadge(
  badgeType: string | number | null | undefined,
  serverBadge?: unknown,
): CategoryBadge | null {
  // 1. 显式无角标：'0'（含数字 0）优先于服务端下发
  if (badgeType !== null && badgeType !== undefined && String(badgeType).trim() === '0')
    return null

  // 2. 服务端配置优先（本轮以服务端为准）
  const fromServer = readServerBadge(serverBadge)
  if (fromServer)
    return fromServer

  // 3. 旧语义兜底；4. 未命中返回 null
  return resolveLegacyBadge(badgeType)
}
