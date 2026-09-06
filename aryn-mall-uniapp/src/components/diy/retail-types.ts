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
