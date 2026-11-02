import { createSSRApp } from 'vue'
import App from './App.vue'
import i18n from './locales'
import router from './router'
import { useAuthStore } from './store/authStore'
import { registerSessionClearHandler } from './store/sessionStore'
import { Local } from './utils/storage'

const pinia = createPinia()
pinia.use(persistPlugin)

/**
 * 会话失效弹窗「重新登录/退出」时才真正清凭证。
 *
 * 统一在主入口注册而不是让 sessionStore 直接 import authStore：handlers/instance
 * 依赖 sessionStore，sessionStore 再依赖 authStore 会形成
 * instance → handlers → sessionStore → authStore → api/auth → instance 的循环；
 * 且「清态到底清哪些」属于登录链路的职责，放在这里有唯一出处。
 *
 * 原 `uni.$emit('auth-expired') → clearAuthData` 链路已随一期改造移除：401 检测瞬间
 * 不再清态（弹窗期间页面数据保持原样），改由本 handler 在用户点按钮后直接清态，
 * 避免「检测时清一半、动作时再清一遍」的双路径。
 *
 * 商城与配送身份隔离：配送失效只清 TOB 凭证，不把商城登录态一起踢掉。
 */
registerSessionClearHandler((target) => {
  if (target === 'delivery') {
    Local.remove('deliveryToken')
    Local.remove('deliveryStaffInfo')
    uni.$emit('delivery-auth-expired')
    return
  }
  useAuthStore(pinia).clearAuthData()
})

export function createApp() {
  const app = createSSRApp(App)
  app.use(router)
  app.use(i18n)
  app.use(pinia)
  return {
    app,
  }
}
