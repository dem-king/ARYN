/**
 * 购物车 tabBar 角标（纯函数，便于单测）。
 *
 * 购物车数量在 shoppingCartStore 全局维护（登录后、分类页 onShow、购物车页
 * 变更、加购与下单后都会刷新），这里只负责把它换算成原生 tabBar 角标需要的
 * 两件事：购物车 tab 的位置、角标文案。uni.setTabBarBadge / removeTabBarBadge
 * 的调用与失败重试在 composables/useTabBarBadge。
 */

/** 购物车 tab 的路由路径，与 pages.config.ts 生成的 pages.json tabBar 项一致 */
export const CART_TAB_PATH = 'pages/user/shopping-cart/index'

/** 角标数字上限：超过后不再涨位数，显示「99+」 */
export const CART_BADGE_LIMIT = 99

export interface TabBarItemLike {
  pagePath?: string
}

/**
 * 由 tabBar 配置推导购物车 tab 的下标。
 *
 * 不写死数字：tab 增删或调序时角标自动跟到正确的项上；找不到（配置改动
 * 漏了购物车 tab）返回 -1，由调用方整体停用角标，而不是打到错误的 tab 上。
 */
export function resolveCartTabIndex(list: TabBarItemLike[] = []): number {
  return list.findIndex(item => item?.pagePath === CART_TAB_PATH)
}

/**
 * 角标文案口径：与分类页悬浮按钮的 wd-badge 同源（cartCount）。
 * 0/负数/非法值 → null（表示无角标）；超过上限 → 「99+」。
 */
export function formatCartBadgeText(count: number): string | null {
  if (!Number.isFinite(count) || count <= 0) {
    return null
  }
  const safeCount = Math.floor(count)
  return safeCount > CART_BADGE_LIMIT ? `${CART_BADGE_LIMIT}+` : String(safeCount)
}
