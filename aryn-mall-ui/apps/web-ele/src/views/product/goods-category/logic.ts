/**
 * 类目角标的纯函数工具。
 *
 * 角标不再是写死的「荐/热」，而是运营在「商品管理 → 类目角标」里维护的配置数据。
 * 类目表单的角标下拉、类目列表的角标列、角标配置的预览都要消费
 * `GET /product/goodscategorybadge/list` 的返回结果，这里集中「配置项 → 下拉选项 /
 * key → 样式映射 / rpx → 预览 px」的归一规则，便于单测覆盖停用标记、历史失效 key
 * 保底、图片角标、高度/圆角推导等边界。
 *
 * 尺寸口径与 C 端保持一致：高度/圆角单位是 rpx，文字角标的字号与左右内边距由高度
 * 推导；管理端预览按 750rpx 设计稿等比缩放（1rpx = 0.5px）。
 */

/** 角标配置接口返回项（GET /product/goodscategorybadge/list） */
export interface CategoryBadgeConfig {
  badgeKey?: null | number | string;
  /** 角标文字；图片角标可留空 */
  badgeText?: null | string;
  bgColor?: null | string;
  /** 角标高度（rpx），合法 16-96，缺省/非法回退 28 */
  badgeHeight?: null | number | string;
  /** 圆角（rpx），合法 0-48，缺省/非法回退 4 */
  borderRadius?: null | number | string;
  /** 角标图片地址；空串或非法地址视为无图（回退文字渲染） */
  iconUrl?: null | string;
  status?: null | string;
  textColor?: null | string;
}

/** 列表/预览渲染所需的角标样式（尺寸字段均为归一后的 rpx） */
export interface CategoryBadgeStyle {
  bgColor: string;
  color: string;
  text: string;
  /** 归一后的图片地址，空串表示文字角标 */
  iconUrl: string;
  /** 角标高度（rpx） */
  height: number;
  /** 圆角（rpx） */
  borderRadius: number;
  /** 文字字号（rpx，由高度推导） */
  fontSize: number;
  /** 文字左右内边距（rpx，由高度推导） */
  paddingX: number;
}

/** 类目表单里的角标下拉选项 */
export interface CategoryBadgeOption {
  label: string;
  value: string;
}

/** `badge_type='0'` 表示无角标（与后端约定一致） */
export const NO_BADGE_VALUE = '0';

/** 角标高度缺省值（rpx） */
export const BADGE_HEIGHT_DEFAULT = 28;
/** 角标高度合法范围（rpx） */
export const BADGE_HEIGHT_MIN = 16;
export const BADGE_HEIGHT_MAX = 96;
/** 圆角缺省值（rpx） */
export const BADGE_BORDER_RADIUS_DEFAULT = 4;
/** 圆角合法范围（rpx） */
export const BADGE_BORDER_RADIUS_MIN = 0;
export const BADGE_BORDER_RADIUS_MAX = 48;
/** 图片地址最大长度（与后端 varchar(512) 一致） */
export const BADGE_ICON_URL_MAX_LENGTH = 512;
/** 图片角标颜色非法时的兜底（图片优先渲染，颜色仅用于降级/预览容器） */
export const DEFAULT_BADGE_BG_COLOR = '#00B578';
export const DEFAULT_BADGE_TEXT_COLOR = '#FFFFFF';

const HEX_COLOR_PATTERN = /^#[0-9a-f]{6}$/i;
/** 图片地址只接受 http(s):// 或站内绝对路径（/ 开头） */
const ICON_URL_PATTERN = /^(?:https?:\/\/|\/)/;
/** 由角标高度推导字号的系数与范围（与 C 端口径一致） */
const FONT_SIZE_RATIO = 0.68;
const FONT_SIZE_MIN = 10;
const FONT_SIZE_MAX = 48;
/** 由角标高度推导左右内边距的系数与范围（与 C 端口径一致） */
const PADDING_X_RATIO = 0.18;
const PADDING_X_MIN = 3;
const PADDING_X_MAX = 16;

/**
 * 把数字四舍五入后限制在 [min, max] 内。
 *
 * 后端字段是 int，这里统一 round 避免浮点值（如 27.4）直接透传到样式与提交参数。
 */
function clampInteger(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, Math.round(value)));
}

/**
 * 高度/圆角这类「整数 + 范围」字段的归一：
 * 空串、非数字视为非法 → 回退缺省值；数字越界 → clamp 到合法范围。
 */
