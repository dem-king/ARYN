<script setup lang="ts">
import type { CouponCardData } from '@/components/coupon-card/index.vue'
import { onLoad } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getPage } from '@/api/promotion/couponInfo'
import { addObj } from '@/api/promotion/couponUser'
import { couponType, formatCouponDeadline } from '@/utils/coupon-display'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { useRequireLogin } from '@/composables/useRequireLogin'

const { handlePageScroll } = useFrostedNavbar()
const { ensureLoggedIn } = useRequireLogin()

definePage({
  name: 'coupon-list',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '领券中心',
  },
})

/** 券类型筛选值：'' 不传（全部）、1 满减、2 折扣 —— 与后端 coupon_type 同一枚举 */
type CouponFilter = '' | '1' | '2'

const { t } = useI18n()
const pagingRef = ref()
const router = useRouter()
const { show } = useGlobalToast()
const state = reactive<{
  list: any[]
  activeType: CouponFilter
  loading: boolean
}>({
  list: [],
  activeType: '',
  loading: true,
})

onLoad(async () => {
  nextTick(() => {
    pagingRef.value?.reload()
  })
})

/**
 * 分段筛选：值即服务端查询条件 couponType。
 *
 * 不在本地过滤 —— 领券中心是分页列表，本地过滤会把「本页没有」误显示成「没有」；
 * 文案复用结算页券类型词条，四处券面口径一致。
 */
const segments = computed<{ key: CouponFilter, label: string }[]>(() => [
  { key: '', label: t('promotion.coupon.list.tabs.all') },
  { key: '1', label: t('checkout.coupon.typeAmount') },
  { key: '2', label: t('checkout.coupon.typeDiscount') },
])

/** 切换券类型：条件变了必须从第 1 页重拉，否则会把上一筛选的旧页拼进来 */
function switchType(key: CouponFilter) {
  if (state.activeType === key)
    return
  state.activeType = key
  pagingRef.value?.reload()
}

/**
 * 列表请求序号：快速连点分段时，旧筛选的响应可能晚于新筛选返回。
 * 只认最后一次请求，避免把上一筛选的券拼进当前列表（z-paging 不按 query 来源丢弃响应）。
 */
let querySeq = 0

async function queryList(pageNo: number, pageSize: number) {
  const seq = ++querySeq
  // 首屏 / 切筛选时列表为空，用骨架卡占位；翻页时已有卡片，不闪骨架
  state.loading = true
  try {
    const response = await getPage({
      current: pageNo,
      size: pageSize,
      desc: 'create_time',
      // 空串表示「全部」，传空串会被后端当作一个无效筛选值，因此干脆不传
      ...(state.activeType ? { couponType: state.activeType } : {}),
    })
    // 期间用户又切了筛选：本次响应已过期，交给最新那次请求收尾
    if (seq !== querySeq)
      return
    pagingRef.value?.complete(response.records)
  }
  finally {
    if (seq === querySeq)
      state.loading = false
  }
}

/**
 * 页头副标题里的「最高立省」：取当前列表满减券的最大面额。
 *
 * 折扣券没有立减金额（面额区展示的是「折」），筛选到折扣券或列表为空时
 * 落到兜底文案，避免显示「最高立省 ¥0」这种自相矛盾的页头。
 */
const heroSubtitle = computed(() => {
  const amounts = state.list
    .filter(item => couponType(item) === '1')
    .map(item => Number(item.amount))
    .filter(amount => Number.isFinite(amount) && amount > 0)
  if (!amounts.length)
    return t('promotion.coupon.list.heroSubtitleFallback')
  const max = Math.max(...amounts)
  return t('promotion.coupon.list.heroSubtitle', { amount: String(Number(max.toFixed(2))) })
})

/** 我的券包：已领券的出口，放页头右上角，不额外占首屏高度 */
function goWallet() {
  router.push({ name: 'coupon-user' })
}

/**
 * 空态主按钮：回首页商品流 —— 没有可领券时不能让用户卡在空页面。
 *
 * 首页是 tabBar 页，必须走 pushTab（底层 switchTab）：对 tabBar 页用 push
 * 会静默失败，点了没反应（与 hr-navbar 首页入口同一口径）。
 */
function goHome() {
  router.pushTab({ name: 'home' })
}

/** 是否已达领取上限：服务端按用户返回已领取次数 */
function isReceived(item: any) {
  return Number(item.userReceiveCount) > 0
}

