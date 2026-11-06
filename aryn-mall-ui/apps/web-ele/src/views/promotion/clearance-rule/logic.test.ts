import type { ClearanceRuleDraft, ClearanceRuleRow, LadderBand } from './logic';

import { describe, expect, it } from 'vitest';

import {
  buildLadder,
  buildRulePayload,
  buildTogglePayload,
  explainIneffective,
  findSameHoursRule,
  formatBandDescription,
  formatBandDiscount,
  formatBandRange,
  formatDiscount,
  formatHours,
  formatRateHint,
  isRuleEffective,
  mergeAdjacentBands,
  rateToZhe,
  simulatePrice,
  summarizeRules,
  toRuleDraft,
  validateDraft,
  zheToRate,
} from './logic';

/** 构造规则行（默认启用、24 小时、8 折） */
function rule(overrides: Partial<ClearanceRuleRow> = {}): ClearanceRuleRow {
  return {
    discountRate: 0.8,
    enabled: '1',
    hoursBeforeExpire: 24,
    id: 'rule-1',
    remark: null,
    ruleName: '规则 1',
    sortOrder: 0,
    ...overrides,
  };
}

/** 构造编辑草稿（默认合法：启用、24 小时、8 折） */
function draft(
  overrides: Partial<ClearanceRuleDraft> = {},
): ClearanceRuleDraft {
  return {
    discountRate: 0.8,
    enabled: true,
    hoursBeforeExpire: 24,
    remark: '',
    ruleName: '规则 1',
    sortOrder: 0,
    ...overrides,
  };
}

describe('折扣文案', () => {
  it('折扣率与「折」互转', () => {
    expect(rateToZhe(0.8)).toBe(8);
    expect(rateToZhe('0.85')).toBe(8.5);
    expect(rateToZhe(0.9)).toBe(9);
    expect(zheToRate(8)).toBe(0.8);
    expect(zheToRate(8.5)).toBe(0.85);
    expect(zheToRate(9.9)).toBe(0.99);
    // 互转后保持精度
    expect(zheToRate(rateToZhe(0.86))).toBe(0.86);
    // 非法输入兜底为 0，避免 NaN 传播到输入框
    expect(rateToZhe(Number.NaN)).toBe(0);
    expect(zheToRate(Number.NaN)).toBe(0);
  });

  it('formatDiscount 固定 1 位小数并带空格', () => {
    expect(formatDiscount(0.8)).toBe('8.0 折');
    expect(formatDiscount('0.85')).toBe('8.5 折');
    expect(formatDiscount(0.856)).toBe('8.6 折');
    expect(formatDiscount(Number.NaN)).toBe('--');
  });

  it('formatRateHint 输出基础出清价百分比', () => {
    expect(formatRateHint(0.8)).toBe('基础出清价 × 80%');
    expect(formatRateHint(0.85)).toBe('基础出清价 × 85%');
    expect(formatRateHint(0.8567)).toBe('基础出清价 × 85.67%');
    expect(formatRateHint(Number.NaN)).toContain('折扣率无效');
  });

  it('formatHours 输出 ≤ N 小时', () => {
    expect(formatHours(24)).toBe('≤ 24 小时');
    expect(formatHours(1)).toBe('≤ 1 小时');
    expect(formatHours(Number.NaN)).toBe('--');
  });
});

