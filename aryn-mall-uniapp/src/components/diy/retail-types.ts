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
  showSales: boolean
  title: string
}

export interface GoodsRankingProps extends RetailBaseProps {
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
 * 展示当前进行中的共享购物车摘要（项数/人数/估算合计/明细预览），
 * 内容由「谁在看 + 当前靠港」决定，因此没有可手选数据源。
 *
 * 刻意不含「已采/还差 X 件」或进度条配置：现有模型没有目标量，
 * 编一个进度只会是假数据；需要时先加 shared_cart_item.planned_quantity。
 */
export interface ReplenishCardProps extends RetailBaseProps {
  /** 是否展示「按单加购」按钮 */
  showBatchAdd: boolean
  /** 是否展示明细预览行 */
  showPreview: boolean
  /** 展示标题，如「今日补给单」 */
  title: string
}

export interface GoodsWaterfallProps extends RetailBaseProps {
  columns: 2 | 3
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
