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
 * | 状态                        | 表现                                  |
 * |----------------------------|---------------------------------------|
 * | 有进行中购物车                | 卡片 + 摘要 + 按单加购                  |
 * | 无进行中购物车                | emptyStrategy=hide 不渲染；=placeholder 渲染引导卡（发起/导入入口） |
 * | 已登录但无船 / 未登录          | 不渲染                                 |
 * | 纯零售租户 business_mode=2    | 不渲染                                 |
 * | 共享购物车/商品服务异常        | 不渲染（摘要失败按无数据处理）            |
 *
 * 展示口径：只回答「几项、几个人、大概多少钱、都有什么」。
 * 不做「已采 / 还差 / 进度条」——系统里没有采购执行的实施反馈来源，
 * 靠人工回填的进度会立刻过期，比不显示更容易误事。
 */
import { computed, ref } from 'vue'
import { getActiveSharedCartSummary } from '@/api/order/sharedCart'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { usePageShowLoad } from '@/composables/usePageShowLoad'
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
/** 摘要请求是否已落地（成功或失败都算）：区分"还没查"与"查过没有单"，占位卡只在后者出现 */
const summaryLoaded = ref(false)

const visible = computed(() => {
  if (!tenantCapabilityStore.resolved)
    return false
  if (!tenantCapabilityStore.shipSupplyEnabled)
    return false
  if (!authStore.isLoggedIn)
    return false
  return summary.value?.cart != null
})

/**
 * 空态占位（emptyStrategy=placeholder 时运营选择"显示占位"）：
 * 登录 + 船供租户 + 摘要已查但无进行中购物车才出现；
 * 未登录/非船供租户仍整体隐藏 —— 占位是给"会用到补给单的人"看的引导，不是给游客的广告位。
 */
const showPlaceholder = computed(() =>
  tenantCapabilityStore.resolved
  && tenantCapabilityStore.shipSupplyEnabled
  && authStore.isLoggedIn
  && summaryLoaded.value
  && summary.value?.cart == null
  && showData.value.emptyStrategy === 'placeholder',
)

function goCreateCart() {
  uni.navigateTo({ url: '/sub-pages/order/shared-cart/list' })
}

function goImportEntry() {
  uni.navigateTo({ url: '/sub-pages/order/shared-cart/import-entry' })
}

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

/** 概览文案：N 项 · M 人参与 · 预估 ¥X */
const overviewText = computed(() => {
  const data = summary.value
  if (!data)
    return ''
  const parts = [`${data.itemCount} 项`, `${data.memberCount} 人参与`]
  if (data.totalAmount > 0)
    parts.push(`预估 ¥${data.totalAmount}`)
  return parts.join(' · ')
})

/**
 * 明细预览：只列成员报的商品与数量。
 *
 * 这里刻意不显示「还差 N」——系统里没有"已买多少"的事实来源，
 * 编一个进度出来会让确认人误以为有人在跟踪采购执行。
 */
const previewLineText = computed(() => {
  const data = summary.value
  if (!data || data.previewItems.length === 0)
    return ''
  const text = data.previewItems
    .map(item => `${item.spuName || item.skuId} ${item.quantity}`)
    .join(' · ')
  return data.previewTruncated ? `${text} …` : text
})

async function loadSummary() {
  try {
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
  finally {
    // 无论哪条路径都标记"已查过"：占位卡要区分「还没查」和「查过但没单」
    summaryLoaded.value = true
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

/**
 * 装载即加载 + 页面每次显示刷新。
 *
 * 只挂 onShow 会在小程序端漏掉首屏：装修内容是异步拉回的，组件挂载时
 * 页面的 onShow 早已跑完，而小程序端不会补调迟到的页面钩子（H5 会）。
 * 详见 usePageShowLoad 注释。
 */
usePageShowLoad(loadSummary)
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
        v-if="showData.showPreview && previewLineText"
        class="card-preview"
      >
        <text class="flex-1 truncate">
          {{ previewLineText }}
        </text>
        <text class="i-carbon:chevron-right ml-8rpx flex-none text-24rpx" />
      </view>
    </view>
  </view>

  <!-- 空态占位：emptyStrategy=placeholder 时的引导卡（发起 / 导入直达） -->
  <view
    v-else-if="showPlaceholder"
    class="replenish-card"
    :style="dynamicStyles"
  >
    <view class="guide-inner">
      <text class="guide-title">
        还没有进行中的补给单
      </text>
      <text class="guide-desc">
        发起补给单与同船成员合并采购，或直接导入 Excel 清单一键下单
      </text>
      <view class="guide-actions">
        <view class="guide-btn guide-btn--ghost" @tap="goCreateCart">
          发起补给单
        </view>
        <view class="guide-btn guide-btn--primary" @tap="goImportEntry">
          导入清单下单
        </view>
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
  margin-top: 14rpx;
  font-size: 22rpx;
  opacity: 0.92;
}

// 空态占位引导卡：白底虚线框区别于有单时的品牌渐变实卡
.guide-inner {
  padding: 24rpx;
  border: 2rpx dashed rgb(11 99 229 / 35%);
  border-radius: 24rpx;
  background: #fff;
  text-align: center;
}

.guide-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #172033;
}

.guide-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  line-height: 1.5;
  color: #7a8699;
}

.guide-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
}

.guide-btn {
  flex: 1;
  height: 64rpx;
  font-size: 24rpx;
  line-height: 64rpx;
  border-radius: 999rpx;
}

.guide-btn--ghost {
  border: 2rpx solid #0b63e5;
  color: #0b63e5;
  background: #fff;
}

.guide-btn--primary {
  color: #fff;
  background: #0b63e5;
}
</style>
