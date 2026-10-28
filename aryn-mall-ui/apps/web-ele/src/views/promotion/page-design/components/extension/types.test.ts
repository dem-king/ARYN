import { describe, expect, it } from 'vitest';

import {
  DEFAULT_BARGAIN_RATE,
  GOODS_TAB_DATA_SOURCE_MODES,
  GOODS_TAB_SORTS,
  normalizeBargainRate,
  validateGoodsTabs,
} from './types';

function createProps(tabs: unknown[]) {
  return { count: 10, tabs };
}

function autoTab(patch: Record<string, unknown> = {}) {
  return {
    dataSource: { mode: 'automatic', sort: 'sales', ...patch },
    id: 'tab-1',
    title: '低价捡漏',
  };
}

describe('f1 低价捡漏排序（goods-tabs）', () => {
  it('includes bargain in the sort whitelist', () => {
    expect(GOODS_TAB_SORTS).toContain('bargain');
  });

  it('accepts an automatic tab sorted by bargain with a valid threshold', () => {
    expect(
      validateGoodsTabs(
        createProps([autoTab({ bargainRate: 1.15, sort: 'bargain' })]),
      ),
    ).toEqual([]);
    // 阈值可留空，C 端按缺省 1.15 处理
    expect(
      validateGoodsTabs(createProps([autoTab({ sort: 'bargain' })])),
    ).toEqual([]);
  });

  it('falls back to sales when sort is missing so legacy documents still pass', () => {
    const legacy = autoTab();
    delete (legacy.dataSource as Record<string, unknown>).sort;
    expect(validateGoodsTabs(createProps([legacy]))).toEqual([]);
  });

  it('rejects an automatic tab sorted by an unknown value', () => {
    const errors = validateGoodsTabs(
      createProps([autoTab({ sort: 'discount_rate' })]),
    );
    expect(errors).toHaveLength(1);
    expect(errors[0]).toContain('排序方式不合法');
  });

  it('only checks the threshold range on the bargain sort', () => {
    // 其他排序即使带着越界阈值也不报错（阈值不生效）
    expect(
      validateGoodsTabs(createProps([autoTab({ bargainRate: 99 })])),
    ).toEqual([]);
  });

  it('rejects a bargain threshold outside (1.00, 9.99]', () => {
    const tooLow = validateGoodsTabs(
      createProps([autoTab({ bargainRate: 1, sort: 'bargain' })]),
    );
    expect(tooLow[0]).toContain('折扣率阈值');

    const tooHigh = validateGoodsTabs(
      createProps([autoTab({ bargainRate: 10, sort: 'bargain' })]),
    );
    expect(tooHigh[0]).toContain('折扣率阈值');

    const notANumber = validateGoodsTabs(
      createProps([autoTab({ bargainRate: 'abc', sort: 'bargain' })]),
    );
    expect(notANumber[0]).toContain('折扣率阈值');
  });

  it('accepts the inclusive upper bound 9.99 and a string threshold', () => {
    expect(
      validateGoodsTabs(
        createProps([autoTab({ bargainRate: 9.99, sort: 'bargain' })]),
      ),
    ).toEqual([]);
    expect(
      validateGoodsTabs(
        createProps([autoTab({ bargainRate: '1.2', sort: 'bargain' })]),
      ),
    ).toEqual([]);
  });

  it('normalizes the threshold to the default 1.15 when empty or invalid', () => {
    expect(normalizeBargainRate(undefined)).toBe(DEFAULT_BARGAIN_RATE);
    expect(normalizeBargainRate(null)).toBe(DEFAULT_BARGAIN_RATE);
    expect(normalizeBargainRate('')).toBe(DEFAULT_BARGAIN_RATE);
    expect(normalizeBargainRate('abc')).toBe(DEFAULT_BARGAIN_RATE);
    expect(normalizeBargainRate(0.9)).toBe(DEFAULT_BARGAIN_RATE);
    expect(normalizeBargainRate(10)).toBe(DEFAULT_BARGAIN_RATE);
    expect(normalizeBargainRate(1)).toBe(DEFAULT_BARGAIN_RATE);
  });

  it('keeps a valid threshold and trims float noise', () => {
    expect(normalizeBargainRate(1.2)).toBe(1.2);
    expect(normalizeBargainRate('2')).toBe(2);
    expect(normalizeBargainRate(9.99)).toBe(9.99);
    expect(normalizeBargainRate(1.234_567)).toBe(1.23);
  });
});

