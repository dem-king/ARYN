<script setup lang="ts">
import { resolveNavbarSpaceHeight } from '@/utils/navbar-space'

/**
 * 顶栏等高占位（fixed 顶栏 + z-paging 整屏滚动的列表页首行）。
 *
 * `hr-navbar` 是 fixed 覆盖层，z-paging 的滚动区铺满整屏，首屏内容需要这一条
 * 占位才从顶栏下沿开始；否则第一张卡片会压在导航栏标题上（收货地址、收藏、
 * 足迹、积分明细等 11 个列表页）。滚动后内容仍然从磨砂顶栏下穿过，观感不变。
 *
 * 高度取运行时实测的状态栏高度（不是 uni 注入的那个固定 CSS 变量）：小程序端
 * 该变量恒为 25px，刘海屏会短 20~35px —— 详见 `@/utils/navbar-space`。
 */
const { statusBarHeight } = uni.getWindowInfo()
const spaceHeight = `${resolveNavbarSpaceHeight(statusBarHeight)}px`
</script>

<template>
  <view class="hr-navbar-space" :style="{ height: spaceHeight }" />
</template>

<style lang="scss" scoped>
.hr-navbar-space {
  flex: none;
}
</style>
