import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

const componentsRoot = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-design/components',
);

const retailTypes = [
  'goods-group',
  'goods-ranking',
  'limited-activity',
  'countdown',
  'marketing-entry',
  'shop-info',
] as const;

function readComponent(type: (typeof retailTypes)[number], file: string) {
  return readFileSync(resolve(componentsRoot, type, file), 'utf8');
}

describe('retail admin component source contract', () => {
  it.each(retailTypes)('%s wires a stable preview frame', (type) => {
    const source = readComponent(type, 'index.vue');

    expect(source).toContain('RetailPreviewFrame');
    expect(source).toContain(':common-style="showData.commonStyle"');
    expect(source).toContain(':status=');
  });

  it.each([
    ['goods-group', 'loadGoodsGroup'],
    ['goods-ranking', 'loadGoodsRanking'],
    ['limited-activity', 'loadLimitedActivities'],
    ['shop-info', 'loadShopInfo'],
  ] as const)('%s isolates its async request through %s', (type, loader) => {
    const source = readComponent(type, 'index.vue');

    expect(source).toContain('createRetailPreviewController');
    expect(source).toContain(loader);
    expect(source).toContain('watch(');
  });

  it.each([
    'goods-group',
    'goods-ranking',
    'limited-activity',
    'marketing-entry',
  ] as const)('%s replaces broken images with an icon fallback', (type) => {
    expect(readComponent(type, 'index.vue')).toContain('#error');
  });

  it.each(retailTypes)('%s exposes real settings and common style', (type) => {
    const source = readComponent(type, 'setting.vue');

    expect(source).toContain("'update:modelValue'");
    expect(source).toContain('CommonStyle');
    expect(source).toContain('cloneDesignerValue');
    expect(source).toContain('emptyStrategy');
    expect(source).toContain('invalidStrategy');
    expect(source).toContain('isSameDesignerValue');
    expect(source).toContain('watch(');
  });

  it('uses structured links for every marketing entry', () => {
    expect(readComponent('marketing-entry', 'setting.vue')).toContain(
      'LinkUrl',
    );
  });

  it('shows retail components in the editor component library', () => {
    const source = readFileSync(
      resolve(
        process.cwd(),
        'apps/web-ele/src/views/promotion/page-designer/components/component-library.vue',
      ),
      'utf8',
    );

    expect(source).toContain('retailComponentTypes');
    expect(source).toContain('...retailComponentTypes');
  });
});
