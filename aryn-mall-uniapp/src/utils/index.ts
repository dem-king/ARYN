import router from '@/router'
import { isTabBarPage, toTabBarUrl } from '@/utils/tab-bar'

/**
 * 跳转。
 *
 * tabBar 页必须走 `switchTab`，且判定要吞掉 query：旧实现拿
 * `jumpUrl` 去掉首斜杠后与 `pagePath` 全等比较，形如
 * `/pages/home/index?tab=1` 的链接匹配不上，会落到 navigateTo 分支，
 * 对 tabBar 页必然失败并触发微信 `routeDone with a webviewId ... is not found`。
 */
export function toJumpUrl(jumpUrl: string) {
  if (!jumpUrl) {
    return
  }
  if (isTabBarPage(jumpUrl)) {
    // tabBar 页不接受 query，统一截断后再 switchTab
    router.pushTab({
      path: toTabBarUrl(jumpUrl),
    })
    return
  }
  const pages = getCurrentPages()
  // 预留 2 个页面空间（避免频繁崩）
  if (pages.length >= 8) {
    router.replace({
      path: jumpUrl,
    })
  }
  else {
    router.push({
      path: jumpUrl,
    })
  }
}
// 格式化时间显示
export function formatTime(timeStr: string) {
  if (!timeStr)
    return ''

  const now = new Date()
  const messageTime = new Date(timeStr)

  // 检查日期是否有效
  if (Number.isNaN(messageTime.getTime())) {
    return timeStr
  }

  const nowTime = now.getTime()
  const messageTimeStamp = messageTime.getTime()
  const diffMs = nowTime - messageTimeStamp
  const diffMinutes = Math.floor(diffMs / (1000 * 60))
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60))

  // 获取今天的开始时间
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const messageDate = new Date(
    messageTime.getFullYear(),
    messageTime.getMonth(),
    messageTime.getDate(),
  )

  // 如果是今天
  if (messageDate.getTime() === today.getTime()) {
    if (diffMinutes < 1) {
      return '刚刚'
    }
    else if (diffMinutes < 60) {
      return `${diffMinutes}分钟前`
    }
    else if (diffHours < 24) {
      return `${diffHours}小时前`
    }
  }

  // 如果是昨天
  const yesterday = new Date(today.getTime() - 24 * 60 * 60 * 1000)
  if (messageDate.getTime() === yesterday.getTime()) {
    return '昨天'
  }

  // 如果是今年
  if (messageTime.getFullYear() === now.getFullYear()) {
    const month = String(messageTime.getMonth() + 1).padStart(2, '0')
    const day = String(messageTime.getDate()).padStart(2, '0')
    return `${month}-${day}`
  }

  // 其他情况显示年月日
  const year = messageTime.getFullYear()
  const month = String(messageTime.getMonth() + 1).padStart(2, '0')
  const day = String(messageTime.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
