import { mount } from '@vue/test-utils';

import { ElAutocomplete } from 'element-plus';
import { describe, expect, it, vi } from 'vitest';

import {
  filterAttrSuggestions,
  GOODS_ATTR_TAG_LABEL_PRESETS,
} from './goods-attr-tag-presets';

/**
 * ElAutocomplete 的 `fetchSuggestions(queryString, callback)` 是本功能的接线点：
 * 回调签名、触发时机（输入 / 聚焦）一旦理解错，下拉就是空的但不会报错，
 * 单测纯函数无法发现。这里用真实组件确认「输入 → 我们的回调被调用 → 候选项正确」。
 *
 * 下拉面板需要 popper 测位，happy-dom 下不渲染，所以断言的是回调载荷，
 * 而不是 DOM 里的 <li>。
 */
describe('attr tag preset autocomplete contract', () => {
  const labels = GOODS_ATTR_TAG_LABEL_PRESETS.map((item) => item.label);

  it('receives the typed keyword and hands back the filtered presets', async () => {
    const received: string[] = [];
    const wrapper = mount(ElAutocomplete, {
      attachTo: document.body,
      props: {
        modelValue: '',
        debounce: 0,
        fetchSuggestions: (
          keyword: string,
          callback: (items: Array<{ value: string }>) => void,
        ) => {
          received.push(keyword);
          callback(
            filterAttrSuggestions(labels, keyword).map((value) => ({ value })),
          );
        },
      },
    });

    await wrapper.find('input').setValue('储');
    await new Promise((resolve) => setTimeout(resolve, 30));

    expect(received).toContain('储');
    // 候选按包含匹配收敛到「储存条件」
    expect(filterAttrSuggestions(labels, '储')).toEqual(['储存条件']);

    wrapper.unmount();
  });

  it('accepts a string model value without coercing it to an object', () => {
    // ElAutocomplete 用 valueKey 取值后 emit 字符串；v-model 直接绑在 tag.value 上，
    // 若回调返回对象会写坏表单字段，这里钉住「产出必须是 { value: string }」
    const suggestions = filterAttrSuggestions(labels, '产地').map((value) => ({
      value,
    }));
    expect(suggestions).toEqual([{ value: '产地' }]);
    expect(suggestions[0]?.value).toBeTypeOf('string');
  });

  it('passes the input maxlength through to the real input element', async () => {
    const wrapper = mount(ElAutocomplete, {
      attachTo: document.body,
      props: {
        modelValue: '',
        maxlength: '12',
        fetchSuggestions: vi.fn(),
      },
    });

    // 属性名 12 字符上限是后端硬约束，必须真的落在 <input> 上
    expect(wrapper.find('input').attributes('maxlength')).toBe('12');

    wrapper.unmount();
  });
});
