<script lang="ts" setup>
import { OVERLAY_Z_INDEX } from '@/utils/overlay'

/**
 * 全局加载中提示。
 *
 * 与 GlobalToast 同源问题：wd-toast 默认 z-index 100 会被页面 900+ 的底部弹层
 * 盖住，弹层里发起请求时「转圈」完全看不见。这里显式取
 * `OVERLAY_Z_INDEX.loading`（比 toast 高一档，避免结果提示先出现后加载层才消失）。
 */
const { loadingOptions, currentPage } = storeToRefs(useGlobalLoading())

const { close: closeGlobalLoading } = useGlobalLoading()

const loading = useToast('globalLoading')
const currentPath = getCurrentPath()

// #ifdef MP-ALIPAY
const hackAlipayVisible = ref(false)

nextTick(() => {
  hackAlipayVisible.value = true
})
// #endif

watch(() => loadingOptions.value, (newVal) => {
  if (newVal && newVal.show) {
    if (currentPage.value === currentPath) {
      loading.loading({ ...loadingOptions.value, zIndex: OVERLAY_Z_INDEX.loading })
    }
  }
  else {
    loading.close()
  }
})
</script>

<script lang="ts">
export default {
  options: {
    virtualHost: true,
    addGlobalClass: true,
    styleIsolation: 'shared',
  },
}
</script>

<template>
  <!-- #ifdef MP-ALIPAY -->
  <wd-toast
    v-if="hackAlipayVisible"
    selector="globalLoading"
    :z-index="OVERLAY_Z_INDEX.loading"
    :closed="closeGlobalLoading"
  />
  <!-- #endif -->
  <!-- #ifndef MP-ALIPAY -->
  <wd-toast selector="globalLoading" :z-index="OVERLAY_Z_INDEX.loading" :closed="closeGlobalLoading" />
  <!-- #endif -->
</template>
