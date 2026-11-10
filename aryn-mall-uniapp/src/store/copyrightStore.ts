/**
 * 版权配置（C 端）。
 *
 * 背景：登录页的品牌与版权此前写死在页面里（logo 内联、文案在 locales 的
 * login.copyright），运营改一次版权要发版，ICP 备案号也无处可填。本 store 承载
 * 租户级配置：免登接口 /upms/app/copyright-config?clientType=app 下发，
 * 名称与 Logo 留空时由服务端回落租户品牌。
 *
 * 缓存策略（防闪烁/防空白）：品牌与版权是首屏内容，手写 storage 缓存并在 store
 * 初始化时**同步**读取作为首帧值，ensureLoaded 再异步刷新。缓存 key 带租户后缀，
 * 同一安装包切换租户（多租户调试/env 切换）时旧租户品牌不会串到新租户首帧。
 *
 * 失败策略与商城主题一致：拉取失败保持缓存或内置默认，不为装饰性配置阻塞登录；
 * 服务端明确返回空时清缓存回内置默认，避免已撤掉的品牌长期残留。
 */
import { defineStore } from 'pinia'
import { getAppCopyrightConfig } from '@/api/upms/copyright'

export interface CopyrightConfigState {
  logoUrl: string
  name: string
  copyrightText: string
  copyrightLink: string
  icp: string
  icpLink: string
}

/** 内置默认：与改造前写死值一致（logo 走静态资源，文案回落语言包） */
export const DEFAULT_COPYRIGHT_CONFIG: CopyrightConfigState = {
  logoUrl: '/static/logo.png',
  name: '',
  copyrightText: '',
  copyrightLink: '',
  icp: '',
  icpLink: '',
}

function storageKey() {
  const tenantId = import.meta.env.VITE_TENANT_ID
  return tenantId ? `copyright-config-cache:${tenantId}` : 'copyright-config-cache'
}

/** 只保留字符串字段：缓存可能来自旧版本或被手改，防脏值渗进渲染 */
function sanitize(config: null | Partial<CopyrightConfigState> | undefined): CopyrightConfigState {
  const clean: CopyrightConfigState = { ...DEFAULT_COPYRIGHT_CONFIG }
  if (!config) {
    return clean
  }
  for (const key of Object.keys(DEFAULT_COPYRIGHT_CONFIG) as (keyof CopyrightConfigState)[]) {
    const value = config[key]
    if (typeof value === 'string') {
      clean[key] = value.trim()
    }
  }
  return clean
}

function readCache(): CopyrightConfigState {
  try {
    return sanitize(uni.getStorageSync(storageKey()) as Partial<CopyrightConfigState> | null)
  }
  catch {
    return { ...DEFAULT_COPYRIGHT_CONFIG }
  }
}

function writeCache(config: CopyrightConfigState) {
  try {
    uni.setStorageSync(storageKey(), config)
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

/**
 * 链接只允许 http/https：值会绑到 web-view 跳转与外链复制，
 * 放开其它协议等于给运营后台开了一个注入入口。
 */
export function isSafeCopyrightLink(link: string): boolean {
  return /^https?:\/\//i.test(link)
}

let pendingRequest: null | Promise<void> = null

/** 最近一次拉取时间（模块级）：回前台刷新按窗口节流，避免连续 onShow 重复请求 */
let lastFetchAt = 0

/** 回到前台刷新的节流窗口（与管理端「改完下次打开生效」口径一致） */
export const COPYRIGHT_REFRESH_THROTTLE_MS = 5 * 60 * 1000

export const useCopyrightStore = defineStore('copyright', {
  state: () => ({
    ...readCache(),
    /** 是否已完成一次拉取（含失败），避免并发重复请求 */
    resolved: false,
  }),

  getters: {
    /** 品牌名：配置名优先，否则由页面回落语言包的 common.appName */
    brandName(state): string {
      return state.name
    },
    /** 版权行文案：配置优先，否则由页面回落语言包 login.copyright */
    copyrightLine(state): string {
      return state.copyrightText
    },
    /** 版权外链（已过滤非法协议，空串表示不渲染链接） */
    safeCopyrightLink(state): string {
      return isSafeCopyrightLink(state.copyrightLink) ? state.copyrightLink : ''
    },
    /** ICP 备案链接（已过滤非法协议） */
    safeIcpLink(state): string {
      return isSafeCopyrightLink(state.icpLink) ? state.icpLink : ''
    },
  },

  actions: {
    async ensureLoaded() {
      if (this.resolved)
        return
      return this.fetch()
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
      return this.fetch()
    },

    async fetch() {
      if (pendingRequest)
        return pendingRequest

      lastFetchAt = Date.now()

      pendingRequest = getAppCopyrightConfig()
        .then((config) => {
          const clean = sanitize(config)
          // 服务端返回了品牌或版权（任意一项非空）才覆盖；全空视为未配置，
          // 回内置默认并清缓存，避免已撤掉的品牌长期残留
          if (!clean.name && !clean.logoUrl && !clean.copyrightText && !clean.icp) {
            this.applyDefaults()
            return
          }
          this.logoUrl = clean.logoUrl || DEFAULT_COPYRIGHT_CONFIG.logoUrl
          this.name = clean.name
          this.copyrightText = clean.copyrightText
          this.copyrightLink = clean.copyrightLink
          this.icp = clean.icp
          this.icpLink = clean.icpLink
          writeCache({
            logoUrl: this.logoUrl,
            name: this.name,
            copyrightText: this.copyrightText,
            copyrightLink: this.copyrightLink,
            icp: this.icp,
            icpLink: this.icpLink,
          })
        })
        .catch(() => {
          // 保持缓存或内置默认
        })
        .finally(() => {
          this.resolved = true
          pendingRequest = null
        })

      return pendingRequest
    },

    /** 恢复内置默认并清缓存；不动 resolved */
    applyDefaults() {
      Object.assign(this, DEFAULT_COPYRIGHT_CONFIG)
      clearCache()
    },

    /** 登出或切换租户时重置，避免上一个租户的品牌泄漏到下一个 */
    reset() {
      Object.assign(this, DEFAULT_COPYRIGHT_CONFIG)
      this.resolved = false
      pendingRequest = null
      clearCache()
    },
  },
})
