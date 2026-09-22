<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'

import { useShipContextStore } from '@/store/shipContextStore'
import { delShoppingCart, editShoppingCart, getPage } from '@/api/order/shoppingCart'
import { saveBatch } from '@/api/product/collect'
import { getById } from '@/api/product/spu'
import { getDefault } from '@/api/user/address'
// 引入组件
import ShipContextPicker from '@/components/ship-context-picker/index.vue'
import WaterfallGoods from '@/components/waterfall-goods/index.vue'
import { initGoodsSpecs } from '@/utils/goods-specs'

definePage({
  name: 'shopping-cart',
  layout: 'tabbar',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '购物车',
  },
})

interface ShoppingCartState {
  customStyle: {
    margin: string
    padding: string
  }
  checkedList: any[] // 更具体化类型
  cartList: any[] // 更具体化类型
  shopCheckedAll: boolean
  totalAmount: string
  isEdit: boolean
  address: any
  goodsSpu: any
  selectArr: any[]
}

// 定义变量
const loading = ref(false)
/** 船舶与靠港选择器：购物车按靠港分组，跨靠港的行需要用户切换船舶才能一起结算 */
const shipPickerVisible = ref(false)
const message = useMessage()
const skuPopup = ref()
const skuKey = ref<boolean>(false)
const skuMode = ref<number>(1)
const selectedSku = ref()
const selectedCartId = ref()
const router = useRouter()
const authStore = useAuthStore()
const goodsStore = useGoodsStore()
const shoppingCartStore = useShoppingCartStore()
// 防串船：按加购靠港分组，当前船舶组排前
const shipContextStore = useShipContextStore()
const cartGroups = computed(() => {
  const groups: Record<string, any[]> = {}
  for (const row of state.cartList) {
    const key = row.vesselCallId || ''
    if (!groups[key]) groups[key] = []
    groups[key].push(row)
  }
  const currentKey = shipContextStore.vesselCallId || ''
  return Object.keys(groups)
    .sort((a, b) => (a === currentKey ? -1 : b === currentKey ? 1 : 0))
    .map((key) => ({
      key,
      label:
        key === currentKey
          ? `当前船舶：${shipContextStore.vesselName || '未命名'}`
          : key
            ? `其他靠港计划（${key.slice(-6)}）`
            : '未指定配送计划',
      isCurrent: key === currentKey && !!key,
      rows: groups[key],
    }))
})
const globalLoading = useGlobalLoading()
const { success: showSuccess } = useGlobalToast()
const { confirm } = useGlobalMessage()
const state = reactive<ShoppingCartState>({
  customStyle: {
    margin: '10rpx',
    padding: '20rpx',
  }, // 去结算按钮样式
  checkedList: [], // 已勾选数组
  cartList: [], // 购物车数据
  shopCheckedAll: false, // 全选
  totalAmount: '0', // 合计金额
  isEdit: false, // 编辑按钮
  address: {}, // 默认收货地址
  goodsSpu: {},
  selectArr: [],
})

