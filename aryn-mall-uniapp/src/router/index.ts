/// <reference types="@uni-helper/vite-plugin-uni-pages/client" />
import type { DistributionPermissionPoint } from '@/api/distribution/entity'

import { pages, subPackages } from 'virtual:uni-pages'
import {
  DISTRIBUTION_CENTER_PATH,
  DISTRIBUTION_PERMISSION_POINTS,
} from '@/api/distribution/entity'
import { translate } from '@/locales'
import { installNavigationLock } from '@/utils/navigation-lock'
import { Local } from '@/utils/storage'
// 定义路由白名单，这些页面不需要登录即可访问
const WHITE_LIST = [
  '/pages/login/index', // 登录页
  '/pages/home/index', // 首页
  '/pages/product/category/index', // 分类页
  '/pages/user/shopping-cart/index', // 购物车页
  '/pages/user/user-center/index', // 用户中心
  '/sub-pages/product/goods-appraise/index', // 商品评价页
  '/sub-pages/product/goods-detail/index', // 商品详情页
  '/sub-pages/product/goods-list/index', // 商品列表页
  '/sub-pages/product/goods-search/index', // 商品搜索页
  '/sub-pages/promotion/coupon/coupon-list/index', // 优惠券列表页
  '/sub-pages/promotion/group-buy/group-buy-list/index', // 拼团活动列表页
  '/sub-pages/promotion/group-buy/group-buy-detail/index', // 拼团活动详情页
  '/sub-pages/promotion/diy-page/index', // diy页面
  // 可以根据需要添加其他不需要登录的页面
]

const ROUTE_PERMISSION_MAP: Record<string, DistributionPermissionPoint[]> = {
  [DISTRIBUTION_CENTER_PATH]: [DISTRIBUTION_PERMISSION_POINTS.ENTITY_PAGE],
}

const DELIVERY_ROUTE_PREFIX = '/pages/delivery/'
const DELIVERY_LOGIN_PATH = '/pages/delivery/login'

function extractPermissionPoints(userInfo: Record<string, any> | null | undefined): string[] {
  if (!userInfo)
    return []

  const candidates = [userInfo.permissions, userInfo.perms, userInfo.authorities]
  for (const candidate of candidates) {
    if (Array.isArray(candidate)) {
      return candidate.filter((item): item is string => typeof item === 'string')
    }
  }
  return []
}

function generateRoutes() {
  const routes = pages.map((page) => {
    const newPath = `/${page.path}`
    return { ...page, path: newPath }
  })
  if (subPackages && subPackages.length > 0) {
    subPackages.forEach((subPackage) => {
      const subRoutes = subPackage.pages.map((page: any) => {
        const newPath = `/${subPackage.root}/${page.path}`
        return { ...page, path: newPath }
      })
      routes.push(...subRoutes)
    })
  }
  return routes
}

const router = createRouter({
  routes: generateRoutes(),
})
router.beforeEach((to, from, next) => {
  const isDeliveryRoute = Boolean(to.path?.startsWith(DELIVERY_ROUTE_PREFIX))
  if (isDeliveryRoute) {
    if (to.path === DELIVERY_LOGIN_PATH || Local.get('deliveryToken')) {
      next()
      return
    }
    next({ path: DELIVERY_LOGIN_PATH })
    return
  }

  // 检查目标页面是否在白名单中
  if (to.path && WHITE_LIST.includes(to.path)) {
    // 在白名单中的页面直接放行
    next()
    return
  }

  // 获取认证和用户信息store实例
  const authStore = useAuthStore()
  const userStore = useUserStore()

  // 检查用户是否已登录
  if (!authStore.isLoggedIn) {
    // 缓存当前页面路径，登录后跳转回此页面
    Local.set('redirectPath', from.fullPath)
    next({ path: '/pages/login/index' })
    return
  }

  // 检查token并获取用户信息
  if (authStore.getToken && !userStore.getUserInfo) {
    /**
     * 只预热、不等待。
     *
     * 旧实现 `await userStore.fetchUserInfo()` 把网络请求挡在导航路径上：冷启动
     * 只恢复了 token 时，第一次点击「没反应」（无 loading、无按钮态），用户必然
     * 再点一次；两次点击的 async 守卫从**同一个 in-flight promise** 恢复，在同一
     * 批微任务里各自发出一次路由 —— 同 tick 两次路由正是微信
     * `routeDone with a webviewId xxx is not found` 的高发成因
     * （见 utils/navigation-lock.ts 文件头）。
     *
     * 页面自己的 onShow 都会补拉用户信息（个人中心/会员中心已是这套口径）；
     * 凭证真的失效时由统一 401/会话失效链路处理，不靠导航守卫同步拦截。
     */
    userStore.fetchUserInfo().catch(() => authStore.clearAuthData())
  }

  // 路由权限点占位校验：
  // 仅当后端返回了权限点集合时才执行拦截，避免对现有无权限模型环境造成影响。
  const requiredPermissions = to.path ? ROUTE_PERMISSION_MAP[to.path] : undefined
  if (requiredPermissions && requiredPermissions.length > 0) {
    const currentPermissions = extractPermissionPoints(userStore.getUserInfo as Record<string, any> | null | undefined)
    if (currentPermissions.length > 0) {
      const hasPermission = requiredPermissions.every(permission => currentPermissions.includes(permission))
      if (!hasPermission) {
        uni.showToast({
          title: translate('app.nav.noPermission'),
          icon: 'none',
        })
        next(false)
        return
      }
    }
  }

  next()
})

/**
 * 路由串行/去重锁：包住 router 实例的 push/replace/pushTab/replaceAll/back。
 *
 * 必须装在实例方法上而不是逐个调用点：全仓 ~95 处 router.* 调用、~85 处裸 uni.*，
 * 且 uni-mini-router 的守卫是异步的、push 不可 await。锁在 beforeEach 之前执行，
 * 保证「一次用户手势最多一条路由」——连点/双击不再产生第二次路由。
 * 细节与成因见 utils/navigation-lock.ts。
 */
installNavigationLock(router)

export default router
