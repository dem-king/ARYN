/**
 * 用户认证状态管理
 * 管理用户登录状态、Token、登录相关操作等
 */
import { defineStore } from 'pinia'
import { logout, passwordLogin as passwordLoginApi, phoneLogin as phoneLoginApi, quickLogin as quickLoginApi, wxLogin as wxLoginApi } from '@/api/auth'
import { useShoppingCartStore } from '@/store/shoppingCartStore'
import { useUserStore } from '@/store/userStore'
import { requireTokenValue } from './auth-token'

interface AuthState {
  token: string
  isLogin: boolean
  isLoading: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: '',
    isLogin: false,
    isLoading: false,
  }),

  getters: {
    /**
     * 是否已登录
     */
    isLoggedIn: state => state.isLogin && !!state.token,

    /**
     * 获取用户Token
     */
    getToken: state => state.token,

    /**
     * 是否正在加载中
     */
    getIsLoading: state => state.isLoading,
  },

  actions: {
    /**
     * 初始化认证状态
     */
    init() {
      // 由于使用了项目的持久化系统，不需要手动初始化
      // 数据会自动从本地存储恢复
    },

    /**
     * 设置用户登录状态
     */
    setLoginState(token: string) {
      this.token = token
      this.isLogin = true
    },

    /**
     * 清除认证数据
     */
    clearAuthData() {
      this.token = ''
      this.isLogin = false
      // 清除用户信息
      try {
        const userStore = useUserStore()
        const shoppingCartStore = useShoppingCartStore()
        userStore.clearUserInfo()
        shoppingCartStore.clearCartCount()
        console.log('📝 用户信息已清除')
      }
      catch (error) {
        console.warn('清除用户信息失败:', error)
      }
    },
    /**
     * 微信小程序登录方法
     */
    async wxLogin(data: any): Promise<any> {
      if (this.isLoading) {
        throw new Error('正在登录中，请稍候...')
      }
      this.isLoading = true

      try {
        const response: any = await wxLoginApi(data).send()
        if (response.tokenValue) {
          // 自动登录
          // 存储token信息
          this.setLoginState(response.tokenValue)

          // 登录成功后自动获取用户信息
          await this.fetchUserInfoAfterLogin()
        }

        return response
      }
      catch (error) {
        console.error('手机验证码登录失败:', error)
        throw error
      }
      finally {
        this.isLoading = false
      }
    },
    /**
     * 手机验证码登录方法
     */
    async phoneLogin(data: any): Promise<any> {
      if (this.isLoading) {
        throw new Error('正在登录中，请稍候...')
      }

      this.isLoading = true

      try {
        const jsCode = await this.ensureWxIdentityForAccountLogin()
        const loginData = Object.assign(data, {
          jsCode,
        })
        const response: any = await phoneLoginApi(loginData).send()

        // 存储token信息
        this.setLoginState(requireTokenValue(response))

        // 登录成功后自动获取用户信息
        await this.fetchUserInfoAfterLogin()

        return response
      }
      catch (error) {
        console.error('手机验证码登录失败:', error)
        throw error
      }
      finally {
        this.isLoading = false
      }
    },

    /**
     * 手机号密码登录方法
     */
    async passwordLogin(data: any): Promise<any> {
      if (this.isLoading) {
        throw new Error('正在登录中，请稍候...')
      }

      this.isLoading = true
      try {
        const jsCode = await this.ensureWxIdentityForAccountLogin()
        const loginData = Object.assign(data, {
          jsCode,
        })
        const response: any = await passwordLoginApi(loginData).send()

        // 存储token信息
        this.setLoginState(requireTokenValue(response))

        // 登录成功后自动获取用户信息
        await this.fetchUserInfoAfterLogin()

        return response
      }
      catch (error) {
        console.error('密码登录失败:', error)
        throw error
      }
      finally {
        this.isLoading = false
      }
    },

    /**
     * 小程序手机号快速登录
     */
    async quickLogin(data: any): Promise<any> {
      if (this.isLoading) {
        throw new Error('正在登录中，请稍候...')
      }

      this.isLoading = true

      try {
        const jsCode = await this.ensureWxIdentityForAccountLogin()
        const loginData = Object.assign(data, {
          jsCode,
        })
        const response: any = await quickLoginApi(loginData).send()

        // 存储token信息
        this.setLoginState(requireTokenValue(response))

        // 登录成功后自动获取用户信息
        await this.fetchUserInfoAfterLogin()

        return response
      }
      catch (error) {
        console.error('快速登录失败:', error)
        throw error
      }
      finally {
        this.isLoading = false
      }
    },

    /**
     * 登录成功后获取用户信息
     * 私有方法，在登录成功后自动调用
     */
    async fetchUserInfoAfterLogin(): Promise<void> {
      try {
        const userStore = useUserStore()
        const shoppingCartStore = useShoppingCartStore()
        console.log('🔄 登录成功，开始获取用户信息...')
        await userStore.fetchUserInfo()
        console.log('✅ 用户信息获取成功')
        await shoppingCartStore.fetchCartCount()
      }
      catch (error) {
        console.warn('⚠️ 登录成功但获取用户信息失败:', error)
        // 不抛出错误，避免影响登录流程
      }
    },

    /**
     * 用户登出
     */
    async userLogout(): Promise<void> {
      try {
        // 调用登出接口
        await logout().send()
      }
      catch (error) {
        console.warn('登出接口调用失败：', error)
      }
      finally {
        // 无论接口是否成功，都清除本地数据
        this.clearAuthData()
      }
    },
    /**
     * 账号登录前获取微信code
     */
    async ensureWxIdentityForAccountLogin(): Promise<string> {
      // #ifdef MP
      const mpLoginRes = await new Promise<UniApp.LoginRes>((resolve, reject) => {
        uni.login({
          success: resolve,
          fail: reject,
        })
      })
      const jsCode = mpLoginRes.code
      if (!jsCode) {
        throw new Error('获取微信登录凭证失败')
      }
      return jsCode
      // #endif
      return ''
    },
  },
})
