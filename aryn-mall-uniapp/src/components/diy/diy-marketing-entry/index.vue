<script setup lang="ts">
import { computed } from 'vue'

import { createDecorationLinkAction, followDecorationLink } from '@/components/diy/link-resolver'
import { retailCommonStyle } from '@/components/diy/retail-types'
import type { MarketingEntry, MarketingEntryProps } from '@/components/diy/retail-types'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = withDefaults(defineProps<{ showData?: Partial<MarketingEntryProps> }>(), {
  showData: () => ({}),
})
const showData = computed<MarketingEntryProps>(() => ({
  columns: props.showData.columns === 5 ? 5 : 4,
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 4,
  dataSource: props.showData.dataSource || { mode: 'manual' },
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  entries: (props.showData.entries || []).slice(0, Number(props.showData.count) || 4),
  invalidStrategy: props.showData.invalidStrategy || 'hide',
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const itemStyle = computed(() => ({ width: `${100 / showData.value.columns}%` }))
const shouldRender = computed(() => showData.value.entries.length > 0 || showData.value.emptyStrategy !== 'hide')

function isContact(entry: MarketingEntry) {
  return createDecorationLinkAction(entry.link).kind === 'contact'
}
</script>

<template>
  <view v-if="shouldRender" class="marketing-entry" :style="dynamicStyles">
    <view v-if="showData.entries.length" class="entry-grid">
      <view v-for="entry in showData.entries" :key="entry.id" class="entry-cell" :style="itemStyle">
        <button v-if="isContact(entry)" class="entry-button" open-type="contact">
          <image v-if="entry.iconUrl" class="entry-icon" :src="entry.iconUrl" mode="aspectFill" />
          <view v-else class="entry-icon entry-icon--empty">{{ entry.title.slice(0, 1) }}</view>
          <text class="entry-title">{{ entry.title }}</text>
        </button>
        <view v-else class="entry-button" @click="followDecorationLink(entry.link)">
          <image v-if="entry.iconUrl" class="entry-icon" :src="entry.iconUrl" mode="aspectFill" />
          <view v-else class="entry-icon entry-icon--empty">{{ entry.title.slice(0, 1) }}</view>
          <text class="entry-title">{{ entry.title }}</text>
        </view>
      </view>
    </view>
    <view v-else class="entry-empty">暂无入口</view>
  </view>
</template>

<style scoped lang="scss">
.entry-grid { display: flex; flex-wrap: wrap; }
.entry-cell { box-sizing: border-box; padding: 10rpx 6rpx; }
.entry-button { display: flex; width: 100%; margin: 0; padding: 0; flex-direction: column; align-items: center; border: 0; background: transparent; line-height: normal; }
.entry-button::after { border: 0; }
.entry-icon { display: flex; width: 84rpx; height: 84rpx; align-items: center; justify-content: center; border-radius: 8rpx; background: #f2f3f5; color: #606266; font-size: 30rpx; }
.entry-title { overflow: hidden; max-width: 100%; margin-top: 12rpx; color: #303133; font-size: 23rpx; text-overflow: ellipsis; white-space: nowrap; }
.entry-empty { padding: 30rpx 0; color: #909399; font-size: 24rpx; text-align: center; }
</style>
