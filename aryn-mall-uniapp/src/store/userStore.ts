/**
 * 用户信息状态管理（增强版）
 */
import { defineStore } from 'pinia'
import type { UserInfo } from '@/api/auth'
import { getUserInfo } from '@/api/auth'
import { getCount as getCollectCount } from '@/api/product/collect'
import { getCount as getCouponCount } from '@/api/promotion/couponUser'
import { getPointsInfo } from '@/api/user/points'
import type { PointsInfo } from '@/api/user/points'

interface UserState {
  userInfo: UserInfo | null

  // 会员积分信息
  pointsInfo: PointsInfo | null

  // loading flags
  isUserInfoLoading: boolean
  isCollectLoading: boolean
  isCouponLoading: boolean
  isPointsLoading: boolean

  // loading promise 去重与复用（避免并发冲突）
  userInfoPromise?: Promise<UserInfo>
  collectPromise?: Promise<number>
  couponPromise?: Promise<number>
  pointsPromise?: Promise<PointsInfo>

  collectCount: number
  couponCount: number
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    userInfo: null,
    pointsInfo: null,

    isUserInfoLoading: false,
    isCollectLoading: false,
    isCouponLoading: false,
    isPointsLoading: false,

    userInfoPromise: undefined,
    collectPromise: undefined,
    couponPromise: undefined,
    pointsPromise: undefined,

    collectCount: 0,
    couponCount: 0,
  }),

  getters: {
    getUserInfo: state => state.userInfo,
    getUserId: state => state.userInfo?.id ?? '',
    getUserNickname: state => state.userInfo?.nickname ?? '',
    getUserAvatar: state => state.userInfo?.avatarUrl ?? '',
    getUserPhone: state => state.userInfo?.phone ?? '',
    getIsLoading: state => state.isUserInfoLoading,
    getCollectCount: state => state.collectCount ?? 0,
    getCouponCount: state => state.couponCount ?? 0,
    getPointsInfo: state => state.pointsInfo,
    getPoint: state => state.pointsInfo?.point ?? 0,
    getBalance: state => state.pointsInfo?.balance ?? 0,
    getLevelName: state => state.pointsInfo?.levelName ?? '',
    getGrowthValue: state => state.pointsInfo?.growthValue ?? 0,
  },

  actions: {
    init() {
      // 持久化自动恢复，无需手动初始化
    },

    setUserInfo(userInfo: UserInfo) {
      this.userInfo = userInfo
    },

    clearUserInfo() {
      this.userInfo = null
      this.pointsInfo = null
      this.collectCount = 0
      this.couponCount = 0
    },

    /**
     * 安全地创建 userInfo stub（当外部只刷新余额但 userInfo 为空时使用）
     * 这里只初始化必要字段，避免破坏类型系统；根据你项目的 UserInfo 类型可扩展字段
     */
    ensureUserInfoStub(): UserInfo {
      if (this.userInfo)
        return this.userInfo
      const stub = {
        id: '',
        nickname: '',
        avatarUrl: '',
        phone: '',
      } as unknown as UserInfo
      this.userInfo = stub
      return this.userInfo
    },

    /**
     * fetchUserInfo - 使用去重 Promise 防止并发重复请求
     */
    async fetchUserInfo(options?: { onlyUser?: boolean }): Promise<UserInfo> {
      // 如果已有进行中的同类请求，直接复用
      if (this.userInfoPromise) {
        return this.userInfoPromise
      }

      this.userInfoPromise = (async () => {
        // 在 try 前设置 loading，保证同步异常也能被捕获后重置
        this.isUserInfoLoading = true
        try {
          const response: UserInfo = await getUserInfo({}).send()
          this.setUserInfo(response)

          // 如果不是仅刷新用户信息，则并行刷新其他统计数据
          if (!options?.onlyUser) {
            Promise.all([
              this.refreshUserCollectCount().catch(err =>
                console.error('[用户信息] 刷新收藏数量失败:', err),
              ),
              this.refreshUserCouponCount().catch(err =>
                console.error('[用户信息] 刷新优惠券数量失败:', err),
              ),
              this.refreshPointsInfo().catch(err =>
                console.error('[用户信息] 刷新积分信息失败:', err),
              ),
            ])
          }

          return response
        }
        catch (error) {
          console.error('获取用户信息失败:', error)
          throw error
        }
        finally {
          this.isUserInfoLoading = false
          // 清除 promise 以便下次可以重新请求
          this.userInfoPromise = undefined
        }
      })()

      return this.userInfoPromise
    },
    /**
     * refreshUserCollectCount - 去重 Promise
     */
    async refreshUserCollectCount(): Promise<number> {
      if (this.collectPromise)
        return this.collectPromise

      this.collectPromise = (async () => {
        this.isCollectLoading = true
        try {
          const response: number = await getCollectCount().send()
          this.collectCount = response
          return response
        }
        catch (error) {
          console.error('刷新收藏数量失败:', error)
          throw error
        }
        finally {
          this.isCollectLoading = false
          this.collectPromise = undefined
        }
      })()

      return this.collectPromise
    },

    updateUserCollectCount(count: number) {
      this.collectCount = count
    },

    /**
     * refreshUserCouponCount - 去重 Promise
     */
    async refreshUserCouponCount(): Promise<number> {
      if (this.couponPromise)
        return this.couponPromise

      this.couponPromise = (async () => {
        this.isCouponLoading = true
        try {
          const response: number = await getCouponCount().send()
          this.couponCount = response
          return response
        }
        catch (error) {
          console.error('刷新优惠券数量失败:', error)
          throw error
        }
        finally {
          this.isCouponLoading = false
          this.couponPromise = undefined
        }
      })()

      return this.couponPromise
    },

    updateUserCouponCount(count: number) {
      this.couponCount = count
    },

    /**
     * refreshPointsInfo - 刷新积分信息（去重 Promise）
     */
    async refreshPointsInfo(): Promise<PointsInfo> {
      if (this.pointsPromise)
        return this.pointsPromise

      this.pointsPromise = (async () => {
        this.isPointsLoading = true
        try {
          const response: PointsInfo = await getPointsInfo().send()
          this.pointsInfo = response
          return response
        }
        catch (error) {
          console.error('刷新积分信息失败:', error)
          throw error
        }
        finally {
          this.isPointsLoading = false
          this.pointsPromise = undefined
        }
      })()

      return this.pointsPromise
    },

    /**
     * 更新用户信息的某个字段
     */
    updateUserField(field: keyof UserInfo, value: any) {
      if (this.userInfo) {
        this.userInfo[field] = value
      }
    },
    /**
     * 本地增加收藏数量
     */
    incrementCollectCount(delta = 1) {
      this.collectCount = Math.max(0, this.collectCount + delta)
    },

    /**
     * 本地减少收藏数量
     */
    decrementCollectCount(delta = 1) {
      this.collectCount = Math.max(0, this.collectCount - delta)
    },

    /**
     * 本地增加优惠券数量
     */
    incrementCouponCount(delta = 1) {
      this.couponCount = Math.max(0, this.couponCount + delta)
    },

    /**
     * 本地减少优惠券数量
     */
    decrementCouponCount(delta = 1) {
      this.couponCount = Math.max(0, this.couponCount - delta)
    },
  },
})
