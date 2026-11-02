<script lang="ts" setup>
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'

import GlassTabBar from '@/components/glass-tabbar/index.vue'
import { resolveGlassTabKey } from '@/components/glass-tabbar/tabs'

/**
 * tab 页布局：小程序端在页面之上再叠一条自绘玻璃底栏（H5 的原生 tabBar 由全局样式加材质）。
 *
 * 活动 tab 按当前页面路由解析：每个 tab 页各有一份布局实例，switchTab 后
 * 新页面的布局会重新解析，不会出现「切了 tab 还高亮旧项」。onShow 再兜一次，
 * 覆盖 H5 KeepAlive 保活与小程序页面栈复用的情况。
 */
function currentRoute() {
  const pages = getCurrentPages()
  return pages[pages.length - 1]?.route ?? ''
}

const activeTab = ref(resolveGlassTabKey(currentRoute()))

onShow(() => {
  activeTab.value = resolveGlassTabKey(currentRoute())
})
</script>

<script lang="ts">
export default {
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: 'shared',
  },
}
</script>

<template>
  <slot />
  <!-- #ifdef MP-WEIXIN -->
  <GlassTabBar :active="activeTab" />
  <!-- #endif -->
</template>
