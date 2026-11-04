import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 捡漏价卡片价格契约（本轮扩围：出清价不再只在低价捡漏 Tab 生效）。
 *
 * 核心口径：
 * 1. 价格主导：小号「捡漏价」标签 + 放大价格 + 原价划线，不能退回普通价格样式；
 * 2. 会员价优先级更高：同一卡片只解释一个成交价，会员价命中时不得再出捡漏价样式；
 * 3. 出清价对「所有 Tab」生效：同一商品出现在首页其它 Tab 时价格必须与低价捡漏一致，
 *    判定与取低统一收敛到 `utils/zone-price#resolveListZonePrice`，组件不再自己按 Tab 排序判定。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))
const COMPONENT = 'src/components/diy/diy-goods-tabs/index.vue'

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

describe('goods tabs bargain card contract', () => {
  it('derives the bargain style from the shared zone-price result instead of a tab sort', () => {
    const component = source(COMPONENT)
    // 捡漏价样式由共享口径的角标类型决定，不再按 Tab 的 sort 单独判定
    expect(component).toContain('function showCardBargainPrice(')
    expect(component).toContain(".badgeKind === 'clearance'")
    expect(component).toContain('resolveListZonePrice(')
    // 不能再退回「只有低价捡漏 Tab 才展示出清价」的旧门控
    expect(component).not.toContain('bargainTab')
    expect(component).not.toContain('clearanceTab')
  })

  it('renders a price-led bargain block with a struck original price', () => {
    const component = source(COMPONENT)
    expect(component).toContain('捡漏价')
    expect(component).toContain('goods-tabs__bargain-label')
    expect(component).toContain('goods-tabs__bargain-amount')
    expect(component).toContain('bargainPriceParts(item).integer')
    expect(component).toContain('goods-tabs__bargain-original-price')
    expect(component).toContain('cardStrikePrice(item)')
    // 会员价分支在前，捡漏价只能走 v-else-if（同一卡片只有一个成交价）
    expect(component).toContain('v-else-if="showCardBargainPrice(item)"')
  })

  it('lets member price win over clearance and keeps the style mutually exclusive', () => {
    const component = source(COMPONENT)
    expect(component).toContain('v-if="showCardMemberPrice(item)"')
    expect(component).toContain("cardDisplay(item).badgeKind === 'member'")
    // 角标文案统一走共享映射（会员价/捡漏价），不各自写死
    expect(component).toContain('zonePriceBadgeText(')
  })

  it('fetches member and clearance prices for every tab (one price across tabs)', () => {
    const component = source(COMPONENT)
    // 所有 Tab 统一补会员价 + 出清价：同一商品在其它 Tab 也要展示会员价/捡漏价
    expect(component).toContain('zone.loadForSpuIds(ids)')
    // 会员专区 Tab 卡片优先用数据层（retail-data）按活动货盘补好的会员价，避免两套口径
    expect(component).toContain('item?.memberPriceDisplay')
    expect(component).toContain('zone.memberDisplayOf(')
    expect(component).toContain('zone.clearanceDisplayOf(')
  })
})
