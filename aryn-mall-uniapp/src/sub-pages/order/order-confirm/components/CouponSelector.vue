<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { formatCouponExpiry } from '@/utils/coupon-display'
import { getCouponUnavailableInfo } from '@/utils/coupon-usability'

interface Props {
  couponPopup: boolean
  couponUserList: any[]
  orderPrice: number
  orderItemList: any[]
  /** 服务端下发的券门槛可计算金额（couponEligibleAmount）：门槛按可叠商品金额判断 */
  couponEligibleBase?: number | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  closeCouponPopup: []
  couponConfirm: [couponUserId: string]
}>()

// 使用本地响应式变量替代直接绑定 prop
const localCouponPopup = ref(props.couponPopup)
const selectedCouponId = ref()
// 监听 prop 变化同步到本地变量
watch(() => props.couponPopup, (newVal) => {
  localCouponPopup.value = newVal
})

function closeCouponPopup() {
  emit('closeCouponPopup')
}

function couponConfirm() {
  emit('couponConfirm', selectedCouponId.value)
}
function getCouponUnavailableMap() {
  return props.couponUserList.reduce((map, item) => {
    map[item.id] = getCouponUnavailableInfo(item, props.orderItemList, props.couponEligibleBase)
    return map
  }, {})
}

const couponUnavailableMap = computed(getCouponUnavailableMap)

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
    band: formatCouponExpiry(item.validatTime || item.couponInfo?.receiveEndedAt),
    reason: unavailable?.unavailable ? unavailable.reason : '',
    tone: unavailable?.unavailable ? ('muted' as const) : ('active' as const),
    selectable: !unavailable?.unavailable,
  }
}))
</script>

<template>
  <wd-action-sheet
    v-model="localCouponPopup" safe-area-inset-bottom custom-class="coupon-action" title="选择优惠券"
    @close="closeCouponPopup"
  >
    <scroll-view scroll-y class="coupon-scroll">
      <view v-for="card in cards" :key="card.item.id" class="coupon-scroll__item">
        <coupon-card
          :coupon="card.item.couponInfo || {}"
          :band="card.band"
          :tone="card.tone"
          selector
          :selected="selectedCouponId === card.item.id"
          :action-enabled="card.selectable"
          @select="handleSelect(card.item)"
        />
        <view v-if="card.reason" class="coupon-scroll__reason">
          优惠券不可用原因：{{ card.reason }}
        </view>
      </view>
      <view v-if="cards.length === 0" class="coupon-scroll__empty">
        暂无可用优惠券
      </view>
    </scroll-view>
    <view class="coupon-scroll__footer">
      <wd-button block type="primary" @click="couponConfirm">
        确认
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
.coupon-scroll__reason {
  padding: 4rpx 8rpx 0;
  font-size: 22rpx;
  color: #ff2d2d;
}
.coupon-scroll__empty {
  padding: 80rpx 0;
  font-size: 26rpx;
  color: #999;
  text-align: center;
}
.coupon-scroll__footer {
  margin: 16rpx;
}
</style>
