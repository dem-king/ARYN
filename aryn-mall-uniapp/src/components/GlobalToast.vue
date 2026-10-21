<script lang="ts" setup>
import { OVERLAY_Z_INDEX } from '@/utils/overlay'

/**
 * 全局轻提示。
 *
 * 层级必须显式下发：wot-design-uni 的 wd-toast 默认 z-index 只有 100，而页面里的
 * 底部弹层常用 900/901 —— 在弹层里操作失败时，提示被弹层盖住，用户看到的是
 * 「点了没反应」（2026-10-18 船舶绑定「提交申请」实测）。统一取
 * `OVERLAY_Z_INDEX.toast`，保证任何页面浮层之上都可见。
 */
const { toastOptions, currentPage } = storeToRefs(useGlobalToast())

const { close: closeGlobalToast } = useGlobalToast()

const toast = useToast('globalToast')
const currentPath = getCurrentPath()

// #ifdef MP-ALIPAY
const hackAlipayVisible = ref(false)

nextTick(() => {
  hackAlipayVisible.value = true
})
// #endif

watch(() => toastOptions.value, (newVal) => {
  if (newVal && newVal.show) {
    if (currentPage.value === currentPath) {
      // 逐次合并而非整体替换：调用方可能只传 msg，其余展示参数沿用 store 里的合并结果
      toast.show({ ...toastOptions.value, zIndex: OVERLAY_Z_INDEX.toast })
    }
  }
  else {
    toast.close()
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
    selector="globalToast"
    :z-index="OVERLAY_Z_INDEX.toast"
    :closed="closeGlobalToast"
  />
  <!-- #endif -->
  <!-- #ifndef MP-ALIPAY -->
  <wd-toast selector="globalToast" :z-index="OVERLAY_Z_INDEX.toast" :closed="closeGlobalToast" />
  <!-- #endif -->
</template>
