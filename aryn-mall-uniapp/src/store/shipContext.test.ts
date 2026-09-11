import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import {
  PURCHASE_SCENE_PERSONAL,
  PURCHASE_SCENE_SHIP_SUPPLY,
  useShipContextStore,
} from '../store/shipContextStore'

describe('shipContextStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('初始状态没有船舶上下文，默认个人购买场景', () => {
    const store = useShipContextStore()
    expect(store.hasVesselContext).toBe(false)
    expect(store.purchaseScene).toBe(PURCHASE_SCENE_PERSONAL)
    expect(store.deliveryContextParams).toBeNull()
  })

  it('登录后加载船舶与靠港计划形成完整上下文', () => {
    const store = useShipContextStore()
    store.setVesselContext({ vesselId: 'vessel-1', vesselName: '测试轮' })
    expect(store.hasVesselContext).toBe(false)

    store.setVesselCall({
      berth: '3号泊位',
      deliveryWindowEnd: '2026-09-15 12:00:00',
      deliveryWindowStart: '2026-09-15 08:00:00',
      id: 'call-1',
      portCode: 'CNSHA',
      portName: '上海港',
    })
    expect(store.hasVesselContext).toBe(true)
    expect(store.vesselName).toBe('测试轮')
    expect(store.portName).toBe('上海港')
    expect(store.deliveryContextParams).toEqual({
      purchaseScene: PURCHASE_SCENE_PERSONAL,
      vesselCallId: 'call-1',
      vesselId: 'vessel-1',
    })
  })

  it('切换船舶清空靠港上下文，防止购物车串船', () => {
    const store = useShipContextStore()
    store.setVesselContext({ vesselId: 'vessel-1', vesselName: 'A轮' })
    store.setVesselCall({ id: 'call-1', portName: '上海港' })
    expect(store.hasVesselContext).toBe(true)

    store.switchVessel('vessel-2', 'B轮')
    expect(store.vesselId).toBe('vessel-2')
    expect(store.vesselName).toBe('B轮')
    expect(store.vesselCallId).toBe('')
    expect(store.hasVesselContext).toBe(false)
  })

  it('切换到同一船舶不重置上下文', () => {
    const store = useShipContextStore()
    store.setVesselContext({ vesselId: 'vessel-1' })
    store.setVesselCall({ id: 'call-1' })
    store.switchVessel('vessel-1', 'A轮')
    expect(store.vesselCallId).toBe('call-1')
  })

  it('购买场景切换同步结算上下文', () => {
    const store = useShipContextStore()
    store.setVesselContext({ vesselId: 'vessel-1' })
    store.setVesselCall({ id: 'call-1' })

    store.setPurchaseScene(PURCHASE_SCENE_SHIP_SUPPLY)
    expect(store.isShipSupply).toBe(true)
    expect(store.deliveryContextParams?.purchaseScene).toBe(
      PURCHASE_SCENE_SHIP_SUPPLY,
    )
  })

  it('非法购买场景被拒绝', () => {
    const store = useShipContextStore()
    expect(() => store.setPurchaseScene('9')).toThrow()
  })

  it('登出清理船舶上下文和购买场景', () => {
    const store = useShipContextStore()
    store.setVesselContext({ vesselId: 'vessel-1' })
    store.setVesselCall({ id: 'call-1' })
    store.setPurchaseScene(PURCHASE_SCENE_SHIP_SUPPLY)

    store.reset()
    expect(store.hasVesselContext).toBe(false)
    expect(store.purchaseScene).toBe(PURCHASE_SCENE_PERSONAL)
    expect(store.deliveryContextParams).toBeNull()
  })
})
