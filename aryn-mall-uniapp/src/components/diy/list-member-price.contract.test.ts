import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 会员专区 Tab「列表卡片会员价」接线契约守门（F3 §8.4 列表侧）。
 *
 * 漏网方式：API 加了 spuIds 参数、纯函数单测也绿，但数据层没接（列表永远原价）、
 * 门控写错（全站 Tab 都发会员价请求）、或卡片仍渲染 `item.price`（切了主价没渲染）。
 * 这些跨文件断线类型检查发现不了，按源码结构钉住：
 * API 只传 spuIds、数据层仅 memberBanner 开启才发且失败静默、卡片消费聚合结果。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const PROMOTION_API = 'src/api/promotion.ts'
const RETAIL_DATA = 'src/components/diy/retail-data.ts'
const RETAIL_TYPES = 'src/components/diy/retail-types.ts'
const TABS_COMPONENT = 'src/components/diy/diy-goods-tabs/index.vue'

/** 截取一个顶层 `export async function name(` 的实现（到下一个顶层导出函数为止） */
function loaderSource(data: string, name: string) {
  const start = data.indexOf(`export async function ${name}(`)
  if (start < 0)
    return ''
  const ends = [
    data.indexOf('\nexport async function ', start + 1),
    data.indexOf('\nasync function ', start + 1),
    data.indexOf('\nexport function ', start + 1),
    data.indexOf('\nfunction ', start + 1),
  ].filter(index => index > start)
  return data.slice(start, ends.length ? Math.min(...ends) : data.length)
}

describe('member spu prices API contract', () => {
  it('adds a spuIds-mode batch endpoint next to the skuIds one and never hardcodes /boot', () => {
    const api = source(PROMOTION_API)
    const fn = api.slice(api.indexOf('export function getMemberSpuPrices'), api.length)

    expect(fn).toContain('/promotion/app/member/prices')
    // 列表只有 SPU 维度：必须传 spuIds（不能偷偷按 skuIds 传）
    expect(fn).toContain("params: { spuIds: ids.join(',') }")
    expect(fn).not.toContain('skuIds')
    // 可选登录：带登录态后端才能判等级资格
    expect(fn).not.toContain('skipToken')
    expect(fn).not.toContain('/boot')
    // 空入参不发请求
    expect(fn).toContain('Promise.resolve<AppMemberPriceVO | null>(null)')

    // 既有 skuIds 模式（商详/购物车）保持原样，不改它的行为
    const skuFn = api.slice(api.indexOf('export function getMemberSkuPrices'), api.indexOf('export function getMemberSpuPrices'))
    expect(skuFn).toContain("params: { skuIds: ids.join(',') }")
  })

  it('declares the list aggregation result type alongside the tab types', () => {
    const types = source(RETAIL_TYPES)
    const typeBlock = types.slice(
      types.indexOf('export interface ListMemberPriceDisplay'),
      types.indexOf('export interface MemberBenefitEntry'),
    )
    for (const field of ['matched', 'showMemberPrice', 'memberPrice', 'strikePrice', 'badgeText', 'guideText'])
      expect(typeBlock).toContain(field)
  })
})

