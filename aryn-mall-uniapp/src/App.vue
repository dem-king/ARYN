<script setup lang="ts">
import { captureDistributionShareParams, flushPendingDistributionShareBinding } from '@/composables/useDistributionShare'

/**
 * 应用启动时的初始化逻辑
 */
onLaunch(async () => {
  // #ifdef MP
  updateManager()
  // #endif
})

onShow((options) => {
  captureDistributionShareParams(options as Record<string, any>)
  flushPendingDistributionShareBinding()
})

function updateManager() {
  const updateManager = uni.getUpdateManager()
  updateManager.onUpdateReady(() => {
    useGlobalMessage().confirm({
      title: '更新提示',
      msg: '新版本已经准备好，是否重启应用？',
      closeOnClickModal: false,
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      success: (res) => {
        if (res.action === 'confirm') {
          // 新的版本已经下载好，调用 applyUpdate 应用新版本并重启
          updateManager.applyUpdate()
        }
      },
    })
  })
}
</script>

<style lang="scss">
page, #page {
  background-color: #F8F8F8 !important;
}
.page-wrapper {
  min-height: calc(100vh - var(--window-top));
  box-sizing: border-box;
  background: #f2f3f4;
  color: #333;
}

.wot-theme-dark.page-wrapper {
  background: #222;
  color: #fff;
}
</style>
