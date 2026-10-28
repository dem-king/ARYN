<script setup lang="ts">
import type {
  AppDiscountActivityVO,
  AppDiscountGoodsVO,
} from '@/api/promotion'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getDiscountActivities } from '@/api/promotion'
import { shouldShowSubtitle } from '@/components/diy/subtitle-display'
import QuickCartButton from '@/components/quick-cart-button/index.vue'
import { formatDiscountValue } from '@/utils/discount-price'
import { navigateToUrl } from '@/utils/tab-bar'

definePage({
  name: 'discount',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '限时折扣',
  },
})

const { t } = useI18n()

/** 全场型活动与空态的出口：回首页商品流（折扣在商详/结算生效） */
const HOME_PATH = '/pages/home/index'

/** 折扣活动列表：一次拉全，Tab 必须是完整集合，不能随分页增长 */
const activityList = ref<AppDiscountActivityVO[]>([])
/** 活动拉取上限：折扣活动是运营配置的少量数据，一次取回即可 */
const ACTIVITY_PAGE_SIZE = 100
/** 首屏骨架：只在还没有活动数据时展示 */
const loading = ref(true)
/** 下拉刷新状态：驱动 scroll-view 的 refresher 收起 */
const refreshing = ref(false)
/** scroll-view 滚动定位目标：切活动/回顶统一落 discount-top */
const scrollIntoView = ref('')
/** 悬浮回顶：滚动超过约 2 屏才出现 */
const showBackTop = ref(false)
/** 当前选中的活动：Tab 即内容切换（与秒杀页「场次轴」同口径），不是页内锚点 */
const activeActivityId = ref('')
const tabScrollInto = ref('')

const showActivityTabs = computed(() => activityList.value.length > 1)

/** 当前活动：Tab 选中项；选中态失效（刷新后活动下线）时回落到第一个 */
const activeActivity = computed<AppDiscountActivityVO | null>(() => {
  return activityList.value.find(activity => activity.activityId === activeActivityId.value)
    ?? activityList.value[0]
    ?? null
})

/**
 * 点击活动 Tab：切换渲染的活动内容（同秒杀页切场次）。
 * 内容整体替换后旧滚动位置不再有意义，统一回到顶部；
 * `scroll-into-view` 需先清空再赋值才能重复触发同一目标。
 */
function selectActivity(activityId: string) {
  if (activityId === activeActivityId.value)
    return
  activeActivityId.value = activityId
  scrollIntoView.value = ''
  tabScrollInto.value = ''
  nextTick(() => {
    scrollIntoView.value = 'discount-top'
    tabScrollInto.value = `tab-${activityId}`
  })
}

/**
 * 悬浮玻璃活动卡（2026-10-26 用户需求）：
 * 活动卡从滚动流里抽出来，常驻内容区顶部，商品从卡下方滑过并透出模糊底色（玻璃拟态）。
 *
 * 卡片不再是滚动内容的一部分 → 不再占布局空间，必须自己补一块等高占位，
 * 否则首屏第一行商品会被玻璃卡压住。卡高受中英文案、端上字体行高影响不能写死，
 * 渲染后实测回填；小程序首帧量不到节点尺寸时最多补测 3 次（同 diy-newbie-floor 口径）。
 */
const FLOAT_CARD_GAP = 12
const floatCardHeight = ref(0)
/** 滚动后抬升态：商品已滑到卡下，加重投影把卡「举」起来 */
const floatCardRaised = ref(false)
/** 悬浮卡数据源：loading 或空态时无卡可浮 */
const floatActivity = computed(() => (loading.value ? null : activeActivity.value))
const floatCardSpaceStyle = computed(() => ({
  height: `${(floatCardHeight.value || 0) + FLOAT_CARD_GAP}px`,
}))

let floatMeasureRetry = 0
let floatMeasureTimer: ReturnType<typeof setTimeout> | undefined

/** 重新实测悬浮卡高度（数据到位 / 切活动后调用） */
function measureFloatCard() {
  if (floatMeasureTimer)
    clearTimeout(floatMeasureTimer)
  floatMeasureRetry = 0
  nextTick(runMeasureFloatCard)
}

