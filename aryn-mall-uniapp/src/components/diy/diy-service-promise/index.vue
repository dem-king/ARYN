<script setup lang="ts">
import type { ServicePromiseProps } from '@/components/diy/retail-types'

import { computed } from 'vue'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const componentProps = computed(() => props.showData as unknown as ServicePromiseProps)
const items = computed(() => componentProps.value.items || [])
const dynamicStyles = useDiyStyle(computed(() => componentProps.value.commonStyle))

/**
 * iconUrl 为空时的内置图标兜底：按承诺文案关键词映射 wd-icon，
 * 未命中回落对勾。装修设置项没有图标上传入口，iconUrl 实际恒为空，
 * 没有这层映射保障条就只剩裸文字。管理端画布按同一关键词表映射
 * element-plus 图标（preview-parity.test.ts 守卫两端同表）。
 */
function fallbackIcon(title: string): string {
  if (/退|换/.test(title))
    return 'refresh'
  if (/[保正质]/.test(title))
    return 'secured'
  if (/服|客|售后/.test(title))
    return 'service'
  if (/[速快时达]/.test(title))
    return 'time'
  if (/票|结算|款|支付/.test(title))
    return 'wallet'
  return 'check-circle'
}
</script>

<template>
  <view class="diy-service-promise" :style="dynamicStyles">
    <view class="promise-inner">
      <view v-if="componentProps.title" class="promise-title">
        {{ componentProps.title }}
      </view>
      <view v-if="items.length === 0" class="promise-empty">
        暂无服务承诺，请在装修中配置
      </view>
      <view v-else class="promise-row">
        <view v-for="item in items" :key="item.id" class="promise-item">
          <view class="promise-badge">
            <image v-if="item.iconUrl" class="promise-badge-image" :src="item.iconUrl" mode="aspectFit" />
            <wd-icon v-else :name="fallbackIcon(item.title)" size="32rpx" custom-style="color: var(--wot-color-theme-primary, #ff2237)" />
          </view>
          <view class="promise-text">
            {{ item.title }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 留白放在内层而不是根节点：根节点的 padding 会被装修 commonStyle 的
   内联样式覆盖成 0（配置缺省即零值），挂根上会让卡片直接顶到屏幕边缘。
   内层同时是保障条白卡本体：底色/圆角/投影与店铺头卡片同一家族。 */
.promise-inner {
  padding: 20rpx;
  background: #fff;
  border-radius: 24rpx;
  box-shadow: 0 12rpx 32rpx rgba(31, 35, 41, 0.06);
}

.promise-title {
  margin-bottom: 16rpx;
  color: #1f2329;
  font-size: 26rpx;
  font-weight: 600;
}

/* 信任背书条：一行均分，超过一行的量自动换行仍均分 */
.promise-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-around;
  row-gap: 16rpx;
}

.promise-item {
  display: flex;
  min-width: 120rpx;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: center;
}

/* 圆底徽标：主题色 9% tint，iconUrl 有图用图、无图走内置图标 */
.promise-badge {
  display: flex;
  width: 64rpx;
  height: 64rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--wot-color-theme-primary-soft, rgba(255, 34, 55, 0.09));
}

.promise-badge-image {
  width: 40rpx;
  height: 40rpx;
}

.promise-text {
  margin-top: 8rpx;
  color: #606266;
  font-size: 22rpx;
  text-align: center;
}

.promise-empty {
  padding: 40rpx;
  color: #999;
  font-size: 26rpx;
  text-align: center;
}
</style>
