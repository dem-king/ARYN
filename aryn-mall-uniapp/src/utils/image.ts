/**
 * 图片 src 兜底工具。
 *
 * 说明：这里**不做** WebP 转码。装修图片由租户存储配置决定来源，
 * 当前部署走 MinIO / 本地存储，而 OSS 的 `x-oss-process` 之类格式参数
 * 对 MinIO 无效——追加了只会变成一个无意义的查询串。若将来接入支持
 * 格式转换的 CDN，应改为通过存储配置下发转换规则，而不是在端上硬拼参数。
 */

/**
 * 图片兜底占位图。
 *
 * 10x10 透明 SVG，避免空 src 时渲染出浏览器/小程序自带的破图边框。
 */
export const PLACEHOLDER_IMAGE
  = 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxMCIgaGVpZ2h0PSIxMCI+PC9zdmc+'

/**
 * 取图片 src，空值回退占位图。
 *
 * 用于 `:src` 没有 v-if 守卫的渲染位：URL 为空时 `<image>` 会渲染成破图，
 * 传占位图可保持布局稳定。
 */
export function resolveImageSrc(url: string | undefined | null): string {
  return url || PLACEHOLDER_IMAGE
}
