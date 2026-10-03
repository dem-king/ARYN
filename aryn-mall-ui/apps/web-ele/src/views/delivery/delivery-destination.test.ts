import { describe, expect, it } from 'vitest';

import { resolveDeliveryDestination } from './delivery-destination';

describe('resolveDeliveryDestination', () => {
  it('prefers the street address when present', () => {
    expect(
      resolveDeliveryDestination({
        recipientAddress: '上海市浦东新区峨山路 91 弄',
        portName: '上海港',
        berth: '321',
      }),
    ).toBe('上海市浦东新区峨山路 91 弄');
  });

  it('falls back to port and berth for internal delivery orders without a street address', () => {
    expect(
      resolveDeliveryDestination({
        recipientAddress: null,
        portName: '福建港',
        berth: '321',
      }),
    ).toBe('福建港 321');
  });

  it('renders the port alone when the berth is missing', () => {
    expect(
      resolveDeliveryDestination({ recipientAddress: '', portName: '福建港' }),
    ).toBe('福建港');
  });

  it('does not render a berth without a port name', () => {
    expect(
      resolveDeliveryDestination({
        recipientAddress: null,
        portName: null,
        berth: '321',
      }),
    ).toBe('');
  });

  it('returns an empty string for blank input so callers can show a placeholder', () => {
    expect(resolveDeliveryDestination({ recipientAddress: '   ' })).toBe('');
    expect(resolveDeliveryDestination({})).toBe('');
    expect(resolveDeliveryDestination(null)).toBe('');
    expect(resolveDeliveryDestination(undefined)).toBe('');
  });
});
