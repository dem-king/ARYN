import type { DecorationLink } from './schema/types'

export type RetailCommonStyle = Record<string, unknown>
export type RetailFallbackStrategy = 'hide' | 'placeholder'

export interface RetailDataSource {
  /** 数据缓存时长（秒），0 或缺省不缓存 */
  cacheTtl?: number
  categoryId?: string
  metric?: string
  mode: 'automatic' | 'current-tenant' | 'manual' | 'ranking' | 'rule'
  sort?: string
  targetIds?: string[]
}

export interface RetailBaseProps {
  commonStyle: RetailCommonStyle
  count: number
  dataSource: RetailDataSource
  emptyStrategy: RetailFallbackStrategy
  invalidStrategy: RetailFallbackStrategy
}

export interface GoodsGroupProps extends RetailBaseProps {
  columns: 2 | 3
  /** 是否展示划线原价（仅当商品原价高于售价时可见） */
  showOriginalPrice: boolean
  showSales: boolean
  title: string
}

export interface GoodsRankingProps extends RetailBaseProps {
  /** 是否展示划线原价（仅当商品原价高于售价时可见） */
  showOriginalPrice: boolean
  showRankNumber: boolean
  title: string
}

export interface LimitedActivityProps extends RetailBaseProps {
  /** 展示样式：list=单列横排（默认，匹配 1 活动=1 商品）；grid=双列货架式 */
  layout: 'grid' | 'list'
  showCountdown: boolean
  /** 楼层头副标题，空串使用模块默认文案 */
  subtitle: string
  title: string
}

export interface CountdownProps extends RetailBaseProps {
  completedText: string
  targetTime: string
  title: string
}

export interface MarketingEntry {
  iconUrl: string
  id: string
  link: DecorationLink
  title: string
}

export interface MarketingEntryProps extends RetailBaseProps {
  columns: 4 | 5
  entries: MarketingEntry[]
}

export interface ShopInfoProps extends RetailBaseProps {
  /** 悬浮压图：顶部外边距再上移 16px（=32rpx），把店铺头卡片叠到上方 banner 上 */
  liftOverBanner: boolean
  showContact: boolean
  showDescription: boolean
}

/**
 * 活动入口条的单张小卡（已组装好，渲染侧只做展示）。
 *
 * 与纯图标宫格的 `MarketingEntry` 的区别：这里带缩略图/钩子/角标，
 * 表达的是「我有活动在进行」而不是「这里有个频道」。
 */
export interface ActivityStripEntry {
  /** 卡片类型：内置三类固定跳对应会场，custom 走 link */
  key: 'custom' | 'discount' | 'group-buy' | 'seckill'
  /** 活动名，如「限时秒杀」；缺图时取首字做色块兜底 */
  title: string
  /** 钩子文案，如「午间场·整点开抢」；空则不渲染该行 */
  hook: string
  /** 角标文字，如「抢」「省¥4」「9.5折」；空则不渲染角标 */
  badge: string
  /** 缩略图，空或远程加载失败走首字色块兜底 */
  imageUrl: string
  /** 跳转：内置三类的 link 在自动模式下填固定会场路由，统一走 followDecorationLink */
  link: DecorationLink
}

/**
 * 活动入口条数据源。
 *
 * · automatic：并行取「进行中的秒杀/拼团/折扣各一条」自动组装，钩子与图随活动刷新，
 *   入口固定指向会场聚合页，因此活动轮换后入口不会失效（本期默认）；
 * · manual：运营手配 `ActivityStripEntry[]`，不实时拉活动，可放领券中心等任意装修链接。
 *   管理端配置面板按需求 R7 后置，本期 C 端先支持读取。
 */
export type ActivityStripDataSource
  = | { mode: 'automatic' }
    | { entries: ActivityStripEntry[], mode: 'manual' }

/**
 * 首页活动入口条（金刚区正下方一排等宽活动小卡，盒马式）。
 *
 * 首页不再让秒杀/拼团/折扣三个通栏楼层纵向堆叠：这里只做「入口」，
 * 完整促销氛围（场次、倒计时、抢购进度）由各会场页承接。
 * 某类没有进行中活动时该卡隐藏、剩余卡按实际数量等宽重排；
 * 三类全无时整条不渲染（默认 emptyStrategy=hide），不留空白占位。
 *
 * 不再提供「每行列数 / 展示数量」：自动模式最多三类活动各一张，
 * 手配卡片数就是运营添加的数量；渲染侧按实际卡数排布（一行最多三张）。
 */
