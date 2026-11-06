<script setup lang="ts">
import type {
  DistributionCenterSummary,
  DistributionCommissionRecord,
  DistributionWithdrawProgress,
} from '@/api/distribution/entity'
import { reactive, ref } from 'vue'
import { onPageScroll, onShow } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import {
  DISTRIBUTION_COUPON_EXCHANGE_PATH,
  DISTRIBUTION_COUPON_EXCHANGE_RECORD_PATH,
  getDistributionCenterSummary,
  getDistributionCommissionRecordPage,
  getDistributionWithdrawProgressPage,
} from '@/api/distribution/entity'
import { SALES_WORKBENCH_PATH, getSalesWorkbench } from '@/api/sales/entity'
import {
  formatMoney,
  formatSignedCommission,
  getCommissionFlowText,
  getWithdrawStatusClass,
  getWithdrawStatusText,
} from './presentation'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'distribution-center',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '分销中心',
  },
})

const { t } = useI18n()
const router = useRouter()
const commissionList = ref<DistributionCommissionRecord[]>([])
const withdrawList = ref<DistributionWithdrawProgress[]>([])
/** 当前会员是否已登记为在职销售（决定「销售工作台」入口是否展示） */
const isSales = ref(false)

const centerSummary = ref<DistributionCenterSummary>({
  inviteUserCount: 0,
  totalCommission: 0,
  availableCommission: 0,
  pendingCommission: 0,
  frozenCommission: 0,
  withdrawnCommission: 0,
  commissionDebt: 0,
  pendingWithdrawCount: 0,
})

const commissionPage = reactive({
  current: 0,
  loading: false,
  size: 10,
  total: 0,
})
const withdrawPage = reactive({
  current: 0,
  loading: false,
  size: 10,
  total: 0,
})

const globalLoading = useGlobalLoading()

async function loadCenterSummary() {
  try {
    const response = await getDistributionCenterSummary()
    centerSummary.value = response
  } catch {
    // 接口不可用时不降级，保持空数据
  }
}

/** 销售工作台入口：仅已登记销售展示，请求失败/非销售一律隐藏 */
async function loadSalesEntrance() {
  try {
    const workbench = await getSalesWorkbench()
    isSales.value = Boolean(workbench?.isSales)
  } catch {
    isSales.value = false
  }
}

async function loadCommissionList(reset = false) {
  if (commissionPage.loading) return
  const current = reset ? 1 : commissionPage.current + 1
  commissionPage.loading = true
  try {
    const response = await getDistributionCommissionRecordPage({
      current,
      size: commissionPage.size,
    })
    const records = response.records || []
    commissionList.value = reset
      ? records
      : [...commissionList.value, ...records]
    commissionPage.current = response.current || current
    commissionPage.total = response.total || 0
  } catch {
    if (reset) commissionList.value = []
  } finally {
    commissionPage.loading = false
  }
}

/** 提现记录降级为只读历史（提现申请入口已下线，佣金改为兑换优惠券） */
async function loadWithdrawList(reset = false) {
  if (withdrawPage.loading) return
  const current = reset ? 1 : withdrawPage.current + 1
  withdrawPage.loading = true
  try {
    const response = await getDistributionWithdrawProgressPage({
      current,
      size: withdrawPage.size,
    })
    const records = response.records || []
    withdrawList.value = reset ? records : [...withdrawList.value, ...records]
    withdrawPage.current = response.current || current
    withdrawPage.total = response.total || 0
  } catch {
    if (reset) withdrawList.value = []
  } finally {
    withdrawPage.loading = false
  }
}

function goCouponExchange() {
  router.push({ path: DISTRIBUTION_COUPON_EXCHANGE_PATH })
}

function goCouponExchangeRecord() {
  router.push({ path: DISTRIBUTION_COUPON_EXCHANGE_RECORD_PATH })
}

function goSalesWorkbench() {
  router.push({ path: SALES_WORKBENCH_PATH })
}

onShow(() => {
  globalLoading.loading(t('common.loading'))
  Promise.all([
    loadCenterSummary(),
    loadCommissionList(true),
    loadWithdrawList(true),
    loadSalesEntrance(),
  ]).finally(() => {
    globalLoading.close()
  })
})

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
</script>

