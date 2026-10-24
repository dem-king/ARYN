<script setup lang="ts">
import type { SharedCart, SharedCartCreatePayload } from '@/api/order/sharedCart'
/**
 * 共享购物车列表：我发起或我被邀请的全部购物车。
 *
 * 页面要回答的问题只有一个：「现在有没有还需要我管的单」。因此：
 * 1. 顶部是当前采购上下文 + 发起入口，回答「这一单送到哪」；
 * 2. 列表按「进行中 / 历史」两段分区，进行中的排在前面且按截止时间升序，
 *    让最紧急的单浮到最上面；
 * 3. 每张卡片给出「这单到哪一步了」（进度 / 倒计时）与「点进去能做什么」
 *    （动词随状态与权限变化），而不是四张卡都写同一个「查看」。
 *
 * 发起支持两种车：船供整车（绑船舶+靠港，要求当前已绑定上下文）与
 * 岸上企业（绑收货地址，未绑船的港口工厂客户用）。类型在创建弹层里切换。
 */
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'

import { computed, reactive, ref } from 'vue'
import { createSharedCart, getMySharedCarts, SHARED_CART_TYPE_SHIP, SHARED_CART_TYPE_SHORE } from '@/api/order/sharedCart'
import { getPage as getAddressPage } from '@/api/user/address'
import hrNavbar from '@/components/hr-navbar/index.vue'
import ShipContextPicker from '@/components/ship-context-picker/index.vue'
import { useCountdownTicker } from '@/composables/useCountdown'
import { useAuthStore } from '@/store/authStore'
import { useShipContextStore } from '@/store/shipContextStore'
import {
  cartActionLabel,
  cartDeadlineView,
  cartStatusLabel,
  cartStatusTheme,
  groupSharedCarts,
  isCartCollecting,
  isShoreCart,
  sharedCartLocationLabel,
  sharedCartTitleLabel,
} from '@/utils/shared-cart'
import { formatCallTime } from '@/utils/vessel-call-time'

definePage({
  name: 'shared-cart-list',
  style: {
    // 列表是本页唯一内容，下拉刷新是用户最顺手的「看看有没有新动静」手势
    enablePullDownRefresh: true,
    navigationStyle: 'custom',
    navigationBarTitleText: '共享购物车',
  },
})

/** 发起弹层里的收货地址（地址簿行；字段口径与 order-confirm 的 AddressSelector 一致） */
interface ShoreAddress {
  id: string
  recipientName?: string
  telephone?: string
  provinceName?: string
  cityName?: string
  areaName?: string
  detailAddress?: string
  isDefault?: string
}

const authStore = useAuthStore()
const shipContextStore = useShipContextStore()

/** 每秒推进的时间基准：卡片倒计时与紧急分档都要自己走，页面上没有别的定时器 */
const { now: tickNow } = useCountdownTicker()

const loading = ref(false)
const loadFailed = ref(false)
const records = ref<SharedCart[]>([])
/** 船舶与靠港选择器：船供整车创建必须有船舶+靠港上下文 */
const shipPickerVisible = ref(false)

/** 创建表单 */
const createState = reactive({
  visible: false,
  submitting: false,
  /** 车类型：1 船供整车 / 2 岸上企业；打开弹层时按上下文预选 */
  cartType: SHARED_CART_TYPE_SHIP,
  remark: '',
  /** 岸上车：已选收货地址ID */
  addressId: '',
  /** 岸上车：地址簿候选 */
  addresses: [] as ShoreAddress[],
  addressLoading: false,
})

/**
 * 船供车可发性：创建入口对登录用户开放（岸上车不要求船舶上下文），
 * 只有切到船供类型时才受船舶上下文约束（弹层与 hero 共用同一判定）。
 */
const shipTypeReady = computed(() => shipContextStore.hasVesselContext)

/** 分区后的列表：服务端按创建时间倒序，这里再按「还能不能动」重排 */
const sections = computed(() => groupSharedCarts(records.value, new Date(tickNow.value)))

