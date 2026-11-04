/**
 * 「专区商品管理」页纯逻辑。
 *
 * 这里只放与 Vue 组件解耦的纯函数：筛选 payload、全选筛选结果分片、
 * 规则/生效状态文案、批量结果与撤销结果聚合、格式化。
 *
 * 设计约束：
 * - 不 import Vue / Element Plus / @/api，保证 vitest 可以零副作用直接测试；
 * - 入参使用结构化最小类型（字段均可选），共享类型 ZoneGoodsRow 等可直接传入；
 * - 页面内所有易错判断集中在此，组件层只负责展示与交互。
 */

/** 页面只管理两个专区，与后端 zoneType=member|clearance 对齐 */
export type ZoneKey = 'clearance' | 'member';

/** /product/goodsspu/ids 支持的 zoneType（含添加商品候选口径） */
export type ZoneQueryType =
  'clearance' | 'member' | 'none' | 'not_clearance' | 'not_member';

/** 页面 tab 展示名 */
export const ZONE_LABEL: Record<ZoneKey, string> = {
  clearance: '低价捡漏',
  member: '会员专区',
};

/** 专区 tab 的副标题/说明（顶部提示用） */
export const ZONE_DESCRIPTION: Record<ZoneKey, string> = {
  clearance:
    '低价捡漏按「出清价」或「原价 × 出清折扣率」计算 C 端价格；截止时间到期、商品下架、规则倒挂时不生效。',
  member:
    '会员价 = min(售价 × min(商品折扣率, 等级折扣率), 会员固定价)，不低于成本兜底下限；专区默认折扣率在商品未配规则时生效。',
};

/** 全选筛选结果时单次取 ID 的默认上限（契约：服务端 limit 默认 500） */
export const ZONE_IDS_DEFAULT_LIMIT = 500;

/** 全选筛选结果时单次取 ID 的硬上限（契约：服务端 limit 上限 1000） */
export const ZONE_IDS_MAX_LIMIT = 1000;

/** 专区筛选表单（对应后端 name/categorySecondId/brandId/status） */
export interface ZoneFilterModel {
  brandId?: null | number | string;
  categorySecondId?: null | number | string;
  name?: null | string;
  status?: null | number | string;
}

/** 分页参数 */
export interface ZonePageModel {
  current?: number;
  size?: number;
}

/** 全选筛选结果模式：manual=手动勾选；filter=全选当前筛选结果 */
export type SelectionMode = 'filter' | 'manual';

/** 商品规则相关字段的结构化最小类型（ZoneGoodsRow 可结构赋值） */
export interface ZoneRuleRow {
  clearanceDiscountRate?: null | number | string;
  clearanceExpireTime?: null | string;
  clearancePrice?: null | number | string;
  effectivePrice?: null | number | string;
  isClearanceItem?: null | number | string;
  isMemberItem?: null | number | string;
  memberDiscountRate?: null | number | string;
  memberPriceFixed?: null | number | string;
  memberPriceFloor?: null | number | string;
  skuOverrideCount?: null | number | string;
  state?: null | string;
  stateLabel?: null | string;
}

/** 生效状态徽标 */
export interface StateMeta {
  label: string;
  tip: string;
  type: 'danger' | 'info' | 'success' | 'warning';
}

/** 同步状态徽标 */
export interface SyncMeta {
  label: string;
  tip: string;
  type: 'danger' | 'info' | 'success' | 'warning';
}

/** 撤销结果（后端 POST .../undo 的响应的结构化最小类型） */
export interface UndoConflictLike {
  reason?: null | string;
  spuId?: null | string;
  spuName?: null | string;
}

export interface UndoResultLike {
  batchId?: null | string;
  conflicts?: null | UndoConflictLike[];
  restored?: null | number;
  skipped?: null | number;
}

/** 批量执行 done 事件 summary 的结构化最小类型 */
export interface BatchDoneSummaryLike {
  added?: null | number;
  changed?: null | number;
  failed?: null | number;
  noChange?: null | number;
  removed?: null | number;
  requested?: null | number;
  success?: null | number;
  updated?: null | number;
}

/** 批次记录行（后端 ZoneTagBatchVO 的结构化最小类型） */
export interface BatchRecordLike {
  batchId?: null | string;
  changed?: null | number;
  clearanceAction?: null | string;
  createTime?: null | string;
  failed?: null | number;
  memberAction?: null | string;
  noChange?: null | number;
  operatorName?: null | string;
  syncStatus?: null | string;
  total?: null | number;
  undoStatus?: null | string;
  undoTime?: null | string;
}

