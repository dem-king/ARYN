/**
 * C 端协议价批量查询（免登可访问，游客请求安全）
 * 对应后端 AppPolicyPriceController（POST /promotion/app/policy/prices）
 *
 * 用户身份从登录态读取，采购主体/船舶上下文由当前会话 store 传入
 * （岸上共享车 buyerOrgId / 船上下文 vesselId），与订单计价上下文同源。
 * 服务端对游客/非成员自动清空主体上下文（不会下发合同价），因此前端无需登录门槛、
 * 也不需要 skipToken 特殊处理；未命中政策返回空数组（按无协议价渲染）。
 *
 * 展示接入点：`utils/zone-price.ts#resolveListZonePrice` 的 `policyPrice` 入参与
 * `policy` 角标（最高优先级，与后端引擎锁定语义一致），统一在 `useZonePrices`
 * 批量补价后下发商品卡/商详/购物车。
 */
import { alovaInstance } from '@/api/core/instance'

/** 协议价单条展示 VO（单 SKU） */
export interface PolicyPriceItem {
  /** 协议价（固定价或按售价折算后的到手价） */
  policyPrice: number
  /** 命中的价格政策 ID */
  policyId: string
  /** 政策名称（展示「协议价」来源） */
  policyName: string
  /**
   * 协议起订量：**参与成交校验**——购买件数 < minQty 时协议价不生效。
   * 展示侧据此 gating：未达量只提示「协议价 N 件起」，不切主价。
   */
  minQty?: number
  /** 当前售价（划线基准） */
  originalPrice: number
  /** SPU ID */
  spuId: string
  /** SKU ID */
  skuId: string
}

/** 协议价批量查询入参 */
export interface PolicyPriceQuery {
  /** SKU ID 集合（优先） */
  skuIds?: string[]
  /** SPU ID 集合（列表卡片聚合价场景；与 skuIds 同时存在时 skuIds 优先） */
  spuIds?: string[]
  /** 采购主体 ID（岸上企业上下文） */
  buyerOrgId?: string
  /** 船舶 ID（船供上下文） */
  vesselId?: string
}

/** 批量查询协议价（免登可访问；游客/非成员由服务端清空主体上下文，未命中返回空数组） */
export function getPolicyPrices(query: PolicyPriceQuery) {
  return alovaInstance.Post<PolicyPriceItem[]>('/promotion/app/policy/prices', query)
}
