import type { VueWrapper } from '@vue/test-utils';

import { flushPromises, mount } from '@vue/test-utils';

import { ElLoading } from 'element-plus';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { getByIds as getGoodsByIds } from '#/api/product/goods-spu';

import IdPickerField from '../id-picker-field/index.vue';
import LinkUrl from './index.vue';

vi.mock('#/api/product/goods-category', () => ({
  getPage: vi.fn(async () => []),
}));
vi.mock('#/api/product/goods-spu', () => ({
  getByIds: vi.fn(async () => []),
  getWarehousePage: vi.fn(async () => ({ records: [], total: 0 })),
}));
vi.mock('#/api/promotion/coupon-info', () => ({
  getByIds: vi.fn(async () => []),
  getPage: vi.fn(async () => ({ records: [], total: 0 })),
}));
vi.mock('#/api/promotion/discount', () => ({
  getById: vi.fn(async () => ({})),
  getPage: vi.fn(async () => ({ records: [], total: 0 })),
}));
vi.mock('#/api/promotion/group-buy-activity', () => ({
  getById: vi.fn(async () => ({})),
  getPage: vi.fn(async () => ({ records: [], total: 0 })),
}));
vi.mock('#/api/promotion/seckill', () => ({
  getSessionPage: vi.fn(async () => ({ records: [] })),
}));
vi.mock('#/api/promotion/page-design', () => ({
  getById: vi.fn(async () => ({})),
  getPage: vi.fn(async () => ({ records: [], total: 0 })),
  pageTypeLabel: (value: string) => `type-${value}`,
}));

/**
 * 选择弹窗是 defineAsyncComponent：浏览器里用户点开前早已加载完，测试里轮询
 * IdPickerField 的 dialogRef 是否就绪，避免用固定 sleep 造成偶发失败。
 */
async function waitForPickerReady(wrapper: VueWrapper) {
  await vi.waitFor(
    () => {
      const field = wrapper.findComponent(IdPickerField);
      // script setup 的 setupState 会把 ref 解包，所以这里直接判断组件实例
      const dialogRef = (field.vm as any)?.$?.setupState?.dialogRef;
      expect(dialogRef).toBeTruthy();
    },
    { timeout: 3000 },
  );
  await flushPromises();
}

function findButton(text: string) {
  return [...document.querySelectorAll('button')].find(
    (button) => button.textContent?.trim() === text,
  );
}

/**
 * 运行时冒烟：链接弹窗是多个装修组件共用的入口，静态契约测试只能证明源码里
 * 写了选择器，这里再确认组件能真正挂载、弹窗能打开、IdPickerField 注册表
 * 能解析出对应的选择弹窗（避免动态 import / props 契约在运行时才炸）。
 */
describe('链接弹窗冒烟', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('商品链接回显实体名称，而不是一串 ID', async () => {
    vi.mocked(getGoodsByIds).mockResolvedValue([
      {
        id: 'g-1',
        name: '新疆红枣',
        salesPrice: 19.9,
        spuUrls: ['https://example.com/a.png'],
      },
    ] as any);

    const wrapper = mount(LinkUrl, {
      attachTo: document.body,
      global: { directives: { loading: ElLoading.directive } },
      props: {
        modelValue: { type: 'goods', targetId: 'g-1', params: {}, path: '' },
      },
    });
    await flushPromises();

    expect((wrapper.find('input').element as HTMLInputElement).value).toBe(
      '新疆红枣',
    );
    wrapper.unmount();
  });

  it('未选目标时能打开商品选择弹窗', async () => {
    const wrapper = mount(LinkUrl, {
      attachTo: document.body,
      global: { directives: { loading: ElLoading.directive } },
      props: {
        modelValue: { type: 'goods', params: {}, path: '' },
      },
    });
    await flushPromises();

    await wrapper.find('input').trigger('click');
    await flushPromises();
    await waitForPickerReady(wrapper);

    expect(document.body.textContent).toContain('链接设置');
    const pickerButton = findButton('选择商品');
    expect(pickerButton).toBeTruthy();
    pickerButton?.click();
    await flushPromises();

    expect(document.body.textContent).toContain('商品选择器');
    wrapper.unmount();
  });

  it('已有目标时点选择器可直接更换，不必先删除', async () => {
    vi.mocked(getGoodsByIds).mockResolvedValue([
      { id: 'g-1', name: '旧商品', spuUrls: [] },
    ] as any);

    const wrapper = mount(LinkUrl, {
      attachTo: document.body,
      global: { directives: { loading: ElLoading.directive } },
      props: {
        modelValue: { type: 'goods', targetId: 'g-1', params: {}, path: '' },
      },
    });
    await flushPromises();

    await wrapper.find('input').trigger('click');
    await flushPromises();
    await waitForPickerReady(wrapper);

    const pickerButton = findButton('选择商品');
    expect(pickerButton).toBeTruthy();
    pickerButton?.click();
    await flushPromises();

    // replace-on-pick：已选满 1 个时仍能直接打开选择器覆盖
    expect(document.body.textContent).toContain('商品选择器');
    wrapper.unmount();
  });
});
