import { createSSRApp } from 'vue'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './store/authStore'

const pinia = createPinia()
pinia.use(persistPlugin)

function clearExpiredAuth() {
  useAuthStore(pinia).clearAuthData()
}

export function createApp() {
  const app = createSSRApp(App)
  app.use(router)
  app.use(pinia)
  uni.$off('auth-expired', clearExpiredAuth)
  uni.$on('auth-expired', clearExpiredAuth)
  return {
    app,
  }
}
