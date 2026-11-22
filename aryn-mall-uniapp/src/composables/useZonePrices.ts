/**
 * 商品列表「专区价」批量取数 composable。
 *
 * 会员价按用户身份下发、出清价对所有用户一致、协议价按主体/船舶圈定、档期特供按
 * 采购场景/船舶圈定，四者都不在商品列表接口里：列表卡片渲染后必须按本页 SPU 批量
 * 补一次价，失败静默回落原价。商品卡营销标签（新人价/秒杀价，`card-tags` 端点）
 * 同属「列表后批量补」管线：新人价按登录身份判定资格、秒杀价全局一致，SPU 级聚合一次拉全。
 *
 * 设计要点：
 * - **模块级缓存**：多个楼层/多个 Tab/翻页会重复请求同一批 SPU，按 spuId 缓存 60s，
 *   只补「缺失或过期」的 SPU，避免首页多楼层把价格接口打爆；特供接口不分 SPU（整量
 *   按场景/船舶下发），因此按「上下文指纹 + 60s」缓存一份 SPU/SKU 索引；
 * - **上下文指纹失效**：登录态（token）与采购上下文（buyerOrgId/vesselId/purchaseScene）
 *   任一变化即整体失效——切船后不得继续显示上一条船的合同价/特供价；
 * - **响应式 revision**：列表先渲染、价格晚一帧回来，computed 通过读取 revision
 *   自动重算，用户看到的是渐进增强而不是白屏等待；
 * - **口径唯一**：会员价复用 `utils/member-price#resolveListMemberPrice`（含资格取优、
 *   未达标引导），出清价复用 `utils/clearance-price#resolveListClearanceDisplay`，
 *   卡片收敛统一走 `utils/zone-price#resolveListZonePrice`，各页面不再自行乘折扣/比价；
 * - **游客可访问**：协议价/特供价接口免登，服务端对游客/非成员自动清空主体上下文，
 *   前端不做登录门槛（无上下文也请求，命中即为可享价）；
 * - **失败静默**：价格标注属增强展示，接口异常绝不影响商品流渲染。
 */

import type { GoodsCardTagItem } from '@/api/product/spu'
import type { AppMemberSkuPriceVO } from '@/api/promotion'
import type { PolicyPriceItem } from '@/api/promotion/policy-price'
import type { CalendarSpecialItem } from '@/api/promotion/trade-calendar'
import type { ListMemberPriceDisplay } from '@/components/diy/retail-types'
import type { AppClearancePriceItem, ClearancePriceDisplay } from '@/utils/clearance-price'
import type { ListZonePrice } from '@/utils/zone-price'

import { ref } from 'vue'
import { getCardTags, getClearanceSpuPrices } from '@/api/product/spu'
import { getMemberSpuPrices } from '@/api/promotion'
import { getPolicyPrices } from '@/api/promotion/policy-price'
import { getCalendarSpecials } from '@/api/promotion/trade-calendar'
import { useShipContextStore } from '@/store/shipContextStore'
import {
  clearanceItemsBySpuId,
  emptyClearancePriceDisplay,
  resolveListClearanceDisplay,
} from '@/utils/clearance-price'
import {
  emptyListMemberPriceDisplay,
  memberPriceItemsBySpuId,
  resolveListMemberPrice,
} from '@/utils/member-price'
import { resolveListZonePrice } from '@/utils/zone-price'

/** 列表价格缓存时长：会员价/出清价都是运营配置，一分钟内足够新鲜，短于用户浏览一屏的时间 */
const CACHE_TTL_MS = 60_000

/** 后端 `/app/member/prices?spuIds=` 单次上限 50（超出静默忽略），前端主动分片 */
const MEMBER_SPU_CHUNK = 50

/** `/app/goodsspu/card-tags?spuIds=` 单次上限 200（服务端再截断），前端留余量分片 */
const CARD_TAG_CHUNK = 100

/** 协议价批量查询单次分片：与会员价同量级（POST body，防超大 payload） */
const POLICY_SPU_CHUNK = 50

interface CacheEntry {
  items: AppMemberSkuPriceVO[]
  fetchedAt: number
}

interface ClearanceCacheEntry {
  items: AppClearancePriceItem[]
  fetchedAt: number
}

interface PolicyCacheEntry {
  items: PolicyPriceItem[]
  fetchedAt: number
}

