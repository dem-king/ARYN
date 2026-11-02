import type {
  SessionInvalidReason,
  SessionInvalidSubReason,
} from '#/store/session';

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { i18n } from '@vben/locales';

import dayjs from 'dayjs';
import { beforeAll, describe, expect, it } from 'vitest';

import { $t } from '#/locales';

import {
  canSessionInvalidRelogin,
  resolveSessionInvalidContentKey,
  resolveSessionInvalidView,
} from './session-invalid-content';

const appRoot = resolve(process.cwd(), 'apps/web-ele');
const zhLocale = JSON.parse(
  readFileSync(
    resolve(
      process.cwd(),
      'packages/locales/src/langs/zh-CN/sessionInvalid.json',
    ),
    'utf8',
  ),
) as Record<string, any>;

type Translate = (key: string, params?: Record<string, unknown>) => string;

const translate = ((key: string, params?: Record<string, unknown>) =>
  params ? $t(key, params as any) : $t(key)) as Translate;

const NOW = dayjs('2026-10-28 22:00:00');
const INVALID_AT = '2026-10-28 21:32:07';

beforeAll(() => {
  i18n.global.mergeLocaleMessage('zh-CN', { sessionInvalid: zhLocale });
  i18n.global.locale.value = 'zh-CN';
});

interface SubReasonCase {
  body: string;
  canRelogin: boolean;
  singleActionText: null | string;
  subReason: null | SessionInvalidSubReason | string;
  title: string;
}

const KICK_OUT_CASES: SubReasonCase[] = [
  {
    body: '管理员已强制将你下线。如非本人预期，请联系管理员。',
    canRelogin: true,
    singleActionText: null,
    subReason: 'ADMIN_KICK',
    title: '已被管理员下线',
  },
  {
    body: '密码已修改，请使用新密码重新登录。',
    canRelogin: true,
    singleActionText: null,
    subReason: 'PASSWORD_CHANGED',
    title: '密码已修改',
  },
  {
    body: '该账号已被停用，无法继续使用。如有疑问请联系管理员。',
    canRelogin: false,
    singleActionText: '返回登录',
    subReason: 'ACCOUNT_DISABLED',
    title: '账号已被停用',
  },
  {
    body: '该账号已被删除，无法继续使用。',
    canRelogin: false,
    singleActionText: '返回登录',
    subReason: 'ACCOUNT_DELETED',
    title: '账号已被删除',
  },
  {
    body: '你所属的租户已被停用或已到期，请联系平台管理员。',
    canRelogin: false,
    singleActionText: '退出',
    subReason: 'TENANT_DISABLED',
    title: '所属租户已停用',
  },
];

