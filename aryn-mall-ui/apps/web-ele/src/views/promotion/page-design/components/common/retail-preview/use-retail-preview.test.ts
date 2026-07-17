import { describe, expect, it, vi } from 'vitest';

import { createRetailPreviewController } from './use-retail-preview';

describe('retail preview controller', () => {
  it('moves from placeholder through loading to data', async () => {
    let resolveRequest: (items: string[]) => void = () => {};
    const request = vi.fn(
      () =>
        new Promise<string[]>((resolve) => {
          resolveRequest = resolve;
        }),
    );
    const controller = createRetailPreviewController<string>();

    expect(controller.state.status).toBe('placeholder');
    const loading = controller.load([], request);
    expect(controller.state.status).toBe('loading');

    resolveRequest(['商品 A']);
    await loading;

    expect(controller.state.status).toBe('data');
    expect(controller.state.items).toEqual(['商品 A']);
  });

  it('uses the empty state when the request returns no data', async () => {
    const controller = createRetailPreviewController<string>();

    await controller.load([], async () => []);

    expect(controller.state.status).toBe('empty');
    expect(controller.state.message).toBe('暂无可预览数据');
  });

  it('uses the invalid state without sending a request', async () => {
    const request = vi.fn(async () => ['不应加载']);
    const controller = createRetailPreviewController<string>();

    await controller.load(['请选择活动'], request);

    expect(request).not.toHaveBeenCalled();
    expect(controller.state.status).toBe('invalid');
    expect(controller.state.message).toBe('请选择活动');
  });

  it('isolates request failures in the component error state', async () => {
    const controller = createRetailPreviewController<string>();

    await controller.load([], async () => {
      throw new Error('network details must not be serialized');
    });

    expect(controller.state.status).toBe('error');
    expect(controller.state.message).toBe('数据加载失败，请稍后重试');
    expect(Object.keys(controller.state).sort()).toEqual([
      'items',
      'message',
      'status',
    ]);
  });
});