describe('toRuleDraft / buildRulePayload / buildTogglePayload', () => {
  it('回显：折扣率字符串转 number，空值回填默认值', () => {
    const row = rule({
      discountRate: '0.8500',
      enabled: '1',
      remark: null,
      sortOrder: null,
    });
    expect(toRuleDraft(row)).toEqual({
      discountRate: 0.85,
      enabled: true,
      hoursBeforeExpire: 24,
      remark: '',
      ruleName: '规则 1',
      sortOrder: 0,
    });
  });

  it('回显：非 "1" 的 enabled 一律视为停用', () => {
    expect(toRuleDraft(rule({ enabled: '0' })).enabled).toBe(false);
    expect(toRuleDraft(rule({ enabled: '2' })).enabled).toBe(false);
  });

  it('草稿 → payload：启用为 "1"，停用为 "0"', () => {
    expect(buildRulePayload(draft())).toEqual({
      discountRate: 0.8,
      enabled: '1',
      hoursBeforeExpire: 24,
      remark: '',
      ruleName: '规则 1',
      sortOrder: 0,
    });
    expect(buildRulePayload(draft({ enabled: false })).enabled).toBe('0');
  });

  it('启停 payload 保留其余字段（字符串折扣率转 number、空值回填）', () => {
    const row = rule({
      discountRate: '0.85',
      enabled: '0',
      hoursBeforeExpire: 48,
      remark: '备注',
      ruleName: '停用中',
      sortOrder: null,
    });
    expect(buildTogglePayload(row, true)).toEqual({
      discountRate: 0.85,
      enabled: '1',
      hoursBeforeExpire: 48,
      remark: '备注',
      ruleName: '停用中',
      sortOrder: 0,
    });
    expect(buildTogglePayload(row, false).enabled).toBe('0');
  });
});

describe('validateDraft', () => {
  it('合法草稿返回 null', () => {
    expect(validateDraft(draft())).toBeNull();
    expect(
      validateDraft(draft({ discountRate: 0.9999, hoursBeforeExpire: 1 })),
    ).toBeNull();
  });

  it('规则名称 trim 后必须非空', () => {
    expect(validateDraft(draft({ ruleName: '' }))).toBe('请输入规则名称');
    expect(validateDraft(draft({ ruleName: '   ' }))).toBe('请输入规则名称');
  });

  it('距到期小时数必须为正整数', () => {
    expect(validateDraft(draft({ hoursBeforeExpire: null }))).toBe(
      '请填写距到期小时数',
    );
    expect(validateDraft(draft({ hoursBeforeExpire: 0 }))).toBe(
      '距到期小时数必须大于 0',
    );
    expect(validateDraft(draft({ hoursBeforeExpire: -5 }))).toBe(
      '距到期小时数必须大于 0',
    );
    expect(validateDraft(draft({ hoursBeforeExpire: 1.5 }))).toBe(
      '距到期小时数必须为正整数',
    );
  });

  it('折扣率必须在开区间 (0, 1) 内', () => {
    expect(validateDraft(draft({ discountRate: null }))).toBe('请填写折扣力度');
    expect(validateDraft(draft({ discountRate: 0 }))).toBe(
      '折扣率必须在 (0, 1) 区间内',
    );
    expect(validateDraft(draft({ discountRate: 1 }))).toBe(
      '折扣率必须在 (0, 1) 区间内',
    );
    expect(validateDraft(draft({ discountRate: -0.2 }))).toBe(
      '折扣率必须在 (0, 1) 区间内',
    );
    expect(validateDraft(draft({ discountRate: 1.2 }))).toBe(
      '折扣率必须在 (0, 1) 区间内',
    );
  });
});

describe('findSameHoursRule', () => {
  const sameA = rule({ hoursBeforeExpire: 24, id: 'a', ruleName: 'A' });
  const sameB = rule({
    enabled: '0',
    hoursBeforeExpire: 24,
    id: 'b',
    ruleName: 'B',
  });
  const other = rule({ hoursBeforeExpire: 48, id: 'c', ruleName: 'C' });
  const rules = [sameA, sameB, other];

  it('按小时数找到重复档位（停用规则也参与提示）', () => {
    expect(findSameHoursRule(rules, 24)).toBe(sameA);
    expect(findSameHoursRule(rules, 24, 'a')).toBe(sameB);
    expect(findSameHoursRule(rules, 48)).toBe(other);
  });

  it('排除自身后无重复、或不存在的档位返回 undefined', () => {
    expect(findSameHoursRule(rules, 48, 'c')).toBeUndefined();
    expect(findSameHoursRule(rules, 36)).toBeUndefined();
  });
});

