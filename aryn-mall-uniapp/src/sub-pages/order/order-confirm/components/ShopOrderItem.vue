<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { useShipContextStore } from '@/store/shipContextStore'
import { deliveryWayLabel, deliveryWayOptions } from '@/sub-pages/utils/delivery-way'
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
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'deliveryWayChange', order: Order): void
  (e: 'paymentWayChange', paymentWay: string): void
  (e: 'remarkChange', order: Order): void
  (e: 'showCoupon', order: Order): void
  (e: 'pointsToggle', enabled: boolean): void
}>()

const PAYMENT_TYPE_COD = '3'

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
  return allItemsCouponBlocked.value ? '活动商品不支持使用优惠券' : ''
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
const activityDiscountLabel = computed(() => (props.groupBuyOrder ? '拼团优惠' : '活动优惠'))

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
const ACTIVITY_TYPE_LABELS: Record<string, string> = {
  groupBuy: '拼团价',
  seckill: '秒杀价',
  discount: '折扣价',
  newbie: '新人价',
}
function activityLabel(goods: OrderItem) {
  return goods.activityType ? (ACTIVITY_TYPE_LABELS[goods.activityType] || '') : ''
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
// 未绑定时提供快递/自提/商城配送三种岸上方式。文案与取值统一来自 @/sub-pages/utils/delivery-way。
const shipContextStore = useShipContextStore()
const deliveryMethodColumns = computed(() =>
  deliveryWayOptions({ withVesselInternal: shipContextStore.hasVesselContext }),
)

function openDeliverySheet() {
  // 仅一种方式时行是静态说明，不弹选择层（上方上下文卡片已解释配送规则）
  if (deliveryMethodColumns.value.length <= 1) {
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
  { name: '在线支付', value: '' },
  { name: '货到付款', value: PAYMENT_TYPE_COD },
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
  <view class="m-2 rounded-xl bg-white p-2 dark:bg-[var(--wot-dark-background2)]">
    <!-- 订单商品内容 -->
    <view>
      <view
        v-for="(goods, goodsIndex) in localOrder.orderItemList" :key="goodsIndex"
        class="flex rounded-lg bg-white px-20rpx py-20rpx"
      >
        <image :src="goods.picUrl" class="h-160rpx w-160rpx flex-none rounded-lg" />
        <view class="ml-20rpx h-full flex flex-1 flex-col overflow-hidden">
          <view class="flex justify-between">
            <view class="mr-20rpx flex-1 overflow-hidden">
              <view class="flex items-center">
                <text
                  v-if="activityLabel(goods)"
                  class="mr-8rpx flex-none rounded-6rpx px-8rpx text-20rpx"
                  :class="goods.couponStackable === false || goods.pointsStackable === false ? 'bg-[#fff1f0] color-[#cf1322]' : 'bg-[#fff7e6] color-[#d46b08]'"
                >
                  {{ activityLabel(goods) }}
                </text>
                <view class="flex-1 overflow-hidden">
                  <wd-text :lines="2" size="26rpx" color="inherit" :text="goods.spuName" />
                </view>
              </view>
              <view v-if="goods.specsInfo" class="pt-10rpx">
                <wd-text custom-class="pt-10rpx" size="24rpx" color="#909090" :text="goods.specsInfo" />
              </view>
            </view>
            <view class="flex flex-shrink-0 flex-col items-end">
              <wd-text :text="goods.paymentPrice" size="14px" color="red" mode="price" prefix=" ￥" />
              <!-- 划线价用原价小计（结算回填）；促销价等于成交价时不划线，避免「自己划自己」 -->
              <wd-text
                v-if="showOriginalPrice(goods)"
                :text="goods.originalTotalPrice"
                size="12px"
                mode="price"
                decoration="line-through"
                prefix=" ￥"
              />
              <wd-text size="13px" :text="`x${goods.buyQuantity}`" />
            </view>
          </view>
        </view>
      </view>
    </view>
    <view>
      <view class="rounded-lg bg-white p-2">
        <view class="flex items-center justify-between pb-20rpx">
          <text class="text-14px">
            商品金额
          </text>
          <wd-text size="26rpx" color="inherit" :text="goodsAmount" mode="price" prefix="￥" />
        </view>
        <!-- 活动价让利单独成行：拼团/秒杀/折扣/新人价节省了多少，给用户正向的成交理由 -->
        <view v-if="activityDiscount > 0" class="flex items-center justify-between pb-20rpx">
          <text class="text-14px">
            {{ activityDiscountLabel }}
          </text>
          <wd-text size="26rpx" color="red" prefix="-￥" :text="activityDiscount.toFixed(2)" />
        </view>
        <view v-if="memberDiscount > 0" class="flex items-center justify-between pb-20rpx">
          <text class="text-14px">
            会员优惠
          </text>
          <wd-text size="26rpx" color="red" prefix="-￥" :text="memberDiscount.toFixed(2)" />
        </view>
        <view v-if="localOrder.deliveryWay === '1'" class="flex items-center justify-between pb-20rpx">
          <text class="text-14px">
            运费
          </text>
          <wd-text size="26rpx" color="inherit" :text="localOrder.freightPrice" mode="price" prefix="￥" />
        </view>
        <view class="flex items-center justify-between pb-20rpx">
          <text class="text-14px">
            优惠券
          </text>
          <view class="flex items-center">
            <!-- 活动商品禁叠券（纯秒杀/新人价单或所选券被服务端拒绝）：置灰给原因，不弹选择器 -->
            <wd-text
              v-if="couponBlockReason"
              size="22rpx"
              color="#909090"
              :text="couponBlockReason"
            />

            <!-- 已选择优惠券 -->
            <wd-text
              v-else-if="hasSelectedCoupon"
              size="26rpx"
              color="red"
              prefix="-￥"
              :text="localOrder.couponPrice"
              @click="showCoupon"
            />

            <!-- 有可选优惠券但未选择 -->
            <text v-else-if="hasAvailableCoupons" class="pr-4rpx text-26rpx" @click="showCoupon">
              选择优惠券
            </text>

            <!-- 无可用优惠券 -->
            <wd-text
              v-else
              size="22rpx"
              text="无可用优惠券"
            />
            <text v-if="!couponBlockReason && (hasSelectedCoupon || hasAvailableCoupons)" class="i-carbon:chevron-right text-14px" />
          </view>
        </view>
        <!-- 积分抵扣：整单不可用（门槛/黑名单/场景）时置灰展示服务端原因，不提供开关 -->
        <view
          v-if="pointsBlockReason || Number(localOrder.pointsDeductMax) > 0 || Number(localOrder.pointsDeductPrice) > 0"
          class="flex items-center justify-between pb-20rpx"
        >
          <view class="flex flex-col">
            <text class="text-14px">
              积分抵扣
            </text>
            <!-- 不可用原因：后端按门槛/黑名单/场景生成口径文案，前端只展示不自造 -->
            <wd-text
              v-if="pointsBlockReason"
              size="22rpx"
              color="#909090"
              :text="pointsBlockReason"
            />
            <template v-else>
              <text class="text-22rpx color-[#909090]">
                可用{{ localOrder.pointsDeductMax || 0 }}积分<text v-if="Number(localOrder.pointsDeductPrice) > 0">
                  ，已抵￥{{ localOrder.pointsDeductPrice }}
                </text>
              </text>
              <!-- 混合单：活动/黑名单商品不参与积分抵扣，最多可抵按可叠部分计算 -->
              <text v-if="pointsPartialStack" class="text-22rpx color-[#909090]">
                部分商品不参与积分抵扣
              </text>
            </template>
          </view>
          <wd-switch v-if="!pointsBlockReason" :model-value="pointsEnabled" size="36rpx" @change="onPointsChange" />
        </view>
        <view class="flex items-center justify-end pb-20rpx">
          <wd-text size="22rpx" color="#909090" :text="`共${localOrder.orderItemList.length}件`" />
          <wd-text
            custom-class="pl-10rpx" size="28rpx" :text="localOrder.paymentPrice" color="red" mode="price"
            prefix="￥"
          />
        </view>
        <view class="flex items-center justify-between pb-20rpx">
          <text class="text-14px">
            配送方式
          </text>
          <view>
            <text class="pr-4rpx text-26rpx" @click="openDeliverySheet">
              {{ deliveryWayLabel(deliveryWay) }}
            </text>
            <!-- 绑定船舶上下文后只剩内部配送一种方式，行转为静态说明、不再给切换箭头 -->
            <text v-if="deliveryMethodColumns.length > 1" class="i-carbon:chevron-right text-14px" />
          </view>
        </view>

        <!-- 支付方式：仅商城配送（3）/内部配送（4）可选货到付款；拼团单恒为在线支付，不展示选择入口 -->
        <view
          v-if="(deliveryWay === '3' || deliveryWay === '4') && !groupBuyOrder"
          class="flex items-center justify-between pb-20rpx"
        >
          <text class="text-14px">
            支付方式
          </text>
          <view>
            <text class="pr-4rpx text-26rpx" @click="openPaymentSheet">
              {{ paymentWay === PAYMENT_TYPE_COD ? '货到付款' : '在线支付' }}
            </text>
            <text class="i-carbon:chevron-right text-14px" />
          </view>
        </view>

        <!-- 拼团单固定在线支付说明 -->
        <view
          v-if="groupBuyOrder"
          class="flex items-center justify-between pb-20rpx"
        >
          <text class="text-14px">
            支付方式
          </text>
          <text class="text-26rpx color-[#07c160]">
            在线支付（拼团需先付款成团）
          </text>
        </view>

        <!-- 修改：备注展示为可点击行，弹出层内再输入 -->
        <view class="flex items-center justify-between pb-20rpx" @click="openRemarkPopup">
          <view class="text-14px">
            备注
          </view>
          <view class="flex items-center">
            <wd-text
              size="26rpx"
              color="inherit"
              :lines="1"
              custom-class="remark-ellipsis"
              :text="localOrder.remark && localOrder.remark.length ? localOrder.remark : '请填写备注'"
            />
            <text class="i-carbon:chevron-right text-14px" />
          </view>
        </view>
      </view>
    </view>

    <!-- 新增：备注弹窗，底部保存按钮 -->
    <wd-action-sheet v-model="remarkPopupShow" position="bottom" :safe-area-inset-bottom="true" custom-class="rounded-t-20rpx" title="订单备注" @cancel="remarkPopupShow = false">
      <view class="px-24rpx pb-2">
        <wd-textarea
          v-model="remarkDraft"
          placeholder="请填写备注"
          no-border
          :maxlength="200"
          show-word-limit
          custom-class="custom-textarea-border rounded-10rpx m-0! p-0!"
        />
      </view>
      <view class="border-1 border-gray-300 border-t-solid p-1">
        <wd-button type="primary" block @click="saveRemark">
          保存
        </wd-button>
      </view>
    </wd-action-sheet>

    <!-- 配送方式弹窗（替换原 wd-action-sheet） -->
    <wd-action-sheet v-model="deliveryShow" position="bottom" :safe-area-inset-bottom="true" custom-class="rounded-t-20rpx" title="选择配送方式" @cancel="deliveryShow = false">
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
            color="#07c160"
          />
        </view>
      </view>
      <view class="border-1 border-gray-300 border-t-solid p-1">
        <wd-button block type="primary" @click="deliveryWayConfirm">
          确认
        </wd-button>
      </view>
    </wd-action-sheet>
    <!-- 支付方式弹窗：货到付款下单后无需在线支付，收货后线下结算 -->
    <wd-action-sheet v-model="paymentShow" position="bottom" :safe-area-inset-bottom="true" custom-class="rounded-t-20rpx" title="选择支付方式" @cancel="paymentShow = false">
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
            color="#07c160"
          />
        </view>
      </view>
      <view class="border-1 border-gray-300 border-t-solid p-1">
        <wd-button block type="primary" @click="paymentWayConfirm">
          确认
        </wd-button>
      </view>
    </wd-action-sheet>
  </view>
</template>

<style lang="scss" scoped>
.custom-textarea-border {
  border: 1px solid #bfbfbf !important;
}
.remark-ellipsis {
  max-width: 450rpx; /* 可按需调整宽度 */
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
