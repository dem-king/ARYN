import type { ZonePreview } from '../types';

import { flushPromises, mount } from '@vue/test-utils';

import { ElLoading } from 'element-plus';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { previewZoneTags } from '#/api/product/goods-zone';

import ZoneBatchDialog from './ZoneBatchDialog.vue';

vi.mock('#/api/product/goods-zone', () => ({
  batchZoneTags: vi.fn(),
  previewZoneTags: vi.fn(),
  undoBatch: vi.fn(),
}));
vi.mock('vue-clipboard3', () => ({
  default: () => ({ toClipboard: vi.fn() }),
}));

const mockedPreview = vi.mocked(previewZoneTags);

function previewFixture(): ZonePreview {
  return {
    items: [
      {
        before: {
          clearanceTag: 0,
          memberTag: 1,
          skuOverrideCount: 0,
        } as any,
        changes: [
          { type: 'NO_CHANGE', zone: 'member', warnings: [] },
          { type: 'NO_CHANGE', zone: 'clearance', warnings: [] },
        ],
        spuId: 'spu-1',
        spuName: '测试商品',
        spuStatus: '1',
        warnings: [],
      },
    ],
    summary: {
      add: 0,
      invalid: 0,
      noChange: 1,
      remove: 0,
      skuOverridesWillClear: 0,
      total: 1,
      update: 0,
      warningCount: 0,
    },
    zones: [
      {
        existing: 1,
        invalid: 0,
        noChange: 1,
        willAdd: 0,
        willRemove: 0,
        willUpdate: 0,
        withSkuOverride: 0,
        zone: 'member',
      },
      {
        existing: 0,
        invalid: 0,
        noChange: 1,
        willAdd: 0,
        willRemove: 0,
        willUpdate: 0,
        withSkuOverride: 0,
        zone: 'clearance',
      },
    ],
  };
}

function mountDialog(mode: 'remove' | 'standard') {
  return mount(ZoneBatchDialog, {
    attachTo: document.body,
    global: {
      directives: { loading: ElLoading.directive },
      stubs: { teleport: true },
    },
    props: {
      mode,
      spuIds: ['spu-1'],
      visible: true,
    },
  });
}

describe('zoneBatchDialog 动作入口去重', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedPreview.mockResolvedValue(previewFixture());
  });

  it('standard 模式（批量设置规则）不提供「移出专区」动作', async () => {
    const wrapper = mountDialog('standard');
    await flushPromises();
    expect(mockedPreview).toHaveBeenCalled();
    const text = wrapper.text();
    expect(text).toContain('加入/更新规则');
    expect(text).toContain('不修改');
    expect(text).not.toContain('移出专区');
    wrapper.unmount();
  });

  it('remove 模式（批量移出专区）只提供「移出专区 / 不修改」', async () => {
    const wrapper = mountDialog('remove');
    await flushPromises();
    const text = wrapper.text();
    expect(text).toContain('移出专区');
    expect(text).not.toContain('加入/更新规则');
    wrapper.unmount();
  });
});
