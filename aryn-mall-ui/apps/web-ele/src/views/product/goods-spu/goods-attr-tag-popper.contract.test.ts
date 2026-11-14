import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

const formSource = readFileSync(
  resolve(import.meta.dirname, 'form.vue'),
  'utf8',
);

/**
 * 属性标签的候选面板由 Element Plus teleport 到 body：
 * - `ElAutocomplete` 的默认插槽 `<li>` 与 `#header`；
 * - `ElDropdown` 的 `ElDropdownMenu`。
 *
 * 写在这些面板上的样式如果留在 `<style scoped>` 里，编译后会带上
 * `.goods-container .goods-info` 祖先链，面板挂到 body 后祖先不存在、样式整段失效——
 * 曾因此把「属性名 + 说明」挤成一长句（视觉上像不可读的大段文字）。
 * 这个契约钉住：面板样式必须走 popper-class + 非 scoped 块。
 */
describe('goods attr tag popper style contract', () => {
  it('hooks both autocompletes and the dropdown with popper-class', () => {
    expect(formSource).toContain(
      'popper-class="attr-tag-popper attr-tag-popper--value"',
    );
    expect(formSource).toContain(
      'popper-class="attr-tag-popper attr-tag-popper--label"',
    );
    expect(formSource).toContain(
      'popper-class="attr-tag-popper attr-tag-popper--presets"',
    );
  });

  it('styles teleported panels in a non-scoped style block', () => {
    const scopedEnd = formSource.indexOf('<style lang="scss">');
    expect(scopedEnd).toBeGreaterThan(-1);
    const globalStyles = formSource.slice(scopedEnd);
    // 面板选择器必须落在非 scoped 块内，且能命中 teleport 后的真实 DOM
    expect(globalStyles).toContain('.attr-tag-popper');
    expect(globalStyles).toContain('.el-autocomplete-suggestion li');
    expect(globalStyles).toContain('.el-dropdown-menu__item');
    // 两行候选不能被 Element Plus 的定高行裁掉
    expect(globalStyles).toContain('height: auto;');
    expect(globalStyles).toContain('white-space: normal;');
  });

  it('never re-declares panel styles inside the scoped block', () => {
    const scopedEnd = formSource.indexOf('<style lang="scss">');
    const scopedStyles = formSource.slice(0, scopedEnd);
    // 这里只允许出现输入框本体的类名；面板内类名一旦回流 scoped 就重新失效
    expect(scopedStyles).not.toContain('.attr-tag-option');
    expect(scopedStyles).not.toContain('.attr-tag-popper');
  });

  it('renders label suggestions as two lines instead of one run-on sentence', () => {
    // 候选结构：主行（名称 + 常用值条数）+ 说明行
    expect(formSource).toContain('attr-tag-option attr-tag-option--stack');
    expect(formSource).toContain('attr-tag-option__desc');
    expect(formSource).toContain('attr-tag-option__count');
    // 值候选在全局池下要标出所属属性名，否则「冷藏」不知道配给谁
    expect(formSource).toContain('v-if="!item.scoped"');
    expect(formSource).toContain('attr-tag-option__owner');
  });

  it('explains the current suggestion scope in the panel header', () => {
    expect(formSource).toContain('attrValueScopeHint');
    expect(formSource).toContain('<template #header>');
  });
});
