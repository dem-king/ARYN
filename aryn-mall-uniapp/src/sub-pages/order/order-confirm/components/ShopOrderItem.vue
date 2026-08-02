<script setup lang="ts">
import { computed, ref, watch } from 'vue'

interface OrderItem {
  spuId: string
  picUrl: string
  spuName: string
  buyQuantity: number
  specsInfo: string
  skuId: string
  salesPrice: number
  paymentPrice: number
  totalPrice: number
}

interface Order {
  orderItemList: OrderItem[]
  totalPrice: number
  freightPrice: number
  couponPrice: number
  paymentPrice: number
  deliveryWay: string
  couponUserId: string
  couponUserList: any[]
  remark: string
}

interface Props {
  order: Order
  deliveryWay: string
  couponUserList: any[]
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'deliveryWayChange', order: Order): void
  (e: 'remarkChange', order: Order): void
  (e: 'showCoupon', order: Order): void
}>()

const deliveryShow = ref(false)
const deliveryWay = ref(props.deliveryWay)
// 创建本地响应式变量来避免直接修改prop
const localOrder = ref<Order>({ ...props.order })

const hasSelectedCoupon = computed(() => !!localOrder.value.couponUserId)

const hasAvailableCoupons = computed(() =>
  Array.isArray(props.couponUserList)
  && props.couponUserList.length > 0
  && !localOrder.value.couponUserId,
)
// 监听prop变化以保持同步
watch(() => props.order, (newOrder) => {
  localOrder.value = { ...newOrder }
}, { deep: true })

// 将 deliveryMethod 转换为 wd-select-picker 需要的格式
// deliveryWay: 1普通快递 2上门自提 3商城配送
const deliveryMethodColumns = computed(() => {
  return [
    { value: '1', name: '普通快递' },
    { value: '2', name: '上门自提' },
    { value: '3', name: '商城配送' },
  ]
})

function handleDeliveryWayChange(item: any) {
  deliveryWay.value = item.value
}
function deliveryWayConfirm() {
  localOrder.value.deliveryWay = deliveryWay.value
  deliveryShow.value = false
  emit('deliveryWayChange', localOrder.value)
}

function showCoupon() {
  emit('showCoupon', localOrder.value)
}

// 新增：备注弹窗开关与草稿
const remarkPopupShow = ref(false)
const remarkDraft = ref('')

// 新增：打开备注弹窗并预填当前备注
function openRemarkPopup() {
  remarkDraft.value = localOrder.value.remark || ''
  remarkPopupShow.value = true
}

// 新增：保存备注到 localOrder.remark，并通知父组件
function saveRemark() {
  localOrder.value.remark = remarkDraft.value
  emit('remarkChange', localOrder.value)
  remarkPopupShow.value = false
}
</script>

