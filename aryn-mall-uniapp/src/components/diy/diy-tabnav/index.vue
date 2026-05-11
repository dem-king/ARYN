<script setup lang="ts">
import { computed, ref } from 'vue'
import { toJumpUrl } from '@/utils/index'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const current = ref(0)

const baseStyle = useDiyStyle(computed(() => props.showData.commonStyle))
const dynamicStyles = computed(() => {
  return {
    ...baseStyle.value,
    color: props.showData.fontColor,
  }
})
const dynamicTabNavStyles = useDiyStyle(
  computed(() => props.showData.commonTabNavStyle),
)
const imageStyle = computed(() => ({
  width: `${props.showData.imgSize}px`,
  height: `${props.showData.imgSize}px`,
  borderRadius: `${props.showData.imgRadius}px`,
}))
</script>

<template>
  <view :style="dynamicStyles">
    <!-- tabs -->
    <wd-tabs v-if="showData.type === '2'" v-model="current">
      <block v-for="item in showData.navList" :key="item">
        <wd-tab :title="item.title" :name="item.title" />
      </block>
    </wd-tabs>

    <!-- 宫格 -->
    <view v-else-if="showData.type === '3' || showData.type === '1'" class="hx-grid">
      <!-- 横向滚动 -->
      <scroll-view v-if="showData.scrollShow" scroll-x class="hx-grid-scroll" :enhanced="true" :show-scrollbar="false">
        <view class="scroll-content">
          <view
            v-for="(item, index) in showData.navList" :key="index" class="scroll-item" :style="dynamicTabNavStyles"
            @click="toJumpUrl(item.link.url)"
          >
            <view class="grid-item-inner" :class="[showData.showType === '1' ? 'row-direction' : 'column-direction']">
              <image :src="item.url" :style="imageStyle" />
              <view v-if="showData.type === '3' && item.title" class="grid-text">
                {{ item.title }}
              </view>
            </view>
          </view>
        </view>
      </scroll-view>

      <!-- 固定宫格 -->
      <view v-else class="hx-grid-list">
        <view
          v-for="(item, index) in showData.navList" :key="index" class="hx-grid-item"
          :style="{ width: `${100 / showData.showNum}%` }" @click="toJumpUrl(item.link.url)"
        >
          <view
            :style="dynamicTabNavStyles" class="grid-item-inner"
            :class="[showData.showType === '1' ? 'row-direction' : 'column-direction']"
          >
            <image :src="item.url" :style="imageStyle" />
            <view v-if="showData.type === '3' && item.title" class="grid-text">
              {{ item.title }}
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.hx-grid {
  width: 100%;

  &-scroll {
    width: 100%;
    overflow-x: auto;
    white-space: nowrap;

    .scroll-content {
      display: flex;
      white-space: nowrap;
    }

    .scroll-item {
      flex-shrink: 0;
      display: inline-flex;
      padding: 0 8rpx;

      .grid-item-inner {
        display: flex;
        align-items: center;
        justify-content: center;

        &.row-direction {
          flex-direction: row;
          gap: 8rpx;
        }

        &.column-direction {
          flex-direction: column;
        }

        image {
          display: block;
        }

        .grid-text {
          font-size: 12px;
          margin-top: 10rpx;
          text-align: center;
        }
      }
    }
  }

  &-list {
    display: flex;
    flex-wrap: wrap;

    .hx-grid-item {
      display: flex;
      align-items: center;
      justify-content: center;

      .grid-item-inner {
        display: flex;
        align-items: center;
        justify-content: center;

        &.row-direction {
          flex-direction: row;
          gap: 8rpx;
        }

        &.column-direction {
          flex-direction: column;
        }

        image {
          display: block;
        }

        .grid-text {
           font-size: 12px;
          margin-top: 10rpx;
          text-align: center;
        }
      }
    }
  }
}
</style>
