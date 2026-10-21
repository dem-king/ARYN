import type { DiyCommonStyle } from '@vben/types';

/**
 * 通用样式圆角 > 0 时给根节点补 `overflow: hidden`。
 *
 * 贴满根节点的图片/色块的直角会盖住圆角弧线，运营在画布上看不到自己设置的
 * 圆角——与 C 端 `utils/common-style.ts` 的 getCommonStyle 同一守卫；
 * 四角全 0 时返回空对象，不改变既有布局（不误裁子元素阴影等设计内溢出）。
 */
export function commonStyleRadiusOverflow(
  commonStyle?: DiyCommonStyle | null,
): Record<string, string> {
  const radius = [
    commonStyle?.styleLtRadius,
    commonStyle?.styleRtRadius,
    commonStyle?.styleLbRadius,
    commonStyle?.styleRbRadius,
  ];
  return radius.some((value) => Number(value) > 0)
    ? { overflow: 'hidden' }
    : {};
}
