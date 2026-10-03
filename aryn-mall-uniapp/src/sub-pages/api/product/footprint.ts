import { alovaInstance } from '@/api/core/instance'

// 分页列表
export function getPage(params: object) {
  return alovaInstance.Get<any>('/product/app/footprint/page', {
    params,
  })
}
