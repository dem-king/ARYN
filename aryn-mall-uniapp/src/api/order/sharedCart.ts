/**
 * 共享购物车 API（同船多海员分别加购，授权人员统一确认提交整船订单）。
 *
 * 后端契约（AppSharedCartController，C 端网关域 mall-order）：
 * - 创建后绑定船舶与靠港计划，不可变更；已提交/已关闭不可编辑
 * - 成员只能维护自己的明细；发起人可邀请成员、关闭购物车
 * - 确认人（默认发起人）可调整核定数量后统一提交，按购物车维度幂等
 */
import { alovaInstance } from '@/api/core/instance'

export interface SharedCart {
  id: string
  cartNo: string
  vesselId: string
  vesselName?: string
  vesselCallId: string
  portCode?: string
  portName?: string
  berth?: string
  deliveryWindowStart?: string
  deliveryWindowEnd?: string
  /**
   * 该车的靠港是否仍可作为新订单的配送计划。
   *
   * 展示字段（vesselName/portName）取自展示快照，靠港结束后**照样有值**，
   * 因此不能用「船名为空」反推靠港已失效（2026-10-09 修正：历史单的船名由此恢复）。
   * false 表示提交时会被服务端顺延到本船最新可用靠港；undefined 表示未取到快照。
   */
  callOrderable?: boolean
  ownerUserId: string
  confirmerUserId: string
  /** 1草稿 2收集中 3待确认 4已提交 5已关闭 6已完成（送达归档） */
  status: string
  expiresAt?: string
  submitOrderId?: string
  submittedTime?: string
  remark?: string
  createTime?: string
  /** 查看者角色：1发起人 2成员 3确认人 */
  viewerRole?: string
  viewerCanEdit?: boolean
  viewerCanConfirm?: boolean
  viewerIsOwner?: boolean
  itemCount?: number
  memberCount?: number
  /** 仅创建接口返回：命中了该船已有的收集中购物车（被复用的实例，非新建） */
  adoptedExisting?: boolean
  /** 分享令牌（发起人可生成，随购物车过期失效） */
  shareToken?: string
}

export interface SharedCartMember {
  id: string
  cartId: string
  userId: string
  /** 1发起人 2成员 3确认人 */
  memberRole: string
  /** 成员展示姓名（加入时填写，配送贴标签用） */
  displayName?: string
  canEdit: string
  canConfirm: string
  joinedTime?: string
}

/**
 * 购物车明细（服务端下发 SharedCartItemVO）。
 *
 * 商品名/规格/图片/单价由服务端经商品域批量补齐 —— 明细表只存 SPU/SKU ID，
 * 前端再单独查一次商品会多一个请求且两边口径可能不一致。
 *
 * 金额口径：收集阶段没有核定数量，行金额 = 单价 × 申请数量（提交后改用核定数量）。
 * `unitPrice` 为 null 表示商品域查不到该 SKU（下架/已删除），界面显示「待核价」，
 * 不能当成 0 元。
 */
export interface SharedCartItem {
  id: string
  cartId: string
  userId: string
  /** 归属人姓名快照（接龙代报场景；空表示归属就是 userId 本人） */
  attributedName?: string | null
  spuId: string
  skuId: string
  /** 商品名称；商品域查询失败时为 null */
  spuName?: string
  /** 规格信息 */
  specsInfo?: string
  /** 商品图片（SKU 图 → SPU 主图回落） */
  picUrl?: string
  /** 成员申请数量（采购单位）：我报了多少 */
  requestedQuantity: number
  /**
   * 确认人核定数量：NULL 未核定；> 0 改定的采购量；0 表示「本次不采」。
   *
   * 收集阶段恒为 null（只在提交那一刻写入），因此不能拿它当"已买多少"。
   */
  approvedQuantity?: number | null
  /** SKU 当前售价（未含促销）；null = 取不到价，界面显示「待核价」 */
  unitPrice?: number | null
  memberRemark?: string
  /** 1待确认 2已确认 3已移除 */
  status: string
  createTime?: string
}

/** 整单预估：仅项数与金额，实付以结算页为准 */
export interface SharedCartEstimate {
  totalItems: number
  /** SKU 当前售价 × 申请数量；未含促销，仅量级参考 */
  totalAmount: number
}

export interface SharedCartCreatePayload {
  vesselId: string
  vesselCallId: string
  expiresAt?: string
  remark?: string
}

export interface SharedCartItemPayload {
  spuId?: string
  skuId: string
  requestedQuantity: number
  memberRemark?: string
}

export interface SharedCartConfirmPayload {
  /** 核定数量调整；quantity=0 表示本次不采该行（不进订单） */
  approvedQuantities?: Array<{ itemId: string, quantity: number }>
  recipientName?: string
  recipientPhone?: string
  agentName?: string
  agentPhone?: string
  remark?: string
  /** 支付类型：''=在线支付（默认）；'3'=货到付款（内部配送专用，下单即进待发货） */
  paymentType?: string
}

