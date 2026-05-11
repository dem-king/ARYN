<script setup lang="ts">
import { onLoad, onPageScroll, onShareAppMessage } from '@dcloudio/uni-app'
// @ts-expect-error: mp-html type declaration issue
import mpHtml from 'mp-html/dist/uni-app/components/mp-html/mp-html'
import { computed, getCurrentInstance, nextTick, onMounted, reactive, ref } from 'vue'
import { addShoppingCart } from '@/api/order/shoppingCart'
import { addObj as addCollect, deleteObj as deleteCollect } from '@/api/product/collect'
import { getById as getSpuById } from '@/api/product/spu'
import { getPage as getCouponPage } from '@/api/promotion/couponInfo'
import { getDefault } from '@/api/user/address'
import { buildDistributionSharePath, captureDistributionShareParams, flushPendingDistributionShareBinding } from '@/composables/useDistributionShare'
import GoodsComment from './components/GoodsComment.vue'
import GoodsFooter from './components/GoodsFooter.vue'
import GoodsInfo from './components/GoodsInfo.vue'
import GoodsNavbar from './components/GoodsNavbar.vue'
import MorePopup from './components/MorePopup.vue'
import SharePopup from './components/SharePopup.vue'

definePage({
  name: 'goods-detail',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '商品详情',
  },
})
interface State {
  couponState: boolean // 领券弹窗
  shareShow: boolean // 分享弹窗
  moreShow: boolean
  isCanBack: boolean
  goodsSpu: any // 商品信息
  address: any // 默认收货地址
  tabActive: number
  selectArr: string
  scrollStatus: boolean
  couponList: any[]
  rateType: string
}

// 定义变量
const skuKey = ref(false)
const skuMode = ref(1)
const loading = ref(true)
const skuPopup = ref()

// 组件ref
const goodsInfoRef = ref()
const goodsCommentRef = ref()

const tabList = ref([
  {
    name: '商品',
    top: 0,
    height: 0,
  },
  {
    name: '评价',
    top: 0,
    height: 0,
  },
  {
    name: '详情',
    top: 0,
    height: 0,
  },
  {
    name: '推荐',
    top: 0,
    height: 0,
  },
])
const state = reactive<State>({
  couponState: false, // 领券弹窗
  shareShow: false, // 分享弹窗
  moreShow: false,
  isCanBack: false,
  goodsSpu: {}, // 商品信息
  address: {}, // 默认收货地址
  tabActive: 0,
  selectArr: '',
  scrollStatus: false,
  couponList: [],
  rateType: '',
})
const router = useRouter()
const authStore = useAuthStore()
const goodsStore = useGoodsStore()
const { confirm } = useGlobalMessage()
const globalLoading = useGlobalLoading()
const userStore = useUserStore()
const shoppingCartStore = useShoppingCartStore()
const spuId = ref()
const proxy = getCurrentInstance()?.proxy
const { statusBarHeight }: any = uni.getSystemInfoSync()
// 导航背景颜色
const background = ref('rgba(255, 255, 255, 0)')
// 导航标题字体颜色
const rootOpacity = ref(0)
const rootStyle = computed(() => {
  const style = {
    'padding-top': `${statusBarHeight}px`,
  }
  return style
})

function resolveSpuIdFromOptions(options?: Record<string, any>) {
  if (!options)
    return ''
  if (options.scene) {
    const scene = decodeURIComponent(String(options.scene))
    const segments = scene.split('&')
    const firstSegment = segments[0] || ''
    if (firstSegment.includes('=')) {
      const [_, value] = firstSegment.split('=')
      return value || ''
    }
    return firstSegment
  }
  return options.id ? String(options.id) : ''
}

onLoad(async (options) => {
  captureDistributionShareParams({
    ...(options || {}),
    sourcePath: '/sub-pages/product/goods-detail/index',
  })
  await flushPendingDistributionShareBinding()
  const id = resolveSpuIdFromOptions(options as Record<string, any>)
  spuId.value = id
  if (!id)
    return
  // 商品详情
  getSpu(id)
  // 获取已登录用户默认收货地址
  getDefaultAddress()
})