// 编辑按钮
function handleEdit() {
  state.isEdit = !state.isEdit
  state.checkedList.forEach((val: any, index: number) => {
    if (!state.isEdit && val.goodsSku.stock <= 0) {
      state.checkedList.splice(index, 1)
    }
  })
  if (state.isEdit && state.shopCheckedAll) {
    // 如果全选了，检查是否存在无货商品
    state.cartList.forEach((val: any) => {
      if (val.goodsSku.stock <= 0) {
        // 存在无货商品，取消全选
        state.shopCheckedAll = false
        // 取消单选全选
        val.checked = false
      }
    })
  }
  if (!state.isEdit && !state.shopCheckedAll) {
    let allChecked = true
    state.cartList.forEach((val: any) => {
      if (!val.checked && val.goodsSku.stock > 0) {
        allChecked = false
      }
    })
    if (allChecked) {
      state.shopCheckedAll = true
    }
  }
  computePrice()
}
// 全选
function checkedAll(e: any) {
  if (e.value) {
    state.cartList.forEach((val: any) => {
      const exists = state.checkedList.some(obj => obj.id === val.id)
      if (state.isEdit || val.goodsSku.stock > 0) {
        if (!exists) {
          val.checked = e.value
          state.checkedList.push(val)
        }
      }
    })
  }
  else {
    state.checkedList = []
    state.cartList.forEach((val) => {
      val.checked = e.value
    })
  }

  computePrice()
}
// 单选
function cartCheck(e: any, index: number) {
  const shop = state.cartList[index]
  shop.checked = e.value
  if (e.value) {
    state.checkedList.push(shop)
  }
  else {
    state.checkedList.forEach((val, index) => {
      if (shop.id === val.id) {
        state.checkedList.splice(index, 1)
      }
    })
  }
  computePrice()
}
function selectedAll() {
  if (!state.cartList || state.cartList.length <= 0) {
    state.shopCheckedAll = false
    return
  }
  // 全选按钮是否选中
  state.shopCheckedAll = state.cartList.every((f: any) => f.checked)
}
// 数量改变
function quantityChange(e: any, id: string) {
  const params = { id, quantity: e.value }
  editCart(params)
  computePrice()
}
// 圆形勾选：单选切换（视觉重构后替代 wd-checkbox 的 change 事件）
function toggleGoodsCheck(row: any, _index: number) {
  row.checked = !row.checked
  if (row.checked) {
    if (!state.checkedList.some(v => v.id === row.id)) {
      state.checkedList.push(row)
    }
  }
  else {
    const i = state.checkedList.findIndex(v => v.id === row.id)
    if (i > -1) state.checkedList.splice(i, 1)
  }
  computePrice()
}
// 圆形勾选：全选切换
function toggleAllCheck() {
  state.shopCheckedAll = !state.shopCheckedAll
  if (state.shopCheckedAll) {
    state.cartList.forEach((val: any) => {
      const exists = state.checkedList.some(obj => obj.id === val.id)
      if ((state.isEdit || (val.goodsSku && val.goodsSku.stock > 0)) && !exists) {
        val.checked = true
        state.checkedList.push(val)
      }
    })
  }
  else {
    state.checkedList = []
    state.cartList.forEach((val: any) => {
      val.checked = false
    })
  }
  computePrice()
}
// 计算合计金额
function computePrice() {
  selectedAll()
  let amount = 0
  state.checkedList.forEach((val: any) => {
    if (val.checked && val.goodsSku) {
      amount += val.goodsSku.salesPrice * val.quantity
    }
  })
  state.totalAmount = amount.toFixed(2)
}

async function getCartPage() {
  globalLoading.loading('请稍候...')
  try {
    const response = await getPage({
      current: 1,
      size: 100,
    })
    state.cartList = response
    loading.value = false
    state.checkedList = []
    computePrice()
    shoppingCartStore.fetchCartCount()
  }
  finally {
    loading.value = false
    globalLoading.close()
  }
}
// 查询用户默认收货地址
async function getDefaultAddress() {
  state.address = await getDefault()
}
// 删除选中的商品
function delCart() {
  if (state.checkedList.length > 0) {
    confirm({
      title: '删除',
      msg: '是否删除选中商品？',
      closeOnClickModal: false,
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      success: (res) => {
        if (res.action === 'confirm') {
          const ids = state.checkedList.map(v => v.id)
          delShoppingCart(ids).then(() => {
            getCartPage()
          })
        }
      },
    })
  }
}
// 删除单个商品（管理模式下点垃圾桶图标）
function delOne(row: any) {
  confirm({
    title: '删除',
    msg: `确定删除「${row.spuName}」吗？`,
    closeOnClickModal: false,
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    success: (res) => {
      if (res.action === 'confirm') {
        delShoppingCart([row.id]).then(() => {
          const i = state.checkedList.findIndex(v => v.id === row.id)
          if (i > -1) state.checkedList.splice(i, 1)
          getCartPage()
        })
      }
    },
  })
}
// 清空购物车
function clearCart() {
  if (!state.cartList.length)
    return
  confirm({
    title: '清空购物车',
    msg: `确定清空购物车中的 ${state.cartList.length} 件商品吗？`,
    closeOnClickModal: false,
    confirmButtonText: '清空',
    cancelButtonText: '取消',
    success: (res) => {
      if (res.action === 'confirm') {
        const ids = state.cartList.map((v: any) => v.id)
        delShoppingCart(ids).then(() => {
          state.checkedList = []
          state.shopCheckedAll = false
          getCartPage()
        })
      }
    },
  })
}
async function openSkuPopup(row: any) {
  globalLoading.loading('请稍候...')
  selectedCartId.value = row.id
  try {
    state.goodsSpu = await getById(row.spuId)
    skuMode.value = 2
    skuKey.value = true
    initGoodsSpecs(state.goodsSpu)
    if (row.goodsSku) {
      selectedSku.value = row.goodsSku
      selectedSku.value.quantity = row.quantity
    }
  }
  finally {
    globalLoading.close()
  }
}
// sku组件 开始-----------------------------------------------------------
function onOpenSkuPopup() {
  console.log('监听 - 打开sku组件')
}
function onCloseSkuPopup(data: any) {
  state.selectArr = data
}
// 添加购物车
function editCart(row: any) {
  if (!row.id) {
    row.id = selectedCartId.value
  }
  editShoppingCart(row).then(() => {
    getCartPage()
    skuKey.value = false
  })
}
function toPageUrl() {
  router.push({
    name: 'goods-list',
  })
}