/** 卡面营销标签（SPU 聚合后两个可享价 + 社证成文与类型 + 价格声明；null=未命中/未达门槛） */
interface CardTagCacheEntry {
  fetchedAt: number
  newbiePrice: number | null
  seckillPrice: number | null
  socialProofText: string | null
  socialProofKind: string | null
  priceTagText: string | null
}

const memberCache = new Map<string, CacheEntry>()
const clearanceCache = new Map<string, ClearanceCacheEntry>()
const cardTagCache = new Map<string, CardTagCacheEntry>()
const policyCache = new Map<string, PolicyCacheEntry>()
/** 最近一次会员价响应里的资格与折扣率（同一用户全局一致） */
let memberEligible: boolean | null = null
let memberDiscountRate: number | null = null

/**
 * 档期特供索引（整量拉取，非按 SPU 分片）。
 *
 * `/app/calendar/specials` 只按场景/船舶圈定、不接受 spuIds，因此一次拉全后在本地
 * 建 SPU 索引（卡面按 SPU 取最低价）；`scopeKey` 记录拉取时的上下文指纹，
 * 切船/切场景/换登录态即失效。服务端已剔除限量耗尽活动，前端不再按 remainingQuota 过滤。
 */
interface SpecialIndex {
  bySpu: Map<string, CalendarSpecialItem[]>
  fetchedAt: number
  scopeKey: string
}
let specialIndex: SpecialIndex | null = null

/** 采购上下文（协议价/特供价共用；岸上企业上下文页面可覆盖默认解析器） */
interface PriceContext {
  buyerOrgId?: string
  purchaseScene?: string
  vesselId?: string
}

/**
 * 价格上下文解析器：返回当前会话的采购主体/船舶/场景上下文。
 *
 * 默认读船上下文（协议价按 vessel 圈定的合同价自动生效；pinia 未激活时回落 null，
 * 测试环境无需建 pinia）；岸上企业上下文页面可 setPolicyPriceContextResolver 临时覆盖
 * （C 端无持久企业上下文 store）。**无上下文也照常请求**：接口免登，服务端对游客/
 * 非成员自动清空主体上下文，不会下发合同价。
 */
type PolicyContextResolver = () => PriceContext | null
let policyContextResolver: PolicyContextResolver = defaultPolicyContext

function defaultPolicyContext(): PriceContext | null {
  try {
    // 函数内 use store：模块加载期 pinia 可能未安装（测试/首帧），异常回落无上下文
    const store = useShipContextStore()
    return { purchaseScene: store.purchaseScene, vesselId: store.vesselId || undefined }
  }
  catch {
    return null
  }
}

/** 注册协议价上下文解析器（岸上企业上下文页面可覆盖默认船上下文；测试可注入受控上下文） */
export function setPolicyPriceContextResolver(resolver: PolicyContextResolver): void {
  policyContextResolver = resolver
}

/** 恢复默认解析器（测试隔离用） */
export function resetPolicyPriceContextResolver(): void {
  policyContextResolver = defaultPolicyContext
}

/** 价格缓存版本号：价格到达/失效时自增，驱动页面 computed 重算 */
const revision = ref(0)

/** 正在请求中的 SPU（合并同一页多组件并发补价，避免重复请求） */
const pendingMember = new Set<string>()
const pendingClearance = new Set<string>()
const pendingCardTag = new Set<string>()
const pendingPolicy = new Set<string>()
/** 进行中的请求 Promise：同一批 SPU 的并发调用共享结果 */
let memberInflight: Promise<void> | null = null
let clearanceInflight: Promise<void> | null = null
let cardTagInflight: Promise<void> | null = null
let policyInflight: Promise<void> | null = null
let specialInflight: Promise<void> | null = null

/**
 * 缓存所属的「登录态 + 采购上下文」指纹。
 *
 * 会员价资格（eligible / userDiscountRate）是用户身份相关的，协议价/特供价还额外
 * 按 buyerOrgId/vesselId/purchaseScene 圈定：登录、退出、切换账号、**切船/切采购场景**后，
 * 60s 内的缓存若继续复用，会把游客/旧等级/上一条船的合同价展示给新上下文。
 * 在每次补价入口比对指纹，变化即整体失效——不依赖各页面手动调用。
 */
