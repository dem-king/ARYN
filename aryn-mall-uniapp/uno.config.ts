import { presetUni } from '@uni-helper/unocss-preset-uni'

import {
  defineConfig,
  presetIcons,
} from 'unocss'

export default defineConfig({
  content: {
    pipeline: {
      exclude: [/node_modules/],
    },
  },
  /**
   * 拦掉 SVG 路径片段被误当成工具类。
   *
   * 图标 body 里 `<path d="m29.92 16.61..."/>` 的 `m29.92` 会命中 margin 规则
   * （`m<数值>` 这类无连字符写法本身就是合法工具类），于是最终产物里这段路径被
   * 当成工具类名改写：applet preset 把类名中的 `.` 换成 `_a_`，路径就变成
   * `m29_a_92` —— SVG 语法失效，图标整块空白。
   *
   * 表现为「某些图标不显示、另一些正常」，因为只有路径起点带小数的图标才会中招
   * （2026-09-29 待收货的 delivery-truck 就是这样丢的）。它与所用类名无关，
   * 所以只查自己的模板永远找不到原因——必须在这里按 token 形状拦。
   */
  blocklist: [
    /^[a-z]\d+\.\d+$/i,
  ],
  presets: [
    presetUni({ attributify: false }),
    presetIcons({
      scale: 1.2,
      warn: true,
      extraProperties: {
        'display': 'inline-block',
        'vertical-align': 'middle',
      },
      // HBuilderX 必须针对要使用的 Collections 做异步导入
      // collections: {
      //   carbon: () => import('@iconify-json/carbon/icons.json').then(i => i.default),
      // },
    }),
  ],
  theme: {
    colors: {
      // 三色主题CSS变量映射
      // H5环境：直接使用CSS变量
      // 小程序环境：通过wot-design-uni的theme-vars传递，命名转换规则：
      // colorThemePrimary -> --wot-color-theme-primary
      // fallback 是 App.ku.vue 的内置默认配色（商城默认主题拉取失败时的兜底），
      // 改默认值必须三处同步：mallThemeStore.DEFAULT_MALL_THEME、这里、theme.json
      'primary': 'var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237))',
      'secondary': 'var(--theme-color-secondary, var(--wot-color-theme-secondary, #FF6B7A))',
      'theme-bg': 'var(--theme-color-background, var(--wot-color-theme-background, #FFF0F0))',
    },
  },
})
