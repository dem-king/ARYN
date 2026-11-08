<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import PriceText from '@/components/price-text/index.vue'
import { useShipContextStore } from '@/store/shipContextStore'
import {
  DELIVERY_WAY_EXPRESS,
  DELIVERY_WAY_MALL_DELIVERY,
  DELIVERY_WAY_SELF_PICKUP,
  DELIVERY_WAY_VESSEL_INTERNAL,
  deliveryWayOptions,
} from '@/sub-pages/utils/delivery-way'
import { getCouponUnavailableInfo } from '@/utils/coupon-usability'

interface OrderItem {
  spuId: string
  picUrl: string
  spuName: string
  buyQuantity: number
  specsInfo: string
  skuId: string
  salesPrice: number
  paymentPrice: number
  totalPrice: number
  /** 原价小计（结算接口回填，非表字段）：划线价用，缺失时按成交小计兜底 */
  originalTotalPrice?: number
  /** 成交活动类型（结算接口回填，非表字段）：groupBuy/seckill/discount/newbie，空=普通商品 */
  activityType?: string | null
  /** 该行活动是否允许叠券（结算接口回填，非表字段）：false=活动禁叠券 */
  couponStackable?: boolean | null
  /** 该行活动是否允许积分抵现（结算接口回填，非表字段）：false=活动禁积分 */
  pointsStackable?: boolean | null
}

interface Order {
  orderItemList: OrderItem[]
  totalPrice: number
  freightPrice: number
  couponPrice: number
  paymentPrice: number
  deliveryWay: string
  couponUserId: string
  couponUserList: any[]
  remark: string
  /** 商品原价合计（结算接口回填，非表字段） */
  originalTotalPrice?: number
  /** 活动优惠合计（原价合计 - 成交商品金额，结算接口回填，非表字段） */
  activityDiscountPrice?: number
  /** 会员折扣金额（结算接口回填，非表字段） */
  memberDiscountPrice?: number
  pointsDeductMax?: number
  pointsDeductMaxPrice?: number
  pointsDeductPrice?: number
  pointsDeductRate?: number
  pointsUsed?: number
  /** 券不可用原因（结算接口回填，非表字段）：选中了不可叠的券时下发 */
  couponStackReason?: string | null
  /** 券门槛可计算金额（允许叠券明细的折后合计，结算接口回填，非表字段） */
  couponEligibleAmount?: number | null
  /** 积分可计算金额（允许积分明细的商品应付合计，结算接口回填，非表字段） */
  pointsEligibleAmount?: number | null
  /** 积分不可用原因（结算接口回填，非表字段）：门槛/黑名单/场景不可用整单时下发，null=可用 */
  pointsStackReason?: string | null
}

interface Props {
  order: Order
  deliveryWay: string
  /** 支付方式：''=在线支付；'3'=货到付款（仅商城配送/内部配送可选） */
  paymentWay: string
  couponUserList: any[]
  /** 是否拼团单：拼团单仅支持在线支付（成团判定依赖支付回调），隐藏支付方式选择 */
  groupBuyOrder?: boolean
  /** 是否已开启积分抵扣（由父组件持有提交值） */
  pointsEnabled?: boolean
  /** 尚未选择收货地址：运费依赖地址，此时展示「选择地址后计算」而不是误导性的 ￥0.00 */
  addressPending?: boolean
  /** 导入直下单交接：配送方式已在向导锁定（船供/岸上），行转静态说明、不可切换 */
  deliveryWayLocked?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'deliveryWayChange', order: Order): void
  (e: 'paymentWayChange', paymentWay: string): void
  (e: 'remarkChange', order: Order): void
  (e: 'showCoupon', order: Order): void
  (e: 'pointsToggle', enabled: boolean): void
}>()

const { t } = useI18n()

const PAYMENT_TYPE_COD = '3'