let cacheScopeKey: string | null = null

/** 当前登录态指纹：只取 token（未登录为空串）；storage 异常按未登录处理 */
function currentAuthKey(): string {
  try {
    const auth = uni.getStorageSync('auth') as { token?: string } | undefined
    return String(auth?.token || '')
  }
  catch {
    return ''
  }
}

/** 上下文指纹：token + 采购主体 + 船舶 + 采购场景（任一变化都要重新补价） */
function scopeKeyOf(context: PriceContext | null): string {
  return [
    currentAuthKey(),
    context?.buyerOrgId || '',
    context?.vesselId || '',
    context?.purchaseScene || '',
  ].join('|')
}

/**
 * 登录态/采购上下文变化时整体失效价格缓存。
 *
 * @returns 是否发生了失效（调用方据此触发重渲染）
 */
function syncCacheScope(context: PriceContext | null): boolean {
  const key = scopeKeyOf(context)
  if (cacheScopeKey === null) {
    cacheScopeKey = key
    return false
  }
  if (cacheScopeKey === key)
    return false
  clearCaches()
  cacheScopeKey = key
  revision.value++
  return true
}

function isFresh(fetchedAt: number, now: number = Date.now()): boolean {
  return now - fetchedAt < CACHE_TTL_MS
}

function uniqueSpuIds(spuIds: Array<string | number | null | undefined>): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const raw of spuIds) {
    const id = String(raw ?? '').trim()
    if (!id || seen.has(id))
      continue
    seen.add(id)
    result.push(id)
  }
  return result
}

function chunk<T>(list: T[], size: number): T[][] {
  if (size <= 0)
    return [list]
  const chunks: T[][] = []
  for (let index = 0; index < list.length; index += size)
    chunks.push(list.slice(index, index + size))
  return chunks
}

function writeMemberCache(vo: Awaited<ReturnType<typeof getMemberSpuPrices>>, ids: string[]) {
  const itemsBySpu = memberPriceItemsBySpuId(vo)
  const now = Date.now()
  for (const spuId of ids) {
    const items = itemsBySpu.get(spuId) || []
    memberCache.set(spuId, { fetchedAt: now, items })
  }
  if (vo) {
    if (vo.eligible === true || vo.eligible === false)
      memberEligible = vo.eligible
    else
      memberEligible = null
    memberDiscountRate = vo.userDiscountRate === null || vo.userDiscountRate === undefined
      ? null
      : Number(vo.userDiscountRate)
  }
}

async function fetchMemberPrices(ids: string[]): Promise<void> {
  if (!ids.length)
    return
  for (const part of chunk(ids, MEMBER_SPU_CHUNK)) {
    try {
      const vo = await getMemberSpuPrices(part)
      writeMemberCache(vo, part)
    }
    catch {
      // 失败也写空缓存：本页 60s 内不再重复打接口，价格回落原价
      const now = Date.now()
      for (const spuId of part)
        memberCache.set(spuId, { fetchedAt: now, items: [] })
    }
    part.forEach(id => pendingMember.delete(id))
  }
}

async function fetchClearancePrices(ids: string[]): Promise<void> {
  if (!ids.length)
    return
  try {
    const vo = await getClearanceSpuPrices(ids)
    const itemsBySpu = clearanceItemsBySpuId(vo)
    const now = Date.now()
    for (const spuId of ids) {
      const items = itemsBySpu.get(spuId) || []
      clearanceCache.set(spuId, { fetchedAt: now, items })
    }
  }
  catch {
    const now = Date.now()
    for (const spuId of ids)
      clearanceCache.set(spuId, { fetchedAt: now, items: [] })
  }
  finally {
    ids.forEach(id => pendingClearance.delete(id))
  }
}

