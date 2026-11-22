import { describe, expect, it } from 'vitest';

import { parseOpenBoot, rewriteBootUrl } from './boot-url';

describe('boot URL rewriting', () => {
  it('removes only the service prefix and preserves query/hash suffixes', () => {
    expect(rewriteBootUrl('/upms/sysuser/page?current=1#users', true)).toBe(
      '/boot/sysuser/page?current=1#users',
    );
  });

  it('leaves URLs unchanged when Boot mode is disabled or already applied', () => {
    expect(rewriteBootUrl('/upms/sysuser/page', false)).toBe(
      '/upms/sysuser/page',
    );
    expect(rewriteBootUrl('/boot/sysuser/page', true)).toBe(
      '/boot/sysuser/page',
    );
  });

  it('parses the environment flag without throwing on missing values', () => {
    expect(parseOpenBoot('true')).toBe(true);
    expect(parseOpenBoot('TRUE')).toBe(true);
    expect(parseOpenBoot(undefined)).toBe(false);
    expect(parseOpenBoot('invalid')).toBe(false);
  });
});

it('rewrites AI and procurement and campaign paths in both modes', () => {
  for (const url of [
    '/upms/ai-config/versions/activate',
    '/upms/ai-config/bootstrap',
    '/product/catalog-index/activate',
    '/product/sku-measurement/preview',
    '/product/sku-identifiers/123/review',
    '/product/catalog-index/123/retry',
    '/mall-order/app/procurement-drafts/1?revision=2',
    '/promotion/campaign-drafts/1/simulate',
    '/mall-order/delivery-groups',
  ]) {
    expect(rewriteBootUrl(url, false)).toBe(url);
    expect(rewriteBootUrl(url, true)).toBe(url.replace(/^\/[^/]+/, '/boot'));
  }
});

it('合送排程双模式路径保持订单域与查询参数', () => {
  expect(
    rewriteBootUrl('/mall-order/delivery-groups/1/candidates', false),
  ).toBe('/mall-order/delivery-groups/1/candidates');
  expect(rewriteBootUrl('/mall-order/delivery-groups/1/candidates', true)).toBe(
    '/boot/delivery-groups/1/candidates',
  );
  expect(rewriteBootUrl('/mall-order/delivery-groups/1/commands', false)).toBe(
    '/mall-order/delivery-groups/1/commands',
  );
  expect(rewriteBootUrl('/mall-order/delivery-groups/1/commands', true)).toBe(
    '/boot/delivery-groups/1/commands',
  );
  expect(
    rewriteBootUrl('/mall-order/fulfillment/wave/1/orders/batch', false),
  ).toBe('/mall-order/fulfillment/wave/1/orders/batch');
  expect(
    rewriteBootUrl('/mall-order/fulfillment/wave/1/orders/batch', true),
  ).toBe('/boot/fulfillment/wave/1/orders/batch');
  expect(rewriteBootUrl('/mall-order/fulfillment/wave/1/start', false)).toBe(
    '/mall-order/fulfillment/wave/1/start',
  );
  expect(rewriteBootUrl('/mall-order/fulfillment/wave/1/start', true)).toBe(
    '/boot/fulfillment/wave/1/start',
  );
});
