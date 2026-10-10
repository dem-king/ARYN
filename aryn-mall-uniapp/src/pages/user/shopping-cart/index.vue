<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'

import { delShoppingCart, editShoppingCart, getPage } from '@/api/order/shoppingCart'
import { saveBatch } from '@/api/product/collect'
import { getById } from '@/api/product/spu'
// 引入组件
import ShipContextPicker from '@/components/ship-context-picker/index.vue'
import WaterfallGoods from '@/components/waterfall-goods/index.vue'
import { useShipContextLoad } from '@/composables/useShipContextLoad'
import { useShipContextStore } from '@/store/shipContextStore'
import { useTenantCapabilityStore } from '@/store/tenantCapabilityStore'
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
// 船舶上下文不持久化，冷启动为空；本页自行装载，不依赖用户是否访问过首页
const { load: loadShipContext } = useShipContextLoad()
// 船舶靠港状态行：不用先加购商品也能看到配送计划状态（2026-10-04）。
// 纯零售租户没有船舶链路、未装载到船舶的用户无可展示内容，都不渲染
const capabilityStore = useTenantCapabilityStore()
const showShipStatusBar = computed(() =>
  authStore.isLoggedIn && capabilityStore.shipSupplyEnabled && !!shipContextStore.vesselId,
)
const hasActiveCall = computed(() => !!shipContextStore.vesselCallId)
const shipStatusBarText = computed(() => {
  if (!hasActiveCall.value)
    return '暂无进行中的靠港计划，商品将按常规方式配送'
  const context = shipContextStore
  // 时间窗由运营排产时补填，刚申报的靠港没有；有则展示 MM-DD HH:mm 区间
  const windowText = context.deliveryWindowStart && context.deliveryWindowEnd
    ? ` · ${context.deliveryWindowStart.slice(5, 16)}~${context.deliveryWindowEnd.slice(5, 16)}`
    : ''
  return `靠港：${context.portName || '待排产'}${context.berth ? ` ${context.berth}` : ''}${windowText}`
})
/** 船舶卡状态点：无靠港计划用橙色预警，与右侧「去申报」同色 */
const shipStatusTone = computed(() => (hasActiveCall.value ? 'is-active' : 'is-warning'))
/** 分组头与其上方船舶卡重复报同一艘船名，这里收敛成只留区分靠港的部分 */
const currentCallSuffix = computed(() =>
  shipContextStore.portName
    ? ` · ${shipContextStore.portName}${shipContextStore.berth ? ` ${shipContextStore.berth}` : ''}`
    : '',
)
const cartGroups = computed(() => {
  const groups: Record<string, any[]> = {}
  for (const row of state.cartList) {
    const key = row.vesselCallId || ''
    if (!groups[key])
      groups[key] = []
    groups[key].push(row)
  }
  const currentKey = shipContextStore.vesselCallId || ''
  return Object.keys(groups)
    .sort((a, b) => (a === currentKey && !!a ? -1 : b === currentKey && !!b ? 1 : 0))
    .map(key => ({
      key,
      // 只有「非空的当前靠港」才算当前船舶。currentKey 为空时它与无归属行
      // 都是 ''，若直接比 key === currentKey 就会把「未指定配送计划」这一组
      // 标成「当前船舶：未命名」——用户明明有船却显示未命名，正是这个误判。
      label: groupLabel(key, groups[key]),
      isCurrent: !!key && key === currentKey,
      rows: groups[key],
    }))
})

/** 无靠港组的标签要区分两种行：靠港已结束的船归属行 vs 从未归属过船舶的行 */
function groupLabel(key: string, rows: any[]) {
  if (key) {
    // 当前靠港组由上方船舶状态卡承载船名与靠港，这里只补区分别组所需的信息
    return key === (shipContextStore.vesselCallId || '')
      ? `本单商品${currentCallSuffix.value}`
      : `其他靠港计划 · ${key.slice(-6)}`
  }
  // 靠港结束（ETD 过点/已完成）后快照只带船不带靠港，行 vesselId 有值、
  // vesselCallId 为空——这不是「未指定」，回落到常规配送方式是设计内行为
  const vesselRow = rows.find(row => row.vesselId)
  if (!vesselRow)
    return '未指定配送计划'
  // 行上只有船 ID 没有船名；行归属恰好是当前上下文那艘时才报得出船名
  return vesselRow.vesselId === shipContextStore.vesselId
    ? `当前船舶：${shipContextStore.vesselName || '未命名'}（暂无进行中的靠港计划）`
    : '船舶暂无进行中的靠港计划'
}
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
  goodsSpu: {},
  selectArr: [],
})