/** 批次明细行（后端 ZoneTagBatchItemVO 的结构化最小类型） */
export interface BatchDetailItemLike {
  changeType?: null | string;
  errorCode?: null | string;
  message?: null | string;
  spuId?: null | string;
  spuName?: null | string;
  success?: boolean | null;
}

/** 数值化：空值/非法值返回 null，避免 0 与「未设置」混淆 */
export function toNumber(value: unknown): null | number {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const num = Number(value);
  return Number.isFinite(num) ? num : null;
}

/** 金额统一两位小数，与列表页 formatMoney 口径一致 */
export function formatMoney(value: unknown): string {
  const num = toNumber(value);
  return num === null ? '—' : num.toFixed(2);
}

/** 金额带 ¥ 前缀 */
export function formatPrice(value: unknown): string {
  const num = toNumber(value);
  return num === null ? '—' : `¥${num.toFixed(2)}`;
}

/**
 * 折扣率展示：0.9 → 9折、0.855 → 8.55折。
 * 后端折扣率有效区间 (0, 1)，非法值返回「—」。
 */
export function formatRate(value: unknown): string {
  const num = toNumber(value);
  if (num === null || num <= 0 || num > 1) {
    return '—';
  }
  const tenths = Math.round(num * 1000) / 100;
  return `${Number(tenths.toFixed(2))}折`;
}

/**
 * 默认折扣率文案：未设置时明确写「未设置」，避免运营误以为有兜底。
 */
export function describeDefaultDiscount(value: unknown): string {
  return toNumber(value) === null ? '未设置' : formatRate(value);
}

/**
 * 时间展示：后端统一 yyyy-MM-dd HH:mm:ss，列表只显示到分钟。
 * 非法/空值显示「—」。
 */
export function formatTime(value: null | string | undefined): string {
  if (!value) {
    return '—';
  }
  const text = String(value).trim();
  if (!text) {
    return '—';
  }
  // 兼容 ISO 字符串（2026-11-04T10:00:00）与空格分隔格式
  const normalized = text.replace('T', ' ');
  return normalized.length >= 16 ? normalized.slice(0, 16) : normalized;
}

/** 当前日期的展示口径（yyyy-MM-dd HH:mm:ss） */
export function formatNow(now: Date = new Date()): string {
  const pad = (num: number) => String(num).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
}

/**
 * 过滤空值并 trim，返回稳定的 key 顺序（name/categorySecondId/brandId/status）。
 * 空字符串不传给后端，避免后端把 '' 当作精确匹配。
 */
export function compactFilter(
  filter: null | undefined | ZoneFilterModel,
): Record<string, string> {
  const result: Record<string, string> = {};
  const keys: Array<keyof ZoneFilterModel> = [
    'name',
    'categorySecondId',
    'brandId',
    'status',
  ];
  for (const key of keys) {
    const value = filter?.[key];
    if (value === null || value === undefined) {
      continue;
    }
    const text = String(value).trim();
    if (text) {
      result[key] = text;
    }
  }
  return result;
}

/** 专区商品分页查询参数（GET /product/goodsspu/zone/page） */
export interface ZonePageQueryLike {
  brandId?: string;
  categorySecondId?: string;
  current: number;
  name?: string;
  size: number;
  status?: string;
  zoneType: ZoneKey;
}

export function buildZonePageQuery(
  zone: ZoneKey,
  filter: null | undefined | ZoneFilterModel,
  page: null | undefined | ZonePageModel,
): ZonePageQueryLike {
  return {
    zoneType: zone,
    current: Math.max(1, Math.trunc(toNumber(page?.current) ?? 1)),
    size: Math.max(1, Math.trunc(toNumber(page?.size) ?? 10)),
    ...compactFilter(filter),
  };
}

/** 按筛选条件取 ID 的入参（POST /product/goodsspu/ids） */
export interface ZoneIdsPayloadLike {
  brandId?: string;
  categorySecondId?: string;
  limit: number;
  name?: string;
  status?: string;
  zoneType: ZoneQueryType;
}

