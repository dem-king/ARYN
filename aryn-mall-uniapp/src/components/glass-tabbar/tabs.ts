export type GlassTabKey = 'home' | 'category' | 'cart' | 'user'

export interface GlassTabItem {
  key: GlassTabKey
  /** 页面路由（无前导斜杠，与 getCurrentPages() 的 route 同口径） */
  route: string
  /** switchTab 目标（带前导斜杠） */
  path: string
  labelKey: string
  icon: string
  activeIcon: string
}

/**
 * 自绘玻璃底栏的 tab 清单：与 pages.config.ts 的 tabBar.list 一一对应。
 *
 * 原生 tabBar 只支持纯色底色、不能背景模糊，小程序端改由自绘组件渲染；
 * 图标沿用原生配置里的两套 PNG（未选中灰描边 / 选中品牌渐变），不另做资源。
 */
export const GLASS_TAB_ITEMS: GlassTabItem[] = [
  {
    key: 'home',
    route: 'pages/home/index',
    path: '/pages/home/index',
    labelKey: 'tabbar.home',
    icon: '/static/tabbar/home.png',
    activeIcon: '/static/tabbar/home-selected.png',
  },
  {
    key: 'category',
    route: 'pages/product/category/index',
    path: '/pages/product/category/index',
    labelKey: 'tabbar.category',
    icon: '/static/tabbar/category.png',
    activeIcon: '/static/tabbar/category-selected.png',
  },
  {
    key: 'cart',
    route: 'pages/user/shopping-cart/index',
    path: '/pages/user/shopping-cart/index',
    labelKey: 'tabbar.cart',
    icon: '/static/tabbar/cart.png',
    activeIcon: '/static/tabbar/cart-selected.png',
  },
  {
    key: 'user',
    route: 'pages/user/user-center/index',
    path: '/pages/user/user-center/index',
    labelKey: 'tabbar.user',
    icon: '/static/tabbar/user.png',
    activeIcon: '/static/tabbar/user-selected.png',
  },
]

const TAB_ROUTE_TO_KEY = new Map(GLASS_TAB_ITEMS.map(item => [item.route, item.key]))

/** 当前路由 → 高亮 tab；未知路由回落到首页（布局只挂在 4 个 tab 页，正常不会走到） */
export function resolveGlassTabKey(route: string): GlassTabKey {
  const normalized = (route || '').replace(/^\/+/, '').replace(/\/+$/, '')
  return TAB_ROUTE_TO_KEY.get(normalized) ?? 'home'
}
