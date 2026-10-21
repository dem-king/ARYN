<script setup lang="ts">
/**
 * 加购确认弹层：确认采购数量，并（在用户有进行中的共享购物车时）选择加入哪张车。
 *
 * 挂载点唯一：根组件 `src/App.ku.vue` 按 `v-if="!!pendingChoice"` 懒挂载
 * （平时零节点开销，有加购流等待确认时才渲染），**宿主一律不要再挂**——
 * quick-cart-button 这类宿主随商品卡重复渲染，由它们各自挂载会让同屏出现
 * N 份弹层：N 层遮罩叠成全黑、N 份 slide-up 动画不同步造成抖动、弹层 DOM
 * 落在商品卡内部导致按钮 tap 冒泡到卡片的打开详情。
 * 展示哪张购物车、数量规则由 useCartDestination 的模块级单例状态决定，宿主无需传 props；
 * 点击遮罩关闭 = 放弃本次加购（不会静默写进个人购物车）。
 *
 * 数量输入（本次新增）：船供采购量大（青菜一次几十斤），原先只能按起订量反复点「+」。
 * 这里用「步进器 + 可键盘输入」：加减按 stepQty 走，也可直接敲几十上百；
 * 手输值经 `normalizeQuantity` 归一化（对外层入口同一份纯函数，与后端
 * `validateQuantityRules` 同口径）——向上取到步长整数倍、不低于起订量、不超库存，
 * 所以用户填 37 斤（5 斤起订、5 斤递增）会被修正为 40 而不是被拦下。
 *
 * z-index 1020：本弹层常在用户刚点完「加入购物车」后出现，必须压住
 * SKU 规格弹层（990）与商详快捷面板（1000），见同仓两处的层级注释。
 */
import type { QuantityRule } from '@/utils/quick-cart'
import { onHide, onUnload } from '@dcloudio/uni-app'
import { computed, ref, watch } from 'vue'

import { pendingChoice, useCartDestination } from '@/composables/useCartDestination'
import { normalizeQuantity, quantityRuleHint } from '@/utils/quick-cart'
import { cartStatusLabel, cartStatusTheme, sharedCartLocationLabel } from '@/utils/shared-cart'

const { answerDestination } = useCartDestination()

/**
 * 离开所在页面 = 放弃本次加购（与点遮罩关闭同义，不会静默写进购物车）。
 *
 * pendingChoice 是跨页面共享的模块级单例，而弹层随根组件注入每一页：
 * 不主动关掉的话，首页开着弹层切到其它 Tab，弹层会在新页面原样出现
 * （弹层跟着人走）。onHide 覆盖切 Tab 与前进跳转，onUnload 覆盖返回
 * 手势 / redirectTo——两条路径都会清掉 pendingChoice，各页面里的弹层
 * 实例随之全部卸载。
 *
 * 页面钩子注册在页面实例上、组件卸载后仍会触发（见 usePageShowLoad），
 * 这里按「挂载时的请求号」比对，迟到的钩子不会误关之后新开的确认
 * （与 useCartDestination 超时兜底同一套 id 比对口径）。
 */
const requestAtMount = pendingChoice.value?.id

function abortOnPageLeave() {
  if (pendingChoice.value?.id === requestAtMount)
    answerDestination('abort')
}

onHide(abortOnPageLeave)
onUnload(abortOnPageLeave)

const cart = computed(() => pendingChoice.value?.cart ?? null)
const rule = computed(() => pendingChoice.value?.rule ?? null)
/**
 * 目的地锁定（共享车分类选货页）：只确认数量，不提供「个人购物车」分叉。
 * 这类页面本身就是"给这张共享车选货"，再问一次去向既多余也容易点错。
 */
const lockDestination = computed(() => pendingChoice.value?.lockDestination === true)
const statusTheme = computed(() => cartStatusTheme(cart.value?.status))

/** 数量输入值：字符串以便用户清空重输，具体数值在交互时归一化 */
const quantityInput = ref('')

/** 每次打开新的确认流都重新初始化数量（弹层由 v-if 挂载，这里防同一实例复用） */
watch(
  () => pendingChoice.value?.id,
  () => {
    quantityInput.value = String(pendingChoice.value?.quantity ?? 1)
  },
  { immediate: true },
)