/**
 * 商品件数不叫「种数」：一行可能买 3 件，按 cartList.length 统计会让顶部
 * 「共 1 件商品」与底部「去结算(3)」对不上。
 * 勾选件数同样按数量累加，保证顶部汇总、底部按钮、编辑模式删除条三处同口径。
 */
const cartPieceCount = computed(() =>
  state.cartList.reduce((sum: number, row: any) => sum + (Number(row.quantity) || 0), 0),
)
const checkedPieceCount = computed(() =>
  state.checkedList.reduce((sum: number, row: any) => sum + (Number(row.quantity) || 0), 0),
)

// 编辑按钮
function handleEdit() {
  state.isEdit = !state.isEdit
  // 退出编辑模式时剔除已选中但无货/下架的行（filter 重建，
  // 边遍历边 splice 会因下标前移漏掉相邻项）
  if (!state.isEdit) {
    state.checkedList = state.checkedList.filter(
      (val: any) => val.goodsSku && val.goodsSku.stock > 0,
    )
  }
  if (state.isEdit && state.shopCheckedAll) {
    // 如果全选了，检查是否存在无货商品
    state.cartList.forEach((val: any) => {
      if (!val.goodsSku || val.goodsSku.stock <= 0) {
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
      if (!val.checked && val.goodsSku && val.goodsSku.stock > 0) {
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
      if (state.isEdit || (val.goodsSku && val.goodsSku.stock > 0)) {
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
    if (i > -1)
      state.checkedList.splice(i, 1)
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
    // 空购物车时后端返回 data:null，统一归一化为空数组，
    // 避免各处 length / forEach / 分组计算面对 null 各写一遍判空
    state.cartList = response || []
    // 空车没有可管理对象，工具行会随列表一起隐藏；编辑态必须同步退出，
    // 否则用户删光商品后找不到退出编辑的入口（底栏停在编辑态变死锁）
    if (state.cartList.length === 0) {
      state.isEdit = false
    }
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
          if (i > -1)
            state.checkedList.splice(i, 1)
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
/** 空态次操作：常购清单（船供场景的复购入口，与首页工作台同一个） */
function toFrequent() {
  uni.navigateTo({ url: '/sub-pages/product/frequent/index' })
}
/** 打开船舶与靠港选择器；切换后分组随 shipContextStore 变化自动重算 */
function openShipPicker() {
  shipPickerVisible.value = true
}// 去结算 跳转结算页
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
    // 两者并行：cartGroups 是 computed，任一侧就绪都会重算，
    // 不必让列表等上下文拉回来才渲染
    void loadShipContext()
    getCartPage()
  }
})
</script>

<template>
  <hr-navbar title="购物车" :left-arrow="false" />
  <!--
    这里曾常驻一条「收货地址」栏，但它是纯展示：state.address 只用于渲染这行文案，
    toSettlement 并不会把它带给结算页，结算页自己会重新取默认地址并允许改选；
    地址的常驻入口在「我的 → 收货地址」。留在购物车只会误导（尤其是船内配送
    delivery_way=4 时地址根本不参与下单），故整行移除，同时省去每次 onShow
    都发一次 getDefault 请求。
  -->
  <!--
    船舶上下文控制区。
    原先是两张等权重的白卡纵向堆叠：上面一张「共享购物车」通栏、下面一张船舶状态卡，
    而「当前船舶：X」在紧接着的分组头里又出现一次。首屏只有一件商品时，
    用户先看到两块几乎一样重的大卡片和两次重复的船名，密度全耗在容器上。
    现在合并为一个区：船舶卡（主控，点按切换/申报）+ 共享购物车降为区内的胶囊入口，
    分组头随即去重（只报靠港，不重复船名）。
    无船舶上下文（纯零售租户 / 未绑定）时整个区不渲染：此时共享购物车不做
    整船合并、没有靠港可切，留一个入口只会引导到空列表。
  -->
  <view v-if="showShipStatusBar" class="ship-panel">
    <view class="ship-card" @click="openShipPicker">
      <!-- 状态点：有进行中靠港=主题色，无靠港=橙色（与右侧「去申报」同色），
           颜色之外还有文案区分，不依赖颜色单独传达状态 -->
      <view class="ship-status-dot" :class="shipStatusTone" />
      <view class="min-w-0 flex-1">
        <view class="ship-name">
          {{ shipContextStore.vesselName || '未命名' }}
        </view>
        <view class="ship-meta" :class="{ 'is-warning': !hasActiveCall }">
          {{ shipStatusBarText }}
        </view>
      </view>
      <text
        v-if="!hasActiveCall"
        class="ship-action is-warning"
      >
        去申报 &gt;
      </text>
      <text v-else class="ship-action">
        切换 &gt;
      </text>
    </view>
    <!-- 共享购物车：同船多成员分别加购，采购确认人统一提交整船订单 -->
    <view class="ship-shared-entry" @click="toSharedCart">
      与同船成员合并采购
      <text class="ship-shared-arrow">
        &gt;
      </text>
    </view>
  </view>
  <view class="cart-container">
    <view v-if="state.cartList && state.cartList.length > 0" class="cart-item">
      <!-- 管理入口挂在列表头而非导航栏：小程序端导航栏右侧要避让微信胶囊，
           文字会被挤到标题和胶囊之间；列表头右侧空间充裕且语义更贴切 -->
      <view class="cart-toolbar">
        <!-- 件数走数量累加：一行买 3 件时「共 1 件商品」会与底部结算件数对不上 -->
        <view class="toolbar-info">
          <text class="toolbar-count">
            共 {{ cartPieceCount }} 件商品
          </text>
          <text v-if="state.shopCheckedAll" class="toolbar-badge">
            已全选
          </text>
          <text v-else-if="state.checkedList.length > 0" class="toolbar-badge">
            已选 {{ checkedPieceCount }}
          </text>
        </view>
        <view class="manage-pill" :class="{ 'is-active': state.isEdit }" @click="handleEdit">
          <wd-icon
            :name="state.isEdit ? 'check' : 'edit-outline'" size="24rpx"
            :color="state.isEdit ? '#07c160' : '#646566'"
          />
          <text class="manage-pill-text">
            {{ state.isEdit ? '完成' : '管理' }}
          </text>
        </view>
      </view>
      <!--
        防串船分组头：商品归属哪条船由上方船舶卡回答（分组头不再重复船名），
        这里只保留「跨靠港结算会被拦截」这一条商品列表信息，且做成细窄说明行，
        避免与船舶卡争夺视觉层级（此前是同款灰底圆角条，读起来像第二张卡片）。
      -->
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
          结算前请切换船舶
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
          <text class="no-stock-text">
            {{ goods.goodsSku ? '无货' : '下架' }}
          </text>
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
              :text="goods.goodsSku.salesPrice" size="30rpx" color="var(--wot-color-theme-primary, #ff2e4d)" mode="price"
              prefix="￥"
            />
            <wd-input-number
              v-if="goods.goodsSku && goods.goodsSku.stock > 0" v-model="goods.quantity" :min="1" :index="goods.id"
              step-strictly :max="goods.goodsSku.stock" :step="1"
              @change="quantityChange($event, goods.id)" @tap.stop=""
            />
          </view>
        </view>
      </view>
    </view>
    <view v-else class="cart-empty">
      <wd-status-tip image="content" tip="购物车还是空的" />
      <!-- 空态给两条出路：主操作回商品列表，次操作去常购清单。
           原先只有「去逛逛」，常购用户每次都要从分类页重新翻一遍 -->
      <view class="cart-empty-actions">
        <view class="empty-btn empty-btn-primary" @tap.stop="toPageUrl">
          去逛逛
        </view>
        <view class="empty-btn" @tap.stop="toFrequent">
          常购清单
        </view>
      </view>
      <view class="cart-empty-tip">
        绑定了船舶的商品会自动按靠港计划分组，方便同船成员合并结算
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
        <text class="footer-all-text">
          全选
        </text>
      </view>
      <view v-if="!state.isEdit" class="footer-right">
        <view class="footer-price">
          <text class="footer-price-label">
            合计
          </text>
          <wd-text size="32rpx" mode="price" :text="state.totalAmount" prefix="￥" color="var(--wot-color-theme-primary, #ff2e4d)" />
        </view>
        <!-- 按钮禁用态去掉饱和渐变与投影：一屏里最重的色块落在不可点的按钮上，
             会让人以为那是可点的主操作。禁用时改浅灰，可点时才用品牌渐变 -->
        <view
          class="settle-btn" :class="{ 'is-disabled': state.checkedList.length <= 0 }"
          @click="state.checkedList.length > 0 && toSettlement()"
        >
          去结算({{ checkedPieceCount }})
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
          删除({{ checkedPieceCount }})
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
  <!-- 船舶与靠港选择器：跨靠港分组需要切换船舶才能合并结算；
       申报成功（chooseCall）后 change 回来刷新列表，让顺延过的行按新靠港重新分组 -->
  <ShipContextPicker v-model="shipPickerVisible" @change="getCartPage" />
</template>

<style scoped lang="scss">
/* 船舶控制区：船舶卡（主控）+ 共享购物车胶囊（次入口） */
.ship-panel {
  margin: 20rpx 20rpx 0;
}

.ship-card {
  display: flex;
  align-items: center;
  padding: 24rpx;
  border-radius: 20rpx;
  background: #fff;

  .ship-status-dot {
    width: 12rpx;
    height: 12rpx;
    border-radius: 50%;
    flex-shrink: 0;
    margin-right: 16rpx;

    &.is-active {
      background: var(--wot-color-theme-primary, #ff2e4d);
    }

    &.is-warning {
      background: #ff8f1f;
    }
  }

  .ship-name {
    font-size: 28rpx;
    font-weight: 700;
    color: #1d2129;
    line-height: 40rpx;
  }

  .ship-meta {
    margin-top: 6rpx;
    font-size: 24rpx;
    color: #86909c;
    line-height: 34rpx;

    &.is-warning {
      color: #ff8f1f;
    }
  }

  .ship-action {
    flex-shrink: 0;
    margin-left: 16rpx;
    font-size: 26rpx;
    color: #86909c;

    &.is-warning {
      color: var(--wot-color-theme-primary, #ff2e4d);
    }
  }
}

/* 共享购物车由通栏大卡降为区内胶囊：它平时只是偶尔用的协作入口，
   与「切换船舶」这对高频操作不该有同等视觉权重 */
.ship-shared-entry {
  display: inline-flex;
  align-items: center;
  margin-top: 12rpx;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  background: #fff;
  font-size: 24rpx;
  color: #4e5969;

  .ship-shared-arrow {
    margin-left: 8rpx;
    color: #c9cdd4;
  }
}

/* 列表工具行：件数 + 选中态 + 管理入口 */
.cart-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20rpx 20rpx 0;

  .toolbar-info {
    display: flex;
    align-items: center;
    min-width: 0;
  }

  .toolbar-count {
    font-size: 24rpx;
    color: #86909c;
  }

  .toolbar-badge {
    margin-left: 12rpx;
    padding: 2rpx 12rpx;
    border-radius: 16rpx;
    font-size: 22rpx;
    color: var(--wot-color-theme-primary, #ff2e4d);
    background: rgba(255, 46, 77, 0.08);
  }

  .manage-pill {
    display: flex;
    align-items: center;
    height: 56rpx;
    padding: 0 22rpx;
    border-radius: 28rpx;
    background: #fff;
    transition: all 0.15s;

    .manage-pill-text {
      margin-left: 6rpx;
      font-size: 24rpx;
      color: #303133;
    }

    &.is-active {
      background: rgba(7, 193, 96, 0.1);

      .manage-pill-text {
        color: #07c160;
      }
    }
  }
}

.cart-container {
  background: #f5f6f8;
  min-height: 100vh;
  padding-bottom: 20rpx;
}

/* 分组说明行：细窄、无底色块，靠左侧色条表达「这一组归属同一靠港」 */
.group-label {
  display: flex;
  align-items: center;
  font-size: 24rpx;
  color: #86909c;
  margin: 20rpx 20rpx 0;
  padding-left: 16rpx;
  line-height: 36rpx;
  border-left: 4rpx solid #dcdfe6;

  .group-switch {
    margin-left: 12rpx;
    color: var(--wot-color-theme-primary, #ff2e4d);
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
    background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
    color: #fff;
    font-size: 30rpx;
    font-weight: 500;
    box-shadow: 0 6rpx 16rpx rgba(255, 77, 46, 0.3);

    &.is-disabled {
      /* 禁用态彻底去饱和：渐变与品牌色投影都会让不可点的按钮看着像主操作 */
      background: #e5e6eb;
      color: #a8abb2;
      box-shadow: none;
      font-weight: 400;
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
      color: var(--wot-color-theme-primary, #ff2237);
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

/* 空态：两条出路并排，主次分明；补一句分组说明让用户知道加购后会发生什么 */
.cart-empty {
  padding: 120rpx 60rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;

  .cart-empty-actions {
    display: flex;
    align-items: center;
    margin-top: 40rpx;
  }

  .empty-btn {
    min-width: 200rpx;
    height: 72rpx;
    line-height: 72rpx;
    text-align: center;
    border-radius: 36rpx;
    font-size: 28rpx;
    color: #4e5969;
    background: #fff;
    border: 1rpx solid #e5e6eb;

    + .empty-btn {
      margin-left: 24rpx;
    }

    &.empty-btn-primary {
      color: #fff;
      border: none;
      background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
    }
  }

  .cart-empty-tip {
    margin-top: 32rpx;
    font-size: 22rpx;
    color: #a8abb2;
    text-align: center;
    line-height: 34rpx;
  }
}
</style>
