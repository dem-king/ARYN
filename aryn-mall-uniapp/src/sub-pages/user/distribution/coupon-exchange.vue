<script setup lang="ts">
import type { DistributionCouponPack } from '@/api/distribution/entity'
import { ref } from 'vue'
import { onPageScroll, onShow } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import {
  DISTRIBUTION_COUPON_EXCHANGE_RECORD_PATH,
  exchangeDistributionCouponPack,
  getDistributionCenterSummary,
  getDistributionCouponPackList,
} from '@/api/distribution/entity'
import {
  formatMoney,
  hasEnoughCommission,
  resolveCouponPackState,
} from './presentation'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'distribution-coupon-exchange',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '兑换优惠券',
  },
})

const { t } = useI18n()
const router = useRouter()
const globalLoading = useGlobalLoading()

/** 可兑换佣金（available_commission），兑换入口的余额口径 */
const availableCommission = ref(0)
const packs = ref<DistributionCouponPack[]>([])
const loading = ref(false)
/** 正在兑换的券包 id：兑换期间禁止重复点击（后端单事务，重复请求只会多扣一次余额） */
const exchangingPackId = ref('')

/**
 * 加载券包列表 + 我的可兑换佣金。
 *
 * 两个接口独立失败：券包挂了余额仍要显示，反之亦然；任一侧静默降级为空数据。
 */
async function loadData(withLoading = false) {
  if (withLoading) {
    globalLoading.loading(t('common.loading'))
  }
  loading.value = true
  try {
    const [summary, packList] = await Promise.all([
      getDistributionCenterSummary().catch(() => null),
      getDistributionCouponPackList().catch(() => []),
    ])
    availableCommission.value = Number(summary?.availableCommission ?? 0)
    packs.value = Array.isArray(packList) ? packList : []
  } finally {
    loading.value = false
    if (withLoading) {
      globalLoading.close()
    }
  }
}

/** 券包置灰原因：下架/售罄 */
function packDisabled(pack: DistributionCouponPack) {
  return resolveCouponPackState(pack) !== 'available'
}

/** 置灰/售罄角标文案；可兑换时返回空串 */
function packStateText(pack: DistributionCouponPack) {
  const state = resolveCouponPackState(pack)
  if (state === 'off-shelf')
    return t('accountSettings.distribution.couponPackOffShelf')
  if (state === 'sold-out')
    return t('accountSettings.distribution.couponPackSoldOut')
  return ''
}

function goExchangeRecord() {
  router.push({ path: DISTRIBUTION_COUPON_EXCHANGE_RECORD_PATH })
}

/**
 * 点击兑换：先做本地校验（置灰/余额不足），再弹二次确认。
 *
 * 服务端仍会重新校验库存与佣金（单事务），本地校验只是把可预期的失败提前，
 * 避免让用户看到后端业务错误弹窗。
 */
function handleExchange(pack: DistributionCouponPack) {
  if (exchangingPackId.value)
    return
  if (packDisabled(pack)) {
    uni.showToast({
      title: packStateText(pack),
      icon: 'none',
    })
    return
  }
  if (!hasEnoughCommission(availableCommission.value, pack.requiredCommission)) {
    uni.showToast({
      title: t('accountSettings.distribution.couponExchangeInsufficient'),
      icon: 'none',
    })
    return
  }
  uni.showModal({
    title: t('accountSettings.distribution.couponExchangeTitle'),
    content: t('accountSettings.distribution.couponExchangeConfirm', {
      commission: formatMoney(pack.requiredCommission),
      name: pack.packName,
    }),
    confirmText: t('common.confirm'),
    cancelText: t('common.cancel'),
    success: (result) => {
      if (result.confirm) {
        void submitExchange(pack)
      }
    },
  })
}

/**
 * 提交兑换。
 *
 * 业务错误（余额不足/发券失败）由请求层统一 toast 服务端文案，这里不再重复提示；
 * 成功先刷新余额与列表，再跳到兑换记录页，让「券已发放」的结果可核对。
 */
async function submitExchange(pack: DistributionCouponPack) {
  exchangingPackId.value = pack.id
  try {
    await exchangeDistributionCouponPack({ packId: pack.id })
    uni.showToast({
      title: t('accountSettings.distribution.couponExchangeSuccess'),
      icon: 'none',
    })
    await loadData()
    goExchangeRecord()
  } catch (error) {
    console.warn('[coupon-exchange] 兑换失败', error)
  } finally {
    exchangingPackId.value = ''
  }
}

onShow(() => {
  void loadData(true)
})

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
</script>

