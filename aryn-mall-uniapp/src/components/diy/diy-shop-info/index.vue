<script setup lang="ts">
import { computed } from 'vue'

import { loadCurrentShop } from '@/components/diy/retail-data'
import { retailCommonStyle } from '@/components/diy/retail-types'
import type { ShopInfoProps } from '@/components/diy/retail-types'
import RetailState from '@/components/diy/retail-state.vue'
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
  showContact: props.showData.showContact !== false,
  showDescription: props.showData.showDescription !== false,
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const { items, shouldRender, status } = useRetailData(showData, loadCurrentShop)
</script>

<template>
  <view v-if="shouldRender" class="shop-info" :style="dynamicStyles">
    <RetailState v-if="status !== 'ready'" :status="status" />
    <view v-else-if="items[0]" class="shop-content">
      <image v-if="items[0].logoUrl" class="shop-logo" :src="items[0].logoUrl" mode="aspectFill" />
      <view v-else class="shop-logo shop-logo--empty">店</view>
      <view class="shop-main">
        <view class="shop-name">{{ items[0].name }}</view>
        <view v-if="showData.showDescription && items[0].address" class="shop-description">{{ items[0].address }}</view>
        <view v-if="showData.showDescription && items[0].phone" class="shop-phone">{{ items[0].phone }}</view>
      </view>
      <button v-if="showData.showContact" class="contact-button" open-type="contact">联系</button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.shop-content { display: flex; min-height: 112rpx; align-items: center; gap: 18rpx; }
.shop-logo { display: flex; width: 96rpx; height: 96rpx; flex: 0 0 auto; align-items: center; justify-content: center; border-radius: 8rpx; background: #f2f3f5; color: #606266; font-size: 34rpx; }
.shop-main { min-width: 0; flex: 1; }
.shop-name { overflow: hidden; color: #1f2329; font-size: 29rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.shop-description, .shop-phone { overflow: hidden; margin-top: 8rpx; color: #909399; font-size: 22rpx; text-overflow: ellipsis; white-space: nowrap; }
.contact-button { width: 112rpx; height: 56rpx; margin: 0; padding: 0; border: 1rpx solid #dcdfe6; border-radius: 8rpx; background: #fff; color: #303133; font-size: 23rpx; line-height: 54rpx; }
.contact-button::after { border: 0; }
</style>
