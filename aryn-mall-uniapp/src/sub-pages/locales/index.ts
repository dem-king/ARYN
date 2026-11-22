import { i18n } from '@/locales'
import { compileMessages } from '@/locales/compile-messages'
import { subpageMessages } from './messages'

// 分包模块加载时注册两种语言；运行时同样预编译，不依赖微信禁用的动态编译。
for (const locale of ['zh-CN', 'en-US'] as const)
  i18n.global.mergeLocaleMessage(locale, compileMessages(subpageMessages[locale]))

export { useI18n } from 'vue-i18n'
