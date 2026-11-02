import type { PageDesignTheme } from '#/api/promotion/page-design';

import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

import {
  buildCanvasThemeVars,
  deriveSecondaryColor,
  THEME_PRESETS,
} from '../page-designer/schema/theme-presets';
import {
  PREVIEW_TABBAR_ACTIVE_KEY,
  PREVIEW_TABBAR_ITEMS,
  resolvePreviewColors,
  resolvePreviewTheme,
  resolvePreviewThemeLabel,
} from './preview-theme';

/**
 * 商城主题页（mall-theme）效果预览契约。
 *
 * 本页曾出过两类「截图才发现」的事故，都用静态契约钉死：
 *
 * 1. **预览手机壳底栏漏项**。C 端 pages.json 的 tabBar 是 4 项
 *    （首页/分类/购物车/我的），管理端手机壳曾只画 3 项（少「购物车」），
 *    运营据此以为 C 端底栏只有分类/我的。这里直接读 C 端 pages.json 比对，
 *    C 端增删 tab 时测试先红，避免两端长期漂移。
 * 2. **预览与主题/布局脱钩**。换肤要「实时」：点主题卡片 → 手机壳底色、
 *    tabBar 选中色、画布主题变量必须同时刷新，且默认主题标注不能因为
 *    点选而丢失「（商城默认）」后缀；手机壳 375:812 等比缩放、屏体内可滚动
 *    是「预览像真机」的前提，布局契约也不允许被顺手改掉。
 *
 * 只读源码/纯函数，不挂载整页组件（异步画布 + 接口依赖会让单测很脆）。
 */

/** 仓库根可能在不同工作目录下跑测试：两套候选路径取存在的那个 */
function findRepoFile(relativeToUi: string, relativeToRepo: string) {
  const candidates = [
    resolve(process.cwd(), relativeToUi),
    resolve(process.cwd(), relativeToRepo),
  ];
  const found = candidates.find((path) => existsSync(path));
  if (!found) throw new Error(`找不到文件：${candidates.join(' / ')}`);
  return found;
}

const themeIndexPath = findRepoFile(
  'apps/web-ele/src/views/promotion/mall-theme/index.vue',
  'aryn-mall-ui/apps/web-ele/src/views/promotion/mall-theme/index.vue',
);
const pagesJsonPath = findRepoFile(
  '../aryn-mall-uniapp/src/pages.json',
  'aryn-mall-uniapp/src/pages.json',
);

const themeIndex = readFileSync(themeIndexPath, 'utf8');
const pagesJson = readFileSync(pagesJsonPath, 'utf8');

/** 取最后一个 <style> 块并剥掉注释：说明性注释会引用旧写法，断言只针对真实声明 */
function styleBlock(source: string) {
  const matches = [...source.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)];
  const block = matches.at(-1)?.[1] ?? '';
  return block.replaceAll(/\/\*[\s\S]*?\*\//g, '');
}

function styleRule(source: string, selector: string) {
  const escaped = selector.replaceAll(/[.*+?^${}()|[\]\\]/g, String.raw`\$&`);
  return new RegExp(`${escaped}\\s*\\{([^}]*)\\}`).exec(source)?.[1] ?? '';
}

const styles = styleBlock(themeIndex);

/** 从 C 端 pages.json 取 tabBar 文案列表（文件由 uni-pages 生成，只有行级 // 注释） */
function readCTabBarTexts(source: string): string[] {
  // tabBar 是 pages.json 最后一个配置块：从它开始截取到文件尾再取 text，
  // 避免按花括号匹配时被第一项自身的 `}` 提前截断
  const tabBarStart = source.indexOf('"tabBar"');
  if (tabBarStart === -1) throw new Error('pages.json 未找到 tabBar 配置');
  const tabBarBlock = source.slice(tabBarStart);
  return [...tabBarBlock.matchAll(/"text"\s*:\s*"([^"]+)"/g)].map(
    (match) => match[1] ?? '',
  );
}

function makeTheme(overrides: Partial<PageDesignTheme>): PageDesignTheme {
  return {
    id: 'theme-default',
    mallDefaultFlag: '0',
    navigationColor: '#FFFFFF',
    navigationTextColor: '#222222',
    pageBackgroundColor: '#F5F5F5',
    primaryColor: '#FF5500',
    radius: 8,
    themeName: '测试',
    ...overrides,
  };
}

/** 线上真实形态：天空蓝（普通）+ 测试（商城默认） */
const SKY_THEME = makeTheme({
  id: 'theme-sky',
  pageBackgroundColor: '#F1F5FF',
  primaryColor: '#4D7FFF',
  themeName: '天空蓝',
});
const DEFAULT_THEME = makeTheme({ id: 'theme-default', mallDefaultFlag: '1' });

