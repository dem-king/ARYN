<script setup lang="ts">
import type {
  AppDiscountActivityVO,
  AppDiscountGoodsVO,
} from '@/api/promotion'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { computed, nextTick, reactive, ref } from 'vue'
import { getDiscountActivities } from '@/api/promotion'
import QuickCartButton from '@/components/quick-cart-button/index.vue'
import { navigateToUrl } from '@/utils/tab-bar'

definePage({
  name: 'discount',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '限时折扣',
  },
})

/** 全场型活动没有商品清单，折扣在商详生效：整卡「去逛逛」回首页商品流 */
const HOME_PATH = '/pages/home/index'

/** 折扣活动列表 */
const activityList = ref<AppDiscountActivityVO[]>([])
/** 是否还有更多 */
const noMore = ref(false)
/** 分页参数 */
const currentPage = ref(1)
const PAGE_SIZE = 10
/** 首屏骨架：只在还没有活动数据时展示 */
const loading = ref(true)
/** 下拉刷新状态：驱动 scroll-view 的 refresher 收起 */
const refreshing = ref(false)
/** 上拉加载中 */
const loadingMore = ref(false)
/** scroll-view 滚动定位目标：活动头「去逛逛」滚到本活动商品区 / 回顶 */
const scrollIntoView = ref('')
/** 悬浮回顶：滚动超过约 2 屏才出现 */
const showBackTop = ref(false)

const authStore = useAuthStore()
const shoppingCartStore = useShoppingCartStore()
/** 购物车角标：未登录不展示（与分类页同源口径） */
const cartCount = computed(() => (authStore.isLoggedIn ? shoppingCartStore.getCartCount : 0))

/**
 * 倒计时状态映射（按活动ID）。
 *
 * `remaining` 为唯一真值，由后端下发的秒数赋值后本地递减；
 * h/m/s 仅由它派生，不再解析 endTime —— 后端返回的是 `yyyy-MM-dd HH:mm:ss`，
 * iOS 对带空格的日期串解析失败会得到 NaN，倒计时会卡在 00:00:00。
 */
const countdownMap = reactive<Record<string, {
  hours: number
  minutes: number
  remaining: number
  seconds: number
  finished: boolean
}>>({})

let countdownTimer: ReturnType<typeof setInterval> | null = null

onLoad(async () => {
  await loadActivities(true)
  startCountdown()
})

onUnload(() => {
  stopCountdown()
})

/** 由剩余秒数派生 h/m/s 与结束标记 */
function applyRemaining(activityId: string) {
  const state = countdownMap[activityId]
  if (!state)
    return
  if (state.remaining <= 0) {
    state.remaining = 0
    state.hours = 0
    state.minutes = 0
    state.seconds = 0
    state.finished = true
    return
  }
  state.hours = Math.floor(state.remaining / 3600)
  state.minutes = Math.floor((state.remaining % 3600) / 60)
  state.seconds = state.remaining % 60
  state.finished = false
}

/** 用后端下发秒数初始化单个活动的倒计时 */
function initCountdown(activity: AppDiscountActivityVO) {
  const total = Math.max(0, Number(activity.countdown) || 0)
  countdownMap[activity.activityId] = {
    finished: total <= 0,
    hours: 0,
    minutes: 0,
    remaining: total,
    seconds: 0,
  }
  applyRemaining(activity.activityId)
}

/** 清理已不在列表里的倒计时状态，避免刷新后残留 */
function pruneCountdowns() {
  const validIds = new Set(activityList.value.map(activity => activity.activityId))
  Object.keys(countdownMap).forEach((id) => {
    if (!validIds.has(id))
      delete countdownMap[id]
  })
}

/** 加载折扣活动列表 */
async function loadActivities(isRefresh = false) {
  if (isRefresh) {
    currentPage.value = 1
    noMore.value = false
  }
  if (noMore.value && !isRefresh)
    return

  // 首屏用骨架占位；下拉刷新沿用手势自带 spinner，不再盖一层全局 loading
  if (isRefresh && activityList.value.length === 0)
    loading.value = true
  if (!isRefresh)
    loadingMore.value = true

  try {
    const response = await getDiscountActivities({
      current: currentPage.value,
      size: PAGE_SIZE,
    })
    const records = response.records || []
    activityList.value = isRefresh
      ? records
      : [...activityList.value, ...records]
    if (records.length < PAGE_SIZE || activityList.value.length >= response.total)
      noMore.value = true
    // 倒计时以后端下发秒数为准，避免客户端与服务端时钟漂移
    records.forEach(initCountdown)
    pruneCountdowns()
    updateAllCountdowns()
  }
  catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  }
  finally {
    loading.value = false
    loadingMore.value = false
  }
}

