import { readonly, shallowReactive } from 'vue';

export type RetailPreviewStatus =
  | 'data'
  | 'empty'
  | 'error'
  | 'invalid'
  | 'loading'
  | 'placeholder';

export interface RetailPreviewState<T> {
  items: T[];
  message: string;
  status: RetailPreviewStatus;
}

export function createRetailPreviewController<T>() {
  const state = shallowReactive<RetailPreviewState<T>>({
    items: [],
    message: '配置后预览实时数据',
    status: 'placeholder',
  });
  let requestSequence = 0;

  async function load(
    validationErrors: string[],
    request: () => Promise<T[]>,
  ): Promise<void> {
    const sequence = ++requestSequence;
    state.items = [];
    if (validationErrors.length > 0) {
      state.message = validationErrors[0] ?? '组件配置无效';
      state.status = 'invalid';
      return;
    }

    state.message = '正在加载预览数据';
    state.status = 'loading';
    try {
      const items = await request();
      if (sequence !== requestSequence) return;
      state.items = items;
      state.message = items.length > 0 ? '' : '暂无可预览数据';
      state.status = items.length > 0 ? 'data' : 'empty';
    } catch {
      if (sequence !== requestSequence) return;
      state.message = '数据加载失败，请稍后重试';
      state.status = 'error';
    }
  }

  return {
    load,
    state: readonly(state),
  };
}