describe('预览手机壳 tabBar 与 C 端 pages.json 同序', () => {
  it('按 首页 → 分类 → 购物车 → 我的 补齐 4 项（此前漏「购物车」）', () => {
    expect(PREVIEW_TABBAR_ITEMS.map((item) => item.label)).toEqual([
      '首页',
      '分类',
      '购物车',
      '我的',
    ]);
    // 预览的是商城首页：选中项固定首页
    expect(PREVIEW_TABBAR_ACTIVE_KEY).toBe('home');
    expect(PREVIEW_TABBAR_ITEMS.map((item) => item.key)).toContain(
      PREVIEW_TABBAR_ACTIVE_KEY,
    );
  });

  it('文案/顺序逐项等于 C 端 tabBar（C 端改了这里会先红）', () => {
    expect(PREVIEW_TABBAR_ITEMS.map((item) => item.label)).toEqual(
      readCTabBarTexts(pagesJson),
    );
  });

  it('模板用 v-for 渲染整个常量，不内联写死条目（否则改常量不生效）', () => {
    expect(themeIndex).toContain('v-for="item in PREVIEW_TABBAR_ITEMS"');
    expect(themeIndex).toContain('item.key === PREVIEW_TABBAR_ACTIVE_KEY');
    // 底栏 4 个 span 都来自循环：真实模板里不应再有「写死三项」的 tabbar span
    const tabbarBlock =
      /<div class="preview-tabbar">([\s\S]*?)<\/div>/.exec(themeIndex)?.[1] ??
      '';
    const spanCount = [...tabbarBlock.matchAll(/<span\b/g)].length;
    expect(spanCount, 'tabbar 模板应只有一个 v-for 循环 span').toBe(1);
  });
});

describe('预览主题换肤：选主题 / 标注 / 取色', () => {
  it('预览主题优先级：选中行 > 商城默认 > 预设兜底', () => {
    expect(
      resolvePreviewTheme([SKY_THEME, DEFAULT_THEME], 'theme-sky').id,
    ).toBe('theme-sky');
    // 未选中（刚进页面）：展示线上生效的商城默认主题
    expect(resolvePreviewTheme([SKY_THEME, DEFAULT_THEME], '')).toMatchObject({
      id: 'theme-default',
      mallDefaultFlag: '1',
    });
    // 主题库为空/都不可用时用首个预设兜底，预览不空转
    const firstPreset = THEME_PRESETS[0];
    expect(firstPreset).toBeDefined();
    const fallback = resolvePreviewTheme([], '');
    expect(fallback.primaryColor).toBe(firstPreset?.primaryColor);
    expect(fallback.themeName).toBe(firstPreset?.name);
  });

  it('商城默认标注：未选中时带「（商城默认）」后缀', () => {
    expect(resolvePreviewThemeLabel([SKY_THEME, DEFAULT_THEME], '')).toBe(
      '测试（商城默认）',
    );
  });

  it('商城默认被显式点选后仍带后缀（不能退化成裸主题名）', () => {
    expect(
      resolvePreviewThemeLabel([SKY_THEME, DEFAULT_THEME], 'theme-default'),
    ).toBe('测试（商城默认）');
  });

  it('非默认主题只显示主题名；无任何主题时标注预设示例', () => {
    expect(
      resolvePreviewThemeLabel([SKY_THEME, DEFAULT_THEME], 'theme-sky'),
    ).toBe('天空蓝');
    expect(resolvePreviewThemeLabel([], '')).toBe('预设示例');
  });

  it('手机壳底色取页面背景色、tabBar 选中色取主色，空值回落内置默认', () => {
    expect(resolvePreviewColors(SKY_THEME)).toEqual({
      background: '#F1F5FF',
      primary: '#4D7FFF',
    });
    const blank = makeTheme({
      pageBackgroundColor: '',
      primaryColor: '',
    });
    expect(resolvePreviewColors(blank)).toEqual({
      background: '#F5F5F5',
      primary: '#FF2237',
    });
  });

  it('换肤走画布主题变量：主色/辅色/底色与主题行同口径（两侧不会漂移）', () => {
    const vars = buildCanvasThemeVars(SKY_THEME);
    expect(vars.primaryColor).toBe(SKY_THEME.primaryColor);
    expect(vars.pageBackgroundColor).toBe(SKY_THEME.pageBackgroundColor);
    expect(vars.secondaryColor).toBe(deriveSecondaryColor('#4D7FFF'));
  });
});

