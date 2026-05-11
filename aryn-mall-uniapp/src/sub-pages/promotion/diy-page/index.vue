<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'

import DiyPage from '@/components/diy/index.vue'
import { getById } from '@/api/promotion/pageDesign'

definePage({
  name: 'diy-page',
  style: {
    navigationStyle: 'custom',
  },
})

const loading = ref(true)
const pageContent = ref<any>()
const pageName = ref()
const diyId = ref()

function initPageDesign() {
  getById(diyId.value).then((response) => {
    pageContent.value = JSON.parse(response.pageContent)
    pageName.value = response.pageName
    if (pageName.value) {
      uni.setNavigationBarTitle({
        title: pageName.value,
      })
    }
  })
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
    diyId.value = options?.id
  }
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
