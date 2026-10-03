/**
 * 配送目的地与「交给手机导航软件」的口径。
 *
 * 司机端不在小程序里做导航，点「导航」只负责把目的地递出去：
 *
 * - **有经纬度**：走 `uni.openLocation` 打开微信内置地图，司机点图上的「导航」
 *   由系统拉起高德/腾讯/苹果地图。这是微信唯一允许的中转，小程序不能用
 *   URL scheme 直接调起第三方 App。
 * - **只有文本地址（当前线上订单全是这种）**：没有任何接口能把地址换成坐标，
 *   所以只能复制到剪贴板，提示司机到导航软件里粘贴。
 *
 * 目的地口径：优先收货地址；船供单的收货地址常常为空，回落到「港口 + 泊位」。
 * 泊位单独出现时（如「321」）没有被搜索的价值，必须有港口名才拼接。
 */

export interface DeliveryDestination {
  /** 收货完整地址 */
  recipientAddress?: null | string
  /** 配送港口名称快照 */
  portName?: null | string
  /** 泊位快照 */
  berth?: null | string
}

export interface DeliveryNavigationTarget extends DeliveryDestination {
  latitude?: null | number
  longitude?: null | number
}

function clean(value?: null | string): string {
  return typeof value === 'string' ? value.trim() : ''
}

/**
 * 可交给导航软件的目的地文本；没有可用目的地时返回空串。
 *
 * 调用方据此决定「复制/打开地图」还是提示「暂无收货地址」——空串一律按无目的地处理，
 * 不能把空白地址复制进剪贴板（粘贴出来是空的，司机只会以为复制失败）。
 */
export function resolveDeliveryDestination(target?: DeliveryDestination | null): string {
  if (!target)
    return ''

  const address = clean(target.recipientAddress)
  if (address)
    return address

  const port = clean(target.portName)
  if (!port)
    return ''

  const berth = clean(target.berth)
  return berth ? `${port} ${berth}` : port
}

/**
 * 可用的经纬度；缺失或为 `(0,0)` 哨兵值时返回 null。
 *
 * 后端 `delivery_task` 目前没有坐标列，字段恒为 undefined 或 null；
 * 逐个判空而不是笼统的真值判断，避免经纬度 0 被当成缺失、也避免 NaN 传进
 * `openLocation` 触发一个司机看不懂的失败弹窗。
 */
export function resolveMapCoordinates(
  target?: DeliveryNavigationTarget | null,
): null | { latitude: number, longitude: number } {
  const latitude = target?.latitude
  const longitude = target?.longitude
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude))
    return null
  // (0,0) 是「没填」的常见哨兵，落到那里只会导航到大西洋
  if (latitude === 0 && longitude === 0)
    return null
  return { latitude: latitude as number, longitude: longitude as number }
}

/**
 * 把目的地交给手机导航软件。
 *
 * `notify` 由页面传入（司机端的全局 Toast），只用于「没有地址」「复制失败」这类
 * 异常路径；正常路径不额外弹 Toast —— `setClipboardData` 自带系统级「内容已复制」提示，
 * 再叠一个只会互相覆盖。
 */
export function openDeliveryNavigation(
  target: DeliveryNavigationTarget | null | undefined,
  notify: (message: string) => void,
): void {
  const destination = resolveDeliveryDestination(target)
  if (!destination) {
    notify('暂无收货地址')
    return
  }

  const coordinates = resolveMapCoordinates(target)
  if (coordinates) {
    uni.openLocation({
      ...coordinates,
      name: destination,
      scale: 18,
      fail: () => notify('打开地图失败'),
    })
    return
  }

  uni.setClipboardData({
    data: destination,
    success: () => {
      uni.showModal({
        title: '地址已复制',
        content: '打开手机上的导航软件，把地址粘贴到搜索框即可开始导航',
        showCancel: false,
        confirmText: '知道了',
      })
    },
    fail: () => notify('复制地址失败'),
  })
}
