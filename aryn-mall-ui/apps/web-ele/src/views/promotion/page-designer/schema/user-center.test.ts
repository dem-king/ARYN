import { describe, expect, it } from 'vitest';

import { migratePageContent as readMobileSnapshot } from '../../../../../../../../aryn-mall-uniapp/src/components/diy/schema/migrate';
import {
  userCenterHeroStyle as mobileHeroStyle,
  normalizeUserCenter as normalizeMobile,
} from '../../../../../../../../aryn-mall-uniapp/src/components/diy/schema/user-center';
import { createDefaultDecorationDocument } from './defaults';
import { migratePageContent } from './migrate';
import { normalizeUserCenter, userCenterHeroStyle } from './user-center';
import { toV3Document } from './v3';

describe('个人中心原生模块的发布契约', () => {
  it('没有设置的旧装修使用默认位置、订单、服务及推荐', () => {
    const expected = normalizeUserCenter(undefined);
    expect(normalizeMobile(undefined)).toEqual(expected);
    expect(expected.contentPosition).toBe('beforeOrders');
    expect(expected.services.order).toHaveLength(8);
    expect(expected.recommendations).toMatchObject({
      source: 'recommended',
      visible: true,
    });
  });

  it('原生模块可以单独保存，并在移动端还原配置', () => {
    const document = createDefaultDecorationDocument();
    const settings = normalizeUserCenter({
      contentPosition: 'afterServices',
      hero: {
        appearance: 'image',
        imageUrl: 'https://example.com/hero.png',
        loginHint: { en: 'My Orders' },
      },
      services: {
        columns: 3,
        order: ['support', 'address'],
        icons: { support: 'star' },
      },
      assets: { order: ['balance'] },
      workbenches: { order: ['sales'] },
      recommendations: { source: 'clearance', visible: false },
    });
    document.page.userCenter = settings;
    const snapshot = JSON.stringify(toV3Document(document));

    expect(migratePageContent(snapshot).page.userCenter).toEqual(settings);
    const published = readMobileSnapshot(snapshot);
    expect(published.components).toHaveLength(0);
    expect(normalizeMobile(published.page.userCenter)).toEqual(settings);
    expect(mobileHeroStyle(settings.hero)).toEqual(
      userCenterHeroStyle(settings.hero),
    );
    expect(settings.assets.order).toEqual(['balance', 'coupon', 'points']);
    expect(settings.workbenches.order).toEqual(['sales', 'delivery', 'vessel']);
  });

  it('清理无效、重复和退役入口，保护固定模块的完整顺序', () => {
    const raw = {
      hero: {
        appearance: 'image',
        imageUrl: 'javascript:invalid',
        startColor: 'white',
      },
      recommendations: { source: 'arbitrary' },
      services: { order: ['coupon', 'distribution', 'coupon', 'unknown'] },
      workbenches: {
        order: [],
        icons: { delivery: 'unknown', sales: 'pin' },
      },
    };
    const settings = normalizeUserCenter(raw);
    expect(normalizeMobile(raw)).toEqual(settings);
    expect(settings.hero.imageUrl).toBe('');
    expect(settings.services.order).toEqual(['coupon']);
    expect(settings.workbenches.order).toEqual(['delivery', 'vessel', 'sales']);
    expect(settings.workbenches.icons).toEqual({ sales: 'pin' });
    expect(settings.recommendations.source).toBe('recommended');
  });
});
