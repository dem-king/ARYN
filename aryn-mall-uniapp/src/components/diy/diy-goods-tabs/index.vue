<script setup lang="ts">
import type {
  GoodsTabConfig,
  GoodsTabMemberBanner,
  GoodsTabsProps,
  ListMemberPriceDisplay,
} from '@/components/diy/retail-types'
import type {
  MemberBannerProfile,
  MemberBannerView,
} from '@/components/diy/member-banner'
import type { ListZonePrice } from '@/utils/zone-price'

import {
  computed,
  getCurrentInstance,
  nextTick,
  reactive,
  ref,
  watch,
} from 'vue'
import { useI18n } from 'vue-i18n'
import { getMemberBenefits, getMemberCurrentInfo } from '@/api/user/member'
import {
  MEMBER_BANNER_LOGIN_URL,
  MEMBER_BANNER_MEMBER_CENTER_URL,
  resolveMemberBannerView,
} from '@/components/diy/member-banner'
import { shouldShowOriginalPrice } from '@/components/diy/price-display'
import { loadGoodsTabPage } from '@/components/diy/retail-data'
import { mergePagedItems } from '@/components/diy/retail-paged-list'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { shouldShowSubtitle } from '@/components/diy/subtitle-display'
import MemberIntroSheet from '@/components/member-intro-sheet/index.vue'
import QuickCartButton from '@/components/quick-cart-button/index.vue'
import { useDiyPagedLoadMore } from '@/composables/useDiyPagedLoadMore'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useZonePrices } from '@/composables/useZonePrices'
import { useAuthStore } from '@/store/authStore'
import { stockHintText } from '@/utils/stock-display'
import { resolveListZonePrice, zonePriceBadgeText } from '@/utils/zone-price'

const props = withDefaults(
  defineProps<{
    showData?: Partial<GoodsTabsProps>
    /**
     * 页面开启了沉浸式导航（滚动切换）：吸顶 Tab 栏与顶栏共用同一磨砂材质。
     *
     * 由 DiyPage 按页面配置下传；内嵌页（分类/商详/个人中心）与未开启沉浸式导航的
     * 页面为 false，Tab 栏保持原来的纯白底，行为不变。
     */
    immersiveNav?: boolean
  }>(),
  {
    showData: () => ({}),
    immersiveNav: false,
  },
)

const { t } = useI18n()

/** 后端没有下发 Tab 配置时的兜底三个 Tab（标题是前端固定文案，随语言切换） */
function defaultTabs(): GoodsTabConfig[] {
  return [
    {
      id: 'recommend',
      title: t('diy.goodsTabs.tabRecommend'),
      dataSource: { mode: 'automatic', sort: 'sales', targetIds: [] },
    },
    {
      id: 'bargain',
      title: t('diy.goodsTabs.tabBargain'),
      dataSource: { mode: 'automatic', sort: 'sales_price', targetIds: [] },
    },
    {
      id: 'member',
      title: t('diy.goodsTabs.tabMember'),
      dataSource: { mode: 'automatic', sort: 'create_time', targetIds: [] },
    },
  ]
}

/**
 * 会员权益条配置归一化（Tab 级）。
 *
 * 与后端发布校验同一形状：`{enabled, memberCopy?, guestCopy?}`。enabled 不为 true
 * 时直接返回 undefined（等价关闭），渲染侧只需判空，不用到处看 enabled。
 */
function normalizeMemberBanner(raw: unknown): GoodsTabMemberBanner | undefined {
  const record =
    raw && typeof raw === 'object' ? (raw as Record<string, unknown>) : null
  if (!record || record.enabled !== true) return undefined
  const memberCopy = String(record.memberCopy || '').trim()
  const guestCopy = String(record.guestCopy || '').trim()
  return {
    enabled: true,
    ...(memberCopy ? { memberCopy } : {}),
    ...(guestCopy ? { guestCopy } : {}),
  }
}

function normalizeTabs(raw: unknown): GoodsTabConfig[] {
  const list = Array.isArray(raw) ? raw : []
  const tabs = list.map((item, index) => {
    const record =
      item && typeof item === 'object' ? (item as Record<string, unknown>) : {}
    const source =
      record.dataSource && typeof record.dataSource === 'object'
        ? (record.dataSource as Record<string, unknown>)
        : {}
    const bargainRate = Number(source.bargainRate)
    // 数据源模式只有手选与自动两种：manual 一次性取全按运营配置顺序，
    // 其余（含装修 JSON 里的历史脏值）统一按 automatic 分类排序取数
    const mode = source.mode === 'manual' ? 'manual' : 'automatic'
    return {
      id: String(record.id || `tab-${index + 1}`),
      title: String(record.title || `Tab ${index + 1}`),
      dataSource: {
        categoryId: String(source.categoryId || ''),
        mode,
        sort: String(source.sort || 'sales'),
        // 阈值合法区间由 retail-query 统一兜底；这里只保留可下发的数值，
        // 避免脏字符串一路带到请求参数里
        ...(Number.isFinite(bargainRate) && bargainRate > 0
          ? { bargainRate }
          : {}),
        targetIds: Array.isArray(source.targetIds)
          ? source.targetIds.map(value => String(value))
          : [],
      },
      memberBanner: normalizeMemberBanner(record.memberBanner),
    } as GoodsTabConfig
  })
  return tabs.length > 0 ? tabs : defaultTabs()
}

