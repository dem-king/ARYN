<script setup lang="ts">
import type {
  DistributionCenterSummary,
  DistributionCommissionRecord,
  DistributionWithdrawProgress,
} from '@/api/distribution/entity'
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import {
  getDistributionCenterSummary,
  getDistributionCommissionRecordPage,
  getDistributionWithdrawProgressPage,
  submitDistributionWithdrawApply,
} from '@/api/distribution/entity'
import {
  formatMoney,
  formatSignedCommission,
  getCommissionFlowText,
  getWithdrawStatusClass,
  getWithdrawStatusText,
} from './presentation'

definePage({
  name: 'distribution-center',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '分销中心',
  },
})

const { t } = useI18n()
const commissionList = ref<DistributionCommissionRecord[]>([])
const withdrawList = ref<DistributionWithdrawProgress[]>([])

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
const submitting = ref(false)

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

const withdrawForm = reactive({
  amountText: '',
  accountType: 'WECHAT',
  accountNo: '',
  accountName: '',
  remark: '',
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

function resetWithdrawForm() {
  withdrawForm.amountText = ''
  withdrawForm.accountNo = ''
  withdrawForm.accountName = ''
  withdrawForm.remark = ''
}

async function submitWithdraw() {
  const amount = Number(withdrawForm.amountText)
  if (!amount || Number.isNaN(amount) || amount <= 0) {
    uni.showToast({
      title: t('accountSettings.distribution.invalidAmount'),
      icon: 'none',
    })
    return
  }
  if (amount > Number(centerSummary.value.availableCommission || 0)) {
    uni.showToast({
      title: t('accountSettings.distribution.amountExceeds'),
      icon: 'none',
    })
    return
  }
  if (!withdrawForm.accountNo.trim() || !withdrawForm.accountName.trim()) {
    uni.showToast({
      title: t('accountSettings.distribution.incompleteAccount'),
      icon: 'none',
    })
    return
  }

  submitting.value = true
  globalLoading.loading(t('accountSettings.distribution.submitting'))
  try {
    await submitDistributionWithdrawApply({
      amount,
      accountType: withdrawForm.accountType,
      accountNo: withdrawForm.accountNo.trim(),
      accountName: withdrawForm.accountName.trim(),
      remark: withdrawForm.remark.trim(),
    })
    uni.showToast({
      title: t('accountSettings.distribution.withdrawSubmitted'),
      icon: 'none',
    })
    resetWithdrawForm()
    await loadCenterSummary()
    await loadWithdrawList(true)
  } catch {
    uni.showToast({
      title: t('accountSettings.distribution.withdrawFailed'),
      icon: 'none',
    })
  } finally {
    submitting.value = false
    globalLoading.close()
  }
}

onShow(() => {
  globalLoading.loading(t('common.loading'))
  Promise.all([
    loadCenterSummary(),
    loadCommissionList(true),
    loadWithdrawList(true),
  ]).finally(() => {
    globalLoading.close()
  })
})
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
            <view class="label"> {{ t('accountSettings.distribution.withdrawable') }} </view>
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

      <!-- 提现申请 -->
      <view class="card">
        <view class="title"> {{ t('accountSettings.distribution.withdrawApply') }} </view>
        <view class="form-row">
          <view class="form-label"> {{ t('accountSettings.distribution.withdrawAmount') }} </view>
          <input
            v-model="withdrawForm.amountText"
            class="form-input"
            type="digit"
            :placeholder="t('accountSettings.distribution.withdrawAmountPlaceholder')"
            placeholder-class="placeholder"
          />
        </view>
        <view class="form-row">
          <view class="form-label"> {{ t('accountSettings.distribution.withdrawAccount') }} </view>
          <input
            v-model="withdrawForm.accountNo"
            class="form-input"
            type="text"
            :placeholder="t('accountSettings.distribution.withdrawAccountPlaceholder')"
            placeholder-class="placeholder"
          />
        </view>
        <view class="form-row">
          <view class="form-label"> {{ t('accountSettings.distribution.payee') }} </view>
          <input
            v-model="withdrawForm.accountName"
            class="form-input"
            type="text"
            :placeholder="t('accountSettings.distribution.payeePlaceholder')"
            placeholder-class="placeholder"
          />
        </view>
        <view class="form-row">
          <view class="form-label"> {{ t('accountSettings.distribution.remark') }} </view>
          <input
            v-model="withdrawForm.remark"
            class="form-input"
            type="text"
            :placeholder="t('accountSettings.distribution.remarkPlaceholder')"
            placeholder-class="placeholder"
          />
        </view>
        <button
          class="submit-btn"
          :disabled="submitting"
          @click="submitWithdraw"
        >
          {{ submitting ? t('accountSettings.distribution.submitting') : t('accountSettings.distribution.submitWithdraw') }}
        </button>
      </view>

      <!-- 提现进度 -->
      <view class="card">
        <view class="title"> {{ t('accountSettings.distribution.withdrawProgress') }} </view>
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
  background: #fff;
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
  color: #303133;
}

.stats-grid {
  margin-top: 20rpx;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}

.stats-item {
  border-radius: 12rpx;
  background: #f7f8fa;
  padding: 16rpx;
}

.label {
  font-size: 22rpx;
  color: #909399;
}

.value {
  margin-top: 8rpx;
  font-size: 34rpx;
  color: #303133;
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
  color: #606266;
}

.record-item {
  padding: 18rpx 0;
  border-bottom: 1px solid #f2f2f2;
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
  color: #303133;
}

.record-time {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #909399;
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
  color: #909399;
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

.form-row {
  margin-top: 16rpx;
}

.form-label {
  margin-bottom: 8rpx;
  font-size: 24rpx;
  color: #606266;
}

.form-input {
  width: 100%;
  height: 74rpx;
  border-radius: 12rpx;
  background: #f7f8fa;
  padding: 0 20rpx;
  box-sizing: border-box;
  font-size: 26rpx;
  color: #303133;
}

.placeholder {
  color: #c0c4cc;
}

.submit-btn {
  margin-top: 24rpx;
  width: 100%;
  height: 78rpx;
  line-height: 78rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  color: #fff;
  background: linear-gradient(135deg, #ff5f56, #ff2d55);
}

.submit-btn[disabled] {
  opacity: 0.7;
}

.empty {
  padding: 24rpx 0 8rpx;
  text-align: center;
  font-size: 26rpx;
  color: #c0c4cc;
}

.load-more-btn {
  width: 100%;
  height: 72rpx;
  margin-top: 12rpx;
  border-radius: 8rpx;
  background: #f2f3f5;
  color: #606266;
  font-size: 26rpx;
  line-height: 72rpx;
}
</style>