/** 下拉刷新 */
async function onRefresh() {
  refreshing.value = true
  try {
    await loadActivities(true)
  }
  finally {
    refreshing.value = false
  }
}

/** 上拉加载更多 */
async function onLoadMore() {
  if (noMore.value)
    return
  currentPage.value++
  await loadActivities(false)
}

/** 启动全局倒计时 */
function startCountdown() {
  stopCountdown()
  updateAllCountdowns()
  countdownTimer = setInterval(updateAllCountdowns, 1000)
}

/** 停止倒计时 */
function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

/** 更新所有活动倒计时（每秒本地递减） */
function updateAllCountdowns() {
  activityList.value.forEach((activity) => {
    const state = countdownMap[activity.activityId]
    if (!state || state.finished)
      return
    state.remaining -= 1
    applyRemaining(activity.activityId)
  })
}

/** 两位补零（倒计时分格） */
function pad(value: number) {
  return String(Math.max(0, value)).padStart(2, '0')
}

/** 倒计时是否仍在本地递减（已结束活动不再跳动） */
function hasLiveCountdown(activity: AppDiscountActivityVO) {
  const state = countdownMap[activity.activityId]
  return activity.status !== 2 && !!state && !state.finished
}

/**
 * 倒计时文案：单行文字（`tabular-nums`），不再用实心分格。
 * 超 24 小时一律走「N 天 HH:MM:SS」，禁止出现三位数小时分格
 * （旧实现把 923 小时塞进分格，格宽不齐且读数无意义）。
 */
function countdownText(activity: AppDiscountActivityVO) {
  const state = countdownMap[activity.activityId]
  if (!state)
    return '00:00:00'
  const days = Math.floor(state.hours / 24)
  const hours = state.hours % 24
  const clock = `${pad(hours)}:${pad(state.minutes)}:${pad(state.seconds)}`
  return days > 0 ? `${days}天 ${clock}` : clock
}

/**
 * 倒计时前导词：进行中是「距结束」；
 * 未开始的倒计时语义是距开抢时间，用「距开始」避免误导。
 */
function getCountdownLabel(activity: AppDiscountActivityVO) {
  return activity.status === 1 ? '距结束' : '距开始'
}

/** 倒计时归零/活动结束时的兜底文案 */
function countdownFallbackText(activity: AppDiscountActivityVO) {
  // status 1 但倒计时归零：服务端状态尚未翻转，按已结束展示更贴近事实
  if (activity.status === 1 || activity.status === 2)
    return '已结束'
  return '即将开始'
}

/**
 * 折扣口径（修复 D1）：0.95 → 9.5折、0.8 → 8折。
 * 旧实现 `Math.round(value * 10)` 会把 0.95 舍成「10 折」，
 * 与首页折扣楼层的 `Math.round(value * 100) / 10` 口径分叉。
 */
function discountTagText(activity: AppDiscountActivityVO) {
  switch (activity.discountType) {
    case 1: {
      const zhe = Math.round(Number(activity.discountValue) * 100) / 10
      if (!Number.isFinite(zhe) || zhe <= 0)
        return '折扣'
      // 力度等于原价（10 折）没有折扣语义，退回「折扣」而不是展示「10折」
      if (zhe >= 10)
        return '折扣'
      return `${Number.isInteger(zhe) ? zhe : zhe.toFixed(1)}折`
    }
    case 2:
      return `减¥${activity.discountValue}`
    case 3:
      return '一口价'
    default:
      return '折扣'
  }
}

/**
 * 折扣大牌字号随文案长度降级。
 * 原型大牌是 128×128rpx 方块里的「8折」（44rpx/800）：
 * 2 字直接用 44rpx；3 字（一口价/折扣）与 4 字以上（9.5折、减¥999）降一档，
 * 保证口径完整不被裁切，也不出现「9.5」掉行。
 */