const showData = computed<GoodsTabsProps>(() => ({
  columns: Number(props.showData.columns) === 3 ? 3 : 2,
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Math.max(1, Number(props.showData.count) || 10),
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  showOriginalPrice: Boolean(props.showData.showOriginalPrice),
  showPrice: props.showData.showPrice !== false,
  showSales: props.showData.showSales !== false,
  stickyTabs: props.showData.stickyTabs !== false,
  tabs: normalizeTabs(props.showData.tabs),
}))

const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${showData.value.columns}, minmax(0, 1fr))`,
}))
const pageSize = computed(() => showData.value.count)

const tabs = computed(() => showData.value.tabs)
const activeIndex = ref(0)
const activeTab = computed(() => tabs.value[activeIndex.value] || tabs.value[0])
const goodsList = ref<any[]>([])
const pagingRef = ref()
const instance = getCurrentInstance()

/**
 * 列表「专区价」批量补价句柄：会员价/出清价都不在商品列表接口里，
 * 列表先渲染、价格后补（见 utils/zone-price 与 composables/useZonePrices）。
 */
const zone = useZonePrices()

// ---------- 会员权益条（Tab 级，默认关） ----------

const authStore = useAuthStore()

/**
 * 当前用户的会员概况（会员权益条数据源）。
 *
 * null = 尚未查询 / 查询失败：横幅一律不渲染——宁可少一条权益透出，
 * 也不能给游客或会员展示错误状态，更不能让商品流等会员接口。
 * `discountRate`（会员折扣率权益）只给运营自定义文案的 `{discount}` 占位用，
 * 内置文案是「本区享会员专享价」这类确定性标签，不依赖折数。
 */
const memberProfile = ref<MemberBannerProfile | null>(null)
/** 是否已为当前登录态查过（成功/失败都算，切 Tab 不重复请求） */
let memberProfileRequested = false
/** 会员介绍弹层：点权益条「了解会员」唤起（原为整页跳会员中心） */
const memberIntroVisible = ref(false)

/**
 * 查询会员等级 + 权益（两请求并行）。
 *
 * 仅已登录才请求：`current-info` / `benefits` 都是登录态 C 端接口（只消费
 * `/app/member/*`，不碰管理端）；未登录直接按游客态渲染，不发无谓请求。
 * 失败静默：横幅是增强信息，会员接口异常绝不影响商品流首屏与翻页。
 */
async function loadMemberProfile() {
  if (memberProfileRequested) return
  memberProfileRequested = true
  if (!authStore.isLoggedIn) {
    memberProfile.value = {
      discountRate: null,
      isLoggedIn: false,
      levelId: '',
      levelName: '',
    }
    return
  }
  try {
    const [info, benefits] = await Promise.all([
      getMemberCurrentInfo(),
      // benefits 提供会员折扣率权益（仅自定义文案 {discount} 占位用）；
      // 未配置权益时后端 discountRate=1（无折扣），与「接口失败」区分开：
      // 前者仍展示会员态（内置文案不依赖折数），后者整体不渲染横幅。
      getMemberBenefits(),
    ])
    const discountRate = Number(benefits?.discountRate)
    memberProfile.value = {
      discountRate:
        Number.isFinite(discountRate) && discountRate > 0 && discountRate < 1
          ? discountRate
          : null,
      isLoggedIn: true,
      levelId: String(info?.levelId || ''),
      levelName: String(info?.levelName || ''),
    }
  } catch {
    // 接口失败：不渲染权益条，商品流照常
    memberProfile.value = null
  }
}

/** 当前 Tab 的会员权益条展示结果；未启用 / 未查到会员信息 → null（不渲染） */
const memberBannerView = computed<MemberBannerView | null>(() => {
  const config = activeTab.value?.memberBanner
  if (!config?.enabled) return null
  const profile = memberProfile.value
  if (!profile) return null
  const view = resolveMemberBannerView(config, profile)
  return view.visible ? view : null
})

/** 横幅右侧动作文案（会员态无动作、不渲染） */
const memberBannerActionText = computed(() => {
  switch (memberBannerView.value?.action) {
    case 'login':
      return t('diy.goodsTabs.login')
    case 'member-center':
      // 会员等级按累计消费自动评定，没有「申请入会」动作：文案只引导去看权益
      return t('diy.goodsTabs.learnMember')
    default:
      return ''
  }
})

/**
 * 会员专区的会员价呈现范围。
 *
 * 权益条开启且当前用户是会员时，本区有真实优惠的商品也按会员价呈现：专区价格本身
 * 就是给会员的成交价，不能因为没单独配置会员价活动就退回普通红价。未登录/无等级
 * 不进入该分支，仍走「原价 + 引导文案」，避免把专区价说成游客的会员价。
 */
const memberZonePricing = computed(
  () =>
    Boolean(activeTab.value?.memberBanner?.enabled) &&
    memberBannerView.value?.state === 'member',
)

/**
 * 卡片专区价展示：会员价 / 捡漏价 / 原价三选一，统一收敛到共享口径
 * `utils/zone-price#resolveListZonePrice`，各 Tab 不再各自比价。
 *
 * - 会员价：会员专区 Tab 用数据层按本页商品补好的 `item.memberPriceDisplay`，
 *   其它 Tab 用 `useZonePrices` 批量补价缓存（会员价活动命中且资格达标才切主价）；
 * - 出清价：对所有 Tab 生效（不仅低价捡漏 Tab），与会员价取低、同价会员价优先；
 * - 都未命中：保持售价，未达标/未登录时给出「会员专享 ¥x」引导；
 * - 会员专区「会员本人兜底」：没有更高的身份价时，专区价本身就是本区会员的成交价，
 *   沿用原金色会员价视觉（有真实划线才标，不给无优惠商品贴假会员价）。
 */
