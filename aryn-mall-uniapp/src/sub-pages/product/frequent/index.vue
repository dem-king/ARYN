<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { getFrequentPurchase } from '@/api/order/orderInfo'
import { getQuickCartInfo } from '@/api/product/spu'
import hrNavbar from '@/components/hr-navbar/index.vue'
import { useCartDestination } from '@/composables/useCartDestination'
import { resolveQuantityRuleForSku } from '@/utils/quick-cart'

definePage({
  name: 'frequent-purchase',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '常购清单',
  },
})

const { submitCartAdd } = useCartDestination()

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

/**
 * 回加常购商品：按历史数量预填，并允许在确认弹层里改数量。
 *
 * 常购清单只有 SKU 快照，拿不到 MOQ/步长；这里补查一次快捷加购信息，并按 skuId
 * 精确取规则（多规格 SPU 下不同 SKU 的起订量可以不同，不能拿 SPU 顶层字段顶替）。
 * **查不到就不传规则**（弹层只问去向、数量按历史值）——「再来一份」在缺资料时
 * 仍要可用，不能因为一次增强查询失败就拦住加购。查询异常同样静默降级。
 */
async function handleAddToCart(item: any) {
  const quantity = item.totalQuantity && item.totalQuantity > 0 ? item.totalQuantity : 1
  let rule = null
  if (item.spuId) {
    try {
      rule = resolveQuantityRuleForSku(await getQuickCartInfo(String(item.spuId)), item.skuId)
    }
    catch {
      rule = null
    }
  }
  try {
    await submitCartAdd(
      {
        skuId: item.skuId,
        quantity,
        spuId: item.spuId,
        addType: '2',
        goodsName: item.spuName,
      },
      { rule },
    )
  }
  catch {
    // 请求异常已由统一拦截器提示
  }
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
            class="h-56rpx text-24rpx leading-56rpx !m-0 !px-24rpx"
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
