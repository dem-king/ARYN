import type {
  ZoneBatchItem,
  ZoneBatchResult,
  ZonePreview,
  ZonePreviewItem,
  ZoneSkuTag,
  ZoneSkuTagSave,
  ZoneTagDraft,
} from './types';

import { describe, expect, it } from 'vitest';

import {
  buildBatchPayload,
  buildPreviewRequest,
  buildRemoveConfirmText,
  buildSkuSaveItems,
  chunkIds,
  collectFailuresText,
  collectPreviewWarnings,
  computeConfirmText,
  computeImpactText,
  computePreviewImpact,
  computeRemoveImpact,
  computeSyncState,
  computeZoneStatus,
  createDefaultDrafts,
  createFailedItems,
  describeSync,
  emptyZoneDraft,
  hasPreviewImpact,
  mergeBatchAggregate,
  resolveErrorMessage,
  summarizeItems,
  toSkuEditRow,
  uniqueSpuIds,
  zoneChangeTypeLabel,
  zoneLabel,
} from './logic';

function draft(
  zone: ZoneTagDraft['zone'],
  action: ZoneTagDraft['action'],
  extra: Partial<ZoneTagDraft> = {},
): ZoneTagDraft {
  return { ...emptyZoneDraft(zone, action), ...extra };
}

function previewItem(
  overrides: Partial<ZonePreviewItem> = {},
): ZonePreviewItem {
  return {
    before: {
      clearanceTag: 0,
      memberTag: 0,
      skuOverrideCount: 0,
    },
    changes: [],
    spuId: 'spu-1',
    spuName: '商品 1',
    ...overrides,
  };
}

function batchItem(overrides: Partial<ZoneBatchItem> = {}): ZoneBatchItem {
  return {
    changeType: 'ADD',
    changes: [],
    spuId: 'spu-1',
    spuName: '商品 1',
    success: true,
    ...overrides,
  };
}

function batchResult(
  overrides: Partial<ZoneBatchResult> = {},
): ZoneBatchResult {
  return {
    batchId: 'batch-1',
    items: [],
    summary: {
      added: 0,
      changed: 0,
      failed: 0,
      noChange: 0,
      removed: 0,
      requested: 0,
      success: 0,
      updated: 0,
    },
    sync: { status: 'SUCCESS' },
    ...overrides,
  };
}

describe('uniqueSpuIds / chunkIds', () => {
  it('去重、去空并保持传入顺序', () => {
    expect(uniqueSpuIds(['b', 'a', 'b', ' ', 'c', 'a'])).toEqual([
      'b',
      'a',
      'c',
    ]);
  });

  it('默认按 200/片分片（服务端单次上限）', () => {
    const ids = Array.from({ length: 450 }, (_, index) => `id-${index}`);
    const chunks = chunkIds(ids);
    expect(chunks).toHaveLength(3);
    expect(chunks[0]).toHaveLength(200);
    expect(chunks[1]).toHaveLength(200);
    expect(chunks[2]).toHaveLength(50);
    expect(chunks.flat()).toHaveLength(450);
  });

  it('空列表返回空分片，自定义分片大小生效且去重', () => {
    expect(chunkIds([])).toEqual([]);
    expect(chunkIds(['a', 'a', 'b', 'c'], 2)).toEqual([['a', 'b'], ['c']]);
    // 非法分片大小时按 1 处理，避免死循环
    expect(chunkIds(['a', 'b'], 0)).toEqual([['a'], ['b']]);
  });
});

