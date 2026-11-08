import { onMounted, onUnmounted, ref } from 'vue'

type WindowResizeEvent = { size?: { windowWidth?: number } }

/**
 * 响应式瀑布流列数：手机 2 列，宽屏（默认 ≥600px，与列表页断点一致）3 列。
 *
 * @param breakpoint 宽屏断点（px）
 * @param phoneColumns 手机列数
 * @param wideColumns 宽屏列数
 */
export function useResponsiveColumns(
  breakpoint = 600,
  phoneColumns = 2,
  wideColumns = 3,
) {
  const info = uni.getSystemInfoSync() as {
    uniPlatform?: string
    windowWidth?: number
  }
  // 宽屏多列只在微信小程序折叠/平板展开态启用，
  // 与原宽屏密度样式段的 MP-WEIXIN 范围保持一致（H5/App 维持手机列数）
  const wideEnabled = info.uniPlatform === 'mp-weixin'

  const columns = ref(phoneColumns)
  const resolve = (width: number) => {
    columns.value = wideEnabled && width >= breakpoint ? wideColumns : phoneColumns
  }
  resolve(Number(info.windowWidth) || 0)

  const onResize = (event: WindowResizeEvent) => {
    const width = Number(event?.size?.windowWidth)
    if (Number.isFinite(width) && width > 0) {
      resolve(width)
    }
  }
  onMounted(() => {
    try {
      (uni as any).onWindowResize?.(onResize)
    }
    catch {
      // 小程序端不支持窗口尺寸监听时保持初始列数
    }
  })
  onUnmounted(() => {
    try {
      (uni as any).offWindowResize?.(onResize)
    }
    catch {
      // 同上，无监听可移除
    }
  })

  return columns
}
