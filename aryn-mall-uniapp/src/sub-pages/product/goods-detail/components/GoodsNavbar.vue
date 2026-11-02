<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { useMallThemeStore } from '@/store/mallThemeStore'

interface Props {
  scrollStatus: boolean
  isCanBack: boolean
  tabActive: number
  tabList: any[]
  background: string
  rootOpacity: number
  rootStyle: any
}

interface Emits {
  (e: 'goBack'): void
  (e: 'currentPage', item: any, index: number): void
  (e: 'share'): void
  (e: 'more'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()
const { t } = useI18n()

function goBack() {
  emit('goBack')
}

function onCurrentPage(item: any, index: number) {
  emit('currentPage', item, index)
}

function handleShare() {
  emit('share')
}

function handleMore() {
  emit('more')
}

// 计算背景色样式：滚动到位后切磨砂玻璃（与首页顶栏同一材质），静置/过渡期保持渐显白底。
// 深色模式切深玻璃（与 glass-tabbar 的 dark 材质同值 86% 暗色），不把夜间顶栏刷白
const mallThemeStore = useMallThemeStore()
const bgColorStyle = computed(() => {
  if (mallThemeStore.mode === 'dark') {
    if (props.scrollStatus)
      return { background: 'rgba(26, 26, 26, 0.86)' }
    return {
      background: `rgba(26, 26, 26, ${props.rootOpacity})`,
    }
  }
  if (props.scrollStatus)
    return { background: 'rgba(255, 255, 255, 0.86)' }
  return {
    background: `rgba(255, 255, 255, ${props.rootOpacity})`,
  }
})
</script>

<template>
  <view class="goods-detail-container">
    <!-- 顶部导航栏 -->
    <view
      class="goods-detail-navbar"
      :class="{ 'goods-detail-navbar--frosted': scrollStatus }"
      :style="bgColorStyle"
    >
      <view class="goods-detail-navbar-warp" :style="rootStyle">
        <view class="left">
          <view class="detail-header-back-icon" :class="scrollStatus ? 'detail-header-back-bg' : ''" @click="goBack">
            <wd-icon size="36rpx" :name="isCanBack ? 'arrow-left' : 'home1'" />
          </view>
        </view>
        <view class="middle" :style="{ opacity: scrollStatus ? 1 : rootOpacity }">
          <view
            v-for="(item, index) in tabList" :key="index" class="tab-item"
            :class="[index === tabActive ? 'tab-item-active text-primary!' : '']" @click="onCurrentPage(item, index)"
          >
            {{ t(item.nameKey) }}
          </view>
        </view>
        <view class="right">
          <!-- #ifndef MP -->
          <view
            class="detail-header-back-icon" style="margin-left: 20rpx"
            :class="scrollStatus ? 'detail-header-back-bg' : ''"
            @click="handleShare"
          >
            <text class="i-flowbite:forward-outline text-36rpx" />
          </view>
          <view
            class="detail-header-back-icon" style="margin-left: 20rpx" :class="scrollStatus ? 'detail-header-back-bg' : ''"
            @click="handleMore"
          >
            <text class="i-flowbite:dots-horizontal-solid text-36rpx" />
          </view>
          <!-- #endif -->
        </view>
      </view>
    </view>
    <view class="navbar-zw" />
  </view>
</template>

<style lang="scss" scoped>
.goods-detail-container {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 0;
  box-sizing: border-box;
}

.goods-detail-navbar {
  width: 100%;
  position: fixed;
  top: 0;
  left: 0;
  /* 顶部补缝线随表面色（浅色补白、深色补卡面深色） */
  box-shadow: 0 -1px var(--app-card, #fff);
  box-sizing: border-box;
  z-index: 99;

  /**
   * 磨砂玻璃态：滚动到位后整个顶栏（含状态栏区域）半透明白 + 背景模糊，
   * 商品图/详情从栏下穿过被模糊成底色。iOS 18 以下需要 -webkit- 前缀；
   * 不支持 backdrop-filter 的内核退化为 86% 白底，图标与文字仍可读。
   */
  &.goods-detail-navbar--frosted {
    box-shadow: 0 1px 0 rgba(15, 23, 42, 0.08);
    -WEBKIT-backdrop-filter: blur(18px) saturate(160%);
    backdrop-filter: blur(18px) saturate(160%);
  }

  .goods-detail-navbar-warp {
    width: 100%;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;

    .left {
      position: absolute;
      left: 10px;
    }

    .middle {
      position: absolute;
      display: flex;
      left: 50%;
      transform: translateX(-50%);
      font-size: 13px;

      .tab-item {
        margin-right: 20rpx;
      }

      .tab-item-active {
        font-weight: 700;
        position: relative;
      }
    }

    .right {
      position: absolute;
      right: 10px;
      display: flex;
    }
  }

  .detail-header-back-icon {
    background-color: rgba(0, 0, 0, 0.3);
    border-radius: 100%;
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
  }

  .detail-header-back-bg {
    background-color: rgba(0, 0, 0, 0) !important;
    /* 玻璃态下图标随语义文字色：浅色深灰、深色浅灰（值≈原 #3a3a3a） */
    color: var(--app-text, #3a3a3a) !important;
  }
}

.navbar-zw {
  /* #ifndef MP */
  height: 0px !important;
  /* #endif */
  height: calc(var(--status-bar-height) + 5px);
}
</style>
