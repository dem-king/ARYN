<script setup lang="ts">
import type { SharedCart } from '@/api/order/sharedCart'
import type { ShipSupplyRow } from '@/sub-pages/utils/ship-supply'

/**
 * 船供选货页（共享购物车选货 / 船供采购）。
 *
 * 两种进入方式共用本页：
 * - 带 `sharedCartId` 进入（共享购物车选货）：加购写入共享购物车，船舶/靠港取自购物车本身；
 * - 独立进入：加购写入个人购物车，船舶上下文取当前 store。
 *
 * 页面结构按「采购员的实际操作顺序」组织：
 *   确认在哪条船/哪个靠港下单 → 按编码或品名找货 → 逐条填数量加入 → 底部清单条交回。
 *
 * 关键取舍（每一条都对应一次实际使用中的问题）：
 * - 上下文与搜索**常驻在滚动区外**（z-paging 的 #top）：选货要滚几十条，滚到中部时
 *   还得知道自己在给哪条船下单，返回顶部才能确认是多余的；
 * - 底部常驻清单条（本页是加购目的地，不是购物车）：原来加完一条没有任何总量反馈，
 *   用户不知道加了几项、也不知道如何回到清单，只能靠返回键猜；
 * - 加购数量用步进器而非裸输入框：采购量有 MOQ/步长约束，裸输入框要么让用户
 *   自己算倍数，要么等结算才报错；
 * - 已加入的商品在卡片上标出来，避免同一条重复加；
 * - 库存/缺货在按钮上就置灰，不放到点击之后才失败。
 */
import { onLoad, onShow } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { alovaInstance } from '@/api/core/instance'
import { addSharedCartItem, getSharedCart, getSharedCartItems } from '@/api/order/sharedCart'
import { addShoppingCart } from '@/api/order/shoppingCart'
import hrNavbar from '@/components/hr-navbar/index.vue'
import ShipContextPicker from '@/components/ship-context-picker/index.vue'
import { useShipContextStore } from '@/store/shipContextStore'
import {
  defaultQuantityOf,
  isRowAddable,
  quantityMaxOf,
  quantityRuleOf,
  salesPriceText,
  shipSupplyRowKey,
  stockHintOf,
  stockToneOf,
  unavailableReasonOf,
} from '@/sub-pages/utils/ship-supply'
import { resolveImageSrc } from '@/utils/image'

definePage({
  name: 'ship-supply',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '船供采购',
  },
})

const shipContextStore = useShipContextStore()

const pagingRef = ref()
const keyword = ref('')
/** 已提交给后端的搜索词：输入框可继续编辑，列表按它渲染，避免边打字边刷列表 */
const activeKeyword = ref('')
const loadFailed = ref(false)
const list = ref<ShipSupplyRow[]>([])

/**
 * 每行的采购数量：key 为 spuId-skuId。
 *
 * 不放行内 `_qty` 字段：视图状态混进接口数据后，刷新/翻页会莫名其妙地保留或丢失；
 * 而且行对象会被 z-paging 整体替换，放在行上等于每次请求都清零。
 */
const quantities = reactive<Record<string, number | string>>({})

/** 本页已加入的 SKU 累计数量（含本次会话内多次加购），用于卡片「已加入」标记 */
const addedBySku = reactive<Record<string, number>>({})

/**
 * 共享购物车选货模式：携带 sharedCartId 进入时，加购写入共享购物车而非个人购物车。
 * 共享购物车自身已绑定船舶与靠港计划，因此展示它的上下文而非当前上下文，
 * 避免用户在错误的船/靠港下选货。
 */
const sharedCartId = ref('')
const sharedCart = ref<SharedCart | null>(null)
const isSharedMode = computed(() => !!sharedCartId.value)
/** 清单内已有明细数（进入时拉一次，加购后自增），底部条用它回答「选了多少」 */
const cartItemCount = ref(0)
/** 船舶与靠港选择器可见性 */
const shipPickerVisible = ref(false)

/** 顶部上下文文案：共享模式下取自购物车，个人模式下取当前上下文 */
const contextVesselName = computed(() => isSharedMode.value
  ? (sharedCart.value?.vesselName || '船舶信息加载中')
  : (shipContextStore.vesselName || '未选择船舶'))

