import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 低价捡漏 Tab 卡片价格契约。
 *
 * 低价捡漏的核心是「价格主导」：小号「捡漏价」标签 + 放大价格 + 原价划线，
 * 不能和猜你喜欢共用普通价格样式；同时会员价优先级更高，两个样式不能同时出。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))
const COMPONENT = 'src/components/diy/diy-goods-tabs/index.vue'

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

describe('goods tabs bargain card contract', () => {
  it('only marks the bargain tab and lets member price win', () => {
    const component = source(COMPONENT)
    expect(component).toContain('BARGAIN_SORT')
    expect(component).toContain('activeTab.value?.dataSource.sort === BARGAIN_SORT')
    expect(component).toContain('function showCardBargainPrice(')
    expect(component).toContain('if (!bargainTab.value || showCardMemberPrice(item))')
  })

  it('renders a price-led bargain block with a struck original price', () => {
    const component = source(COMPONENT)
    expect(component).toContain('捡漏价')
    expect(component).toContain('goods-tabs__bargain-label')
    expect(component).toContain('goods-tabs__bargain-amount')
    expect(component).toContain('bargainPriceParts(item).integer')
    expect(component).toContain('goods-tabs__bargain-original-price')
    expect(component).toContain('cardStrikePrice(item)')
    // 会员价分支在前，捡漏价只能走 v-else-if
    expect(component).toContain('v-else-if="showCardBargainPrice(item)"')
  })
})
