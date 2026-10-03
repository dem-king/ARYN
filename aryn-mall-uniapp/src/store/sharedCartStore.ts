import type { SharedCart } from '@/api/order/sharedCart'

/**
 * 共享购物车全局状态：当前用户可加购的「进行中」那张单。
 *
 * 用途：首页/分类/商详/常购清单等普通加购入口在选择目的地时读取。
 * 服务端 active-summary 已按「我是成员 + 收集中 + 未过期」过滤，
 * 前端再经 pickActiveCart 防御一次（见 utils/shared-cart.ts）。
 *
 * 缓存策略：TTL 内直接用缓存（加购零等待）；过期则现场刷新后再返回，
 * 保证弹层里展示的就是当前还开放收集的单据。未登录 / 租户未开通船供
 * 不发请求，直接视为没有进行中单据，普通加购零打扰。
 */
import { defineStore } from 'pinia'
import { getActiveSharedCartSummary } from '@/api/order/sharedCart'
import { useAuthStore } from '@/store/authStore'
import { useShipContextStore } from '@/store/shipContextStore'
import { useTenantCapabilityStore } from '@/store/tenantCapabilityStore'
import { pickActiveCart } from '@/utils/shared-cart'

/** 摘要缓存有效期：连续加购与页面切换共用这份缓存，避免每次点击都发请求 */
const SUMMARY_TTL_MS = 30_000

/** 模块级在途请求锁：加购与页面 onShow 同时触发刷新时只发一次 */
let inflight: Promise<void> | null = null

export const useSharedCartStore = defineStore('sharedCart', {
  state: () => ({
    /** 当前可作为快捷加购目的地的购物车；null 表示没有（或不可编辑） */
    activeCart: null as SharedCart | null,
    /** 缓存写入时间；配合 loadedToken 判断是否可复用 */
    loadedAt: 0,
    /** 写缓存时的登录态（token）：登录/切换账号后强制重取 */
    loadedToken: '',
  }),

  actions: {
    /** 作废缓存：共享车明细变化（加购/提交/关闭）后调用，下一次读取强制刷新 */
    invalidate() {
      this.loadedAt = 0
    },

    async refreshActive() {
      const authStore = useAuthStore()
      const token = authStore.isLoggedIn ? authStore.token : ''
      if (!inflight && this.loadedToken === token && Date.now() - this.loadedAt < SUMMARY_TTL_MS)
        return

      if (!inflight) {
        inflight = this.doLoad(token).finally(() => {
          inflight = null
        })
      }
      await inflight
    },

    async doLoad(token: string) {
      try {
        if (!token) {
          this.activeCart = null
          return
        }
        // 未开通船供的租户永远不会有共享购物车，不发这个请求
        const capabilityStore = useTenantCapabilityStore()
        await capabilityStore.ensureLoaded()
        if (!capabilityStore.shipSupplyEnabled) {
          this.activeCart = null
          return
        }
        const shipContextStore = useShipContextStore()
        const summary = await getActiveSharedCartSummary(shipContextStore.vesselCallId || undefined)
        this.activeCart = pickActiveCart(summary)
      }
      catch {
        // 摘要失败不阻塞普通加购：按「没有进行中单据」处理，下次点击再试
        this.activeCart = null
      }
      finally {
        this.loadedAt = Date.now()
        this.loadedToken = token
      }
    },
  },
})
