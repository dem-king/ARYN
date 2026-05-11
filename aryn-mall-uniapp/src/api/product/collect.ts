import { alovaInstance } from '@/api/core/instance'

// 分页列表
export function getPage(params: object) {
  return alovaInstance.Get<any>('/product/app/collect/page', {
    params,
  })
}
// 收藏
export function addObj(data: object) {
  return alovaInstance.Post<any>('/product/app/collect', data)
}

// 取消收藏
export function deleteObj(id: string) {
  return alovaInstance.Delete<any>(`/product/app/collect/${id}`)
}
// 查询用户收藏数量
export function getCount() {
  return alovaInstance.Get<any>('/product/app/collect/count')
}

// 批量收藏
export function saveBatch(data: object) {
  return alovaInstance.Post<any>('/product/app/collect/save-batch', data)
}
