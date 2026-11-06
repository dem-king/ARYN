<script setup lang="ts">
import type { DistributionCouponExchangeRecord } from '@/api/distribution/entity'
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { getDistributionCouponExchangeRecordPage } from '@/api/distribution/entity'
import {
  formatMoney,
  getCouponExchangeStatusClass,
  getCouponExchangeStatusText,
} from './presentation'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'distribution-coupon-exchange-record',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '兑换记录',
  },
})

const { t } = useI18n()
const list = ref<DistributionCouponExchangeRecord[]>([])
const pagingRef = ref()

onLoad(() => {
  nextTick(() => {
    pagingRef.value?.reload()
  })
})

async function queryList(pageNo: number, pageSize: number) {
  try {
    const response = await getDistributionCouponExchangeRecordPage({
      current: pageNo,
      size: pageSize,
    })
    pagingRef.value?.complete(response.records || [])
  }
  catch (error) {
    // 失败必须告知 z-paging，否则列表永远停在加载态
    console.error('[coupon-exchange-record] 加载失败', error)
    pagingRef.value?.complete(false)
  }
}

/** 记录时间优先取兑换时间，旧数据回落创建时间 */
function recordTime(item: DistributionCouponExchangeRecord) {
  return item.exchangeTime || item.createTime || ''
}
</script>

<template>
  <hr-navbar :title="t('accountSettings.distribution.exchangeRecordNavTitle')" />
  <z-paging
    ref="pagingRef"
    v-model="list"
    :auto="false"
    :empty-view-text="t('accountSettings.distribution.exchangeRecordEmpty')"
    :empty-view-reload-text="t('common.retry')"
    @query="queryList"
    @scroll="handlePageScroll"
  >
    <hr-navbar-space />

    <view
      v-for="item in list"
      :key="item.id"
      class="record-card"
    >
      <view class="record-head">
        <view class="record-title">
          {{ item.packName || '-' }}
        </view>
        <view class="record-status" :class="getCouponExchangeStatusClass(item.status)">
          {{ getCouponExchangeStatusText(item.status) }}
        </view>
      </view>
      <view class="record-amount">
        {{ formatMoney(item.faceAmount) }}
      </view>
      <view class="record-footer">
        <view class="record-time">
          {{ t('accountSettings.distribution.exchangeRecordTime', { time: recordTime(item) }) }}
        </view>
        <view class="record-cost">
          {{ t('accountSettings.distribution.exchangeRecordConsumed', { amount: formatMoney(item.commissionAmount) }) }}
        </view>
      </view>
    </view>
  </z-paging>
</template>

<style scoped lang="scss">
.record-card {
  margin: 20rpx 24rpx 0;
  border-radius: 16rpx;
  background: var(--app-card);
  padding: 24rpx;
}

.record-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.record-title {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--app-text);
}

.record-status {
  margin-left: 16rpx;
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

.record-amount {
  margin-top: 16rpx;
  font-size: 44rpx;
  font-weight: 600;
  color: #f56c6c;
}

.record-footer {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.record-time,
.record-cost {
  font-size: 22rpx;
  color: var(--app-text-3);
}
</style>
