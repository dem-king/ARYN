import { describe, expect, it } from 'vitest';

import {
  buildRemoveConfirmText,
  buildRuleChips,
  buildRuleSummary,
  buildZoneIdsPayload,
  buildZonePageQuery,
  changeTypeTagType,
  clampIdsLimit,
  compactFilter,
  dedupeIds,
  describeDefaultDiscount,
  describeEffectiveDiscount,
  describeEffectivePrice,
  describeSelectAllResult,
  describeSelection,
  describeSkuOverride,
  formatActionLabel,
  formatChangeType,
  formatMoney,
  formatPrice,
  formatRate,
  formatShortTime,
  formatTime,
  hasSkuOverride,
  hasZoneRule,
  isBatchUndoable,
  isSelectAllOversized,
  mergeFilterSelection,
  readBatchTotal,
  resolveActivityStatusMeta,
  resolveBatchDoneTone,
  resolveExpiryMeta,
  resolvePickerZoneType,
  resolveSelectAllLimit,
  resolveStateMeta,
  resolveSyncMeta,
  resolveUndoMeta,
  resolveZoneEnabledMeta,
  summarizeBatchActions,
  summarizeBatchDone,
  summarizeBatchItems,
  summarizeBatchRecord,
  summarizeUndoResult,
  toNumber,
} from './logic';

describe('筛选 payload', () => {
  it('compactFilter 去空、trim，并保持稳定 key 顺序', () => {
    expect(
      compactFilter({
        name: '  拌粉 ',
        categorySecondId: '',
        brandId: null,
        status: 0,
      }),
    ).toEqual({ name: '拌粉', status: '0' });
    expect(compactFilter(undefined)).toEqual({});
  });

  it('buildZonePageQuery 带专区与分页，空筛选不传给后端', () => {
    expect(
      buildZonePageQuery(
        'member',
        { name: '', categorySecondId: 'c2', brandId: 'b1', status: '' },
        { current: 0, size: 20 },
      ),
    ).toEqual({
      zoneType: 'member',
      current: 1,
      size: 20,
      categorySecondId: 'c2',
      brandId: 'b1',
    });
  });

  it('buildZoneIdsPayload 默认 limit=500 并收敛到 1~1000', () => {
    expect(buildZoneIdsPayload('clearance', { name: 'x' })).toEqual({
      zoneType: 'clearance',
      name: 'x',
      limit: 500,
    });
    expect(clampIdsLimit(0)).toBe(1);
    expect(clampIdsLimit(5000)).toBe(1000);
    expect(clampIdsLimit(undefined)).toBe(500);
    expect(buildZoneIdsPayload('member', {}, 1200).limit).toBe(1000);
  });
});

describe('全选筛选结果', () => {
  it('limit 按总数在 500 / 1000 之间切换', () => {
    expect(resolveSelectAllLimit(0)).toBe(500);
    expect(resolveSelectAllLimit(500)).toBe(500);
    expect(resolveSelectAllLimit(501)).toBe(1000);
    expect(resolveSelectAllLimit(1000)).toBe(1000);
    expect(resolveSelectAllLimit(5000)).toBe(1000);
  });

  it('超过 1000 件提示分批 / 缩小筛选', () => {
    expect(isSelectAllOversized(1001)).toBe(true);
    expect(isSelectAllOversized(1000)).toBe(false);
    expect(describeSelectAllResult(18, 18)).toBe('已全选筛选结果 18 件');
    const overflow = describeSelectAllResult(5000, 1000, true);
    expect(overflow).toContain('共 5000 件');
    expect(overflow).toContain('已全选前 1000 件');
    expect(overflow).toContain('分批操作');
    expect(describeSelectAllResult(0, 0)).toBe('筛选结果为空，无可选商品');
  });

  it('dedupeIds 转字符串、去重保序', () => {
    expect(dedupeIds(['1', 2, '2', '', null, undefined, ' 3 '])).toEqual([
      '1',
      '2',
      '3',
    ]);
    expect(dedupeIds(null)).toEqual([]);
    expect(dedupeIds([1000, 1001])).toHaveLength(2);
  });

  it('describeSelection 区分手动选择与全选筛选结果', () => {
    expect(describeSelection(0)).toBe('未选择商品');
    expect(describeSelection(3)).toBe('已选 3 件');
    expect(describeSelection(18, 'filter', 18)).toBe(
      '已选 18 件（当前筛选全部）',
    );
    expect(describeSelection(1000, 'filter', 5000)).toBe(
      '已选 1000 件（筛选共 5000 件）',
    );
  });

  it('候选商品 zoneType 与当前专区互补', () => {
    expect(resolvePickerZoneType('member')).toBe('not_member');
    expect(resolvePickerZoneType('clearance')).toBe('not_clearance');
  });
});

