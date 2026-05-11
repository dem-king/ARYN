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
  return alovaInstance.Get<any>(`/product/app/goodsspu/${id}`)
}

// 通过ids查询商品列表
export function getByIds(ids: string[]) {
  return alovaInstance.Get<any>(`/product/app/goodsspu/list/${ids}`)
}

// 获取热搜商品 Top10
export function getTop10HotSearchGoods() {
  return alovaInstance.Get<any>('/product/app/goodsspu/hot-search/top10')
}