/** card-tags 响应聚合成 SPU 级标签（价签同 SPU 多行取低；社证取首个非空成文） */
function cardTagEntryFrom(items: GoodsCardTagItem[], now: number): CardTagCacheEntry {
  let newbiePrice: number | null = null
  let seckillPrice: number | null = null
  let socialProofText: string | null = null
  let socialProofKind: string | null = null
  let priceTagText: string | null = null
  for (const item of items) {
    const newbie = Number(item?.newbiePrice)
    if (Number.isFinite(newbie) && newbie > 0 && (newbiePrice === null || newbie < newbiePrice))
      newbiePrice = newbie
    const seckill = Number(item?.seckillPrice)
    if (Number.isFinite(seckill) && seckill > 0 && (seckillPrice === null || seckill < seckillPrice))
      seckillPrice = seckill
    const proof = typeof item?.socialProofText === 'string' ? item.socialProofText.trim() : ''
    if (!socialProofText && proof) {
      socialProofText = proof
      socialProofKind = typeof item?.socialProofKind === 'string' && item.socialProofKind
        ? item.socialProofKind
        : null
    }
    const tag = typeof item?.priceTagText === 'string' ? item.priceTagText.trim() : ''
    if (!priceTagText && tag)
      priceTagText = tag
  }
  return { fetchedAt: now, newbiePrice, seckillPrice, socialProofKind, socialProofText, priceTagText }
}

async function fetchCardTags(ids: string[]): Promise<void> {
  if (!ids.length)
    return
  for (const part of chunk(ids, CARD_TAG_CHUNK)) {
    try {
      const items = await getCardTags(part)
      const itemsBySpu = new Map<string, GoodsCardTagItem[]>()
      for (const item of items || []) {
        const spuId = String(item?.spuId || '').trim()
        if (!spuId)
          continue
        const list = itemsBySpu.get(spuId) || []
        list.push(item)
        itemsBySpu.set(spuId, list)
      }
      const now = Date.now()
      for (const spuId of part)
        cardTagCache.set(spuId, cardTagEntryFrom(itemsBySpu.get(spuId) || [], now))
    }
    catch {
      // 失败也写空缓存：本页 60s 内不再重复打接口，标签回落不展示
      const now = Date.now()
      for (const spuId of part)
        cardTagCache.set(spuId, { fetchedAt: now, newbiePrice: null, seckillPrice: null, socialProofKind: null, socialProofText: null, priceTagText: null })
    }
    part.forEach(id => pendingCardTag.delete(id))
  }
}

/**
 * 协议价批量拉取（免登可访问，游客也请求）。
 *
 * 协议价是主体/船舶/客群 scoped 的合同价：服务端对游客/非成员自动清空主体上下文，
 * 因此前端不做登录/上下文门槛——有船/企业上下文就带上，没有就按游客口径请求。
 * 接口失败静默回落原价（写空缓存占位，避免同页反复尝试），不影响商品流。
 * 缓存吃「登录态 + 上下文」指纹失效（syncCacheScope）与 60s TTL；请求飞行期间上下文
 * 变化时丢弃本次结果，避免把上一条船的合同价写进新上下文的缓存。
 */
async function fetchPolicyPrices(ids: string[], context: PriceContext): Promise<void> {
  const startScopeKey = scopeKeyOf(context)
  for (const part of chunk(ids, POLICY_SPU_CHUNK)) {
    try {
      const items = await getPolicyPrices({
        buyerOrgId: context.buyerOrgId,
        spuIds: part,
        vesselId: context.vesselId,
      })
      if (cacheScopeKey !== startScopeKey)
        continue
      const itemsBySpu = new Map<string, PolicyPriceItem[]>()
      for (const item of items || []) {
        const spuId = String(item?.spuId || '').trim()
        if (!spuId)
          continue
        const list = itemsBySpu.get(spuId) || []
        list.push(item)
        itemsBySpu.set(spuId, list)
      }
      const stamped = Date.now()
      for (const spuId of part)
        policyCache.set(spuId, { fetchedAt: stamped, items: itemsBySpu.get(spuId) || [] })
    }
    catch {
      if (cacheScopeKey !== startScopeKey)
        continue
      const stamped = Date.now()
      for (const spuId of part)
        policyCache.set(spuId, { fetchedAt: stamped, items: [] })
    }
    finally {
      part.forEach(id => pendingPolicy.delete(id))
    }
  }
}

/**
 * 档期特供整量拉取（免登可访问，按采购场景/船舶圈定）。
 *
 * 接口不接受 spuIds：一次拉全后建 SPU 索引，同 SPU 命中多活动保留全部项，
 * 由取价 helper 取最低（与商详/结算同口径）。服务端已剔除限量耗尽活动。
 * 失败静默写空索引（60s 内不再重复打接口）；上下文变化时丢弃本次结果。
 */