function cardDisplay(item: any): ListZonePrice {
  const sales = Number(item?.price) || 0
  const display = resolveListZonePrice({
    salesPrice: sales,
    originalPrice: item?.originalPrice,
    showOriginal: showData.value.showOriginalPrice,
    memberDisplay: memberDisplayOf(item, sales),
    clearanceDisplay: zone.clearanceDisplayOf(item?.id, sales),
  })
  if (!display.badgeKind && !display.guideText && memberZonePricing.value) {
    const strike = shouldShowOriginalPrice(item?.price, item?.originalPrice)
      ? Number(item?.originalPrice)
      : null
    if (strike !== null) {
      return {
        badgeKind: 'member',
        guideText: null,
        mainPrice: display.mainPrice,
        strikePrice: showData.value.showOriginalPrice ? strike : null,
      }
    }
  }
  return display
}

/**
 * 卡片会员价展示入参：会员专区 Tab 优先用数据层按活动补好的结果（口径同上），
 * 其余 Tab 用批量补价缓存里的会员价展示。
 */
function memberDisplayOf(item: any, cardPrice: number): ListMemberPriceDisplay | null {
  const fromData = item?.memberPriceDisplay as ListMemberPriceDisplay | undefined
  if (fromData) return fromData
  return zone.memberDisplayOf(String(item?.id || '').trim(), cardPrice)
}

/** 卡片是否按会员价样式呈现：同一卡片只解释一个成交价（与捡漏价互斥） */
function showCardMemberPrice(item: any): boolean {
  return cardDisplay(item).badgeKind === 'member'
}

/**
 * 卡片是否按捡漏价样式呈现（价格主导的红价块）。
 *
 * 出清价对所有 Tab 生效，不再只在低价捡漏/出清标签 Tab；会员价与出清价
 * 由 `resolveListZonePrice` 取低，同价会员价优先，因此两个样式不会同时出现。
 */
function showCardBargainPrice(item: any): boolean {
  return cardDisplay(item).badgeKind === 'clearance'
}

/**
 * 本轮本页商品补专区价：列表先渲染、价格后补，失败静默。
 *
 * 会员专区 Tab 虽已由数据层按本页商品补过会员价，这里仍统一补一次：
 * 卡片加购弹层的 SKU 级成交价、以及导出到其它位置的同 SPU 价格都依赖模块级
 * 价格缓存，少补一次会出现「卡片会员价、加购弹层普通价」的漂移；
 * 共享层有 60s 缓存与并发合并，重复请求成本可接受。
 */
function loadZonePrices(items: any[]) {
  const ids = items.map(item => String(item?.id || '').trim()).filter(Boolean)
  if (!ids.length) return
  void zone.loadForSpuIds(ids)
}

/** 按需查询会员信息：只有当前 Tab 开启了权益条才发请求（整组件生命周期缓存一次） */
function ensureMemberProfile() {
  if (!activeTab.value?.memberBanner?.enabled) return
  void loadMemberProfile()
}

watch(() => activeTab.value?.id, ensureMemberProfile, { immediate: true })
// 登录/退出后会员等级会变：重置缓存，回到本组件时按新身份重新查询
watch(() => authStore.isLoggedIn, () => {
  memberProfileRequested = false
  memberProfile.value = null
  ensureMemberProfile()
})

function onMemberBannerClick() {
  const view = memberBannerView.value
  if (!view || view.action === 'none') return
  if (view.action === 'login') {
    uni.navigateTo({ url: MEMBER_BANNER_LOGIN_URL })
    return
  }
  /*
   * 登录但无等级：先底部弹层介绍会员（权益 / 成长路径 / 会员专享价说明），
   * 用户在弹层里点「查看会员中心」再整页跳转。
   * 原来直接整页跳会员中心，用户只是想「了解」时被打断浏览，且解释不了
   * 商品卡上的「会员专享 ¥x」从哪来。
   */
  memberIntroVisible.value = true
}

/** 弹层主按钮：进入会员中心查看完整等级权益（积分/储值/专属券入口也在这） */
function onMemberIntroCenter() {
  uni.navigateTo({ url: MEMBER_BANNER_MEMBER_CENTER_URL })
}

interface FeedState {
  hasMore: boolean
  items: any[]
  loaded: boolean
  nextPage: number
}

/**
 * 每个 Tab 一份 feed 状态。
 *
 * 只换当前 Tab 的数据，不重建组件：切回已加载过的 Tab 时直接复用缓存，
 * 翻页游标 nextPage 也按 Tab 独立推进。
 */
const feedStates = reactive<Record<string, FeedState>>({})
/**
 * 切 Tab 时临时保留旧列表高度。
 *
 * z-paging 的 reload 会先清空列表，页面高度随之骤降；在页面滚动模式下，
 * 浏览器会把超出新内容高度的滚动位置夹回页面顶部，表现为「切 Tab 就回顶」。
 * 高度只在首次查询返回后释放，避免用户在加载瞬间被拉走。
 */
const feedMinHeight = ref(0)
let switchingTab = false
const anchorId = `goods-tabs-anchor-${Math.random().toString(36).slice(2, 8)}`
const stickyOffsetPx = ref(0)