/** 价格/优惠统一走商城主题色（换肤后随之变化），与 C 端价格口径一致 */
const THEME_COLOR = 'var(--wot-color-theme-primary, #ff2e4d)'

const deliveryShow = ref(false)
/** 已确认的配送方式（提交值） */
const deliveryWay = ref(props.deliveryWay)
/** 弹窗内草稿值：只有点「确认」才提交，避免取消后展示值与提交值不一致 */
const deliveryWayDraft = ref(props.deliveryWay)

const paymentShow = ref(false)
/** 已确认的支付方式（提交值） */
const paymentWay = ref(props.paymentWay || '')
/** 弹窗内草稿值：只有点「确认」才提交 */
const paymentWayDraft = ref(paymentWay.value)
// 创建本地响应式变量来避免直接修改prop
const localOrder = ref<Order>({ ...props.order })

const hasSelectedCoupon = computed(() => !!localOrder.value.couponUserId)

/**
 * 全部明细都不允许叠券（纯秒杀/纯新人价单）：券行置灰给原因，不弹选择器。
 * 兼容老结算接口（不下发 couponStackable）时恒为 false，不影响普通订单。
 */
const allItemsCouponBlocked = computed(() => {
  const items = localOrder.value.orderItemList || []
  return items.length > 0 && items.every(item => item.couponStackable === false)
})

/** 券不可用原因：优先服务端下发的 couponStackReason（选中了不可叠券时） */
const couponBlockReason = computed(() => {
  if (localOrder.value.couponStackReason)
    return localOrder.value.couponStackReason
  return allItemsCouponBlocked.value ? t('checkout.coupon.activityBlocked') : ''
})

/**
 * 积分整单不可用原因：直接展示服务端下发的 pointsStackReason
 * （后端已按门槛/黑名单/购买场景生成口径文案，前端只做展示，不自造第二套判断）。
 */
const pointsBlockReason = computed(() => localOrder.value.pointsStackReason || '')

/**
 * 至少一张券可用（门槛 + 范围 + 活动可叠）：只看券数量会把「有券但全部不可用」
 * 误显示成「选择优惠券」，用户点进去发现没有可用券。
 */
const hasUsableCoupon = computed(() => {
  if (allItemsCouponBlocked.value || !Array.isArray(props.couponUserList))
    return false
  const eligibleBase = Number(localOrder.value.couponEligibleAmount)
  return props.couponUserList.some((coupon) => {
    const info = getCouponUnavailableInfo(
      coupon,
      localOrder.value.orderItemList,
      Number.isFinite(eligibleBase) ? eligibleBase : null,
    )
    return !info.unavailable
  })
})

const hasAvailableCoupons = computed(() => hasUsableCoupon.value && !localOrder.value.couponUserId)

/** 优惠券行是否可点：已选（可换）或有可用券（可选）且未被置灰 */
const couponRowActionable = computed(() => !couponBlockReason.value && (hasSelectedCoupon.value || hasAvailableCoupons.value))

/**
 * 混合单部分商品不参与积分：活动禁叠或命中积分黑名单（后端均以 pointsStackable=false
 * 下发，非前端自造口径），积分只作用于可叠明细，副文案注明口径。
 */
const pointsPartialStack = computed(() => {
  const items = localOrder.value.orderItemList || []
  return items.some(item => item.pointsStackable === false)
})

/**
 * 「商品金额」按原价合计展示：活动价（拼团/秒杀/折扣/新人价）让利另行成行，
 * 让用户看到「原价 → 优惠 → 实付」的完整链路；无活动时原价合计即成交金额。
 * 老结算接口不返回该字段时回落到成交金额，不影响展示与提交金额口径。
 */
const goodsAmount = computed(() => {
  const original = Number(localOrder.value.originalTotalPrice)
  return Number.isFinite(original) && original > 0 ? original : localOrder.value.totalPrice
})

/** 活动优惠合计：0 或字段缺失时不渲染优惠行，避免零优惠「假优惠」 */
const activityDiscount = computed(() => {
  const value = Number(localOrder.value.activityDiscountPrice)
  return Number.isFinite(value) && value > 0 ? value : 0
})

