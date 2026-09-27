import { describe, expect, it } from 'vitest';

import { shouldShowOriginalPrice } from './price-display';

/**
 * 划线原价的显示判定。
 *
 * 与小程序端 `aryn-mall-uniapp/src/components/diy/price-display.ts` 是两套独立实现，
 * 但判定口径必须完全一致——否则运营在装修器里看到的效果与实机不符，
 * 这正是本文件存在的原因（用例与小程序侧同构，改动需两端同步）。
 */
describe('shouldShowOriginalPrice 划线原价显示判定', () => {
  it('原价高于售价时显示', () => {
    expect(shouldShowOriginalPrice({ price: 3.8, originalPrice: 23.8 })).toBe(
      true,
    );
  });

  it('兼容接口原始字段名 salesPrice', () => {
    expect(
      shouldShowOriginalPrice({ salesPrice: 3.9, originalPrice: 4.9 }),
    ).toBe(true);
  });

  it('原价未填（0）不显示——否则会划出「￥0」', () => {
    expect(shouldShowOriginalPrice({ price: 12.9, originalPrice: 0 })).toBe(
      false,
    );
    expect(shouldShowOriginalPrice({ price: 12.9 })).toBe(false);
  });

  it('原价等于售价不显示——无折扣的划线没有信息量', () => {
    expect(shouldShowOriginalPrice({ price: 9.9, originalPrice: 9.9 })).toBe(
      false,
    );
  });

  it('原价低于售价不显示——避免出现「涨价」的误导划线', () => {
    expect(shouldShowOriginalPrice({ price: 23.8, originalPrice: 3.8 })).toBe(
      false,
    );
  });

  it('非数值输入不显示，不抛错', () => {
    expect(shouldShowOriginalPrice({ price: 'abc', originalPrice: 10 })).toBe(
      false,
    );
    expect(shouldShowOriginalPrice({ price: 10, originalPrice: 'abc' })).toBe(
      false,
    );
    expect(shouldShowOriginalPrice({})).toBe(false);
  });
});
