<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { getCount as getOrderCount } from '@/api/order/orderInfo'
import {
  type DeliveryEligibility,
  exchangeDeliveryIdentity,
  getDeliveryEligibility,
  getMyDeliveryStaff,
} from '@/api/delivery'
// 引入组件
import WaterfallGoods from '@/components/waterfall-goods/index.vue'
import { useMessageStore } from '@/store/messageStore'
import { Local } from '@/utils/storage'

definePage({
  name: 'user-center',
  layout: 'tabbar',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '个人中心',
  },
})
interface MyOrder {
  icon: string
  name: string
  status: string
}
interface MyService {
  icon: string
  name: string
  url: string
}

// 定义变量
const orderCountArray = ref([])
const userStore = useUserStore()
const myOrder = ref<MyOrder[]>([
  {
    icon: 'i-carbon:wallet',
    name: '待付款',
    status: '1',
  },
  {
    icon: 'i-carbon:package-node',
    name: '待发货',
    status: '2',
  },
  {
    icon: 'i-carbon:delivery-truck',
    name: '待收货',
    status: '3',
  },
  {
    icon: 'i-carbon:chat',
    name: '待评价',
    status: '4',
  },
  {
    icon: 'i-carbon:right-panel-open',
    name: '退款/售后',
    status: '5',
  },
])
const myService = ref<MyService[]>([
  {
    icon: 'i-carbon:user-profile',
    name: '会员中心',
    url: '/sub-pages/user/member/index',
  },
  {
    icon: 'i-carbon:location',
    name: '收货地址',
    url: '/sub-pages/user/address/index',
  },
  {
    icon: 'i-carbon:edit',
    name: '我的评价',
    url: '/sub-pages/user/appraise/index',
  },
  {
    icon: 'i-carbon:time',
    name: '浏览记录',
    url: '/sub-pages/user/footprint/index',
  },
  {
    icon: 'i-carbon:star',
    name: '我的收藏',
    url: '/sub-pages/user/collect/index',
  },
  {
    icon: 'i-carbon:ticket',
    name: '我的优惠券',
    url: '/sub-pages/promotion/coupon/coupon-user/index',
  },
  {
    icon: 'i-carbon:share-knowledge',
    name: '分销中心',
    url: '/sub-pages/user/distribution/index',
  },
])

const router = useRouter()
const authStore = useAuthStore()
const messageStore = useMessageStore()
const { show: showToast } = useGlobalToast()

myService.value.unshift({
  icon: 'i-carbon:notification-new',
  name: '消息中心',
  url: '/sub-pages/message/notice/index',
})

onShow(() => {
  if (authStore.isLoggedIn) {
    getUserOrderCount()
    void messageStore.refreshUnread()
    messageStore.connect()
    loadDeliveryEligibility()
  }
  else {
    deliveryEligibility.value = null
    hasDeliveryToken.value = false
  }
})

// ===================== 配送工作台入口 =====================

/** 配送资格（服务端判断，资格请求完成前不渲染入口，避免先显示后隐藏跳动） */
const deliveryEligibility = ref<DeliveryEligibility | null>(null)
/** 本地是否已有配送员 token（仅作减少换取次数的提示，不作为授权依据） */
const hasDeliveryToken = ref(false)
/** 换取/探活请求进行中，防止连续点击重复换取 */
const deliveryEntering = ref(false)

/**
 * 查询配送资格：请求失败或无资格时一律隐藏入口（token 不能作为授权依据）
 */
function loadDeliveryEligibility() {
  hasDeliveryToken.value = !!Local.get('deliveryToken')
  getDeliveryEligibility()
    .send()
    .then((eligibility) => {
      deliveryEligibility.value = eligibility ?? null
    })
    .catch(() => {
      // 资格查询失败：清理展示状态并隐藏入口，留在个人中心
      deliveryEligibility.value = null
      hasDeliveryToken.value = false
    })
}

/**
 * 入口是否可见：
 * ACTIVE 正常展示；ACCOUNT_DISABLED（员工账号停用）展示灰色提示卡；
 * UNBOUND / PERMISSION_MISSING / STAFF_INVALID 及请求失败一律不展示。
 */
const deliveryEntranceVisible = computed(() => {
  const eligibility = deliveryEligibility.value
  if (!eligibility) {
    return false
  }
  return eligibility.eligible || eligibility.status === 'ACCOUNT_DISABLED'
})

/** 入口副标题 */
const deliveryEntranceSubtitle = computed(() => {
  const eligibility = deliveryEligibility.value
  if (eligibility?.eligible) {
    return eligibility.pendingTaskCount && eligibility.pendingTaskCount > 0
      ? `${eligibility.pendingTaskCount} 项任务待处理`
      : '已为你开通配送权限'
  }
  if (eligibility?.status === 'ACCOUNT_DISABLED') {
    return '配送账号已停用'
  }
  return '进入配送工作台'
})

