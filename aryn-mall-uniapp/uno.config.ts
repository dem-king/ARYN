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
      'primary': 'var(--theme-color-primary, var(--wot-color-theme-primary, #4D7FFF))',
      'secondary': 'var(--theme-color-secondary, var(--wot-color-theme-secondary, #7BA7FF))',
      'theme-bg': 'var(--theme-color-background, var(--wot-color-theme-background, #F0F4FF))',
    },
  },
})