<template>
  <view>
    <hr-navbar :title="t('accountSettings.distribution.couponExchangeNavTitle')" />

    <view class="exchange-page">
      <!-- 我的可兑换佣金 -->
      <view class="balance-card">
        <view class="balance-copy">
          <view class="balance-label">
            {{ t('accountSettings.distribution.couponExchangeAvailable') }}
          </view>
          <view class="balance-value">
            ¥ {{ formatMoney(availableCommission) }}
          </view>
        </view>
        <view class="balance-record" hover-class="balance-record--pressed" @click="goExchangeRecord">
          {{ t('accountSettings.distribution.exchangeRecordEntry') }}
          <text class="i-carbon:chevron-right balance-record__arrow" />
        </view>
      </view>

      <!-- 券包列表 -->
      <view class="section-title">
        {{ t('accountSettings.distribution.couponPacks') }}
      </view>

      <view v-if="!loading && packs.length === 0" class="empty-card">
        {{ t('accountSettings.distribution.couponPackEmpty') }}
      </view>

      <view
        v-for="pack in packs"
        :key="pack.id"
        class="pack-card"
        :class="{ 'pack-card--disabled': packDisabled(pack) }"
      >
        <view class="pack-main">
          <view class="pack-face">
            <text class="pack-face__symbol">¥</text>
            <text class="pack-face__value">{{ formatMoney(pack.faceAmount) }}</text>
          </view>
          <view class="pack-info">
            <view class="pack-name">
              {{ pack.packName }}
            </view>
            <view class="pack-threshold">
              {{ pack.thresholdAmount && Number(pack.thresholdAmount) > 0
                ? t('accountSettings.distribution.couponPackThreshold', { amount: formatMoney(pack.thresholdAmount) })
                : t('accountSettings.distribution.couponPackNoThreshold') }}
            </view>
            <view class="pack-meta">
              <text v-if="pack.validDays" class="pack-meta__item">
                {{ t('accountSettings.distribution.couponPackValidDays', { days: pack.validDays }) }}
              </text>
              <text class="pack-meta__item">
                {{ pack.stock === null || pack.stock === undefined
                  ? t('accountSettings.distribution.couponPackUnlimitedStock')
                  : t('accountSettings.distribution.couponPackStock', { count: pack.stock }) }}
              </text>
            </view>
          </view>
        </view>
        <view class="pack-footer">
          <view class="pack-cost">
            {{ t('accountSettings.distribution.couponPackRequire', { amount: formatMoney(pack.requiredCommission) }) }}
          </view>
          <view
            class="pack-action"
            :class="{ 'pack-action--disabled': packDisabled(pack) }"
            @click="handleExchange(pack)"
          >
            {{ packStateText(pack) || t('accountSettings.distribution.couponPackExchange') }}
          </view>
        </view>
      </view>

      <view class="page-bottom-space" />
    </view>
  </view>
</template>

<style scoped lang="scss">
.exchange-page {
  padding: 24rpx;
}

.balance-card {
  border-radius: 20rpx;
  padding: 32rpx 28rpx;
  background: linear-gradient(135deg, #ff5f56, #ff2d55);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.balance-copy {
  flex: 1;
  min-width: 0;
}

.balance-label {
  font-size: 24rpx;
  opacity: 0.85;
}

.balance-value {
  margin-top: 12rpx;
  font-size: 52rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
}

.balance-record {
  margin-left: 16rpx;
  padding: 12rpx 20rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.22);
  font-size: 24rpx;
  display: flex;
  align-items: center;
}

.balance-record--pressed {
  opacity: 0.8;
}

.balance-record__arrow {
  margin-left: 4rpx;
  font-size: 22rpx;
}

.section-title {
  margin: 32rpx 4rpx 16rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--app-text);
}

.empty-card {
  border-radius: 16rpx;
  background: var(--app-card);
  padding: 60rpx 24rpx;
  text-align: center;
  font-size: 26rpx;
  color: var(--app-text-3);
}

.pack-card {
  border-radius: 16rpx;
  background: var(--app-card);
  padding: 24rpx;
  margin-bottom: 20rpx;
}

.pack-card--disabled {
  opacity: 0.55;
}

.pack-main {
  display: flex;
  align-items: flex-start;
}

.pack-face {
  width: 168rpx;
  flex: none;
  margin-right: 24rpx;
  border-radius: 12rpx;
  background: rgba(245, 108, 108, 0.1);
  padding: 20rpx 8rpx;
  text-align: center;
  color: #f56c6c;
}

.pack-face__symbol {
  font-size: 26rpx;
  font-weight: 600;
}

.pack-face__value {
  font-size: 48rpx;
  font-weight: 600;
  margin-left: 4rpx;
}

.pack-info {
  flex: 1;
  min-width: 0;
}

.pack-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--app-text);
}

.pack-threshold {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--app-text-3);
}

.pack-meta {
  margin-top: 12rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.pack-meta__item {
  font-size: 22rpx;
  color: var(--app-text-3);
  background: var(--app-card-2);
  border-radius: 6rpx;
  padding: 4rpx 10rpx;
}

.pack-footer {
  margin-top: 24rpx;
  padding-top: 20rpx;
  border-top: 1px solid var(--app-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pack-cost {
  font-size: 24rpx;
  color: var(--app-text-2);
}

.pack-action {
  min-width: 168rpx;
  height: 64rpx;
  line-height: 64rpx;
  padding: 0 28rpx;
  border-radius: 999rpx;
  text-align: center;
  font-size: 26rpx;
  color: #fff;
  background: linear-gradient(135deg, #ff5f56, #ff2d55);
}

.pack-action--disabled {
  color: var(--app-text-3);
  background: var(--app-card-2);
}

.page-bottom-space {
  height: 40rpx;
}
</style>