function stateOf(tabId: string) {
  if (!feedStates[tabId]) {
    feedStates[tabId] = {
      hasMore: true,
      items: [],
      loaded: false,
      nextPage: 1,
    }
  }
  return feedStates[tabId]
}

// 页面下拉刷新会重新下发 JSON，组件实例按 id 复用；显式清空各 Tab 缓存并重载
watch(
  () => [props.showData.tabs, props.showData.count],
  () => {
    Object.keys(feedStates).forEach(key => {
      delete feedStates[key]
    })
    if (activeIndex.value >= tabs.value.length) activeIndex.value = 0
    pagingRef.value?.reload?.()?.catch?.(() => {})
  },
  { deep: true },
)

// 自动续页：页面滚动接近底部时只加载当前 Tab 的下一页
const { runQuery } = useDiyPagedLoadMore(pagingRef, '.goods-tabs')

let querySequence = 0

async function queryList(pageNo: number, pageSize: number) {
  const tab = activeTab.value
  if (!tab) return
  const state = stateOf(tab.id)
  const sequence = ++querySequence
  const isCurrent = () =>
    sequence === querySequence && tab.id === activeTab.value.id

  try {
    await runQuery(async () => {
      // 切回已加载过的 Tab：直接用缓存恢复，不重复请求第一页
      if (pageNo === 1 && state.loaded) {
        // 缓存列表同样补一次价（模块级缓存命中时几乎零成本），
        // 保证会员价/出清价晚到或已过期时卡片能自动刷新
        loadZonePrices(state.items)
        await pagingRef.value?.completeByNoMore(
          [...state.items],
          !state.hasMore,
        )
        return
      }

      // 恢复缓存后 z-paging 的 pageNo 会回到 1，真实下一面以 Tab 自己的游标为准
      const currentPage = pageNo === 1 ? 1 : state.nextPage
      const result = await loadGoodsTabPage(tab, currentPage, pageSize)
      if (!isCurrent()) return

      let pageItems = result.list
      let reachedLimit = false
      if (currentPage === 1) {
        state.items = [...pageItems]
      } else {
        const merged = mergePagedItems(state.items, pageItems)
        pageItems = merged.items
        reachedLimit = merged.reachedLimit
        state.items = [...state.items, ...pageItems]
      }

      state.loaded = true
      state.nextPage = currentPage + 1
      const nomore =
        result.manual ||
        reachedLimit ||
        result.list.length === 0 ||
        (result.total > 0 && state.items.length >= result.total)
      state.hasMore = !nomore
      // 列表先渲染、价格后补：不 await，避免会员价/出清价接口阻塞首屏
      loadZonePrices(pageItems)
      await pagingRef.value?.completeByNoMore(pageItems, nomore)
    }, isCurrent)
  } finally {
    if (switchingTab && sequence === querySequence) {
      switchingTab = false
      // 等新列表完成一次渲染再释放占位，避免同一帧内高度先塌后涨
      await nextTick()
      feedMinHeight.value = 0
    }
  }
}

function measureFeedHeight(): Promise<number> {
  return new Promise(resolve => {
    uni
      .createSelectorQuery()
      .in(instance?.proxy ?? instance)
      .select('.goods-tabs__inner')
      .boundingClientRect(raw => {
        const rect = (Array.isArray(raw) ? raw[0] : raw) as {
          height?: number
        } | null
        resolve(Number(rect?.height || 0))
      })
      .exec()
  })
}

function measureTabPosition(): Promise<{ barTop: number; rootTop: number }> {
  return new Promise(resolve => {
    const query = uni.createSelectorQuery().in(instance?.proxy ?? instance)
    query.select('.goods-tabs').boundingClientRect()
    query.select('.goods-tabs__bar').boundingClientRect()
    query.exec(raw => {
      const result = Array.isArray(raw) ? raw : []
      const root = result[0] as { top?: number } | null
      const bar = result[1] as { top?: number } | null
      resolve({
        barTop: Number(bar?.top ?? 0),
        rootTop: Number(root?.top ?? 0),
      })
    })
  })
}

async function switchTab(index: number) {
  if (index === activeIndex.value || !tabs.value[index]) return

  const { barTop, rootTop } = await measureTabPosition()
  const paddingTop = Number(
    (showData.value.commonStyle as Record<string, unknown> | undefined)
      ?.styleTopPadding || 0,
  )
  // 栏顶高于「根节点 + 顶部内边距」说明已离开自然位置、处于吸顶状态
  const barStuck =
    showData.value.stickyTabs && barTop < rootTop + paddingTop - 0.5
  if (barStuck) {
    stickyOffsetPx.value = Math.max(0, barTop - paddingTop)
    await nextTick()
    // 锚点悬在根节点上方，滚到锚点即让 Tab 栏停在吸顶位置
    uni.pageScrollTo({ selector: `#${anchorId}`, duration: 200 })
  }

  const height = await measureFeedHeight()
  if (height > 0) feedMinHeight.value = height
  switchingTab = true
  activeIndex.value = index
  await nextTick()
  // 只重置当前 Tab 的数据；未吸顶时不主动滚动，保持用户当前页面位置
  pagingRef.value?.reload?.()?.catch?.(() => {})
}

function openGoods(id: string) {
  uni.navigateTo({ url: `/sub-pages/product/goods-detail/index?id=${id}` })
}

/**
 * 卡片信息 chip：规格 / 低库存。
 *
 * 运费（免配送费）不在商品卡片上展示，口径统一以商详 / 结算页为准。
 */
