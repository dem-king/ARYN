<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { getById } from '@/api/order/orderInfo'
import OrderOperation from '@/sub-pages/order/components/order-operation/index.vue'
import DeliveryProgress from '@/components/delivery/delivery-progress.vue'
import { useDict } from '@/utils/dict'
import { customerServiceRoute } from '@/utils/message'

definePage({
  name: 'order-detail',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '订单详情',
  },
})
const { order_item_status, pay_type } = useDict(
  'order_item_status',
  'pay_type',
)
const globalLoading = useGlobalLoading()

// 定义变量
const loading = ref(true)
const router = useRouter()
const state = reactive<{ order: any }>({
  order: {},
})
// 动态标题
const navbarTitle = computed(() => {
  const status = state.order.status
  const way = state.order.deliveryWay
  switch (status) {
    case '1':
      return '等待付款'
    case '2':
      return way === '1' ? '等待发货' : way === '3' ? '商城备货中' : '商家备货中'
    case '3':
      return way === '1' ? '等待签收' : way === '3' ? '配送中' : '等待提货'
    case '4':
      return '交易完成'
    case '5':
      return '订单已取消'
    default:
      return '订单详情'
  }
})
// 动态副标题
const navbarSubTitle = computed(() => {
  const status = state.order.status
  const way = state.order.deliveryWay
  switch (status) {
    case '1':
      return '请在30分钟内付款，超时订单自动取消'
    case '2':
      return way === '1'
        ? '订单已付款，等待商家发货'
        : way === '3'
          ? '订单已付款，商城正在备货配货'
          : '订单已付款，商家备货中'
    case '3':
      return way === '1'
        ? '商家已发货，等待签收'
        : way === '3'
          ? '商城配送员正在为您配送'
          : '商家已备货，等待提货中'
    default:
      return ''
  }
})
// 添加更多信息公开关状态
const showMoreInfo = ref(false)

onLoad((options) => {
  getOrder(options?.id)
})

// 订单查询
async function getOrder(id: string) {
  loading.value = true
  globalLoading.loading('加载中...')
  try {
    const response = await getById(id)
    state.order = response
  }
  finally {
    loading.value = false
    globalLoading.close()
  }
}
// 复制订单编号
function copyOrderNo() {
  uni.setClipboardData({
    data: state.order.orderNo,
  })
}
// 订单删除
function orderDel() {
  uni.navigateBack({
    delta: 1,
  })
}
// 订单确认收货
function orderReceiver(row: any) {
  getOrder(row.id)
}
// 订单取消
function orderCancel(row: any) {
  getOrder(row.id)
}
// 跳转到退款申请页面
function toRefunds(orderItemId: string, status: string) {
  if (status === '1' || status === '2') {
    router.push({
      name: 'refunds-submit',
      params: { orderItemId },
    })
  }
}
// 跳转到退款详情页面
function handleRefundDetail(item: any) {
  const id = item.orderRefund.id
  if (!id) {
    return
  }
  router.push({
    name: 'refunds-detail',
    params: { id },
  })
}
// 切换更多信息显示状态
function toggleMoreInfo() {
  showMoreInfo.value = !showMoreInfo.value
}
function toCustomerService() {
  const firstItem = state.order.orderItemList?.[0]
  uni.navigateTo({
    url: customerServiceRoute({
      messageType: 'ORDER_CARD',
      payload: {
        amount: state.order.paymentPrice ? `￥${state.order.paymentPrice}` : '',
        image: firstItem?.picUrl || '',
        orderId: String(state.order.id),
        statusText: navbarTitle.value,
        title: `订单 ${state.order.orderNo || state.order.id}`,
      },
    }),
  })
}
</script>