const contextLocation = computed(() => {
  if (isSharedMode.value) {
    return [sharedCart.value?.portName, sharedCart.value?.berth].filter(Boolean).join(' ')
  }
  if (!shipContextStore.hasVesselContext)
    return ''
  return [shipContextStore.portName, shipContextStore.berth].filter(Boolean).join(' ')
})

/** 已加入的商品种类数（区别于件数：采购员更关心「还差哪些没选」） */
const addedSkuCount = computed(() => Object.keys(addedBySku).length)

function quantityOf(row: ShipSupplyRow) {
  return quantities[shipSupplyRowKey(row)]
}

function addedQuantityOf(row: ShipSupplyRow) {
  return addedBySku[row.skuId ?? ''] ?? 0
}

/**
 * 列表查询。关键词统一走 `keyword` 参数：服务端按商品名称/子标题匹配
 * （船供编码搜索随船供资料下线移除，2026-09-29）。
 */
async function queryList(pageNo: number, pageSize: number) {
  try {
    const res = await alovaInstance.Get<any>('/product/app/goodsspu/ship/page', {
      params: {
        current: pageNo,
        size: pageSize,
        keyword: activeKeyword.value || undefined,
      },
    })
    const records: ShipSupplyRow[] = res?.records ?? []
    // 新到货的行按「可加购的最小合法数量」预填，用户不填也能直接点加购
    for (const row of records) {
      const key = shipSupplyRowKey(row)
      if (quantities[key] === undefined)
        quantities[key] = defaultQuantityOf(row)
    }
    loadFailed.value = false
    // completeByTotal 而非 complete：服务端给了 total，让它决定「没有更多」，
    // 不要靠「本页不足 size」猜（页大小会被服务端改写）
    pagingRef.value?.completeByTotal(records, res?.total ?? records.length)
  }
  catch {
    loadFailed.value = true
    // complete(false) 让 z-paging 走失败分支（显示重试而不是「暂无数据」）
    pagingRef.value?.complete(false)
  }
}

function handleSearch() {
  const next = keyword.value.trim()
  // 空关键词也是一次有效查询（回到全部商品），不能因为「没变」就吞掉
  activeKeyword.value = next
  pagingRef.value?.reload()
}

function clearSearch() {
  keyword.value = ''
  handleSearch()
}

/** 进入共享购物车列表 */
function goSharedCartList() {
  uni.navigateTo({ url: '/sub-pages/order/shared-cart/list' })
}

/** 回到共享购物车详情（加完货交回确认人） */
function goSharedCartDetail() {
  if (sharedCartId.value)
    uni.navigateTo({ url: `/sub-pages/order/shared-cart/detail?id=${sharedCartId.value}` })
  else
    goSharedCartList()
}

/** 打开船舶与靠港选择器（此前提示「请在首页选择」但首页没有入口） */
function openShipPicker() {
  shipPickerVisible.value = true
}

/** 查看商品详情（只读核对，不打断选货节奏） */
function goGoodsDetail(row: ShipSupplyRow) {
  if (!row.spuId)
    return
  uni.navigateTo({ url: `/sub-pages/product/goods-detail/index?id=${row.spuId}` })
}

/**
 * 加购数量按采购单位填写，MOQ/步长由服务端结算再次校验；
 * 这里就地拦住明显不合法的值，避免用户等到提交整船订单才被打回。
 */
function validateQuantity(row: ShipSupplyRow, quantity: number) {
  if (!quantity || quantity < 1) {
    uni.showToast({ title: '请填写采购数量', icon: 'none' })
    return null
  }
  const max = quantityMaxOf(row)
  if (max !== null && quantity > max) {
    uni.showToast({ title: `库存仅 ${max}`, icon: 'none' })
    return null
  }
  const { moq, stepQty } = quantityRuleOf(row)
  if (quantity < moq) {
    uni.showToast({ title: `未达最小起订量 ${moq}`, icon: 'none' })
    return null
  }
  if (quantity % stepQty !== 0) {
    uni.showToast({ title: `数量需为 ${stepQty} 的整数倍`, icon: 'none' })
    return null
  }
  return quantity
}

