<script setup lang="ts">
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
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
const { canPullDownRefresh, title } = useDecorationPage({
  fallbackTitle: pageName,
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
