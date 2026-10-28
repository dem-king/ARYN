export const DECORATION_SCHEMA_VERSION = 2 as const

export type DecorationLinkType
  = | 'activity'
    | 'category'
    | 'coupon'
    | 'custom'
    | 'customer-service'
    | 'goods'
    | 'mini-program'
    | 'page'

export interface DecorationLink {
  params: Record<string, string>
  path: string
  targetId?: string
  type: DecorationLinkType
}

export interface DecorationComponent<TProps extends Record<string, unknown> = Record<string, unknown>> {
  id: string
  props: TProps
  type: string
  version: number
}

export type NavigationScrollMode = 'fade' | 'none'
export type NavigationScrollSurface = 'custom' | 'frosted' | 'theme'

/**
 * 导航栏滚动渐显（沉浸式导航）配置。
 *
 * 缺省 `mode='none'` 表示保持现状（存量页面零变化）；所有字段由
 * `normalizeNavigationScrollEffect()` 归一化，渲染端不信任草稿存值。
 * 与管理端 NavigationSettings.scrollEffect 同构。
 */
export interface NavigationScrollEffect {
  mode: NavigationScrollMode
  /** 目标底色：磨砂白 / 商城主题导航色 / 自定义色 */
  surface: NavigationScrollSurface
  /** surface='custom' 时生效；非法值回落磨砂白 */
  surfaceColor: string
  /** 渐显距离 px（40–400，缺省 140） */
  distance: number
  /** 目标态是否毛玻璃（仅 surface='frosted' 生效） */
  blur: boolean
}

export interface PageSettings {
  backgroundColor: string
  backgroundImage: string
  enablePullDownRefresh: boolean
  /**
   * 是否跟随商城默认主题配色（页面背景/导航），缺省视为跟随；
   * false 表示管理端「页面设置」关闭了跟随，页面自存配色直接生效（自定义配色）。
   */
  followMallTheme?: boolean
  navigation: {
    backgroundColor: string
    /**
     * 导航栏背景来源：
     *   · 'custom'（缺省）—— 用 backgroundColor / 商城主题导航色；
     *   · 'followFirstSection' —— 跟随首个有内容的区块：渐变取顶边起始色，
     *     左右（to right）渐变整段复用；文字颜色默认按背景自动取黑/白。
     * 与管理端 NavigationSettings 同构，由 preview-parity 守卫解析口径。
     */
    backgroundMode?: 'custom' | 'followFirstSection'
    /** 跟随首个区块时是否自动取对比文字色；false 时用 textColor */
    textColorAuto?: boolean
    textColor: string
    title: string
    visible: boolean
    /** 滚动渐显（沉浸式导航）配置；缺省 undefined 视为关闭 */
    scrollEffect?: NavigationScrollEffect
  }
  share: {
    description: string
    imageUrl: string
    title: string
  }
}

export interface DecorationDocument {
  components: DecorationComponent[]
  page: PageSettings
  schemaVersion: typeof DECORATION_SCHEMA_VERSION
  sections: DecorationSection[]
  /**
   * 页面引用主题（发布时固化的 themeSnapshot）的品牌主色/辅色。
   * 与商城默认主题（mallThemeStore）两级并存：页面级在 diy 内容区
   * 以 CSS 变量下放并覆盖全局，未引用主题时为空、回落全局主题。
   */
  themePrimaryColor?: string
  themeSecondaryColor?: string
}

/**
 * Schema v3：管理端发布契约（页面 -> 区块 -> 组件）。
 * 移动端只消费已发布快照：components 为拍平后的渲染列表，
 * sections 保留区块背景/间距/横滑/吸顶/条件显示等包装信息。
 * terminalOverrides/themeRef 为后续多终端与主题能力的预留字段。
 */
export const DECORATION_SCHEMA_VERSION_V3 = 3 as const

export type DecorationTerminal = 'admin' | 'h5' | 'weapp'

/**
 * 区块显示条件：向后兼容字符串简写（always/guest/login），
 * 同时支持对象组合条件（SectionConditionGroup），与管理后台保持一致。
 */
export type SectionCondition = 'always' | 'guest' | 'login' | SectionConditionGroup

export interface SectionConditionGroup {
  logic: 'and' | 'or'
  rules: SectionConditionRule[]
}

export type SectionConditionRule
  = | { type: 'login' }
    | { type: 'guest' }
    | { type: 'memberLevel', memberLevelIds: string[] }
    | { type: 'userTag', userTagIds: string[] }
    | { type: 'timeRange', startTime: string, endTime: string }

/**
 * 区块背景渐变。与组件通用样式（commonStyle 的 bgColorDirection /
 * bgStartColor / bgEndColor）同语义，供运营把区块底色做成渐变色带。
 * 优先级：backgroundImage（背景图）> backgroundGradient > backgroundColor。
 */
export interface SectionBackgroundGradient {
  /** CSS 渐变方向，只接受白名单值：'to right' | 'to bottom' | '135deg' | '225deg' */
  direction: string
  endColor: string
  startColor: string
}

/**
 * 区块容器样式。长度单位一律为 **px**，与组件级 commonStyle 以及
 * 管理端画布（375px 手机壳）保持同一口径；早期 paddingY 在 C 端被当作
 * rpx 渲染、管理端按 px 编辑，同一数值两端相差 2 倍，已统一为 px。
 */
export interface SectionStyle {
  backgroundColor: string
  /** 背景渐变；起止色都为空视为未启用（回退 backgroundColor） */
  backgroundGradient?: SectionBackgroundGradient
  backgroundImage: string
  condition: SectionCondition
  horizontalScroll: boolean
  /** 左右外边距：让区块脱离通栏、成为一张卡片（配合 radius 使用） */
  marginX: number
  /** 上下外边距：撑开区块之间的距离 */
  marginY: number
  /** 左右内边距：让区块内的组件不贴边，避免盖住圆角 */
  paddingX: number
  paddingY: number
  /** 圆角：0 表示通栏直角区块 */
  radius: number
  sticky: boolean
}

export interface DecorationSection {
  components: DecorationComponent[]
  id: string
  name?: string
  style: SectionStyle
  type: string
}

export interface DecorationDocumentV3 {
  analytics?: { campaignId?: string, enabled: boolean }
  page: PageSettings
  schemaVersion: typeof DECORATION_SCHEMA_VERSION_V3
  sections: DecorationSection[]
  terminalOverrides?: Partial<Record<DecorationTerminal, Partial<PageSettings>>>
  themeRef?: string
}
