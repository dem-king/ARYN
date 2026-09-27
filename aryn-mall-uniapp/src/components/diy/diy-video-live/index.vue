<script setup lang="ts">
import type { VideoLiveProps } from '@/components/diy/retail-types'

import { computed } from 'vue'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const componentProps = computed(() => props.showData as unknown as VideoLiveProps)
const dynamicStyles = useDiyStyle(computed(() => componentProps.value.commonStyle))

function enterLive() {
  // 直播承载页 `/sub-pages/promotion/live/index` **尚未实现**（全仓无该页面）。
  // 此前这里照样拼出该路径跳转，必然失败并触发微信
  // `routeDone with a webviewId ... is not found`。
  // 在承载页落地前，明确提示而不是跳一个不存在的页面。
  if (!componentProps.value.liveId) {
    uni.showToast({ title: '暂无可观看的直播', icon: 'none' })
    return
  }
  uni.showToast({ title: '直播功能即将开放', icon: 'none' })
}
</script>

<template>
  <view class="diy-video-live" :style="dynamicStyles">
    <view v-if="componentProps.title" class="videolive-title">
      {{ componentProps.title }}
    </view>
    <template v-if="componentProps.mode === 'video' && componentProps.videoUrl">
      <video
        class="videolive-video"
        :src="componentProps.videoUrl"
        :poster="componentProps.coverUrl || undefined"
        controls
      />
    </template>
    <view
      v-else-if="componentProps.mode === 'live' || (!componentProps.videoUrl && componentProps.coverUrl)"
      class="videolive-cover"
      @click="enterLive"
    >
      <image
        v-if="componentProps.coverUrl"
        class="videolive-cover-img"
        :src="componentProps.coverUrl"
        mode="aspectFill"
      />
      <view v-else class="videolive-cover-fallback">
        直播入口
      </view>
      <view class="videolive-live-tag">
        直播
      </view>
    </view>
    <view v-else class="videolive-empty">
      请在装修中配置视频地址或直播入口
    </view>
  </view>
</template>

<style lang="scss" scoped>
.diy-video-live {
  padding: 20rpx;
}

.videolive-title {
  margin-bottom: 16rpx;
  font-size: 32rpx;
  font-weight: 600;
}

.videolive-video {
  width: 100%;
  height: 380rpx;
  border-radius: 12rpx;
}

.videolive-cover {
  position: relative;
  overflow: hidden;
  border-radius: 12rpx;
}

.videolive-cover-img {
  width: 100%;
  height: 380rpx;
}

.videolive-cover-fallback {
  display: grid;
  height: 380rpx;
  place-items: center;
  color: #fff;
  font-size: 32rpx;
  background: linear-gradient(135deg, #ff5000, #ff8a00);
}

.videolive-live-tag {
  position: absolute;
  top: 16rpx;
  left: 16rpx;
  padding: 4rpx 16rpx;
  color: #fff;
  font-size: 22rpx;
  background: rgb(255 80 0 / 90%);
  border-radius: 999rpx;
}

.videolive-empty {
  padding: 40rpx;
  color: #999;
  font-size: 26rpx;
  text-align: center;
}
</style>
