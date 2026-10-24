import { describe, expect, it } from 'vitest';

import {
  componentRegistry,
  extensionComponentTypes,
  legacyComponentTypes,
  retailComponentTypes,
} from './component-registry';

const expectedRetailComponentTypes = [
  'goods-group',
  'goods-ranking',
  'goods-scroll',
  'countdown',
  'marketing-entry',
  'shop-info',
  'ship-workbench',
  'replenish-card',
  'newbie-floor',
  'activity-strip',
] as const;

/** legacy 组件数：P1 起 custom-html 纳入基础组件 */
const LEGACY_COMPONENT_COUNT = 12;

/** retail 组件数：旧版秒杀/拼团/折扣楼层已下线（由会场页承接），当前 10 个 */
const RETAIL_COMPONENT_COUNT = 10;

/** 活动入口条按实际卡数自动排布，不设「展示数量」，不参与 count 契约 */
const countConfiguredRetailTypes = expectedRetailComponentTypes.filter(
  (type) => type !== 'activity-strip',
);

describe('legacy component registry', () => {
  it('registers every legacy component exactly once', () => {
    expect(legacyComponentTypes).toHaveLength(LEGACY_COMPONENT_COUNT);
    expect(new Set(legacyComponentTypes).size).toBe(LEGACY_COMPONENT_COUNT);
    expect(legacyComponentTypes.every((type) => componentRegistry[type])).toBe(
      true,
    );
  });

  it.each(legacyComponentTypes)(
    '%s has a complete typed definition',
    (type) => {
      const definition = componentRegistry[type];

      expect(definition).toBeDefined();
      expect(definition?.type).toBe(type);
      expect(definition?.label).toBeTruthy();
      expect(definition?.category).toBeTruthy();
      expect(definition?.version).toBeGreaterThan(0);
      expect(definition?.preview).toBeTruthy();
      expect(definition?.settings).toBeTruthy();
      expect(definition?.supportedTerminals).toEqual(['admin', 'uniapp']);
      if (type === 'custom-html') {
        // 自定义 HTML 的默认内容刻意留空：空块不允许发布，
        // 运营必须粘贴内容后才能上线，因此新拖入的组件校验必然不通过。
        expect(definition?.validate(definition.createDefaultProps())).toEqual([
          'HTML 内容不能为空',
        ]);
      } else {
        expect(definition?.validate(definition.createDefaultProps())).toEqual(
          [],
        );
      }
      expect(
        Object.keys(definition?.createDefaultProps() ?? {}),
      ).not.toHaveLength(0);
      expect(definition?.createDefaultProps()).not.toBe(
        definition?.createDefaultProps(),
      );
    },
  );

  it('coupon-receive 缺省走自动领券，手选为空时不允许发布', () => {
    const definition = componentRegistry['coupon-receive'];
    const defaults = definition.createDefaultProps();

    expect(defaults).toMatchObject({
      dataSource: { mode: 'automatic', targetIds: [] },
    });
    expect(
      definition.validate({
        ...defaults,
        dataSource: { mode: 'manual', targetIds: [] },
      }),
    ).toEqual(['手选优惠券不能为空']);
  });
});

