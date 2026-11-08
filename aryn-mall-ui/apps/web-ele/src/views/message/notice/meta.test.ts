import type { NoticeRecord } from '#/api/message/types';

import { describe, expect, it } from 'vitest';

import {
  audienceSummary,
  canRetry,
  categoryMeta,
  deliveryPercent,
  deliveryText,
  dispatchMeta,
  noticeActionPlan,
  parseAudienceSnapshot,
  rowCommands,
  splitDateTime,
  statusMeta,
  targetTypesText,
  taskStatusMeta,
} from './meta';

function record(overrides: Partial<NoticeRecord> = {}): NoticeRecord {
  return {
    category: 'SYSTEM',
    content: '正文',
    createTime: '2026-11-06 12:00:00',
    dispatchStatus: 'NONE',
    id: 'notice-1',
    priority: 'NORMAL',
    status: 'DRAFT',
    summary: '摘要',
    targetTypes: 'MALL_USER',
    title: '标题',
    ...overrides,
  };
}

/**
 * 回归守卫：改动前基线实测「分类列 12/12 行显示原始枚举、`VESSEL_CALL_CHANGE`
 * 在 120px 列宽里换行撑高行高」，这里钉住「任何已知枚举都必须有中文标签」。
 */
describe('categoryMeta', () => {
  it('translates every category the backend actually emits', () => {
    const created = [
      'ORDER',
      'REFUND',
      'PROMOTION',
      'DELIVERY_TASK',
      'VESSEL_CALL_CHANGE',
      'SYSTEM',
    ];
    for (const raw of created) {
      const meta = categoryMeta(raw);
      expect(meta.label).not.toBe(raw);
      expect(meta.label).toMatch(/[\u4E00-\u9FA5]/);
    }
  });

  it('keeps an unknown category readable instead of blank', () => {
    expect(categoryMeta('CUSTOM_SOMETHING')).toEqual({
      label: 'CUSTOM_SOMETHING',
      tone: 'info',
    });
    expect(categoryMeta('')).toEqual({ label: '—', tone: 'info' });
    expect(categoryMeta(null)).toEqual({ label: '—', tone: 'info' });
  });

  it('is case insensitive against the stored enum', () => {
    expect(categoryMeta('order').label).toBe(categoryMeta('ORDER').label);
  });
});

describe('statusMeta / taskStatusMeta', () => {
  it('maps the three notice states', () => {
    expect(statusMeta('DRAFT').label).toBe('草稿');
    expect(statusMeta('PUBLISHED').label).toBe('已发布');
    expect(statusMeta('REVOKED').label).toBe('已撤回');
  });

  it('maps dispatch task states including partial failure', () => {
    expect(taskStatusMeta('PARTIAL_FAILED').label).toBe('部分失败');
    expect(taskStatusMeta('SUCCEEDED').tone).toBe('success');
    expect(taskStatusMeta(undefined)).toEqual({ label: '—', tone: 'info' });
  });
});

describe('dispatchMeta', () => {
  it('hides dispatch for draft and revoked rows', () => {
    expect(
      dispatchMeta({ dispatchStatus: 'SUCCEEDED', status: 'DRAFT' }),
    ).toEqual({ label: '—', tone: 'info' });
    expect(
      dispatchMeta({ dispatchStatus: 'FAILED', status: 'REVOKED' }),
    ).toEqual({
      label: '—',
      tone: 'info',
    });
  });

  it('falls back to NOT dispatched when published without a dispatch status', () => {
    expect(dispatchMeta({ status: 'PUBLISHED' })).toEqual({
      label: '未分发',
      tone: 'info',
    });
  });

  it('treats partial failure as warning, total failure as danger', () => {
    expect(
      dispatchMeta({ dispatchStatus: 'PARTIAL_FAILED', status: 'PUBLISHED' })
        .tone,
    ).toBe('warning');
    expect(
      dispatchMeta({ dispatchStatus: 'FAILED', status: 'PUBLISHED' }).tone,
    ).toBe('danger');
  });
});

describe('delivery metrics', () => {
  it('never divides by zero for an unpublished row', () => {
    expect(deliveryPercent({ readCount: 0, recipientCount: 0 })).toBe(0);
    expect(deliveryText({ readCount: 0, recipientCount: 0 })).toBe('—');
  });

  it('reports a percentage and a read / total pair', () => {
    expect(deliveryPercent({ readCount: 3, recipientCount: 4 })).toBe(75);
    expect(deliveryText({ readCount: 3, recipientCount: 4 })).toBe('3 / 4');
  });

  it('clamps dirty data into 0–100', () => {
    expect(deliveryPercent({ readCount: 9, recipientCount: 4 })).toBe(100);
    expect(deliveryPercent({ readCount: -2, recipientCount: 4 })).toBe(0);
  });
});

describe('targetTypesText', () => {
  it('translates every target端 and keeps order', () => {
    expect(targetTypesText('MALL_USER,SYS_USER')).toBe('会员、工作人员');
    expect(targetTypesText('SYS_USER')).toBe('工作人员');
    expect(targetTypesText(undefined)).toBe('—');
  });

  it('keeps unknown target types visible', () => {
    expect(targetTypesText('MALL_USER,SYSTEM')).toBe('会员、SYSTEM');
  });
});

