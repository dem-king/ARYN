<script setup lang="ts">
import type { AppSeckillGoodsVO, AppSeckillVO } from '@/api/promotion'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { computed, nextTick, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { requestSubscribeMessage } from '@/api/delivery'
import {
  getSeckillSessions,
  getSessionGoods,
  subscribeSeckill,
  unsubscribeSeckill,
} from '@/api/promotion'
import { shouldShowSubtitle } from '@/components/diy/subtitle-display'
import { useAuthStore } from '@/store/authStore'
import { normalizeStock, resolveSeckillProgress } from '@/utils/seckill-progress'
import { navigateToUrl } from '@/utils/tab-bar'

definePage({
  name: 'seckill',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '限时秒杀',
  },
})

const { t } = useI18n()
const authStore = useAuthStore()

/** 空态「去首页逛逛」目标：与折扣会场同源 */
const HOME_PATH = '/pages/home/index'
/** 价差占原价比例超过此值时，图上角标不再喊具体省额，改「限量特价」 */
const DEEP_DISCOUNT_RATIO = 0.7

/** 场次列表 */
const sessionList = ref<AppSeckillVO[]>([])
/** 当前选中场次在 sessionList 中的索引（Tab 过滤掉已结束场次后仍可定位） */
const currentSessionIndex = ref(0)
/** 当前场次商品列表 */
const goodsList = ref<AppSeckillGoodsVO[]>([])
/** 是否还有更多商品（后端一次性下发全部商品，保留模板契约） */
const noMore = ref(true)
/** 首屏骨架：仅在还没有场次数据时展示 */
const loading = ref(true)
/** 商品区骨架：切场次时占位，避免上一场商品短暂闪现 */
const goodsLoading = ref(false)
/** 下拉刷新状态：驱动 scroll-view 的 refresher 收起 */
const refreshing = ref(false)
/** 场次轴是否已吸顶（吸顶后加阴影，与内容分层） */
const axisStuck = ref(false)
/** 悬浮回顶：滚动超过约 2 屏才出现 */
const showBackTop = ref(false)
/** 回顶锚点（scroll-into-view 比改 scroll-top 可靠：后者在用户手动滚动后不重置） */
const scrollInto = ref('')
/** 本场在抢人数：场次内 SKU 当前仍在购物车里的去重人数（后端只读统计，失败为空） */
const cartUserCount = ref<number | null>(null)
/** 已触发过「倒计时结束自动刷新」的场次：后端状态未及时翻转时不反复重载 */
const refreshedSessionIds = new Set<string>()
/** 商品请求序号：快速切场次时丢弃过期响应，防止串场 */
let goodsRequestSeq = 0

/** 倒计时数值（深色分格用）；天为 0 时不出天格 */
const countdown = reactive({
  days: 0,
  hours: 0,
  minutes: 0,
  seconds: 0,
  finished: false,
})
let countdownTimer: ReturnType<typeof setInterval> | null = null

/** 当前场次 */
const currentSession = computed<AppSeckillVO | null>(() => {
  return sessionList.value[currentSessionIndex.value] || null
})

/** 「N 人在抢」文案：拿不到或为 0 时不展示（不伪造氛围） */
const cartUserText = computed(() => {
  const count = cartUserCount.value ?? 0
  return count > 0 ? t('promotion.seckill.live', { count }) : ''
})

/** 场次轴条目：已结束场次不进轴（进行中 → 未开始，顺序沿用后端下发） */
const tabSessions = computed(() => {
  return sessionList.value
    .map((session, index) => ({ index, session }))
    .filter(item => item.session.status !== 2)
})

/** 倒计时标签：进行中看结束、未开始看开始（明日场次标明「明日」） */
const countdownLabel = computed(() => {
  const session = currentSession.value
  if (session?.status === 1)
    return t('promotion.seckill.countdownEnd')
  if (Number(session?.dayOffset) === 1)
    return t('promotion.seckill.countdownStartTomorrow')
  return t('promotion.seckill.countdownStart')
})

/** 已订阅：后端随场次列表回填，切换场次后随对象更新 */
const subscribed = computed(() => !!currentSession.value?.subscribed)

/** 未开始且后端开放提醒时，hero 区出现「开抢提醒」 */
const showReminderButton = computed(() => {
  const session = currentSession.value
  return !!session && session.status === 0 && session.reminderEnabled === true
})

/** 已结束场次不渲染倒计时（轴里选不到，仅作兜底） */
const showCountdown = computed(() => {
  return !!currentSession.value && currentSession.value.status !== 2
})

/** 未开始场次：整场尚未开抢，进度不成立 */
const isPendingSession = computed(() => currentSession.value?.status === 0)

/** 空态文案：没有可展示场次 vs 当前场次暂未上架商品 */
const noSession = computed(() => tabSessions.value.length === 0)

onLoad(async () => {
  await loadSessions()
})

onUnload(() => {
  stopCountdown()
})

/** 加载场次列表：优先落到进行中，其次未开始；全部结束则走空态 */
async function loadSessions() {
  if (sessionList.value.length === 0)
    loading.value = true
  try {
    const data = await getSeckillSessions()
    sessionList.value = data || []
    // 默认选中第一个进行中场次，没有进行中则选第一个未开始场次
    const activeIndex = sessionList.value.findIndex(session => session.status === 1)
    const pendingIndex = sessionList.value.findIndex(session => session.status === 0)
    const nextIndex = activeIndex >= 0 ? activeIndex : pendingIndex
    if (nextIndex >= 0) {
      await selectSession(nextIndex)
    }
    else {
      currentSessionIndex.value = 0
      cartUserCount.value = null
      goodsList.value = []
      noMore.value = true
      stopCountdown()
      countdown.finished = true
    }
  }
  catch {
    uni.showToast({ title: t('promotion.common.loadFailed'), icon: 'none' })
  }
  finally {
    loading.value = false
  }
}

