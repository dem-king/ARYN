/**
 * 类目树停用过滤（纯函数，便于单测）。
 *
 * 后端 `/product/app/goodscategory/tree` 不过滤 `status`，停用（status='1'）
 * 的类目也会随 extra 下发。导航展示位（分类页、金刚区）只应呈现启用中的类目，
 * 这里做客户端过滤：一级、二级各滤一层，其余字段原样保留。
 *
 * 与 goods-list `resolveCategoryId` 的分工：那是「历史 categoryId → 层级」的
 * 解析，必须用全量树——停用一级分类的老链接若被滤掉，会回退成「当二级」的
 * 历史行为，categorySecondId 传一级 id 查出来恒空。层级解析不过滤、导航展示过滤，
 * 两边是刻意的双轨。
 *
 * 与 [[category-icon]]、[[category-badge]] 的分工：那两个管「启用类目怎么展示」，
 * 这个管「哪些类目有资格出现在导航上」。
 */

/** 停用状态值，与后端 `goods_category.status` 的 '1' 对应 */
const CATEGORY_STATUS_DISABLED = '1'

/** 树节点的最小契约：tree 接口把 extra（status/sort/badgeType…）拍平在节点上 */
export interface CategoryTreeNode {
  children?: CategoryTreeNode[]
  status?: string
  [key: string]: unknown
}

export function filterActiveCategoryTree<T extends CategoryTreeNode>(tree: T[]): T[] {
  return tree
    .filter(node => node.status !== CATEGORY_STATUS_DISABLED)
    .map(node => ({
      ...node,
      children: (node.children ?? []).filter(
        (child: CategoryTreeNode) => child.status !== CATEGORY_STATUS_DISABLED,
      ),
    }))
}

/**
 * 在类目树中定位目标分类，翻译成左栏/侧栏的选中下标（纯函数，便于单测）。
 *
 * 商品列表页「全部分类」入口跳分类页时 switchTab 带不了 query，目标分类经
 * categoryLocateStore 中转，这里负责 id → 下标：
 *   · 优先按二级 id 命中（同时锁住一级与二级）；
 *   · 二级未携带或未命中时退回按一级 id 命中，二级落到该一级下第一项；
 *   · 都未命中（分类已删 / 停用被 getActiveTree 过滤）返回 -1，调用方保持默认选中。
 * id 统一按字符串比较：树上节点的 id 可能是 number 也可能是 string。
 */
export function locateCategoryInTree<T extends CategoryTreeNode>(
  tree: T[],
  target: { categoryFirstId?: string, categorySecondId?: string },
): { firstIndex: number, secondIndex: number } {
  const secondId = target.categorySecondId ? String(target.categorySecondId) : ''
  const firstId = target.categoryFirstId ? String(target.categoryFirstId) : ''
  if (secondId) {
    for (let firstIndex = 0; firstIndex < tree.length; firstIndex++) {
      const children = tree[firstIndex]?.children ?? []
      const secondIndex = children.findIndex(child => String(child.id) === secondId)
      if (secondIndex >= 0)
        return { firstIndex, secondIndex }
    }
  }
  if (firstId) {
    const firstIndex = tree.findIndex(node => String(node.id) === firstId)
    if (firstIndex >= 0)
      return { firstIndex, secondIndex: -1 }
  }
  return { firstIndex: -1, secondIndex: -1 }
}
