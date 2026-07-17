<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const placeholder = computed(() => props.showData.placeholder || '搜索商品')
const style = computed(() => props.showData.style || '1')
const bgColor = computed(() => props.showData.bgColor || '#f5f5f5')
const showScan = computed(() => props.showData.showScan !== false)

const borderRadius = computed(() => (style.value === '1' ? '20px' : '4px'))

function handleSearch() {
  uni.navigateTo({ url: '/sub-pages/product/goods-search/index' })
}
</script>

<template>
  <view class="search-bar" @click="handleSearch">
    <view class="search-bar-inner" :style="{ backgroundColor: bgColor, borderRadius }">
      <wd-icon name="search" size="16px" color="#999" />
      <text class="search-bar-placeholder">
        {{ placeholder }}
      </text>
    </view>
    <view v-if="showScan" class="search-bar-scan" @click.stop="handleSearch">
      <wd-icon name="scan" size="22px" color="#333" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.search-bar {
  display: flex;
  align-items: center;
  padding: 6px 12px;

  .search-bar-inner {
    flex: 1;
    display: flex;
    align-items: center;
    height: 36px;
    padding: 0 12px;

    .search-bar-placeholder {
      margin-left: 6px;
      font-size: 14px;
      color: #999;
    }
  }

  .search-bar-scan {
    margin-left: 10px;
    display: flex;
    align-items: center;
  }
}
</style>
