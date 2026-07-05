<script setup lang="ts">
import { captureDistributionShareParams, flushPendingDistributionShareBinding } from '@/composables/useDistributionShare'
import {
  connectNotifyWebSocket,
  disconnectNotifyWebSocket,
} from '@/composables/useNotifyWebSocket'
import { getUnreadCount } from '@/api/notify/notifyMessage'
import { useAuthStore } from '@/store/authStore'

/** 更新 tabBar 未读 Badge */
async function updateNotifyBadge() {
  const authStore = useAuthStore()
  if (!authStore.isLoggedIn) {
    uni.removeTabBarBadge({ index: 3 })
    return
  }
  try {
    const res = await getUnreadCount()
    const total = res?.total || 0
    if (total > 0) {
      uni.setTabBarBadge({
        index: 3,
        text: total > 99 ? '99+' : String(total),
      })
    }
    else {
      uni.removeTabBarBadge({ index: 3 })
    }
  }
  catch (e) {
    console.warn('[Notify] 更新未读 Badge 失败', e)
  }
}

/**
 * 应用启动时的初始化逻辑
 */
onLaunch(async () => {
  // #ifdef MP
  updateManager()
  // #endif

  // 全局错误处理
  if (import.meta.env.MODE === 'development') {
    // 开发环境下捕获未处理的 Promise rejection
    // #ifdef H5
    window.addEventListener('unhandledrejection', (event) => {
      console.error('[Unhandled Promise Rejection]', event.reason)
    })
    // #endif
  }

  // 已登录则启动站内信 WebSocket
  const authStore = useAuthStore()
  if (authStore.isLoggedIn) {
    connectNotifyWebSocket()
    updateNotifyBadge()
  }
  // 监听登录状态变化，自动连接/断开 WebSocket
  authStore.$subscribe((mutation, state) => {
    if (state.isLogin && state.token) {
      connectNotifyWebSocket()
      updateNotifyBadge()
    }
    else {
      disconnectNotifyWebSocket()
      uni.removeTabBarBadge({ index: 3 })
    }
  })

  // 监听未读数刷新事件
  uni.$on('notify:unread-refresh', updateNotifyBadge)
  uni.$on('notify:new', updateNotifyBadge)
})

onShow((options) => {
  captureDistributionShareParams(options as Record<string, any>)
  flushPendingDistributionShareBinding()
})

/**
 * 全局错误捕获（小程序/App 平台）
 */
onError((error) => {
  console.error('[App Error]', error)
  // TODO: 生产环境接入错误上报平台
})

/**
 * 全局未处理 Promise rejection 捕获
 */
onUnhandledRejection((res) => {
  console.error('[Unhandled Rejection]', res.reason)
})

function updateManager() {
  const updateManager = uni.getUpdateManager()
  updateManager.onUpdateReady(() => {
    useGlobalMessage().confirm({
      title: '更新提示',
      msg: '新版本已经准备好，是否重启应用？',
      closeOnClickModal: false,
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      success: (res) => {
        if (res.action === 'confirm') {
          // 新的版本已经下载好，调用 applyUpdate 应用新版本并重启
          updateManager.applyUpdate()
        }
      },
    })
  })
}
</script>

<style lang="scss">
page, #page {
  background-color: #F8F8F8 !important;
}
.page-wrapper {
  min-height: calc(100vh - var(--window-top));
  box-sizing: border-box;
  background: #f2f3f4;
  color: #333;
}

.wot-theme-dark.page-wrapper {
  background: #222;
  color: #fff;
}
</style>
