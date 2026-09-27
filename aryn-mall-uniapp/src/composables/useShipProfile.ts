import { ref } from 'vue'

import { getShipSummary } from '@/api/product/spu'

/**
 * 商品船供资料（profile + 每个 SKU 的 MOQ/步长/采购单位）。
 *
 * 模块级缓存：同一次应用生命周期内同一 SPU 只请求一次。
 * 详情页有两处消费——
 *  1. ShipProfileCard 展示采购单位/箱规/起订/步长；
 *  2. 购买链路把单规格 SKU 的 MOQ/步长灌进数量弹层（min-buy-num / step-buy-num），
 *     否则弹层固定从 1 起，船供商品要等共享购物车提交时才被数量规则拦下。
 * 两处共用同一份缓存，避免同一详情页发两次相同请求。
 *
 * 请求失败不写缓存（下次进入重试），且不抛出——船供资料缺失不阻塞商品主流程。
 */
const profileCache = new Map<string, { profile: any, skuProfiles: any[] }>()

export function useShipProfile() {
  const profile = ref<any>(null)
  const skuProfiles = ref<any[]>([])
  const loadedSpuId = ref('')

  async function load(spuId: string) {
    if (!spuId || loadedSpuId.value === spuId)
      return
    loadedSpuId.value = spuId

    const cached = profileCache.get(spuId)
    if (cached) {
      profile.value = cached.profile
      skuProfiles.value = cached.skuProfiles
      return
    }

    try {
      const response = await getShipSummary(spuId)
      profile.value = response?.profile ?? null
      skuProfiles.value = response?.skuProfiles ?? []
      profileCache.set(spuId, { profile: profile.value, skuProfiles: skuProfiles.value })
    }
    catch {
      // 接口异常时清空标记，允许下次进入重试；卡片与数量规则按「无船供资料」降级
      loadedSpuId.value = ''
    }
  }

  return { profile, skuProfiles, load }
}
