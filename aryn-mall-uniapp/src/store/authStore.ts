/**
 * 用户认证状态管理
 * 管理用户登录状态、Token、登录相关操作等
 */
import { defineStore } from 'pinia'
import {
  logout,
  passwordLogin as passwordLoginApi,
  phoneLogin as phoneLoginApi,
  quickLogin as quickLoginApi,
  wxLogin as wxLoginApi,
} from '@/api/auth'
import { useUserStore } from '@/store/userStore'
import { useShoppingCartStore } from '@/store/shoppingCartStore'

/** 登录参数类型 */
interface PhoneLoginParams {
  phone: string
  code: string
}

interface PasswordLoginParams {
  phone: string
  password: string
}

interface QuickLoginParams {
  phone: string
  code: string
}

interface MPLoginParams {
  jsCode: string
}

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
    /** 是否已登录 */
    isLoggedIn: state => state.isLogin && !!state.token,
    /** 获取用户Token */
    getToken: state => state.token,
    /** 是否正在加载中 */
    getIsLoading: state => state.isLoading,
  },

  actions: {
    /** 初始化认证状态（持久化自动恢复，无需手动初始化） */
    init() {},

    /** 设置用户登录状态 */
    setLoginState(token: string) {
      this.token = token
      this.isLogin = true
    },

    /** 清除认证数据 */
    clearAuthData() {
      this.token = ''
      this.isLogin = false
      try {
        const userStore = useUserStore()
        const shoppingCartStore = useShoppingCartStore()
        userStore.clearUserInfo()
        shoppingCartStore.clearCartCount()
      }
      catch (error) {
        console.warn('清除用户信息失败:', error)
      }
    },

    /**
     * 登录公共逻辑（消除 4 个登录方法 ~120 行重复代码）
     * @param apiFn 登录 API 函数
     * @param data 登录参数
     * @param needJsCode 是否需要获取微信 jsCode
     */
    async executeLogin<T>(
      apiFn: (data: any) => { send: () => Promise<any> },
      data: T,
      needJsCode = false,
    ): Promise<any> {
      if (this.isLoading) {
        throw new Error('正在登录中，请稍候...')
      }
      this.isLoading = true

      try {
        let loginData: any = data
        if (needJsCode) {
          const jsCode = await this.ensureWxIdentityForAccountLogin()
          loginData = Object.assign({}, data, { jsCode })
        }

        const response: any = await apiFn(loginData).send()

        if (response.tokenValue) {
          this.setLoginState(response.tokenValue)
          await this.fetchUserInfoAfterLogin()
        }

        return response
      }
      catch (error) {
        console.error('登录失败:', error)
        throw error
      }
      finally {
        this.isLoading = false
      }
    },

    /** 微信小程序登录 */
    async wxLogin(data: MPLoginParams): Promise<any> {
      return this.executeLogin(wxLoginApi, data, false)
    },

    /** 手机验证码登录 */
    async phoneLogin(data: PhoneLoginParams): Promise<any> {
      return this.executeLogin(phoneLoginApi, data, true)
    },

    /** 手机号密码登录 */
    async passwordLogin(data: PasswordLoginParams): Promise<any> {
      return this.executeLogin(passwordLoginApi, data, true)
    },

    /** 小程序手机号快速登录 */
    async quickLogin(data: QuickLoginParams): Promise<any> {
      return this.executeLogin(quickLoginApi, data, true)
    },

    /** 登录成功后获取用户信息 */
    async fetchUserInfoAfterLogin(): Promise<void> {
      try {
        const userStore = useUserStore()
        const shoppingCartStore = useShoppingCartStore()
        await userStore.fetchUserInfo()
        await shoppingCartStore.fetchCartCount()
      }
      catch (error) {
        console.warn('登录成功但获取用户信息失败:', error)
      }
    },

    /** 用户登出 */
    async userLogout(): Promise<void> {
      try {
        await logout().send()
      }
      catch (error) {
        console.warn('登出接口调用失败：', error)
      }
      finally {
        this.clearAuthData()
      }
    },

    /** 账号登录前获取微信 code */
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
