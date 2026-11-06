import { alovaInstance } from '@/api/core/instance'

/**
 * 销售工作台（公司销售）小程序接口。
 *
 * 与普通分销（`api/distribution/entity.ts`）相互独立：销售业绩单独记账，
 * 提成随基本工资发放，小程序内**只读、不提供提现**。
 *
 * 路径约定（双模式）：
 * - 业绩/客户/汇总在 promotion 域，首段 `/promotion`；
 * - 推广码在 user 域（只有 user-biz 持有 `WxMaService`），首段 `/mall-user`。
 * 首段微服务域写死，boot 模式的前缀由 `api/core/instance.ts` 的请求中间件
 * 统一改写；页面层禁止手拼 boot 前缀。
 */

export const SALES_WORKBENCH_PATH = '/sub-pages/user/sales/index'

/**
 * 销售工作台汇总（`AppSalesWorkbenchVO`）
 */
export interface SalesWorkbench {
  /** 当前会员是否已登记为在职销售 */
  isSales: boolean
  staffId?: string
  staffName?: string
  deptName?: string
  /** 名下客户数 */
  customerCount?: number
  /** 本月成交单数 */
  monthOrderCount?: number
  /** 本月成交额 */
  monthTradeAmount?: number
  /** 本月预估提成 */
  monthCommission?: number
  /** 累计已产生提成 */
  totalCommission?: number
  /** 推广码图片（data URI，可为空） */
  qrcodeUrl?: string
}

/**
 * 我的客户（`AppSalesCustomerVO`）
 */
export interface SalesCustomer {
  customerUserId: string
  /** 服务端脱敏后的昵称（可能为空） */
  nickname?: string
  avatar?: string
  /** SHARE / QRCODE / ADMIN / TRANSFER / CLAIM */
  bindSource?: string
  bindTime?: string
}

/**
 * 业绩明细（`AppSalesOrderVO`）
 */
export interface SalesOrderRecord {
  id: string
  orderNo?: string
  payTime?: string
  /** 提成基数（实付金额，不含运费） */
  commissionBase?: number
  /** 提成金额 */
  commissionAmount?: number
  /** 0 待确认 1 可发放 2 已发放 3 已全额退款 */
  status: string
}

/**
 * 小程序推广码（`SalesQrcodeVO`）
 *
 * `qrcodeUrl` 为 data URI（`data:image/png;base64,...`）时可直接给 `<image>`：
 * 小程序 `<image>` 支持 data URI，保存到相册才需要先写临时文件。
 */
export interface SalesQrcode {
  qrcodeUrl?: string
  /** 小程序页面路径 */
  page?: string
  /** 小程序码 scene（`sid=<memberUserId>`） */
  scene?: string
}

/**
 * 通用分页结果
 */
export interface SalesPageResult<T = any> {
  records: T[]
  total: number
  current: number
  size: number
}

/**
 * 销售分页查询参数
 */
export interface SalesPageQuery {
  current?: number
  size?: number
}

/**
 * 销售工作台汇总
 */
export function getSalesWorkbench() {
  return alovaInstance.Get<SalesWorkbench>('/promotion/app/sales/workbench')
}

/**
 * 我的客户分页
 */
export function getSalesCustomerPage(params: SalesPageQuery = {}) {
  return alovaInstance.Get<SalesPageResult<SalesCustomer>>(
    '/promotion/app/sales/customer/page',
    { params },
  )
}

/**
 * 我的业绩明细分页
 */
export function getSalesOrderPage(params: SalesPageQuery = {}) {
  return alovaInstance.Get<SalesPageResult<SalesOrderRecord>>(
    '/promotion/app/sales/order/page',
    { params },
  )
}

/**
 * 获取本人推广小程序码
 *
 * 仅在职销售本人可调；服务端 Redis 缓存 7 天。
 */
export function getSalesQrcode() {
  return alovaInstance.Get<SalesQrcode>('/mall-user/app/sales/qrcode')
}
