import { alovaInstance } from '@/api/core/instance'

/** 搜索结果商品项 */
export interface SearchProductItem {
  id: string
  name: string
  spuUrls: string[]
  salesPrice: number
  salesVolume: number
  categoryName: string
  categoryId: string
  subTitle: string
}

/** 分面筛选 — 分类统计项 */
export interface FacetCategory {
  id: string
  name: string
  count: number
}

/** 分面筛选 — 价格区间统计项 */
export interface FacetPriceRange {
  label: string
  min: number
  max: number
  count: number
}

/** 搜索结果 */
export interface SearchResult {
  records: SearchProductItem[]
  total: number
  facets: {
    categories: FacetCategory[]
    priceRanges: FacetPriceRange[]
  }
}

/** 搜索建议项 */
export interface SuggestItem {
  text: string
  type: 'keyword' | 'category' | 'product'
}

/** 搜索请求参数 */
export interface SearchParams {
  q?: string
  category?: string
  price_range?: string
  sort?: string
  page?: number
  limit?: number
}

/**
 * Meilisearch 商品搜索
 */
export function searchProducts(params: SearchParams) {
  return alovaInstance.Get<SearchResult>('/product/app/product/search', {
    params,
    headers: {
      skipToken: true,
    },
  })
}

/**
 * 搜索联想词
 */
export function searchSuggest(q: string) {
  return alovaInstance.Get<SuggestItem[]>('/product/app/product/suggest', {
    params: { q },
    headers: {
      skipToken: true,
    },
  })
}