function ratioTextClass(text: string) {
  if (text.length >= 4)
    return 'act-ratio-text--sm'
  if (text.length === 3)
    return 'act-ratio-text--md'
  return ''
}

/** 形态 A：全场型活动（goodsList 为空），用权益卡替代旧的灰字说明 */
function isFullScope(activity: AppDiscountActivityVO) {
  return !activity.goodsList || activity.goodsList.length === 0
}

/** 全场权益卡说明：打折活动换算出等价的「下单立减 X%」，其余类型不编造比例 */
function fullDesc(activity: AppDiscountActivityVO) {
  if (activity.discountType !== 1)
    return '全场商品参与 · 下单立减'
  const percent = Math.round((1 - Number(activity.discountValue)) * 100)
  if (!Number.isFinite(percent) || percent <= 0)
    return '全场商品参与 · 下单立减'
  return `全场商品参与 · 下单立减 ${percent}%`
}

/**
 * 活动卡副行（原型 p6）：全场型展示权益换算，商品型展示参与件数。
 * 只使用接口已下发的真实字段，不编造比例/人气。
 */
function activitySubtitle(activity: AppDiscountActivityVO) {
  if (isFullScope(activity)) {
    const desc = fullDesc(activity)
    const quota = activityQuotaText(activity)
    return quota ? `${desc} · ${quota}` : desc
  }
  const count = activity.goodsList?.length || 0
  return count > 0 ? `${count}件商品参与` : '指定商品参与'
}

/**
 * 全场型活动的额度文案：剩余为 0 显示「已售罄」，紧张时显示「仅剩 N 件」，
 * 其余显示「限量 N 件」（活动总量，供用户判断促销规模）。
 */
function activityQuotaText(activity: AppDiscountActivityVO) {
  const remaining = activity.remainingStock
  if (remaining === null || remaining === undefined || !Number.isFinite(Number(remaining)))
    return ''
  const count = Math.max(0, Math.floor(Number(remaining)))
  if (count <= 0)
    return '已售罄'
  if (count <= 10)
    return `仅剩 ${count} 件`
  return `限量 ${(activity.stock ?? count)} 件`
}

/** 原价严格大于折后价才划线；否则原价与省额整块隐藏 */
function hasOriginalPrice(goods: AppDiscountGoodsVO) {
  return Number(goods.originalPrice) > Number(goods.discountPrice)
}

/** 省额徽章：只在真实有价差时展示（原价 − 折后价） */
function savedText(goods: AppDiscountGoodsVO) {
  const diff = Number(goods.originalPrice) - Number(goods.discountPrice)
  if (!Number.isFinite(diff) || diff <= 0)
    return ''
  return `省¥${diff.toFixed(2)}`
}

/** 金额统一两位小数 */
function formatPrice(value: number) {
  const price = Number(value)
  return Number.isFinite(price) ? price.toFixed(2) : '0.00'
}

/** 是否已售罄：仅对配置了限量的商品成立（remaining 为空 = 不限量，永远可买） */
function isSoldOut(goods: AppDiscountGoodsVO) {
  const remaining = goods.remainingStock
  if (remaining === null || remaining === undefined || !Number.isFinite(Number(remaining)))
    return false
  return Number(remaining) <= 0
}

/**
 * 库存紧迫文案：只在真的紧张时出现，避免每张卡都挂一个数字。
 * remaining 为空 = 不限量（不展示）；0 = 已售罄；≤10 件才提示「仅剩 N 件」。
 * 售罄时加购入口同步禁用（P3-02）：真实拦截在下单侧，但不该让用户先加进购物车。
 */
function stockHint(goods: AppDiscountGoodsVO) {
  const remaining = goods.remainingStock
  if (remaining === null || remaining === undefined || !Number.isFinite(Number(remaining)))
    return ''
  const count = Math.max(0, Math.floor(Number(remaining)))
  if (count <= 0)
    return '已售罄'
  return count <= 10 ? `仅剩 ${count} 件` : ''
}

/** 活动头「去逛逛」：商品型活动滚到本活动商品区，全场型活动回首页商品流 */
function onBrowse(activity: AppDiscountActivityVO) {
  if (isFullScope(activity)) {
    navigateToUrl(HOME_PATH)
    return
  }
  const target = `goods-${activity.activityId}`
  // 先清空再设置，保证连续点击同一个活动也能再次触发滚动
  scrollIntoView.value = ''
  nextTick(() => {
    scrollIntoView.value = target
  })
}