<template>
  <view>
    <hr-navbar :title="t('accountSettings.distribution.navTitle')" />

    <view class="distribution-page">
      <!-- 分销概览 -->
      <view class="card overview-card">
        <view class="overview-header">
          <view class="title"> {{ t('accountSettings.distribution.overview') }} </view>
        </view>
        <view class="stats-grid">
          <view class="stats-item">
            <view class="label"> {{ t('accountSettings.distribution.totalCommission') }} </view>
            <view class="value">
              {{ formatMoney(centerSummary.totalCommission) }}
            </view>
          </view>
          <view class="stats-item">
            <view class="label"> {{ t('accountSettings.distribution.availableCommission') }} </view>
            <view class="value amount-highlight">
              {{ formatMoney(centerSummary.availableCommission) }}
            </view>
          </view>
          <view class="stats-item">
            <view class="label"> {{ t('accountSettings.distribution.pendingSettlement') }} </view>
            <view class="value">
              {{ formatMoney(centerSummary.pendingCommission) }}
            </view>
          </view>
          <view class="stats-item">
            <view class="label"> {{ t('accountSettings.distribution.frozen') }} </view>
            <view class="value">
              {{ formatMoney(centerSummary.frozenCommission) }}
            </view>
          </view>
          <view class="stats-item">
            <view class="label"> {{ t('accountSettings.distribution.debt') }} </view>
            <view class="value debt-value">
              {{ formatMoney(centerSummary.commissionDebt) }}
            </view>
          </view>
          <view class="stats-item">
            <view class="label"> {{ t('accountSettings.distribution.withdrawn') }} </view>
            <view class="value">
              {{ formatMoney(centerSummary.withdrawnCommission) }}
            </view>
          </view>
        </view>
        <view class="invite-text">
          {{ t('accountSettings.distribution.inviteCount', { count: centerSummary.inviteUserCount || 0 }) }}
        </view>
      </view>

      <!-- 佣金兑换优惠券：提现入口已下线，佣金只能兑换券 -->
      <view class="card exchange-card">
        <view class="exchange-head">
          <view class="exchange-copy">
            <view class="title"> {{ t('accountSettings.distribution.exchangeCoupon') }} </view>
            <view class="exchange-desc">
              {{ t('accountSettings.distribution.exchangeCouponDesc') }}
            </view>
          </view>
          <view class="exchange-balance">
            <view class="balance-label">
              {{ t('accountSettings.distribution.couponExchangeAvailable') }}
            </view>
            <view class="balance-value">
              {{ formatMoney(centerSummary.availableCommission) }}
            </view>
          </view>
        </view>
        <view class="exchange-actions">
          <button class="primary-btn" @click="goCouponExchange">
            {{ t('accountSettings.distribution.exchangeCoupon') }}
          </button>
          <button class="ghost-btn" @click="goCouponExchangeRecord">
            {{ t('accountSettings.distribution.exchangeRecordEntry') }}
          </button>
        </view>
      </view>

      <!-- 销售工作台入口：仅在职销售可见（销售提成随工资发放，工作台内只读） -->
      <view
        v-if="isSales"
        class="card entry-row"
        hover-class="entry-row--pressed"
        @click="goSalesWorkbench"
      >
        <view class="entry-icon">
          <text class="i-carbon:chart-line entry-glyph" />
        </view>
        <view class="entry-body">
          <view class="entry-title">
            {{ t('userCenter.salesWorkbench') }}
          </view>
          <view class="entry-desc">
            {{ t('userCenter.salesWorkbenchDesc') }}
          </view>
        </view>
        <text class="i-carbon:chevron-right entry-arrow" />
      </view>

      <!-- 佣金记录 -->
      <view class="card">
        <view class="title"> {{ t('accountSettings.distribution.commissionRecords') }} </view>
        <view v-if="commissionList.length === 0" class="empty">
          {{ t('accountSettings.distribution.noCommissionRecords') }}
        </view>
        <view
          v-for="item in commissionList"
          v-else
          :key="item.id"
          class="record-item"
        >
          <view class="record-main">
            <view class="record-order">
              {{ item.bizOrderId || t('accountSettings.distribution.commissionCredit') }}
            </view>
            <view class="record-time">
              {{ item.createTime }}
            </view>
          </view>
          <view class="record-right">
            <view
              class="record-amount"
              :class="{ minus: item.flowType === 'EXPENSE' }"
            >
              {{ formatSignedCommission(item.amount, item.flowType) }}
            </view>
            <view class="record-status">
              {{ getCommissionFlowText(item.flowType) }}
            </view>
          </view>
        </view>
        <button
          v-if="commissionList.length < commissionPage.total"
          class="load-more-btn"
          :disabled="commissionPage.loading"
          @click="loadCommissionList()"
        >
          {{ commissionPage.loading ? t('common.loading') : t('accountSettings.distribution.loadMore') }}
        </button>
      </view>

      <!-- 历史提现记录：只读，入口已下线 -->
      <view class="card">
        <view class="title"> {{ t('accountSettings.distribution.withdrawHistory') }} </view>
        <view v-if="withdrawList.length === 0" class="empty">
          {{ t('accountSettings.distribution.noWithdrawRecords') }}
        </view>
        <view
          v-for="item in withdrawList"
          v-else
          :key="item.id"
          class="record-item"
        >
          <view class="record-main">
            <view class="record-order">
              {{ item.withdrawNo || item.id }}
            </view>
            <view class="record-time"> {{ t('accountSettings.distribution.applyTime', { time: item.createTime }) }} </view>
            <view v-if="item.rejectReason" class="record-time">
              {{ t('accountSettings.distribution.rejectReason', { reason: item.rejectReason }) }}
            </view>
            <view v-if="item.payoutNo" class="record-time">
              {{ t('accountSettings.distribution.payoutNo', { no: item.payoutNo }) }}
            </view>
          </view>
          <view class="record-right">
            <view class="record-amount minus">
              -{{ formatMoney(item.amount) }}
            </view>
            <view
              class="record-status"
              :class="getWithdrawStatusClass(item.status)"
            >
              {{ getWithdrawStatusText(item.status) }}
            </view>
          </view>
        </view>
        <button
          v-if="withdrawList.length < withdrawPage.total"
          class="load-more-btn"
          :disabled="withdrawPage.loading"
          @click="loadWithdrawList()"
        >
          {{ withdrawPage.loading ? t('common.loading') : t('accountSettings.distribution.loadMore') }}
        </button>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.distribution-page {
  margin: 24rpx;
}