function cardTags(item: any): string[] {
  const tags: string[] = []
  if (item.specsInfo) tags.push(String(item.specsInfo))
  // 库存 chip 与全站库存口径同源（utils/stock-display）：售罄/低库存才出字，充足不外露数字
  const hint = stockHintText(item.stock)
  if (hint) tags.push(hint)
  return tags
}

/**
 * 卡片主价：会员价/捡漏价命中时切换，否则沿用列表价格（SPU 售价）。
 *
 * 会员价聚合结果由 `loadGoodsTabPage` 在 `memberBanner.enabled=true` 时写入
 * （仅该 Tab 会带），其它 Tab 的会员价/出清价由 `useZonePrices` 按本页 SPU 批量补；
 * 两种来源最终都收敛到 `cardDisplay`（共享 zone-price 口径）。
 */
function cardMainPrice(item: any): number {
  return cardDisplay(item).mainPrice
}

/**
 * 卡片划线价：由共享专区价口径给出。
 *
 * - 会员价/捡漏价命中：划当前售价（与商详「身份价 vs 当前售价」同口径），
 *   此时不再叠加 SPU 原价——否则会把身份价说成更大的折扣；
 * - 都未命中：沿用「SPU 原价 > 售价才划线」口径，并受 `showOriginalPrice` 开关约束。
 */
function cardStrikePrice(item: any): number | null {
  return cardDisplay(item).strikePrice
}

function splitPriceParts(value: number): { decimal: string; integer: string } {
  const [integer, decimal = '00'] = Number(value).toFixed(2).split('.')
  return { decimal, integer }
}

/** 普通价拆成整数/小数两段：整数大、小数小，与捡漏价同一套价格层级 */
function cardPriceParts(item: any): { decimal: string; integer: string } {
  return splitPriceParts(cardMainPrice(item))
}

/** 捡漏价拆成整数/小数两段：整数大、小数小，价格更像线下价签 */
function bargainPriceParts(item: any): { decimal: string; integer: string } {
  return splitPriceParts(cardMainPrice(item))
}

/** 角标文案（会员价/捡漏价）；无角标返回空串，卡片不渲染 */
function cardBadgeText(item: any): string {
  return zonePriceBadgeText(cardDisplay(item).badgeKind)
}

/** 会员价未达标/未登录时的引导文案（「会员专享 ¥X」）；无则不渲染 */
function cardMemberGuide(item: any): string {
  // 会员专区 Tab 的引导由数据层聚合结果直接透出（与商详/购物车同源）；
  // 其它 Tab 由共享口径根据批量补价结果生成。两处都取，避免引导在缓存未命中时丢失。
  return cardDisplay(item).guideText
    || item?.memberPriceDisplay?.guideText
    || ''
}
</script>