<template>
  <hr-navbar :title="navbarTitle" />
  <view v-if="!loading">
    <!-- 状态提示（可选显示在标题下方） -->
    <view
      v-if="navbarSubTitle"
      class="bg-white py-10px text-center text-24rpx text-gray-400"
    >
      {{ navbarSubTitle }}
    </view>
    <!-- 收货地址 -->
    <view
      v-if="state.order.deliveryWay === '1' || state.order.deliveryWay === '3'"
      class="m-2 rounded-xl bg-white p-2"
    >
      <view class="flex items-center">
        <view class="flex items-center">
          <wd-icon name="location" size="22px" />
        </view>
        <view class="pl-10px">
          <view>
            <text class="text-14px">
              {{ state.order.recipientName }}
            </text>
            <text class="pl-10px text-14px">
              {{ state.order.recipientPhone }}
            </text>
          </view>
          <view class="py-8px text-13px">
            {{ state.order.recipientProvince }} {{ state.order.recipientCity }}
            {{ state.order.recipientArea }}
            {{ state.order.recipientAddress }}
          </view>
        </view>
      </view>
    </view>
    <view class="m-2 rounded-xl bg-white p-2">
      <view
        v-for="(item, index) in state.order.orderItemList"
        :key="index"
        class="flex pt-20rpx"
      >
        <image
          :src="item.picUrl"
          class="h-160rpx w-160rpx flex-none rounded-lg"
        />
        <view class="ml-20rpx h-full flex flex-1 flex-col overflow-hidden">
          <view class="flex justify-between">
            <view class="mr-20rpx flex-1 overflow-hidden">
              <wd-text
                :lines="2"
                size="26rpx"
                color="inherit"
                :text="item.spuName"
              />
              <view v-if="item.specsInfo" class="pt-10rpx">
                <wd-text
                  custom-class="pt-10rpx"
                  size="24rpx"
                  color="#909090"
                  :text="item.specsInfo"
                />
              </view>
            </view>
            <view class="flex flex-shrink-0 flex-col items-end">
              <wd-text
                :text="item.paymentPrice"
                size="14px"
                color="red"
                mode="price"
                prefix=" ￥"
              />
              <wd-text size="13px" :text="`x${item.buyQuantity}`" />
            </view>
          </view>
          <view
            v-if="state.order.payStatus === '1'"
            class="flex justify-end pt-10rpx"
          >
            <wd-button
              v-if="item.status === '1' || item.status === '2'"
              size="small"
              plain
              hairline
              type="info"
              @tap.stop="toRefunds(item.id, item.status)"
            >
              申请售后
            </wd-button>
            <wd-button
              v-else
              type="info"
              size="small"
              plain
              hairline
              @click="handleRefundDetail(item)"
            >
              <dict-tag :options="order_item_status" :value="item.status" />
            </wd-button>
          </view>
        </view>
      </view>
    </view>
    <!-- 商城配送进度时间线（deliveryWay=3 且已付款后展示） -->
    <view
      v-if="state.order.deliveryWay === '3' && state.order.payStatus === '1'"
      class="m-2"
    >
      <DeliveryProgress :order-id="state.order.id" />
    </view>
    <view class="m-2 rounded-xl bg-white p-2">
      <view class="p-20rpx">
        <!-- 信息列表 -->
        <view>
          <!-- 商品金额（仅展开时显示） -->
          <view
            v-if="showMoreInfo"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              商品金额
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="state.order.totalPrice"
              mode="price"
              prefix="￥"
            />
          </view>

          <!-- 运费 -->
          <view
            v-if="showMoreInfo"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              运费
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="state.order.freightPrice"
              mode="price"
              prefix="￥"
            />
          </view>

          <!-- 优惠券 -->
          <view
            v-if="showMoreInfo"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              优惠券
            </text>
            <wd-text
              size="26rpx"
              :text="state.order.couponPrice"
              color="red"
              mode="price"
              prefix="-￥"
            />
          </view>
          <!-- 实付款 -->
          <view class="flex items-center justify-between pb-20rpx">
            <text class="text-14px">
              实付款
            </text>
            <wd-text
              custom-class="pl-10rpx"
              size="28rpx"
              :text="state.order.paymentPrice"
              color="red"
              mode="price"
              prefix="￥"
            />
          </view>
          <wd-divider v-if="showMoreInfo" color="#E8E8E8" />

          <!-- 配送方式 -->
          <view
            v-if="showMoreInfo"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              配送方式
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="
                state.order.deliveryWay === '1'
                  ? '普通快递'
                  : state.order.deliveryWay === '2'
                    ? '上门自提'
                    : state.order.deliveryWay === '3'
                      ? '商城配送'
                      : '无需配送'
              "
            />
          </view>
          <!-- 订单编号（始终显示） -->
          <view class="flex items-center justify-between pb-20rpx">
            <text class="text-14px">
              订单编号
            </text>
            <view class="flex items-center">
              <wd-text
                size="26rpx"
                color="inherit"
                :text="state.order.orderNo"
              />
              <wd-icon
                name="file-copy"
                color="var(--wot-color-theme)"
                custom-class="ml-10rpx"
                @click="copyOrderNo"
              />
            </view>
          </view>
          <!-- 支付方式 -->
          <view
            v-if="showMoreInfo && state.order.payStatus === '1'"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              支付方式
            </text>
            <dict-tag :options="pay_type" :value="state.order.paymentType" />
          </view>
          <!-- 创建时间（始终显示） -->
          <view class="flex items-center justify-between pb-20rpx">
            <text class="text-14px">
              创建时间
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="state.order.createTime"
            />
          </view>
          <!-- 付款时间 -->
          <view
            v-if="showMoreInfo && state.order.paymentTime"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              付款时间
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="state.order.paymentTime"
            />
          </view>

          <!-- 发货时间 -->
          <view
            v-if="showMoreInfo && state.order.deliverTime"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              发货时间
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="state.order.deliverTime"
            />
          </view>

          <!-- 收货时间 -->
          <view
            v-if="showMoreInfo && state.order.receiverTime"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              收货时间
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="state.order.receiverTime"
            />
          </view>

          <!-- 取消时间 -->
          <view
            v-if="showMoreInfo && state.order.cancelTime"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              取消时间
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="state.order.cancelTime"
            />
          </view>
          <!-- 备注 -->
          <view
            v-if="showMoreInfo && state.order.remark"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              备注
            </text>
            <wd-text size="26rpx" color="inherit" :text="state.order.remark" />
          </view>
        </view>

        <!-- 展开/收起按钮 -->
        <view
          class="mt-10rpx flex items-center justify-between border-t border-gray-200 dark:border-gray-700"
          @click="toggleMoreInfo"
        >
          <text class="text-14px">
            更多信息
          </text>
          <view>
            <text class="mr-10rpx text-14px">
              {{ showMoreInfo ? "收起" : "展开" }}
            </text>
            <wd-icon :name="showMoreInfo ? 'arrow-up' : 'arrow-down'" />
          </view>
        </view>
      </view>
      <wd-divider color="#E8E8E8" />
    </view>
    <WaterfallGoods />
    <wd-gap :height="80" />
    <view
      class="fixed bottom-0 left-0 right-0 flex gap-12rpx bg-white p-20rpx pb-[max(env(safe-area-inset-bottom),16rpx)]"
    >
      <wd-button plain type="info" icon="service" @click="toCustomerService">
        联系客服
      </wd-button>
      <order-operation
        :order-info="state.order"
        @order-del="orderDel"
        @order-receiver="orderReceiver($event)"
        @order-cancel="orderCancel($event)"
      />
    </view>
  </view>
</template>
