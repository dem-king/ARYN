import type { DeliveryGroup } from '#/api/order/delivery-group';

import { describe, expect, it } from 'vitest';

import { canJoin, commandRequest } from './group-state';

const group: DeliveryGroup = {
  id: 'g',
  maxOrders: 10,
  portCode: 'p',
  revision: 1,
  status: 'OPEN',
  vesselCallId: 'call',
  vesselId: 'v',
  warehouseId: 'w',
  windowSequence: 1,
  windowStart: '2030-01-01 12:00:00',
  windowEnd: '2030-01-01 14:00:00',
  cutoff: '2030-01-01 10:00:00',
};
describe('合送组命令', () => {
  it('截单与待排程均阻止普通归组，特批不能越过执行态和窗口结束', () => {
    const now = Date.parse('2030-01-01T11:00:00');
    expect(canJoin(group, false, now)).toBe(false);
    expect(canJoin(group, true, now)).toBe(true);
    expect(canJoin({ ...group, cutoff: undefined }, true, now)).toBe(false);
    expect(canJoin({ ...group, status: 'FULFILLING' }, true, now)).toBe(false);
    expect(canJoin(group, true, Date.parse('2030-01-01T15:00:00'))).toBe(false);
  });
  it('失败重试保持请求号，改动修订或订单必须换号', () => {
    const first = commandRequest(
      undefined,
      { expectedRevision: 1, orderIds: ['a'] },
      () => 'request-a',
    );
    expect(
      commandRequest(
        first,
        { expectedRevision: 1, orderIds: ['a'] },
        () => 'unused',
      ),
    ).toBe(first);
    expect(
      commandRequest(
        first,
        { expectedRevision: 2, orderIds: ['a'] },
        () => 'request-b',
      ).id,
    ).toBe('request-b');
    expect(
      commandRequest(
        first,
        { expectedRevision: 1, orderIds: ['b'] },
        () => 'request-c',
      ).id,
    ).toBe('request-c');
  });
});