describe('buildLadder', () => {
  it('无规则或全部停用时返回空阶梯', () => {
    expect(buildLadder([])).toEqual([]);
    expect(
      buildLadder([
        rule({ enabled: '0', hoursBeforeExpire: 24, discountRate: 0.5 }),
      ]),
    ).toEqual([]);
  });

  it('单档：原价段 + 唯一折扣段', () => {
    const bands = buildLadder([
      rule({
        discountRate: 0.8,
        hoursBeforeExpire: 24,
        ruleName: '临期 8 折',
      }),
    ]);
    expect(bands).toEqual([
      {
        driverNames: [],
        fromHours: 24,
        hitCount: 0,
        rate: null,
        toHours: null,
      },
      {
        driverNames: ['临期 8 折'],
        fromHours: null,
        hitCount: 1,
        rate: 0.8,
        toHours: 24,
      },
    ]);
  });

  it('多档：去重小时后分档，取 hours ≥ 上界规则的最小折扣率', () => {
    const bands = buildLadder([
      rule({
        discountRate: 0.9,
        hoursBeforeExpire: 24,
        id: 'r24a',
        ruleName: '24 小时 9 折',
      }),
      rule({
        discountRate: 0.85,
        hoursBeforeExpire: 24,
        id: 'r24b',
        ruleName: '24 小时 8.5 折',
      }),
      rule({
        discountRate: 0.8,
        hoursBeforeExpire: 48,
        id: 'r48',
        ruleName: '48 小时 8 折',
      }),
      rule({
        discountRate: 0.85,
        hoursBeforeExpire: 72,
        id: 'r72',
        ruleName: '72 小时 8.5 折',
      }),
    ]);
    expect(bands).toEqual([
      {
        driverNames: [],
        fromHours: 72,
        hitCount: 0,
        rate: null,
        toHours: null,
      },
      {
        driverNames: ['72 小时 8.5 折'],
        fromHours: 48,
        hitCount: 1,
        rate: 0.85,
        toHours: 72,
      },
      {
        driverNames: ['48 小时 8 折'],
        fromHours: 24,
        hitCount: 2,
        rate: 0.8,
        toHours: 48,
      },
      {
        driverNames: ['48 小时 8 折'],
        fromHours: null,
        hitCount: 4,
        rate: 0.8,
        toHours: 24,
      },
    ]);
  });

  it('结果顺序从远到近：原价段在最前，依次到最临近到期的档', () => {
    const bands = buildLadder([
      rule({ hoursBeforeExpire: 24, id: 'a', ruleName: '24 档' }),
      rule({ hoursBeforeExpire: 72, id: 'b', ruleName: '72 档' }),
      rule({ hoursBeforeExpire: 48, id: 'c', ruleName: '48 档' }),
    ]);
    expect(bands.map((band) => formatBandRange(band))).toEqual([
      '剩余 > 72 小时',
      '剩余 48–72 小时',
      '剩余 24–48 小时',
      '剩余 ≤ 24 小时',
    ]);
  });

  it('同率跨档并列时 driverNames 含全部规则名（时间档从大到小）', () => {
    const bands = buildLadder([
      rule({ hoursBeforeExpire: 48, id: 'b', ruleName: 'B 档' }),
      rule({ hoursBeforeExpire: 72, id: 'a', ruleName: 'A 档' }),
    ]);
    // 剩余 ≤ 48 小时时两条规则同时命中且同为最低折扣
    expect(bands[2]).toMatchObject({
      driverNames: ['A 档', 'B 档'],
      fromHours: null,
      hitCount: 2,
      rate: 0.8,
      toHours: 48,
    });
  });

  it('浮点精度：0.7 不会被 0.9 误判（取最小折扣率）', () => {
    const bands = buildLadder([
      rule({ discountRate: 0.9, hoursBeforeExpire: 24, ruleName: '近档 9 折' }),
      rule({ discountRate: 0.7, hoursBeforeExpire: 48, ruleName: '远档 7 折' }),
    ]);
    expect(bands[2]?.rate).toBe(0.7);
    expect(bands[2]?.driverNames).toEqual(['远档 7 折']);
  });

  it('1e-9 精度内的等值折扣视为同率', () => {
    const bands = buildLadder([
      rule({
        discountRate: 0.7,
        hoursBeforeExpire: 48,
        id: 'far',
        ruleName: '远档',
      }),
      rule({
        discountRate: 0.700_000_000_1,
        hoursBeforeExpire: 24,
        id: 'near',
        ruleName: '近档',
      }),
    ]);
    expect(bands[2]?.rate).toBe(0.7);
    expect(bands[2]?.driverNames).toEqual(['远档', '近档']);
  });

  it('停用规则不参与分档与命中计数', () => {
    const bands = buildLadder([
      rule({ discountRate: 0.8, hoursBeforeExpire: 24, ruleName: '启用 8 折' }),
      rule({
        discountRate: 0.5,
        enabled: '0',
        hoursBeforeExpire: 48,
        ruleName: '停用 5 折',
      }),
    ]);
    expect(bands).toHaveLength(2);
    expect(bands[1]).toMatchObject({
      fromHours: null,
      hitCount: 1,
      rate: 0.8,
      toHours: 24,
    });
  });
});

