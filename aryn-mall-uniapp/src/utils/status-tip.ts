import { apiBaseUrl, buildApiUrl } from '@/api/core/api-base-url'
import { parseOpenBoot, rewriteBootUrl } from '@/api/core/boot-url'

/** wd-status-tip 内置的 7 个预设位 */
export const STATUS_TIP_PRESETS = [
  'search',
  'network',
  'content',
  'collect',
  'comment',
  'halo',
  'message',
] as const

export type StatusTipPreset = (typeof STATUS_TIP_PRESETS)[number]

/** 购物车 / 订单 / 秒杀三张会场空态插画（随包内置，可通过后台覆盖） */
export const STATUS_TIP_ILLUSTRATIONS = [
  'empty-cart',
  'empty-order',
  'empty-seckill',
] as const

export type StatusTipIllustration = (typeof STATUS_TIP_ILLUSTRATIONS)[number]

/** store 与后台配置共同支持的 10 个图片位 */
export type StatusTipImageKey = StatusTipPreset | StatusTipIllustration

/** 图片位 → 自定义图片 URL（空串/缺省表示回落内置图） */
export type StatusTipImageMap = Partial<Record<StatusTipImageKey, string>>

/**
 * 三张会场插画的随包兜底路径。
 *
 * 与 wd-status-tip 的预设图不同：它们不在后端 static/status-tip 目录里，
 * 而是随小程序包发布，未配置时直接走包内资源，避免为空态再增加一次网络请求。
 */
export const STATUS_TIP_ILLUSTRATION_FALLBACKS: Record<StatusTipIllustration, string> = {
  'empty-cart': '/static/promotion/empty/empty-cart.png',
  'empty-order': '/static/promotion/empty/empty-order.png',
  'empty-seckill': '/static/promotion/empty/empty-seckill.png',
}

/**
 * 内置空态图的后端地址前缀（仅 wd-status-tip 的 7 个预设位使用）。
 *
 * 图片随 aryn-upms 静态资源发布（`resources/static/status-tip/*.png`），
 * cloud 形态是 `/upms/status-tip/`，boot 形态由 `rewriteBootUrl` 改写为 `/boot/status-tip/`。
 * 与商品图共用同一个后端域名，小程序 downloadFile 合法域名不用新增，
 * 也不再依赖 registry.npmmirror.com（该包已被 npmmirror 的 unpkg 白名单拒绝，返回 451）。
 */
export function resolveStatusTipUrlPrefix(
  openBoot: boolean,
  baseUrl: string = apiBaseUrl,
): string {
  const path = rewriteBootUrl('/upms/status-tip/', openBoot) || '/upms/status-tip/'
  const url = buildApiUrl(path, baseUrl)
  // 组件内部按 `${urlPrefix}${image}.png` 拼接，前缀必须以 / 结尾
  return url.endsWith('/') ? url : `${url}/`
}

export const STATUS_TIP_URL_PREFIX = resolveStatusTipUrlPrefix(
  parseOpenBoot(import.meta.env.VITE_OPEN_BOOT),
)

/** 图片地址只允许 http/https 或站内相对路径，与服务端校验口径一致 */
export function isStatusTipImageUrl(url: string): boolean {
  return /^(?:https?:\/\/|\/)/i.test(url.trim())
}

/**
 * 把配置值变成可直接渲染的地址。
 *
 * 后台本地上传在未配置存储域名时返回的是 `/upms/file/local/...` 这类站内相对路径，
 * 小程序 `<image>` 只认完整 URL（相对路径会被当成包内文件），因此这里补后端域名；
 * boot 模式下 `/upms/` 还要改写为 `/boot/`。对象存储返回的公网 URL 原样保留。
 */
export function resolveStatusTipImageUrl(
  url: string,
  baseUrl: string = apiBaseUrl,
  openBoot: boolean = parseOpenBoot(import.meta.env.VITE_OPEN_BOOT),
): string {
  const trimmed = url.trim()
  if (/^https?:\/\//i.test(trimmed)) {
    return trimmed
  }
  let path = trimmed
  if (openBoot && path.startsWith('/upms/')) {
    path = `/boot/${path.slice('/upms/'.length)}`
  }
  return buildApiUrl(path, baseUrl)
}

function isStatusTipIllustration(
  image: StatusTipImageKey,
): image is StatusTipIllustration {
  return (STATUS_TIP_ILLUSTRATIONS as readonly string[]).includes(image)
}

/**
 * 解析某个图片位最终使用的图片地址：
 * 租户在后台配了合法 URL 就用配置值；否则回落内置图
 * （7 个预设位走后端静态图，3 张会场插画走随包 PNG）。
 */
export function resolveStatusTipImage(
  image: StatusTipImageKey,
  images: StatusTipImageMap = {},
  prefix = STATUS_TIP_URL_PREFIX,
): string {
  const configured = images[image]?.trim()
  if (configured && isStatusTipImageUrl(configured)) {
    return resolveStatusTipImageUrl(configured)
  }
  if (isStatusTipIllustration(image)) {
    return STATUS_TIP_ILLUSTRATION_FALLBACKS[image]
  }
  return `${prefix}${image}.png`
}