/** 选择场次：拉取该场商品并重启倒计时 */
async function selectSession(index: number) {
  const session = sessionList.value[index]
  if (!session)
    return
  currentSessionIndex.value = index
  cartUserCount.value = session.cartUserCount ?? null
  goodsList.value = []
  noMore.value = true
  goodsLoading.value = true
  const seq = ++goodsRequestSeq
  try {
    await loadSessionGoods(session.sessionId, seq)
  }
  finally {
    if (seq === goodsRequestSeq)
      goodsLoading.value = false
  }
  if (seq === goodsRequestSeq)
    startCountdown()
}

/** 加载场次商品（后端一次性返回全部商品，无分页） */
async function loadSessionGoods(sessionId: string, seq: number) {
  try {
    const session = await getSessionGoods(sessionId)
    if (seq !== goodsRequestSeq)
      return
    goodsList.value = session.goodsList || []
    cartUserCount.value = session.cartUserCount ?? cartUserCount.value
  }
  catch {
    if (seq === goodsRequestSeq)
      uni.showToast({ title: t('promotion.seckill.goodsLoadFailed'), icon: 'none' })
  }
}

/** 下拉刷新：重取场次并刷新当前场商品 */
async function onRefresh() {
  refreshing.value = true
  try {
    await loadSessions()
  }
  finally {
    refreshing.value = false
  }
}

/** 上拉加载更多（后端无分页，保留空实现以兼容模板事件） */
async function onLoadMore() {
  // 后端一次性返回全部商品，无需分页加载
}

/** 页面滚动：驱动场次轴阴影与悬浮回顶的显隐（阈值按 px，约 2 屏） */
function onPageScroll(event: { detail: { scrollTop: number } }) {
  const top = event?.detail?.scrollTop ?? 0
  axisStuck.value = top > 16
  showBackTop.value = top > 600
}

/** 回顶：走 scroll-into-view 锚点，避免 scroll-top 在手动滚动后不生效 */
function backTop() {
  scrollInto.value = ''
  nextTick(() => {
    scrollInto.value = 'seckill-top'
  })
}

/** 启动倒计时 */
function startCountdown() {
  stopCountdown()
  updateCountdown()
  countdownTimer = setInterval(updateCountdown, 1000)
}

/** 停止倒计时 */
function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

/**
 * 后端时间串兼容 `yyyy-MM-dd HH:mm:ss`：
 * iOS 部分内核直接 Date.parse 会得到 NaN，退化成 ISO 形式再解析。
 */
function parseTime(value: string) {
  if (!value)
    return Number.NaN
  const direct = Date.parse(value)
  if (!Number.isNaN(direct))
    return direct
  return Date.parse(value.replace(' ', 'T'))
}

/** 每秒推进倒计时；归零后自动刷新场次（同一场次只触发一次，避免服务端状态滞后时死循环） */
function updateCountdown() {
  const session = currentSession.value
  if (!session || session.status === 2) {
    countdown.finished = true
    return
  }
  // 进行中算距结束时间，未开始算距开始时间
  const targetTime = session.status === 1 ? session.endTime : session.startTime
  const target = parseTime(targetTime)
  if (Number.isNaN(target)) {
    countdown.finished = true
    return
  }
  const diff = target - Date.now()
  if (diff <= 0) {
    countdown.days = 0
    countdown.hours = 0
    countdown.minutes = 0
    countdown.seconds = 0
    countdown.finished = true
    stopCountdown()
    if (session.sessionId && !refreshedSessionIds.has(session.sessionId)) {
      refreshedSessionIds.add(session.sessionId)
      void loadSessions()
    }
    return
  }
  const totalSeconds = Math.floor(diff / 1000)
  // 天单独一格：只有跨天场次才出现（盒马/京东同款口径）
  countdown.days = Math.floor(totalSeconds / 86400)
  countdown.hours = Math.floor((totalSeconds % 86400) / 3600)
  countdown.minutes = Math.floor((totalSeconds % 3600) / 60)
  countdown.seconds = totalSeconds % 60
  countdown.finished = false
}

/** 两位补零（倒计时分格） */
function pad(value: number) {
  return String(Math.max(0, value)).padStart(2, '0')
}

/** 场次起始整点「HH:00」；startTime 缺失/无法解析时返回空串 */
function startHourLabel(session: AppSeckillVO) {
  const start = parseTime(session.startTime)
  if (Number.isNaN(start))
    return ''
  return `${pad(new Date(start).getHours())}:00`
}

/**
 * 场次轴文案：进行中「10:00 抢购中」、未开始「14:00 开抢」。
 * 时间解析失败时退化成纯状态词，绝不回落后端 sessionName（
 * 「午间场 / 明日晚场20点」这类运营命名与「HH:00」混排会让整条轴失去节奏）。
 */
function sessionAxisLabel(session: AppSeckillVO) {
  const hour = startHourLabel(session)
  if (session.status === 1)
    return hour ? t('promotion.seckill.axisLive', { hour }) : t('promotion.seckill.axisLiveFallback')
  if (Number(session.dayOffset) === 1) {
    return hour
      ? t('promotion.seckill.axisTomorrowStart', { hour })
      : t('promotion.seckill.axisStartFallback')
  }
  return hour ? t('promotion.seckill.axisStart', { hour }) : t('promotion.seckill.axisStartFallback')
}

/**
 * 立省角标：价差 > 0 才出现。
 * 价差占原价超过 70%（如 ¥0.01 对 ¥19.90）时不再喊具体省额 —— 该场景下
 * 「立省¥19.89」与巨石价、划线价互相抢，改「限量特价」把「省」让给价格表达。
 */