describe('splitDateTime', () => {
  it('splits the backend datetime into a stable two-line cell', () => {
    expect(splitDateTime('2026-11-06 12:00:05')).toEqual({
      date: '2026-11-06',
      time: '12:00:05',
    });
  });

  it('accepts the T separator and a missing seconds part', () => {
    expect(splitDateTime('2026-11-06T12:00')).toEqual({
      date: '2026-11-06',
      time: '12:00',
    });
  });

  it('never renders undefined for empty or dirty values', () => {
    expect(splitDateTime(undefined)).toEqual({ date: '—', time: '' });
    expect(splitDateTime('未知时间')).toEqual({ date: '未知时间', time: '' });
  });
});

describe('rowCommands', () => {
  it('offers edit / publish / delete for a draft', () => {
    expect(rowCommands({ status: 'DRAFT' })).toEqual([
      'edit',
      'publish',
      'delete',
    ]);
  });

  it('only offers republish for a revoked notice', () => {
    expect(rowCommands({ status: 'REVOKED' })).toEqual(['republish']);
  });

  it('adds retry only when the dispatch actually failed', () => {
    expect(
      rowCommands({ dispatchStatus: 'FAILED', status: 'PUBLISHED' }),
    ).toEqual(['revoke', 'retry']);
    expect(
      rowCommands({ dispatchStatus: 'SUCCEEDED', status: 'PUBLISHED' }),
    ).toEqual(['revoke']);
  });
});

describe('canRetry', () => {
  it('requires both a published notice and a failed dispatch', () => {
    expect(canRetry({ dispatchStatus: 'FAILED', status: 'PUBLISHED' })).toBe(
      true,
    );
    expect(
      canRetry({ dispatchStatus: 'PARTIAL_FAILED', status: 'PUBLISHED' }),
    ).toBe(true);
    expect(canRetry({ dispatchStatus: 'FAILED', status: 'DRAFT' })).toBe(false);
    expect(canRetry({ dispatchStatus: 'SUCCEEDED', status: 'PUBLISHED' })).toBe(
      false,
    );
  });
});

describe('audience snapshot', () => {
  it('survives dirty JSON instead of throwing', () => {
    expect(parseAudienceSnapshot('{oops')).toEqual({});
    expect(parseAudienceSnapshot(undefined)).toEqual({});
    expect(parseAudienceSnapshot('null')).toEqual({});
  });

  it('reads the frozen audience back', () => {
    const snapshot = parseAudienceSnapshot(
      JSON.stringify({ memberLevelId: 'lv-1', roleIds: ['r1'] }),
    );
    expect(snapshot.memberLevelId).toBe('lv-1');
    expect(snapshot.roleIds).toEqual(['r1']);
  });

  it('says 全部 when no condition was frozen (backend sends to everyone)', () => {
    expect(audienceSummary({}, 'MALL_USER')).toBe('全部会员');
    expect(audienceSummary({}, 'MALL_USER,SYS_USER')).toBe(
      '全部会员；全部工作人员',
    );
  });

  it('summarises level/tag and role/dept filters', () => {
    expect(audienceSummary({ memberLevelId: 'lv-1' }, 'MALL_USER')).toBe(
      '会员按等级/标签筛选',
    );
    expect(audienceSummary({ deptIds: ['d1'] }, 'SYS_USER')).toBe(
      '工作人员按角色/部门筛选',
    );
  });

  it('prefers an explicit id list over the filter wording', () => {
    expect(
      audienceSummary(
        { mallUserIds: ['u1', 'u2'], memberLevelId: 'lv-1' },
        'MALL_USER',
      ),
    ).toBe('指定会员 2 名');
  });
});

describe('record shape used by the table', () => {
  it('keeps a published row renderable end to end', () => {
    const row = record({
      dispatchStatus: 'PARTIAL_FAILED',
      readCount: 1,
      recipientCount: 2,
      status: 'PUBLISHED',
    });
    expect(deliveryText(row)).toBe('1 / 2');
    expect(deliveryPercent(row)).toBe(50);
    expect(dispatchMeta(row).label).toBe('部分失败');
    expect(rowCommands(row)).toEqual(['revoke', 'retry']);
  });
});

/**
 * 动作文案单源化守卫：列表「更多」与详情抽屉必须给出同一套确认承诺。
 * 这里钉住「不泄漏占位符」「发布文案带上真实目标端」「危险动作标记正确」。
 */
describe('noticeActionPlan', () => {
  it('never leaks the {target} placeholder', () => {
    for (const action of [
      'delete',
      'publish',
      'republish',
      'retry',
      'revoke',
    ] as const) {
      const plan = noticeActionPlan(action, 'MALL_USER');
      expect(plan.message).not.toContain('{target}');
      expect(plan.title).not.toBe('');
      expect(plan.ok).not.toBe('');
      expect(plan.done).not.toBe('');
    }
  });

  it('fills the publish promise with the real audience', () => {
    const plan = noticeActionPlan('publish', 'MALL_USER,SYS_USER');
    expect(plan.message).toContain('会员、工作人员');
    expect(plan.message).toContain('正文不可再修改');
  });

  it('marks only delete and retry as destructive', () => {
    expect(noticeActionPlan('delete', '').danger).toBe(true);
    expect(noticeActionPlan('retry', '').danger).toBe(true);
    expect(noticeActionPlan('publish', '').danger).toBe(false);
    expect(noticeActionPlan('revoke', '').danger).toBe(false);
    expect(noticeActionPlan('republish', '').danger).toBe(false);
  });

  it('keeps every confirmation a complete sentence instead of a bare question', () => {
    for (const action of [
      'delete',
      'publish',
      'republish',
      'retry',
      'revoke',
    ] as const) {
      // 说明后果才叫确认：至少一句完整说明，不能只有「确定吗」
      expect(noticeActionPlan(action, '').message.length).toBeGreaterThan(10);
    }
  });
});
