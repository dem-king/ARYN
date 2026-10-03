import { alovaInstance } from '@/api/core/instance'

/**
 * 全量启用品牌列表。
 *
 * C 端**不应**再用它渲染分类页/搜索页的品牌筛选条：它与当前分类、关键词无关，
 * 而品牌在本租户是稀疏属性（272 件在售商品仅 62 件有品牌），水果等 7 个一级分类下
 * 全部商品无品牌 —— 用全量列表会出现「点了必然为空」的选项。筛选条请用 getFilterList。
 */
export function getList() {
  return alovaInstance.Get<any>('/product/app/goodsbrand/list', {
    headers: {
      skipToken: true,
    },
  })
}

/**
 * 品牌筛选项：只返回在当前查询条件下确有在售商品的品牌（含商品数）。
 *
 * 入参字段与 `/product/app/goodsspu/page` 同名同义，服务端按同一套谓词聚合，
 * 因此返回的每个品牌点进去都必然有商品。
 */
export function getFilterList(params: {
  categoryFirstId?: string
  categorySecondId?: string
  name?: string
}) {
  return alovaInstance.Get<any>('/product/app/goodsbrand/filter-list', {
    params,
    headers: {
      skipToken: true,
    },
  })
}