/** 会员优惠：活动价明细不参与会员折扣，金额由服务端计算 */
const memberDiscount = computed(() => {
  const value = Number(localOrder.value.memberDiscountPrice)
  return Number.isFinite(value) && value > 0 ? value : 0
})

/** 拼团单用「拼团优惠」点题，其余活动价统一叫「活动优惠」 */
const activityDiscountLabel = computed(() =>
  props.groupBuyOrder ? t('checkout.goods.groupBuyDiscount') : t('checkout.goods.activityDiscount'),
)

/**
 * 明细划线价：仅当「原价小计 > 本行实付」时展示。
 * 原来的划线取 totalPrice，促销落价后与实付相等（拼团单 25.90 划 25.90），
 * 看上去像假折扣；这里改用结算接口回填的原价，且不做「自己划自己」。
 */
function showOriginalPrice(goods: OrderItem) {
  const original = Number(goods.originalTotalPrice)
  const paid = Number(goods.paymentPrice)
  return Number.isFinite(original) && Number.isFinite(paid) && original > paid
}

/** 明细活动标签：解释「这行为什么是活动价/为什么不能叠券」 */
const activityTypeLabels = computed<Record<string, string>>(() => ({
  groupBuy: t('checkout.goods.groupBuyPrice'),
  seckill: t('checkout.goods.seckillPrice'),
  discount: t('checkout.goods.discountPrice'),
  newbie: t('checkout.goods.newbiePrice'),
}))
function activityLabel(goods: OrderItem) {
  return goods.activityType ? (activityTypeLabels.value[goods.activityType] || '') : ''
}

// 监听prop变化以保持同步
watch(() => props.order, (newOrder) => {
  localOrder.value = { ...newOrder }
}, { deep: true })

// 父组件在重新结算时可能改变配送方式（如绑定船舶后回落为内部配送），需同步已确认值
watch(() => props.deliveryWay, (way) => {
  deliveryWay.value = way
  if (!deliveryShow.value) {
    deliveryWayDraft.value = way
  }
})

// 父组件切换配送方式离开 3/4 时会重置支付方式，需同步已确认值
watch(() => props.paymentWay, (way) => {
  const next = way || ''
  paymentWay.value = next
  if (!paymentShow.value) {
    paymentWayDraft.value = next
  }
})

// 配送方式选项。已绑定船舶+靠港上下文（在船成员）只走内部配送（入口统一），
// 未绑定时提供快递/自提/商城配送三种岸上方式。取值来自 @/sub-pages/utils/delivery-way，
// 展示文案走本页词条，保证切换语言后即时刷新。
const shipContextStore = useShipContextStore()

/** 配送方式取值仍来自 delivery-way 单一来源，展示文案走本页词条 */
const DELIVERY_WAY_LABEL_KEYS: Record<string, string> = {
  [DELIVERY_WAY_EXPRESS]: 'checkout.delivery.express',
  [DELIVERY_WAY_SELF_PICKUP]: 'checkout.delivery.pickup',
  [DELIVERY_WAY_MALL_DELIVERY]: 'checkout.delivery.mall',
  [DELIVERY_WAY_VESSEL_INTERNAL]: 'checkout.delivery.vessel',
}

function deliveryWayName(way?: null | string): string {
  if (!way)
    return ''
  const key = DELIVERY_WAY_LABEL_KEYS[way]
  return key ? t(key) : t('checkout.delivery.unknown')
}

const deliveryMethodColumns = computed(() =>
  deliveryWayOptions({ withVesselInternal: shipContextStore.hasVesselContext })
    .map(item => ({ ...item, name: deliveryWayName(item.value) })),
)

