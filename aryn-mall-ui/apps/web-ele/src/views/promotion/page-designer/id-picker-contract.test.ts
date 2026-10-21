import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

import { COMMON_PAGE_PRESETS } from '../page-design/components/common/link-url/common-pages';

const componentsRoot = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-design/components',
);

function readSetting(...segments: string[]) {
  return readFileSync(resolve(componentsRoot, ...segments), 'utf8');
}

/** pages.json 是 JSONC（含注释），这里剥注释后按主包 + 分包 root 拼出完整路由集合 */
function stripJsonComments(source: string) {
  return source
    .replaceAll(/\/\*[\s\S]*?\*\//g, '')
    .replaceAll(/(^|[^:])\/\/.*$/gm, '$1');
}

function collectUniappRoutes(source: string) {
  const config = JSON.parse(stripJsonComments(source)) as {
    pages?: Array<{ path: string }>;
    subPackages?: Array<{ pages?: Array<{ path: string }>; root: string }>;
  };
  const routes = new Set<string>();
  for (const page of config.pages ?? []) routes.add(`/${page.path}`);
  for (const subPackage of config.subPackages ?? []) {
    for (const page of subPackage.pages ?? []) {
      routes.add(`/${subPackage.root}/${page.path}`);
    }
  }
  return routes;
}

/**
 * 装修器「手选数据」必须走选择器，禁止再让运营手抄 ID 的契约。
 *
 * 背景：装修组件的 manual 模式最初用「手选 ID（逗号分隔）」文本框 /
 * 可输入 ID 的下拉，运营根本记不住商品、场次、活动 ID，配置形同虚设；
 * 2026-10-14 起统一换成选择器（商品/优惠券/分类/折扣/拼团/秒杀场次），
 * 面板内回显已选名称并可删除。此处锁住八个入口，防止回退。
 *
 * 2026-10-21 追加：链接弹窗（LinkUrl）同样不能退回「目标 ID」文本框——
 * 商品/微页面/优惠券/活动一律用选择器，触发器与面板回显实体名称。
 */
describe('装修手选数据选择器契约', () => {
  it('extension-settings 的商品/优惠券手选走 IdPickerField，分类走分类树选择', () => {
    const source = readSetting('extension', 'extension-settings.vue');

    expect(source).toContain('IdPickerField');
    expect(source).toContain('CategoryPicker');
    expect(source).not.toContain('手选商品 ID');
    expect(source).not.toContain('手选 ID（逗号分隔）');
  });

  const manualPanelCases = [
    { kind: 'goods', name: 'goods-group' },
    { kind: 'goods', name: 'goods-scroll' },
    { kind: 'seckill-session', name: 'seckill' },
    { kind: 'discount-activity', name: 'discount' },
    { kind: 'groupbuy-activity', name: 'limited-activity' },
  ];

  it.each(manualPanelCases)(
    '$name 的手选入口使用 $kind 选择器',
    ({ kind, name }) => {
      const source = readSetting(name, 'setting.vue');

      expect(source).toContain('IdPickerField');
      expect(source).toContain(`kind="${kind}"`);
      expect(source).not.toContain('输入');
      expect(source).not.toContain('逗号');
    },
  );

  it('goods-group / goods-scroll 的自动模式分类走分类树选择', () => {
    for (const type of ['goods-group', 'goods-scroll']) {
      const source = readSetting(type, 'setting.vue');
      expect(source).toContain('CategoryPicker');
      expect(source).not.toContain('分类 ID');
    }
  });
});

describe('装修链接目标选择器契约', () => {
  const linkSource = () => readSetting('common', 'link-url', 'index.vue');
  const registrySource = () =>
    readSetting('common', 'id-picker-field', 'registry.ts');

  it('商品/微页面/优惠券/活动链接走实体选择器，分类走分类树，不再手填目标 ID', () => {
    const source = linkSource();

    expect(source).toContain('IdPickerField');
    expect(source).toContain('ElCascader');
    // 类型 → 选择器映射单独成模块，由 link-types.test.ts 直接验证
    expect(source).toContain('pickerKindOfType');
    // 回归护栏：曾经这里是 label="目标 ID" 的 ElInput + v-model="form.targetId"
    expect(source).not.toContain('label="目标 ID"');
    expect(source).not.toContain('v-model="form.targetId"');
    // 已有目标时允许直接点选择器覆盖，不必先删除
    expect(source).toContain('replace-on-pick');
  });

  it('微页面选择器只列「已发布微页面」，与 C 端 diy-page 的打开条件一致', () => {
    const registry = registrySource();

    expect(registry).toContain("pageType: '0'");
    expect(registry).toContain("publishedStatus: '1'");
    // 历史配置里的未发布/内嵌页在回显时标失效
    expect(registry).toContain('pageDesignLinkItem');
  });

  it('外部小程序用 AppID / 版本 / 路径三个显式字段，不再让运营拼 params JSON', () => {
    const source = linkSource();

    expect(source).toContain('小程序 AppID');
    expect(source).toContain("setMiniParam('appId'");
    expect(source).toContain("setMiniParam('envVersion'");
    expect(source).toContain('miniProgramEnvOptions');
  });

  it('触发器回显实体名称而不是 ID，附加参数收进高级设置并做 JSON 校验', () => {
    const source = linkSource();

    expect(source).toContain('resolvedLabel');
    expect(source).toContain('.hydrate(');
    expect(source).toContain('ElCollapse');
    expect(source).toContain('高级设置');
    expect(source).toContain('parseParamsText');
    // 编辑器保存用 strict；发布校验保持宽松，存量页面不会因为历史链接被卡死
    expect(source).toContain('{ strict: true }');
    expect(source).toContain("advancedOpen.value = ['params']");
  });

  it('自定义路径的常用页面全部存在于 C 端 pages.json', () => {
    const pagesJson = readFileSync(
      resolve(process.cwd(), '../aryn-mall-uniapp/src/pages.json'),
      'utf8',
    );
    const routes = collectUniappRoutes(pagesJson);

    expect(routes.size).toBeGreaterThan(0);
    expect(COMMON_PAGE_PRESETS.length).toBeGreaterThanOrEqual(18);
    expect(new Set(COMMON_PAGE_PRESETS.map((preset) => preset.path)).size).toBe(
      COMMON_PAGE_PRESETS.length,
    );
    for (const preset of COMMON_PAGE_PRESETS) {
      expect(
        routes.has(preset.path),
        `常用页面 ${preset.path} 未在 C 端 pages.json 注册`,
      ).toBe(true);
    }
  });
});
