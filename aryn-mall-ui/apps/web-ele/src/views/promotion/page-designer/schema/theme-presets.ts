import type { PageDesignThemePayload } from '#/api/promotion/page-design';

/**
 * 装修主题预设色板与颜色衍生工具。
 *
 * 对标主流商城 SaaS（微盟 8 套预设、有赞全店风格）：预设保证配色效果可控，
 * 自定义色满足品牌色需求。预设只定「品牌主色 + 页面底色」两个高信号字段，
 * 导航保持白底黑字（商家有需求可在表单里自行改）。
 *
 * 衍生口径必须与后端 ThemeColorUtils / C 端 mallThemeStore 一致：
 * 辅色 = 主色向白色混合 35%；预设底色 = 主色向白色混合 92%。
 * 改动任一端都要三端同步，否则 C 端实机与管理端画布会漂移
 * （见 preview-parity.test.ts 的守卫目的）。
 */

export interface ThemePreset {
  /** 预设名（同时用作新建主题的名称预填） */
  name: string;
  /** 主色（品牌色） */
  primaryColor: string;
  /** 页面底色（主色混白 92% 的浅色） */
  pageBackgroundColor: string;
}

/** 内置默认主色/辅色：与 C 端 mallThemeStore.DEFAULT_MALL_THEME、uno.config fallback 同源 */
export const DEFAULT_PRIMARY_COLOR = '#FF2237';
export const DEFAULT_SECONDARY_COLOR = '#FF6B7A';

const HEX_RE = /^#[0-9a-f]{6}$/i;

function isHex(value: string): boolean {
  return HEX_RE.test(value);
}

function parseRgb(color: string): [number, number, number] | null {
  if (!isHex(color)) return null;
  return [
    Number.parseInt(color.slice(1, 3), 16),
    Number.parseInt(color.slice(3, 5), 16),
    Number.parseInt(color.slice(5, 7), 16),
  ];
}

/** 向白色混合（ratio 为白占比 0~1），非法输入返回 null */
export function mixTowardWhite(color: string, ratio: number): null | string {
  const rgb = parseRgb(color);
  if (!rgb) return null;
  const channel = (value: number) =>
    Math.round(value + (255 - value) * ratio)
      .toString(16)
      .padStart(2, '0');
  return `#${channel(rgb[0])}${channel(rgb[1])}${channel(rgb[2])}`.toUpperCase();
}

/** 主色 → 辅色（混白 35%，与后端 ThemeColorUtils.deriveSecondary 同口径） */
export function deriveSecondaryColor(primaryColor: string): string {
  return mixTowardWhite(primaryColor, 0.35) ?? '#FF6B7A';
}

/** 主色 → 页面底色（混白 92%），非法输入回落 #F5F5F5 */
export function derivePageBackgroundColor(primaryColor: string): string {
  return mixTowardWhite(primaryColor, 0.92) ?? '#F5F5F5';
}

/** 预设色板（顺序即展示顺序），底色由主色统一衍生，避免手工色值漂移 */
const PRESET_PRIMARIES: Array<[name: string, primaryColor: string]> = [
  ['活力红', '#FF2237'],
  ['暖橙', '#FF6B35'],
  ['琥珀金', '#F5A623'],
  ['抹茶绿', '#07C160'],
  ['青碧', '#00B8A9'],
  ['天空蓝', '#4D7FFF'],
  ['优雅紫', '#7C4DFF'],
  ['玫粉', '#FF5C8A'],
];

export const THEME_PRESETS: ThemePreset[] = PRESET_PRIMARIES.map(
  ([name, primaryColor]) => ({
    name,
    pageBackgroundColor: derivePageBackgroundColor(primaryColor),
    primaryColor,
  }),
);

/** 由主色一键生成一套主题表单值（导航保持白底黑字） */
export function buildThemePayloadFromPrimary(
  primaryColor: string,
  themeName: string,
): PageDesignThemePayload {
  return {
    navigationColor: '#FFFFFF',
    navigationTextColor: '#222222',
    pageBackgroundColor: derivePageBackgroundColor(primaryColor),
    primaryColor,
    radius: 8,
    themeName,
  };
}
