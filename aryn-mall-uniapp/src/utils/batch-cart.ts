import type { QuickCartInfo } from './quick-cart'
/**
 * 批量加购的分拣规则（纯函数，便于单测）。
 *
 * 首页勾选 N 个商品后一次性加购，但每个商品的形态不同：
 *   · 单规格 → 能直接算出 skuId 与合法数量，可以走批量接口
 *   · 多规格 → 必须先选规格才能确定 SKU，批量接口无法代劳
 *   · 已售罄 → 直接跳过并给出原因
 *
 * 这里只做「分拣」，不发请求：把每个 SPU 的加购信息归类后交给调用方，
 * 便于单测覆盖判定边界，也避免把网络逻辑与判定逻辑揉在一起。
 */
import {
  QUICK_CART_MODE_DIRECT,
  QUICK_CART_MODE_UNAVAILABLE,
  quickAddBlockedText,
  resolveQuickAddMode,
  resolveQuickAddQuantity,
} from './quick-cart'

/** 可直接批量加购的一项 */
export interface BatchAddReadyItem {
  spuId: string
  skuId: string
  quantity: number
  name: string
}

/** 需要用户先选规格的一项 */
export interface BatchAddNeedChooseItem {
  spuId: string
  info: QuickCartInfo
}

/** 不可加购的一项及原因 */
export interface BatchAddBlockedItem {
  spuId: string
  reason: string
}

export interface BatchAddPlan {
  ready: BatchAddReadyItem[]
  needChoose: BatchAddNeedChooseItem[]
  blocked: BatchAddBlockedItem[]
}

/**
 * 把「勾选的 SPU + 各自查到的加购信息」分拣成三类。
 *
 * @param pickedIds 勾选的 SPU ID（去重由调用方或本函数处理，这里会去重）
 * @param infoBySpuId 已查到的加购信息；缺失视为查询失败，归入 blocked
 */
export function buildBatchAddPlan(
  pickedIds: string[],
  infoBySpuId: Record<string, QuickCartInfo | null | undefined>,
): BatchAddPlan {
  const plan: BatchAddPlan = { ready: [], needChoose: [], blocked: [] }
  // 去重但不打乱顺序：底部条按用户勾选顺序展示更符合直觉
  const unique = [...new Set(pickedIds.filter(Boolean))]

  for (const spuId of unique) {
    const info = infoBySpuId[spuId]
    if (!info) {
      plan.blocked.push({ spuId, reason: '商品信息获取失败，请稍后重试' })
      continue
    }

    const mode = resolveQuickAddMode(info)
    if (mode === QUICK_CART_MODE_UNAVAILABLE) {
      plan.blocked.push({ spuId, reason: quickAddBlockedText(info) })
      continue
    }

    if (mode !== QUICK_CART_MODE_DIRECT) {
      plan.needChoose.push({ spuId, info })
      continue
    }

    const quantity = resolveQuickAddQuantity(info)
    if (quantity <= 0) {
      // 单规格但库存不足最小起订量：归入 blocked 而不是 ready
      plan.blocked.push({ spuId, reason: quickAddBlockedText(info) })
      continue
    }

    plan.ready.push({
      spuId,
      skuId: String(info.skuId),
      quantity,
      name: info.name || spuId,
    })
  }

  return plan
}

/**
 * 批量加购后的汇总文案。
 *
 * 刻意把三类结果都摆出来：只报「已加入 N 项」会让用户以为全成功了，
 * 而多规格与售罄项实际上并没有进购物车。
 */
export function batchAddSummaryText(result: {
  added: number
  needChoose: number
  blocked: number
  failed: number
}): string {
  const parts: string[] = []
  if (result.added > 0)
    parts.push(`已加入 ${result.added} 项`)
  if (result.needChoose > 0)
    parts.push(`${result.needChoose} 项需选规格`)
  if (result.blocked > 0)
    parts.push(`${result.blocked} 项不可加购`)
  if (result.failed > 0)
    parts.push(`${result.failed} 项加入失败`)
  return parts.length > 0 ? parts.join('，') : '没有可加购的商品'
}

/**
 * 是否需要给用户更详细的反馈（而非一个轻提示）。
 *
 * 全部成功时一个 toast 就够；只要存在需选规格/不可加购/失败项，
 * 就应该给出可查看的明细，否则用户不知道哪些没进去。
 */
export function needsBatchAddDetail(result: {
  needChoose: number
  blocked: number
  failed: number
}): boolean {
  return result.needChoose + result.blocked + result.failed > 0
}