/** 空态文案要区分「没登录」「没单」「有过滤无结果」，不共用一句话 */
const showEmpty = computed(() =>
  authStore.isLoggedIn && !loading.value && !loadFailed.value && records.value.length === 0,
)

/** 每张卡片的派生视图一次算好，模板里不再逐张做时间换算与文案拼装 */
interface CardView {
  action: string
  deadline: null | { expired: boolean, text: string, urgent: boolean }
  /** 收集截止的绝对时刻，用于核对「几点截止」 */
  expiresPoint: string
}

const cardViews = computed<Record<string, CardView>>(() => {
  const now = new Date(tickNow.value)
  const map: Record<string, CardView> = {}
  for (const cart of records.value) {
    map[cart.id] = {
      action: cartActionLabel(cart, now),
      deadline: cartDeadlineView(cart, now),
      expiresPoint: isCartCollecting(cart.status) && cart.expiresAt
        ? formatCallTime(cart.expiresAt, now)
        : '',
    }
  }
  return map
})

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

function fetchList() {
  if (!authStore.isLoggedIn) {
    records.value = []
    loadFailed.value = false
    return Promise.resolve()
  }
  loading.value = true
  return getMySharedCarts()
    .then((res) => {
      records.value = res ?? []
      loadFailed.value = false
    })
    .catch(() => {
      loadFailed.value = true
    })
    .finally(() => {
      loading.value = false
      uni.stopPullDownRefresh()
    })
}

/** 拉地址簿候选并预选默认地址（岸上发起弹层打开时调用） */
function loadShoreAddresses() {
  createState.addressLoading = true
  getAddressPage({ current: 1, size: 50 })
    .then((res) => {
      const list: ShoreAddress[] = res?.records ?? []
      createState.addresses = list
      // 已选地址仍有效则保留；否则回落默认地址，再回落第一条
      const stillValid = list.some(item => item.id === createState.addressId)
      if (!stillValid) {
        const preferred = list.find(item => item.isDefault === '1') ?? list[0]
        createState.addressId = preferred?.id ?? ''
      }
    })
    .catch(() => {
      // 拉失败不清空已有候选（重开弹层时还能用上一次的），只保留当前选择
    })
    .finally(() => {
      createState.addressLoading = false
    })
}

function openCreate() {
  if (!authStore.isLoggedIn) {
    goLogin()
    return
  }
  // 有船舶上下文默认发船供整车；未绑船（港口工厂等岸上客户）直接落到岸上企业
  createState.cartType = shipTypeReady.value ? SHARED_CART_TYPE_SHIP : SHARED_CART_TYPE_SHORE
  createState.remark = ''
  createState.addressId = ''
  createState.visible = true
  if (createState.cartType === SHARED_CART_TYPE_SHORE) {
    loadShoreAddresses()
  }
}

/** 弹层内切换车类型：切到岸上时按需拉地址，切到船供时缺上下文先弹选择器 */
function switchCartType(type: string) {
  if (type === createState.cartType)
    return
  if (type === SHARED_CART_TYPE_SHIP && !shipTypeReady.value) {
    shipPickerVisible.value = true
    return
  }
  createState.cartType = type
  if (type === SHARED_CART_TYPE_SHORE && createState.addresses.length === 0) {
    loadShoreAddresses()
  }
}

function toAddressForm() {
  uni.navigateTo({ url: '/sub-pages/user/address/form' })
}

