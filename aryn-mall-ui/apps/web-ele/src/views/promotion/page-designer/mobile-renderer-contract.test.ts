import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

import {
  legacyComponentTypes,
  retailComponentTypes,
} from './registry/component-registry';

const mobileDiyRoot = resolve(
  process.cwd(),
  '../aryn-mall-uniapp/src/components/diy',
);
const mobileSourceRoot = resolve(process.cwd(), '../aryn-mall-uniapp/src');

function readMobileFile(relativePath: string) {
  return readFileSync(resolve(mobileDiyRoot, relativePath), 'utf8');
}

describe('mobile decoration renderer contract', () => {
  it('registers every admin fixture component', () => {
    const registry = readMobileFile('registry.ts');
    const expectedTypes = [...legacyComponentTypes, ...retailComponentTypes];

    for (const type of expectedTypes) {
      expect(registry, `missing mobile registry entry: ${type}`).toContain(
        `'${type}'`,
      );
    }
  });

  it('migrates legacy content and renders every component through static branches', () => {
    const renderer = readMobileFile('index.vue');
    const expectedTypes = [...legacyComponentTypes, ...retailComponentTypes];

    expect(renderer).toContain('migratePageContent');
    for (const type of expectedTypes) {
      expect(renderer, `missing static mobile renderer: ${type}`).toContain(
        `item.type === '${type}'`,
      );
    }
    expect(renderer).not.toContain('<component');
    expect(renderer).not.toContain('getDiyComponent');
    expect(renderer).toContain(':show-data="item.props"');
    expect(renderer).not.toContain('item.formData');
  });

  it('keeps unknown components silent in production', () => {
    const renderer = readMobileFile('index.vue');

    expect(existsSync(resolve(mobileDiyRoot, 'unknown-component.vue'))).toBe(
      true,
    );
    expect(renderer).toContain('UnknownComponent');
  });

  it.each(['pages/home/index.vue', 'sub-pages/promotion/diy-page/index.vue'])(
    '%s applies page settings through the shared composable',
    (file) => {
      const source = readFileSync(resolve(mobileSourceRoot, file), 'utf8');

      expect(source).toContain('useDecorationPage');
      expect(source).toContain('canPullDownRefresh');
      expect(source).toContain('uni.stopPullDownRefresh()');
    },
  );

  it.each([
    ['goods-group', ['dataSource', 'count', 'columns', 'showSales']],
    ['goods-ranking', ['dataSource', 'count', 'showRankNumber']],
    ['limited-activity', ['dataSource', 'count', 'showCountdown']],
    ['countdown', ['targetTime', 'completedText']],
    ['marketing-entry', ['entries', 'columns']],
    ['shop-info', ['showContact', 'showDescription']],
  ] as const)('%s implements its mobile props contract', (type, fields) => {
    const file = `${type === 'shop-info' ? 'diy-shop-info' : `diy-${type}`}/index.vue`;
    const source = readMobileFile(file);

    for (const field of fields) expect(source).toContain(field);
    expect(source).toContain('emptyStrategy');
    expect(source).toContain('invalidStrategy');
    expect(source).toContain('commonStyle');
  });

  it.each([
    'goods-group',
    'goods-ranking',
    'limited-activity',
    'countdown',
    'marketing-entry',
    'shop-info',
  ])('registers the %s renderer instead of the unknown fallback', (type) => {
    const registry = readMobileFile('registry.ts');
    const componentName = type
      .split('-')
      .map((part) => part[0]?.toUpperCase() + part.slice(1))
      .join('');

    expect(registry).toContain(`Diy${componentName}`);
    expect(registry).toContain(`'${type}': Diy${componentName}`);
  });

  it.each([
    'diy-goods-group/index.vue',
    'diy-goods-ranking/index.vue',
    'diy-limited-activity/index.vue',
    'diy-shop-info/index.vue',
  ])('%s uses shared async retail state', (file) => {
    expect(readMobileFile(file)).toContain('useRetailData');
  });

  it.each([
    'diy-category-nav/index.vue',
    'diy-image/index.vue',
    'diy-swiper-banner/index.vue',
    'diy-tabnav/index.vue',
    'diy-titletext/index.vue',
  ])('%s routes clicks through the shared link resolver', (file) => {
    const source = readMobileFile(file);

    expect(source).toContain('followDecorationLink');
    expect(source).not.toContain('link.url');
    expect(source).not.toContain('uni.navigateTo');
  });

  it('loads only the current tenant shop whitelist', () => {
    const tenantApi = readFileSync(
      resolve(mobileSourceRoot, 'api/upms/tenant.ts'),
      'utf8',
    );

    expect(tenantApi).toContain('/upms/app/tenant/shop-info');
    expect(tenantApi).not.toContain('/tenant/list');
  });

  it('keeps public decoration and activity routes available on mobile', () => {
    const router = readFileSync(
      resolve(mobileSourceRoot, 'router/index.ts'),
      'utf8',
    );
    const pages = readFileSync(resolve(mobileSourceRoot, 'pages.json'), 'utf8');
    const detailPath = 'promotion/group-buy/group-buy-detail/index';
    const detailIndex = pages.indexOf(detailPath);

    expect(router).toContain("'/sub-pages/promotion/diy-page/index'");
    expect(router).not.toContain("'/sub-pages/shop/diy-page/index'");
    expect(detailIndex).toBeGreaterThan(0);
    expect(
      pages.slice(Math.max(0, detailIndex - 80), detailIndex),
    ).not.toContain('#ifdef H5');
  });

  it('uses product API category and ordering parameter names', () => {
    const retailData = readMobileFile('retail-data.ts');

    expect(retailData).toContain('categorySecondId:');
    expect(retailData).not.toContain('categoryId:');
    expect(retailData).not.toMatch(/\b(?:ascs|descs):/);
  });
});
