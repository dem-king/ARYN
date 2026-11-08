<script setup lang="ts">
import type { GoodsSocialBubble } from '@/api/product/spu'
import type { AppSeckillGoodsVO } from '@/api/promotion'
import type { ClearancePriceDisplay } from '@/utils/clearance-price'
import type { DiscountPriceDisplay } from '@/utils/discount-price'
import type { SpecRowView } from '@/utils/goods-purchase'
import type { MemberPriceDisplay } from '@/utils/member-price'
import type { NewbiePriceDisplay } from '@/utils/newbie-price'

// @ts-expect-error: mp-html type declaration issue
import mpHtml from 'mp-html/dist/uni-app/components/mp-html/mp-html'
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import PriceText from '@/components/price-text/index.vue'
import { useMallThemeStore } from '@/store/mallThemeStore'
import { useShipContextStore } from '@/store/shipContextStore'
import { formatStripAmount } from '@/utils/discount-price'
import { resolveDeliveryRow } from '@/utils/goods-purchase'
import { stockHintText } from '@/utils/stock-display'
import GoodsAttrTags from './GoodsAttrTags.vue'

interface Props {
  goodsSpu: any
  couponList: Array<any>
  /** 规格行视图：父级在注入「默认」占位规格前用 resolvePurchaseDecision/resolveSpecRow 算好 */
  specRow: SpecRowView
  address: any
  /** 当前 SKU 的秒杀信息；null 表示无进行中的秒杀 */
  seckillInfo?: AppSeckillGoodsVO | null
  /**
   * 当前 SKU 的会员专享价展示：父级已按「秒杀 > 会员价 > 新人价 > 限时折扣」的
   * 优先级算好，上层活动命中时传的是空展示，这里不用再重复判定优先级。
   * 未达标/未登录时 showMemberPrice=false，主价保持原价、只渲染 guideText 引导。
   */
  memberDisplay: MemberPriceDisplay
  /**
   * 当前 SKU 的有效出清价：父级已按「出清价 vs 会员价取低」收敛，
   * 命中时会员价展示为空，价格区只解释出清价。
   */
  clearanceDisplay: ClearancePriceDisplay
  /**
   * 当前 SKU 的新人价展示：父级已按「秒杀 > 会员价 > 新人价」的优先级算好，
   * 秒杀/会员价命中时传的是空展示，这里不用再重复判定优先级。
   */
  newbieDisplay: NewbiePriceDisplay
  /**
   * 当前 SKU 的限时折扣展示：父级已按「秒杀 > 会员价 > 新人价 > 限时折扣」的
   * 优先级算好，上层活动命中时传的是空展示，这里不用再重复判定优先级。
   */
  discountDisplay: DiscountPriceDisplay
  /** 口碑气泡（详情接口随 goodsSpu.socialBubble 下发）；null=未达门槛，整颗不渲染 */
  socialBubble?: GoodsSocialBubble | null
}