describe('金额 / 折扣 / 时间格式化', () => {
  it('toNumber 区分 0 与未设置', () => {
    expect(toNumber(0)).toBe(0);
    expect(toNumber('0')).toBe(0);
    expect(toNumber('')).toBeNull();
    expect(toNumber(null)).toBeNull();
    expect(toNumber('abc')).toBeNull();
  });

  it('金额两位小数，空值显示 —', () => {
    expect(formatPrice(39.9)).toBe('¥39.90');
    expect(formatPrice('35')).toBe('¥35.00');
    expect(formatPrice(null)).toBe('—');
    expect(formatMoney(0)).toBe('0.00');
    expect(formatMoney(undefined)).toBe('—');
  });

  it('折扣率 0.9 → 9折，0.855 → 8.55折，非法值 —', () => {
    expect(formatRate(0.9)).toBe('9折');
    expect(formatRate('0.85')).toBe('8.5折');
    expect(formatRate(0.855)).toBe('8.55折');
    expect(formatRate(1)).toBe('10折');
    expect(formatRate(0)).toBe('—');
    expect(formatRate(1.2)).toBe('—');
    expect(describeDefaultDiscount(null)).toBe('未设置');
    expect(describeDefaultDiscount(0.9)).toBe('9折');
  });

  it('时间截断到分钟并兼容 ISO', () => {
    expect(formatTime('2026-11-04 10:00:00')).toBe('2026-11-04 10:00');
    expect(formatTime('2026-11-04T10:00:00')).toBe('2026-11-04 10:00');
    expect(formatTime('')).toBe('—');
    expect(formatTime(null)).toBe('—');
  });
});

describe('规则摘要与生效状态', () => {
  it('会员规则：折扣 / 固定价 / 下限', () => {
    expect(
      buildRuleSummary('member', {
        isMemberItem: 1,
        memberDiscountRate: 0.9,
        memberPriceFixed: 29.9,
        memberPriceFloor: 12,
      }),
    ).toEqual(['折扣 9折', '固定价 ¥29.90', '下限 ¥12.00']);
  });

  it('未配规则 / 未加入', () => {
    expect(buildRuleSummary('member', { isMemberItem: 1 })).toEqual([
      '未配规则',
    ]);
    expect(buildRuleSummary('member', { isMemberItem: 0 })).toEqual(['未加入']);
    expect(buildRuleSummary('clearance', { isClearanceItem: 0 })).toEqual([
      '未加入',
    ]);
  });

  it('出清规则：出清价 / 折扣 / 截止时间', () => {
    expect(
      buildRuleSummary('clearance', {
        isClearanceItem: 1,
        clearancePrice: 19.9,
        clearanceExpireTime: '2026-12-31 23:59:59',
      }),
    ).toEqual(['出清价 ¥19.90', '截止 2026-12-31 23:59']);
  });

  it('sKU 精调单独展示，不混入规则摘要', () => {
    expect(
      buildRuleSummary('member', { isMemberItem: 1, memberDiscountRate: 0.9 }),
    ).toEqual(['折扣 9折']);
    expect(describeSkuOverride({ skuOverrideCount: 2 })).toBe('2 个规格精调');
    expect(describeSkuOverride({ skuOverrideCount: 0 })).toBe('—');
    expect(hasSkuOverride({ skuOverrideCount: 2 })).toBe(true);
    expect(hasSkuOverride({})).toBe(false);
  });

  it('state 映射为 5 种生效状态，未知回退 stateLabel', () => {
    expect(resolveStateMeta('NORMAL')).toMatchObject({
      type: 'success',
      label: '生效中',
    });
    expect(resolveStateMeta('NO_RULE').type).toBe('warning');
    expect(resolveStateMeta('INVERTED').type).toBe('danger');
    expect(resolveStateMeta('EXPIRED').type).toBe('info');
    expect(resolveStateMeta('OFF_SHELF').type).toBe('info');
    expect(resolveStateMeta(null, '自定义')).toMatchObject({
      type: 'info',
      label: '自定义',
    });
    expect(resolveStateMeta(undefined).label).toBe('未知');
  });

  it('生效价带 SKU 精调标记', () => {
    expect(describeEffectivePrice({ effectivePrice: 35.91 })).toBe('¥35.91');
    expect(
      describeEffectivePrice({ effectivePrice: 35.91, skuOverrideCount: 2 }),
    ).toBe('¥35.91（含 SKU 精调）');
    expect(describeEffectivePrice({})).toBe('—');
  });
});

