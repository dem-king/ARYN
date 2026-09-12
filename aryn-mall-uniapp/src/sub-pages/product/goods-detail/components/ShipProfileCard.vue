<script setup lang="ts">
/**
 * 船供资料卡片（商品详情页）：显示采购单位/箱规/MOQ/步长/储存条件与内部配送说明。
 * 无船供资料时不渲染。
 */
import { ref } from 'vue'

import { alovaInstance } from '@/api/core/instance'

const props = defineProps<{ spuId: string }>()

const profile = ref<any>(null)
const skuProfiles = ref<any[]>([])

const storageLabels: Record<string, string> = {
  '1': '常温',
  '2': '冷藏',
  '3': '冷冻',
  '4': '危险品',
  '5': '其他',
}

function load() {
  if (!props.spuId) return
  alovaInstance
    .Get<any>(`/product/app/goodsspu/ship-summary/${props.spuId}`)
    .then((res) => {
      profile.value = res?.profile ?? null
      skuProfiles.value = res?.skuProfiles ?? []
    })
    .catch(() => {})
}

load()

function firstSkuField(field: string) {
  for (const sku of skuProfiles.value) {
    if (sku[field] != null && sku[field] !== '') return sku[field]
  }
  return ''
}
</script>

<template>
  <view
    v-if="profile"
    class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-24rpx"
  >
    <view class="text-28rpx font-bold">船供资料</view>
    <view class="mt-10rpx flex flex-wrap text-24rpx text-gray-600">
      <text v-if="profile.impaCode" class="mr-24rpx">IMPA: {{ profile.impaCode }}</text>
      <text v-if="profile.issaCode" class="mr-24rpx">ISSA: {{ profile.issaCode }}</text>
      <text v-if="profile.internalItemCode" class="mr-24rpx">编码: {{ profile.internalItemCode }}</text>
      <text v-if="profile.storageType">储存: {{ storageLabels[profile.storageType] ?? profile.storageType }}</text>
    </view>
    <view class="mt-6rpx flex flex-wrap text-24rpx text-gray-600">
      <text v-if="firstSkuField('purchaseUnit')" class="mr-24rpx">采购单位: {{ firstSkuField('purchaseUnit') }}</text>
      <text v-if="firstSkuField('packageSpec')" class="mr-24rpx">箱规: {{ firstSkuField('packageSpec') }}</text>
      <text v-if="firstSkuField('moq')" class="mr-24rpx">起订: {{ firstSkuField('moq') }}</text>
      <text v-if="firstSkuField('stepQty')">步长: {{ firstSkuField('stepQty') }}</text>
    </view>
    <view class="mt-10rpx text-22rpx text-green-600">
      公司司机按靠港计划送达港口/船舶
    </view>
  </view>
</template>
