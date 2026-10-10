import { describe, expect, it } from 'vitest';

import {
  PURCHASE_SCENE_PERSONAL,
  PURCHASE_SCENE_SHIP_SUPPLY,
  purchaseSceneText,
} from './purchase-scene';

/**
 * 购买场景展示口径测试：
 * 只有 purchase_scene='2' 是船供采购，其余（含历史数据未落场景的空值）都是个人购买。
 * 列表页与详情页共用本口径，任一处单独判断都会让同一张订单出现两种显示。
 */
describe('purchase scene text', () => {
  it('船供采购原样展示', () => {
    expect(purchaseSceneText(PURCHASE_SCENE_SHIP_SUPPLY)).toBe('船供采购');
  });

  it('个人购买与历史空值都展示个人购买，不出现占位符', () => {
    expect(purchaseSceneText(PURCHASE_SCENE_PERSONAL)).toBe('个人购买');
    // 历史版本仅内部配送落场景，商城配送/快递单场景为空
    expect(purchaseSceneText(undefined)).toBe('个人购买');
    expect(purchaseSceneText(null as any)).toBe('个人购买');
    expect(purchaseSceneText('')).toBe('个人购买');
  });
});