describe('同步 / 活动状态', () => {
  it('同步状态三态', () => {
    expect(resolveSyncMeta('SUCCESS')).toMatchObject({
      type: 'success',
      label: '同步成功',
    });
    expect(resolveSyncMeta('FAILED')).toMatchObject({
      type: 'danger',
      label: '同步失败',
    });
    expect(resolveSyncMeta('FAILED').tip).toContain('重试同步');
    expect(resolveSyncMeta(null)).toMatchObject({
      type: 'warning',
      label: '未同步',
    });
  });

  it('活动状态：数字状态码（PromotionActivity.STATUS_*）映射', () => {
    // 1 草稿 / 2 已发布 / 3 已暂停 / 4 已结束 / 5 未开始
    expect(resolveActivityStatusMeta('1')).toMatchObject({
      label: '草稿',
      type: 'info',
    });
    expect(resolveActivityStatusMeta('2')).toMatchObject({
      label: '已发布',
      type: 'success',
    });
    expect(resolveActivityStatusMeta('3')).toMatchObject({
      label: '已暂停',
      type: 'warning',
    });
    expect(resolveActivityStatusMeta('4')).toMatchObject({
      label: '已结束',
      type: 'info',
    });
    expect(resolveActivityStatusMeta('5')).toMatchObject({
      label: '未开始',
      type: 'info',
    });
  });

  it('活动状态：兼容旧字符串口径与空值', () => {
    expect(resolveActivityStatusMeta('PUBLISHED')).toMatchObject({
      label: '已发布',
      type: 'success',
    });
    expect(resolveActivityStatusMeta('paused')).toMatchObject({
      label: '已暂停',
      type: 'warning',
    });
    expect(resolveActivityStatusMeta('DRAFT')).toMatchObject({ label: '草稿' });
    expect(resolveActivityStatusMeta('ENDED')).toMatchObject({
      label: '已结束',
    });
    expect(resolveActivityStatusMeta('UPCOMING')).toMatchObject({
      label: '未开始',
    });
    // 未知/空值回退「未发布」，不会把已发布活动误判
    expect(resolveActivityStatusMeta(null)).toMatchObject({
      label: '未发布',
      type: 'info',
    });
    expect(resolveActivityStatusMeta('')).toMatchObject({ label: '未发布' });
  });
});

describe('批量结果聚合', () => {
  it('动作文案与批次动作摘要', () => {
    expect(formatActionLabel('ADD')).toBe('加入/更新');
    expect(formatActionLabel('REMOVE')).toBe('移出');
    expect(formatActionLabel('NONE')).toBe('不修改');
    expect(formatActionLabel(null)).toBe('—');
    expect(summarizeBatchActions('ADD', 'NONE')).toBe('会员：加入/更新');
    expect(summarizeBatchActions('REMOVE', 'REMOVE')).toBe(
      '会员：移出 · 出清：移出',
    );
    expect(summarizeBatchActions('NONE', null)).toBe('无动作');
  });

  it('批次记录摘要与总件数', () => {
    expect(
      summarizeBatchRecord({ total: 10, changed: 2, noChange: 8, failed: 0 }),
    ).toBe('变更 2 · 无变化 8 · 失败 0');
    expect(readBatchTotal({ total: 10 })).toBe(10);
    expect(readBatchTotal({})).toBe(0);
  });

  it('done 事件摘要：成功/无变化/失败', () => {
    const payload = {
      summary: { success: 10, noChange: 8, failed: 2, changed: 2 },
      failedCount: 2,
    };
    expect(summarizeBatchDone(payload)).toBe('成功 10 · 无变化 8 · 失败 2');
    expect(resolveBatchDoneTone(payload)).toBe('warning');
    expect(
      summarizeBatchDone({
        summary: { success: 3, noChange: 0, failed: 0 },
        failedCount: 0,
      }),
    ).toBe('成功 3');
    expect(resolveBatchDoneTone({ summary: null, failedCount: 0 })).toBe(
      'success',
    );
    expect(summarizeBatchDone(null)).toBe('操作完成');
    expect(summarizeBatchDone({ failedCount: 1 })).toBe('操作完成，失败 1 件');
  });

  it('明细聚合区分成功/无变化/失败/不生效', () => {
    const summary = summarizeBatchItems([
      { changeType: 'ADD', success: true },
      { changeType: 'UPDATE', success: true },
      { changeType: 'NO_CHANGE', success: true },
      { changeType: 'REMOVE', success: true },
      { changeType: 'INVALID', success: false },
      { changeType: null, success: false },
    ]);
    expect(summary).toMatchObject({
      total: 6,
      success: 4,
      changed: 3,
      noChange: 1,
      failed: 2,
      invalid: 1,
    });
    expect(summarizeBatchItems(null).total).toBe(0);
  });

  it('变更类型文案与标签色', () => {
    expect(formatChangeType('ADD')).toBe('新增');
    expect(formatChangeType('NO_CHANGE')).toBe('无变化');
    expect(formatChangeType('INVALID')).toBe('不生效');
    expect(formatChangeType('unknown')).toBe('—');
    expect(changeTypeTagType('ADD')).toBe('success');
    expect(changeTypeTagType('INVALID')).toBe('danger');
    expect(changeTypeTagType('REMOVE')).toBe('warning');
    expect(changeTypeTagType('NO_CHANGE')).toBe('info');
  });
});

