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

// 通过id查询商品
export function getById(id: string) {
  return alovaInstance.Get<any>(`/product/app/goodsspu/${id}`, {
    headers: { skipToken: true },
  })
}

// 通过ids查询商品列表
export function getByIds(ids: string[]) {
  return alovaInstance.Get<any>(`/product/app/goodsspu/list/${ids}`, {
    headers: { skipToken: true },
  })
}

// 获取热搜商品 Top10
export function getTop10HotSearchGoods() {
  return alovaInstance.Get<any>('/product/app/goodsspu/hot-search/top10', {
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
