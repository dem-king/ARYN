import { tabBar } from '@/pages.json'
import router from '@/router'

/**
 * 获取当前页面路径
 * @returns 当前页面路径
 */
export function getCurrentPath() {
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1]
  return currentPage.route || ''
}

/**
 * 跳转
 */
export function toJumpUrl(jumpUrl: string) {
  if (!jumpUrl) {
    return
  }
  const cleanUrl = jumpUrl.startsWith('/') ? jumpUrl.substring(1) : jumpUrl
  const index = tabBar.list.findIndex((item: any) => {
    return cleanUrl === item.pagePath
  })
  if (index < 0) {
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
  else {
    router.pushTab({
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
