import { describe, expect, it } from 'vitest';

import { validateGoodsSkus } from './goods-spu-form-validation';

describe('goods spu form validation', () => {
  it('rejects an empty sku list', () => {
    expect(validateGoodsSkus('1', [])).toBe('请至少配置一个SKU');
  });

  it('rejects negative price or stock values', () => {
    expect(
      validateGoodsSkus('0', [
        {
          costPrice: 8,
          originalPrice: 12,
          salesPrice: 10,
          stock: -1,
        },
      ]),
    ).toBe('SKU价格和库存不能小于0');
  });

  it('rejects duplicate multi-spec combinations', () => {
    const specsArr = [
      {
        specsId: 'color',
        specsValueId: 'red',
      },
    ];

    expect(
      validateGoodsSkus('1', [
        {
          costPrice: 8,
          originalPrice: 12,
          salesPrice: 10,
          specsArr,
          stock: 1,
        },
        {
          costPrice: 9,
          originalPrice: 13,
          salesPrice: 11,
          specsArr,
          stock: 2,
        },
      ]),
    ).toBe('SKU规格组合不能重复');
  });

  it('rejects an existing sku without its inventory version', () => {
    expect(
      validateGoodsSkus('0', [
        {
          costPrice: 8,
          id: 'sku-1',
          originalPrice: 12,
          salesPrice: 10,
          stock: 1,
        },
      ]),
    ).toBe('商品库存版本缺失，请刷新后重试');
  });

  it('accepts complete unique multi-spec skus', () => {
    expect(
      validateGoodsSkus('1', [
        {
          costPrice: 8,
          originalPrice: 12,
          salesPrice: 10,
          specsArr: [{ specsId: 'color', specsValueId: 'red' }],
          stock: 1,
        },
        {
          costPrice: 9,
          originalPrice: 13,
          salesPrice: 11,
          specsArr: [{ specsId: 'color', specsValueId: 'blue' }],
          stock: 2,
        },
      ]),
    ).toBeNull();
  });
});
