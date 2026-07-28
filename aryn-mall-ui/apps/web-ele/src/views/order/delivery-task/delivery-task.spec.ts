import { describe, expect, it } from 'vitest';

import {
  buildAssignmentPayload,
  getOrderFulfillmentAction,
  isEvidenceAccessExpired,
} from '#/api/order/delivery-task';

describe('商城配送管理契约', () => {
  it('商城配送待发货订单进入派单而不是快递发货', () => {
    expect(getOrderFulfillmentAction({ deliveryWay: '3', status: '2' })).toBe(
      'ASSIGN',
    );
    expect(getOrderFulfillmentAction({ deliveryWay: '1', status: '2' })).toBe(
      'SHIP',
    );
    expect(getOrderFulfillmentAction({ deliveryWay: '2', status: '3' })).toBe(
      'PICKUP',
    );
  });

  it('派单和改派请求保留版本与改派原因', () => {
    expect(
      buildAssignmentPayload('REASSIGN', {
        assigneeId: 'staff-1',
        description: '原配送员临时无法配送',
        reasonCode: 'STAFF_BUSY',
        remark: '',
        requestId: 'request-1',
        version: 4,
      }),
    ).toEqual({
      assigneeId: 'staff-1',
      description: '原配送员临时无法配送',
      reasonCode: 'STAFF_BUSY',
      requestId: 'request-1',
      version: 4,
    });
  });

  it('短期凭证地址在过期前可用，过期后要求刷新', () => {
    expect(
      isEvidenceAccessExpired('2099-07-28T10:00:00', new Date('2026-07-28')),
    ).toBe(false);
    expect(
      isEvidenceAccessExpired('2026-07-27T10:00:00', new Date('2026-07-28')),
    ).toBe(true);
  });
});
