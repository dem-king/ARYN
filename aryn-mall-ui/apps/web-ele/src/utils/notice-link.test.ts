import { describe, expect, it } from 'vitest';

import {
  isSafeInternalRoute,
  parseJumpPayload,
  resolveNoticeLink,
} from './notice-link';

describe('parseJumpPayload', () => {
  it('parses the JSON object convention', () => {
    expect(parseJumpPayload('{"route":"/vessel/bind-apply"}')).toMatchObject({
      route: '/vessel/bind-apply',
    });
    expect(
      parseJumpPayload('{"bizType":"ORDER","bizId":"1001"}'),
    ).toMatchObject({ bizId: '1001', bizType: 'ORDER' });
  });

  it('accepts legacy bare route strings', () => {
    expect(parseJumpPayload('/order/order-info')).toMatchObject({
      route: '/order/order-info',
    });
  });

  it('returns an empty payload for malformed JSON', () => {
    expect(parseJumpPayload('not-json')).toEqual({});
    expect(parseJumpPayload(null)).toEqual({});
  });
});

describe('isSafeInternalRoute', () => {
  it('allows admin routes only', () => {
    expect(isSafeInternalRoute('/order/detail?id=1001')).toBe(true);
    expect(isSafeInternalRoute('/vessel/bind-apply')).toBe(true);
    expect(isSafeInternalRoute('/message/inbox')).toBe(true);
  });

  it('rejects external, protocol-relative and C-end routes', () => {
    expect(isSafeInternalRoute('https://evil.example.com')).toBe(false);
    expect(isSafeInternalRoute('//evil.example.com')).toBe(false);
    expect(isSafeInternalRoute('javascript:alert(1)')).toBe(false);
    expect(isSafeInternalRoute('/pages/order/detail?id=1')).toBe(false);
    expect(isSafeInternalRoute('/sub-pages/message/notice/detail')).toBe(false);
    expect(isSafeInternalRoute('/order/..%2F..%2Fetc')).toBe(false);
    expect(isSafeInternalRoute('')).toBe(false);
  });
});

describe('resolveNoticeLink', () => {
  it('keeps a safe detail route from jumpPayload', () => {
    expect(
      resolveNoticeLink({
        jumpPayload: '{"route":"/order/detail?id=1001"}',
      }),
    ).toBe('/order/detail?id=1001');
  });

  it('upgrades legacy order list routes to the detail page', () => {
    expect(
      resolveNoticeLink({
        cardPayload: '{"orderId":"1001","amount":"88.00"}',
        jumpPayload: '{"route":"/order/order-info"}',
        sourceType: 'ORDER_CREATE',
      }),
    ).toBe('/order/detail?id=1001');
  });

  it('upgrades legacy refund list routes with the refund id from cardPayload', () => {
    expect(
      resolveNoticeLink({
        cardPayload: '{"orderId":"9001","amount":"12.00"}',
        jumpPayload: '{"route":"/order/order-refund"}',
        sourceType: 'ORDER_REFUND_APPLY',
      }),
    ).toBe('/order/order-refund?refundId=9001');
  });

  it('maps BIZ_DETAIL payloads without route by bizType', () => {
    expect(
      resolveNoticeLink({
        jumpPayload: '{"bizType":"ORDER","bizId":"1002"}',
      }),
    ).toBe('/order/detail?id=1002');
    expect(
      resolveNoticeLink({
        jumpPayload: '{"bizType":"REFUND","bizId":"9002"}',
      }),
    ).toBe('/order/order-refund?refundId=9002');
  });

  it('falls back to cardPayload for legacy notices', () => {
    expect(
      resolveNoticeLink({
        cardPayload: '{"orderId":"1003"}',
        sourceType: 'ORDER_CANCEL',
      }),
    ).toBe('/order/detail?id=1003');
    expect(
      resolveNoticeLink({
        cardPayload: '{"orderId":"9003"}',
        sourceType: 'ORDER_REFUND_APPLY',
      }),
    ).toBe('/order/order-refund?refundId=9003');
  });

  it('keeps list routes when there is no business id to upgrade', () => {
    expect(
      resolveNoticeLink({ jumpPayload: '{"route":"/vessel/bind-apply"}' }),
    ).toBe('/vessel/bind-apply');
    expect(
      resolveNoticeLink({ jumpPayload: '{"route":"/order/order-info"}' }),
    ).toBe('/order/order-info');
  });

  it('returns undefined for plain notices and unsafe routes', () => {
    expect(resolveNoticeLink({})).toBeUndefined();
    expect(
      resolveNoticeLink({
        jumpPayload: '{"route":"/pages/delivery/task-detail?id=1"}',
      }),
    ).toBeUndefined();
    expect(
      resolveNoticeLink({
        jumpPayload: '{"route":"https://evil.example.com"}',
      }),
    ).toBeUndefined();
  });
});
