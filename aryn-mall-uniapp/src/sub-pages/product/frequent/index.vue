<script setup lang="ts">
import { ref } from 'vue'
import { onPageScroll, onShow } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'

import { getFrequentPurchase } from '@/api/order/orderInfo'
import { getQuickCartInfo } from '@/api/product/spu'
import hrNavbar from '@/components/hr-navbar/index.vue'
import { useCartDestination } from '@/composables/useCartDestination'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { useAuthStore } from '@/store/authStore'
import { resolveQuantityRuleForSku } from '@/utils/quick-cart'
import { Local } from '@/utils/storage'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'frequent-purchase',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '常购清单',
  },
})

const { submitCartAdd } = useCartDestination()
const { t } = useI18n()
const authStore = useAuthStore()

const loading = ref(false)
const records = ref<any[]>([])

function fetchPage() {
  // 常购接口需要有效 token：游客（含登录态已失效）不请求，否则会拿到 401，
  // 以前会被请求层误判成「登录状态已失效」。直接展示登录引导。
  if (!authStore.isLoggedIn) {
    records.value = []
    loading.value = false
    return
  }
  loading.value = true
  getFrequentPurchase()
    .then((res) => {
      records.value = res ?? []
    })
    .catch(() => {
      // 请求异常已由统一拦截器提示
    })
    .finally(() => {
      loading.value = false
    })
}

/** 游客引导登录：记录来源页，登录成功后回跳常购清单（登录页读取 redirectPath）。 */
function goLogin() {
  Local.set('redirectPath', '/sub-pages/product/frequent/index')
  uni.navigateTo({ url: '/pages/login/index' })
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

// 常购清单是需登录页面：购物车空态的裸 `uni.navigateTo` 会绕过 router 守卫，
// 游客也能进来，所以守卫必须落在页面自身（未登录不请求）；登录后返回本页时
// onShow 会再次触发，保证登录成功回跳后能自动加载。
onShow(() => {
  if (authStore.isLoggedIn)
    fetchPage()
})

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
</script>

<template>
  <view>
    <hr-navbar :title="t('catalog.frequentTitle')" />
    <!-- 游客态：登录引导。不能落到「暂无常购商品」空态，更不能请求需登录接口 -->
    <view v-if="!authStore.isLoggedIn" class="flex flex-col items-center px-64rpx pt-160rpx text-center">
      <text class="text-26rpx leading-40rpx text-gray-400">
        {{ t('catalog.frequentGuestTip') }}
      </text>
      <view
        class="mt-40rpx flex h-76rpx w-320rpx items-center justify-center rounded-full text-28rpx font-medium text-white"
        :style="{ backgroundColor: 'var(--wot-color-theme-primary, #ff2e4d)' }"
        @tap="goLogin"
      >
        {{ t('catalog.frequentGoLogin') }}
      </view>
    </view>
    <template v-else>
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
            {{ t('catalog.frequentStats', { qty: item.totalQuantity, count: item.orderCount }) }}
          </view>
          <view class="mt-12rpx flex items-center justify-end">
            <button
              class="h-56rpx text-24rpx leading-56rpx !m-0 !px-24rpx"
              type="primary"
              size="mini"
              @tap="handleAddToCart(item)"
            >
              {{ t('catalog.buyAgain') }}
            </button>
          </view>
        </view>
      </view>
      <view v-if="!loading && records.length === 0" class="py-80rpx text-center text-26rpx text-gray-400">
        {{ t('catalog.frequentEmpty') }}
      </view>
    </template>
  </view>
</template>
