<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { getById } from '@/api/order/orderRefunds'
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
  globalLoading.loading('加载中...')
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
function getStatusInfo(status: string) {
  const statusMap: Record<
    string | number,
    { icon: string, title: string, desc: string }
  > = {
    1: { icon: 'money-circle', title: '售后申请中', desc: '请等待商家处理' },
    2: {
      icon: 'money-circle',
      title: '待退货',
      desc: '商家同意退货，请尽快退回商品',
    },
    3: {
      icon: 'logistics',
      title: '退货处理中',
      desc: '退货正在处理中，请耐心等待',
    },
    4: {
      icon: 'logistics',
      title: '退货完成',
      desc: '商品已退回，等待商家确认',
    },
    5: {
      icon: 'money-circle',
      title: '退款处理中',
      desc: '退款正在处理中，请耐心等待',
    },
    6: {
      icon: 'check-outline',
      title: '退款成功',
      desc: '退款已完成，资金将原路返回',
    },
    7: {
      icon: 'close-outline',
      title: '退货失败',
      desc: '退货失败，请联系商家处理',
    },
    8: {
      icon: 'close-outline',
      title: '退款失败',
      desc: '退款失败，请联系商家处理',
    },
  }
  return (
    statusMap[status] || {
      icon: 'money-circle',
      title: '处理中',
      desc: '请等待',
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
        title: state.refunds.orderItem?.spuName || '退款申请',
      },
    }),
  })
}
</script>

<template>
  <view>
    <view v-if="!loading">
      <hr-navbar title="退款详情" />
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
                  申请数量：{{ state.refunds.orderItem.buyQuantity }}
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
            <wd-cell title="申请退款">
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
            <wd-cell title="申请时间">
              <view class="text-14px">
                {{ state.refunds.createTime }}
              </view>
            </wd-cell>
            <wd-cell title="申请原因">
              <view class="text-14px">
                {{ state.refunds.refundReason }}
              </view>
            </wd-cell>
            <wd-cell title="退款方式">
              <view class="text-14px">
                原支付返还
              </view>
            </wd-cell>
            <wd-cell
              v-if="state.refunds.userReceivedAccount"
              title="退款入账账户"
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
          联系客服
        </wd-button>
        <wd-gap height="50" />
      </view>
    </view>
  </view>
</template>