describe('retail component registry', () => {
  it('registers all retail components exactly once', () => {
    expect(retailComponentTypes).toEqual(expectedRetailComponentTypes);
    expect(new Set(expectedRetailComponentTypes).size).toBe(
      RETAIL_COMPONENT_COUNT,
    );
    expect(Object.keys(componentRegistry).sort()).toEqual(
      [
        ...legacyComponentTypes,
        ...expectedRetailComponentTypes,
        ...extensionComponentTypes,
      ].sort(),
    );
  });

  it('registers all seven extension components exactly once', () => {
    expect(new Set(extensionComponentTypes).size).toBe(7);
    for (const type of extensionComponentTypes) {
      const definition = componentRegistry[type];
      expect(definition).toBeDefined();
      if (type === 'image-cube') {
        // 图片魔方默认无图属刻意设计：空魔方不允许发布，
        // 运营至少填一张图后校验才通过（同 custom-html 的「内容不能为空」）
        expect(definition.validate(definition.createDefaultProps())).toEqual([
          '请至少为一张图片填入地址',
        ]);
      } else {
        expect(definition.validate(definition.createDefaultProps())).toEqual(
          [],
        );
      }
      expect(Object.keys(definition.createDefaultProps())).not.toHaveLength(0);
    }
  });

  it.each(countConfiguredRetailTypes)(
    '%s has complete dynamic-data defaults',
    (type) => {
      const definition = componentRegistry[type];
      const defaults = definition.createDefaultProps();

      expect(definition.type).toBe(type);
      expect(definition.preview).toBeTruthy();
      expect(definition.settings).toBeTruthy();
      expect(definition.supportedTerminals).toEqual(['admin', 'uniapp']);
      expect(defaults.dataSource).toBeTruthy();
      expect(defaults.count).toBeTypeOf('number');
      expect(defaults.count).toBeGreaterThan(0);
      expect(defaults.emptyStrategy).toBeTruthy();
      expect(defaults.invalidStrategy).toBeTruthy();
      expect(defaults.commonStyle).toMatchObject({
        bgStartColor: expect.any(String),
        styleBottomMargin: expect.any(Number),
        styleLeftMargin: expect.any(Number),
        styleRightMargin: expect.any(Number),
        styleTopMargin: expect.any(Number),
      });
      expect(definition.validate(defaults)).toEqual([]);
      expect(defaults).not.toBe(definition.createDefaultProps());
    },
  );

  it.each(countConfiguredRetailTypes)('%s rejects an invalid count', (type) => {
    const definition = componentRegistry[type];
    const defaults = definition.createDefaultProps();

    expect(definition.validate({ ...defaults, count: 0 })).toContain(
      '展示数量必须大于 0',
    );
    expect(
      definition.validate({ ...defaults, count: 999 }).length,
    ).toBeGreaterThan(0);
  });

  it('rejects missing component-specific targets', () => {
    const cases = [
      ['goods-group', { dataSource: { mode: 'manual', targetIds: [] } }],
      ['goods-ranking', { dataSource: { metric: '', mode: 'ranking' } }],
      ['countdown', { targetTime: '' }],
      ['marketing-entry', { entries: [] }],
    ] as const;

    for (const [type, patch] of cases) {
      const definition = componentRegistry[type];
      expect(
        definition.validate({
          ...definition.createDefaultProps(),
          ...patch,
        }),
      ).not.toEqual([]);
    }
  });

  it('creates a practical countdown target about 24 hours ahead', () => {
    const before = Date.now();
    const defaults = componentRegistry.countdown.createDefaultProps();
    const remaining = Date.parse(String(defaults.targetTime)) - before;

    expect(remaining).toBeGreaterThanOrEqual(23 * 60 * 60 * 1000);
    expect(remaining).toBeLessThanOrEqual(25 * 60 * 60 * 1000);
  });
});

describe('component style settings registry', () => {
  const typesWithSpecificStyleSettings = [
    'category-nav',
    'gap',
    'goods',
    'image-ad',
    'notice',
    'rich-text',
    'search-bar',
    'swiper-banner',
    'tab-nav',
    'title-text',
    'newbie-floor',
  ] as const;

  it.each(typesWithSpecificStyleSettings)(
    '%s exposes a dedicated style settings panel',
    (type) => {
      expect(componentRegistry[type].styleSettings).toBeTruthy();
    },
  );

  it('components without specific style fields rely on the shared common style', () => {
    expect(componentRegistry['coupon-receive'].styleSettings).toBeUndefined();
    expect(componentRegistry['goods-group'].styleSettings).toBeUndefined();
    expect(componentRegistry['image-cube'].styleSettings).toBeUndefined();
  });

  it('custom-html has neither common style nor a style panel', () => {
    const defaults = componentRegistry['custom-html'].createDefaultProps();

    expect(defaults).not.toHaveProperty('commonStyle');
    expect(componentRegistry['custom-html'].styleSettings).toBeUndefined();
  });
});