describe('mergeAdjacentBands', () => {
  it('空数组返回新的空数组', () => {
    const bands: LadderBand[] = [];
    const merged = mergeAdjacentBands(bands);
    expect(merged).toEqual([]);
    expect(merged).not.toBe(bands);
  });

  it('单段内容原样返回（返回新对象）', () => {
    const band: LadderBand = {
      driverNames: ['远档'],
      fromHours: 24,
      hitCount: 1,
      rate: 0.8,
      toHours: 48,
    };
    const merged = mergeAdjacentBands([band]);
    expect(merged).toEqual([band]);
    expect(merged[0]).not.toBe(band);
  });

  it('连续同率合并：暴露 24 小时档不改变价格', () => {
    const bands = buildLadder([
      rule({
        discountRate: 0.8,
        hoursBeforeExpire: 48,
        id: 'r48',
        ruleName: '48 小时 8 折',
      }),
      rule({
        discountRate: 0.85,
        hoursBeforeExpire: 24,
        id: 'r24',
        ruleName: '24 小时 8.5 折',
      }),
      rule({
        discountRate: 0.7,
        hoursBeforeExpire: 6,
        id: 'r6',
        ruleName: '6 小时 7 折',
      }),
    ]);
    const merged = mergeAdjacentBands(bands);
    expect(merged.map((band) => formatBandDescription(band))).toEqual([
      '剩余 > 48 小时 · 原价',
      '剩余 6–48 小时 · 8.0 折',
      '剩余 ≤ 6 小时 · 7.0 折',
    ]);
    expect(merged[1]).toEqual({
      driverNames: ['48 小时 8 折'],
      fromHours: 6,
      hitCount: 2,
      rate: 0.8,
      toHours: 48,
    });
  });

  it('连锁多段同率折叠成一段', () => {
    const bands = buildLadder([
      rule({
        discountRate: 0.8,
        hoursBeforeExpire: 72,
        id: 'r72',
        ruleName: '72 小时 8 折',
      }),
      rule({
        discountRate: 0.85,
        hoursBeforeExpire: 48,
        id: 'r48',
        ruleName: '48 小时 8.5 折',
      }),
      rule({
        discountRate: 0.9,
        hoursBeforeExpire: 24,
        id: 'r24',
        ruleName: '24 小时 9 折',
      }),
    ]);
    const merged = mergeAdjacentBands(bands);
    expect(merged).toHaveLength(2);
    expect(merged[0]?.rate).toBeNull();
    expect(merged[1]).toEqual({
      driverNames: ['72 小时 8 折'],
      fromHours: null,
      hitCount: 3,
      rate: 0.8,
      toHours: 72,
    });
  });

  it('中间折扣率不同时不合并', () => {
    const bands = buildLadder([
      rule({
        discountRate: 0.8,
        hoursBeforeExpire: 48,
        id: 'r48',
        ruleName: '48 小时 8 折',
      }),
      rule({
        discountRate: 0.7,
        hoursBeforeExpire: 24,
        id: 'r24',
        ruleName: '24 小时 7 折',
      }),
    ]);
    const merged = mergeAdjacentBands(bands);
    expect(merged).toHaveLength(3);
    expect(merged.map((band) => band.rate)).toEqual([null, 0.8, 0.7]);
  });

  it('原价段不参与合并', () => {
    const bands: LadderBand[] = [
      {
        driverNames: [],
        fromHours: 48,
        hitCount: 0,
        rate: null,
        toHours: null,
      },
      { driverNames: [], fromHours: 24, hitCount: 0, rate: null, toHours: 48 },
    ];
    const merged = mergeAdjacentBands(bands);
    expect(merged).toHaveLength(2);
    expect(merged.map((band) => band.rate)).toEqual([null, null]);
  });

  it('不修改入参（入参深拷贝不变，返回新数组/新对象）', () => {
    const bands = buildLadder([
      rule({
        discountRate: 0.8,
        hoursBeforeExpire: 48,
        id: 'r48',
        ruleName: '48 小时 8 折',
      }),
      rule({
        discountRate: 0.8,
        hoursBeforeExpire: 24,
        id: 'r24',
        ruleName: '24 小时 8 折',
      }),
    ]);
    const snapshot = structuredClone(bands);
    const merged = mergeAdjacentBands(bands);
    expect(bands).toEqual(snapshot);
    expect(merged).not.toBe(bands);
    expect(merged).toHaveLength(2);
    expect(merged[0]).not.toBe(bands[0]);
  });

  it('driverNames 并集去重并保持远→近先出现顺序', () => {
    const bands: LadderBand[] = [
      {
        driverNames: ['A', 'B'],
        fromHours: 24,
        hitCount: 2,
        rate: 0.8,
        toHours: 48,
      },
      {
        driverNames: ['B', 'C'],
        fromHours: 6,
        hitCount: 2,
        rate: 0.800_000_000_1,
        toHours: 24,
      },
      {
        driverNames: ['C', 'D'],
        fromHours: null,
        hitCount: 3,
        rate: 0.7,
        toHours: 6,
      },
    ];
    const merged = mergeAdjacentBands(bands);
    expect(merged).toHaveLength(2);
    expect(merged[0]?.driverNames).toEqual(['A', 'B', 'C']);
    expect(merged[0]?.fromHours).toBe(6);
    expect(merged[0]?.toHours).toBe(48);
    expect(merged[0]?.hitCount).toBe(2);
    expect(merged[1]?.rate).toBe(0.7);
  });
});

