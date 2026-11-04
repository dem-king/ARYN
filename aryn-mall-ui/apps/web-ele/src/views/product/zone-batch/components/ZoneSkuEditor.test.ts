import type { ZoneTagDetail } from '../types';

import { flushPromises, mount } from '@vue/test-utils';

import { ElButton, ElLoading } from 'element-plus';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getZoneTags, saveZoneTags } from '#/api/product/goods-zone';

import ZoneSkuEditor from './ZoneSkuEditor.vue';

vi.mock('#/api/product/goods-zone', () => ({
  getZoneTags: vi.fn(),
  saveZoneTags: vi.fn(),
}));

const mockedGetZoneTags = vi.mocked(getZoneTags);
const mockedSaveZoneTags = vi.mocked(saveZoneTags);

function detailFixture(overrides: Partial<ZoneTagDetail> = {}): ZoneTagDetail {
  return {
    clearanceDiscountRate: null,
    clearanceExpireTime: null,
    clearancePrice: null,
    isClearanceItem: 0,
    isMemberItem: 1,
    memberDiscountRate: 0.9,
    memberPriceFixed: null,
    memberPriceFloor: null,
    skuOverrideCount: 0,
    skus: [
      {
        clearanceDiscountRate: null,
        clearanceExpireTime: null,
        clearancePrice: null,
        hasOverride: false,
        isClearanceItem: null,
        isMemberItem: null,
        memberDiscountRate: null,
        memberPriceFixed: null,
        memberPriceFloor: null,
        originalPrice: 39.9,
        salesPrice: 29.9,
        skuId: 'sku-1',
        specsName: '500g',
      },
    ],
    spuId: 'spu-1',
    spuName: '测试商品',
    warnings: [],
    ...overrides,
  } as ZoneTagDetail;
}

function mountEditor() {
  return mount(ZoneSkuEditor, {
    global: {
      directives: { loading: ElLoading.directive },
      stubs: {
        ElTable: {
          props: ['data'],
          template: '<div class="el-table-stub"><slot /></div>',
        },
        ElTableColumn: {
          template: '<div class="el-table-column-stub" />',
        },
      },
    },
    props: { spuId: 'spu-1' },
  });
}

describe('zoneSkuEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetZoneTags.mockResolvedValue(detailFixture());
    mockedSaveZoneTags.mockResolvedValue(undefined);
  });

  it('挂载后调用 getZoneTags 并回显 SPU 会员规则', async () => {
    const wrapper = mountEditor();
    await flushPromises();
    expect(mockedGetZoneTags).toHaveBeenCalledWith('spu-1');
    expect(wrapper.text()).toContain('会员专区（SPU 级）');
    expect(wrapper.text()).toContain('低价捡漏（SPU 级）');
    expect(wrapper.text()).toContain('SKU 级精调（0 个规格已覆盖）');
  });

  it('保存调用 saveZoneTags：replaceSkuRules=true 且继承 SKU 提交全 null（精确覆盖）', async () => {
    const wrapper = mountEditor();
    await flushPromises();
    const saveButton = wrapper
      .findAllComponents(ElButton)
      .find((button) => button.text().includes('保存专区标签'));
    if (!saveButton) throw new Error('未找到「保存专区标签」按钮');
    await saveButton.trigger('click');
    await flushPromises();
    expect(mockedSaveZoneTags).toHaveBeenCalledTimes(1);
    const call = mockedSaveZoneTags.mock.calls[0];
    if (!call) throw new Error('saveZoneTags 未被调用');
    const [spuId, payload] = call;
    expect(spuId).toBe('spu-1');
    expect(payload).toMatchObject({
      clearanceTag: false,
      memberTag: true,
      memberDiscountRate: 0.9,
      replaceSkuRules: true,
    });
    expect(payload.skus).toEqual([
      {
        clearanceDiscountRate: null,
        clearanceExpireTime: null,
        clearancePrice: null,
        clearanceTag: null,
        memberDiscountRate: null,
        memberPriceFixed: null,
        memberPriceFloor: null,
        memberTag: null,
        skuId: 'sku-1',
      },
    ]);
    // 保存成功后触发 saved 事件供外层同步主表单专区字段
    expect(wrapper.emitted('saved')).toHaveLength(1);
  });

  it('读取失败展示错误与重试入口', async () => {
    mockedGetZoneTags.mockRejectedValueOnce(new Error('读取失败'));
    const wrapper = mountEditor();
    await flushPromises();
    expect(wrapper.text()).toContain('读取失败');
  });
});
