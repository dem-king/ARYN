/**
 * 租户能力开关（C 端）。
 *
 * 背景：`sys_tenant.business_mode`（1 综合 = 个人 + 船供并存；2 纯零售）由
 * `45ship_supply_menu_permission.sql` 引入，但此前**全仓无任何代码读取**，
 * 导致纯零售租户的 C 端首页照样渲染船舶工作台。
 *
 * 该 store 是 /upms/app/tenant/shop-info 的客户端缓存：只承载「当前租户
 * 有哪些业务能力」这一件事，不入库、不作为权限事实，仅用于决定入口是否渲染。
 *
 * 失败策略：拉取失败或字段缺失时按综合模式处理。船舶入口在无船/未登录时
 * 本就自行隐藏，误显示一张状态条的成本，低于把船供租户的入口整体隐藏。
 */
import { defineStore } from 'pinia'

import { getCurrentShop } from '@/api/upms/tenant'

/** 综合模式：个人购买与船供采购并存 */
export const BUSINESS_MODE_COMPREHENSIVE = '1'
/** 纯零售：不展示任何船供专属入口 */
export const BUSINESS_MODE_RETAIL = '2'

/**
 * 进行中的请求，用于并发去重。
 *
 * 刻意放在模块作用域而非 state：pinia 的持久化插件会 deepClone 整个 $state，
 * Promise 一旦进入 state 就会被序列化成无意义的值。
 */
let pendingRequest: Promise<void> | null = null

export const useTenantCapabilityStore = defineStore('tenantCapability', {
  state: () => ({
    businessMode: BUSINESS_MODE_COMPREHENSIVE as string,
    /** 是否已完成一次拉取（含失败），避免并发重复请求 */
    resolved: false,
  }),

  getters: {
    /** 是否展示船供专属能力（船舶工作台、靠港上下文等） */
    shipSupplyEnabled: state => state.businessMode !== BUSINESS_MODE_RETAIL,
  },

  actions: {
    /**
     * 拉取当前租户业务模式。
     *
     * 页面在 onShow 中调用：成功即缓存，失败不重试也不阻塞渲染，
     * 避免弱网下首页被一个装饰性入口拖住。
     */
    async ensureLoaded() {
      if (this.resolved)
        return
      if (pendingRequest)
        return pendingRequest

      pendingRequest = getCurrentShop()
        .then((shop) => {
          const mode = (shop as { businessMode?: string } | null)?.businessMode
          if (mode) {
            this.businessMode = mode
          }
        })
        .catch(() => {
          // 保持默认的综合模式，船舶入口的显隐继续由登录态与船舶关系决定
        })
        .finally(() => {
          this.resolved = true
          pendingRequest = null
        })

      return pendingRequest
    },

    /** 登出或切换租户时重置，避免上一个租户的业务模式泄漏到下一个 */
    reset() {
      this.businessMode = BUSINESS_MODE_COMPREHENSIVE
      this.resolved = false
      pendingRequest = null
    },
  },
})
