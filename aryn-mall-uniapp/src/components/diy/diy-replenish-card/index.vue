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
 *
 * 视觉口径（2026-11-01 重设计，对齐全站「白卡 + 发丝线 + 轻投影 + 中性灰 +
 * 品牌色点状强调」）：
 * - 旧版是深蓝渐变整块 + 半透明白胶囊装地址，长地址会折 3 行把卡片撑高并压住
 *   按钮；信息全部压在渐变上，没有层级也没有品牌关系（全站主色是橙红）。
 * - 新版是白卡：品牌色只出现在图标块 / 状态点 / 主 CTA，地址收成标题下的一行
 *   省略文本，摘要做成「数字 + 单位」的弱化 meta 行，明细预览收进浅灰面板。
 * - 主色一律走主题变量（不写死），换肤后不会出现「红配蓝」；正文/分隔线用全站
 *   中性色，与 11-01 券体系重设计同一套令牌。
 */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getActiveSharedCartSummary } from '@/api/order/sharedCart'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { usePageShowLoad } from '@/composables/usePageShowLoad'
import { useAuthStore } from '@/store/authStore'
import { useShipContextStore } from '@/store/shipContextStore'
import { useTenantCapabilityStore } from '@/store/tenantCapabilityStore'
import { amountText, cartStatusLabel } from '@/utils/shared-cart'

const props = withDefaults(defineProps<{ showData?: Partial<ReplenishCardProps> }>(), {
  showData: () => ({}),
})
const { t } = useI18n()

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
  title: props.showData.title || t('diy.replenishCard.defaultTitle'),
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

/** 位置文案：岸上车显示地址快照；船供车优先泊位/港口，都没有时回落船舶名 */
const locationText = computed(() => {
  const cart = summary.value?.cart
  if (!cart)
    return ''
  if (cart.cartType === '2')
    return cart.addressSnapshot || t('diy.replenishCard.addressPending')
  const parts = [cart.portName, cart.berth].filter(Boolean)
  if (parts.length > 0)
    return parts.join(' ')
  return cart.vesselName || ''
})

/** 位置图标：岸上=坐标点，船供=船（UnoCSS 扫描源码里的字面量类名，勿动态拼接） */
const locationIconClass = computed(() =>
  summary.value?.cart?.cartType === '2' ? 'i-carbon:location' : 'i-carbon:sailboat-coastal',
)

/**
 * 状态胶囊：文案统一走 cartStatusLabel()（共享购物车的状态口径唯一来源），
 * 颜色保持中性 —— 状态是"卡片现在是什么阶段"，不是促销信息，不抢主 CTA。
 */
const statusText = computed(() => cartStatusLabel(summary.value?.cart?.status))

/** 统计数字：项 / 人（单位走 i18n，数字单独放大） */
const itemCount = computed(() => summary.value?.itemCount ?? 0)
const memberCount = computed(() => summary.value?.memberCount ?? 0)

/** 主 CTA 文案随状态变：清单为空时「按单加购」没有对象，改成「去选品」 */
const actionText = computed(() =>
  itemCount.value > 0 ? t('diy.replenishCard.batchAdd') : t('diy.replenishCard.pickAction'),
)

/** 预估金额：只作量级参考，不是结算金额；0 元时不显示（没有金额比显示 ¥0.00 更诚实） */
const amountValue = computed(() => {
  const amount = summary.value?.totalAmount ?? 0
  return amount > 0 ? amountText(amount) : ''
})

/**
 * 明细预览：只列成员报的商品与数量。
 *
 * 这里刻意不显示「还差 N」——系统里没有"已买多少"的事实来源，
 * 编一个进度出来会让确认人误以为有人在跟踪采购执行。
 * 数量用「×N」而不是裸数字，避免和商品名里的规格数字混淆。
 */