/** 入口角标：待处理任务数（最多显示 99+） */
const deliveryEntranceBadge = computed(() => {
  const count = deliveryEligibility.value?.pendingTaskCount ?? 0
  return count > 0 ? (count > 99 ? '99+' : String(count)) : ''
})

/**
 * 清理本地配送登录态（停用、解绑或 token 失效后调用）
 */
function clearDeliveryAuth() {
  Local.remove('deliveryToken')
  Local.remove('deliveryStaffInfo')
  hasDeliveryToken.value = false
}

/**
 * 保存配送登录态并进入工作台
 */
async function saveDeliveryAuthAndEnter(token: string) {
  Local.set('deliveryToken', token)
  try {
    const staffResponse = await getMyDeliveryStaff().send()
    Local.set('deliveryStaffInfo', staffResponse?.data || staffResponse || {})
  }
  catch {
    Local.set('deliveryStaffInfo', {})
  }
  router.push({ path: '/pages/delivery/index' })
}

/**
 * 用商城登录态换取配送员身份（供首次进入与 token 失效重试）
 */
async function exchangeAndEnter(): Promise<boolean> {
  const response = await exchangeDeliveryIdentity().send()
  const token = response?.tokenValue
  if (!token) {
    showToast('暂时无法进入配送工作台，请稍后重试')
    return false
  }
  await saveDeliveryAuthAndEnter(token)
  return true
}

/**
 * 点击入口：
 * 1. 停用状态：清理本地配送态并提示，不发起换取
 * 2. 本地有 token：先探活，有效直接进入；失效清理后重新换取一次
 * 3. 无 token：直接换取
 */
async function enterDeliveryWorkspace() {
  const eligibility = deliveryEligibility.value
  if (eligibility && !eligibility.eligible) {
    if (eligibility.status === 'ACCOUNT_DISABLED') {
      clearDeliveryAuth()
      showToast('配送账号已停用，请联系管理员')
    }
    return
  }
  if (deliveryEntering.value) {
    return
  }
  deliveryEntering.value = true
  try {
    if (hasDeliveryToken.value) {
      try {
        // 轻量探活：避免资格正常时重复换取
        await getMyDeliveryStaff().send()
        router.push({ path: '/pages/delivery/index' })
        return
      }
      catch (error: any) {
        // 探活失败（token 过期/权限回收）：清理后自动换取一次
        clearDeliveryAuth()
        const code = error?.code
        if (code !== 401 && code !== 403) {
          showToast('暂时无法进入配送工作台，请稍后重试')
          return
        }
      }
    }
    await exchangeAndEnter()
  }
  catch {
    // 401 已引导商城登录，403/业务错误已 toast，均留在个人中心
  }
  finally {
    deliveryEntering.value = false
  }
}
/**
 * 查询订单数量
 */
function getUserOrderCount() {
  getOrderCount({}).then((response) => {
    orderCountArray.value = response
  })
}
/**
 * 跳转订单页
 * @param status 订单状态
 */
function toOrder(status: string) {
  if (status === '5') {
    // 跳转退款售后页面
    router.push({
      path: '/sub-pages/order/order-refunds/refunds-list/index',
    })
  }
  else {
    router.push({
      path: `/sub-pages/order/order-list/index?status=${status}`,
    })
  }
}
/**
 *  跳转
 * @param url 页面url
 */
function toRoute(url: string) {
  router.push({
    path: url,
  })
}
/**
 * 跳转登录页
 */
function toLogin() {
  if (authStore.isLoggedIn) {
    return
  }
  router.replaceAll({
    name: 'login',
  })
}
</script>

