<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getPage } from '@/api/promotion/couponUser'

definePage({
  name: 'coupon-user',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '我的优惠券',
  },
})
const pagingRef = ref()
const state = reactive<{ list: any[], queryParams: any }>({
  list: [],
  queryParams: {
    status: '',
  },
})
const tabs = reactive({
  statusList: [
    {
      name: '全部',
    },
    {
      name: '待使用',
    },
    {
      name: '已使用',
    },
    {
      name: '已过期',
    },
    {
      name: '冻结中',
    },
  ],

  tabCurrent: 0,
})
const router = useRouter()
const globalLoading = useGlobalLoading()
function statusLabel(status: string) {
  if (status === '0')
    return '未使用'
  if (status === '1')
    return '已使用'
  if (status === '2')
    return '已过期'
  if (status === '3')
    return '冻结中'
  return '未知'
}
function changeTab({ index }: any) {
  state.queryParams.status = `${index - 1}`
  pagingRef.value?.reload()
}
function toGoodsList() {
  router.push({
    name: 'goods-list',
  })
}

onLoad(async (options) => {
  if (options?.status) {
    tabs.tabCurrent = Number(options.status)
    state.queryParams.status = options.status
  }
  nextTick(() => {
    pagingRef.value?.reload()
  })
})
async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading('加载中...')
  try {
    const response = await getPage({
      current: pageNo,
      size: pageSize,
      desc: 'create_time',
      status: state.queryParams.status === '-1' ? '' : state.queryParams.status,
    })
    pagingRef.value?.complete(response.records)
  }
  finally {
    globalLoading.close()
  }
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="state.list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar title="我的优惠券" />
      <wd-tabs v-model="tabs.tabCurrent" @change="changeTab">
        <block v-for="(item, index) in tabs.statusList" :key="index">
          <wd-tab :title="item.name" />
        </block>
      </wd-tabs>
    </template>
    <wd-notice-bar
      v-if="tabs.tabCurrent === 4" text="使用优惠券的订单未支付，优惠券会被冻结哦" :scrollable="false"
      prefix="warn-bold"
    />

    <view v-for="(item, index) in state.list" :key="index" class="coupon-card">
      <view class="coupon-left">
        <view class="coupon-amount">
          {{ item.couponInfo?.couponType === '1' ? `￥${item.couponInfo?.amount}` : `${item.couponInfo?.discount}折` }}
        </view>
        <view class="coupon-threshold">
          {{ item.couponInfo?.threshold && item.couponInfo?.threshold > 0 ? `满${item.couponInfo?.threshold}元可用` : '无门槛优惠券' }}
        </view>
        <view class="coupon-notch" />
      </view>
      <view class="coupon-right">
        <view class="coupon-title">
          {{ item.couponInfo?.couponName }}
        </view>
        <view class="coupon-desc">
          {{ item.couponInfo?.useRange === '1' ? '全部商品可用' : '部分商品可用' }}
        </view>
      </view>
      <view class="coupon-status-tag" :class="item.status === '1' ? 'status-used' : item.status === '2' ? 'status-expired' : item.status === '3' ? 'status-frozen' : ''">
        {{ statusLabel(item.status) }}
      </view>
      <view
        v-if="item.status === '0'"
        class="coupon-btn"
        @click.stop="toGoodsList"
      >
        去使用
      </view>
      <view v-if="item.status !== '0'" class="coupon-mask" />
    </view>
  </z-paging>
</template>

<style lang="scss" scoped>
.page-wrapper {
  min-height: calc(100vh - var(--window-top));
  background: #fff;
}
.coupon-card {
  position: relative;
  width: calc(100% - 32rpx);
  height: 160rpx;
  border-radius: 24rpx;
  overflow: hidden;
  display: flex;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  margin: 16rpx;
  background: linear-gradient(90deg, #ff5a4a 0%, #ff2d2d 100%);
  border: 2px solid #ff2d2d;
  box-sizing: border-box;
}
.coupon-left {
  width: 180rpx;
  background: #fff;
  border-top-left-radius: 20rpx;
  border-bottom-left-radius: 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20rpx 0;
  position: relative;
}
.coupon-amount {
  color: #ff2d2d;
  font-size: 44rpx;
  font-weight: bold;
  line-height: 1.1;
}
.coupon-threshold {
  color: #bfbfbf;
  font-size: 20rpx;
  margin-top: 4rpx;
}
.coupon-notch {
  position: absolute;
  right: -20rpx;
  top: 50%;
  transform: translateY(-50%);
  width: 40rpx;
  height: 40rpx;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
}
.coupon-right {
  flex: 1;
  padding: 18rpx 24rpx 18rpx 18rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  margin-left: 10px;
}
.coupon-title {
  color: #fff;
  font-size: 28rpx;
  font-weight: bold;
  margin-bottom: 8rpx;
}
.coupon-desc {
  color: #fff;
  font-size: 20rpx;
  margin-top: 2rpx;
}
.coupon-shop {
  display: flex;
  align-items: center;
  margin-top: 8rpx;
}
.coupon-btn {
  position: absolute;
  right: 24rpx;
  top: 50%;
  transform: translateY(-50%);
  background: #fff;
  color: #ff2d2d;
  font-size: 22rpx;
  font-weight: bold;
  border-radius: 24rpx;
  padding: 8rpx 18rpx;
  width: 100rpx;
  text-align: center;
  box-shadow: 0 2rpx 8rpx rgba(255, 107, 107, 0.08);
  border: none;
}
.coupon-status-tag {
  position: absolute;
  top: 0;
  right: 0;
  background: #ff2d2d;
  color: #fff;
  font-size: 20rpx;
  font-weight: bold;
  padding: 0 24rpx;
  height: 40rpx;
  line-height: 40rpx;
  border-top-right-radius: 20rpx;
  border-bottom-left-radius: 20rpx;
  transform: rotate(25deg) translate(18rpx, -12rpx);
  box-shadow: 0 2rpx 8rpx rgba(255, 107, 107, 0.12);
}
.coupon-mask {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(200, 200, 200, 0.2);
  border-radius: 20rpx;
}
.status-used { background: #999999; }
.status-expired { background: #666666; }
.status-frozen { background: #ff8a00; }
@media (max-width: 750rpx) {
  .coupon-left {
    width: 110rpx;
    padding: 12rpx 0;
  }
  .coupon-amount {
    font-size: 32rpx;
  }
  .coupon-title {
    font-size: 22rpx;
  }
  .coupon-btn {
    font-size: 18rpx;
    padding: 6rpx 18rpx;
  }
}
</style>