async function fetchSpecials(context: PriceContext): Promise<void> {
  const startScopeKey = scopeKeyOf(context)
  const empty = (): SpecialIndex => ({
    bySpu: new Map(),
    fetchedAt: Date.now(),
    scopeKey: startScopeKey,
  })
  try {
    const specials = await getCalendarSpecials({
      purchaseScene: context.purchaseScene,
      vesselId: context.vesselId,
    })
    if (cacheScopeKey !== startScopeKey)
      return
    const index = empty()
    for (const activity of specials || []) {
      for (const item of activity?.items || []) {
        const price = Number(item?.price)
        if (!Number.isFinite(price) || price <= 0)
          continue
        const spuId = String(item?.spuId || '').trim()
        if (!spuId)
          continue
        index.bySpu.set(spuId, [...(index.bySpu.get(spuId) || []), item])
      }
    }
    specialIndex = index
  }
  catch {
    if (cacheScopeKey !== startScopeKey)
      return
    specialIndex = empty()
  }
}

/** 某 SPU 的卡面特供价（同 SPU 多活动/多 SKU 取最低；倒挂由 resolveListZonePrice 再防御） */
function specialCardPriceOf(spuId: string): number | null {
  return lowestSpecialPrice(specialIndex?.bySpu.get(spuId))
}

function lowestSpecialPrice(items: CalendarSpecialItem[] | undefined): number | null {
  let best: number | null = null
  for (const item of items || []) {
    const price = Number(item?.price)
    if (!Number.isFinite(price) || price <= 0)
      continue
    if (best === null || price < best)
      best = price
  }
  return best
}

/**
 * 某 SPU 的卡面协议价（同 SPU 多 SKU 取最低；倒挂/非正数由 resolveListZonePrice 再防御）。
 *
 * **minQty 参与成交校验**：卡片数量未定（按 1 件口径展示），因此起订量 > 1 的政策
 * 不上卡——否则卡面标合同价、结算按售价，价差观感重现。商详/购物车按实际数量另行 gating。
 */
function policyCardPriceOf(spuId: string): number | null {
  const entry = policyCache.get(spuId)
  if (!entry)
    return null
  let best: number | null = null
  for (const item of entry.items) {
    const price = Number(item?.policyPrice)
    if (!Number.isFinite(price) || price <= 0)
      continue
    if (policyMinQtyOf(item) > 1)
      continue
    if (best === null || price < best)
      best = price
  }
  return best
}

/** 协议起订量（缺省/脏数据按 1 件，即无门槛） */
function policyMinQtyOf(item: PolicyPriceItem | null | undefined): number {
  const minQty = Math.floor(Number(item?.minQty))
  return Number.isFinite(minQty) && minQty > 0 ? minQty : 1
}

function pickMissing(
  ids: string[],
  cache: Map<string, { fetchedAt: number }>,
  pending: Set<string>,
): string[] {
  const now = Date.now()
  return ids.filter((id) => {
    if (pending.has(id))
      return false
    const entry = cache.get(id)
    return !entry || !isFresh(entry.fetchedAt, now)
  })
}

/** 清空价格与资格缓存（不动版本号，由调用方决定何时重渲染） */
function clearCaches(): void {
  memberCache.clear()
  clearanceCache.clear()
  cardTagCache.clear()
  policyCache.clear()
  pendingMember.clear()
  pendingClearance.clear()
  pendingCardTag.clear()
  pendingPolicy.clear()
  memberEligible = null
  memberDiscountRate = null
  specialIndex = null
  memberInflight = null
  clearanceInflight = null
  cardTagInflight = null
  policyInflight = null
  specialInflight = null
}

/** 清理模块级缓存（登录态/采购上下文切换后资格与价格可能整体变化；测试也用它隔离） */
export function resetZonePriceCache(): void {
  clearCaches()
  cacheScopeKey = scopeKeyOf(policyContextResolver())
  revision.value++
}

export interface ZonePriceLoadOptions {
  /** 是否补会员价（默认 true）；会员专区 Tab 已由数据层按活动补过价时传 false */
  member?: boolean
  /** 是否补出清价（默认 true） */
  clearance?: boolean
  /** 是否补卡面营销标签（默认 true）：新人价/秒杀价（card-tags 端点） */
  cardTag?: boolean
  /** 是否补协议价（默认 true；免登可访问，游客/无上下文也请求，服务端自动清空主体） */
  policy?: boolean
  /** 是否补档期特供价（默认 true；免登可访问，按采购场景/船舶圈定） */
  special?: boolean
}

