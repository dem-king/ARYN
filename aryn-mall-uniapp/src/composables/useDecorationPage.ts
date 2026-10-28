import type { MaybeRefOrGetter } from 'vue'
import type { MetricEvent } from '@/api/promotion/pageDesign'

import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import { computed, toValue, watch } from 'vue'

import { reportMetrics } from '@/api/promotion/pageDesign'
import { resolveEffectivePageTheme } from '@/components/diy/schema/effective-theme'
import { migratePageContent } from '@/components/diy/schema/migrate'
import { normalizeNavigationScrollEffect } from '@/components/diy/schema/navigation-scroll'
import { resolveNavigationAppearance } from '@/components/diy/schema/navigation-style'
import { useMallThemeStore } from '@/store/mallThemeStore'

interface DecorationPageMeta {
  pageId?: string
  versionId?: string
}

interface UseDecorationPageOptions {
  fallbackTitle: MaybeRefOrGetter<string | undefined>
  /** 页面标识与发布版本，用于数据看板埋点 */
  meta?: MaybeRefOrGetter<DecorationPageMeta | undefined>
  pageContent: MaybeRefOrGetter<unknown>
}

function currentTerminal(): 'h5' | 'weapp' {
  // #ifdef MP-WEIXIN
  return 'weapp'
  // #endif
  // #ifndef MP-WEIXIN
  return 'h5'
  // #endif
}

function normalizeNavigationTextColor(color: string): '#000000' | '#ffffff' {
  return color.toLowerCase() === '#ffffff' ? '#ffffff' : '#000000'
}

export function useDecorationPage(options: UseDecorationPageOptions) {
  const mallThemeStore = useMallThemeStore()
  const document = computed(() => migratePageContent(toValue(options.pageContent)))
  const title = computed(
    () => document.value.page.navigation.title || toValue(options.fallbackTitle) || '',
  )
  const canPullDownRefresh = computed(
    () => document.value.page.enablePullDownRefresh,
  )
  const share = computed(() => document.value.page.share)
  const scrollEffect = computed(() =>
    normalizeNavigationScrollEffect(document.value.page.navigation.scrollEffect),
  )
  // 导航外观与 diy/index.vue 同口径解析：followFirstSection 下导航底色来自首个
  // 区块，只有解析后的 textColor 才与真实底色一致（原始 navigation.textColor
  // 是运营手填值，白字压白底的潜在缺陷就在这里，见设计方案 §4.5.4）
  const effectiveTheme = computed(() =>
    resolveEffectivePageTheme(
      document.value.page,
      document.value.themePrimaryColor
        ? undefined
        : {
            navigationColor: mallThemeStore.navigationColor,
            navigationTextColor: mallThemeStore.navigationTextColor,
            pageBackgroundColor: mallThemeStore.pageBackgroundColor,
          },
    ),
  )
  const navigationAppearance = computed(() =>
    resolveNavigationAppearance(
      document.value.page,
      effectiveTheme.value,
      document.value.sections,
    ),
  )
  /**
   * fade 模式且导航实际渲染时，状态栏前景色由 hr-navbar 按滚动进度独占控制：
   * 两处同时写会互相覆盖 —— 导航刚把状态栏翻成黑，这里又写回静态白。
   * 导航隐藏（visible=false）时没有 navbar 接管，仍走下面的兜底逻辑。
   */
  const navbarOwnsStatusBar = computed(
    () => scrollEffect.value.mode === 'fade' && document.value.page.navigation.visible,
  )

  watch(
    () => ({
      backgroundColor: document.value.page.navigation.backgroundColor,
      textColor: navigationAppearance.value.textColor,
      title: title.value,
    }),
    (navigation) => {
      if (navigation.title) {
        uni.setNavigationBarTitle({ title: navigation.title })
      }
      if (navbarOwnsStatusBar.value)
        return
      uni.setNavigationBarColor({
        backgroundColor: navigation.backgroundColor,
        frontColor: normalizeNavigationTextColor(navigation.textColor),
      })
    },
    { immediate: true },
  )

  watch(
    () => toValue(options.meta),
    (meta) => {
      if (!meta?.pageId) return
      const rendered = document.value.components
      const rawEmpty
        = rendered.length === 0 && toValue(options.pageContent) != null
      const events: MetricEvent[] = [
        {
          action: rawEmpty ? 'render_error' : 'page_view',
          pageDesignId: meta.pageId,
          terminal: currentTerminal(),
          versionId: meta.versionId,
        },
      ]
      // 渲染失败时附带 render_error 事件；埋点失败静默，不影响页面
      reportMetrics(events).catch(() => {})
    },
    { immediate: true },
  )

  onShareAppMessage(() => ({
    imageUrl: share.value.imageUrl,
    title: share.value.title || title.value,
  }))

  onShareTimeline(() => ({
    imageUrl: share.value.imageUrl,
    title: share.value.title || title.value,
  }))

  return {
    canPullDownRefresh,
    document,
    title,
  }
}
