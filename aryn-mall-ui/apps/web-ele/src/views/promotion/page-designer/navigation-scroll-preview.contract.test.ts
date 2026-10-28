import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

/**
 * 记录一下「沉浸式导航」在管理端两处预览里的接线契约
 * （需求：docs/50-需求文档/2026-10-27-小程序沉浸式导航栏/README.md §4.6.2/§4.6.3）。
 *
 * 两次踩过的设计红线，这里静态钉死：
 *
 * 1. **画布预览态不能污染草稿**。编辑器画布没有内部滚动容器（外层 stage 才滚动），
 *    硬造滚动容器会与 stage 滚动叠加、破坏拖拽/选中定位。所以「滚动后」预览态
 *    是 index.vue 的本地 ref，经 prop 传给 phone-canvas 只参与导航样式计算；
 *    一旦写进 document / patchPage，拖一下滑杆就多一步撤销、并触发脏标记。
 *
 * 2. **草稿预览必须接真实滚动**。预览页是浏览器整页滚动，导航要 sticky 吸顶；
 *    但 `.preview-canvas { overflow: hidden }` 会把它变成 sticky 的滚动祖先，
 *    导航到顶后不再吸住。同时 page-preview 此前漏传 pageType，内嵌页型预览会
 *    误渲染装修导航——沉浸式效果会把「画布没有、预览有」放大成事故。
 */
const designerRoot = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-designer',
);
const previewRoot = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-preview',
);
const previewUsageRoots = resolve(process.cwd(), 'apps/web-ele/src');

function readDesigner(relativePath: string) {
  return readFileSync(resolve(designerRoot, relativePath), 'utf8');
}

function readPreview(relativePath: string) {
  return readFileSync(resolve(previewRoot, relativePath), 'utf8');
}

function readPreviewUsage(relativePath: string) {
  return readFileSync(resolve(previewUsageRoots, relativePath), 'utf8');
}

/** 取最后一个 <style> 块并剥掉注释：说明性注释会引用旧写法，断言只针对真实声明 */
function styleBlock(source: string) {
  const matches = [...source.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)];
  const block = matches.at(-1)?.[1] ?? '';
  return block.replaceAll(/\/\*[\s\S]*?\*\//g, '');
}

function styleRule(source: string, selector: string) {
  // 选择器里的 `.` `:` `(` `)` 都要转义，否则带 :not() 的规则匹配不到
  const escaped = selector.replaceAll(/[.*+?^${}()|[\]\\]/g, String.raw`\$&`);
  return new RegExp(`${escaped}\\s*\\{([^}]*)\\}`).exec(source)?.[1] ?? '';
}

const phoneCanvas = readDesigner('components/phone-canvas.vue');
const toolbar = readDesigner('components/designer-toolbar.vue');
const designerIndex = readDesigner('index.vue');
const previewIndex = readPreview('index.vue');
const previewCanvas = readPreview('components/preview-canvas.vue');