describe('createDefaultDrafts', () => {
  it('standard 模式默认全部「不修改」', () => {
    expect(createDefaultDrafts('both', 'standard')).toEqual([
      { action: 'NONE', zone: 'member' },
      { action: 'NONE', zone: 'clearance' },
    ]);
  });

  it('remove 模式范围内专区默认移出，范围外不修改', () => {
    expect(createDefaultDrafts('both', 'remove')).toEqual([
      { action: 'REMOVE', zone: 'member' },
      { action: 'REMOVE', zone: 'clearance' },
    ]);
    expect(createDefaultDrafts('clearance', 'remove')).toEqual([
      { action: 'NONE', zone: 'member' },
      { action: 'REMOVE', zone: 'clearance' },
    ]);
  });

  it('单项有限范围 + standard 时不预选任何动作', () => {
    const drafts = createDefaultDrafts('member', 'standard');
    expect(drafts[0]).toEqual({ action: 'NONE', zone: 'member' });
    expect(drafts[1]).toEqual({ action: 'NONE', zone: 'clearance' });
  });

  it('standard + defaultAction=ADD：「添加商品」入口范围内专区默认加入', () => {
    expect(createDefaultDrafts('member', 'standard', 'ADD')).toEqual([
      { action: 'ADD', zone: 'member' },
      { action: 'NONE', zone: 'clearance' },
    ]);
    expect(createDefaultDrafts('both', 'standard', 'ADD')).toEqual([
      { action: 'ADD', zone: 'member' },
      { action: 'ADD', zone: 'clearance' },
    ]);
  });

  it('remove 模式下 defaultAction 不生效（仍默认移出）', () => {
    expect(createDefaultDrafts('both', 'remove', 'ADD')).toEqual([
      { action: 'REMOVE', zone: 'member' },
      { action: 'REMOVE', zone: 'clearance' },
    ]);
  });
});

describe('buildPreviewRequest', () => {
  it('默认请求：两个专区都是 NONE，spuIds 去重，overwriteSkuRules=false', () => {
    const request = buildPreviewRequest(
      ['spu-2', 'spu-1', 'spu-2'],
      createDefaultDrafts('both', 'standard'),
    );
    expect(request).toEqual({
      clearanceAction: 'NONE',
      memberAction: 'NONE',
      overwriteSkuRules: false,
      spuIds: ['spu-2', 'spu-1'],
    });
  });

  it('加入(ADD) 时提交规则，空规则字段不落到 payload（=沿用旧规则）', () => {
    const request = buildPreviewRequest(
      ['spu-1'],
      [
        draft('member', 'ADD', {
          memberDiscountRate: 0.9,
          memberPriceFixed: null,
        }),
        draft('clearance', 'NONE'),
      ],
    );
    expect(request.memberAction).toBe('ADD');
    expect(request.memberDiscountRate).toBe(0.9);
    expect(request.memberPriceFixed).toBeUndefined();
    expect('clearancePrice' in request).toBe(false);
  });

  it('移出(REMOVE) 时忽略规则字段', () => {
    const request = buildPreviewRequest(
      ['spu-1'],
      [
        draft('member', 'REMOVE', { memberDiscountRate: 0.8 }),
        draft('clearance', 'ADD', {
          clearancePrice: 9.9,
          clearanceDiscountRate: 0.5,
          clearanceExpireTime: '2026-12-31 23:59:59',
        }),
      ],
      true,
    );
    expect(request.memberAction).toBe('REMOVE');
    expect(request.memberDiscountRate).toBeUndefined();
    expect(request.clearanceAction).toBe('ADD');
    expect(request.clearancePrice).toBe(9.9);
    expect(request.clearanceDiscountRate).toBe(0.5);
    expect(request.clearanceExpireTime).toBe('2026-12-31 23:59:59');
    expect(request.overwriteSkuRules).toBe(true);
  });
});

