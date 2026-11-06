import type {
  ConversationContext,
  MemberContext,
  OrderContextItem,
} from './context';

import { beforeEach, describe, expect, expectTypeOf, it, vi } from 'vitest';

import { rewriteBootUrl } from '#/api/boot-url';
import { requestClient } from '#/api/request';

import { getConversationContext } from './context';

/**
 * 会员/订单上下文 API 契约（P2-A 会员订单上下文契约 §8）。
 *
 * 这里只锁前端职责：请求路径以微服务域 `message` 开头（双模式改写由
 * requestClient 内部 parseOpenBoot/rewriteBootUrl 负责，禁止硬编码 `/boot`）、
 * 会话 ID 编码，以及响应字段的 TS 形状。
 */
vi.mock('#/api/request', () => ({
  requestClient: {
    get: vi.fn(),
  },
}));

describe('getConversationContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('请求 /message/staff/conversation/{id}/context（cloud 原样路径）', async () => {
    await getConversationContext('conversation-1');

    expect(requestClient.get).toHaveBeenCalledWith(
      '/message/staff/conversation/conversation-1/context',
    );
  });

  it('路径以微服务域 /message 开头且不硬编码 /boot', async () => {
    await getConversationContext('conversation-1');

    const path = vi.mocked(requestClient.get).mock.calls[0]?.[0] as string;
    expect(path.startsWith('/message/')).toBe(true);
    expect(path).not.toContain('/boot');
  });

  it('会话 ID 编码后拼入路径（避免特殊字符破坏路径结构）', async () => {
    await getConversationContext('a b/c?d');

    expect(requestClient.get).toHaveBeenCalledWith(
      '/message/staff/conversation/a%20b%2Fc%3Fd/context',
    );
  });

  it('双模式路径：cloud 原样，boot 由 rewriteBootUrl 改写为 /boot/...', () => {
    const path = '/message/staff/conversation/conversation-1/context';
    expect(rewriteBootUrl(path, false)).toBe(path);
    expect(rewriteBootUrl(path, true)).toBe(
      '/boot/staff/conversation/conversation-1/context',
    );
  });

  it('响应类型与契约 §8.2 字段一致', () => {
    expectTypeOf<
      ConversationContext['member']
    >().toEqualTypeOf<MemberContext | null>();
    expectTypeOf<ConversationContext['orders']>().toEqualTypeOf<
      OrderContextItem[]
    >();
    expectTypeOf<OrderContextItem['orderId']>().toEqualTypeOf<string>();
    expectTypeOf<OrderContextItem['payAmount']>().toEqualTypeOf<
      null | number | undefined
    >();
    expectTypeOf<OrderContextItem['productSummary']>().toEqualTypeOf<
      null | string | undefined
    >();
  });
});
