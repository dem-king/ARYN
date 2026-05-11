<script setup lang="ts" name="orderOperation">
import { computed } from 'vue'
import { orderCancel, orderDel, orderReceiver } from '@/api/order/orderInfo'

const props = defineProps({
  orderInfo: {
    type: Object as () => any,
    default: () => ({}),
  },
  buttonSize: {
    type: String,
    default: 'medium',
  },
})

const emit = defineEmits(['orderCancel', 'orderDel', 'orderReceiver', 'closeQRCode'])

const { confirm } = useGlobalMessage()
const router = useRouter()
const showQRCodePopup = ref(false)

/** 通用 confirm */
async function useConfirm(title: string, msg: string) {
  return new Promise((resolve) => {
    confirm({
      title,
      msg,
      closeOnClickModal: false,
      success: resolve,
    })
  })
}

/** 立即付款 */
function onPay() {
  router.push({
    name: 'order-pay',
    params: { orderNo: props.orderInfo.orderNo },
  })
}

/** 取消订单 */
async function onCancel() {
  const res = await useConfirm('取消订单', '是否取消订单？')
  if (res.action === 'confirm') {
    const { data } = await orderCancel(props.orderInfo.id)
    emit('orderCancel', data)
  }
}

/** 删除订单 */
async function onDel() {
  const res = await useConfirm('删除订单', '是否删除订单？')
  if (res.action === 'confirm') {
    const { data } = await orderDel(props.orderInfo.id)
    emit('orderDel', data)
  }
}

/** 确认收货 */
async function onReceiver() {
  const res = await useConfirm('确认收货', '是否确认收货？')
  if (res.action === 'confirm') {
    await orderReceiver(props.orderInfo.id)
    emit('orderReceiver', { id: props.orderInfo.id })
  }
}

function onAppraise() {
  router.push({
    name: 'order-appraise',
    params: { id: props.orderInfo.id },
  })
}

function onLogistics() {
  router.push({
    name: 'order-logistics',
    params: { id: props.orderInfo.id },
  })
}
function closeQRCodePopup() {
  showQRCodePopup.value = false
  emit('closeQRCode', props.orderInfo.id)
}

function openQRCodePopup() {
  showQRCodePopup.value = true
}
/* ---------------------
  按钮显示状态 computed
--------------------- */
const showDelete = computed(() =>
  props.orderInfo.status === '11' && props.orderInfo.payStatus === '0',
)

const showCancel = computed(() =>
  props.orderInfo.status === '1' && props.orderInfo.payStatus === '0',
)

const showPay = computed(() =>
  props.orderInfo.status === '1' && props.orderInfo.payStatus === '0',
)

const showLogistics = computed(() =>
  ['3', '4', '7'].includes(props.orderInfo.status) && props.orderInfo.deliveryWay === '1',
)

const showReceiver = computed(() =>
  props.orderInfo.status === '3' && props.orderInfo.deliveryWay === '1',
)

const showAppraise = computed(() =>
  props.orderInfo.status === '4' && props.orderInfo.appraiseStatus === '0',
)

const showQRCodeBtn = computed(() =>
  props.orderInfo.status === '3' && props.orderInfo.deliveryWay === '2',
)
</script>

<template>
  <view class="order-operation-warp">
    <view v-if="showDelete" class="button">
      <wd-button type="error" size="small" hairline plain @click.stop="onDel">
        删除订单
      </wd-button>
    </view>

    <view v-if="showCancel" class="button">
      <wd-button type="info" size="small" hairline plain @click.stop="onCancel">
        取消订单
      </wd-button>
    </view>

    <view v-if="showPay" class="button">
      <wd-button type="primary" size="small" hairline plain @click.stop="onPay">
        立即付款
      </wd-button>
    </view>

    <view v-if="showLogistics" class="button">
      <wd-button type="warning" size="small" hairline plain @click.stop="onLogistics">
        查看物流
      </wd-button>
    </view>

    <view v-if="showReceiver" class="button">
      <wd-button type="primary" size="small" hairline plain @click.stop="onReceiver">
        确认收货
      </wd-button>
    </view>

    <view v-if="showQRCodeBtn" class="button">
      <wd-button type="primary" size="small" @click="openQRCodePopup">
        提货二维码
      </wd-button>
    </view>

    <view v-if="showAppraise" class="button">
      <wd-button type="primary" size="small" hairline plain @click.stop="onAppraise">
        评价晒单
      </wd-button>
    </view>
  </view>
  <!-- 提货二维码弹窗 -->
  <wd-popup v-model="showQRCodePopup" position="center" custom-style="border-radius:32rpx;">
    <view class="rounded-xl bg-white p-5">
      <view class="pb-20rpx text-center text-lg font-bold">
        提货二维码
      </view>

      <view class="flex justify-center p-2">
        <ikun-qrcode
          width="200"
          height="200"
          unit="px"
          :data="orderInfo.orderNo"
        />
      </view>

      <view class="pt-20rpx text-center">
        <wd-button type="primary" size="small" @click="closeQRCodePopup">
          关闭
        </wd-button>
      </view>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.order-operation-warp {
  display: flex;
  align-items: center;
  justify-content: flex-end;

  .button {
    padding-left: 20rpx;
  }
}

.groupon-item {
  background-color: #fff;
  border-radius: 10rpx;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;

  .left {
    .avatar {
      width: 35px;
      height: 35px;
      border-radius: 50%;
      background-color: #ccc; // 默认颜色
    }

    .avatar:not(:last-child) {
      margin-left: 20px;
    }
  }

  &:after {
    content: '';
    position: absolute;
    border-bottom: 1px solid #f2f2f2;
    bottom: 0;
    left: 0;
    width: 100%;
  }
}
</style>
