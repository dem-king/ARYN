import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

/**
 * 装修编辑器画布「能编辑、看得见真实落点」的契约。
 *
 * 两个曾经踩过的坑，这里都用静态断言钉死：
 *
 * 1. **组件操作按钮被区块圆角裁掉**。区块 `radius > 0` 时会内联
 *    `overflow: hidden`（为了裁掉轮播图这类子组件的直角背景，见
 *    `section-style.ts` 与 C 端同构实现），而操作按钮原先定位在组件盒子
 *    右外侧 44px（`right: -44px`），溢出的部分被整块剪掉——运营选中组件
 *    后看不到删除/复制按钮，只能绕去「页面大纲」。
 *
 * 2. **内嵌页型画布只画区块、不画原生页面**。分类页(3)/商详页(2)/个人中心页(4)
 *    的装修块在 C 端都只是页面中的一段内容（`DiyPage` 传 `embedded`：不渲染
 *    自带导航栏、不撑满 100vh），且分类页的装修区在**右栏商品流内**，
 *    可用宽度只有 375 - 88 = 287px。画布若按 375px 通栏渲染，运营按画布做的
 *    满宽 banner 到实机整体变窄；种子数据把 `navigation.visible` 设为 false，
 *    更让画布看起来像几个悬空组件。
 */

const adminComponentsRoot = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-designer/components',
);
const mobileSourceRoot = resolve(process.cwd(), '../aryn-mall-uniapp/src');

function readAdmin(file: string) {
  return readFileSync(resolve(adminComponentsRoot, file), 'utf8');
}

/**
 * 取最后一个 <style> 块并剥掉注释：说明性注释里会引用旧写法
 * （如「原先定位在盒子右侧外 44px」），断言只针对真实声明。
 */
function styleBlock(source: string) {
  const matches = [...source.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)];
  const block = matches.at(-1)?.[1] ?? '';
  return block.replaceAll(/\/\*[\s\S]*?\*\//g, '');
}

function scriptBlock(source: string) {
  return /<script setup[^>]*>([\s\S]*?)<\/script>/.exec(source)?.[1] ?? '';
}

describe('page designer canvas contract', () => {
  it('keeps component actions inside the component box', () => {
    const sectionList = readAdmin('canvas-section-list.vue');
    const styles = styleBlock(sectionList);

    // 操作条必须挂在组件盒子**内部**：内联 overflow:hidden 的区块（圆角裁切）
    // 会把任何负偏移溢出的按钮剪掉。
    expect(sectionList).toContain('复制组件');
    expect(sectionList).toContain('删除组件');
    expect(styles).not.toMatch(/right:\s*-\d+px/);
    expect(styles).not.toMatch(/right:\s*-\d+%/);

    // 组件宽度跟随区块内容盒（width:100%），不能钉死 375px：
    // 区块带 margin/padding 时会溢出并被圆角裁切，画布比实机宽。
    expect(styles).toMatch(/\.canvas-component\s*\{[^}]*width:\s*100%/);
    expect(styles).not.toMatch(/\.canvas-component\s*\{[^}]*width:\s*375px/);
  });

  it('renders the native page shell only for embedded page types', () => {
    const canvas = readAdmin('phone-canvas.vue');
    const shell = readAdmin('canvas-page-shell.vue');

    // 内嵌页型（商详 2 / 分类 3 / 个人中心 4）默认套骨架
    expect(canvas).toContain("['2', '3', '4']");
    expect(canvas).toContain('CanvasPageShell');
    expect(canvas).toContain('showPageShell');

    // 骨架必须覆盖三个内嵌页型，且给出各自的原生结构
    for (const type of ["'2'", "'3'", "'4'"]) {
      expect(shell).toContain(`pageType === ${type}`);
    }
    expect(shell).toContain('data-shell="category"');
    expect(shell).toContain('data-shell="usercenter"');
    expect(shell).toContain('data-shell="detail"');
  });

  it('draws the decoration navigation bar only for full-page DIY', () => {
    const canvas = readAdmin('phone-canvas.vue');
    const script = scriptBlock(canvas);

    // C 端 embedded 模式不渲染装修自带导航栏；画布必须同构，
    // 否则运营在画布里看到导航栏、保存后到实机却没有。
    expect(script).toMatch(
      /showNavigation\s*=\s*computed\(\s*\(\)\s*=>\s*!isEmbeddedPage\.value\s*&&\s*props\.page\.navigation\.visible/,
    );
  });

  it('matches the category page right-column width against the mobile rail', () => {
    const shell = readAdmin('canvas-page-shell.vue');
    const categoryPage = readFileSync(
      resolve(mobileSourceRoot, 'pages/product/category/index.vue'),
      'utf8',
    );

    // 左栏 rail 宽度是本契约的关键数值：C 端 176rpx（= 88px），
    // 右栏可用宽度 = 375 - 88 = 287px。任一端改动都必须同步另一端。
    expect(categoryPage).toContain('width: 176rpx');
    expect(shell).toContain('width: 88px');

    // 排序条 88rpx = 44px，同样两端对齐
    expect(categoryPage).toContain('height: 88rpx');
    expect(shell).toContain('height: 44px');
  });

  it('keeps the decoration slot at the mobile mount point', () => {
    const categoryPage = readFileSync(
      resolve(mobileSourceRoot, 'pages/product/category/index.vue'),
      'utf8',
    );

    // C 端装修区嵌在右栏商品流顶部（goods-list-panel 的 #banner 插槽），
    // 骨架的插槽位置必须与之一致：在右栏内、排序条之前。
    expect(categoryPage).toContain('<template #banner>');
    expect(categoryPage).toContain('<goods-list-panel');
  });
});