<template>
  <view class="goods-tabs" :style="dynamicStyles">
    <view
      :id="anchorId"
      class="goods-tabs__anchor"
      :style="{ top: `-${stickyOffsetPx}px` }"
    />
    <view
      v-if="tabs.length > 1"
      class="goods-tabs__bar"
      :class="{
        'goods-tabs__bar--sticky': showData.stickyTabs,
        'goods-tabs__bar--immersive': immersiveNav && showData.stickyTabs,
      }"
    >
      <view
        v-for="(tab, index) in tabs"
        :key="tab.id"
        class="goods-tabs__item"
        :class="{ 'goods-tabs__item--active': index === activeIndex }"
        @click="switchTab(index)"
      >
        <text class="goods-tabs__label">
          {{ tab.title }}
        </text>
        <view v-if="index === activeIndex" class="goods-tabs__indicator" />
      </view>
    </view>

    <!--
      会员权益条（Tab 头下方，Tab 级配置，默认关），文案只用确定性标签：
      会员态「您已是 X，本区享会员专享价」，不写折数（折率与本区会员价是两套体系）。
      只做权益透出，不做购买拦截。接口失败 / 未配置 / 未启用都不渲染，
      商品流不为会员接口等待（memberProfileRequested 保证只查一次）。
    -->
    <view
      v-if="memberBannerView"
      class="goods-tabs__member-banner"
      :class="{
        'goods-tabs__member-banner--guest': memberBannerView.state === 'guest',
      }"
      @click="onMemberBannerClick"
    >
      <text class="goods-tabs__member-banner-tag">
        {{ t('diy.goodsTabs.memberTag') }}
      </text>
      <text class="goods-tabs__member-banner-text">
        {{ memberBannerView.text }}
      </text>
      <text
        v-if="memberBannerActionText"
        class="goods-tabs__member-banner-action"
      >
        {{ memberBannerActionText }}
      </text>
    </view>

    <view
      class="goods-tabs__inner"
      :style="
        feedMinHeight > 0 ? { minHeight: `${feedMinHeight}px` } : undefined
      "
    >
      <!--
        use-page-scroll：复用装修页页面滚动，由 useDiyPagedLoadMore 测自身底边位置，
        接近底部前自动加载当前 Tab 的下一页（不出现「点击加载更多」）。
        refresher-enabled=false：装修页整体已有下拉刷新，避免与 z-paging 下拉冲突。
      -->
      <z-paging
        ref="pagingRef"
        v-model="goodsList"
        use-page-scroll
        :refresher-enabled="false"
        :default-page-size="pageSize"
        :auto-scroll-to-top-when-reload="false"
        loading-more-default-text=""
        @query="queryList"
      >
        <view class="goods-tabs__grid" :style="gridStyle">
          <view
            v-for="item in goodsList"
            :key="item.id"
            class="goods-tabs__card"
            :class="{ 'goods-tabs__card--soldout': item.stock <= 0 }"
            hover-class="goods-tabs__card--pressed"
            :hover-stay-time="80"
            @click="openGoods(item.id)"
          >
            <view class="goods-tabs__image-box">
              <image
                v-if="item.imageUrl"
                class="goods-tabs__image"
                :src="item.imageUrl"
                mode="aspectFit"
                lazy-load
              />
              <view v-else class="goods-tabs__image goods-tabs__image--empty">
                {{ t('diy.common.product') }}
              </view>
              <view v-if="item.stock <= 0" class="goods-tabs__sold-out">
                {{ t('diy.common.outOfStock') }}
              </view>
            </view>
            <view class="goods-tabs__name">
              {{ item.name }}
            </view>
            <view
              v-if="shouldShowSubtitle(item.name, item.subTitle)"
              class="goods-tabs__subtitle"
            >
              {{ item.subTitle }}
            </view>
            <view v-if="cardTags(item).length > 0" class="goods-tabs__tags">
              <text
                v-for="tag in cardTags(item)"
                :key="tag"
                class="goods-tabs__tag"
              >
                {{ tag }}
              </text>
            </view>
            <view class="goods-tabs__meta">
              <view class="goods-tabs__meta-text">
                <!-- 价格区优先级：会员价 > 捡漏价 > 普通价 -->
                <view v-if="showData.showPrice" class="goods-tabs__price-line">
                  <!--
                    会员价：金色小标签 + 近黑主价 + 灰色日常价划线。
                    不用整块底色，靠字重、颜色和小标签建立层级，避免抢商品图重心。
                  -->
                  <template v-if="showCardMemberPrice(item)">
                    <view class="goods-tabs__member-price">
                      <view class="goods-tabs__member-price-main">
                        <text class="goods-tabs__member-badge">
                          {{
                            item.memberPriceDisplay?.showMemberPrice
                              ? item.memberPriceDisplay.badgeText
                              : zonePriceBadgeText('member')
                          }}
                        </text>
                        <text
                          class="goods-tabs__price goods-tabs__price--member"
                        >
                          ¥{{ cardMainPrice(item).toFixed(2) }}
                        </text>
                      </view>
                      <view
                        v-if="
                          showData.showOriginalPrice &&
                          cardStrikePrice(item) !== null
                        "
                        class="goods-tabs__member-price-original"
                      >
                        <text class="goods-tabs__member-price-label">
                          {{ t('diy.goodsTabs.regularPrice') }}
                        </text>
                        <text class="goods-tabs__price-original">
                          ¥{{ Number(cardStrikePrice(item)).toFixed(2) }}
                        </text>
                      </view>
                    </view>
                  </template>
                  <!--
                    捡漏价：所有 Tab 都对出清标签商品生效（不再只在低价捡漏 Tab）。
                    小号「捡漏价」标签 + 放大的红色价格（整数大、小数小）+
                    灰色原价划线。对标小象「出清价」的价格主导层级，不做整块底色。
                  -->
                  <template v-else-if="showCardBargainPrice(item)">
                    <view class="goods-tabs__bargain-price">
                      <view class="goods-tabs__bargain-price-main">
                        <text class="goods-tabs__bargain-label">
                          {{ cardBadgeText(item) }}
                        </text>
                        <view class="goods-tabs__bargain-amount">
                          <text class="goods-tabs__bargain-symbol">¥</text>
                          <text class="goods-tabs__bargain-integer">{{
                            bargainPriceParts(item).integer
                          }}</text>
                          <text class="goods-tabs__bargain-decimal"
                            >.{{ bargainPriceParts(item).decimal }}</text
                          >
                        </view>
                        <text
                          v-if="
                            showData.showOriginalPrice &&
                            cardStrikePrice(item) !== null
                          "
                          class="goods-tabs__bargain-original-price"
                        >
                          ¥{{ Number(cardStrikePrice(item)).toFixed(2) }}
                        </text>
                      </view>
                    </view>
                  </template>
                  <template v-else>
                    <view class="goods-tabs__price-amount">
                      <text class="goods-tabs__price-symbol">¥</text>
                      <text class="goods-tabs__price-integer">{{
                        cardPriceParts(item).integer
                      }}</text>
                      <text class="goods-tabs__price-decimal"
                        >.{{ cardPriceParts(item).decimal }}</text
                      >
                    </view>
                    <!-- 普通价：沿用「原价 > 售价才划线」口径，与其它 Tab 完全一致 -->
                    <text
                      v-if="
                        showData.showOriginalPrice &&
                        cardStrikePrice(item) !== null
                      "
                      class="goods-tabs__price-original"
                    >
                      ¥{{ Number(cardStrikePrice(item)).toFixed(2) }}
                    </text>
                  </template>
                </view>
                <!-- 未达标/未登录的会员价引导：只透出「会员专享 ¥X」，主价不动 -->
                <view
                  v-if="showData.showPrice && cardMemberGuide(item)"
                  class="goods-tabs__member-guide"
                >
                  {{ cardMemberGuide(item) }}
                </view>
              </view>
              <quick-cart-button
                v-if="item.stock > 0"
                :spu-id="item.id"
                size="56rpx"
                :zone-prices="zone.skuZonePricesOf(item.id)"
              />
            </view>
          </view>
        </view>
        <template #empty>
          <view class="goods-tabs__empty">
            {{ t('diy.common.noProducts') }}
          </view>
        </template>
      </z-paging>
    </view>

    <!--
      会员介绍弹层：点权益条右侧「了解会员」唤起。
      会员态数据复用权益条已查到的 memberProfile，弹层自身不发请求。
    -->
    <member-intro-sheet
      v-model="memberIntroVisible"
      :is-member="memberBannerView?.state === 'member'"
      :level-name="memberProfile?.levelName || ''"
      @member-center="onMemberIntroCenter"
    />
  </view>
