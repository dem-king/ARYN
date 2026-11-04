import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * `useQuickCart.quickAdd` 的专区成交价（会员价/捡漏价）接线行为。
 *
 * 关键契约：
 * - 不传 zonePrices 时行为与改造前完全一致（向后兼容商详/购物车/装修楼层）；
 * - 活动价（新人价/秒杀价）适用时活动价优先，专区价不得覆盖；
 * - 活动价不适用时，专区价按 quick-cart 选中的 SKU 生效，确认弹层单价/小计
 *   与卡片一致，且不限购（会员价/捡漏价不封顶数量）；
 * - 专区价不低于普通售价 / SKU 不匹配时回落普通售价（绝不把价格抬高）。
 */

const mocks = vi.hoisted(() => ({
  getQuickCartInfo: vi.fn(),
  goLogin: vi.fn(),
  submitCartAdd: vi.fn(),
}))

const authState = { isLoggedIn: true }

vi.mock('@/api/product/spu', () => ({
  getQuickCartInfo: mocks.getQuickCartInfo,
}))
vi.mock('@/composables/useCartDestination', () => ({
  useCartDestination: () => ({ submitCartAdd: mocks.submitCartAdd }),
}))
vi.mock('@/composables/useRequireLogin', () => ({
  useRequireLogin: () => ({ goLogin: mocks.goLogin }),
}))
vi.mock('@/store/authStore', () => ({
  useAuthStore: () => authState,
}))

const { useQuickCart } = await import('./useQuickCart')

/** 单规格商品的 quick-cart 下发信息（后端按库存挑一行 SKU） */
function directInfo(overrides: Record<string, unknown> = {}) {
  return {
    mode: 'direct',
    moq: 1,
    name: '皮蛋瘦肉粥',
    salesPrice: 12.9,
    skuId: 'sku-1',
    spuId: 'spu-1',
    stepQty: 1,
    stock: 99,
    ...overrides,
  }
}

/** 最近一次确认弹层的入参（submitCartAdd 的 payload） */
function lastPayload() {
  return mocks.submitCartAdd.mock.calls.at(-1)?.[0] ?? {}
}

async function quickAddWith(options: Record<string, unknown>): Promise<Awaited<ReturnType<ReturnType<typeof useQuickCart>['quickAdd']>>> {
  const { quickAdd } = useQuickCart()
  return quickAdd('spu-1', options)
}

beforeEach(() => {
  authState.isLoggedIn = true
  mocks.getQuickCartInfo.mockReset()
  mocks.goLogin.mockReset()
  mocks.submitCartAdd.mockReset()
  mocks.getQuickCartInfo.mockResolvedValue(directInfo())
  mocks.submitCartAdd.mockResolvedValue('personal')
})

describe('useQuickCart 专区成交价', () => {
  it('不传 zonePrices：确认弹层沿用普通售价（向后兼容）', async () => {
    const result = await quickAddWith({})
    expect(result.added).toBe(true)
    expect(lastPayload().unitPrice).toBe(12.9)
    expect(lastPayload().maxQuantity).toBeNull()
    expect(lastPayload().quantityHint).toBe('')
  })

  it('活动价不适用时：确认弹层单价用专区价（卡片价 = 弹层小计）', async () => {
    const result = await quickAddWith({ zonePrices: { 'sku-1': 5.9 } })
    expect(result.added).toBe(true)
    expect(lastPayload().unitPrice).toBe(5.9)
    // 会员价/捡漏价不限购：不带 maxQuantity 封顶
    expect(lastPayload().maxQuantity).toBeNull()
  })

  it('专区价可携带角标说明：确认弹层回显「会员价」', async () => {
    await quickAddWith({ zonePrices: { 'sku-1': { hint: '会员价', price: 5.9 } } })
    expect(lastPayload().unitPrice).toBe(5.9)
    expect(lastPayload().quantityHint).toBe('会员价')
  })

  it('活动价适用时活动价优先，专区价不覆盖（新人价/秒杀价优先逻辑不变）', async () => {
    await quickAddWith({
      promotion: { hint: '新人专享', maxQuantity: 2, skuId: 'sku-1', unitPrice: 8.8 },
      zonePrices: { 'sku-1': 5.9 },
    })
    expect(lastPayload().unitPrice).toBe(8.8)
    expect(lastPayload().maxQuantity).toBe(2)
    expect(lastPayload().quantityHint).toBe('新人专享')
  })

  it('专区价 SKU 与 quick-cart 选中的 SKU 不一致：回落普通售价', async () => {
    await quickAddWith({ zonePrices: { 'sku-2': 5.9 } })
    expect(lastPayload().unitPrice).toBe(12.9)
  })

  it('专区价不低于普通售价（脏数据/倒挂）：回落普通售价，绝不抬高价格', async () => {
    await quickAddWith({ zonePrices: { 'sku-1': 19.9 } })
    expect(lastPayload().unitPrice).toBe(12.9)
  })

  it('多规格商品：专区价不参与直加，交给规格弹层（needChoose 原样返回）', async () => {
    mocks.getQuickCartInfo.mockResolvedValue({
      goodsSkus: [{ salesPrice: 12.9, skuId: 'sku-1' }],
      mode: 'choose',
      spuId: 'spu-1',
    })
    const result = await quickAddWith({ zonePrices: { 'sku-1': 5.9 } })
    expect(result.needChoose).toBe(true)
    expect(mocks.submitCartAdd).not.toHaveBeenCalled()
  })
})
