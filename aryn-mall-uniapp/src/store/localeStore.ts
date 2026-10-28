import { defineStore } from 'pinia'
import type { SupportedLocale } from '@/locales'
import { getLocale, i18n, resolveInitialLocale, setStoredLocale } from '@/locales'

const TABBAR_KEYS = ['tabbar.home', 'tabbar.category', 'tabbar.cart', 'tabbar.user'] as const

export const useLocaleStore = defineStore('locale', {
  state: () => ({
    locale: resolveInitialLocale() as SupportedLocale,
  }),
  getters: {
    isEnglish: state => state.locale === 'en-US',
    /** 语言名称按自身语言展示，不随界面语言翻译 */
    localeLabel: state => (state.locale === 'en-US' ? 'English' : '简体中文'),
  },
  actions: {
    init() {
      if (getLocale() !== this.locale) {
        i18n.global.locale.value = this.locale
      }
      this.syncTabBar()
    },
    setLocale(locale: SupportedLocale) {
      this.locale = locale
      setStoredLocale(locale)
      this.syncTabBar()
    },
    /**
     * 小程序 tabBar 文案在 pages.json 里是静态的，只能运行时逐项覆盖。
     * 设置页、首页等非 tab 页调用时可能失败（取决于基础库），失败静默，
     * tab 页 onShow 还会再同步一次（见 useTabBarLocale）。
     */
    syncTabBar() {
      TABBAR_KEYS.forEach((key, index) => {
        uni.setTabBarItem({
          index,
          text: i18n.global.t(key),
          fail: () => {},
        })
      })
    },
  },
})
