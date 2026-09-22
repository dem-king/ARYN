/**
 * 商品勾选态（首页「批量加购」用）。
 *
 * 背景：B 版原型在首页瀑布流卡片上加勾选方块，底部常驻操作条汇总
 * 「已选 N 项 / 批量加购」。勾选是**跨楼层共享**的（今日爆款、秒杀、
 * 常购清单都能勾），因此不能放在单个组件里，否则各楼层各选各的、
 * 底部条永远只反映最后一个组件的选择。
 *
 * 边界：
 *   · 只保存 SPU ID，不保存数量与规格 —— 数量规则（MOQ/步长）由服务端
 *     在批量加购时逐项校验，前端不自作主张算数量。
 *   · 不持久化：勾选是一次会话内的临时意图，重启后残留只会让用户困惑。
 *   · 多规格商品需要选规格才能确定 SKU，勾选阶段只记 SPU，真正加购时
 *     由调用方决定是直接加购还是唤起规格弹层。
 */
import { defineStore } from 'pinia'

export const useGoodsPickStore = defineStore('goodsPick', {
  state: () => ({
    /** 已勾选的 SPU ID（用数组而非 Set，便于持久化与调试） */
    pickedIds: [] as string[],
  }),

  getters: {
    pickedCount: state => state.pickedIds.length,
    hasPicked: state => state.pickedIds.length > 0,
    isPicked: state => (spuId: string) => state.pickedIds.includes(spuId),
  },

  actions: {
    toggle(spuId: string) {
      if (!spuId)
        return
      const index = this.pickedIds.indexOf(spuId)
      if (index >= 0)
        this.pickedIds.splice(index, 1)
      else
        this.pickedIds.push(spuId)
    },

    /**
     * 全选/全不选。
     *
     * @param spuIds 当前页可勾选的 SPU 列表
     * @param picked true 全选，false 全不选
     */
    setAll(spuIds: string[], picked: boolean) {
      const valid = spuIds.filter(Boolean)
      if (picked) {
        // 并集：不丢掉其他楼层已勾选的项
        const merged = new Set([...this.pickedIds, ...valid])
        this.pickedIds = [...merged]
        return
      }
      // 全不选只清空本页可见项，其他楼层的勾选保留
      const removing = new Set(valid)
      this.pickedIds = this.pickedIds.filter(id => !removing.has(id))
    },

    remove(spuId: string) {
      const index = this.pickedIds.indexOf(spuId)
      if (index >= 0)
        this.pickedIds.splice(index, 1)
    },

    clear() {
      this.pickedIds = []
    },
  },
})
