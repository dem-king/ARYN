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

  it('搜索栏：外层内边距与输入区高度两端一致', () => {
    const admin = styleBlock(readAdmin('search-bar'));
    const mobile = styleBlock(readMobile('diy-search-bar'));

    expect(value(declarations(admin, '.search-bar-row'), 'padding')).toBe(
      '6px 12px',
    );
    expect(value(declarations(mobile, '.search-bar'), 'padding')).toBe(
      '6px 12px',
    );
    expect(value(declarations(admin, '.search-bar-inner'), 'height')).toBe(
      '36px',
    );
    expect(value(declarations(mobile, '.search-bar-inner'), 'height')).toBe(
      '36px',
    );
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

  it('轮播图：外层左右 12px 边距两端一致', () => {
    const admin = styleBlock(readAdmin('swiper-banner'));
    expect(value(declarations(admin, '.swiper-banner-box'), 'margin')).toBe(
      '0 12px',
    );
    expect(styleBlock(readMobile('diy-swiper-banner'))).toContain(
      'margin: 0 12px',
    );
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
  });
});