onPageScroll((res) => {
  // 获取当前滚动位置
  const scrollTop = res.scrollTop
  // 定义最大滚动高度和最大透明度
  const maxScrollTop = 200 // 根据你的页面调整这个值
  const maxOpacity = 1 // 最大透明度
  state.scrollStatus = scrollTop > 100
  // 计算透明度
  let opacity = (scrollTop / maxScrollTop) * maxOpacity
  // 确保透明度在0到maxOpacity之间
  opacity = Math.min(maxOpacity, Math.max(0, opacity))
  background.value = `rgba(255, 255, 255, ${opacity})`
  rootOpacity.value = opacity
  // 处理tabs切换
  const index = tabList.value.findIndex(item => scrollTop < item.top + item.height - statusBarHeight - 60)
  state.tabActive = index >= 0 ? index : tabList.value.length - 1
})
onMounted(async () => {
  const pages = getCurrentPages()
  /**
   * 判断是否能返回
   */
  if (pages.length <= 1 || pages[pages.length - 1].route === 'pages/login/index') {
    state.isCanBack = false
  }
  else {
    state.isCanBack = true
  }
})
// 富文本渲染完成
function readyHandel() {
  getBoundingClientRect('#scroll-description')
    .then((data: any) => {
      tabList.value[2].top = data.top
      tabList.value[2].height = data.height
    })
    .catch((error: any) => {
      console.error('获取商品详情位置失败:', error)
    })
  getBoundingClientRect('#scroll-like')
    .then((data: any) => {
      tabList.value[3].top = data.top
      tabList.value[3].height = data.height
    })
    .catch((error: any) => {
      console.error('获取推荐位置失败:', error)
    })
}
function goBack() {
  if (state.isCanBack) {
    uni.navigateBack({
      delta: 1,
    })
  }
  else {
    toHome()
  }
}

function openSkuPopup(value: number) {
  skuMode.value = value
  skuKey.value = true
}
function onCurrentPage(item: any, index: number) {
  uni.pageScrollTo({
    scrollTop: item.top - statusBarHeight - 50,
  })
  state.tabActive = index
}
onShareAppMessage(() => {
  const path = buildDistributionSharePath(`/sub-pages/product/goods-detail/index?id=${state.goodsSpu.id}`)
  return {
    title: state.goodsSpu.name,
    path,
    imageUrl: state.goodsSpu?.spuUrls[0],
  }
})
// 商品分享
function goodsShare() {
  const path = buildDistributionSharePath(`/sub-pages/product/goods-detail/index?id=${state.goodsSpu.id}`)
  // #ifdef H5
  uni.setClipboardData({
    data: window.location.origin + path,
    success() { },
  })
  // #endif
}

// 处理分享按钮点击
function handleShare() {
  state.shareShow = true
}

// 处理更多按钮点击
function handleMore() {
  state.moreShow = true
}
// sku组件 开始-----------------------------------------------------------
function onOpenSkuPopup() {
  console.log('监听 - 打开sku组件')
}
function onCloseSkuPopup(data: any) {
  if (data) {
    state.selectArr = data.join(' ').trim()
  }
  console.log('监听 - 关闭sku组件')
}
// 打开优惠券弹窗
function showCoupon() {
  state.couponState = true
}
// 通过id查询商品详情
async function getSpu(id: string) {
  globalLoading.loading('加载中...')
  loading.value = true
  try {
    const response = await getSpuById(id)
    loading.value = false
    state.goodsSpu = response
    if (response) {
      queryCoupon()
      if (state.goodsSpu.enableSpecs === '0') {
        state.goodsSpu.goodsSkus[0].specsArr = [
          {
            specsValueName: '默认',
          },
        ]
        state.goodsSpu.specList = [
          {
            specsName: '默认',
            list: [
              {
                specsValueName: '默认',
              },
            ],
          },
        ]
      }
      else {
        // 获取每个goodsSku 里的 specsArr 然后组装成 goodsSpuSpecs
        const specsMap = new Map<string, Set<string>>()
        state.goodsSpu.goodsSkus.forEach((sku: any) => {
          if (sku.specsArr && Array.isArray(sku.specsArr)) {
            sku.specsArr.forEach((spec: any) => {
              const { specsName, specsValueName } = spec
              if (!specsMap.has(specsName)) {
                specsMap.set(specsName, new Set())
              }
              specsMap.get(specsName)?.add(specsValueName)
            })
          }
        })

        const goodsSpuSpecs: any[] = []
        specsMap.forEach((values, name) => {
          const list = Array.from(values).map(value => ({ specsValueName: value }))
          goodsSpuSpecs.push({
            specsName: name,
            list,
          })
        })
        state.goodsSpu.specList = goodsSpuSpecs
      }
      nextTick(() => {
        uni.setNavigationBarTitle({
          title: state.goodsSpu.name,
        })
        // 在数据加载完成且DOM更新后获取元素位置
        updateTabListPositions()
      })
    }
    else {
      confirm({
        title: '提示',
        msg: '商品已下架',
        closeOnClickModal: false,
        success: (res) => {
          if (res.action === 'confirm') {
            // 用户确认阅读并同意
            uni.navigateBack()
          }
          else {
            uni.navigateBack()
          }
        },
      })
    }
  }
  catch (error) {
    console.error('获取商品详情失败:', error)
  }
  finally {
    globalLoading.close()
  }
}
function getBoundingClientRect(id: string) {
  return new Promise((resolve, reject) => {
    const query = uni.createSelectorQuery().in(proxy)
    query
      .select(id)
      .boundingClientRect((data) => {
        if (data) {
          resolve(data)
        }
        else {
          reject(new Error('Element not found or query failed'))
        }
      })
      .exec()
  })
}

