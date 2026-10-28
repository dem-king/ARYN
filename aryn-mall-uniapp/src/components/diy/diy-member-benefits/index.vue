<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { followDecorationLink } from '@/components/diy/link-resolver'
import type { MemberBenefitsProps } from '@/components/diy/retail-types'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const { t } = useI18n()
const componentProps = computed(() => props.showData as unknown as MemberBenefitsProps)
const entries = computed(() => componentProps.value.entries || [])
const dynamicStyles = useDiyStyle(computed(() => componentProps.value.commonStyle))
</script>

<template>
  <view class="diy-member-benefits" :style="dynamicStyles">
    <view class="benefits-inner">
      <view v-if="componentProps.title" class="benefits-title">
        {{ componentProps.title }}
      </view>
      <view v-if="entries.length === 0" class="benefits-empty">
        {{ t('diy.memberBenefits.empty') }}
      </view>
      <view v-else class="benefits-grid">
        <view
          v-for="entry in entries"
          :key="entry.id"
          class="benefit-item"
          @click="followDecorationLink(entry.link)"
        >
          <image v-if="entry.iconUrl" class="benefit-icon" :src="entry.iconUrl" />
          <view v-else class="benefit-icon benefit-icon-fallback">{{ t('diy.memberBenefits.fallbackIcon') }}</view>
          <view class="benefit-text">
            <view class="benefit-name">
              {{ entry.title }}
            </view>
            <view v-if="entry.description" class="benefit-desc">
              {{ entry.description }}
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 留白放在内层而不是根节点：根节点的 padding 会被装修 commonStyle 的
   内联样式覆盖成 0（配置缺省即零值），挂根上会让卡片直接顶到屏幕边缘 */
.benefits-inner {
  padding: 20rpx;
}

.benefits-title {
  margin-bottom: 16rpx;
  font-size: 32rpx;
  font-weight: 600;
}

.benefits-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
}

.benefit-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx;
  background: #fff;
  border-radius: 12rpx;
}

.benefit-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 12rpx;
}

.benefit-icon-fallback {
  display: grid;
  place-items: center;
  color: #b8860b;
  font-size: 28rpx;
  background: #fdf3e0;
}

.benefit-name {
  font-size: 26rpx;
  font-weight: 600;
}

.benefit-desc {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #999;
}

.benefits-empty {
  padding: 40rpx;
  color: #999;
  font-size: 26rpx;
  text-align: center;
}
</style>