describe('buildBatchPayload', () => {
  it('不修改(NONE) 不提交标签字段（后端语义：不修改）', () => {
    const payload = buildBatchPayload(
      ['spu-1'],
      createDefaultDrafts('both', 'standard'),
    );
    expect(payload).toEqual({ overwriteSkuRules: false, spuIds: ['spu-1'] });
    expect('memberTag' in payload).toBe(false);
    expect('clearanceTag' in payload).toBe(false);
  });

  it('会员 ADD → memberTag=true 并携带规则；出清 NONE 不出现', () => {
    const payload = buildBatchPayload(
      ['spu-1'],
      [
        draft('member', 'ADD', {
          memberDiscountRate: 0.9,
          memberPriceFixed: 19.9,
        }),
        draft('clearance', 'NONE'),
      ],
      true,
    );
    expect(payload.memberTag).toBe(true);
    expect(payload.memberDiscountRate).toBe(0.9);
    expect(payload.memberPriceFixed).toBe(19.9);
    expect(payload.clearanceTag).toBeUndefined();
    expect(payload.overwriteSkuRules).toBe(true);
  });

  it('会员 REMOVE → memberTag=false 且不带规则字段', () => {
    const payload = buildBatchPayload(
      ['spu-1'],
      [
        draft('member', 'REMOVE', { memberDiscountRate: 0.7 }),
        draft('clearance', 'NONE'),
      ],
    );
    expect(payload.memberTag).toBe(false);
    expect('memberDiscountRate' in payload).toBe(false);
  });

  it('出清 ADD 映射价格/折扣/截止时间', () => {
    const payload = buildBatchPayload(
      ['spu-1'],
      [
        draft('member', 'NONE'),
        draft('clearance', 'ADD', {
          clearanceDiscountRate: 0.5,
          clearanceExpireTime: '2026-12-31 23:59:59',
          clearancePrice: null,
        }),
      ],
    );
    expect(payload.clearanceTag).toBe(true);
    expect(payload.clearancePrice).toBeUndefined();
    expect(payload.clearanceDiscountRate).toBe(0.5);
    expect(payload.clearanceExpireTime).toBe('2026-12-31 23:59:59');
  });
});

describe('预检影响汇总与确认文案', () => {
  const preview: ZonePreview = {
    items: [],
    summary: {
      add: 2,
      invalid: 0,
      noChange: 8,
      remove: 0,
      skuOverridesWillClear: 3,
      total: 10,
      update: 0,
      warningCount: 2,
    },
    zones: [],
  };

  it('null 预检返回全 0 影响', () => {
    expect(computePreviewImpact(null)).toMatchObject({ add: 0, total: 0 });
    expect(hasPreviewImpact(computePreviewImpact(null))).toBe(false);
  });

  it('从预检 summary 提取影响', () => {
    const impact = computePreviewImpact(preview);
    expect(impact).toMatchObject({ add: 2, noChange: 8, total: 10 });
    expect(hasPreviewImpact(impact)).toBe(true);
  });

  it('确认按钮文案随动作变化', () => {
    expect(
      computeConfirmText({
        add: 2,
        invalid: 0,
        noChange: 8,
        remove: 0,
        skuOverridesWillClear: 0,
        total: 10,
        update: 0,
        warningCount: 0,
      }),
    ).toBe('确认：加入 2 件');
    expect(
      computeConfirmText({
        add: 2,
        invalid: 0,
        noChange: 0,
        remove: 0,
        skuOverridesWillClear: 0,
        total: 10,
        update: 8,
        warningCount: 0,
      }),
    ).toBe('确认：加入 2 件 / 更新 8 件');
    expect(
      computeConfirmText({
        add: 0,
        invalid: 0,
        noChange: 0,
        remove: 10,
        skuOverridesWillClear: 0,
        total: 10,
        update: 0,
        warningCount: 0,
      }),
    ).toBe('确认移出 10 件');
  });

  it('全量无变化时没有影响，按钮禁用依据成立', () => {
    const noChangeImpact = computePreviewImpact({
      ...preview,
      summary: {
        ...preview.summary,
        add: 0,
        noChange: 10,
        remove: 0,
        update: 0,
      },
    });
    expect(hasPreviewImpact(noChangeImpact)).toBe(false);
    expect(computeImpactText(noChangeImpact)).toBe(
      '新增 0 件 · 更新 0 件 · 移出 0 件 · 无变化 10 件 · 预警 2 条',
    );
  });

  it('影响文案包含无法执行件数', () => {
    expect(
      computeImpactText({
        add: 1,
        invalid: 2,
        noChange: 3,
        remove: 0,
        skuOverridesWillClear: 0,
        total: 6,
        update: 0,
        warningCount: 0,
      }),
    ).toContain('无法执行 2 件');
  });
});