const previewLineText = computed(() => {
  const data = summary.value
  if (!data || data.previewItems.length === 0)
    return ''
  const text = data.previewItems
    .map(item => `${item.spuName || item.skuId} ×${item.quantity}`)
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
    <!-- 有进行中的补给单：整卡可点进详情，右侧 CTA 直达按单加购（stop 防冒泡） -->
    <view
      class="rp-card"
      hover-class="rp-card--pressed"
      @tap="openDetail"
    >
      <view class="rp-head">
        <view class="rp-icon">
          <text class="i-carbon:list-checked rp-icon-glyph" />
        </view>
        <view class="rp-heading">
          <view class="rp-title-row">
            <text class="rp-title">{{ showData.title }}</text>
            <view v-if="statusText" class="rp-status">
              <text class="rp-status-dot" />
              <text class="rp-status-text">{{ statusText }}</text>
            </view>
          </view>
          <view v-if="locationText" class="rp-loc">
            <text class="rp-loc-icon" :class="locationIconClass" />
            <text class="rp-loc-text">{{ locationText }}</text>
          </view>
        </view>
        <view
          v-if="showData.showBatchAdd"
          class="rp-action"
          hover-class="rp-action--pressed"
          @tap.stop="goBatchAdd"
        >
          <text class="rp-action-text">{{ actionText }}</text>
          <text class="i-carbon:arrow-right rp-action-icon" />
        </view>
      </view>

      <view class="rp-divider" />

      <view class="rp-meta">
        <view class="rp-meta-stats">
          <view class="rp-stat">
            <text class="rp-stat-num">{{ itemCount }}</text>
            <text class="rp-stat-unit">{{ t('diy.replenishCard.itemUnit') }}</text>
          </view>
          <text class="rp-stat-sep">·</text>
          <view class="rp-stat">
            <text class="rp-stat-num">{{ memberCount }}</text>
            <text class="rp-stat-unit">{{ t('diy.replenishCard.memberUnit') }}</text>
          </view>
        </view>
        <view v-if="amountValue" class="rp-amount">
          <text class="rp-amount-label">{{ t('diy.replenishCard.estimateLabel') }}</text>
          <text class="rp-amount-symbol">¥</text>
          <text class="rp-amount-value">{{ amountValue }}</text>
        </view>
      </view>

      <view
        v-if="showData.showPreview"
        class="rp-preview"
      >
        <template v-if="previewLineText">
          <text class="rp-preview-text">{{ previewLineText }}</text>
          <text class="i-carbon:chevron-right rp-preview-icon" />
        </template>
        <text v-else class="rp-preview-placeholder">
          {{ t('diy.replenishCard.emptyItems') }}
        </text>
      </view>
    </view>
  </view>

  <!-- 空态占位：emptyStrategy=placeholder 时的引导卡（发起 / 导入直达） -->
  <view
    v-else-if="showPlaceholder"
    class="replenish-card"
    :style="dynamicStyles"
  >
    <view class="rp-guide">
      <view class="rp-guide-head">
        <view class="rp-icon">
          <text class="i-carbon:add-alt rp-icon-glyph" />
        </view>
        <view class="rp-guide-heading">
          <text class="rp-guide-title">{{ t('diy.replenishCard.emptyTitle') }}</text>
          <text class="rp-guide-desc">{{ t('diy.replenishCard.emptyDesc') }}</text>
        </view>
      </view>
      <view class="rp-guide-actions">
        <view
          class="rp-guide-btn rp-guide-btn--ghost"
          hover-class="rp-guide-btn--pressed"
          @tap="goCreateCart"
        >
          {{ t('diy.replenishCard.createAction') }}
        </view>
        <view
          class="rp-guide-btn rp-guide-btn--primary"
          hover-class="rp-guide-btn--pressed"
          @tap="goImportEntry"
        >
          {{ t('diy.replenishCard.importAction') }}
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/**
 * 补给单卡片设计令牌（2026-11-01 重设计 v1）。
 *
 * 与 `src/styles/coupon-ticket.scss` 同一套中性色口径；品牌色是主题变量
 * （三色主题：页面级 --theme-color-* > 商城主题 --wot-color-theme-* > 兜底红），
 * 禁止写死主色 —— 换肤后卡片配色必须跟着变。
 *
 * 换算口径：小程序屏宽恒 750rpx，管理端预览画布 375px ⇒ **1rpx = 0.5px**。
 * 改这里任何一个数值都必须同步 `aryn-mall-ui/.../replenish-card/index.vue`，
 * `preview-parity.test.ts` 会锁住高信号值。
 */
$rp-brand: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
$rp-text-1: #1f2329;
$rp-text-2: #5b616e;
$rp-text-3: #6b7280;
$rp-muted: #9ca3af;
$rp-line: #eef0f3;
$rp-fill: #f7f8fa;
$rp-fill-strong: #f4f5f7;
$rp-pressed: #fafbfc;

.replenish-card {
  // 背景/边距/圆角由装修通用样式接管，这里只保留卡片内容的结构
}

.rp-card {
  position: relative;
  padding: 24rpx 24rpx 22rpx;
  overflow: hidden;
  color: $rp-text-1;
  background: #fff;
  border: 1rpx solid $rp-line;
  border-radius: 24rpx;
  // 两层叠加：近处细、远处散，白底楼层上靠发丝线 + 浅投影立住卡片边界
  box-shadow:
    0 2rpx 6rpx rgb(15 23 42 / 3%),
    0 12rpx 28rpx rgb(15 23 42 / 5%);
  transition: background-color 160ms ease;

  // 按压反馈：H5 用 :active，小程序用 hover-class，两者效果一致
  &:active {
    background: $rp-pressed;
  }
}

.rp-card--pressed {
  background: $rp-pressed;
}

.rp-head {
  display: flex;
  align-items: center;
}

/* 品牌色图标块：整卡唯一的"实心色块"，作为视觉锚点，其余全部中性 */
.rp-icon {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 76rpx;
  height: 76rpx;
  color: #fff;
  background: $rp-brand;
  border-radius: 22rpx;
}

.rp-icon-glyph {
  font-size: 38rpx;
  line-height: 1;
}

.rp-heading {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.rp-title-row {
  display: flex;
  align-items: center;
}

.rp-title {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  font-size: 32rpx;
  font-weight: 700;
  line-height: 1.25;
  color: $rp-text-1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 状态胶囊：中性底 + 品牌色状态点，只表达"阶段"，不抢 CTA */
.rp-status {
  display: flex;
  flex: none;
  align-items: center;
  padding: 4rpx 12rpx 4rpx 10rpx;
  margin-left: 12rpx;
  background: $rp-fill-strong;
  border-radius: 8rpx;
}

.rp-status-dot {
  flex: none;
  width: 8rpx;
  height: 8rpx;
  margin-right: 6rpx;
  background: $rp-brand;
  border-radius: 50%;
}

.rp-status-text {
  flex: none;
  font-size: 20rpx;
  font-weight: 600;
  line-height: 1.2;
  color: $rp-text-2;
}

/* 位置行：长地址（岸上收货地址）必须收成 1 行省略，不再折行撑高卡片 */
.rp-loc {
  display: flex;
  align-items: center;
  min-width: 0;
  margin-top: 8rpx;
}

.rp-loc-icon {
  flex: none;
  margin-right: 6rpx;
  font-size: 22rpx;
  line-height: 1;
  color: $rp-muted;
}

.rp-loc-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 22rpx;
  line-height: 1.3;
  color: $rp-text-3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 主 CTA：品牌实底胶囊（对齐券体系重设计口径：主行动用实底，不用渐变） */
.rp-action {
  position: relative;
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  height: 64rpx;
  padding: 0 24rpx 0 26rpx;
  margin-left: 16rpx;
  background: $rp-brand;
  border-radius: 999rpx;
  box-shadow: 0 8rpx 20rpx rgb(15 23 42 / 12%);
  transition: opacity 160ms ease, transform 160ms ease;

  // 视觉高 64rpx，触控热区补到 88rpx（44px）：::after 只扩热区不改布局
  &::after {
    position: absolute;
    top: -12rpx;
    right: -8rpx;
    bottom: -12rpx;
    left: -8rpx;
    content: '';
    border-radius: inherit;
  }
}

.rp-action--pressed {
  opacity: 0.88;
  transform: scale(0.97);
}

.rp-action-text {
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1;
  color: #fff;
}

.rp-action-icon {
  margin-left: 4rpx;
  font-size: 22rpx;
  line-height: 1;
  color: #fff;
}

.rp-divider {
  height: 1rpx;
  margin: 16rpx 0 14rpx;
  background: $rp-line;
}

/* 摘要行：左边「N 项商品 · M 人参与」，右边「预估 ¥X.XX」——金额靠右形成张力 */
.rp-meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.rp-meta-stats {
  display: flex;
  flex: 1;
  align-items: baseline;
  min-width: 0;
}

.rp-stat {
  display: flex;
  align-items: baseline;
}

.rp-stat-num {
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1;
  color: $rp-text-1;
  font-variant-numeric: tabular-nums;
}

.rp-stat-unit {
  margin-left: 4rpx;
  font-size: 22rpx;
  line-height: 1;
  color: $rp-text-3;
}

.rp-stat-sep {
  margin: 0 12rpx;
  font-size: 22rpx;
  line-height: 1;
  color: #c9cdd4;
}

.rp-amount {
  display: flex;
  flex: none;
  align-items: baseline;
  margin-left: 16rpx;
}

.rp-amount-label {
  margin-right: 6rpx;
  font-size: 20rpx;
  line-height: 1;
  color: $rp-text-3;
}

.rp-amount-symbol {
  margin-right: 2rpx;
  font-size: 22rpx;
  font-weight: 700;
  line-height: 1;
  color: $rp-text-2;
}

.rp-amount-value {
  font-size: 36rpx;
  font-weight: 700;
  line-height: 1;
  color: $rp-text-1;
  font-variant-numeric: tabular-nums;
}

/* 明细预览：浅灰面板收口，1 行省略；0 项时同一位置给引导文案 */
.rp-preview {
  display: flex;
  align-items: center;
  padding: 12rpx 18rpx;
  margin-top: 12rpx;
  background: $rp-fill;
  border-radius: 16rpx;
}

.rp-preview-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 22rpx;
  line-height: 1.4;
  color: $rp-text-2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rp-preview-icon {
  flex: none;
  margin-left: 8rpx;
  font-size: 22rpx;
  line-height: 1;
  color: $rp-muted;
}

.rp-preview-placeholder {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 22rpx;
  line-height: 1.4;
  color: $rp-text-3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 空态占位引导卡 ---------- */
.rp-guide {
  padding: 24rpx;
  background: #fff;
  border: 1rpx solid $rp-line;
  border-radius: 24rpx;
  box-shadow:
    0 2rpx 6rpx rgb(15 23 42 / 3%),
    0 12rpx 28rpx rgb(15 23 42 / 5%);
}

.rp-guide-head {
  display: flex;
  align-items: center;
}

.rp-guide-heading {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.rp-guide-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1.3;
  color: $rp-text-1;
}

.rp-guide-desc {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  line-height: 1.5;
  color: $rp-text-3;
}

.rp-guide-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
}

.rp-guide-btn {
  position: relative;
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  height: 72rpx;
  font-size: 26rpx;
  font-weight: 600;
  line-height: 1;
  border-radius: 999rpx;
  transition: opacity 160ms ease, transform 160ms ease;

  // 视觉高 72rpx，触控热区补到 88rpx（44px）
  &::after {
    position: absolute;
    top: -8rpx;
    right: 0;
    bottom: -8rpx;
    left: 0;
    content: '';
    border-radius: inherit;
  }
}

.rp-guide-btn--pressed {
  opacity: 0.88;
  transform: scale(0.98);
}

.rp-guide-btn--ghost {
  color: $rp-text-1;
  background: $rp-fill-strong;
}

.rp-guide-btn--primary {
  color: #fff;
  background: $rp-brand;
  box-shadow: 0 8rpx 20rpx rgb(15 23 42 / 12%);
}
</style>
