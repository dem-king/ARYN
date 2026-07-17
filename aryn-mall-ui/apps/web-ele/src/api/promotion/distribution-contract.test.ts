import type { DistributionConfigRecord } from './distribution-config';
import type { DistributionOrderRecord } from './distribution-order';
import type {
  DistributionWithdrawAuditPayload,
  DistributionWithdrawRecord,
} from './distribution-withdraw';

import { beforeEach, describe, expect, expectTypeOf, it, vi } from 'vitest';

import { requestClient } from '#/api/request';

import {
  formatMoney,
  formatSignedCommission,
  getCommissionFlowText,
  getWithdrawStatusText,
} from '../../../../../../aryn-mall-uniapp/src/sub-pages/user/distribution/presentation';
import { settleOrder } from './distribution-order';
import { audit } from './distribution-withdraw';

vi.mock('#/api/request', () => ({
  requestClient: {
    delete: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

describe('distribution transport and presentation contracts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('settles a pending commission order only by its immutable id', async () => {
    await settleOrder('distribution-order-1');

    expect(requestClient.post).toHaveBeenCalledWith(
      '/promotion/distribution/order/settle/distribution-order-1',
    );
  });

  it('submits payout reference when approving a withdraw', async () => {
    const payload: DistributionWithdrawAuditPayload = {
      id: 'withdraw-1',
      payoutNo: 'BANK-20260717-001',
      status: '1',
    };

    await audit(payload);

    expect(requestClient.post).toHaveBeenCalledWith(
      '/promotion/distribution/withdraw/audit',
      payload,
    );
  });

  it('keeps full financial fields in typed admin records', () => {
    expectTypeOf<DistributionConfigRecord>().toHaveProperty(
      'commissionRateLevel2',
    );
    expectTypeOf<DistributionConfigRecord>().toHaveProperty('settleCycleDays');
    expectTypeOf<DistributionOrderRecord>().toHaveProperty(
      'commissionBaseAmount',
    );
    expectTypeOf<DistributionOrderRecord>().toHaveProperty(
      'refundedCommissionAmount',
    );
    expectTypeOf<DistributionWithdrawRecord>().toHaveProperty('payoutNo');
    expectTypeOf<DistributionWithdrawRecord>().toHaveProperty('payoutTime');
  });

  it('renders money, flow direction and withdraw status without mutating data', () => {
    expect(formatMoney(-1.2)).toBe('-1.20');
    expect(formatSignedCommission(12.3, 'INCOME')).toBe('+12.30');
    expect(formatSignedCommission(4.5, 'EXPENSE')).toBe('-4.50');
    expect(getCommissionFlowText('EXPENSE')).toBe('支出');
    expect(getWithdrawStatusText('2')).toBe('已驳回');
  });
});
