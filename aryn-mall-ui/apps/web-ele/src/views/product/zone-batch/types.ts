/**
 * 专区批量操作共享类型（契约：docs/50-需求文档/2026-11-04-专区批量打标交互优化/04-实施方案与接口契约.md §2/§3.2）。
 *
 * 字段名严格对齐后端 DTO/VO，禁止在组件内另起别名，避免与 `#/api/product/goods-zone` 漂移。
 * 与后端 LocalDateTime 一致，时间字段统一使用 `YYYY-MM-DD HH:mm:ss` 字符串。
 */

/** 可写专区：会员专区 / 低价捡漏（出清） */
export type ZoneType = 'clearance' | 'member';

/**
 * 批量动作：
 * - ADD 加入（已在则更新规则）
 * - REMOVE 移出专区（清空 SPU 规则与全部 SKU 精调）
 * - NONE 不修改
 */
export type ZoneAction = 'ADD' | 'NONE' | 'REMOVE';

/** 单个商品在某个专区的变更类型（预检与执行结果共用） */
export type ZoneChangeType =
  'ADD' | 'INVALID' | 'NO_CHANGE' | 'REMOVE' | 'UPDATE';

/** 批量执行后的价格同步状态；FAILED 时前端不得展示「已同步」 */
export type ZoneSyncState = 'FAILED' | 'SKIPPED' | 'SUCCESS';

/** 专区商品生效状态（专区商品管理页表格） */
export type ZoneGoodsState =
  'EXPIRED' | 'INVERTED' | 'NO_RULE' | 'NORMAL' | 'OFF_SHELF';

/** 商品列表/候选筛选支持的专区筛选值（新增 not_* 供「添加商品」候选） */
export type ZoneFilterType = 'none' | 'not_clearance' | 'not_member' | ZoneType;

/** 预检/执行结果中的固定告警码（契约 §2.1） */
export type ZoneWarningCode =
  | 'EXPIRED'
  | 'INVERTED_PRICE'
  | 'MEMBER_FIXED_KEPT'
  | 'NO_RULE'
  | 'OFF_SHELF'
  | 'RECENT_SALES_INCREASE'
  | 'SKU_OVERRIDE_CLEARED'
  | 'SKU_OVERRIDE_PRESERVED'
  | 'ZONE_DEFAULT_RATE';

/** 专区操作告警/提示 */
export interface ZoneWarning {
  code: string | ZoneWarningCode;
  message: string;
}

/** 弹窗内每个专区的动作 + 规则草稿 */
export interface ZoneTagDraft {
  action: ZoneAction;
  clearanceDiscountRate?: null | number;
  clearanceExpireTime?: null | string;
  clearancePrice?: null | number;
  memberDiscountRate?: null | number;
  memberPriceFixed?: null | number;
  zone: ZoneType;
}

/** SPU 级专区标签与规则快照（预检 before / 单商品回显顶层字段） */
export interface ZoneRuleSnapshot {
  clearanceDiscountRate?: null | number;
  clearanceExpireTime?: null | string;
  clearancePrice?: null | number;
  /** 0 否 / 1 是 */
  clearanceTag?: null | number;
  memberDiscountRate?: null | number;
  memberPriceFixed?: null | number;
  memberPriceFloor?: null | number;
  /** 0 否 / 1 是 */
  memberTag?: null | number;
  /** 存在专区规则的 SKU 数（预检 before 提供；单商品详情新增） */
  skuOverrideCount?: null | number;
}

/** 单个专区在一次预检/执行中的变更描述 */
export interface ZoneTagChange {
  /** 变更前规则快照（执行结果会返回） */
  after?: Record<string, any> | ZoneRuleSnapshot;
  before?: Record<string, any> | ZoneRuleSnapshot;
  type: ZoneChangeType;
  warnings?: ZoneWarning[];
  zone: ZoneType;
}

/** 预检响应：逐商品条目（§2.1） */
export interface ZonePreviewItem {
  before: ZoneRuleSnapshot;
  changes: ZoneTagChange[];
  spuId: string;
  spuName?: string;
  /** 商品状态：1 上架 / 0 下架 */
  spuStatus?: null | string;
  warnings?: ZoneWarning[];
}

