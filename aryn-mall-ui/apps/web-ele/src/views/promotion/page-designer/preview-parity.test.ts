import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

/**
 * 装修组件的「编辑器预览 / 小程序实机」视觉一致性契约。
 *
 * 背景：两端是两套独立实现（管理端 `page-design/components/**` 与
 * C 端 `aryn-mall-uniapp/src/components/diy/**`），历史上长期各自演进，
 * 结果运营在编辑器里看到的样式与小程序实际渲染对不上（字号、配色、
 * 卡片结构全都不同），运营据此调的参数等于白调。
 *
 * 换算口径：小程序屏宽在 rpx 下恒为 750，编辑器画布正好 375px，
 * 因此 **1rpx = 0.5px**，两端数值应严格满足该倍数关系。
 *
 * 本测试只锁「会被用户直接看到、且曾经漂移过」的高信号数值（主色、
 * 标题/价格字号、卡片圆角与内边距）。新增装修组件或改动任一端样式时，
 * 若这里失败，说明两端又不同步了——请同时改两端而不是放宽断言。
 */

const adminComponentsRoot = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-design/components',
);
const adminSchemaRoot = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-designer/schema',
);
const mobileDiyRoot = resolve(
  process.cwd(),
  '../aryn-mall-uniapp/src/components/diy',
);
const mobileStylesRoot = resolve(
  process.cwd(),
  '../aryn-mall-uniapp/src/styles',
);

function readAdmin(type: string) {
  return readFileSync(resolve(adminComponentsRoot, type, 'index.vue'), 'utf8');
}

function readMobile(dir: string) {
  return readFileSync(resolve(mobileDiyRoot, dir, 'index.vue'), 'utf8');
}

/** 取出组件文件中最后一个 <style> 块，忽略 <script> 里的字符串 */
function styleBlock(source: string) {
  const matches = [...source.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)];
  return matches.at(-1)?.[1] ?? '';
}

/**
 * 按选择器取「叶子声明块」：只返回该选择器自身、首个嵌套规则之前的声明。
 * SCSS 里常见 `.a { padding: 1px; .b { … } }`，这里要的是 `.a` 自己的声明。
 */
function declarations(style: string, selector: string): string {
  const pattern = new RegExp(
    `${selector.replaceAll(/[.*+?^${}()|[\]\\]/g, String.raw`\$&`)}\\s*\\{`,
    'g',
  );
  let match = pattern.exec(style);
  while (match !== null) {
    const start = match.index + match[0].length;
    let index = start;
    let depth = 1;
    let firstNested = -1;
    while (index < style.length && depth > 0) {
      const char = style[index];
      if (char === '{') {
        if (depth === 1 && firstNested < 0) firstNested = index;
        depth += 1;
      } else if (char === '}') {
        depth -= 1;
        if (depth === 0) break;
      }
      index += 1;
    }
    const leafEnd = firstNested >= 0 ? firstNested : index;
    const body = style.slice(start, leafEnd).trim();
    if (body) return `${body};`;
    match = pattern.exec(style);
  }
  return '';
}

/** 从声明块中读某个属性的值 */
function value(block: string, property: string): string {
  const match = new RegExp(`(?:^|;|\\s)${property}\\s*:\\s*([^;]+);`).exec(
    block.replaceAll('\n', ' '),
  );
  return match?.[1]?.trim() ?? '';
}

/** 背景：两端可能分别写成 background / background-color，统一取值比较 */
function backgroundColorIsSame(blockA: string, blockB: string) {
  const read = (block: string) =>
    value(block, 'background-color') || value(block, 'background');
  return read(blockA) === read(blockB);
}

/** rpx → 编辑器画布 px（屏宽 750rpx 对应 375px） */
function rpxToPx(rpx: number) {
  return rpx / 2;
}