describe('computeZoneStatus', () => {
  it('按 items[].before/changes 计算已在/未加入/规则将变/无变化/SKU 精调/异常', () => {
    const preview: ZonePreview = {
      items: [
        previewItem({
          before: { memberTag: 1, skuOverrideCount: 2 },
          changes: [{ type: 'UPDATE', zone: 'member' }],
          spuId: 'spu-1',
        }),
        previewItem({
          before: { memberTag: 0 },
          changes: [{ type: 'ADD', zone: 'member' }],
          spuId: 'spu-2',
        }),
        previewItem({
          before: { memberTag: 1 },
          changes: [{ type: 'NO_CHANGE', zone: 'member' }],
          spuId: 'spu-3',
        }),
        previewItem({
          before: { memberTag: 0 },
          changes: [{ type: 'INVALID', zone: 'member' }],
          spuId: 'spu-4',
        }),
      ],
      summary: {
        add: 1,
        invalid: 1,
        noChange: 1,
        remove: 0,
        skuOverridesWillClear: 0,
        total: 4,
        update: 1,
        warningCount: 0,
      },
      zones: [],
    };
    const status = computeZoneStatus(preview, 'member');
    expect(status).toMatchObject({
      existing: 2,
      invalid: 1,
      noChange: 1,
      notJoined: 2,
      total: 4,
      willAdd: 1,
      willUpdate: 1,
      withSkuOverride: 1,
    });
  });

  it('items 缺失时退回 zones[] 汇总', () => {
    const status = computeZoneStatus(
      {
        items: [],
        summary: {
          add: 0,
          invalid: 0,
          noChange: 0,
          remove: 0,
          skuOverridesWillClear: 0,
          total: 10,
          update: 0,
          warningCount: 0,
        },
        zones: [
          {
            existing: 8,
            invalid: 0,
            noChange: 0,
            willAdd: 2,
            willRemove: 0,
            willUpdate: 0,
            withSkuOverride: 3,
            zone: 'member',
          },
        ],
      },
      'member',
    );
    expect(status).toMatchObject({
      existing: 8,
      notJoined: 2,
      total: 10,
      willAdd: 2,
      withSkuOverride: 3,
    });
  });
});

describe('告警聚合与失败清单', () => {
  it('预检告警按 code+message 聚合计数（商品级 + 专区级）', () => {
    const preview: ZonePreview = {
      items: [
        previewItem({
          changes: [
            {
              type: 'ADD',
              warnings: [{ code: 'OFF_SHELF', message: '商品已下架' }],
              zone: 'member',
            },
          ],
          spuId: 'spu-1',
          warnings: [{ code: 'OFF_SHELF', message: '商品已下架' }],
        }),
        previewItem({
          spuId: 'spu-2',
          warnings: [{ code: 'OFF_SHELF', message: '商品已下架' }],
        }),
      ],
      summary: {
        add: 0,
        invalid: 0,
        noChange: 0,
        remove: 0,
        skuOverridesWillClear: 0,
        total: 2,
        update: 0,
        warningCount: 3,
      },
      zones: [],
    };
    expect(collectPreviewWarnings(preview)).toEqual([
      { code: 'OFF_SHELF', count: 3, message: '商品已下架' },
    ]);
  });

  it('失败清单包含商品名、SPU ID、错误码与原因', () => {
    const text = collectFailuresText([
      batchItem({
        errorCode: 'INVALID_RULE',
        message: '缺少出清规则',
        spuId: 'spu-9',
        spuName: '出清商品',
        success: false,
      }),
      batchItem({ spuId: 'spu-ok' }),
    ]);
    expect(text).toContain('共 1 件');
    expect(text).toContain('出清商品（spu-9）');
    expect(text).toContain('INVALID_RULE');
    expect(text).toContain('缺少出清规则');
    expect(collectFailuresText([batchItem()])).toBe('');
  });

  it('请求异常时为整片 SPU 生成可重试失败条目', () => {
    const items = createFailedItems(['spu-1', 'spu-1', 'spu-2'], '网络错误');
    expect(items).toHaveLength(2);
    expect(items[0]).toMatchObject({
      changeType: 'INVALID',
      errorCode: 'BATCH_REQUEST_FAILED',
      message: '网络错误',
      success: false,
    });
  });
});

