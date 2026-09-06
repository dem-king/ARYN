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
  'limited-activity',
  'countdown',
  'marketing-entry',
  'shop-info',
] as const;

describe('legacy component registry', () => {
  it('registers every legacy component exactly once', () => {
    expect(legacyComponentTypes).toHaveLength(11);
    expect(new Set(legacyComponentTypes).size).toBe(11);
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
      expect(definition?.validate(definition.createDefaultProps())).toEqual([]);
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
  it('registers all six retail components exactly once', () => {
    expect(retailComponentTypes).toEqual(expectedRetailComponentTypes);
    expect(new Set(expectedRetailComponentTypes).size).toBe(6);
    expect(Object.keys(componentRegistry).sort()).toEqual(
      [
        ...legacyComponentTypes,
        ...expectedRetailComponentTypes,
        ...extensionComponentTypes,
      ].sort(),
    );
  });

  it('registers all six extension components exactly once', () => {
    expect(new Set(extensionComponentTypes).size).toBe(6);
    for (const type of extensionComponentTypes) {
      const definition = componentRegistry[type];
      expect(definition).toBeDefined();
      expect(definition.validate(definition.createDefaultProps())).toEqual([]);
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
