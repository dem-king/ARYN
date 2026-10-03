<script setup lang="ts">
import type { SharedCart, SharedCartCreatePayload } from '@/api/order/sharedCart'
/**
 * 共享购物车列表：我发起或我被邀请的全部购物车。
 *
 * 页面要回答的问题只有一个：「现在有没有还需要我管的单」。因此：
 * 1. 顶部是当前船舶上下文 + 发起入口，回答「在哪条船上发起」；
 * 2. 列表按「进行中 / 历史」两段分区，进行中的排在前面且按截止时间升序，
 *    让最紧急的单浮到最上面；
 * 3. 每张卡片给出「这单到哪一步了」（进度 / 倒计时）与「点进去能做什么」
 *    （动词随状态与权限变化），而不是四张卡都写同一个「查看」。
 *
 * 创建入口仅在当前已绑定船舶+靠港计划时可用——共享购物车创建时即绑定
 * 船舶与靠港计划且不可变更，缺少上下文无法创建。
 */
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'

import { computed, reactive, ref } from 'vue'
import { createSharedCart, getMySharedCarts } from '@/api/order/sharedCart'
import hrNavbar from '@/components/hr-navbar/index.vue'
import ShipContextPicker from '@/components/ship-context-picker/index.vue'
import { useCountdownTicker } from '@/composables/useCountdown'
import { useAuthStore } from '@/store/authStore'
import { useShipContextStore } from '@/store/shipContextStore'
import { buildReplenishSummaryView } from '@/utils/replenish-progress'
import {
  cartActionLabel,
  cartDeadlineView,
  cartStatusLabel,
  cartStatusTheme,
  groupSharedCarts,
  isCartCollecting,
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

const authStore = useAuthStore()
const shipContextStore = useShipContextStore()

/** 每秒推进的时间基准：卡片倒计时与紧急分档都要自己走，页面上没有别的定时器 */
const { now: tickNow } = useCountdownTicker()

const loading = ref(false)
const loadFailed = ref(false)
const records = ref<SharedCart[]>([])
/** 船舶与靠港选择器：创建共享购物车必须先有船舶+靠港上下文 */
const shipPickerVisible = ref(false)

/** 创建表单 */
const createState = reactive({
  visible: false,
  submitting: false,
  /** 收集窗口（小时）：24 / 48 / 0 表示不限期 */
  remark: '',
})

const canCreate = computed(() => authStore.isLoggedIn && shipContextStore.hasVesselContext)

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
  progress: ReturnType<typeof buildReplenishSummaryView>
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
      progress: buildReplenishSummaryView(cart.progress),
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

function openCreate() {
  if (!authStore.isLoggedIn) {
    goLogin()
    return
  }
  // 缺少船舶/靠港上下文时直接给出选择入口，而不是一句无法执行的提示
  if (!canCreate.value) {
    shipPickerVisible.value = true
    return
  }
  createState.remark = ''
  createState.visible = true
}

function submitCreate() {
  if (createState.submitting)
    return
  createState.submitting = true
  const payload: SharedCartCreatePayload = {
    vesselId: shipContextStore.vesselId,
    vesselCallId: shipContextStore.vesselCallId,
    remark: createState.remark || undefined,
  }
  createSharedCart(payload)
    .then((cart) => {
      createState.visible = false
      // 同一船舶已有进行中的采购时，服务端复用该购物车而非新建，
      // 需明确告知用户是被并入，避免误以为刚创建了一个新的。
      uni.showToast({
        title: cart?.adoptedExisting ? '该船已在进行中，已为你打开' : '已创建',
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
      船舶上下文条：回答「我在哪条船上发起」。这不是一张独立卡片——
      它只承载当前上下文与一个创建动作，用品牌渐变把它和下面的单据列表区分开，
      用户一眼能看出「上面这块是发起入口，下面这些是已有单据」。
    -->
    <view class="hero">
      <view class="hero__head">
        <text class="hero__vessel">
          {{ shipContextStore.vesselName || '我的船舶' }}
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
      <view v-else class="hero__location" @tap="shipPickerVisible = true">
        <text class="i-carbon:warning-alt hero__icon" />
        <text>先选择船舶和靠港计划，才能发起共享购物车</text>
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
              {{ cart.vesselName || '船舶信息加载中' }}
            </text>
            <!-- 自己发起的单标一下，同船多人时才知道这单该找谁 -->
            <text v-if="cart.viewerIsOwner" class="card__badge">
              我发起
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
          <text>{{ cart.portName || '港口待定' }} {{ cart.berth }}</text>
        </view>

        <!--
          进度条：只对排过计划的单画。没排计划时画一条空槽再配「—」会被读成
          「加载失败」，因此那种情况只出一行文字。
        -->
        <view v-if="cardViews[cart.id]?.progress.text" class="card__progress">
          <view v-if="cardViews[cart.id]?.progress.hasPlan" class="card__track">
            <view
              class="card__bar"
              :style="`width:${cardViews[cart.id]?.progress.percent}%`"
            />
          </view>
          <text class="card__progress-text">
            {{ cardViews[cart.id]?.progress.text }}
          </text>
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
        发起一单与同船成员合并采购，由确认人统一提交整船订单
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
          创建后 24 小时内有效，超时自动关闭。同船成员可分别加购，由确认人统一提交整船订单。
        </text>
        <view class="sheet__context">
          <text class="i-carbon:location sheet__context-icon" />
          <text>{{ shipContextStore.vesselName }} · {{ shipContextStore.portName }} {{ shipContextStore.berth }}</text>
        </view>
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

.card__progress {
  margin-top: 18rpx;
}

.card__track {
  overflow: hidden;
  height: 10rpx;
  border-radius: 999rpx;
  background: #eef0f3;
}

.card__bar {
  height: 10rpx;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #ffb25c, #f2741d);
  transition: width 0.3s ease;
}

.card__progress-text {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #7a8699;
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