export interface ActivityStripProps
  extends Omit<RetailBaseProps, 'count' | 'dataSource'> {
  /** 自动取数（本期默认）或运营手配卡片 */
  dataSource: ActivityStripDataSource
}

export const retailCommonStyle: RetailCommonStyle = {
  bgColorDirection: 'to right',
  bgEndColor: '',
  bgPicUrl: '',
  bgStartColor: '#ffffff',
  styleBottomMargin: 10,
  styleBottomPadding: 12,
  styleLbRadius: 0,
  styleLeftMargin: 10,
  styleLeftPadding: 12,
  styleLtRadius: 0,
  styleRbRadius: 0,
  styleRightMargin: 10,
  styleRightPadding: 12,
  styleRtRadius: 0,
  styleTopMargin: 10,
  styleTopPadding: 12,
}

/**
 * 船舶工作台。
 *
 * 展示当前登录用户此刻的船舶与靠港，内容由「谁在看」决定，
 * 因此没有可手选的数据源；运营只能控制「常购」入口与通用样式。
 */
export interface ShipWorkbenchProps extends RetailBaseProps {
  showFrequent: boolean
}

/**
 * 补给单卡片（首页「今日补给单」）。
 *
 * 展示当前进行中的共享购物车摘要（项数/人数/预估合计/明细预览），
 * 内容由「谁在看 + 当前靠港」决定，因此没有可手选数据源。
 *
 * 刻意不含「已采/还差 X 件」或进度条配置：提单只是一份需求清单，
 * 采没采、采到什么程度由线下沟通，系统里没有这个事实来源。
 */
export interface ReplenishCardProps extends RetailBaseProps {
  /** 是否展示「按单加购」按钮 */
  showBatchAdd: boolean
  /** 是否展示明细预览行 */
  showPreview: boolean
  /** 展示标题，如「今日补给单」 */
  title: string
}

/**
 * 秒杀会场组件。
 *
 * 秒杀与拼团的数据形态不同：拼团是「一活动一商品」的平铺列表，
 * 秒杀是「活动 → 场次 → 商品」两层结构，故单独建类型而非复用限时活动。
 */
export interface SeckillProps extends RetailBaseProps {
  /** 是否展示场次倒计时 */
  showCountdown: boolean
  /** 是否展示已售进度条 */
  showProgress: boolean
  /** 楼层头副标题，空串使用模块默认文案 */
  subtitle: string
  title: string
}

/** 秒杀场次商品项（已归一化） */
export interface SeckillGoodsItem {
  goodsImage: string
  goodsName: string
  /** 每人限购数，0 或缺失表示不限购 */
  limitPerUser: number
  originalPrice: number
  /** 已售件数 */
  soldCount: number
  seckillPrice: number
  skuId: string
  spuId: string
  /** 剩余库存；-1 表示后端未下发，此时不渲染进度 */
  remainingStock: number
  /** 秒杀总库存 */
  seckillStock: number
}

/** 秒杀场次（已归一化） */
export interface SeckillSessionItem {
  countdown: number
  endTime: string
  goodsList: SeckillGoodsItem[]
  sessionId: string
  sessionName: string
  startTime: string
  /** 0未开始 1进行中 2已结束 */
  status: number
}

/**
 * 折扣会场组件。
 *
 * 与秒杀同样按活动分组，但只有一层（活动 → 商品），没有场次概念。
 */
export interface DiscountProps extends RetailBaseProps {
  /** 每个活动最多展示的商品数（防止楼层过高） */
  goodsLimit: number
  /** 是否展示活动倒计时 */
  showCountdown: boolean
  /** 楼层头副标题，空串使用模块默认文案 */
  subtitle: string
  title: string
}

/** 折扣活动商品项（已归一化） */
export interface DiscountGoodsItem {
  discountPrice: number
  goodsImage: string
  goodsName: string
  originalPrice: number
  skuId: string
  spuId: string
}

/** 折扣活动（已归一化） */
export interface DiscountActivityItem {
  activityId: string
  activityName: string
  countdown: number
  /**
   * 入口卡缩略图（取数层已按优先级解析好）。
   *
   * 指定商品活动取首件商品图；全场活动（scope=1）没有商品清单，
   * 由 `loadDiscounts` 补一张真实商品图作代表（全店销量 Top1 商品，确实参与全场折扣，
   * 不伪造数据）。仍取不到图时空串，渲染侧回落首字色块。
   */
  coverImage: string
  discountType: number
  discountValue: number
  endTime: string
  goodsList: DiscountGoodsItem[]
  scope: number
  startTime: string
  status: number
}