<template>
  <hr-navbar :left-arrow="false" title="个人中心" />
  <view class="user-info">
    <wd-img
      :width="50"
      :height="50"
      round
      :src="userStore.getUserAvatar || '/static/default-avatar.png'"
      @click="toLogin"
    />
    <view style="display: flex; flex-direction: column; flex: 1">
      <view class="nick-name" @click="toLogin">
        <view>
          {{ userStore.getUserNickname || "登录/注册" }}
        </view>
        <view
          v-if="authStore.isLoggedIn && userStore.getLevelName"
          class="level-tag ml-2"
        >
          {{ userStore.getLevelName }}
        </view>
      </view>
      <view class="pl-2 pt-2 text-12px">
        <text
          class="user-tag ml-1"
          @click="toRoute('/sub-pages/promotion/coupon/coupon-user/index')"
        >
          优惠券{{ userStore.getCouponCount || 0 }}
        </text>
      </view>
    </view>
    <view class="user-setting">
      <text
        class="i-carbon:settings text-xl"
        @click="toJumpUrl('/sub-pages/user/user-setting/index')"
      />
    </view>
  </view>
  <view class="grid-container">
    <view class="warp">
      <view class="header">
        <view class="title">
          我的订单
        </view>
        <view class="right" @click="toOrder('')">
          查看全部<wd-icon name="arrow-right" size="22rpx" />
        </view>
      </view>
      <view class="grid">
        <view
          v-for="(item, index) in myOrder"
          :key="index"
          class="grid-item"
          @click="toOrder(item.status)"
        >
          <text :class="item.icon" class="text-xl" />
          <view
            v-if="
              orderCountArray
                && orderCountArray[index + 1]
                && orderCountArray[index + 1] !== 0
            "
            class="grid-dot"
          >
            <view class="grid-dot-text">
              {{ orderCountArray[index + 1] }}
            </view>
          </view>
          <view class="grid-text">
            {{ item.name }}
          </view>
        </view>
      </view>
    </view>
  </view>
  <view class="grid-container">
    <view class="warp">
      <view class="header">
        <view class="title">
          我的服务
        </view>
      </view>
      <view class="grid">
        <view
          v-for="(item, index) in myService"
          :key="index"
          class="grid-item"
          @click="toRoute(item.url)"
        >
          <text :class="item.icon" class="text-24px" />
          <view
            v-if="item.name === '消息中心' && messageStore.totalUnread"
            class="grid-dot"
          >
            <view class="grid-dot-text">
              {{
                messageStore.totalUnread > 99 ? "99+" : messageStore.totalUnread
              }}
            </view>
          </view>
          <view class="grid-text">
            {{ item.name }}
          </view>
        </view>
      </view>
    </view>
  </view>
  <!-- 配送工作台入口：仅已登录且具备配送资格的用户可见 -->
  <view v-if="deliveryEntranceVisible" class="px-20rpx pb-20rpx">
    <view
      class="flex items-center justify-between rounded-20rpx bg-white p-30rpx"
      :class="{ 'opacity-60': deliveryEligibility && !deliveryEligibility.eligible, 'opacity-50': deliveryEntering }"
      @click="enterDeliveryWorkspace"
    >
      <view class="flex items-center">
        <text class="i-carbon:delivery-truck mr-20rpx text-40rpx text-primary" />
        <view>
          <text class="text-28rpx font-bold">
            配送工作台
          </text>
          <view class="mt-4rpx text-24rpx text-gray-400">
            {{ deliveryEntranceSubtitle }}
          </view>
        </view>
      </view>
      <view class="flex items-center">
        <view
          v-if="deliveryEntranceBadge"
          class="mr-10rpx rounded-full bg-red px-12rpx py-2rpx text-20rpx text-white"
        >
          {{ deliveryEntranceBadge }}
        </view>
        <text class="i-carbon:chevron-right text-28rpx text-gray-400" />
      </view>
    </view>
  </view>
  <view class="flex items-center p-1">
    猜你喜欢
  </view>
  <WaterfallGoods />
</template>

<style lang="scss" scoped>
.user-info {
  background-color: rgb(255, 255, 255);
  display: flex;
  justify-content: space-between;
  padding: 40rpx;

  .nick-name {
    padding-left: 30rpx;
    display: flex;
    align-items: center;
  }

  .level-tag {
    border: 1px solid #667eea;
    border-radius: 50rpx;
    background: linear-gradient(135deg, #667eea, #764ba2);
    padding: 2rpx 12rpx;
    font-size: 20rpx;
    color: #fff;
  }

  .user-tag {
    border: 1px solid #f4f4f5;
    border-radius: 50rpx;
    background-color: #f4f4f5;
    padding: 4rpx 8rpx;
  }

  .user-setting {
    display: flex;
    justify-content: flex-end;
    align-items: center;
  }
}

.grid-container {
  padding: 20rpx;

  .warp {
    background-color: rgb(255, 255, 255);
    padding: 10rpx;
    border-radius: 20rpx;

    .header {
      height: 80rpx;
      display: flex;
      align-items: center;
      justify-content: space-between;
      position: relative;
      .title {
        font-size: 14px;
        color: rgb(60, 60, 60);
      }

      .right {
        font-size: 22rpx;
        color: rgb(200, 200, 200);
      }
    }
  }

  .grid {
    display: flex;
    padding: 20rpx 0;
    flex-wrap: wrap;

    .grid-item {
      padding: 20rpx 0;
      width: 20%;
      display: flex;
      align-items: center;
      flex-direction: column;
      position: relative;

      .grid-text {
        padding-top: 10rpx;
        font-size: 24rpx;
      }

      .grid-dot {
        position: absolute;
        right: 18px;
        width: 16px;
        top: 5px;

        .grid-dot-text {
          border-radius: 15px;
          padding: 1rpx;
          color: #fff;
          text-align: center;
          background-color: red;
          font-size: 18rpx;
        }
      }
    }
  }
}
</style>