function runMeasureFloatCard() {
  uni.createSelectorQuery()
    .select('.act-float-card')
    .boundingClientRect()
    .exec((rects) => {
      const height = Number((rects?.[0] as { height?: number } | null)?.height) || 0
      if (height > 0) {
        floatCardHeight.value = Math.ceil(height)
        return
      }
      // 卡片还没上屏（loading → loaded 首帧）或端上布局未就绪：补测后仍拿不到就保持 CSS 兜底高度
      if (floatMeasureRetry >= 3)
        return
      floatMeasureRetry += 1
      floatMeasureTimer = setTimeout(runMeasureFloatCard, 120)
    })
}

watch(
  () => [loading.value, activeActivityId.value] as const,
  () => {
    if (floatActivity.value)
      measureFloatCard()
    else
      floatCardHeight.value = 0
  },
  { flush: 'post' },
)

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
  await loadActivities()
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

/**
 * 加载折扣活动列表（一次拉全）。
 * Tab 是内容切换而不是锚点，必须先把完整活动集合拿到再渲染，
 * 不能沿用分页追加（否则第 2 页活动要等上拉才出现在 Tab 里）。
 */
async function loadActivities() {
  // 首屏用骨架占位；下拉刷新沿用手势自带 spinner，不再盖一层全局 loading
  if (activityList.value.length === 0)
    loading.value = true

  try {
    const response = await getDiscountActivities({
      current: 1,
      size: ACTIVITY_PAGE_SIZE,
    })
    const records = response.records || []
    activityList.value = records
    // 倒计时以后端下发秒数为准，避免客户端与服务端时钟漂移
    records.forEach(initCountdown)
    pruneCountdowns()
    updateAllCountdowns()
    // 刷新后原选中活动可能已下线/被删除：回落到第一个活动
    if (!records.some(activity => activity.activityId === activeActivityId.value))
      activeActivityId.value = records[0]?.activityId ?? ''
  }
  catch {
    uni.showToast({ title: t('promotion.common.loadFailed'), icon: 'none' })
  }
  finally {
    loading.value = false
  }
}

/** 下拉刷新 */
async function onRefresh() {
  refreshing.value = true
  try {
    await loadActivities()
  }
  finally {
    refreshing.value = false
  }
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
  return days > 0 ? t('promotion.common.daysClock', { clock, days }) : clock
}

/**
 * 倒计时前导词：进行中是「距结束」；
 * 未开始的倒计时语义是距开抢时间，用「距开始」避免误导。
 */
function getCountdownLabel(activity: AppDiscountActivityVO) {
  return activity.status === 1
    ? t('promotion.discount.countdownEnd')
    : t('promotion.discount.countdownStart')
}

/** 倒计时归零/活动结束时的兜底文案 */
function countdownFallbackText(activity: AppDiscountActivityVO) {
  // status 1 但倒计时归零：服务端状态尚未翻转，按已结束展示更贴近事实
  if (activity.status === 1 || activity.status === 2)
    return t('promotion.discount.ended')
  return t('promotion.discount.comingSoon')
}

/**
 * 折扣口径：与 `utils/discount-price` 的 `formatDiscountValue` 同一套
 * （0.95 → 9.5折、0.8 → 8折；≥10 折退化为中性「折扣」；减价/固定价沿用后端语义），
 * 中文按「8折」、英文按「20% OFF」表达，避免本页与商详/购物车角标漂移。
 */
function discountTagText(activity: AppDiscountActivityVO) {
  return formatDiscountValue(activity)
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
    return t('promotion.discount.fullScopeDesc')
  const percent = Math.round((1 - Number(activity.discountValue)) * 100)
  if (!Number.isFinite(percent) || percent <= 0)
    return t('promotion.discount.fullScopeDesc')
  return t('promotion.discount.fullScopeDescPercent', { percent })
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
  return count > 0
    ? t('promotion.discount.goodsCount', { count })
    : t('promotion.discount.goodsScope')
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
    return t('labels.soldOut')
  if (count <= 10)
    return t('labels.onlyLeft', { count })
  return t('promotion.common.limitedStock', { count: activity.stock ?? count })
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
  return t('promotion.common.saveAmount', { amount: diff.toFixed(2) })
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
    return t('labels.soldOut')
  return count <= 10 ? t('labels.onlyLeft', { count }) : ''
}

/** 空态主按钮：无折扣活动时回首页商品流 */
function goHome() {
  navigateToUrl(HOME_PATH)
}

/** 导航栏购物车入口 */
function toCart() {
  navigateToUrl('/pages/user/shopping-cart/index')
}