function offBadgeText(goods: AppSeckillGoodsVO) {
  const diff = Number(goods.originalPrice) - Number(goods.seckillPrice)
  if (!Number.isFinite(diff) || diff <= 0)
    return ''
  const original = Number(goods.originalPrice)
  if (original > 0 && diff / original > DEEP_DISCOUNT_RATIO)
    return t('promotion.seckill.limitedDeal')
  return t('promotion.seckill.saveAmount', { amount: diff.toFixed(2) })
}

/** 划线原价：仅原价大于到手价时展示 */
function hasOriginalPrice(goods: AppSeckillGoodsVO) {
  return Number(goods.originalPrice) > Number(goods.seckillPrice)
}

/** 金额统一两位小数 */
function formatPrice(value: number) {
  const price = Number(value)
  return Number.isFinite(price) ? price.toFixed(2) : '0.00'
}

/**
 * 已抢进度：轨道（h12）+ 文案双出。
 * 判定规则集中在 utils/seckill-progress —— 会场页、首页秒杀楼层、管理端预览
 * 三处必须同一口径，这里只取长文案（会场卡片够宽）。
 */
function stockInfo(goods: AppSeckillGoodsVO) {
  return resolveSeckillProgress(goods.seckillStock, goods.remainingStock)
}

/** 未开始场次：不出进度轨道，只出「限量 N 件」文字 */
function pendingStockText(goods: AppSeckillGoodsVO) {
  const stock = normalizeStock(goods.seckillStock)
  if (!Number.isFinite(stock) || stock <= 0)
    return ''
  return t('promotion.common.limitedStock', { count: stock })
}

/**
 * 是否渲染已抢轨道：未开始场次整场不成立；进行中但一件没卖出（pct=0）时
 * 轨道长度为 0，渲染出来只是一条空灰条 —— 此时只出「限量 N 件」文字，
 * 避免每张卡底部挂一条无进度语义的灰线。
 */
function showStockTrack(goods: AppSeckillGoodsVO) {
  if (isPendingSession.value)
    return false
  return (stockInfo(goods)?.pct ?? 0) > 0
}

/** 进度/限量文案：未开始只报限量，进行中报已抢进度 */
function stockText(goods: AppSeckillGoodsVO) {
  if (isPendingSession.value)
    return pendingStockText(goods)
  return stockInfo(goods)?.fullText ?? ''
}

/**
 * 文案强度：只有「快抢完 / 已抢光」用主色，常规进度与限量数走辅助灰。
 * 一卡主色密度从 4 处（角标/价格/CTA/进度）压到 3 处，进度条本身已表达紧迫度。
 */
function stockTone(goods: AppSeckillGoodsVO) {
  if (isPendingSession.value)
    return 'is-muted'
  const info = stockInfo(goods)
  if (!info)
    return 'is-muted'
  if (info.soldOut)
    return 'is-out'
  return info.low ? 'is-urgent' : 'is-muted'
}

/** 是否已抢完（库存未知不判售罄，仍允许进详情） */
function isSoldOut(goods: AppSeckillGoodsVO) {
  const remaining = normalizeStock(goods.remainingStock)
  return Number.isFinite(remaining) && remaining <= 0
}

/**
 * CTA 文案：进行中「抢」、未开始「14:00 开抢」、售罄「已抢光」。
 * 未开始用起始整点而不是「即将开始」，让用户知道几点回来。
 */
function ctaText(goods: AppSeckillGoodsVO) {
  const session = currentSession.value
  if (!session)
    return t('promotion.seckill.unavailable')
  if (session.status === 0) {
    const hour = startHourLabel(session)
    if (Number(session.dayOffset) === 1) {
      return hour
        ? t('promotion.seckill.ctaTomorrow', { hour })
        : t('promotion.seckill.comingSoon')
    }
    return hour
      ? t('promotion.seckill.axisStart', { hour })
      : t('promotion.seckill.comingSoon')
  }
  if (session.status === 2)
    return t('promotion.seckill.ended')
  if (isSoldOut(goods))
    return t('promotion.seckill.soldOut')
  return t('promotion.seckill.grab')
}

/** CTA 是否可点：仅进行中且未售罄 */
function canBuy(goods: AppSeckillGoodsVO) {
  const session = currentSession.value
  if (!session || session.status !== 1)
    return false
  return !isSoldOut(goods)
}

/**
 * 抢购：跳商品详情加购，由服务端在下单时按秒杀价结算。
 * D1 已拍板：CTA 文案改「抢」，但下单动线保持「进详情加购」不变。
 */
function handleBuy(goods: AppSeckillGoodsVO) {
  if (!canBuy(goods))
    return
  uni.navigateTo({
    url: `/sub-pages/product/goods-detail/index?id=${goods.spuId}&skuId=${goods.skuId}&from=seckill`,
  })
}

/** 跳转商品详情（整卡可点，保留原有无 from 参数的行为） */
function goGoodsDetail(goods: AppSeckillGoodsVO) {
  uni.navigateTo({
    url: `/sub-pages/product/goods-detail/index?id=${goods.spuId}`,
  })
}

/**
 * 开抢提醒：先请求微信一次性订阅授权，再把订阅落到服务端。
 * 微信订阅消息每次授权只能发送一条，因此按场次逐次订阅。
 */
