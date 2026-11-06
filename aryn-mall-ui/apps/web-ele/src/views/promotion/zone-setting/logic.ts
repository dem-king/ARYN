/**
 * 「专区设置」页纯逻辑。
 *
 * 页面只维护两个专区的 4 个字段（开关 / 默认折扣率 / 成本兜底倍率 / C 端排序），
 * 但每个字段在界面上都要翻译成运营能直接读懂的口径（9 折、成本上浮 5%、已同步…）。
 * 这里集中这些易错转换，组件层只负责展示与交互：
 * - 不 import Vue / Element Plus / #/api，vitest 可零副作用直接测；
 * - 入参统一用结构化最小类型，后端 VO 可直接结构化传入；
 * - 空值一律归一成 null（不用 undefined），保证「未设置」「清空」判断唯一。
 */

/** 与后端 zoneType 对齐：member=会员专区，clearance=低价捡漏（出清）专区 */
export type ZoneKey = 'clearance' | 'member';

/** 同步状态徽标语气，只控制圆点颜色，不用于文字着色（保证正文对比度） */
export type SyncTone = 'danger' | 'info' | 'success';

/** 页面表单模型：数字字段 null 表示「未设置/不启用」 */
export interface ZoneFormModel {
  costFloorMultiplier: null | number;
  defaultDiscountRate: null | number;
  enabled: boolean;
  sortMode: string;
}

/** 后端 GET /zone/setting/{zoneType} 的最小结构化类型 */
export interface ZoneSettingLike {
  costFloorMultiplier?: boolean | null | number | string;
  defaultDiscountRate?: boolean | null | number | string;
  enabled?: boolean | null | number | string;
  lastSyncError?: null | string;
  lastSyncStatus?: null | string;
  lastSyncTime?: null | string;
  sortMode?: null | string;
}

/** 保存请求体（字段与后端 MallZoneSetting 对齐，enabled 用 '1'/'0'） */
export interface ZoneSettingPayload {
  costFloorMultiplier: null | number;
  defaultDiscountRate: null | number;
  enabled: string;
  sortMode: string;
}

/** 同步状态展示口径 */
export interface ZoneSyncMeta {
  /** 后端原始状态，未同步过时为空串 */
  error: string;
  label: string;
  status: string;
  time: string;
  tip: string;
  tone: SyncTone;
}

/** 带空态的展示文案：empty 用于区分「未设置」样式（虚线 chip） */
export interface ZoneChipText {
  empty: boolean;
  text: string;
}

/** 两个专区的稳定顺序：会员在前（主专区），出清在后 */
export const ZONE_KEYS: readonly ZoneKey[] = ['member', 'clearance'];

/** C 端出清列表排序选项（value 与后端 sortMode 对齐） */
export const SORT_MODE_OPTIONS: Array<{ label: string; value: string }> = [
  { label: '出清价从低到高', value: 'default' },
  { label: '折扣从高到低', value: 'discount_desc' },
];

/** 数值化：空值/非法值返回 null，避免 0 与「未设置」混淆 */
export function toNumber(value: unknown): null | number {
  if (value === null || value === undefined) {
    return null;
  }
  const text = typeof value === 'string' ? value.trim() : value;
  if (text === '') {
    return null;
  }
  const num = typeof text === 'number' ? text : Number(text);
  return Number.isFinite(num) ? num : null;
}

/**
 * 开关归一：后端未配置时返回默认开启；兼容 '0'/'1'、0/1、true/false。
 * 不能直接用 Boolean('0')（结果是 true），这是历史最容易踩的坑。
 */
export function isZoneEnabled(value: unknown): boolean {
  if (value === null || value === undefined || value === '') {
    return true;
  }
  if (typeof value === 'boolean') {
    return value;
  }
  const text = String(value).trim().toLowerCase();
  return !(text === '0' || text === 'false' || text === 'off');
}

/** 全新表单（加载失败/尚未加载时的占位） */
export function createEmptyZoneForm(): ZoneFormModel {
  return {
    enabled: true,
    defaultDiscountRate: null,
    costFloorMultiplier: null,
    sortMode: 'default',
  };
}

/** 后端返回值 → 表单模型 */
export function normalizeZoneForm(
  data?: null | Partial<ZoneSettingLike>,
): ZoneFormModel {
  return {
    enabled: isZoneEnabled(data?.enabled),
    defaultDiscountRate: toNumber(data?.defaultDiscountRate),
    costFloorMultiplier: toNumber(data?.costFloorMultiplier),
    sortMode: String(data?.sortMode ?? '').trim() || 'default',
  };
}