/** 页面滚动：驱动悬浮回顶显隐（阈值按 px，约 2 屏）与玻璃卡抬升态 */
function onPageScroll(event: { detail: { scrollTop: number } }) {
  const top = event?.detail?.scrollTop ?? 0
  showBackTop.value = top > 600
  // 阈值取小：内容一开始从卡下滑过就抬升（回顶后立刻落回常态阴影）
  const raised = top > 2
  if (raised !== floatCardRaised.value)
    floatCardRaised.value = raised
}

/** 回顶：复用 scroll-into-view（改 scroll-top 在用户手动滚动后不生效） */
function backTop() {
  scrollIntoView.value = ''
  nextTick(() => {
    scrollIntoView.value = 'discount-top'
  })
}

/** 跳转商品详情（整卡可点，加购按钮单独 stop 冒泡） */
/**
 * 快捷加购确认弹层的成交价。
 *
 * 确认弹层只从 quick-cart 拿得到 SKU 普通售价，而本页商品卡标的是折后价；
 * 不把活动价带进去就会出现「卡片 ¥23.92、弹层小计 ¥29.90」的价差
 * （用户本次反馈的问题之一）。折后价必须严格低于基准价才采用，
 * 避免把异常配置的价格当优惠宣传。
 */
function quickAddPromotionOf(goods: AppDiscountGoodsVO) {
  const skuId = String(goods?.skuId || '')
  const price = Number(goods?.discountPrice)
  const original = Number(goods?.originalPrice)
  if (!skuId || !Number.isFinite(price) || price <= 0 || !Number.isFinite(original) || original <= price)
    return null
  return {
    skuId,
    unitPrice: price,
    maxQuantity: null,
    hint: t('promotion.discount.quickAddHint', { discount: formatDiscountValue(goods) }),
  }
}

function goGoodsDetail(goods: AppDiscountGoodsVO) {
  uni.navigateTo({
    url: `/sub-pages/product/goods-detail/index?id=${goods.spuId}`,
  })
}
</script>