describe('isRuleEffective / explainIneffective', () => {
  it('停用规则一律无效', () => {
    const disabled = rule({ enabled: '0' });
    const rules = [disabled];
    expect(isRuleEffective(rules, disabled)).toBe(false);
    expect(explainIneffective(rules, disabled)).toBe('规则已停用');
  });

  it('各档折扣依次更深时全部有效', () => {
    const near = rule({
      discountRate: 0.8,
      hoursBeforeExpire: 24,
      id: 'near',
      ruleName: '近档',
    });
    const far = rule({
      discountRate: 0.9,
      hoursBeforeExpire: 48,
      id: 'far',
      ruleName: '远档',
    });
    const rules = [near, far];
    expect(isRuleEffective(rules, near)).toBe(true);
    expect(isRuleEffective(rules, far)).toBe(true);
    expect(explainIneffective(rules, near)).toBeNull();
  });

  it('更长时间档同率覆盖：近档无效并给出原因', () => {
    const near = rule({
      discountRate: 0.8,
      hoursBeforeExpire: 24,
      id: 'near',
      ruleName: '近档',
    });
    const far = rule({
      discountRate: 0.8,
      hoursBeforeExpire: 48,
      id: 'far',
      ruleName: '远档',
    });
    const rules = [near, far];
    expect(isRuleEffective(rules, near)).toBe(false);
    expect(explainIneffective(rules, near)).toBe(
      '更长时间档已有 8.0 折，本档不会降低出清价',
    );
    expect(isRuleEffective(rules, far)).toBe(true);
  });

  it('更长时间档更深覆盖：近档无效且原因是远档折扣', () => {
    const near = rule({
      discountRate: 0.9,
      hoursBeforeExpire: 24,
      id: 'near',
      ruleName: '近档',
    });
    const far = rule({
      discountRate: 0.7,
      hoursBeforeExpire: 48,
      id: 'far',
      ruleName: '远档',
    });
    const rules = [near, far];
    expect(isRuleEffective(rules, near)).toBe(false);
    expect(explainIneffective(rules, near)).toContain('更长时间档已有 7.0 折');
  });

  it('同一小时数档位：只有达到该档最小折扣率的规则有效', () => {
    const deep = rule({
      discountRate: 0.8,
      hoursBeforeExpire: 24,
      id: 'deep',
      ruleName: '深档',
    });
    const shallow = rule({
      discountRate: 0.9,
      hoursBeforeExpire: 24,
      id: 'shallow',
      ruleName: '浅档',
    });
    const rules = [deep, shallow];
    expect(isRuleEffective(rules, deep)).toBe(true);
    expect(isRuleEffective(rules, shallow)).toBe(false);
    expect(explainIneffective(rules, shallow)).toBe(
      '同时间档「深档」已是 8.0 折',
    );
  });

  it('同一小时数同率并列时都算有效', () => {
    const first = rule({
      discountRate: 0.8,
      hoursBeforeExpire: 24,
      id: 'first',
      ruleName: '甲',
    });
    const second = rule({
      discountRate: 0.8,
      hoursBeforeExpire: 24,
      id: 'second',
      ruleName: '乙',
    });
    const rules = [first, second];
    expect(isRuleEffective(rules, first)).toBe(true);
    expect(isRuleEffective(rules, second)).toBe(true);
    expect(explainIneffective(rules, first)).toBeNull();
  });

  it('连锁冗余：中间档被顶档覆盖后，近档仍被顶档覆盖', () => {
    const far = rule({
      discountRate: 0.8,
      hoursBeforeExpire: 72,
      id: 'far',
      ruleName: '顶档',
    });
    const middle = rule({
      discountRate: 0.9,
      hoursBeforeExpire: 48,
      id: 'middle',
      ruleName: '中档',
    });
    const near = rule({
      discountRate: 0.8,
      hoursBeforeExpire: 24,
      id: 'near',
      ruleName: '近档',
    });
    const rules = [far, middle, near];
    expect(isRuleEffective(rules, far)).toBe(true);
    expect(isRuleEffective(rules, middle)).toBe(false);
    // 中档自身的 0.9 是冗余的，但顶档 0.8 仍覆盖近档，近档同样无效
    expect(isRuleEffective(rules, near)).toBe(false);
    expect(explainIneffective(rules, near)).toBe(
      '更长时间档已有 8.0 折，本档不会降低出清价',
    );
  });

  it('中间档无效：远档折扣更深时中间档不会降价', () => {
    const far = rule({
      discountRate: 0.9,
      hoursBeforeExpire: 72,
      id: 'far',
      ruleName: '远档',
    });
    const middle = rule({
      discountRate: 0.95,
      hoursBeforeExpire: 48,
      id: 'middle',
      ruleName: '中档',
    });
    const near = rule({
      discountRate: 0.8,
      hoursBeforeExpire: 24,
      id: 'near',
      ruleName: '近档',
    });
    const rules = [far, middle, near];
    expect(isRuleEffective(rules, middle)).toBe(false);
    expect(isRuleEffective(rules, near)).toBe(true);
    expect(explainIneffective(rules, near)).toBeNull();
  });

  it('浮点精度：0.7000000001 被视为与 0.7 同率并被覆盖', () => {
    const far = rule({
      discountRate: 0.7,
      hoursBeforeExpire: 48,
      id: 'far',
      ruleName: '长档',
    });
    const near = rule({
      discountRate: 0.700_000_000_1,
      hoursBeforeExpire: 24,
      id: 'near',
      ruleName: '近档',
    });
    const rules = [far, near];
    expect(isRuleEffective(rules, near)).toBe(false);
    expect(explainIneffective(rules, near)).toContain('更长时间档已有 7.0 折');
  });
});