function submitCreate() {
  if (createState.submitting)
    return
  const isShore = createState.cartType === SHARED_CART_TYPE_SHORE
  if (isShore && !createState.addressId) {
    uni.showToast({ title: '请选择收货地址', icon: 'none' })
    return
  }
  if (!isShore && !shipTypeReady.value) {
    shipPickerVisible.value = true
    return
  }
  createState.submitting = true
  const payload: SharedCartCreatePayload = {
    cartType: createState.cartType,
    vesselId: isShore ? undefined : shipContextStore.vesselId,
    vesselCallId: isShore ? undefined : shipContextStore.vesselCallId,
    userAddressId: isShore ? createState.addressId : undefined,
    remark: createState.remark || undefined,
  }
  createSharedCart(payload)
    .then((cart) => {
      createState.visible = false
      // 同船/同一发起人已有进行中的采购时，服务端复用该购物车而非新建，
      // 需明确告知用户是被并入，避免误以为刚创建了一个新的。
      uni.showToast({
        title: cart?.adoptedExisting ? '已有进行中采购，已为你打开' : '已创建',
        icon: 'none',
      })
      if (cart?.id) {
        uni.navigateTo({ url: `/sub-pages/order/shared-cart/detail?id=${cart.id}` })
      }
      else {
        void fetchList()
      }
    })
    .catch(() => {})
    .finally(() => {
      createState.submitting = false
    })
}

function goDetail(cart: SharedCart) {
  uni.navigateTo({ url: `/sub-pages/order/shared-cart/detail?id=${cart.id}` })
}

onShow(() => {
  void fetchList()
})

onPullDownRefresh(() => {
  void fetchList()
})
</script>