describe('编辑画布：导航滚动渐显预览态', () => {
  it('phone-canvas 接收 scrollTop，并用共享解析函数合成 base/surface 两层', () => {
    // scrollTop 是可选 prop（默认 0），只参与样式计算
    expect(phoneCanvas).toMatch(/scrollTop\?:\s*number/);
    expect(phoneCanvas).toContain('props.scrollTop ?? 0');
    // 先静态目标底色、再按进度合成：两层 DOM + hairline，与 C 端 hr-navbar 同构
    expect(phoneCanvas).toContain('resolveNavigationScrollSurface');
    expect(phoneCanvas).toContain('resolveNavigationScrollAppearance');
    expect(phoneCanvas).toContain('phone-navigation__base');
    expect(phoneCanvas).toContain('phone-navigation__surface');
    expect(phoneCanvas).toContain('phone-navigation__hairline');
    expect(phoneCanvas).toContain('scrollAppearance.value.baseOpacity');
    expect(phoneCanvas).toContain('scrollAppearance.value.surfaceOpacity');
    expect(phoneCanvas).toContain('scrollAppearance.hairlineOpacity');
  });

  it('无渐显（mode!=fade）时导航仍走旧样式，375px 画布契约不变', () => {
    expect(phoneCanvas).toContain('fadeEnabled.value');
    // 渐显关闭：外层仍用整块 navigationStyle；开启才交给两层背景
    expect(phoneCanvas).toMatch(
      /fadeEnabled\.value\s*\?\s*\{\s*color:[^}]+\}\s*:\s*navigationStyle\.value/,
    );
    expect(phoneCanvas).toContain(
      "const fadeEnabled = computed(() => scrollEffect.value.mode === 'fade')",
    );
    // 375px 设备屏宽与内嵌页型 showNavigation 判定不能被动过
    expect(phoneCanvas).toContain('width: 375px');
    expect(phoneCanvas).toMatch(
      /showNavigation\s*=\s*computed\(\s*\(\)\s*=>\s*!isEmbeddedPage\.value\s*&&\s*props\.page\.navigation\.visible/,
    );
    // 画布只读：代码里不得出现 patchPage 调用（注释里的设计说明不算）
    const phoneCanvasScript =
      /<script setup[^>]*>([\s\S]*?)<\/script>/.exec(phoneCanvas)?.[1] ?? '';
    const phoneCanvasCode = phoneCanvasScript
      .replaceAll(/\/\*[\s\S]*?\*\//g, '')
      .replaceAll(/\/\/.*$/gm, '');
    expect(phoneCanvasCode).not.toContain('patchPage');
  });

  it('工具栏「顶部 / 滚动后」切换与 0–260px 滑杆只 emit 本地滚动值', () => {
    expect(toolbar).toContain("'update:scrollTop': [value: number]");
    expect(toolbar).toContain('const SCROLL_PREVIEW_MAX = 260');
    expect(toolbar).toContain('顶部');
    expect(toolbar).toContain('滚动后');
    // 仅当页面导航开启渐显且非内嵌页型时渲染（避免死控件）
    expect(toolbar).toContain('scrollPreviewAvailable');
    expect(toolbar).toContain('v-if="scrollPreviewAvailable"');
    // 滑杆 0–260
    expect(toolbar).toContain(':max="SCROLL_PREVIEW_MAX"');
    expect(toolbar).toContain(':min="0"');
    expect(toolbar).toContain("emit('update:scrollTop'");
    // 原有缩放 / 骨架开关契约不能被新控件顶掉
    expect(toolbar).toContain(
      "emit('update:zoom', Math.max(0.75, zoom - 0.05))",
    );
    expect(toolbar).toContain(
      "emit('update:zoom', Math.min(1.15, zoom + 0.05))",
    );
    expect(toolbar).toContain("emit('update:showPageShell', !showPageShell)");
  });

  it('滚动预览态是 index.vue 的本地 ref，不进 document、不触发 patchPage/撤销', () => {
    const script =
      /<script setup[^>]*>([\s\S]*?)<\/script>/.exec(designerIndex)?.[1] ?? '';

    expect(script).toContain('const canvasScrollTop = ref(0)');
    expect(script).toContain('const scrollEffectAvailable = computed(');
    // 可用性 = 开启渐显 + 非内嵌页型，复用 #/api isEmbeddedPageType 的口径
    expect(script).toContain('isEmbeddedPageType(pageType.value)');
    expect(script).toContain('normalizeNavigationScrollEffect(');

    // 画布预览态不得进入文档/撤销链路
    expect(script).not.toMatch(/patchPage\([^)]*canvasScrollTop/);
    expect(script).not.toMatch(/changed\([\s\S]{0,80}canvasScrollTop/);

    // 传给画布与工具栏；页面设置面板拿到 pageType
    expect(designerIndex).toContain(':scroll-top="canvasScrollTop"');
    expect(designerIndex).toContain(
      '@update:scroll-top="canvasScrollTop = $event"',
    );
    expect(designerIndex).toContain(
      ':scroll-preview-available="scrollEffectAvailable"',
    );

    const panelUsageStart = designerIndex.indexOf('<PageSettingsPanel');
    expect(panelUsageStart).toBeGreaterThan(-1);
    const panelUsage = designerIndex.slice(
      panelUsageStart,
      designerIndex.indexOf('/>', panelUsageStart),
    );
    expect(panelUsage).toContain(':page-type="pageType"');
  });
});

describe('草稿预览：真实滚动接线与导航吸顶', () => {
  it('page-preview 从 getPreview 响应取 pageType 并传给 PreviewCanvas', () => {
    // 修既有缺陷：接口响应里有 pageType，此前没透传，内嵌页型预览会误渲染导航
    expect(previewIndex).toContain('response.pageType');
    expect(previewIndex).toContain('pageType.value = response.pageType');
    expect(previewIndex).toContain(':page-type="pageType"');
    expect(previewIndex).toContain(
      "const pageType = shallowRef<PageDesignType>('0')",
    );
  });

  it('window scroll 监听 passive + rAF 合并，scrollTop = max(0, -画布矩形 top)', () => {
    expect(previewIndex).toContain(
      "window.addEventListener('scroll', scheduleCanvasScrollMeasure",
    );
    expect(previewIndex).toContain('passive: true');
    expect(previewIndex).toContain('window.requestAnimationFrame');
    expect(previewIndex).toContain('getBoundingClientRect().top');
    expect(previewIndex).toContain('Math.max(0, -');
    expect(previewIndex).toContain(':scroll-top="scrollTop"');
    // 卸载必须移除监听并取消挂起的帧
    expect(previewIndex).toContain(
      "window.removeEventListener('scroll', scheduleCanvasScrollMeasure)",
    );
    expect(previewIndex).toContain('window.cancelAnimationFrame');
  });

  it('preview-canvas 接收 scrollTop 并消费共享解析函数渲染两层', () => {
    expect(previewCanvas).toMatch(/scrollTop\?:\s*number/);
    // 缺省 0：生效卡片等缩略图（fullHeight=false）保持顶部态
    expect(previewCanvas).toContain('scrollTop: 0');
    expect(previewCanvas).toContain('resolveNavigationScrollSurface');
    expect(previewCanvas).toContain('resolveNavigationScrollAppearance');
    expect(previewCanvas).toContain('preview-navigation__base');
    expect(previewCanvas).toContain('preview-navigation__surface');
    expect(previewCanvas).toContain('preview-navigation__hairline');
    // 内嵌页型不渲染装修导航的既有判定保持不变
    expect(previewCanvas).toContain(
      'v-if="!isEmbeddedPage && document.page.navigation.visible"',
    );
  });

  it('导航 sticky 吸顶且层级高于区块吸顶 10；.preview-canvas 不能再是滚动祖先', () => {
    const styles = styleBlock(previewCanvas);
    // sticky 只对整页草稿预览生效：卡片/模板缩略图是 --inline，不能误吸顶
    const stickyRule = styleRule(
      styles,
      '.preview-canvas:not(.preview-canvas--inline) .preview-navigation',
    );
    expect(stickyRule).toMatch(/position:\s*sticky/);
    expect(stickyRule).toMatch(/top:\s*0/);
    const zIndex = Number(/z-index:\s*(\d+)/.exec(stickyRule)?.[1] ?? '0');
    expect(zIndex).toBeGreaterThan(10);
    // 基础规则不再自己 sticky（否则 --inline 缩略图也会吸顶）
    expect(styleRule(styles, '.preview-navigation')).not.toMatch(
      /position:\s*sticky/,
    );

    // 画布容器多个 `.preview-canvas` 规则里都不能出现 overflow: hidden
    const canvasRules = [...styles.matchAll(/\.preview-canvas\s*\{([^}]*)\}/g)];
    expect(canvasRules.length).toBeGreaterThan(0);
    for (const rule of canvasRules) {
      expect(rule[1]).not.toMatch(/overflow:\s*hidden/);
    }
    expect(styles).toMatch(/overflow-x:\s*clip/);
  });

  it('整页预览页的 weapp 外壳也不能把 sticky 吃掉', () => {
    const styles = styleBlock(previewIndex);
    const shellRule = styleRule(styles, '.preview-shell.weapp');
    expect(shellRule).not.toMatch(/overflow:\s*hidden/);
  });

  it('生效卡片/模板/换肤缩略图不传 scrollTop，保持顶部态无回归', () => {
    for (const file of [
      'views/promotion/page-design/components/effective-page-thumb.vue',
      'views/promotion/template-market/index.vue',
      'views/promotion/mall-theme/index.vue',
    ]) {
      const source = readPreviewUsage(file);
      expect(source, `${file} 不应传滚动距离`).not.toContain(':scroll-top');
    }
  });
});