/**
 * 新人专享楼层。
 *
 * 数据源固定为「当前进行中的新人价活动」，无需运营手配活动 ID；
 * 仍继承 RetailBaseProps 以复用取数、空态与样式机制（dataSource 不参与取数）。
 */
export interface NewbieFloorProps extends RetailBaseProps {
  /** 是否展示活动结束倒计时 */
  showCountdown: boolean
  /** 是否展示「新人」角标 */
  showTag: boolean
  /**
   * 自动前进（分页式：一次一张卡）。
   * 字段名沿用 autoScroll 以兼容已发布页面的存量数据；
   * 语义已由「连续跑马灯」改为「分页步进 + 触摸暂停」。
   */
  autoScroll: boolean
  /** 每张卡片的停留时长（毫秒） */
  interval: number
  /** 区块主色（6 位 hex）用于淡色底纹，空串跟随页面主题色 */
  mainColor: string
  title: string
}

/** 新人价商品项（已归一化） */
export interface NewbieGoodsItem {
  goodsImage: string
  goodsName: string
  /** 新人专享价 */
  newbiePrice: number
  /** 划线原价（当前售价） */
  originalPrice: number
  skuId: string
  spuId: string
}

/** 新人专享活动（已归一化，单活动） */
export interface NewbieFloorItem {
  activityId: string
  activityName: string
  endTime: string
  /** 每人每 SKU 限购件数（固定 1） */
  limitPerUser: number
  /** 注册时效（天），0 表示不限 */
  registerWithinDays: number
  goodsList: NewbieGoodsItem[]
}

export interface GoodsWaterfallProps extends RetailBaseProps {
  columns: 2 | 3
  /** 是否展示划线原价（仅当商品原价高于售价时可见） */
  showOriginalPrice: boolean
  showPrice: boolean
  showSales: boolean
  title: string
}

/**
 * 多 Tab 商品流中的单个 Tab。
 *
 * 每个 Tab 拥有独立数据源与分页状态：首次切换才取数，切回直接复用缓存，
 * 触底加载只作用于当前 Tab。数据源语义与其它零售组件一致：
 * automatic 按分类/排序取数，manual 只展示运营手选商品。
 */
export interface GoodsTabConfig {
  dataSource: RetailDataSource
  id: string
  title: string
}

export interface GoodsTabsProps {
  columns: 2 | 3
  commonStyle: RetailCommonStyle
  count: number
  emptyStrategy: RetailFallbackStrategy
  invalidStrategy: RetailFallbackStrategy
  /** 是否展示划线原价（仅当商品原价高于售价时可见） */
  showOriginalPrice: boolean
  showPrice: boolean
  showSales: boolean
  /** Tab 栏是否吸顶（随页面滚动停在导航栏下方） */
  stickyTabs: boolean
  tabs: GoodsTabConfig[]
}

export interface MemberBenefitEntry {
  description: string
  iconUrl: string
  id: string
  link: DecorationLink
  title: string
}

export interface MemberBenefitsProps extends RetailBaseProps {
  entries: MemberBenefitEntry[]
  title: string
}

export interface ServicePromiseItem {
  description: string
  iconUrl: string
  id: string
  title: string
}

export interface ServicePromiseProps {
  commonStyle: RetailCommonStyle
  items: ServicePromiseItem[]
  title: string
}

export interface VideoLiveProps {
  commonStyle: RetailCommonStyle
  coverUrl: string
  liveId: string
  mode: 'live' | 'video'
  title: string
  videoUrl: string
}

/**
 * 商品推荐（商详「看了又看」）。
 *
 * automatic 模式取「当前商品同分类销量 Top N」（分类解析自 goodsId，
 * 运营也可通过 dataSource.categoryId 固定分类；商详上下文缺失时回落全站
 * 销量榜）；manual 模式为手选商品。automatic 模式下 targetIds 同时作为
 * 补位来源：列表不足 count 时按顺序追加未重复的手选商品。
 */
export interface GoodsRecommendProps extends RetailBaseProps {
  showOriginalPrice: boolean
  showPrice: boolean
  title: string
}

export interface ImageCubeItem {
  id: string
  link: DecorationLink
  url: string
}

/**
 * 图片魔方：布局决定格数（items 长度建议与之一致），纯静态图片 + 链接。
 * 格位尺寸两端同源（1rpx = 0.5px）：大格 376rpx、小格 184rpx、三分格 120rpx。
 */
export interface ImageCubeProps {
  commonStyle: RetailCommonStyle
  items: ImageCubeItem[]
  layout: '1' | '1+2' | '1+3' | '2h' | '2v' | '4'
}
