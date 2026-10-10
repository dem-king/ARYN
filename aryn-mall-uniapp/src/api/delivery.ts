/**
 * 商城自配送相关API
 * 包含配送员端API和客户端API
 */
import { alovaInstance } from '@/api/core/instance'

// ===================== 类型定义 =====================

/** 出车单状态：1待配货 2配货中 3配送中 4已完成 */
export type TripStatus = '1' | '2' | '3' | '4'

/** 配送任务状态：1待派单 2待取货 3配货中 4待送达 5已送达 6已签收 7已取消 8异常 9待退回 */
export type DeliveryTaskStatus = '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9'

/** 取货明细项（页面视图模型，由任务明细 itemList 映射而来） */
export interface PickItem {
  id: string
  orderId: string
  orderNo: string
  spuName: string
  picUrl: string
  specsInfo: string
  quantity: number
  /** 商品分类名（空串表示后端未回填，展示归「未分类」） */
  categoryName: string
  /** 是否已确认取货：'0' 否 '1' 是 */
  picked: string
}

/** 取货清单按订单分组 */
export interface PickGroup {
  orderId: string
  orderNo: string
  items: PickItem[]
}

/** 司机工作台的一站：只带列表上判断所需的字段，不含取货明细 */
export interface DeliveryTripTaskBrief {
  id: string
  taskNo: string
  orderNo: string
  /** 趟车内送货顺序，1 起 */
  sortNo: number
  status: DeliveryTaskStatus
  recipientName: string
  recipientPhone: string
  recipientAddress: string
  vesselName?: string
  portName?: string
  berth?: string
  deliveryWindowStart?: string
  deliveryWindowEnd?: string
  arriveTime?: string
}

/** 工作台的一张趟次卡片（含该趟全部订单，按送货顺序） */
export interface DeliveryTripBrief {
  id: string
  tripNo: string
  status: TripStatus
  warehouseName?: string
  warehouseAddress?: string
  taskCount: number
  totalItemCount: number
  pickedItemCount: number
  arrivedTaskCount: number
  departTime?: string
  taskList: DeliveryTripTaskBrief[]
}

/** 工作台首页数据：一次请求拿齐统计与全部在途趟次 */
export interface DeliveryWorkbench {
  /** 待处理单数（待取货+配货中+待送达） */
  pendingTaskCount: number
  /** 今日已完成单数（今天送达或签收） */
  todayDoneCount: number
  trips: DeliveryTripBrief[]
}

/** 候选订单的商品摘要行 */
export interface DeliveryCandidateItem {
  spuName?: string
  specsInfo?: string
  quantity?: number
  picUrl?: string
}

/** 可拉进当前趟次的候选订单（配货页选单面板一行） */
export interface DeliveryCandidateOrder {
  orderId: string
  orderNo: string
  /** MINE 我的未完成任务；UNASSIGNED 未派送订单 */
  source: 'MINE' | 'UNASSIGNED'
  taskId?: string
  taskStatus?: string
  /** 已有任务所在出车单（挂在哪趟车上，便于司机判断） */
  tripId?: string
  tripNo?: string
  recipientName?: string
  recipientPhone?: string
  recipientAddress?: string
  vesselName?: string
  portName?: string
  berth?: string
  paymentPrice?: number
  deliveryWay?: string
  /** 3 货到付款：司机送达时要收款 */
  paymentType?: string
  payStatus?: string
  createTime?: string
  itemCount?: number
  items?: DeliveryCandidateItem[]
}

/** 候选来源 */
export type DeliveryCandidateSource = 'MINE' | 'UNASSIGNED'

/** 配送任务 */
export interface DeliveryTask {
  id: string
  taskNo: string
  tripId: string
  orderId: string
  orderNo: string
  /** 收货人姓名 */
  recipientName: string
  /** 收货人电话 */
  recipientPhone: string
  /** 收货地址（拼接后） */
  recipientAddress: string
  /** 纬度 */
  latitude?: number
  /** 经度 */
  longitude?: number
  /** 排序号 */
  sortNo: number
  status: DeliveryTaskStatus
  /** 取货时间 */
  pickUpTime?: string
  /** 送达时间 */
  arriveTime?: string
  /** 签收时间 */
  signTime?: string
  /** 配送船舶ID快照 */
  vesselId?: string
  /** 购买场景快照：1海员个人购买 2船供采购 */
  purchaseScene?: string
  /** 配送船舶名称快照 */
  vesselName?: string
  /** 港口名称快照 */
  portName?: string
  /** 泊位快照 */
  berth?: string
  /** 配送时间窗开始 */
  deliveryWindowStart?: string
  /** 配送时间窗结束 */
  deliveryWindowEnd?: string
  /** 订单明细（后端 DeliveryTaskItem：图片字段为 image、规格字段为 skuName） */
  itemList?: Array<{
    id: string
    spuName?: string
    skuName?: string
    image?: string
    quantity: number
    /** 商品分类名（读时聚合回填；分类≈供应商批次，配货清单按它分组） */
    categoryName?: string
    /** 是否已确认取货：'0' 否 '1' 是 */
    picked: string
  }>
}

