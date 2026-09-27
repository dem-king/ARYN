import type { SectionStyle } from './types';

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
  const result: Record<string, number | string | undefined> = {
    backgroundColor: style.backgroundColor || undefined,
    backgroundImage: style.backgroundImage
      ? `url(${style.backgroundImage})`
      : undefined,
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
  if (style.backgroundImage) {
    // 区块背景图缺这三项时会按浏览器默认平铺重复
    result.backgroundPosition = 'top center';
    result.backgroundRepeat = 'no-repeat';
    result.backgroundSize = '100% auto';
  }
  if (style.sticky) {
    result.position = 'sticky';
    result.top = '0';
    if (options.stickyZIndex !== undefined) {
      result.zIndex = options.stickyZIndex;
    }
  }
  return result;
}
