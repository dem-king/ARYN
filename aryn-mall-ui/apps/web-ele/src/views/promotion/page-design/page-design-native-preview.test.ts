import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

/**
 * 生效卡片「原生页面」预览回归保护。
 *
 * 商详/分类/个人中心是「C 端原生页面 + 可选装修区」：没有发布装修时，
 * C 端页面依然存在（商详主体、分类导航、个人中心都照常渲染）。
 * 生效卡片曾对这种槽位直接显示「暂无预览 / 未配置」，运营误以为整页没上线。
 * 未配置的内嵌页型必须回落到与装修画布同构的原生骨架预览。
 */
const promotionRoot = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion',
);

const listSource = readFileSync(
  resolve(promotionRoot, 'page-design/index.vue'),
  'utf8',
);
const thumbSource = readFileSync(
  resolve(promotionRoot, 'page-design/components/effective-page-thumb.vue'),
  'utf8',
);
const previewSource = readFileSync(
  resolve(promotionRoot, 'page-preview/components/preview-canvas.vue'),
  'utf8',
);

describe('page design native fallback preview', () => {
  it('renders the native shell when an embedded slot has no decoration', () => {
    expect(thumbSource).toContain('isEmbeddedPageType(props.pageType)');
    expect(thumbSource).toContain('createDefaultDecorationDocument');
    expect(thumbSource).toContain("'未配置装修'");
  });

  it('keeps the empty embedded slot on the native preview instead of the ghost placeholder', () => {
    expect(listSource).toContain('isEmbeddedPageType(slot.type)');
    expect(listSource).toContain('emptySlotHint(slot)');
    expect(listSource).toContain('C 端展示原生');
  });

  it('renders a visible placeholder for the empty decoration area', () => {
    expect(previewSource).toContain('emptyText');
    expect(previewSource).toContain('--el-border-color-darker');
  });
});
