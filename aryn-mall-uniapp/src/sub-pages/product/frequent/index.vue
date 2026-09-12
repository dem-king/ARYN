<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { addShoppingCart } from '@/api/order/shoppingCart'
import { getFrequentPurchase } from '@/api/order/orderInfo'
import hrNavbar from '@/components/hr-navbar/index.vue'

definePage({
  name: 'frequent-purchase',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '常购清单',
  },
})

const loading = ref(false)
const records = ref<any[]>([])

function fetchPage() {
  loading.value = true
  getFrequentPurchase()
    .then((res) => {
      records.value = res ?? []
    })
    .finally(() => {
      loading.value = false
    })
}

function handleAddToCart(item: any) {
  const quantity = item.totalQuantity && item.totalQuantity > 0 ? item.totalQuantity : 1
  addShoppingCart({
    skuId: item.skuId,
    quantity,
    addType: '2',
  })
    .then(() => {
      uni.showToast({ title: '已加入购物车', icon: 'success' })
    })
    .catch(() => {})
}

onMounted(fetchPage)
</script>

<template>
  <view>
    <hr-navbar title="常购清单" />
    <view
      v-for="item in records"
      :key="item.skuId"
      class="mx-20rpx mt-20rpx flex rounded-20rpx bg-white p-24rpx"
    >
      <image
        v-if="item.picUrl"
        :src="item.picUrl"
        class="h-120rpx w-120rpx flex-none rounded-lg"
        mode="aspectFill"
      />
      <view class="ml-20rpx flex flex-1 flex-col overflow-hidden">
        <view class="text-28rpx font-bold">
          {{ item.spuName }}
        </view>
        <view class="text-24rpx text-gray-500">
          {{ item.specsInfo }}
        </view>
        <view class="mt-6rpx text-22rpx text-gray-400">
          近90天购买 {{ item.totalQuantity }} 件 / {{ item.orderCount }} 次
        </view>
        <view class="mt-12rpx flex items-center justify-end">
          <button
            class="!m-0 h-56rpx !px-24rpx text-24rpx leading-56rpx"
            type="primary"
            size="mini"
            @tap="handleAddToCart(item)"
          >
            再来一份
          </button>
        </view>
      </view>
    </view>
    <view v-if="!loading && records.length === 0" class="py-80rpx text-center text-26rpx text-gray-400">
      暂无常购商品，下单后自动统计
    </view>
  </view>
</template>