// 跳转商品详情
function toGoodsDetail(id: string) {
  router.push({
    name: 'goods-detail',
    params: {
      id,
    },
  })
}
/** 进入共享购物车列表（同船多成员合并采购） */
function toSharedCart() {
  uni.navigateTo({ url: '/sub-pages/order/shared-cart/list' })
}
/** 打开船舶与靠港选择器；切换后分组随 shipContextStore 变化自动重算 */
function openShipPicker() {
  shipPickerVisible.value = true
}
// 去结算 跳转结算页
function toSettlement() {
  goodsStore.setGoodsList(state.checkedList)
  router.push({
    name: 'order-confirm',
    params: {
      createWay: '1',
    },
  })
}
// 收藏
async function toCollect() {
  message
    .confirm({
      msg: '是否移入收藏夹吗？',
      title: '移入收藏夹',
    })
    .then(() => {
      saveBatch(state.checkedList).then(() => {
        getCartPage()
        showSuccess({ msg: '移入成功' })
      })
    })
    .catch(() => { })
}

onShow(async () => {
  if (authStore.isLoggedIn) {
    getCartPage()
    getDefaultAddress()
  }
})
</script>

<template>
  <hr-navbar title="购物车" :left-arrow="false">
    <template #right>
      <view class="manage-entry" @click="handleEdit">
        {{ state.isEdit ? '完成' : '管理' }}
      </view>
    </template>
  </hr-navbar>
  <view class="cart-header bg-white">
    <!-- 收货地址 -->
    <view @click="toJumpUrl('/sub-pages/user/address/index')">
      <wd-icon name="location" size="26rpx" />
      <wd-text
        :text="state.address?.detailAddress || '请添加收货地址'"
        size="26rpx" color="inherit"
      />
    </view>
  </view>
  <!-- 共享购物车入口：同船多成员分别加购，采购确认人统一提交 -->
  <view
    class="mx-20rpx mt-20rpx flex items-center justify-between rounded-20rpx bg-white p-24rpx"
    @click="toSharedCart"
  >
    <view>
      <view class="text-28rpx font-bold">
        共享购物车
      </view>
      <view class="mt-6rpx text-24rpx text-gray-500">
        与同船成员分别加购，由采购确认人统一提交整船订单
      </view>
    </view>
    <text class="text-26rpx text-gray-400">
      查看 &gt;
    </text>
  </view>
  <view class="cart-container">
    <view v-if="state.cartList && state.cartList.length > 0" class="cart-item">
      <!-- 防串船分组头：当前船舶组排前，其他靠港行结算时将被拦截 -->
      <view
        v-for="group in cartGroups"
        :key="group.key || 'none'"
        class="group-label"
      >
        {{ group.label }}
        <text
          v-if="!group.isCurrent && group.key"
          class="group-switch"
          @click.stop="openShipPicker"
        >
          （结算前请点此切换船舶）
        </text>
      </view>
      <view
        v-for="(goods, goodsIndex) in state.cartList" :key="goodsIndex" class="goods-card"
        @click="toGoodsDetail(goods.spuId)"
      >
        <!-- 管理模式下右上角删除图标 -->
        <view v-if="state.isEdit" class="card-del" @tap.stop="delOne(goods)">
          <wd-icon name="delete" size="32rpx" color="#969799" />
        </view>
        <!-- 圆形勾选：编辑模式全可选；非编辑模式仅在售商品可勾 -->
        <view
          v-if="state.isEdit || (goods.goodsSku && goods.goodsSku.stock > 0)"
          class="check-circle" :class="{ 'is-checked': goods.checked }"
          @tap.stop="toggleGoodsCheck(goods, goodsIndex)"
        >
          <wd-icon v-if="goods.checked" name="check" size="22rpx" color="#fff" />
        </view>
        <view v-else class="check-circle is-disabled">
          <text class="no-stock-text">{{ goods.goodsSku ? '无货' : '下架' }}</text>
        </view>
        <!-- left -->
        <image class="goods-img" :src="goods.picUrl" mode="aspectFill" />
        <!-- right -->
        <view class="goods-info">
          <wd-text
            :lines="2" :text="goods.spuName" size="26rpx"
            :color="goods.goodsSku && goods.goodsSku.stock > 0 ? '#303133' : '#c0c4cc'"
          />
          <view v-if="goods.specsInfo" class="spec-tag" @tap.stop="openSkuPopup(goods)">
            <wd-text
              :text="goods.goodsSku ? goods.specsInfo : '规格不存在请重新选择'" size="21rpx"
              color="#909399"
            />
            <wd-icon name="arrow-down" size="20rpx" color="#909399" />
          </view>
          <view
            v-if="goods.goodsSku && goods.goodsSku.stock > 0 && goods.goodsSku.stock <= 10"
            class="stock-tip"
          >
            仅剩{{ goods.goodsSku.stock }}件
          </view>
          <view v-if="goods.goodsSku" class="goods-bottom">
            <wd-text
              :text="goods.goodsSku.salesPrice" size="30rpx" color="#ff2e4d" mode="price"
              prefix="￥"
            />
            <wd-input-number
              v-if="goods.goodsSku.stock > 0" v-model="goods.quantity" :min="1" :index="goods.id"
              step-strictly :max="goods.goodsSku.stock" :step="1"
              @change="quantityChange($event, goods.id)" @tap.stop=""
            />
          </view>
        </view>
      </view>
    </view>
    <view v-else>
      <wd-status-tip image="content" tip="购物车为空" />
      <view style="text-align: center; margin-top: 20rpx">
        <wd-button type="primary" size="small" @tap.stop="toPageUrl">
          去逛逛
        </wd-button>
      </view>
    </view>
  </view>
  <!-- 底部全选结算 -->
  <view class="cart-footer">
    <view class="cart-footer-warp">
      <view class="footer-check" @click="toggleAllCheck">
        <view class="check-circle check-circle-lg" :class="{ 'is-checked': state.shopCheckedAll }">
          <wd-icon v-if="state.shopCheckedAll" name="check" size="24rpx" color="#fff" />
        </view>
        <text class="footer-all-text">全选</text>
      </view>
      <view v-if="!state.isEdit" class="footer-right">
        <view class="footer-price">
          <text class="footer-price-label">合计：</text>
          <wd-text size="32rpx" mode="price" :text="state.totalAmount" prefix="￥" color="#ff2e4d" />
        </view>
        <view
          class="settle-btn" :class="{ 'is-disabled': state.checkedList.length <= 0 }"
          @click="state.checkedList.length > 0 && toSettlement()"
        >
          去结算({{ state.checkedList.length }})
        </view>
      </view>
      <view v-else class="footer-right">
        <view class="clear-entry" @click="clearCart">
          清空
        </view>
        <view
          class="action-btn action-btn-plain" :class="{ 'is-disabled': state.checkedList.length <= 0 }"
          @click="state.checkedList.length > 0 && toCollect()"
        >
          移入收藏夹
        </view>
        <view
          class="action-btn action-btn-danger" :class="{ 'is-disabled': state.checkedList.length <= 0 }"
          @click="state.checkedList.length > 0 && delCart()"
        >
          删除({{ state.checkedList.length }})
        </view>
      </view>
    </view>
  </view>
  <!-- 为你推荐 -->
  <view class="like-title py-2">
    <wd-icon name="heart" color="red" size="18px" />
    <text class="px-1 text-xs">
      为你推荐
    </text>
    <wd-icon name="heart" color="red" size="18px" />
  </view>
  <WaterfallGoods />
  <!-- 底部结算条占位：结算条固定悬浮，避免遮挡推荐商品最后一行 -->
  <view class="cart-footer-holder" />
  <vk-data-goods-sku-popup
    ref="skuPopup" v-model="skuKey" border-radius="20" :localdata="state.goodsSpu"
    :default-select="selectedSku" sku-arr-name="specsArr" sku-list-name="goodsSkus"
    spec-list-name="specList" :mode="skuMode" @open="onOpenSkuPopup" @close="onCloseSkuPopup"
    @add-cart="editCart"
  />
  <!-- 船舶与靠港选择器：跨靠港分组需要切换船舶才能合并结算 -->
  <ShipContextPicker v-model="shipPickerVisible" />
