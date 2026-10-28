import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

/**
 * 页面设置「导航标题」与三端导航栏文案的回退口径对齐。
 *
 * 背景（2026-10-21 用户反馈）：画布导航栏上显示「悦航购 · 商城首页」，
 * 右侧「页面设置 → 导航标题」却是空的。根因不是草稿丢字段——三端渲染
 * 都是 `navigation.title || 页面名称`，首页的 `navigation.title` 一直是空的，
 * 画布上的文案是页面名称回退。面板此前没有任何回退说明，运营会误以为
 * 标题没显示或没保存，因此本测试锁定：
 *   1. 面板必须把「留空回退页面名称」写出来，并拿到页面名称；
 *   2. 编辑器必须把 pageName 传给页面设置面板；
 *   3. 三端渲染回退链保持不变，否则面板提示会变成谎话。
 */
const designerRoot = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-designer',
);

function readDesignerFile(relativePath: string) {
  return readFileSync(resolve(designerRoot, relativePath), 'utf8');
}

const panelSource = readDesignerFile('components/page-settings.vue');
const designerSource = readDesignerFile('index.vue');
const canvasSource = readDesignerFile('components/phone-canvas.vue');
const previewSource = readFileSync(
  resolve(
    process.cwd(),
    'apps/web-ele/src/views/promotion/page-preview/components/preview-canvas.vue',
  ),
  'utf8',
);
const mobileSource = readFileSync(
  resolve(process.cwd(), '../aryn-mall-uniapp/src/components/diy/index.vue'),
  'utf8',
);

describe('页面设置：导航标题回退说明', () => {
  it('面板接收页面名称，并把「留空回退页面名称」写在输入框与说明里', () => {
    expect(panelSource).toContain('pageName?: string');
    expect(panelSource).toContain('navigationTitlePlaceholder');
    expect(panelSource).toContain('留空时显示页面名称');
    expect(panelSource).toContain('修改页面名称会同步影响导航文案');
    expect(panelSource).toContain('填写后以导航标题为准');
  });

  it('导航标题输入框仍只绑定 navigation.title，回退文案不会写进草稿', () => {
    expect(panelSource).toContain('v-model="form.navigation.title"');
    expect(panelSource).not.toContain('v-model="navigationTitlePlaceholder"');
  });

  it('编辑器把页面名称传给页面设置面板', () => {
    const panelUsageStart = designerSource.indexOf('<PageSettingsPanel');
    expect(panelUsageStart).toBeGreaterThan(-1);
    const panelUsage = designerSource.slice(
      panelUsageStart,
      designerSource.indexOf('/>', panelUsageStart),
    );
    expect(panelUsage).toContain(':page-name="pageName"');
    expect(panelUsage).toContain(':model-value="designer.document.value.page"');
  });

  it('三端渲染仍是「导航标题优先、留空回退页面名称」', () => {
    expect(canvasSource).toContain('page.navigation.title || pageName');
    expect(previewSource).toContain(
      'document.page.navigation.title || pageName',
    );
    expect(mobileSource).toContain(
      'document.value.page.navigation.title || props.pageName',
    );
  });
});

/**
 * 页面设置「滚动效果」（沉浸式导航）契约。
 *
 * 需求：docs/50-需求文档/2026-10-27-小程序沉浸式导航栏/README.md §4.6.1。
 * 面板只负责把运营意图写进 page.navigation.scrollEffect，渲染由共享解析层负责；
 * 这里锁住「控件齐全、默认值来自归一化函数、内嵌页型禁用、提示不骗人」。
 */
describe('页面设置：导航滚动渐显', () => {
  it('面板接收 pageType，内嵌页型禁用整组控件并提示', () => {
    expect(panelSource).toContain('pageType?: PageDesignType');
    expect(panelSource).toContain('import { isEmbeddedPageType }');
    // 内嵌页型（商详/分类/个人中心）不渲染装修导航栏 → 效果整组禁用
    expect(panelSource).toContain('isEmbeddedPageType(props.pageType)');
    expect(panelSource).toContain('内嵌装修不渲染导航栏');
    // 模式 / 目标底色 / 距离 / 毛玻璃都要带同一份 disabled 绑定
    expect(panelSource).toContain(':disabled="embeddedPage"');
  });

  it('滚动效果组：关闭/沉浸式渐显、三种目标底色、60–260 距离滑杆、毛玻璃', () => {
    expect(panelSource).toContain('label="滚动效果"');
    expect(panelSource).toContain('关闭（固定底色）');
    expect(panelSource).toContain('沉浸式渐显');
    // 目标底色三选一，文案与设计稿一致
    expect(panelSource).toContain('磨砂白（推荐）');
    expect(panelSource).toContain('商城主题导航色');
    expect(panelSource).toContain('value="custom"');
    // 渐显距离用 Element Plus 滑杆，范围 60–260（缺省值由共享归一化给 140）
    expect(panelSource).toContain('ElSlider');
    expect(panelSource).toContain(':min="60"');
    expect(panelSource).toContain(':max="260"');
    expect(panelSource).toContain('v-model="scrollDistanceModel"');
    // 毛玻璃开关只对磨砂白目标底色生效
    expect(panelSource).toContain('毛玻璃');
    expect(panelSource).toContain('v-model="scrollBlurModel"');
  });

  it('scrollEffect 一律经 normalizeNavigationScrollEffect 归一化后写回 form', () => {
    expect(panelSource).toContain(
      "import { normalizeNavigationScrollEffect } from '../schema/navigation-scroll'",
    );
    expect(panelSource).toContain(
      'normalizeNavigationScrollEffect(form.navigation.scrollEffect)',
    );
    expect(panelSource).toContain('form.navigation.scrollEffect =');
    expect(panelSource).toContain('patchScrollEffect');
    // 不允许绕过归一化手写整对象（会丢字段/写入非法枚举）
    expect(panelSource).not.toMatch(/form\.navigation\.scrollEffect\s*=\s*\{/);
  });

  it('提示保存草稿后生效、预览需重新生成 token', () => {
    expect(panelSource).toContain('保存草稿后生效');
    expect(panelSource).toContain('预览需重新生成预览链接');
    expect(panelSource).toContain('旧 token 失效');
  });

  it('编辑器把 pageType 传给页面设置面板', () => {
    const panelUsageStart = designerSource.indexOf('<PageSettingsPanel');
    expect(panelUsageStart).toBeGreaterThan(-1);
    const panelUsage = designerSource.slice(
      panelUsageStart,
      designerSource.indexOf('/>', panelUsageStart),
    );
    expect(panelUsage).toContain(':page-type="pageType"');
  });
});
