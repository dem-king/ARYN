import { describe, expect, it } from 'vitest'
import { applyDeliveryContext, resolveDeliveryContext } from './checkout-delivery'

describe('resolveDeliveryContext', () => {
  it('导入船供必须锁定 way4/scene2，并使用交接里保存的船舶与靠港', () => {
    const resolved = resolveDeliveryContext({
      deliveryWay: '3',
      importScene: '2',
      importVesselId: 'vessel-1',
      importVesselCallId: 'call-1',
      globalContext: { purchaseScene: '1', vesselId: 'vessel-other', vesselCallId: 'call-other' },
    })

    expect(resolved).toEqual({
      deliveryWay: '4',
      purchaseScene: '2',
      vesselId: 'vessel-1',
      vesselCallId: 'call-1',
    })
  })

  it('导入岸上必须锁定 way3/scene3，不能带任何船舶字段', () => {
    const resolved = resolveDeliveryContext({
      deliveryWay: '4',
      importScene: '3',
      importVesselId: 'vessel-1',
      importVesselCallId: 'call-1',
      globalContext: { purchaseScene: '1', vesselId: 'vessel-1', vesselCallId: 'call-1' },
    })

    expect(resolved).toEqual({ deliveryWay: '3', purchaseScene: '3' })
  })

  it('普通到船沿用全局上下文（含场景），普通岸上不传场景', () => {
    expect(resolveDeliveryContext({
      deliveryWay: '4',
      globalContext: { purchaseScene: '1', vesselId: 'vessel-1', vesselCallId: 'call-1' },
    })).toEqual({
      deliveryWay: '4',
      purchaseScene: '1',
      vesselId: 'vessel-1',
      vesselCallId: 'call-1',
    })

    expect(resolveDeliveryContext({ deliveryWay: '3' })).toEqual({ deliveryWay: '3' })
  })
})

describe('applyDeliveryContext', () => {
  it('未解析出的字段必须删除，避免上一单场景/船舶残留', () => {
    const params: Record<string, any> = {
      deliveryWay: '4',
      purchaseScene: '2',
      vesselId: 'vessel-1',
      vesselCallId: 'call-1',
    }

    applyDeliveryContext(params, resolveDeliveryContext({ deliveryWay: '3' }))

    expect(params.deliveryWay).toBe('3')
    expect(params).not.toHaveProperty('purchaseScene')
    expect(params).not.toHaveProperty('vesselId')
    expect(params).not.toHaveProperty('vesselCallId')
  })

  it('导入岸上覆盖预览阶段可能残留的船供场景', () => {
    const params: Record<string, any> = { deliveryWay: '4', purchaseScene: '1', vesselId: 'v', vesselCallId: 'c' }

    applyDeliveryContext(params, resolveDeliveryContext({
      deliveryWay: '4',
      importScene: '3',
      importVesselId: 'v',
      importVesselCallId: 'c',
    }))

    expect(params).toEqual({ deliveryWay: '3', purchaseScene: '3' })
  })
})
