/**
 * C 端阶梯价批量查询（免登可访问，游客请求安全）
 * 对应后端 AppLadderPriceController：GET /promotion/app/ladder/prices
 *
 * 阶梯价与数量相关：档位按 minQty 升序，购买件数达到某档才享该档单价。
 * 展示口径（与后端引擎一致）：卡片不算阶梯价（数量未定）；商详按当前数量取档
 * 并与身份价家族取低；协议价锁定成交基价后阶梯价一律不覆盖。
 * 服务端按 purchaseScene/vesselId 圈定活动，游客/非成员不会拿到受限活动价。
 */
import { alovaInstance } from '@/api/core/instance'

/** 阶梯档位（服务端按 minQty 升序下发） */
export interface LadderTier {
  /** 起订件数：购买件数 ≥ minQty 才享本档单价 */
  minQty: number
  /** 本档单价（元） */
  unitPrice: number
}

/** 单 SKU 命中的阶梯活动 */
export interface LadderPriceItem {
  /** 命中的阶梯活动 ID */
  activityId: string
  /** 活动名（展示「阶梯价」来源） */
  activityName: string
  /** 档位列表（minQty 升序） */
  ladders: LadderTier[]
  /** SKU ID */
  skuId: string
}

/** 阶梯价批量查询入参 */
export interface LadderPriceQuery {
  /** SKU ID 集合（必填；空集合不发请求） */
  skuIds: string[]
  /** 采购场景：1个人 2船供 3企业（服务端按场景圈定活动） */
  purchaseScene?: string
  /** 船舶 ID（船供上下文） */
  vesselId?: string
}

/** 批量查询阶梯档位（未命中返回空数组） */
export function getLadderPrices(query: LadderPriceQuery) {
  const ids = (query.skuIds || []).map(id => String(id ?? '').trim()).filter(Boolean)
  if (!ids.length)
    return Promise.resolve<LadderPriceItem[]>([])
  return alovaInstance.Get<LadderPriceItem[]>('/promotion/app/ladder/prices', {
    params: {
      skuIds: ids.join(','),
      purchaseScene: query.purchaseScene || undefined,
      vesselId: query.vesselId || undefined,
    },
  })
}