function handleAddToCart(row: ShipSupplyRow) {
  // 不可加购的行按钮已置灰，这里再兜一次（视图用的是 view，没有原生 disabled）
  if (!isRowAddable(row)) {
    uni.showToast({ title: unavailableReasonOf(row) || '当前不可加购', icon: 'none' })
    return
  }
  if (!row.skuId) {
    uni.showToast({ title: '该商品暂无可售规格', icon: 'none' })
    return
  }
  const raw = Number(quantityOf(row) ?? defaultQuantityOf(row))
  const quantity = validateQuantity(row, raw)
  if (quantity === null)
    return

  const request = isSharedMode.value
    ? addSharedCartItem(sharedCartId.value, {
        spuId: row.spuId,
        skuId: row.skuId,
        requestedQuantity: quantity,
      })
    : addShoppingCart({
        skuId: row.skuId,
        quantity,
        addType: '2',
      })
  request
    .then(() => {
      // 加购成功：数量回正到默认值，并累计本页已加入量
      const key = shipSupplyRowKey(row)
      quantities[key] = defaultQuantityOf(row)
      addedBySku[row.skuId ?? ''] = addedQuantityOf(row) + quantity
      cartItemCount.value += 1
      uni.showToast({
        title: isSharedMode.value ? '已加入共享购物车' : '已加入购物车',
        icon: 'success',
      })
    })
    .catch(() => {})
}

/** 进入共享模式时拉一次清单概览：底部条要显示「清单已有 N 项」 */
function fetchCartSummary() {
  if (!sharedCartId.value)
    return Promise.resolve()
  return Promise.all([
    getSharedCart(sharedCartId.value).then((res) => {
      sharedCart.value = res ?? null
    }),
    getSharedCartItems(sharedCartId.value).then((res) => {
      cartItemCount.value = res?.length ?? 0
    }),
  ]).catch(() => {})
}

onLoad((options) => {
  sharedCartId.value = options?.sharedCartId ?? ''
  void fetchCartSummary()
})

/** 从详情页返回时刷新清单条计数（成员可能在别处改过清单） */
onShow(() => {
  if (sharedCartId.value && sharedCart.value)
    void fetchCartSummary()
})
</script>

