import { alovaInstance } from '@/api/core/instance'
import { ensureShipContextLoaded } from '@/composables/useShipContextLoad'
import { useShipContextStore } from '@/store/shipContextStore'

// 分页列表
export function getPage(params: object) {
  return alovaInstance.Get<any>('/mall-order/app/shopping-cart/page', {
    params,
  })
}

/**
 * 加购前确保船舶上下文已装载。
 *
 * 这里携带的 vesselId / vesselCallId 是**加购时刻的归属快照**，会落库并被
 * 购物车据以分组（防串船）。上下文不持久化，冷启动为空——若不先装载，
 * 快照就写空，商品进购物车后一律落到「未指定配送计划」，跨靠港拦截失效。
 * 装载失败不阻断加购（按无归属写入），与页面降级口径一致。
 */
async function withShipContext() {
  await ensureShipContextLoaded()
  const shipContextStore = useShipContextStore()
  return {
    vesselId: shipContextStore.vesselId || undefined,
    vesselCallId: shipContextStore.vesselCallId || undefined,
    purchaseScene: shipContextStore.purchaseScene || undefined,
  }
}

// 购物车添加（自动携带当前船舶/靠港/场景归属，防串船分组键）
export async function addShoppingCart(data: Record<string, any>) {
  const context = await withShipContext()
  return alovaInstance.Post<any>('/mall-order/app/shopping-cart', {
    ...data,
    ...context,
  })
}

/** 批量加购结果（部分成功语义：单项失败不回滚其余项） */
export interface ShoppingCartBatchAddResult {
  requestedCount: number
  addedCount: number
  failedCount: number
  failures: Array<{
    skuId: string
    quantity: number
    reason: string
  }>
}

/**
 * 购物车批量加购。
 *
 * 用于首页「清单还差 / 今日补给单」勾选多项后一次加入：
 * 服务端逐项复用单条加购校验，返回成功/失败清单，前端可提示"已加入 8 项，2 项失败"。
 * 船舶/靠港上下文与单条加购口径一致，统一由此函数补齐，避免调用方漏传导致串船。
 */
export async function batchAddShoppingCart(items: Array<{ skuId: string, quantity: number }>) {
  const context = await withShipContext()
  return alovaInstance.Post<ShoppingCartBatchAddResult>('/mall-order/app/shopping-cart/batch', {
    items: items.map(item => ({
      ...item,
      ...context,
    })),
  })
}

// 购物车编辑
export function editShoppingCart(data: object) {
  return alovaInstance.Put<any>('/mall-order/app/shopping-cart', data)
}

// 购物车删除
export function delShoppingCart(data: object) {
  return alovaInstance.Post<any>('/mall-order/app/shopping-cart/del', data)
}

// 购物车数量查询
export function getCount(params: object) {
  return alovaInstance.Get<any>('/mall-order/app/shopping-cart/count', {
    params,
  })
}
