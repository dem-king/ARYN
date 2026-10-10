<script setup lang="ts">
import { captureDistributionShareParams, flushPendingDistributionShareBinding } from '@/composables/useDistributionShare'
import { refreshBindingOnShow } from '@/api/core/tenant-identity'
import { MALL_THEME_REFRESH_THROTTLE_MS, useMallThemeStore } from '@/store/mallThemeStore'

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
  // 租户构建：回前台按 5 分钟节流重查绑定映射（默认开发 generated=null 直接返回）
  void refreshBindingOnShow()
  // 管理端改色「下次打开生效」的另一半：冷启动由 App.ku.vue 的
  // useMallThemeSync 拉取，这里覆盖后台切回前台的场景（带节流，
  // 避免频繁 onShow 重复请求；失败/无变化静默保持当前配色）
  void useMallThemeStore().refresh({ maxAgeMs: MALL_THEME_REFRESH_THROTTLE_MS })
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
/*  #ifdef  MP  */
@import '@/styles/wd-icon-font.scss';
/*  #endif  */
page, #page {
  background-color: #F8F8F8 !important;
}
.page-wrapper {
  min-height: calc(100vh - var(--window-top));
  box-sizing: border-box;
  /* 外壳背景跟随商城默认主题：变量由同节点的 wd-config-provider 下发，
     拿不到时（老基础库/极端时序）回落原灰色。page 根节点取不到子节点
     的 CSS 变量，保持原值——可视区已被本节点盖满 */
  background: var(--wot-color-theme-background, #f2f3f4);
  color: #333;
}

.wot-theme-dark.page-wrapper {
  background: #222;
  color: #fff;
}
</style>