</template>

<style lang="scss" scoped>
.goods-tabs {
  position: relative;
}

/* 滚动锚点：top 由切 Tab 时的吸顶偏移动态写入，滚到它即可让 Tab 栏停在吸顶位置 */
.goods-tabs__anchor {
  position: absolute;
  left: 0;
  width: 1rpx;
  height: 1rpx;
  opacity: 0;
  pointer-events: none;
}

.goods-tabs__bar {
  display: flex;
  align-items: center;
  height: 88rpx;
  background: #fff;
  border-bottom: 1rpx solid #f2f3f5;
}

/* 吸顶偏移由装修页通过 --diy-sticky-top 注入（避开固定导航栏；内嵌场景为 0） */
.goods-tabs__bar--sticky {
  position: sticky;
  top: var(--diy-sticky-top, 0);
  z-index: 10;
}

/*
 * 沉浸式导航开启时，吸顶 Tab 栏与顶栏共用同一磨砂材质。
 *
 * 变量由 DiyPage 挂在 .diy-page 根节点（与 --diy-sticky-top 同一继承链路）：
 *   --diy-nav-surface-bg   目标底色（磨砂白 rgba .86 / 主题色 / 自定义色）
 *   --diy-nav-surface-blur backdrop-filter 值（毛玻璃关闭时为 none）
 *   --diy-nav-hairline     底部细线色（与 hr-navbar 的 hairline 同色）
 * 未下发变量（内嵌页 / 未开沉浸式导航）时回落原来的白底 + #f2f3f5 细线。
 */
