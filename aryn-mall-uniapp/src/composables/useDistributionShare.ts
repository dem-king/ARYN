import type { DistributionBindSource, DistributionShareBindPayload } from '@/api/distribution/entity'
import { bindDistributionShareParams } from '@/api/distribution/entity'
import { Local } from '@/utils/storage'

const DISTRIBUTION_SHARE_CACHE_KEY = 'distributionShareParams'

interface DistributionShareCaptureResult {
  shareUserId?: string
  referralCode?: string
  scene?: string
  sourcePath?: string
  /** 绑定来源：分享链接 SHARE（默认）、小程序推广码扫码 QRCODE */
  bindSource?: DistributionBindSource
}

function parseSceneQuery(scene: string): Record<string, string> {
  const queryString = decodeURIComponent(scene)
  const result: Record<string, string> = {}
  queryString.split('&').forEach((segment) => {
    const [rawKey, ...rest] = segment.split('=')
    if (!rawKey)
      return
    const key = rawKey.trim()
    const value = rest.join('=').trim()
    if (!key)
      return
    result[key] = value
  })
  return result
}

function toStringValue(value: unknown): string {
  if (typeof value === 'string')
    return value.trim()
  if (typeof value === 'number')
    return String(value)
  return ''
}

function normalizeShareParams(options?: Record<string, any>): DistributionShareCaptureResult | null {
  if (!options)
    return null
  let sceneParams: Record<string, string> = {}
  const scene = toStringValue(options.scene)
  if (scene) {
    sceneParams = parseSceneQuery(scene)
  }

  const shareUserId = toStringValue(options.shareUserId || options.sid || sceneParams.shareUserId || sceneParams.sid)
  const referralCode = toStringValue(options.referralCode || sceneParams.referralCode)
  const sourcePath = toStringValue(options.sourcePath || options.path || '')
  if (!shareUserId && !referralCode)
    return null

  /**
   * 绑定来源分流：
   * - 小程序推广码 `wxacode.getUnlimited` 的 scene 固定为 `sid=<memberUserId>`，
   *   扫码进入时后端需要区分 SHARE / QRCODE 写 `sales_customer_bind.bind_source`；
   * - 其余入口（商品页分享链接携带 shareUserId）保持默认 SHARE，行为不变。
   */
  const bindSource: DistributionBindSource = sceneParams.sid ? 'QRCODE' : 'SHARE'

  return {
    shareUserId: shareUserId || undefined,
    referralCode: referralCode || undefined,
    scene: scene || undefined,
    sourcePath: sourcePath || undefined,
    bindSource,
  }
}

export function captureDistributionShareParams(options?: Record<string, any>) {
  const params = normalizeShareParams(options)
  if (!params)
    return null
  Local.set(DISTRIBUTION_SHARE_CACHE_KEY, params)
  return params
}

export function buildDistributionSharePath(path: string) {
  const userStore = useUserStore()
  const shareUserId = userStore.getUserId
  if (!shareUserId)
    return path
  const hasQuery = path.includes('?')
  const connector = hasQuery ? '&' : '?'
  return `${path}${connector}shareUserId=${encodeURIComponent(shareUserId)}`
}

export async function flushPendingDistributionShareBinding() {
  const authStore = useAuthStore()
  if (!authStore.isLoggedIn)
    return false

  const userStore = useUserStore()
  const currentUserId = userStore.getUserId
  const cache = Local.get(DISTRIBUTION_SHARE_CACHE_KEY) as DistributionShareCaptureResult | undefined
  if (!cache)
    return false

  if (cache.shareUserId && currentUserId && cache.shareUserId === currentUserId) {
    Local.remove(DISTRIBUTION_SHARE_CACHE_KEY)
    return false
  }

  try {
    const payload: DistributionShareBindPayload = {
      inviterUserId: cache.shareUserId,
      // 旧版本缓存没有 bindSource，按分享链接的默认口径兜底
      bindSource: cache.bindSource ?? 'SHARE',
    }
    await bindDistributionShareParams(payload)
    Local.remove(DISTRIBUTION_SHARE_CACHE_KEY)
    return true
  }
  catch (error) {
    console.warn('[distribution-share] 绑定失败，保留缓存待下次重试', error)
    return false
  }
}
