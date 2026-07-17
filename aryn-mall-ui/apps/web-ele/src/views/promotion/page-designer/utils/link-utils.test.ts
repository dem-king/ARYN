import { describe, expect, it } from 'vitest';

import { normalizeDecorationLink, validateDecorationLink } from './link-utils';

describe('structured decoration links', () => {
  it('requires targets for business entities and paths for external routes', () => {
    expect(validateDecorationLink({ params: {}, type: 'goods' })).toContain(
      '请选择目标',
    );
    expect(
      validateDecorationLink({ params: {}, type: 'mini-program' }),
    ).toContain('请输入路径');
    expect(
      validateDecorationLink({
        params: {},
        targetId: 'goods-1',
        type: 'goods',
      }),
    ).toEqual([]);
  });

  it('adapts legacy name/url values to a custom structured link', () => {
    expect(
      normalizeDecorationLink({ name: '搜索', url: '/pages/search' }),
    ).toEqual({
      params: {},
      path: '/pages/search',
      type: 'custom',
    });
  });
});
