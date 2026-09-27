import type { DecorationLink } from './schema/types'

import { navigateToUrl } from '../../utils/tab-bar'

declare const uni: {
  navigateToMiniProgram: (options: {
    appId: string
    envVersion: 'develop' | 'release' | 'trial'
    path: string
  }) => void
}

export type DecorationLinkAction
  = | {
    appId: string
    envVersion: string
    kind: 'mini-program'
    path: string
  }
  | { kind: 'navigate', url: string }
  | { kind: 'none' }

interface LegacyDecorationLink {
  name?: string
  url?: string
}

function isSafeInternalPath(path: string) {
  return /^\/(?!\/)/.test(path) && !path.includes('\\')
}

function migrateLegacyPath(path: string) {
  const routeMap: Record<string, string> = {
    '/pages/product/goods-detail/index':
      '/sub-pages/product/goods-detail/index',
    '/pages/shop/diy-page/index': '/sub-pages/promotion/diy-page/index',
  }
  for (const [legacyPath, currentPath] of Object.entries(routeMap)) {
    if (path === legacyPath || path.startsWith(`${legacyPath}?`))
      return `${currentPath}${path.slice(legacyPath.length)}`
  }
  return path
}

function normalizeMiniProgramEnvironment(value?: string) {
  return value === 'develop' || value === 'trial' ? value : 'release'
}

export function normalizeDecorationLink(link: unknown): DecorationLink | null {
  if (typeof link === 'string') {
    const path = migrateLegacyPath(link)
    return isSafeInternalPath(path)
      ? { params: {}, path, type: 'custom' }
      : null
  }
  if (!link || typeof link !== 'object')
    return null

  const record = link as Partial<DecorationLink> & LegacyDecorationLink
  if (record.type) {
    const path
      = record.type === 'custom'
        ? migrateLegacyPath(record.path || '')
        : record.path || ''
    return {
      params: record.params || {},
      path,
      targetId: record.targetId,
      type: record.type,
    }
  }
  if (record.url) {
    const path = migrateLegacyPath(record.url)
    if (isSafeInternalPath(path))
      return { params: {}, path, type: 'custom' }
  }
  return null
}

function appendParams(path: string, params: Record<string, string>) {
  const query = Object.entries(params)
    .filter(([, value]) => value !== '')
    .map(
      ([key, value]) =>
        `${encodeURIComponent(key)}=${encodeURIComponent(value)}`,
    )
    .join('&')
  if (!query)
    return path
  return `${path}${path.includes('?') ? '&' : '?'}${query}`
}

export function createDecorationLinkAction(
  link?: DecorationLink | null,
): DecorationLinkAction {
  if (!link)
    return { kind: 'none' }
  if (link.type === 'customer-service')
    return { kind: 'navigate', url: '/sub-pages/message/chat/index' }
  if (link.type === 'mini-program') {
    return {
      appId: link.params.appId || '',
      envVersion: normalizeMiniProgramEnvironment(link.params.envVersion),
      kind: 'mini-program',
      path: link.path,
    }
  }

  const targetId = link.targetId || ''
  const defaultPaths: Partial<Record<DecorationLink['type'], string>> = {
    activity: `/sub-pages/promotion/group-buy/group-buy-detail/index?id=${targetId}`,
    coupon: `/sub-pages/promotion/coupon/coupon-list/index?id=${targetId}`,
    goods: `/sub-pages/product/goods-detail/index?id=${targetId}`,
    page: `/sub-pages/promotion/diy-page/index?id=${targetId}`,
  }

  // 分类链接：后台分类树选择器会把 categoryFirstId/categorySecondId 写进 params，
  // 此时按明确层级跳转，goods-list 直接查询、无需再查树判断层级。
  // 旧数据（只有 targetId、params 里没有层级）回退历史 categoryId，
  // 由 goods-list 页查分类树解析它到底是一级还是二级。
  if (link.type === 'category') {
    const base = '/sub-pages/product/goods-list/index'
    const p = link.params || {}
    const url = (p.categoryFirstId || p.categorySecondId)
      ? appendParams(base, p)
      : `${base}?categoryId=${targetId}`
    return { kind: 'navigate', url }
  }

  const path = link.path || defaultPaths[link.type] || ''
  return path && isSafeInternalPath(path)
    ? { kind: 'navigate', url: appendParams(path, link.params) }
    : { kind: 'none' }
}

/**
 * 按装修链接跳转。
 *
 * 跳转本身交给 `navigateToUrl`：装修链接的落地页由运营在后台配置，
 * 可能指向 tabBar 页（首页/分类/购物车/我的）。此前这里写的是
 * `navigateTo` + `fail: () => switchTab`，对 tabBar 页必然先失败一次再补跳，
 * 两次路由挤在同一 tick，微信会报
 * `routeDone with a webviewId xxx is not found`。
 */
export function followDecorationLink(
  link?: DecorationLink | LegacyDecorationLink | string | null,
) {
  const action = createDecorationLinkAction(normalizeDecorationLink(link))
  if (action.kind === 'navigate') {
    navigateToUrl(action.url)
  }
  else if (action.kind === 'mini-program' && action.appId) {
    uni.navigateToMiniProgram({
      appId: action.appId,
      envVersion: action.envVersion as 'develop' | 'release' | 'trial',
      path: action.path,
    })
  }
}
