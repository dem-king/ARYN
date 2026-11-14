/**
 * 空态插画配置（C 端）。
 *
 * 背景：wd-status-tip 的 7 个预设位原本只认 registry.npmmirror.com 的图，被 npmmirror
 * 的 unpkg 白名单拒绝（HTTP 451）后全部裂图。图片先随 upms 静态资源发布，再由本 store
 * 承接租户级覆盖：免登接口 /upms/app/status-tip-config 下发，未配置的位回落到内置图。
 * 同一份配置还覆盖购物车、订单列表、秒杀会场三张随包插画，后台换图无需重新发版。
 *
 * 缓存策略：空态是页面级高频场景，手写 storage 缓存并在 store 初始化时同步读取，
 * 避免首帧空白；缓存 key 带租户后缀，多租户调试/切换时旧租户图不会串到新租户。
 *
 * 失败策略：拉取失败保持缓存或内置图，不为装饰性配置阻塞页面；服务端明确返回空时
 * 清缓存回落内置图，避免已撤掉的图长期残留。
 */
import type { StatusTipImageKey, StatusTipImageMap } from '@/utils/status-tip'

import { defineStore } from 'pinia'
import { getAppStatusTipConfig } from '@/api/upms/status-tip'
import { isStatusTipImageUrl, resolveStatusTipImage } from '@/utils/status-tip'

/** 回到前台刷新的节流窗口（与管理端「改完下次打开生效」口径一致） */
export const STATUS_TIP_REFRESH_THROTTLE_MS = 5 * 60 * 1000

/** 10 个图片位 → 后端扁平字段（7 个 wd-status-tip 预设 + 3 张会场插画） */
const IMAGE_FIELDS: Record<StatusTipImageKey, keyof StatusTipConfigLike> = {
  'search': 'searchUrl',
  'network': 'networkUrl',
  'content': 'contentUrl',
  'collect': 'collectUrl',
  'comment': 'commentUrl',
  'halo': 'haloUrl',
  'message': 'messageUrl',
  'empty-cart': 'emptyCartUrl',
  'empty-order': 'emptyOrderUrl',
  'empty-seckill': 'emptySeckillUrl',
}

interface StatusTipConfigLike {
  searchUrl: string
  networkUrl: string
  contentUrl: string
  collectUrl: string
  commentUrl: string
  haloUrl: string
  messageUrl: string
  emptyCartUrl: string
  emptyOrderUrl: string
  emptySeckillUrl: string
}

function storageKey() {
  const tenantId = import.meta.env.VITE_TENANT_ID
  return tenantId ? `status-tip-cache:${tenantId}` : 'status-tip-cache'
}

/** 只保留合法的图片地址，缓存可能来自旧版本或被手改 */
function sanitize(images: null | Partial<StatusTipImageMap> | undefined): StatusTipImageMap {
  const clean: StatusTipImageMap = {}
  if (!images) {
    return clean
  }
  for (const image of Object.keys(IMAGE_FIELDS) as StatusTipImageKey[]) {
    const value = images[image]
    if (typeof value === 'string' && isStatusTipImageUrl(value)) {
      clean[image] = value.trim()
    }
  }
  return clean
}

function fromApi(config: StatusTipConfigLike | null | undefined): StatusTipImageMap {
  const images: StatusTipImageMap = {}
  if (!config) {
    return images
  }
  for (const image of Object.keys(IMAGE_FIELDS) as StatusTipImageKey[]) {
    const value = config[IMAGE_FIELDS[image]]
    if (typeof value === 'string' && isStatusTipImageUrl(value)) {
      images[image] = value.trim()
    }
  }
  return images
}

function readCache(): StatusTipImageMap {
  try {
    return sanitize(uni.getStorageSync(storageKey()) as Partial<StatusTipImageMap> | null)
  }
  catch {
    return {}
  }
}

function writeCache(images: StatusTipImageMap) {
  try {
    uni.setStorageSync(storageKey(), images)
  }
  catch {
    // storage 满/禁用只影响下次冷启动首帧，不影响本次会话
  }
}

function clearCache() {
  try {
    uni.removeStorageSync(storageKey())
  }
  catch {
    // 忽略存储异常
  }
}

let pendingRequest: null | Promise<void> = null
let lastFetchAt = 0

export const useStatusTipStore = defineStore('statusTip', {
  state: () => ({
    images: readCache(),
    /** 是否已完成一次拉取（含失败），避免并发重复请求 */
    resolved: false,
  }),

  actions: {
    /** 某个图片位最终使用的图片地址：配置优先，否则内置图 */
    imageFor(image: StatusTipImageKey): string {
      return resolveStatusTipImage(image, this.images)
    },

    async ensureLoaded() {
      if (this.resolved) {
        return
      }
      await this.fetch()
    },

    /**
     * 回前台刷新（App.vue onShow）。传 maxAgeMs 时节流；不传则强制拉取。
     * 请求进行中始终复用同一 Promise。
     */
    async refresh(options?: { maxAgeMs?: number }) {
      const maxAgeMs = options?.maxAgeMs
      if (
        maxAgeMs !== undefined
        && this.resolved
        && Date.now() - lastFetchAt < maxAgeMs
      ) {
        return
      }
      await this.fetch()
    },

    async fetch() {
      if (pendingRequest) {
        return pendingRequest
      }

      lastFetchAt = Date.now()

      pendingRequest = getAppStatusTipConfig()
        .then((config) => {
          const images = sanitize(fromApi(config))
          this.images = images
          if (Object.keys(images).length > 0) {
            writeCache(images)
          }
          else {
            clearCache()
          }
        })
        .catch(() => {
          // 保持缓存或内置图，不阻塞空态渲染
        })
        .finally(() => {
          this.resolved = true
          pendingRequest = null
        })

      return pendingRequest
    },

    /** 登出或切换租户时重置，避免上一个租户的图泄漏到下一个 */
    reset() {
      this.images = {}
      this.resolved = false
      pendingRequest = null
      clearCache()
    },
  },
})
