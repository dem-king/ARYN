/**
 * 商城主题（C 端）。
 *
 * 背景：page_design_theme 此前只能被单个装修页面通过 themeRef 引用，
 * 主题改动「存了但全商城不生效」。本 store 承载「商城默认主题」：
 * 启动时经免登接口 /promotion/app/pagedesign/mall-theme 拉取，
 * App.ku.vue 的 wd-config-provider 读 themeVars 换肤，tabBar 同步选中色，
 * 未引用页面主题的装修页背景/导航也跟随本主题（见 diy/schema/effective-theme.ts）。
 *
 * 页面级覆盖：装修页发布快照里的 themeSnapshot（页面引用主题的固化快照）
 * 优先于本主题，由 diy 渲染器在该页内容区自行下放 CSS 变量，不经过本 store。
 *
 * 缓存策略（防闪色）：配色手动持久化到 storage，store 初始化时**同步**读缓存
 * 作为首屏值，ensureLoaded 再异步刷新 —— 若依赖 pinia persist 插件（异步不保证
 * 首帧前完成）或直接用服务端值，弱网/冷启动会先渲染默认红再突变。
 * 因此 persist 插件排除本 store（见 store/persist.ts），resolved 标记不入缓存。
 * 缓存 key 带租户后缀：同一 app 包切租户（多租户调试/env 切换）时旧租户
 * 配色不会串到新租户首帧。
 *
 * 失败策略：拉取失败或色值非法时保持现状（缓存或内置默认），与租户能力
 * store 同口径——换肤是装饰性能力，不值得为它阻塞或报错。
 * 服务端明确返回空（默认主题被删除/租户无默认主题）时回内置默认并清缓存，
 * 避免已撤掉的配色长期残留。
 */
import { defineStore } from 'pinia'
import { getMallTheme } from '@/api/promotion/pageDesign'

/** 内置默认配色：与 App.ku.vue 原写死值一致，也是非法/缺失时的兜底 */
export const DEFAULT_MALL_THEME = {
  themeId: '',
  primaryColor: '#FF2237',
  secondaryColor: '#FF6B7A',
  pageBackgroundColor: '#FFF0F0',
  navigationColor: '#FFFFFF',
  navigationTextColor: '#222222',
  radius: 8,
}

/**
 * 回到前台刷新的节流窗口：管理端改色后「下次打开生效」包含冷启动与
 * 后台切回两种场景，无需每次 onShow 都打接口。
 */
export const MALL_THEME_REFRESH_THROTTLE_MS = 5 * 60 * 1000

/** 深色模式：light 跟随主题配色；dark 由 wot theme + 原生外壳切换（三期） */
export type MallThemeMode = 'dark' | 'light'

function storageKey() {
  const tenantId = import.meta.env.VITE_TENANT_ID
  return tenantId ? `mall-theme-cache:${tenantId}` : 'mall-theme-cache'
}

const HEX_RE = /^#[0-9a-f]{6}$/i

interface MallThemeCache {
  themeId?: string
  primaryColor?: string
  secondaryColor?: string
  pageBackgroundColor?: string
  navigationColor?: string
  navigationTextColor?: string
  radius?: number
}

function isHex(value: unknown): value is string {
  return typeof value === 'string' && HEX_RE.test(value)
}

/** 只保留合法字段：缓存可能来自旧版本或被手改，逐字段校验防脏值渗进 CSS 变量 */
function sanitize(theme: MallThemeCache | null): MallThemeCache {
  if (!theme) {
    return {}
  }
  const clean: MallThemeCache = {}
  if (typeof theme.themeId === 'string') {
    clean.themeId = theme.themeId
  }
  if (isHex(theme.primaryColor)) {
    clean.primaryColor = theme.primaryColor
  }
  if (isHex(theme.secondaryColor)) {
    clean.secondaryColor = theme.secondaryColor
  }
  if (isHex(theme.pageBackgroundColor)) {
    clean.pageBackgroundColor = theme.pageBackgroundColor
  }
  if (isHex(theme.navigationColor)) {
    clean.navigationColor = theme.navigationColor
  }
  if (isHex(theme.navigationTextColor)) {
    clean.navigationTextColor = theme.navigationTextColor
  }
  if (typeof theme.radius === 'number' && theme.radius >= 0 && theme.radius <= 28) {
    clean.radius = theme.radius
  }
  return clean
}