describe('simulatePrice', () => {
  const near = rule({
    discountRate: 0.6,
    hoursBeforeExpire: 24,
    id: 'near',
    ruleName: '近档 6 折',
  });
  const far = rule({
    discountRate: 0.8,
    hoursBeforeExpire: 48,
    id: 'far',
    ruleName: '远档 8 折',
  });
  const rules = [near, far];

  it('无启用规则时不命中，也无档位下标', () => {
    expect(simulatePrice([], 100, 10)).toEqual({
      bandIndex: -1,
      basePrice: 100,
      driverNames: [],
      finalPrice: 100,
      hitCount: 0,
      rate: null,
    });
    expect(simulatePrice([rule({ enabled: '0' })], 100, 10).bandIndex).toBe(-1);
  });

  it('剩余小时恰好等于阈值时命中', () => {
    // 24 小时：同时命中 24/48 两档，取最深 6 折
    const at24 = simulatePrice(rules, 100, 24);
    expect(at24).toEqual({
      bandIndex: 2,
      basePrice: 100,
      driverNames: ['近档 6 折'],
      finalPrice: 60,
      hitCount: 2,
      rate: 0.6,
    });
    // 48 小时：只命中 48 小时档
    const at48 = simulatePrice(rules, 100, 48);
    expect(at48).toEqual({
      bandIndex: 1,
      basePrice: 100,
      driverNames: ['远档 8 折'],
      finalPrice: 80,
      hitCount: 1,
      rate: 0.8,
    });
  });

  it('区间内命中：24–48 小时只命中远档，≤24 小时命中两档', () => {
    expect(simulatePrice(rules, 100, 24.5)).toMatchObject({
      bandIndex: 1,
      hitCount: 1,
      rate: 0.8,
    });
    expect(simulatePrice(rules, 100, 12)).toMatchObject({
      bandIndex: 2,
      hitCount: 2,
      rate: 0.6,
    });
  });

  it('剩余超过最大时间档时不命中，落在原价段下标 0', () => {
    expect(simulatePrice(rules, 100, 49)).toEqual({
      bandIndex: 0,
      basePrice: 100,
      driverNames: [],
      finalPrice: 100,
      hitCount: 0,
      rate: null,
    });
  });

  it('只应用最深一档，不连乘', () => {
    // 若连乘会是 100 × 0.8 × 0.5 = 40，正确结果只取最深 0.5 一档
    expect(
      simulatePrice(
        [
          rule({ discountRate: 0.8, hoursBeforeExpire: 48, id: 'a' }),
          rule({ discountRate: 0.5, hoursBeforeExpire: 24, id: 'b' }),
        ],
        100,
        10,
      ).finalPrice,
    ).toBe(50);
  });

  it('折后价保留 2 位小数', () => {
    const single = [rule({ discountRate: 0.8, hoursBeforeExpire: 24 })];
    expect(simulatePrice(single, 9.99, 10).finalPrice).toBe(7.99);
    expect(
      simulatePrice(
        [rule({ discountRate: 0.3333, hoursBeforeExpire: 24 })],
        10,
        10,
      ).finalPrice,
    ).toBe(3.33);
  });

  it('字符串折扣率同样参与计算，并列司机名单去重排序稳定', () => {
    const simulation = simulatePrice(
      [
        rule({
          discountRate: '0.8',
          hoursBeforeExpire: 48,
          id: 'b',
          ruleName: 'B 档',
        }),
        rule({
          discountRate: '0.8',
          hoursBeforeExpire: 72,
          id: 'a',
          ruleName: 'A 档',
        }),
      ],
      100,
      10,
    );
    expect(simulation).toMatchObject({
      bandIndex: 2,
      driverNames: ['A 档', 'B 档'],
      finalPrice: 80,
      hitCount: 2,
      rate: 0.8,
    });
  });

  it('负剩余小时（已过期）不命中，与后端 resolveTierRate 口径一致', () => {
    const expired = [rule({ discountRate: 0.8, hoursBeforeExpire: 24 })];
    const simulation = simulatePrice(expired, 100, -1);
    expect(simulation.rate).toBeNull();
    expect(simulation.hitCount).toBe(0);
    expect(simulation.finalPrice).toBe(100);
  });
});