describe('retail-data list aggregation contract', () => {
  it('only aggregates for tabs with memberBanner.enabled === true', () => {
    const data = source(RETAIL_DATA)
    const helper = data.slice(
      data.indexOf('async function withListMemberPrices('),
      data.indexOf('/**\n * 多 Tab 商品流取数'),
    )

    // 产品耦合门控：未开启的 Tab 必须直接返回原列表（不发请求、不加字段）
    expect(helper).toContain('tab.memberBanner?.enabled !== true')
    expect(helper).toContain('getMemberSpuPrices(spuIds)')
    // 本页去重 + 防御性上限
    expect(helper).toContain('new Set(list.map(item => item.id)')
    expect(helper).toContain('.slice(0, MEMBER_LIST_PRICE_SPU_LIMIT)')
    expect(data).toContain('export const MEMBER_LIST_PRICE_SPU_LIMIT = 50')
    // 失败静默：接口异常不得抛错阻塞列表
    expect(helper).toContain('catch')
    expect(helper).toContain('vo = null')
    // 只用 matched 的 SPU 写字段，未命中/倒挂的卡片保持原样
    expect(helper).toContain('display.matched ? { ...item, memberPriceDisplay: display } : item')
  })

  it('wires the aggregation into both manual and automatic tab branches only', () => {
    const data = source(RETAIL_DATA)
    const tabPage = data.slice(
      data.indexOf('export async function loadGoodsTabPage('),
      data.indexOf('export async function loadGoodsRanking('),
    )
    // manual / activity / automatic 三条分支都要补价（会员专区 Tab 三种数据源，
    // activity 为本轮新增的「按会员价活动自动」货盘）
    expect(tabPage.match(/withListMemberPrices\(tab, /g) || []).toHaveLength(3)

    // 其它楼层不参与（会员价只属于 Tab 级会员权益条的场景）
    for (const loader of ['loadGoodsGroup', 'loadGoodsGroupPage', 'loadGoodsRanking', 'loadGoodsWaterfall']) {
      expect(loaderSource(data, loader)).not.toContain('withListMemberPrices')
    }
  })
})

describe('goods tabs card rendering contract', () => {
  it('renders member price as the main price with badge and card-price strike-through', () => {
    const component = source(TABS_COMPONENT)
    // 主价/划线/引导都从聚合结果派生，禁止卡片继续直读 item.price
    expect(component).toContain('function cardMainPrice(')
    expect(component).toContain('function cardStrikePrice(')
    expect(component).toContain('function cardMemberGuide(')
    expect(component).toContain('cardMainPrice(item)')
    expect(component).not.toContain('item.price.toFixed(2)')

    // 达标：会员价 + 角标；划线取聚合结果里的卡片价（不再叠加 SPU 原价）
    expect(component).toContain('item.memberPriceDisplay?.showMemberPrice')
    expect(component).toContain('item.memberPriceDisplay.badgeText')
    expect(component).toContain('cardStrikePrice(item)')
    expect(component).toContain('goods-tabs__member-badge')

    // 未达标/未登录：小字引导「会员专享 ¥X」
    expect(component).toContain('cardMemberGuide(item)')
    expect(component).toContain('goods-tabs__member-guide')
  })

  it('presents member-zone prices in the gold member band for members with a real discount', () => {
    const component = source(TABS_COMPONENT)
    // 会员专区内会员本人即使没单独配会员价活动，也要用会员价视觉呈现专区成交价
    // （无优惠商品不贴假会员价，故必须要求有划线基准）
    expect(component).toContain('memberZonePricing')
    expect(component).toContain('showCardMemberPrice(item)')
    expect(component).toContain('goods-tabs__member-price')
    expect(component).toContain('日常价')
  })

  it('keeps the request in the data layer (single gating point)', () => {
    const component = source(TABS_COMPONENT)
    // 组件只消费 loadGoodsTabPage 合并好的 item，不自己发会员价请求——
    // 否则「其它 Tab 不发请求」的门控会出现第二份实现
    expect(component).not.toContain('getMemberSpuPrices')
    // 横幅与卡片同开同关：横幅判定仍是 Tab 级 memberBanner.enabled
    expect(component).toContain('memberBanner?.enabled')
  })
})

/**
 * 会员折扣率「展示取优」契约（本轮新增）。
 *
 * 结算已改为「会员价与会员折扣率取优、不叠加」，展示必须同口径，否则页面标会员价、
 * 结算按折扣价，价差观感重现。这里钉死：VO 带 userDiscountRate → 数据层透传 →
 * 共享纯函数取优 → 商详/购物车同源消费同一份 VO。
 */
describe('member discount rate best-price contract', () => {
  it('declares userDiscountRate on the batch price VO', () => {
    const api = source(PROMOTION_API)
    const voBlock = api.slice(
      api.indexOf('export interface AppMemberPriceVO'),
      api.indexOf('export function getMemberSkuPrices'),
    )
    expect(voBlock).toContain('userDiscountRate: number | null')
    // 只用于展示取优，成交以结算为准
    expect(voBlock).toContain('仅供展示取优')
  })

  it('takes the lower of member price and discounted price (best-price, not stacking)', () => {
    const util = source('src/utils/member-price.ts')
    // 取优工具：min(会员价, round2(售价 × 折扣率))
    expect(util).toContain('export function resolveBestMemberPrice')
    expect(util).toContain('Math.min(member, roundMemberPriceToCent(original * rate))')
    // 明确「取优不是叠加」，避免后人写成折上折
    expect(util).toContain('取优不是叠加')
    expect(util).not.toContain('memberPrice * rate')

    // 两套展示入口都要取优
    const skuFn = util.slice(
      util.indexOf('export function resolveMemberPriceDisplay'),
      util.indexOf('export function memberPriceUnitPrice'),
    )
    expect(skuFn).toContain('resolveBestMemberPrice(')
    expect(skuFn).toContain('vo.userDiscountRate')

    const listFn = util.slice(util.indexOf('export function resolveListMemberPrice('))
    expect(listFn).toContain('resolveBestMemberPrice(hit.memberPrice, card, userDiscountRate)')
  })

  it('passes the rate through the list aggregation and consumes it via the shared VO', () => {
    const data = source(RETAIL_DATA)
    const helper = data.slice(
      data.indexOf('async function withListMemberPrices('),
      data.indexOf('/**\n * 多 Tab 商品流取数'),
    )
    // 列表补价把后端下发的会员折扣率透传给取优逻辑
    expect(helper).toContain('vo.userDiscountRate')

    // 商详/购物车：会员折扣率随 AppMemberPriceVO 透传，页面统一走共享判定（不各算一遍）
    const detail = source('src/sub-pages/product/goods-detail/index.vue')
    expect(detail).toContain('resolveMemberPriceDisplay(state.memberPriceVO, currentSkuId.value)')
    expect(detail).not.toContain('userDiscountRate *')
    const cart = source('src/pages/user/shopping-cart/index.vue')
    expect(cart).toContain('resolveMemberPriceDisplay(vo, skuId)')
    expect(cart).not.toContain('userDiscountRate *')
  })

  it('keeps the deterministic member banner copy free of discount numbers', () => {
    // 文案落在 zh 词条、渲染侧 translate 调用时求值（切语言实时刷新）
    const banner = source('src/components/diy/member-banner.ts')
    expect(banner).toContain("translate('diy.memberBanner.memberCopy'")
    // 占位符仍保留给运营自定义文案（会员折扣率权益）
    expect(banner).toContain('{discount}')
    expect(banner).toContain('{折扣}')

    const copyLine = source('src/locales/modules/diy.zh-CN.ts')
      .split('\n')
      .find(line => line.includes('memberCopy:'))
    expect(copyLine).toBeDefined()
    // 文案是确定性标签：不含「折」，也不引用 {discount}
    expect(copyLine).toContain('本区享会员专享价')
    expect(copyLine).not.toContain('折')
    expect(copyLine).not.toContain('{discount}')
  })
})
