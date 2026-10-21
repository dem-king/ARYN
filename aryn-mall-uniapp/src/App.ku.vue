<script setup lang="ts">
import { pendingChoice } from '@/composables/useCartDestination'
import { useMallThemeSync } from '@/composables/useMallThemeSync'
import { useTabBarBadge } from '@/composables/useTabBarBadge'
import { useMallThemeStore } from '@/store/mallThemeStore'
import {
  getTenantGuardError,
  getTenantGuardState,
  isTenantBuild,
  onTenantGuardStateChange,
  retryTenantBinding,
} from '@/api/core/tenant-identity'
import type { TenantGuardState } from '@/api/core/tenant-identity'
import { OVERLAY_Z_INDEX } from '@/utils/overlay'

// 租户构建的启动守卫 UI：安全边界在各请求入口（instance/file/socket/pay），
// 这里只负责最早发起校验、在 blocked 时给全屏阻断界面与重试入口。
// 默认开发（generated=null）isTenantBuild() 为 false，零开销放行。
const tenantEnabled = isTenantBuild()
const tenantState = ref<TenantGuardState>(getTenantGuardState())
const tenantError = computed(() => getTenantGuardError())

const tenantErrorDescription = computed(() => {
  const error = tenantError.value
  if (!error) {
    return ''
  }
  // buildId 仅守卫错误携带（preflight 错误带 endpoint/分类），对用户只显示类型与可追溯 ID
  const buildId = (error as { buildId?: string }).buildId
  return `${error.type}${buildId ? ` · ${buildId}` : ''}`
})

if (tenantEnabled) {
  void retryTenantBinding().finally(() => {
    tenantState.value = getTenantGuardState()
  })
  // 回前台刷新在 App.vue 的应用级 onShow（App.ku.vue 是页面包装层，App 级
  // 生命周期不保证触发）；这里只订阅状态变化驱动阻断 UI
  onUnmounted(onTenantGuardStateChange((next) => {
    tenantState.value = next
  }))
}

async function onTenantRetry() {
  await retryTenantBinding()
  tenantState.value = getTenantGuardState()
}

// 底部 tabBar 购物车角标：根组件全局挂一份，watch 共享的 cartCount 即可，
// 各加购入口/页面不需要（也不允许）自己打角标。
useTabBarBadge()

// 商城默认主题：store 初始化时同步读 storage 缓存（防闪色），
// ensureLoaded 异步拉取最新值；主题色由管理端 page_design_theme
// 设为「商城默认」后经免登接口下发，全商城生效
useMallThemeSync()
const mallThemeStore = useMallThemeStore()
const themeVars = computed(() => mallThemeStore.themeVars)
</script>

<template>
  <wd-config-provider
    custom-class="page-wrapper"
    :theme="mallThemeStore.mode"
    :theme-vars="themeVars"
  >
    <ku-root-view />
    <!--
      全局反馈层显式定级（见 @/utils/overlay）：库默认只有 99/100，页面里的
      底部弹层普遍 900/901（最高 1020），提示会被盖住 → 用户看到「点了没反应」。
      这里保留 wd-toast / wd-message-box 的默认 selector 实例，并一并抬高层级。

      注意（2026-10-18 复核结论）：**不要**指望「页面里裸调 useToast()/useMessage()」
      能命中根组件这两个实例——wot-design-uni 的 useToast 在没有可 inject 的祖先时
      会自己 provide 一个新 ref，根组件这两个实例属于另一条组件树分支，收不到。
      页面里要用全局提示请走 `useGlobalToast` / `useGlobalLoading` / `useGlobalMessage`；
      现存 `pages/delivery/exception-report.vue`、`pages/user/shopping-cart/index.vue`
      的裸调用是历史遗留（前者无入口、后者「移入收藏夹」确认框可能无反应），
      与本轮改动无关，待后续统一收敛。
    -->
    <wd-notify :z-index="OVERLAY_Z_INDEX.notify" />
    <wd-message-box />
    <wd-toast :z-index="OVERLAY_Z_INDEX.toast" />
    <global-loading />
    <global-toast />
    <global-message />

    <!--
      加购目的地选择弹层（加入共享购物车 / 加入个人购物车）。

      必须挂在根组件上且全局只此一份：宿主（quick-cart-button 等）是随商品卡
      重复渲染的组件，由宿主各自挂载会让同屏出现 N 份弹层，三个可见后果——
        1. N 层 rgba(0,0,0,.65) 遮罩叠加 → 背景全黑；
        2. N 份 slide-up 动画各自跑时间线 → 弹出瞬间抖动；
        3. 弹层 DOM 落在商品卡内部，按钮 tap 冒泡到卡片的打开详情 → 两个按钮
           都跳进商详页。
      pendingChoice 是模块级单例，这里按它懒挂载，平时零节点开销；
      根组件每页一份，弹层因此在任何页面都恰好只有一个实例。

      注意 pendingChoice 同时是**跨页面**共享的：弹层只属于发起加购的那一页，
      离开所在页面（切 Tab / 返回）时由弹层自己 onHide/onUnload → abort 关掉
      （见 shared-cart-destination-sheet），否则切页后弹层会在新页面原样出现。
    -->
    <shared-cart-destination-sheet v-if="!!pendingChoice" />

    <!-- #ifdef MP-WEIXIN -->
    <privacy-popup />
    <!-- #endif -->

    <!-- 租户身份被阻断（本地一致/AppID/服务端映射任一失败）：全屏阻断，附 buildId 供追溯 -->
    <view v-if="tenantEnabled && tenantState === 'blocked'" class="tenant-blocked">
      <text class="tenant-blocked__title">应用配置暂不可用</text>
      <text class="tenant-blocked__desc">请联系管理员确认小程序与租户配置，稍后重试</text>
      <text class="tenant-blocked__meta">{{ tenantErrorDescription }}</text>
      <button class="tenant-blocked__retry" type="primary" @tap="onTenantRetry">
        重试
      </button>
    </view>
  </wd-config-provider>
</template>

<style scoped>
.tenant-blocked {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  padding: 0 64rpx;
  background: #f6f7f9;
}

.tenant-blocked__title {
  font-size: 36rpx;
  font-weight: 600;
  color: #1f2937;
}

.tenant-blocked__desc {
  font-size: 26rpx;
  color: #6b7280;
  text-align: center;
}

.tenant-blocked__meta {
  font-size: 22rpx;
  color: #9ca3af;
}

.tenant-blocked__retry {
  margin-top: 24rpx;
  width: 320rpx;
}
</style>
