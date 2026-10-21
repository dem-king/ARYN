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
