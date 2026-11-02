import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it, vi } from 'vitest';

import {
  createLoginDevicesController,
  formatLoginDeviceLabel,
  formatLoginTime,
  hasKickableDevice,
  hasOtherLoginDevices,
  isLoginLogSuccess,
  LOGIN_LOG_LIMIT,
  normalizeLoginDevices,
  normalizeLoginLogs,
} from './login-devices';

const appRoot = resolve(process.cwd(), 'apps/web-ele');
const readAppSource = (path: string) =>
  readFileSync(resolve(appRoot, 'src', path), 'utf8');

describe('login devices view helpers', () => {
  describe('formatLoginDeviceLabel', () => {
    it.each([
      [{ browser: 'Chrome 120', os: 'Windows 10' }, 'Chrome 120 · Windows 10'],
      [{ browser: 'Chrome 120' }, 'Chrome 120'],
      [{ os: 'Windows 10' }, 'Windows 10'],
      [{ browser: '  ', os: 'Windows 10' }, 'Windows 10'],
      [{}, ''],
      [null, ''],
      [undefined, ''],
    ])('formats %s as %s', (device, expected) => {
      expect(formatLoginDeviceLabel(device as never)).toBe(expected);
    });
  });

  describe('formatLoginTime', () => {
    it.each([
      ['2026-10-29 09:10:00', '2026-10-29 09:10'],
      ['2026-10-28 23:10:00', '2026-10-28 23:10'],
      ['2026-10-29T09:10:00+08:00', '2026-10-29 09:10'],
      [null, ''],
      ['not-a-date', ''],
    ])('formats %s as %s', (value, expected) => {
      expect(formatLoginTime(value as null | string)).toBe(expected);
    });
  });

  describe('normalizeLoginDevices', () => {
    it('keeps server order and current flag', () => {
      expect(
        normalizeLoginDevices([
          { current: true, id: 'hash-1', os: 'Windows 10' },
          { id: 'hash-2', os: 'macOS' },
        ]),
      ).toEqual([
        { current: true, id: 'hash-1', os: 'Windows 10' },
        { current: false, id: 'hash-2', os: 'macOS' },
      ]);
    });

    it('drops non-object rows and defaults current to false', () => {
      const devices = normalizeLoginDevices([
        null,
        'bad',
        42,
        { id: 'hash-1', current: 'yes' },
      ]);
      expect(devices).toHaveLength(1);
      expect(devices[0]?.current).toBe(false);
      expect(devices[0]?.id).toBe('hash-1');
    });

    it('keeps rows without id but marks them non-kickable', () => {
      const devices = normalizeLoginDevices([{ os: 'Windows 10' }]);
      expect(devices[0]?.id).toBe('');
      expect(hasKickableDevice(devices)).toBe(false);
    });

    it.each([null, undefined, {}, 'devices'])(
      'degrades non-array payload %s to []',
      (value) => {
        expect(normalizeLoginDevices(value)).toEqual([]);
      },
    );
  });

  describe('hasOtherLoginDevices / hasKickableDevice', () => {
    it('only the current device means single-session state', () => {
      const devices = [{ current: true, id: 'cur' }];
      expect(hasOtherLoginDevices(devices)).toBe(false);
      expect(hasKickableDevice(devices)).toBe(false);
    });

    it('detects other and kickable sessions', () => {
      const devices = [
        { current: true, id: 'cur' },
        { current: false, id: 'other' },
      ];
      expect(hasOtherLoginDevices(devices)).toBe(true);
      expect(hasKickableDevice(devices)).toBe(true);
    });

    it('a non-current row without id is not kickable', () => {
      expect(hasOtherLoginDevices([{ current: false, id: '' }])).toBe(true);
      expect(hasKickableDevice([{ current: false, id: '' }])).toBe(false);
    });
  });

  describe('login log helpers', () => {
    it.each([
      ['1', true],
      [1, true],
      ['0', false],
      [0, false],
      [undefined, false],
      [null, false],
    ])('isLoginLogSuccess(%s) → %s', (status, expected) => {
      expect(isLoginLogSuccess(status as never)).toBe(expected);
    });

    it('keeps only the latest 10 rows', () => {
      const logs = normalizeLoginLogs(
        Array.from({ length: 15 }, (_, index) => ({
          createTime: `2026-10-29 09:${String(index).padStart(2, '0')}:00`,
          status: '1',
        })),
      );
      expect(logs).toHaveLength(LOGIN_LOG_LIMIT);
    });

    it('normalizes fields and never renders ipAddr in the component', () => {
      const logs = normalizeLoginLogs([
        {
          browser: 'Chrome 120',
          createTime: '2026-10-29 09:10:00',
          ipAddr: '1.2.3.4',
          location: '上海市',
          os: 'Windows 10',
          status: 1,
        },
      ]);
      expect(logs[0]).toMatchObject({
        browser: 'Chrome 120',
        location: '上海市',
        status: 1,
      });
      expect(readAppSource('layouts/login-devices-modal.vue')).not.toContain(
        'ipAddr',
      );
    });

    it.each([null, undefined, {}, 'logs'])(
      'degrades non-array payload %s to []',
      (value) => {
        expect(normalizeLoginLogs(value)).toEqual([]);
      },
    );
  });
});