export function buildZoneIdsPayload(
  zone: ZoneQueryType,
  filter: null | undefined | ZoneFilterModel,
  limit: number = ZONE_IDS_DEFAULT_LIMIT,
): ZoneIdsPayloadLike {
  return {
    zoneType: zone,
    ...compactFilter(filter),
    limit: clampIdsLimit(limit),
  };
}

/** limit 收敛到契约允许区间 [1, 1000] */
export function clampIdsLimit(limit: unknown): number {
  const num = toNumber(limit) ?? ZONE_IDS_DEFAULT_LIMIT;
  return Math.min(ZONE_IDS_MAX_LIMIT, Math.max(1, Math.trunc(num)));
}

/**
 * 全选筛选结果时单次请求的 limit：
 * ≤500 用 500；500~1000 用 1000；>1000 仍用 1000 并由服务端 truncated 提示分批。
 */
export function resolveSelectAllLimit(total: unknown): number {
  const num = Math.max(0, Math.trunc(toNumber(total) ?? 0));
  return num > ZONE_IDS_DEFAULT_LIMIT
    ? ZONE_IDS_MAX_LIMIT
    : ZONE_IDS_DEFAULT_LIMIT;
}

/** 筛选结果是否超过单次全选上限（需要提示分批 / 缩小筛选） */
export function isSelectAllOversized(total: unknown): boolean {
  const num = Math.max(0, Math.trunc(toNumber(total) ?? 0));
  return num > ZONE_IDS_MAX_LIMIT;
}

/** 全选筛选结果的提示文案（成功/分批两种口径） */
export function describeSelectAllResult(
  total: unknown,
  selected: unknown,
  truncated = false,
): string {
  const safeTotal = Math.max(0, Math.trunc(toNumber(total) ?? 0));
  const safeSelected = Math.max(0, Math.trunc(toNumber(selected) ?? 0));
  if (safeSelected === 0) {
    return '筛选结果为空，无可选商品';
  }
  if (truncated || safeTotal > safeSelected) {
    return `筛选结果共 ${safeTotal} 件，已全选前 ${safeSelected} 件；单次最多 ${ZONE_IDS_MAX_LIMIT} 件，超出部分请缩小筛选范围后分批操作`;
  }
  return `已全选筛选结果 ${safeSelected} 件`;
}

/** ID 去重：转字符串、trim、保序 */
export function dedupeIds(ids: Array<unknown> | null | undefined): string[] {
  if (!Array.isArray(ids)) {
    return [];
  }
  const seen = new Set<string>();
  const result: string[] = [];
  for (const id of ids) {
    if (id === null || id === undefined) {
      continue;
    }
    const key = String(id).trim();
    if (!key || seen.has(key)) {
      continue;
    }
    seen.add(key);
    result.push(key);
  }
  return result;
}

/** 已选计数文案：区分「手动跨页选择」与「全选筛选结果」 */
export function describeSelection(
  count: unknown,
  mode: SelectionMode = 'manual',
  filterTotal?: unknown,
): string {
  const safeCount = Math.max(0, Math.trunc(toNumber(count) ?? 0));
  if (safeCount === 0) {
    return '未选择商品';
  }
  const total = Math.max(0, Math.trunc(toNumber(filterTotal) ?? 0));
  if (mode === 'filter') {
    return total > safeCount
      ? `已选 ${safeCount} 件（筛选共 ${total} 件）`
      : `已选 ${safeCount} 件（当前筛选全部）`;
  }
  return `已选 ${safeCount} 件`;
}

/**
 * 当前规则摘要（不含 SKU 精调，后者单独一列）：
 * 会员：折扣 / 固定价 / 下限；出清：出清价 / 折扣 / 截止时间。
 */
export function buildRuleSummary(zone: ZoneKey, row: ZoneRuleRow): string[] {
  if (zone === 'member') {
    if (toNumber(row.isMemberItem) !== 1) {
      return ['未加入'];
    }
    const items: string[] = [];
    const rate = toNumber(row.memberDiscountRate);
    const fixed = toNumber(row.memberPriceFixed);
    const floor = toNumber(row.memberPriceFloor);
    if (rate !== null) {
      items.push(`折扣 ${formatRate(rate)}`);
    }
    if (fixed !== null) {
      items.push(`固定价 ${formatPrice(fixed)}`);
    }
    if (floor !== null) {
      items.push(`下限 ${formatPrice(floor)}`);
    }
    return items.length > 0 ? items : ['未配规则'];
  }

  if (toNumber(row.isClearanceItem) !== 1) {
    return ['未加入'];
  }
  const items: string[] = [];
  const price = toNumber(row.clearancePrice);
  const rate = toNumber(row.clearanceDiscountRate);
  if (price !== null) {
    items.push(`出清价 ${formatPrice(price)}`);
  }
  if (rate !== null) {
    items.push(`折扣 ${formatRate(rate)}`);
  }
  if (row.clearanceExpireTime) {
    items.push(`截止 ${formatTime(row.clearanceExpireTime)}`);
  }
  return items.length > 0 ? items : ['未配规则'];
}

