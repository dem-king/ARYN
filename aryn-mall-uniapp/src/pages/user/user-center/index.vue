<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getCount as getOrderCount } from '@/api/order/orderInfo'
// 引入组件
import WaterfallGoods from '@/components/waterfall-goods/index.vue'
import { useMessageStore } from '@/store/messageStore'

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
  }
})
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
