<script setup lang="ts">
import { onLoad, onPageScroll, onPullDownRefresh } from '@dcloudio/uni-app'
import { shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { getPageDesign } from '@/api/promotion/pageDesign'
import DiyPage from '@/components/diy/index.vue'
import { useDecorationInfiniteScroll } from '@/composables/useDecorationInfiniteScroll'
import { useDecorationPage } from '@/composables/useDecorationPage'
import { useDiyNavbarScroll } from '@/composables/useDiyNavbarScroll'
import { createLatestRequestRunner } from '@/composables/useLatestRequest'
import { useTabBarLocale } from '@/composables/useTabBarLocale'

definePage({
  name: 'home',
  layout: 'tabbar',
  type: 'home',
  style: {
    navigationStyle: 'custom',
  },
})

const loading = shallowRef(true)
const errorMessage = shallowRef('')
const pageContent = shallowRef<unknown>()
const pageName = shallowRef<string>()
const pageMeta = shallowRef<{ pageId?: string, versionId?: string }>()
const { t, locale } = useI18n()
useTabBarLocale()
const { canPullDownRefresh, title } = useDecorationPage({
  fallbackTitle: pageName,
  meta: pageMeta,
  pageContent,
})
const requestRunner = createLatestRequestRunner()

// 导航栏滚动渐显：页面只负责把滚动距离写进 provide 出去的 ref，消费方在 hr-navbar
const { handlePageScroll } = useDiyNavbarScroll()

// 滚动接近底部时提前通知装修里的分页商品组件（goods-group / goods-waterfall / goods-tabs）加载下一页
useDecorationInfiniteScroll()

async function initPageDesign() {
  errorMessage.value = ''
  if (!pageContent.value)
    loading.value = true
  await requestRunner.run(
    () => getPageDesign({ pageApp: 'MOBILE' }),
    {
      onError: () => {
        if (!pageContent.value)
          errorMessage.value = t('common.loadFailed')
      },
      onSettled: () => {
        loading.value = false
        uni.stopPullDownRefresh()
      },
      onSuccess: (response) => {
        pageContent.value = response.pageContent
        pageName.value = response.pageName
        pageMeta.value = { pageId: response.id, versionId: response.publishedVersionId }
      },
    },
  )
}
onLoad(() => {
  void initPageDesign()
})

/**
 * 滚动渐显接线（必须写成页面 SFC 内的字面量调用）。
 *
 * 微信小程序编译期由 @dcloudio/uni-mp-vite 的 runtimeHooks 插件对**页面源文本**
 * 做字符串匹配生成 `__runtimeHooks` 位标记；只把 onPageScroll 包在 composable
 * 里调用，页面产物不会带该标记，滚动事件静默丢失（H5 正常，所以浏览器看不出来）。
 */
onPageScroll(handlePageScroll)

/**
 * 语言切换后重新拉取装修数据。
 *
 * 首页/楼层里有一部分固定文案是在「取数归一化」阶段烘焙的（活动入口条标题、
 * 领券楼层券面文案等），模板里的 t() 会实时刷新，但这部分不会；重新取一次
 * 装修 JSON 才能让它们跟着切语言（已有内容时不闪 loading）。
 */
watch(locale, () => {
  void initPageDesign()
})

onPullDownRefresh(() => {
  if (canPullDownRefresh.value) {
    void initPageDesign()
  }
  else {
    uni.stopPullDownRefresh()
  }
})
</script>

<template>
  <view v-if="loading" class="decoration-state">
    {{ t('common.loading') }}
  </view>
  <view v-else-if="errorMessage" class="decoration-state decoration-state-error">
    {{ errorMessage }}
  </view>
  <view v-else>
    <diy-page
      :page-content-data="pageContent"
      :page-name="title"
    />
  </view>
</template>

<style scoped>
.decoration-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  font-size: 14px;
  color: #666;
}

.decoration-state-error {
  color: #c0362c;
}
</style>