/** 预检响应：分专区汇总（§2.1） */
export interface ZonePreviewZone {
  existing: number;
  invalid: number;
  noChange: number;
  willAdd: number;
  willRemove: number;
  willUpdate: number;
  withSkuOverride: number;
  zone: ZoneType;
}

/** 预检响应：整体汇总（§2.1） */
export interface ZonePreviewSummary {
  add: number;
  invalid: number;
  noChange: number;
  remove: number;
  skuOverridesWillClear: number;
  total: number;
  update: number;
  warningCount: number;
}

/** 批量预检响应（§2.1） */
export interface ZonePreview {
  items: ZonePreviewItem[];
  summary: ZonePreviewSummary;
  zones: ZonePreviewZone[];
}

/** 批量执行响应：整体汇总（§2.2） */
export interface ZoneBatchSummary {
  added: number;
  changed: number;
  failed: number;
  noChange: number;
  removed: number;
  requested: number;
  success: number;
  updated: number;
}

/** 批量执行响应：分专区汇总（§2.2） */
export interface ZoneBatchZoneSummary {
  added: number;
  failed: number;
  noChange: number;
  removed: number;
  updated: number;
  zone: ZoneType;
}

/** 批量执行响应：价格同步状态（§2.2） */
export interface ZoneBatchSync {
  finishedAt?: null | string;
  message?: null | string;
  status: ZoneSyncState;
}

/** 批量执行响应：逐商品结果（§2.2） */
export interface ZoneBatchItem {
  changeType: ZoneChangeType;
  changes?: ZoneTagChange[];
  /** 契约 §2.2 错误码：SPU_NOT_FOUND / INVALID_RULE / NO_ORIGINAL_PRICE / EXPIRED_TIME / ZONE_TAG_LIMIT / SYNC_FAILED / NO_ACTION */
  errorCode?: null | string;
  message?: null | string;
  spuId: string;
  spuName?: string;
  success: boolean;
  warnings?: ZoneWarning[];
}

/** 批量执行响应（§2.2） */
export interface ZoneBatchResult {
  batchId: string;
  items: ZoneBatchItem[];
  summary: ZoneBatchSummary;
  sync?: null | ZoneBatchSync;
  zones?: ZoneBatchZoneSummary[];
}

/** 批次记录行（§2.7 `GET /tags/batch/page` records[]） */
export interface ZoneBatchHeader {
  batchId: string;
  changed: number;
  clearanceAction?: null | string;
  createTime?: null | string;
  failed: number;
  memberAction?: null | string;
  noChange: number;
  operatorName?: null | string;
  syncStatus?: null | ZoneSyncState;
  total: number;
  undoStatus?: null | string;
  undoTime?: null | string;
}

/** 批次详情（§2.7 `GET /tags/batch/{batchId}`） */
export interface ZoneBatchDetail extends ZoneBatchHeader {
  items: ZoneBatchItem[];
}

/** 撤销冲突项（§2.7） */
export interface ZoneUndoConflict {
  reason: string;
  spuId: string;
  spuName?: string;
}

/** 撤销批次响应（§2.7） */
export interface ZoneUndoResult {
  batchId: string;
  conflicts: ZoneUndoConflict[];
  restored: number;
  skipped: number;
}

/** SKU 级专区标签状态（§2.3 `skus[]`） */
export interface ZoneSkuTag {
  clearanceDiscountRate?: null | number;
  clearanceExpireTime?: null | string;
  clearancePrice?: null | number;
  /** 是否存在任一专区覆盖 */
  hasOverride?: boolean;
  /** null=继承 SPU 0 否 1 是 */
  isClearanceItem?: null | number;
  /** null=继承 SPU 0 否 1 是 */
  isMemberItem?: null | number;
  memberDiscountRate?: null | number;
  memberPriceFixed?: null | number;
  memberPriceFloor?: null | number;
  originalPrice?: null | number;
  salesPrice?: null | number;
  skuId: string;
  specsName?: string;
}