interface Emits {
  (e: 'swiper', obj: any): void
  (e: 'showCoupon'): void
  (e: 'openSkuPopup', value: number): void
  (e: 'toAddress'): void
  (e: 'collect'): void
  (e: 'vipActivate'): void
  (e: 'share'): void
  (e: 'gotoComment'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const shipContextStore = useShipContextStore()
const mallThemeStore = useMallThemeStore()
const { t } = useI18n()

/**
 * 真实状态栏高度（px），供宽屏首屏下移 / 图集吸顶偏移使用。
 *
 * 本页 navigationStyle: custom，自绘顶栏是 fixed 覆盖层、页面内容从 y=0 开始；
 * 宽屏双栏若贴 y=0，右栏价格条会被顶栏压住，所以首屏要整段下移「状态栏 + 顶栏内容高」。
 * 不能用 CSS 的 --status-bar-height：它在 mp-weixin 是编译期常量 25px（dist app.wxss
 * 可核实），刘海/挖孔机的真实值更大，会漏出一截被顶栏遮住的右栏。
 * 这里用运行时实测值下发成 CSS 变量，只被宽屏段消费；折叠态无规则引用它，手机端零变化。
 */
const statusBarHeight = uni.getWindowInfo().statusBarHeight ?? 0
const wideNavOffsetStyle = { '--goods-nav-offset': `${statusBarHeight}px` }

/** 轮播页码：参考商超详情页的 1/5 角标，用户能感知还有多少张图 */
const currentImage = ref(0)
const imageCount = computed(() => props.goodsSpu?.spuUrls?.length || 0)

/** 库存提示：售罄/低库存时的一行稀缺文案；充足或未知为空串（整行隐藏） */
const stockHint = computed(() => {
  if (!stockHintText(props.goodsSpu?.stock))
    return ''
  const stock = Number(props.goodsSpu?.stock)
  return stock <= 0
    ? t('product.stockSoldOut')
    : t('product.stockLeft', { count: stock })
})

function onSwiperChange(event: any) {
  currentImage.value = Number(event?.current) || 0
}

/**
 * 口碑气泡：社证文案（服务端成文，与商品卡同源同门槛）+ 好评率，最多两条轮换。
 *
 * 自动动效总开关（mallThemeStore.autoMotionEnabled）约束两件事：
 * - 弹出：开启 → 挂载 800ms 后弹出（一次性 CSS 动画）；关闭 → 首帧即静态显示，内容不丢；
 * - 轮换：开启且两条文案齐备 → 4s 定时切换（diy-search-bar 同款 watch 自愈启停）；
 *   只有一条文案时不开定时器。
 */
const bubbleMessages = computed(() =>
  [props.socialBubble?.socialText, props.socialBubble?.praiseText].filter(Boolean) as string[],
)
const bubbleVisible = ref(!mallThemeStore.autoMotionEnabled)
const bubbleIndex = ref(0)
let bubbleShowTimer: ReturnType<typeof setTimeout> | null = null
let bubbleRotateTimer: ReturnType<typeof setInterval> | null = null

function scheduleBubbleShow() {
  if (bubbleShowTimer) {
    clearTimeout(bubbleShowTimer)
    bubbleShowTimer = null
  }
  if (!mallThemeStore.autoMotionEnabled) {
    bubbleVisible.value = true
    return
  }
  bubbleShowTimer = setTimeout(() => {
    bubbleVisible.value = true
  }, 800)
}

function startRotation() {
  stopRotation()
  if (!mallThemeStore.autoMotionEnabled)
    return
  if (bubbleMessages.value.length <= 1)
    return
  bubbleRotateTimer = setInterval(rotateMessage, 4000)
}

/**
 * 文案切换动画（三态类切换，小程序不支持 <transition>）：
 * idle 常驻 → leaving 上移淡出 → 换字后 entering 下方无过渡就位 → 回 idle 滑入淡上。
 */
const bubbleTextState = ref<'idle' | 'leaving' | 'entering'>('idle')
let bubbleSwapTimer: ReturnType<typeof setTimeout> | null = null

function rotateMessage() {
  if (bubbleMessages.value.length <= 1 || bubbleTextState.value !== 'idle')
    return
  bubbleTextState.value = 'leaving'
  bubbleSwapTimer = setTimeout(() => {
    bubbleIndex.value = (bubbleIndex.value + 1) % bubbleMessages.value.length
    bubbleTextState.value = 'entering'
    // 下一拍再释放回 idle：让 entering 的「无过渡跳变」先落定，随后才触发滑入过渡
    bubbleSwapTimer = setTimeout(() => {
      bubbleTextState.value = 'idle'
    }, 50)
  }, 200)
}

function stopRotation() {
  if (bubbleRotateTimer) {
    clearInterval(bubbleRotateTimer)
    bubbleRotateTimer = null
  }
  if (bubbleSwapTimer) {
    clearTimeout(bubbleSwapTimer)
    bubbleSwapTimer = null
  }
  bubbleTextState.value = 'idle'
}

onMounted(() => {
  scheduleBubbleShow()
  startRotation()
})
// 主题拉取晚于挂载：开关异步到达后重新评估弹出与轮换（diy-search-bar 同款）
watch([() => mallThemeStore.autoMotionEnabled, bubbleMessages], () => {
  scheduleBubbleShow()
  startRotation()
})
onBeforeUnmount(() => {
  if (bubbleShowTimer)
    clearTimeout(bubbleShowTimer)
  stopRotation()
})

function onBubbleTap() {
  emit('gotoComment')
}

/**
 * 价格区主价：秒杀 > 会员价 > 新人价 > 限时折扣 > 销售价（与后端 OrderPriceComputeService 的
 * 「拼团 > 秒杀 > 会员价 > 新人价 > 限时折扣 > 原价」同向，前端只覆盖这个页面能拿到的层级）。
 */
const mainPrice = computed(() => {
  if (props.seckillInfo)
    return props.seckillInfo.seckillPrice
  if (props.memberDisplay.showMemberPrice)
    return props.memberDisplay.memberPrice
  if (props.clearanceDisplay.showClearancePrice)
    return props.clearanceDisplay.clearancePrice
  if (props.newbieDisplay.showNewbiePrice)
    return props.newbieDisplay.newbiePrice
  if (props.discountDisplay.showDiscountPrice)
    return props.discountDisplay.discountPrice
  return props.goodsSpu?.salesPrice
})

/**
 * 价格区划线价：
 * - 秒杀：划当前售价（后端口径：秒杀价基于 salesPrice 降）；
 * - 会员价：划会员价里的 originalPrice（后端回填的当前售价），
 *   与新人价同口径——不划商品原价，避免把会员价说成更大折扣；
 * - 新人价：划新人价里的 originalPrice（后端回填的当前售价），
 *   没有则不划——自己划自己只会制造噪音；
 * - 无活动：沿用既有「售价 < 原价」的划线口径（与 diy/price-display 同源）。
 */
const strikePrice = computed(() => {
  if (props.seckillInfo)
    return props.goodsSpu?.salesPrice
  if (props.memberDisplay.showMemberPrice)
    return props.memberDisplay.originalPrice
  if (props.clearanceDisplay.showClearancePrice)
    return props.clearanceDisplay.originalPrice
  if (props.newbieDisplay.showNewbiePrice)
    return props.newbieDisplay.originalPrice
  // 限时折扣的基准价是 SKU 当前售价（会场卡片同样划这个价），
  // 不能继续划商品原价（38.87）——那会把 8 折说成 7.7 折，与会场口径矛盾
  if (props.discountDisplay.showDiscountPrice)
    return props.discountDisplay.originalPrice
  return props.goodsSpu?.salesPrice < props.goodsSpu?.originalPrice
    ? props.goodsSpu?.originalPrice
    : null
})

/** 「省 ¥x」：有划线价才展示，给价格区一个正向的成交理由 */
const savingAmount = computed(() => {
  const main = Number(mainPrice.value)
  const strike = Number(strikePrice.value)
  if (!Number.isFinite(main) || !Number.isFinite(strike) || strike <= main)
    return ''
  return (strike - main).toFixed(2)
})

/**
 * 商详首屏卖点：后台富文本录入、服务端白名单清洗后的 HTML 片段。
 * 允许逐商品配色（span 的 color 等），为空时整块不渲染。
 */
const introductionHtml = computed(() =>
  String(props.goodsSpu?.introduction || '').trim(),
)

/**
 * 副标题降级为「无卖点时的兜底短描述」，与卖点富文本不并排展示：
 * 小象/盒马商详只有一个描述面（描述段落跟在属性标签后），而运营录入时
 * 副标题往往就是卖点的前半句，并排必然重复。列表/购物车等卡片面仍照常
 * 展示 subTitle 的一行卖点，不受本规则影响。
 */
const showSubtitle = computed(() => {
  const subtitle = String(props.goodsSpu?.subTitle || '').trim()
  const name = String(props.goodsSpu?.name || '').trim()
  return subtitle.length > 0 && subtitle !== name && introductionHtml.value.length === 0
})

/**
 * 卖点的版式约束：只放开行内样式，段落边距清零。
 * 后台能调颜色/加粗/字号，但不能靠段落外边距把标题卡撑开。
 */
const introTagStyle = {
  p: 'margin: 0;',
}

/** 可享新人价时的限购提示；不适用时为空串（避免渲染空条） */
const newbieLimitText = computed(() =>
  props.newbieDisplay.showNewbiePrice
    ? t('product.newbieLimit', { count: props.newbieDisplay.limitPerUser })
    : '',
)

/**
 * 会员价引导文案：未达标/未登录时「会员专享 ¥x」。
 *
 * 达标时主价已切会员价（showMemberPrice），不再重复引导；无活动/价格倒挂时
 * guideText 为 null，整行隐藏。只在价格区下方透出引导，不改变主价与划线口径。
 */
const memberGuideText = computed(() => {
  if (!props.memberDisplay.guideText || props.memberDisplay.memberPrice == null)
    return ''
  return t('product.memberOnly', { price: formatStripAmount(props.memberDisplay.memberPrice) })
})

/**
 * 价格区下方是否存在活动说明条。
 *
 * 标题卡原设计用 `margin-top: -32px` 上提、与价格渐变区咬合；一旦中间插入了
 * 活动条（秒杀/会员价/新人价/限时折扣），上提量会把标题拖进条内、首行被裁掉。
 * 有条时取消上提，让标题卡排在条下方。
 */
const hasPriceStrip = computed(() =>
  Boolean(props.seckillInfo)
  || Boolean(memberGuideText.value)
  || Boolean(newbieLimitText.value || props.newbieDisplay.ineligibleText)
  || props.discountDisplay.showDiscountPrice,
)

/** 秒杀剩余库存：后端 Redis 缺失时以「总量-已售」兜底 */
const seckillRemaining = computed(() => {
  const info = props.seckillInfo
  if (!info)
    return 0
  return info.remainingStock ?? Math.max(0, (info.seckillStock || 0) - (info.soldCount || 0))
})

/** 场次是否已结束 */
const seckillFinished = ref(false)
const seckillCountdownText = ref('')

let seckillTimer: ReturnType<typeof setInterval> | null = null

/** 每秒刷新秒杀倒计时；场次结束时停表并展示「已结束」 */
function startSeckillCountdown() {
  stopSeckillCountdown()
  if (!props.seckillInfo?.sessionEndTime) {
    seckillFinished.value = true
    return
  }
  updateSeckillCountdown()
  seckillTimer = setInterval(updateSeckillCountdown, 1000)
}

function updateSeckillCountdown() {
  const end = props.seckillInfo?.sessionEndTime
  if (!end) {
    seckillFinished.value = true
    stopSeckillCountdown()
    return
  }
  // iOS 下 new Date('yyyy-MM-dd HH:mm:ss') 解析失败，统一替换斜杠
  const diff = new Date(end.replace(/-/g, '/')).getTime() - Date.now()
  if (diff <= 0) {
    seckillFinished.value = true
    seckillCountdownText.value = ''
    stopSeckillCountdown()
    return
  }
  seckillFinished.value = false
  const totalSeconds = Math.floor(diff / 1000)
  const hours = String(Math.floor(totalSeconds / 3600)).padStart(2, '0')
  const minutes = String(Math.floor((totalSeconds % 3600) / 60)).padStart(2, '0')
  const seconds = String(totalSeconds % 60).padStart(2, '0')
  seckillCountdownText.value = `${hours}:${minutes}:${seconds}`
}

function stopSeckillCountdown() {
  if (seckillTimer) {
    clearInterval(seckillTimer)
    seckillTimer = null
  }
}

watch(() => props.seckillInfo, (info) => {
  if (info) {
    startSeckillCountdown()
  }
  else {
    stopSeckillCountdown()
    seckillFinished.value = false
    seckillCountdownText.value = ''
  }
}, { immediate: true })

onBeforeUnmount(stopSeckillCountdown)

/**
 * 发货行：已绑定船舶上下文时订单将按内部配送（delivery_way=4）履约，
 * 展示「配送至 船舶」+ 靠港信息（与结算页同口径），而不是收货地址——
 * 船供订单不走收货地址，原先显示「配送至：某某小区」是误导。
 * 普通场景仅收费商品（freight_type='1'）展示运费，「包邮」不再上商详
 * （小象/盒马同口径，与商品卡删「包邮」chip 的决策对齐），可见性与
 * 文案口径都在共享层，地址改选交给结算页。
 */
const deliveryRow = computed(() => resolveDeliveryRow({
  hasVesselContext: shipContextStore.hasVesselContext,
  vesselName: shipContextStore.vesselName,
  portName: shipContextStore.portName,
  berth: shipContextStore.berth,
  deliveryWindowStart: shipContextStore.deliveryWindowStart,
  deliveryWindowEnd: shipContextStore.deliveryWindowEnd,
  freightType: props.goodsSpu?.freightType,
  fixedFreightPrice: props.goodsSpu?.fixedFreightPrice,
  addressText: formatAddressText(props.address),
}))

/** 配送行主文案：共享层负责口径判定（visible），文案在这里按当前语言渲染 */
const deliveryText = computed(() => {
  if (shipContextStore.hasVesselContext) {
    const vessel = (shipContextStore.vesselName ?? '').trim() || t('product.currentVessel')
    return t('product.deliveryToVessel', { vessel })
  }
  return t('product.freightAmount', { amount: props.goodsSpu?.fixedFreightPrice ?? 0 })
})

/** 白卡内行的分隔线归属：只有后面还有可见行时才带底边框，末行不留悬空线 */
function cellClass(hasFollower: boolean) {
  return hasFollower
    ? 'goods-cell goods-cell--border flex items-center py-3'
    : 'goods-cell flex items-center py-3'
}

/** 配送行副文案：船舶场景沿用共享层拼好的港口/靠港时段（业务数据，语言无关） */
const deliveryDetail = computed(() => {
  if (shipContextStore.hasVesselContext)
    return deliveryRow.value.detail
  const address = formatAddressText(props.address)
  return address ? t('product.deliveryToAddress', { address }) : ''
})

function formatAddressText(address: any) {
  if (!address?.provinceName)
    return ''
  return [address.provinceName, address.cityName, address.areaName, address.detailAddress]
    .filter(Boolean)
    .join('')
}

function handleSwiper(obj: any) {
  emit('swiper', obj)
}

function showCoupon() {
  emit('showCoupon')
}

function openSkuPopup(value: number) {
  emit('openSkuPopup', value)
}

/** 内部配送的船舶/靠港在购物车与工作台切换，行本身只做展示 */
function onDeliveryRowTap() {
  if (deliveryRow.value.tappable)
    emit('toAddress')
}

function handleCollect() {
  emit('collect')
}

const proxy = getCurrentInstance()?.proxy

// 获取scroll-pic元素位置的方法
function getScrollPicRect() {
  return new Promise((resolve, reject) => {
    if (!proxy) {
      reject(new Error('组件实例 proxy 为 null'))
      return
    }

    const query = uni.createSelectorQuery().in(proxy)
    query
      .select('#scroll-pic')
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
// 分享
function handleShare() {
  emit('share')
}
// 暴露方法给父组件
defineExpose({
  getScrollPicRect,
})
</script>

<template>
  <view id="scroll-pic" class="goods-info" :style="wideNavOffsetStyle">
    <!-- 宽屏包裹层：折叠态（<600px）不挂任何样式，仅作 ≥600px 双栏的左列图集；规则见文末宽屏段 -->
    <view class="goods-gallery">
      <view class="goods-swiper">
        <wd-swiper
          height="750rpx" :list="goodsSpu?.spuUrls" autoplay :indicator="{ type: 'dots-bar' }"
          @click="handleSwiper" @change="onSwiperChange"
        />
        <view v-if="imageCount > 1" class="goods-swiper__counter">
          {{ currentImage + 1 }}/{{ imageCount }}
        </view>
        <!-- 口碑气泡：社证/好评率服务端成文，点击跳评价区；压图浮层用固定深色（与页码角标同惯例） -->
        <view
          v-if="bubbleVisible && bubbleMessages.length"
          class="goods-swiper__bubble"
          :class="{ 'goods-swiper__bubble--enter': mallThemeStore.autoMotionEnabled }"
          @tap="onBubbleTap"
        >
          <view v-if="socialBubble?.avatars?.length" class="goods-swiper__bubble-avatars">
            <image
              v-for="(url, index) in socialBubble.avatars.slice(0, 3)"
              :key="index"
              class="goods-swiper__bubble-avatar"
              :src="url"
              mode="aspectFill"
            />
          </view>
          <text
            class="goods-swiper__bubble-text"
            :class="{
              'goods-swiper__bubble-text--leaving': bubbleTextState === 'leaving',
              'goods-swiper__bubble-text--entering': bubbleTextState === 'entering',
            }"
          >
            {{ bubbleMessages[bubbleIndex] }}
          </text>
        </view>
      </view>
    </view>
    <!-- 宽屏包裹层：折叠态（<600px）不挂任何样式，仅作 ≥600px 双栏的右栏（价格条 → 优惠/规格/配送） -->
    <view class="goods-body">
      <view
        class="goods-price-bar relative h-72px rounded-t-xl from-primary to-secondary bg-gradient-to-r px-3 py-2 -top-10px"
      >
        <view class="flex items-center justify-between pt-1">
          <!-- 价格：秒杀 > 会员价 > 新人价 > 限时折扣 > 销售价；主价旁的划线价随主价口径切换 -->
          <view class="flex-1">
            <view class="flex flex-wrap items-center">
              <price-text
                class="goods-price-main"
                :value="mainPrice"
                symbol="￥"
              />
              <!-- 会员价角标：白字底上用白色描边胶囊，与新人价角标同层级 -->
              <text v-if="memberDisplay.showMemberPrice" class="member-badge">
                {{ t('product.memberPrice') }}
              </text>
              <text v-if="clearanceDisplay.showClearancePrice" class="clearance-badge">
                {{ t('product.clearancePrice') }}
              </text>
              <!-- 新人价角标：白字底上用白色描边胶囊，不引入额外色块压住渐变主色 -->
              <text v-if="newbieDisplay.showNewbiePrice" class="newbie-badge">
                {{ t('product.newbiePrice') }}
              </text>
              <text v-if="discountDisplay.showDiscountPrice" class="discount-badge">
                {{ discountDisplay.badgeText }}
              </text>
              <text v-if="savingAmount" class="saving-badge">
                {{ t('product.saveAmount', { amount: savingAmount }) }}
              </text>
            </view>
            <wd-text
              v-if="strikePrice != null" color="white" custom-class="pl-5px goods-strike-price" size="28rpx" prefix="￥"
              mode="price" :text="strikePrice" decoration="line-through"
            />
          </view>
          <!-- 收藏分享 -->
          <view class="flex-shrink-0">
            <view class="flex flex-row">
              <view class="flex flex-col items-center" @click="handleCollect">
                <text v-if="goodsSpu?.collectId" class="i-flowbite:heart-solid text-18px text-red-5 text-white!" />
                <text v-else class="i-flowbite:heart-outline text-18px text-white!" />
                <text class="text-12px text-white!">
                  {{ goodsSpu?.collectId ? t('product.favorited') : t('product.favorite') }}
                </text>
              </view>
              <view class="flex flex-col items-center pl-10px" @click="handleShare">
                <text class="i-flowbite:share-all-outline text-18px text-white!" />
                <text class="text-12px text-white!">
                  {{ t('product.share') }}
                </text>
              </view>
            </view>
          </view>
        </view>
      </view>
      <!-- 秒杀条：有进行中的秒杀才展示；已抢完/已结束仅提示，不影响加购 -->
      <view v-if="seckillInfo" class="seckill-strip">
        <view class="seckill-tag">
          {{ t('product.flashSale') }}
        </view>
        <view v-if="!seckillFinished" class="seckill-countdown">
          {{ t('product.endsIn') }}
          <text class="seckill-time">
            {{ seckillCountdownText }}
          </text>
        </view>
        <text v-else class="seckill-over">
          {{ t('product.sessionEnded') }}
        </text>
        <view class="seckill-meta">
          <text v-if="seckillInfo.limitPerUser">
            {{ t('product.limitPerUser', { count: seckillInfo.limitPerUser }) }}
          </text>
          <text v-if="seckillRemaining > 0" class="seckill-remaining">
            {{ t('product.stockLeft', { count: seckillRemaining }) }}
          </text>
          <text v-else class="seckill-remaining">
            {{ t('product.soldOut') }}
          </text>
        </view>
      </view>
      <!--
      新人价条：只承载「为什么价格是这样」的信息，不做第二个活动入口。
      两种情况互斥：
        · 可享 → 限购提示（后端硬校验每人 1 件，提前说明避免结算才被拒）；
        · 确定不符合 → 说明原因，价格区已回落原价，否则用户会以为页面标错价。
      未登录时后端 eligible=null，display 按可享处理，不出现「你没有资格」这类误伤文案。
    -->
      <view v-if="newbieLimitText || newbieDisplay.ineligibleText" class="newbie-strip">
        <text v-if="newbieLimitText" class="newbie-strip-text">
          {{ newbieLimitText }}
        </text>
        <text v-else class="newbie-strip-text is-muted">
          {{ newbieDisplay.ineligibleText }}
        </text>
      </view>

      <!--
      会员价引导条：未达标/未登录时透出「会员专享 ¥x」，只做引导不做拦截。
      达标时主价已切会员价（价格区角标已说明），不重复渲染本行；
      价格倒挂等无可引导信息时 guideText 为 null，整行隐藏（不改变主价与划线口径）。
    -->
      <view v-if="memberGuideText" class="member-strip">
        <text class="member-strip-tag">
          {{ t('product.member') }}
        </text>
        <text class="member-strip-text">
          {{ memberGuideText }}
        </text>
        <text class="member-strip-meta">
          {{ t('product.joinToEnjoy') }}
        </text>
      </view>

      <!-- 限时折扣条：解释「为什么主价是折后价」，活动名让用户知道价从哪来 -->
      <view v-if="discountDisplay.showDiscountPrice" class="discount-strip">
        <text class="discount-strip-tag">
          {{ t('product.limitedDiscount') }}
        </text>
        <text class="discount-strip-name">
          {{ discountDisplay.activityName || t('product.activityItem') }}
        </text>
        <text class="discount-strip-meta">
          {{ t('product.autoAppliedAtCheckout') }}
        </text>
      </view>
      <view
        class="goods-title-card relative rounded-xl bg-white dark:bg-[var(--wot-dark-background2)]"
        :class="{ 'goods-title-card--striped': hasPriceStrip }"
      >
        <view class="goods-title-card__main">
          <!--  商品标题 -->
          <wd-text :lines="2" size="36rpx" color="inherit" bold custom-class="goods-title-text" :text="goodsSpu?.name" />
          <!-- 副标题 -->
          <view v-if="showSubtitle" class="goods-title-card__subtitle">
            <wd-text :lines="2" size="24rpx" color="#8a8f99" custom-class="goods-subtitle-text" :text="goodsSpu?.subTitle" />
          </view>
        </view>
        <!--
        运营配置的属性标签（地标/冷藏/上榜…）：等宽铺满，放在标题之后、
        库存提示之前。后端未部署或旧数据无 attrTags 时组件内部整块不渲染，
        首屏与改动前完全一致。
      -->
        <GoodsAttrTags :attr-tags="goodsSpu?.attrTags" />
        <!--
        商详首屏卖点：运营可配的白名单富文本，支持逐商品配色，
        紧跟在属性卡之后；为空时整块不渲染（旧数据与未配置卖点一致）。
      -->
        <view
          v-if="introductionHtml"
          class="goods-title-card__intro text-[#6b7280] dark:text-[var(--wot-dark-color3)]"
        >
          <mp-html :content="introductionHtml" :tag-style="introTagStyle" />
        </view>
        <!--
        库存口径：绝对库存/累计销量不外露（运营敏感数据，自营竞品也不展示），
        只在售罄或低库存时给一行稀缺提示；充足或未知时整行隐藏。
      -->
        <view v-if="stockHint" class="goods-title-card__meta">
          <text class="goods-title-card__meta-item">
            {{ stockHint }}
          </text>
        </view>
      </view>
      <!-- 优惠/规格/发货行共用一张白卡：三行都可能被隐藏（无券、单规格无规格值、包邮商品），
         全隐时整卡不渲染；分隔线按「非末行才带底边框」动态归属，避免末行留悬空线 -->
      <view
        v-if="(couponList && couponList.length > 0) || specRow.visible || deliveryRow.visible"
        class="goods-cells mx-3 my-2 rounded-xl p-3 bg-white dark:bg-[var(--wot-dark-background2)]"
      >
        <view class="overflow-hidden rounded-lg">
          <view
            v-if="couponList && couponList.length > 0"
            :class="cellClass(specRow.visible || deliveryRow.visible)"
            hover-class="goods-cell--pressed"
            @click="showCoupon"
          >
            <view class="goods-cell__label w-80rpx flex-shrink-0 text-28rpx text-gray-800">
              {{ t('product.discounts') }}
            </view>
            <view class="goods-cell__value mx-5 flex-1 text-26rpx text-gray-600">
              <view class="line-clamp-1 flex">
                <view v-for="(item, index) in couponList.slice(0, 3)" :key="index" class="coupons_css">
                  {{ item.couponName }}
                  <view class="leftdot" />
                  <view class="rightdot" />
                </view>
              </view>
            </view>
            <view class="flex-shrink-0 text-gray-400">
              <wd-icon :size="16" name="arrow-right" color="var(--wot-cell-arrow-color)" />
            </view>
          </view>
          <!--
          规格行：多规格是「选择规格」入口；单规格显示真实规格值（没有规格值时整行隐藏，
          规格信息在商品名/船供箱规里），点击只调数量——弹层对单规格不渲染规格选择区。
        -->
          <view
            v-if="specRow.visible"
            :class="cellClass(deliveryRow.visible)"
            hover-class="goods-cell--pressed"
            @click="openSkuPopup(2)"
          >
            <view class="goods-cell__label w-80rpx flex-shrink-0 text-28rpx text-gray-800">
              {{ t('product.specs') }}
            </view>
            <view class="goods-cell__value mx-5 flex-1 text-left text-26rpx text-gray-600">
              {{ specRow.text }}
            </view>
            <view class="flex-shrink-0 text-gray-400">
              <wd-icon :size="16" name="arrow-right" color="var(--wot-cell-arrow-color)" />
            </view>
          </view>
          <view
            v-if="deliveryRow.visible"
            class="goods-cell flex items-center py-3"
            :hover-class="deliveryRow.tappable ? 'goods-cell--pressed' : 'none'"
            @click="onDeliveryRowTap"
          >
            <view class="goods-cell__label w-80rpx flex-shrink-0 text-28rpx text-gray-800">
              {{ t('product.delivery') }}
            </view>
            <view class="goods-cell__value mx-5 flex-1 text-left text-26rpx text-gray-600">
              {{ deliveryText }}
              <view v-if="deliveryDetail" class="line-clamp-1 text-12px text-gray-400">
                {{ deliveryDetail }}
              </view>
            </view>
            <view v-if="deliveryRow.tappable" class="flex-shrink-0 text-gray-400">
              <wd-icon :size="16" name="arrow-right" color="var(--wot-cell-arrow-color)" />
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.goods-swiper {
  position: relative;

  // 与参考商详一致：页码常驻右下，图片再少也能一眼看到总张数
  &__counter {
    position: absolute;
    right: 24rpx;
    bottom: 44rpx;
    padding: 4rpx 16rpx;
    border-radius: 999rpx;
    background: rgb(0 0 0 / 34%);
    color: #fff;
    font-size: 22rpx;
    line-height: 1.5;
  }

  // 口碑气泡：悬浮在左上角（fixed），滚出图集区也不消失；top 让出自绘顶栏（状态栏 + 44px 栏高）。
  // 注意：若祖先链出现 transform/filter 包含块，fixed 会退化成相对该祖先定位，验收时要滚动确认。
  &__bubble {
    position: fixed;
    top: calc(var(--goods-nav-offset, var(--status-bar-height, 0px)) + 44px + 12rpx);
    left: 20rpx;
    z-index: 1;
    display: flex;
    align-items: center;
    max-width: 62%;
    padding: 8rpx 20rpx 8rpx 10rpx;
    border-radius: 999rpx;
    background: rgb(17 17 17 / 55%);
    backdrop-filter: blur(8px);
    color: #fff;
  }

  &__bubble-avatar {
    width: 36rpx;
    height: 36rpx;
    border-radius: 50%;
    border: 2rpx solid rgb(255 255 255 / 80%);
  }

  &__bubble-avatar + &__bubble-avatar {
    margin-left: -12rpx;
  }

  &__bubble-text {
    min-width: 0;
    margin-left: 8rpx;
    overflow: hidden;
    font-size: 22rpx;
    line-height: 1.5;
    text-overflow: ellipsis;
    white-space: nowrap;
    transition: opacity 0.2s ease, transform 0.2s ease;
  }

  // 切换动画三态（配 rotateMessage 的状态机）：上移淡出 → 下方无过渡就位 → 滑入淡上
  &__bubble-text--leaving {
    opacity: 0;
    transform: translateY(-8rpx);
  }

  &__bubble-text--entering {
    transition: none;
    opacity: 0;
    transform: translateY(8rpx);
  }

  &__bubble--enter {
    animation: goods-bubble-pop 0.45s cubic-bezier(0.34, 1.56, 0.64, 1) both;
  }
}

// keyframes 必须在 <style> 块内（写在块外会被 SFC 静默丢弃）
@keyframes goods-bubble-pop {
  from {
    opacity: 0;
    transform: translateY(-10rpx) scale(0.85);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .goods-swiper__bubble--enter {
    animation: none;
  }

  // 切换动画一并退化为直接换字（内容不丢，只是无动效）
  .goods-swiper__bubble-text {
    transition: none;
  }
}

.goods-title-card {
  // 默认上提，与价格渐变区咬合；有活动说明条时由 --striped 取消（见 hasPriceStrip）
  margin-top: -32px;
  padding: 24rpx;
  box-shadow: 0 6rpx 18rpx rgb(22 34 51 / 5%);

  &__main {
    min-width: 0;
  }

  &__subtitle {
    margin-top: 8rpx;
  }

  // 卖点段落：与属性卡之间用细分隔线收口，字号/行高按「一段可读文案」给，
  // 颜色由运营配置的行内样式决定；未配色时落在外层的次级文本色上（弱于标题正文）。
  &__intro {
    margin-top: 16rpx;
    padding-top: 16rpx;
    border-top: 1rpx solid var(--app-border);
    font-size: 26rpx;
    line-height: 1.9;
    word-break: break-word;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 16rpx;
    margin-top: 16rpx;
    padding-top: 16rpx;
    border-top: 1rpx solid var(--app-border);
    color: var(--app-text-3);
    font-size: 22rpx;
  }

  &__meta-value {
    color: var(--app-text);
    font-size: 24rpx;
    font-weight: 600;
  }

  &__meta-divider {
    width: 1rpx;
    height: 18rpx;
    background: var(--app-border);
  }
}

.goods-cell {
  transition: opacity 120ms ease;

  &--border {
    border-bottom: 1rpx solid var(--app-border);
  }

  &--pressed {
    opacity: 0.66;
  }
}

// 秒杀条：紧贴价格渐变区下方，展示倒计时与限购信息
.seckill-strip {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin: -20rpx 16rpx 12rpx;
  padding: 14rpx 20rpx;
  border-radius: 12rpx;
  background: #fff1ee;

  .seckill-tag {
    flex-shrink: 0;
    padding: 4rpx 14rpx;
    border-radius: 8rpx;
    background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff6b35), var(--wot-color-theme-primary, #ff4500));
    color: #fff;
    font-size: 22rpx;
    font-weight: 600;
  }

  .seckill-countdown {
    color: var(--wot-color-theme-primary, #ff2237);
    font-size: 24rpx;

    .seckill-time {
      font-weight: 700;
    }
  }

  .seckill-over {
    color: var(--app-text-3);
    font-size: 24rpx;
  }

  .seckill-meta {
    display: flex;
    flex: 1;
    justify-content: flex-end;
    gap: 16rpx;
    color: var(--app-text-3);
    font-size: 22rpx;

    .seckill-remaining {
      color: var(--wot-color-theme-primary, #ff2237);
    }
  }
}

/**
 * 新人价角标。
 *
 * 价格区底色是 primary→secondary 渐变、文字全白：角标若用高饱和实色块会在白字旁
 * 形成两个互相争夺注意力的色块，且小屏上易与主价糊成一片。
 * 因此用「半透明白底 + 白字 + 细白描边」，只靠形状区分层级，保持白字可读。
 */
.newbie-badge {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border: 1rpx solid rgb(255 255 255 / 70%);
  border-radius: 999rpx;
  background: rgb(255 255 255 / 18%);
  color: #fff;
  font-size: 20rpx;
  font-weight: 600;
  line-height: 1.6;
}

// 主价：整数大、小数小（price-text 组件内用 em 跟随本字号）
.goods-price-main {
  color: #fff;
  font-size: 44rpx;
  font-weight: 700;
  line-height: 1;
}

// 「省￥x」与新人价角标同一套白描边语言，避免在渐变区再引入第二个色块
.saving-badge {
  margin-left: 10rpx;
  padding: 2rpx 12rpx;
  border: 1rpx solid rgb(255 255 255 / 55%);
  border-radius: 999rpx;
  background: rgb(255 255 255 / 12%);
  color: #fff;
  font-size: 20rpx;
  line-height: 1.6;
}

// 有活动条时标题卡不再上提，避免首行被条裁切
.goods-title-card--striped {
  margin-top: 0;
}

// 会员价角标：与新人价/折扣角标同一套白描边语言（价格区为渐变主色底）
.member-badge,
.clearance-badge {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border: 1rpx solid rgb(255 255 255 / 70%);
  border-radius: 999rpx;
  background: rgb(255 255 255 / 18%);
  color: #fff;
  font-size: 20rpx;
  font-weight: 600;
  line-height: 1.6;
}

// 会员价引导条：未达标/未登录时解释「专享价是多少、差哪一级」，独立 class
// 避免与新人价条/折扣条共用样式（三类活动语义与迭代都不同）
.member-strip {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: -20rpx 16rpx 12rpx;
  padding: 14rpx 20rpx;
  border-radius: 12rpx;
  background: #fdf6e7;

  .member-strip-tag {
    flex-shrink: 0;
    padding: 4rpx 14rpx;
    border-radius: 8rpx;
    background: linear-gradient(135deg, #d8b25e, #b8862f);
    color: #fff;
    font-size: 22rpx;
    font-weight: 600;
  }

  .member-strip-text {
    flex: 1;
    overflow: hidden;
    color: #8a5a1c;
    font-size: 22rpx;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .member-strip-meta {
    flex-shrink: 0;
    color: #b0803a;
    font-size: 22rpx;
  }
}

// 限时折扣角标：与新人价角标同一套白描边语言（价格区为渐变主色底）
.discount-badge {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border: 1rpx solid rgb(255 255 255 / 70%);
  border-radius: 999rpx;
  background: rgb(255 255 255 / 18%);
  color: #fff;
  font-size: 20rpx;
  font-weight: 600;
  line-height: 1.6;
}

// 限时折扣条：独立 class，避免以后调秒杀/新人价样式时被连带改掉
.discount-strip {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: -20rpx 16rpx 12rpx;
  padding: 14rpx 20rpx;
  border-radius: 12rpx;
  background: #fff1ee;

  .discount-strip-tag {
    flex-shrink: 0;
    padding: 4rpx 14rpx;
    border-radius: 8rpx;
    background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff6b35), var(--wot-color-theme-primary, #ff4500));
    color: #fff;
    font-size: 22rpx;
    font-weight: 600;
  }

  .discount-strip-name {
    flex: 1;
    overflow: hidden;
    color: #7a3412;
    font-size: 22rpx;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .discount-strip-meta {
    flex-shrink: 0;
    color: #b25a3a;
    font-size: 22rpx;
  }
}

// 新人价条：与秒杀条同位置同节奏，但独立 class——两类活动不能共用样式，
// 否则以后改秒杀样式会顺带改掉新人价（二者语义与后续迭代都不同）
.newbie-strip {
  position: relative;
  z-index: 1;
  margin: -20rpx 16rpx 12rpx;
  padding: 14rpx 20rpx;
  border-radius: 12rpx;
  background: #fff7e8;

  .newbie-strip-text {
    color: #b25a00;
    font-size: 22rpx;

    // 「不符合资格」是解释性文案，弱化一级：不需要用户采取任何动作
    &.is-muted {
      color: #8a6d4b;
    }
  }
}

.coupons_css {
  height: 32upx;
  position: relative;
  margin-right: 20upx;
  margin-bottom: 2rpx;
  text-align: center;
  line-height: 32upx;
  padding: 0 14upx;
  border: 1upx solid var(--wot-color-theme-primary, #ff2237);
  border-radius: 10upx;
  font-size: 22upx;
  color: var(--wot-color-theme-primary, #ff2237);
}

.leftdot {
  position: absolute;
  top: 13upx;
  left: -3upx;
  width: 6upx;
  height: 10upx;
  border-top-right-radius: 20upx;
  border-bottom-right-radius: 20upx;
  border: 1upx solid var(--wot-color-theme-primary, #ff2237);
  background-color: var(--app-card);
  border-left: 0;
}

.rightdot {
  position: absolute;
  top: 13upx;
  right: -3upx;
  width: 6upx;
  height: 10upx;
  border-top-left-radius: 20upx;
  border-bottom-left-radius: 20upx;
  border: 1upx solid var(--wot-color-theme-primary, #ff2237);
  background-color: var(--app-card);
  border-right: 0;
}

/**
 * ===== 折叠屏 / 平板展开态：商详首屏双栏（仅微信小程序，窗口宽 ≥600px） =====
 *
 * 为什么单独一段：rpx 以「窗口宽 = 750rpx」为基准，折叠屏展开到 ~700px 时手机端那套
 * 尺寸会等比放大约 1.8 倍——图集被拉高、正文变成 26px、横向全是空白，「铺满」不等于
 * 好用。本段用 px 重建平板密度：左列图集 46%（吸顶），右列价格/标题/优惠/规格/配送
 * 保底 300px（验收 A6）。
 *
 * 三条硬约束：
 *  1. 只 MP-WEIXIN 生效：H5 / App 产物不含本段；折叠态（<600px）不命中媒体查询，
 *     另外新增的 .goods-gallery / .goods-body 包裹层在窄屏没有任何样式，手机端零变化；
 *  2. 段内禁止 rpx/upx，只用 px 与 App.vue 下发的 --wide-* token（一律带兜底值）；
 *  3. 本段在文件末尾：文件内自定义 class 同名再写一次即可覆盖；模板里的 UnoCSS 工具类
 *     编译产物注入在本段之后、同权时后写者胜，打不过，必须 !important；wd-text 的 size、
 *     wd-swiper 的 height 是组件内联 style，行内优先级最高，也只能 !important。
 */
/* #ifdef MP-WEIXIN */
@media (min-width: 600px) {
  .goods-info {
    /* 顶栏让位：本页是 navigationStyle:custom，顶栏为 fixed 覆盖层、页面内容从 y=0
       开始（手机端让图集钻到透明栏下没问题）。宽屏双栏时右栏若也贴 y=0，价格条会被
       顶栏图标/标题压住；且 sticky 吸顶位移在滚动前也会把图集向下顶同样的距离。
       因此宽屏整段内容统一下移到顶栏之下，并把这个值直接复用为图集吸顶偏移。
       --goods-nav-offset 是模板根节点下发的真实状态栏高度（运行时 uni.getWindowInfo）；
       拿不到时回退 CSS 常量 --status-bar-height（mp-weixin 固定 25px）；折叠态本段不生效。 */
    --goods-wide-nav-offset: calc(var(--goods-nav-offset, var(--status-bar-height, 0px)) + var(--wide-nav, 44px) + 8px);
    display: grid;
    /* 左列图集 46%、右列保底 300px：600px 临界时左列会自动收缩到 ~43%（仍在验收
       A6 的 42–48% 区间内），右列不塌，也就不会撑出横向滚动 */
    grid-template-columns: minmax(0, 46%) minmax(300px, 1fr);
    /* 24px 两栏间距同时是折痕安全区（页面级 token，兜底同值） */
    gap: var(--wide-gutter, 24px);
    /* 吸顶生效的前提：栅格子项不被拉伸成整行高 */
    align-items: start;
    width: 100%;
    box-sizing: border-box;
    padding: var(--goods-wide-nav-offset) var(--wide-pad, 16px) var(--wide-pad, 16px);
  }

  .goods-gallery {
    /* 左列图集吸顶：与上面内容下移量同值（状态栏 + 商详顶栏内容高 + 8px 呼吸位） */
    position: sticky;
    top: var(--goods-wide-nav-offset);
    min-width: 0;
    /* 老基础库不认 min() 时退化为固定平板高度 */
    height: 320px;
    height: min(46vw, 420px);
    overflow: hidden;
    border-radius: var(--wide-radius, 12px);
    background: var(--app-card);

    /* wd-swiper 高度链：外层先逐层接上 100%（组件内 scroll-view 自带 height:100%，无需覆盖） */
    :deep(.wd-swiper) {
      height: 100%;
    }

    /* 这两个内层高度是组件内联 style（模板传入的 750 设计稿值），行内优先级最高，
       必须 !important 才能跟随容器 */
    :deep(.wd-swiper__track) {
      height: 100% !important;
    }

    :deep(.wd-swiper__image),
    :deep(.wd-swiper__video) {
      height: 100% !important;
    }
  }

  .goods-swiper {
    height: 100%;
  }

  .goods-swiper__counter {
    right: 12px;
    bottom: 12px;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 11px;
  }

  /* 口碑气泡宽屏 px 变体：宽屏是平板双栏布局，悬浮改回图集卡内（fixed 会悬到整个视口），
     图集已整体下移到顶栏之下（--goods-wide-nav-offset），气泡 top 不再让位顶栏 */
  .goods-swiper__bubble {
    position: absolute;
    top: 12px;
    left: 12px;
    max-width: 46%;
    padding: 2px 8px 2px 4px;
  }

  .goods-swiper__bubble-avatar {
    width: 18px;
    height: 18px;
    border-width: 1px;
  }

  .goods-swiper__bubble-avatar + .goods-swiper__bubble-avatar {
    margin-left: -6px;
  }

  .goods-swiper__bubble-text {
    margin-left: 4px;
    font-size: 11px;
  }

  /* 右栏：价格条 → 活动条 → 标题卡 → 优惠/规格/配送，纵向 12px 节奏 */
  .goods-body {
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-width: 0;
  }

  .goods-price-bar {
    /* 手机端渐变条靠 Uno -top-10px 上移咬住主图，宽屏是独立卡片、不再重叠；
       Uno 工具类注入在本段之后且同权，必须 !important 才能赢 */
    top: 0 !important;
    /* Uno h-72px 写死高度，宽屏让角标折行时自然撑高 */
    height: auto !important;
    min-height: 72px;
    /* Uno px-3 / py-2 的间距在展开态会被等比放大，改 12/16px 平板内边距 */
    padding: 12px 16px !important;
    /* Uno rounded-t-xl 只圆上两角（手机端咬合主图用），宽屏卡片四角统一宽屏圆角 */
    border-radius: var(--wide-radius, 12px) !important;
  }

  /* 价格区角标：手机端那枚小胶囊在展开态会长到 ~19px，收到 12px 平板密度 */
  .member-badge,
  .clearance-badge,
  .newbie-badge,
  .discount-badge,
  .saving-badge {
    margin-left: 8px;
    padding: 1px 8px;
    border-radius: 999px;
    font-size: 12px;
  }

  /* 活动说明条：手机端用负 margin 上移咬住价格条，宽屏改由 .goods-body 的 gap 控间距 */
  .seckill-strip,
  .newbie-strip,
  .member-strip,
  .discount-strip {
    margin: 0;
    padding: 10px 14px;
    border-radius: 8px;
    gap: 8px;

    .seckill-tag,
    .member-strip-tag,
    .discount-strip-tag {
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 12px;
    }
  }

  .seckill-strip {
    .seckill-countdown,
    .seckill-over {
      font-size: 13px;
    }

    .seckill-meta {
      gap: 8px;
      font-size: 12px;
    }
  }

  .member-strip {
    .member-strip-text {
      font-size: 13px;
    }

    .member-strip-meta {
      font-size: 12px;
    }
  }

  .discount-strip {
    .discount-strip-name {
      font-size: 13px;
    }

    .discount-strip-meta {
      font-size: 12px;
    }
  }

  .newbie-strip {
    .newbie-strip-text {
      font-size: 13px;
    }
  }

  .goods-title-card {
    /* 手机端上提 32px 与价格渐变区咬合；宽屏价格条已独立成卡，回正交给 gap */
    margin-top: 0;
    padding: 16px;
    /* Uno rounded-xl 注入在本段之后且同权，必须 !important 才能换成宽屏圆角 token */
    border-radius: var(--wide-radius, 12px) !important;
    box-shadow: 0 2px 8px rgb(22 34 51 / 5%);
  }

  .goods-title-card__subtitle {
    margin-top: 6px;
  }

  /* 卖点：手机端正文在展开态会被放到 ~24px，收回 14px 平板正文 */
  .goods-title-card__intro {
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--app-border);
    font-size: 14px;
    line-height: 1.8;
  }

  .goods-title-card__meta {
    gap: 8px;
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--app-border);
    font-size: 12px;
  }

  .goods-title-card__meta-value {
    font-size: 12px;
  }

  .goods-title-card__meta-divider {
    width: 1px;
    height: 10px;
  }

  /* 下面 4 处 wd-text 的字号由组件的 size prop 写成内联 style，行内优先级最高，
     只能 !important 覆盖（custom-class 由模板挂载） */
  .goods-price-bar :deep(.goods-price-main) {
    font-size: 22px !important;
  }

  .goods-price-bar :deep(.goods-strike-price) {
    font-size: 13px !important;
  }

  .goods-title-card :deep(.goods-title-text) {
    font-size: 15px !important;
  }

  .goods-title-card :deep(.goods-subtitle-text) {
    font-size: 13px !important;
  }

  /* 优惠/规格/配送列表：外层 Uno mx-3 / my-2 / p-3 / rounded-xl 注入在本段之后且同权，
     必须 !important；标签列宽/内容列外边距/字号这几个工具类同理 */
  .goods-cells {
    margin: 0 !important;
    padding: 4px 16px !important;
    border-radius: var(--wide-radius, 12px) !important;
  }

  .goods-cell {
    /* Uno py-3 的上下内边距在展开态会变成 ~22px */
    padding-top: 10px !important;
    padding-bottom: 10px !important;
  }

  .goods-cell__label {
    /* Uno 的固定标签宽与字号（展开态分别放大到 ~75px / ~26px）注入在后，必须 !important */
    width: 64px !important;
    font-size: 13px !important;
  }

  .goods-cell__value {
    /* Uno 的左右外边距（展开态 ~37px）与字号（~24px）注入在后，必须 !important */
    margin-left: 8px !important;
    margin-right: 8px !important;
    font-size: 13px !important;
  }

  /* 优惠券 chip 用的是手机端那套等比尺寸（不是 Uno 工具类，不受宽度断点约束），
     展开态同样会放大约 1.8 倍；右栏只有 ~300px 宽时三枚 chip 会被行内裁切。
     这里按平板密度重写，两端的半圆缺口（leftdot/rightdot）同步缩放 */
  .coupons_css {
    height: 20px;
    margin-right: 8px;
    margin-bottom: 0;
    padding: 0 8px;
    border-radius: 4px;
    font-size: 12px;
    line-height: 20px;
  }

  .leftdot,
  .rightdot {
    width: 4px;
    height: 6px;
  }

  .leftdot {
    top: 7px;
    left: -2px;
  }

  .rightdot {
    top: 7px;
    right: -2px;
  }
}
/* #endif */
</style>
