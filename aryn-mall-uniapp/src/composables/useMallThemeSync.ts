import { tintTabBarIcons } from '@/composables/useTabBarIconTint'
/**
 * 商城主题同步：把 mallThemeStore 的主题色应用到小程序原生外壳，
 * 并让深色模式跟随系统（wot 组件层 + 原生导航/tabBar）。
 *
 * App.ku.vue 每页实例化一次，但主题换肤只需全进程做一次：
 * - store.ensureLoaded() 自带并发去重；
 * - $subscribe 挂在 store 实例（模块级单例）上，不随首个页面卸载而失效；
 * - setTabBarStyle/setTabBarItem 只认 tabBar 页上下文，非 tab 页调用会 fail，
 *   静默降级即可（tab 页的 App.ku.vue 也会触发同步，最终一定应用上）。
 *
 * 深色模式：manifest 开启 darkmode 后，原生导航/tabBar 由 theme.json 的
 * dark 段自动切换；页面内容层由 store.mode → App.ku.vue 的
 * wd-config-provider :theme="mode" 加 wot-theme-dark class 生效。
 */
import { useMallThemeStore } from '@/store/mallThemeStore'

const HEX_RE = /^#[0-9a-f]{6}$/i

function syncTabBar(store: ReturnType<typeof useMallThemeStore>) {
  if (!HEX_RE.test(store.primaryColor)) {
    return
  }
  try {
    uni.setTabBarStyle({
      selectedColor: store.primaryColor,
      fail: () => {
        // 非 tabBar 页上下文调用会失败，等 tab 页的 App.ku.vue 再触发
      },
    })
  }
  catch {
    // H5 端或极端时序下的兜底，换肤是装饰性能力不抛错
  }
  tintTabBarIcons(store.primaryColor)
}

/** 读取系统深浅并登记监听：darkmode 未开启（H5/未升级基础库）时恒为 light */
function syncThemeMode(store: ReturnType<typeof useMallThemeStore>) {
  try {
    const appBaseInfo = uni.getAppBaseInfo()
    store.mode = appBaseInfo.theme === 'dark' ? 'dark' : 'light'
  }
  catch {
    store.mode = 'light'
  }
  try {
    uni.onThemeChange((result) => {
      store.mode = result.theme === 'dark' ? 'dark' : 'light'
    })
  }
  catch {
    // 平台不支持主题监听时保持 light
  }
}

/** 是否已在全进程启动过一次主题同步 */
let started = false

export function useMallThemeSync() {
  if (started) {
    return
  }
  started = true

  const themeStore = useMallThemeStore()
  void themeStore.ensureLoaded()
  syncThemeMode(themeStore)
  syncTabBar(themeStore)
  themeStore.$subscribe(() => syncTabBar(themeStore))
}
