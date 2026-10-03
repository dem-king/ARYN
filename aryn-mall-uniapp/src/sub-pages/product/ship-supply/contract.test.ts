import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 船供选货页（含共享购物车选货模式）的 UI/UX 契约守门。
 *
 * 这条链路的回归不会报错，只会「变回不好用」：
 * - 上下文/搜索退回滚动区内 → 选货滚到中部就不知道在给哪条船下单；
 * - 底部清单条被删 → 加完没有任何总量反馈，也没有回清单的入口；
 * - 数量回到裸输入框 → 采购员要自己算 MOQ 的倍数，算错等结算才报错；
 * - 卡片图字段前端接上了、后端没下发 → 商品图静默变成灰块（跨仓，类型检查看不见）。
 * 因此这些结构性决定必须用源码契约锁住。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))
const repoRoot = resolve(projectRoot, '..')

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

function repoSource(relativePath: string) {
  return readFileSync(resolve(repoRoot, relativePath), 'utf8')
}

const PAGE = 'src/sub-pages/product/ship-supply/index.vue'
const UTIL = 'src/sub-pages/utils/ship-supply.ts'
const VO = 'aryn-mall-java/aryn-product/aryn-product-api/src/main/java/com/aryn/cloud/product/api/vo/GoodsCatalogSummaryVO.java'
const MAPPER = 'aryn-mall-java/aryn-product/aryn-product-biz/src/main/resources/mapper/GoodsSpuMapper.xml'

/**
 * 取 z-paging 某个命名插槽的内容。
 *
 * 按标签深度配对，不能「切到下一个插槽标记为止」：滚动区就排在各插槽之间，
 * 那样切出来的片段会把默认插槽（商品流）也算进 #top 里 ——
 * 实测把搜索块从 #top 搬到滚动区后断言仍然通过，正是这个原因。
 */
function slotOf(page: string, name: 'bottom' | 'top') {
  const start = page.indexOf(`<template #${name}>`)
  if (start < 0)
    return ''
  // 插槽内部还有 `<template v-if>` 等嵌套标签，必须按深度找到配对的结束标签
  const tagPattern = /<template\b[^>]*>|<\/template>/g
  tagPattern.lastIndex = start
  let depth = 1
  let match = tagPattern.exec(page)
  // 首个匹配就是插槽自身的开标签，从它之后开始计数
  match = tagPattern.exec(page)
  while (match) {
    depth += match[0].startsWith('</') ? -1 : 1
    if (depth === 0)
      return page.slice(start, match.index)
    match = tagPattern.exec(page)
  }
  return page.slice(start)
}