describe('summarizeRules', () => {
  it('空列表返回全空汇总', () => {
    expect(summarizeRules([])).toEqual({
      deepestRate: null,
      enabledCount: 0,
      longestHours: null,
      shortestHours: null,
      total: 0,
    });
  });

  it('total 含停用规则，极值只统计启用规则', () => {
    expect(
      summarizeRules([
        rule({ discountRate: 0.5, enabled: '0', hoursBeforeExpire: 72 }),
        rule({ discountRate: 0.8, hoursBeforeExpire: 48 }),
        rule({ discountRate: 0.6, enabled: '1', hoursBeforeExpire: 24 }),
      ]),
    ).toEqual({
      deepestRate: 0.6,
      enabledCount: 2,
      longestHours: 48,
      shortestHours: 24,
      total: 3,
    });
  });

  it('全部停用时只有 total 有值', () => {
    expect(summarizeRules([rule({ enabled: '0' })])).toEqual({
      deepestRate: null,
      enabledCount: 0,
      longestHours: null,
      shortestHours: null,
      total: 1,
    });
  });
});

describe('阶梯文案格式化', () => {
  const bands = buildLadder([
    rule({
      discountRate: 0.9,
      hoursBeforeExpire: 48,
      id: 'far',
      ruleName: '远档 9 折',
    }),
    rule({
      discountRate: 0.8,
      hoursBeforeExpire: 24,
      id: 'near',
      ruleName: '近档 8 折',
    }),
  ]);

  it('formatBandRange 区分原价段/中段/最临近段', () => {
    expect(bands.map((band) => formatBandRange(band))).toEqual([
      '剩余 > 48 小时',
      '剩余 24–48 小时',
      '剩余 ≤ 24 小时',
    ]);
  });

  it('formatBandDiscount 原价段与折扣段', () => {
    expect(bands.map((band) => formatBandDiscount(band))).toEqual([
      '原价',
      '9.0 折',
      '8.0 折',
    ]);
  });

  it('formatBandDescription 组合区间与折扣', () => {
    expect(bands.map((band) => formatBandDescription(band))).toEqual([
      '剩余 > 48 小时 · 原价',
      '剩余 24–48 小时 · 9.0 折',
      '剩余 ≤ 24 小时 · 8.0 折',
    ]);
  });

  it('单档时原价段下界为唯一档位', () => {
    const single = buildLadder([rule({ hoursBeforeExpire: 24 })]);
    expect(single.map((band) => formatBandRange(band))).toEqual([
      '剩余 > 24 小时',
      '剩余 ≤ 24 小时',
    ]);
  });
});
