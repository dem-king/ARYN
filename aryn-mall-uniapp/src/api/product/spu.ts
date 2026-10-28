import { alovaInstance } from '@/api/core/instance'

// 获取商品列表
export function getPage(params: object) {
  return alovaInstance.Get<any>('/product/app/goodsspu/page', {
    params,
    headers: {
      skipToken: true,
    },
  })
}

// 推荐流（商详/购物车/个人中心/订单页）：同分类优先、排除已选商品；
// 不跳过登录态——登录用户在无显式上下文时按近期浏览足迹个性化
export function getRecommendPage(params: object) {
  return alovaInstance.Get<any>('/product/app/goodsspu/recommend', {
    params,
  })
}

// 通过id查询商品
// 必须携带登录态（不传 skipToken）：后端据此写入浏览足迹并回填收藏状态，
// 免登请求拿不到用户，足迹会一直是空的。
export function getById(id: string) {
  return alovaInstance.Get<any>(`/product/app/goodsspu/${id}`)
}

// 通过ids查询商品列表
export function getByIds(ids: string[]) {
  return alovaInstance.Get<any>(`/product/app/goodsspu/list/${ids}`, {
    headers: { skipToken: true },
  })
}

/**
 * 榜单商品精简出参（热搜榜 / 热销榜共用）。
 *
 * 后端 `AppGoodsRankVO` 是最窄白名单：只有 id/name/subTitle/picUrl，
 * 不含价格、库存、销量——榜单只表达名次，任何绝对销量数字都不该出现在端上
 * （口径见 `src/utils/stock-display.ts`）。
 */
export interface RankGoodsItem {
  id: string
  name: string
  subTitle?: string
  picUrl?: string
}

// 获取热搜商品 Top10（真实浏览足迹：当前租户、近 30 天、在售）
export function getTop10HotSearchGoods() {
  return alovaInstance.Get<RankGoodsItem[]>('/product/app/goodsspu/hot-search/top10', {
    headers: { skipToken: true },
  })
}

// 获取热销商品 Top10（真实有效订单聚合：当前租户、近 90 天、在售）
export function getSalesRankGoods() {
  return alovaInstance.Get<RankGoodsItem[]>('/product/app/goodsspu/sales-rank/top10', {
    headers: { skipToken: true },
  })
}

// 搜索联想词（在售商品名，前缀优先，最多10条；免登）
export function getSuggestWords(keyword: string) {
  return alovaInstance.Get<string[]>('/product/app/goodsspu/suggest', {
    params: { keyword },
    headers: { skipToken: true },
  })
}

// 快捷加购信息（列表页加购按钮按需查询 SKU/库存/MOQ，不写浏览足迹）
export function getQuickCartInfo(id: string) {
  return alovaInstance.Get<any>(`/product/app/goodsspu/quick-cart/${id}`, {
    headers: { skipToken: true },
  })
}