function openDeliverySheet() {
  // 仅一种方式时行是静态说明，不弹选择层（上方上下文卡片已解释配送规则）；
  // 导入直下单交接：方式在向导里已锁定（场景值随方式联动），同样不弹
  if (props.deliveryWayLocked || deliveryMethodColumns.value.length <= 1) {
    return
  }
  deliveryWayDraft.value = deliveryWay.value
  deliveryShow.value = true
}
function handleDeliveryWayChange(item: any) {
  deliveryWayDraft.value = item.value
}
function deliveryWayConfirm() {
  deliveryWay.value = deliveryWayDraft.value
  localOrder.value.deliveryWay = deliveryWay.value
  deliveryShow.value = false
  emit('deliveryWayChange', localOrder.value)
}

// 支付方式选项。货到付款仅商城配送/内部配送可用，支付方式不影响结算价格
const paymentMethodColumns = computed(() => [
  { name: t('checkout.payment.online'), value: '' },
  { name: t('checkout.payment.cod'), value: PAYMENT_TYPE_COD },
])

function openPaymentSheet() {
  paymentWayDraft.value = paymentWay.value
  paymentShow.value = true
}
function handlePaymentWayChange(item: any) {
  paymentWayDraft.value = item.value
}
function paymentWayConfirm() {
  paymentWay.value = paymentWayDraft.value
  paymentShow.value = false
  emit('paymentWayChange', paymentWay.value)
}

function showCoupon() {
  emit('showCoupon', localOrder.value)
}

function onPointsChange(event: any) {
  const enabled = typeof event === 'boolean' ? event : Boolean(event?.value)
  emit('pointsToggle', enabled)
}

// 新增：备注弹窗开关与草稿
const remarkPopupShow = ref(false)
const remarkDraft = ref('')

// 新增：打开备注弹窗并预填当前备注
function openRemarkPopup() {
  remarkDraft.value = localOrder.value.remark || ''
  remarkPopupShow.value = true
}

// 新增：保存备注到 localOrder.remark，并通知父组件
function saveRemark() {
  localOrder.value.remark = remarkDraft.value
  emit('remarkChange', localOrder.value)
  remarkPopupShow.value = false
}
</script>