/** 出车单 */
export interface DeliveryTrip {
  id: string
  tripNo: string
  /** 仓库名称（后端派生自仓库配置） */
  warehouseName?: string
  /** 仓库地址 */
  warehouseAddress?: string
  status: TripStatus
  /** 总件数（后端按任务明细汇总） */
  totalItemCount: number
  /** 已取件数（后端派生） */
  pickedItemCount: number
  /** 任务总数 */
  taskCount: number
  /** 已送达单数（后端派生） */
  arrivedTaskCount: number
  /** 配送员ID */
  staffId: string
  /** 出车时间 */
  departTime?: string
  /** 开始配货时间 */
  startLoadTime?: string
  /** 完成时间 */
  completeTime?: string
  /** 创建时间 */
  createTime: string
  /**
   * 本租户是否开放司机自助拉未派送订单（后端派生自 order_config）。
   * false 时隐藏「未派送订单」入口：该池子已关闭，点进去也是空的。
   */
  selfPullUnassignedAllowed?: boolean
  /** 任务列表 */
  taskList: DeliveryTask[]
}

/** 配送进度时间线节点 */
export interface DeliveryProgressNode {
  /** 节点状态码 */
  status: string
  /** 节点名称 */
  name: string
  /** 发生时间 */
  time?: string
  /** 是否已完成 */
  done: boolean
  /** 是否当前进行中 */
  active: boolean
}

/** 配送进度 */
export interface DeliveryProgress {
  /** 订单ID */
  orderId: string
  /** 出车单号 */
  tripNo?: string
  /** 配送员姓名 */
  staffName?: string
  /** 配送员电话 */
  staffPhone?: string
  /** 时间线节点列表 */
  nodes: DeliveryProgressNode[]
  /** 送达凭证图片URL（已送达/已签收时后端返回） */
  evidenceUrls?: string[]
}

/** 任务列表查询参数 */
export interface TaskPageParams {
  current?: number
  size?: number
  status?: string
  keyword?: string
}

/** 配送员登录参数 */
export interface DeliveryLoginParams {
  phone: string
  password: string
}

/** 配送工作台资格 */
export interface DeliveryEligibility {
  /** 是否允许展示配送工作台入口 */
  eligible: boolean
  /**
   * 资格状态：ACTIVE正常 UNBOUND未绑定 PERMISSION_MISSING权限未授予
   * ACCOUNT_DISABLED员工账号停用 STAFF_INVALID配送资料失效
   */
  status: 'ACTIVE' | 'UNBOUND' | 'PERMISSION_MISSING' | 'ACCOUNT_DISABLED' | 'STAFF_INVALID'
  /** 配送员姓名（仅展示用） */
  staffName?: string
  /** 待处理任务数 */
  pendingTaskCount?: number
}

/** 身份换取结果 */
export interface DeliveryExchangeResult {
  tokenValue: string
  /** 有效期（秒） */
  expiresIn?: number
  /** 最小配送员资料 */
  staff?: {
    id: string
    staffName?: string
  }
}

// ===================== 配送员端API =====================

// Keep the service segment in source URLs. Boot mode strips it to /boot, while
// Cloud mode routes it to aryn-order-biz through /mall-order.
const DELIVERY_API_BASE = '/mall-order/app/delivery'

/**
 * 配送员登录（独立入口）
 * 配送员使用手机号+密码登录，登录后进入配送工作台
 */
export function deliveryLogin(data: DeliveryLoginParams) {
  return alovaInstance.Post<any>('/auth/token/delivery-login', {
    phone: data.phone,
    password: data.password,
  }, {
    headers: {
      skipToken: true,
    },
  })
}

/** 查询当前登录配送员资料 */
export function getMyDeliveryStaff() {
  return alovaInstance.Get<any>(`${DELIVERY_API_BASE}/staff/me`)
}

// ===================== 配送工作台入口API（使用商城登录态） =====================

// 注意：以下接口使用商城 TOC token（不带 skipToken，URL 也不命中配送请求判定），
// Boot 模式改写为 /boot/delivery/**，Cloud 模式由网关 /auth/** 路由到认证服务。

/**
 * 查询配送工作台资格
 * 仅已登录商城用户可调用，服务端判断绑定与权限，前端只负责展示
 */
export function getDeliveryEligibility() {
  return alovaInstance.Get<DeliveryEligibility>('/auth/delivery/eligibility')
}

/**
 * 用商城登录态换取独立配送员 token（免重复登录）
 * 成功后保存 deliveryToken 与 deliveryStaffInfo 再进入工作台
 */
