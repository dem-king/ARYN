<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { getOrder as getOrderByNo } from '@/api/pay/index'
import { useDict } from '@/utils/dict'

definePage({
  name: 'pay-result',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '支付结果',
  },
})
const { pay_type } = useDict('pay_type')
const { confirm, alert } = useGlobalMessage()

const loading = ref(true)
const state = reactive<{ order: any, payType: string }>({
  order: {},
  payType: '',
})
const globalLoading = useGlobalLoading()
const router = useRouter()
onLoad((options) => {
  const orderNo = options?.orderNo
  getOrder(orderNo)

  // #ifdef H5
  if (state.order?.payStatus !== '0') {
    return // 已支付则不处理
  }
  confirm({
    title: '支付提醒',
    msg: '确认支付是否已完成？',
    closeOnClickModal: false,
    confirmButtonText: '已完成支付',
    cancelButtonText: '遇到问题重新支付',
    success: (res) => {
      if (res.action === 'confirm') {
        // 显示加载
        globalLoading.loading('查询支付结果中...')
        // 轮询逻辑
        pollGetOrder(orderNo).catch(() => {
          // 轮询失败，弹出提示
          alert({
            msg: '系统暂未确认到支付成功，请稍后到订单列表查看或联系客服。',
            title: '温馨提示',
          })
        })
        // 支付成功，页面数据会自动更新
      }
      else {
        router.replace({
          name: 'order-pay',
          params: {
            orderNo,
          },
        })
      }
    },
    fail: () => {
      router.replace({
        name: 'order-pay',
        params: {
          orderNo,
        },
      })
    },
  })

  // #endif
})

async function getOrder(orderNo: string) {
  await getOrderByNo(orderNo).then((response) => {
    loading.value = false
    state.order = response
    if (state.order) {
      const extra = JSON.parse(state.order?.extra)
      state.payType = extra?.payType
    }
  })
}

// 添加轮询查询方法
function pollGetOrder(orderNo: string, maxTry = 3, interval = 2000) {
  let tryCount = 0

  return new Promise<void>((resolve, reject) => {
    const check = async () => {
      tryCount++
      await getOrder(orderNo)
      if (state.order?.payStatus === '1') {
        resolve()
      }
      else if (tryCount < maxTry) {
        setTimeout(check, interval)
      }
      else {
        reject(new Error('查询支付结果超时'))
      }
    }
    check()
  })
}
// 返回首页
function goHome() {
  router.replaceAll({ name: 'home' })
}
// 跳转订单
function goOrder() {
  router.push({ name: 'order-list' })
}
</script>

<template>
  <hr-navbar title="支付结果" />
  <view v-if="!loading" class="pay-result-container">
    <view class="pay-result-bg-elements">
      <view class="bg-circle bg-circle-1" />
      <view class="bg-circle bg-circle-2" />
      <view class="bg-circle bg-circle-3" />
    </view>
    <view class="pay-result-header animated bounceInDown flex flex-col items-center justify-center p-30rpx">
      <wd-icon color="#19be6b" name="check-circle" size="100rpx" class="success-icon animated pulse infinite" />
      <text class="animated fadeInUp py-1 text-xl font-bold">
        支付成功
      </text>
    </view>
    <view class="pay-result-content animated fadeInUp m-50rpx mx-80rpx delay-03s">
      <view class="item flex items-center justify-between pb-10rpx">
        <text class="text-xs">
          支付编号
        </text>
        <wd-text size="26rpx" color="inherit" :text="state.order?.outTradeNo" />
      </view>
      <view class="item flex items-center justify-between pb-10rpx">
        <text class="text-xs">
          支付方式
        </text>
        <dict-tag :options="pay_type" :value="state.payType" />
      </view>
      <view class="item flex items-center justify-between pb-10rpx">
        <text class="text-xs">
          支付金额
        </text>
        <wd-text size="16px" color="red" :text="state.order?.amount" mode="price" prefix="￥" />
      </view>
    </view>
    <view class="pay-result-foot animated fadeInUp mt-40rpx flex items-center justify-center delay-05s">
      <view class="p-2">
        <wd-button plain custom-class="action-btn hover-scale" @click="goHome">
          返回首页
        </wd-button>
      </view>
      <view class="p-2">
        <wd-button type="primary" custom-class="action-btn hx-ml10 hover-scale" @click="goOrder">
          查看订单
        </wd-button>
      </view>
    </view>
    <WaterfallGoods />
  </view>
</template>

<style lang="scss" scoped>
.pay-result-container {
  position: relative;
  min-height: calc(100vh - var(--window-top));
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf9 100%);
  overflow: hidden;
}

.pay-result-bg-elements {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--theme-color-primary, #4D7FFF) 0%, var(--theme-color-secondary, #7BA7FF) 100%);
  opacity: 0.1;
  filter: blur(20rpx);
}

.bg-circle-1 {
  top: -50rpx;
  right: -50rpx;
  width: 300rpx;
  height: 300rpx;
  animation: float 6s ease-in-out infinite;
}

.bg-circle-2 {
  top: 300rpx;
  left: -80rpx;
  width: 200rpx;
  height: 200rpx;
  animation: float 8s ease-in-out infinite 1s;
}

.bg-circle-3 {
  bottom: 100rpx;
  right: 50rpx;
  width: 150rpx;
  height: 150rpx;
  animation: float 7s ease-in-out infinite 0.5s;
}

.success-icon {
  animation-duration: 2s;
}

.action-btn {
  transition: all 0.3s ease;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.1);

  &:active {
    transform: scale(0.95);
  }
}

.hover-scale {
  transition: transform 0.3s ease;

  &:hover {
    transform: scale(1.05);
  }

  &:active {
    transform: scale(0.95);
  }
}

/* 自定义动画类 */
.animated {
  animation-duration: 1s;
  animation-fill-mode: both;
}

.bounceInDown {
  animation-name: bounceInDown;
}

.fadeInUp {
  animation-name: fadeInUp;
}

.pulse {
  animation-name: pulse;
  animation-timing-function: ease-in-out;
}

.infinite {
  animation-iteration-count: infinite;
}

.delay-03s {
  animation-delay: 0.3s;
}

.delay-05s {
  animation-delay: 0.5s;
}

@keyframes bounceInDown {
  from,
  60%,
  75%,
  90%,
  to {
    animation-timing-function: cubic-bezier(0.215, 0.61, 0.355, 1);
  }

  0% {
    opacity: 0;
    transform: translate3d(0, -3000rpx, 0);
  }

  60% {
    opacity: 1;
    transform: translate3d(0, 25rpx, 0);
  }

  75% {
    transform: translate3d(0, -10rpx, 0);
  }

  90% {
    transform: translate3d(0, 5rpx, 0);
  }

  to {
    transform: translate3d(0, 0, 0);
  }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translate3d(0, 100rpx, 0);
  }

  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@keyframes pulse {
  from {
    transform: scale3d(1, 1, 1);
  }

  50% {
    transform: scale3d(1.05, 1.05, 1.05);
  }

  to {
    transform: scale3d(1, 1, 1);
  }
}

@keyframes float {
  0% {
    transform: translate(0, 0);
  }
  50% {
    transform: translate(20rpx, -20rpx);
  }
  100% {
    transform: translate(0, 0);
  }
}
</style>
