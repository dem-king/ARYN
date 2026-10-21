<script setup lang="ts">
import type { NewbieFloorProps, NewbieGoodsItem } from '@/components/diy/retail-types'
import { computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getQuickCartInfo } from '@/api/product/spu'
import { followDecorationLink } from '@/components/diy/link-resolver'
import { loadNewbieFloor } from '@/components/diy/retail-data'
import RetailState from '@/components/diy/retail-state.vue'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { formatRemainingTime, useCountdownTicker } from '@/composables/useCountdown'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useQuickCart } from '@/composables/useQuickCart'
import { useRetailData } from '@/composables/useRetailData'
import { useAuthStore } from '@/store/authStore'
import { useMallThemeStore } from '@/store/mallThemeStore'
import { QUICK_CART_MODE_DIRECT, resolveQuickAddMode, resolveQuickAddQuantity } from '@/utils/quick-cart'

const props = withDefaults(defineProps<{ showData?: Partial<NewbieFloorProps> }>(), {
  showData: () => ({}),
})
const showData = computed<NewbieFloorProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 6,
  dataSource: props.showData.dataSource || { mode: 'automatic' },
  emptyStrategy: props.showData.emptyStrategy || 'hide',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  autoScroll: props.showData.autoScroll === true,
  interval: Number(props.showData.interval) || 3000,
  mainColor: props.showData.mainColor || '',
  showCountdown: props.showData.showCountdown !== false,
  showTag: props.showData.showTag !== false,
  title: props.showData.title || '新人专享',
}))

const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const { items, shouldRender, status } = useRetailData(showData, loadNewbieFloor)

/** 全站自动动效总开关（商城主题配置）；关闭时不启动自动前进 */
const mallThemeStore = useMallThemeStore()

/** 楼层只展示一个「进行中的新人价活动」，取第一个（后端已按优先级排序） */
const floor = computed(() => items.value[0])
const goodsList = computed(() => (floor.value?.goodsList || []).slice(0, showData.value.count))

const { now } = useCountdownTicker()
/**
 * 倒计时拆成「前缀 + 时间」两段：前缀中性灰、时间走全站价格色。
 * 轻量版不再用白底胶囊：浅色面板上直接排字，避免又一次「胶囊上叠胶囊」。
 */
const countdown = computed(() => {
  const endTime = floor.value?.endTime
  if (!showData.value.showCountdown || !endTime)
    return null
  const target = Date.parse(endTime)
  if (Number.isNaN(target))
    return { prefix: '活动', text: '已结束' }
  const remaining = formatRemainingTime(endTime, now.value)
  // 剩余不足 1 秒时 formatRemainingTime 仍返回 00:00:00，与管理端「到点即结束」口径对齐
  return remaining && remaining !== '00:00:00'
    ? { prefix: '距结束', text: remaining }
    : { prefix: '活动', text: '已结束' }
})

function savedAmount(goods: { newbiePrice: number, originalPrice: number }) {
  return Math.max(0, goods.originalPrice - goods.newbiePrice)
}

/**
 * 区块底纹的运营配置色：只作为浅色渐变底纹（--nf-main）叠加在白底上，
 * 不再铺满整块 Surface；价格等强调色仍走全站价格口径。
 */
