import type {
  SessionInvalidDevice,
  SessionInvalidReason,
} from '#/store/session';

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { i18n } from '@vben/locales';

import dayjs from 'dayjs';
import { beforeAll, describe, expect, it } from 'vitest';

import { SESSION_INVALID_MODAL_Z_INDEX } from '#/api/session-invalid';
import { $t } from '#/locales';

import {
  formatSessionInvalidTime,
  resolveSessionInvalidDeviceLine,
  resolveSessionInvalidView,
} from './session-invalid-content';

const appRoot = resolve(process.cwd(), 'apps/web-ele');
const readAppSource = (path: string) =>
  readFileSync(resolve(appRoot, 'src', path), 'utf8');
const readLocaleJson = (lang: string) =>
  JSON.parse(
    readFileSync(
      resolve(
        process.cwd(),
        `packages/locales/src/langs/${lang}/sessionInvalid.json`,
      ),
      'utf8',
    ),
  ) as Record<string, any>;

const zhLocale = readLocaleJson('zh-CN');
const enLocale = readLocaleJson('en-US');

type Translate = (key: string, params?: Record<string, unknown>) => string;

const translate = ((key: string, params?: Record<string, unknown>) =>
  params ? $t(key, params as any) : $t(key)) as Translate;

const NOW = dayjs('2026-10-28 22:00:00');
const INVALID_AT = '2026-10-28 21:32:07';

beforeAll(() => {
  i18n.global.mergeLocaleMessage('zh-CN', { sessionInvalid: zhLocale });
  i18n.global.mergeLocaleMessage('en-US', { sessionInvalid: enLocale });
  i18n.global.locale.value = 'zh-CN';
});

function collectKeys(value: Record<string, any>, prefix = ''): string[] {
  return Object.entries(value).flatMap(([key, child]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return child && typeof child === 'object' && !Array.isArray(child)
      ? collectKeys(child, path)
      : [path];
  });
}

describe('session invalid copy mapping (table driven)', () => {
  const genericTitle = '登录状态已失效';
  const genericBody = '登录状态已失效，请重新登录。';
  const cases: Array<{
    body: string;
    reason: null | SessionInvalidReason;
    title: string;
  }> = [
    {
      body: '当前账号于 21:32 在另一台设备登录，你已被迫下线。重新登录会使那台设备下线。',
      reason: 'BE_REPLACED',
      title: '账号已在其他设备登录',
    },
    {
      body: '登录状态已过期，请重新登录。',
      reason: 'TOKEN_EXPIRED',
      title: '登录已过期',
    },
    { body: genericBody, reason: 'KICK_OUT', title: genericTitle },
    { body: genericBody, reason: 'TOKEN_INVALID', title: genericTitle },
    { body: genericBody, reason: 'NOT_TOKEN', title: genericTitle },
    { body: genericBody, reason: 'TOKEN_FREEZE', title: genericTitle },
    { body: genericBody, reason: 'NO_PREFIX', title: genericTitle },
    // 旧后端 / 未知 reason：通用文案兜底，不得空白或报错
    { body: genericBody, reason: null, title: genericTitle },
  ];

  it.each(cases)('renders title/body for reason $reason', (testCase) => {
    const view = resolveSessionInvalidView(
      { invalidAt: INVALID_AT, reason: testCase.reason },
      translate,
      NOW,
    );

    expect(view.title).toBe(testCase.title);
    expect(view.body).toBe(testCase.body);
  });

  it('shares the same key tree in zh-CN and en-US', () => {
    expect(collectKeys(enLocale).sort()).toEqual(collectKeys(zhLocale).sort());
  });
});

