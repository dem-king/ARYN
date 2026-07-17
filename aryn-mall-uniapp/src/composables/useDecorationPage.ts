import type { MaybeRefOrGetter } from 'vue'

import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import { computed, toValue, watch } from 'vue'

import { migratePageContent } from '@/components/diy/schema/migrate'

interface UseDecorationPageOptions {
  fallbackTitle: MaybeRefOrGetter<string | undefined>
  pageContent: MaybeRefOrGetter<unknown>
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