function readCache(): MallThemeCache {
  try {
    return sanitize(uni.getStorageSync(storageKey()) as MallThemeCache | null)
  }
  catch {
    return {}
  }
}

function writeCache(theme: MallThemeCache) {
  try {
    uni.setStorageSync(storageKey(), theme)
  }
  catch {
    // storage 满/禁用只影响下次冷启动的首帧配色，不影响本次会话
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
 * 进行中的请求，用于并发去重。
 * 刻意放模块作用域而非 state：Promise 进入 state 会被持久化插件序列化成无意义值。
 */
let pendingRequest: Promise<void> | null = null

/** 最近一次发起拉取的时间戳（模块级，见 refresh 的节流说明） */
let lastFetchAt = 0

export const useMallThemeStore = defineStore('mallTheme', {
  state: () => ({
    ...DEFAULT_MALL_THEME,
    ...readCache(),
    mode: 'light' as MallThemeMode,
    /** 是否已完成一次拉取（含失败），避免并发重复请求 */
    resolved: false,
  }),

  getters: {
    /** wot-design-uni ConfigProvider 主题变量（camelCase → --wot-* CSS 变量） */
    themeVars(state) {
      return {
        colorTheme: state.primaryColor,
        colorThemePrimary: state.primaryColor,
        colorThemeSecondary: state.secondaryColor,
        colorThemeBackground: state.pageBackgroundColor,
      }
    },
  },

  actions: {
    /** 首次加载：已拉取过则直接返回（冷启动用，缓存先行防闪色） */
    async ensureLoaded() {
      if (this.resolved)
        return
      return this.fetch()
    },

    /**
     * 刷新商城默认主题。应用回到前台时调用（App.vue onShow）：
     * 传 maxAgeMs 时节流——窗口内拉取过则跳过，省掉连续 onShow 的重复请求；
     * 不传参数则强制拉取。请求进行中始终复用同一 Promise。
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
      pendingRequest = getMallTheme()
        .then((theme) => {
          // 服务端已无默认主题（被删除/租户未设置）：回内置默认并清缓存，
          // 避免已撤掉的配色或旧租户主题残留；拉取失败（网络等）才保持现状
          if (!theme) {
            this.applyDefaults()
            return
          }
          const clean = sanitize(theme)
          if (clean.primaryColor) {
            this.themeId = clean.themeId ?? ''
            this.primaryColor = clean.primaryColor
            this.secondaryColor = clean.secondaryColor ?? DEFAULT_MALL_THEME.secondaryColor
            this.pageBackgroundColor = clean.pageBackgroundColor ?? DEFAULT_MALL_THEME.pageBackgroundColor
            this.navigationColor = clean.navigationColor ?? DEFAULT_MALL_THEME.navigationColor
            this.navigationTextColor = clean.navigationTextColor ?? DEFAULT_MALL_THEME.navigationTextColor
            this.radius = clean.radius ?? DEFAULT_MALL_THEME.radius
            writeCache({
              themeId: this.themeId,
              primaryColor: this.primaryColor,
              secondaryColor: this.secondaryColor,
              pageBackgroundColor: this.pageBackgroundColor,
              navigationColor: this.navigationColor,
              navigationTextColor: this.navigationTextColor,
              radius: this.radius,
            })
          }
        })
        .catch(() => {
          // 保持缓存或内置默认配色
        })
        .finally(() => {
          this.resolved = true
          pendingRequest = null
        })

      return pendingRequest
    },

    /** 恢复内置默认配色并清缓存（服务端无默认主题时）；不动深色模式与 resolved */
    applyDefaults() {
      Object.assign(this, DEFAULT_MALL_THEME)
      clearCache()
    },

    /** 登出或切换租户时重置，避免上一个租户的主题泄漏到下一个 */
    reset() {
      Object.assign(this, DEFAULT_MALL_THEME)
      this.mode = 'light'
      this.resolved = false
      pendingRequest = null
      clearCache()
    },
  },
})
