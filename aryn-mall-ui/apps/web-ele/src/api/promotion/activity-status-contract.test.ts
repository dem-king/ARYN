import { beforeEach, describe, expect, it, vi } from 'vitest';

import { requestClient } from '#/api/request';

import { updateStatus as updateDiscountStatus } from './discount';
import { updateStatus as updateSeckillStatus } from './seckill';

vi.mock('#/api/request', () => ({
  requestClient: {
    delete: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

/**
 * 秒杀/折扣活动的启停接口，后端 Controller 用 `@RequestParam("status")` 接收状态。
 *
 * 2026-10-23 修复前前端把 status 放在请求体，Spring 视为缺失参数并返回
 * `Required request parameter 'status' for method parameter type Integer is not present`，
 * 表现为列表页点「暂停 / 启用」直接报错。契约：status 必须出现在 query 上。
 */
describe('promotion activity status transport', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('sends the seckill status as a query parameter so pause works', async () => {
    await updateSeckillStatus('seckill-1', 3);

    expect(requestClient.put).toHaveBeenCalledWith(
      '/promotion/seckill/activity/seckill-1/status?status=3',
    );
  });

  it('sends the seckill status as a query parameter when resuming', async () => {
    await updateSeckillStatus('seckill-1', 0);

    expect(requestClient.put).toHaveBeenCalledWith(
      '/promotion/seckill/activity/seckill-1/status?status=0',
    );
  });

  it('sends the discount status as a query parameter so pause works', async () => {
    await updateDiscountStatus('discount-1', 3);

    expect(requestClient.put).toHaveBeenCalledWith(
      '/promotion/discount/activity/discount-1/status?status=3',
    );
  });
});