function normalizeIntegerInRange(
  value: null | number | string | undefined,
  fallback: number,
  min: number,
  max: number,
): number {
  const raw = String(value ?? '').trim();
  const parsed = raw === '' ? Number.NaN : Number(raw);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  return clampInteger(parsed, min, max);
}

/**
 * 归一化角标 key。
 *
 * 后端 `badge_type` 是 varchar，但历史数据/数字字面量可能以 number 返回，
 * 统一成字符串；空值视为「无角标」，避免表单出现 undefined 选中态。
 */
export function normalizeBadgeKey(
  badgeType: null | number | string | undefined,
): string {
  const key = String(badgeType ?? '').trim();
  return key === '' ? NO_BADGE_VALUE : key;
}

/** 归一角标高度（rpx）：非法值回退 28，越界值 clamp 到 16-96 */
export function normalizeBadgeHeight(
  value: null | number | string | undefined,
): number {
  return normalizeIntegerInRange(
    value,
    BADGE_HEIGHT_DEFAULT,
    BADGE_HEIGHT_MIN,
    BADGE_HEIGHT_MAX,
  );
}

/** 归一圆角（rpx）：非法值回退 4，越界值 clamp 到 0-48 */
export function normalizeBadgeBorderRadius(
  value: null | number | string | undefined,
): number {
  return normalizeIntegerInRange(
    value,
    BADGE_BORDER_RADIUS_DEFAULT,
    BADGE_BORDER_RADIUS_MIN,
    BADGE_BORDER_RADIUS_MAX,
  );
}

/**
 * 归一图片地址：trim 后必须匹配 http(s):// 或 / 开头且长度 ≤512，否则视为无图。
 *
 * 空串是合法状态（文字角标）；非法地址不进样式映射，避免管理端把脏 URL 渲染成
 * 破图，也避免提交时把历史脏数据原样带回后端。
 */
export function normalizeBadgeIconUrl(
  value: null | number | string | undefined,
): string {
  const iconUrl = String(value ?? '').trim();
  if (
    iconUrl === '' ||
    iconUrl.length > BADGE_ICON_URL_MAX_LENGTH ||
    !ICON_URL_PATTERN.test(iconUrl)
  ) {
    return '';
  }
  return iconUrl;
}

/** 由高度推导文字字号（rpx）：clamp(round(height*0.68), 10, 48) */
export function deriveBadgeFontSize(height: number): number {
  return Math.min(
    FONT_SIZE_MAX,
    Math.max(FONT_SIZE_MIN, Math.round(height * FONT_SIZE_RATIO)),
  );
}

/** 由高度推导文字左右内边距（rpx）：clamp(round(height*0.18), 3, 16) */
export function deriveBadgePaddingX(height: number): number {
  return Math.min(
    PADDING_X_MAX,
    Math.max(PADDING_X_MIN, Math.round(height * PADDING_X_RATIO)),
  );
}

/** 角标几何（归一后的 rpx，图片角标只用高度/圆角，文字角标全部使用） */
export interface CategoryBadgeGeometry {
  borderRadius: number;
  fontSize: number;
  height: number;
  paddingX: number;
}

/** 归一配置的尺寸字段并推导字号/内边距，表单预览与样式映射共用同一口径 */
export function resolveBadgeGeometry(
  config: CategoryBadgeConfig | null | undefined,
): CategoryBadgeGeometry {
  const height = normalizeBadgeHeight(config?.badgeHeight);
  return {
    borderRadius: normalizeBadgeBorderRadius(config?.borderRadius),
    fontSize: deriveBadgeFontSize(height),
    height,
    paddingX: deriveBadgePaddingX(height),
  };
}

/**
 * 单条配置 → 渲染样式；无文字且无图、或文字角标颜色非法时返回 null。
 *
 * 图片角标（iconUrl 非空）文字可留空，此时不校验底色/字色合法性：C 端优先渲染
 * 图片，脏颜色只影响降级容器，回退默认绿/白比整条角标消失更合理。
 * 文字角标仍要求底色/字色都是 #RRGGBB，与后端约定一致。
 */
