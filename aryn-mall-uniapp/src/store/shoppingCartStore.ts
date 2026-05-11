/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * 购物车数量状态管理（仅管理购物车商品数量）
 */
import { defineStore } from 'pinia'
import { getCount } from '@/api/order/shoppingCart'

interface ShoppingCartState {
  cartCount: number
  isLoading: boolean
  _pendingPromise?: Promise<number> | null // 防止重复请求
}

export const useShoppingCartStore = defineStore('shoppingCart', {
  state: (): ShoppingCartState => ({
    cartCount: 0,
    isLoading: false,
    _pendingPromise: null,
  }),

  getters: {
    /** 获取购物车数量 */
    getCartCount: state => state.cartCount,

    /** 是否正在加载中 */
    getIsLoading: state => state.isLoading,

    /** 购物车是否为空 */
    getIsCartEmpty: state => state.cartCount === 0,
  },

  actions: {
    /** 设置购物车数量 */
    setCartCount(count: number) {
      this.cartCount = count
    },

    /**
     * 获取购物车数量（带防重请求）
     * - 自动防止重复请求
     * - 返回 Promise<number>
     */
    async fetchCartCount(): Promise<number> {
      // 如果有未完成的请求，直接复用
      if (this._pendingPromise) {
        return this._pendingPromise
      }

      this._pendingPromise = (async () => {
        try {
          this.isLoading = true
          const response = await getCount({}).send()
          const count = Number(response ?? 0)
          this.setCartCount(count)
          return count
        }
        catch (error) {
          console.error('[购物车] 获取数量失败:', error)
          throw error
        }
        finally {
          this.isLoading = false
          this._pendingPromise = null
        }
      })()

      return this._pendingPromise
    },

    /** 本地增加购物车数量 */
    incrementCartCount(amount: number = 1) {
      this.cartCount += amount
    },

    /** 本地减少购物车数量 */
    decrementCartCount(amount: number = 1) {
      this.cartCount = Math.max(0, this.cartCount - amount)
    },

    /** 清空购物车数量 */
    clearCartCount() {
      this.cartCount = 0
    },
  },
})
