<script setup lang="ts">
import { onLoad, onPageScroll, onPullDownRefresh } from '@dcloudio/uni-app'
import { shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { getById } from '@/api/promotion/pageDesign'
import DiyPage from '@/components/diy/index.vue'
import { useDecorationInfiniteScroll } from '@/composables/useDecorationInfiniteScroll'
import { useDecorationPage } from '@/composables/useDecorationPage'
import { useDiyNavbarScroll } from '@/composables/useDiyNavbarScroll'
import { createLatestRequestRunner } from '@/composables/useLatestRequest'

definePage({
  name: 'diy-page',
  style: {
    navigationStyle: 'custom',
  },
})

const loading = shallowRef(true)
const errorMessage = shallowRef('')
const pageContent = shallowRef<unknown>()
const pageName = shallowRef<string>()
const pageMeta = shallowRef<{ pageId?: string, versionId?: string }>()
const diyId = shallowRef('')
const { t, locale } = useI18n()
const { canPullDownRefresh, title } = useDecorationPage({
  fallbackTitle: pageName,
  meta: pageMeta,
  pageContent,
})
const requestRunner = createLatestRequestRunner()

// 导航栏滚动渐显：页面只负责把滚动距离写进 provide 出去的 ref，消费方在 hr-navbar
const { handlePageScroll } = useDiyNavbarScroll()

// 落地页同样接入无限滚动：滚动接近底部时提前加载装修里的分页商品下一页
useDecorationInfiniteScroll()

async function initPageDesign() {
  errorMessage.value = ''
  if (!pageContent.value)
    loading.value = true
  await requestRunner.run(
    () => getById(diyId.value),
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
onLoad(async (options) => {
  if (options?.scene) {
    // 处理二维码场景值
    const scene = decodeURIComponent(options.scene)
    const params = scene.split('&')
    if (params.length > 0) {
      diyId.value = params[0]
    }
  }
  else {
    diyId.value = options?.id || ''
  }
  loading.value = true
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

onPullDownRefresh(() => {
  if (canPullDownRefresh.value) {
    void initPageDesign()
  }
  else {
    uni.stopPullDownRefresh()
  }
})

/**
 * 语言切换后重新拉取装修数据。
 *
 * 楼层里部分固定文案在取数归一化阶段烘焙（活动入口条、领券楼层券面等），
 * 模板 t() 会实时刷新但烘焙文案不会；重新取一次装修 JSON 才能整体切语言。
 * 未拿到 diyId（如冷启动参数缺失）时不请求，避免打空接口。
 */
watch(locale, () => {
  if (diyId.value)
    void initPageDesign()
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