/** 空态主按钮：无折扣活动时回首页商品流 */
function goHome() {
  navigateToUrl(HOME_PATH)
}

/** 导航栏购物车入口 */
function toCart() {
  navigateToUrl('/pages/user/shopping-cart/index')
}

/** 页面滚动：驱动悬浮回顶显隐（阈值按 px，约 2 屏） */
function onPageScroll(event: { detail: { scrollTop: number } }) {
  const top = event?.detail?.scrollTop ?? 0
  showBackTop.value = top > 600
}

/** 回顶：复用 scroll-into-view（改 scroll-top 在用户手动滚动后不生效） */
function backTop() {
  scrollIntoView.value = ''
  nextTick(() => {
    scrollIntoView.value = 'discount-top'
  })
}

/** 跳转商品详情（整卡可点，加购按钮单独 stop 冒泡） */
function goGoodsDetail(goods: AppDiscountGoodsVO) {
  uni.navigateTo({
    url: `/sub-pages/product/goods-detail/index?id=${goods.spuId}`,
  })
}
</script>

<template>
  <view class="discount-page">
    <hr-navbar title="限时折扣">
      <!-- 购物车入口放导航栏（与分类页同源），不做悬浮圆钮：右下角会和网格卡的加购按钮叠在一起 -->
      <template #right>
        <view class="nav-cart" @tap="toCart">
          <wd-badge :model-value="cartCount">
            <wd-icon name="cart" size="44rpx" />
          </wd-badge>
        </view>
      </template>
    </hr-navbar>

    <scroll-view
      scroll-y
      class="content-scroll"
      refresher-enabled
      :refresher-triggered="refreshing"
      :scroll-into-view="scrollIntoView"
      @refresherrefresh="onRefresh"
      @scroll="onPageScroll"
      @scrolltolower="onLoadMore"
    >
      <view id="discount-top" />
      <!-- 首屏骨架：1 张活动卡 + 2 列 × 2 行商品卡，形状与真实内容一致 -->
      <template v-if="loading">
        <view class="sk-page">
          <view class="act-skeleton">
            <view class="sk-ratio" />
            <view class="sk-head-lines">
              <view class="sk-line sk-line--lg" />
              <view class="sk-line sk-line--md" />
              <view class="sk-line sk-line--sm" />
            </view>
          </view>
          <view class="goods-grid">
            <view v-for="n in 4" :key="n" class="goods-card">
              <view class="sk-img" />
              <view class="sk-body">
                <view class="sk-line sk-line--lg" />
                <view class="sk-line sk-line--md" />
                <view class="sk-line sk-line--sm" />
              </view>
            </view>
          </view>
        </view>
      </template>

      <!-- 空态：品牌渐变圆 + 白色折扣图标 + 主按钮（统一走 HallState） -->
      <hall-state
        v-else-if="activityList.length === 0"
        art="discount"
        title="暂无折扣活动"
        desc="换个时间再来，或先去逛逛商品"
        action-text="去首页逛逛"
        @action="goHome"
      />

      <template v-else>
        <view
          v-for="activity in activityList"
          :key="activity.activityId"
          class="act-group"
        >
          <!--
            活动卡（第二轮重构）：白底**无描边** + 左侧实心折扣大牌
            + 活动名/副行 + 一行倒计时文字（`tabular-nums`）+ 右侧「去逛逛 ›」文字链。
            描边是「没有阴影就拿描边凑」的妥协，叠上大牌/圆钮会在同一张卡出现三次，故删除。
          -->
          <view class="act-card">
            <view class="act-ratio">
              <text
                class="act-ratio-text"
                :class="ratioTextClass(discountTagText(activity))"
              >
                {{ discountTagText(activity) }}
              </text>
            </view>

            <view class="act-main">
              <text class="act-name">
                {{ activity.activityName || '限时折扣' }}
              </text>

              <!-- 副行：全场型显示权益换算，商品型显示参与件数 -->
              <text class="act-sub">
                {{ activitySubtitle(activity) }}
              </text>

              <!-- 倒计时：一行文字，超 24h 走「N 天 HH:MM:SS」 -->
              <text v-if="hasLiveCountdown(activity)" class="act-cd">
                {{ getCountdownLabel(activity) }} {{ countdownText(activity) }}
              </text>
              <text v-else class="act-cd act-cd--end">
                {{ countdownFallbackText(activity) }}
              </text>
            </view>

            <view class="act-btn-hit" @click.stop="onBrowse(activity)">
              <text class="act-btn">去逛逛 ›</text>
            </view>
          </view>

          <!--
            商品型活动：双列网格。
            单张卡 = 方图 + 图上折角标（折扣力度全卡只此一处）+ 品名两行
            + 到手价/划线 + 省额胶囊 + 右下快捷加购；
            第二轮去掉「到手价」label（图上角标已表达力度，卡内不再重复文案）。
          -->
          <view
            v-if="!isFullScope(activity)"
            :id="`goods-${activity.activityId}`"
            class="goods-grid"
          >
            <view
              v-for="goods in activity.goodsList"
              :key="goods.skuId"
              class="goods-card"
              hover-class="goods-card--pressed"
              :hover-stay-time="120"
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
                  <text>折扣</text>
                </view>
                <text class="goods-off">
                  {{ discountTagText(activity) }}
                </text>
              </view>

              <view class="goods-body">
                <view class="goods-name">
                  {{ goods.goodsName || '折扣商品' }}
                </view>
                <view class="goods-price">
                  <text class="price-sym">¥</text>
                  <text class="price-val">{{ formatPrice(goods.discountPrice) }}</text>
                  <text v-if="hasOriginalPrice(goods)" class="price-orig">
                    ¥{{ formatPrice(goods.originalPrice) }}
                  </text>
                </view>
                <view class="goods-bottom">
                  <text v-if="savedText(goods)" class="goods-save">
                    {{ savedText(goods) }}
                  </text>
                  <text v-if="stockHint(goods)" class="goods-stock" :class="{ 'is-out': stockHint(goods) === '已售罄' }">
                    {{ stockHint(goods) }}
                  </text>
                </view>

                <!--
                  加购复用 QuickCartButton（未登录跳登录 / 多规格唤起 SKU 弹层 /
                  加购去向确认都在组件内，弹层由 App.ku.vue 全局挂载一份）。
                  外观改为「浅主色底实心圆钮」，不再用描边圆钮（同卡描边只留 0 处）。
                -->
                <view class="goods-cart" @click.stop>
                  <QuickCartButton v-if="!isSoldOut(goods)" :spu-id="goods.spuId" size="64rpx">
                    <view class="goods-cart-btn">
                      <wd-icon name="cart" size="34rpx" />
                    </view>
                  </QuickCartButton>
                  <!-- 售罄：不再唤起加购（避免进车后到下单才被拦），整卡仍可进详情 -->
                  <view v-else class="goods-cart-btn goods-cart-btn--out">
                    <text class="goods-cart-out">
                      售罄
                    </text>
                  </view>
                </view>
              </view>
            </view>
          </view>
        </view>

        <view class="load-more">
          <text v-if="noMore">
            —— 没有更多了 ——
          </text>
          <text v-else-if="loadingMore">
            加载中...
          </text>
          <text v-else>
            上拉加载更多
          </text>
        </view>
      </template>
    </scroll-view>

    <!-- 悬浮回顶：滚动超过约 2 屏才出现 -->
    <view v-if="showBackTop" class="back-top" @tap="backTop">
      <view class="back-top-glyph" />
    </view>
  </view>