/** SKU 精调数量文案：0 件显示「—」 */
export function describeSkuOverride(row: ZoneRuleRow): string {
  const count = Math.max(0, Math.trunc(toNumber(row.skuOverrideCount) ?? 0));
  return count > 0 ? `${count} 个规格精调` : '—';
}

/** 是否存在 SKU 精调（列表高亮/提示用） */
export function hasSkuOverride(row: ZoneRuleRow): boolean {
  return Math.max(0, Math.trunc(toNumber(row.skuOverrideCount) ?? 0)) > 0;
}

/**
 * 生效状态徽标。state 口径见契约 §2.6：
 * OFF_SHELF > EXPIRED > NO_RULE > INVERTED > NORMAL。
 * 未知 state 时回退到后端 stateLabel。
 */
export function resolveStateMeta(
  state?: null | string,
  stateLabel?: null | string,
): StateMeta {
  switch ((state ?? '').toUpperCase()) {
    case 'EXPIRED': {
      return {
        type: 'info',
        label: stateLabel || '已到期',
        tip: '出清截止时间已过，C 端出清价不再生效',
      };
    }
    case 'INVERTED': {
      return {
        type: 'danger',
        label: stateLabel || '倒挂不生效',
        tip: '按规则计算的专区价不低于商品售价，C 端不会展示该专区价',
      };
    }
    case 'NO_RULE': {
      return {
        type: 'warning',
        label: stateLabel || '未配规则',
        tip: '商品未配置规则且专区默认折扣率为空，该商品不产生专区价',
      };
    }
    case 'NORMAL': {
      return {
        type: 'success',
        label: stateLabel || '生效中',
        tip: '专区价已生效，C 端按该价格展示',
      };
    }
    case 'OFF_SHELF': {
      return {
        type: 'info',
        label: stateLabel || '已下架',
        tip: '商品已下架，打标后 C 端不展示',
      };
    }
    default: {
      return {
        type: 'info',
        label: stateLabel || '未知',
        tip: '未获取到生效状态，请刷新后重试',
      };
    }
  }
}

/** 生效价展示：SPU 口径；有 SKU 精调时加标记，避免误读为所有规格统一价 */
export function describeEffectivePrice(row: ZoneRuleRow): string {
  const price = formatPrice(row.effectivePrice);
  if (price === '—') {
    return '—';
  }
  return hasSkuOverride(row) ? `${price}（含 SKU 精调）` : price;
}

/** 专区同步状态徽标（GET /promotion/zone/sync-status） */
export function resolveSyncMeta(status?: null | string): SyncMeta {
  switch ((status ?? '').toUpperCase()) {
    case 'FAILED': {
      return {
        type: 'danger',
        label: '同步失败',
        tip: '标签已保存，价格同步失败；可点击「重试同步」重新计算',
      };
    }
    case 'SUCCESS': {
      return {
        type: 'success',
        label: '同步成功',
        tip: '专区价已同步到 C 端系统活动',
      };
    }
    default: {
      return {
        type: 'warning',
        label: '未同步',
        tip: '尚未成功同步价格或状态未知；可点击「重试同步」触发重算',
      };
    }
  }
}

/** 系统活动状态徽标（sync-status.activityStatus） */
export function resolveActivityStatusMeta(status?: null | string): {
  label: string;
  type: 'danger' | 'info' | 'success' | 'warning';
} {
  // 后端 `/promotion/zone/sync-status` 的 activityStatus 是 PromotionActivity.STATUS_* 数字字符串：
  // 1 草稿 / 2 已发布 / 3 已暂停 / 4 已结束 / 5 未开始；同时兼容旧字符串口径。
  switch ((status ?? '').trim().toUpperCase()) {
    case '1':
    case 'DRAFT': {
      return { type: 'info', label: '草稿' };
    }
    case '2':
    case 'PUBLISHED': {
      return { type: 'success', label: '已发布' };
    }
    case '3':
    case 'PAUSED': {
      return { type: 'warning', label: '已暂停' };
    }
    case '4':
    case 'ENDED': {
      return { type: 'info', label: '已结束' };
    }
    case '5':
    case 'UPCOMING': {
      return { type: 'info', label: '未开始' };
    }
    default: {
      return { type: 'info', label: '未发布' };
    }
  }
}

