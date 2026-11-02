/**
 * 微信自定义 tabBar 的「首帧」原生实现（玻璃悬浮胶囊）。
 *
 * 为什么要有原生实现：小程序切 tab 本质是换页。新页面的 Vue 根还没挂载时，
 * 页面里自绘的 `components/glass-tabbar` 还不存在，底部会先空一段再出现底栏。
 * custom-tab-bar 组件随页面首帧渲染、数据全静态，不依赖 Vue 启动，可以兜住
 * 「页面首帧已出（骨架屏）→ Vue 挂载」这段空窗。
 *
 * 它**覆盖不到**首帧之前的整屏纯白：custom-tab-bar 与页面在同一个 webview，
 * 页面首帧渲染前它也不存在（录帧实测冷切先有一整帧纯白）。要根治只能改回
 * 原生 tabBar（窗口层渲染，但失去玻璃胶囊）或单页承载 4 个 tab（大重构）。
 *
 * 两者分工：
 *  · 本组件只负责 Vue 启动前的首帧，视觉与 Vue 版逐值一致；
 *  · Vue 版挂载后通过 getTabBar().setData({ hidden: true }) 把本组件隐藏
 *    （custom 模式下 wx.hideTabBar 不生效），之后选中态、角标、多语言、
 *    页面浮层级全部回到 Vue 版，行为与之前完全一致；
 *  · 同一份状态（主题色 / 深色 / 角标 / 文案）由 Vue 版写入
 *    getApp().__glassTabBarSeed，供下一个页面的首帧直接取用，避免交接时跳色。
 *
 * 本文件会被构建原样拷进产物，不能 import 项目里的 TS 模块；tab 清单与
 * `components/glass-tabbar/tabs.ts`、`pages.config.ts` 由契约测试守同。
 *
 * 选中项为什么不在 attached 里直接算：实测组件 attached 时页面栈还是空的
 * （getCurrentPages() === []），要等下一拍页面入栈才拿得到路由；因此首帧
 * 先保持「不亮任何 tab」的中性态，setTimeout(0) 后再按真实路由点亮。
 */

const LOCALE_KEY = 'ARYN_LOCALE'
const DEFAULT_PRIMARY = '#ff2237'

/** 与 tabs.ts 的 GLASS_TAB_ITEMS 一一对应 */
const TABS = [
  {
    key: 'home',
    path: '/pages/home/index',
    icon: '/static/tabbar/home.png',
    activeIcon: '/static/tabbar/home-selected.png',
    zh: '首页',
    en: 'Home',
  },
  {
    key: 'category',
    path: '/pages/product/category/index',
    icon: '/static/tabbar/category.png',
    activeIcon: '/static/tabbar/category-selected.png',
    zh: '分类',
    en: 'Category',
  },
  {
    key: 'cart',
    path: '/pages/user/shopping-cart/index',
    icon: '/static/tabbar/cart.png',
    activeIcon: '/static/tabbar/cart-selected.png',
    zh: '购物车',
    en: 'Cart',
  },
  {
    key: 'user',
    path: '/pages/user/user-center/index',
    icon: '/static/tabbar/user.png',
    activeIcon: '/static/tabbar/user-selected.png',
    zh: '我的',
    en: 'Me',
  },
]

function currentRoute() {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1]
  return (page && page.route) || ''
}

function selectedIndex() {
  const route = currentRoute().replace(/^\/+/, '').replace(/\/+$/, '')
  // 组件 attached 时页面栈还是空的，先给 -1（不亮任何 tab），
  // 等下一拍页面入栈后再按真实路由解析；宁可短暂中性，也不要先亮错 tab
  if (!route) {
    return -1
  }
  for (let i = 0; i < TABS.length; i++) {
    if (TABS[i].path.replace(/^\//, '') === route) {
      return i
    }
  }
  return 0
}

function resolveLocale() {
  try {
    const saved = wx.getStorageSync(LOCALE_KEY)
    if (typeof saved === 'string' && saved) {
      return saved.toLowerCase().indexOf('en') === 0 ? 'en-US' : 'zh-CN'
    }
    const info = typeof wx.getAppBaseInfo === 'function' ? wx.getAppBaseInfo() : wx.getSystemInfoSync()
    return String((info && info.language) || '').toLowerCase().indexOf('en') === 0 ? 'en-US' : 'zh-CN'
  }
  catch (e) {
    return 'zh-CN'
  }
}

function readSeed() {
  try {
    const app = getApp()
    return (app && app.__glassTabBarSeed) || null
  }
  catch (e) {
    return null
  }
}

function systemDark() {
  try {
    const info = typeof wx.getAppBaseInfo === 'function' ? wx.getAppBaseInfo() : wx.getSystemInfoSync()
    return !!info && info.theme === 'dark'
  }
  catch (e) {
    return false
  }
}

Component({
  data: {
    /** Vue 底栏接管后置 true，此后本组件不再渲染 */
    hidden: false,
    selected: -1,
    items: TABS.map(function (tab) {
      return { key: tab.key, path: tab.path, icon: tab.icon, activeIcon: tab.activeIcon, label: tab.zh }
    }),
    badge: '',
    primaryColor: DEFAULT_PRIMARY,
    dark: false,
  },

  lifetimes: {
    attached() {
      this.applyState()
      // attached 阶段页面还没入栈，下一拍再解析一次选中项。
      // 实测这两次 setData 都发生在页面首帧之前，正常情况下看不到中性态。
      const self = this
      this.__resolveTimer = setTimeout(function () {
        self.applyState()
      }, 0)
    },
    detached() {
      if (this.__resolveTimer) {
        clearTimeout(this.__resolveTimer)
        this.__resolveTimer = null
      }
    },
  },

  methods: {
    applyState() {
      const seed = readSeed() || {}
      const locale = resolveLocale()
      const labels = Array.isArray(seed.labels) && seed.labels.length === TABS.length
        ? seed.labels
        : TABS.map(function (tab) { return locale === 'en-US' ? tab.en : tab.zh })

      this.setData({
        selected: selectedIndex(),
        items: TABS.map(function (tab, index) {
          return {
            key: tab.key,
            path: tab.path,
            icon: tab.icon,
            activeIcon: tab.activeIcon,
            label: labels[index],
          }
        }),
        badge: typeof seed.badge === 'string' ? seed.badge : '',
        primaryColor: seed.primaryColor || DEFAULT_PRIMARY,
        dark: typeof seed.dark === 'boolean' ? seed.dark : systemDark(),
      })
    },

    tap(e) {
      const index = Number(e.currentTarget.dataset.index)
      if (!Number.isFinite(index) || index === this.data.selected) {
        return
      }
      wx.switchTab({ url: TABS[index].path, fail() {} })
    },
  },
})