describe('预览画布接线：主题变量 / 缩放 / 滚动', () => {
  it('手机壳预览把预览主题变量注入 PreviewCanvas（点卡片即重算换肤）', () => {
    expect(themeIndex).toContain(':theme-vars="previewThemeVars"');
    expect(themeIndex).toContain('buildCanvasThemeVars(previewTheme.value)');
    // 画布高度随内容收缩：手机屏体自己滚动，而不是整页 100dvh
    expect(themeIndex).toContain(':full-height="false"');
    expect(themeIndex).toContain('page-type="1"');
    // 导航标题与 C 端同源：page.navigation.title || pageName（首页快照标题为空
    // 时展示页名「悦航购 · 商城首页」），不能再硬编码泛化的「商城首页」。
    expect(themeIndex).toContain(':page-name="homePageName"');
    expect(themeIndex).not.toContain('page-name="商城首页"');
    expect(themeIndex).toContain('homePage.value?.pageName');
    // 手机壳底色 / tabBar 选中色跟随预览主题
    expect(themeIndex).toMatch(/background:\s*previewColors\.background/);
    expect(themeIndex).toMatch(/color:\s*previewColors\.primary/);
    expect(themeIndex).toMatch(/background:\s*previewColors\.primary/);
  });

  it('手机屏体按 375 设计宽用 zoom 等比缩放（zoom 参与布局，sticky 不被破坏）', () => {
    expect(themeIndex).toContain('screenWidth.value / 375');
    // 两处 zoom：画布 + 模拟 tabBar
    expect([...themeIndex.matchAll(/zoom:\s*previewZoom/g)]).toHaveLength(2);
    // CSS 保证 375:812 真机比例 + 屏体内部滚动
    expect(styleRule(styles, '.preview-phone')).toMatch(
      /aspect-ratio:\s*375\s*\/\s*812/,
    );
    expect(styleRule(styles, '.preview-phone')).toMatch(
      /max-width:\s*calc\(375px \+ 12px\)/,
    );
    expect(styleRule(styles, '.preview-screen')).toMatch(
      /overflow:\s*hidden auto/,
    );
  });

  it('布局吃满内容区：grid 两栏 + 高度 calc(100vh - 130px)，为等比缩放留出空间', () => {
    expect(styleRule(styles, '.theme-body')).toContain('calc(100vh - 130px)');
    expect(styleRule(styles, '.theme-body')).toContain(
      'grid-template-columns: minmax(520px, 1fr) 415px',
    );
    expect(styleRule(styles, '.preview-stage')).toMatch(/flex:\s*1/);
    expect(styleRule(styles, '.preview-stage')).toMatch(
      /justify-content:\s*center/,
    );
  });

  it('大屏手机壳不越界：宽度取 min(舞台宽, 舞台高×375/812) 再等比反推高度', () => {
    // 1920×1080 曾出现：flex 把手机壳高度拉到舞台高、宽度又被 max-width 卡在
    // 387px，结果横向溢出 4px 且 375:812 比例失真。舞台作为 size 容器后，
    // 宽度 = min(100%, 100cqh × 375/812)，再由 aspect-ratio 反推高度，两轴都不越界。
    expect(styleRule(styles, '.preview-stage')).toMatch(
      /container-type:\s*size/,
    );
    expect(styleRule(styles, '.preview-stage')).toMatch(
      /align-items:\s*center/,
    );
    expect(styleRule(styles, '.preview-phone')).toMatch(
      /width:\s*min\(100%,\s*calc\(100cqh \* 375 \/ 812\)\)/,
    );
    // 1:1 真机宽上限保留：再大的窗口手机壳也不会超过 387px（375 + 6×2 边框）
    expect(styleRule(styles, '.preview-phone')).toMatch(
      /max-width:\s*calc\(375px \+ 12px\)/,
    );
  });
});

describe('预览提示条：如实说明首页不跟随商城主题的两种例外', () => {
  it('关闭「跟随商城主题」时提示页面自存配色', () => {
    expect(themeIndex).toContain(
      'homeDocument.value?.page.followMallTheme !== false',
    );
    expect(themeIndex).toContain(
      '该首页已关闭「跟随商城主题」，实际显示页面自存配色',
    );
  });

  it('页面自带固化主题（本页主题快照）时提示显示页面固化配色', () => {
    expect(themeIndex).toContain(
      'Boolean(homeDocument.value?.themePrimaryColor)',
    );
    expect(themeIndex).toContain(
      '该首页已指定「本页主题」，实际显示页面固化的主题配色',
    );
  });
});
