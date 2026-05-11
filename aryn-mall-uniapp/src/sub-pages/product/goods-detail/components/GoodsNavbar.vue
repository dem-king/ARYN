<script setup lang="ts">
import { computed } from 'vue'

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

// 计算背景色样式，根据暗黑模式和透明度设置
const bgColorStyle = computed(() => {
  return {
    background: `rgba(255, 255, 255, ${props.rootOpacity})`,
  }
})
</script>

<template>
  <view class="goods-detail-container">
    <!-- 顶部导航栏 -->
    <view class="goods-detail-navbar" :style="bgColorStyle">
      <view class="goods-detail-navbar-warp" :style="rootStyle">
        <view class="left">
          <view class="detail-header-back-icon" :class="scrollStatus ? 'detail-header-back-bg' : ''" @click="goBack">
            <wd-icon size="36rpx" :name="isCanBack ? 'arrow-left' : 'home1'" />
          </view>
        </view>
        <view class="middle" :style="{ opacity: rootOpacity }">
          <view
            v-for="(item, index) in tabList" :key="index" class="tab-item"
            :class="[index === tabActive ? 'tab-item-active text-primary!' : '']" @click="onCurrentPage(item, index)"
          >
            {{ item.name }}
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
  box-shadow: 0 -1px #fff;
  box-sizing: border-box;
  z-index: 99;

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
    color: #3a3a3a !important;
  }
}

.navbar-zw {
  /* #ifndef MP */
  height: 0px !important;
  /* #endif */
  height: calc(var(--status-bar-height) + 5px);
}
</style>