async function handleRemind() {
  const session = currentSession.value
  if (!session || session.status !== 0)
    return
  if (!authStore.isLoggedIn) {
    uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  if (session.subscribed) {
    try {
      await unsubscribeSeckill(session.sessionId)
      session.subscribed = false
      uni.showToast({ title: t('promotion.seckill.remindCancelled'), icon: 'none' })
    }
    catch {
      uni.showToast({ title: t('promotion.seckill.remindFailed'), icon: 'none' })
    }
    return
  }
  if (!session.reminderEnabled || !session.reminderTemplateId) {
    uni.showToast({ title: t('promotion.seckill.remindUnavailable'), icon: 'none' })
    return
  }
  try {
    const result = await requestSubscribeMessage([session.reminderTemplateId])
    if (!result || Object.keys(result).length === 0) {
      uni.showToast({ title: t('promotion.seckill.remindNeedWechat'), icon: 'none' })
      return
    }
    if (result[session.reminderTemplateId] !== 'accept') {
      uni.showToast({ title: t('promotion.seckill.remindRejected'), icon: 'none' })
      return
    }
    await subscribeSeckill(session.sessionId)
    session.subscribed = true
    uni.showToast({ title: t('promotion.seckill.remindSuccess'), icon: 'none' })
  }
  catch {
    uni.showToast({ title: t('promotion.seckill.remindFailed'), icon: 'none' })
  }
}

/** 活动规则页（独立静态页，文案由运营/产品维护在页面内） */
function goRulePage() {
  uni.navigateTo({ url: '/pages/promotion/seckill-rule' })
}

/** 空态主按钮：没有场次时回首页商品流 */
function goHome() {
  navigateToUrl(HOME_PATH)
}

/** 当前场无商品：链到其它可展示场次（有则切场，只有一场则隐藏文字链） */
const otherSessionIndex = computed(() => {
  return tabSessions.value.find(item => item.index !== currentSessionIndex.value)?.index ?? -1
})

function goOtherSession() {
  if (otherSessionIndex.value >= 0)
    void selectSession(otherSessionIndex.value)
}
</script>

<template>
  <view class="seckill-page">
    <!--
      顶栏回到全站业务页口径：白底深字、标题即活动名。
      2026-11-01：原「实心主色导航 + 满幅主色信息条」是全站唯一不切磨砂、
      且同一主色在一屏里出现三种画法的页面，与折扣会场/购物车/商详等页面割裂；
      「活动规则」也因小程序胶囊避让只剩约 40px，和居中标题粘连。
      现在主色只作为一张信息卡出现，规则入口收进卡内，导航右槽不再放文字。
    -->
    <hr-navbar :title="t('promotion.seckill.title')" />

    <!-- 首屏骨架：信息卡 + 场次轴 + 2 行商品（形状与真实首屏同高，加载完不跳一屏） -->
    <template v-if="loading">
      <view class="hero-skeleton" />
      <view class="panel-skeleton">
        <view class="sk-tabs">
          <view v-for="n in 3" :key="`sk-tab-${n}`" class="sk-tab" />
        </view>
      </view>
      <view class="goods-list">
        <view v-for="n in 2" :key="`sk-card-${n}`" class="card-skeleton">
          <view class="sk-media" />
          <view class="sk-lines">
            <view class="sk-line sk-line--lg" />
            <view class="sk-line" />
            <view class="sk-line sk-line--sm" />
          </view>
        </view>
      </view>
    </template>

    <!--
      整页滚动（信息条也在滚动区内）：信息条随内容滚走，场次轴吸顶 ——
      即主流会场的「高矮版头动态切换」；吸顶后轴条加阴影与页面分层。
      场次轴用 position: sticky，小程序端不支持时退化为随页滚动，不破坏布局。
    -->
    <scroll-view
      v-else
      scroll-y
      class="page-scroll"
      refresher-enabled
      :refresher-triggered="refreshing"
      :scroll-into-view="scrollInto"
      @refresherrefresh="onRefresh"
      @scroll="onPageScroll"
      @scrolltolower="onLoadMore"
    >
      <view id="seckill-top" />
      <!--
        信息卡：整页唯一的主色面。
        钩子句与「活动规则」在卡头（主色实底、白字可读），倒计时在卡内的白色
        胶囊条上（品牌色数字压白底 = 全卡最高对比），既保留秒杀强度，又不把
        主色铺满首屏；「N 人在抢 / 开抢提醒」同在这条白色胶囊条的右端。
      -->
      <view class="hero">
        <view class="hero-head">
          <view class="hero-icon">
            <image class="hero-icon-img" src="/static/promotion/icon-flash.svg" mode="aspectFit" />
          </view>
          <text class="hero-sub">
            {{ t('promotion.seckill.subtitle') }}
          </text>
          <view class="hero-rule-hit" @tap.stop="goRulePage">
            <view class="hero-rule">
              <text class="hero-rule-text">
                {{ t('promotion.seckill.rulesLink') }}
              </text>
              <view class="hero-rule-arrow" />
            </view>
          </view>
        </view>

        <view v-if="showCountdown" class="hero-cd">
          <text class="cd-label">
            {{ countdownLabel }}
          </text>
          <view class="hero-cd-main">
            <view class="cd-blocks">
              <text v-if="countdown.days > 0" class="cd-days">
                {{ t('promotion.common.days', { days: countdown.days }) }}
              </text>
              <text class="cd-num">
                {{ pad(countdown.hours) }}
              </text>
              <text class="cd-colon">
                :
              </text>
              <text class="cd-num">
                {{ pad(countdown.minutes) }}
              </text>
              <text class="cd-colon">
                :
              </text>
              <text class="cd-num">
                {{ pad(countdown.seconds) }}
              </text>
            </view>
            <view class="hero-cd-tail">
              <!-- 未开始场次的主动作是「开抢提醒」，同屏再挤「N 人在抢」会把条塞满 -->
              <text v-if="cartUserText && !showReminderButton" class="hero-live">
                {{ cartUserText }}
              </text>
              <view
                v-if="showReminderButton"
                class="hero-remind-hit"
                @tap.stop="handleRemind"
              >
                <text class="hero-remind">
                  {{ subscribed ? t('promotion.seckill.reminded') : t('promotion.seckill.remindMe') }}
                </text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 场次轴：吸顶（已结束场次不渲染） -->
      <view v-if="tabSessions.length > 0" class="hall-panel" :class="{ 'is-stuck': axisStuck }">
        <scroll-view
          scroll-x
          class="hall-tabs"
          :show-scrollbar="false"
        >
          <view class="hall-tabs-track">
            <view
              v-for="item in tabSessions"
              :key="item.session.sessionId"
              class="hall-tab"
              :class="[
                item.session.status === 1 ? 'is-live' : 'is-pending',
                { 'is-current': item.index === currentSessionIndex },
              ]"
              @click="selectSession(item.index)"
            >
              <text class="hall-tab-label">
                {{ sessionAxisLabel(item.session) }}
              </text>
            </view>
          </view>
        </scroll-view>
      </view>

      <view class="goods-list">
        <!-- 切场次骨架：2 行商品 -->
        <template v-if="goodsLoading">
          <view v-for="n in 2" :key="`sk-${n}`" class="card-skeleton">
            <view class="sk-media" />
            <view class="sk-lines">
              <view class="sk-line sk-line--lg" />
              <view class="sk-line" />
              <view class="sk-line sk-line--sm" />
            </view>
          </view>
        </template>

        <!-- 空态：没有可展示场次，或当前场次尚未上架商品 -->
        <hall-state
          v-else-if="noSession"
          art="seckill"
          :title="t('promotion.seckill.emptyTitle')"
          :desc="t('promotion.seckill.emptyDesc')"
          :action-text="t('promotion.common.goHome')"
          @action="goHome"
        />
        <hall-state
          v-else-if="goodsList.length === 0"
          art="seckill"
          :title="t('promotion.seckill.emptyGoodsTitle')"
          :desc="otherSessionIndex >= 0 ? t('promotion.seckill.otherSessionDesc') : t('promotion.seckill.retryLaterDesc')"
          :link-text="otherSessionIndex >= 0 ? t('promotion.seckill.switchSession') : ''"
          @link="goOtherSession"
        />

        <template v-else>
          <!-- 商品行卡：左图（唯一角标）+ 右侧品名/双价·CTA 同行/已抢轨道 -->
          <view
            v-for="goods in goodsList"
            :key="goods.skuId"
            class="goods-card"
            :class="{ 'is-out': isSoldOut(goods) }"
            hover-class="goods-card--pressed"
            @click="goGoodsDetail(goods)"
          >
            <view class="goods-media">
              <image
                v-if="goods.goodsImage"
                class="goods-img"
                :src="resolveImageSrc(goods.goodsImage)"
                mode="aspectFill"
                lazy-load
              />
              <view v-else class="goods-img goods-img--empty">
                <text>{{ t('promotion.seckill.badge') }}</text>
              </view>
              <text v-if="offBadgeText(goods)" class="goods-off">
                {{ offBadgeText(goods) }}
              </text>
              <view v-if="isSoldOut(goods)" class="goods-cover">
                <text class="goods-cover-text">
                  {{ t('promotion.seckill.soldOut') }}
                </text>
              </view>
            </view>

            <view class="goods-info">
              <view class="goods-name">
                {{ goods.goodsName || t('promotion.seckill.goodsFallback') }}
              </view>

              <!-- 子标题：与装修商品卡/折扣会场同口径，未配置或与名称相同时整行隐藏 -->
              <view v-if="shouldShowSubtitle(goods.goodsName, goods.subTitle)" class="goods-sub">
                {{ goods.subTitle }}
              </view>

              <!-- 价格与 CTA 同一行：CTA 不再独占一条竖直通道 -->
              <view class="goods-price-row">
                <view class="goods-price">
                  <text class="price-sym">
                    ¥
                  </text>
                  <text class="price-val">
                    {{ formatPrice(goods.seckillPrice) }}
                  </text>
                  <text v-if="hasOriginalPrice(goods)" class="price-orig">
                    ¥{{ formatPrice(goods.originalPrice) }}
                  </text>
                </view>
                <view class="cta-hit" @click.stop="handleBuy(goods)">
                  <view class="goods-cta" :class="{ 'is-disabled': !canBuy(goods) }">
                    {{ ctaText(goods) }}
                  </view>
                </view>
              </view>

              <!--
                进度：已抢 > 0 才出轨道 + 文案；0% 与未开始场次只出「限量 N 件」文字
                （0% 的空轨道没有进度语义，之前每张卡底部都挂一条空灰条）。
              -->
              <view v-if="stockText(goods)" class="prog">
                <view v-if="showStockTrack(goods)" class="prog-track">
                  <view
                    class="prog-fill"
                    :class="{ 'is-out': isSoldOut(goods) }"
                    :style="{ width: `${stockInfo(goods)?.pct ?? 0}%` }"
                  />
                </view>
                <text class="prog-text" :class="stockTone(goods)">
                  {{ stockText(goods) }}
                </text>
              </view>
            </view>
          </view>

          <view class="load-more">
            <text v-if="noMore">
              {{ t('promotion.common.noMore') }}
            </text>
            <text v-else>
              {{ t('common.loading') }}
            </text>
          </view>
        </template>
      </view>
    </scroll-view>

    <!-- 悬浮回顶：滚动超过约 2 屏才出现 -->
    <view v-if="showBackTop" class="back-top" @tap="backTop">
      <view class="back-top-glyph" />
    </view>
  </view>
