import type { SectionBackgroundGradient, SectionStyle } from './types';

export interface SectionStyleOptions {
  /**
   * 吸顶时的层级，由调用方指定：
   * 编辑器画布用 4（让位给组件操作按钮 z-index 5/8），实机与草稿预览用 10。
   */
  stickyZIndex?: number;
}

/**
 * 把区块样式映射成内联 CSS，供编辑器画布与草稿预览共用。
 *
 * 与 C 端 `aryn-mall-uniapp/src/components/diy/index.vue` 的 `sectionStyle()`
 * 保持同构，两端数值口径均为 **px**（画布 375px 对应小程序 750rpx，即 1rpx = 0.5px），
 * 由 `preview-parity.test.ts` 守卫。改动此处必须同步改 C 端。
 */
export function buildSectionStyle(
  style: SectionStyle,
  options: SectionStyleOptions = {},
): Record<string, number | string | undefined> {
  const hasBackgroundImage = Boolean(style.backgroundImage);
  // 背景图优先；其次渐变；最后纯色。渐变塞进 backgroundImage 而不是
  // background 简写，保留 backgroundColor 作为半透明渐变的垫底色。
  const gradient = hasBackgroundImage
    ? undefined
    : buildSectionGradient(style.backgroundGradient);
  const result: Record<string, number | string | undefined> = {
    backgroundColor: style.backgroundColor || undefined,
    backgroundImage: hasBackgroundImage
      ? `url(${style.backgroundImage})`
      : gradient,
    marginBottom: `${style.marginY}px`,
    marginLeft: `${style.marginX}px`,
    marginRight: `${style.marginX}px`,
    marginTop: `${style.marginY}px`,
    paddingBottom: `${style.paddingY}px`,
    paddingLeft: `${style.paddingX}px`,
    paddingRight: `${style.paddingX}px`,
    paddingTop: `${style.paddingY}px`,
  };
  if (style.radius > 0) {
    result.borderRadius = `${style.radius}px`;
    // 圆角要裁掉子组件溢出的直角背景（轮播图等自带白底），否则四角露白边；
    // 横滑区块靠 overflow-x: auto 滚动，内联裁切会盖掉它，故跳过。
    if (!style.horizontalScroll) {
      result.overflow = 'hidden';
    }
  }
  if (hasBackgroundImage) {
    // 区块背景图缺这三项时会按浏览器默认平铺重复
    result.backgroundPosition = 'top center';
    result.backgroundRepeat = 'no-repeat';
    result.backgroundSize = '100% auto';
  }
  if (style.sticky) {
    result.position = 'sticky';
    // 画布里的页面导航随内容滚动（不是 fixed 覆盖层），吸顶停在画布顶部即可；
    // C 端整页 DIY 的 wd-navbar 是 fixed，渲染器自行让出状态栏+导航高度。
    result.top = '0';
    if (options.stickyZIndex !== undefined) {
      result.zIndex = options.stickyZIndex;
    }
  }
  return result;
}

/* SECTION-GRADIENT-BEGIN */
const SECTION_GRADIENT_DIRECTIONS = new Set([
  '135deg',
  '225deg',
  'to bottom',
  'to right',
]);

/** 只接受合法色值，避免任意字符串进入内联样式；方向同样走白名单 */
function normalizeSectionGradientColor(value: unknown): string {
  if (typeof value !== 'string') return '';
  const color = value.trim();
  if (!color) return '';
  const legal =
    /^(?:#(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})|rgba?\(\s*[\d.%]+\s*(?:,\s*[\d.%]+\s*){2,3}\)|transparent)$/i;
  return legal.test(color) ? color : '';
}

/** 方向白名单归一：非法值统一回落 to right，供渐变预览与导航栏跟随共用 */
function normalizeSectionGradientDirection(value: unknown): string {
  return typeof value === 'string' &&
    SECTION_GRADIENT_DIRECTIONS.has(value.trim())
    ? value.trim()
    : 'to right';
}

/** 区块背景渐变 → CSS linear-gradient；起止色都为空时返回 undefined（回退纯色） */
export function buildSectionGradient(
  gradient?: null | SectionBackgroundGradient,
): string | undefined {
  if (!gradient) return undefined;
  const start = normalizeSectionGradientColor(gradient.startColor);
  const end = normalizeSectionGradientColor(gradient.endColor);
  const from = start || end;
  if (!from) return undefined;
  const to = end || start;
  const direction = normalizeSectionGradientDirection(gradient.direction);
  return `linear-gradient(${direction}, ${from}, ${to})`;
}

/**
 * 区块背景的「顶边起色」：渐变取起始色（只填一端时取那一端），供导航栏
 * 「跟随首个区块」取色；没有渐变时返回空串，由纯色/主题色兜底。
 */
export function getSectionGradientAnchorColor(
  gradient?: null | SectionBackgroundGradient,
): string {
  if (!gradient) return '';
  const start = normalizeSectionGradientColor(gradient.startColor);
  const end = normalizeSectionGradientColor(gradient.endColor);
  return start || end;
}

/** 左右方向（to right）的渐变可以整段复用到导航栏，接缝两侧逐列同色 */
export function isHorizontalSectionGradient(
  gradient?: null | SectionBackgroundGradient,
): boolean {
  return (
    !!gradient &&
    normalizeSectionGradientDirection(gradient.direction) === 'to right'
  );
}
/* SECTION-GRADIENT-END */