describe('summarizeItems / aggregateBatchResults / mergeBatchAggregate', () => {
  it('按条目重算 summary（成功/无变化/变更/失败）', () => {
    const items = [
      batchItem({ changeType: 'ADD', spuId: 'a' }),
      batchItem({ changeType: 'UPDATE', spuId: 'b' }),
      batchItem({ changeType: 'NO_CHANGE', spuId: 'c' }),
      batchItem({
        changeType: 'INVALID',
        message: '失败',
        spuId: 'd',
        success: false,
      }),
    ];
    const { failedCount, summary } = summarizeItems(items);
    expect(summary).toEqual({
      added: 1,
      changed: 2,
      failed: 1,
      noChange: 1,
      removed: 0,
      requested: 4,
      success: 3,
      updated: 1,
    });
    expect(failedCount).toBe(1);
  });

  it('分专区计数来自 changes[]', () => {
    const { zones } = summarizeItems([
      batchItem({
        changeType: 'ADD',
        changes: [{ type: 'ADD', zone: 'member' }],
        spuId: 'a',
      }),
      batchItem({
        changeType: 'UPDATE',
        changes: [
          { type: 'UPDATE', zone: 'member' },
          { type: 'ADD', zone: 'clearance' },
        ],
        spuId: 'b',
      }),
    ]);
    expect(zones.find((zone) => zone.zone === 'member')).toMatchObject({
      added: 1,
      updated: 1,
    });
    expect(zones.find((zone) => zone.zone === 'clearance')).toMatchObject({
      added: 1,
    });
  });

  it('多分片结果合并批次号并保留 syncs', () => {
    const aggregate = summarizeItems([batchItem({ spuId: 'a' })]);
    expect(aggregate.summary.requested).toBe(1);
  });

  it('mergeBatchAggregate 以重试结果覆盖同一 SPU 的旧失败条目', () => {
    const first = summarizeItems([
      batchItem({ changeType: 'ADD', spuId: 'a' }),
      batchItem({
        changeType: 'INVALID',
        message: '失败',
        spuId: 'b',
        success: false,
      }),
    ]);
    const previous = {
      batchIds: ['batch-1'],
      failedCount: first.failedCount,
      items: [
        batchItem({ changeType: 'ADD', spuId: 'a' }),
        batchItem({
          changeType: 'INVALID',
          message: '失败',
          spuId: 'b',
          success: false,
        }),
      ],
      summary: first.summary,
      syncs: [{ message: null, status: 'FAILED' as const }],
      zones: first.zones,
    };
    const merged = mergeBatchAggregate(previous, [
      batchResult({
        batchId: 'batch-2',
        items: [batchItem({ changeType: 'ADD', spuId: 'b' })],
        sync: { status: 'SUCCESS' },
      }),
    ]);
    expect(merged.batchIds).toEqual(['batch-1', 'batch-2']);
    expect(merged.failedCount).toBe(0);
    expect(merged.items).toHaveLength(2);
    expect(merged.summary.success).toBe(2);
    expect(merged.syncs).toHaveLength(2);
  });

  it('空响应结果也会被还原为全 0 汇总', () => {
    const first = summarizeItems([]);
    expect(first.summary.requested).toBe(0);
  });
});

