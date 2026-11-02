<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { daysUntil } from '@/utils/coupon-display'
import { getCouponUnavailableInfo } from '@/utils/coupon-usability'

interface Props {
  couponPopup: boolean
  couponUserList: any[]
  orderPrice: number
  orderItemList: any[]
  /** 服务端下发的券门槛可计算金额（couponEligibleAmount）：门槛按可叠商品金额判断 */
  couponEligibleBase?: number | null
  /** 当前已选券（含确认页自动选中的最优券）：弹层打开时据此回显选中态 */
  couponUserId?: string
}

const props = defineProps<Props>()

const { t } = useI18n()

const emit = defineEmits<{
  closeCouponPopup: []
  couponConfirm: [couponUserId: string]
}>()

// 使用本地响应式变量替代直接绑定 prop
const localCouponPopup = ref(props.couponPopup)
const selectedCouponId = ref(props.couponUserId || '')
// 每次打开弹层都以当前已选券回显：否则重新打开后不显示选中态，
// 直接点「确认」会把已选券清掉（历史上这就是「自动选券后又丢券」的来源）
watch(() => props.couponPopup, (newVal) => {
  localCouponPopup.value = newVal
  if (newVal)
    selectedCouponId.value = props.couponUserId || ''
})
// 弹层打开期间父组件更新已选券（自动选券的二次结算回来）时同步回显
watch(() => props.couponUserId, (newVal) => {
  if (props.couponPopup)
    selectedCouponId.value = newVal || ''
})

function closeCouponPopup() {
  emit('closeCouponPopup')
}

function couponConfirm() {
  // 未选中任何券 = 用户选择「不使用优惠券」，显式回传空串清空
  emit('couponConfirm', selectedCouponId.value || '')
}
function getCouponUnavailableMap() {
  return props.couponUserList.reduce((map, item) => {
    map[item.id] = getCouponUnavailableInfo(item, props.orderItemList, props.couponEligibleBase)
    return map
  }, {})
}

const couponUnavailableMap = computed(getCouponUnavailableMap)

/**
 * 券不可用原因本地化。
 *
 * 判定口径由 coupon-usability 统一产出，这里只把该模块的三个固定文案
 * 映射成词条；未覆盖的文案原样展示，避免丢失信息。
 */
function localizeCouponReason(reason: string): string {
  if (!reason)
    return ''
  switch (reason) {
    case '活动商品不支持使用优惠券':
      return t('checkout.coupon.activityBlocked')
    case '仅部分商品可用（本单无可用商品）':
      return t('checkout.coupon.reasonPartialOnly')
    default: {
      const gap = reason.match(/^还差 ￥(.+) 可用该券$/)
      return gap ? t('checkout.coupon.reasonGap', { amount: gap[1] }) : reason
    }
  }
}

/** 券有效期状态带：按当前语言生成，切换语言后即时刷新 */
function expiryBand(value?: string | null): string {
  const days = daysUntil(value)
  if (days === null)
    return ''
  if (days < 0)
    return t('checkout.coupon.expired')
  if (days === 0)
    return t('checkout.coupon.expiresToday')
  if (days === 1)
    return t('checkout.coupon.expiresTomorrow')
  if (days <= 7)
    return t('checkout.coupon.expiresInDays', { days })
  const matched = String(value ?? '').match(/^\d{4}-(\d{2})-(\d{2})/)
  return matched ? t('checkout.coupon.expiresOn', { date: `${matched[1]}-${matched[2]}` }) : ''
}

// 选择事件处理：选择当前优惠券（并转为 string）
function handleSelect(item: any) {
  const unavailable = couponUnavailableMap.value?.[item.id]?.unavailable
  if (unavailable) {
    return
  }
  if (selectedCouponId.value === item.id) {
    selectedCouponId.value = ''
  }
  else {
    selectedCouponId.value = item.id
  }
}
/**
 * 列表 → 券卡视图模型。
 *
 * 状态带统一放有效期，不可用原因单独放在卡片下方（沿用原有红色说明文案），
 * 两处都写原因会重复——灰色的带已经把「不可用」表达清楚了。
 */
const cards = computed(() => props.couponUserList.map((item) => {
  const unavailable = couponUnavailableMap.value[item.id]
  return {
    item,
    band: expiryBand(item.validatTime || item.couponInfo?.receiveEndedAt),
    reason: unavailable?.unavailable ? localizeCouponReason(unavailable.reason) : '',
    tone: unavailable?.unavailable ? ('muted' as const) : ('active' as const),
    selectable: !unavailable?.unavailable,
  }
}))
</script>

<template>
  <wd-action-sheet
    v-model="localCouponPopup" safe-area-inset-bottom custom-class="coupon-action" :title="t('checkout.coupon.select')"
    @close="closeCouponPopup"
  >
    <scroll-view scroll-y class="coupon-scroll">
      <!-- 不使用优惠券：自动选券后用户要能显式取消，不能靠「再点一次已选券」猜 -->
      <view class="coupon-scroll__none" @click="selectedCouponId = ''">
        <text class="coupon-scroll__none-text">
          {{ t('checkout.coupon.noCoupon') }}
        </text>
        <wd-icon
          v-if="!selectedCouponId"
          name="check-circle-filled"
          :color="'#ff5500'"
          size="22px"
        />
        <wd-icon v-else name="circle1" color="#c8c8c8" size="22px" />
      </view>
      <view v-for="card in cards" :key="card.item.id" class="coupon-scroll__item">
        <coupon-card
          :coupon="card.item.couponInfo || {}"
          :band="card.band"
          :tone="card.tone"
          selector
          notch-color="#ffffff"
          :selected="selectedCouponId === card.item.id"
          :action-enabled="card.selectable"
          @select="handleSelect(card.item)"
        />
        <view v-if="card.reason" class="coupon-scroll__reason">
          {{ t('checkout.coupon.unavailableReason', { reason: card.reason }) }}
        </view>
      </view>
      <view v-if="cards.length === 0" class="coupon-scroll__empty">
        {{ t('checkout.coupon.noneAvailable') }}
      </view>
    </scroll-view>
    <view class="coupon-scroll__footer">
      <wd-button block type="primary" @click="couponConfirm">
        {{ t('common.confirm') }}
      </wd-button>
    </view>
  </wd-action-sheet>
</template>

<style lang="scss" scoped>
.coupon-action {
  max-height: 500px;
}
.coupon-scroll {
  height: 400px;
}
.coupon-scroll__item {
  padding: 16rpx 24rpx 0;
}
.coupon-scroll__none {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 96rpx;
  padding: 0 24rpx;
  margin: 16rpx 24rpx 0;
  background: var(--app-card);
  border-radius: 24rpx;
  box-shadow: 0 2rpx 6rpx rgba(15, 23, 42, 0.03), 0 12rpx 32rpx rgba(15, 23, 42, 0.05);
}
.coupon-scroll__none-text {
  font-size: 26rpx;
  color: var(--app-text);
}
.coupon-scroll__reason {
  padding: 4rpx 8rpx 0;
  font-size: 22rpx;
  color: #ff2d2d;
}
.coupon-scroll__empty {
  padding: 80rpx 0;
  font-size: 26rpx;
  color: var(--app-text-3);
  text-align: center;
}
.coupon-scroll__footer {
  margin: 16rpx;
}
</style>
