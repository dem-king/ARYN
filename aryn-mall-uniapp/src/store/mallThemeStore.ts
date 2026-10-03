/**
 * 商城主题（C 端）。
 *
 * 背景：page_design_theme 此前只能被单个装修页面通过 themeRef 引用，
 * 主题改动「存了但全商城不生效」。本 store 承载「商城默认主题」：
 * 启动时经免登接口 /promotion/app/pagedesign/mall-theme 拉取，
 * App.ku.vue 的 wd-config-provider 读 themeVars 换肤，tabBar 同步选中色。
 *
 * 页面级覆盖：装修页发布快照里的 themeSnapshot（页面引用主题的固化快照）
 * 优先于本主题，由 diy 渲染器在该页内容区自行下放 CSS 变量，不经过本 store。
 *
 * 缓存策略（防闪色）：配色手动持久化到 storage，store 初始化时**同步**读缓存
 * 作为首屏值，ensureLoaded 再异步刷新 —— 若依赖 pinia persist 插件（异步不保证
 * 首帧前完成）或直接用服务端值，弱网/冷启动会先渲染默认红再突变。
 * 因此 persist 插件排除本 store（见 store/persist.ts），resolved 标记不入缓存。
 *
 * 失败策略：拉取失败或色值非法时保持现状（缓存或内置默认），与租户能力
 * store 同口径——换肤是装饰性能力，不值得为它阻塞或报错。
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

/** 深色模式：light 跟随主题配色；dark 由 wot theme + 原生外壳切换（三期） */
export type MallThemeMode = 'dark' | 'light'

const STORAGE_KEY = 'mall-theme-cache'

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
    return sanitize(uni.getStorageSync(STORAGE_KEY) as MallThemeCache | null)
  }
  catch {
    return {}
  }
}

function writeCache(theme: MallThemeCache) {
  try {
    uni.setStorageSync(STORAGE_KEY, theme)
  }
  catch {
    // storage 满/禁用只影响下次冷启动的首帧配色，不影响本次会话
  }
}

/**
 * 进行中的请求，用于并发去重。
 * 刻意放模块作用域而非 state：Promise 进入 state 会被持久化插件序列化成无意义值。
 */
let pendingRequest: Promise<void> | null = null

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
    async ensureLoaded() {
      if (this.resolved)
        return
      if (pendingRequest)
        return pendingRequest

      pendingRequest = getMallTheme()
        .then((theme) => {
          // 未设置默认主题（null）时保持现状： merchants 随时可以撤掉默认主题，
          // 但 C 端已渲染的配色与缓存没必要跟着回跳默认红
          if (!theme) {
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

    /** 登出或切换租户时重置，避免上一个租户的主题泄漏到下一个 */
    reset() {
      Object.assign(this, DEFAULT_MALL_THEME)
      this.mode = 'light'
      this.resolved = false
      pendingRequest = null
      try {
        uni.removeStorageSync(STORAGE_KEY)
      }
      catch {
        // 忽略存储异常
      }
    },
  },
})
