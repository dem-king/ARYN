/**
 * 专区批量操作纯逻辑：动作→payload 映射、预检/执行汇总、200/片分片、
 * 影响文案与告警文案（契约 §3.2）。
 *
 * 这里刻意不依赖 Vue 与 Element Plus，便于 vitest 直接覆盖；
 * 组件层只做状态机与渲染。
 */
import type {
  ZoneAction,
  ZoneBatchAggregate,
  ZoneBatchItem,
  ZoneBatchPayload,
  ZoneBatchPreviewRequest,
  ZoneBatchResult,
  ZoneBatchSummary,
  ZoneBatchSync,
  ZoneBatchZoneSummary,
  ZoneChangeType,
  ZoneGoodsState,
  ZonePreview,
  ZoneRuleSnapshot,
  ZoneSkuEditRow,
  ZoneSkuTag,
  ZoneSkuTagSave,
  ZoneSyncState,
  ZoneTagDraft,
  ZoneType,
  ZoneWarning,
} from './types';

/** 后端单次批量上限：超过后前端自动分片（契约 §2.1/§2.2） */
export const ZONE_BATCH_CHUNK_SIZE = 200;

/** 专区顺序：界面与汇总固定「会员专区 → 低价捡漏」，避免对象键顺序不稳定 */
export const ZONE_TYPES: ZoneType[] = ['member', 'clearance'];

/** 专区文案 */
export const ZONE_LABELS: Record<ZoneType, string> = {
  clearance: '低价捡漏',
  member: '会员专区',
};

const ZONE_ACTION_LABELS: Record<ZoneAction, string> = {
  ADD: '加入/更新规则',
  NONE: '不修改',
  REMOVE: '移出专区',
};

const ZONE_CHANGE_TYPE_LABELS: Record<ZoneChangeType, string> = {
  ADD: '新增',
  INVALID: '无法执行',
  NO_CHANGE: '无变化',
  REMOVE: '移出',
  UPDATE: '更新',
};

const ZONE_STATE_LABELS: Record<ZoneGoodsState, string> = {
  EXPIRED: '已到期',
  INVERTED: '倒挂不生效',
  NORMAL: '生效中',
  NO_RULE: '未配规则',
  OFF_SHELF: '已下架',
};

/** 专区显示名 */
export function zoneLabel(zone: string | ZoneType): string {
  return ZONE_LABELS[zone as ZoneType] ?? String(zone);
}

/** 动作显示名 */
export function zoneActionLabel(action: string | ZoneAction): string {
  return ZONE_ACTION_LABELS[action as ZoneAction] ?? String(action);
}

/** 变更类型显示名 */
export function zoneChangeTypeLabel(type: string | ZoneChangeType): string {
  return ZONE_CHANGE_TYPE_LABELS[type as ZoneChangeType] ?? String(type);
}

/** 专区商品生效状态显示名（后端 stateLabel 缺失时兜底） */
export function zoneStateLabel(state: string | ZoneGoodsState): string {
  return ZONE_STATE_LABELS[state as ZoneGoodsState] ?? String(state);
}

/** 生效状态对应的 ElTag 类型 */
export function zoneStateTagType(
  state: string | ZoneGoodsState,
): 'danger' | 'info' | 'success' | 'warning' {
  switch (state) {
    case 'EXPIRED':
    case 'INVERTED':
    case 'NO_RULE': {
      return 'warning';
    }
    case 'NORMAL': {
      return 'success';
    }
    case 'OFF_SHELF': {
      return 'info';
    }
    default: {
      return 'info';
    }
  }
}

/** 告警严重度：OFF_SHELF/INVALID 类偏危险，配置类提示偏 warning */
export function zoneWarningTagType(
  code: string,
): 'danger' | 'info' | 'warning' {
  if (code === 'OFF_SHELF') return 'danger';
  if (code === 'NO_RULE' || code === 'EXPIRED') return 'warning';
  return 'info';
}