/** 单商品专区标签回显（§2.3 `GET /product/goodsspu/tags/{spuId}`，沿用并增强 `GoodsZoneTagVO`） */
export interface ZoneTagDetail {
  clearanceDiscountRate?: null | number;
  clearanceExpireTime?: null | string;
  clearancePrice?: null | number;
  /** 0 否 / 1 是 */
  isClearanceItem?: null | number;
  /** 0 否 / 1 是 */
  isMemberItem?: null | number;
  memberDiscountRate?: null | number;
  memberPriceFixed?: null | number;
  memberPriceFloor?: null | number;
  /** 存在专区规则的 SKU 数（§2.3 新增） */
  skuOverrideCount?: null | number;
  skus?: ZoneSkuTag[];
  spuId: string;
  spuName?: string;
  /** SPU 级告警（缺规则/倒挂/到期/下架等） */
  warnings?: ZoneWarning[];
}

/** 专区商品管理行（§2.6） */
export interface ZoneGoodsRow {
  brandName?: string;
  categoryName?: string;
  clearanceDiscountRate?: null | number;
  clearanceExpireTime?: null | string;
  clearancePrice?: null | number;
  /** SPU 口径生效价（含 SKU 精调时前端加标记） */
  effectivePrice?: null | number;
  id: string;
  isClearanceItem?: null | number;
  isMemberItem?: null | number;
  memberDiscountRate?: null | number;
  memberPriceFixed?: null | number;
  memberPriceFloor?: null | number;
  name?: string;
  originalPrice?: null | number;
  salesPrice?: null | number;
  skuOverrideCount?: null | number;
  /** NORMAL / NO_RULE / INVERTED / EXPIRED / OFF_SHELF */
  state?: string | ZoneGoodsState;
  stateLabel?: string;
  status?: string;
  stock?: null | number;
  subTitle?: string;
  spuUrls?: string[];
  zoneType?: string | ZoneType;
}

/** 专区商品分页响应（§2.6 `IPage<ZoneGoodsRowVO>`） */
export interface ZoneGoodsPage {
  current?: number;
  records: ZoneGoodsRow[];
  size?: number;
  total: number;
}

/** 专区商品分页查询参数（§2.6） */
export interface ZoneGoodsPageQuery {
  brandId?: string;
  categorySecondId?: string;
  current?: number;
  name?: string;
  size?: number;
  status?: string;
  zoneType: ZoneType;
}

/** 按筛选取 ID 请求（§2.5） */
export interface ZoneGoodsIdsQuery {
  brandId?: string;
  categorySecondId?: string;
  limit?: number;
  name?: string;
  status?: string;
  zoneType?: ZoneFilterType;
}

/** 按筛选取 ID 响应（§2.5） */
export interface ZoneGoodsIdsResult {
  ids: string[];
  total: number;
  truncated: boolean;
}

/** 批次记录分页查询参数（§2.7） */
export interface ZoneBatchPageQuery {
  current?: number;
  size?: number;
}

/** 批次记录分页响应（§2.7） */
export interface ZoneBatchPage {
  current?: number;
  records: ZoneBatchHeader[];
  size?: number;
  total: number;
}

/** 批次详情查询（§2.7） */

/** 单个专区的同步状态（§2.8） */
export interface ZoneSyncZone {
  activityId?: null | string;
  activityStatus?: null | string;
  enabled: boolean;
  lastError?: null | string;
  lastSyncStatus?: 'FAILED' | 'SUCCESS' | 'UNKNOWN' | null | string;
  lastSyncTime?: null | string;
  rulesSkuCount?: null | number;
  zone: ZoneType;
}

/** 专区同步状态（§2.8 `GET /promotion/zone/sync-status`） */
export interface ZoneSyncStatus {
  checkedAt?: null | string;
  zones: ZoneSyncZone[];
}

/** 批量预检请求（§2.1） */
export interface ZoneBatchPreviewRequest {
  clearanceAction?: ZoneAction;
  clearanceDiscountRate?: null | number;
  clearanceExpireTime?: null | string;
  clearancePrice?: null | number;
  memberAction?: ZoneAction;
  memberDiscountRate?: null | number;
  memberPriceFixed?: null | number;
  overwriteSkuRules?: boolean;
  spuIds: string[];
}