/** 专区动作文案（批次记录/结果面板用） */
export function formatActionLabel(action?: null | string): string {
  switch ((action ?? '').toUpperCase()) {
    case 'ADD': {
      return '加入/更新';
    }
    case 'NONE': {
      return '不修改';
    }
    case 'REMOVE': {
      return '移出';
    }
    case 'UPDATE': {
      return '更新规则';
    }
    default: {
      return '—';
    }
  }
}

/** 批次动作摘要：会员 + 出清两个专区 */
export function summarizeBatchActions(
  memberAction?: null | string,
  clearanceAction?: null | string,
): string {
  const parts: string[] = [];
  const member = formatActionLabel(memberAction);
  const clearance = formatActionLabel(clearanceAction);
  if (member !== '—' && member !== '不修改') {
    parts.push(`会员：${member}`);
  }
  if (clearance !== '—' && clearance !== '不修改') {
    parts.push(`出清：${clearance}`);
  }
  return parts.length > 0 ? parts.join(' · ') : '无动作';
}

/** 批次记录结果摘要：变更/无变化/失败 */
export function summarizeBatchRecord(
  record: BatchRecordLike | null | undefined,
): string {
  const changed = Math.max(0, Math.trunc(toNumber(record?.changed) ?? 0));
  const noChange = Math.max(0, Math.trunc(toNumber(record?.noChange) ?? 0));
  const failed = Math.max(0, Math.trunc(toNumber(record?.failed) ?? 0));
  return `变更 ${changed} · 无变化 ${noChange} · 失败 ${failed}`;
}

/** 批次记录总件数 */
export function readBatchTotal(
  record: BatchRecordLike | null | undefined,
): number {
  return Math.max(0, Math.trunc(toNumber(record?.total) ?? 0));
}

/** 变更类型文案（ADD/UPDATE/REMOVE/NO_CHANGE/INVALID） */
export function formatChangeType(type?: null | string): string {
  switch ((type ?? '').toUpperCase()) {
    case 'ADD': {
      return '新增';
    }
    case 'INVALID': {
      return '不生效';
    }
    case 'NO_CHANGE': {
      return '无变化';
    }
    case 'REMOVE': {
      return '移出';
    }
    case 'UPDATE': {
      return '更新';
    }
    default: {
      return '—';
    }
  }
}

/** 变更类型对应的标签色（明细表用） */
export function changeTypeTagType(
  type?: null | string,
): 'danger' | 'info' | 'success' | 'warning' {
  switch ((type ?? '').toUpperCase()) {
    case 'ADD': {
      return 'success';
    }
    case 'INVALID': {
      return 'danger';
    }
    case 'REMOVE': {
      return 'warning';
    }
    case 'UPDATE': {
      return 'warning';
    }
    default: {
      return 'info';
    }
  }
}

/** 批次明细聚合：成功/失败/无变化/不生效/真正发生变更数 */
export function summarizeBatchItems(
  items: BatchDetailItemLike[] | null | undefined,
): {
  changed: number;
  failed: number;
  invalid: number;
  noChange: number;
  success: number;
  total: number;
} {
  const list = Array.isArray(items) ? items : [];
  let changed = 0;
  let failed = 0;
  let invalid = 0;
  let noChange = 0;
  let success = 0;
  for (const item of list) {
    const type = (item?.changeType ?? '').toUpperCase();
    if (item?.success === false || type === 'INVALID') {
      failed += 1;
      if (type === 'INVALID') {
        invalid += 1;
      }
      continue;
    }
    success += 1;
    if (type === 'NO_CHANGE') {
      noChange += 1;
    } else if (['ADD', 'REMOVE', 'UPDATE'].includes(type)) {
      changed += 1;
    }
  }
  return { total: list.length, success, changed, noChange, invalid, failed };
}

