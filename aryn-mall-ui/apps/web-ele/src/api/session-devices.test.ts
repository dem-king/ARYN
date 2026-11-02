import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { requestClient } from '#/api/request';

import { rewriteBootUrl } from './boot-url';
import {
  getMyDevices,
  getMyLoginLogs,
  removeDevice,
} from './core/session-devices';

vi.mock('#/api/request', () => ({
  requestClient: {
    delete: vi.fn(),
    get: vi.fn(),
  },
}));

const appRoot = resolve(process.cwd(), 'apps/web-ele');
const apiSource = readFileSync(
  resolve(appRoot, 'src/api/core/session-devices.ts'),
  'utf8',
);
const indexSource = readFileSync(
  resolve(appRoot, 'src/api/core/index.ts'),
  'utf8',
);

describe('session devices api contract (三期契约 §2)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(requestClient.get).mockResolvedValue(undefined);
    vi.mocked(requestClient.delete).mockResolvedValue(undefined);
  });

  it('gET /auth/session/devices for the active sessions', async () => {
    await getMyDevices();

    expect(requestClient.get).toHaveBeenCalledWith('/auth/session/devices');
  });

  it('dELETE /auth/session/devices/{id} uses the server-issued hash id as-is', async () => {
    await removeDevice('a1b2c3d4e5f6');

    expect(requestClient.delete).toHaveBeenCalledWith(
      '/auth/session/devices/a1b2c3d4e5f6',
    );
  });

  it('encodes the path segment instead of building paths from tokens', async () => {
    await removeDevice('a/b c');

    expect(requestClient.delete).toHaveBeenCalledWith(
      '/auth/session/devices/a%2Fb%20c',
    );
    // 前端不允许把 token 拼进 URL
    expect(apiSource).not.toContain('accessToken');
    expect(apiSource).not.toContain('tokenValue');
  });

  it('gET /upms/sysloginlog/my with current/size (PageArgumentResolver 约定)', async () => {
    await getMyLoginLogs({ current: 1, size: 10 });

    expect(requestClient.get).toHaveBeenCalledWith('/upms/sysloginlog/my', {
      params: { current: 1, size: 10 },
    });
  });

  it('returns the request client payload unchanged', async () => {
    const devices = [{ current: true, id: 'abc', os: 'Windows 10' }];
    vi.mocked(requestClient.get).mockResolvedValue(devices);

    await expect(getMyDevices()).resolves.toEqual(devices);
  });

  it('uses the unified authenticated request client only', () => {
    expect(apiSource).toContain('requestClient');
    expect(apiSource).not.toContain('baseRequestClient');
    expect(apiSource).not.toContain("from 'axios'");
  });

  it('never hardcodes the boot prefix or bypasses rewriteBootUrl', () => {
    expect(apiSource).not.toContain("'/boot");
    expect(apiSource).not.toContain('import.meta.env');
    expect(apiSource).not.toContain('parseOpenBoot(');
  });

  it('is re-exported from the api/core barrel', () => {
    expect(indexSource).toContain("export * from './session-devices';");
  });
});

describe('session devices api boot/cloud rewrite', () => {
  it.each([
    ['/auth/session/devices', '/boot/session/devices'],
    [
      '/auth/session/devices/a1b2c3d4e5f6',
      '/boot/session/devices/a1b2c3d4e5f6',
    ],
    [
      '/upms/sysloginlog/my?pageNum=1&pageSize=10',
      '/boot/sysloginlog/my?pageNum=1&pageSize=10',
    ],
  ])('boot mode rewrites %s to %s', (url, expected) => {
    expect(rewriteBootUrl(url, true)).toBe(expected);
  });

  it.each([
    '/auth/session/devices',
    '/auth/session/devices/a1b2c3d4e5f6',
    '/upms/sysloginlog/my?pageNum=1&pageSize=10',
  ])('cloud mode keeps %s unchanged', (url) => {
    expect(rewriteBootUrl(url, false)).toBe(url);
  });
});
