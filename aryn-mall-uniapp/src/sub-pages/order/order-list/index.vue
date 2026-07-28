<script setup lang="ts">
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { nextTick, reactive, ref } from 'vue'
import { getPage } from '@/api/order/orderInfo'
import OrderOperation from '@/sub-pages/order/components/order-operation/index.vue'

definePage({
  name: 'order-list',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '我的订单',
  },
})
interface TabChangeParams {
  index: number
  name: string
}
interface State {
  queryParams: {
    name: string
    status: string
    appraiseStatus: string
    keyword: string
  }
  tabCurrent: number
  orderList: Array<any>
}
// 定义变量
const pagingRef = ref()
const router = useRouter()
const globalLoading = useGlobalLoading()
// 滑动相关变量
const touchStartX = ref(0)
const touchEndX = ref(0)
// 字典
const { order_item_status, order_status } = useDict(
  'order_item_status',
  'order_status',
)
const orderStatusList = ref([
  {
    name: '全部订单',
  },
  {
    name: '待付款',
  },
  {
    name: '待发货',
  },
  {
    name: '待收货',
  },
  {
    name: '待评价',
  },
])
const state = reactive<State>({
  queryParams: {
    name: '',
    status: '0',
    appraiseStatus: '',
    keyword: '',
  },
  tabCurrent: 0,
  orderList: [],
})
onLoad(async (options) => {
  if (options?.status) {
    state.tabCurrent = Number(options.status)
    state.queryParams.status = options.status
    if (options.status === '4') {
      state.queryParams.appraiseStatus = '0'
    }
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
      status: state.queryParams.status !== '0' ? state.queryParams.status : '',
      appraiseStatus: state.queryParams.appraiseStatus,
      desc: 'create_time',
      keyword: state.queryParams.keyword,
    })
    pagingRef.value?.complete(response.records)
  }
  finally {
    globalLoading.close()
  }
}
function toOrderDetail(orderId: string) {
  router.push({
    name: 'order-detail',
    params: { id: orderId },
  })
}

function changeTab({ index }: TabChangeParams) {
  state.queryParams.status = `${index}`
  state.queryParams.appraiseStatus = ''
  if (index === 4) {
    state.queryParams.appraiseStatus = '0'
  }
  pagingRef.value?.reload()
}

// 处理触摸开始事件
function handleTouchStart(e: any) {
  touchStartX.value = e.touches[0].clientX
}

// 处理触摸结束事件
function handleTouchEnd(e: any) {
  touchEndX.value = e.changedTouches[0].clientX
  handleSwipe()
}

// 处理滑动逻辑
function handleSwipe() {
  const deltaX = touchEndX.value - touchStartX.value
  const minSwipeDistance = 120 // 最小滑动距离

  // 向左滑动 - 切换到下一个tab
  if (deltaX < -minSwipeDistance) {
    if (state.tabCurrent < orderStatusList.value.length - 1) {
      const newIndex = state.tabCurrent + 1
      state.tabCurrent = newIndex
      changeTab({
        index: newIndex,
        name: orderStatusList.value[newIndex].name,
      })
    }
  }
  // 向右滑动 - 切换到上一个tab
  else if (deltaX > minSwipeDistance) {
    if (state.tabCurrent > 0) {
      const newIndex = state.tabCurrent - 1
      state.tabCurrent = newIndex
      changeTab({
        index: newIndex,
        name: orderStatusList.value[newIndex].name,
      })
    }
  }
}

function orderDel(index: number) {
  state.orderList.splice(index, 1)
}
function orderReceiver() {
  pagingRef.value?.reload()
}
function orderCancel() {
  pagingRef.value?.reload()
}
// 跳转到退款申请页面
function toRefunds(orderItemId: string, status: string) {
  if (status === '1' || status === '2') {
    router.push({ name: 'refunds-submit', params: { orderItemId } })
  }
}
function handleRefresh() {
  pagingRef.value?.reload()
}

uni.$on('refresh', handleRefresh)

onUnload(() => {
  uni.$off('refresh', handleRefresh)
})
</script>