export interface ZonePriceLookup {
  /** 补拉本页 SPU 的会员价/出清价（分批、带缓存、失败静默） */
  loadForSpuIds: (spuIds: Array<string | number | null | undefined>, options?: ZonePriceLoadOptions) => Promise<void>
  /** 某 SPU 的列表会员价展示（复用于所有卡片） */
  memberDisplayOf: (spuId: string, cardPrice: unknown) => ListMemberPriceDisplay
  /** 某 SPU 的列表出清价展示（SPU 聚合口径） */
  clearanceDisplayOf: (spuId: string, cardPrice: unknown) => ClearancePriceDisplay
  /**
   * 卡片专区价展示：入参为列表项（`id` + `price`/`salesPrice` + `originalPrice`）。
   *
   * @param item 归一化后的列表项或接口原始 SPU
   * @param showOriginal 装修「显示原价」开关；false 时只隐藏划线，不隐藏专区主价
   */
  cardPriceOf: (item: { id?: unknown, price?: unknown, salesPrice?: unknown, originalPrice?: unknown } | null | undefined, showOriginal?: boolean) => ListZonePrice
  /** 某 SPU 的卡面营销标签原始值（新人价/秒杀价/社证成文；自建 resolve 调用的组件用） */
  cardTagOf: (spuId: string) => { newbiePrice: number | null, seckillPrice: number | null, socialProofKind: string | null, socialProofText: string | null, priceTagText: string | null } | null
  /** 某 SPU 下按 SKU 的专区成交价（多规格弹层/快捷加购确认用） */
  skuZonePricesOf: (spuId: string) => Record<string, number>
  /** 某 SKU 的专区成交价（无命中返回 null） */
  skuZonePriceOf: (spuId: string, skuId: string) => number | null
  /** 当前缓存版本号（组件可读它触发 computed 重算） */
  revision: typeof revision
}

function spuIdOf(item: { id?: unknown } | null | undefined): string {
  return String(item?.id ?? '').trim()
}

function cardSalesPriceOf(item: { price?: unknown, salesPrice?: unknown } | null | undefined): number {
  const raw = item?.price ?? item?.salesPrice
  const value = Number(raw)
  return Number.isFinite(value) ? value : 0
}

/**
 * 创建（或复用）专区价查询句柄。
 *
 * 缓存与资格状态都在模块级，组件实例可安全多次调用；`revision` 是模块级 ref，
 * 任一组件补价成功都会让所有订阅它的 computed 一起重算。
 */
