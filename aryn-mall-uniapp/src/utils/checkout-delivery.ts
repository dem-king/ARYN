/**
 * 结算页配送上下文解析（单一入口）。
 *
 * 预览（settlement）与提交（create）原来各写一套 way/scene 赋值：
 * 预览用全局 shipContextStore 的 deliveryContextParams 覆盖，提交时再补场景，
 * 船供单的 purchaseScene 会被 store 默认的「1 个人」覆盖，导致预览可抵积分、
 * 提交按船供场景拒绝，expectedPaymentPrice 不一致而拒单。
 *
 * 这里把「导入交接 / 普通到船 / 普通岸上」三种来源固化成一个纯函数，
 * 结算与提交都必须调用它，避免再次漂移。导入场景优先于全局船舶上下文：
 * 有船用户也可能在向导里选择送岸上地址。
 */
export interface DeliveryContextSource {
  /** 当前配送方式（普通流程可能被地址/船舶上下文改写） */
  deliveryWay: string
  /** 导入交接场景：'2' 船供采购；'3' 岸上企业采购；空=非导入 */
  importScene?: string
  /** 导入交接锁定的船舶与靠港（刷新后仍可恢复） */
  importVesselId?: string
  importVesselCallId?: string
  /** 全局船舶上下文（仅普通到船订单使用） */
  globalContext?: null | {
    purchaseScene?: string
    vesselId?: string
    vesselCallId?: string
  }
}

export interface ResolvedDeliveryContext {
  deliveryWay: string
  purchaseScene?: string
  vesselId?: string
  vesselCallId?: string
}

export function resolveDeliveryContext(source: DeliveryContextSource): ResolvedDeliveryContext {
  if (source.importScene === '2') {
    return {
      deliveryWay: '4',
      purchaseScene: '2',
      vesselId: source.importVesselId || '',
      vesselCallId: source.importVesselCallId || '',
    }
  }
  if (source.importScene === '3') {
    return { deliveryWay: '3', purchaseScene: '3' }
  }
  if (source.deliveryWay === '4' && source.globalContext) {
    return {
      deliveryWay: '4',
      purchaseScene: source.globalContext.purchaseScene,
      vesselId: source.globalContext.vesselId,
      vesselCallId: source.globalContext.vesselCallId,
    }
  }
  // 普通岸上/快递/自提：不传场景（服务端按个人购买归一），也不带船舶字段
  return { deliveryWay: source.deliveryWay }
}

/** 把解析结果写回结算/下单参数：未解析出的字段必须删除，避免上一单残留 */
export function applyDeliveryContext(
  params: Record<string, any>,
  resolved: ResolvedDeliveryContext,
) {
  params.deliveryWay = resolved.deliveryWay
  setOrDelete(params, 'purchaseScene', resolved.purchaseScene)
  setOrDelete(params, 'vesselId', resolved.vesselId)
  setOrDelete(params, 'vesselCallId', resolved.vesselCallId)
}

function setOrDelete(params: Record<string, any>, key: string, value?: string) {
  if (value) {
    params[key] = value
  }
  else {
    delete params[key]
  }
}