const colorVars = computed(() => {
  const main = (showData.value.mainColor || '').trim()
  if (!/^#[0-9a-f]{6}$/i.test(main))
    return undefined
  return { '--nf-main': main }
})

/**
 * 自动前进：一次一张卡的分页式滚动，替代连续位移的跑马灯。
 * 连续跑马灯让内容无法阅读、用户无法接管（NN/g 自动轮播研究），
 * WCAG 2.2.2 也要求自动移动超过 5 秒的内容必须可暂停。
 * 手动横滑不再与自动互斥：触摸即暂停，松手 5 秒后自动恢复。
 */
const shouldAutoAdvance = computed(
  () =>
    showData.value.autoScroll
    && goodsList.value.length > 2
    && mallThemeStore.autoMotionEnabled,
)

/** 每张卡片的停留时长（毫秒）；历史草稿缺省回退 3000 */
const intervalMs = computed(() => Number(showData.value.interval) || 3000)

/** 触摸后自动恢复延迟：留出读完一张卡的时间 */
const RESUME_DELAY_MS = 5000

/**
 * 卡片几何（rpx）：必须与样式里的 `.nf-card` 宽 200rpx、`.nf-track` 间距 12rpx 一致，
 * 否则自动步进会错位（改样式时同步这里）。
 * 200rpx 让一屏恰好完整露出 3 张（3×200 + 2×12 = 624rpx），
 * 与生鲜商超「一屏三品」的新人专区一致，不再只露两张半。
 */
const CARD_WIDTH_RPX = 200
const CARD_GAP_RPX = 12
const CARD_STEP_RPX = CARD_WIDTH_RPX + CARD_GAP_RPX

/** scroll-view 的 scroll-left 是 px：按 windowWidth/750 把 rpx 换算成 px */
function rpxToPx(rpx: number) {
  return (rpx * uni.getSystemInfoSync().windowWidth) / 750
}

/**
 * 轨道显式宽度：小程序里块级 flex 轨道的宽度默认只有 scroll-view 那么宽，
 * 原生滚动区量不到溢出内容，最后一张卡会滑不全（管理端画布早已用 width:max-content，
 * 两端因此不一致）。这里按卡片数把轨道撑到实际内容宽，原生横滑才能滚到底。
 */
const trackStyle = computed(() => {
  const len = goodsList.value.length
  const widthRpx = len * CARD_WIDTH_RPX + Math.max(0, len - 1) * CARD_GAP_RPX
  // 行内样式用 px 而不是 rpx：不依赖小程序对「行内 rpx」的支持，也与实测滚动上限的 px 口径一致
  return { width: `${rpxToPx(widthRpx)}px` }
})

/**
 * 滚动上限（px）必须实测：楼层外层还有装修区块的 paddingX / marginX（当前页面用「卡片区块」预设：12px/边 = 24rpx），
 * 旧实现按 750rpx - 横向内边距 40rpx 硬算可视宽，比实际可视宽（654rpx）多算 56rpx，
 * 自动前进「到末尾」时最后一张卡仍被裁掉约半张——运营反馈的「不能完整滚动」。
 */
const maxScrollLeft = ref(0)
const instance = getCurrentInstance()
let measureRetry = 0
let measureTimer: ReturnType<typeof setTimeout> | undefined

function measureScrollRange() {
  measureRetry = 0
  // 清掉上一轮补测链：避免多次触发 watch 时旧链继续跑多余的 query
  if (measureTimer)
    clearTimeout(measureTimer)
  nextTick(runMeasure)
}

/** 小程序首帧可能还量不到节点尺寸：最多补测 3 次，避免自动前进永远拿不到上限 */
function runMeasure() {
  const query = uni.createSelectorQuery()
  const scopedQuery = instance?.proxy ? query.in(instance.proxy) : query
  scopedQuery
    .select('.nf-scroll')
    .boundingClientRect()
    .select('.nf-track')
    .boundingClientRect()
    .exec((rects) => {
      const viewport = Number((rects?.[0] as { width?: number } | null)?.width) || 0
      const track = Number((rects?.[1] as { width?: number } | null)?.width) || 0
      if (viewport > 0 && track > 0) {
        maxScrollLeft.value = Math.max(0, track - viewport)
        return
      }
      if (measureRetry >= 3)
        return
      measureRetry += 1
      measureTimer = setTimeout(runMeasure, 120)
    })
}

/**
 * scroll-left 只承载「程序化步进」；用户手势位置单独记在 liveScrollLeft。
 * 旧实现把 @scroll 的位置回写进绑定的 prop，小程序上会与手指抢滚动位置
 * （滑到一半被拽回去），这也是横滑体验差的来源之一。
 */
const scrollLeft = ref(0)
let liveScrollLeft = 0

/** 小程序 scroll-left 只在值变化时生效：目标与当前值相同时先错开 1px 再落回 */
function applyScrollLeft(target: number) {
  const next = Math.max(0, Math.round(target))
  if (Math.abs(next - scrollLeft.value) < 1) {
    scrollLeft.value = next + 1
    nextTick(() => {
      scrollLeft.value = next
    })
    return
  }
  scrollLeft.value = next
}

let autoTimer: ReturnType<typeof setInterval> | undefined
let resumeTimer: ReturnType<typeof setTimeout> | undefined
let paused = false

function restartAuto() {
  if (autoTimer)
    clearInterval(autoTimer)
  autoTimer = undefined
  if (!shouldAutoAdvance.value)
    return
  autoTimer = setInterval(() => {
    if (paused)
      return
    const max = maxScrollLeft.value
    if (goodsList.value.length < 2 || max <= 0)
      return
    const step = rpxToPx(CARD_STEP_RPX)
    // 步进越界先落到末尾（保证最后一张完整可见），已在末尾则回卷首张
    const atEnd = liveScrollLeft >= max - 4
    const target = atEnd ? 0 : Math.min(liveScrollLeft + step, max)
    liveScrollLeft = target
    applyScrollLeft(target)
  }, intervalMs.value)
}

function pauseAuto() {
  paused = true
  if (resumeTimer)
    clearTimeout(resumeTimer)
  resumeTimer = undefined
}

function scheduleResume() {
  if (resumeTimer)
    clearTimeout(resumeTimer)
  resumeTimer = setTimeout(() => {
    paused = false
  }, RESUME_DELAY_MS)
}

/** 手动滑动或程序化滚动后同步当前位置：只更新自动前进的起点，不回写 prop */
function onScroll(event: { detail?: { scrollLeft?: number } }) {
  const next = Number(event?.detail?.scrollLeft)
  if (!Number.isNaN(next))
    liveScrollLeft = next
}

// 数据异步到达后再启动：挂载时 goodsList 还是空的
watch([shouldAutoAdvance, intervalMs, () => goodsList.value.length], () => {
  paused = false
  restartAuto()
  measureScrollRange()
})

onMounted(measureScrollRange)

onBeforeUnmount(() => {
  if (autoTimer)
    clearInterval(autoTimer)
  if (resumeTimer)
    clearTimeout(resumeTimer)
  if (measureTimer)
    clearTimeout(measureTimer)
})

function openGoods(spuId: string) {
  followDecorationLink({ params: {}, path: '', targetId: spuId, type: 'goods' })
}

/**
 * 「抢」按钮改成真加购后复用统一加购动线：登录拦截、不可加购原因、多规格、
 * 去向确认弹层（useCartDestination）全部归它管，楼层不再自己实现一份——
 * 两份实现必然漂移，加购口径只能有一处真相。
 */
const { pending, quickAdd } = useQuickCart()
const authStore = useAuthStore()

/**
 * 新人价每人每款限购件数。
 *
 * 取楼层级字段（活动配置），而不是商品自身的限购：用户看到的「每人每款限 N 件」
 * 出自活动规则，加购拦截必须与这句承诺同一口径。
 * 归一化层已兜底为 1，这里再防一次非正数，避免历史脏数据把限购算成 0 件。
 */
function newbieLimitPerUser() {
  const limit = Math.floor(Number(floor.value?.limitPerUser))
  return Number.isFinite(limit) && limit > 0 ? limit : 1
}

/**
 * 点「抢」= 直接加购，不再冒泡到整卡点击跳商详（按钮语义终于兑现）。
 *
 * 为什么加购前自己再查一次 quick-cart：`useQuickCart` 只在内部按 MOQ/步长算
 * 数量、不向外返回实际数量，而「新人价限购几件」是楼层才知道的约束。这里用
 * 与它同一份纯函数 `resolveQuickAddQuantity` 预判最小合规数量：一旦最小合规
 * 数量就超过限购件数，说明「限购 1 件」与「起订量/步长 > 1」冲突——任何合规
 * 数量都享不到新人价，硬加只会让用户到结算才发现被恢复原价或被数量校验拦下。
 * 这种冲突不在楼层自行加购（产品口径上更安全的处理），改为打开商详让用户
 * 看清活动规则与规格。
 *
 * 代价与取舍：多一次同接口的 GET（仅在点击时发生，不占渲染）。之所以不拿这份
 * info 自己加购，是为了不复制 useQuickCart 里的不可加购文案/数量归一化/去向
 * 弹层那套逻辑。备注：2026-09-29 船供包装资料下线后后端不再下发 MOQ/步长，
 * 当前最小合规数量恒为 1，该冲突分支正常不会触发；保留它是为了将来恢复数量
 * 规则时不留下「楼层能加、结算被拦」的漏洞。
 *
 * 这次预查询还顺带挡住另一类错单：后端快捷加购挑的是「库存最充足的可用 SKU」，
 * 而楼层卡展示的是活动配置的那个 SKU。两者不是同一行时（历史下架 SKU、活动 SKU
 * 售罄等），用户看到 A 的价、入车却是 B，价差要到结算才暴露，因此对不上就走商详。
 */
async function handleGrab(goods: NewbieGoodsItem) {
  const spuId = String(goods.spuId || '')
  if (!spuId)
    return
  // useQuickCart 内部有 pending 防重；这里提前短路，省掉一次重复的 quick-cart 预查询
  if (pending.value)
    return

  // 未登录时加购必定不会发生（quickAdd 直接引导登录，本组件不重复写登录跳转），
  // 预查询没有意义，跳过它省一次请求
  if (authStore.isLoggedIn) {
    const limit = newbieLimitPerUser()
    // 楼层商品项自带活动配置的 skuId（这也是本组件比列表页多出来的信息）
    const activitySkuId = String(goods.skuId || '')
    try {
      const info = await getQuickCartInfo(spuId)
      if (resolveQuickAddMode(info) === QUICK_CART_MODE_DIRECT) {
        // 加错规格比不加更糟：卡片上标的是活动 SKU 的新人价，而快捷加购是后端按
        // 「库存最充足的可用 SKU」挑的一行（QuickCartServiceImpl.resolveSingleSku），
        // 单规格 SPU 存在历史下架 SKU 时两者可能不是同一行——用户看到 A 的价格、
        // 入车却是 B，价差在结算才暴露。对不上就交给商详让用户看清具体规格。
        if (activitySkuId && String(info?.skuId || '') !== activitySkuId) {
          openGoods(spuId)
          return
        }
        // 「限购 1 件」与「起订量/步长 > 1」冲突：任何合规数量都超限，不自行加购
        if (resolveQuickAddQuantity(info) > limit) {
          // 原生 toast 不随页面卸载立即消失，先提示再跳转，用户能读到限购原因
          uni.showToast({ title: `新人专享价每人限购 ${limit} 件`, icon: 'none' })
          openGoods(spuId)
          return
        }
      }
    }
    catch {
      // 预查询失败不拦：交给 quickAdd 自己再取一次，异常由统一拦截器提示，
      // 不能因为一次探测失败就让「抢」彻底点不动
    }
  }

  // 确认弹层必须按卡片上标的新人价展示小计：quick-cart 只回普通售价，
  // 不把活动价带进去就会出现「卡片 ¥0.01、弹层小计 ¥12.90」的价差；
  // 限购件数同时交给弹层封顶，超量在提交订单时会被后端整单拒绝。
  const result = await quickAdd(spuId, {
    promotion: {
      skuId: String(goods.skuId || ''),
      unitPrice: goods.newbiePrice,
      maxQuantity: newbieLimitPerUser(),
      hint: `新人专享价每人限购 ${newbieLimitPerUser()} 件`,
    },
  })
  if (result.needChoose) {
    // 多规格无法确定加哪个 SKU：楼层卡只标活动配置的那个 SKU 的价格，
    // 在楼层里替用户猜规格会加错货，交给商详选规格更合理
    uni.showToast({ title: '请选择规格', icon: 'none' })
    openGoods(spuId)
  }
  // 剩下两种结果都不再插手：info === null 是未登录/请求失败（登录跳转与提示由
  // useQuickCart 内部处理），added === true 时它已 toast「已加入购物车」并刷新角标。
  // 这里再提示一次就会出现「一个按钮两个提示」，失败时还会误报成功。
}
</script>

<template>
  <view v-if="shouldRender" class="newbie-floor" :style="dynamicStyles">
    <view class="nf-panel" :style="colorVars">
      <view class="nf-head">
        <view v-if="showData.showTag" class="nf-mark">
          新
        </view>
        <text class="nf-title">
          {{ showData.title }}
        </text>
        <text v-if="showData.showTag" class="nf-subtitle">
          首单专享价
        </text>
        <view v-if="countdown" class="nf-countdown">
          <text class="nf-cd-label">
            {{ countdown.prefix }}
          </text>
          <text class="nf-cd-time">
            {{ countdown.text }}
          </text>
        </view>
      </view>
      <view v-if="floor" class="nf-perks">
        注册{{ floor.registerWithinDays }}天内新人 · 每人每款限{{ floor.limitPerUser }}件
      </view>

      <RetailState v-if="status !== 'ready'" :status="status" />

      <scroll-view
        v-else
        class="nf-scroll"
        scroll-x
        :scroll-left="scrollLeft"
        :scroll-with-animation="true"
        @scroll="onScroll"
        @touchstart="pauseAuto"
        @touchend="scheduleResume"
        @touchcancel="scheduleResume"
      >
        <view class="nf-track" :style="trackStyle">
          <view
            v-for="goods in goodsList"
            :key="goods.skuId"
            class="nf-card"
            @click="openGoods(goods.spuId)"
          >
            <view class="nf-media">
              <text v-if="showData.showTag" class="nf-badge">
                新人价
              </text>
              <image
                v-if="goods.goodsImage"
                class="nf-img"
                :src="resolveImageSrc(goods.goodsImage)"
                mode="aspectFill"
                lazy-load
              />
              <view v-else class="nf-img nf-img--empty">
                商品
              </view>
            </view>
            <view class="nf-body">
              <view class="nf-name">
                {{ goods.goodsName || '新人专享商品' }}
              </view>
              <view class="nf-price-row">
                <text class="nf-price">
                  <text class="nf-price-symbol">
                    ¥
                  </text>{{ goods.newbiePrice.toFixed(2) }}
                </text>
                <!--
                  .stop 是关键：不阻止冒泡会同时触发整卡 @click，加购完立刻被带进商详。
                  待确认态只降透明度不改尺寸：按钮尺寸是两端 parity 测试锁定值，不能因状态漂移。
                -->
                <view
                  class="nf-btn"
                  :class="{ 'nf-btn--pending': pending }"
                  @click.stop="handleGrab(goods)"
                >
                  抢
                </view>
              </view>
              <view v-if="goods.originalPrice > goods.newbiePrice" class="nf-meta">
                <text class="nf-original">
                  ¥{{ goods.originalPrice.toFixed(2) }}
                </text>
                <text class="nf-save">
                  省¥{{ savedAmount(goods).toFixed(2) }}
                </text>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<!--
  与管理端 newbie-floor 预览逐值对齐（1rpx = 0.5px），改任一端都要同步另一端。
  视觉方向（参考小象超市/盒马/叮咚等生鲜商超的新人专区）：
    · 浅色面板 + 一屏三张的小卡，替换原先「高饱和整块横幅 + 大图白卡」，
      整块高度约减两成，投影从 18% 降到 4%；
    · 价格 / 抢购 / 角标 / 省¥ / 倒计时时间 = 全站价格口径（--wot-color-theme-primary），
      与商品横滑条同 token；标题、商品名、划线原价 = 固定中性色；
    · 区块底纹 = --nf-main 低透明度斜向渐隐（运营可配，空值跟随页面主题），
      只做一层氛围，不再叠加光环装饰。
  横滑完整性（2026-10-18 运营反馈「不能完整滚动」的两条根因）：
    · .nf-track 必须显式给宽度：小程序块级 flex 轨道量不到溢出内容，最后一张卡滑不全；
    · 自动前进的滚动上限必须用 SelectorQuery 实测，不能按 750rpx 硬算（外层区块还有 paddingX）。
-->
<style scoped lang="scss">
/* 浅色面板：白底上叠一层 91% 白罩 + 主色斜向渐隐，等效 9% 淡色底纹；
   不用伪元素，避免在部分小程序 WebView 上出现层级/裁切差异 */
.nf-panel { position: relative; overflow: hidden; padding: 20rpx 20rpx 16rpx; border: 1rpx solid #f2e7e2; border-radius: 24rpx; background: linear-gradient(rgb(255 255 255 / 91%), rgb(255 255 255 / 91%)), linear-gradient(135deg, var(--nf-main, var(--wot-color-theme-primary, #ff2237)) 0%, rgb(255 255 255 / 0%) 58%), #fff; box-shadow: 0 4rpx 16rpx rgb(31 35 41 / 4%); }
.nf-head { display: flex; align-items: center; gap: 8rpx; }
/* 标题前的「新」字标：小面积品牌渐变，承担促销识别，不靠整块色面 */
.nf-mark { display: flex; flex: 0 0 auto; align-items: center; justify-content: center; width: 32rpx; height: 32rpx; border-radius: 8rpx; background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff7a45), var(--wot-color-theme-primary, #ff4d4f)); color: #fff; font-size: 19rpx; font-weight: 700; line-height: 1; }
.nf-title { color: #1f2329; font-size: 30rpx; font-weight: 700; letter-spacing: 0.4rpx; line-height: 1; }
.nf-subtitle { flex: 0 0 auto; padding: 2rpx 8rpx; border-radius: 4rpx; background: #fff0eb; color: var(--wot-color-theme-primary, #ff2237); font-size: 18rpx; font-weight: 600; line-height: 1.5; }
/* 倒计时：浅色面板上直接排字，前缀中性灰、时间走全站价格色 */
.nf-countdown { display: flex; margin-left: auto; align-items: baseline; gap: 6rpx; font-variant-numeric: tabular-nums; }
.nf-cd-label { color: #8f959e; font-size: 20rpx; }
.nf-cd-time { color: var(--wot-color-theme-primary, #ff2237); font-size: 22rpx; font-weight: 600; }
.nf-perks { margin-top: 6rpx; color: #8f959e; font-size: 20rpx; line-height: 1.4; }
.nf-scroll { width: 100%; margin-top: 14rpx; padding-bottom: 8rpx; }
/* 轨道 nowrap + 显式宽度保证小程序横滑能滚到最后一张；卡片内文案各自复位换行 */
.nf-track { display: flex; gap: 12rpx; width: max-content; white-space: nowrap; }
/* 一屏三张：3×200 + 2×12 = 624rpx，外层 12px 区块内边距下仍能完整露出三张 */
.nf-card { display: flex; overflow: hidden; box-sizing: border-box; width: 200rpx; flex: 0 0 auto; flex-direction: column; border: 1rpx solid #f2eae6; border-radius: 16rpx; background: #fff; box-shadow: 0 4rpx 16rpx rgb(31 35 41 / 4%); white-space: normal; }
.nf-media { position: relative; flex: 0 0 auto; }
/* 贴边角标：左上角随卡片圆角，不再浮起投影 */
.nf-badge { position: absolute; top: 0; left: 0; z-index: 1; padding: 4rpx 10rpx; border-radius: 16rpx 0 12rpx 0; background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff7a45), var(--wot-color-theme-primary, #ff4d4f)); color: #fff; font-size: 18rpx; font-weight: 600; letter-spacing: 0.2rpx; }
/* 图片垫底用中性灰：商品图多为不透明非方图，aspectFill 裁切后不会露出彩底；
   与商品横滑条/商品分组的 #f3f4f6 口径一致 */
.nf-img { display: block; width: 100%; height: 200rpx; background: #f3f4f6; }
.nf-img--empty { display: flex; align-items: center; justify-content: center; color: var(--wot-color-theme-primary, #ff2237); font-size: 22rpx; }
.nf-body { min-width: 0; padding: 10rpx 12rpx 12rpx; }
/* 名称固定两行高：同一排卡片价格行必须齐平；两行 clamp 兜住超长商品名 */
.nf-name { display: -webkit-box; overflow: hidden; height: 64rpx; color: #303133; font-size: 24rpx; font-weight: 500; line-height: 32rpx; overflow-wrap: break-word; white-space: normal; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.nf-price-row { display: flex; margin-top: 4rpx; height: 40rpx; align-items: center; justify-content: space-between; gap: 6rpx; }
.nf-price { display: flex; overflow: hidden; min-width: 0; align-items: baseline; color: var(--wot-color-theme-primary, #ff2237); font-size: 30rpx; font-weight: 700; line-height: 1; white-space: nowrap; }
.nf-price-symbol { margin-right: 2rpx; font-size: 18rpx; font-weight: 600; }
/* 「抢」改为小胶囊：与一屏三张的窄卡同尺度，圆形大按钮在 200rpx 卡里过重 */
.nf-btn { display: flex; flex: 0 0 auto; align-items: center; justify-content: center; height: 36rpx; padding: 0 14rpx; border-radius: 999rpx; box-shadow: 0 2rpx 6rpx rgb(255 77 79 / 22%); background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff7a45), var(--wot-color-theme-primary, #ff4d4f)); color: #fff; font-size: 20rpx; font-weight: 600; line-height: 1; }
/* 加购请求在途：只降透明度，不改尺寸/位置——尺寸是两端 parity 测试锁定的值，
   而透明度足以表达「已点过，别重复点」，与 quick-cart-button 的 pending 态同口径 */
.nf-btn--pending { opacity: 0.6; }
.nf-meta { display: flex; margin-top: 4rpx; height: 24rpx; align-items: center; justify-content: space-between; gap: 6rpx; }
.nf-original { color: #b6b9c0; font-size: 18rpx; text-decoration: line-through; }
.nf-save { color: var(--wot-color-theme-primary, #ff2237); font-size: 18rpx; font-weight: 600; }
</style>