<template>
  <view class="shared-cart-list">
    <hr-navbar title="共享购物车" />

    <!--
      发起上下文条：回答「这一单送到哪」。这不是一张独立卡片——
      它只承载当前上下文与一个创建动作，用品牌渐变把它和下面的单据列表区分开，
      用户一眼能看出「上面这块是发起入口，下面这些是已有单据」。
      绑船用户显示船+靠港；未绑船的岸上客户（港口工厂等）提示走收货地址发起。
    -->
    <view class="hero">
      <view class="hero__head">
        <text class="hero__vessel">
          {{ shipContextStore.vesselName || '合并采购' }}
        </text>
        <view
          v-if="shipContextStore.hasVesselContext"
          class="hero__switch"
          @tap="shipPickerVisible = true"
        >
          切换
        </view>
      </view>

      <!-- 上下文齐备时补一句「这单会送到哪」，比只显示船名更有用 -->
      <view v-if="shipContextStore.hasVesselContext" class="hero__location">
        <text class="i-carbon:location hero__icon" />
        <text>{{ shipContextStore.portName || '港口待定' }} {{ shipContextStore.berth }}</text>
      </view>
      <view v-else class="hero__location">
        <text class="i-carbon:location hero__icon" />
        <text>未绑定船舶：可按岸上收货地址发起企业合并采购</text>
      </view>

      <view class="hero__cta" @tap="openCreate">
        <text class="i-carbon:add hero__cta-icon" />
        <text>发起共享购物车</text>
      </view>
    </view>

    <!-- 列表分区：进行中在前（按截止时间升序），历史在后；空分区整段不渲染 -->
    <view v-for="section in sections" :key="section.key" class="section">
      <view class="section__head">
        <view class="section__title-row">
          <text class="section__title">
            {{ section.title }}
          </text>
          <text class="section__count">
            {{ section.carts.length }}
          </text>
        </view>
        <text class="section__hint">
          {{ section.hint }}
        </text>
      </view>

      <view
        v-for="cart in section.carts"
        :key="cart.id"
        class="card"
        hover-class="card--pressed"
        :hover-stay-time="80"
        @tap="goDetail(cart)"
      >
        <view class="card__head">
          <view class="card__vessel-wrap">
            <text class="card__vessel">
              {{ sharedCartTitleLabel(cart) || '船舶信息加载中' }}
            </text>
            <!-- 自己发起的单标一下，同船多人时才知道这单该找谁 -->
            <text v-if="cart.viewerIsOwner" class="card__badge">
              我发起
            </text>
            <!-- 岸上企业单标类型，与船供单在混排列表里区分开 -->
            <text v-else-if="isShoreCart(cart)" class="card__badge card__badge--shore">
              岸上
            </text>
          </view>
          <view
            class="card__status"
            :style="`background:${cartStatusTheme(cart.status).bg};color:${cartStatusTheme(cart.status).text}`"
          >
            {{ cartStatusLabel(cart.status) }}
          </view>
        </view>

        <view class="card__location">
          <text class="i-carbon:location card__icon" />
          <text>{{ sharedCartLocationLabel(cart) || '港口待定' }}</text>
        </view>

        <!--
          收集截止：倒计时回答「还有多久」，绝对时刻用于核对「几点」。
          不足 1 小时转警示色，与详情页同一分档。
        -->
        <view
          v-if="cardViews[cart.id]?.deadline"
          class="card__deadline"
          :class="{
            'card__deadline--urgent': cardViews[cart.id]?.deadline?.urgent,
            'card__deadline--expired': cardViews[cart.id]?.deadline?.expired,
          }"
        >
          <text class="i-carbon:time card__icon" />
          <text>{{ cardViews[cart.id]?.deadline?.text }}</text>
          <text
            v-if="cardViews[cart.id]?.expiresPoint"
            class="card__deadline-point"
          >
            （{{ cardViews[cart.id]?.expiresPoint }}）
          </text>
        </view>

        <view class="card__foot">
          <text class="card__meta">
            {{ cart.itemCount ?? 0 }} 项商品 · {{ cart.memberCount ?? 0 }} 名成员
          </text>
          <!-- 主操作给出动词，用户不点进去也知道这一步会发生什么 -->
          <view class="card__action">
            <text>{{ cardViews[cart.id]?.action }}</text>
            <text class="i-carbon:chevron-right card__action-icon" />
          </view>
        </view>

        <!-- 编号是受理号性质的参考信息：放最底且弱化，不占视线 -->
        <text class="card__no">
          编号 {{ cart.cartNo }}
        </text>
      </view>
    </view>

    <!-- 状态区分：加载中 / 加载失败 / 未登录 / 空，四者文案不混用 -->
    <view v-if="loading && records.length === 0" class="state">
      <text class="state__text">
        加载中...
      </text>
    </view>
    <view v-else-if="loadFailed && records.length === 0" class="state">
      <text class="state__text">
        加载失败，请检查网络后重试
      </text>
      <view class="state__retry" @tap="fetchList">
        重新加载
      </view>
    </view>
    <view v-else-if="!authStore.isLoggedIn" class="state">
      <text class="state__text">
        登录后可查看共享购物车
      </text>
      <view class="state__retry" @tap="goLogin">
        去登录
      </view>
    </view>
    <view v-else-if="showEmpty" class="state">
      <text class="state__text">
        还没有共享购物车
      </text>
      <text class="state__desc">
        发起一单与伙伴合并采购，由确认人统一提交订单
      </text>
      <view class="state__cta" @tap="openCreate">
        发起共享购物车
      </view>
    </view>

    <!-- 船舶与靠港选择器：创建共享购物车前必须先选定上下文 -->
    <ShipContextPicker v-model="shipPickerVisible" />

    <!-- 创建表单：收集设置是低频决策，用底部弹层，不占据列表首屏 -->
    <wd-popup
      v-model="createState.visible"
      position="bottom"
      :safe-area-inset-bottom="true"
      custom-style="border-radius: 24rpx 24rpx 0 0; overflow: hidden;"
    >
      <view class="sheet">
        <text class="sheet__title">
          发起共享购物车
        </text>
        <text class="sheet__desc">
          创建后 24 小时内有效，超时自动关闭。成员可分别加购，由确认人统一提交合并订单。
        </text>

        <!-- 车类型：船供整车绑船+靠港，岸上企业绑收货地址；未绑船时岸上可选、船供引导去选船 -->
        <view class="sheet__type">
          <view
            class="sheet__type-item"
            :class="{ 'sheet__type-item--on': createState.cartType === '1' }"
            @tap="switchCartType('1')"
          >
            <text class="sheet__type-title">
              船供整车
            </text>
            <text class="sheet__type-desc">
              送靠港泊位
            </text>
          </view>
          <view
            class="sheet__type-item"
            :class="{ 'sheet__type-item--on': createState.cartType === '2' }"
            @tap="switchCartType('2')"
          >
            <text class="sheet__type-title">
              岸上企业
            </text>
            <text class="sheet__type-desc">
              送收货地址
            </text>
          </view>
        </view>

        <view v-if="createState.cartType === '1'" class="sheet__context">
          <text class="i-carbon:location sheet__context-icon" />
          <text>{{ shipContextStore.vesselName }} · {{ shipContextStore.portName }} {{ shipContextStore.berth }}</text>
        </view>

        <!-- 岸上：收货地址单选列表（配送终点与范围校验都以所选地址为准） -->
        <template v-else>
          <view class="sheet__label">
            收货地址
          </view>
          <view v-if="createState.addressLoading && createState.addresses.length === 0" class="sheet__addr-state">
            地址加载中...
          </view>
          <view v-else-if="createState.addresses.length === 0" class="sheet__addr-state">
            <text>还没有收货地址，企业采购需要一个可配送的收货地址</text>
            <view class="sheet__addr-add" @tap="toAddressForm">
              去新增地址
            </view>
          </view>
          <scroll-view v-else scroll-y class="sheet__addr-list">
            <view
              v-for="item in createState.addresses"
              :key="item.id"
              class="sheet__addr"
              :class="{ 'sheet__addr--on': item.id === createState.addressId }"
              @tap="createState.addressId = item.id"
            >
              <view class="min-w-0 flex-1">
                <view class="sheet__addr-line">
                  <text class="sheet__addr-name">
                    {{ item.recipientName }}
                  </text>
                  <text class="sheet__addr-phone">
                    {{ item.telephone }}
                  </text>
                  <text v-if="item.isDefault === '1'" class="sheet__addr-default">
                    默认
                  </text>
                </view>
                <view class="sheet__addr-detail">
                  {{ item.provinceName }}{{ item.cityName }}{{ item.areaName }}{{ item.detailAddress }}
                </view>
              </view>
              <text
                class="i-carbon:checkmark-filled sheet__addr-check"
                :class="{ 'sheet__addr-check--on': item.id === createState.addressId }"
              />
            </view>
            <view class="sheet__addr-add" @tap="toAddressForm">
              + 新增地址
            </view>
          </scroll-view>
        </template>

        <view class="sheet__label">
          备注（选填）
        </view>
        <input
          v-model="createState.remark"
          class="sheet__input"
          placeholder="如：本次补给含甲板部需求"
          :maxlength="120"
        >
        <view class="sheet__actions">
          <button class="sheet__cancel !m-0" @tap="createState.visible = false">
            取消
          </button>
          <view
            class="sheet__ok"
            :class="{ 'sheet__ok--busy': createState.submitting }"
            @tap="submitCreate"
          >
            {{ createState.submitting ? '创建中...' : '创建' }}
          </view>
        </view>
      </view>
    </wd-popup>
  </view>