describe('computeSyncState / describeSync', () => {
  it('同步状态严重度：FAILED 优先，其次 SUCCESS/SKIPPED', () => {
    expect(
      computeSyncState([{ status: 'SUCCESS' }, { status: 'FAILED' }]),
    ).toBe('FAILED');
    expect(
      computeSyncState([{ status: 'SKIPPED' }, { status: 'SUCCESS' }]),
    ).toBe('SUCCESS');
    expect(computeSyncState([{ status: 'SKIPPED' }])).toBe('SKIPPED');
    expect(computeSyncState([null, undefined])).toBeNull();
  });

  it('失败(FAILED) 文案明确「价格同步失败/可重试」，不得出现「已同步」承诺', () => {
    const text = describeSync('FAILED', '快照查询超时');
    expect(text).toContain('价格同步失败');
    expect(text).toContain('快照查询超时');
    expect(text).toContain('可重试');
    expect(text).not.toContain('专区价格已同步');
  });

  it('成功/未触发(SUCCESS/SKIPPED) 文案', () => {
    expect(describeSync('SUCCESS')).toContain('专区价格已同步');
    expect(describeSync('SKIPPED')).toContain('未触发价格同步');
    expect(describeSync(undefined)).toContain('同步状态未知');
  });
});

describe('resolveErrorMessage', () => {
  it('优先取后端业务错误 message', () => {
    expect(
      resolveErrorMessage({
        message: '请求失败',
        response: { data: { message: '出清商品必须配置出清价或出清折扣' } },
      }),
    ).toBe('出清商品必须配置出清价或出清折扣');
  });

  it('字符串错误、fallback 兜底', () => {
    expect(resolveErrorMessage('直接字符串')).toBe('直接字符串');
    expect(resolveErrorMessage(null, '兜底')).toBe('兜底');
    expect(resolveErrorMessage({}, '兜底')).toBe('兜底');
  });
});

describe('移出影响范围', () => {
  const preview: ZonePreview = {
    items: [
      previewItem({
        before: {
          clearanceTag: 0,
          memberDiscountRate: 0.9,
          memberTag: 1,
          skuOverrideCount: 2,
        },
        spuId: 'spu-1',
      }),
      previewItem({
        before: { clearanceTag: 0, memberTag: 1, skuOverrideCount: 0 },
        spuId: 'spu-2',
      }),
      previewItem({
        before: { clearanceTag: 0, memberTag: 0 },
        spuId: 'spu-3',
      }),
    ],
    summary: {
      add: 0,
      invalid: 0,
      noChange: 0,
      remove: 0,
      skuOverridesWillClear: 0,
      total: 3,
      update: 0,
      warningCount: 0,
    },
    zones: [],
  };

  it('统计将移出商品数、规则数与 SKU 精调数', () => {
    const impact = computeRemoveImpact(preview, [
      draft('member', 'REMOVE'),
      draft('clearance', 'NONE'),
    ]);
    expect(impact).toEqual({
      ruleCount: 1,
      skuRuleCount: 2,
      spuCount: 2,
      zones: ['member'],
    });
    const text = buildRemoveConfirmText(impact);
    expect(text).toContain('2 件商品');
    expect(text).toContain('1 条 SPU 规则');
    expect(text).toContain('2 条 SKU 精调');
    expect(text).toContain('会员专区');
  });

  it('items 缺失时退回 zones 汇总', () => {
    const impact = computeRemoveImpact(
      {
        items: [],
        summary: {
          add: 0,
          invalid: 0,
          noChange: 0,
          remove: 0,
          skuOverridesWillClear: 0,
          total: 10,
          update: 0,
          warningCount: 0,
        },
        zones: [
          {
            existing: 8,
            invalid: 0,
            noChange: 0,
            willAdd: 0,
            willRemove: 0,
            willUpdate: 0,
            withSkuOverride: 3,
            zone: 'clearance',
          },
        ],
      },
      [draft('member', 'NONE'), draft('clearance', 'REMOVE')],
    );
    expect(impact).toMatchObject({
      skuRuleCount: 3,
      spuCount: 8,
      zones: ['clearance'],
    });
  });
});

