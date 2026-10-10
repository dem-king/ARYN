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
  showCountdown: boolean
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
  showContact: boolean
  showDescription: boolean
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
  /** 是否展示活动倒计时 */
  showCountdown: boolean
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
  discountType: number
  discountValue: number
  endTime: string
  goodsList: DiscountGoodsItem[]
  scope: number
  startTime: string
  status: number
}

export interface GoodsWaterfallProps extends RetailBaseProps {
  columns: 2 | 3
  /** 是否展示划线原价（仅当商品原价高于售价时可见） */
  showOriginalPrice: boolean
  showPrice: boolean
  showSales: boolean
  title: string
}

export interface CouponComboProps extends RetailBaseProps {
  showReceiveBtn: boolean
  showThreshold: boolean
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

export interface BottomNavItem {
  iconUrl: string
  id: string
  link: DecorationLink
  text: string
}

export interface BottomNavProps {
  activeColor: string
  backgroundColor: string
  commonStyle: RetailCommonStyle
  items: BottomNavItem[]
  textColor: string
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