</template>

<!--
  2026-10-23 第二轮重构：把「加元素表达促销」换成「删元素 + 主色面积当稀缺资源」。
  头部由四段压成两段（导航承载标题 → 信息条 + 场次轴），场次改横滑 pill 三态
  （进行中实心主色、未开始浅底、已结束不渲染），价格与 CTA 同行，已抢恢复
  h12 真实轨道 + 文案双出，并新增两条降噪规则（见 stockInfo / offBadgeText）。
  空态统一走 HallState（品牌渐变圆 + 白色线性图标），不再用中文单字方块。
  颜色一律走 --wot-color-theme-* token，跟随多租户主题换肤。

  2026-11-01 风格统一（用户反馈「跟其它页面风格不一致」）：
  · 顶栏改回全站业务页口径：白底深字 + 默认分隔线，不再用满幅主色导航，
    同时消掉小程序端「状态栏黑字压橙底」和「活动规则与居中标题粘连」两个硬伤；
  · 原满幅主色信息条只保留成一张卡（24rpx 圆角 + 品牌渐变 + 品牌色投影），
    主色面积从首屏约 1/3 压到一张 180rpx 的卡；
  · 倒计时从 #1F2329 纯黑分格改为卡内白色胶囊条 + 品牌色等宽数字（对比最高，
    也是全站唯一不再出现的黑色块）；「N 人在抢 / 开抢提醒」收进同一条右端；
  · 场次 pill 对齐折扣页活动 Tab：h64、激活实心主色、待开始 #5b616e（5.6:1）；
  · 商品卡：24rpx 圆角 + 购物车同档轻投影；已抢 0% 不再渲染空轨道，
    进度文案常规态转辅助灰（22/500 #8a8f99），仅「仅剩 / 已抢光」升主色；
  · 骨架按新首屏同高重排，收尾补 env(safe-area-inset-bottom) 让位。
