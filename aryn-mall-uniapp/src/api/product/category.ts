import { alovaInstance } from '@/api/core/instance'

// 获取树结构商品类目列表
export function getTree() {
  return alovaInstance.Get<any>('/product/app/goodscategory/tree', {
    headers: {
      skipToken: true,
    },
  })
}
