import type { DistributionShareBindPayload } from '@/api/distribution/entity'
import { bindDistributionShareParams } from '@/api/distribution/entity'
import { Local } from '@/utils/storage'

const DISTRIBUTION_SHARE_CACHE_KEY = 'distributionShareParams'

interface DistributionShareCaptureResult {
  shareUserId?: string
  referralCode?: string
  scene?: string
  sourcePath?: string
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

  return {
    shareUserId: shareUserId || undefined,
    referralCode: referralCode || undefined,
    scene: scene || undefined,
    sourcePath: sourcePath || undefined,
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
