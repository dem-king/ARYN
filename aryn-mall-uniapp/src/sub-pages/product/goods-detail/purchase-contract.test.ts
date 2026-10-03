import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 商详页「单规格免选规格」口径守门。
 *
 * 这类回归的漏网方式：详情页各处各写一份 `enableSpecs` 判断（三角链、字符串比较），
 * 或者有人把「先取原始规格值、再注入默认占位」的顺序调换——规格行就会重新显示
 * 「默认」甚至「选择规格」。类型检查发现不了这些，因此按源码结构断言。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const DETAIL_PAGE = 'src/sub-pages/product/goods-detail/index.vue'
const GOODS_INFO = 'src/sub-pages/product/goods-detail/components/GoodsInfo.vue'
const SHEET = 'src/components/goods-detail-sheet/index.vue'
const SHIP_CARD_PATH = 'src/sub-pages/product/goods-detail/components/ShipProfileCard.vue'

describe('goods detail purchase contract', () => {
  it('detail page branches on resolvePurchaseDecision instead of ad-hoc enableSpecs checks', () => {
    const page = source(DETAIL_PAGE)
    expect(page).toContain('resolvePurchaseDecision(response)')
    // 加入购物车入口先做单规格判定，命中才直加
    expect(page).toMatch(/value === 2 && !purchaseDecision\.value\.needChoose/)
    // 直加复用 useQuickCart（数量规则/登录守卫同一份口径）
    expect(page).toContain('quickAdd(')
  })

  it('detail page falls back to the SKU popup when direct add is not possible', () => {
    const page = source(DETAIL_PAGE)
    // 前后端判定不一致（需选规格）或查询异常 → 退回弹层，避免点击无响应
    expect(page).toMatch(/result\.needChoose \|\| result\.info === null/)
  })

  it('captures raw spec text BEFORE injecting the default placeholder spec', () => {
    const page = source(DETAIL_PAGE)
    const decisionIndex = page.indexOf('resolvePurchaseDecision(response)')
    const injectIndex = page.indexOf('initGoodsSpecs(state.goodsSpu)')
    expect(decisionIndex).toBeGreaterThan(-1)
    expect(injectIndex).toBeGreaterThan(decisionIndex)
  })

  it('spec row is rendered from the shared view model, not the selectArr ternary', () => {
    const info = source(GOODS_INFO)
    // 回归锚点：旧实现把「默认」当过滤词，单规格商品永远显示「选择规格」
    expect(info).not.toContain('selectArr !== \'默认\'')
    expect(info).toContain('v-if="specRow.visible"')
    expect(info).toContain('{{ specRow.text }}')
  })

  it('delivery row switches to internal delivery wording via resolveDeliveryRow', () => {
    const info = source(GOODS_INFO)
    expect(info).toContain('resolveDeliveryRow({')
    expect(info).toContain('hasVesselContext: shipContextStore.hasVesselContext')
    // 与结算页同判据，不允许回退成 freightType 三元链直连
    expect(info).not.toContain('goodsSpu.freightType === \'0\' ? \'包邮\'')
  })

  it('detail sheet shares the same spec row and direct-add rules', () => {
    const sheet = source(SHEET)
    expect(sheet).toContain('resolvePurchaseDecision(response)')
    expect(sheet).toContain('resolveSpecRow(purchaseDecision.value')
    expect(sheet).toContain('quickAdd(')
    expect(sheet).toMatch(/result\.needChoose \|\| result\.info === null/)
  })

  it('ship profile card and ship-summary api stay removed', () => {
    // 船供资料下线（2026-09-29）：商详不再有船供卡片与 ship-summary 摘要接口，
    // 防止有人把旧链路接回来却找不到数据源（接口已在后端一并删除）
    const page = source(DETAIL_PAGE)
    expect(page).not.toContain('ShipProfileCard')
    expect(source('src/api/product/spu.ts')).not.toContain('ship-summary')
    expect(() => source(SHIP_CARD_PATH)).toThrow()
  })
})
