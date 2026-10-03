import { alovaInstance } from '@/api/core/instance'

import { filterActiveCategoryTree } from '@/utils/category-tree'

/**
 * 获取树结构商品类目列表（全量，含停用）。
 *
 * 层级解析类消费方（goods-list `resolveCategoryId`）必须用这份：
 * 停用分类的历史链接也要能正确解析出一级/二级，过滤会把一级误判成二级。
 */
export function getTree() {
  return alovaInstance.Get<any>('/product/app/goodscategory/tree', {
    headers: {
      skipToken: true,
    },
  })
}

/**
 * 只含启用中的分类树（一级/二级均过滤停用），导航展示位专用
 * （分类页、金刚区 diy-category-nav）。后端 tree 接口不过滤 status，
 * 停用项随 extra 下发，这里统一做客户端过滤。
 */
export async function getActiveTree() {
  const tree = (await getTree()) ?? []
  return filterActiveCategoryTree<any>(tree)
}
