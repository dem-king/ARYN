/**
 * tabBar 选中图标随主题色染色（canvas source-in 染色 + setTabBarItem 换图）。
 *
 * 原生 tabBar 图标是 PNG 静态资源，不吃 CSS 变量，微信也没有「图标改色」API
 * （有赞为此专门做了「底部导航自定义品牌色」功能，解决的是同一个问题）。
 * 方案：把选中图标读进离屏 canvas，用 source-in 把非透明像素统一刷成主题色，
 * 导出临时文件后经 setTabBarItem 替换；同一次会话内同一颜色只染一次。
 *
 * 降级策略：任一步失败（低版本基础库不支持离屏 canvas / 临时文件导出失败）
 * 静默保持原始红色图标——选中态文字色仍由 setTabBarStyle 同步，可接受。
 * 仅微信小程序启用；默认红 #FF2237 与内置图标同色，跳过染色省一次启动开销。
 */

const ICON_SIZE = 120
const DEFAULT_PRIMARY = '#FF2237'

interface TabBarIcon {
  index: number
  path: string
}

/** 与 pages.config.ts 的 tabBar 列表一一对应（选中态图标） */
const SELECTED_ICONS: TabBarIcon[] = [
  { index: 0, path: '/static/tabbar/home-selected.png' },
  { index: 1, path: '/static/tabbar/category-selected.png' },
  { index: 2, path: '/static/tabbar/cart-selected.png' },
  { index: 3, path: '/static/tabbar/user-selected.png' },
]

/** 上一次染成功的颜色（含失败占位），避免主题未变时重复染 */
let lastTintedColor = ''

/** 离屏 canvas 的最小结构类型（不依赖 miniprogram 命名空间，H5 编译下也能过类型检查） */
interface TintCanvas {
  createImage: () => TintCanvasImage
  getContext: (contextId: '2d') => CanvasRenderingContext2D
}

interface TintCanvasImage {
  onload: (() => void) | null
  onerror: (() => void) | null
  src: string
}

function tintIcon(
  canvas: TintCanvas,
  icon: TabBarIcon,
  color: string,
): Promise<void> {
  return new Promise((resolve) => {
    const image = canvas.createImage()
    image.onload = () => {
      try {
        const ctx = canvas.getContext('2d')
        ctx.clearRect(0, 0, ICON_SIZE, ICON_SIZE)
        ctx.drawImage(image as unknown as CanvasImageSource, 0, 0, ICON_SIZE, ICON_SIZE)
        // source-in：只保留原图 alpha 形状，颜色整体刷成主题色
        ctx.globalCompositeOperation = 'source-in'
        ctx.fillStyle = color
        ctx.fillRect(0, 0, ICON_SIZE, ICON_SIZE)
        ctx.globalCompositeOperation = 'source-over'
        wx.canvasToTempFilePath({
          canvas,
          success: (res) => {
            uni.setTabBarItem({
              index: icon.index,
              selectedIconPath: res.tempFilePath,
              fail: () => {
                // 非 tabBar 页上下文会失败，tab 页触发时会再走一遍
              },
            })
            resolve()
          },
          fail: () => resolve(),
        })
      }
      catch {
        resolve()
      }
    }
    image.onerror = () => resolve()
    image.src = icon.path
  })
}

export function tintTabBarIcons(color: string) {
  // #ifdef MP-WEIXIN
  if (!color || color.toUpperCase() === DEFAULT_PRIMARY || lastTintedColor === color) {
    return
  }
  if (typeof wx.createOffscreenCanvas !== 'function') {
    return
  }
  lastTintedColor = color
  const canvas = wx.createOffscreenCanvas({
    type: '2d',
    height: ICON_SIZE,
    width: ICON_SIZE,
  })
  if (!canvas) {
    return
  }
  void SELECTED_ICONS.reduce(
    (chain, icon) => chain.then(() => tintIcon(canvas, icon, color)),
    Promise.resolve(),
  )
  // #endif
}
