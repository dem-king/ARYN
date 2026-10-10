import type { PageSettings } from './types';

/**
 * 页面有效主题色解析：页面级主题快照 > 商城默认主题 > 页面自存配色。
 *
 * 装修模板/存量页面常把品牌色写死在 page 配置里（如模板种子的蓝色导航
 * #1543e8），此前页面背景和导航栏只读页面配置，导致管理端换了商城默认
 * 主题后首页仍显示旧颜色。规则：
 *   · 页面 followMallTheme === false（页面设置关闭「跟随商城主题」）——无视
 *     theme 入参，页面自存配色直接生效（自定义配色脱离全局换肤）；
 *   · 页面引用过主题（发布固化为 themeSnapshot）——快照颜色已由 migrate
 *     合并进 page 配置，调用方传 theme=undefined，保持发布时视觉；
 *   · 页面未引用主题——传商城默认主题（mallThemeStore）覆盖页面配色，
 *     字段为空串时逐字段回落页面自存值。
 * 管理端画布/预览使用同一函数（见 page-designer/schema/effective-theme.ts），
 * 两端函数体必须逐字一致，由管理端 preview-parity.test.ts 守卫。
 */
/* EFFECTIVE-THEME-BEGIN */
export interface PageThemeColors {
  navigationColor: string;
  navigationTextColor: string;
  pageBackgroundColor: string;
}

export interface EffectivePageTheme {
  backgroundColor: string;
  navigationBackgroundColor: string;
  navigationTextColor: string;
}

export function resolveEffectivePageTheme(
  page: PageSettings,
  theme: PageThemeColors | undefined,
): EffectivePageTheme {
  // 页面关闭「跟随商城主题」时无视主题入参，页面自存配色直接生效；
  // 字段缺省视为跟随，存量页面与模板不受影响
  const followTheme = page.followMallTheme !== false;
  return {
    backgroundColor: followTheme
      ? theme?.pageBackgroundColor || page.backgroundColor
      : page.backgroundColor,
    navigationBackgroundColor: followTheme
      ? theme?.navigationColor || page.navigation.backgroundColor
      : page.navigation.backgroundColor,
    navigationTextColor: followTheme
      ? theme?.navigationTextColor || page.navigation.textColor
      : page.navigation.textColor,
  };
}
/* EFFECTIVE-THEME-END */
