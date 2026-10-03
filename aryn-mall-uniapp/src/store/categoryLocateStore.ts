/**
 * 商品列表页 → 分类页的跨 tab 定位中转。
 *
 * 分类页是 tabBar 页，`switchTab` 不接受 query，URL 带不上目标分类；
 * 用内存 store 中转：商品列表页跳转前写入，分类页 onShow 读走后立即清空。
 * 刻意**不落本地缓存**：定位意图只对「紧接着的那一次进入分类页」有效，
 * 持久化反而会在 App 重启后冒出一次过时定位（storage 方案读后忘记清就如此）。
 */
import { defineStore } from 'pinia'

/** 待定位的目标分类；两个 id 与商品列表页解析后的入参同义 */
export interface CategoryLocateTarget {
  categoryFirstId: string
  categorySecondId: string
}

interface CategoryLocateState {
  pendingLocate: CategoryLocateTarget | null
}

export const useCategoryLocateStore = defineStore('categoryLocate', {
  state: (): CategoryLocateState => ({
    pendingLocate: null,
  }),

  actions: {
    /** 记录「下一次进入分类页要定位到哪」（快照拷贝，防调用方复用对象串味） */
    setPendingLocate(target: CategoryLocateTarget) {
      this.pendingLocate = { ...target }
    },

    /** 取走定位目标（读后即清，之后的普通进入分类页不再触发定位） */
    consumePendingLocate(): CategoryLocateTarget | null {
      const target = this.pendingLocate
      this.pendingLocate = null
      return target
    },
  },
})