</template>

<style scoped lang="scss">
.cart-header {
  display: flex;
  align-items: center;
  padding: 15rpx 24rpx;
  background: #fff;
  border-bottom: 1rpx solid #f2f3f5;
}

.manage-entry {
  font-size: 28rpx;
  color: #303133;
  padding: 8rpx 12rpx;
}

.cart-container {
  background: #f5f6f8;
  min-height: 100vh;
  padding-bottom: 20rpx;
}

.group-label {
  background: #eef0f3;
  border-radius: 12rpx;
  color: #646566;
  font-size: 24rpx;
  margin: 20rpx 20rpx 0;
  padding: 12rpx 20rpx;

  .group-switch {
    color: #ff2e4d;
    text-decoration: underline;
  }
}

/* 商品卡片 */
.goods-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 24rpx;
  margin: 20rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
  position: relative;

  .card-del {
    position: absolute;
    top: 16rpx;
    right: 16rpx;
    padding: 8rpx;
  }

  .goods-img {
    width: 168rpx;
    height: 168rpx;
    border-radius: 16rpx;
    flex-shrink: 0;
    background: #f7f8fa;
  }

  .goods-info {
    flex: 1;
    min-width: 0;
    margin-left: 20rpx;
    display: flex;
    flex-direction: column;

    .spec-tag {
      display: inline-flex;
      align-items: center;
      align-self: flex-start;
      background: #f4f5f7;
      border-radius: 8rpx;
      padding: 4rpx 12rpx;
      margin-top: 10rpx;
    }

    .stock-tip {
      margin-top: 8rpx;
      font-size: 22rpx;
      color: #ff976a;
    }

    .goods-bottom {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-top: 16rpx;
    }
  }
}

