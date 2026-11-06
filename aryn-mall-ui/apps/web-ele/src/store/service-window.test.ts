import { describe, expect, it } from 'vitest';

import {
  buildServiceDeskUrl,
  DESK_ALIVE_TIMEOUT_MS,
  isServiceDeskLeaseAlive,
  parseServiceDeskMessage,
  SERVICE_DESK_WINDOW_NAME,
} from './service-window';

describe('service desk url', () => {
  it('builds a hash url under the app base', () => {
    expect(
      buildServiceDeskUrl({ base: '/', origin: 'https://admin.example.com' }),
    ).toBe('https://admin.example.com/#/message/service-window');
  });

  it('appends base path and encodes conversation query', () => {
    expect(
      buildServiceDeskUrl({
        base: '/admin/',
        conversationId: 'conv 1',
        origin: 'https://admin.example.com',
      }),
    ).toBe(
      'https://admin.example.com/admin/#/message/service-window?conversationId=conv%201',
    );
  });

  it('keeps a stable window name for re-focus after main window reload', () => {
    expect(SERVICE_DESK_WINDOW_NAME).toBe('aryn-service-desk');
  });
});

describe('parse service desk message', () => {
  const self = 'tab-self';

  it('rejects invalid payloads', () => {
    expect(parseServiceDeskMessage(null, self)).toBeNull();
    expect(parseServiceDeskMessage('not-json', self)).toBeNull();
    expect(parseServiceDeskMessage({}, self)).toBeNull();
    expect(
      parseServiceDeskMessage({ from: self, type: 'hello' }, self),
    ).toBeNull();
  });

  it('normalizes desk messages', () => {
    expect(
      parseServiceDeskMessage(
        {
          at: 123,
          conversationId: 'conv-1',
          from: 'tab-desk',
          kind: 'claimed',
          lastReadSeq: 9,
          locked: false,
          role: 'desk',
          type: 'changed',
        },
        self,
      ),
    ).toEqual({
      at: 123,
      conversationId: 'conv-1',
      from: 'tab-desk',
      kind: 'claimed',
      lastReadSeq: 9,
      locked: false,
      role: 'desk',
      tenantId: undefined,
      type: 'changed',
    });
  });

  it('treats unknown roles as main and parses JSON strings', () => {
    const message = parseServiceDeskMessage(
      JSON.stringify({ at: 1, from: 'tab-a', role: 'weird', type: 'lock' }),
      self,
    );
    expect(message?.role).toBe('main');
  });
});

describe('desk lease liveness', () => {
  const lease = (at: number) =>
    JSON.stringify({ at, conversationId: 'c1', role: 'desk', tabId: 't' });

  it('accepts a fresh desk lease', () => {
    expect(isServiceDeskLeaseAlive(lease(1000), 1500)).toBe(true);
  });

  it('expires after the timeout (main window shows alerts again)', () => {
    expect(
      isServiceDeskLeaseAlive(lease(1000), 1000 + DESK_ALIVE_TIMEOUT_MS + 1),
    ).toBe(false);
  });

  it('ignores non-desk / broken leases', () => {
    expect(isServiceDeskLeaseAlive(null, 1000)).toBe(false);
    expect(isServiceDeskLeaseAlive('not-json', 1000)).toBe(false);
    expect(
      isServiceDeskLeaseAlive(JSON.stringify({ at: 1000, role: 'main' }), 1000),
    ).toBe(false);
    expect(
      isServiceDeskLeaseAlive(JSON.stringify({ at: 0, role: 'desk' }), 1000),
    ).toBe(false);
  });
});