/** 首页「今日补给单」卡片摘要（服务端一次聚合，避免首屏串行多请求） */
export interface SharedCartSummary {
  cart: SharedCart | null
  itemCount: number
  memberCount: number
  /** 预估金额（SKU 当前售价 × 申请数量；未含促销，实付以结算页为准） */
  totalAmount: number
  previewItems: Array<{
    itemId: string
    spuId: string
    skuId: string
    spuName?: string
    specsInfo?: string
    quantity: number
    picUrl?: string
    amount: number
  }>
  previewTruncated: boolean
}

const BASE = '/mall-order/app/shared-cart'

/** 我参与的全部共享购物车（我发起或我被邀请），按创建时间倒序 */
export function getMySharedCarts() {
  return alovaInstance.Get<SharedCart[]>(`${BASE}/my`)
}

/**
 * 当前进行中的共享购物车摘要（首页补给单卡片）。
 *
 * 无进行中的购物车时 cart 为 null（首页渲染空态引导），不抛异常。
 */
export function getActiveSharedCartSummary(vesselCallId?: string) {
  return alovaInstance.Get<SharedCartSummary>(`${BASE}/active-summary`, {
    params: { vesselCallId },
  })
}

/** 购物车详情（仅成员可见） */
export function getSharedCart(id: string) {
  return alovaInstance.Get<SharedCart>(`${BASE}/${id}`)
}

/** 成员列表 */
export function getSharedCartMembers(id: string) {
  return alovaInstance.Get<SharedCartMember[]>(`${BASE}/${id}/members`)
}

/** 有效明细列表 */
export function getSharedCartItems(id: string) {
  return alovaInstance.Get<SharedCartItem[]>(`${BASE}/${id}/items`)
}

/** 创建共享购物车（发起人） */
export function createSharedCart(data: SharedCartCreatePayload) {
  return alovaInstance.Post<SharedCart>(BASE, data)
}

/** 邀请成员（发起人）；memberRole: 2成员 3确认人 */
export function inviteSharedCartMember(id: string, memberUserId: string, memberRole = '2') {
  return alovaInstance.Post<SharedCartMember>(`${BASE}/${id}/members`, undefined, {
    params: { memberUserId, memberRole },
  })
}

/** 生成/复用分享令牌（发起人），用于转发到微信群 */
export function shareSharedCart(id: string) {
  return alovaInstance.Post<string>(`${BASE}/${id}/share`)
}

/** 凭分享令牌自助加入（点击群卡片后调用，服务端自动补建船舶成员关系） */
export function joinSharedCartByToken(token: string) {
  return alovaInstance.Post<string>(`${BASE}/join`, undefined, {
    params: { token },
  })
}

/** 设置我的展示姓名（加入时填写一次，配送贴标签用） */
export function setMyDisplayName(id: string, displayName: string) {
  return alovaInstance.Put<SharedCartMember>(`${BASE}/${id}/members/me/name`, { displayName })
}

/** 添加自己的明细 */
export function addSharedCartItem(id: string, data: SharedCartItemPayload) {
  return alovaInstance.Post<SharedCartItem>(`${BASE}/${id}/items`, data)
}

/** 修改自己的明细 */
export function updateSharedCartItem(id: string, itemId: string, data: SharedCartItemPayload) {
  return alovaInstance.Put<SharedCartItem>(`${BASE}/${id}/items/${itemId}`, data)
}

/** 移除自己的明细 */
export function removeSharedCartItem(id: string, itemId: string) {
  return alovaInstance.Delete<void>(`${BASE}/${id}/items/${itemId}`)
}

/** 历史补给单复用入参（把历史明细复制到当前靠港计划下的购物车） */
export interface SharedCartReusePayload {
  /** 船舶ID（必须与源单一致；跨船复用无业务意义） */
  vesselId: string
  /** 本次采购的靠港计划ID（取当前上下文，不是源单那个历史靠港） */
  vesselCallId: string
  /** 备注；不传则沿用源单备注 */
  remark?: string
}

/** 历史补给单复用结果 */
export interface SharedCartReuseResult {
  cartId: string
  cartNo?: string
  /** 是否并入该船已有的进行中购物车（true 表示不是新建的） */
  adoptedExisting?: boolean
  /** 成功复用的明细项数（按 SKU 合并后） */
  reusedCount: number
  /** 跳过的项数 */
  skippedCount: number
  skipped: Array<{ skuId: string, reason: string }>
}

/* -------------------------------------------------------------------------
 * 补给单 Excel 导入（C2）
 * ---------------------------------------------------------------------- */

/** 导入行结果类型（与后端 SharedCartImportRow 常量一一对应） */
export type SharedCartImportResultType
  = | 'OK'
    | 'UNMATCHED'
    | 'SPEC_CHANGED'
    | 'OVER_STOCK'
    | 'INVALID_QTY'
    | 'OFF_SHELF'
    /**
     * 未填数量：目录模板里没填数量的行 = 本次不采购，**不是错误**。
     *
     * 与 INVALID_QTY 分开的原因：模板按分类铺满在售商品，客户只填要买的几行，
     * 若把空数量当异常，273 行的模板会显示 268 行红色报错。
     */
    | 'NOT_FILLED'

