<script setup lang="ts">
import type { ShopInfoProps } from '@/components/diy/retail-types'

import { computed } from 'vue'
import { loadCurrentShop } from '@/components/diy/retail-data'
import RetailState from '@/components/diy/retail-state.vue'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRetailData } from '@/composables/useRetailData'

const props = withDefaults(defineProps<{ showData?: Partial<ShopInfoProps> }>(), {
  showData: () => ({}),
})
const showData = computed<ShopInfoProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 1,
  dataSource: props.showData.dataSource || { mode: 'current-tenant' },
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  liftOverBanner: props.showData.liftOverBanner === true,
  showContact: props.showData.showContact !== false,
  showDescription: props.showData.showDescription !== false,
}))
const commonStyles = useDiyStyle(computed(() => showData.value.commonStyle))
// 悬浮压图：在运营设置的顶部外边距上再上移 16px（=32rpx），把店铺头卡片
// 叠到上方 banner 上。直接改根节点 marginTop 而不是内层负边距——根节点
// 圆角 > 0 时会被 commonStyle 加 overflow:hidden，内层负边距会把卡片裁掉。
const dynamicStyles = computed(() => {
  const styles = { ...commonStyles.value }
  if (showData.value.liftOverBanner) {
    const top = Number(showData.value.commonStyle?.styleTopMargin ?? 0)
    styles.marginTop = `${top - 16}px`
  }
  return styles
})
const { items, shouldRender, status } = useRetailData(showData, loadCurrentShop)
</script>

<template>
  <view v-if="shouldRender" class="shop-info" :style="dynamicStyles">
    <RetailState v-if="status !== 'ready'" :status="status" />
    <view v-else-if="items[0]" class="shop-card">
      <image v-if="items[0].logoUrl" class="shop-logo" :src="items[0].logoUrl" mode="aspectFill" lazy-load />
      <view v-else class="shop-logo shop-logo--empty">
        <wd-icon name="shop" size="44rpx" color="#ffffff" />
      </view>
      <view class="shop-main">
        <view class="shop-name">
          {{ items[0].name }}
        </view>
        <view v-if="showData.showDescription && items[0].address" class="shop-meta">
          <wd-icon name="location" size="24rpx" custom-style="color: var(--wot-color-theme-primary, #ff2237)" />
          <text class="shop-meta-text">
            {{ items[0].address }}
          </text>
        </view>
        <view v-if="showData.showDescription && items[0].phone" class="shop-meta">
          <wd-icon name="phone" size="24rpx" custom-style="color: var(--wot-color-theme-primary, #ff2237)" />
          <text class="shop-meta-text">
            {{ items[0].phone }}
          </text>
        </view>
      </view>
      <button v-if="showData.showContact" class="contact-button" open-type="contact">
        <wd-icon name="service" size="26rpx" color="#ffffff" />
        <text>联系</text>
      </button>
    </view>
  </view>
</template>

<style scoped lang="scss">
/* 根节点抬层：悬浮压图时卡片要叠在上方 banner 之上 */
.shop-info {
  position: relative;
  z-index: 1;
}

/* 店铺头卡片：白底 + 圆角 + 轻投影，与服务承诺保障条同一家族 */
.shop-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  background: #fff;
  border-radius: 24rpx;
  box-shadow: 0 12rpx 32rpx rgba(31, 35, 41, 0.06);
}

.shop-logo {
  display: flex;
  width: 88rpx;
  height: 88rpx;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 20rpx;
  background: #f2f3f5;
}

/* 无 logo 兜底：主题色渐变底 + 白色店铺图标（灰底「店」字太糙） */
.shop-logo--empty {
  background: linear-gradient(135deg, var(--wot-color-theme-primary, #ff2237), var(--wot-color-theme-secondary, #ff6b7a));
}

.shop-main {
  min-width: 0;
  flex: 1;
}

.shop-name {
  overflow: hidden;
  color: #1f2329;
  font-size: 30rpx;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shop-meta {
  display: flex;
  align-items: center;
  gap: 6rpx;
  margin-top: 8rpx;
  overflow: hidden;
}

.shop-meta-text {
  overflow: hidden;
  color: #667085;
  font-size: 22rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 微信客服按钮：主题色渐变胶囊（保留 open-type=contact 能力） */
.contact-button {
  display: flex;
  height: 60rpx;
  margin: 0;
  padding: 0 28rpx;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  border: 0;
  border-radius: 60rpx;
  background: linear-gradient(135deg, var(--wot-color-theme-primary, #ff2237), var(--wot-color-theme-secondary, #ff6b7a));
  color: #fff;
  font-size: 25rpx;
  font-weight: 500;
  line-height: normal;
  box-shadow: 0 8rpx 20rpx rgba(31, 35, 41, 0.12);
}

.contact-button::after {
  border: 0;
}
</style>