describe('subReason copy and button rules (二期契约 §2)', () => {
  it.each(KICK_OUT_CASES)(
    'renders KICK_OUT + $subReason copy/buttons',
    (testCase) => {
      const view = resolveSessionInvalidView(
        {
          invalidAt: INVALID_AT,
          reason: 'KICK_OUT',
          subReason: testCase.subReason as SessionInvalidSubReason,
        },
        translate,
        NOW,
      );

      expect(view.title).toBe(testCase.title);
      expect(view.body).toBe(testCase.body);
      expect(view.canRelogin).toBe(testCase.canRelogin);
      expect(view.singleActionText).toBe(testCase.singleActionText);
    },
  );

  it.each([
    ['no subReason', undefined],
    ['unknown subReason', 'SOMETHING_NEW'],
    // 移动端专用细分原因：管理端不出现，按未知值降级为 KICK_OUT 通用文案
    ['mobile-only MEMBER_DISABLED', 'MEMBER_DISABLED'],
    ['mobile-only DELIVERY_REVOKED', 'DELIVERY_REVOKED'],
  ])('degrades %s to the generic KICK_OUT copy', (_, subReason) => {
    const view = resolveSessionInvalidView(
      {
        invalidAt: INVALID_AT,
        reason: 'KICK_OUT',
        subReason: subReason as SessionInvalidSubReason,
      },
      translate,
      NOW,
    );

    expect(view.title).toBe('登录状态已失效');
    expect(view.body).toBe('登录状态已失效，请重新登录。');
    expect(view.canRelogin).toBe(true);
    expect(view.singleActionText).toBeNull();
  });

  it('keeps phase-1 copy for other reasons even with a stray subReason', () => {
    const view = resolveSessionInvalidView(
      {
        invalidAt: INVALID_AT,
        reason: 'BE_REPLACED',
        subReason: 'ADMIN_KICK',
      },
      translate,
      NOW,
    );

    expect(view.title).toBe('账号已在其他设备登录');
    expect(view.canRelogin).toBe(true);
    expect(view.singleActionText).toBeNull();
  });

  it('ignores unknown/absent reason (legacy backend)', () => {
    const view = resolveSessionInvalidView(
      { invalidAt: INVALID_AT, reason: null },
      translate,
      NOW,
    );

    expect(view.title).toBe('登录状态已失效');
    expect(view.body).toBe('登录状态已失效，请重新登录。');
    expect(view.canRelogin).toBe(true);
  });

  describe('content key resolution', () => {
    it.each([
      ['KICK_OUT', 'ACCOUNT_DISABLED', 'KICK_OUT_ACCOUNT_DISABLED'],
      ['KICK_OUT', 'TENANT_DISABLED', 'KICK_OUT_TENANT_DISABLED'],
      ['KICK_OUT', 'MEMBER_DISABLED', 'KICK_OUT'],
      ['KICK_OUT', 'SOMETHING_NEW', 'KICK_OUT'],
      ['KICK_OUT', null, 'KICK_OUT'],
      ['TOKEN_EXPIRED', null, 'TOKEN_EXPIRED'],
      ['BE_REPLACED', 'ADMIN_KICK', 'BE_REPLACED'],
      [null, null, 'UNKNOWN'],
    ])('maps %s + %s to %s', (reason, subReason, expected) => {
      expect(
        resolveSessionInvalidContentKey(
          reason as null | SessionInvalidReason,
          subReason as null | SessionInvalidSubReason,
        ),
      ).toBe(expected);
    });
  });

  describe('canSessionInvalidRelogin', () => {
    it.each([
      ['KICK_OUT', 'ADMIN_KICK', true],
      ['KICK_OUT', 'PASSWORD_CHANGED', true],
      ['KICK_OUT', 'ACCOUNT_DISABLED', false],
      ['KICK_OUT', 'ACCOUNT_DELETED', false],
      ['KICK_OUT', 'TENANT_DISABLED', false],
      ['KICK_OUT', null, true],
      ['BE_REPLACED', null, true],
      ['TOKEN_EXPIRED', null, true],
    ])('reason=%s subReason=%s → %s', (reason, subReason, expected) => {
      expect(
        canSessionInvalidRelogin(
          reason as null | SessionInvalidReason,
          subReason as null | SessionInvalidSubReason,
        ),
      ).toBe(expected);
    });
  });
});

describe('session invalid modal button rendering contract', () => {
  const modalSource = readFileSync(
    resolve(appRoot, 'src/layouts/session-invalid-modal.vue'),
    'utf8',
  );

  it('only renders the relogin form when canRelogin is true', () => {
    expect(modalSource).toContain('v-if="view.canRelogin"');
    expect(modalSource).toContain(
      ':submit-text="$t(\'sessionInvalid.relogin\')"',
    );
  });

  it('renders a single exit-only button when canRelogin is false', () => {
    expect(modalSource).toContain('v-else');
    expect(modalSource).toContain('view.singleActionText');
    expect(modalSource).toContain('handleForcedExit');
    expect(modalSource).toContain('authStore.logout(false)');
  });

  it('keeps the modal non-dismissible', () => {
    expect(modalSource).toContain(':closable="false"');
    expect(modalSource).toContain(':close-on-press-escape="false"');
    expect(modalSource).toContain(':footer="false"');
  });
});