.goods-tabs__bar--immersive {
  background: var(--diy-nav-surface-bg, #fff);
  border-bottom-color: var(--diy-nav-hairline, #f2f3f5);
  -WEBKIT-backdrop-filter: var(--diy-nav-surface-blur, none);
  backdrop-filter: var(--diy-nav-surface-blur, none);
}

/*
 * 会员权益条：金色底表达「会员身份权益」，游客/无等级态换成主题红色调，
 * 颜色差异让用户一眼区分「我享有」与「我还没开通」。
 */
.goods-tabs__member-banner {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin: 12rpx 20rpx 0;
  padding: 14rpx 18rpx;
  border-radius: 12rpx;
  background: linear-gradient(90deg, #fff6e8, #ffe9cf);
  color: #8a5a1c;
  font-size: 24rpx;
  line-height: 1.4;
}

.goods-tabs__member-banner--guest {
  background: linear-gradient(90deg, #fff1f2, #ffe4e6);
  color: #9b3b47;
}

.goods-tabs__member-banner-tag {
  flex: none;
  padding: 2rpx 10rpx;
  border-radius: 999rpx;
  background: #c8a05a;
  color: #fff;
  font-size: 20rpx;
  line-height: 1.6;
}

.goods-tabs__member-banner--guest .goods-tabs__member-banner-tag {
  background: var(--wot-color-theme-primary, #ff2237);
}

.goods-tabs__member-banner-text {
  flex: 1;
  min-width: 0;
}

.goods-tabs__member-banner-action {
  flex: none;
  font-weight: 600;
  color: #b0803a;
}

.goods-tabs__member-banner--guest .goods-tabs__member-banner-action {
  color: var(--wot-color-theme-primary, #ff2237);
}

.goods-tabs__item {
  position: relative;
  display: flex;
  flex: 1;
  height: 100%;
  align-items: center;
  justify-content: center;
  padding: 0 12rpx;
  color: #4e5969;
  font-size: 30rpx;
}

.goods-tabs__item--active {
  color: #1f2329;
  font-weight: 600;
}

.goods-tabs__indicator {
  position: absolute;
  bottom: 10rpx;
  left: 50%;
  width: 40rpx;
  height: 6rpx;
  margin-left: -20rpx;
  border-radius: 999rpx;
  background: var(--wot-color-theme-primary, #ff2237);
}

.goods-tabs__inner {
  padding: 16rpx 20rpx 20rpx;
}

.goods-tabs__grid {
  display: grid;
  gap: 20rpx;
}

.goods-tabs__card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-sizing: border-box;
  border: 1rpx solid #f0f1f3;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 8rpx 24rpx rgb(31 35 41 / 5%);
  transition: transform 160ms ease, box-shadow 160ms ease;
}

/* 小程序 hover-class：轻缩反馈，不改变卡片排版 */
.goods-tabs__card--pressed {
  transform: scale(0.985);
  box-shadow: 0 2rpx 10rpx rgb(31 35 41 / 8%);
}

/* 小程序 <image> 自带默认宽高，用 padding-top 百分比把图片区锁成 1:1 */
.goods-tabs__image-box {
  position: relative;
  width: 100%;
  padding-top: 100%;
  background: #fff;
}

.goods-tabs__image {
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: #a8abb2;
  font-size: 24rpx;
}

.goods-tabs__image--empty {
  background: #f3f4f6;
}

/* 售罄：图片压暗 + 居中胶囊，不再用顶部黑条挡商品图 */
.goods-tabs__card--soldout .goods-tabs__image {
  opacity: 0.45;
}

.goods-tabs__sold-out {
  position: absolute;
  top: 50%;
  left: 50%;
  padding: 10rpx 22rpx;
  border-radius: 999rpx;
  background: rgb(17 24 39 / 72%);
  color: #fff;
  font-size: 22rpx;
  line-height: 1.4;
  transform: translate(-50%, -50%);
}

.goods-tabs__name {
  display: -webkit-box;
  overflow: hidden;
  min-height: 1.4em;
  margin: 16rpx 20rpx 0;
  color: #1f2329;
  font-size: 28rpx;
  font-weight: 500;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

/* 商品子标题（商品介绍）：单行省略，与其它商品卡副标题口径一致 */
.goods-tabs__subtitle {
  overflow: hidden;
  margin: 6rpx 20rpx 0;
  color: #8a919f;
  font-size: 22rpx;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-tabs__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  padding: 10rpx 20rpx 0;
}

.goods-tabs__tag {
  padding: 2rpx 10rpx;
  border-radius: 999rpx;
  background: #f4f5f7;
  color: #6b7280;
  font-size: 20rpx;
  line-height: 1.6;
}

.goods-tabs__meta {
  display: flex;
  margin-top: auto;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8rpx;
  padding: 14rpx 20rpx 20rpx;
}

.goods-tabs__meta-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.goods-tabs__price-line {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10rpx;
  min-width: 0;
}

/*
 * 会员价：不用整块底色，靠金色小标签 + 近黑主价 + 灰色日常价建立层级，
 * 与其它 Tab 的红色促销价拉开差异，也不抢商品图的视觉重心。
 * 3 列模式允许换行，不挤压右侧购物车按钮。
 */
.goods-tabs__member-price {
  display: flex;
  flex-direction: column;
  gap: 2rpx;
  align-self: flex-start;
  max-width: 100%;
  min-width: 0;
}

.goods-tabs__member-price-main {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6rpx;
  min-width: 0;
}

.goods-tabs__member-price-original {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
  min-width: 0;
}

.goods-tabs__member-price-label {
  flex: none;
  color: #9aa3ad;
  font-size: 18rpx;
  line-height: 1.5;
}

.goods-tabs__member-price-original .goods-tabs__price-original {
  color: #9aa3ad;
  font-size: 20rpx;
}

/*
 * 低价捡漏：价格主导。小号「捡漏价」标签 + 放大红色价格（整数大、小数小）+
 * 灰色原价划线，不做整块底色，避免促销贴纸感。
 */
.goods-tabs__bargain-price {
  align-self: flex-start;
  max-width: 100%;
  min-width: 0;
}

.goods-tabs__bargain-price-main {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6rpx;
  min-width: 0;
}

.goods-tabs__bargain-label {
  flex: none;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 18rpx;
  font-weight: 600;
  line-height: 1.5;
}

.goods-tabs__bargain-amount {
  display: inline-flex;
  align-items: baseline;
  color: var(--wot-color-theme-primary, #ff2237);
  min-width: 0;
}

.goods-tabs__bargain-symbol {
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1;
}

.goods-tabs__bargain-integer {
  font-size: 40rpx;
  font-weight: 700;
  line-height: 1.05;
}

.goods-tabs__bargain-decimal {
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1;
}

.goods-tabs__bargain-original-price {
  flex: none;
  color: #9aa3ad;
  font-size: 20rpx;
  font-weight: normal;
  text-decoration: line-through;
}

.goods-tabs__price {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 32rpx;
  font-weight: 600;
}

/* 普通价：整数大、小数小、符号降一档，价格层级更接近线下价签 */
.goods-tabs__price-amount {
  display: inline-flex;
  align-items: baseline;
  min-width: 0;
  color: var(--wot-color-theme-primary, #ff2237);
}

.goods-tabs__price-symbol {
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1;
}

.goods-tabs__price-integer {
  font-size: 36rpx;
  font-weight: 700;
  line-height: 1.05;
}

.goods-tabs__price-decimal {
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1;
}

/* 会员价主价：近黑高对比，靠字号与字重强调；金色身份由左侧小标签承担 */
.goods-tabs__price--member {
  color: #1f2329;
  font-size: 34rpx;
  font-weight: 700;
}

.goods-tabs__price-original {
  flex: none;
  color: #999;
  font-size: 22rpx;
  font-weight: normal;
  text-decoration: line-through;
}

/* 会员价角标：浅金描边小标签，不做实心色块，保持卡片留白 */
.goods-tabs__member-badge {
  flex: none;
  padding: 2rpx 8rpx;
  border: 1rpx solid #ecd4a5;
  border-radius: 6rpx;
  background: #fffaf0;
  color: #a06a1f;
  font-size: 18rpx;
  font-weight: 600;
  line-height: 1.5;
}

/* 会员价引导：未达标/未登录时的一行小字，不改变主价与划线口径 */
.goods-tabs__member-guide {
  align-self: flex-start;
  margin-top: 4rpx;
  color: #a06a1f;
  font-size: 20rpx;
  line-height: 1.5;
}

.goods-tabs__sales {
  color: #909399;
  font-size: 20rpx;
}

.goods-tabs__empty {
  padding: 60rpx 0;
  color: #999;
  font-size: 26rpx;
  text-align: center;
}
</style>