/**
 * 批量执行请求（§2.2，沿用 `GoodsZoneTagBatchDTO` 旧字段）。
 *
 * - `memberTag=true` 加入/更新；`false` 移出；缺省不修改
 * - `clearanceTag` 同理
 * - `overwriteSkuRules=true` 才会清空 SKU 精调
 */
export interface ZoneBatchPayload {
  clearanceDiscountRate?: null | number;
  clearanceExpireTime?: null | string;
  clearancePrice?: null | number;
  clearanceTag?: boolean;
  memberDiscountRate?: null | number;
  memberPriceFixed?: null | number;
  memberTag?: boolean;
  overwriteSkuRules?: boolean;
  spuIds: string[];
}

/** 单商品保存：SKU 条目（§2.4） */
export interface ZoneSkuTagSave {
  clearanceDiscountRate?: null | number;
  clearanceExpireTime?: null | string;
  clearancePrice?: null | number;
  /** 0 否 / 1 是；null=清空覆盖、恢复继承 */
  clearanceTag?: 0 | 1 | null;
  memberDiscountRate?: null | number;
  memberPriceFixed?: null | number;
  memberPriceFloor?: null | number;
  /** 0 否 / 1 是；null=清空覆盖、恢复继承 */
  memberTag?: 0 | 1 | null;
  skuId: string;
}

/** 单商品专区标签保存请求（§2.4） */
export interface ZoneTagSavePayload {
  /** 显式清空 SPU 出清规则（不影响标签） */
  clearClearanceRule?: boolean;
  /** 显式清空 SPU 会员规则（折扣/固定价/下限），不影响标签 */
  clearMemberRule?: boolean;
  clearanceDiscountRate?: null | number;
  clearanceExpireTime?: null | string;
  clearancePrice?: null | number;
  clearanceTag?: boolean;
  memberDiscountRate?: null | number;
  memberPriceFixed?: null | number;
  memberPriceFloor?: null | number;
  memberTag?: boolean;
  /** true：skus[] 精确覆盖（null=清空恢复继承），未出现的 SKU 不动 */
  replaceSkuRules?: boolean;
  skus?: ZoneSkuTagSave[];
}

/** 批量读取专区状态请求（§2.3） */
export interface ZoneTagQueryRequest {
  spuIds: string[];
  withSkus?: boolean;
}

/** 批量读取专区状态响应条目（§2.3） */
export interface ZoneTagQueryItem {
  before: ZoneRuleSnapshot;
  skus?: ZoneSkuTag[];
  spuId: string;
  spuName?: string;
  warnings?: ZoneWarning[];
}

/** 批量读取专区状态响应（§2.3，形如 `{ items: [...] }`） */
export interface ZoneTagQueryResult {
  items: ZoneTagQueryItem[];
}

/** ZoneSkuEditor 的 SKU 行编辑模型（逐行「继承/覆盖」开关 + 规则字段） */
export interface ZoneSkuEditRow {
  clearanceDiscountRate?: null | number;
  clearanceExpireTime?: null | string;
  clearanceOverride: boolean;
  clearancePrice?: null | number;
  /** 覆盖时：0 排除 / 1 参与 */
  clearanceTag: 0 | 1;
  memberDiscountRate?: null | number;
  memberOverride: boolean;
  memberPriceFixed?: null | number;
  memberPriceFloor?: null | number;
  /** 覆盖时：0 排除 / 1 参与 */
  memberTag: 0 | 1;
  originalPrice?: null | number;
  salesPrice?: null | number;
  skuId: string;
  specsName?: string;
}

/** ZoneBatchDialog `done` 事件载荷（§3.3） */
export interface ZoneBatchDoneEvent {
  batchIds: string[];
  failedCount: number;
  summary: null | ZoneBatchSummary;
}

/** 批次执行聚合结果（前端分片后合并，logic.ts 使用） */
export interface ZoneBatchAggregate {
  batchIds: string[];
  failedCount: number;
  items: ZoneBatchItem[];
  summary: ZoneBatchSummary;
  syncs: ZoneBatchSync[];
  zones: ZoneBatchZoneSummary[];
}