</template>

<!--
  2026-10-22 营销会场页视觉重设计（轻促销级），2026-10-23 按原型 p6 全面对齐：
  - 活动卡：白底 + 2rpx 主色描边 + 实心折扣大牌（128rpx）+ 活动名/副行 +
    「剩」+ 三个 primary 实心倒计时分格 + 描边胶囊「去逛逛」；
  - 商品区：两列网格（方图 + 左上折角标 + 到手价 label + 到手价/划线 +
    省额胶囊 + 右下快捷加购），单卡白底圆角 20rpx，无阴影；
  - 折扣=轻促销：主色只出现在大牌/倒计时/价格/省额/按钮上，其余大量留白，
    与秒杀会场的强促销拉开层级。
  颜色一律走 --wot-color-theme-* token，跟随多租户主题换肤。
-->
<style lang="scss" scoped>
.discount-page {
  display: flex;
  /* 固定视口高度 + 内部 scroll-view 滚动：min-height 会被内容撑高，
     使 scroll-view 不产生内部滚动（下拉刷新与 @scroll 全部失效）。 */
  height: 100vh;
  flex-direction: column;
  overflow: hidden;
  background: #f5f6f8;
}

.content-scroll {
  box-sizing: border-box;
  min-height: 0;
  flex: 1;
  padding: 20rpx 0 0;
}