describe('撤销结果', () => {
  it('全部撤销成功', () => {
    expect(summarizeUndoResult({ restored: 5, skipped: 0 })).toMatchObject({
      tone: 'success',
      summary: '已撤销 5 件商品',
      conflictCount: 0,
    });
  });

  it('存在冲突时提示跳过并给出冲突数', () => {
    const result = summarizeUndoResult({
      restored: 3,
      skipped: 2,
      conflicts: [
        { spuId: '1', spuName: 'A', reason: '商品已被再次修改' },
        { spuId: '2', reason: '规则冲突' },
      ],
    });
    expect(result.tone).toBe('warning');
    expect(result.summary).toContain('跳过 2 件');
    expect(result.conflictCount).toBe(2);
  });

  it('空结果与无可撤销', () => {
    expect(summarizeUndoResult(null).tone).toBe('info');
    expect(summarizeUndoResult({ restored: 0, skipped: 0 }).summary).toContain(
      '没有可撤销',
    );
  });

  it('撤销状态与可撤销判断', () => {
    expect(resolveUndoMeta('UNDONE')).toMatchObject({
      type: 'success',
      label: '已撤销',
    });
    expect(resolveUndoMeta(null)).toMatchObject({
      type: 'info',
      label: '可撤销',
    });
    expect(isBatchUndoable({ total: 10, undoStatus: '' })).toBe(true);
    expect(isBatchUndoable({ total: 10, undoStatus: 'UNDONE' })).toBe(false);
    expect(isBatchUndoable({ total: 0 })).toBe(false);
    expect(isBatchUndoable(null)).toBe(false);
  });

  it('移出确认文案包含专区名与影响范围', () => {
    const text = buildRemoveConfirmText('member', 3);
    expect(text).toContain('3 件商品');
    expect(text).toContain('会员专区');
    expect(text).toContain('SKU 精调');
  });
});