<template>
  <view class="confirm-sections">
    <!-- 卡一：商品清单 -->
    <view class="section-card">
      <view
        v-for="(goods, goodsIndex) in localOrder.orderItemList"
        :key="goodsIndex"
        class="goods-row"
      >
        <image
          v-if="goods.picUrl"
          :src="goods.picUrl"
          class="goods-row__img"
          mode="aspectFill"
        />
        <!-- picUrl 缺失时给中性占位，避免空图把行撑出大段空白 -->
        <view v-else class="goods-row__img goods-row__img--empty">
          <text class="i-carbon:shopping-bag goods-row__img-placeholder" />
        </view>
        <view class="goods-row__info">
          <view class="goods-row__name-line">
            <text
              v-if="activityLabel(goods)"
              class="goods-row__tag"
              :class="{ 'is-strict': goods.couponStackable === false || goods.pointsStackable === false }"
            >
              {{ activityLabel(goods) }}
            </text>
            <view class="goods-row__name">
              <wd-text :lines="2" size="28rpx" color="inherit" :text="goods.spuName" />
            </view>
          </view>
          <text v-if="goods.specsInfo" class="goods-row__specs">
            {{ goods.specsInfo }}
          </text>
          <view class="goods-row__price-line">
            <view class="goods-row__price">
              <view class="goods-row__price-main">
                <price-text class="goods-row__price-value" :value="goods.paymentPrice" symbol="￥" />
              </view>
              <!-- 划线价用原价小计（结算回填）；促销价等于成交价时不划线，避免「自己划自己」 -->
              <view v-if="showOriginalPrice(goods)" class="goods-row__price-origin">
                <wd-text
                  :text="goods.originalTotalPrice"
                  size="22rpx"
                  mode="price"
                  decoration="line-through"
                  prefix="￥"
                  color="#c9cdd4"
                />
              </view>
            </view>
            <text class="goods-row__qty">
              x{{ goods.buyQuantity }}
            </text>
          </view>
        </view>
      </view>
    </view>

    <!-- 卡二：费用明细 -->
    <view class="section-card fee-card">
      <view class="fee-row">
        <text class="fee-row__label">
          {{ t('checkout.goods.amount') }}
        </text>
        <wd-text size="26rpx" color="#1d2129" :text="goodsAmount" mode="price" prefix="￥" />
      </view>
      <!-- 活动价让利单独成行：拼团/秒杀/折扣/新人价节省了多少，给用户正向的成交理由 -->
      <view v-if="activityDiscount > 0" class="fee-row">
        <text class="fee-row__label">
          {{ activityDiscountLabel }}
        </text>
        <wd-text size="26rpx" :color="THEME_COLOR" prefix="-￥" :text="activityDiscount.toFixed(2)" />
      </view>
      <view v-if="memberDiscount > 0" class="fee-row">
        <text class="fee-row__label">
          {{ t('checkout.goods.memberDiscount') }}
        </text>
        <wd-text size="26rpx" :color="THEME_COLOR" prefix="-￥" :text="memberDiscount.toFixed(2)" />
      </view>
      <!--
        运费：普通快递（1）与商城配送（3）都由后端 orderFreightHandler 计费；
        岸上企业导入走 way=3，之前只判 way=1 会让用户只看到合计增加、看不到运费来源。
      -->
      <view v-if="localOrder.deliveryWay === '1' || localOrder.deliveryWay === '3'" class="fee-row">
        <text class="fee-row__label">
          {{ t('checkout.goods.shipping') }}
        </text>
        <!-- 无地址时结算跳过运费计算（运费为 0 占位），明示「待计算」避免误解为免运费 -->
        <wd-text
          v-if="addressPending"
          size="26rpx"
          color="#86909c"
          :text="t('checkout.goods.shippingPending')"
        />
        <wd-text v-else size="26rpx" color="#1d2129" :text="localOrder.freightPrice" mode="price" prefix="￥" />
      </view>
      <view
        class="fee-row"
        :class="{ 'is-actionable': couponRowActionable }"
        @click="couponRowActionable && showCoupon()"
      >
        <text class="fee-row__label">
          {{ t('checkout.coupon.label') }}
        </text>
        <view class="fee-row__value">
          <!-- 活动商品禁叠券（纯秒杀/新人价单或所选券被服务端拒绝）：置灰给原因，不弹选择器 -->
          <view v-if="couponBlockReason" class="fee-row__block-reason">
            <wd-text
              size="24rpx"
              color="#c9cdd4"
              :lines="1"
              :text="couponBlockReason"
            />
          </view>
          <!-- 已选择优惠券 -->
          <wd-text
            v-else-if="hasSelectedCoupon"
            size="26rpx"
            :color="THEME_COLOR"
            prefix="-￥"
            :text="localOrder.couponPrice"
          />
          <!-- 有可选优惠券但未选择 -->
          <text v-else-if="hasAvailableCoupons" class="fee-row__hint">
            {{ t('checkout.coupon.select') }}
          </text>
          <!-- 无可用优惠券 -->
          <text v-else class="fee-row__hint is-muted">
            {{ t('checkout.coupon.noneAvailable') }}
          </text>
          <text
            v-if="couponRowActionable"
            class="i-carbon:chevron-right fee-row__arrow"
          />
        </view>
      </view>
      <!-- 积分抵扣：整单不可用（门槛/黑名单/场景）时置灰展示服务端原因，不提供开关 -->
      <view
        v-if="pointsBlockReason || Number(localOrder.pointsDeductMax) > 0 || Number(localOrder.pointsDeductPrice) > 0"
        class="fee-row"
      >
        <view class="fee-row__stack">
          <text class="fee-row__label">
            {{ t('checkout.points.label') }}
          </text>
          <!-- 不可用原因：后端按门槛/黑名单/场景生成口径文案，前端只展示不自造 -->
          <wd-text
            v-if="pointsBlockReason"
            size="22rpx"
            color="#c9cdd4"
            :lines="1"
            :text="pointsBlockReason"
          />
          <template v-else>
            <text class="fee-row__sub">
              {{ t('checkout.points.usable', { points: localOrder.pointsDeductMax || 0 }) }}<text v-if="Number(localOrder.pointsDeductPrice) > 0">
                {{ t('checkout.points.deducted', { amount: localOrder.pointsDeductPrice }) }}
              </text>
            </text>
            <!-- 混合单：活动/黑名单商品不参与积分抵扣，最多可抵按可叠部分计算 -->
            <text v-if="pointsPartialStack" class="fee-row__sub">
              {{ t('checkout.points.partial') }}
            </text>
          </template>
        </view>
        <wd-switch v-if="!pointsBlockReason" :model-value="pointsEnabled" size="36rpx" @change="onPointsChange" />
      </view>
      <view class="fee-card__total">
        <wd-text size="24rpx" color="#86909c" :text="t('checkout.goods.count', { count: localOrder.orderItemList.length })" />
        <view class="fee-card__total-amount">
          <text class="fee-card__total-label">
            {{ t('checkout.footer.total') }}
          </text>
          <wd-text
            :text="localOrder.paymentPrice"
            size="34rpx"
            color="#1d2129"
            mode="price"
            prefix="￥"
          />
        </view>
      </view>
    </view>

    <!-- 卡三：配送 / 支付 / 备注 -->
    <view class="section-card option-card">
      <view class="option-row" @click="openDeliverySheet">
        <text class="option-row__label">
          {{ t('checkout.delivery.label') }}
        </text>
        <view class="option-row__right">
          <text class="option-row__value">
            {{ deliveryWayName(deliveryWay) }}
          </text>
          <!-- 绑定船舶上下文后只剩内部配送一种方式，行转为静态说明、不再给切换箭头；
               导入直下单交接同理（方式在向导锁定） -->
          <text v-if="deliveryMethodColumns.length > 1 && !deliveryWayLocked" class="i-carbon:chevron-right option-row__arrow" />
        </view>
      </view>

      <!-- 支付方式：仅商城配送（3）/内部配送（4）可选货到付款；拼团单恒为在线支付，不展示选择入口 -->
      <view
        v-if="(deliveryWay === '3' || deliveryWay === '4') && !groupBuyOrder"
        class="option-row"
        @click="openPaymentSheet"
      >
        <text class="option-row__label">
          {{ t('checkout.payment.label') }}
        </text>
        <view class="option-row__right">
          <text class="option-row__value">
            {{ paymentWay === PAYMENT_TYPE_COD ? t('checkout.payment.cod') : t('checkout.payment.online') }}
          </text>
          <text class="i-carbon:chevron-right option-row__arrow" />
        </view>
      </view>

      <!-- 拼团单固定在线支付说明 -->
      <view v-if="groupBuyOrder" class="option-row">
        <text class="option-row__label">
          {{ t('checkout.payment.label') }}
        </text>
        <text class="option-row__value is-group-buy">
          {{ t('checkout.payment.onlineGroupBuy') }}
        </text>
      </view>

      <!-- 备注展示为可点击行，弹出层内再输入 -->
      <view class="option-row" @click="openRemarkPopup">
        <text class="option-row__label">
          {{ t('checkout.remark.label') }}
        </text>
        <view class="option-row__right">
          <text
            class="option-row__value option-row__remark"
            :class="{ 'is-placeholder': !(localOrder.remark && localOrder.remark.length) }"
          >
            {{ localOrder.remark && localOrder.remark.length ? localOrder.remark : t('checkout.remark.placeholder') }}
          </text>
          <text class="i-carbon:chevron-right option-row__arrow" />
        </view>
      </view>
    </view>

    <!-- 备注弹窗，底部保存按钮 -->
    <wd-action-sheet v-model="remarkPopupShow" position="bottom" :safe-area-inset-bottom="true" custom-class="rounded-t-20rpx" :title="t('checkout.remark.title')" @cancel="remarkPopupShow = false">
      <view class="px-24rpx pb-2">
        <wd-textarea
          v-model="remarkDraft"
          :placeholder="t('checkout.remark.placeholder')"
          no-border
          :maxlength="200"
          show-word-limit
          custom-class="custom-textarea-border rounded-10rpx m-0! p-0!"
        />
      </view>
      <view class="border-1 border-gray-300 border-t-solid p-1">
        <wd-button type="primary" block @click="saveRemark">
          {{ t('checkout.remark.save') }}
        </wd-button>
      </view>
    </wd-action-sheet>

    <!-- 配送方式弹窗 -->
    <wd-action-sheet v-model="deliveryShow" position="bottom" :safe-area-inset-bottom="true" custom-class="rounded-t-20rpx" :title="t('checkout.delivery.selectTitle')" @cancel="deliveryShow = false">
      <view class="px-24rpx pb-2">
        <view
          v-for="item in deliveryMethodColumns"
          :key="item.value"
          class="flex items-center justify-between border-b border-[#f0f0f0] py-24rpx"
          @click="handleDeliveryWayChange(item)"
        >
          <wd-text size="26rpx" color="inherit" :text="item.name" />
          <wd-icon
            v-if="deliveryWayDraft === item.value"
            name="check"
            size="26rpx"
            :color="THEME_COLOR"
          />
        </view>
      </view>
      <view class="border-1 border-gray-300 border-t-solid p-1">
        <wd-button block type="primary" @click="deliveryWayConfirm">
          {{ t('common.confirm') }}
        </wd-button>
      </view>
    </wd-action-sheet>
    <!-- 支付方式弹窗：货到付款下单后无需在线支付，收货后线下结算 -->
    <wd-action-sheet v-model="paymentShow" position="bottom" :safe-area-inset-bottom="true" custom-class="rounded-t-20rpx" :title="t('checkout.payment.selectTitle')" @cancel="paymentShow = false">
      <view class="px-24rpx pb-2">
        <view
          v-for="item in paymentMethodColumns"
          :key="item.value"
          class="flex items-center justify-between border-b border-[#f0f0f0] py-24rpx"
          @click="handlePaymentWayChange(item)"
        >
          <wd-text size="26rpx" color="inherit" :text="item.name" />
          <wd-icon
            v-if="paymentWayDraft === item.value"
            name="check"
            size="26rpx"
            :color="THEME_COLOR"
          />
        </view>
      </view>
      <view class="border-1 border-gray-300 border-t-solid p-1">
        <wd-button block type="primary" @click="paymentWayConfirm">
          {{ t('common.confirm') }}
        </wd-button>
      </view>
    </wd-action-sheet>
  </view>
