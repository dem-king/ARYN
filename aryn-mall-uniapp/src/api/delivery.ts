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

/** 取货明细项 */
export interface PickItem {
  id: string
  orderId: string
  orderNo: string
  skuId: string
  spuId: string
  spuName: string
  picUrl: string
  specsInfo: string
  quantity: number
  /** 是否已确认取货：'0' 否 '1' 是 */
  picked: string
}

/** 取货清单按订单分组 */
export interface PickGroup {
  orderId: string
  orderNo: string
  items: PickItem[]
}

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
  /** 订单明细 */
  itemList?: Array<{
    id: string
    picUrl: string
    spuName: string
    specsInfo: string
    quantity: number
  }>
}

/** 出车单 */
export interface DeliveryTrip {
  id: string
  tripNo: string
  /** 仓库ID */
  warehouseId: string
  /** 仓库名称 */
  warehouseName: string
  /** 仓库地址 */
  warehouseAddress: string
  /** 仓库纬度 */
  warehouseLatitude?: number
  /** 仓库经度 */
  warehouseLongitude?: number
  status: TripStatus
  /** 总件数 */
  totalItemCount: number
  /** 已取件数 */
  pickedItemCount: number
  /** 任务总数 */
  taskCount: number
  /** 已送达单数 */
  arrivedTaskCount: number
  /** 配送员ID */
  staffId: string
  /** 配送员姓名 */
  staffName: string
  /** 出车时间 */
  departTime?: string
  /** 开始配货时间 */
  startLoadTime?: string
  /** 完成时间 */
  completeTime?: string
  /** 创建时间 */
  createTime: string
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

// ===================== 配送员端API =====================

const DELIVERY_API_BASE = '/app/delivery'

/**
 * 配送员登录（独立入口）
 * 配送员使用手机号+密码登录，登录后进入配送工作台
 */
export function deliveryLogin(data: DeliveryLoginParams) {
  return alovaInstance.Post<any>(`${DELIVERY_API_BASE}/staff/login`, data, {
    headers: {
      skipToken: true,
    },
  })
}

/**
 * 获取当前进行中的出车单
 * 配送员工作台首页调用，返回当前未完成的出车单
 */
export function getActiveTrip() {
  return alovaInstance.Get<DeliveryTrip | null>(`${DELIVERY_API_BASE}/trip/active`)
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
 * 获取取货清单（按订单分组）
 */
export function getPickList(tripId: string) {
  return alovaInstance.Get<PickGroup[]>(`${DELIVERY_API_BASE}/trip/${tripId}/pick-list`)
}

/**
 * 逐项确认取货
 */
export function pickItem(tripId: string, itemId: string) {
  return alovaInstance.Post<any>(`${DELIVERY_API_BASE}/trip/${tripId}/items/${itemId}/pick`)
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
  return alovaInstance.Get<DeliveryProgress>(`/app/order/${orderId}/delivery-progress`)
}

// ===================== 状态辅助函数 =====================

/** 出车单状态映射 */
export const TRIP_STATUS_MAP: Record<TripStatus, { name: string, color: string }> = {
  '1': { name: '待配货', color: '#fa9500' },
  '2': { name: '配货中', color: '#0084ff' },
  '3': { name: '配送中', color: '#07c160' },
  '4': { name: '已完成', color: '#909399' },
}

/** 配送任务状态映射 */
export const TASK_STATUS_MAP: Record<DeliveryTaskStatus, { name: string, color: string }> = {
  '1': { name: '待派单', color: '#909399' },
  '2': { name: '待取货', color: '#fa9500' },
  '3': { name: '配货中', color: '#0084ff' },
  '4': { name: '待送达', color: '#e6a23c' },
  '5': { name: '已送达', color: '#07c160' },
  '6': { name: '已签收', color: '#909399' },
  '7': { name: '已取消', color: '#c0c4cc' },
  '8': { name: '异常', color: '#f56c6c' },
  '9': { name: '待退回', color: '#f56c6c' },
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
  return alovaInstance.Post('/app/wechat/binding/bind', { appId, openid })
}

/** 通过微信 login code 绑定 */
export function bindWechatByCode(appId: string, code: string) {
  return alovaInstance.Post('/app/wechat/binding/bind-by-code', { appId, code })
}

/** 查询当前用户微信绑定状态 */
export function getWechatBindStatus() {
  return alovaInstance.Post<boolean>('/app/wechat/binding/status')
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
      success: (res) => resolve(res as Record<string, string>),
      fail: (err) => reject(err),
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
      success: (res) => resolve(res.code),
      fail: (err) => reject(err),
    })
    // #endif
    // #ifndef MP-WEIXIN
    resolve('')
    // #endif
  })
}