/** 同步状态文案（前端固定口径：FAILED 不得显示「已同步」） */
export function zoneSyncStateLabel(status: null | string | ZoneSyncState) {
  switch (status) {
    case 'FAILED': {
      return '价格同步失败';
    }
    case 'SKIPPED': {
      return '未触发价格同步';
    }
    case 'SUCCESS': {
      return '价格已同步';
    }
    default: {
      return '同步状态未知';
    }
  }
}

/** 结果面板同步说明：FAILED 必须明确提示可重试，禁止承诺「已同步」 */
export function describeSync(
  status: null | undefined | ZoneSyncState,
  message?: null | string,
): string {
  switch (status) {
    case 'FAILED': {
      return `标签已保存，但价格同步失败${
        message ? `：${message}` : ''
      }。可重试同步或等待每日兜底任务，请勿重复打标。`;
    }
    case 'SKIPPED': {
      return '标签已保存；本次没有实际变更，未触发价格同步。';
    }
    case 'SUCCESS': {
      return '标签已保存，专区价格已同步。';
    }
    default: {
      return '标签已保存，价格同步状态未知，请稍后核对专区活动。';
    }
  }
}

/** 从请求错误中提取可展示文案（后端业务错误优先） */
export function resolveErrorMessage(
  error: unknown,
  fallback = '操作失败，请稍后重试',
): string {
  if (!error) return fallback;
  if (typeof error === 'string') return error;
  const err = error as Record<string, any>;
  const responseMessage =
    err?.response?.data?.message ??
    err?.response?.data?.msg ??
    err?.response?.data?.data;
  if (typeof responseMessage === 'string' && responseMessage) {
    return responseMessage;
  }
  if (typeof err?.message === 'string' && err.message) return err.message;
  if (typeof err?.msg === 'string' && err.msg) return err.msg;
  return fallback;
}

/** 去重 + 去空 SPU ID（保持传入顺序） */
export function uniqueSpuIds(ids: readonly string[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const id of ids) {
    const value = String(id ?? '').trim();
    if (!value || seen.has(value)) continue;
    seen.add(value);
    result.push(value);
  }
  return result;
}

/** 按 size 分片（默认 200/片，服务端硬上限） */
export function chunkIds(
  ids: readonly string[],
  size = ZONE_BATCH_CHUNK_SIZE,
): string[][] {
  const unique = uniqueSpuIds(ids);
  const chunkSize = Math.max(1, Math.floor(size));
  const chunks: string[][] = [];
  for (let index = 0; index < unique.length; index += chunkSize) {
    chunks.push(unique.slice(index, index + chunkSize));
  }
  return chunks;
}

/** 空草稿 */
export function emptyZoneDraft(zone: ZoneType, action: ZoneAction = 'NONE') {
  return { action, zone } satisfies ZoneTagDraft;
}

/**
 * 生成默认草稿：
 * - standard：范围内专区默认取 `defaultAction`（默认「不修改」；「添加商品」入口传 ADD）；
 * - remove：范围内专区默认「移出专区」，范围外「不修改」，`defaultAction` 不生效。
 */
export function createDefaultDrafts(
  zoneScope: 'both' | ZoneType = 'both',
  mode: 'remove' | 'standard' = 'standard',
  defaultAction: ZoneAction = 'NONE',
): ZoneTagDraft[] {
  const scopedZones: ZoneType[] =
    zoneScope === 'both' ? [...ZONE_TYPES] : [zoneScope];
  return ZONE_TYPES.map((zone) => {
    const inScope = scopedZones.includes(zone);
    let action: ZoneAction = 'NONE';
    if (inScope) {
      action = mode === 'remove' ? 'REMOVE' : defaultAction;
    }
    return emptyZoneDraft(zone, action);
  });
}

