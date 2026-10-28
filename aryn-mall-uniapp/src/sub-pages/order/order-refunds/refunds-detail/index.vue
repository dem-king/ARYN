<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getById } from '@/sub-pages/api/order/orderRefunds'
import { customerServiceRoute } from '@/utils/message'

definePage({
  name: 'refunds-detail',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '退款详情',
  },
})
const refundId = ref()
const globalLoading = useGlobalLoading()
const { t } = useI18n()
const stepCurrent = ref(1)
const loading = ref(false)
const state = reactive<{ refunds: any }>({
  refunds: {},
})
onLoad((options) => {
  refundId.value = options?.id
  getDetail()
})
async function getDetail() {
  loading.value = true
  globalLoading.loading(t('common.loading'))
  try {
    state.refunds = await getById(refundId.value)
    if (state.refunds.status === '1' || state.refunds.status === '2') {
      stepCurrent.value = 1
    }
    else {
      stepCurrent.value = 2
    }
  }
  finally {
    loading.value = false
    globalLoading.close()
  }
}
const statusInfoMap = computed<
  Record<string | number, { icon: string, title: string, desc: string }>
>(() => ({
  1: {
    icon: 'money-circle',
    title: t('afterSales.detail.status.pendingReview.title'),
    desc: t('afterSales.detail.status.pendingReview.desc'),
  },
  2: {
    icon: 'money-circle',
    title: t('afterSales.detail.status.awaitingReturn.title'),
    desc: t('afterSales.detail.status.awaitingReturn.desc'),
  },
  3: {
    icon: 'logistics',
    title: t('afterSales.detail.status.returnProcessing.title'),
    desc: t('afterSales.detail.status.returnProcessing.desc'),
  },
  4: {
    icon: 'logistics',
    title: t('afterSales.detail.status.returnCompleted.title'),
    desc: t('afterSales.detail.status.returnCompleted.desc'),
  },
  5: {
    icon: 'money-circle',
    title: t('afterSales.detail.status.refundProcessing.title'),
    desc: t('afterSales.detail.status.refundProcessing.desc'),
  },
  6: {
    icon: 'check-outline',
    title: t('afterSales.detail.status.refundSuccess.title'),
    desc: t('afterSales.detail.status.refundSuccess.desc'),
  },
  7: {
    icon: 'close-outline',
    title: t('afterSales.detail.status.returnFailed.title'),
    desc: t('afterSales.detail.status.returnFailed.desc'),
  },
  8: {
    icon: 'close-outline',
    title: t('afterSales.detail.status.refundFailed.title'),
    desc: t('afterSales.detail.status.refundFailed.desc'),
  },
}))
function getStatusInfo(status: string) {
  return (
    statusInfoMap.value[status] || {
      icon: 'money-circle',
      title: t('afterSales.detail.fallbackTitle'),
      desc: t('afterSales.detail.fallbackDesc'),
    }
  )
}
function toCustomerService() {
  uni.navigateTo({
    url: customerServiceRoute({
      messageType: 'REFUND_CARD',
      payload: {
        amount: state.refunds.refundAmount
          ? `￥${state.refunds.refundAmount}`
          : '',
        image: state.refunds.orderItem?.picUrl || '',
        refundId: String(state.refunds.id),
        statusText: getStatusInfo(state.refunds.status).title,
        title: state.refunds.orderItem?.spuName || t('afterSales.detail.refundRequest'),
      },
    }),
  })
}
</script>

<template>
  <view>
    <view v-if="!loading">
      <hr-navbar :title="t('afterSales.detail.navTitle')" />
      <!-- 退款明细 -->
      <view
        class="h-200rpx flex items-center justify-center bg-primary text-white"
      >
        <view>
          <view class="flex items-center text-28rpx font-bold">
            <wd-icon
              :name="getStatusInfo(state.refunds.status).icon"
              size="22px"
              color="#ffffff"
            />
            <view class="pl-10rpx">
              {{ getStatusInfo(state.refunds.status).title }}
            </view>
          </view>
          <view class="pt-20rpx text-center text-24rpx">
            {{ getStatusInfo(state.refunds.status).desc }}
          </view>
        </view>
      </view>
      <view
        class="m-2 rounded-xl bg-white p-2 dark:bg-[var(--wot-dark-background2)]"
      >
        <view v-if="state.refunds.orderItem">
          <view class="flex p-2">
            <image
              :src="state.refunds.orderItem.picUrl"
              class="h-160rpx w-160rpx flex-none rounded-lg"
            />
            <view class="ml-20rpx h-full flex flex-1 flex-col overflow-hidden">
              <view class="text-28rpx">
                <wd-text
                  :text="state.refunds.orderItem.spuName"
                  :lines="2"
                  color="inherit"
                  size="14px"
                />
              </view>
              <view v-if="state.refunds.orderItem.specsInfo">
                <wd-text
                  :text="state.refunds.orderItem.specsInfo"
                  custom-class="pt-1"
                  :lines="1"
                  color="#909090"
                  size="12px"
                />
              </view>
              <view class="mt-auto flex items-center justify-between pt-10rpx">
                <text class="text-12px">
                  {{ t('afterSales.detail.applyQty', { count: state.refunds.orderItem.buyQuantity }) }}
                </text>
                <wd-text
                  :text="state.refunds.refundAmount"
                  mode="price"
                  type="error"
                  prefix="￥"
                  size="28rpx"
                />
              </view>
            </view>
          </view>
          <wd-divider />
          <wd-cell-group>
            <wd-cell :title="t('afterSales.detail.refundAmount')">
              <view>
                <wd-text
                  :text="state.refunds.refundAmount"
                  mode="price"
                  type="error"
                  prefix="￥"
                  size="26rpx"
                />
              </view>
            </wd-cell>
            <wd-cell :title="t('afterSales.detail.applyTime')">
              <view class="text-14px">
                {{ state.refunds.createTime }}
              </view>
            </wd-cell>
            <wd-cell :title="t('afterSales.detail.applyReason')">
              <view class="text-14px">
                {{ state.refunds.refundReason }}
              </view>
            </wd-cell>
            <wd-cell :title="t('afterSales.detail.refundMethod')">
              <view class="text-14px">
                {{ t('afterSales.detail.originalPayment') }}
              </view>
            </wd-cell>
            <wd-cell
              v-if="state.refunds.userReceivedAccount"
              :title="t('afterSales.detail.receivingAccount')"
            >
              <view class="text-14px">
                {{ state.refunds.userReceivedAccount }}
              </view>
            </wd-cell>
          </wd-cell-group>
        </view>
        <wd-button
          block
          plain
          type="info"
          icon="service"
          @click="toCustomerService"
        >
          {{ t('afterSales.detail.contactSupport') }}
        </wd-button>
        <wd-gap height="50" />
      </view>
    </view>
  </view>
</template>
