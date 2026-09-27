import { describe, expect, it } from 'vitest';

import { isConfiguredRule, sanitizeCondition } from './condition';

describe('isConfiguredRule', () => {
  it('treats empty id lists as unconfigured', () => {
    expect(isConfiguredRule({ type: 'memberLevel', memberLevelIds: [] })).toBe(
      false,
    );
    expect(
      isConfiguredRule({ type: 'memberLevel', memberLevelIds: ['lv1'] }),
    ).toBe(true);
    expect(isConfiguredRule({ type: 'userTag', userTagIds: [] })).toBe(false);
    expect(isConfiguredRule({ type: 'userTag', userTagIds: ['t1'] })).toBe(
      true,
    );
  });

  it('treats incomplete time ranges as unconfigured', () => {
    expect(
      isConfiguredRule({ type: 'timeRange', startTime: '', endTime: '21:00' }),
    ).toBe(false);
    expect(
      isConfiguredRule({ type: 'timeRange', startTime: '09:00', endTime: '' }),
    ).toBe(false);
    expect(
      isConfiguredRule({
        type: 'timeRange',
        startTime: '09:00',
        endTime: '21:00',
      }),
    ).toBe(true);
  });

  it('treats login/guest rules as always configured', () => {
    expect(isConfiguredRule({ type: 'login' })).toBe(true);
    expect(isConfiguredRule({ type: 'guest' })).toBe(true);
  });
});

describe('sanitizeCondition', () => {
  it('drops unconfigured rules when writing back to the document', () => {
    const condition = {
      logic: 'and' as const,
      rules: [
        { type: 'memberLevel' as const, memberLevelIds: [] },
        { type: 'memberLevel' as const, memberLevelIds: ['lv1'] },
        { type: 'userTag' as const, userTagIds: [] },
      ],
    };

    expect(sanitizeCondition(condition)).toEqual({
      logic: 'and',
      rules: [{ type: 'memberLevel', memberLevelIds: ['lv1'] }],
    });
    // 深拷贝：不得把面板里的响应式对象引用写进文档
    expect(sanitizeCondition(condition)).not.toBe(condition.rules[1]);
  });

  it('normalizes to always when every rule is unconfigured', () => {
    expect(
      sanitizeCondition({
        logic: 'or',
        rules: [
          { type: 'memberLevel', memberLevelIds: [] },
          { type: 'userTag', userTagIds: [] },
        ],
      }),
    ).toBe('always');
  });

  it('keeps string shorthands untouched', () => {
    expect(sanitizeCondition('login')).toBe('login');
    expect(sanitizeCondition('always')).toBe('always');
  });
});
