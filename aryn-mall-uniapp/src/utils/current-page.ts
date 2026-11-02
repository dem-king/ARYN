export function getCurrentPath() {
  return getCurrentPages().at(-1)?.route || ''
}

/**
 * 当前页面栈顶页面的完整路径（含 query），如 `/sub-pages/product/goods-detail/index?id=1`。
 *
 * `getCurrentPath()` 只返回 route（且不带前导 `/`，也不含参数），适合做「页面身份」比对；
 * 需要把用户送回原页时（登录 redirectPath）必须带上参数，否则商详/订单详情这类页面
 * 回跳后会丢 id 打不开。
 */
export function getCurrentFullPath() {
  const pages = getCurrentPages()
  const page = pages.at(-1) as
    | (Page.PageInstance & { options?: Record<string, unknown>, $page?: { options?: Record<string, unknown> } })
    | undefined
  const route = (page?.route || '').replace(/^\/+/, '')
  if (!route)
    return ''
  // 微信/支付宝把页面参数放在 page.options；部分平台（H5/App）挂在 page.$page.options
  const rawOptions = page?.options ?? page?.$page?.options ?? {}
  const options = rawOptions && typeof rawOptions === 'object' ? rawOptions : {}
  const query = Object.entries(options)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&')
  return `/${route}${query ? `?${query}` : ''}`
}