</template>

<style lang="scss" scoped>
/*
 * 结算页三卡布局：商品清单 / 费用明细 / 配送支付备注。
 * 页面底色由宿主页提供浅灰，白卡 20rpx 圆角浮在上面。
 */
.confirm-sections {
  min-width: 0;
}

.section-card {
  margin: 20rpx 20rpx 0;
  padding: 8rpx 24rpx;
  border-radius: 20rpx;
  background: var(--app-card);
}

/* ---- 卡一：商品行 ---- */
.goods-row {
  display: flex;
  align-items: stretch;
  padding: 24rpx 0;
}

.goods-row__img {
  flex-shrink: 0;
  width: 160rpx;
  height: 160rpx;
  border-radius: 16rpx;
  background: var(--app-card-2);
}

.goods-row__img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.goods-row__img-placeholder {
  font-size: 56rpx;
  color: var(--app-text-3);
}

.goods-row__info {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: space-between;
  min-width: 0;
  margin-left: 20rpx;
}

.goods-row__name-line {
  display: flex;
  align-items: flex-start;
}

.goods-row__tag {
  flex-shrink: 0;
  margin-right: 8rpx;
  padding: 2rpx 10rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  line-height: 30rpx;
  color: #d46b08;
  background: #fff7e6;

  &.is-strict {
    color: #cf1322;
    background: #fff1f0;
  }
}