describe('session invalid device line', () => {
  const fullDevice: SessionInvalidDevice = {
    browser: 'Chrome 120',
    ip: '1.2.3.4',
    location: '上海市',
    loginTime: '2026-10-28 21:32:05',
    os: 'Windows 10',
  };

  it('renders the contract format and puts the line on its own row', () => {
    const view = resolveSessionInvalidView(
      { device: fullDevice, invalidAt: INVALID_AT, reason: 'BE_REPLACED' },
      translate,
      NOW,
    );

    expect(view.deviceLine).toBe(
      '登录设备：Chrome 120 · Windows 10｜地点：上海市｜IP：1.2.3.4｜时间：21:32',
    );
    expect(view.body).toBe(
      '当前账号于 21:32 在另一台设备登录，你已被迫下线。\n登录设备：Chrome 120 · Windows 10｜地点：上海市｜IP：1.2.3.4｜时间：21:32\n重新登录会使那台设备下线。',
    );
  });

  it('omits the ip fragment when the backend does not send one (old contract)', () => {
    const line = resolveSessionInvalidDeviceLine(
      {
        browser: 'Chrome 120',
        location: '上海市',
        loginTime: '2026-10-28 21:32:05',
        os: 'Windows 10',
      },
      translate,
      NOW,
    );
    expect(line).toBe(
      '登录设备：Chrome 120 · Windows 10｜地点：上海市｜时间：21:32',
    );
  });

  it.each([
    {
      desc: 'omits the browser fragment when browser is missing',
      device: { os: 'Windows 10', location: '上海市', loginTime: undefined },
      expected: '登录设备：Windows 10｜地点：上海市',
    },
    {
      desc: 'omits the os fragment when os is missing',
      device: { browser: 'Chrome 120', location: '上海市' },
      expected: '登录设备：Chrome 120｜地点：上海市',
    },
    {
      desc: 'omits the location fragment when location is missing',
      device: { browser: 'Chrome 120', ip: '192.168.8.105', os: 'Windows 10' },
      expected: '登录设备：Chrome 120 · Windows 10｜IP：192.168.8.105',
    },
    {
      desc: 'omits the ip fragment when ip is missing',
      device: { browser: 'Chrome 120', location: '上海市', os: 'Windows 10' },
      expected: '登录设备：Chrome 120 · Windows 10｜地点：上海市',
    },
    {
      desc: 'omits the time fragment when loginTime is invalid',
      device: { browser: 'Chrome 120', loginTime: 'not-a-date' },
      expected: '登录设备：Chrome 120',
    },
    {
      desc: 'shows MM-DD HH:mm for cross-day login time',
      device: { browser: 'Chrome 120', loginTime: '2026-10-27 21:32:05' },
      expected: '登录设备：Chrome 120｜时间：10-27 21:32',
    },
  ])('$desc', ({ device, expected }) => {
    const line = resolveSessionInvalidDeviceLine(
      device as SessionInvalidDevice,
      translate,
      NOW,
    );
    expect(line).toBe(expected);
  });

  it.each([
    ['missing device', null],
    ['empty device object', {}],
    ['blank fields only', { browser: '  ', os: '', location: null }],
  ])(
    'hides the whole row for %s without faking "unknown device"',
    (_, device) => {
      expect(
        resolveSessionInvalidDeviceLine(
          device as null | SessionInvalidDevice,
          translate,
          NOW,
        ),
      ).toBe('');

      const view = resolveSessionInvalidView(
        {
          device: device as null | SessionInvalidDevice,
          invalidAt: INVALID_AT,
          reason: 'BE_REPLACED',
        },
        translate,
        NOW,
      );
      // 设备行缺失时正文仍是完整契约文案，不出现空行/占位符
      expect(view.body).toBe(
        '当前账号于 21:32 在另一台设备登录，你已被迫下线。重新登录会使那台设备下线。',
      );
    },
  );

  it('falls back to detectedAt time when invalidAt/loginTime are missing', () => {
    const detectedAt = dayjs('2026-10-28 21:32:00').valueOf();
    const view = resolveSessionInvalidView(
      { detectedAt, reason: 'BE_REPLACED' },
      translate,
      NOW,
    );

    expect(view.body).toContain('当前账号于 21:32 在另一台设备登录');
  });
});

describe('session invalid time formatting', () => {
  it.each([
    ['2026-10-28 21:32:07', '21:32'],
    ['2026-10-27 21:32:07', '10-27 21:32'],
    [null, ''],
    [undefined, ''],
    ['not-a-date', ''],
  ])('formats %s as %s', (value, expected) => {
    expect(formatSessionInvalidTime(value as null | string, NOW)).toBe(
      expected,
    );
  });
});

describe('session invalid modal component contract', () => {
  const modalSource = readAppSource('layouts/session-invalid-modal.vue');
  const loginSource = readAppSource('views/_core/authentication/login.vue');

  it('is a blocking modal with no close path and no footer', () => {
    expect(modalSource).toContain(':closable="false"');
    expect(modalSource).toContain(':close-on-click-modal="false"');
    expect(modalSource).toContain(':close-on-press-escape="false"');
    expect(modalSource).toContain(':footer="false"');
  });

  it('reuses the existing LoginForm as the relogin action', () => {
    expect(modalSource).toContain('LoginForm');
    expect(modalSource).toContain(
      ':submit-text="$t(\'sessionInvalid.relogin\')"',
    );
    expect(loginSource).toContain('submitText');
  });

  it('offers exactly the two exits: relogin (form submit) and logout', () => {
    expect(modalSource).toContain("$t('sessionInvalid.exit')");
    expect(modalSource).toContain('handleLogout');
    expect(modalSource).toContain('authStore.logout()');
  });

  it('mounts below the captcha mask so the slider verification stays usable', () => {
    expect(modalSource).toContain('SESSION_INVALID_MODAL_Z_INDEX');
    expect(SESSION_INVALID_MODAL_Z_INDEX).toBeLessThan(1001);
  });
});
