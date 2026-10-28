<script setup lang="ts">
import type { GroupBuyActivity } from '@/api/promotion/groupBuyActivity'
import type { GroupBuyMemberVO, GroupBuyRecord } from '@/sub-pages/api/promotion/groupBuyRecord'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getByIds } from '@/api/product/spu'
import { getActivityById } from '@/api/promotion/groupBuyActivity'
import { normalizeRetailGoods } from '@/components/diy/retail-normalizers'
import { getRecordPage, joinGroup, openGroup } from '@/sub-pages/api/promotion/groupBuyRecord'

definePage({
  name: 'group-buy-detail',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '拼团详情',
  },
})

const { t } = useI18n()

/** 头像环最多画几个位置：多人团（groupNum 偏大）折叠成 +N，避免挤爆一行 */
const MAX_RING_SLOTS = 4

const state = reactive<{
  activity: GroupBuyActivity | null
  recordList: GroupBuyRecord[]
  loading: boolean
  loadFailed: boolean
  recordFailed: boolean
}>({
  activity: null,
  recordList: [],
  loading: true,
  loadFailed: false,
  recordFailed: false,
})

const activityId = ref('')
const spuInfo = ref<{ spuName: string, picUrl: string } | null>(null)
const spuImageFailed = ref(false)
/** 头像加载失败标记（key = 环位置 key），失败后退回首字灰圆 */
const failedAvatars = ref<Record<string, boolean>>({})

const globalLoading = useGlobalLoading()
/** 全局 1s ticker：成团倒计时/各团剩余时间共用，组件卸载自动清理 */
const { now } = useCountdownTicker()

const activity = computed(() => state.activity)

onLoad(async (options) => {
  activityId.value = options?.id ?? ''
  if (!activityId.value) {
    state.loading = false
    state.loadFailed = true
    return
  }
  await loadActivity(activityId.value)
})

onShareAppMessage(() => {
  const current = state.activity
  const name = current?.activityName || t('promotion.groupBuy.detail.shareFallbackName')
  const price = current?.groupPrice ? `¥${current.groupPrice}` : ''
  return {
    title: price
      ? t('promotion.groupBuy.detail.shareTitleWithPrice', { name, price })
      : t('promotion.groupBuy.detail.shareTitle', { name }),
    path: `/sub-pages/promotion/group-buy/group-buy-detail/index?id=${current?.id}`,
  }
})

/** 右上角转发引导：小程序端由 open-type="share" 触发；其它端提示走右上角菜单 */
function handleShareTap() {
  // #ifndef MP-WEIXIN
  uni.showToast({ title: t('promotion.groupBuy.detail.shareHint'), icon: 'none' })
  // #endif
}