<template>
  <z-paging
    ref="pagingRef"
    v-model="state.orderList"
    :auto="false"
    @query="queryList"
    @touchstart="handleTouchStart"
    @touchend="handleTouchEnd"
  >
    <template #top>
      <hr-navbar title="我的订单" />
      <wd-tabs
        v-model="state.tabCurrent"
        slidable="always"
        swipeable
        @change="changeTab"
      >
        <block v-for="(item, index) in orderStatusList" :key="index">
          <wd-tab :title="item.name" />
        </block>
      </wd-tabs>
    </template>

    <view class="px-20rpx">
      <view
        v-for="(order, index) in state.orderList"
        :key="index"
        class="my-20rpx rounded-xl bg-white p-20rpx"
      >
        <view @click.stop="toOrderDetail(order.id)">
          <view class="flex items-center justify-between">
            <view
              v-if="order.orderNo"
              class="flex items-center text-12px text-gray"
            >
              {{ order.orderNo }}
            </view>
            <view>
              <view
                v-if="order.deliveryWay === '2' && order.status === '3'"
                class="text-22rpx"
              >
                待自提
              </view>
              <view
                v-else-if="order.deliveryWay === '3' && order.status === '2'"
                class="text-theme text-22rpx"
              >
                待安排配送
              </view>
              <view
                v-else-if="order.deliveryWay === '3' && order.status === '3'"
                class="text-theme text-22rpx"
              >
                商城配送中
              </view>
              <dict-tag v-else :options="order_status" :value="order.status" />
            </view>
          </view>
          <view
            v-for="(item, itemIndex) in order.orderItemList"
            :key="itemIndex"
            class="flex pt-10rpx"
          >
            <view class="flex flex-col items-center">
              <image
                :src="item.picUrl"
                class="h-160rpx w-160rpx flex-none rounded-lg"
              />
            </view>

            <view class="ml-20rpx h-full flex flex-1 flex-col overflow-hidden">
              <!-- 商品信息和价格信息左右布局 -->
              <view class="flex justify-between">
                <!-- 左侧：商品名称和规格 -->
                <view class="mr-10rpx flex-1">
                  <view>
                    <wd-text
                      :lines="2"
                      size="14px"
                      color="inherit"
                      :text="item.spuName"
                    />
                  </view>
                  <view v-if="item.specsInfo" class="pt-5rpx">
                    <wd-text
                      size="13px"
                      color="#909090"
                      :text="item.specsInfo"
                    />
                  </view>
                </view>

                <!-- 右侧：价格和数量，居右对齐 -->
                <view class="flex flex-col items-end">
                  <wd-text
                    :text="item.salesPrice"
                    size="14px"
                    mode="price"
                    prefix="￥"
                  />
                  <wd-text
                    class="pt-10rpx"
                    size="12px"
                    :text="`x${item.buyQuantity}`"
                  />
                </view>
              </view>

              <view v-if="order.payStatus === '1'" class="pt-1">
                <view
                  v-if="item.status === '1' || item.status === '2'"
                  @click.stop="toRefunds(item.id, item.status)"
                >
                  <wd-button
                    custom-class="float-right"
                    size="small"
                    plain
                    hairline
                    hover-stop-propagation
                    type="info"
                  >
                    申请售后
                  </wd-button>
                </view>
                <wd-button
                  v-else
                  type="text"
                  custom-class="float-right"
                  size="small"
                >
                  <dict-tag :options="order_item_status" :value="item.status" />
                </wd-button>
              </view>
            </view>
          </view>
        </view>

        <view class="flex items-center justify-end py-20rpx">
          <text class="text-13px">
            共{{ order.orderItemList.length }}件商品， 合计
          </text>
          <wd-text
            custom-class="pl-10rpx"
            size="28rpx"
            :text="order.paymentPrice"
            color="red"
            mode="price"
            prefix="￥"
          />
        </view>
        <order-operation
          :order-info="order"
          @order-del="orderDel(index)"
          @order-receiver="orderReceiver"
          @order-cancel="orderCancel"
        />
      </view>
    </view>
  </z-paging>
</template>

<style lang="scss">
/* 已使用UnoCSS重构样式，移除原有样式 */
</style>