describe('f2 会员权益条（goods-tabs）', () => {
  const banner = (patch: Record<string, unknown>) => ({
    ...autoTab(),
    memberBanner: { enabled: true, ...patch },
  });

  it('accepts enabled banners with copies up to 30 characters', () => {
    const copy = '您已是黄金会员，本区享会员专属权益与专属券';
    expect(copy.length).toBeLessThanOrEqual(30);
    expect(
      validateGoodsTabs(
        createProps([banner({ guestCopy: copy, memberCopy: copy })]),
      ),
    ).toEqual([]);
  });

  it('accepts an enabled banner without copies (falls back to built-in copy)', () => {
    expect(validateGoodsTabs(createProps([banner({})]))).toEqual([]);
  });

  it('rejects member copy longer than 30 characters', () => {
    const tooLong = '会'.repeat(31);
    const errors = validateGoodsTabs(
      createProps([banner({ memberCopy: tooLong })]),
    );
    expect(errors).toHaveLength(1);
    expect(errors[0]).toContain('会员文案不能超过 30 字');
  });

  it('rejects guest copy longer than 30 characters', () => {
    const tooLong = '券'.repeat(31);
    const errors = validateGoodsTabs(
      createProps([banner({ guestCopy: tooLong })]),
    );
    expect(errors).toHaveLength(1);
    expect(errors[0]).toContain('游客文案不能超过 30 字');
  });

  it('ignores copy length when the banner is disabled', () => {
    const tab = {
      ...autoTab(),
      memberBanner: { enabled: false, memberCopy: '会'.repeat(50) },
    };
    expect(validateGoodsTabs(createProps([tab]))).toEqual([]);
  });

  it('counts copy length by unicode code points, so emoji count as one', () => {
    // 30 个 emoji 的 UTF-16 长度是 60，按码点计仍是 30 字（与后端 codePointCount 同口径）
    const banner30 = banner({ memberCopy: '🎉'.repeat(30) });
    expect(validateGoodsTabs(createProps([banner30]))).toEqual([]);
    const banner31 = banner({ guestCopy: '🎉'.repeat(31) });
    const errors = validateGoodsTabs(createProps([banner31]));
    expect(errors).toHaveLength(1);
    expect(errors[0]).toContain('游客文案不能超过 30 字');
  });

  it('rejects a malformed memberBanner payload', () => {
    const tab = { ...autoTab(), memberBanner: 'yes' };
    const errors = validateGoodsTabs(createProps([tab]));
    expect(errors[0]).toContain('会员权益条配置格式不正确');
  });
});

describe('会员专区按会员价活动自动（activity 模式）', () => {
  function activityTab(patch: Record<string, unknown> = {}) {
    return {
      dataSource: { mode: 'activity', ...patch },
      id: 'member',
      title: '会员专区',
    };
  }

  it('includes activity in the supported data source modes', () => {
    expect(GOODS_TAB_DATA_SOURCE_MODES).toContain('activity');
  });

  it('accepts the persisted activity data source shape', () => {
    expect(
      validateGoodsTabs(createProps([activityTab({ activityId: '' })])),
    ).toEqual([]);
  });

  it('allows activityId to be omitted (auto picks the in-progress activity)', () => {
    expect(validateGoodsTabs(createProps([activityTab()]))).toEqual([]);
  });

  it('accepts a specified activity id', () => {
    expect(
      validateGoodsTabs(
        createProps([activityTab({ activityId: 'activity-1' })]),
      ),
    ).toEqual([]);
  });

  it('skips sort/targetIds/categoryId validation in activity mode', () => {
    const tab = activityTab({
      activityId: '',
      categoryId: '',
      sort: 'bargain',
      targetIds: [],
    });
    expect(validateGoodsTabs(createProps([tab]))).toEqual([]);
    // 活动模式下 sort 不生效，脏值也不该拦发布
    expect(
      validateGoodsTabs(createProps([activityTab({ sort: 'discount_rate' })])),
    ).toEqual([]);
  });

  it('keeps rejecting a manual tab without goods', () => {
    const manualTab = {
      dataSource: { mode: 'manual', targetIds: [] },
      id: 'manual',
      title: '手选',
    };
    expect(validateGoodsTabs(createProps([manualTab]))).toEqual([
      '「手选」手选商品不能为空',
    ]);
  });

  it('still validates the member banner in activity mode', () => {
    const tab = {
      ...activityTab({ activityId: '' }),
      memberBanner: { enabled: true, memberCopy: '会'.repeat(31) },
    };
    const errors = validateGoodsTabs(createProps([tab]));
    expect(errors[0]).toContain('会员文案不能超过 30 字');
  });
});