/* 圆形勾选 */
.check-circle {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid #dcdee0;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: 16rpx;
  transition: all 0.15s;

  &.is-checked {
    background: #07c160;
    border-color: #07c160;
  }

  &.is-disabled {
    background: #f2f3f5;
    border-color: #ebedf0;

    .no-stock-text {
      font-size: 18rpx;
      color: #969799;
    }
  }
}

.check-circle-lg {
  width: 40rpx;
  height: 40rpx;
  margin-right: 12rpx;
}

/* 底部结算条 */
.cart-footer {
  position: fixed;
  width: 100%;
  left: 0;
  z-index: 10;
  box-sizing: border-box;
  background: #fff;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.04);
  // 结算条固定在 TabBar 之上：H5 的 --window-bottom 为 TabBar 高度（含安全区），
  // 小程序为原生 TabBar，该变量为 0，视口本身已排除 TabBar。
  bottom: var(--window-bottom, 0px);

  .cart-footer-warp {
    display: flex;
    align-items: center;
  }

  .footer-check {
    display: flex;
    align-items: center;
  }

  .footer-all-text {
    font-size: 28rpx;
    color: #303133;
  }

  .footer-right {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: flex-end;
  }

  .footer-price {
    display: flex;
    align-items: baseline;
    margin-right: 24rpx;

    .footer-price-label {
      font-size: 26rpx;
      color: #303133;
    }
  }

  .settle-btn {
    min-width: 240rpx;
    height: 80rpx;
    line-height: 80rpx;
    text-align: center;
    padding: 0 44rpx;
    border-radius: 40rpx;
    background: linear-gradient(135deg, #ff8a00, #ff4d2e);
    color: #fff;
    font-size: 30rpx;
    font-weight: 500;
    box-shadow: 0 6rpx 16rpx rgba(255, 77, 46, 0.3);

    &.is-disabled {
      background: #c8c9cc;
      box-shadow: none;
    }
  }

  .action-btn {
    height: 72rpx;
    line-height: 72rpx;
    padding: 0 28rpx;
    border-radius: 36rpx;
    font-size: 26rpx;
    margin-left: 16rpx;

    &.action-btn-plain {
      border: 1rpx solid #dcdee0;
      color: #303133;
      background: #fff;
    }

    &.action-btn-danger {
      background: #fff1f0;
      color: #ff2e4d;
    }

    &.is-disabled {
      opacity: 0.5;
    }
  }

  .clear-entry {
    font-size: 26rpx;
    color: #969799;
    padding: 0 12rpx;
  }
}

.like-title {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24rpx 0 8rpx;
}

// 与固定结算条等高的滚动占位，保证最后一行推荐商品可完整滚入可视区
.cart-footer-holder {
  height: calc(112rpx + env(safe-area-inset-bottom));
}
</style>
