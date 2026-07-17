import type { DecorationLink } from './schema/types'

export type RetailCommonStyle = Record<string, unknown>
export type RetailFallbackStrategy = 'hide' | 'placeholder'

export interface RetailDataSource {
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
