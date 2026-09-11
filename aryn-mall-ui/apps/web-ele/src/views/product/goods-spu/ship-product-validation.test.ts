import { describe, expect, it } from 'vitest';

import {
  computePublishCompleteness,
  isPublishReadyForShipSupply,
  validateQtyRule,
  validateSaleScope,
  validateShipCodes,
  validateStorageType,
  validateWeightVolume,
} from './ship-product-validation';

describe('ship product validation', () => {
  it('rejects invalid sale scope', () => {
    expect(validateSaleScope('9')).toBe('销售范围不合法');
    expect(validateSaleScope(undefined)).toBe('销售范围不合法');
    expect(validateSaleScope('3')).toBeNull();
  });

  it('requires at least one ship code for ship supply visible goods', () => {
    expect(
      validateShipCodes({
        saleScope: '2',
        impaCode: '',
        issaCode: undefined,
        internalItemCode: undefined,
      }),
    ).toBe('船供商品必须至少提供 IMPA/ISSA/内部物料编码之一');
    expect(
      validateShipCodes({ saleScope: '2', impaCode: '751100' }),
    ).toBeNull();
    expect(
      validateShipCodes({ saleScope: '2', internalItemCode: 'IN-001' }),
    ).toBeNull();
    expect(validateShipCodes({ saleScope: '1' })).toBeNull();
  });

  it('enforces purchase unit, moq and step qty rules for ship supply', () => {
    expect(
      validateQtyRule('2', { purchaseUnit: undefined, moq: 10, stepQty: 5 }),
    ).toBe('船供商品必须提供采购单位');
    expect(
      validateQtyRule('2', { purchaseUnit: '箱', moq: 0, stepQty: 5 }),
    ).toBe('最小起订量必须大于0');
    expect(
      validateQtyRule('2', { purchaseUnit: '箱', moq: 10, stepQty: 0 }),
    ).toBe('数量步长必须大于0');
    expect(
      validateQtyRule('2', { purchaseUnit: '箱', moq: 7, stepQty: 5 }),
    ).toBe('最小起订量必须是数量步长的整数倍');
    expect(
      validateQtyRule('2', { purchaseUnit: '箱', moq: 10, stepQty: 5 }),
    ).toBeNull();
  });

  it('skips qty rule for personal goods until fields are filled', () => {
    expect(validateQtyRule('1', {})).toBeNull();
    expect(validateQtyRule('1', { moq: 7, stepQty: 5 })).toBe(
      '最小起订量必须是数量步长的整数倍',
    );
  });

  it('rejects negative weight or volume', () => {
    expect(validateWeightVolume(-1, 2)).toBe('重量和体积不能小于0');
    expect(validateWeightVolume(1, undefined)).toBeNull();
    expect(validateWeightVolume(1, 0.5)).toBeNull();
  });

  it('validates storage type values', () => {
    expect(validateStorageType('9')).toBe('储存条件不合法');
    expect(validateStorageType('2')).toBeNull();
    expect(validateStorageType(undefined)).toBeNull();
  });

  it('computes publish completeness by filled fields ratio', () => {
    const empty = computePublishCompleteness({ saleScope: '3' });
    expect(empty).toBeGreaterThan(0);
    expect(empty).toBeLessThan(10);
    const full = computePublishCompleteness(
      {
        barcode: '6901',
        impaCode: '751100',
        internalItemCode: 'IN-1',
        issaCode: 'ISSA-1',
        nameEn: 'Item',
        saleScope: '3',
        shelfLifeDays: 365,
        storageType: '1',
      },
      { moq: 10, packageSpec: '1*12', purchaseUnit: '箱', stepQty: 5 },
    );
    expect(full).toBe(100);
  });

  it('gates ship supply catalog readiness', () => {
    const ready = isPublishReadyForShipSupply(
      { impaCode: '751100', saleScope: '2', storageType: '1' },
      { moq: 10, purchaseUnit: '箱', stepQty: 5 },
    );
    expect(ready).toBe(true);
    const notReady = isPublishReadyForShipSupply(
      { saleScope: '2', storageType: '1' },
      { moq: 10, purchaseUnit: '箱', stepQty: 5 },
    );
    expect(notReady).toBe(false);
  });
});
