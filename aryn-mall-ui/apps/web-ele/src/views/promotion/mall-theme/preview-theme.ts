import type {
  PageDesignTheme,
  PageDesignThemePayload,
} from '#/api/promotion/page-design';

import {
  buildThemePayloadFromPrimary,
  THEME_PRESETS,
} from '../page-designer/schema/theme-presets';

/**
 * 商城主题页「效果预览」的展示口径。
 *
 * 本页的核心价值是「所见即 C 端实机」：右侧手机壳 = 商城首页发布版快照 +
 * 当前预览主题换肤 + 模拟 tabBar。这份口径此前全散在 index.vue 的 computed 里，
 * 只能靠肉眼看截图回归；抽成纯函数后由同目录 index.test.ts 直接断言：
 *   · 预览主题优先级：选中行 > 商城默认 > 预设兜底；
 *   · 商城默认标注后缀：无论默认主题是否被显式选中都带「（商城默认）」；
 *   · 手机壳底色 / tabBar 选中色跟随主题主色与页面底色。
 *
 * 注意：颜色衍生、主题变量构建仍在 page-designer/schema/theme-presets，
 * 这里只做「选哪个主题、标什么名字、取哪两个颜色」的展示决策，
 * 不重复实现主题变量解析（避免管理端两侧口径漂移）。
 */

/** 预览主题：完整主题行，或「拿不到任何主题行」时的预设兜底（同形数据） */
export type PreviewTheme = PageDesignThemePayload & { id?: string };

/**
 * 预览手机壳底栏：文案与顺序必须与 C 端 aryn-mall-uniapp/src/pages.json 的
 * tabBar 完全一致（首页 → 分类 → 购物车 → 我的）。
 *
 * 这里刻意不读真实小程序配置、也不接路由：管理端只做「换肤后 tabBar 选中色」
 * 的视觉参照。但结构一旦与真机不同，运营会误以为 C 端底栏只有三项
 * （本页曾漏画「购物车」），所以顺序/文案属于契约，index.test.ts 会读取
 * pages.json 逐项比对。
 */
export const PREVIEW_TABBAR_ITEMS = [
  { key: 'home', label: '首页' },
  { key: 'category', label: '分类' },
  { key: 'cart', label: '购物车' },
  { key: 'user', label: '我的' },
] as const;

/** 预览的是商城首页，底栏选中项固定「首页」；非选中项保持中性色 */
export const PREVIEW_TABBAR_ACTIVE_KEY = 'home';

/**
 * 预览主题：优先选中行（点卡片即看该主题效果），其次商城默认
 * （刚进页面时右边就该展示线上正在生效的配色），都没有时用首个预设兜底。
 */
export function resolvePreviewTheme(
  themes: PageDesignTheme[],
  previewId: string,
): PreviewTheme {
  const picked = themes.find((theme) => theme.id === previewId);
  if (picked) return picked;
  const defaultTheme = themes.find((theme) => theme.mallDefaultFlag === '1');
  if (defaultTheme) return defaultTheme;
  // 预设色板始终非空；这里仍用可选链兜底，避免未来删空预设时预览直接抛错
  const firstPreset = THEME_PRESETS[0];
  return buildThemePayloadFromPrimary(
    firstPreset?.primaryColor ?? '#FF2237',
    firstPreset?.name ?? '活力红',
  );
}

/**
 * 当前预览主题名。商城默认无论是否被显式选中都带「（商城默认）」后缀——
 * 否则运营点一下默认主题卡片，标注就退化成裸主题名，和卡片上「商城默认」
 * 标签、按钮禁用态脱节，看不出预览的到底是哪套线上配色。
 */
export function resolvePreviewThemeLabel(
  themes: PageDesignTheme[],
  previewId: string,
): string {
  const picked = themes.find((theme) => theme.id === previewId);
  const theme = picked ?? themes.find((item) => item.mallDefaultFlag === '1');
  if (!theme) return '预设示例';
  return theme.mallDefaultFlag === '1'
    ? `${theme.themeName}（商城默认）`
    : theme.themeName;
}

/**
 * 预览手机壳底色与 tabBar 选中色：底色取主题页面背景色、选中色取主题主色，
 * 空值逐项回落内置默认（与 buildCanvasThemeVars 的兜底口径一致）。
 */
export function resolvePreviewColors(theme: PreviewTheme): {
  background: string;
  primary: string;
} {
  return {
    background: theme.pageBackgroundColor || '#F5F5F5',
    primary: theme.primaryColor || '#FF2237',
  };
}
