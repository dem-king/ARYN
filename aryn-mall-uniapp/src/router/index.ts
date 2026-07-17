/// <reference types="@uni-helper/vite-plugin-uni-pages/client" />
import type { DistributionPermissionPoint } from '@/api/distribution/entity'

import { pages, subPackages } from 'virtual:uni-pages'
import {
  DISTRIBUTION_CENTER_PATH,
  DISTRIBUTION_PERMISSION_POINTS,
} from '@/api/distribution/entity'
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
router.beforeEach(async (to, from, next) => {
  console.log('🚀 beforeEach 守卫触发:', { to, from })

  // 检查目标页面是否在白名单中
  if (to.path && WHITE_LIST.includes(to.path)) {
    // 在白名单中的页面直接放行
    console.log(`✅ 页面 ${to.path} 在白名单中，直接放行`)
    next()
    return
  }

  // 获取认证和用户信息store实例
  const authStore = useAuthStore()
  const userStore = useUserStore()

  // 检查用户是否已登录
  if (!authStore.isLoggedIn) {
    console.log(`🔒 用户未登录，跳转至登录页${to.path}`)
    // 缓存当前页面路径，登录后跳转回此页面
    Local.set('redirectPath', from.fullPath)
    next({ path: '/pages/login/index' })
    return
  }

  // 检查token并获取用户信息
  if (authStore.getToken && !userStore.getUserInfo) {
    console.log('👤 检测到token但用户信息不存在，开始获取用户信息...')
    try {
      await userStore.fetchUserInfo()
      console.log('👤 用户信息获取成功:', userStore.getUserInfo)
    }
    catch (error) {
      console.warn('👤 用户信息获取失败:', error)
      // 清除认证信息并跳转到登录页
      authStore.clearAuthData()
      next({ path: '/pages/login/index' })
      return
    }
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
          title: '暂无访问权限',
          icon: 'none',
        })
        next(false)
        return
      }
    }
  }

  // 演示：基本的导航日志记录
  if (to.path && from.path) {
    console.log(`📍 导航: ${from.path} → ${to.path}`)
  }
  next()
})

router.afterEach((to, from) => {
  console.log('🎯 afterEach 钩子触发:', { to, from })

  if (to.path) {
    console.log(`📄 页面切换完成: ${to.path}`)
  }
})

export default router