<template>
  <view class="m-2 rounded-xl bg-white p-2 dark:bg-[var(--wot-dark-background2)]">
    <!-- 订单商品内容 -->
    <view>
      <view
        v-for="(goods, goodsIndex) in localOrder.orderItemList" :key="goodsIndex"
        class="flex rounded-lg bg-white px-20rpx py-20rpx"
      >
        <image :src="goods.picUrl" class="h-160rpx w-160rpx flex-none rounded-lg" />
        <view class="ml-20rpx h-full flex flex-1 flex-col overflow-hidden">
          <view class="flex justify-between">
            <view class="mr-20rpx flex-1 overflow-hidden">
              <wd-text :lines="2" size="26rpx" color="inherit" :text="goods.spuName" />
              <view v-if="goods.specsInfo" class="pt-10rpx">
                <wd-text custom-class="pt-10rpx" size="24rpx" color="#909090" :text="goods.specsInfo" />
              </view>
            </view>
            <view class="flex flex-shrink-0 flex-col items-end">
              <wd-text :text="goods.paymentPrice" size="14px" color="red" mode="price" prefix=" ￥" />
              <wd-text :text="goods.totalPrice" size="12px" mode="price" decoration="line-through" prefix=" ￥" />
              <wd-text size="13px" :text="`x${goods.buyQuantity}`" />
            </view>
          </view>
        </view>
      </view>
    </view>
    <view>
      <view class="rounded-lg bg-white p-2">
        <view class="flex items-center justify-between pb-20rpx">
          <text class="text-14px">
            商品金额
          </text>
          <wd-text size="26rpx" color="inherit" :text="localOrder.totalPrice" mode="price" prefix="￥" />
        </view>
        <view v-if="localOrder.deliveryWay === '1'" class="flex items-center justify-between pb-20rpx">
          <text class="text-14px">
            运费
          </text>
          <wd-text size="26rpx" color="inherit" :text="localOrder.freightPrice" mode="price" prefix="￥" />
        </view>
        <view class="flex items-center justify-between pb-20rpx">
          <text class="text-14px">
            优惠券
          </text>
          <view class="flex items-center">
            <!-- 已选择优惠券 -->
            <wd-text
              v-if="hasSelectedCoupon"
              size="26rpx"
              color="red"
              prefix="-￥"
              :text="localOrder.couponPrice"
              @click="showCoupon"
            />

            <!-- 有可选优惠券但未选择 -->
            <text v-else-if="hasAvailableCoupons" class="pr-4rpx text-26rpx" @click="showCoupon">
              选择优惠券
            </text>

            <!-- 无可用优惠券 -->
            <wd-text
              v-else
              size="22rpx"
              text="无可用优惠券"
            />
            <text v-if="hasSelectedCoupon || hasAvailableCoupons" class="i-carbon:chevron-right text-14px" />
          </view>
        </view>
        <view class="flex items-center justify-end pb-20rpx">
          <wd-text size="22rpx" color="#909090" :text="`共${localOrder.orderItemList.length}件`" />
          <wd-text
            custom-class="pl-10rpx" size="28rpx" :text="localOrder.paymentPrice" color="red" mode="price"
            prefix="￥"
          />
        </view>
        <view class="flex items-center justify-between pb-20rpx">
          <text class="text-14px">
            配送方式
          </text>
          <view>
            <text class="pr-4rpx text-26rpx" @click="deliveryShow = true">
              {{ localOrder.deliveryWay === '2' ? '上门自提' : localOrder.deliveryWay === '3' ? '商城配送' : '普通快递' }}
            </text>
            <text class="i-carbon:chevron-right text-14px" />
          </view>
        </view>

        <!-- 修改：备注展示为可点击行，弹出层内再输入 -->
        <view class="flex items-center justify-between pb-20rpx" @click="openRemarkPopup">
          <view class="text-14px">
            备注
          </view>
          <view class="flex items-center">
            <wd-text
              size="26rpx"
              color="inherit"
              :lines="1"
              custom-class="remark-ellipsis"
              :text="localOrder.remark && localOrder.remark.length ? localOrder.remark : '请填写备注'"
            />
            <text class="i-carbon:chevron-right text-14px" />
          </view>
        </view>
      </view>
    </view>

    <!-- 新增：备注弹窗，底部保存按钮 -->
    <wd-action-sheet v-model="remarkPopupShow" position="bottom" :safe-area-inset-bottom="true" custom-class="rounded-t-20rpx" title="订单备注" @cancel="remarkPopupShow = false">
      <view class="px-24rpx pb-2">
        <wd-textarea
          v-model="remarkDraft"
          placeholder="请填写备注"
          no-border
          :maxlength="200"
          show-word-limit
          custom-class="custom-textarea-border rounded-10rpx m-0! p-0!"
        />
      </view>
      <view class="border-1 border-gray-300 border-t-solid p-1">
        <wd-button type="primary" block @click="saveRemark">
          保存
        </wd-button>
      </view>
    </wd-action-sheet>

    <!-- 配送方式弹窗（替换原 wd-action-sheet） -->
    <wd-action-sheet v-model="deliveryShow" position="bottom" :safe-area-inset-bottom="true" custom-class="rounded-t-20rpx" title="选择配送方式" @cancel="deliveryShow = false">
      <view class="px-24rpx pb-2">
        <view
          v-for="item in deliveryMethodColumns"
          :key="item.value"
          class="flex items-center justify-between border-b border-[#f0f0f0] py-24rpx"
          @click="handleDeliveryWayChange(item)"
        >
          <wd-text size="26rpx" color="inherit" :text="item.name" />
          <wd-icon
            v-if="deliveryWay === item.value"
            name="check"
            size="26rpx"
            color="#07c160"
          />
        </view>
      </view>
      <view class="border-1 border-gray-300 border-t-solid p-1">
        <wd-button block type="primary" @click="deliveryWayConfirm">
          确认
        </wd-button>
      </view>
    </wd-action-sheet>
  </view>
</template>

<style lang="scss" scoped>
.custom-textarea-border {
  border: 1px solid #bfbfbf !important;
}
.remark-ellipsis {
  max-width: 450rpx; /* 可按需调整宽度 */
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
