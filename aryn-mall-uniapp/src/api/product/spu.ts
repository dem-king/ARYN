import type { AppClearancePriceVO } from '@/utils/clearance-price'

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

/**
 * 批量查询 SKU 有效出清价（商详/购物车展示）。
 *
 * 免登接口：出清价对所有用户一致；只返回未到期且真实低于当前售价的 SKU。
 */
export function getClearanceSkuPrices(skuIds: string[]) {
  const ids = skuIds.filter(Boolean)
  if (!ids.length)
    return Promise.resolve<AppClearancePriceVO | null>(null)
  return alovaInstance.Get<AppClearancePriceVO | null>(
    '/product/app/clearance/prices',
    {
      params: { skuIds: ids.join(',') },
      headers: { skipToken: true },
    },
  )
}

/**
 * 批量查询 SPU 维度有效出清价（商品列表卡片展示）。
 *
 * 列表接口只下发 SPU（无 SKU 明细），且 `clearancePrice` 字段可能是过期/未分层的
 * 原始配置价；这里按 spuIds 由后端用与专区/结算同一套算法算出「每个 SKU 的有效出清价」，
 * 前端再按卡片价聚合最低有效价（`resolveListClearanceDisplay`）。
 *
 * 免登：出清价对所有用户一致。
 */
export function getClearanceSpuPrices(spuIds: string[]) {
  const ids = spuIds.filter(Boolean)
  if (!ids.length)
    return Promise.resolve<AppClearancePriceVO | null>(null)
  return alovaInstance.Get<AppClearancePriceVO | null>(
    '/product/app/clearance/prices',
    {
      params: { spuIds: ids.join(',') },
      headers: { skipToken: true },
    },
  )
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

/** 商品卡营销标签（SPU 级聚合：同 SPU 多 SKU 命中取最低可享价） */
export interface GoodsCardTagItem {
  spuId: string
  /** 新人专享价；null/缺省=未命中（或当前用户无资格） */
  newbiePrice?: number | null
  /** 进行中秒杀价；null/缺省=未命中 */
  seckillPrice?: number | null
  /** 社证文案（月 N+ 人已下单 / 月 N+ 位回头客 / N% 的人回购）；null/缺省=未达门槛不展示 */
  socialProofText?: string | null
  /** 社证类型（repurchase=回购率/repeat=回头客/buyer=月售），与 socialProofText 同生共死；null/缺省=未知，前端兜底图标 */
  socialProofKind?: string | null
  /** 价格声明文案（近N天最低价，服务端已按窗口/门槛判定成文）；null/缺省=不展示 */
  priceTagText?: string | null
}

/** 商详口碑气泡（轮播图浮层，详情接口随 goodsSpu.socialBubble 下发） */
export interface GoodsSocialBubble {
  /** 社证文案（月售N+/回头客N+/回购率N%），与商品卡同源同门槛；null/缺省=未达门槛不展示 */
  socialText?: string | null
  /** 社证类型（repurchase=回购率/repeat=回头客/buyer=月售），与 socialText 同生共死 */
  socialKind?: string | null
  /** 好评率成文（好评率N%，服务端按评价数门槛判定）；null/缺省=不展示 */
  praiseText?: string | null
  /** 好评率数值（一位小数），供本地化兜底；null/缺省=未达门槛 */
  praiseRate?: number | null
  /** 买家头像（近期非匿名评价快照，≤3个）；空=纯文字胶囊 */
  avatars?: string[] | null
}

/**
 * 批量查询商品卡营销标签（新人价/秒杀价）。
 *
 * 可选登录（**不传 skipToken**）：登录用户要带 token，后端据此判定新人资格
 * （不合格不下发）；未登录时中间件本就不发 token，后端按潜在新人口径下发。
 */
export function getCardTags(spuIds: string[]) {
  const ids = spuIds.filter(Boolean)
  if (!ids.length)
    return Promise.resolve<GoodsCardTagItem[]>([])
  return alovaInstance.Get<GoodsCardTagItem[]>('/product/app/goodsspu/card-tags', {
    params: { spuIds: ids.join(',') },
  })
}