</template>

<style lang="scss" scoped>
.shared-cart-list {
  min-height: 100vh;
  padding-bottom: 40rpx;
  background: #f5f6f8;
}

/* ---------------------------------------------------------------- 船舶上下文条 */
// 品牌蓝渐变：与下面的白色单据卡形成明确分界——上半屏是「发起」，下半屏是「已有」
.hero {
  margin: 20rpx 24rpx 0;
  padding: 32rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #082e63, #0b63e5);
  box-shadow: 0 8rpx 24rpx rgb(11 99 229 / 22%);
}

.hero__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hero__vessel {
  overflow: hidden;
  font-size: 38rpx;
  font-weight: 700;
  color: #fff;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero__switch {
  flex: none;
  margin-left: 16rpx;
  padding: 6rpx 20rpx;
  border: 2rpx solid rgb(255 255 255 / 45%);
  border-radius: 999rpx;
  font-size: 22rpx;
  color: #fff;
}

.hero__location {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: rgb(255 255 255 / 82%);
}

.hero__icon {
  flex: none;
  margin-right: 8rpx;
  font-size: 26rpx;
}

.hero__cta {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 80rpx;
  margin-top: 28rpx;
  border-radius: 999rpx;
  background: #fff;
  font-size: 28rpx;
  font-weight: 700;
  color: #0a4da3;
}