function createDeps(overrides: Record<string, unknown> = {}) {
  return {
    confirmKick: vi.fn().mockResolvedValue(true),
    getDevices: vi.fn().mockResolvedValue([
      { current: true, id: 'cur', os: 'Windows 10' },
      { id: 'other', os: 'macOS' },
    ]),
    getLogs: vi.fn().mockResolvedValue({
      records: [{ createTime: '2026-10-29 09:10:00', status: '1' }],
    }),
    notifyKickError: vi.fn(),
    notifyKickSuccess: vi.fn(),
    removeDevice: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

describe('login devices controller', () => {
  it('loads sessions and login logs independently', async () => {
    const deps = createDeps();
    const controller = createLoginDevicesController(deps);

    await controller.loadDevices();
    await controller.loadLogs();

    expect(controller.devices.value).toHaveLength(2);
    expect(controller.devices.value[0]?.current).toBe(true);
    expect(controller.logs.value).toHaveLength(1);
    expect(deps.getLogs).toHaveBeenCalledWith({
      current: 1,
      size: LOGIN_LOG_LIMIT,
    });
    expect(controller.devicesLoading.value).toBe(false);
    expect(controller.logsLoading.value).toBe(false);
    expect(controller.showSinglePointNotice.value).toBe(false);
  });

  it('shows the single-session notice when only the current device exists', async () => {
    const deps = createDeps({
      getDevices: vi.fn().mockResolvedValue([{ current: true, id: 'cur' }]),
    });
    const controller = createLoginDevicesController(deps);

    await controller.loadDevices();
    expect(controller.showSinglePointNotice.value).toBe(true);

    // 加载中/出错时不展示说明（避免误导）
    deps.getDevices.mockRejectedValueOnce(new Error('boom'));
    const failed = controller.loadDevices();
    expect(controller.showSinglePointNotice.value).toBe(false);
    await failed;
    expect(controller.showSinglePointNotice.value).toBe(false);
  });

  it('keeps the other section usable when one section fails', async () => {
    const deps = createDeps({
      getDevices: vi.fn().mockRejectedValue(new Error('devices down')),
    });
    const controller = createLoginDevicesController(deps);

    await controller.loadDevices();
    await controller.loadLogs();

    expect(controller.devicesError.value).toBe(true);
    expect(controller.devices.value).toEqual([]);
    expect(controller.logsError.value).toBe(false);
    expect(controller.logs.value).toHaveLength(1);
  });

  it('marks the login log section as failed independently', async () => {
    const deps = createDeps({
      getLogs: vi.fn().mockRejectedValue(new Error('logs down')),
    });
    const controller = createLoginDevicesController(deps);

    await controller.loadDevices();
    await controller.loadLogs();

    expect(controller.devicesError.value).toBe(false);
    expect(controller.logsError.value).toBe(true);
    expect(controller.logs.value).toEqual([]);
  });

  it('never kicks the current device (no confirm, no request)', async () => {
    const deps = createDeps();
    const controller = createLoginDevicesController(deps);

    await controller.kickDevice({ current: true, id: 'cur' });
    await controller.kickDevice({ id: '' });

    expect(deps.confirmKick).not.toHaveBeenCalled();
    expect(deps.removeDevice).not.toHaveBeenCalled();
  });

  it('does nothing when the user cancels the confirmation', async () => {
    const deps = createDeps({
      confirmKick: vi.fn().mockResolvedValue(false),
    });
    const controller = createLoginDevicesController(deps);

    await controller.kickDevice({ current: false, id: 'other' });

    expect(deps.removeDevice).not.toHaveBeenCalled();
    expect(deps.notifyKickSuccess).not.toHaveBeenCalled();
    expect(controller.kickingId.value).toBe('');
  });

  it('kicks a non-current session then refreshes the list', async () => {
    const deps = createDeps();
    const controller = createLoginDevicesController(deps);
    await controller.loadDevices();

    await controller.kickDevice({ current: false, id: 'other' });

    expect(deps.confirmKick).toHaveBeenCalledTimes(1);
    expect(deps.removeDevice).toHaveBeenCalledWith('other');
    expect(deps.notifyKickSuccess).toHaveBeenCalledTimes(1);
    // 成功后再拉一次列表，保证与在线记录一致
    expect(deps.getDevices).toHaveBeenCalledTimes(2);
    expect(controller.kickingId.value).toBe('');
  });

  it('surfaces the backend rejection message without throwing', async () => {
    const deps = createDeps({
      removeDevice: vi
        .fn()
        .mockRejectedValue({ msg: '当前设备请使用退出登录' }),
    });
    const controller = createLoginDevicesController(deps);

    await expect(
      controller.kickDevice({ current: false, id: 'other' }),
    ).resolves.toBeUndefined();

    expect(deps.notifyKickError).toHaveBeenCalledWith('当前设备请使用退出登录');
    expect(controller.kickingId.value).toBe('');
  });

  it('loadAll triggers both loads', async () => {
    const deps = createDeps();
    const controller = createLoginDevicesController(deps);

    controller.loadAll();

    await vi.waitFor(() => {
      expect(deps.getDevices).toHaveBeenCalledTimes(1);
      expect(deps.getLogs).toHaveBeenCalledTimes(1);
    });
  });
});

describe('login devices wiring contracts', () => {
  const modalSource = readAppSource('layouts/login-devices-modal.vue');
  const basicSource = readAppSource('layouts/basic.vue');
  const zhPage = JSON.parse(
    readFileSync(
      resolve(process.cwd(), 'apps/web-ele/src/locales/langs/zh-CN/page.json'),
      'utf8',
    ),
  ) as Record<string, any>;
  const enPage = JSON.parse(
    readFileSync(
      resolve(process.cwd(), 'apps/web-ele/src/locales/langs/en-US/page.json'),
      'utf8',
    ),
  ) as Record<string, any>;

  it('opens the device modal from the user dropdown menu', () => {
    expect(basicSource).toContain(':menus="userMenus"');
    expect(basicSource).toContain(
      'connectedComponent: LoginDevicesModalContent',
    );
    expect(basicSource).toContain('loginDevicesModalApi.open()');
    expect(basicSource).toContain('<LoginDevicesModal />');
    expect(basicSource).toContain("$t('page.loginDevices.menu')");
  });

  it('uses the unified API module and confirms before kicking', () => {
    expect(modalSource).toContain("from '#/api/core/session-devices'");
    expect(modalSource).toContain('getMyDevices');
    expect(modalSource).toContain('getMyLoginLogs');
    expect(modalSource).toContain('removeDevice');
    expect(modalSource).toContain('ElMessageBox.confirm');
    expect(modalSource).toContain("'page.loginDevices.kickConfirm'");
  });

  it('never renders a kick action for the current device', () => {
    expect(modalSource).toContain('v-if="!row.current && row.id"');
    expect(modalSource).toContain("'page.loginDevices.current'");
  });

  it('has loading, empty, error and retry states for both sections', () => {
    expect(modalSource).toContain('v-loading="devicesLoading"');
    expect(modalSource).toContain('v-loading="logsLoading"');
    expect(modalSource).toContain("'page.loginDevices.noDevices'");
    expect(modalSource).toContain("'page.loginDevices.noLogs'");
    expect(modalSource).toContain("'page.loginDevices.loadFailed'");
    expect(modalSource).toContain("'page.loginDevices.retry'");
  });

  it('explains the single-session policy when no other session exists', () => {
    expect(modalSource).toContain('showSinglePointNotice');
    expect(modalSource).toContain("'page.loginDevices.singlePointNotice'");
  });

  it('keeps the contract copy in both locales', () => {
    expect(zhPage.loginDevices?.singlePointNotice).toBe(
      '同一账号仅允许一处登录，其他设备登录会自动顶掉本设备',
    );
    expect(zhPage.loginDevices?.menu).toBe('登录设备');
    expect(zhPage.loginDevices?.kick).toBe('下线');
    expect(zhPage.loginDevices?.current).toBe('当前设备');
    expect(enPage.loginDevices?.singlePointNotice).toContain(
      'Only one session is allowed per account',
    );
    expect(Object.keys(enPage.loginDevices).sort()).toEqual(
      Object.keys(zhPage.loginDevices).sort(),
    );
  });

  it('does not assemble or expose tokens in the devices UI', () => {
    for (const source of [modalSource, basicSource]) {
      expect(source).not.toContain('accessToken');
      expect(source).not.toContain('tokenValue');
    }
  });
});
