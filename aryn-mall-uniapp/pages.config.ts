/*
 * @Author: weisheng
 * @Date: 2025-06-23 22:23:05
 * @LastEditTime: 2025-06-27 13:04:54
 * @LastEditors: weisheng
 * @Description:
 * @FilePath: /aryn-uniapp-pro/pages.config.ts
 * 记得注释
 */
import { defineUniPages } from '@uni-helper/vite-plugin-uni-pages'

/**
 * 微信自定义 tabBar（自绘悬浮玻璃底栏）。
 *
 * `custom: true` 让小程序**从根上不渲染原生 tabBar**：冷启动 / 重新编译不会先
 * 闪出一条原生底栏；真正的视觉由 `src/custom-tab-bar/`（页面首帧的原生实现）
 * 与 `src/components/glass-tabbar/`（Vue 常态实现）接管。
 *
 * 必须**无条件**写入，不能按编译平台做条件分支：
 * `src/pages.json` 是三端（H5 / 小程序 / App）共用的同一份生成文件，H5 的启动
 * 与构建也会重写它。一旦按平台条件写入，任何一次 H5 运行都会把 custom 抹掉，
 * 正在跑的 mp-weixin dev 监听到 pages.json 变化后重新生成的 app.json 随即丢掉
 * custom，微信开发者工具就退回原生底栏（2026-10-29 实际踩过这个坑）。
 * H5 / App 的运行时不认识该字段，按微信 schema 原样透传即可；H5 的内置
 * tabBar 仍由全局 CSS 渲染成磨砂胶囊，不受影响。
 */
const customTabBar = { custom: true } as Record<string, unknown>

export default defineUniPages({
  pages: [],
  // 配送入口在「我的」页；只在 Wi-Fi 下预下载，避免普通顾客消耗流量
  preloadRule: {
    'pages/user/user-center/index': {
      network: 'wifi',
      packages: ['pages/delivery'],
    },
  },
  easycom: {
    autoscan: true,
    custom: {
      '^(?!z-paging-refresh|z-paging-load-more)z-paging(.*)': 'z-paging/components/z-paging$1/z-paging$1.vue',
    },
  },
  globalStyle: {
    // 导航栏配置
    navigationBarBackgroundColor: '@navBgColor',
    navigationBarTextStyle: '@navTxtStyle',
    navigationBarTitleText: 'Aryn Mall',

    // 页面背景配置
    backgroundColor: '@bgColor',
    backgroundTextStyle: '@bgTxtStyle',
    backgroundColorTop: '@bgColorTop',
    backgroundColorBottom: '@bgColorBottom',

    // 下拉刷新配置
    enablePullDownRefresh: false,
    onReachBottomDistance: 50,

    // animationType/animationDuration 不配：App 专用字段且默认值就是 pop-in/300，
    // 配了会被原样带进 mp-weixin 的 app.json window，被微信判成无效字段告警
  },
  tabBar: {
    // 微信自定义 tabBar 字段不在 uni 类型里，无条件按微信 schema 原样透传到 app.json
    ...customTabBar,
    selectedColor: '#FF2237',
    list: [{
      pagePath: 'pages/home/index',
      text: '首页',
      iconPath: 'static/tabbar/home.png',
      selectedIconPath: 'static/tabbar/home-selected.png',
    }, {
      pagePath: 'pages/product/category/index',
      text: '分类',
      iconPath: 'static/tabbar/category.png',
      selectedIconPath: 'static/tabbar/category-selected.png',
    }, {
      pagePath: 'pages/user/shopping-cart/index',
      text: '购物车',
      iconPath: 'static/tabbar/cart.png',
      selectedIconPath: 'static/tabbar/cart-selected.png',
    }, {
      pagePath: 'pages/user/user-center/index',
      text: '我的',
      iconPath: 'static/tabbar/user.png',
      selectedIconPath: 'static/tabbar/user-selected.png',
    }],
  },
})
