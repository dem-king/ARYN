import type { SupportedLocale } from '@/locales'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleStore } from '@/store/localeStore'

/**
 * 语言切换入口（个人设置页 / 个人中心 / 登录页共用）。
 *
 * 登录页放在导航栏右上角，用「另一种语言的自称」做快捷键
 * （当前中文时显示 English，当前英文时显示 简体中文）——外国朋友在
 * 读不懂界面时也能一键切到英文；个人中心放在页头右侧、与设置齿轮并排
 * （个人中心的导航栏右上角被居中标题和微信胶囊夹住，只剩约 37px，放不下
 * 带文字的按钮）；个人设置页仍保留标准 action sheet。
 *
 * 语言名一律按自身语言展示（English / 简体中文），不做翻译，避免
 * 用户看不懂「当前语言叫什么」。
 */
export function useLocaleSwitch() {
  const { t } = useI18n()
  const localeStore = useLocaleStore()

  const localeLabel = computed(() => localeStore.localeLabel)
  const isEnglish = computed(() => localeStore.isEnglish)
  /** 快捷按钮文案：展示「可切换到的另一种语言」 */
  const switchLabel = computed(() => (isEnglish.value ? '简体中文' : 'English'))

  function setLocale(locale: SupportedLocale) {
    localeStore.setLocale(locale)
  }

  /** 一键互切（登录页导航栏 / 个人中心页头） */
  function toggleLanguage() {
    setLocale(isEnglish.value ? 'zh-CN' : 'en-US')
  }

  /** 标准选择器（个人设置页） */
  function chooseLanguage() {
    const { success: showSuccess } = useGlobalToast()
    uni.showActionSheet({
      itemList: [t('common.chinese'), t('common.english')],
      success: ({ tapIndex }) => {
        setLocale(tapIndex === 0 ? 'zh-CN' : 'en-US')
        showSuccess(t('common.languageChanged'))
      },
    })
  }

  return {
    localeLabel,
    isEnglish,
    switchLabel,
    setLocale,
    toggleLanguage,
    chooseLanguage,
  }
}
