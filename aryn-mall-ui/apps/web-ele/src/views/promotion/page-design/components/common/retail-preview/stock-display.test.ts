import { describe, expect, it } from 'vitest';

import { STOCK_LOW_THRESHOLD, stockHintText } from './stock-display';

/**
 * 库存提示的显示口径。
 *
 * 与小程序端 `aryn-mall-uniapp/src/utils/stock-display.test.ts` 是两套独立实现，
 * 但用例同构——改动需两端同步，否则装修器预览与实机不符。
 */
describe('stockHintText 库存提示显示口径', () => {
  it('库存未知（null/undefined/负数/空串）返回空串，整块隐藏', () => {
    expect(stockHintText(null)).toBe('');
    expect(stockHintText(undefined)).toBe('');
    expect(stockHintText(-1)).toBe('');
    expect(stockHintText('')).toBe('');
  });

  it('售罄显示「已售罄」', () => {
    expect(stockHintText(0)).toBe('已售罄');
  });

  it('低库存（含阈值边界）显示「仅剩 N 件」', () => {
    expect(stockHintText(1)).toBe('仅剩 1 件');
    expect(stockHintText(3)).toBe('仅剩 3 件');
    expect(stockHintText(STOCK_LOW_THRESHOLD)).toBe(
      `仅剩 ${STOCK_LOW_THRESHOLD} 件`,
    );
  });

  it('库存充足不外露绝对数字，返回空串', () => {
    expect(stockHintText(STOCK_LOW_THRESHOLD + 1)).toBe('');
    expect(stockHintText(999)).toBe('');
  });
});