<template>
  <view class="ship-supply">
    <!--
      用 z-paging 自带的 fixed 布局（默认）：组件占满视口，`#top`/`#bottom` 插槽
      自然固定在滚动区上下，无需页面再手写 fixed 定位与等高占位。
    -->
    <z-paging
      ref="pagingRef"
      v-model="list"
      :safe-area-inset-bottom="false"
      @query="queryList"
    >
      <!--
        常驻区（上下文 + 搜索）放在 #top：选货列表很长，滚到中部仍要能确认
        「在给哪条船选货」，并随时换关键词。
      -->
      <template #top>
        <hr-navbar :title="isSharedMode ? '共享购物车选货' : '船供采购'" />

        <!-- 共享购物车选货模式：加购写入共享购物车，船舶/靠港取自购物车本身 -->
        <view v-if="isSharedMode" class="context context--shared">
          <view class="context__head">
            <wd-icon name="cart" size="16px" color="#185FA5" />
            <text class="context__badge">
              共享购物车选货
            </text>
          </view>
          <view class="context__vessel">
            {{ contextVesselName }}
          </view>
          <view class="context__meta">
            <text v-if="contextLocation">
              {{ contextLocation }}
            </text>
            <text v-else>
              靠港信息待补全
            </text>
          </view>
          <view class="context__tip">
            选好的货进入共享购物车，由采购确认人统一提交
          </view>
        </view>

        <!-- 个人模式：当前船舶上下文，缺失时就地打开选择器 -->
        <view v-else class="context context--personal">
          <view class="context__head">
            <wd-icon name="location" size="16px" color="#8a94a6" />
            <text class="context__vessel">
              {{ contextVesselName }}
            </text>
            <text class="context__link" @tap="goSharedCartList">
              共享购物车
            </text>
          </view>
          <view v-if="contextLocation" class="context__meta">
            {{ contextLocation }}
          </view>
          <view v-else class="context__meta context__meta--action" @tap="openShipPicker">
            选择船舶和靠港计划
            <wd-icon name="chevron-right" size="14px" color="#185FA5" />
          </view>
        </view>

        <!-- 搜索：按商品名称/子标题匹配（编码搜索随船供资料下线移除） -->
        <view class="search">
          <view class="search__box">
            <wd-icon name="search" size="18px" color="#9aa2ae" />
            <input
              v-model="keyword"
              class="search__input"
              type="text"
              placeholder="搜索商品名称"
              placeholder-class="search__placeholder"
              confirm-type="search"
              @confirm="handleSearch"
            >
            <view v-if="keyword" class="search__clear" @tap="clearSearch">
              <wd-icon name="close-circle-filled" size="16px" color="#c3c9d2" />
            </view>
          </view>
          <view class="search__action" @tap="handleSearch">
            搜索
          </view>
        </view>
      </template>

      <!-- 商品流：整行卡，左侧商品图 + 右侧信息与加购 -->
      <view
        v-for="(row, index) in list"
        :key="shipSupplyRowKey(row)"
        class="row"
        :style="{ animationDelay: `${Math.min(index, 8) * 32}ms` }"
      >
        <view class="row__thumb" @tap="goGoodsDetail(row)">
          <image class="row__pic" :src="resolveImageSrc(row.picUrl)" mode="aspectFill" lazy-load />
          <view v-if="unavailableReasonOf(row)" class="row__sold-out">
            {{ unavailableReasonOf(row) }}
          </view>
        </view>

        <view class="row__body">
          <view class="row__name" @tap="goGoodsDetail(row)">
            {{ row.name || '未命名商品' }}
          </view>
          <view class="row__price-line">
            <view class="row__price">
              <text class="row__price-symbol">
                ￥
              </text>
              <text class="row__price-value">
                {{ salesPriceText(row) }}
              </text>
            </view>
            <!-- 库存文案与档位分开：颜色只是补充，文字本身已说明情况 -->
            <text
              v-if="stockHintOf(row)"
              class="row__stock"
              :class="`row__stock--${stockToneOf(row)}`"
            >
              {{ stockHintOf(row) }}
            </text>
          </view>

          <view class="row__action">
            <view class="row__controls">
              <wd-input-number
                v-if="isRowAddable(row)"
                v-model="quantities[shipSupplyRowKey(row)]"
                :min="quantityRuleOf(row).moq"
                :max="quantityMaxOf(row) ?? 999999"
                :step="quantityRuleOf(row).stepQty"
                :input-width="56"
                step-strictly
              />
              <!-- 已加入标记与步进器同组靠左，重复加购前一眼可见 -->
              <text v-if="addedQuantityOf(row) > 0" class="row__added">
                已加 {{ addedQuantityOf(row) }}
              </text>
            </view>
            <!-- 加购按钮独立贴右：有无「已加」标记都不影响按钮右缘对齐 -->
            <view
              class="row__add"
              :class="{ 'row__add--disabled': !isRowAddable(row) }"
              @tap="handleAddToCart(row)"
            >
              {{ isSharedMode ? '加入共享车' : '加入购物车' }}
            </view>
          </view>
        </view>
      </view>

      <!-- 空态：加载失败与无数据必须区分，避免把请求失败误报为「暂无商品」 -->
      <template #empty>
        <view class="empty">
          <wd-status-tip
            :image="loadFailed ? 'network' : activeKeyword ? 'search' : 'content'"
            image-size="176rpx"
            :tip="loadFailed
              ? '加载失败，请检查网络后重试'
              : activeKeyword
                ? `没有找到「${activeKeyword}」相关的商品`
                : '暂无船供商品'"
          />
          <view v-if="loadFailed || activeKeyword" class="empty__action" @tap="loadFailed ? pagingRef?.reload() : clearSearch()">
            {{ loadFailed ? '重新加载' : '清空搜索' }}
          </view>
        </view>
      </template>
      <!-- 底部常驻清单条：z-paging 的 #bottom 插槽固定在滚动区下方，并自动处理安全区 -->
      <template #bottom>
        <view class="checkout-bar">
          <view class="checkout-bar__info">
            <view class="checkout-bar__count">
              已选 <text class="checkout-bar__count-num">
                {{ addedSkuCount }}
              </text> 种
            </view>
            <view class="checkout-bar__meta">
              <template v-if="isSharedMode">
                清单共 {{ cartItemCount }} 项 · 交确认人提交
              </template>
              <template v-else>
                {{ shipContextStore.hasVesselContext ? '结算时按靠港配送' : '未选船舶与靠港' }}
              </template>
            </view>
          </view>
          <view class="checkout-bar__btn" @tap="goSharedCartDetail">
            {{ isSharedMode ? '查看共享购物车' : '去购物车结算' }}
          </view>
        </view>
      </template>
    </z-paging>

    <!-- 船舶与靠港选择器 -->
    <ShipContextPicker v-model="shipPickerVisible" />
  </view>