/**
 * 已领取的券按钮是「去使用」，仍然可点（原来就是这样跳商品列表）。
 * 注意不能把它置灰：置灰的「去使用」等于告诉用户领了也用不了。
 */
function actionText(item: any) {
  return isReceived(item)
    ? t('promotion.coupon.user.use')
    : t('promotion.coupon.list.receive')
}

/** 领券截止时间当作状态带文案；没有截止时间则整条不显示 */
function bandText(item: any) {
  return formatCouponDeadline(item.receiveEndedAt)
}

/** 未领取则领取，已领取则去挑商品 */
async function handleAction(coupon: CouponCardData) {
  const item = state.list.find(entry => entry.id === coupon.id)
  if (item && isReceived(item)) {
    router.push({ name: 'goods-list' })
    return
  }
  // 游客领券：记录回跳并打开登录页，不发出需登录的领券请求
  if (!ensureLoggedIn())
    return
  try {
    await addObj({ couponId: coupon.id })
    show(t('promotion.coupon.list.receiveSuccess'))
    // 本地累加已领次数，卡片即时切到「去使用」，不必重拉列表
    if (item)
      item.userReceiveCount = Number(item.userReceiveCount || 0) + 1
  }
  catch (err: any) {
    // 服务端失败原因已由请求层统一提示；这里兜底补一条，避免静默失败
    show(err?.message || t('promotion.coupon.list.receiveFailed'))
  }
}
</script>

<template>
  <hr-navbar :title="t('promotion.coupon.list.title')" />
  <!--
    class 挂在 z-paging 根节点上：整页只有这一块滚动容器，
    采集/断言脚本以 .cc-page 作为页面锚点（见 outputs/coupon-center-redesign/capture.mjs）。
  -->
  <z-paging
    ref="pagingRef"
    v-model="state.list"
    class="cc-page"
    :auto="false"
    @query="queryList"
    @scroll="handlePageScroll"
  >
    <!-- 顶栏覆盖层等高占位：列表整屏滚动，首屏内容从顶栏下沿开始 -->
    <hr-navbar-space />

    <view class="cc-body">
      <!--
        品牌渐变页头：先把「这里是领券的」讲清楚，再进列表。
        「我的券包」放右上角，避免再占一行入口高度 —— 首屏利用率是本页改版的核心指标。
      -->
      <view class="cc-hero">
        <view class="cc-hero__top">
          <view class="cc-hero__badge">
            {{ t('promotion.coupon.list.badge') }}
          </view>
          <view class="cc-hero__wallet" @click="goWallet">
            <text class="cc-hero__wallet-text">
              {{ t('promotion.coupon.list.wallet') }}
            </text>
            <wd-icon name="chevron-right" size="20rpx" color="rgba(255, 255, 255, .9)" />
          </view>
        </view>
        <view class="cc-hero__title">
          {{ t('promotion.coupon.list.heroTitle') }}
        </view>
        <view class="cc-hero__sub">
          {{ heroSubtitle }}
        </view>
      </view>

      <!-- 分段筛选：券类型是互斥的筛选条件，分段控件比 Tab 更贴近「筛选」语义 -->
      <view class="cc-segments">
        <view
          v-for="segment in segments"
          :key="segment.key || 'all'"
          class="cc-segment"
          :class="{ 'cc-segment--active': state.activeType === segment.key }"
          @click="switchType(segment.key)"
        >
          {{ segment.label }}
        </view>
      </view>

      <!-- 首屏骨架：只在还没有任何卡片时占位，翻页/下拉刷新已有数据不闪骨架 -->
      <view v-if="state.loading && state.list.length === 0" class="cc-list">
        <view v-for="n in 3" :key="`cc-skeleton-${n}`" class="cc-skeleton" />
      </view>
      <view v-else class="cc-list">
        <coupon-card
          v-for="item in state.list"
          :key="item.id"
          :coupon="item"
          :band="bandText(item)"
          :action-text="actionText(item)"
          :action-variant="isReceived(item) ? 'ghost' : 'primary'"
          @action="handleAction"
        />
      </view>
    </view>

    <!--
      空态：z-paging 只在「首屏查询完成且列表为空」时渲染本插槽，
      因此加载中不会闪空态，切筛选后也不会出现只剩页头的空白页。
    -->
    <template #empty>
      <hall-state
        art="discount"
        :title="t('promotion.coupon.list.emptyTitle')"
        :desc="t('promotion.coupon.list.emptyDesc')"
        :action-text="t('promotion.common.goHome')"
        @action="goHome"
      />
    </template>
  </z-paging>
