<script setup lang="ts">
import type { SharedCartSummary } from '@/api/order/sharedCart'
import type { ReplenishCardProps } from '@/components/diy/retail-types'

/**
 * 补给单卡片（首页「今日补给单」）
 *
 * 数据来自共享购物车：本项目里「补给单」不是新领域对象，而是同一个
 * `shared_cart` 的产品化外壳（见可行性分析 §10.3 B1 路径）。
 * 因此这里只做展示 + 跳转，不引入任何新的采购模型。
 *
 * 可见性（与船舶工作台同口径，船供场景专属）：
 *
 * | 状态                        | 表现                    |
 * |----------------------------|-------------------------|
 * | 有进行中购物车                | 卡片 + 摘要 + 按单加购   |
 * | 无进行中购物车                | 不渲染（不堆引导文案）   |
 * | 已登录但无船 / 未登录          | 不渲染                  |
 * | 纯零售租户 business_mode=2    | 不渲染                  |
 * | 共享购物车/商品服务异常        | 不渲染                  |
 *
 * 刻意不展示「已采 N 项 / 还差 X 件 / 进度条」：现有模型 approved_quantity
 * 与 ITEM_CONFIRMED 只在确认人提交整船订单时写入，收集阶段没有目标量可算，
 * 编一个进度就是假数据（见 SharedCartSummaryVO 类注释）。
 */
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { getActiveSharedCartSummary } from '@/api/order/sharedCart'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useAuthStore } from '@/store/authStore'
import { useShipContextStore } from '@/store/shipContextStore'
import { useTenantCapabilityStore } from '@/store/tenantCapabilityStore'

const props = withDefaults(defineProps<{ showData?: Partial<ReplenishCardProps> }>(), {
  showData: () => ({}),
})

/**
 * 与其他零售组件保持一致：运营在编辑器配置的「通用样式」必须真正生效，
 * emptyStrategy / invalidStrategy 同样透传。
 */
const showData = computed<ReplenishCardProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 1,
  dataSource: props.showData.dataSource || { mode: 'current-tenant' },
  emptyStrategy: props.showData.emptyStrategy || 'hide',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  showBatchAdd: props.showData.showBatchAdd !== false,
  showPreview: props.showData.showPreview !== false,
  title: props.showData.title || '今日补给单',
}))

const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))

const authStore = useAuthStore()
const shipContextStore = useShipContextStore()
const tenantCapabilityStore = useTenantCapabilityStore()

const summary = ref<SharedCartSummary | null>(null)

const visible = computed(() => {
  if (!tenantCapabilityStore.resolved)
    return false
  if (!tenantCapabilityStore.shipSupplyEnabled)
    return false
  if (!authStore.isLoggedIn)
    return false
  return summary.value?.cart != null
})

/** 靠港/泊位文案：优先泊位，其次港口，都没有时回落船舶名 */
const locationText = computed(() => {
  const cart = summary.value?.cart
  if (!cart)
    return ''
  const parts = [cart.portName, cart.berth].filter(Boolean)
  if (parts.length > 0)
    return parts.join(' ')
  return cart.vesselName || ''
})

/** 概览文案：N 项 · M 人参与 · 合计 ¥X */
const overviewText = computed(() => {
  const data = summary.value
  if (!data)
    return ''
  const parts = [`${data.itemCount} 项`, `${data.memberCount} 人参与`]
  if (data.totalAmount > 0)
    parts.push(`合计 ¥${data.totalAmount}`)
  return parts.join(' · ')
})

/**
 * 明细预览文案：`番茄 2 · 矿泉水 5`，超出上限以省略号收尾。
 * 商品名拿不到时回落 SKU ID，避免出现空白行。
 */
const previewText = computed(() => {
  const data = summary.value
  if (!data || data.previewItems.length === 0)
    return ''
  const text = data.previewItems
    .map(item => `${item.spuName || item.skuId} ${item.quantity}`)
    .join(' · ')
  return data.previewTruncated ? `${text} …` : text
})

async function loadSummary() {
  // 未登录用户永远不会看到卡片，无需为租户能力或摘要发起请求
  if (!authStore.isLoggedIn) {
    summary.value = null
    return
  }
  await tenantCapabilityStore.ensureLoaded()
  if (!tenantCapabilityStore.shipSupplyEnabled) {
    summary.value = null
    return
  }
  try {
    summary.value = await getActiveSharedCartSummary(shipContextStore.vesselCallId || undefined)
  }
  catch {
    // 摘要失败不打断整页装修：静默隐藏卡片
    summary.value = null
  }
}

function openDetail() {
  const cartId = summary.value?.cart?.id
  if (!cartId)
    return
  uni.navigateTo({ url: `/sub-pages/order/shared-cart/detail?id=${cartId}` })
}

/**
 * 按单加购：进入该购物车的选货页，把清单里还缺的商品批量加入。
 *
 * 这里刻意不直接调批量加购接口 —— 清单条目需要用户确认数量与规格，
 * 直接下单式加购会绕过 MOQ/步长确认。批量接口留给"选货页勾选后提交"。
 */
function goBatchAdd() {
  const cartId = summary.value?.cart?.id
  if (!cartId)
    return
  uni.navigateTo({
    url: `/sub-pages/product/ship-supply/index?scene=2&sharedCartId=${cartId}`,
  })
}

onShow(() => {
  void loadSummary()
})
</script>

<template>
  <view
    v-if="visible"
    class="replenish-card"
    :style="dynamicStyles"
  >
    <view class="card-inner" @tap="openDetail">
      <view class="card-head">
        <view class="min-w-0 flex flex-1 flex-col">
          <view class="flex items-center">
            <text class="card-title">
              {{ showData.title }}
            </text>
            <text v-if="locationText" class="card-location">
              {{ locationText }}
            </text>
          </view>
          <text class="card-overview">
            {{ overviewText }}
          </text>
        </view>
        <view
          v-if="showData.showBatchAdd"
          class="card-action"
          @tap.stop="goBatchAdd"
        >
          按单加购
        </view>
      </view>

      <view
        v-if="showData.showPreview && previewText"
        class="card-preview"
      >
        <text class="flex-1 truncate">
          {{ previewText }}
        </text>
        <text class="i-carbon:chevron-right ml-8rpx flex-none text-24rpx" />
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.replenish-card {
  // 背景由装修通用样式接管，这里只保留卡片内容的结构与品牌渐变
}

.card-inner {
  overflow: hidden;
  padding: 24rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #082e63, #0b63e5);
  color: #fff;
}

.card-head {
  display: flex;
  align-items: flex-start;
}

.card-title {
  flex: none;
  font-size: 30rpx;
  font-weight: 800;
}

.card-location {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.22);
  font-size: 20rpx;
}

.card-overview {
  margin-top: 8rpx;
  font-size: 22rpx;
  opacity: 0.85;
}

.card-action {
  flex: none;
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  background: #fff;
  color: #0a4da3;
  font-size: 26rpx;
  font-weight: 700;
}

.card-preview {
  display: flex;
  align-items: center;
  margin-top: 18rpx;
  font-size: 22rpx;
  opacity: 0.92;
}
</style>