/** 展示用：商品名与小计（有单价时才算） */
const goodsName = computed(() => pendingChoice.value?.payload.goodsName ?? '')
const unitPrice = computed(() => {
  const price = Number(pendingChoice.value?.payload.unitPrice)
  return Number.isFinite(price) && price > 0 ? price : null
})
const subtotalText = computed(() => {
  if (unitPrice.value === null)
    return ''
  const quantity = Number(quantityInput.value)
  if (!Number.isFinite(quantity) || quantity <= 0)
    return ''
  return (unitPrice.value * quantity).toFixed(2)
})

const ruleHint = computed(() => (rule.value ? quantityRuleHint(rule.value) : ''))

/** 活动限购说明（如「新人专享价每人限购 1 件」）；无活动限制时为空串 */
const quantityHint = computed(() => pendingChoice.value?.payload.quantityHint || '')

function toPositiveInt(value: unknown): number | null {
  const parsed = Math.floor(Number(value))
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

/**
 * 数量上限：库存与本次加购的活动限购取小。
 *
 * 新人价每人每 SKU 限购 1 件，超量不是「恢复原价」而是整单被后端拒绝
 * （OrderPriceComputeService.assertNewbieQuantityLimit），因此必须在加购确认
 * 这一步就封顶，不能让用户加 2 件到提交订单才看到失败。
 */
const quantityLimit = computed(() => {
  const stock = rule.value?.stock ?? null
  const activityLimit = toPositiveInt(pendingChoice.value?.payload.maxQuantity)
  if (stock === null)
    return activityLimit
  if (activityLimit === null)
    return stock
  return Math.min(stock, activityLimit)
})

/** 归一化用规则：把活动限购折进库存上限，与后端校验同一口径 */
const normalizeRule = computed<QuantityRule | null>(() => {
  if (!rule.value)
    return null
  if (quantityLimit.value === null)
    return rule.value
  return { ...rule.value, stock: quantityLimit.value }
})

/** 减到起订量就不能再减；库存/限购封顶时加号禁用，与后端规则一致 */
const minQuantity = computed(() => normalizeRule.value?.moq ?? 1)
const canDecrease = computed(() => Number(quantityInput.value) > minQuantity.value)
const canIncrease = computed(
  () => quantityLimit.value === null || Number(quantityInput.value) < quantityLimit.value,
)

/** 按步长增减一步（结果仍走归一化，保证库存/限购边界处也合法） */
function stepQuantity(direction: 1 | -1) {
  const current = pendingChoice.value
  const currentRule = normalizeRule.value
  if (!current || !currentRule)
    return
  const step = currentRule.stepQty
  const next = Number(quantityInput.value) + direction * step
  const normalized = normalizeQuantity(next, currentRule)
  if (normalized <= 0) {
    uni.showToast({ title: `库存不足，最少起订 ${currentRule.moq}${currentRule.purchaseUnit}`, icon: 'none' })
    return
  }
  quantityInput.value = String(normalized)
  current.touch()
}

/** 键盘输入：只保留数字，失焦/提交时再归一化（输入过程中不打断用户） */
function onQuantityInput(event: any) {
  const raw = String(event?.detail?.value ?? '').replace(/\D/g, '')
  quantityInput.value = raw
  pendingChoice.value?.touch()
}

/** 失焦时把输入落到合法值，让用户立刻看到最终会提交多少 */
function normalizeInput() {
  const current = pendingChoice.value
  const currentRule = normalizeRule.value
  if (!current || !currentRule)
    return
  const normalized = normalizeQuantity(quantityInput.value, currentRule)
  quantityInput.value = String(normalized > 0 ? normalized : currentRule.moq)
  current.touch()
}

/**
 * 提交：先归一化再写入，用户手输的 37 斤（5 斤起订、5 斤递增）会按 40 斤提交并提示，
 * 而不是把一次加购拆成「先报错、再改、再点一次」——大批量补货时这一步省很多。
 */
function confirm(dest: 'shared' | 'personal') {
  const current = pendingChoice.value
  if (!current)
    return
  if (!rule.value) {
    // 数量已由 SKU 弹层定好，这里只负责选去向
    answerDestination(dest)
    return
  }
  const currentRule = normalizeRule.value
  if (!currentRule)
    return
  const normalized = normalizeQuantity(quantityInput.value, currentRule)
  if (normalized <= 0) {
    uni.showToast({
      title: `库存不足，最少起订 ${currentRule.moq}${currentRule.purchaseUnit}`,
      icon: 'none',
    })
    return
  }
  quantityInput.value = String(normalized)
  answerDestination(dest, normalized)
}
</script>

<template>
  <wd-popup
    v-if="pendingChoice"
    :model-value="true"
    position="bottom"
    :safe-area-inset-bottom="true"
    :z-index="1020"
    custom-style="border-radius: 24rpx 24rpx 0 0; overflow: hidden;"
    @close="answerDestination('abort')"
  >
    <view class="px-32rpx pb-32rpx pt-28rpx">
      <view class="text-32rpx font-bold">
        {{ rule ? '确认采购数量' : (lockDestination ? '加入共享购物车' : '选择加入哪里') }}
      </view>

      <!-- 商品信息：确认加的是哪一件，避免在长列表里点错商品 -->
      <view v-if="goodsName" class="mt-12rpx truncate text-26rpx text-gray-500">
        {{ goodsName }}
      </view>

      <!-- 数量区：步进器 + 键盘输入，支持一次买几十上百 -->
      <view v-if="rule" class="mt-20rpx rounded-16rpx bg-gray-50 p-24rpx">
        <view class="flex items-center justify-between">
          <text class="text-28rpx text-gray-700">
            采购数量
          </text>
          <view class="flex items-center">
            <view
              class="quantity-btn"
              :class="{ 'quantity-btn--disabled': !canDecrease }"
              @tap.stop="stepQuantity(-1)"
            >
              <wd-icon name="decrease" size="28rpx" />
            </view>
            <input
              class="quantity-input"
              type="number"
              :value="quantityInput"
              @input="onQuantityInput"
              @blur="normalizeInput"
            >
            <view
              class="quantity-btn"
              :class="{ 'quantity-btn--disabled': !canIncrease }"
              @tap.stop="stepQuantity(1)"
            >
              <wd-icon name="add" size="28rpx" />
            </view>
          </view>
        </view>
        <view v-if="ruleHint" class="mt-10rpx text-22rpx text-gray-500">
          {{ ruleHint }}
        </view>
        <view v-if="quantityHint" class="mt-6rpx text-22rpx text-amber-600">
          {{ quantityHint }}
        </view>
        <view v-if="subtotalText" class="mt-8rpx text-24rpx text-gray-600">
          小计 ¥{{ subtotalText }}
        </view>
      </view>

      <!-- 进行中的共享购物车信息：让用户确认加进的是哪张单 -->
      <view v-if="cart" class="mt-20rpx rounded-16rpx bg-gray-50 p-24rpx">
        <view class="flex items-center justify-between">
          <text class="text-30rpx font-bold">
            {{ cart.vesselName }}
          </text>
          <text
            class="rounded-full px-16rpx py-4rpx text-22rpx"
            :style="{ background: statusTheme.bg, color: statusTheme.text }"
          >
            {{ cartStatusLabel(cart.status) }}
          </text>
        </view>
        <view class="mt-6rpx text-24rpx text-gray-500">
          {{ sharedCartLocationLabel(cart) }}
        </view>
        <view v-if="cart.expiresAt" class="mt-4rpx text-24rpx text-amber-600">
          收集截止 {{ cart.expiresAt }}
        </view>
      </view>

      <view
        v-if="cart"
        class="mt-24rpx h-80rpx flex items-center justify-center rounded-full bg-primary text-28rpx text-white font-medium"
        @tap.stop="confirm('shared')"
      >
        加入共享购物车
      </view>
      <view v-if="cart" class="mt-10rpx text-center text-22rpx text-gray-400">
        与同船成员合并，由采购确认人统一提交整船订单
      </view>
      <!-- 锁定向导只保留共享车入口：分类选货页不存在「加进个人车」的语义 -->
      <view
        v-if="!lockDestination"
        class="h-80rpx flex items-center justify-center rounded-full text-28rpx font-medium"
        :class="cart
          ? 'mt-16rpx bg-gray-100 text-gray-700'
          : 'mt-24rpx bg-primary text-white'"
        @tap.stop="confirm('personal')"
      >
        加入个人购物车
      </view>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.quantity-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60rpx;
  height: 60rpx;
  border: 2rpx solid #e8e8e8;
  border-radius: 12rpx;
  background: #fff;
  color: #303133;
}

.quantity-btn--disabled {
  color: #c8c9cc;
  background: #f7f8fa;
}

.quantity-input {
  width: 120rpx;
  height: 60rpx;
  margin: 0 12rpx;
  border: 2rpx solid #e8e8e8;
  border-radius: 12rpx;
  background: #fff;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
  color: #303133;
}
</style>
