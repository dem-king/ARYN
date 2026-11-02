import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

/**
 * 预览画布「导航底色逃逸」与「图片缩放口径」回归契约。
 *
 * 背景（2026-10-28 用户反馈：管理端首页预览与小程序实机不一致、商城主题页有问题）：
 *
 * 1. **导航底色必须有包含块**。滚动切换导航把当前/目标底色画在
 *    `.preview-navigation__base` / `.preview-navigation__surface` 两层
 *    `position: absolute; inset: 0` 上。这要求 `.preview-navigation` 自身是定位
 *    元素；否则绝对定位层的包含块一路上溯到最近的定位祖先：
 *      · 生效卡片缩略图：被 `.page-thumb-canvas` 的 transform 兜住，但蓝色会铺满
 *        整个画布、盖住搜索框等未定位内容；
 *      · 商城主题页：画布外层只有 `zoom`（不建立包含块），直接逃逸到管理端内容区，
 *        把导航底色铺满整屏、盖住左侧「主题库」面板。
 *    编辑器画布 `phone-canvas` 的 `.phone-navigation` 一直是 relative，预览画布
 *    此前漏了这条，导致「画布正常、预览整屏蓝」——两处渲染面必须同时钉住。
 *
 * 2. **图片缩放口径要跟 C 端一致**。C 端 `diy-swiper-banner` 用
 *    `mode="scaleToFill"`、`diy-image` 默认 scaleToFill（拉伸铺满、不裁切）；
 *    管理端预览若用 `object-fit: cover` 会裁掉两侧，banner 上的文案/按钮位置
 *    与实机错位，运营据此调图等于白调。
 *
 * 3. **轮播指示点颜色由运营配**。C 端 uni swiper 读
 *    `indicator-color` / `indicator-active-color`；管理端 ElCarousel 只能靠
 *    CSS 变量覆盖内部按钮，缺少接线时两端颜色不一致。
 */
const appRoot = resolve(process.cwd(), 'apps/web-ele/src');
const mobileDiyRoot = resolve(
  process.cwd(),
  '../aryn-mall-uniapp/src/components/diy',
);

function read(relativePath: string) {
  return readFileSync(resolve(appRoot, relativePath), 'utf8');
}

function readMobile(relativePath: string) {
  return readFileSync(resolve(mobileDiyRoot, relativePath), 'utf8');
}

/** 取最后一个 <style> 块并剥掉注释：说明性注释会引用旧写法，断言只针对真实声明 */
function styleBlock(source: string) {
  const matches = [...source.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)];
  const block = matches.at(-1)?.[1] ?? '';
  return block.replaceAll(/\/\*[\s\S]*?\*\//g, '');
}

const previewCanvas = read(
  'views/promotion/page-preview/components/preview-canvas.vue',
);
const phoneCanvas = read(
  'views/promotion/page-designer/components/phone-canvas.vue',
);
const adminSwiperBanner = read(
  'views/promotion/page-design/components/swiper-banner/index.vue',
);
const adminImageAd = read(
  'views/promotion/page-design/components/image-ad/index.vue',
);
const mobileSwiperBanner = readMobile('diy-swiper-banner/index.vue');
const mobileImage = readMobile('diy-image/index.vue');

describe('预览画布导航层：绝对定位底色必须有包含块', () => {
  it('preview-canvas 的 .preview-navigation 是定位元素（渐显层不再逃逸）', () => {
    const styles = styleBlock(previewCanvas);
    const rule = /\.preview-navigation\s*\{([^}]*)\}/.exec(styles)?.[1] ?? '';
    expect(rule).toMatch(/position:\s*relative/);
    // 不能再是 static：sticky 分支只覆盖整页预览，缩略图/商城主题走的是基础规则
    expect(rule).not.toMatch(/position:\s*static/);
  });

  it('编辑器画布 .phone-navigation 保持同一包含块约定（两端渲染面一致）', () => {
    const styles = styleBlock(phoneCanvas);
    const rule = /\.phone-navigation\s*\{([^}]*)\}/.exec(styles)?.[1] ?? '';
    expect(rule).toMatch(/position:\s*relative/);
  });

  it('渐显两层仍是 absolute + inset:0（契约前提，不能悄悄改成 fixed/relative）', () => {
    const styles = styleBlock(previewCanvas);
    const rule =
      /\.preview-navigation__base,\s*\.preview-navigation__surface\s*\{([^}]*)\}/.exec(
        styles,
      )?.[1] ?? '';
    expect(rule).toMatch(/position:\s*absolute/);
    expect(rule).toMatch(/inset:\s*0/);
  });
});

describe('装修预览与 C 端图片缩放口径一致', () => {
  it('轮播图：C 端 scaleToFill ↔ 管理端 object-fit: fill（不裁切）', () => {
    expect(mobileSwiperBanner).toContain('mode="scaleToFill"');
    const styles = styleBlock(adminSwiperBanner);
    expect(styles).toMatch(/object-fit:\s*fill/);
    // cover 会裁掉图片两侧，与 C 端拉伸铺满不一致
    expect(styles).not.toMatch(/object-fit:\s*cover/);
  });

  it('图片广告：C 端默认 scaleToFill ↔ 管理端 ElImage fit="fill"', () => {
    expect(mobileImage).toContain('mode="scaleToFill"');
    // ElImage 的 fit 才是作用在内层 <img> 上的 object-fit；外层 .el-image 上的
    // CSS object-fit 不生效，必须显式传 prop（缺省 '' 虽等价 fill，但语义不明确）
    expect(adminImageAd).toContain('fit="fill"');
    const styles = styleBlock(adminImageAd);
    expect(styles).toMatch(/object-fit:\s*fill/);
    expect(styles).not.toMatch(/object-fit:\s*cover/);
  });

  it('轮播指示点颜色接线：消费 indicatorColor / indicatorActiveColor', () => {
    // 管理端把运营配置的颜色挂成 CSS 变量，再覆盖 ElCarousel 内部按钮
    for (const source of [adminSwiperBanner, adminImageAd]) {
      expect(source).toMatch(/indicatorColor\s*\|\|/);
      expect(source).toMatch(/indicatorActiveColor\s*\|\|/);
    }
    expect(styleBlock(adminSwiperBanner)).toMatch(/--banner-indicator/);
    expect(styleBlock(adminImageAd)).toMatch(/--image-indicator/);
  });
});