.card {
  border-radius: 16rpx;
  background: var(--app-card);
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
}

.overview-card {
  margin-top: 8rpx;
}

.overview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--app-text);
}

.stats-grid {
  margin-top: 20rpx;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}

.stats-item {
  border-radius: 12rpx;
  background: var(--app-card-2);
  padding: 16rpx;
}

.label {
  font-size: 22rpx;
  color: var(--app-text-3);
}

.value {
  margin-top: 8rpx;
  font-size: 34rpx;
  color: var(--app-text);
  font-weight: 600;
}

.amount-highlight {
  color: #f56c6c;
}

.debt-value {
  color: #c45656;
}

.invite-text {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: var(--app-text-2);
}

/* ===== 佣金兑换优惠券 ===== */

.exchange-card {
  /* 用主题红低透明度叠加卡片底色：深色模式下不会出现一块高亮白底 */
  background: linear-gradient(135deg, rgba(255, 95, 86, 0.1), var(--app-card));
}

.exchange-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.exchange-copy {
  flex: 1;
  min-width: 0;
}

.exchange-desc {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--app-text-3);
}

.exchange-balance {
  margin-left: 16rpx;
  text-align: right;
}

.balance-label {
  font-size: 22rpx;
  color: var(--app-text-3);
}

.balance-value {
  margin-top: 6rpx;
  font-size: 40rpx;
  font-weight: 600;
  color: #f56c6c;
}

.exchange-actions {
  margin-top: 24rpx;
  display: flex;
  gap: 16rpx;
}

.primary-btn {
  flex: 1;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  color: #fff;
  background: linear-gradient(135deg, #ff5f56, #ff2d55);
}

.ghost-btn {
  flex: 1;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  color: var(--app-text-2);
  background: var(--app-card-2);
}

/* ===== 角色入口行 ===== */

.entry-row {
  display: flex;
  align-items: center;
}

.entry-row--pressed {
  opacity: 0.85;
}

.entry-icon {
  width: 72rpx;
  height: 72rpx;
  margin-right: 20rpx;
  border-radius: 50%;
  background: var(--app-card-2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.entry-glyph {
  font-size: 36rpx;
  color: #ff5f56;
}

.entry-body {
  flex: 1;
  min-width: 0;
}

.entry-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--app-text);
}

.entry-desc {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--app-text-3);
}

.entry-arrow {
  font-size: 28rpx;
  color: var(--app-text-3);
}

.record-item {
  padding: 18rpx 0;
  border-bottom: 1px solid var(--app-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.record-item:last-child {
  border-bottom: 0;
}

.record-main {
  flex: 1;
  min-width: 0;
}

.record-order {
  font-size: 26rpx;
  color: var(--app-text);
}

.record-time {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--app-text-3);
}

.record-right {
  margin-left: 16rpx;
  text-align: right;
}

.record-amount {
  font-size: 28rpx;
  font-weight: 600;
  color: #67c23a;
}

.record-amount.minus {
  color: #f56c6c;
}

.record-status {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--app-text-3);
}

.status-pending {
  color: #e6a23c;
}

.status-success {
  color: #67c23a;
}

.status-rejected {
  color: #f56c6c;
}

.empty {
  padding: 24rpx 0 8rpx;
  text-align: center;
  font-size: 26rpx;
  color: var(--app-text-3);
}

.load-more-btn {
  width: 100%;
  height: 72rpx;
  margin-top: 12rpx;
  border-radius: 8rpx;
  background: var(--app-card-2);
  color: var(--app-text-2);
  font-size: 26rpx;
  line-height: 72rpx;
}
</style>