describe('装修预览与小程序视觉一致性', () => {
  it('商品横滑：主色、标题字号与胶囊副标题两端一致', () => {
    const admin = styleBlock(readAdmin('goods-scroll'));
    const mobile = styleBlock(readMobile('diy-goods-scroll'));

    // 标题
    expect(value(declarations(admin, '.gs-title'), 'font-size')).toBe(
      `${rpxToPx(32)}px`,
    );
    expect(value(declarations(mobile, '.gs-title'), 'font-size')).toBe('32rpx');
    expect(value(declarations(admin, '.gs-title'), 'color')).toBe(
      value(declarations(mobile, '.gs-title'), 'color'),
    );

    // 副标题做成粉底胶囊（曾两端结构不同：一端纯文字、一端胶囊）
    expect(
      backgroundColorIsSame(
        declarations(admin, '.gs-subtitle'),
        declarations(mobile, '.gs-subtitle'),
      ),
    ).toBe(true);
    // 胶囊圆角：两端都必须是「全圆」，表达方式（999px / 999rpx）不同但效果一致
    expect(value(declarations(admin, '.gs-subtitle'), 'border-radius')).toBe(
      '999px',
    );
    expect(value(declarations(mobile, '.gs-subtitle'), 'border-radius')).toBe(
      '999rpx',
    );

    // 价格主色（曾为预览 #dc2626 / 小程序 #e5484d）
    expect(value(declarations(admin, '.gs-price'), 'color')).toBe(
      value(declarations(mobile, '.gs-price'), 'color'),
    );

    // 卡片：圆角 16rpx、1rpx 细边框
    expect(value(declarations(admin, '.gs-card'), 'border-radius')).toBe(
      `${rpxToPx(16)}px`,
    );
    expect(value(declarations(mobile, '.gs-card'), 'border-radius')).toBe(
      '16rpx',
    );
    expect(value(declarations(admin, '.gs-card'), 'border')).toBe(
      `${rpxToPx(1)}px solid #eef0f3`,
    );
    expect(value(declarations(mobile, '.gs-card'), 'border')).toBe(
      '1rpx solid #eef0f3',
    );
  });

  it('商品分组：价格主色与标题字号两端一致', () => {
    const admin = styleBlock(readAdmin('goods-group'));
    const mobile = styleBlock(readMobile('diy-goods-group'));

    expect(value(declarations(admin, '.goods-price'), 'color')).toBe(
      value(declarations(mobile, '.goods-price'), 'color'),
    );
    // 分组标题（曾预览放在框架 header、小程序放在内容区）
    expect(value(declarations(admin, '.section-title'), 'font-size')).toBe(
      `${rpxToPx(30)}px`,
    );
    expect(value(declarations(mobile, '.section-title'), 'font-size')).toBe(
      '30rpx',
    );
    expect(value(declarations(admin, '.section-title'), 'color')).toBe(
      value(declarations(mobile, '.section-title'), 'color'),
    );
    // 商品名与卡片边框
    expect(value(declarations(admin, '.goods-name'), 'color')).toBe(
      value(declarations(mobile, '.goods-name'), 'color'),
    );
    expect(value(declarations(admin, '.goods-card'), 'border-radius')).toBe(
      `${rpxToPx(8)}px`,
    );
    expect(value(declarations(mobile, '.goods-card'), 'border-radius')).toBe(
      '8rpx',
    );
  });

  it('公告：文字色由配置直出，行高与字号两端一致', () => {
    const admin = styleBlock(readAdmin('notice'));
    expect(value(declarations(admin, '.notice-text'), 'font-size')).toBe(
      '12px',
    );
    expect(value(declarations(admin, '.notice-text'), 'line-height')).toBe(
      '18px',
    );
    // 小程序用 wd-notice-bar 渲染，其默认字号/行高即 12px / 18px
    expect(readMobile('diy-notice')).toContain('wd-notice-bar');
    // 曾用 var(...) 把 color 包起来，浏览器拿不到该变量导致图标颜色失效；
    // 现在应把 color 直接作为颜色值使用
    const noticeAdmin = readAdmin('notice');
    expect(noticeAdmin).not.toMatch(/color:\s*`var\(/);
    expect(noticeAdmin).toMatch(/color:\s*titleColor/);
  });

  it.each([
    ['goods-ranking', 'diy-goods-ranking'],
    ['limited-activity', 'diy-limited-activity'],
  ])('%s：区块标题与小程序同值', (adminType, mobileDir) => {
    const admin = styleBlock(readAdmin(adminType));
    const mobile = styleBlock(readMobile(mobileDir));

    expect(value(declarations(admin, '.section-title'), 'font-size')).toBe(
      `${rpxToPx(30)}px`,
    );
    expect(value(declarations(mobile, '.section-title'), 'font-size')).toBe(
      '30rpx',
    );
    expect(value(declarations(admin, '.section-title'), 'color')).toBe(
      value(declarations(mobile, '.section-title'), 'color'),
    );
  });

  it('商品排行：价格与图片尺寸两端一致', () => {
    const admin = styleBlock(readAdmin('goods-ranking'));
    const mobile = styleBlock(readMobile('diy-goods-ranking'));

    expect(value(declarations(admin, '.ranking-price'), 'color')).toBe(
      value(declarations(mobile, '.ranking-price'), 'color'),
    );
    // 图片 104rpx 方块
    expect(value(declarations(admin, '.ranking-image'), 'width')).toBe(
      `${rpxToPx(104)}px`,
    );
    expect(value(declarations(mobile, '.ranking-image'), 'width')).toBe(
      '104rpx',
    );
  });

  it('拼团：用的不是「限时价」标签，而是与小程序一致的倒计时文案', () => {
    const admin = readAdmin('limited-activity');
    // 曾预览渲染 ElTag「限时价」+ 绝对时间，小程序渲染「剩余 HH:mm:ss」
    expect(admin).not.toContain('限时价');
    expect(admin).toContain('剩余');
    expect(readMobile('diy-limited-activity')).toContain('剩余');
  });

  it('营销入口 / 店铺信息：图标尺寸与主色两端一致', () => {
    const adminEntry = styleBlock(readAdmin('marketing-entry'));
    const mobileEntry = styleBlock(readMobile('diy-marketing-entry'));
    expect(value(declarations(adminEntry, '.entry-icon'), 'width')).toBe(
      `${rpxToPx(84)}px`,
    );
    expect(value(declarations(mobileEntry, '.entry-icon'), 'width')).toBe(
      '84rpx',
    );
    expect(
      backgroundColorIsSame(
        declarations(adminEntry, '.entry-icon'),
        declarations(mobileEntry, '.entry-icon'),
      ),
    ).toBe(true);

    const adminShop = styleBlock(readAdmin('shop-info'));
    const mobileShop = styleBlock(readMobile('diy-shop-info'));
    expect(value(declarations(adminShop, '.shop-logo'), 'width')).toBe(
      `${rpxToPx(96)}px`,
    );
    expect(value(declarations(mobileShop, '.shop-logo'), 'width')).toBe(
      '96rpx',
    );
    expect(value(declarations(adminShop, '.shop-name'), 'color')).toBe(
      value(declarations(mobileShop, '.shop-name'), 'color'),
    );
  });

  it('倒计时：单行标题 + 数值，与小程序结构一致', () => {
    const admin = styleBlock(readAdmin('countdown'));
    const mobile = styleBlock(readMobile('diy-countdown'));

    // 曾预览是 4 格深色方块（天/时/分/秒），小程序是单行文案
    expect(admin).not.toContain('countdown-segment');
    expect(value(declarations(admin, '.countdown-value'), 'color')).toBe(
      value(declarations(mobile, '.countdown-value'), 'color'),
    );
    expect(value(declarations(mobile, '.countdown-value'), 'font-family')).toBe(
      'monospace',
    );
    expect(value(declarations(admin, '.countdown-value'), 'font-family')).toBe(
      'monospace',
    );
  });

  it('搜索栏：commonStyle 挂根节点、固定留白挂内层行容器，输入区高度两端一致', () => {
    const admin = readAdmin('search-bar');
    const mobile = readMobile('diy-search-bar');
    const adminStyle = styleBlock(admin);
    const mobileStyle = styleBlock(mobile);

    // 通用样式（外边距/背景/圆角）两端都由根节点消费；固定留白必须挂内层行容器
    // —— 根节点 padding 会被 commonStyle 内联零值覆盖（仓库统一口径）
    expect(admin).toContain(':style="dynamicStyles"');
    expect(mobile).toContain(':style="dynamicStyles"');
    expect(mobile).toContain('useDiyStyle');
    expect(value(declarations(adminStyle, '.search-bar-row'), 'padding')).toBe(
      '6px 12px',
    );
    expect(value(declarations(mobileStyle, '.search-bar-row'), 'padding')).toBe(
      '6px 12px',
    );
    expect(value(declarations(adminStyle, '.search-bar-inner'), 'height')).toBe(
      '36px',
    );
    expect(
      value(declarations(mobileStyle, '.search-bar-inner'), 'height'),
    ).toBe('36px');
    // 小程序搜索栏右侧有消息铃铛，预览必须有对应占位
    expect(admin).toContain('search-bar-bell');
  });

  it('分类导航 / 标题文本：容器内边距与对齐逻辑两端一致', () => {
    // 分类导航：容器上下 12px、单元上下 8px、文字单行截断
    const adminNav = styleBlock(readAdmin('category-nav'));
    const mobileNav = styleBlock(readMobile('diy-category-nav'));
    expect(value(declarations(adminNav, '.category-nav-box'), 'padding')).toBe(
      '12px 0',
    );
    expect(value(declarations(mobileNav, '.category-nav'), 'padding')).toBe(
      '12px 0',
    );
    expect(value(declarations(adminNav, '.nav-text'), 'font-size')).toBe(
      '12px',
    );
    expect(
      value(declarations(mobileNav, '.category-nav-text'), 'font-size'),
    ).toBe('12px');

    // 标题文本：左对齐 / 居中两态与 more-btn 绝对定位（曾预览只有 space-between）
    const adminTitle = styleBlock(readAdmin('title-text'));
    const mobileTitle = styleBlock(readMobile('diy-titletext'));
    for (const cls of ['.title-left', '.title-center']) {
      expect(adminTitle).toContain(cls);
      expect(mobileTitle).toContain(cls);
    }
    expect(value(declarations(adminTitle, '.title'), 'position')).toBe(
      'relative',
    );
    expect(value(declarations(mobileTitle, '.title'), 'position')).toBe(
      'relative',
    );
  });

  it('领券：票面结构（票根缺口 / 券类型胶囊 / 状态带）两端一致', () => {
    const admin = readAdmin('coupon-receive');
    const mobile = readMobile('diy-coupon-receive');
    // 曾预览缺少中缝虚线，两端视觉明显不同；改为票面后仍要求结构同名同量
    for (const marker of [
      'coupon-face',
      'coupon-notch',
      'coupon-pill',
      'coupon-band',
    ]) {
      expect(admin).toContain(marker);
      expect(mobile).toContain(marker);
    }

    const adminStyle = styleBlock(admin);
    const mobileStyle = styleBlock(mobile);
    // 金额字号：22px ↔ 44rpx
    expect(value(declarations(adminStyle, '.coupon-value'), 'font-size')).toBe(
      `${rpxToPx(44)}px`,
    );
    expect(value(declarations(mobileStyle, '.coupon-value'), 'font-size')).toBe(
      '44rpx',
    );
    // 描边厚度 3px ↔ 6rpx、票根缺口 9px ↔ 18rpx、状态带高度 26px ↔ 52rpx
    // （`.coupon-item` 在 list-style 分支里也出现，取声明块会取错，故按原文断言）
    expect(adminStyle).toContain('padding: 3px;');
    expect(mobileStyle).toContain('padding: $coupon-rim-width;');
    expect(value(declarations(adminStyle, '.coupon-notch'), 'width')).toBe(
      '9px',
    );
    expect(value(declarations(mobileStyle, '.coupon-notch'), 'width')).toBe(
      '$coupon-notch-size',
    );
    expect(value(declarations(adminStyle, '.coupon-band'), 'height')).toBe(
      '26px',
    );
    expect(value(declarations(mobileStyle, '.coupon-band'), 'height')).toBe(
      '$coupon-band-height',
    );
    // 券类型胶囊配色两端同源：小程序用 SCSS 变量，故比对预览写死的色值
    // 是否等于 coupon-ticket.scss 里该变量的值（颜色不一致会立刻失败）
    const pillColor =
      value(declarations(adminStyle, '.coupon-pill'), 'background-color') ||
      value(declarations(adminStyle, '.coupon-pill'), 'background');
    const tokens = readFileSync(
      resolve(mobileStylesRoot, 'coupon-ticket.scss'),
      'utf8',
    );
    expect(pillColor).toMatch(/^#[0-9a-f]{6}$/i);
    expect(tokens.toLowerCase()).toContain(
      `$coupon-pill-bg: ${pillColor.toLowerCase()};`,
    );
  });

  it('领券：小程序票面变量取值与预览换算口径一致（1rpx = 0.5px）', () => {
    // 小程序侧用 SCSS 变量，数值在 coupon-ticket.scss；这里锁住变量值，
    // 避免只改一边导致两端悄悄错位（预览是写死的 px，改不动就被这条挡住）
    const tokens = readFileSync(
      resolve(mobileStylesRoot, 'coupon-ticket.scss'),
      'utf8',
    );
    expect(tokens).toContain('$coupon-rim-width: 6rpx;');
    expect(tokens).toContain('$coupon-notch-size: 18rpx;');
    expect(tokens).toContain('$coupon-band-height: 52rpx;');
    expect(tokens).toContain('$coupon-radius: 20rpx;');
  });

  it('轮播图：commonStyle 与私有圆角分层，12px 兜底留白两端一致', () => {
    const admin = readAdmin('swiper-banner');
    const mobile = readMobile('diy-swiper-banner');

    // 根节点消费通用样式（边距/背景/圆角，圆角 > 0 裁切）；老数据缺 commonStyle
    // 时不绑内联样式，保留写死的 12px 左右留白兜底（两端同口径）
    expect(admin).toContain(':style="dynamicStyles"');
    expect(mobile).toContain(':style="dynamicStyles"');
    expect(mobile).toContain('getCommonStyle');
    expect(admin).toContain('commonStyleRadiusOverflow');
    expect(admin).toMatch(
      /if \(!props\.showData\.commonStyle\)\s*return \{\};/,
    );
    expect(mobile).toContain(
      'return commonStyle ? getCommonStyle(commonStyle) : {}',
    );
    expect(
      value(declarations(styleBlock(admin), '.swiper-banner-box'), 'margin'),
    ).toBe('0 12px');
    expect(
      value(declarations(styleBlock(mobile), '.swiper-banner'), 'margin'),
    ).toBe('0 12px');
    // 私有圆角继续作用在内容层（图片裁切），不被通用圆角取代
    expect(admin).toContain('showData.borderRadius');
    expect(mobile).toContain('props.showData.borderRadius');
  });

  it('补给单：空态占位引导卡两端一致（emptyStrategy=placeholder）', () => {
    const admin = readAdmin('replenish-card');
    const mobile = readMobile('diy-replenish-card');
    const adminStyle = styleBlock(admin);
    const mobileStyle = styleBlock(mobile);

    // 两端都按策略切换形态，占位卡结构与两个入口文案一致
    for (const source of [admin, mobile]) {
      expect(source).toContain("emptyStrategy === 'placeholder'");
      expect(source).toContain('guide-inner');
      expect(source).toContain('导入清单下单');
      expect(source).toContain('发起补给单');
    }

    // 1rpx = 0.5px：内边距 / 圆角 / 标题字号 / 按钮高度逐值换算
    expect(value(declarations(adminStyle, '.guide-inner'), 'padding')).toBe(
      '12px',
    );
    expect(value(declarations(mobileStyle, '.guide-inner'), 'padding')).toBe(
      '24rpx',
    );
    expect(
      value(declarations(adminStyle, '.guide-inner'), 'border-radius'),
    ).toBe('12px');
    expect(
      value(declarations(mobileStyle, '.guide-inner'), 'border-radius'),
    ).toBe('24rpx');
    expect(value(declarations(adminStyle, '.guide-title'), 'font-size')).toBe(
      '14px',
    );
    expect(value(declarations(mobileStyle, '.guide-title'), 'font-size')).toBe(
      '28rpx',
    );
    expect(value(declarations(adminStyle, '.guide-btn'), 'height')).toBe(
      '32px',
    );
    expect(value(declarations(mobileStyle, '.guide-btn'), 'height')).toBe(
      '64rpx',
    );
  });

  it('图文导航（金刚区）：分页高度自适应公式两端一致', () => {
    // 两端独立实现过一次 pagerHeight，曾只改一边导致预览与实机高度差一截；
    // 这里锁住「按当前页实际行数收缩高度」的公式与高度过渡同时存在于两端
    const admin = readAdmin('tab-nav');
    const mobile = readMobile('diy-tabnav');
    for (const source of [admin, mobile]) {
      expect(source).toContain('actualRows');
      expect(source).toMatch(
        /Math\.min\(\s*pageRows\.value\s*,\s*Math\.ceil\(\s*page\.length\s*\/\s*showNum\.value\s*\)/,
      );
      expect(source).toContain('transition: height 0.3s ease');
    }
  });

  it('图片魔方：格距与格位高度换算两端一致（1rpx = 0.5px）', () => {
    // 预览画布渲染在共享的 extension-preview.vue 里（组件壳只是转发）
    const admin = readFileSync(
      resolve(adminComponentsRoot, 'extension/extension-preview.vue'),
      'utf8',
    );
    const mobile = readMobile('diy-image-cube');

    expect(value(declarations(admin, '.preview-cube'), 'gap')).toBe('4px');
    expect(value(declarations(mobile, '.cube-grid'), 'gap')).toBe('8rpx');
    // 格位高度：大格 376rpx↔188px、小格 184rpx↔92px、三分格 120rpx↔60px
    expect(admin).toContain('188');
    expect(admin).toContain('92');
    expect(admin).toContain('60');
    expect(mobile).toContain('376');
    expect(mobile).toContain('184');
    expect(mobile).toContain('120');
    // 图片裁切铺满格位，两端同口径
    expect(admin).toContain('object-fit: cover');
    expect(mobile).toContain('aspectFill');
    // 通用样式（圆角/背景/边距）挂在两端根节点；圆角 > 0 时的裁切守卫见下一条契约
    expect(admin).toContain('frameStyle');
    expect(mobile).toContain(':style="dynamicStyles"');
  });

  it('商品推荐：横滑卡尺寸与留白两端一致（1rpx = 0.5px）', () => {
    const admin = readFileSync(
      resolve(adminComponentsRoot, 'extension/goods-recommend/index.vue'),
      'utf8',
    );
    const mobile = readMobile('diy-goods-recommend');

    expect(value(declarations(admin, '.gr-track'), 'gap')).toBe('8px');
    expect(value(declarations(mobile, '.gr-track'), 'gap')).toBe('16rpx');
    expect(value(declarations(admin, '.gr-card'), 'flex')).toBe('0 0 100px');
    expect(value(declarations(mobile, '.gr-card'), 'flex')).toBe('0 0 200rpx');
    expect(value(declarations(admin, '.gr-pic'), 'height')).toBe('100px');
    expect(value(declarations(mobile, '.gr-pic'), 'height')).toBe('200rpx');
    expect(value(declarations(admin, '.gr-card'), 'border-radius')).toBe('8px');
    expect(value(declarations(mobile, '.gr-card'), 'border-radius')).toBe(
      '16rpx',
    );
    // 组件留白：画布壳 10px ↔ 小程序内层 20rpx。实机内容曾整块顶到屏幕边缘——
    // 留白挂在根节点时会被 commonStyle 的内联零值覆盖，必须留在 .gr-inner
    expect(value(declarations(admin, '.gr-inner'), 'padding')).toBe('10px');
    expect(value(declarations(mobile, '.gr-inner'), 'padding')).toBe('20rpx');
  });

  it('商品瀑布流：卡片圆角与留白两端一致（1rpx = 0.5px）', () => {
    const admin = readFileSync(
      resolve(adminComponentsRoot, 'extension/goods-waterfall/index.vue'),
      'utf8',
    );
    const mobile = readMobile('diy-goods-waterfall');

    expect(value(declarations(admin, '.waterfall-columns'), 'column-gap')).toBe(
      '8px',
    );
    expect(
      value(declarations(mobile, '.waterfall-columns'), 'column-gap'),
    ).toBe('16rpx');
    expect(value(declarations(admin, '.waterfall-card'), 'border-radius')).toBe(
      '6px',
    );
    expect(
      value(declarations(mobile, '.waterfall-card'), 'border-radius'),
    ).toBe('12rpx');
    // 留白同样挂内层，防止 commonStyle 内联零值把实机内容顶到屏幕边缘
    expect(value(declarations(admin, '.waterfall-inner'), 'padding')).toBe(
      '10px',
    );
    expect(value(declarations(mobile, '.waterfall-inner'), 'padding')).toBe(
      '20rpx',
    );
  });

  it('多Tab商品流：Tab 栏高度、卡片圆角与留白两端一致', () => {
    const admin = readFileSync(
      resolve(adminComponentsRoot, 'extension/goods-tabs/index.vue'),
      'utf8',
    );
    const mobile = readMobile('diy-goods-tabs');

    expect(value(declarations(admin, '.goods-tabs-bar'), 'height')).toBe(
      '44px',
    );
    expect(value(declarations(mobile, '.goods-tabs__bar'), 'height')).toBe(
      '88rpx',
    );
    expect(
      value(declarations(admin, '.goods-tabs-card'), 'border-radius'),
    ).toBe('8px');
    expect(
      value(declarations(mobile, '.goods-tabs__card'), 'border-radius'),
    ).toBe('16rpx');
    // 留白挂内层，避免 commonStyle 内联零值覆盖
    expect(value(declarations(admin, '.goods-tabs-inner'), 'padding')).toBe(
      '8px 10px 10px',
    );
    expect(value(declarations(mobile, '.goods-tabs__inner'), 'padding')).toBe(
      '16rpx 20rpx 20rpx',
    );
  });

  it('extension 共享画布：通用样式框承接圆角/背景，内容留白挂内层 10px', () => {
    // 六个共用 extension-preview.vue 的组件，画布样式由 .preview-frame 内联样式
    // 承载（commonStyle 的圆角/背景/边距，圆角 > 0 时裁切子内容——否则运营调了
    // 圆角画布上看不到效果）；内容留白挂在 .preview-inner（对应 C 端内层 20rpx）。
    // 底部导航/图片魔方在 C 端满宽贴边，画布同样不加假留白（--flush）；
    // 商品瀑布流与商品推荐为真实数据渲染，各自 .waterfall-inner/.gr-inner 承接留白
    const admin = readFileSync(
      resolve(adminComponentsRoot, 'extension/extension-preview.vue'),
      'utf8',
    );
    expect(admin).toContain('preview-frame');
    expect(admin).toContain('frameStyle');
    expect(value(declarations(admin, '.preview-inner'), 'padding')).toBe(
      '10px',
    );
    expect(value(declarations(admin, '.preview-inner--flush'), 'padding')).toBe(
      '0',
    );
  });

  it('通用样式圆角：两端都按「圆角 > 0 裁切子内容」守卫（画布框 / C 端同源）', () => {
    // 贴满根节点的图片/色块直角会盖住圆角弧线，两端都必须裁切，否则运营设置的
    // 圆角（图片魔方等纯图片组件最直观）在任一渲染面「设置了不生效」。
    // C 端守卫在 utils/common-style.ts 的 getCommonStyle；管理端画布框与其
    // 孪生实现 common-style-clip.ts，共享同一判断。
    const adminClip = readFileSync(
      resolve(adminComponentsRoot, 'common/common-style-clip.ts'),
      'utf8',
    );
    const mobileCommonStyle = readFileSync(
      resolve(mobileDiyRoot, '../../utils/common-style.ts'),
      'utf8',
    );

    for (const source of [adminClip, mobileCommonStyle]) {
      expect(source).toMatch(/overflow[^\n]*'hidden'/);
      expect(source).toMatch(/> 0/);
    }
    // C 端不裁切时不得输出 overflow（四角全 0 的既有布局不被改变）
    expect(mobileCommonStyle).toContain('if (hasRadius)');
  });

  it.each([
    ['diy-goods-waterfall', '.waterfall-inner'],
    ['diy-coupon-combo', '.combo-inner'],
    ['diy-member-benefits', '.benefits-inner'],
    ['diy-service-promise', '.promise-inner'],
    ['diy-video-live', '.videolive-inner'],
  ] as const)(
    'extension 组件 %s 的留白挂在内层（防 commonStyle 内联零值覆盖）',
    (dir, selector) => {
      // 与商品推荐同根因：留白挂根节点会被装修 commonStyle 的 padding-*:0
      // 内联样式整块盖掉，六个 extension 组件曾因此在实机顶到屏幕边缘
      const mobile = readMobile(dir);
      expect(value(declarations(mobile, selector), 'padding')).toBe('20rpx');
    },
  );

  it('区块容器：两端都按 px 输出留白与圆角', () => {
    // 管理端画布与草稿预览共用 buildSectionStyle，这里锁住它本身；
    // C 端渲染器 diy/index.vue 是第二套实现，必须逐项对齐。
    const adminSectionStyle = readFileSync(
      resolve(adminSchemaRoot, 'section-style.ts'),
      'utf8',
    );
    const mobileRenderer = readFileSync(
      resolve(mobileDiyRoot, 'index.vue'),
      'utf8',
    );

    for (const source of [adminSectionStyle, mobileRenderer]) {
      // paddingY 曾在小程序侧被当作 rpx 渲染，与管理端 px 相差 2 倍，
      // 运营在编辑器调的留白到实机就变了；两端必须统一为 px。
      // 两端变量名前缀不同（style. / section.style.），用正则同时接受。
      expect(source).not.toMatch(/paddingY\}rpx/);
      for (const field of [
        'paddingX',
        'paddingY',
        'marginX',
        'marginY',
        'radius',
      ]) {
        expect(source).toMatch(
          new RegExp(String.raw`\$\{(?:section\.)?style\.${field}\}px`),
        );
      }
      // 圆角必须同时裁掉子组件溢出，否则四角露出直角背景；
      // 但横滑区块不能裁（内联 overflow 会盖掉 overflow-x 滚动），两端都要有这层守卫
      expect(source).toContain("overflow = 'hidden'");
      expect(source).toMatch(/if \(!.*horizontalScroll\)/);
    }

    // 吸顶偏移存在平台差异：画布里的页面导航随内容滚动，管理端停在画布顶部即可；
    // C 端整页 DIY 的 wd-navbar 是 fixed 覆盖层，必须让出状态栏+导航高度，
    // 否则区块被吸到导航栏底下、顶部内容（品牌区搜索栏）被永久遮住。
    expect(adminSectionStyle).toMatch(/result\.top = '0'/);
    expect(mobileRenderer).toMatch(/style\.top = `\$\{stickyTop\.value\}px`/);
  });

  it('区块背景渐变：两端解析同源，背景图优先级一致', () => {
    // 运营在「区块设置」把背景色切成渐变后，管理端画布 / 草稿预览 / C 端实机
    // 三处渲染面必须输出同一个 linear-gradient。纯解析函数在两端各留一份拷贝
    // （避免跨仓 import 别名问题），用 SECTION-GRADIENT 标记圈出函数体，
    // 去掉空白与分号后必须逐字一致；改方向白名单或色值校验必须同时改两端。
    const extractGradientCore = (source: string, label: string) => {
      const match =
        /\/\* SECTION-GRADIENT-BEGIN \*\/([\s\S]*?)\/\* SECTION-GRADIENT-END \*\//.exec(
          source,
        );
      expect(match, `${label} 缺少 SECTION-GRADIENT 标记`).toBeTruthy();
      return (match?.[1] ?? '').replaceAll(/[\s;]/g, '');
    };
    const adminSectionStyle = readFileSync(
      resolve(adminSchemaRoot, 'section-style.ts'),
      'utf8',
    );
    const mobileRenderer = readFileSync(
      resolve(mobileDiyRoot, 'index.vue'),
      'utf8',
    );
    const mobileGradient = readFileSync(
      resolve(mobileDiyRoot, 'schema/section-gradient.ts'),
      'utf8',
    );

    expect(
      extractGradientCore(adminSectionStyle, '管理端 section-style.ts'),
    ).toBe(extractGradientCore(mobileGradient, 'C 端 section-gradient.ts'));

    // 两端渲染器都必须消费同一解析函数，并且 backgroundImage 优先级最高
    for (const source of [adminSectionStyle, mobileRenderer]) {
      expect(source).toContain('buildSectionGradient');
      expect(source).toContain('backgroundGradient');
      expect(source).toMatch(
        /hasBackgroundImage[\s\S]{0,200}buildSectionGradient/,
      );
    }
  });

  it('页面主题继承：两端有效主题解析同源，三处渲染面统一消费', () => {
    // 页面背景/导航颜色的主题继承是三套实现（编辑画布 / 草稿预览 / C 端 diy），
    // 各自解析就会出现「管理端已换主题色、实机仍是模板写死的旧品牌色」。
    // 纯解析函数在两端各留一份拷贝（避免跨仓 import 别名问题），
    // 用 EFFECTIVE-THEME 标记圈出函数体，去掉空白与分号后必须逐字一致。
    const extractCore = (source: string, label: string) => {
      const match =
        /\/\* EFFECTIVE-THEME-BEGIN \*\/([\s\S]*?)\/\* EFFECTIVE-THEME-END \*\//.exec(
          source,
        );
      expect(match, `${label} 缺少 EFFECTIVE-THEME 标记`).toBeTruthy();
      return (match?.[1] ?? '').replaceAll(/[\s;]/g, '');
    };
    const adminCore = extractCore(
      readFileSync(resolve(adminSchemaRoot, 'effective-theme.ts'), 'utf8'),
      '管理端 effective-theme.ts',
    );
    const mobileCore = extractCore(
      readFileSync(resolve(mobileDiyRoot, 'schema/effective-theme.ts'), 'utf8'),
      'C 端 effective-theme.ts',
    );
    expect(adminCore).toBe(mobileCore);

    // 三处渲染面都必须走同一解析器，并消费解析出的导航配色
    // （页面背景若绕过解析器直接读 page，模板写死的品牌色会遮住商城默认主题；
    // 导航色在此基础上再经 navigation-style 的「跟随首个区块」二次解析）
    for (const source of [
      readFileSync(resolve(mobileDiyRoot, 'index.vue'), 'utf8'),
      readFileSync(
        resolve(adminSchemaRoot, '../components/phone-canvas.vue'),
        'utf8',
      ),
      readFileSync(
        resolve(
          adminSchemaRoot,
          '../../page-preview/components/preview-canvas.vue',
        ),
        'utf8',
      ),
    ]) {
      expect(source).toContain('resolveEffectivePageTheme');
      expect(source).toContain('resolveNavigationAppearance');
    }
  });

  it('导航栏跟随首个区块：两端解析同源，三处渲染面统一消费', () => {
    // 导航栏背景与首区块背景原本是两块独立配置：运营想让导航和首屏区块连成
    // 一条渐变，只能手动把导航色设成区块渐变起始色，改一次区块就要再对一次色。
    // 「跟随首个区块」把这段取色规则抽成纯函数，两端各留一份拷贝（避免跨仓
    // import 别名问题），用 NAVIGATION-FOLLOW 标记圈出函数体，去掉空白与分号
    // 后必须逐字一致；改取色口径必须同时改两端。
    const extractCore = (source: string, label: string) => {
      const match =
        /\/\* NAVIGATION-FOLLOW-BEGIN \*\/([\s\S]*?)\/\* NAVIGATION-FOLLOW-END \*\//.exec(
          source,
        );
      expect(match, `${label} 缺少 NAVIGATION-FOLLOW 标记`).toBeTruthy();
      return (match?.[1] ?? '').replaceAll(/[\s;]/g, '');
    };
    const adminCore = extractCore(
      readFileSync(resolve(adminSchemaRoot, 'navigation-style.ts'), 'utf8'),
      '管理端 navigation-style.ts',
    );
    const mobileCore = extractCore(
      readFileSync(
        resolve(mobileDiyRoot, 'schema/navigation-style.ts'),
        'utf8',
      ),
      'C 端 navigation-style.ts',
    );
    expect(adminCore).toBe(mobileCore);

    // 三处渲染面（编辑画布 / 草稿预览 / C 端 diy）都必须消费同一解析函数
    for (const source of [
      readFileSync(resolve(mobileDiyRoot, 'index.vue'), 'utf8'),
      readFileSync(
        resolve(adminSchemaRoot, '../components/phone-canvas.vue'),
        'utf8',
      ),
      readFileSync(
        resolve(
          adminSchemaRoot,
          '../../page-preview/components/preview-canvas.vue',
        ),
        'utf8',
      ),
    ]) {
      expect(source).toContain('resolveNavigationAppearance');
    }

    // 自定义模式必须继续沿用有效主题导航色（全局换肤不能被页面配置遮住），
    // 只有 followFirstSection 才接管；取色规则依赖区块渐变顶边起色
    expect(adminCore).toContain('followFirstSection');
    expect(adminCore).toContain('getSectionGradientAnchorColor');
    // 左右渐变整段复用需要 C 端 hr-navbar 支持背景图
    const navbar = readFileSync(
      resolve(mobileDiyRoot, '../hr-navbar/index.vue'),
      'utf8',
    );
    expect(navbar).toContain('backgroundImage');
  });

  it('页面固化主题：预览画布与 C 端 diy 都按快照主色停止叠加注入主题', () => {
    // 生效卡片/商城主题预览会给画布注入商城默认主题变量；页面发布时固化过
    // themeSnapshot（migrate 暴露 themePrimaryColor）的页面实机用快照色，
    // 两端渲染器必须同样判定，否则卡片/换肤预览会盖掉页面发布时的固有色
    // （曾因此出现生效卡片配色与小程序不一致）。
    const admin = readFileSync(
      resolve(
        adminSchemaRoot,
        '../../page-preview/components/preview-canvas.vue',
      ),
      'utf8',
    );
    const mobile = readFileSync(resolve(mobileDiyRoot, 'index.vue'), 'utf8');
    for (const source of [admin, mobile]) {
      expect(source).toContain('themePrimaryColor');
      expect(source).toContain('themeSecondaryColor');
    }
  });

  it('新人专享：自动前进为分页步进，且不再用连续跑马灯关键帧', () => {
    // 交互已由「连续位移的跑马灯」改为「分页式自动前进」：
    // 连续跑马灯让内容无法阅读、用户无法接管（NN/g 自动轮播研究），
    // WCAG 2.2.2 还要求自动移动超过 5 秒的内容必须可暂停。
    const admin = readAdmin('newbie-floor');
    const mobile = readMobile('diy-newbie-floor');

    for (const [label, source] of [
      ['管理端 newbie-floor', admin],
      ['C 端 diy-newbie-floor', mobile],
    ] as const) {
      // 跑马灯遗留物必须清干净：关键帧、双份轨道、is-auto 类都不应存在
      expect(source, `${label} 不应再保留 nf-marquee 关键帧`).not.toContain(
        '@keyframes nf-marquee',
      );
      expect(source, `${label} 不应再保留 nf-track-wrap`).not.toContain(
        'nf-track-wrap',
      );
      expect(source, `${label} 不应再保留 is-auto`).not.toContain('is-auto');
      // 自动前进必须存在
      expect(source, `${label} 须实现自动前进`).toContain('shouldAutoAdvance');
      // 触摸/按住即暂停，松手后延时恢复（WCAG 2.2.2 的暂停机制）
      expect(source, `${label} 须有暂停与恢复`).toMatch(/pauseAuto|pause/);
      expect(source, `${label} 须有恢复延时`).toMatch(
        /resumeTimer|scheduleResume/,
      );
      expect(source, `${label} 恢复延时应为 5 秒`).toContain('5000');
      // 间隔驱动步进（不再是 animationDuration）
      expect(source, `${label} 应以 interval 驱动步进`).toMatch(
        /intervalMs|interval/,
      );
      expect(source, `${label} 应对历史草稿缺省回退 3000`).toContain('3000');
    }
  });

  it('新人专享：手动横滑与自动前进并存（不再互斥）', () => {
    // 曾把两者做成互斥：`scroll-x="!autoScroll"`、`overflow: hidden`，
    // 导致「开了自动就滑不动」，用户唯一能改变内容的方式是干等。
    const admin = readAdmin('newbie-floor');
    const mobile = readMobile('diy-newbie-floor');
    const adminStyle = styleBlock(admin);
    const mobileStyle = styleBlock(mobile);

    // 管理端容器始终可横向滚动，且拖拽与自动并存
    expect(value(declarations(adminStyle, '.nf-scroll'), 'overflow-x')).toBe(
      'auto',
    );
    expect(admin).toContain('startDrag');
    expect(admin).toMatch(/@mousedown="startDrag"/);

    // C 端 scroll-view 恒开 scroll-x，并挂了触摸暂停/恢复
    expect(mobile).toMatch(/scroll-x\s*:scroll-left/);
    expect(mobile).toContain('@touchstart="pauseAuto"');
    expect(mobile).toContain('@touchend="scheduleResume"');
    // 底部留白承接卡片投影（overflow-y 裁切会切出硬边）：8rpx = 管理端 4px
    expect(mobileStyle).toContain('padding-bottom: 8rpx');

    // ── 小程序横滑完整性契约（运营反馈「最后一张卡滑不全」的两条根因） ──
    // 根因一：轨道是块级 flex 容器，自身宽度只有 scroll-view 可视宽，
    // 原生滚动区量不到溢出的卡片。两端都必须显式撑开轨道宽度——管理端
    // width:max-content，C 端同样 max-content + 模板行内 :style="trackStyle"
    // （按卡片数算内容宽的兜底，小程序对块级 flex 轨道的原生测量不可靠）。
    expect(value(declarations(adminStyle, '.nf-track'), 'width')).toBe(
      'max-content',
    );
    expect(value(declarations(mobileStyle, '.nf-track'), 'width')).toBe(
      'max-content',
    );
    expect(mobile).toContain(':style="trackStyle"');
    // 根因二：自动前进的滚动上限不能按「屏宽 750rpx - 硬编码内边距」估算。
    // 楼层外层装修区块还有 paddingX（当前页面用的「卡片区块」预设 12px/边 = 24rpx，共缺 48rpx），
    // 少算后自动前进到末尾时最后一张卡仍会被裁掉半张；小程序必须用
    // SelectorQuery 实测可视宽与轨道宽，旧 BANNER_INNER_RPX 硬编码不得回潮。
    expect(mobile).toContain('createSelectorQuery');
    expect(mobile).toContain('maxScrollLeft');
    expect(mobile).not.toContain('BANNER_INNER_RPX');
  });

  it('新人专享：三层色彩角色（区块底纹 / 价格暖色 / 中性），两端一致', () => {
    // 曾把运营配的 --nf-main 铺满整层（横幅 / 角标 / 价格 / 抢购 / 省¥ / 倒计时
    // 同一个色值），同一屏里跟商品横滑条的两组价格颜色不一致。
    // 现分三层：底纹=--nf-main 低透明度斜向渐隐；价格类元素=全站价格口径
    // （--wot-color-theme-primary，与 .gs-price 同 token）；其余固定中性色。
    const admin = readAdmin('newbie-floor');
    const mobile = readMobile('diy-newbie-floor');
    const adminStyle = styleBlock(admin);
    const mobileStyle = styleBlock(mobile);

    // 价格 / 省¥ / 倒计时继续取全站价格色；角标取主题渐变
    for (const [label, style] of [
      ['管理端', adminStyle],
      ['C 端', mobileStyle],
    ] as const) {
      const price = value(declarations(style, '.nf-price'), 'color');
      const save = value(declarations(style, '.nf-save'), 'color');
      const countdown = value(declarations(style, '.nf-cd-time'), 'color');
      const badge = value(declarations(style, '.nf-badge'), 'background');

      // 与商品横滑条同 token：全站价格统一暖色口径
      const priceToken = 'var(--wot-color-theme-primary, #ff2237)';
      expect(price, `${label} 价格须取全站价格色`).toBe(priceToken);
      expect(save, `${label} 省¥ 须取全站价格色`).toBe(priceToken);
      expect(countdown, `${label} 倒计时时间须取全站价格色`).toBe(priceToken);
      // 角标从「纯价格色底」改为暖色渐变，意图仍是「不跟随 --nf-main」：
      // 渐变两端都是全站主题 token（secondary → primary），故断言两个 token 都在
      expect(badge, `${label} 角标须取全站主题渐变`).toContain(
        'var(--wot-color-theme-primary',
      );
      expect(badge, `${label} 角标须取全站主题渐变`).toContain(
        '--wot-color-theme-secondary',
      );
    }

    // 图片垫底中性灰：与商品横滑条/商品分组的 #f3f4f6 同口径
    expect(value(declarations(adminStyle, '.nf-img'), 'background')).toBe(
      '#f3f4f6',
    );
    expect(value(declarations(mobileStyle, '.nf-img'), 'background')).toBe(
      '#f3f4f6',
    );

    // 无图占位的文字色也须同 token（曾两端不同：一端跟 --nf-main、一端跟价格色）
    expect(value(declarations(adminStyle, '.nf-img--empty'), 'color')).toBe(
      value(declarations(mobileStyle, '.nf-img--empty'), 'color'),
    );

    // 倒计时结构同步：两端都拆成「前缀 + 时间」两段（前缀中性灰、时间价格色），
    // 旧版是一整串「距结束 xx:xx:xx」单段文本，两端结构曾各写一套
    for (const [label, source] of [
      ['管理端', admin],
      ['C 端', mobile],
    ] as const) {
      expect(source, `${label} 倒计时应拆出前缀段`).toContain('nf-cd-label');
      expect(source, `${label} 倒计时应拆出时间段`).toContain('nf-cd-time');
      expect(source, `${label} countdown 须返回 prefix`).toContain(
        'countdown.prefix',
      );
      expect(source, `${label} countdown 须返回 text`).toContain(
        'countdown.text',
      );
    }

    // 区块底纹仍是运营可配的主色：只作为 9% 斜向渐隐叠加在白底上
    expect(
      value(declarations(adminStyle, '.nf-panel'), 'background'),
    ).toContain('var(--nf-main');
    expect(
      value(declarations(mobileStyle, '.nf-panel'), 'background'),
    ).toContain('var(--nf-main');

    // 抢购按钮与商品横滑条同款渐变（两端同 token、同方向 135deg）
    for (const [label, background] of [
      ['管理端', value(declarations(adminStyle, '.nf-btn'), 'background')],
      ['C 端', value(declarations(mobileStyle, '.nf-btn'), 'background')],
    ] as const) {
      expect(background, `${label} 抢购按钮须用全站主题渐变`).toContain(
        '--wot-color-theme-secondary',
      );
      expect(background, `${label} 抢购按钮渐变方向须为 135deg`).toContain(
        '135deg',
      );
    }

    // 轻量投影：浅色面板上用 4% 低透明度拉开层次，不再用 18% 重投影
    expect(value(declarations(adminStyle, '.nf-card'), 'box-shadow')).toBe(
      '0 2px 8px rgb(31 35 41 / 4%)',
    );
    expect(value(declarations(mobileStyle, '.nf-card'), 'box-shadow')).toBe(
      '0 4rpx 16rpx rgb(31 35 41 / 4%)',
    );

    // 轻量圆角：面板 24rpx↔12px、白卡 16rpx↔8px（严格 2 倍关系）
    expect(value(declarations(adminStyle, '.nf-panel'), 'border-radius')).toBe(
      '12px',
    );
    expect(value(declarations(mobileStyle, '.nf-panel'), 'border-radius')).toBe(
      '24rpx',
    );
    expect(value(declarations(adminStyle, '.nf-card'), 'border-radius')).toBe(
      '8px',
    );
    expect(value(declarations(mobileStyle, '.nf-card'), 'border-radius')).toBe(
      '16rpx',
    );

    // 一屏三张的紧凑几何：卡宽 200rpx↔100px、图高 200rpx↔100px、抢购钮 36rpx↔18px
    for (const [label, style, scale] of [
      ['管理端', adminStyle, 1],
      ['C 端', mobileStyle, 2],
    ] as const) {
      expect(
        value(declarations(style, '.nf-card'), 'width'),
        `${label} 卡宽`,
      ).toBe(`${100 * scale}${scale === 1 ? 'px' : 'rpx'}`);
      expect(
        value(declarations(style, '.nf-img'), 'height'),
        `${label} 图高`,
      ).toBe(`${100 * scale}${scale === 1 ? 'px' : 'rpx'}`);
      expect(
        value(declarations(style, '.nf-btn'), 'height'),
        `${label} 抢购钮高`,
      ).toBe(`${18 * scale}${scale === 1 ? 'px' : 'rpx'}`);
    }
  });
});
