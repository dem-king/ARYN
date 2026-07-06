import { alovaInstance } from '@/api/core/instance'

// 获取品牌列表
export function getList() {
  return alovaInstance.Get<any>('/product/app/brand/list', {
    headers: {
      skipToken: true,
    },
  })
}

// 根据ID获取品牌详情
export function getById(id: string) {
  return alovaInstance.Get<any>(`/product/app/brand/${id}`, {
    headers: {
      skipToken: true,
    },
  })
}