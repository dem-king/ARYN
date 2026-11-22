import { beforeEach, expect, it, vi } from 'vitest';

import { requestClient } from '#/api/request';

import { rewriteBootUrl } from '../boot-url';
import {
  generateCampaign,
  generateCampaignPage,
  previewCampaignPage,
  retryCampaignPublication,
} from './campaign-draft';

vi.mock('#/api/request', () => ({
  requestClient: { get: vi.fn(), post: vi.fn(), put: vi.fn() },
}));
beforeEach(() => vi.clearAllMocks());
it('页面、生成和恢复保留修订及请求标识', async () => {
  await generateCampaign('a', 3, 'request');
  await generateCampaignPage('a', {
    expectedRevision: 3,
    expectedPageRevision: 2,
  });
  await previewCampaignPage(
    'a',
    { expectedRevision: 3, expectedPageRevision: 2, simulationId: 'sim' },
    true,
  );
  await retryCampaignPublication('operation');
  expect(requestClient.post).toHaveBeenNthCalledWith(
    1,
    '/promotion/campaign-draft/a/generation',
    { expectedRevision: 3, requestId: 'request' },
  );
  expect(requestClient.post).toHaveBeenNthCalledWith(
    2,
    '/promotion/campaign-draft/a/page',
    { expectedRevision: 3, expectedPageRevision: 2 },
  );
  expect(requestClient.post).toHaveBeenNthCalledWith(
    3,
    '/promotion/campaign-draft/a/page-review',
    { expectedRevision: 3, expectedPageRevision: 2, simulationId: 'sim' },
  );
  expect(requestClient.post).toHaveBeenNthCalledWith(
    4,
    '/promotion/campaign-draft/publications/operation/retry',
  );
});
it('实际Controller路径在boot与cloud正确改写', () => {
  for (const url of [
    '/promotion/campaign-draft',
    '/promotion/campaign-draft/a/generation',
    '/promotion/campaign-draft/a/page-preview',
    '/promotion/campaign-draft/a/page-review',
    '/promotion/campaign-draft/a/publish',
    '/promotion/campaign-draft/publications/operation/retry',
    '/promotion/campaign-simulation',
    '/promotion/campaign-draft/a/metrics',
  ]) {
    expect(rewriteBootUrl(url, false)).toBe(url);
    expect(rewriteBootUrl(url, true)).toBe(
      url.replace('/promotion/', '/boot/'),
    );
  }
});
