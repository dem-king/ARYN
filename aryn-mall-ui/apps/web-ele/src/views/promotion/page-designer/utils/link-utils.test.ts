import { describe, expect, it } from 'vitest';

import {
  isSafeInternalPath,
  normalizeDecorationLink,
  validateDecorationLink,
} from './link-utils';

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

describe('strict link validation (link editor only)', () => {
  const miniProgram = {
    params: { envVersion: 'release' },
    path: 'pages/index/index',
    type: 'mini-program' as const,
  };

  it('keeps publish validation lenient for legacy mini-program links without appId', () => {
    expect(validateDecorationLink(miniProgram)).toEqual([]);
  });

  it('requires a trimmed appId and a non-slash-prefixed path in strict mode', () => {
    expect(validateDecorationLink(miniProgram, { strict: true })).toContain(
      '请填写小程序 AppID',
    );
    expect(
      validateDecorationLink(
        { ...miniProgram, params: { appId: '   ' } },
        { strict: true },
      ),
    ).toContain('请填写小程序 AppID');
    expect(
      validateDecorationLink(
        { ...miniProgram, params: { appId: 'wx123' } },
        { strict: true },
      ),
    ).toEqual([]);
    expect(
      validateDecorationLink(
        { ...miniProgram, params: { appId: 'wx123' }, path: '/pages/index' },
        { strict: true },
      ),
    ).toContain('小程序页面路径不要以 / 开头');
  });

  it('rejects internal paths that the C-side resolver silently ignores', () => {
    const custom = (path: string) => ({
      params: {},
      path,
      type: 'custom' as const,
    });

    expect(
      validateDecorationLink(custom('//evil'), { strict: true }),
    ).toContain('页面路径需以 / 开头，且不能包含 // 或反斜杠');
    expect(
      validateDecorationLink(custom(String.raw`/pages\home`), { strict: true }),
    ).toContain('页面路径需以 / 开头，且不能包含 // 或反斜杠');
    expect(
      validateDecorationLink(custom('pages/home'), { strict: true }),
    ).toContain('页面路径需以 / 开头，且不能包含 // 或反斜杠');
    expect(
      validateDecorationLink(custom('/pages/home/index'), { strict: true }),
    ).toEqual([]);
  });

  it('mirrors the C-side safe internal path rule', () => {
    expect(isSafeInternalPath('/pages/home/index')).toBe(true);
    expect(isSafeInternalPath('/sub-pages/product/goods-list/index?a=1')).toBe(
      true,
    );
    expect(isSafeInternalPath('//evil.example.com')).toBe(false);
    expect(isSafeInternalPath('pages/home/index')).toBe(false);
    expect(isSafeInternalPath(String.raw`/pages\home`)).toBe(false);
    expect(isSafeInternalPath('')).toBe(false);
  });
});
