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
  'countdown',
  'marketing-entry',
  'shop-info',
  'ship-workbench',
] as const;

function readComponent(type: (typeof retailTypes)[number], file: string) {
  return readFileSync(resolve(componentsRoot, type, file), 'utf8');
}

describe('retail admin component source contract', () => {
  it.each(retailTypes)('%s wires a stable preview frame', (type) => {
    const source = readComponent(type, 'index.vue');

    expect(source).toContain('RetailPreviewFrame');
    // shop-info 传派生的 frameCommonStyle（悬浮压图上移 + 卡片圆角兜底，
    // 源头仍是 showData.commonStyle），其余组件直传配置值
    expect(source).toMatch(
      type === 'shop-info'
        ? /:common-style="frameCommonStyle"/
        : /:common-style="showData\.commonStyle"/,
    );
    expect(source).toContain(':status=');
  });

  it.each([
    ['goods-group', 'loadGoodsGroup'],
    ['goods-ranking', 'loadGoodsRanking'],
    ['shop-info', 'loadShopInfo'],
  ] as const)('%s isolates its async request through %s', (type, loader) => {
    const source = readComponent(type, 'index.vue');

    expect(source).toContain('createRetailPreviewController');
    expect(source).toContain(loader);
    expect(source).toContain('watch(');
  });

  it.each(['goods-group', 'goods-ranking', 'marketing-entry'] as const)(
    '%s replaces broken images with an icon fallback',
    (type) => {
      expect(readComponent(type, 'index.vue')).toContain('#error');
    },
  );

  it.each(retailTypes)('%s exposes real content settings', (type) => {
    const source = readComponent(type, 'setting.vue');

    expect(source).toContain("'update:modelValue'");
    expect(source).toContain('cloneDesignerValue');
    expect(source).toContain('emptyStrategy');
    expect(source).toContain('invalidStrategy');
    expect(source).toContain('isSameDesignerValue');
    expect(source).toContain('watch(');
  });

  it.each(retailTypes)(
    '%s keeps common style out of the content panel',
    (type) => {
      expect(readComponent(type, 'setting.vue')).not.toContain('CommonStyle');
    },
  );

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
