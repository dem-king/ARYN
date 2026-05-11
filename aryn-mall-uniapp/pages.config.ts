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

export default defineUniPages({
  pages: [],
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

    // 动画配置
    animationType: 'pop-in',
    animationDuration: 300,
  },
  tabBar: {
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
