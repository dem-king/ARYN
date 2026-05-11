<script setup lang="ts">
import { computed } from 'vue'
import { toJumpUrl } from '@/utils/index'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => { },
  },
})

const baseStyle = useDiyStyle(computed(() => props.showData.commonStyle))
const dynamicStyles = computed(() => {
  const baseHeight = props.showData?.height ?? 0
  const imageCount = props.showData.imageList?.length || 1
  const totalHeight = props.showData.type === '1' ? baseHeight * imageCount : baseHeight
  return {
    ...baseStyle.value,
    minHeight: `${Math.max(totalHeight, 0)}px`,
  }
})
const imageBaseStyle = useDiyStyle(
  computed(() => props.showData.commonImageStyle),
)
const dynamicImageStyles = computed(() => {
  return {
    ...imageBaseStyle.value,
    height: `${Math.max(props.showData.height, 0)}px`,
  }
})
function handleSwiper(obj: any) {
  toJumpUrl(obj.link.url)
}
</script>

<template>
  <view class="image-box" :style="dynamicStyles">
    <view>
      <view v-if="showData.imageList && showData.imageList.length > 0" class="image-view-box">
        <view v-if="showData.type === '1'">
          <view v-for="(item, index) in showData.imageList" :key="index" :style="dynamicImageStyles">
            <image
              style="width: 100%; height: 100%; display: block;" :src="item.url"
              :style="{ borderRadius: `${showData.imgRadius}px` }" @click="toJumpUrl(item.link.url)"
            />
          </view>
        </view>
        <view v-else class="card-swiper">
          <!-- 轮播图  uniapp swiper -->
          <swiper
            :class="showData.swiperType === '2' ? 'card-swiper-container' : ''" circular autoplay
            :interval="showData.interval * 1000" :indicator-dots="showData.indicatorDots"
            :indicator-color="showData.indicatorColor"
            :indicator-active-color="showData.indicatorActiveColor"
            :previous-margin="showData.swiperType === '2' ? '50px' : '0px'"
            :next-margin="showData.swiperType === '2' ? '50px' : '0px'"
            :style="{ height: `${showData.height}px` }"
          >
            <swiper-item
              v-for="(item, index) in showData.imageList" :key="index"
              @click="handleSwiper(item)"
            >
              <image
                :src="item.url" mode="scaleToFill"
                :style="{ width: '100%', height: '100%', borderRadius: `${showData.imgRadius}px` }"
              />
            </swiper-item>
          </swiper>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.image-box {
  .cursor {
    cursor: pointer;
    font-size: 12px;
    color: #fff;

    &.color {
      color: #2c3e50;
    }
  }

  img {
    width: 100%;
    user-select: none;
  }

  .image-view-box {
    // position: relative;
    overflow: hidden;

    &.ad-view-2,
    &.ad-view-3 {
      white-space: nowrap;
      display: -webkit-box;

      .image-view-item {
        width: 100%;
      }
    }

    &.ad-view-3 {
      .image-view-item {
        width: 40%;
      }
    }
  }

  .image-view-item {
    position: relative;
    height: 135px;
    overflow: hidden;
    font-size: 12px;
    background-color: #ebf8fd;
    background-repeat: no-repeat;
    background-size: cover;
    background-position: center;
    color: #88c4dc;
    text-align: center;

    .image-title {
      line-height: 135px;
    }
  }
}

.card-swiper {
  width: 100%;

  &-container {
    swiper-item {
      padding: 0 10rpx;
      box-sizing: border-box;
    }
  }
}
</style>