.hero__cta-icon {
  margin-right: 8rpx;
  font-size: 32rpx;
}

/* ------------------------------------------------------------------ 分区标题 */
.section {
  margin-top: 32rpx;
}

.section__head {
  padding: 0 32rpx;
}

.section__title-row {
  display: flex;
  align-items: baseline;
}

.section__title {
  font-size: 30rpx;
  font-weight: 700;
  color: #172033;
}

.section__count {
  margin-left: 10rpx;
  font-size: 24rpx;
  color: #9aa4b2;
}

.section__hint {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #9aa4b2;
}

/* -------------------------------------------------------------------- 单据卡 */
.card {
  margin: 20rpx 24rpx 0;
  padding: 28rpx;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 2rpx 12rpx rgb(23 32 51 / 5%);
}

// 小程序端 :active 不生效，用 hover-class 提供按压反馈
.card--pressed {
  background: #f7f8fa;
}

.card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.card__vessel-wrap {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
}

.card__vessel {
  overflow: hidden;
  font-size: 32rpx;
  font-weight: 700;
  color: #172033;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__badge {
  flex: none;
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
  background: #eef4ff;
  font-size: 20rpx;
  color: #0b63e5;
}

/* 岸上企业单的类型角标：灰底与「我发起」的蓝底区分 */
.card__badge--shore {
  background: #f1f2f4;
  color: #5f6b7a;
}

.card__status {
  flex: none;
  margin-left: 16rpx;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
}

.card__location {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #5f6b7a;
}

.card__icon {
  flex: none;
  margin-right: 6rpx;
  font-size: 24rpx;
  color: #9aa4b2;
}

.card__deadline {
  display: flex;
  align-items: center;
  margin-top: 14rpx;
  font-size: 24rpx;
  color: #b45309;
}

.card__deadline--urgent {
  font-weight: 700;
  color: #c2410c;
}

.card__deadline--expired {
  color: #9aa4b2;
}

.card__deadline-point {
  margin-left: 4rpx;
  color: #b9c0ca;
}

.card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20rpx;
}

.card__meta {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  font-size: 22rpx;
  color: #9aa4b2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__action {
  display: flex;
  flex: none;
  align-items: center;
  margin-left: 16rpx;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  background: #eef4ff;
  font-size: 24rpx;
  color: #0b63e5;
}

.card__action-icon {
  margin-left: 2rpx;
  font-size: 24rpx;
}

.card__no {
  display: block;
  margin-top: 14rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid #f2f3f5;
  font-size: 20rpx;
  color: #b9c0ca;
}

/* -------------------------------------------------------------------- 空/异常态 */
.state {
  padding: 120rpx 64rpx;
  text-align: center;
}

.state__text {
  display: block;
  font-size: 27rpx;
  color: #7a8699;
}

.state__desc {
  display: block;
  margin-top: 12rpx;
  font-size: 23rpx;
  line-height: 1.6;
  color: #9aa4b2;
}

.state__retry {
  margin-top: 24rpx;
  font-size: 26rpx;
  color: #0b63e5;
}

.state__cta {
  height: 76rpx;
  margin: 32rpx 40rpx 0;
  border-radius: 999rpx;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
  box-shadow: 0 6rpx 16rpx rgb(255 77 46 / 30%);
  font-size: 28rpx;
  font-weight: 500;
  line-height: 76rpx;
  color: #fff;
}