export function buildBadgeStyle(
  config: CategoryBadgeConfig | null | undefined,
): CategoryBadgeStyle | null {
  if (!config) {
    return null;
  }
  const text = String(config.badgeText ?? '').trim();
  const iconUrl = normalizeBadgeIconUrl(config.iconUrl);
  if (text === '' && iconUrl === '') {
    return null;
  }
  const geometry = resolveBadgeGeometry(config);
  const rawBgColor = String(config.bgColor ?? '').trim();
  const rawColor = String(config.textColor ?? '').trim();
  if (iconUrl !== '') {
    return {
      bgColor: HEX_COLOR_PATTERN.test(rawBgColor)
        ? rawBgColor
        : DEFAULT_BADGE_BG_COLOR,
      color: HEX_COLOR_PATTERN.test(rawColor)
        ? rawColor
        : DEFAULT_BADGE_TEXT_COLOR,
      text,
      iconUrl,
      ...geometry,
    };
  }
  if (
    !HEX_COLOR_PATTERN.test(rawBgColor) ||
    !HEX_COLOR_PATTERN.test(rawColor)
  ) {
    return null;
  }
  return { bgColor: rawBgColor, color: rawColor, text, iconUrl, ...geometry };
}

/**
 * 配置项 → 列表渲染样式映射（key 为归一化后的 badgeKey）。
 *
 * 颜色非法的文字角标、文字为空且无图的配置、key 为「无角标」时不进映射，
 * 列表会渲染占位横线，不凭空兜底旧配色（角标样式只认运营配置）。
 */
export function buildBadgeStyleMap(
  list: CategoryBadgeConfig[] | null | undefined,
): Record<string, CategoryBadgeStyle> {
  const map: Record<string, CategoryBadgeStyle> = {};
  for (const item of list ?? []) {
    const key = normalizeBadgeKey(item.badgeKey);
    if (key === NO_BADGE_VALUE) {
      continue;
    }
    const style = buildBadgeStyle(item);
    if (style) {
      map[key] = style;
    }
  }
  return map;
}

/** 从样式映射里解析类目角标；无角标 / 未知 key 返回 null（列表显示占位横线） */
export function resolveBadgeStyle(
  styleMap: null | Record<string, CategoryBadgeStyle> | undefined,
  badgeType: null | number | string | undefined,
): CategoryBadgeStyle | null {
  const key = normalizeBadgeKey(badgeType);
  if (key === NO_BADGE_VALUE) {
    return null;
  }
  return styleMap?.[key] ?? null;
}

/**
 * 配置项 → 类目表单角标下拉选项。
 *
 * - 首项固定「无角标」；
 * - 停用角标也保留（存量类目编辑回显不能丢），文案追加「（已停用）」；
 * - 无文字的角标是图片角标（C 端优先渲染 iconUrl），文案统一用「图片角标」；
 * - 编辑值不在配置里（历史脏值 / 角标已删除）时追加「已失效角标（key）」，
 *   保证下拉能显示当前值且可原样保存，避免静默清零。
 */
export function buildBadgeOptions(
  list: CategoryBadgeConfig[] | null | undefined,
  currentKey?: null | number | string,
): CategoryBadgeOption[] {
  const options: CategoryBadgeOption[] = [
    { label: '无角标', value: NO_BADGE_VALUE },
  ];
  const exists = new Set([NO_BADGE_VALUE]);
  for (const item of list ?? []) {
    const key = normalizeBadgeKey(item.badgeKey);
    if (exists.has(key)) {
      continue;
    }
    exists.add(key);
    const text = String(item.badgeText ?? '').trim() || '图片角标';
    options.push({
      label: item.status === '1' ? `${text}（已停用）` : text,
      value: key,
    });
  }
  const key = normalizeBadgeKey(currentKey);
  if (!exists.has(key)) {
    options.push({ label: `已失效角标（${key}）`, value: key });
  }
  return options;
}

/** rpx → 预览 px（750rpx 设计稿口径，1rpx = 0.5px） */
export function toPreviewPx(rpx: number): number {
  return rpx / 2;
}

/** 角标预览的内联样式（表单弹窗与列表预览共用，尺寸按 rpx/2 缩放） */
export function badgePreviewStyle(
  style: CategoryBadgeStyle,
): Record<string, number | string> {
  const height = `${toPreviewPx(style.height)}px`;
  const borderRadius = `${toPreviewPx(style.borderRadius)}px`;
  if (style.iconUrl !== '') {
    // 图片角标按正方形渲染：宽 = 高
    return { borderRadius, height, width: height };
  }
  return {
    backgroundColor: style.bgColor,
    borderRadius,
    color: style.color,
    fontSize: `${toPreviewPx(style.fontSize)}px`,
    height,
    lineHeight: height,
    paddingLeft: `${toPreviewPx(style.paddingX)}px`,
    paddingRight: `${toPreviewPx(style.paddingX)}px`,
  };
}