.goods-row__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 28rpx;
  font-weight: 500;
  color: var(--app-text);
  line-height: 38rpx;
}

.goods-row__specs {
  margin-top: 8rpx;
  overflow: hidden;
  font-size: 24rpx;
  color: var(--app-text-3);
  line-height: 32rpx;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.goods-row__price-line {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: 12rpx;
}

.goods-row__price {
  display: flex;
  align-items: baseline;
  min-width: 0;
}

.goods-row__price-main {
  flex-shrink: 0;
  font-weight: 600;
}

/* 主价：整数大、小数小（price-text 组件内用 em 跟随本字号） */
.goods-row__price-value {
  color: var(--wot-color-theme-primary, #ff2e4d);
  font-size: 30rpx;
  line-height: 1;
}

.goods-row__price-origin {
  margin-left: 12rpx;
}

.goods-row__qty {
  flex-shrink: 0;
  margin-left: 16rpx;
  font-size: 24rpx;
  color: var(--app-text-3);
  line-height: 34rpx;
}

/* ---- 卡二：费用明细 ---- */
.fee-card {
  padding-top: 16rpx;
}

.fee-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14rpx 0;
}

.fee-row__label {
  flex-shrink: 0;
  font-size: 26rpx;
  color: var(--app-text-2);
  line-height: 36rpx;
}

.fee-row__value {
  display: flex;
  align-items: center;
  min-width: 0;
  margin-left: 24rpx;
}

.fee-row__stack {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  margin-right: 24rpx;
}

.fee-row__sub {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: var(--app-text-3);
  line-height: 30rpx;
}

.fee-row__hint {
  font-size: 26rpx;
  color: var(--app-text-2);

  &.is-muted {
    color: var(--app-text-3);
  }
}

.fee-row__block-reason {
  max-width: 420rpx;
  overflow: hidden;
}

.fee-row__arrow {
  flex-shrink: 0;
  margin-left: 8rpx;
  font-size: 26rpx;
  color: var(--app-text-3);
}

.fee-row.is-actionable {
  .fee-row__hint {
    color: var(--app-text);
  }

  .fee-row__arrow {
    color: var(--app-text-3);
  }
}

.fee-card__total {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  margin-top: 8rpx;
  padding: 20rpx 0 24rpx;
  border-top: 1rpx solid var(--app-border);
}

.fee-card__total-amount {
  display: flex;
  align-items: baseline;
  margin-left: 12rpx;
  font-weight: 600;
}

.fee-card__total-label {
  margin-right: 8rpx;
  font-size: 26rpx;
  color: var(--app-text);
}

/* ---- 卡三：选项行 ---- */
.option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 92rpx;
  padding: 8rpx 0;
}

.option-row__label {
  flex-shrink: 0;
  font-size: 28rpx;
  color: var(--app-text);
}

.option-row__right {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: flex-end;
  min-width: 0;
  margin-left: 24rpx;
}

.option-row__value {
  overflow: hidden;
  font-size: 26rpx;
  color: var(--app-text-2);
  white-space: nowrap;
  text-overflow: ellipsis;

  &.is-placeholder {
    color: var(--app-text-3);
  }

  &.is-group-buy {
    color: #07c160;
  }
}

.option-row__remark {
  max-width: 460rpx;
}

.option-row__arrow {
  flex-shrink: 0;
  margin-left: 8rpx;
  font-size: 28rpx;
  color: var(--app-text-3);
}

.custom-textarea-border {
  border: 1px solid var(--app-border) !important;
}
</style>