/** 用户对单行的处置动作 */
export type SharedCartImportAction
  = | 'ACCEPT_SPEC'
    | 'ADJUST_QTY'
    | 'REPLACE_SKU'
    | 'SKIP'

/** 匹配候选（多规格歧义时由服务端给出） */
export interface SharedCartImportCandidate {
  spuId?: string
  skuId?: string
  name?: string
  spec?: string
  purchaseUnit?: string
  salesPrice?: number
  stock?: number
  skuStatus?: string
  spuStatus?: string
}

/** 导入报告行 */
export interface SharedCartImportRow {
  rowNo: number
  /** 接龙人名原文（接龙导入来源；Excel 导入为 null） */
  personName?: string
  rawCode?: string
  rawName?: string
  rawSpec?: string
  rawQuantity?: number
  rawUnit?: string
  rawRemark?: string
  matchType?: 'CODE' | 'NAME' | 'AMBIGUOUS' | 'NONE'
  matchedSkuId?: string
  matchedName?: string
  matchedSpec?: string
  matchedUnit?: string
  matchedPrice?: number
  matchedStock?: number
  /** 本次采购数量 */
  quantity?: number
  resultType: SharedCartImportResultType
  resultMessage?: string
  resolvedAction?: SharedCartImportAction
  resolvedSkuId?: string
  resolvedQuantity?: number
  /** 服务端建议数量（超库存调减 / 数量异常就近取整） */
  suggestedQuantity?: number
  candidates?: SharedCartImportCandidate[]
}

/** 导入报告（任务 + 行明细；列表接口不含 rows） */
export interface SharedCartImport {
  importId: string
  cartId: string
  fileName?: string
  /** 1待确认 2已并入 3已取消 */
  status: string
  totalRows: number
  matchedRows: number
  unmatchedRows: number
  specChangedRows: number
  overStockRows: number
  invalidRows: number
  offShelfRows: number
  /** 未填数量行数（客户本次不采购，非错误） */
  notFilledRows?: number
  importedRows?: number
  skippedRows?: number
  createTime?: string
  confirmedTime?: string
  rows?: SharedCartImportRow[]
}

/** 单行处置入参：客户端只传动作，不传行内容 */
export interface SharedCartImportActionPayload {
  rowNo: number
  action: SharedCartImportAction
  /** action=REPLACE_SKU 时必填 */
  skuId?: string
  /** action=ADJUST_QTY 时必填 */
  quantity?: number
  /** 修正后的归属人名（接龙导入；空表示沿用解析结果） */
  personName?: string
}

/** 取回导入报告（「稍后处理」后回来继续） */
export function getSharedCartImport(id: string, importId: string) {
  return alovaInstance.Get<SharedCartImport>(`${BASE}/${id}/imports/${importId}`)
}

/** 导入记录列表（不带行明细） */
export function listSharedCartImports(id: string) {
  return alovaInstance.Get<SharedCartImport[]>(`${BASE}/${id}/imports`)
}

/**
 * 确认并入补给单（幂等）。
 *
 * rows 只包含需要显式处置的行；匹配成功的行由服务端按原匹配结果并入。
 */
export function confirmSharedCartImport(
  id: string,
  importId: string,
  rows: SharedCartImportActionPayload[],
) {
  return alovaInstance.Post<SharedCartImport>(`${BASE}/${id}/imports/${importId}/confirm`, { rows })
}

/**
 * 粘贴微信群接龙，解析成「人×商品×数量」并返回导入报告（确认人/发起人）。
 *
 * 报告与确认沿用 Excel 导入的同两个接口（imports/{importId}、confirm），
 * 行上多带 personName（接龙人名原文）。
 */
export function previewChainImport(id: string, text: string) {
  return alovaInstance.Post<SharedCartImport>(`${BASE}/${id}/chain-import/preview`, { text })
}

/**
 * 历史补给单一键复用：把历史购物车的明细复制到当前靠港计划下的购物车。
 *
 * 复用只搬清单，不下单、不锁价、不校验库存 —— 用户确认后仍走正常的
 * 「改数量 → 提交整船订单」流程（与订单「再来一单」同口径）。
 */
export function reuseSharedCartFromHistory(id: string, data: SharedCartReusePayload) {
  return alovaInstance.Post<SharedCartReuseResult>(`${BASE}/${id}/reuse`, data)
}

/** 确认人统一提交，生成整船订单（幂等，返回订单ID） */
export function confirmSharedCart(id: string, data: SharedCartConfirmPayload) {
  return alovaInstance.Post<string>(`${BASE}/${id}/confirm`, data)
}

/** 发起人关闭购物车 */
export function closeSharedCart(id: string) {
  return alovaInstance.Post<void>(`${BASE}/${id}/close`)
}
