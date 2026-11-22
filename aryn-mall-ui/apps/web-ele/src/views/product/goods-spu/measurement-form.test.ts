import { describe, expect, it } from 'vitest';

import { normalizeMeasurement, validateMeasurement } from './measurement-form';

describe('商品度量编辑', () => {
  it('旧SKU保持未知重量，修改时沿用服务端版本', () => {
    const legacy = normalizeMeasurement({});
    expect(legacy.quantityPrecision).toBe('UNKNOWN');
    expect(legacy.baseQtyPerSalesUnit).toBeUndefined();
    expect(
      normalizeMeasurement({ measurementRevision: 7 }).measurementRevision,
    ).toBe(7);
    expect(validateMeasurement(legacy)).toBeNull();
  });
  it('精确系数以十进制文本传输，超限小数和非整数门槛被拒绝', () => {
    const facts = {
      salesUnit: '袋',
      baseDimension: 'MASS',
      baseUnit: 'g',
      quantityPrecision: 'EXACT',
      baseQtyPerSalesUnit: '1000.000000001',
    };
    expect(normalizeMeasurement(facts).baseQtyPerSalesUnit).toBe(
      '1000.000000001',
    );
    expect(validateMeasurement(facts)).toBeNull();
    expect(
      validateMeasurement({ ...facts, baseQtyPerSalesUnit: '0.0000000001' }),
    ).toContain('9位小数');
    expect(validateMeasurement({ ...facts, stepQty: 1.5 })).toContain('正整数');
    expect(validateMeasurement({ ...facts, baseUnit: 'ml' })).toContain('量纲');
    expect(
      validateMeasurement({ ...facts, quantityPrecision: 'APPROX' }),
    ).toBeNull();
  });
});
