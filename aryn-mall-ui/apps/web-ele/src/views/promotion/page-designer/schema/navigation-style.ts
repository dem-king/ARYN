import type { EffectivePageTheme } from './effective-theme';
import type { DecorationSection, PageSettings } from './types';

import {
  buildSectionGradient,
  getSectionGradientAnchorColor,
  isHorizontalSectionGradient,
} from './section-style';

/**
 * 导航栏背景解析：让「导航栏」与「首个区块」共用一份背景来源。
 *
 * 装修器里导航栏背景与区块背景是两块独立配置，运营想让导航和首屏区块
 * 连成一条渐变，只能手动把导航色设成区块渐变起始色，改一次区块就要再对
 * 一次色。这里把「跟随首个区块」抽成纯函数，管理端画布、草稿预览与 C 端
 * diy 渲染器共用同一口径（由 preview-parity.test.ts 的 NAVIGATION-FOLLOW
 * 标记块守卫两端逐字一致）。
 *
 * 规则：
 *   · backgroundMode !== 'followFirstSection' —— 原样沿用有效主题导航色；
 *   · 跟随首个「有内容」的区块（C 端会过滤空区块，管理端画布也不渲染其背景）：
 *     有渐变时取顶边起色；to right 渐变整段复用到导航栏，接缝逐列同色；
 *     上下/斜向渐变取起始色，上下方向可与区块完全无缝；
 *   · 仅有背景图的区块无法在导航栏内连续呈现，仍用主题/自定义色兜底；
 *   · 文字颜色默认按背景亮度自动取黑/白，避免跟随深色渐变后白字看不清。
 */
/* NAVIGATION-FOLLOW-BEGIN */
export interface NavigationAppearance {
  backgroundColor: string;
  /** 左右方向渐变复用段；其余方向为空，导航栏用纯色顶边起色 */
  backgroundImage?: string;
  textColor: string;
}

/** 只解析 hex 与 rgb/rgba 的 sRGB 通道；非法值返回 null，由调用方回落主题文字色 */
function parseNavigationColor(value: string): [number, number, number] | null {
  const color = value.trim();
  const hex = /^#([0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$/i.exec(color);
  if (hex) {
    const raw = hex[1] ?? '';
    let base = raw.slice(0, 6);
    if (raw.length <= 4) {
      base = '';
      for (let index = 0; index < 3; index += 1) {
        const char = raw[index] ?? '';
        base += char + char;
      }
    }
    const red = Number.parseInt(base.slice(0, 2), 16);
    const green = Number.parseInt(base.slice(2, 4), 16);
    const blue = Number.parseInt(base.slice(4, 6), 16);
    if (
      !Number.isFinite(red) ||
      !Number.isFinite(green) ||
      !Number.isFinite(blue)
    ) {
      return null;
    }
    return [red, green, blue];
  }
  const rgb = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)/i.exec(color);
  if (!rgb) return null;
  const red = Number(rgb[1]);
  const green = Number(rgb[2]);
  const blue = Number(rgb[3]);
  if (
    !Number.isFinite(red) ||
    !Number.isFinite(green) ||
    !Number.isFinite(blue) ||
    red < 0 ||
    green < 0 ||
    blue < 0 ||
    red > 255 ||
    green > 255 ||
    blue > 255
  ) {
    return null;
  }
  return [red, green, blue];
}

/** 按背景亮度自动选可读文字色：浅底深字、深底白字（阈值 0.6） */
export function pickReadableNavigationTextColor(
  backgroundColor: string,
  fallback: string,
): string {
  const rgb = parseNavigationColor(backgroundColor);
  if (!rgb) return fallback;
  const luminance = (rgb[0] * 0.299 + rgb[1] * 0.587 + rgb[2] * 0.114) / 255;
  return luminance > 0.6 ? '#222222' : '#FFFFFF';
}

/**
 * 按背景亮度自动选可读图标色：浅底深图标、深底白图标（阈值 0.6，与导航
 * 文字同口径）。搜索栏铃铛/扫码这类直接坐在区块背景上的图标消费：深色
 * 头部背景上恒用深色图标会被误读为不可点击。
 */
export function pickReadableIconColor(
  backgroundColor: string,
  fallback: string,
): string {
  const rgb = parseNavigationColor(backgroundColor);
  if (!rgb) return fallback;
  const luminance = (rgb[0] * 0.299 + rgb[1] * 0.587 + rgb[2] * 0.114) / 255;
  return luminance > 0.6 ? '#333333' : '#FFFFFF';
}

/** 导航栏有效外观：自定义色（缺省）或跟随首个区块背景 */
export function resolveNavigationAppearance(
  page: PageSettings,
  theme: EffectivePageTheme,
  sections: DecorationSection[],
): NavigationAppearance {
  const fallback: NavigationAppearance = {
    backgroundColor: theme.navigationBackgroundColor,
    textColor: theme.navigationTextColor,
  };
  if (page.navigation.backgroundMode !== 'followFirstSection') return fallback;
  let section: DecorationSection | undefined;
  for (const item of sections) {
    if (item.components.length > 0) {
      section = item;
      break;
    }
  }
  if (!section) return fallback;
  // 背景图优先于渐变的区块口径见 section-style：只有没有背景图时才取渐变
  const gradient = section.style.backgroundImage
    ? undefined
    : section.style.backgroundGradient;
  const anchor =
    getSectionGradientAnchorColor(gradient) || section.style.backgroundColor;
  if (!anchor || anchor === 'transparent') return fallback;
  const backgroundImage = isHorizontalSectionGradient(gradient)
    ? buildSectionGradient(gradient)
    : undefined;
  const autoTextColor = page.navigation.textColorAuto !== false;
  return {
    backgroundColor: anchor,
    ...(backgroundImage ? { backgroundImage } : {}),
    textColor: autoTextColor
      ? pickReadableNavigationTextColor(anchor, theme.navigationTextColor)
      : page.navigation.textColor || theme.navigationTextColor,
  };
}
/* NAVIGATION-FOLLOW-END */
