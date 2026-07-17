import { describe, expect, it, vi } from 'vitest';

import { createLatestRequestRunner } from '../../../../../../../aryn-mall-uniapp/src/composables/useLatestRequest';

function deferred<T>() {
  let reject!: (reason?: unknown) => void;
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}

describe('mobile latest request runner', () => {
  it('does not let an older response overwrite a refreshed result', async () => {
    const first = deferred<string>();
    const second = deferred<string>();
    const committed: string[] = [];
    const runner = createLatestRequestRunner();

    const firstRun = runner.run(() => first.promise, {
      onSuccess: (value) => committed.push(value),
    });
    const secondRun = runner.run(() => second.promise, {
      onSuccess: (value) => committed.push(value),
    });
    second.resolve('fresh');
    await secondRun;
    first.resolve('stale');
    await firstRun;

    expect(committed).toEqual(['fresh']);
  });

  it('reports only the latest request error and settlement', async () => {
    const first = deferred<string>();
    const second = deferred<string>();
    const onError = vi.fn();
    const onSettled = vi.fn();
    const runner = createLatestRequestRunner();

    const firstRun = runner.run(() => first.promise, { onError, onSettled });
    const secondRun = runner.run(() => second.promise, { onError, onSettled });
    first.reject(new Error('stale error'));
    await firstRun;
    second.reject(new Error('latest error'));
    await secondRun;

    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'latest error' }),
    );
    expect(onSettled).toHaveBeenCalledTimes(1);
  });
});
