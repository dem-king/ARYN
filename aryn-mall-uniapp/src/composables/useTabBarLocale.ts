import { onShow } from '@dcloudio/uni-app'
import { useLocaleStore } from '@/store/localeStore'

/**
 * tabBar 页在每次 onShow 时同步一次 tabBar 文案。
 *
 * tabBar 配置是静态的，语言切换后若用户直接切 Tab，之前设置页里的
 * setTabBarItem 可能未生效（非 tab 页调用受基础库限制），这里兜底。
 */
export function useTabBarLocale() {
  const localeStore = useLocaleStore()
  onShow(() => {
    localeStore.init()
  })
}
