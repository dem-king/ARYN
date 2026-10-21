import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

const componentsRoot = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-design/components',
);

/** 复用共用设置面板的 extension 组件（image-cube 自带独立设置面板） */
const extensionPanelTypes = [
  'bottom-nav',
  'coupon-combo',
  'goods-recommend',
  'goods-tabs',
  'goods-waterfall',
  'member-benefits',
  'service-promise',
  'video-live',
] as const;

/**
 * extension 装修组件的设置面板契约。
 *
 * 背景：样式编辑已从各组件的内容设置面板拆分到属性面板的「样式」tab。
 * extension 组件的内容设置只保留数据/内容字段，通用样式统一由
 * property-panel.vue 渲染；底部导航的背景/文字/选中色归属独立样式面板。
 */
describe('extension admin component settings contract', () => {
  it('共用内容设置面板不再重复挂载通用样式编辑器', () => {
    const source = readFileSync(
      resolve(componentsRoot, 'extension/extension-settings.vue'),
      'utf8',
    );

    expect(source).not.toContain('CommonStyle');
    expect(source).not.toContain('form.commonStyle');
    expect(source).toContain("'update:modelValue'");
  });

  it.each(extensionPanelTypes)('%s 的设置入口转发到共用设置面板', (type) => {
    const source = readFileSync(
      resolve(componentsRoot, 'extension', type, 'setting.vue'),
      'utf8',
    );

    expect(source).toContain('ExtensionSettings');
  });

  it('底部导航的背景/文字/选中色归属独立样式面板', () => {
    const source = readFileSync(
      resolve(componentsRoot, 'extension/bottom-nav/style-setting.vue'),
      'utf8',
    );

    expect(source).toContain('backgroundColor');
    expect(source).toContain('textColor');
    expect(source).toContain('activeColor');
  });

  it('图片魔方内容面板不再重复挂载通用样式编辑器', () => {
    const source = readFileSync(
      resolve(componentsRoot, 'extension/image-cube/setting.vue'),
      'utf8',
    );

    expect(source).not.toContain('CommonStyle');
    expect(source).not.toContain('commonStyle');
  });
});
