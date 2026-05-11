<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'

import DiyPage from '@/components/diy/index.vue'

import { getPageDesign } from '@/api/promotion/pageDesign'

definePage({
  name: 'home',
  layout: 'tabbar',
  type: 'home',
  style: {
    navigationStyle: 'custom',
  },
})

const loading = ref(true)
const pageContent = ref<any>()
const pageName = ref()

async function initPageDesign() {
  try {
    const response = await getPageDesign({ pageApp: 'MOBILE' })
    pageContent.value = JSON.parse(response.pageContent)
    pageName.value = response.pageName
    if (pageName.value) {
      uni.setNavigationBarTitle({
        title: pageName.value,
      })
    }
  }
  finally {
    loading.value = false
  }
}
onLoad(async () => {
  loading.value = true
  initPageDesign()
})

onPullDownRefresh(() => {
  initPageDesign()
})
</script>

<template>
  <view>
    <diy-page
      :page-content-data="pageContent"
      :page-name="pageName"
    />
  </view>
</template>

<style>
</style>