-->
<style lang="scss" scoped>
.seckill-page {
  display: flex;
  /* 固定视口高度 + 内部 scroll-view 滚动：min-height 会让内容撑高整页，
     导致 scroll-view 不产生内部滚动（下拉刷新与 @scroll 全部失效）。 */
  height: 100vh;
  flex-direction: column;
  overflow: hidden;
  background: var(--app-bg);
}

/* ---- 信息卡：整页唯一的主色面（品牌渐变卡 + 白色倒计时胶囊条） ---- */
.hero {
  display: flex;
  overflow: hidden;
  margin: 20rpx 24rpx 0;
  padding: 24rpx;
  flex-direction: column;
  gap: 16rpx;
  border-radius: 24rpx;
  color: #fff;
  /* 与个人中心 / 会员中心同一套品牌渐变写法；浅端只落在卡底装饰区，白字全在主色实底上 */
  background: linear-gradient(180deg, var(--wot-color-theme-primary, #ff2237) 0%, var(--wot-color-theme-secondary, #ff6b7a) 100%);
  box-shadow: 0 10rpx 28rpx rgb(20 29 45 / 10%);
}

.hero-head {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 14rpx;
}

/* 图形位：白 22% 底 + 白色闪电图标（线性语义，替代「秒」字方块） */
.hero-icon {
  display: flex;
  width: 48rpx;
  height: 48rpx;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  background: rgb(255 255 255 / 22%);
  border-radius: 50%;
}

.hero-icon-img {
  width: 28rpx;
  height: 28rpx;
}

.hero-sub {
  overflow: hidden;
  min-width: 0;
  flex: 1;
  color: #fff;
  font-size: 26rpx;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 「活动规则」：白 20% 玻璃胶囊；热区用负 margin 撑到 88rpx，不撑高卡头 */
.hero-rule-hit {
  display: flex;
  height: 88rpx;
  margin: -20rpx 0;
  flex: 0 0 auto;
  align-items: center;
}

.hero-rule {
  display: flex;
  height: 48rpx;
  padding: 0 16rpx;
  align-items: center;
  gap: 6rpx;
  background: rgb(255 255 255 / 20%);
  border-radius: 999rpx;
}

.hero-rule-text {
  color: #fff;
  font-size: 22rpx;
  font-weight: 500;
  line-height: 1;
}

/* 右向细箭头：2rpx 描边旋转 45°，比引图标字体轻 */
.hero-rule-arrow {
  width: 8rpx;
  height: 8rpx;
  border-top: 2rpx solid rgb(255 255 255 / 80%);
  border-right: 2rpx solid rgb(255 255 255 / 80%);
  transform: rotate(45deg);
}

/* 倒计时胶囊条：白底 + 品牌色数字 —— 全卡最高对比，主色「省」给数字。
   两端对齐（label 左 / 数字+尾部右），没有在抢人数时右侧也不会空出一条白带 */
.hero-cd {
  display: flex;
  min-height: 68rpx;
  padding: 0 20rpx;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  background: rgb(255 255 255 / 94%);
  border-radius: 16rpx;
}

.hero-cd-main {
  display: flex;
  overflow: hidden;
  min-width: 0;
  align-items: center;
  gap: 12rpx;
}

.cd-label {
  flex: 0 0 auto;
  color: #5b616e;
  font-size: 20rpx;
  font-weight: 500;
  line-height: 1;
}

.cd-blocks {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}

/* 倒计时数字：等宽 + 品牌色 + 800 字重（这张卡的信息主角） */
.cd-num,
.cd-colon {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 30rpx;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.6rpx;
  line-height: 1;
}

.cd-colon {
  opacity: 0.45;
}

/* 跨天场次：天单独一段、字号降一档，不与时:分:秒 抢宽 */
.cd-days {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 22rpx;
  font-weight: 700;
  line-height: 1;
}

.hero-cd-tail {
  display: flex;
  overflow: hidden;
  min-width: 0;
  flex: 0 1 auto;
  align-items: center;
  gap: 12rpx;
}

/* 「N 人在抢」：真实购物车去重人数，后端拿不到时不渲染（不伪造氛围） */
.hero-live {
  overflow: hidden;
  min-width: 0;
  flex: 0 1 auto;
  color: #8a8f99;
  font-size: 20rpx;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 开抢提醒：白条右端的实心主色小胶囊；热区负 margin 撑到 88rpx */
.hero-remind-hit {
  display: flex;
  height: 88rpx;
  margin: -10rpx 0;
  align-items: center;
}

.hero-remind {
  display: flex;
  height: 44rpx;
  padding: 0 16rpx;
  align-items: center;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 999rpx;
  color: #fff;
  font-size: 20rpx;
  font-weight: 600;
  line-height: 1;
}

/* ---- 场次轴：白色吸顶条（信息卡滚走后仍可切场次）；不支持 sticky 时随页滚动 ---- */
.hall-panel {
  position: sticky;
  top: 0;
  z-index: 3;
  margin-top: 16rpx;
  background: var(--app-card);
  transition: box-shadow 0.2s;
}

.hall-panel.is-stuck {
  box-shadow: 0 8rpx 20rpx rgb(15 23 42 / 8%);
}

.hall-tabs {
  width: 100%;
  white-space: nowrap;
}

.hall-tabs-track {
  display: inline-flex;
  padding: 16rpx 24rpx;
  gap: 12rpx;
}

/* 场次 pill：与折扣页活动 Tab 同规格（h64 / 激活实心主色 / 待开始浅底） */
.hall-tab {
  display: inline-flex;
  box-sizing: border-box;
  height: 64rpx;
  padding: 0 24rpx;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
}

/* 进行中：实心主色 + 白字（强促销：主色只给「现在」这一场） */
.hall-tab.is-live {
  background: var(--wot-color-theme-primary, #ff2237);
}

.hall-tab.is-live .hall-tab-label {
  color: #fff;
  font-size: 24rpx;
  font-weight: 700;
}

/* 未开始：浅底 + 中性字（#5b616e 对比度 5.6:1，比原来的 3.0:1 更可读） */
.hall-tab.is-pending {
  background: var(--app-card-2);
}

.hall-tab.is-pending .hall-tab-label {
  color: var(--app-text-2);
  font-size: 24rpx;
  font-weight: 500;
}

/*
 * 选中的未开始场次：全场都还没开抢时（例如晚间打开页面），原来所有 pill 长得一样，
 * 用户不知道正在看哪一场。用主色描边 + 主色字标出当前场次；进行中场次本身就是
 * 实心主色，不需要再叠选中态。
 */
.hall-tab.is-pending.is-current {
  background: var(--app-card);
  border: 2rpx solid var(--wot-color-theme-primary, #ff2237);
}

.hall-tab.is-pending.is-current .hall-tab-label {
  color: var(--wot-color-theme-primary, #ff2237);
  font-weight: 600;
}

.hall-tab-label {
  line-height: 1;
  white-space: nowrap;
}

/* ---- 商品列表 ---- */
.page-scroll {
  /* H5 端 uni-scroll-view 基座自带 width:100%，显式 border-box 让内层 padding
     收进宽度内，避免卡片右缘被挤出视口。min-height:0 让滚动区能被压缩，
     否则 flex 子项按内容高度撑开、不产生内部滚动。 */
  box-sizing: border-box;
  min-height: 0;
  flex: 1;
}

.goods-list {
  padding: 20rpx 24rpx 0;
}

.goods-card {
  display: flex;
  padding: 20rpx;
  margin-bottom: 20rpx;
  background: var(--app-card);
  border-radius: 24rpx;
  /* 与购物车行卡同档：列表卡用极轻投影，不再用 8/24 的浮起阴影 */
  box-shadow: 0 2rpx 12rpx rgb(15 23 42 / 4%);
}

.goods-card--pressed {
  background: var(--app-card-2);
}

/* 售罄整卡置灰：图降透明度、文字压成辅助色 */
.goods-card.is-out {
  .goods-img {
    opacity: 0.55;
  }

  .goods-name,
  .price-sym,
  .price-val {
    color: var(--app-text-3);
  }
}

.goods-media {
  position: relative;
  overflow: hidden;
  width: 200rpx;
  height: 200rpx;
  flex: 0 0 auto;
  background: var(--app-card-2);
  border-radius: 16rpx;
}

.goods-img {
  display: block;
  width: 200rpx;
  height: 200rpx;
}

.goods-img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 24rpx;
}

/* 图上唯一角标：实心主色小块（价差过深时文案降噪为「限量特价」），与拼团卡同规格 */
.goods-off {
  position: absolute;
  top: 10rpx;
  left: 10rpx;
  display: flex;
  height: 34rpx;
  padding: 0 12rpx;
  align-items: center;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 8rpx;
  color: #fff;
  font-size: 20rpx;
  font-weight: 700;
}

/* 售罄蒙层：图上居中半透明「已抢光」 */
.goods-cover {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(255 255 255 / 42%);
}

.goods-cover-text {
  display: flex;
  width: 108rpx;
  height: 108rpx;
  align-items: center;
  justify-content: center;
  background: rgb(31 35 41 / 62%);
  border-radius: 50%;
  color: #fff;
  font-size: 22rpx;
  font-weight: 700;
}

.goods-info {
  display: flex;
  overflow: hidden;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  padding: 2rpx 0 0 20rpx;
}

.goods-name {
  display: -webkit-box;
  overflow: hidden;
  height: 72rpx;
  color: var(--app-text);
  font-size: 26rpx;
  font-weight: 500;
  line-height: 36rpx;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

/* 子标题：单行省略（#8a8f99 与进度文案同色）；未配置/与名称相同时整行隐藏 */
.goods-sub {
  overflow: hidden;
  margin-top: 4rpx;
  color: var(--app-text-3);
  font-size: 22rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 价格与 CTA 同一行：价格左、CTA 右，行高由 CTA 撑到 64rpx */
.goods-price-row {
  display: flex;
  margin-top: 8rpx;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}

.goods-price {
  display: flex;
  overflow: hidden;
  align-items: baseline;
  gap: 8rpx;
  white-space: nowrap;
}

.price-sym {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 20rpx;
  font-weight: 700;
}

.price-val {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 42rpx;
  font-weight: 800;
  letter-spacing: -0.8rpx;
  line-height: 1;
}

.price-orig {
  overflow: hidden;
  color: var(--app-text-3);
  font-size: 20rpx;
  text-decoration: line-through;
  text-overflow: ellipsis;
}

/* 触控热区 ≥88rpx；视觉按钮 64rpx 高胶囊 */
.cta-hit {
  display: flex;
  min-width: 104rpx;
  min-height: 88rpx;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-end;
}

.goods-cta {
  display: flex;
  min-width: 104rpx;
  height: 64rpx;
  padding: 0 24rpx;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff6b35), var(--wot-color-theme-primary, #ff2237));
  border-radius: 999rpx;
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  white-space: nowrap;
}

/* 未开始/已结束/售罄：浅底主色字 vs 灰底灰字（四字文案缩到 22rpx 保证不折行） */
.goods-cta.is-disabled {
  background: var(--app-card-2);
  color: var(--app-text-3);
  font-size: 22rpx;
  letter-spacing: 0;
}

/* ---- 已抢进度：真实轨道 + 文案双出（0% 不出轨道，见模板注释） ---- */
.prog {
  display: flex;
  margin-top: 12rpx;
  min-height: 32rpx;
  align-items: center;
  gap: 12rpx;
}

.prog-track {
  overflow: hidden;
  height: 12rpx;
  min-width: 120rpx;
  flex: 1;
  background: var(--app-card-2);
  border-radius: 999rpx;
}

.prog-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--wot-color-theme-secondary, #ff6b35), var(--wot-color-theme-primary, #ff2237));
  border-radius: 999rpx;
}

/* 售罄：轨道同步转中性色 */
.prog-fill.is-out {
  background: #d7dae0;
}

/* 常规进度 / 限量数走辅助灰；只有「仅剩 N 件 / 已抢光」升主色，一卡主色不堆叠 */
.prog-text {
  overflow: hidden;
  flex: 0 0 auto;
  color: var(--app-text-3);
  font-size: 22rpx;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.prog-text.is-urgent {
  color: var(--wot-color-theme-primary, #ff2237);
  font-weight: 600;
}

.prog-text.is-out {
  color: var(--app-text-3);
}

/* ---- 骨架 ---- */
@keyframes sk-pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.55;
  }
}

/* 信息卡骨架：与真实卡同高（padding24 + 卡头48 + 间距16 + 倒计时条68 = 180rpx） */
.hero-skeleton {
  margin: 20rpx 24rpx 0;
  height: 180rpx;
  background: var(--app-card-2);
  border-radius: 24rpx;
  animation: sk-pulse 1.4s ease-in-out infinite;
}

/* 面板骨架：与真实场次轴同形（白底条 + 场次 pill） */
.panel-skeleton {
  margin-top: 16rpx;
  padding: 16rpx 24rpx;
  background: var(--app-card);
  animation: sk-pulse 1.4s ease-in-out infinite;
}

.sk-tabs {
  display: flex;
  height: 64rpx;
  align-items: center;
  gap: 12rpx;
}

.sk-tab {
  width: 176rpx;
  height: 64rpx;
  background: var(--app-card-2);
  border-radius: 999rpx;
}

.sk-line {
  height: 24rpx;
  background: var(--app-card-2);
  border-radius: 8rpx;
}

.sk-line--lg {
  width: 72%;
  height: 28rpx;
}

.sk-line--sm {
  width: 42%;
}

.card-skeleton {
  display: flex;
  padding: 20rpx;
  margin-bottom: 20rpx;
  background: var(--app-card);
  border-radius: 24rpx;
  box-shadow: 0 2rpx 12rpx rgb(15 23 42 / 4%);
  animation: sk-pulse 1.4s ease-in-out infinite;
}

.sk-media {
  width: 200rpx;
  height: 200rpx;
  flex: 0 0 auto;
  background: var(--app-card-2);
  border-radius: 16rpx;
}

.sk-lines {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding-left: 20rpx;
  gap: 18rpx;
}

/* ---- 加载更多（底部预留 home indicator 安全区，最后一张卡不被压住） ---- */
.load-more {
  padding: 24rpx 0 calc(40rpx + env(safe-area-inset-bottom));
  color: var(--app-text-3);
  font-size: 22rpx;
  text-align: center;
}

/* ---- 悬浮回顶（滚动约 2 屏后出现，纯 CSS 箭头，不引图标） ---- */
.back-top {
  position: fixed;
  /* 放左下：卡片右下是「抢」主按钮，右下角悬浮必然压住 CTA */
  left: 24rpx;
  bottom: calc(48rpx + env(safe-area-inset-bottom));
  z-index: 20;
  display: flex;
  width: 80rpx;
  height: 80rpx;
  align-items: center;
  justify-content: center;
  background: rgb(255 255 255 / 94%);
  border-radius: 50%;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.14);
}

.back-top-glyph {
  width: 22rpx;
  height: 22rpx;
  margin-top: 6rpx;
  border-top: 3rpx solid #5b616e;
  border-left: 3rpx solid #5b616e;
  transform: rotate(45deg);
}

/**
 * 深色模式：白玻璃件（hero 倒计时胶囊、回顶钮）切深色玻璃，
 * 压在其上的中性灰文字/箭头同步换语义浅色——浅色分支的深灰在深玻璃上不可读。
 * 品牌渐变 hero、售罄白蒙层不属于白玻璃件，保持原样。
 */
.wot-theme-dark .hero-cd {
  background: rgb(26 26 26 / 88%);
}

.wot-theme-dark .cd-label {
  color: var(--app-text-2);
}

.wot-theme-dark .hero-live {
  color: var(--app-text-3);
}

.wot-theme-dark .back-top {
  background: rgb(26 26 26 / 88%);
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.46);
}

.wot-theme-dark .back-top-glyph {
  border-top-color: rgba(255, 255, 255, 0.68);
  border-left-color: rgba(255, 255, 255, 0.68);
}
</style>
