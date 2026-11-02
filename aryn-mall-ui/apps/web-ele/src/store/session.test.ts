import { useAccessStore } from '@vben/stores';

import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  normalizeSessionInvalidDevice,
  normalizeSessionInvalidReason,
  normalizeSessionInvalidSubReason,
  useSessionStore,
} from './session';

describe('session invalid store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('starts in the valid state', () => {
    const store = useSessionStore();
    expect(store.invalid).toBe(false);
    expect(store.isInvalid).toBe(false);
    expect(store.reason).toBeNull();
    expect(store.subReason).toBeNull();
    expect(store.invalidAt).toBeNull();
    expect(store.device).toBeNull();
    expect(store.detectedAt).toBeNull();
  });

  it('records reason/invalidAt/device when invalidated', () => {
    const store = useSessionStore();
    const migrated = store.invalidate(
      {
        device: {
          browser: 'Chrome 120',
          location: '上海市',
          loginTime: '2026-10-28T21:32:05+08:00',
          os: 'Windows 10',
        },
        invalidAt: '2026-10-28T21:32:07+08:00',
        reason: 'BE_REPLACED',
      },
      1_700_000_000_000,
    );

    expect(migrated).toBe(true);
    expect(store.invalid).toBe(true);
    expect(store.isInvalid).toBe(true);
    expect(store.reason).toBe('BE_REPLACED');
    expect(store.invalidAt).toBe('2026-10-28T21:32:07+08:00');
    expect(store.device).toEqual({
      browser: 'Chrome 120',
      location: '上海市',
      loginTime: '2026-10-28T21:32:05+08:00',
      os: 'Windows 10',
    });
    expect(store.detectedAt).toBe(1_700_000_000_000);
  });

  it('is idempotent: concurrent 401 only keeps the first record', () => {
    const store = useSessionStore();
    store.invalidate(
      { invalidAt: '2026-10-28T21:32:07+08:00', reason: 'BE_REPLACED' },
      1000,
    );

    const second = store.invalidate({ reason: 'TOKEN_EXPIRED' }, 2000);

    expect(second).toBe(false);
    expect(store.reason).toBe('BE_REPLACED');
    expect(store.invalidAt).toBe('2026-10-28T21:32:07+08:00');
    expect(store.detectedAt).toBe(1000);
  });

  it('accepts a payload without reason (legacy backend / business 401)', () => {
    const store = useSessionStore();
    store.invalidate({});

    expect(store.invalid).toBe(true);
    expect(store.reason).toBeNull();
    expect(store.subReason).toBeNull();
    expect(store.device).toBeNull();
  });

  it('reset() restores the valid state and is idempotent', () => {
    const store = useSessionStore();
    store.invalidate({ reason: 'KICK_OUT' });

    store.reset();
    store.reset();

    expect(store.invalid).toBe(false);
    expect(store.reason).toBeNull();
    expect(store.subReason).toBeNull();
    expect(store.invalidAt).toBeNull();
    expect(store.device).toBeNull();
    expect(store.detectedAt).toBeNull();
  });

  it('re-invalidates after reset (relogin then new session expires)', () => {
    const store = useSessionStore();
    store.invalidate({ reason: 'BE_REPLACED' });
    store.reset();
    store.invalidate({ reason: 'TOKEN_EXPIRED' }, 10);

    expect(store.invalid).toBe(true);
    expect(store.reason).toBe('TOKEN_EXPIRED');
    expect(store.detectedAt).toBe(10);
  });

  it('exposes $reset for resetAllStores() during logout', () => {
    const store = useSessionStore();
    store.invalidate({ reason: 'KICK_OUT' });

    expect(() => store.$reset()).not.toThrow();
    expect(store.invalid).toBe(false);
  });

  describe('normalizeSessionInvalidReason', () => {
    it.each([
      'BE_REPLACED',
      'KICK_OUT',
      'NO_PREFIX',
      'NOT_TOKEN',
      'TOKEN_EXPIRED',
      'TOKEN_FREEZE',
      'TOKEN_INVALID',
    ])('keeps known wire value %s', (reason) => {
      expect(normalizeSessionInvalidReason(reason)).toBe(reason);
    });

    it.each([null, undefined, '', 'be_replaced', 'SUB_REASON', 401, {}])(
      'degrades unknown value %s to null',
      (value) => {
        expect(normalizeSessionInvalidReason(value)).toBeNull();
      },
    );
  });

  describe('subReason (二期)', () => {
    it('keeps known subReason only for KICK_OUT', () => {
      const store = useSessionStore();
      store.invalidate({
        reason: 'KICK_OUT',
        subReason: 'ACCOUNT_DISABLED',
      });

      expect(store.reason).toBe('KICK_OUT');
      expect(store.subReason).toBe('ACCOUNT_DISABLED');
    });

    it('drops subReason when reason is not KICK_OUT', () => {
      const store = useSessionStore();
      store.invalidate({
        reason: 'BE_REPLACED',
        subReason: 'ADMIN_KICK',
      });

      expect(store.reason).toBe('BE_REPLACED');
      expect(store.subReason).toBeNull();
    });

    it('drops unknown subReason values (graceful degrade)', () => {
      const store = useSessionStore();
      store.invalidate({
        reason: 'KICK_OUT',
        subReason: 'SOMETHING_NEW' as never,
      });

      expect(store.reason).toBe('KICK_OUT');
      expect(store.subReason).toBeNull();
    });

    it('keeps detectedAt from a multi-tab broadcast payload', () => {
      const store = useSessionStore();
      store.invalidate({ detectedAt: 12_345, reason: 'KICK_OUT' }, 999_999);

      expect(store.detectedAt).toBe(12_345);
    });

    it('ignores malformed detectedAt and falls back to now', () => {
      const store = useSessionStore();
      store.invalidate(
        { detectedAt: 'not-a-number' as never, reason: 'KICK_OUT' },
        777,
      );

      expect(store.detectedAt).toBe(777);
    });
  });

  describe('lock screen boundary (二期契约 §3.3)', () => {
    it('unlocks the lock screen before showing the session invalid dialog', () => {
      const accessStore = useAccessStore();
      accessStore.lockScreen('secret');
      expect(accessStore.isLockScreen).toBe(true);

      useSessionStore().invalidate({ reason: 'BE_REPLACED' });

      expect(accessStore.isLockScreen).toBe(false);
      expect(accessStore.lockScreenPassword).toBeUndefined();
    });

    it('does not touch the lock screen when the session is still valid', () => {
      const accessStore = useAccessStore();
      accessStore.lockScreen('secret');

      // 未失效时不调用 invalidate，正常锁屏必须保持不变
      expect(useSessionStore().invalid).toBe(false);
      expect(accessStore.isLockScreen).toBe(true);
    });

    it('keeps the lock screen when invalidation is a no-op (already invalid)', () => {
      const store = useSessionStore();
      store.invalidate({ reason: 'KICK_OUT' });

      const accessStore = useAccessStore();
      accessStore.lockScreen('secret');
      store.invalidate({ reason: 'KICK_OUT' });

      // 幂等失效不重复触发副作用，正常锁屏/解锁流程不受影响
      expect(accessStore.isLockScreen).toBe(true);
    });
  });

  describe('normalizeSessionInvalidSubReason', () => {
    it.each([
      'ADMIN_KICK',
      'ACCOUNT_DISABLED',
      'PASSWORD_CHANGED',
      'ACCOUNT_DELETED',
      'TENANT_DISABLED',
      'DELIVERY_REVOKED',
      'MEMBER_DISABLED',
    ])('keeps known wire value %s', (subReason) => {
      expect(normalizeSessionInvalidSubReason(subReason)).toBe(subReason);
    });

    it.each([null, undefined, '', 'admin_kick', 'UNKNOWN', 1, {}])(
      'degrades unknown value %s to null',
      (value) => {
        expect(normalizeSessionInvalidSubReason(value)).toBeNull();
      },
    );
  });

  describe('normalizeSessionInvalidDevice', () => {
    it('trims strings and drops empty fields', () => {
      expect(
        normalizeSessionInvalidDevice({
          browser: ' Chrome 120 ',
          location: '  ',
          loginTime: '',
          os: 'Windows 10',
        }),
      ).toEqual({ browser: 'Chrome 120', os: 'Windows 10' });
    });

    it('keeps the ip field (2026-11-01 contract increment)', () => {
      expect(
        normalizeSessionInvalidDevice({
          browser: 'Chrome 120',
          ip: ' 192.168.8.105 ',
          os: 'macOS',
        }),
      ).toEqual({ browser: 'Chrome 120', ip: '192.168.8.105', os: 'macOS' });
    });

    it('treats the Unknown UA fallback as missing', () => {
      expect(
        normalizeSessionInvalidDevice({
          browser: 'Unknown',
          ip: '192.168.8.105',
          location: '内网IP',
          os: 'Unknown',
        }),
      ).toEqual({ ip: '192.168.8.105', location: '内网IP' });
    });

    it.each([null, undefined, 'device', 42, {}, { browser: '  ' }])(
      'drops device without any usable field: %s',
      (value) => {
        expect(normalizeSessionInvalidDevice(value)).toBeNull();
      },
    );
  });
});