/** 后端时间串兼容 `yyyy-MM-dd HH:mm:ss`（iOS/部分小程序内核直接 Date.parse 会 NaN） */
function parseTime(value?: string) {
  if (!value)
    return Number.NaN
  const direct = Date.parse(value)
  if (!Number.isNaN(direct))
    return direct
  return Date.parse(value.replace(' ', 'T'))
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

/** 目标时间 → 倒计时分段；已结束/非法时间返回 null */
function countdownPartsFromTimestamp(target: number) {
  if (Number.isNaN(target))
    return null
  const diff = target - now.value
  if (diff <= 0)
    return null
  const totalSeconds = Math.floor(diff / 1000)
  return {
    days: Math.floor(totalSeconds / 86400),
    hours: Math.floor((totalSeconds % 86400) / 3600),
    minutes: Math.floor((totalSeconds % 3600) / 60),
    seconds: totalSeconds % 60,
  }
}

function countdownParts(targetTime?: string) {
  return countdownPartsFromTimestamp(parseTime(targetTime))
}

function remainingText(value?: string) {
  const parts = countdownParts(value)
  if (!parts)
    return ''
  const clock = `${pad(parts.hours)}:${pad(parts.minutes)}:${pad(parts.seconds)}`
  return parts.days > 0 ? t('promotion.common.daysClock', { clock, days: parts.days }) : clock
}

function formatPrice(value?: number) {
  if (typeof value !== 'number' || !Number.isFinite(value))
    return ''
  return value.toFixed(2)
}

const groupPriceText = computed(() => formatPrice(activity.value?.groupPrice))
const originalPriceText = computed(() => formatPrice(activity.value?.originalPrice))
const goodsName = computed(() =>
  spuInfo.value?.spuName || activity.value?.activityName || t('promotion.groupBuy.detail.goodsFallback'),
)
const goodsImage = computed(() => spuInfo.value?.picUrl || '')
const showGoodsImage = computed(() => Boolean(goodsImage.value) && !spuImageFailed.value)
const showActivityName = computed(() => Boolean(spuInfo.value?.spuName && activity.value?.activityName))

/**
 * 「已拼 N 件」：读后端合成的 soldCount（运营虚增 virtualNum + 真实已付款参团件数），
 * 真实成团/退款实时反映；老接口未下发 soldCount 时回落到 virtualNum。>0 才展示。
 */
const soldText = computed(() => {
  const count = activity.value?.soldCount ?? activity.value?.virtualNum ?? 0
  return count > 0 ? t('promotion.groupBuy.card.sold', { count }) : ''
})

/**
 * 标签组（第二轮：4 个 → 2 个）：
 * 只保留「N人成团」「限购N件」两个硬约束；「N小时内成团」并入倒计时行前缀、
 * 「已拼N件」升为「正在拼的团」分区头的社会证明 —— 一张卡并列四个灰胶囊
 * 是"写了没错但不写也行"的信息堆叠（竞品同屏最多两个标签）。
 */
const baseTags = computed(() => {
  const current = activity.value
  if (!current)
    return []
  const tags: string[] = []
  if ((current.groupNum ?? 0) > 0)
    tags.push(t('promotion.groupBuy.card.badge', { count: current.groupNum }))
  if ((current.limitNum ?? 0) > 0)
    tags.push(t('promotion.groupBuy.card.limit', { count: current.limitNum }))
  return tags
})

/** 成团时限（小时）：活动未配置时按 24 小时口径 */
const groupExpireHours = computed(() => activity.value?.groupExpireHours || 24)

/**
 * 成团倒计时：取进行中团最早的 expireAt；没有进行中团回落到活动 endedAt。
 * 两个来源都过期则不渲染倒计时行。前置词承担「N 小时内成团」语义。
 */
const groupCountdown = computed(() => {
  const targets = state.recordList
    .filter(record => record.groupStatus === '0')
    .map(record => parseTime(record.expireAt))
    .filter(target => !Number.isNaN(target) && target > now.value)
  const hasGroupTarget = targets.length > 0
  const target = hasGroupTarget ? Math.min(...targets) : parseTime(activity.value?.endedAt)
  if (Number.isNaN(target) || target <= now.value)
    return null
  const parts = countdownPartsFromTimestamp(target)
  if (!parts)
    return null
  return {
    ...parts,
    label: hasGroupTarget
      ? t('promotion.groupBuy.detail.countdownGroup', { hours: groupExpireHours.value })
      : t('promotion.groupBuy.detail.countdownActivity'),
  }
})

/** 「正在拼的团」：进行中 + 已拼成；拼团失败的不再出现在这一栏 */
const displayRecords = computed(() =>
  state.recordList
    .filter(record => record.groupStatus !== '2')
    .slice()
    .sort((left, right) => {
      if (left.groupStatus !== right.groupStatus)
        return left.groupStatus === '0' ? -1 : 1
      const leftExpire = parseTime(left.expireAt)
      const rightExpire = parseTime(right.expireAt)
      if (Number.isNaN(leftExpire) || Number.isNaN(rightExpire))
        return 0
      return leftExpire - rightExpire
    }),
)

async function loadActivity(id: string) {
  state.loading = true
  state.loadFailed = false
  try {
    const response = await getActivityById(id)
    state.activity = response
    await Promise.all([loadSpuInfo(response.spuId), loadRecords(id)])
  }
  catch {
    state.loadFailed = true
  }
  finally {
    state.loading = false
  }
}

async function loadSpuInfo(spuId?: string) {
  if (!spuId)
    return
  try {
    /**
     * 必须走免登批量接口，不能用 `getById`：
     * 1) `getById` 携带登录态（后端据此写浏览足迹），而分享出去的拼团详情页绝大多数
     *    访客未登录 → 401 → 商品图/商品名恒为空；
     * 2) 商品接口真实响应是 `{ id, name, spuUrls[] }`，并没有 `spuName/picUrl/sliderPicUrls`，
     *    旧代码读的三个字段都不存在，即使登录也取不到。
     * 这里复用装修楼层的归一化（firstImage 兼容 spuUrls[0]），一次请求拿到名称与主图。
     */
    const goodsList = normalizeRetailGoods(await getByIds([spuId]))
    const goods = goodsList[0]
    spuImageFailed.value = false
    spuInfo.value = {
      spuName: goods?.name || '',
      picUrl: goods?.imageUrl || '',
    }
  }
  catch {
    spuInfo.value = null
  }
}

async function loadRecords(id: string) {
  try {
    const response = await getRecordPage({
      current: 1,
      size: 20,
      activityId: id,
    })
    state.recordList = response.records ?? []
    state.recordFailed = false
  }
  catch {
    state.recordList = []
    state.recordFailed = true
  }
}

function retryLoad() {
  if (activityId.value)
    void loadActivity(activityId.value)
}

function retryRecords() {
  if (activityId.value)
    void loadRecords(activityId.value)
}

function leaderNickname(record: GroupBuyRecord) {
  const leader = (record.memberList ?? []).find(member => member.isLeader === '1')
  const nickname = (record.leaderNickname || leader?.nickname || '').trim()
  // 后端未装配/查无用户时也不再显示裸 userId
  return nickname || t('promotion.groupBuy.detail.peerFallback')
}

function leaderAvatar(record: GroupBuyRecord) {
  const leader = (record.memberList ?? []).find(member => member.isLeader === '1')
  return record.leaderAvatar || leader?.avatarUrl || ''
}

interface RingSlot {
  key: string
  filled: boolean
  isLeader: boolean
  avatar: string
  nickname: string
}

/**
 * 头像进度环：团长（首格）+ 成员已付款位置，空位渲染虚线问号圆圈。
 * 已填位置取 memberList 的 userId 数，与 currentNum 取大值兜底（成员接口缺字段时不掉进度）。
 */
function ringSlots(record: GroupBuyRecord): RingSlot[] {
  const total = Math.max(record.groupNum ?? 0, 1)
  const members = (record.memberList ?? []).filter((member): member is GroupBuyMemberVO => Boolean(member?.userId))
  const filledCount = Math.min(total, Math.max(record.currentNum ?? 0, members.length))
  const shownCount = Math.min(total, MAX_RING_SLOTS)
  const slots: RingSlot[] = []
  for (let index = 0; index < shownCount; index += 1) {
    const isLeader = index === 0
    const member = isLeader ? undefined : members[index]
    slots.push({
      key: `${record.id ?? record.leaderUserId ?? 'record'}-${index}`,
      filled: index < filledCount,
      isLeader,
      avatar: isLeader ? leaderAvatar(record) : (member?.avatarUrl ?? ''),
      nickname: isLeader
        ? leaderNickname(record)
        : (member?.nickname ?? t('promotion.groupBuy.detail.peerFallback')),
    })
  }
  return slots
}

function hiddenSlotCount(record: GroupBuyRecord) {
  const total = Math.max(record.groupNum ?? 0, 1)
  return Math.max(0, total - Math.min(total, MAX_RING_SLOTS))
}

function avatarText(nickname: string) {
  const chars = Array.from(nickname.trim())
  return chars[0] ?? t('promotion.groupBuy.detail.avatarFallback')
}

function markAvatarFailed(key: string) {
  failedAvatars.value = { ...failedAvatars.value, [key]: true }
}

function remainCount(record: GroupBuyRecord) {
  const total = Math.max(record.groupNum ?? 0, record.currentNum ?? 0)
  if (total <= 0)
    return 0
  return Math.max(0, total - (record.currentNum ?? 0))
}

function recordStatusText(record: GroupBuyRecord) {
  if (record.groupStatus === '1')
    return t('promotion.groupBuy.record.statusSuccess')
  if (record.groupStatus === '2')
    return t('promotion.groupBuy.record.statusFailed')
  return ''
}

type RecordAction = 'order' | 'pay' | 'join' | 'joined' | 'success' | 'failed'

/**
 * 团按钮分支：与改造前逐条件一致，仅抽成渲染用的枚举。
 * 已占坑未下单→去下单 / 已下单待付款→去支付 / 未参与→参与拼单 / 已参与→我参团中；
 * 已拼成灰态、不显示参团按钮。
 */
function recordAction(record: GroupBuyRecord): RecordAction {
  if (record.groupStatus === '1')
    return 'success'
  if (record.groupStatus !== '0')
    return 'failed'
  if (record.myMemberStatus === '0' && !record.myOrderId)
    return 'order'
  if (record.myMemberStatus === '0' && record.myOrderId)
    return 'pay'
  if (!record.isJoined)
    return 'join'
  return 'joined'
}

function actionLabel(action: RecordAction) {
  if (action === 'order')
    return t('promotion.groupBuy.detail.actionOrder')
  if (action === 'pay')
    return t('promotion.groupBuy.detail.actionPay')
  if (action === 'join')
    return t('promotion.groupBuy.detail.actionJoin')
  if (action === 'joined')
    return t('promotion.groupBuy.detail.actionJoined')
  if (action === 'success')
    return t('promotion.groupBuy.detail.formed')
  return t('promotion.groupBuy.status.ended')
}

function actionClass(action: RecordAction) {
  if (action === 'joined')
    return 'cta-sm--weak'
  if (action === 'success' || action === 'failed')
    return 'cta-sm--disabled'
  return ''
}

function handleRecordAction(record: GroupBuyRecord) {
  const action = recordAction(record)
  if (action === 'order')
    gotoOrderConfirm(record.id)
  else if (action === 'pay' && record.myOrderId)
    gotoPay(record.myOrderId)
  else if (action === 'join' && record.id)
    handleJoinGroup(record.id)
}

async function handleOpenGroup() {
  if (!state.activity?.id)
    return
  globalLoading.loading(t('promotion.groupBuy.detail.opening'))
  try {
    const record = await openGroup({ activityId: state.activity.id })
    await loadRecords(state.activity.id)
    gotoOrderConfirm(record?.id)
  }
  catch {
    uni.showToast({ title: t('promotion.groupBuy.detail.openFailed'), icon: 'none' })
  }
  finally {
    globalLoading.close()
  }
}

async function handleJoinGroup(recordId: string) {
  if (!state.activity?.id)
    return
  globalLoading.loading(t('promotion.groupBuy.detail.joining'))
  try {
    const record = await joinGroup({ activityId: state.activity.id, recordId })
    await loadRecords(state.activity.id)
    gotoOrderConfirm(record?.id || recordId)
  }
  catch {
    uni.showToast({ title: t('promotion.groupBuy.detail.joinFailed'), icon: 'none' })
  }
  finally {
    globalLoading.close()
  }
}

/**
 * 开团/参团成功后进入结算页下单。
 *
 * <p>拼团价只在服务端按拼团记录重算，结算页无商品上下文会空单，
 * 故把活动 SKU 写入 goodsStore 并携带 recordId 走「立即购买」链路；
 * 该拼团件为拼团单，支付方式由结算页锁定为在线支付（成团判定依赖支付回调）。
 */
function gotoOrderConfirm(recordId?: string) {
  const activity = state.activity
  if (!recordId || !activity?.skuId) {
    uni.showToast({ title: t('promotion.groupBuy.detail.qualificationFailed'), icon: 'none' })
    return
  }
  const goodsStore = useGoodsStore()
  goodsStore.setGoodsList([
    {
      spuId: activity.spuId,
      skuId: activity.skuId,
      spuName: spuInfo.value?.spuName || activity.activityName,
      picUrl: spuInfo.value?.picUrl || '',
      quantity: 1,
      specsInfo: '',
    },
  ])
  uni.navigateTo({
    url: `/sub-pages/order/order-confirm/index?createWay=2&groupBuyRecordId=${recordId}`,
  })
}

/**
 * 已下单未支付：跳订单详情（内含「去支付」入口，按订单号进收银台），
 * 避免重复下单再占一份拼团资格。
 */
function gotoPay(orderId: string) {
  uni.navigateTo({
    url: `/sub-pages/order/order-detail/index?id=${orderId}`,
  })
}

/** 左 CTA「单独购买」：走商品详情常规购买链路（不改变开团/参团动线） */
function gotoGoodsDetail() {
  const spuId = state.activity?.spuId
  if (!spuId) {
    uni.showToast({ title: t('promotion.groupBuy.detail.goodsLoading'), icon: 'none' })
    return
  }
  uni.navigateTo({
    url: `/sub-pages/product/goods-detail/index?id=${spuId}`,
  })
}

/** 我的拼团列表（无参数：列出当前用户全部拼团记录） */
function gotoMyGroups() {
  uni.navigateTo({
    url: '/sub-pages/promotion/group-buy/group-buy-record/index',
  })
}

/** 省额：价差 > 0 才展示，写进右侧主按钮形成同屏比价（与列表卡同源） */
const saveText = computed(() => {
  const original = activity.value?.originalPrice ?? 0
  const group = activity.value?.groupPrice ?? 0
  return original > group
    ? t('promotion.common.saveAmount', { amount: (original - group).toFixed(2) })
    : ''
})

/** 「正在拼的团」分区头右侧：优先用已拼件数（虚增+真实，后端合成）做社会证明，无数据时给团数 */
const sectionSub = computed(() => {
  if (soldText.value)
    return soldText.value
  const count = displayRecords.value.length
  return count > 0 ? t('promotion.groupBuy.detail.groupCount', { count }) : ''
})

/** 空态说明：成团人数 + 拼成价（有拼团价时展示「拼成即享」） */
const emptyDesc = computed(() => {
  const count = activity.value?.groupNum ?? 2
  return groupPriceText.value
    ? t('promotion.groupBuy.detail.emptyDescWithPrice', { count, price: groupPriceText.value })
    : t('promotion.groupBuy.detail.emptyDesc', { count })
})

/** 我的团：同活动同一用户只占一个坑，用于底部 CTA 的状态判定 */
const mineRecord = computed(() => displayRecords.value.find(record => record.isJoined) ?? null)

type BottomMode = 'ended' | 'join' | 'joined' | 'order' | 'pay' | 'pending'

/**
 * 底部 CTA 状态矩阵（第二轮核心改动）。
 *
 * 旧实现只在 activityStatus === '1' 时渲染底部整条，未开始/已结束直接悬空，
 * 用户不知道下一步做什么。主流做法是按钮随「活动状态 × 我的团状态」变化，
 * 任何分支都保留可执行动作。分支：
 * 未开始 → 开抢时间禁用 + 单独购买；已结束 → 去商品详情 + 已结束；
 * 我已占坑未下单 → 去下单；已下单待付款 → 去支付；已参团 → 邀请好友；
 * 未参团 → 单独购买 + 发起拼单。
 */
const bottomMode = computed<BottomMode>(() => {
  const status = activity.value?.activityStatus
  if (status === '2')
    return 'ended'
  if (status === '0')
    return 'pending'
  const mine = mineRecord.value
  if (!mine)
    return 'join'
  const action = recordAction(mine)
  if (action === 'order')
    return 'order'
  if (action === 'pay')
    return 'pay'
  return 'joined'
})

/** 未开始活动的开抢时间：HH:mm，拿不到时间时退回「即将开抢」 */
const startTimeText = computed(() => {
  const start = parseTime(activity.value?.startedAt)
  if (Number.isNaN(start))
    return t('promotion.groupBuy.detail.startingSoon')
  const date = new Date(start)
  return t('promotion.groupBuy.detail.startAt', { time: `${pad(date.getHours())}:${pad(date.getMinutes())}` })
})

/** 底部右侧主按钮动作（分享分支由原生 button open-type 承担，不进此处） */
function handleBottomPrimary() {
  const mine = mineRecord.value
  if (bottomMode.value === 'order')
    gotoOrderConfirm(mine?.id)
  else if (bottomMode.value === 'pay' && mine?.myOrderId)
    gotoPay(mine.myOrderId)
  else if (bottomMode.value === 'join')
    void handleOpenGroup()
}

/** 底部左侧：单独购买 / 查看我的拼团 / 去商品详情 */
function handleBottomSecondary() {
  if (bottomMode.value === 'order' || bottomMode.value === 'pay' || bottomMode.value === 'joined') {
    gotoMyGroups()
    return
  }
  gotoGoodsDetail()
}
</script>

<template>
  <view class="group-buy-page">
    <hr-navbar :title="t('promotion.groupBuy.detail.title')">
      <template #right>
        <button class="nav-share" open-type="share" @tap="handleShareTap">
          {{ t('promotion.groupBuy.detail.share') }}
        </button>
      </template>
    </hr-navbar>

    <!-- 加载骨架 -->
    <view v-if="state.loading" class="skeleton">
      <view class="sk-card">
        <view class="sk-media sk-pulse" />
        <view class="sk-body">
          <view class="sk-line sk-pulse" />
          <view class="sk-line sk-line--short sk-pulse" />
          <view class="sk-line sk-line--price sk-pulse" />
        </view>
      </view>
      <view class="sk-card">
        <view class="sk-line sk-line--title sk-pulse" />
        <view v-for="row in 2" :key="row" class="sk-record">
          <view class="sk-avatar sk-pulse" />
          <view class="sk-body">
            <view class="sk-line sk-pulse" />
            <view class="sk-line sk-line--short sk-pulse" />
          </view>
          <view class="sk-btn sk-pulse" />
        </view>
      </view>
    </view>

    <template v-else-if="activity">
      <!-- 商品信息卡：商品图 + 双价 + 标签组 + 成团倒计时 -->
      <view class="goods-card">
        <view class="goods-main">
          <view class="goods-media">
            <image
              v-if="showGoodsImage"
              class="goods-img"
              :src="resolveImageSrc(goodsImage)"
              mode="aspectFill"
              @error="spuImageFailed = true"
            />
            <view v-else class="goods-img goods-img--empty">
              {{ t('promotion.groupBuy.card.fallbackBadge') }}
            </view>
          </view>
          <view class="goods-info">
            <view class="goods-name">
              {{ goodsName }}
            </view>
            <view v-if="showActivityName" class="goods-activity">
              {{ activity.activityName }}
            </view>
            <view class="price-row">
              <view class="price-main">
                <text class="price-sym">¥</text>
                <text class="price-val">{{ groupPriceText || '--' }}</text>
                <text class="price-label">{{ t('promotion.groupBuy.groupPrice') }}</text>
              </view>
              <view v-if="originalPriceText" class="price-other">
                <text class="price-other-label">{{ t('promotion.groupBuy.detail.buyAlone') }}</text>
                <text class="price-orig">¥{{ originalPriceText }}</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 标签组：只留硬约束（N人成团 / 限购N件）；「已拼N件」升为下方分区头 -->
        <view class="tag-row">
          <text v-for="tag in baseTags" :key="tag" class="tag">{{ tag }}</text>
        </view>

        <!-- 倒计时：天单独一格（仅跨天出现），前置词承担「N 小时内成团」 -->
        <view v-if="groupCountdown" class="countdown-row">
          <text class="countdown-label">{{ groupCountdown.label }}</text>
          <view class="countdown-blocks">
            <text v-if="groupCountdown.days > 0" class="cd-block cd-days">{{ t('promotion.common.days', { days: groupCountdown.days }) }}</text>
            <text class="cd-block">{{ pad(groupCountdown.hours) }}</text>
            <text class="cd-colon">:</text>
            <text class="cd-block">{{ pad(groupCountdown.minutes) }}</text>
            <text class="cd-colon">:</text>
            <text class="cd-block">{{ pad(groupCountdown.seconds) }}</text>
          </view>
        </view>
      </view>

      <!-- 正在拼的团（分区头带已拼件数：虚增 + 真实已付款参团） -->
      <view class="record-section">
        <view class="section-head">
          <text class="section-title">{{ t('promotion.groupBuy.detail.activeGroups') }}</text>
          <text v-if="sectionSub" class="section-sub">{{ sectionSub }}</text>
        </view>

        <view v-if="state.recordFailed" class="records-error" @tap="retryRecords">
          {{ t('promotion.groupBuy.detail.recordsFailed') }}
        </view>

        <!-- 空态：图形位 + 说明 + 主按钮 + 次要出口（邀请好友一起拼） -->
        <view v-else-if="displayRecords.length === 0" class="empty-card">
          <hall-state
            art="group"
            :title="t('promotion.groupBuy.detail.emptyTitle')"
            :desc="emptyDesc"
            :action-text="t('promotion.groupBuy.detail.startGroup')"
            @action="handleOpenGroup"
          >
            <template #link>
              <button class="empty-share" open-type="share" @tap="handleShareTap">
                {{ t('promotion.groupBuy.detail.inviteToJoin') }}
              </button>
            </template>
          </hall-state>
        </view>

        <view
          v-for="(record, index) in displayRecords"
          :key="record.id ?? index"
          class="record-item"
          :class="{ 'is-mine': record.isJoined, 'is-done': record.groupStatus === '1' }"
        >
          <view class="record-ring">
            <view
              v-for="slot in ringSlots(record)"
              :key="slot.key"
              class="ring-slot"
              :class="{ 'is-leader': slot.isLeader }"
            >
              <image
                v-if="slot.filled && slot.avatar && !failedAvatars[slot.key]"
                class="ring-avatar"
                :src="resolveImageSrc(slot.avatar)"
                mode="aspectFill"
                @error="markAvatarFailed(slot.key)"
              />
              <view v-else-if="slot.filled" class="ring-avatar ring-avatar--text">
                {{ avatarText(slot.nickname) }}
              </view>
              <view v-else class="ring-avatar ring-avatar--empty">
                ?
              </view>
              <view v-if="slot.isLeader" class="ring-crown">
                {{ t('promotion.groupBuy.detail.leader') }}
              </view>
            </view>
            <text v-if="hiddenSlotCount(record) > 0" class="ring-more">+{{ hiddenSlotCount(record) }}</text>
          </view>

          <view class="record-info">
            <view class="record-title">
              <text class="record-nick">{{ leaderNickname(record) }}</text>
              <text
                v-if="record.groupStatus === '0' && remainCount(record) > 0"
                class="record-remain"
                :class="{ 'is-urgent': remainCount(record) === 1 }"
              >{{ t('promotion.groupBuy.detail.remain', { count: remainCount(record) }) }}</text>
              <text v-else-if="record.groupStatus === '1'" class="record-done">{{ t('promotion.groupBuy.detail.formed') }}</text>
              <text v-else-if="recordStatusText(record)" class="record-done">{{ recordStatusText(record) }}</text>
              <text v-else-if="record.groupStatus === '0'" class="record-done">{{ t('promotion.groupBuy.detail.fullWaiting') }}</text>
            </view>
            <view class="record-sub">
              <text v-if="record.groupStatus === '0' && remainingText(record.expireAt)" class="record-time">
                {{ t('promotion.groupBuy.detail.remaining', { time: remainingText(record.expireAt) }) }}
              </text>
              <text v-else class="record-time">{{ recordStatusText(record) || t('promotion.groupBuy.detail.full') }}</text>
            </view>
          </view>

          <view class="record-action">
            <view
              class="action-hit"
              :class="{ 'is-disabled': ['joined', 'success', 'failed'].includes(recordAction(record)) }"
              @tap="handleRecordAction(record)"
            >
              <view class="cta-sm" :class="actionClass(recordAction(record))">
                {{ actionLabel(recordAction(record)) }}
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 玩法说明：三步流程图，填补记录区之后的空白并降低理解成本 -->
      <view class="how-card">
        <view class="how-title">
          {{ t('promotion.groupBuy.detail.howTitle') }}
        </view>
        <view class="how-steps">
          <view class="how-step">
            <view class="how-num">
              1
            </view>
            <text class="how-label">{{ t('promotion.groupBuy.detail.howStepStart') }}</text>
          </view>
          <view class="how-arrow" />
          <view class="how-step">
            <view class="how-num">
              2
            </view>
            <text class="how-label">{{ t('promotion.groupBuy.detail.howStepInvite') }}</text>
          </view>
          <view class="how-arrow" />
          <view class="how-step">
            <view class="how-num">
              3
            </view>
            <text class="how-label">{{ t('promotion.groupBuy.detail.howStepShip') }}</text>
          </view>
        </view>
      </view>
    </template>

    <!-- 接口异常：不白屏，给重试入口 -->
    <view v-else class="state-panel">
      <hall-state
        art="error"
        :title="t('promotion.groupBuy.detail.loadFailedTitle')"
        :desc="t('promotion.groupBuy.detail.loadFailedDesc')"
        :action-text="t('promotion.groupBuy.detail.reload')"
        @action="retryLoad"
      />
    </view>

    <!--
      底部固定 CTA：状态矩阵（未参团 / 我占坑待下单 / 待支付 / 我参团中 / 未开始 / 已结束），
      任何分支都不整条消失。我参团中一支走原生 button open-type="share" 邀请好友。
    -->
    <view v-if="activity" class="bottom-bar">
      <view class="bottom-cta-hit" @tap="handleBottomSecondary">
        <view class="cta-ghost cta-ghost--wide">
          <template v-if="bottomMode === 'order' || bottomMode === 'pay' || bottomMode === 'joined'">
            {{ t('promotion.groupBuy.detail.myGroups') }}
          </template>
          <template v-else-if="bottomMode === 'ended'">
            {{ t('promotion.groupBuy.detail.goGoods') }}
          </template>
          <template v-else>
            {{ t('promotion.groupBuy.detail.buyAlone') }}<text v-if="originalPriceText" class="cta-ghost-price"> ¥{{ originalPriceText }}</text>
          </template>
        </view>
      </view>

      <view class="bottom-cta-hit bottom-cta-hit--grow">
        <!-- 我参团中：右侧主按钮即分享入口 -->
        <button
          v-if="bottomMode === 'joined'"
          class="cta-solid cta-solid--wide cta-solid--share"
          open-type="share"
          @tap="handleShareTap"
        >
          <text class="cta-solid-label">{{ t('promotion.groupBuy.detail.inviteFriends') }}</text>
        </button>
        <view
          v-else
          class="cta-solid cta-solid--wide"
          :class="{ 'is-disabled': bottomMode === 'pending' || bottomMode === 'ended' }"
          @tap="handleBottomPrimary"
        >
          <text v-if="bottomMode === 'pending'" class="cta-solid-label">{{ startTimeText }}</text>
          <text v-else-if="bottomMode === 'ended'" class="cta-solid-label">{{ t('promotion.groupBuy.detail.activityEnded') }}</text>
          <text v-else-if="bottomMode === 'order'" class="cta-solid-label">{{ t('promotion.groupBuy.detail.actionOrder') }}</text>
          <text v-else-if="bottomMode === 'pay'" class="cta-solid-label">{{ t('promotion.groupBuy.detail.actionPay') }}</text>
          <template v-else>
            <text v-if="groupPriceText" class="cta-solid-price">¥{{ groupPriceText }}</text>
            <text class="cta-solid-label">{{ t('promotion.groupBuy.detail.startGroup') }}{{ saveText ? ` · ${saveText}` : '' }}</text>
          </template>
        </view>
      </view>
    </view>
  </view>
</template>

<!--
  拼团详情（促销强度「中」，§4/§5.3）：
  白卡 + 主题色只给价格/CTA/标签；深色分格倒计时固定 #1F2329；
  商品图 176rpx 圆角 16rpx、卡片 20rpx、CTA 视觉 56rpx/热区 88rpx。
-->
<style lang="scss" scoped>
.group-buy-page {
  box-sizing: border-box;
  min-height: 100vh;
  padding-bottom: calc(160rpx + env(safe-area-inset-bottom));
  background: var(--wot-color-theme-background, #f5f5f5);
}

/* ---------- 右上角分享引导（小程序端 open-type="share"） ---------- */
.nav-share {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 88rpx;
  height: 88rpx;
  padding: 0 16rpx;
  margin: 0;
  color: #5b616e;
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1;
  background: transparent;
  border: none;
}

.nav-share::after {
  border: none;
}

/* ---------- 商品信息卡 ---------- */
.goods-card,
.record-section {
  margin: 20rpx 24rpx 0;
  padding: 24rpx;
  background: #fff;
  border-radius: 20rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
}

.goods-main {
  display: flex;
  gap: 20rpx;
}

.goods-media {
  flex: 0 0 auto;
}

.goods-img {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 224rpx;
  height: 224rpx;
  background: #f3f4f6;
  border-radius: 16rpx;
}

.goods-img--empty {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 24rpx;
  background: var(--wot-color-theme-background, #fff1ee);
}

.goods-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.goods-name {
  display: -webkit-box;
  height: 68rpx;
  overflow: hidden;
  color: #1f2329;
  font-size: 25rpx;
  font-weight: 500;
  line-height: 34rpx;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.goods-activity {
  margin-top: 8rpx;
  overflow: hidden;
  color: #8a8f99;
  font-size: 20rpx;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.price-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  margin-top: auto;
  padding-top: 12rpx;
  gap: 16rpx;
}

.price-main {
  display: flex;
  align-items: baseline;
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
  line-height: 1;
  letter-spacing: -0.6rpx;
}

.price-label {
  padding: 2rpx 8rpx;
  margin-left: 8rpx;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 18rpx;
  font-weight: 600;
  background: var(--wot-color-theme-background, #fff1ee);
  border-radius: 6rpx;
}

.price-other {
  display: flex;
  align-items: baseline;
  gap: 6rpx;
}

.price-other-label {
  color: #8a8f99;
  font-size: 20rpx;
}

.price-orig {
  color: #b6b9c0;
  font-size: 20rpx;
  text-decoration: line-through;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  margin-top: 20rpx;
  gap: 10rpx;
}

/* 标签：只保留「N人成团 / 限购N件」两个硬约束 */
.tag {
  padding: 6rpx 14rpx;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 20rpx;
  font-weight: 600;
  background: var(--wot-color-theme-background, #fff1ee);
  border-radius: 8rpx;
}

.countdown-row {
  display: flex;
  align-items: center;
  padding-top: 18rpx;
  margin-top: 18rpx;
  gap: 12rpx;
  border-top: 1rpx solid #f1f2f4;
}

.countdown-label {
  color: #5b616e;
  font-size: 22rpx;
  font-weight: 600;
}

.countdown-blocks {
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.cd-block {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 40rpx;
  height: 40rpx;
  padding: 0 8rpx;
  color: #fff;
  font-size: 23rpx;
  font-weight: 700;
  background: #1f2329;
  border-radius: 8rpx;
  font-variant-numeric: tabular-nums;
}

.cd-days {
  font-size: 20rpx;
}

.cd-colon {
  color: #5b616e;
  font-size: 20rpx;
  font-weight: 700;
}

/* ---------- 正在拼的团 ---------- */
.section-head {
  display: flex;
  align-items: baseline;
  margin-bottom: 8rpx;
  gap: 12rpx;
}

.section-title {
  color: #1f2329;
  font-size: 30rpx;
  font-weight: 800;
  letter-spacing: 0.4rpx;
}

.section-sub {
  color: #8a8f99;
  font-size: 20rpx;
}

.records-error {
  padding: 40rpx 0;
  color: #8a8f99;
  font-size: 24rpx;
  text-align: center;
}

.record-item {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f1f2f4;
}

.record-item:last-child {
  border-bottom: none;
}

/* 我的团：左侧 4rpx 主色竖条 + 浅主色底（比整卡色块更清晰，也不挤压头像环） */
.record-item.is-mine {
  position: relative;
  padding: 16rpx 12rpx 16rpx 20rpx;
  margin: 4rpx -12rpx;
  background: var(--wot-color-theme-background, #fff1ee);
  border-radius: 16rpx;
}

.record-item.is-mine::before {
  position: absolute;
  top: 16rpx;
  bottom: 16rpx;
  left: 8rpx;
  width: 4rpx;
  content: '';
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 999rpx;
}

.record-item.is-done {
  opacity: 0.72;
}

.record-ring {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  padding-left: 8rpx;
}

.ring-slot {
  position: relative;
  margin-left: -16rpx;
}

.ring-slot:first-child {
  margin-left: 0;
}

.ring-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  background: #f1f2f4;
  border: 2rpx solid #fff;
  border-radius: 50%;
}

.ring-slot.is-leader .ring-avatar {
  width: 72rpx;
  height: 72rpx;
}

.ring-avatar--text {
  color: #8a8f99;
  font-size: 24rpx;
  font-weight: 700;
}

.ring-avatar--empty {
  color: #b6b9c0;
  font-size: 26rpx;
  font-weight: 700;
  background: #fafafa;
  border: 2rpx dashed #d9dbe0;
}

.ring-crown {
  position: absolute;
  bottom: -8rpx;
  left: 50%;
  padding: 0 8rpx;
  height: 26rpx;
  color: #fff;
  font-size: 16rpx;
  font-weight: 700;
  line-height: 26rpx;
  white-space: nowrap;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border: 1rpx solid #fff;
  border-radius: 999rpx;
  transform: translateX(-50%);
}

.ring-more {
  margin-left: 8rpx;
  color: #8a8f99;
  font-size: 20rpx;
  font-weight: 600;
}

.record-info {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.record-title {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.record-nick {
  overflow: hidden;
  max-width: 220rpx;
  color: #1f2329;
  font-size: 26rpx;
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.record-remain {
  color: #8a8f99;
  font-size: 22rpx;
  font-weight: 600;
  white-space: nowrap;
}

.record-remain.is-urgent {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 24rpx;
  font-weight: 800;
}

.record-done {
  color: #8a8f99;
  font-size: 20rpx;
  font-weight: 500;
}

.record-sub {
  margin-top: 6rpx;
}

.record-time {
  color: #8a8f99;
  font-size: 20rpx;
  font-variant-numeric: tabular-nums;
}

.record-action {
  flex: 0 0 auto;
  margin-left: 8rpx;
}

/* 视觉按钮 56rpx / 命中区 ≥88rpx（§4.3 触控要求） */
.action-hit {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-height: 88rpx;
  min-width: 132rpx;
}

.cta-sm {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 56rpx;
  padding: 0 24rpx;
  color: #fff;
  font-size: 22rpx;
  font-weight: 700;
  letter-spacing: 0.4rpx;
  white-space: nowrap;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border-radius: 999rpx;
}

.cta-sm--weak {
  color: var(--wot-color-theme-primary, #ff2237);
  background: var(--wot-color-theme-background, #fff1ee);
}

.cta-sm--disabled {
  color: #b6b9c0;
  background: #f1f2f4;
}

/* ---------- 空态（HallState：图形位 + 说明 + 主按钮 + 邀请好友） ---------- */
.empty-card {
  background: #fff;
  border-radius: 20rpx;
  overflow: hidden;
}

/* 原生 button 复位成文字链外观：小程序分享必须由 open-type="share" 触发 */
.empty-share {
  height: auto;
  padding: 12rpx;
  margin-top: 20rpx;
  background: transparent;
  border: none;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1.4;
}

.empty-share::after {
  border: none;
}

/* ---------- 异常态 ---------- */
.state-panel {
  background: #fff;
  border-radius: 20rpx;
  margin: 20rpx 24rpx 0;
  overflow: hidden;
}

/* ---------- 玩法说明（三步） ---------- */
.how-card {
  margin: 20rpx 24rpx 0;
  padding: 24rpx;
  background: #fff;
  border-radius: 20rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
}

.how-title {
  color: #1f2329;
  font-size: 26rpx;
  font-weight: 700;
}

.how-steps {
  display: flex;
  margin-top: 20rpx;
  align-items: center;
}

.how-step {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
}

.how-num {
  display: flex;
  width: 44rpx;
  height: 44rpx;
  align-items: center;
  justify-content: center;
  background: var(--wot-color-theme-background, #fff1ee);
  border-radius: 50%;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 22rpx;
  font-weight: 700;
}

.how-label {
  color: #5b616e;
  font-size: 22rpx;
}

/* 步骤之间的细箭头：与序号行对齐 */
.how-arrow {
  position: relative;
  width: 28rpx;
  height: 2rpx;
  margin-bottom: 34rpx;
  background: #e5e7eb;
}

.how-arrow::after {
  position: absolute;
  top: -4rpx;
  right: 0;
  width: 8rpx;
  height: 8rpx;
  content: '';
  border-top: 2rpx solid #e5e7eb;
  border-right: 2rpx solid #e5e7eb;
  transform: rotate(45deg);
}

/* ---------- 底部固定 CTA ---------- */
.bottom-bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  gap: 16rpx;
  background: #fff;
  border-top: 1rpx solid #f1f2f4;
  box-shadow: 0 -8rpx 24rpx rgba(15, 23, 42, 0.05);
}

.bottom-cta-hit {
  display: flex;
  flex: 0 0 auto;
  min-width: 240rpx;
  align-items: center;
  min-height: 88rpx;
}

.bottom-cta-hit--grow {
  flex: 1;
  min-width: 0;
}

/* 视觉按钮 80rpx 高胶囊：底部主次分明的双 CTA */
.cta-ghost,
.cta-solid {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: 80rpx;
  padding: 0 28rpx;
  font-size: 26rpx;
  font-weight: 700;
  white-space: nowrap;
  border-radius: 999rpx;
}

.cta-ghost {
  color: var(--wot-color-theme-primary, #ff2237);
  background: #fff;
  border: 2rpx solid var(--wot-color-theme-primary, #ff2237);
}

.cta-ghost-price {
  margin-left: 4rpx;
  font-size: 22rpx;
  font-weight: 800;
}

.cta-solid {
  color: #fff;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
}

/* 未开始 / 已结束：禁用态保留按钮形态（不再整条消失） */
.cta-solid.is-disabled {
  background: #f1f2f4;
  color: #8a8f99;
}

/* 原生 button 复位：我参团中一支的主按钮即分享入口 */
.cta-solid--share {
  margin: 0;
  border: none;
  line-height: 1.4;
}

.cta-solid--share::after {
  border: none;
}

.cta-ghost--wide,
.cta-solid--wide {
  display: flex;
  flex: 1;
}

.cta-solid-price {
  font-size: 28rpx;
  font-weight: 800;
}

.cta-solid-label {
  margin-left: 6rpx;
  font-size: 26rpx;
  font-weight: 700;
}

/* ---------- 加载骨架 ---------- */
.skeleton {
  padding: 20rpx 24rpx 0;
}

.sk-card {
  padding: 24rpx;
  margin-bottom: 20rpx;
  background: #fff;
  border-radius: 20rpx;
}

.sk-card:first-child {
  display: flex;
  gap: 20rpx;
}

.sk-media {
  flex: 0 0 auto;
  width: 176rpx;
  height: 176rpx;
  border-radius: 16rpx;
}

.sk-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16rpx;
}

.sk-line {
  height: 28rpx;
  border-radius: 8rpx;
}

.sk-line--short {
  width: 60%;
}

.sk-line--price {
  width: 40%;
  height: 44rpx;
}

.sk-line--title {
  width: 40%;
  margin-bottom: 24rpx;
}

.sk-record {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  gap: 20rpx;
}

.sk-avatar {
  flex: 0 0 auto;
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
}

.sk-btn {
  flex: 0 0 auto;
  width: 132rpx;
  height: 56rpx;
  border-radius: 999rpx;
}

.sk-pulse {
  background: #f1f2f4;
  animation: gb-skeleton-pulse 1.4s ease-in-out infinite;
}

@keyframes gb-skeleton-pulse {
  0% {
    opacity: 0.55;
  }

  50% {
    opacity: 1;
  }

  100% {
    opacity: 0.55;
  }
}
</style>