<template>
  <view class="discount-page">
    <hr-navbar :title="t('promotion.discount.title')">
      <!-- 购物车入口放导航栏（与分类页同源），不做悬浮圆钮：右下角会和网格卡的加购按钮叠在一起 -->
      <template #right>
        <view class="nav-cart" @tap="toCart">
          <wd-badge :model-value="cartCount">
            <wd-icon name="cart" size="44rpx" />
          </wd-badge>
        </view>
      </template>
    </hr-navbar>

    <!-- 多活动时的活动切换条：切换下方渲染的活动内容（内容区独立滚动，Tab 恒可见） -->
    <scroll-view
      v-if="showActivityTabs"
      scroll-x
      class="act-tabs"
      :show-scrollbar="false"
      :scroll-into-view="tabScrollInto"
      scroll-with-animation
    >
      <view class="act-tabs-track">
        <view
          v-for="activity in activityList"
          :id="`tab-${activity.activityId}`"
          :key="`tab-${activity.activityId}`"
          class="act-tab"
          :class="{ 'is-on': activity.activityId === activeActivityId }"
          @tap="selectActivity(activity.activityId)"
        >
          <text class="act-tab-ratio">
            {{ discountTagText(activity) }}
          </text>
          <text class="act-tab-name">
            {{ activity.activityName || t('promotion.discount.title') }}
          </text>
        </view>
      </view>
    </scroll-view>

    <!--
      内容舞台：scroll-view 与悬浮玻璃卡同处一个定位容器。
      卡片绝对定位在滚动区之上（不再是滚动内容），商品从卡下方滑过，玻璃底色才透得出滚动内容。
    -->
    <view class="content-stage">
      <scroll-view
        scroll-y
        class="content-scroll"
        refresher-enabled
        :refresher-triggered="refreshing"
        :scroll-into-view="scrollIntoView"
        @refresherrefresh="onRefresh"
        @scroll="onPageScroll"
      >
        <view id="discount-top" />
        <!-- 悬浮卡等高空位：高度由脚本实测卡高回填，保证首屏第一行商品不被玻璃卡压住 -->
        <view
          v-if="floatActivity"
          class="act-float-space"
          :style="floatCardSpaceStyle"
        />
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
          :title="t('promotion.discount.emptyTitle')"
          :desc="t('promotion.discount.emptyDesc')"
          :action-text="t('promotion.common.goHome')"
          @action="goHome"
        />

        <template v-else-if="activeActivity">
          <view :key="activeActivity.activityId" class="act-group">
            <!-- 活动卡已抽到滚动区外的悬浮玻璃卡（见 .content-stage 末尾），此处只留商品区 -->
            <!--
              商品型活动：双列网格。
              单张卡 = 方图 + 图上折角标（折扣力度全卡只此一处）+ 品名两行
              + 子标题一行 + 到手价/划线 + 省额胶囊 + 右下快捷加购；
              第二轮去掉「到手价」label（图上角标已表达力度，卡内不再重复文案）。
            -->
            <view v-if="!isFullScope(activeActivity)" class="goods-grid">
              <view
                v-for="goods in activeActivity.goodsList"
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
                    <text>{{ t('labels.discount') }}</text>
                  </view>
                  <text class="goods-off">
                    {{ discountTagText(activeActivity) }}
                  </text>
                </view>

                <view class="goods-body">
                  <view class="goods-name">
                    {{ goods.goodsName || t('promotion.discount.goodsFallback') }}
                  </view>
                  <!-- 子标题：与装修商品卡同口径，未配置或与名称相同时整行隐藏 -->
                  <view v-if="shouldShowSubtitle(goods.goodsName, goods.subTitle)" class="goods-sub">
                    {{ goods.subTitle }}
                  </view>
                  <view class="goods-price">
                    <text class="price-sym">
                      ¥
                    </text>
                    <text class="price-val">
                      {{ formatPrice(goods.discountPrice) }}
                    </text>
                    <text v-if="hasOriginalPrice(goods)" class="price-orig">
                      ¥{{ formatPrice(goods.originalPrice) }}
                    </text>
                  </view>
                  <view class="goods-bottom">
                    <text v-if="savedText(goods)" class="goods-save">
                      {{ savedText(goods) }}
                    </text>
                    <text v-if="stockHint(goods)" class="goods-stock" :class="{ 'is-out': isSoldOut(goods) }">
                      {{ stockHint(goods) }}
                    </text>
                  </view>

                  <!--
                  加购复用 QuickCartButton（未登录跳登录 / 多规格唤起 SKU 弹层 /
                  加购去向确认都在组件内，弹层由 App.ku.vue 全局挂载一份）。
                  外观改为「浅主色底实心圆钮」，不再用描边圆钮（同卡描边只留 0 处）。
                -->
                  <view class="goods-cart" @click.stop>
                    <QuickCartButton
                      v-if="!isSoldOut(goods)"
                      :spu-id="goods.spuId"
                      :promotion="quickAddPromotionOf(goods)"
                      size="64rpx"
                    >
                      <view class="goods-cart-btn">
                        <wd-icon name="cart" size="34rpx" />
                      </view>
                    </QuickCartButton>
                    <!-- 售罄：不再唤起加购（避免进车后到下单才被拦），整卡仍可进详情 -->
                    <view v-else class="goods-cart-btn goods-cart-btn--out">
                      <text class="goods-cart-out">
                        {{ t('promotion.discount.soldOutShort') }}
                      </text>
                    </view>
                  </view>
                </view>
              </view>
            </view>

            <!--
            全场型活动（scope=1）没有商品清单，折扣在商详/结算生效。
            不能只剩一张活动卡：给一块权益说明 + 去首页商品流的出口，
            避免切到该 Tab 后页面像没加载出来。
          -->
            <view v-else class="full-scope-panel">
              <text class="full-scope-title">
                {{ t('promotion.discount.fullScopeTitle') }}
              </text>
              <text class="full-scope-desc">
                {{ t('promotion.discount.fullScopeHint', { discount: discountTagText(activeActivity) }) }}
              </text>
              <view class="full-scope-btn-hit" @tap="goHome">
                <text class="full-scope-btn">
                  {{ t('promotion.discount.goShopping') }}
                </text>
              </view>
            </view>
          </view>

          <!-- 全场型活动没有商品清单，不出现列表收尾文案 -->
          <view v-if="!isFullScope(activeActivity)" class="load-more">
            <text>{{ t('promotion.common.noMore') }}</text>
          </view>
        </template>
      </scroll-view>

      <!--
        悬浮玻璃活动卡（2026-10-26）：绝对定位在滚动区之上、常驻内容区顶部，
        商品从卡下方滑过，backdrop-filter 把透出的内容模糊成底色 —— 玻璃拟态。
        loading / 空态不渲染；卡高由脚本实测后回填滚动区内的等高空位。
      -->
      <view
        v-if="floatActivity"
        class="act-float-card"
        :class="{ 'is-raised': floatCardRaised }"
      >
        <view class="act-ratio">
          <text
            class="act-ratio-text"
            :class="ratioTextClass(discountTagText(floatActivity))"
          >
            {{ discountTagText(floatActivity) }}
          </text>
        </view>

        <view class="act-main">
          <text class="act-name">
            {{ floatActivity.activityName || t('promotion.discount.title') }}
          </text>

          <!-- 副行：全场型显示权益换算，商品型显示参与件数 -->
          <text class="act-sub">
            {{ activitySubtitle(floatActivity) }}
          </text>

          <!-- 倒计时：一行文字，超 24h 走「N 天 HH:MM:SS」 -->
          <text v-if="hasLiveCountdown(floatActivity)" class="act-cd">
            {{ getCountdownLabel(floatActivity) }} {{ countdownText(floatActivity) }}
          </text>
          <text v-else class="act-cd act-cd--end">
            {{ countdownFallbackText(floatActivity) }}
          </text>
        </view>
      </view>
    </view>

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

  2026-10-23 修正 Tab 语义：活动 Tab 由「页内锚点」改为「内容切换」（与秒杀页
  场次轴同口径）—— 同一时刻只渲染选中活动的卡与商品；活动一次拉全，Tab 不再
  随分页增长；全场型活动无商品清单，补权益说明 + 去首页入口，避免空 Tab。

  2026-10-25 移除活动卡「去逛逛」文字链：商品型的商品区就贴在卡下方，
  全场型已有权益卡的「去首页逛商品」出口，该入口在两种形态下都冗余。

  2026-10-26 活动卡改为悬浮玻璃卡（用户需求：滑动商品时一直展示）：
  - 卡片从 scroll-view 内容里抽到 .content-stage（定位容器）上，绝对定位常驻顶部，
    商品从卡下方滑过，backdrop-filter 把透出的内容模糊成底色 —— 玻璃拟态；
  - 卡片脱离滚动流后由脚本实测卡高、回填 .act-float-space 等高空位，首屏不被遮挡；
  - 滚动后加 .is-raised 加重投影，回顶落回常态阴影；load/空态不渲染悬浮卡。
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

