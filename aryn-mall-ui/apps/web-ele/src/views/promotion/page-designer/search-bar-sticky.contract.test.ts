import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

const searchBarRoot = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-design/components/search-bar',
);

const designerRoot = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-designer',
);

function readSearchBar(file: string) {
  return readFileSync(resolve(searchBarRoot, file), 'utf8');
}

const settingSource = readSearchBar('setting.vue');
const registrySource = readFileSync(
  resolve(designerRoot, 'registry/component-registry.ts'),
  'utf8',
);

/**
 * 搜索框「滚动常驻」管理端契约。
 *
 * 背景：C 端首页的搜索框会随页面滚动移出视口。C 端按 showData.sticky === true
 * 实现滚动常驻：当且仅当该搜索框是页面首个区块的第一个组件时，滚动时固定在
 * 导航栏下方。管理端只负责配置入口与默认值，渲染语义由 C 端消费。
 *
 * 这条契约防的是配置入口被拆散或误放：
 * 1. 默认值必须显式为 false——旧页面数据缺少 sticky 字段时行为仍可预期；
 * 2. 开关必须绑定 props.sticky（v-model="form.sticky"），而不是另起字段名，
 *    否则 C 端读不到；
 * 3. 提示文案必须写明「首个区块」限定——常驻只在特定位置生效，运营在非
 *    首屏位置开启后不至于误以为一定生效；
 * 4. 常驻属于内容/行为设置，不得混入样式面板（style-setting.vue），
 *    以维持「内容设置 / 样式设置」的拆分约定。
 */
describe('search-bar sticky admin contract', () => {
  it('offers a sticky switch bound to form.sticky', () => {
    expect(settingSource).toContain('滚动常驻');
    expect(settingSource).toContain('ElSwitch');
    expect(settingSource).toContain('v-model="form.sticky"');
  });

  it('defaults sticky to false in the content settings', () => {
    expect(settingSource).toMatch(/sticky:\s*false/);
  });

  it('documents the first-block restriction in the tip', () => {
    expect(settingSource).toContain('首个区块');
    expect(settingSource).toContain('导航栏下方常驻');
  });

  it('keeps the sticky default in the legacy component registry', () => {
    // 画布拖入组件时读 registry 的 createDefaultProps，默认值必须与设置面板
    // 的 defaultConfig 保持一致，否则首次渲染与保存后的数据形态不一致。
    expect(registrySource).toMatch(/'search-bar':[\s\S]*?sticky:\s*false/);
  });

  it('keeps sticky out of the style panel', () => {
    // 常驻是内容/行为配置，样式面板只管视觉呈现。
    expect(readSearchBar('style-setting.vue')).not.toContain('sticky');
  });
});
