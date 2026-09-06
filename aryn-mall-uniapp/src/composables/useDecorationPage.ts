import type { MaybeRefOrGetter } from 'vue'

import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import { computed, toValue, watch } from 'vue'

import { migratePageContent } from '@/components/diy/schema/migrate'
import type { MetricEvent } from '@/api/promotion/pageDesign'
import { reportMetrics } from '@/api/promotion/pageDesign'

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
  const document = computed(() => migratePageContent(toValue(options.pageContent)))
  const title = computed(
    () => document.value.page.navigation.title || toValue(options.fallbackTitle) || '',
  )
  const canPullDownRefresh = computed(
    () => document.value.page.enablePullDownRefresh,
  )
  const share = computed(() => document.value.page.share)

  watch(
    () => ({
      backgroundColor: document.value.page.navigation.backgroundColor,
      textColor: document.value.page.navigation.textColor,
      title: title.value,
    }),
    (navigation) => {
      if (navigation.title) {
        uni.setNavigationBarTitle({ title: navigation.title })
      }
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
