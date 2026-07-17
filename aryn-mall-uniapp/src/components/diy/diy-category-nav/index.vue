<script setup lang="ts">
import { computed } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const navList = computed(() => props.showData.navList || [])
const showNum = computed(() => props.showData.showNum || 4)
const imgSize = computed(() => props.showData.imgSize || 25)
const imgRadius = computed(() => props.showData.imgRadius || 0)
const fontColor = computed(() => props.showData.fontColor || '#333')
const scrollShow = computed(() => props.showData.scrollShow === true)
const dynamicStyles = useDiyStyle(computed(() => props.showData.commonStyle))

const itemWidth = computed(() => `${100 / showNum.value}%`)

function handleClick(item: any) {
  followDecorationLink(item.link || item.linkUrl)
}
</script>

<template>
  <view class="category-nav" :style="dynamicStyles">
    <scroll-view v-if="scrollShow" scroll-x class="category-nav-scroll">
      <view class="category-nav-grid">
        <view
          v-for="(item, index) in navList"
          :key="index"
          class="category-nav-item"
          :style="{ width: itemWidth }"
          @click="handleClick(item)"
        >
          <image
            v-if="item.url"
            :src="item.url"
            mode="aspectFit"
            :style="{
              width: `${imgSize}px`,
              height: `${imgSize}px`,
              borderRadius: `${imgRadius}px`,
            }"
          />
          <text class="category-nav-text" :style="{ color: fontColor }">
            {{ item.text }}
          </text>
        </view>
      </view>
    </scroll-view>
    <view v-else class="category-nav-grid">
      <view
        v-for="(item, index) in navList"
        :key="index"
        class="category-nav-item"
        :style="{ width: itemWidth }"
        @click="handleClick(item)"
      >
        <image
          v-if="item.url"
          :src="item.url"
          mode="aspectFit"
          :style="{
            width: `${imgSize}px`,
            height: `${imgSize}px`,
            borderRadius: `${imgRadius}px`,
          }"
        />
        <text class="category-nav-text" :style="{ color: fontColor }">
          {{ item.text }}
        </text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.category-nav {
  padding: 12px 0;

  .category-nav-scroll {
    white-space: nowrap;
  }

  .category-nav-grid {
    display: flex;
    flex-wrap: wrap;

    .category-nav-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 8px 0;

      .category-nav-text {
        margin-top: 6px;
        font-size: 12px;
        color: #333;
        text-align: center;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 100%;
      }
    }
  }
}
</style>