/** 取指定专区的草稿 */
export function findZoneDraft(
  drafts: readonly ZoneTagDraft[],
  zone: ZoneType,
): ZoneTagDraft {
  return drafts.find((draft) => draft.zone === zone) ?? emptyZoneDraft(zone);
}

function toRuleValue(value: null | number | undefined): number | undefined {
  if (value === null || value === undefined) return undefined;
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function toExpireValue(value: null | string | undefined): string | undefined {
  if (!value) return undefined;
  return value;
}

function applyDraftToPreviewRequest(
  request: ZoneBatchPreviewRequest,
  draft: ZoneTagDraft,
) {
  if (draft.zone === 'member') {
    request.memberAction = draft.action;
    if (draft.action === 'ADD') {
      request.memberDiscountRate = toRuleValue(draft.memberDiscountRate);
      request.memberPriceFixed = toRuleValue(draft.memberPriceFixed);
    }
    return;
  }
  request.clearanceAction = draft.action;
  if (draft.action === 'ADD') {
    request.clearancePrice = toRuleValue(draft.clearancePrice);
    request.clearanceDiscountRate = toRuleValue(draft.clearanceDiscountRate);
    request.clearanceExpireTime = toExpireValue(draft.clearanceExpireTime);
  }
}

/**
 * 动作/规则草稿 → 预检请求（§2.1）。
 * 规则字段只在 ADD 时提交；空值表示「沿用旧规则/专区默认折扣」。
 */
export function buildPreviewRequest(
  spuIds: readonly string[],
  drafts: readonly ZoneTagDraft[],
  overwriteSkuRules = false,
): ZoneBatchPreviewRequest {
  const request: ZoneBatchPreviewRequest = {
    overwriteSkuRules: overwriteSkuRules === true,
    spuIds: uniqueSpuIds(spuIds),
  };
  for (const zone of ZONE_TYPES) {
    applyDraftToPreviewRequest(request, findZoneDraft(drafts, zone));
  }
  return request;
}

/**
 * 动作/规则草稿 → 批量执行请求（§2.2，沿用 `GoodsZoneTagBatchDTO` 旧字段）。
 * NONE 不提交对应 `memberTag/clearanceTag`，后端据此判定「不修改」。
 */
export function buildBatchPayload(
  spuIds: readonly string[],
  drafts: readonly ZoneTagDraft[],
  overwriteSkuRules = false,
): ZoneBatchPayload {
  const payload: ZoneBatchPayload = {
    overwriteSkuRules: overwriteSkuRules === true,
    spuIds: uniqueSpuIds(spuIds),
  };
  for (const zone of ZONE_TYPES) {
    const draft = findZoneDraft(drafts, zone);
    if (draft.action === 'NONE') continue;
    const joined = draft.action === 'ADD';
    if (zone === 'member') {
      payload.memberTag = joined;
      if (joined) {
        payload.memberDiscountRate = toRuleValue(draft.memberDiscountRate);
        payload.memberPriceFixed = toRuleValue(draft.memberPriceFixed);
      }
      continue;
    }
    payload.clearanceTag = joined;
    if (joined) {
      payload.clearancePrice = toRuleValue(draft.clearancePrice);
      payload.clearanceDiscountRate = toRuleValue(draft.clearanceDiscountRate);
      payload.clearanceExpireTime = toExpireValue(draft.clearanceExpireTime);
    }
  }
  return payload;
}

/** 预检影响汇总（底部实时影响 + 确认按钮） */
export interface ZonePreviewImpact {
  add: number;
  invalid: number;
  noChange: number;
  remove: number;
  skuOverridesWillClear: number;
  total: number;
  update: number;
  warningCount: number;
}

const EMPTY_IMPACT: ZonePreviewImpact = {
  add: 0,
  invalid: 0,
  noChange: 0,
  remove: 0,
  skuOverridesWillClear: 0,
  total: 0,
  update: 0,
  warningCount: 0,
};

/** 从预检响应提取影响汇总（null 时返回全 0，便于直接绑定 UI） */
export function computePreviewImpact(
  preview: null | undefined | ZonePreview,
): ZonePreviewImpact {
  if (!preview?.summary) return { ...EMPTY_IMPACT };
  const summary = preview.summary;
  return {
    add: summary.add ?? 0,
    invalid: summary.invalid ?? 0,
    noChange: summary.noChange ?? 0,
    remove: summary.remove ?? 0,
    skuOverridesWillClear: summary.skuOverridesWillClear ?? 0,
    total: summary.total ?? 0,
    update: summary.update ?? 0,
    warningCount: summary.warningCount ?? 0,
  };
}

/** 是否存在实际生效动作（新增/更新/移出） */
export function hasPreviewImpact(impact: ZonePreviewImpact): boolean {
  return impact.add + impact.update + impact.remove > 0;
}

/** 确认按钮文案：`确认：加入 2 件 / 更新 8 件`、`确认移出 10 件` */
export function computeConfirmText(impact: ZonePreviewImpact): string {
  const parts: string[] = [];
  if (impact.add > 0) parts.push(`加入 ${impact.add} 件`);
  if (impact.update > 0) parts.push(`更新 ${impact.update} 件`);
  if (impact.remove > 0) parts.push(`移出 ${impact.remove} 件`);
  if (parts.length === 0) return '确认';
  if (impact.remove > 0 && parts.length === 1) {
    return `确认移出 ${impact.remove} 件`;
  }
  return `确认：${parts.join(' / ')}`;
}

/** 底部影响汇总文案 */
export function computeImpactText(impact: ZonePreviewImpact): string {
  const parts = [
    `新增 ${impact.add} 件`,
    `更新 ${impact.update} 件`,
    `移出 ${impact.remove} 件`,
    `无变化 ${impact.noChange} 件`,
  ];
  if (impact.invalid > 0) parts.push(`无法执行 ${impact.invalid} 件`);
  if (impact.warningCount > 0) parts.push(`预警 ${impact.warningCount} 条`);
  return parts.join(' · ');
}

/** 单个专区的执行前状态汇总（弹窗顶部展示） */
export interface ZoneStatusCounts {
  /** 已在专区 */
  existing: number;
  /** 无法执行（INVALID） */
  invalid: number;
  /** 未加入专区 */
  notJoined: number;
  /** 无变化 */
  noChange: number;
  /** 商品总数 */
  total: number;
  /** 将加入 */
  willAdd: number;
  /** 将移出 */
  willRemove: number;
  /** 规则将变化 */
  willUpdate: number;
  /** 有 SKU 精调的商品数 */
  withSkuOverride: number;
}

/** 规则字段是否有值（null/undefined 均视为未配置） */
function isPresent(value: null | number | string | undefined): boolean {
  return value !== null && value !== undefined;
}

function snapshotHasZoneRule(
  before: null | undefined | ZoneRuleSnapshot,
  zone: ZoneType,
): boolean {
  if (!before) return false;
  if (zone === 'member') {
    return (
      isPresent(before.memberDiscountRate) ||
      isPresent(before.memberPriceFixed) ||
      isPresent(before.memberPriceFloor)
    );
  }
  return (
    isPresent(before.clearancePrice) ||
    isPresent(before.clearanceDiscountRate) ||
    isPresent(before.clearanceExpireTime)
  );
}

/** 快照是否已在专区 */
export function snapshotInZone(
  before: null | undefined | ZoneRuleSnapshot,
  zone: ZoneType,
): boolean {
  if (!before) return false;
  const tag = zone === 'member' ? before.memberTag : before.clearanceTag;
  return Number(tag) === 1;
}

/**
 * 专区状态汇总：优先按 `items[].before/changes` 计算（动作切换后实时刷新），
 * `items` 缺失时退回 `zones[]` 汇总。
 */
export function computeZoneStatus(
  preview: null | undefined | ZonePreview,
  zone: ZoneType,
): ZoneStatusCounts {
  const items = preview?.items ?? [];
  const zoneSummary = preview?.zones?.find((item) => item.zone === zone);
  const status: ZoneStatusCounts = {
    existing: 0,
    invalid: 0,
    noChange: 0,
    notJoined: 0,
    total: items.length > 0 ? items.length : (preview?.summary?.total ?? 0),
    willAdd: 0,
    willRemove: 0,
    willUpdate: 0,
    withSkuOverride: 0,
  };
  if (items.length === 0) {
    if (zoneSummary) {
      status.existing = zoneSummary.existing ?? 0;
      status.invalid = zoneSummary.invalid ?? 0;
      status.noChange = zoneSummary.noChange ?? 0;
      status.withSkuOverride = zoneSummary.withSkuOverride ?? 0;
      status.willAdd = zoneSummary.willAdd ?? 0;
      status.willRemove = zoneSummary.willRemove ?? 0;
      status.willUpdate = zoneSummary.willUpdate ?? 0;
      status.notJoined = Math.max(0, status.total - status.existing);
    }
    return status;
  }
  for (const item of items) {
    if (snapshotInZone(item.before, zone)) status.existing += 1;
    if ((item.before?.skuOverrideCount ?? 0) > 0) status.withSkuOverride += 1;
    const change = item.changes?.find((entry) => entry.zone === zone);
    switch (change?.type) {
      case 'ADD': {
        status.willAdd += 1;
        break;
      }
      case 'INVALID': {
        status.invalid += 1;
        break;
      }
      case 'NO_CHANGE': {
        status.noChange += 1;
        break;
      }
      case 'REMOVE': {
        status.willRemove += 1;
        break;
      }
      case 'UPDATE': {
        status.willUpdate += 1;
        break;
      }
      default: {
        break;
      }
    }
  }
  status.notJoined = Math.max(0, status.total - status.existing);
  return status;
}

/** 聚合告警（按 code+message 去重计数），弹窗顶部统一提示 */
export interface AggregatedWarning {
  code: string;
  count: number;
  message: string;
}

function aggregateWarnings(
  warnings: readonly (null | undefined | ZoneWarning)[],
): AggregatedWarning[] {
  const map = new Map<string, AggregatedWarning>();
  for (const warning of warnings) {
    if (!warning?.message) continue;
    const code = warning.code ?? 'UNKNOWN';
    const key = `${code}::${warning.message}`;
    const existing = map.get(key);
    if (existing) {
      existing.count += 1;
    } else {
      map.set(key, { code, count: 1, message: warning.message });
    }
  }
  return [...map.values()].sort((a, b) => b.count - a.count);
}

/** 预检告警聚合（商品级 + 专区级） */
export function collectPreviewWarnings(
  preview: null | undefined | ZonePreview,
): AggregatedWarning[] {
  const warnings: ZoneWarning[] = [];
  for (const item of preview?.items ?? []) {
    warnings.push(...(item.warnings ?? []));
    for (const change of item.changes ?? []) {
      warnings.push(...(change.warnings ?? []));
    }
  }
  return aggregateWarnings(warnings);
}

/** 执行结果告警聚合 */
export function collectItemWarnings(
  items: readonly ZoneBatchItem[],
): AggregatedWarning[] {
  const warnings: ZoneWarning[] = [];
  for (const item of items) {
    warnings.push(...(item.warnings ?? []));
    for (const change of item.changes ?? []) {
      warnings.push(...(change.warnings ?? []));
    }
  }
  return aggregateWarnings(warnings);
}

/** 从执行结果条目汇总 summary + 分专区计数（重试后按最新条目重算，保证自洽） */
export function summarizeItems(items: readonly ZoneBatchItem[]): {
  failedCount: number;
  summary: ZoneBatchSummary;
  zones: ZoneBatchZoneSummary[];
} {
  const summary: ZoneBatchSummary = {
    added: 0,
    changed: 0,
    failed: 0,
    noChange: 0,
    removed: 0,
    requested: items.length,
    success: 0,
    updated: 0,
  };
  const zoneMap = new Map<ZoneType, ZoneBatchZoneSummary>();
  for (const zone of ZONE_TYPES) {
    zoneMap.set(zone, {
      added: 0,
      failed: 0,
      noChange: 0,
      removed: 0,
      updated: 0,
      zone,
    });
  }
  for (const item of items) {
    if (!item.success) {
      summary.failed += 1;
      continue;
    }
    summary.success += 1;
    switch (item.changeType) {
      case 'ADD': {
        summary.added += 1;
        break;
      }
      case 'NO_CHANGE': {
        summary.noChange += 1;
        break;
      }
      case 'REMOVE': {
        summary.removed += 1;
        break;
      }
      case 'UPDATE': {
        summary.updated += 1;
        break;
      }
      default: {
        break;
      }
    }
    for (const change of item.changes ?? []) {
      const zoneSummary = zoneMap.get(change.zone);
      if (!zoneSummary) continue;
      switch (change.type) {
        case 'ADD': {
          zoneSummary.added += 1;
          break;
        }
        case 'NO_CHANGE': {
          zoneSummary.noChange += 1;
          break;
        }
        case 'REMOVE': {
          zoneSummary.removed += 1;
          break;
        }
        case 'UPDATE': {
          zoneSummary.updated += 1;
          break;
        }
        default: {
          // INVALID / 其它不计入成功分区计数
          break;
        }
      }
    }
  }
  summary.changed = summary.added + summary.updated + summary.removed;
  // 失败条目也归入对应专区（便于「分专区失败数」展示）
  for (const item of items) {
    if (item.success) continue;
    for (const change of item.changes ?? []) {
      const zoneSummary = zoneMap.get(change.zone);
      if (zoneSummary) zoneSummary.failed += 1;
    }
  }
  return {
    failedCount: summary.failed,
    summary,
    zones: [...zoneMap.values()],
  };
}

/** 汇总多次批量响应（前端分片后合并） */
export function aggregateBatchResults(
  results: readonly ZoneBatchResult[],
): ZoneBatchAggregate {
  const items = results.flatMap((result) => result.items ?? []);
  const { failedCount, summary, zones } = summarizeItems(items);
  const syncs: ZoneBatchSync[] = [];
  for (const result of results) {
    if (result.sync) syncs.push(result.sync);
  }
  return {
    batchIds: results.map((result) => result.batchId).filter(Boolean),
    failedCount,
    items,
    summary,
    syncs,
    zones,
  };
}

/**
 * 重试合并：同一 SPU 以最新一次结果为准（重试覆盖旧失败条目），
 * 批次号与同步状态累积保留。
 */
export function mergeBatchAggregate(
  previous: null | undefined | ZoneBatchAggregate,
  retryResults: readonly ZoneBatchResult[],
): ZoneBatchAggregate {
  const retried = aggregateBatchResults(retryResults);
  const itemMap = new Map<string, ZoneBatchItem>();
  for (const item of previous?.items ?? []) itemMap.set(item.spuId, item);
  for (const item of retried.items) itemMap.set(item.spuId, item);
  const items = [...itemMap.values()];
  const { failedCount, summary, zones } = summarizeItems(items);
  return {
    batchIds: [...(previous?.batchIds ?? []), ...retried.batchIds],
    failedCount,
    items,
    summary,
    syncs: [...(previous?.syncs ?? []), ...retried.syncs],
    zones,
  };
}

/** 同步状态严重度：FAILED > SUCCESS > SKIPPED > 未知 */
export function computeSyncState(
  syncs: readonly (null | undefined | ZoneBatchSync)[],
): null | ZoneSyncState {
  let hasSkipped = false;
  let hasSuccess = false;
  for (const sync of syncs) {
    if (!sync?.status) continue;
    if (sync.status === 'FAILED') return 'FAILED';
    if (sync.status === 'SUCCESS') hasSuccess = true;
    if (sync.status === 'SKIPPED') hasSkipped = true;
  }
  if (hasSuccess) return 'SUCCESS';
  if (hasSkipped) return 'SKIPPED';
  return null;
}

/** 请求异常时，为整片 SPU 生成失败条目（保证结果面板可复制/可重试） */
export function createFailedItems(
  spuIds: readonly string[],
  message: string,
): ZoneBatchItem[] {
  return uniqueSpuIds(spuIds).map((spuId) => ({
    changeType: 'INVALID',
    changes: [],
    errorCode: 'BATCH_REQUEST_FAILED',
    message,
    spuId,
    spuName: '',
    success: false,
    warnings: [],
  }));
}

function formatSkuLabel(spuName: string | undefined, spuId: string): string {
  return spuName ? `${spuName}（${spuId}）` : spuId;
}

/** 「复制失败清单」文本 */
export function collectFailuresText(items: readonly ZoneBatchItem[]): string {
  const failures = items.filter((item) => !item.success);
  if (failures.length === 0) return '';
  const lines = [`专区批量操作失败清单（共 ${failures.length} 件）`];
  failures.forEach((item, index) => {
    const reason = item.message || '未知原因';
    const code = item.errorCode ? `【${item.errorCode}】` : '';
    lines.push(
      `${index + 1}. ${formatSkuLabel(item.spuName, item.spuId)}：${code}${reason}`,
    );
  });
  return lines.join('\n');
}

/** 移出影响范围（二次确认文案用） */
export interface ZoneRemoveImpact {
  /** 将被移出的商品数（当前真的在该专区） */
  spuCount: number;
  /** 将清空的 SPU 规则条数 */
  ruleCount: number;
  /** 将清空的 SKU 精调条数 */
  skuRuleCount: number;
  /** 涉及的专区 */
  zones: ZoneType[];
}

/** 计算移出动作的影响范围（用于危险操作二次确认） */
export function computeRemoveImpact(
  preview: null | undefined | ZonePreview,
  drafts: readonly ZoneTagDraft[],
): ZoneRemoveImpact {
  const zones = drafts
    .filter((draft) => draft.action === 'REMOVE')
    .map((draft) => draft.zone);
  const impact: ZoneRemoveImpact = {
    ruleCount: 0,
    skuRuleCount: 0,
    spuCount: 0,
    zones,
  };
  const items = preview?.items ?? [];
  const handled = new Set<string>();
  for (const item of items) {
    for (const zone of zones) {
      if (!snapshotInZone(item.before, zone)) continue;
      if (!handled.has(item.spuId)) {
        impact.spuCount += 1;
        handled.add(item.spuId);
      }
      if (snapshotHasZoneRule(item.before, zone)) impact.ruleCount += 1;
      impact.skuRuleCount += item.before?.skuOverrideCount ?? 0;
    }
  }
  if (items.length === 0) {
    for (const zone of zones) {
      const zoneSummary = preview?.zones?.find((item) => item.zone === zone);
      impact.spuCount += zoneSummary?.existing ?? 0;
      impact.skuRuleCount += zoneSummary?.withSkuOverride ?? 0;
    }
  }
  return impact;
}

/** 移出影响范围确认文案 */
export function buildRemoveConfirmText(impact: ZoneRemoveImpact): string {
  const zoneText =
    impact.zones.length > 0
      ? impact.zones.map((zone) => zoneLabel(zone)).join('、')
      : '专区';
  const details = [
    `将把 ${impact.spuCount} 件商品从${zoneText}移出`,
    `清空 ${impact.ruleCount} 条 SPU 规则`,
    `清空 ${impact.skuRuleCount} 条 SKU 精调`,
  ];
  return `${details.join('，')}；移出后 C 端立即不再展示该专区价格。是否继续？`;
}

/** SKU 回显 → 行编辑模型（hasOverride/标签/规则字段） */
export function toSkuEditRow(sku: ZoneSkuTag): ZoneSkuEditRow {
  const memberOverride =
    isPresent(sku.isMemberItem) ||
    isPresent(sku.memberDiscountRate) ||
    isPresent(sku.memberPriceFixed) ||
    isPresent(sku.memberPriceFloor);
  const clearanceOverride =
    isPresent(sku.isClearanceItem) ||
    isPresent(sku.clearancePrice) ||
    isPresent(sku.clearanceDiscountRate) ||
    isPresent(sku.clearanceExpireTime);
  return {
    clearanceDiscountRate: sku.clearanceDiscountRate ?? undefined,
    clearanceExpireTime: sku.clearanceExpireTime ?? undefined,
    clearanceOverride,
    clearancePrice: sku.clearancePrice ?? undefined,
    clearanceTag: Number(sku.isClearanceItem) === 0 ? 0 : 1,
    memberDiscountRate: sku.memberDiscountRate ?? undefined,
    memberOverride,
    memberPriceFixed: sku.memberPriceFixed ?? undefined,
    memberPriceFloor: sku.memberPriceFloor ?? undefined,
    memberTag: Number(sku.isMemberItem) === 0 ? 0 : 1,
    originalPrice: sku.originalPrice ?? undefined,
    salesPrice: sku.salesPrice ?? undefined,
    skuId: sku.skuId,
    specsName: sku.specsName,
  };
}

function ruleValue(value: null | number | undefined): null | number {
  return value === null || value === undefined ? null : value;
}

function expireValue(value: null | string | undefined): null | string {
  return value === null || value === undefined || value === '' ? null : value;
}

/**
 * 行编辑模型 → `skus[]` 保存条目（`replaceSkuRules=true` 精确覆盖语义）：
 * - 未开覆盖：所有字段显式 null → 清空历史覆盖、恢复继承；
 * - 覆盖且参与：提交标签 1 + 规则（空=继承）；
 * - 覆盖且排除：标签 0 + 规则 null。
 */
export function toSkuSaveItem(row: ZoneSkuEditRow): ZoneSkuTagSave {
  if (!row.memberOverride && !row.clearanceOverride) {
    return {
      clearanceDiscountRate: null,
      clearanceExpireTime: null,
      clearancePrice: null,
      clearanceTag: null,
      memberDiscountRate: null,
      memberPriceFixed: null,
      memberPriceFloor: null,
      memberTag: null,
      skuId: row.skuId,
    };
  }
  const memberJoined = row.memberOverride && row.memberTag === 1;
  const clearanceJoined = row.clearanceOverride && row.clearanceTag === 1;
  return {
    clearanceDiscountRate: clearanceJoined
      ? ruleValue(row.clearanceDiscountRate)
      : null,
    clearanceExpireTime: clearanceJoined
      ? expireValue(row.clearanceExpireTime)
      : null,
    clearancePrice: clearanceJoined ? ruleValue(row.clearancePrice) : null,
    clearanceTag: row.clearanceOverride ? row.clearanceTag : null,
    memberDiscountRate: memberJoined ? ruleValue(row.memberDiscountRate) : null,
    memberPriceFixed: memberJoined ? ruleValue(row.memberPriceFixed) : null,
    memberPriceFloor: memberJoined ? ruleValue(row.memberPriceFloor) : null,
    memberTag: row.memberOverride ? row.memberTag : null,
    skuId: row.skuId,
  };
}

/** SKU 行编辑模型 → 精确覆盖 `skus[]` */
export function buildSkuSaveItems(
  rows: readonly ZoneSkuEditRow[],
): ZoneSkuTagSave[] {
  return rows.map((row) => toSkuSaveItem(row));
}