/** 表单模型 → 保存请求体（与改造前页面提交的字段完全一致） */
export function buildZoneSettingPayload(
  form: ZoneFormModel,
): ZoneSettingPayload {
  return {
    enabled: form.enabled ? '1' : '0',
    defaultDiscountRate: form.defaultDiscountRate ?? null,
    costFloorMultiplier: form.costFloorMultiplier ?? null,
    sortMode: form.sortMode,
  };
}

/** 与加载快照逐字段比较，决定是否允许保存 */
export function isZoneFormDirty(
  current: ZoneFormModel,
  baseline: ZoneFormModel,
): boolean {
  return (
    current.enabled !== baseline.enabled ||
    (current.defaultDiscountRate ?? null) !==
      (baseline.defaultDiscountRate ?? null) ||
    (current.costFloorMultiplier ?? null) !==
      (baseline.costFloorMultiplier ?? null) ||
    current.sortMode !== baseline.sortMode
  );
}

/** 去掉多余小数位：9 → '9'，8.5 → '8.5'，8.55 → '8.55' */
function trimNumber(num: number, fractionDigits = 2): string {
  return String(Number(num.toFixed(fractionDigits)));
}

/**
 * 折扣率文案：0.9 → '9 折'，0.855 → '8.55 折'；空/越界返回 null。
 * 折扣率合法区间是 (0, 1]，1 表示不打折。
 */
export function formatDiscountRate(value: unknown): null | string {
  const num = toNumber(value);
  if (num === null || num <= 0 || num > 1) {
    return null;
  }
  // 保留 3 位小数：输入下限 0.0001（≈0.001 折）不能被舍成「0 折」
  return `${trimNumber(num * 10, 3)} 折`;
}

/** 折扣率 chip：有值展示「≈ 9 折」，空值交给调用方传专区专属的「未设置」文案 */
export function describeDiscountChip(
  value: unknown,
  emptyText: string,
): ZoneChipText {
  const rate = formatDiscountRate(value);
  return {
    empty: rate === null,
    text: rate === null ? emptyText : `≈ ${rate}`,
  };
}

/**
 * 成本兜底倍率 chip：1.05 → '成本上浮 5%'，1 → '按成本价兜底'，
 * 0.9 → '成本下浮 10%'（低于 1 是异常配置，仍如实展示避免运营误解）。
 */
export function describeCostFloorChip(value: unknown): ZoneChipText {
  const multiplier = toNumber(value);
  if (multiplier === null) {
    return { empty: true, text: '未启用' };
  }
  if (multiplier === 1) {
    return { empty: false, text: '按成本价兜底' };
  }
  if (multiplier > 1) {
    return {
      empty: false,
      text: `成本上浮 ${trimNumber((multiplier - 1) * 100)}%`,
    };
  }
  return {
    empty: false,
    text: `成本下浮 ${trimNumber((1 - multiplier) * 100)}%`,
  };
}

/** 同步时间展示：yyyy-MM-dd HH:mm；空值显示「—」，避免出现 Invalid Date */
export function formatSyncTime(value: unknown): string {
  const text = String(value ?? '')
    .trim()
    .replace('T', ' ');
  if (!text) {
    return '—';
  }
  return text.length >= 16 ? text.slice(0, 16) : text;
}

/**
 * 同步状态展示口径：已同步 / 同步失败 / 未同步。
 * 三态文案与提示统一在这里，避免组件里出现两份不一致的映射。
 */
export function resolveZoneSyncMeta(
  data?: null | Partial<ZoneSettingLike>,
): ZoneSyncMeta {
  const status = String(data?.lastSyncStatus ?? '')
    .trim()
    .toUpperCase();
  const error = String(data?.lastSyncError ?? '').trim();
  const time = formatSyncTime(data?.lastSyncTime);
  if (status === 'SUCCESS') {
    return {
      error,
      label: '已同步',
      status,
      time,
      tip: '专区价已同步到 C 端；保存设置后会重新计算并再次同步。',
      tone: 'success',
    };
  }
  if (status === 'FAILED') {
    return {
      error,
      label: '同步失败',
      status,
      time,
      tip:
        error || '上次价格同步失败；可点击「重试同步」或重新保存设置触发同步。',
      tone: 'danger',
    };
  }
  return {
    error,
    label: '未同步',
    status,
    time,
    tip: '还没有成功同步过价格；保存设置后会立即触发一次同步。',
    tone: 'info',
  };
}