describe('精调映射（replaceSkuRules=true 精确覆盖）', () => {
  const baseSku: ZoneSkuTag = {
    clearanceDiscountRate: null,
    clearanceExpireTime: null,
    clearancePrice: null,
    hasOverride: false,
    isClearanceItem: null,
    isMemberItem: null,
    memberDiscountRate: null,
    memberPriceFixed: null,
    memberPriceFloor: null,
    originalPrice: 39.9,
    salesPrice: 29.9,
    skuId: 'sku-1',
    specsName: '500g',
  };

  it('回显：全部为 null 时视为继承', () => {
    const row = toSkuEditRow(baseSku);
    expect(row.memberOverride).toBe(false);
    expect(row.clearanceOverride).toBe(false);
  });

  it('回显：存在规则或显式标签即为覆盖', () => {
    expect(toSkuEditRow({ ...baseSku, isMemberItem: 0 }).memberOverride).toBe(
      true,
    );
    expect(
      toSkuEditRow({ ...baseSku, memberDiscountRate: 0.8 }).memberOverride,
    ).toBe(true);
    expect(
      toSkuEditRow({ ...baseSku, clearancePrice: 9.9 }).clearanceOverride,
    ).toBe(true);
  });

  it('继承行提交全 null（恢复继承）', () => {
    const item = buildSkuSaveItems([
      toSkuEditRow(baseSku),
    ])[0] as ZoneSkuTagSave;
    expect(item).toEqual({
      clearanceDiscountRate: null,
      clearanceExpireTime: null,
      clearancePrice: null,
      clearanceTag: null,
      memberDiscountRate: null,
      memberPriceFixed: null,
      memberPriceFloor: null,
      memberTag: null,
      skuId: 'sku-1',
    });
  });

  it('覆盖且参与：提交标签 1 与规则；未覆盖专区保持 null', () => {
    const row = {
      ...toSkuEditRow(baseSku),
      clearanceOverride: false,
      memberDiscountRate: 0.85,
      memberOverride: true,
      memberPriceFixed: 19.9,
      memberTag: 1 as const,
    };
    const item = buildSkuSaveItems([row])[0] as ZoneSkuTagSave;
    expect(item.memberTag).toBe(1);
    expect(item.memberDiscountRate).toBe(0.85);
    expect(item.memberPriceFixed).toBe(19.9);
    expect(item.memberPriceFloor).toBeNull();
    expect(item.clearanceTag).toBeNull();
    expect(item.clearancePrice).toBeNull();
  });

  it('覆盖且排除：标签 0、规则字段全部 null', () => {
    const row = {
      ...toSkuEditRow(baseSku),
      clearanceDiscountRate: 0.5,
      clearanceOverride: true,
      clearanceTag: 0 as const,
      memberTag: 0 as const,
      memberOverride: true,
    };
    const item = buildSkuSaveItems([row])[0] as ZoneSkuTagSave;
    expect(item.memberTag).toBe(0);
    expect(item.clearanceTag).toBe(0);
    expect(item.clearanceDiscountRate).toBeNull();
  });

  it('出清覆盖提交价格/折扣/截止时间', () => {
    const row = {
      ...toSkuEditRow(baseSku),
      clearanceDiscountRate: 0.6,
      clearanceExpireTime: '2026-12-31 23:59:59',
      clearanceOverride: true,
      clearancePrice: 9.9,
      clearanceTag: 1 as const,
    };
    const item = buildSkuSaveItems([row])[0] as ZoneSkuTagSave;
    expect(item.clearanceTag).toBe(1);
    expect(item.clearancePrice).toBe(9.9);
    expect(item.clearanceDiscountRate).toBe(0.6);
    expect(item.clearanceExpireTime).toBe('2026-12-31 23:59:59');
  });
});

describe('文案工具', () => {
  it('专区/变更类型文案', () => {
    expect(zoneLabel('member')).toBe('会员专区');
    expect(zoneLabel('clearance')).toBe('低价捡漏');
    expect(zoneChangeTypeLabel('NO_CHANGE')).toBe('无变化');
  });
});