/* ---- 活动卡（白底无描边；轻促销：主色只给大牌与文字链） ---- */
.act-group {
  display: block;
}

.act-card {
  display: flex;
  margin: 0 24rpx 20rpx;
  padding: 24rpx;
  align-items: center;
  gap: 20rpx;
  background: #fff;
  border-radius: 20rpx;
}

/* 折扣大牌：实心 primary 方块 112×112rpx、圆角 20rpx、白字 40rpx/800
   （原 128rpx/44rpx，缩一档让价格与商品成为主角） */
.act-ratio {
  display: flex;
  box-sizing: border-box;
  width: 112rpx;
  height: 112rpx;
  padding: 0 8rpx;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 20rpx;
}

.act-ratio-text {
  color: #fff;
  font-size: 40rpx;
  font-weight: 800;
  letter-spacing: -1rpx;
  line-height: 1.05;
  text-align: center;
  word-break: break-all;
}

/* 长文案（9.5折 / 减¥999 / 一口价）降一档字号，保证 112rpx 方块内完整展示 */
.act-ratio-text--md {
  font-size: 30rpx;
}

.act-ratio-text--sm {
  font-size: 24rpx;
}

.act-main {
  display: flex;
  overflow: hidden;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 6rpx;
}

.act-name {
  display: block;
  overflow: hidden;
  color: #1f2329;
  font-size: 28rpx;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 副行：24rpx/500 muted，单行省略 */
.act-sub {
  overflow: hidden;
  color: #8a8f99;
  font-size: 22rpx;
  font-weight: 400;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 倒计时：一行文字（等宽数字），不出分格 */
.act-cd {
  color: #8a8f99;
  font-size: 22rpx;
  font-weight: 400;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.act-cd--end {
  color: #b6b9c0;
}

/* 「去逛逛」：文字链（不再用描边胶囊，卡片内描边归零）；热区 88rpx */
.act-btn-hit {
  display: flex;
  min-height: 88rpx;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-end;
}

.act-btn {
  padding: 12rpx 0 12rpx 12rpx;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 24rpx;
  font-weight: 700;
  white-space: nowrap;
}

/* ---- 商品双列网格 ---- */
.goods-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
  padding: 0 24rpx 24rpx;
}

.goods-card {
  position: relative;
  overflow: hidden;
  background: #fff;
  border-radius: 20rpx;
  transition: transform 160ms ease;
}

.goods-card--pressed {
  transform: scale(0.98);
}

/* 方图：小程序 <image> 自带默认宽高，比例用 padding-top 撑框、图片绝对铺满 */
.goods-media {
  position: relative;
  width: 100%;
  padding-top: 100%;
  background: #f6f7f9;
}

.goods-img {
  position: absolute;
  top: 0;
  left: 0;
  display: block;
  width: 100%;
  height: 100%;
}

.goods-img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 24rpx;
}

/* 左上折角标：实心 primary 底白字，圆角只在右下 */
.goods-off {
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  height: 34rpx;
  padding: 0 12rpx;
  align-items: center;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 0 0 10rpx 0;
  color: #fff;
  font-size: 20rpx;
  font-weight: 700;
}

.goods-body {
  position: relative;
  padding: 16rpx 16rpx 20rpx;
}

.goods-name {
  display: -webkit-box;
  overflow: hidden;
  height: 68rpx;
  color: #1f2329;
  font-size: 25rpx;
  font-weight: 500;
  line-height: 34rpx;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

/* 到手价：36rpx/700（比秒杀的 42rpx/800 降一档 —— 轻促销不与强促销抢字号） */
.goods-price {
  display: flex;
  overflow: hidden;
  margin-top: 10rpx;
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
  font-size: 36rpx;
  font-weight: 700;
  letter-spacing: -0.6rpx;
  line-height: 1;
}

.price-orig {
  overflow: hidden;
  color: #b6b9c0;
  font-size: 20rpx;
  text-decoration: line-through;
  text-overflow: ellipsis;
}

/* 底部行：省额胶囊靠左，右下角留给绝对定位的加购按钮 */
.goods-bottom {
  display: flex;
  min-height: 64rpx;
  padding: 4rpx 72rpx 0 0;
  align-items: center;
}

/* 省额徽章：浅橙底 + 主色字、999rpx 胶囊、宽度自适应 */
.goods-save {
  display: inline-flex;
  height: 34rpx;
  padding: 0 12rpx;
  align-items: center;
  background: var(--wot-color-theme-background, #fff1ee);
  border-radius: 999rpx;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 19rpx;
  font-weight: 600;
  white-space: nowrap;
}

/* 库存提示：与省额同排，已售罄用弱化灰 */
.goods-stock {
  margin-left: 8rpx;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 20rpx;
  font-weight: 600;
  white-space: nowrap;
}

.goods-stock.is-out {
  color: #b6b9c0;
}

/* 加购按钮绝对定位在卡身右下，与省额行同一视觉行 */
.goods-cart {
  position: absolute;
  right: 16rpx;
  bottom: 20rpx;
  display: flex;
}

/* 描边购物车圆钮改为「浅主色底实心圆钮」：卡片内描边归零。
   底色用 primary 低透明度叠层而非 --wot-color-theme-background ——
   租户 pageBackgroundColor 可能是中性灰，会把浅底洗成灰底。 */
.goods-cart-btn {
  position: relative;
  display: flex;
  box-sizing: border-box;
  overflow: hidden;
  width: 64rpx;
  height: 64rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: var(--wot-color-theme-primary, #ff2237);
}

.goods-cart-btn::before {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  content: '';
  background: var(--wot-color-theme-primary, #ff2237);
  opacity: 0.1;
}

.goods-cart-btn :deep(.wd-icon) {
  position: relative;
  z-index: 1;
}

/* 售罄：灰底灰字，不可点（无按压态） */
.goods-cart-btn--out {
  color: #b6b9c0;
}

.goods-cart-btn--out::before {
  background: #f1f2f4;
  opacity: 1;
}

.goods-cart-out {
  position: relative;
  z-index: 1;
  font-size: 18rpx;
  font-weight: 600;
}

/* ---- 骨架：形状与真实内容一致（1 活动卡 + 2 列 × 2 行商品卡） ---- */
@keyframes sk-pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.55;
  }
}

.sk-page {
  animation: sk-pulse 1.4s ease-in-out infinite;
}

.act-skeleton {
  display: flex;
  margin: 0 24rpx 20rpx;
  padding: 24rpx;
  align-items: center;
  gap: 20rpx;
  background: #fff;
  border-radius: 20rpx;
}

.sk-ratio {
  width: 112rpx;
  height: 112rpx;
  flex: 0 0 auto;
  background: #edeff3;
  border-radius: 20rpx;
}

.sk-head-lines {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16rpx;
}

.sk-line {
  height: 24rpx;
  background: #edeff3;
  border-radius: 8rpx;
}

.sk-line--lg {
  width: 72%;
  height: 28rpx;
}

.sk-line--md {
  width: 56%;
}

.sk-line--sm {
  width: 38%;
}

.sk-img {
  width: 100%;
  padding-top: 100%;
  background: #edeff3;
}

.sk-body {
  display: flex;
  padding: 16rpx 16rpx 20rpx;
  flex-direction: column;
  gap: 14rpx;
}

/* ---- 收尾 ---- */
.load-more {
  padding: 24rpx 0 60rpx;
  color: #b6b9c0;
  font-size: 22rpx;
  text-align: center;
}

/* ---- 导航栏购物车入口（热区 ≥88rpx） ---- */
.nav-cart {
  display: flex;
  min-width: 88rpx;
  min-height: 88rpx;
  align-items: center;
  justify-content: flex-end;
}

/* ---- 悬浮回顶 ---- */
.back-top {
  position: fixed;
  /* 放左下：网格卡右下是加购按钮，右下角悬浮会压住它 */
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
</style>