</template>

<style lang="scss" scoped>
.ship-supply {
  min-height: 100vh;
  background: #f4f5f7;
}

/**
 * 上下文卡。
 *
 * 共享模式用蓝底强调「这是整船共享的清单」，个人模式用白底中性呈现，
 * 两者一眼可辨 —— 用户误以为在给自己下单、实际写进整船清单是真实风险。
 */
.context {
  margin: 16rpx 20rpx 0;
  padding: 20rpx 24rpx;
  border-radius: 16rpx;

  &--shared {
    background: linear-gradient(135deg, #e8f2fd, #dceafa);
    border: 1rpx solid #cfe2f8;
    color: #185fa5;
  }

  &--personal {
    background: #fff;
  }

  &__head {
    display: flex;
    align-items: center;
    gap: 8rpx;
  }

  &__badge {
    font-size: 24rpx;
    font-weight: 600;
    color: #185fa5;
  }

  &__vessel {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    font-size: 30rpx;
    font-weight: 600;
    color: #1b2739;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__link {
    flex: none;
    font-size: 24rpx;
    color: #185fa5;
  }

  &--shared &__vessel {
    flex: none;
    margin-top: 8rpx;
    font-size: 32rpx;
    color: #10456f;
  }

  &__meta {
    margin-top: 6rpx;
    font-size: 24rpx;
    color: #6b7686;

    &--action {
      display: flex;
      align-items: center;
      gap: 4rpx;
      color: #185fa5;
    }
  }

  &--shared &__meta {
    color: #2b6ca3;
  }

  &__tip {
    margin-top: 10rpx;
    font-size: 22rpx;
    color: #2b6ca3;
  }
}

/* 搜索行：常驻在滚动区外，输入框高度按 44px 触控下限设计 */
.search {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 20rpx;
  background: #f4f5f7;

  &__box {
    display: flex;
    flex: 1;
    align-items: center;
    gap: 10rpx;
    height: 76rpx;
    padding: 0 24rpx;
    border-radius: 38rpx;
    background: #fff;
    box-sizing: border-box;
  }

  &__input {
    flex: 1;
    min-width: 0;
    height: 76rpx;
    font-size: 27rpx;
    color: #1b2739;
  }

  &__placeholder {
    font-size: 26rpx;
    color: #a8afba;
  }

  &__clear {
    display: flex;
    flex: none;
    align-items: center;
    justify-content: center;
    width: 44rpx;
    height: 44rpx;
  }

  &__action {
    flex: none;
    padding: 0 8rpx;
    font-size: 28rpx;
    font-weight: 500;
    color: var(--theme-color-primary, var(--wot-color-theme-primary));
  }
}

/* 商品行：左图右信息，图与文字基线对齐 */
.row {
  display: flex;
  margin: 0 20rpx 16rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  background: #fff;
  animation: row-in 280ms ease-out backwards;

  &__thumb {
    position: relative;
    width: 180rpx;
    height: 180rpx;
    flex: none;
    overflow: hidden;
    border-radius: 12rpx;
    background: #f2f3f5;
  }

  &__pic {
    display: block;
    width: 100%;
    height: 100%;
  }

  &__sold-out {
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    padding: 6rpx 0;
    background: rgb(27 39 57 / 66%);
    font-size: 22rpx;
    color: #fff;
    text-align: center;
  }

  &__body {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
    padding-left: 20rpx;
  }

  &__name {
    font-size: 29rpx;
    font-weight: 600;
    line-height: 1.35;
    color: #1b2739;
    /* 小程序不支持 unocss 的 line-clamp 简写，显式写 box 属性 */
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    overflow: hidden;
  }

  &__price-line {
    display: flex;
    align-items: baseline;
    margin-top: 12rpx;
  }

  &__price {
    display: flex;
    align-items: baseline;
    flex: 1;
    min-width: 0;
    color: var(--wot-color-theme-primary, #ff2237);
    font-weight: 600;
  }

  &__price-symbol {
    font-size: 22rpx;
  }

  &__price-value {
    font-size: 34rpx;
  }

  &__stock {
    flex: none;
    font-size: 22rpx;
    color: #9aa2ae;

    &--low {
      color: #e8734a;
    }

    &--out {
      color: #c0563a;
    }
  }

  /* 步进器/已加标记靠左，加购按钮贴右：各行按钮右缘对齐 */
  &__action {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 12rpx;
  }

  &__controls {
    display: flex;
    flex: none;
    align-items: center;
    gap: 12rpx;
  }

  /* 已加入标记：同一条重复加购前能一眼看到 */
  &__added {
    flex: none;
    padding: 2rpx 10rpx;
    border-radius: 6rpx;
    background: #e8f6ee;
    font-size: 20rpx;
    color: #0f6e56;
  }

  /**
   * 加购按钮与步进器同行。
   *
   * 不做通栏大按钮：目录有 272 条商品，卡片再高一截会让每屏只能看两三件，
   * 列表页的浏览效率比单个按钮的视觉重量更重要。
   * 触控面积仍按 64rpx（≈32px）高度 + 左右留白，满足最小可点区域。
   */
  &__add {
    flex: none;
    /* space-between 已把按钮推到右缘，这里只兜底极窄屏下的最小间距 */
    min-width: 168rpx;
    height: 64rpx;
    margin-left: 12rpx;
    padding: 0 20rpx;
    border-radius: 32rpx;
    background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
    box-shadow: 0 4rpx 12rpx rgb(255 77 46 / 24%);
    font-size: 25rpx;
    font-weight: 500;
    line-height: 64rpx;
    color: #fff;
    text-align: center;

    &--disabled {
      background: #e5e7eb;
      box-shadow: none;
      color: #a8afba;
    }
  }
}

@keyframes row-in {
  from {
    opacity: 0;
    transform: translateY(12rpx);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.empty {
  padding: 60rpx 0;

  &__action {
    margin-top: 16rpx;
    font-size: 26rpx;
    color: var(--theme-color-primary, var(--wot-color-theme-primary));
    text-align: center;
  }
}

/**
 * 底部常驻清单条。
 *
 * 由 z-paging 的 `#bottom` 插槽承载：组件已把它排在滚动区下方的固定容器里，
 * 因此这里不再自己写 `position: fixed`（手写一份会与 z-paging 的 fixed 容器重叠）。
 * 安全区不走 z-paging 的 `safe-area-inset-bottom`（那会在条下方再插一个占位 view），
 * 直接并进自身内边距，与详情页 action-bar 同一口径。
 */
.checkout-bar {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -2rpx 12rpx rgb(27 39 57 / 6%);

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__count {
    font-size: 27rpx;
    color: #1b2739;
  }

  &__count-num {
    margin: 0 4rpx;
    font-size: 32rpx;
    font-weight: 600;
    color: var(--wot-color-theme-primary, #ff2237);
  }

  &__meta {
    margin-top: 2rpx;
    font-size: 22rpx;
    color: #9aa2ae;
  }

  &__btn {
    flex: none;
    height: 76rpx;
    padding: 0 40rpx;
    border-radius: 38rpx;
    background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
    box-shadow: 0 6rpx 16rpx rgb(255 77 46 / 30%);
    font-size: 28rpx;
    font-weight: 500;
    line-height: 76rpx;
    color: #fff;
  }
}

@media (prefers-reduced-motion: reduce) {
  .row {
    animation: none;
  }
}
</style>