/* -------------------------------------------------------------------- 创建弹层 */
.sheet {
  padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
}

.sheet__title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #172033;
}

.sheet__desc {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  line-height: 1.6;
  color: #7a8699;
}

.sheet__context {
  display: flex;
  align-items: center;
  margin-top: 20rpx;
  padding: 20rpx 24rpx;
  border-radius: 16rpx;
  background: #f5f7fa;
  font-size: 24rpx;
  color: #5f6b7a;
}

.sheet__context-icon {
  flex: none;
  margin-right: 8rpx;
  font-size: 26rpx;
  color: #0b63e5;
}

/* ------------------------------------------------------------ 车类型切换 */
.sheet__type {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
  margin-top: 24rpx;
}

.sheet__type-item {
  padding: 20rpx 24rpx;
  border: 2rpx solid #e5e8ee;
  border-radius: 16rpx;
  background: #fafbfc;
}

.sheet__type-item--on {
  border-color: #0b63e5;
  background: #eef4ff;
}

.sheet__type-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #172033;
}

.sheet__type-item--on .sheet__type-title {
  color: #0b63e5;
}

.sheet__type-desc {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #7a8699;
}

/* ---------------------------------------------------------- 岸上地址选择 */
.sheet__addr-state {
  margin-top: 12rpx;
  padding: 28rpx 24rpx;
  border-radius: 12rpx;
  background: #f5f7fa;
  font-size: 24rpx;
  color: #7a8699;
}

/* 新增地址入口：空态与列表底部共用 */
.sheet__addr-add {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #0b63e5;
}

.sheet__addr-list {
  max-height: 400rpx;
  margin-top: 12rpx;
}

.sheet__addr {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
  padding: 20rpx 24rpx;
  border: 2rpx solid #e5e8ee;
  border-radius: 12rpx;
  background: #fafbfc;
}

.sheet__addr--on {
  border-color: #0b63e5;
  background: #eef4ff;
}

.sheet__addr-line {
  display: flex;
  align-items: center;
  min-width: 0;
}

.sheet__addr-name {
  font-size: 26rpx;
  font-weight: 600;
  color: #172033;
}

.sheet__addr-phone {
  margin-left: 12rpx;
  font-size: 22rpx;
  color: #7a8699;
}

.sheet__addr-default {
  flex: none;
  margin-left: 12rpx;
  padding: 0 10rpx;
  border-radius: 6rpx;
  background: #eef4ff;
  font-size: 20rpx;
  color: #0b63e5;
}

.sheet__addr-detail {
  overflow: hidden;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #7a8699;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sheet__addr-check {
  flex: none;
  margin-left: 16rpx;
  font-size: 30rpx;
  color: transparent;
}

.sheet__addr-check--on {
  color: #0b63e5;
}

.sheet__label {
  margin-top: 24rpx;
  font-size: 24rpx;
  color: #5f6b7a;
}

.sheet__input {
  height: 80rpx;
  box-sizing: border-box;
  margin-top: 10rpx;
  padding: 0 24rpx;
  border-radius: 12rpx;
  background: #f5f6f8;
  font-size: 28rpx;
}

.sheet__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
  margin-top: 32rpx;
}

.sheet__cancel {
  height: 80rpx;
  font-size: 28rpx;
  line-height: 80rpx;
  color: #5f6b7a;
  background: #f5f6f8;
  border-radius: 999rpx;
}

// 与全站主 CTA 同款橙红渐变（购物车「去结算」、详情页「提交整船订单」）
.sheet__ok {
  height: 80rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
  box-shadow: 0 6rpx 16rpx rgb(255 77 46 / 30%);
  font-size: 28rpx;
  font-weight: 500;
  line-height: 80rpx;
  text-align: center;
  color: #fff;
}

.sheet__ok--busy {
  opacity: 0.6;
}
</style>