// 更新tabList位置的函数
async function updateTabListPositions() {
  // 添加延迟确保组件完全渲染
  await new Promise(resolve => setTimeout(resolve, 500))

  // 通过组件ref获取元素位置
  if (goodsInfoRef.value) {
    goodsInfoRef.value
      .getScrollPicRect()
      .then((data: any) => {
        tabList.value[0].top = data.top
        tabList.value[0].height = data.height
      })
      .catch((error: any) => {
        console.error('获取商品信息位置失败:', error)
      })
  }

  if (goodsCommentRef.value) {
    goodsCommentRef.value
      .getScrollCommentRect()
      .then((data: any) => {
        tabList.value[1].top = data.top
        tabList.value[1].height = data.height
      })
      .catch((error: any) => {
        console.error('获取评论位置失败:', error)
      })
  }
}
// 添加购物车
function addCart(data: any) {
  addShoppingCart(data).then(() => {
    uni.showToast({
      title: '添加成功',
      icon: 'none',
      duration: 3000,
    })
    skuKey.value = false
    shoppingCartStore.fetchCartCount()
  })
}
// 立即购买
function buyNow(data: any) {
  goodsStore.setGoodsList(
    [
      data,
    ],
  )
  router.push({
    name: 'order-confirm',
  })
}
// 收藏
async function handleCollect() {
  globalLoading.loading('加载中...')
  if (state.goodsSpu.collectId) {
    // 取消收藏
    try {
      await deleteCollect(state.goodsSpu.collectId)
      state.moreShow = false
      state.goodsSpu.collectId = ''
      userStore.updateUserCollectCount(userStore.getCollectCount - 1)
    }
    finally {
      globalLoading.close()
    }
  }
  else {
    try {
      const response = await addCollect({ spuId: state.goodsSpu.id, salesPrice: state.goodsSpu.salesPrice })
      state.moreShow = false
      state.goodsSpu.collectId = response.id
      userStore.updateUserCollectCount(userStore.getCollectCount + 1)
    }
    finally {
      globalLoading.close()
    }
  }
}

// 查询用户默认收货地址
async function getDefaultAddress() {
  if (authStore.isLoggedIn) {
    state.address = await getDefault()
  }
}

// 查询优惠券
function queryCoupon() {
  getCouponPage({
    spuId: state.goodsSpu.id,
    current: 1,
    size: 50,
    desc: 'create_time',
  }).then((response) => {
    state.couponList = response.records
  })
}

// 跳转收货地址
function toAddress() {
  router.push({
    name: 'address-list',
  })
}
// 跳转首页
function toHome() {
  router.pushTab({ name: 'home' })
}
// 跳转购物车
function toCart() {
  router.pushTab({
    name: 'shopping-cart',
  })
}