</template>

<style lang="scss" scoped>
@import '@/styles/coupon-ticket.scss';

.cc-body {
  padding: 24rpx 24rpx 0;
}

/* ---------- 页头 ---------- */
.cc-hero {
  position: relative;
  overflow: hidden;
  padding: 28rpx 32rpx;
  color: #fff;
  border-radius: 32rpx;
  box-shadow: 0 16rpx 40rpx rgba(255, 85, 0, 0.18);
  @include coupon-hero-bg;

  /*
    两枚半透明白圆当「光斑」：让整块渐变从纯色条变成有体积的卡片。
    用伪元素而不是真实节点，页面结构里就只剩语义内容；overflow:hidden 裁掉外溢部分。
  */
  &::before {
    position: absolute;
    top: -74rpx;
    right: -58rpx;
    width: 168rpx;
    height: 168rpx;
    content: '';
    background: rgba(255, 255, 255, 0.13);
    border-radius: 50%;
  }

  &::after {
    position: absolute;
    right: 44rpx;
    bottom: -66rpx;
    width: 96rpx;
    height: 96rpx;
    content: '';
    background: rgba(255, 255, 255, 0.09);
    border-radius: 50%;
  }
}

.cc-hero__top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cc-hero__badge {
  display: flex;
  height: 40rpx;
  padding: 0 18rpx;
  align-items: center;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.22);
  font-size: 20rpx;
  font-weight: 600;
  letter-spacing: 0.5rpx;
}

/* 「我的券包」入口：渐变底上再叠一层半透明白，是页头里唯一可点的出口 */
.cc-hero__wallet {
  position: relative;
  display: flex;
  height: 56rpx;
  padding: 0 20rpx;
  gap: 4rpx;
  align-items: center;
  border: 1rpx solid rgba(255, 255, 255, 0.26);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.18);

  /* 视觉胶囊 56rpx，热区扩到 88rpx（44px）：小程序上小胶囊很常见，
     但不能只按视觉高度接收点击（伪元素向外 16rpx 仍算在元素内） */
  &::after {
    position: absolute;
    top: -16rpx;
    right: -12rpx;
    bottom: -16rpx;
    left: -12rpx;
    content: '';
  }
}

.cc-hero__wallet-text {
  font-size: 20rpx;
  font-weight: 600;
  line-height: 1;
}

.cc-hero__title {
  position: relative;
  z-index: 1;
  margin-top: 18rpx;
  font-size: 40rpx;
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: 1rpx;
}

.cc-hero__sub {
  position: relative;
  z-index: 1;
  margin-top: 10rpx;
  color: rgba(255, 255, 255, 0.9);
  font-size: 22rpx;
  line-height: 1.4;
}

/* ---------- 分段筛选 ---------- */
.cc-segments {
  display: flex;
  margin-top: 24rpx;
  padding: 6rpx;
  gap: 4rpx;
  background: $coupon-surface;
  border-radius: 999rpx;
  box-shadow: $coupon-card-shadow;
}

.cc-segment {
  display: flex;
  flex: 1;
  height: 64rpx;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  color: $coupon-text-2;
  font-size: 24rpx;

  &--active {
    background: $coupon-brand;
    box-shadow: 0 8rpx 20rpx rgba(255, 85, 0, 0.22);
    color: #fff;
    font-weight: 600;
  }
}

/* ---------- 券卡列表 ---------- */
.cc-list {
  display: flex;
  margin-top: 24rpx;
  flex-direction: column;
  gap: 20rpx;
}

/*
  骨架卡：高度/圆角/投影与真实券卡同形，数据到位后不产生高度跳变。
  使用说明改为完整换行后，带两行说明的券卡约 208rpx，骨架按常见形态对齐。
*/
.cc-skeleton {
  height: 208rpx;
  border-radius: $coupon-card-radius;
  background: $coupon-surface;
  box-shadow: $coupon-card-shadow;
  animation: cc-pulse 1.4s ease-in-out infinite;
}

@keyframes cc-pulse {
  0%,
  100% {
    opacity: 0.55;
  }

  50% {
    opacity: 1;
  }
}
</style>