export function exchangeDeliveryIdentity() {
  return alovaInstance.Post<DeliveryExchangeResult>('/auth/delivery/exchange')
}

/**
 * 工作台首页数据：统计 + 在途趟次（每趟含按顺序排列的订单摘要）。
 *
 * 一个司机一辆车 = 一张在途出车单，所以正常只会有一张；历史遗留多张时也全部返回，
 * 由司机在配货页用「加单」手动拉合。
 */
export function getDeliveryWorkbench() {
  return alovaInstance.Get<DeliveryWorkbench>(`${DELIVERY_API_BASE}/trip/workbench`)
}

/**
 * 获取出车单详情（含所有任务和取货明细）
 */
export function getTripDetail(id: string) {
  return alovaInstance.Get<DeliveryTrip>(`${DELIVERY_API_BASE}/trip/${id}`)
}

/**
 * 开始配货（trip.status: 1 -> 2）
 */
export function startLoading(tripId: string) {
  return alovaInstance.Post<any>(`${DELIVERY_API_BASE}/trip/${tripId}/start-loading`)
}

/**
 * 获取取货清单（后端按任务返回，明细在 itemList，字段为 image/skuName；
 * 页面展示用 PickGroup 时由 trip-detail 本地归组映射）
 */
export function getPickList(tripId: string) {
  return alovaInstance.Get<DeliveryTask[]>(`${DELIVERY_API_BASE}/trip/${tripId}/pick-list`)
}

/**
 * 逐项确认取货
 */
export function pickItem(tripId: string, itemId: string) {
  return alovaInstance.Post<any>(`${DELIVERY_API_BASE}/trip/${tripId}/items/${itemId}/pick`)
}

/**
 * 批量确认/取消取货。
 *
 * 配货汇总行是「商品 + 规格」合并后的合计，一次勾选命中整趟车里该货的多条明细；
 * 逐条调用 `pickItem` 会把一次勾选放大成几十个请求。
 */
export function batchPickItems(tripId: string, itemIds: string[], picked = true) {
  return alovaInstance.Post<number>(`${DELIVERY_API_BASE}/trip/${tripId}/items/batch-pick`, {
    itemIds,
    picked,
  })
}

/**
 * 取消确认取货
 */
export function unpickItem(tripId: string, itemId: string) {
  return alovaInstance.Post<any>(`${DELIVERY_API_BASE}/trip/${tripId}/items/${itemId}/unpick`)
}

/**
 * 装货完毕出发（trip.status: 2 -> 3）
 */
export function departTrip(tripId: string) {
  return alovaInstance.Post<any>(`${DELIVERY_API_BASE}/trip/${tripId}/depart`)
}

/**
 * 调整送货顺序
 * @param tripId 出车单ID
 * @param taskSort 任务排序数组，形如 [{ taskId: 'xxx', sortNo: 1 }, ...]
 */
export function sortTripTasks(tripId: string, taskSort: Array<{ taskId: string, sortNo: number }>) {
  return alovaInstance.Put<any>(`${DELIVERY_API_BASE}/trip/${tripId}/sort`, {
    taskIds: taskSort.map(t => t.taskId),
  })
}

/**
 * 可拉进本趟的候选订单。
 *
 * @param tripId 出车单ID
 * @param source MINE 我的未完成任务；UNASSIGNED 未派送订单（受租户开关控制）
 * @param keyword 订单号/收货人/电话模糊匹配
 */
export function getPullCandidates(tripId: string, source?: DeliveryCandidateSource, keyword?: string) {
  const params: Record<string, string> = {}
  if (source)
    params.source = source
  if (keyword)
    params.keyword = keyword
  return alovaInstance.Get<DeliveryCandidateOrder[]>(`${DELIVERY_API_BASE}/trip/${tripId}/pull-candidates`, { params })
}

/**
 * 把订单拉进本趟（配货页加单入口共用：我的任务 / 未派送订单）。
 *
 * 订单商品、收货人、地址全部来自订单本身，司机不手填任何字段。
 * 未派送订单受租户开关约束，关闭时后端会拒绝拉入。
 */
export function pullOrdersIntoTrip(tripId: string, orderIds: string[]) {
  return alovaInstance.Post<number>(`${DELIVERY_API_BASE}/trip/${tripId}/pull-orders`, { orderIds })
}

/**
 * 送达某单（task.status: 4 -> 5），可携带凭证图片
 */
export function arriveTask(taskId: string, materialIds?: string[], remark?: string) {
  const config: any = { params: {} }
  if (materialIds && materialIds.length > 0) {
    config.params.materialIds = materialIds.join(',')
  }
  if (remark) {
    config.params.remark = remark
  }
  return alovaInstance.Post<any>(`${DELIVERY_API_BASE}/task/${taskId}/arrive`, {}, config)
}

