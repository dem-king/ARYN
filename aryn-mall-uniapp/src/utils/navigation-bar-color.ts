/**
 * 原生导航条颜色归一化（微信 API 参数约束）。
 *
 * 微信 `wx.setNavigationBarColor` 把 `backgroundColor` 定为**必填**，且有效值
 * 只接受十六进制颜色：
 *   · 只传 `frontColor` → `setNavigationBarColor:fail parameter error:
 *     parameter.backgroundColor should be String instead of Undefined`；
 *   · 传 `rgb()` / CSS 变量 / 空串 → 换成 `should be HexColor` 的另一种参数错误。
 *
 * 沉浸式导航（滚动态 / 磨砂材质）的页面都是 `navigationStyle: custom`，原生
 * 导航条本身不可见，这个字段只用来满足参数校验。因此这里把调用方手里的语义色
 * 归一成合法 hex 占位；读不出色值（CSS 变量、空串、脏数据）一律回退白色，
 * 保证任何页面配置下都不会再因为该参数缺失或非法在控制台报错。
 */
const HEX_COLOR = /^#([\da-f]{3,4}|[\da-f]{6}|[\da-f]{8})$/i
const RGB_COLOR = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)/i

function toHexChannel(value: number): string {
  return Math.round(Math.min(Math.max(value, 0), 255))
    .toString(16)
    .padStart(2, '0')
}

/**
 * 归一化为 `#rrggbb`。
 *
 * @param value 语义色：hex（#RGB / #RGBA / #RRGGBB / #RRGGBBAA）、rgb()/rgba()，
 *              或 CSS 变量等不可解析的值
 * @param fallback 不可解析时的兜底色，缺省白
 */
export function toNativeNavigationBarHexColor(
  value: string | null | undefined,
  fallback = '#ffffff',
): string {
  if (typeof value !== 'string')
    return fallback
  const color = value.trim()
  const hex = HEX_COLOR.exec(color)
  if (hex) {
    const raw = hex[1] ?? ''
    // #RGB / #RGBA 逐位翻倍；#RRGGBBAA 丢弃 alpha 段
    const base = raw.length <= 4
      ? [...raw.slice(0, 3)].map(char => char + char).join('')
      : raw.slice(0, 6)
    return `#${base.toLowerCase()}`
  }
  const rgb = RGB_COLOR.exec(color)
  if (rgb) {
    const channels = [rgb[1], rgb[2], rgb[3]].map(Number)
    if (
      channels.every(
        channel => Number.isFinite(channel) && channel >= 0 && channel <= 255,
      )
    ) {
      return `#${channels.map(channel => toHexChannel(channel)).join('')}`
    }
  }
  return fallback
}