describe('列表价格 / 到期 / 规则标记（改版新增口径）', () => {
  it('生效价相对售价的实际折扣，无优惠不展示', () => {
    expect(describeEffectiveDiscount(8.91, 9.9)).toBe('9折');
    expect(describeEffectiveDiscount('7.92', 9.9)).toBe('8折');
    expect(describeEffectiveDiscount(9.9, 9.9)).toBeNull();
    expect(describeEffectiveDiscount(10, 9.9)).toBeNull();
    expect(describeEffectiveDiscount(null, 9.9)).toBeNull();
    expect(describeEffectiveDiscount(8.91, 0)).toBeNull();
  });

  it('短时间只保留 MM-DD HH:mm，非法值回退', () => {
    expect(formatShortTime('2026-11-08 12:46:00')).toBe('11-08 12:46');
    expect(formatShortTime('2026-11-08T12:46:00')).toBe('11-08 12:46');
    expect(formatShortTime('')).toBe('—');
    expect(formatShortTime('不是时间')).toBe('不是时间');
  });

  it('出清截止风险：已到期 / 3 天内 / 正常', () => {
    const now = new Date(2026, 10, 4, 20, 0, 0);
    expect(resolveExpiryMeta('2026-11-04 19:00:00', now)).toMatchObject({
      expired: true,
      label: '已到期',
      tone: 'danger',
    });
    expect(resolveExpiryMeta('2026-11-06 12:00:00', now)).toMatchObject({
      expired: false,
      tone: 'warning',
      label: '2 天后到期',
    });
    expect(resolveExpiryMeta('2026-11-30 12:00:00', now)).toBeNull();
    expect(resolveExpiryMeta('', now)).toBeNull();
    expect(resolveExpiryMeta(null, now)).toBeNull();
  });

  it('规则标记带提示色：未配置 / 未加入 / 到期预警', () => {
    const now = new Date(2026, 10, 4, 20, 0, 0);
    expect(buildRuleChips('member', { isMemberItem: 0 })).toEqual([
      { label: '未加入专区', tone: 'warning' },
    ]);
    expect(buildRuleChips('member', { isMemberItem: 1 })).toEqual([
      { label: '未配置规则' },
    ]);
    expect(
      buildRuleChips('member', {
        isMemberItem: 1,
        memberDiscountRate: 0.9,
        memberPriceFixed: 29.9,
      }),
    ).toEqual([{ label: '折扣 9折' }, { label: '固定价 ¥29.90' }]);
    expect(
      buildRuleChips(
        'clearance',
        {
          isClearanceItem: 1,
          clearancePrice: 7.9,
          clearanceExpireTime: '2026-11-06 12:00:00',
        },
        now,
      ),
    ).toEqual([
      { label: '出清价 ¥7.90' },
      { hint: '截止 2026-11-06 12:00', label: '2 天后到期', tone: 'warning' },
    ]);
    expect(
      buildRuleChips(
        'clearance',
        { isClearanceItem: 1, clearanceExpireTime: '2026-12-31 23:59:59' },
        now,
      ),
    ).toEqual([{ hint: '截止 2026-12-31 23:59', label: '截止 12-31 23:59' }]);
  });

  it('专区开关：同步状态优先，其次专区设置，最终未知', () => {
    expect(resolveZoneEnabledMeta(true, undefined)).toMatchObject({
      label: '已开启',
      tone: 'success',
    });
    expect(resolveZoneEnabledMeta(false, '1')).toMatchObject({
      label: '已关闭',
      tone: 'danger',
    });
    expect(resolveZoneEnabledMeta(undefined, '0')).toMatchObject({
      label: '已关闭',
    });
    expect(resolveZoneEnabledMeta(undefined, 1)).toMatchObject({
      label: '已开启',
    });
    expect(resolveZoneEnabledMeta(undefined, undefined)).toMatchObject({
      label: '未知',
      tone: 'unknown',
    });
  });
});

describe('全选筛选结果与规则归属（改版修复口径）', () => {
  it('filter 模式下取消当前页某行只从结果集移除该行，不整体退化', () => {
    const all = ['1', '2', '3', '4', '5'];
    expect(mergeFilterSelection(all, ['1', '2', '3'], ['1', '3'])).toEqual([
      '1',
      '3',
      '4',
      '5',
    ]);
    // 重新勾选加回
    expect(
      mergeFilterSelection(['2', '4', '5'], ['1', '2', '3'], ['1', '2', '3']),
    ).toEqual(['2', '4', '5', '1', '3']);
    // 当前页之外的 id 不受影响
    expect(mergeFilterSelection(all, ['1', '2'], ['1', '2'])).toEqual(all);
    expect(mergeFilterSelection(all, ['1', '2'], [])).toEqual(['3', '4', '5']);
    expect(mergeFilterSelection([], ['1'], [])).toEqual([]);
  });

  it('hasZoneRule 区分「未配置规则」与「继承默认折扣」', () => {
    expect(hasZoneRule('member', { isMemberItem: 1 })).toBe(false);
    expect(
      hasZoneRule('member', { isMemberItem: 1, memberDiscountRate: 0.9 }),
    ).toBe(true);
    expect(
      hasZoneRule('member', { isMemberItem: 1, memberPriceFixed: 0 }),
    ).toBe(true);
    expect(hasZoneRule('member', { isMemberItem: 0 })).toBe(false);
    expect(hasZoneRule('clearance', { isClearanceItem: 1 })).toBe(false);
    expect(
      hasZoneRule('clearance', {
        isClearanceItem: 1,
        clearanceExpireTime: '2026-12-01 00:00',
      }),
    ).toBe(true);
    expect(
      hasZoneRule('clearance', { isClearanceItem: 1, clearancePrice: 9.9 }),
    ).toBe(true);
  });
});