/**
 * 上报异常
 */
export function reportException(taskId: string, reasonCode: string, reasonDesc: string, materialIds?: string[]) {
  const config: any = { params: { reasonCode, reasonDesc } }
  if (materialIds && materialIds.length > 0) {
    config.params.materialIds = materialIds.join(',')
  }
  return alovaInstance.Post<any>(`${DELIVERY_API_BASE}/task/${taskId}/exception`, {}, config)
}

/**
 * 查询任务凭证
 */
export function getTaskEvidence(taskId: string) {
  return alovaInstance.Get<any[]>(`${DELIVERY_API_BASE}/task/${taskId}/evidence`)
}

/**
 * 我的配送任务列表（分页）
 */
export function getMyTaskPage(params: TaskPageParams) {
  return alovaInstance.Get<any>(`${DELIVERY_API_BASE}/task/page`, {
    params,
  })
}

/**
 * 任务详情
 */
export function getTaskDetail(taskId: string) {
  return alovaInstance.Get<DeliveryTask>(`${DELIVERY_API_BASE}/task/${taskId}`)
}

// ===================== 客户端API =====================

/**
 * 获取订单配送进度（客户端使用）
 * 当订单 deliveryWay=3（商城配送）时调用
 */
export function getOrderDeliveryProgress(orderId: string) {
  return alovaInstance.Get<DeliveryProgress>(`/mall-order/app/order/${orderId}/delivery-progress`)
}

// ===================== 状态辅助函数 =====================

/** 出车单状态映射 */
export const TRIP_STATUS_MAP: Record<TripStatus, { name: string, color: string }> = {
  1: { name: '待配货', color: '#fa9500' },
  2: { name: '配货中', color: '#0084ff' },
  3: { name: '配送中', color: '#07c160' },
  4: { name: '已完成', color: '#909399' },
}

/** 配送任务状态映射 */
export const TASK_STATUS_MAP: Record<DeliveryTaskStatus, { name: string, color: string }> = {
  1: { name: '待派单', color: '#909399' },
  2: { name: '待取货', color: '#fa9500' },
  3: { name: '配货中', color: '#0084ff' },
  4: { name: '待送达', color: '#e6a23c' },
  5: { name: '已送达', color: '#07c160' },
  6: { name: '已签收', color: '#909399' },
  7: { name: '已取消', color: '#c0c4cc' },
  8: { name: '异常', color: '#f56c6c' },
  9: { name: '待退回', color: '#f56c6c' },
}

/** 获取出车单状态名称 */
export function getTripStatusName(status: TripStatus): string {
  return TRIP_STATUS_MAP[status]?.name ?? '未知'
}

/** 获取配送任务状态名称 */
export function getTaskStatusName(status: DeliveryTaskStatus): string {
  return TASK_STATUS_MAP[status]?.name ?? '未知'
}

/** 获取出车单状态颜色 */
export function getTripStatusColor(status: TripStatus): string {
  return TRIP_STATUS_MAP[status]?.color ?? '#909399'
}

/** 获取配送任务状态颜色 */
export function getTaskStatusColor(status: DeliveryTaskStatus): string {
  return TASK_STATUS_MAP[status]?.color ?? '#909399'
}

// ===================== 微信订阅消息 =====================

/** 绑定微信 openid */
export function bindWechatOpenid(appId: string, openid: string) {
  return alovaInstance.Post('/upms/app/wechat/binding/bind', { appId, openid }, {
    headers: { authScope: 'delivery' },
  })
}

/** 通过微信 login code 绑定 */
export function bindWechatByCode(appId: string, code: string) {
  return alovaInstance.Post('/upms/app/wechat/binding/bind-by-code', { appId, code }, {
    headers: { authScope: 'delivery' },
  })
}

/** 查询当前用户微信绑定状态 */
export function getWechatBindStatus() {
  return alovaInstance.Post<boolean>('/upms/app/wechat/binding/status', undefined, {
    headers: { authScope: 'delivery' },
  })
}

/**
 * 请求微信订阅消息授权
 * @param templateIds 需要授权的模板ID列表
 * @returns 授权结果，key=模板ID value='accept'|'reject'|'ban'
 */
export function requestSubscribeMessage(templateIds: string[]): Promise<Record<string, string>> {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    wx.requestSubscribeMessage({
      tmplIds: templateIds,
      success: res => resolve(res as Record<string, string>),
      fail: err => reject(err),
    })
    // #endif
    // #ifndef MP-WEIXIN
    resolve({})
    // #endif
  })
}

/**
 * 获取微信登录 code
 */
export function getWxLoginCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    wx.login({
      success: res => resolve(res.code),
      fail: err => reject(err),
    })
    // #endif
    // #ifndef MP-WEIXIN
    resolve('')
    // #endif
  })
}