/** 批量执行 done 事件的结果摘要（弹窗关闭后轻提示） */
export function summarizeBatchDone(
  payload?: null | {
    failedCount?: null | number;
    summary?: BatchDoneSummaryLike | null;
  },
): string {
  const summary = payload?.summary;
  if (!summary) {
    const failedCount = Math.max(
      0,
      Math.trunc(toNumber(payload?.failedCount) ?? 0),
    );
    return failedCount > 0 ? `操作完成，失败 ${failedCount} 件` : '操作完成';
  }
  const success = Math.max(0, Math.trunc(toNumber(summary.success) ?? 0));
  const failed = Math.max(
    0,
    Math.trunc(toNumber(summary.failed) ?? toNumber(payload?.failedCount) ?? 0),
  );
  const noChange = Math.max(0, Math.trunc(toNumber(summary.noChange) ?? 0));
  const parts = [`成功 ${success}`];
  if (noChange > 0) {
    parts.push(`无变化 ${noChange}`);
  }
  if (failed > 0) {
    parts.push(`失败 ${failed}`);
  }
  return parts.join(' · ');
}

/** done 结果摘要的提示类型：有失败用 warning，否则 success */
export function resolveBatchDoneTone(
  payload?: null | {
    failedCount?: null | number;
    summary?: BatchDoneSummaryLike | null;
  },
): 'success' | 'warning' {
  const failed = Math.max(
    0,
    Math.trunc(
      toNumber(payload?.summary?.failed) ?? toNumber(payload?.failedCount) ?? 0,
    ),
  );
  return failed > 0 ? 'warning' : 'success';
}

/** 撤销结果聚合：成功/跳过/冲突明细 */
export function summarizeUndoResult(result?: null | UndoResultLike): {
  conflictCount: number;
  summary: string;
  tone: 'info' | 'success' | 'warning';
} {
  if (!result) {
    return {
      tone: 'info',
      summary: '撤销未返回结果，请刷新批次记录确认',
      conflictCount: 0,
    };
  }
  const restored = Math.max(0, Math.trunc(toNumber(result.restored) ?? 0));
  const skipped = Math.max(0, Math.trunc(toNumber(result.skipped) ?? 0));
  const conflicts = Array.isArray(result.conflicts) ? result.conflicts : [];
  if (skipped > 0) {
    return {
      tone: 'warning',
      summary: `已撤销 ${restored} 件，跳过 ${skipped} 件（当前值已被修改，未回滚）`,
      conflictCount: conflicts.length,
    };
  }
  if (restored > 0) {
    return {
      tone: 'success',
      summary: `已撤销 ${restored} 件商品`,
      conflictCount: conflicts.length,
    };
  }
  return {
    tone: 'info',
    summary: '没有可撤销的商品（可能已撤销或商品已被再次修改）',
    conflictCount: conflicts.length,
  };
}

/** 撤销状态徽标 */
export function resolveUndoMeta(undoStatus?: null | string): {
  label: string;
  type: 'info' | 'success' | 'warning';
} {
  switch ((undoStatus ?? '').toUpperCase()) {
    case 'PARTIAL': {
      return { type: 'warning', label: '部分撤销' };
    }
    case 'UNDONE': {
      return { type: 'success', label: '已撤销' };
    }
    default: {
      return { type: 'info', label: '可撤销' };
    }
  }
}

/** 批次是否可撤销：已撤销的不再提供入口 */
export function isBatchUndoable(record?: BatchRecordLike | null): boolean {
  return (
    !!record &&
    (record.undoStatus ?? '').trim().toUpperCase() !== 'UNDONE' &&
    readBatchTotal(record) > 0
  );
}

/** 添加商品候选的 zoneType：会员专区候选 = not_member，出清候选 = not_clearance */
export function resolvePickerZoneType(
  zone: ZoneKey,
): 'not_clearance' | 'not_member' {
  return zone === 'member' ? 'not_member' : 'not_clearance';
}

/** 移出确认文案（影响范围提示，避免误点） */
export function buildRemoveConfirmText(zone: ZoneKey, count: number): string {
  return `确认将所选的 ${count} 件商品移出「${ZONE_LABEL[zone]}」？移出后 SPU 规则、SKU 精调与 C 端专区价都会清空，此操作不可逆。`;
}

/** 商品编辑页路由（单商品查看，行内跳转用） */
export function buildGoodsEditPath(spuId: string): string {
  return `/spu/form?id=${encodeURIComponent(spuId)}`;
}