export function useZonePrices(): ZonePriceLookup {
  function memberDisplayOf(spuId: string, cardPrice: unknown): ListMemberPriceDisplay {
    // 读取 revision 建立响应式依赖：价格晚到时页面的 computed 会自动重算
    void revision.value
    const id = String(spuId || '').trim()
    if (!id)
      return emptyListMemberPriceDisplay()
    const entry = memberCache.get(id)
    if (!entry)
      return emptyListMemberPriceDisplay()
    return resolveListMemberPrice(
      entry.items,
      id,
      cardPrice,
      memberEligible,
      memberDiscountRate,
    )
  }

  function clearanceDisplayOf(spuId: string, cardPrice: unknown): ClearancePriceDisplay {
    void revision.value
    const id = String(spuId || '').trim()
    if (!id)
      return emptyClearancePriceDisplay()
    const entry = clearanceCache.get(id)
    if (!entry)
      return emptyClearancePriceDisplay()
    return resolveListClearanceDisplay({ items: entry.items }, id, cardPrice)
  }

  /** 某 SPU 的卡面营销标签（未拉取/未命中返回 null，标签位不渲染） */
  function cardTagOf(spuId: string): CardTagCacheEntry | null {
    void revision.value
    const id = String(spuId || '').trim()
    if (!id)
      return null
    return cardTagCache.get(id) || null
  }

  function cardPriceOf(
    item: { id?: unknown, price?: unknown, salesPrice?: unknown, originalPrice?: unknown } | null | undefined,
    showOriginal = true,
  ): ListZonePrice {
    void revision.value
    const id = spuIdOf(item)
    const sales = cardSalesPriceOf(item)
    if (!id) {
      return resolveListZonePrice({
        salesPrice: sales,
        originalPrice: item?.originalPrice,
        showOriginal,
      })
    }
    const tags = cardTagOf(id)
    return resolveListZonePrice({
      salesPrice: sales,
      originalPrice: item?.originalPrice,
      showOriginal,
      policyPrice: policyCardPriceOf(id),
      specialPrice: specialCardPriceOf(id),
      memberDisplay: memberDisplayOf(id, sales),
      clearanceDisplay: clearanceDisplayOf(id, sales),
      seckillPrice: tags?.seckillPrice,
      newbiePrice: tags?.newbiePrice,
      socialProofKind: tags?.socialProofKind,
      socialProofText: tags?.socialProofText,
      priceTagText: tags?.priceTagText,
    })
  }

  function skuZonePriceOf(spuId: string, skuId: string): number | null {
    void revision.value
    const map = skuZonePriceMapOf(spuId)
    return map[String(skuId || '').trim()] ?? null
  }

  function skuZonePricesOf(spuId: string): Record<string, number> {
    void revision.value
    return { ...skuZonePriceMapOf(spuId) }
  }

  function skuZonePriceMapOf(spuId: string): Record<string, number> {
    const id = String(spuId || '').trim()
    const result: Record<string, number> = {}
    if (!id)
      return result
    // 协议价（合同价）锁定成交基价：按 SKU 覆盖，特供/会员/出清一律让位（与引擎锁定语义一致）。
    // 本 map 与数量无关（快捷加购确认弹层按单件口径取价），故起订量 > 1 的政策不锁定。
    const policyLockedSkus = new Set<string>()
    const policy = policyCache.get(id)
    if (policy) {
      for (const item of policy.items) {
        const skuId = String(item?.skuId || '').trim()
        const price = Number(item?.policyPrice)
        if (!skuId || !Number.isFinite(price) || price <= 0)
          continue
        if (policyMinQtyOf(item) > 1)
          continue
        result[skuId] = Math.round(price * 100) / 100
        policyLockedSkus.add(skuId)
      }
    }
    // 档期特供：身份价家族里归属最高（同价压过会员/出清），先写入再由后两者「严格更低才覆盖」
    const special = specialIndex?.bySpu.get(id)
    if (special) {
      for (const item of special) {
        const skuId = String(item?.skuId || '').trim()
        const price = Number(item?.price)
        const original = Number(item?.originalPrice)
        if (!skuId || !Number.isFinite(price) || price <= 0)
          continue
        if (policyLockedSkus.has(skuId))
          continue
        // 倒挂防御：服务端回填的当前售价存在时，特供价必须严格更低
        if (Number.isFinite(original) && original > 0 && price >= original)
          continue
        const current = result[skuId]
        const rounded = Math.round(price * 100) / 100
        if (current === undefined || rounded < current)
          result[skuId] = rounded
      }
    }
    const member = memberCache.get(id)
    const eligible = memberEligible === true
    if (member && eligible) {
      const rate = Number(memberDiscountRate)
      const hasRate = Number.isFinite(rate) && rate > 0 && rate < 1
      for (const item of member.items) {
        const skuId = String(item?.skuId || '').trim()
        const memberPrice = Number(item?.memberPrice)
        const original = Number(item?.originalPrice)
        if (!skuId || !Number.isFinite(memberPrice) || memberPrice <= 0)
          continue
        if (policyLockedSkus.has(skuId))
          continue
        let price = memberPrice
        if (hasRate && Number.isFinite(original) && original > 0)
          price = Math.min(memberPrice, Math.round(original * rate * 100) / 100)
        if (Number.isFinite(original) && original > 0 && price >= original)
          continue
        const rounded = Math.round(price * 100) / 100
        const current = result[skuId]
        // 同价归属特供：会员价只有严格更低才覆盖（家族取低口径见 utils/zone-price）
        if (current === undefined || rounded < current)
          result[skuId] = rounded
      }
    }
    const clearance = clearanceCache.get(id)
    if (clearance) {
      const now = Date.now()
      const skuMap = new Map<string, number>()
      for (const item of clearance.items) {
        const skuId = String(item?.skuId || '').trim()
        const price = Number(item?.clearancePrice)
        const sales = Number(item?.salesPrice)
        if (!skuId || !Number.isFinite(price) || price <= 0)
          continue
        if (!Number.isFinite(sales) || price >= sales)
          continue
        if (policyLockedSkus.has(skuId))
          continue
        const expire = String(item?.clearanceExpireTime || '').trim()
        if (expire) {
          const time = new Date(expire.replace(' ', 'T')).getTime()
          if (Number.isFinite(time) && time <= now)
            continue
        }
        const current = skuMap.get(skuId)
        if (current === undefined || price < current)
          skuMap.set(skuId, Math.round(price * 100) / 100)
      }
      // 同价归属会员价：只有严格更低才覆盖
      for (const [skuId, price] of skuMap) {
        const member = result[skuId]
        if (member === undefined || price < member)
          result[skuId] = price
      }
    }
    return result
  }

  async function loadForSpuIds(
    spuIds: Array<string | number | null | undefined>,
    options: ZonePriceLoadOptions = {},
  ): Promise<void> {
    const ids = uniqueSpuIds(spuIds)
    if (!ids.length)
      return
    // 登录/退出/切号/切船/切采购场景后先整体失效，再按新上下文补价（失败静默由各请求分支兜住）
    const context = policyContextResolver() || {}
    syncCacheScope(context)
    const wantMember = options.member !== false
    const wantClearance = options.clearance !== false
    const wantCardTag = options.cardTag !== false
    const wantPolicy = options.policy !== false
    const wantSpecial = options.special !== false
    const tasks: Promise<void>[] = []

    if (wantMember) {
      const missing = pickMissing(ids, memberCache, pendingMember)
      if (missing.length) {
        missing.forEach(id => pendingMember.add(id))
        // 有并发请求在飞时排队串行，避免同一批 SPU 多次打接口
        const run = async () => {
          if (memberInflight)
            await memberInflight.catch(() => {})
          memberInflight = fetchMemberPrices(missing)
          await memberInflight
          memberInflight = null
        }
        tasks.push(run())
      }
    }
    if (wantClearance) {
      const missing = pickMissing(ids, clearanceCache, pendingClearance)
      if (missing.length) {
        missing.forEach(id => pendingClearance.add(id))
        const run = async () => {
          if (clearanceInflight)
            await clearanceInflight.catch(() => {})
          clearanceInflight = fetchClearancePrices(missing)
          await clearanceInflight
          clearanceInflight = null
        }
        tasks.push(run())
      }
    }
    if (wantCardTag) {
      const missing = pickMissing(ids, cardTagCache, pendingCardTag)
      if (missing.length) {
        missing.forEach(id => pendingCardTag.add(id))
        const run = async () => {
          if (cardTagInflight)
            await cardTagInflight.catch(() => {})
          cardTagInflight = fetchCardTags(missing)
          await cardTagInflight
          cardTagInflight = null
        }
        tasks.push(run())
      }
    }
    if (wantPolicy) {
      // 协议价免登可访问：游客/无上下文也请求（服务端自动清空主体，不会下发合同价），
      // 有船/企业上下文就带上，与结算计价上下文同源
      const missing = pickMissing(ids, policyCache, pendingPolicy)
      if (missing.length) {
        missing.forEach(id => pendingPolicy.add(id))
        const run = async () => {
          if (policyInflight)
            await policyInflight.catch(() => {})
          policyInflight = fetchPolicyPrices(missing, context)
          await policyInflight
          policyInflight = null
        }
        tasks.push(run())
      }
    }
    if (wantSpecial) {
      // 特供接口不分 SPU：整量按上下文拉一次，60s 内复用（切船/切场景由指纹失效兜住）
      if (!specialIndex || !isFresh(specialIndex.fetchedAt)) {
        const run = async () => {
          if (specialInflight)
            await specialInflight.catch(() => {})
          specialInflight = fetchSpecials(context)
          await specialInflight
          specialInflight = null
        }
        tasks.push(run())
      }
    }
    if (!tasks.length)
      return
    await Promise.all(tasks.map(task => task.catch(() => {})))
    revision.value++
  }

  return {
    cardPriceOf,
    cardTagOf,
    clearanceDisplayOf,
    loadForSpuIds,
    memberDisplayOf,
    revision,
    skuZonePriceOf,
    skuZonePricesOf,
  }
}
