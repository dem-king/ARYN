import { beforeEach, expect, it, vi } from 'vitest';

import { requestClient } from '#/api/request';

import { rewriteBootUrl } from '../boot-url';
import {
  copyActivity,
  deleteActivity,
  pauseActivity,
  publishActivity,
  updateActivity,
} from './ship-activity';

vi.mock('#/api/request', () => ({
  requestClient: { delete: vi.fn(), post: vi.fn(), put: vi.fn() },
}));

beforeEach(() => vi.clearAllMocks());

it('binds edits and lifecycle commands to the revision reviewed by the operator', async () => {
  await updateActivity('a', { activityName: '草稿', expectedRevision: 3 });
  await publishActivity('a', 3, true);
  await pauseActivity('a', 4);
  await copyActivity('a', 5);
  await deleteActivity('a', 5);

  expect(requestClient.put).toHaveBeenCalledWith('/promotion/ship-activity/a', {
    activityName: '草稿',
    expectedRevision: 3,
  });
  expect(requestClient.post).toHaveBeenNthCalledWith(
    1,
    '/promotion/ship-activity/a/publish',
    null,
    { params: { expectedRevision: 3, force: true } },
  );
  expect(requestClient.post).toHaveBeenNthCalledWith(
    2,
    '/promotion/ship-activity/a/pause',
    null,
    { params: { expectedRevision: 4 } },
  );
  expect(requestClient.post).toHaveBeenNthCalledWith(
    3,
    '/promotion/ship-activity/a/copy',
    null,
    { params: { expectedRevision: 5 } },
  );
  expect(requestClient.delete).toHaveBeenCalledWith(
    '/promotion/ship-activity/a',
    {
      params: { expectedRevision: 5 },
    },
  );
});

it('preserves revision and force parameters when rewriting both deployment modes', () => {
  const url =
    '/promotion/ship-activity/a/publish?expectedRevision=3&force=true';
  expect(rewriteBootUrl(url, false)).toBe(url);
  expect(rewriteBootUrl(url, true)).toBe(
    '/boot/ship-activity/a/publish?expectedRevision=3&force=true',
  );
});
