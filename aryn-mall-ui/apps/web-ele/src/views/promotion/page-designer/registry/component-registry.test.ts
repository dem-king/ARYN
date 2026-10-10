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
  'limited-activity',
  'seckill',
  'discount',
  'countdown',
  'marketing-entry',
  'shop-info',
  'ship-workbench',
  'replenish-card',
] as const;

/** legacy 组件数：P1 起 custom-html 纳入基础组件 */
const LEGACY_COMPONENT_COUNT = 12;

/** retail 组件数：P1 起含 goods-scroll；本轮新增 seckill / discount */
const RETAIL_COMPONENT_COUNT = 11;

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

  it('registers all eight extension components exactly once', () => {
    expect(new Set(extensionComponentTypes).size).toBe(8);
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

  it.each(expectedRetailComponentTypes)(
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

  it.each(expectedRetailComponentTypes)(
    '%s rejects an invalid count',
    (type) => {
      const definition = componentRegistry[type];
      const defaults = definition.createDefaultProps();

      expect(definition.validate({ ...defaults, count: 0 })).toContain(
        '展示数量必须大于 0',
      );
      expect(
        definition.validate({ ...defaults, count: 999 }).length,
      ).toBeGreaterThan(0);
    },
  );

  it('rejects missing component-specific targets', () => {
    const cases = [
      ['goods-group', { dataSource: { mode: 'manual', targetIds: [] } }],
      ['goods-ranking', { dataSource: { metric: '', mode: 'ranking' } }],
      ['limited-activity', { dataSource: { mode: 'manual', targetIds: [] } }],
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
