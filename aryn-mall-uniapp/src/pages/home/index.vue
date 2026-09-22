<script setup lang="ts">
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { shallowRef } from 'vue'

import { getPageDesign } from '@/api/promotion/pageDesign'
import DiyPage from '@/components/diy/index.vue'
import { useDecorationPage } from '@/composables/useDecorationPage'
import { createLatestRequestRunner } from '@/composables/useLatestRequest'

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
const { canPullDownRefresh, title } = useDecorationPage({
  fallbackTitle: pageName,
  meta: pageMeta,
  pageContent,
})
const requestRunner = createLatestRequestRunner()

async function initPageDesign() {
  errorMessage.value = ''
  if (!pageContent.value)
    loading.value = true
  await requestRunner.run(
    () => getPageDesign({ pageApp: 'MOBILE' }),
    {
      onError: () => {
        if (!pageContent.value)
          errorMessage.value = '页面加载失败，请稍后重试'
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

onPullDownRefresh(() => {
  if (canPullDownRefresh.value) {
    void initPageDesign()
  }
  else {
    uni.stopPullDownRefresh()
  }
})

// 页面滚动到底：转发给装修里的分页商品组件（goods-group / goods-waterfall），触发自动加载下一页
onReachBottom(() => {
  uni.$emit('home-reach-bottom')
})
</script>

<template>
  <view v-if="loading" class="decoration-state">
    加载中...
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
