<script lang="ts" setup>
import { deepClone, isFunction } from 'wot-design-uni/components/common/util'
import { OVERLAY_Z_INDEX } from '@/utils/overlay'

/**
 * 全局确认框。
 *
 * 层级显式下发：wd-message-box 默认 z-index 99（内部 wd-popup 默认 10），
 * 页面底部弹层普遍 900/901 —— 在弹层里点「撤回」等需要二次确认的动作时，
 * 确认框会被弹层盖住，看起来像「点了没反应」。统一取
 * `OVERLAY_Z_INDEX.messageBox`（同时高于 toast，结果提示不会压在按钮上）。
 */
const { messageOptions, currentPage } = storeToRefs(useGlobalMessage())

const messageBox = useMessage('globalMessage')
const currentPath = getCurrentPath()

// #ifdef MP-ALIPAY
const hackAlipayVisible = ref(false)

nextTick(() => {
  hackAlipayVisible.value = true
})
// #endif

watch(() => messageOptions.value, (newVal) => {
  if (newVal) {
    if (currentPage.value === currentPath) {
      const option = deepClone(newVal)
      option.zIndex = OVERLAY_Z_INDEX.messageBox
      messageBox.show(option).then((res) => {
        if (isFunction(option.success)) {
          option.success(res)
        }
      }).catch((err) => {
        if (isFunction(option.fail)) {
          option.fail(err)
        }
      })
    }
  }
  else {
    messageBox.close()
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
  <wd-message-box v-if="hackAlipayVisible" selector="globalMessage" />
  <!-- #endif -->
  <!-- #ifndef MP-ALIPAY -->
  <wd-message-box selector="globalMessage" />
  <!-- #endif -->
</template>