function handleSwiper(obj: any) {
  uni.previewImage({
    current: obj.index,
    urls: state.goodsSpu.spuUrls,
  })
}

// 领取优惠券
async function handleReceive(coupon: any) {
  const item = state.couponList.find(item => item.id === coupon.id)
  if (item) {
    item.userReceiveCount = item.userReceiveCount + 1
  }
}
</script>

<template>
  <!-- 顶部导航组件 -->
  <GoodsNavbar
    :scroll-status="state.scrollStatus" :is-can-back="state.isCanBack" :tab-active="state.tabActive"
    :tab-list="tabList" :background="background" :root-opacity="rootOpacity" :root-style="rootStyle" @go-back="goBack"
    @current-page="onCurrentPage" @share="handleShare" @more="handleMore"
  />
  <view v-if="!loading">
    <!-- 商品信息组件 -->
    <GoodsInfo
      ref="goodsInfoRef" :goods-spu="state.goodsSpu" :coupon-list="state.couponList"
      :select-arr="state.selectArr" :address="state.address" @swiper="handleSwiper" @show-coupon="showCoupon"
      @open-sku-popup="openSkuPopup" @to-address="toAddress" @collect="handleCollect" @share="handleShare"
    />
    <!-- 评论组件 -->
    <GoodsComment ref="goodsCommentRef" :spu-id="state.goodsSpu.id" />
    <!-- 商品介绍 -->
    <view id="scroll-description" class="m-2 rounded-xl bg-white p-2 dark:bg-[var(--wot-dark-background2)]">
      <wd-divider custom-class="px-26! text-[#333333]!">
        商品详情
      </wd-divider>
      <view>
        <mp-html :content="state.goodsSpu.description" @ready="readyHandel" />
      </view>
    </view>
    <!-- 猜你喜欢 -->
    <view id="scroll-like" class="my-2 flex items-center justify-center text-sm">
      你可能还会喜欢
    </view>
    <WaterfallGoods />
    <wd-gap :height="40" />
    <GoodsFooter
      :shopping-cart-count="shoppingCartStore.getCartCount" @to-home="toHome" @to-cart="toCart"
      @open-sku-popup="openSkuPopup"
    />
  </view>

  <vk-data-goods-sku-popup
    ref="skuPopup" v-model="skuKey" border-radius="20" :localdata="state.goodsSpu"
    sku-arr-name="specsArr" sku-list-name="goodsSkus" spec-list-name="specList" :mode="skuMode"
    @open="onOpenSkuPopup" @close="onCloseSkuPopup" @add-cart="addCart" @buy-now="buyNow"
  />
  <!-- 领券弹窗 -->
  <wd-action-sheet
    v-model="state.couponState" title="优惠券" custom-class="coupon-action"
    @close="state.couponState = false"
  >
    <scroll-view scroll-y class="mb-4 h-400px">
      <view v-for="coupon in state.couponList" :key="coupon.id" class="px-4 py-1">
        <CouponCard
          :coupon="coupon" :status="coupon.userReceiveCount > 0 ? 'received' : 'available'"
          @receive="handleReceive"
        />
      </view>
    </scroll-view>
  </wd-action-sheet>

  <!-- 分享弹窗 -->
  <SharePopup v-model="state.shareShow" @share="goodsShare" />

  <!-- 更多弹窗 -->
  <MorePopup
    v-model="state.moreShow" :collect-id="state.goodsSpu?.collectId" @to-home="toHome"
    @collect="handleCollect"
  />
</template>

<style lang="scss">
.wd-popup-title {
  color: var(--wot-action-sheet-color, rgba(0, 0, 0, 0.85));
  position: relative;
  height: var(--wot-action-sheet-title-height, 40px);
  line-height: var(--wot-action-sheet-title-height, 40px);
  text-align: center;
  font-size: var(--wot-action-sheet-title-fs, var(--wot-fs-title, 16px));
  font-weight: var(--wot-action-sheet-weight, 500);
}

.coupon-action {
  max-height: 500px;
}

:deep() {
  .wd-swiper__track {
    border-radius: 0px !important;
  }
  .wd-swiper-nav--bottom {
    bottom: 30rpx !important;
  }
}
</style>
