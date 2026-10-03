<script setup lang="ts">
import { pendingChoice } from '@/composables/useCartDestination'
import { useMallThemeSync } from '@/composables/useMallThemeSync'
import { useTabBarBadge } from '@/composables/useTabBarBadge'
import { useMallThemeStore } from '@/store/mallThemeStore'

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
    <wd-notify />
    <wd-message-box />
    <wd-toast />
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
  </wd-config-provider>
</template>