/* ---- 内容舞台：滚动区 + 悬浮玻璃卡，同一个定位容器 ---- */
.content-stage {
  position: relative;
  display: flex;
  overflow: hidden;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.content-scroll {
  box-sizing: border-box;
  min-height: 0;
  flex: 1;
  padding: 20rpx 0 0;
}

/* ---- 活动切换条（多活动时） ---- */
.act-tabs {
  width: 100%;
  flex: 0 0 auto;
  padding: 12rpx 0;
  background: #fff;
  white-space: nowrap;
}

.act-tabs-track {
  display: inline-flex;
  padding: 0 24rpx;
  gap: 16rpx;
}

.act-tab {
  display: inline-flex;
  height: 64rpx;
  max-width: 420rpx;
  padding: 0 20rpx;
  flex: 0 0 auto;
  align-items: center;
  gap: 8rpx;
  background: #f5f6f8;
  border-radius: 999rpx;
}

.act-tab.is-on {
  background: var(--wot-color-theme-primary, #ff2237);
}

.act-tab-ratio {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 22rpx;
  font-weight: 700;
  white-space: nowrap;
}

.act-tab-name {
  overflow: hidden;
  color: #5b616e;
  font-size: 22rpx;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.act-tab.is-on .act-tab-ratio,
.act-tab.is-on .act-tab-name {
  color: #fff;
}

/* ---- 商品区容器（活动卡已上浮，此处只剩商品网格/权益卡） ---- */
.act-group {
  display: block;
}

/* ---- 悬浮玻璃活动卡（玻璃拟态） ---- */
/* 等高空位：高度由脚本实测卡高回填；min-height 是量不到节点尺寸时的兜底 */
.act-float-space {
  min-height: 172rpx;
}

/*
  卡片的玻璃感来自三层：
  1. 半透明底 + backdrop-filter：下方滑过的商品被模糊成底色（不是纯装饰，滚动内容真的从卡下穿过）；
  2. 1rpx 半透明白描边 + 上缘向下的白色高光（::before）：模拟玻璃厚边与反光；
  3. 双层投影：常态轻浮起，滚动后（.is-raised）加重，把卡和商品分层。
  不支持 backdrop-filter 的内核（部分安卓 X5）退化为高不透明白，文案仍可读。
  文案对比度：卡底是半透明白叠模糊内容，副行/倒计时由 #8a8f99 提到 #5b616e 才够读。
*/
.act-float-card {
  position: absolute;
  top: 20rpx;
  right: 24rpx;
  left: 24rpx;
  z-index: 6;
  display: flex;
  box-sizing: border-box;
  overflow: hidden;
  padding: 24rpx;
  align-items: center;
  gap: 20rpx;
  border: 1rpx solid rgb(255 255 255 / 70%);
  border-radius: 24rpx;
  /* 斜向渐变让玻璃有厚薄差：左上更透亮、右下略深，避免整块死白 */
  background: linear-gradient(135deg, rgb(255 255 255 / 80%) 0%, rgb(255 255 255 / 58%) 100%);
  /* WEBKIT 大写写法避开 autoprefixer 的过时前缀清理（iOS 17- 的 WKWebView 只认带前缀的 backdrop-filter） */
  -WEBKIT-backdrop-filter: blur(28px) saturate(200%);
  backdrop-filter: blur(28px) saturate(200%);
  box-shadow:
    0 12rpx 32rpx rgb(20 29 45 / 12%),
    0 2rpx 8rpx rgb(20 29 45 / 6%),
    inset 0 1rpx 0 rgb(255 255 255 / 85%);
  transition: box-shadow 220ms ease;
}

/* 滚动后抬升：商品已滑到卡下，加重投影把卡「举」起来 */
.act-float-card.is-raised {
  box-shadow:
    0 20rpx 48rpx rgb(20 29 45 / 18%),
    0 2rpx 8rpx rgb(20 29 45 / 6%),
    inset 0 1rpx 0 rgb(255 255 255 / 90%);
}

/* 玻璃高光：上缘一道向下渐隐的白，纯装饰层，不参与点击 */
.act-float-card::before {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 56%;
  content: '';
  background: linear-gradient(180deg, rgb(255 255 255 / 42%) 0%, rgb(255 255 255 / 0%) 100%);
  pointer-events: none;
}

/* 高光层在内容之下：大牌与文案必须抬到 ::before 之上 */
.act-float-card .act-ratio,
.act-float-card .act-main {
  position: relative;
  z-index: 1;
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
  /* 玻璃面上的一枚实心色块：给一点内高光，避免像贴上去的纯色 */
  box-shadow: inset 0 2rpx 0 rgb(255 255 255 / 22%);
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

/* 副行：muted 单行省略；玻璃底上 #8a8f99 偏浅，压深到 #5b616e 保证可读 */
.act-sub {
  overflow: hidden;
  color: #5b616e;
  font-size: 22rpx;
  font-weight: 400;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 倒计时：一行文字（等宽数字），不出分格；玻璃底上同样压深一档 */
.act-cd {
  color: #5b616e;
  font-size: 22rpx;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.act-cd--end {
  color: #8a8f99;
}

/* ---- 全场型活动：无商品清单时的权益说明与出口 ---- */
.full-scope-panel {
  display: flex;
  margin: 0 24rpx 20rpx;
  padding: 56rpx 32rpx 48rpx;
  align-items: center;
  flex-direction: column;
  gap: 12rpx;
  background: #fff;
  border-radius: 20rpx;
}

.full-scope-title {
  color: #1f2329;
  font-size: 28rpx;
  font-weight: 600;
  text-align: center;
}

.full-scope-desc {
  color: #8a8f99;
  font-size: 22rpx;
  font-weight: 400;
  line-height: 1.5;
  text-align: center;
}

.full-scope-btn-hit {
  display: flex;
  min-height: 88rpx;
  margin-top: 12rpx;
  align-items: center;
}

.full-scope-btn {
  padding: 16rpx 44rpx;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 999rpx;
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
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
  color: #1f2329;
  font-size: 25rpx;
  font-weight: 500;
  line-height: 34rpx;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

/* 子标题：单行省略（#8a8f99 与页内 muted 文案同色）；未配置/与名称相同时隐藏 */
.goods-sub {
  overflow: hidden;
  margin-top: 4rpx;
  color: #8a8f99;
  font-size: 22rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
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
