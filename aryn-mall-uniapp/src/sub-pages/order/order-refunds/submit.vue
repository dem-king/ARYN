<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { getById } from '@/api/order/orderItem'
import { addObj } from '@/api/order/orderRefunds'

definePage({
  name: 'refunds-submit',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '申请退款',
  },
})
const state = reactive<{ orderItem: any, orderRefund: any }>({
  orderItem: {},
  orderRefund: {
    status: '1',
    refundReason: '',
    refundType: '',
  },
})
const refundTypeColumns = ref([
  {
    value: '1',
    label: '仅退款',
  },
  {
    value: '2',
    label: '退货退款',
  },
])
const loading = ref(false)
const { confirm } = useGlobalMessage()
const { show } = useGlobalToast()
const router = useRouter()
const globalLoading = useGlobalLoading()
onLoad((options) => {
  getOrderItem(options?.orderItemId)
})
async function getOrderItem(orderItemId: string) {
  globalLoading.loading('加载中...')
  loading.value = true
  try {
    state.orderItem = await getById(orderItemId)
  }
  finally {
    loading.value = false
    globalLoading.close()
  }
}
function onsubmit() {
  state.orderRefund.orderItemId = state.orderItem.id
  if (!state.orderRefund.refundType) {
    return show('请选择服务类型')
  }
  confirm({
    title: '申请退款',
    msg: '确认提交申请？',
    closeOnClickModal: false,
    success: (res) => {
      if (res.action === 'confirm') {
        globalLoading.loading('加载中...')
        addObj(state.orderRefund).then(() => {
          globalLoading.close()
          uni.$emit('refresh')

          router.back()
        })
      }
    },
  })
}
</script>

<template>
  <hr-navbar title="申请退款" />
  <view class="p-2">
    <view v-if="!loading" class="rounded-xl bg-white p-2 dark:bg-[var(--wot-dark-background2)]">
      <view class="text-14px text-gray-800 font-bold">
        退款商品
      </view>
      <view class="flex pt-2">
        <image :src="state.orderItem.picUrl" class="h-160rpx w-160rpx flex-none rounded-lg" />
        <view class="ml-20rpx h-full flex flex-1 flex-col overflow-hidden">
          <view>
            <wd-text :lines="2" size="14px" color="inherit" :text="state.orderItem.spuName" />
          </view>
          <view v-if="state.orderItem.specsInfo">
            <wd-text custom-class="pt-1" size="12px" color="#909090" :text="state.orderItem.specsInfo" />
          </view>
          <view class="flex items-center justify-between pt-1">
            <wd-text :text="state.orderItem.salesPrice" color="red" mode="price" prefix="￥" />
            <wd-text size="26rpx" color="inherit" :text="`x${state.orderItem.buyQuantity}`" />
          </view>
        </view>
      </view>
      <wd-select-picker
        v-model="state.orderRefund.refundType" label="服务类型" title="选择服务类型" align-right type="radio"
        required placeholder="请选择服务类型" :columns="refundTypeColumns"
      />
      <wd-cell title="退款金额" required>
        <wd-text :text="state.orderItem?.paymentPrice" color="red" mode="price" prefix="￥" />
      </wd-cell>
      <wd-cell v-if="state.orderRefund.refundType === '2'" title="退货件数" required>
        <wd-text size="26rpx" color="inherit" :text="`x${state.orderItem?.buyQuantity}`" />
      </wd-cell>
      <wd-cell title="退款原因" title-width="100%" />
      <view>
        <wd-textarea v-model="state.orderRefund.refundReason" custom-class="wd-textarea" placeholder="请填写退款原因" />
      </view>
      <view class="fixed bottom-0 left-0 right-0 bg-white p-20rpx pb-[max(env(safe-area-inset-bottom),16rpx)]">
        <wd-button block type="primary" @click="onsubmit">
          提交申请
        </wd-button>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
:deep(.wd-textarea) {
  border: 1px solid #bfbfbf !important;
  border-radius: 10rpx !important;
  margin: 10px !important;
}
</style>
