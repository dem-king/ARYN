<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { delShoppingCart, editShoppingCart, getPage } from '@/api/order/shoppingCart'
import { saveBatch } from '@/api/product/collect'
import { getById } from '@/api/product/spu'
import { getDefault } from '@/api/user/address'
// 引入组件
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
  <hr-navbar title="购物车" :left-arrow="false" />
  <view class="cart-header bg-white">
    <!-- 收货地址 -->
    <view @click="toJumpUrl('/sub-pages/user/address/index')">
      <wd-icon name="location" size="26rpx" />
      <wd-text
        :text="state.address?.detailAddress || '请添加收货地址'"
        size="26rpx" color="inherit"
      />
    </view>
    <!-- 编辑按钮 -->
    <view>
      <wd-button size="small" type="primary" @click="handleEdit">
        {{ state.isEdit ? '完成' : '编辑'
        }}
      </wd-button>
    </view>
  </view>
  <wd-gap bg-color="#FFFFFF" height="80rpx" />
  <view class="cart-container bg-white">
    <view v-if="state.cartList && state.cartList.length > 0" class="cart-item">
      <view
        v-for="(goods, goodsIndex) in state.cartList" :key="goodsIndex" class="cart-content"
        @click="toGoodsDetail(goods.spuId)"
      >
        <!-- checked -->
        <view v-if="state.isEdit">
          <wd-checkbox v-model="goods.checked" @change="cartCheck($event, goodsIndex)" @tap.stop="" />
        </view>
        <view v-else>
          <view v-if="goods.goodsSku">
            <wd-checkbox
              v-if="goods.goodsSku && goods.goodsSku.stock > 0" v-model="goods.checked"
              @change="cartCheck($event, goodsIndex)" @tap.stop=""
            />
            <view v-else class="no-stock-warp">
              无货
            </view>
          </view>
          <view v-else class="no-stock-warp">
            下架
          </view>
        </view>
        <!-- left -->
        <image :src="goods.picUrl" />
        <!-- right -->
        <view class="cart-right">
          <view>
            <wd-text
              :lines="2" :text="goods.spuName" size="26rpx"
              :color="goods.goodsSku && goods.goodsSku.stock > 0 ? '#606266' : '#c0c4cc'"
            />
          </view>
          <view v-if="goods.specsInfo" class="select">
            <view class="select-text" @tap.stop="openSkuPopup(goods)">
              <wd-text
                custom-style="padding-right:5rpx" :lines="2"
                :text="goods.goodsSku ? goods.specsInfo : '规格不存在请重新选择'" size="21rpx"
              />
              <wd-icon name="arrow-down" size="20rpx" />
            </view>
          </view>
          <view
            v-if="goods.goodsSku && goods.goodsSku.stock > 0 && goods.goodsSku.stock <= 10"
            class="hx-pl10 hx-pt10"
          >
            <wd-text :text="`仅剩${goods.goodsSku.stock}件`" size="24rpx" />
          </view>
          <view v-if="goods.goodsSku" class="price">
            <wd-text :text="goods.goodsSku.salesPrice" size="28rpx" color="red" mode="price" prefix="￥" />
            <wd-input-number
              v-if="goods.goodsSku.stock > 0" v-model="goods.quantity" :min="1" :index="goods.id"
              step-strictly :max="goods.goodsSku.stock" :step="1" @change="quantityChange($event, goods.id)"
              @tap.stop=""
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
  <view class="cart-footer bg-white dark:bg-[var(--wot-dark-background)]">
    <view class="cart-footer-warp">
      <view class="cart-footer-left">
        <wd-checkbox v-model="state.shopCheckedAll" @change="checkedAll">
          全选
        </wd-checkbox>
      </view>
      <view v-if="!state.isEdit" class="cart-footer-right">
        <view class="price">
          <wd-text size="24rpx" text="合计：" color="inherit" />
          <wd-text size="28rpx" mode="price" :text="state.totalAmount" prefix="￥" color="red" />
        </view>
        <wd-button
          size="small" type="primary" custom-style="margin:14rpx;" :disabled="state.checkedList.length <= 0"
          @click="toSettlement"
        >
          去结算({{ state.checkedList.length
          }})
        </wd-button>
      </view>
      <view v-if="state.isEdit" class="cart-footer-right">
        <wd-button
          size="small" type="primary" plain custom-style="margin:14rpx;"
          :disabled="state.checkedList.length <= 0" @click="toCollect"
        >
          移入收藏夹
        </wd-button>

        <wd-button size="small" custom-class="bg-secondary! border-secondary!" :disabled="state.checkedList.length <= 0" @click="delCart">
          删除({{ state.checkedList.length }})
        </wd-button>
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
  <vk-data-goods-sku-popup
    ref="skuPopup" v-model="skuKey" border-radius="20" :localdata="state.goodsSpu"
    :default-select="selectedSku" sku-arr-name="specsArr" sku-list-name="goodsSkus"
    spec-list-name="specList" :mode="skuMode" @open="onOpenSkuPopup" @close="onCloseSkuPopup"
    @add-cart="editCart"
  />
</template>

<style scoped lang="scss">
.cart-header {
  display: flex;
  align-items: center;
  padding: 15rpx;
  justify-content: space-between;
  position: fixed;
  right: 0;
  left: 0;
  z-index: 99;
  box-shadow: 0 0 5px rgba(0, 0, 0, 0.2);
}

.cart-container {
  padding: 20rpx;
  .cart-item {
    border-radius: 10rpx;
    margin-bottom: 20rpx;

    .shop-info {
      display: flex;
      align-items: center;
      padding: 18rpx;
    }

    .cart-content {
      padding: 20rpx;
      display: flex;
      align-items: center;
      position: relative;

      &::after {
        content: '';
        position: absolute;
        left: 0;
        bottom: 2rpx;
        width: 100%;
        height: 1px;
      }

      image {
        width: 160rpx;
        flex: 0 0 160rpx;
        height: 160rpx;
        border-radius: 8rpx;
      }

      .cart-right {
        flex: 1 0 0%;
        height: 100%;
        margin-left: 20rpx;
        display: flex;
        flex-direction: column;
        overflow: hidden;

        .name {
          font-size: 14px;
        }

        .price {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-top: 14rpx;

          .price-text {
            color: red;
            font-size: 16px;
          }
        }

        .select {
          margin-top: 10rpx;
          display: flex;
          align-items: center;

          .select-text {
            display: flex;
            align-items: center;
            border-radius: 20rpx;
            padding: 4rpx 14rpx;
            background-color: rgb(248, 248, 248);
          }
        }
      }
    }
  }
}

.cart-footer {
  position: fixed;
  width: 100%;
  left: 0;
  z-index: 2;
  box-sizing: border-box;
  bottom: 0;
  .cart-footer-warp {
    display: flex;
    position: relative;
  }

  .cart-footer-left {
    display: flex;
    align-items: center;
    padding-left: 30rpx;
  }

  .cart-footer-right {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: flex-end;
    padding-right: 10rpx;

    .price {
      padding-right: 10rpx;
    }
  }
}

.like-title {
  display: flex;
  align-items: center;
  justify-content: center;
}

.no-stock-warp {
  padding: 5rpx;
  background-color: #909399;
  border-radius: 10rpx;
  color: #fff;
  font-size: 13px;
}
</style>