describe('ship supply page structure contract', () => {
  it('keeps context and search in the fixed top slot', () => {
    const page = source(PAGE)
    // 常驻区必须在 z-paging 的 #top 里；放回默认插槽就会随列表滚走
    const topSlot = slotOf(page, 'top')
    expect(topSlot).toContain('class="context')
    expect(topSlot).toContain('class="search"')
    expect(topSlot).toContain('搜索商品名称')
    expect(topSlot).toContain('hr-navbar')
  })

  it('keeps a persistent bottom bar with a way back to the list', () => {
    const page = source(PAGE)
    const bottomSlot = slotOf(page, 'bottom')
    expect(bottomSlot).toContain('checkout-bar')
    // 加购后必须能回清单，否则用户只能靠返回键猜
    expect(bottomSlot).toContain('goSharedCartDetail')
  })

  it('does not hand-roll fixed positioning on top of z-paging own layout', () => {
    const page = source(PAGE)
    const style = page.slice(page.indexOf('<style'))
    // z-paging 的 fixed 容器已经负责贴边与安全区，再写一份会重叠
    expect(style).not.toMatch(/\.checkout-bar\s*\{[^}]*position:\s*fixed/)
    expect(style).not.toContain('checkout-bar__spacer')
  })

  it('uses a stepper bound to the row quantity rules instead of a bare input', () => {
    const page = source(PAGE)
    expect(page).toContain('wd-input-number')
    expect(page).toContain('step-strictly')
    expect(page).toContain('quantityRuleOf(row).moq')
    expect(page).toContain('quantityRuleOf(row).stepQty')
    // 裸 number 输入框会把 MOQ/步长校验推迟到结算
    expect(page).not.toContain('type="number"')
  })

  it('keeps per-row quantity state out of the API row objects', () => {
    const page = source(PAGE)
    // 曾经把 _qty 挂在行对象上：z-paging 整体替换行数据后输入值会莫名丢失
    expect(page).not.toMatch(/item\._qty|row\._qty/)
    expect(page).toContain('quantities')
  })

  it('keeps the shared-cart picking mode dual-path', () => {
    const page = source(PAGE)
    expect(page).toContain('sharedCartId')
    expect(page).toContain('addSharedCartItem')
    expect(page).toContain('isSharedMode')
    // 个人模式下仍写个人购物车
    expect(page).toContain('addShoppingCart')
  })

  it('distinguishes load failure from an empty result', () => {
    const page = source(PAGE)
    // 请求失败被渲染成「暂无商品」会让用户以为目录里真没有
    expect(page).toContain('loadFailed')
    expect(page).toContain('加载失败')
    expect(page).toContain('complete(false)')
  })

  it('keeps the actionable missing-context entry', () => {
    const page = source(PAGE)
    expect(page).toContain('openShipPicker')
    expect(page).toContain('选择船舶和靠港计划')
    expect(page).toContain('ShipContextPicker')
  })
})

describe('ship supply row view contract', () => {
  it('keeps stock 0 distinct from an absent stock field', () => {
    // 与 utils/quick-cart 的 resolveQuantityRule 刻意不同：那边把 stock<=0 归一为 null，
    // 这里 0 必须表示缺货，否则缺货商品会被当成"不限量"而放行加购。
    // 断言的是「没有真的去用它」（注释里提到它是允许的）。
    const util = source(UTIL)
    expect(util).toContain('function toStock')
    expect(util).toMatch(/stock <= 0/)
    expect(util).not.toMatch(/from '\.\/quick-cart'|from '@\/utils\/quick-cart'/)
  })

  it('keeps the row key SKU-scoped', () => {
    // 多规格 SPU 会展开成多行，只用 spuId 会让两行共享一个数量
    expect(source(UTIL)).toMatch(/shipSupplyRowKey[\s\S]{0,120}skuId/)
  })
})

describe('ship supply list image contract', () => {
  it('serves a list thumbnail from the summary query', () => {
    // 前端卡片读 picUrl，后端不下发就是一片灰块——跨仓，类型检查发现不了
    expect(source(PAGE)).toContain('row.picUrl')
    expect(repoSource(VO)).toContain('private String picUrl;')
    const mapper = repoSource(MAPPER)
    expect(mapper).toContain('AS pic_url')
    // 取货顺序与 QuickCartServiceImpl.resolvePicUrl 一致：SKU 图优先，回退 SPU 首图
    expect(mapper).toContain('NULLIF(sku.pic_url')
    expect(mapper).toContain('SUBSTRING_INDEX(spu.spu_urls')
  })
})

describe('ship supply list sku contract', () => {
  it('derives the row skuId from goods_sku with no ship profile tables', () => {
    // 回归背景：行主键曾取自 LEFT JOIN 的 ship_sku_profile.sku_id，JOIN 落空即
    // 下发 skuId=null → 选货页加购一律「暂无可售规格」。船供资料三表已删
    // （2026-09-29），目录 SQL 收敛到 goods_spu + goods_sku，并防旧表回流。
    const mapper = repoSource(MAPPER)
    expect(mapper).toContain('sku.id AS sku_id')
    expect(mapper).not.toContain('ship_goods_profile')
    expect(mapper).not.toContain('ship_sku_profile')
  })
})
