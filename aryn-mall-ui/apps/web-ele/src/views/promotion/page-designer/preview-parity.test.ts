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
const mobileLocalesRoot = resolve(
  process.cwd(),
  '../aryn-mall-uniapp/src/locales',
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
      '20px',
    );
    // 小程序用 wd-notice-bar 渲染，行高通过 CSS 变量与画布对齐
    const mobileNotice = readMobile('diy-notice');
    expect(mobileNotice).toContain('wd-notice-bar');
    expect(mobileNotice).toContain("'--wot-notice-bar-line-height': '20px'");
    // 曾用 var(...) 把 color 包起来，浏览器拿不到该变量导致图标颜色失效；
    // 现在应把 color 直接作为颜色值使用
    const noticeAdmin = readAdmin('notice');
    expect(noticeAdmin).not.toMatch(/color:\s*`var\(/);
    expect(noticeAdmin).toMatch(/color:\s*titleColor/);
  });

  it('公告：滚动行为两端一致（单条 ticker / 多条逐条滚动 / 竖向轮播）', () => {
    // 画布曾只静态显示首条，运营配置的滚动方式/速度在预览里完全不可见。
    // 现在与小程序 diy-notice 同构：单条横向走 MarqueeText ticker（无缝循环），
    // 多条横向/竖向走 wd-notice-bar 同算法仿真（transitionend 逐条驱动）。
    const admin = readAdmin('notice');
    const mobile = readMobile('diy-notice');

    // 单条横向：两端都引用 activity-strip 的 MarqueeText 并开启 ticker
    expect(mobile).toContain('const singleTicker = computed');
    expect(mobile).toContain('MarqueeText');
    expect(admin).toContain('const singleTicker = computed');
    expect(admin).toContain("from '../activity-strip/marquee-text.vue'");
    expect(admin).toContain('ticker');

    // 多条/竖向：画布必须有 wd-notice-bar 仿真，且与小程序同走 scrollable/wrapable 组合
    expect(admin).toContain('notice-scroller');
    expect(admin).toContain(':scrollable="!singleNotice"');
    expect(admin).toContain(':wrapable="singleNotice && !singleTicker"');
    expect(mobile).toContain(':scrollable="!singleNotice"');
    // 小程序通知栏只在非单条 ticker 时渲染，wrapable 写 singleNotice 或
    // singleNotice && !singleTicker 视觉等价；两种写法都放行。
    expect(mobile).toMatch(/:wrapable="singleNotice(?: && !singleTicker)?"/);

    // 速度口径：两端同为 px/s，0/非法值回落 50
    expect(admin).toContain('|| 50');
    expect(mobile).toContain('|| 50');

    // 仿真状态机与 wd-notice-bar 同节奏：transitionend 驱动换条、
    // 条目间停顿 1s（库默认 delay）、横向 translateX 逐条进出、竖向 translateY 步进轮播
    const scroller = readFileSync(
      resolve(adminComponentsRoot, 'notice/notice-scroller.vue'),
      'utf8',
    );
    expect(scroller).toContain('transitionend');
    expect(scroller).toContain('const DELAY_SEC = 1');
    expect(scroller).toContain('translateX');
    expect(scroller).toContain('translateY');
    expect(scroller).toContain('transitionProperty');
  });

  it('商品排行：区块标题与小程序同值', () => {
    const admin = styleBlock(readAdmin('goods-ranking'));
    const mobile = styleBlock(readMobile('diy-goods-ranking'));

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
      `${rpxToPx(88)}px`,
    );
    expect(value(declarations(mobileShop, '.shop-logo'), 'width')).toBe(
      '88rpx',
    );
    expect(value(declarations(adminShop, '.shop-name'), 'color')).toBe(
      value(declarations(mobileShop, '.shop-name'), 'color'),
    );
    // 店铺头卡片与联系胶囊（2026-10 重构）：内边距/高度两端同值
    expect(value(declarations(adminShop, '.shop-content'), 'padding')).toBe(
      `${rpxToPx(24)}px`,
    );
    expect(value(declarations(mobileShop, '.shop-card'), 'padding')).toBe(
      '24rpx',
    );
    expect(value(declarations(adminShop, '.contact-button'), 'height')).toBe(
      `${rpxToPx(60)}px`,
    );
    expect(value(declarations(mobileShop, '.contact-button'), 'height')).toBe(
      '60rpx',
    );
  });

  it('服务承诺：保障条白卡与圆底徽标两端一致，图标兜底同关键词表', () => {
    const adminStyle = styleBlock(
      readFileSync(
        resolve(adminComponentsRoot, 'extension/extension-preview.vue'),
        'utf8',
      ),
    );
    const mobileStyle = styleBlock(readMobile('diy-service-promise'));
    // 白卡：C 端 promise-inner（padding 20rpx 另有 it.each 守卫），画布 preview-inner--card
    expect(
      value(declarations(adminStyle, '.preview-inner--card'), 'border-radius'),
    ).toBe(`${rpxToPx(24)}px`);
    expect(
      value(declarations(mobileStyle, '.promise-inner'), 'border-radius'),
    ).toBe('24rpx');
    // 圆底徽标 64rpx ↔ 32px
    expect(
      value(declarations(adminStyle, '.preview-promise-badge'), 'width'),
    ).toBe(`${rpxToPx(64)}px`);
    expect(value(declarations(mobileStyle, '.promise-badge'), 'width')).toBe(
      '64rpx',
    );
    expect(
      value(declarations(adminStyle, '.preview-promise-text'), 'color'),
    ).toBe(value(declarations(mobileStyle, '.promise-text'), 'color'));
    // iconUrl 无上传入口恒为空，两端必须按同一关键词表做内置图标兜底
    // （映射在 <script> 里，styleBlock 只取样式，这里对全文断言）
    const adminSource = readFileSync(
      resolve(adminComponentsRoot, 'extension/extension-preview.vue'),
      'utf8',
    );
    const mobileSource = readMobile('diy-service-promise');
    for (const keyword of [
      '退|换',
      '[保正质]',
      '服|客|售后',
      '[速快时达]',
      '票|结算|款|支付',
    ]) {
      expect(mobileSource).toContain(keyword);
      expect(adminSource).toContain(keyword);
    }
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

  it('搜索栏：铃铛/扫码图标按环境底色自动取可读色，渲染面统一下发区块底色', () => {
    // 深色头部背景（主题色渐变/深色区块）上铃铛恒用深色会被误读为不可点击：
    // 两端都按 ambientBackground 亮度取图标色（pickReadableIconColor 在
    // NAVIGATION-FOLLOW 标记块内逐字同源），三个渲染面（C 端 diy / 编辑画布 /
    // 预览画布）都必须把区块环境底色传给搜索栏。
    const admin = readAdmin('search-bar');
    const mobile = readMobile('diy-search-bar');
    for (const [label, source] of [
      ['管理端', admin],
      ['C 端', mobile],
    ] as const) {
      expect(source, `${label} 须消费宿主区块底色`).toContain('hostBackground');
      expect(source, `${label} 须按环境底色取图标色`).toContain(
        'pickReadableIconColor',
      );
      expect(source, `${label} 须保留自身背景优先口径`).toContain(
        'bgStartColor',
      );
    }
    expect(mobile).toContain(':color="iconColor"');
    expect(admin).toContain(':stroke="iconColor"');

    for (const [label, source] of [
      [
        'C 端 diy 渲染器',
        readFileSync(resolve(mobileDiyRoot, 'index.vue'), 'utf8'),
      ],
      [
        '管理端编辑画布',
        readFileSync(
          resolve(adminSchemaRoot, '../components/canvas-section-list.vue'),
          'utf8',
        ),
      ],
      [
        '管理端预览画布',
        readFileSync(
          resolve(
            adminSchemaRoot,
            '../../page-preview/components/preview-canvas.vue',
          ),
          'utf8',
        ),
      ],
    ] as const) {
      expect(source, `${label} 须向搜索栏下发区块底色`).toContain(
        'host-background',
      );
      expect(source, `${label} 须计算区块环境底色`).toContain(
        'sectionAmbientColor',
      );
    }
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

  it('领券：票面结构（白卡 / 券类型胶囊 / 虚线撕口 / 领取按钮）两端一致', () => {
    const admin = readAdmin('coupon-receive');
    const mobile = readMobile('diy-coupon-receive');

    // 2026-11 券面重构：渐变描边外框与整条状态带下线，统一为
    // 「白卡 + 上券面 / 左值块 + 虚线撕口 + 轻投影」；结构 class 两端同名同量。
    for (const marker of [
      'coupon-item',
      'coupon-face',
      'coupon-pill',
      'amount-row',
      'coupon-value',
      'coupon-threshold',
      'coupon-name',
      'coupon-info',
      'coupon-desc',
      'coupon-band',
      'coupon-btn',
    ]) {
      expect(admin, `管理端预览缺少结构 ${marker}`).toContain(marker);
      expect(mobile, `小程序缺少结构 ${marker}`).toContain(marker);
    }

    const adminStyle = styleBlock(admin);
    const mobileStyle = styleBlock(mobile);

    // 面额字号：24px ↔ 48rpx（券卡与领券卡同一口径）
    expect(value(declarations(adminStyle, '.coupon-value'), 'font-size')).toBe(
      `${rpxToPx(48)}px`,
    );
    expect(value(declarations(mobileStyle, '.coupon-value'), 'font-size')).toBe(
      '48rpx',
    );

    // 券面上内边距逐值换算（曾两端各写各的，一排 3 张时卡片高度明显不同）
    const mobileFacePadding = value(
      declarations(mobileStyle, '.coupon-face'),
      'padding',
    );
    expect(mobileFacePadding).toBe('20rpx 12rpx 14rpx');
    expect(value(declarations(adminStyle, '.coupon-face'), 'padding')).toBe(
      mobileFacePadding
        .split(' ')
        .map((step) => `${rpxToPx(Number.parseFloat(step))}px`)
        .join(' '),
    );

    // 虚线撕口：2rpx 虚线 ↔ 1px dashed（宽度锁死，避免一边实线/一边虚线）
    const mobileBandBorder = value(
      declarations(mobileStyle, '.coupon-band'),
      'border-top',
    );
    expect(mobileBandBorder).toContain('dashed');
    expect(mobileBandBorder).toContain('$coupon-divider-width');
    const adminBandBorder = value(
      declarations(adminStyle, '.coupon-band'),
      'border-top',
    );
    expect(adminBandBorder).toContain('dashed');
    expect(adminBandBorder).toBe(`${rpxToPx(2)}px dashed #ffe3d6`);

    // 领取按钮高度：26px ↔ 52rpx（一排 3 张时的紧凑高度）
    expect(value(declarations(adminStyle, '.coupon-btn'), 'height')).toBe(
      `${rpxToPx(52)}px`,
    );
    expect(value(declarations(mobileStyle, '.coupon-btn'), 'height')).toBe(
      '52rpx',
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
      `$coupon-tint-strong: ${pillColor.toLowerCase()};`,
    );
  });

  it('领券：小程序票面变量取值与预览换算口径一致（1rpx = 0.5px）', () => {
    // 小程序侧用 SCSS 变量，数值在 coupon-ticket.scss；这里锁住变量值，
    // 避免只改一边导致两端悄悄错位（预览是写死的 px，改不动就被这条挡住）
    const tokens = readFileSync(
      resolve(mobileStylesRoot, 'coupon-ticket.scss'),
      'utf8',
    );
    expect(tokens).toContain('$coupon-card-radius: 24rpx;');
    expect(tokens).toContain('$coupon-notch-size: 20rpx;');
    expect(tokens).toContain('$coupon-value-width: 176rpx;');
    // 撕口虚线宽是「2rpx ↔ 1px」换算的依据，单独锁住，避免只改预览的 1px
    expect(tokens).toContain('$coupon-divider-width: 2rpx;');

    // 管理端预览写死的 px 必须与上面的令牌保持 1rpx = 0.5px 换算：
    // 票卡圆角 24rpx ↔ 12px；横版票左值块 176rpx ↔ 88px
    const adminStyle = styleBlock(readAdmin('coupon-receive'));
    expect(
      value(declarations(adminStyle, '.coupon-item'), 'border-radius'),
    ).toBe(`${rpxToPx(24)}px`);
    expect(adminStyle).toMatch(new RegExp(`width:\\s*${rpxToPx(176)}px;`));
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

  it('补给单：白卡 / 空态引导两端同名同值（1rpx = 0.5px）', () => {
    const admin = readAdmin('replenish-card');
    const mobile = readMobile('diy-replenish-card');
    const adminStyle = styleBlock(admin);
    const mobileStyle = styleBlock(mobile);

    const zhDiy = readFileSync(
      resolve(mobileLocalesRoot, 'modules/diy.zh-CN.ts'),
      'utf8',
    );

    // 2026-11 重设计：旧版「深蓝渐变 + 半透明地址胶囊 / guide-inner」下线，
    // 两端统一为「白卡 + 发丝线 + 轻投影 + 中性灰 + 品牌色点状强调」。
    // 有单卡片与空态引导卡的结构 class 两端同名同量（管理端画布只换算单位）
    for (const marker of [
      'rp-card',
      'rp-guide',
      'rp-icon',
      'rp-title',
      'rp-action',
      'rp-stat-num',
      'rp-amount-value',
      'rp-preview',
      'rp-guide-btn',
      'rp-guide-btn--primary',
    ]) {
      expect(admin, `管理端预览缺少结构 ${marker}`).toContain(marker);
      expect(mobile, `小程序缺少结构 ${marker}`).toContain(marker);
    }

    // 两端都按策略切换形态；C 端入口文案走 i18n 键，画布写死同词条
    for (const source of [admin, mobile]) {
      expect(source).toContain("emptyStrategy === 'placeholder'");
    }
    for (const label of ['导入清单下单', '发起补给单']) {
      expect(admin).toContain(label);
    }
    for (const key of ['createAction', 'importAction']) {
      expect(mobile).toContain(`t('diy.replenishCard.${key}')`);
    }
    expect(zhDiy).toContain("createAction: '发起补给单'");
    expect(zhDiy).toContain("importAction: '导入清单下单'");

    // 画布示意数据与 C 端真实字段同口径：标题绑配置，3 项商品 / 1 人参与 /
    // 预估 ¥189.20 / 预览 3 个商品，长地址只给一行示意
    expect(admin).toContain('{{ showData.title }}');
    for (const text of [
      '项商品',
      '人参与',
      '预估',
      '189.20',
      '烟台大樱桃 ×2',
      '北京市东城区',
    ]) {
      expect(admin).toContain(text);
    }
    // 两个开关两端同名：关掉「按单加购」/「明细预览」时对应区块不渲染
    for (const source of [admin, mobile]) {
      expect(source).toContain('showBatchAdd');
      expect(source).toContain('showPreview');
    }

    // 1rpx = 0.5px：卡片 / 图标 / 标题 / CTA / 数字 / 金额 / 预览条逐值换算
    const adminCard = declarations(adminStyle, '.rp-card');
    const mobileCard = declarations(mobileStyle, '.rp-card');
    // 卡片内边距 2026-11-01 收口过一次（26/26/24 → 24/24/22），两端一起改
    expect(value(adminCard, 'padding')).toBe('12px 12px 11px');
    expect(value(mobileCard, 'padding')).toBe('24rpx 24rpx 22rpx');
    expect(value(adminCard, 'border-radius')).toBe(`${rpxToPx(24)}px`);
    expect(value(mobileCard, 'border-radius')).toBe('24rpx');

    expect(value(declarations(adminStyle, '.rp-icon'), 'width')).toBe(
      `${rpxToPx(76)}px`,
    );
    expect(value(declarations(mobileStyle, '.rp-icon'), 'width')).toBe('76rpx');

    expect(value(declarations(adminStyle, '.rp-title'), 'font-size')).toBe(
      `${rpxToPx(32)}px`,
    );
    expect(value(declarations(mobileStyle, '.rp-title'), 'font-size')).toBe(
      '32rpx',
    );

    expect(value(declarations(adminStyle, '.rp-action'), 'height')).toBe(
      `${rpxToPx(64)}px`,
    );
    expect(value(declarations(mobileStyle, '.rp-action'), 'height')).toBe(
      '64rpx',
    );

    expect(value(declarations(adminStyle, '.rp-stat-num'), 'font-size')).toBe(
      `${rpxToPx(30)}px`,
    );
    expect(value(declarations(mobileStyle, '.rp-stat-num'), 'font-size')).toBe(
      '30rpx',
    );

    expect(
      value(declarations(adminStyle, '.rp-amount-value'), 'font-size'),
    ).toBe(`${rpxToPx(36)}px`);
    expect(
      value(declarations(mobileStyle, '.rp-amount-value'), 'font-size'),
    ).toBe('36rpx');

    // 浅灰预览条：padding 12rpx 18rpx ↔ 6px 9px，上间距 12rpx ↔ 6px
    expect(value(declarations(adminStyle, '.rp-preview'), 'padding')).toBe(
      `${rpxToPx(12)}px ${rpxToPx(18)}px`,
    );
    expect(value(declarations(mobileStyle, '.rp-preview'), 'padding')).toBe(
      '12rpx 18rpx',
    );
    expect(value(declarations(adminStyle, '.rp-preview'), 'margin-top')).toBe(
      `${rpxToPx(12)}px`,
    );
    expect(value(declarations(mobileStyle, '.rp-preview'), 'margin-top')).toBe(
      '12rpx',
    );

    // 空态引导卡：内边距（24rpx ↔ 12px）/ 标题字号 / 按钮高度
    expect(value(declarations(adminStyle, '.rp-guide'), 'padding')).toBe(
      `${rpxToPx(24)}px`,
    );
    expect(value(declarations(mobileStyle, '.rp-guide'), 'padding')).toBe(
      '24rpx',
    );
    expect(
      value(declarations(adminStyle, '.rp-guide-title'), 'font-size'),
    ).toBe(`${rpxToPx(30)}px`);
    expect(
      value(declarations(mobileStyle, '.rp-guide-title'), 'font-size'),
    ).toBe('30rpx');
    expect(value(declarations(adminStyle, '.rp-guide-btn'), 'height')).toBe(
      `${rpxToPx(72)}px`,
    );
    expect(value(declarations(mobileStyle, '.rp-guide-btn'), 'height')).toBe(
      '72rpx',
    );

    // 其余冻结值同样成对：状态胶囊 / 状态点 / 发丝线 / 预览条圆角 / 按钮字号
    expect(value(declarations(adminStyle, '.rp-status'), 'padding')).toBe(
      '2px 6px 2px 5px',
    );
    expect(value(declarations(mobileStyle, '.rp-status'), 'padding')).toBe(
      '4rpx 12rpx 4rpx 10rpx',
    );
    expect(value(declarations(adminStyle, '.rp-status-dot'), 'width')).toBe(
      `${rpxToPx(8)}px`,
    );
    expect(value(declarations(mobileStyle, '.rp-status-dot'), 'width')).toBe(
      '8rpx',
    );
    expect(value(declarations(adminStyle, '.rp-divider'), 'height')).toBe(
      `${rpxToPx(1)}px`,
    );
    expect(value(declarations(mobileStyle, '.rp-divider'), 'height')).toBe(
      '1rpx',
    );
    // 分隔线上下留白 16rpx/14rpx ↔ 8px/7px
    expect(value(declarations(adminStyle, '.rp-divider'), 'margin')).toBe(
      `${rpxToPx(16)}px 0 ${rpxToPx(14)}px`,
    );
    expect(value(declarations(mobileStyle, '.rp-divider'), 'margin')).toBe(
      '16rpx 0 14rpx',
    );
    // 状态胶囊：中性底圆角 8rpx ↔ 4px；状态小字 20rpx ↔ 10px
    expect(value(declarations(adminStyle, '.rp-status'), 'border-radius')).toBe(
      `${rpxToPx(8)}px`,
    );
    expect(
      value(declarations(mobileStyle, '.rp-status'), 'border-radius'),
    ).toBe('8rpx');
    expect(
      value(declarations(adminStyle, '.rp-status-text'), 'font-size'),
    ).toBe(`${rpxToPx(20)}px`);
    expect(
      value(declarations(mobileStyle, '.rp-status-text'), 'font-size'),
    ).toBe('20rpx');
    expect(
      value(declarations(adminStyle, '.rp-preview'), 'border-radius'),
    ).toBe(`${rpxToPx(16)}px`);
    expect(
      value(declarations(mobileStyle, '.rp-preview'), 'border-radius'),
    ).toBe('16rpx');
    expect(value(declarations(adminStyle, '.rp-guide-btn'), 'font-size')).toBe(
      `${rpxToPx(26)}px`,
    );
    expect(value(declarations(mobileStyle, '.rp-guide-btn'), 'font-size')).toBe(
      '26rpx',
    );

    // 字重口径两端同值：标题 / 数值 700，状态 / CTA / 按钮 600，正文 400
    for (const [selector, weight] of [
      ['.rp-title', '700'],
      ['.rp-stat-num', '700'],
      ['.rp-amount-value', '700'],
      ['.rp-status-text', '600'],
      ['.rp-action-text', '600'],
      ['.rp-guide-btn', '600'],
    ] as const) {
      expect(value(declarations(adminStyle, selector), 'font-weight')).toBe(
        weight,
      );
      expect(value(declarations(mobileStyle, selector), 'font-weight')).toBe(
        weight,
      );
    }

    // 配色令牌两端同源：发丝线 / 浅填充 / 灰底 / 主文字 / 次文字 / 辅助文字；
    // 管理端画布固定演示租户主色 #ff5500（与 coupon-receive 预览同口径），
    // C 端品牌色走主题变量链（换肤后卡片跟着变），兜底红 #ff2237
    for (const color of [
      '#eef0f3',
      '#f7f8fa',
      '#f4f5f7',
      '#1f2329',
      '#5b616e',
      '#6b7280',
    ]) {
      expect(admin.toLowerCase()).toContain(color);
      expect(mobile.toLowerCase()).toContain(color);
    }
    expect(admin.toLowerCase()).toContain('#ff5500');
    expect(mobile).toContain(
      'var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237))',
    );

    // 画布状态文案固定「收集中」；C 端按 cartStatusLabel() 的当前状态取值
    expect(admin).toContain('收集中');
  });

  it('图文导航（金刚区）：分页高度自适应公式两端一致，实机握手段高度由 WXS 视图层驱动', () => {
    // 两端独立实现过一次 pagerHeight，曾只改一边导致预览与实机高度差一截；
    // 这里锁住「按当前页实际行数收缩高度」的公式。
    // 实机（小程序）必须让高度跟着手指同步收缩/展开，但**不能**在逻辑层逐帧写：
    // swiper 的 transition 事件每秒 60~120 次，逐帧 setData 会重排金刚区下方所有
    // 楼层，真机表现为整页掉帧、收缩滞后于手指。现由 WXS 在视图层 setStyle 逐帧
    // 驱动（不经过 setData），页码仍在 animationfinish 结算；C 端由
    // `diy-tabnav/pager-height.contract.test.ts` 单独钉死，这里做跨端防回归。
    // 管理端预览没有 WXS：保持滚动停稳后（scrollSettleTimer 防抖）再改高度。
    const admin = readAdmin('tab-nav');
    const mobile = readMobile('diy-tabnav');
    for (const source of [admin, mobile]) {
      expect(source).toContain('actualRows');
      expect(source).toMatch(
        /Math\.min\(\s*pageRows\.value\s*,\s*Math\.ceil\(\s*page\.length\s*\/\s*showNum\.value\s*\)/,
      );
      expect(source).toContain(
        'transition: height 240ms cubic-bezier(0.34, 1.35, 0.64, 1)',
      );
      // 图标放大后的行距与文字规格：两端都按「行高 + 8px、13px 字号」计算与渲染
      expect(source).toContain('rowHeight.value + 8');
      expect(source).toContain('font-size: 13px');
      expect(source).toContain('padding-bottom: 8px');
    }
    expect(mobile).toContain('@animationfinish="onPagerAnimationFinish"');
    // 小程序端：transition 绑到 WXS 视图层事件，并由 data-* 下发高度/页码/宽度
    expect(mobile).toContain('<script module="tabnavWxs" lang="wxs">');
    expect(mobile).toContain('@transition="tabnavWxs.onTransition"');
    expect(mobile).toContain(':data-heights="pagerHeightsAttr"');
    // 小程序端 wxss 不允许带高度过渡（WXS 逐帧写入，过渡会变「追赶」拖影）：
    // 240ms 过渡只保留给 H5/App 的 JS 通道，因此必须包在条件编译里
    expect(mobile).toContain('/* #ifndef MP-WEIXIN */');
    expect(admin).toContain('scrollSettleTimer');
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
    ).toBe('12px');
    expect(
      value(declarations(mobile, '.goods-tabs__card'), 'border-radius'),
    ).toBe('24rpx');
    // 留白挂内层，避免 commonStyle 内联零值覆盖
    expect(value(declarations(admin, '.goods-tabs-inner'), 'padding')).toBe(
      '8px 10px 10px',
    );
    expect(value(declarations(mobile, '.goods-tabs__inner'), 'padding')).toBe(
      '16rpx 20rpx 20rpx',
    );
  });

  it('多Tab商品流：会员权益条 / 会员价 / 捡漏价样式两端一致（1rpx = 0.5px）', () => {
    const adminSource = readFileSync(
      resolve(adminComponentsRoot, 'extension/goods-tabs/index.vue'),
      'utf8',
    );
    const mobileSource = readMobile('diy-goods-tabs');
    const admin = styleBlock(adminSource);
    const mobile = styleBlock(mobileSource);

    // 会员权益条：金色会员态、12rpx↔6px 圆角、14rpx 18rpx↔7px 9px 内边距
    expect(
      value(declarations(admin, '.goods-tabs-member-banner'), 'border-radius'),
    ).toBe('6px');
    expect(
      value(
        declarations(mobile, '.goods-tabs__member-banner'),
        'border-radius',
      ),
    ).toBe('12rpx');
    expect(
      value(declarations(admin, '.goods-tabs-member-banner'), 'padding'),
    ).toBe('7px 9px');
    expect(
      value(declarations(mobile, '.goods-tabs__member-banner'), 'padding'),
    ).toBe('14rpx 18rpx');
    expect(
      value(declarations(admin, '.goods-tabs-member-banner'), 'margin'),
    ).toBe('6px 10px 0');
    expect(
      value(declarations(mobile, '.goods-tabs__member-banner'), 'margin'),
    ).toBe('12rpx 20rpx 0');
    expect(
      value(declarations(admin, '.goods-tabs-member-banner'), 'background'),
    ).toBe(
      value(declarations(mobile, '.goods-tabs__member-banner'), 'background'),
    );

    // 会员价：浅金描边小标 + 近黑主价 + 灰色日常价，两端颜色与尺寸同值
    expect(
      value(declarations(admin, '.goods-tabs-member-badge'), 'font-size'),
    ).toBe('9px');
    expect(
      value(declarations(mobile, '.goods-tabs__member-badge'), 'font-size'),
    ).toBe('18rpx');
    expect(
      value(declarations(admin, '.goods-tabs-member-badge'), 'color'),
    ).toBe(value(declarations(mobile, '.goods-tabs__member-badge'), 'color'));
    expect(
      backgroundColorIsSame(
        declarations(admin, '.goods-tabs-member-badge'),
        declarations(mobile, '.goods-tabs__member-badge'),
      ),
    ).toBe(true);
    expect(
      value(declarations(admin, '.goods-tabs-price--member'), 'font-size'),
    ).toBe('17px');
    expect(
      value(declarations(mobile, '.goods-tabs__price--member'), 'font-size'),
    ).toBe('34rpx');
    expect(
      value(declarations(admin, '.goods-tabs-price--member'), 'color'),
    ).toBe(value(declarations(mobile, '.goods-tabs__price--member'), 'color'));

    // 捡漏价：标签 18rpx↔9px、整数 40rpx↔20px、小数 24rpx↔12px、原价 20rpx↔10px
    for (const [adminSelector, mobileSelector, adminValue, mobileValue] of [
      [
        '.goods-tabs-bargain-label',
        '.goods-tabs__bargain-label',
        '9px',
        '18rpx',
      ],
      [
        '.goods-tabs-bargain-integer',
        '.goods-tabs__bargain-integer',
        '20px',
        '40rpx',
      ],
      [
        '.goods-tabs-bargain-decimal',
        '.goods-tabs__bargain-decimal',
        '12px',
        '24rpx',
      ],
      [
        '.goods-tabs-bargain-original-price',
        '.goods-tabs__bargain-original-price',
        '10px',
        '20rpx',
      ],
    ] as const) {
      expect(value(declarations(admin, adminSelector), 'font-size')).toBe(
        adminValue,
      );
      expect(value(declarations(mobile, mobileSelector), 'font-size')).toBe(
        mobileValue,
      );
    }
    expect(
      value(declarations(admin, '.goods-tabs-bargain-original-price'), 'color'),
    ).toBe(
      value(
        declarations(mobile, '.goods-tabs__bargain-original-price'),
        'color',
      ),
    );

    // 普通价三档字号：符号 22rpx↔11px、整数 36rpx↔18px、小数 24rpx↔12px
    for (const [adminSelector, mobileSelector, adminValue, mobileValue] of [
      [
        '.goods-tabs-price-symbol',
        '.goods-tabs__price-symbol',
        '11px',
        '22rpx',
      ],
      [
        '.goods-tabs-price-integer',
        '.goods-tabs__price-integer',
        '18px',
        '36rpx',
      ],
      [
        '.goods-tabs-price-decimal',
        '.goods-tabs__price-decimal',
        '12px',
        '24rpx',
      ],
    ] as const) {
      expect(value(declarations(admin, adminSelector), 'font-size')).toBe(
        adminValue,
      );
      expect(value(declarations(mobile, mobileSelector), 'font-size')).toBe(
        mobileValue,
      );
    }

    // 标题只预留一行高度（单行标题不再空出一行），子标题上边距 6rpx↔3px
    expect(value(declarations(admin, '.goods-tabs-name'), 'min-height')).toBe(
      '1.4em',
    );
    expect(value(declarations(mobile, '.goods-tabs__name'), 'min-height')).toBe(
      '1.4em',
    );
    expect(value(declarations(admin, '.goods-tabs-subtitle'), 'margin')).toBe(
      '3px 10px 0',
    );
    expect(value(declarations(mobile, '.goods-tabs__subtitle'), 'margin')).toBe(
      '6rpx 20rpx 0',
    );

    // 两端都必须消费同一组样式标记与优先级
    for (const [label, source] of [
      ['管理端', adminSource],
      ['C 端', mobileSource],
    ] as const) {
      expect(source, `${label} 须渲染会员权益条`).toContain(
        'memberBanner?.enabled',
      );
      expect(source, `${label} 须渲染会员价`).toContain('会员价');
      expect(source, `${label} 须渲染捡漏价`).toContain('捡漏价');
    }
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
    ['diy-member-benefits', '.benefits-inner'],
    ['diy-service-promise', '.promise-inner'],
    ['diy-video-live', '.videolive-inner'],
  ] as const)(
    'extension 组件 %s 的留白挂在内层（防 commonStyle 内联零值覆盖）',
    (dir, selector) => {
      // 与商品推荐同根因：留白挂根节点会被装修 commonStyle 的 padding-*:0
      // 内联样式整块盖掉，extension 组件曾因此在实机顶到屏幕边缘
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
    // 搜索框开启「滚动常驻」时 C 端还要再让出一条常驻搜索栏（stickyOffsetTop =
    // 导航高度 + 搜索栏占用高度）；管理端画布/草稿预览是静态滚动面、不渲染常驻层，
    // 仍停在画布顶部（与 docs/50-需求文档/2026-10-27-搜索框滚动常驻/README.md
    // §「与设计稿的偏差」一致）。
    expect(adminSectionStyle).toMatch(/result\.top = '0'/);
    expect(mobileRenderer).toMatch(
      /style\.top = `\$\{stickyOffsetTop\.value\}px`/,
    );
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

  it('导航栏滚动渐显：两端解析同源，三处渲染面统一消费', () => {
    // 沉浸式导航把「滚动进度 → 背景/文字/状态栏」抽成纯函数：管理端画布、
    // 草稿预览、C 端 hr-navbar 共用；两端各留一份拷贝（避免跨仓 import 别名），
    // 用 NAVIGATION-SCROLL 标记圈出，去掉空白与分号后必须逐字一致。
    const extractCore = (source: string, label: string) => {
      const match =
        /\/\* NAVIGATION-SCROLL-BEGIN \*\/([\s\S]*?)\/\* NAVIGATION-SCROLL-END \*\//.exec(
          source,
        );
      expect(match, `${label} 缺少 NAVIGATION-SCROLL 标记`).toBeTruthy();
      return (match?.[1] ?? '').replaceAll(/[\s;]/g, '');
    };
    const adminCore = extractCore(
      readFileSync(resolve(adminSchemaRoot, 'navigation-scroll.ts'), 'utf8'),
      '管理端 navigation-scroll.ts',
    );
    const mobileCore = extractCore(
      readFileSync(
        resolve(mobileDiyRoot, 'schema/navigation-scroll.ts'),
        'utf8',
      ),
      'C 端 navigation-scroll.ts',
    );
    expect(adminCore).toBe(mobileCore);

    // 类型契约：C 端是内联 nav 类型、管理端是独立 NavigationSettings，没有
    // 类型共享，靠这里对账 NavigationScrollEffect 的字段形状。
    const extractInterface = (source: string, name: string) => {
      const match = new RegExp(
        `export interface ${name} \\{([\\s\\S]*?)\\n\\}`,
      ).exec(source);
      expect(match, `缺少 ${name} 接口`).toBeTruthy();
      return (match?.[1] ?? '').replaceAll(/[\s;]/g, '');
    };
    expect(
      extractInterface(
        readFileSync(resolve(adminSchemaRoot, 'types.ts'), 'utf8'),
        'NavigationScrollEffect',
      ),
    ).toBe(
      extractInterface(
        readFileSync(resolve(mobileDiyRoot, 'schema/types.ts'), 'utf8'),
        'NavigationScrollEffect',
      ),
    );

    // 三处渲染面（编辑画布 / 草稿预览 / C 端导航）都必须消费滚动解析函数
    for (const [label, source] of [
      [
        '管理端 phone-canvas',
        readFileSync(
          resolve(adminSchemaRoot, '../components/phone-canvas.vue'),
          'utf8',
        ),
      ],
      [
        '管理端 preview-canvas',
        readFileSync(
          resolve(
            adminSchemaRoot,
            '../../page-preview/components/preview-canvas.vue',
          ),
          'utf8',
        ),
      ],
      [
        'C 端 hr-navbar',
        readFileSync(resolve(mobileDiyRoot, '../hr-navbar/index.vue'), 'utf8'),
      ],
    ] as const) {
      expect(source, `${label} 未消费滚动解析`).toContain(
        'resolveNavigationScrollAppearance',
      );
    }

    // 兼容底线：缺省 mode!=='fade' 时必须原样返回起始外观（存量页面零变化）
    expect(mobileCore).toContain("normalized.mode!=='fade'");
    expect(mobileCore).toContain('baseOpacity:1');
    expect(mobileCore).toContain('surfaceOpacity:0');
    // 状态栏只允许微信支持的两个值
    expect(mobileCore).toContain("'#000000'|'#ffffff'");
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

    // 管理端容器始终可横向滚动，且拖拽与自动并存。
    // 两轴同时声明时 stylelint 要求 overflow 简写（x 在前、y 在后），这里兼容两种写法。
    const adminScrollStyle = declarations(adminStyle, '.nf-scroll');
    const adminOverflowX =
      value(adminScrollStyle, 'overflow-x') ||
      value(adminScrollStyle, 'overflow').split(/\s+/)[0];
    expect(adminOverflowX).toBe('auto');
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

  /**
   * 2026-10-22 营销楼层重设计契约（秒杀 / 拼团 / 限时折扣）。
   *
   * 背景：三个楼层此前共用「白底 + 1rpx 彩边 + 30rpx 价格」的通用列表样式，
   * 运营反馈「一点也不好看」。重设计按促销强度分级：
   *   秒杀=强（品牌渐变头 + 深色分格倒计时 + 横滑卡 + 双态进度）
   *   拼团=中（浅底头 + 单列/双列卡 + 成团徽标 + 双价 + 去拼单）
   *   折扣=轻（浅底头 + 折扣大牌 + 全场权益卡 / 双列商品卡）
   * 以下逐值锁住两端关键视觉，防止任一端单独演进造成画布与实机漂移。
   */
  it('营销楼层：管理端画布补下发 --wot-color-theme-background（多租户浅色面同源）', () => {
    // C 端由 wd-config-provider 的 colorThemeBackground 提供；画布此前漏挂，
    // 导致促销组件浅色面在蓝/绿主题下落到暖色兜底 #fff1ee。
    const phoneCanvas = readFileSync(
      resolve(
        process.cwd(),
        'apps/web-ele/src/views/promotion/page-designer/components/phone-canvas.vue',
      ),
      'utf8',
    );
    const previewCanvas = readFileSync(
      resolve(
        process.cwd(),
        'apps/web-ele/src/views/promotion/page-preview/components/preview-canvas.vue',
      ),
      'utf8',
    );
    for (const [label, source] of [
      ['编辑器画布', phoneCanvas],
      ['预览画布', previewCanvas],
    ] as const) {
      expect(source, `${label} 须下发主题浅色面变量`).toContain(
        '--wot-color-theme-background',
      );
    }
    expect(phoneCanvas).toContain('pageBackgroundColor');
  });

  /**
   * 2026-10-22 活动入口条（diy-activity-strip）管理端注册契约。
   *
   * C 端先实现（§5.5.3 / §5.5.5 / §5.5.6），管理端本轮补注册与配置面板，
   * 两端仍是独立实现。这里锁住「用户直接看到」的换算值与促销口径，
   * 防止任一端单独演进（1rpx = 0.5px，画布 375px ↔ 小程序 750rpx）。
   */
  it('活动入口条：卡片/缩略图/角标/箭头两端逐值一致（1rpx = 0.5px）', () => {
    const admin = styleBlock(readAdmin('activity-strip'));
    const mobile = styleBlock(readMobile('diy-activity-strip'));

    // 卡片：白底 + 1rpx 发丝描边（0.5px 会被设备取整，沿用 1rpx→1px 口径）
    for (const [label, style] of [
      ['管理端', admin],
      ['C 端', mobile],
    ] as const) {
      expect(
        value(declarations(style, '.strip-card'), 'background'),
        `${label} 卡片白底`,
      ).toBe('#fff');
    }
    expect(value(declarations(admin, '.strip-card'), 'border')).toBe(
      '1px solid #f1f2f4',
    );
    expect(value(declarations(mobile, '.strip-card'), 'border')).toBe(
      '1rpx solid #f1f2f4',
    );
    expect(value(declarations(admin, '.strip-card'), 'border-radius')).toBe(
      `${rpxToPx(16)}px`,
    );
    expect(value(declarations(mobile, '.strip-card'), 'border-radius')).toBe(
      '16rpx',
    );
    expect(value(declarations(admin, '.strip-card'), 'padding')).toBe(
      `${rpxToPx(12)}px`,
    );
    expect(value(declarations(mobile, '.strip-card'), 'padding')).toBe('12rpx');
    expect(value(declarations(admin, '.strip-card'), 'gap')).toBe(
      `${rpxToPx(8)}px`,
    );
    expect(value(declarations(mobile, '.strip-card'), 'gap')).toBe('8rpx');
    expect(value(declarations(admin, '.strip-card'), 'flex-direction')).toBe(
      'column',
    );
    expect(value(declarations(mobile, '.strip-card'), 'flex-direction')).toBe(
      'column',
    );

    // 首行缩略图 + 活动名；钩子文案独占整卡宽的第二行（三张卡时不再截断）
    expect(value(declarations(admin, '.strip-main'), 'gap')).toBe(
      `${rpxToPx(8)}px`,
    );
    expect(value(declarations(mobile, '.strip-main'), 'gap')).toBe('8rpx');
    for (const [label, style] of [
      ['管理端', admin],
      ['C 端', mobile],
    ] as const) {
      expect(
        value(declarations(style, '.strip-hook'), 'white-space'),
        `${label} 钩子独占整卡宽一行`,
      ).toBe('nowrap');
      expect(
        value(declarations(style, '.strip-name'), 'flex'),
        `${label} 活动名占满首行剩余宽度`,
      ).toBe('1');
    }

    // 卡片间距 10rpx：C 端由 gridStyle 内联输出，管理端写在样式里
    expect(value(declarations(admin, '.strip-grid'), 'gap')).toBe(
      `${rpxToPx(10)}px`,
    );
    expect(readMobile('diy-activity-strip')).toContain('GRID_GAP_RPX = 10');

    // 缩略图 64rpx 方块 / 圆角 12rpx
    expect(value(declarations(admin, '.strip-thumb'), 'width')).toBe(
      `${rpxToPx(64)}px`,
    );
    expect(value(declarations(mobile, '.strip-thumb'), 'width')).toBe('64rpx');
    expect(value(declarations(admin, '.strip-thumb__img'), 'width')).toBe(
      `${rpxToPx(64)}px`,
    );
    expect(value(declarations(mobile, '.strip-thumb__img'), 'width')).toBe(
      '64rpx',
    );
    expect(
      value(declarations(admin, '.strip-thumb__img'), 'border-radius'),
    ).toBe(`${rpxToPx(12)}px`);
    expect(
      value(declarations(mobile, '.strip-thumb__img'), 'border-radius'),
    ).toBe('12rpx');

    // 活动名 24rpx/700 #1f2329、钩子 20rpx #8a8f99
    expect(value(declarations(admin, '.strip-name'), 'font-size')).toBe(
      `${rpxToPx(24)}px`,
    );
    expect(value(declarations(mobile, '.strip-name'), 'font-size')).toBe(
      '24rpx',
    );
    expect(value(declarations(admin, '.strip-name'), 'color')).toBe(
      value(declarations(mobile, '.strip-name'), 'color'),
    );
    expect(value(declarations(admin, '.strip-name'), 'color')).toBe('#1f2329');
    expect(value(declarations(admin, '.strip-hook'), 'font-size')).toBe(
      `${rpxToPx(20)}px`,
    );
    expect(value(declarations(mobile, '.strip-hook'), 'font-size')).toBe(
      '20rpx',
    );
    expect(value(declarations(admin, '.strip-hook'), 'color')).toBe('#8a8f99');
    expect(value(declarations(mobile, '.strip-hook'), 'color')).toBe('#8a8f99');

    // 角标：18rpx/700、高 28rpx 胶囊，贴缩略图右下角出边 6rpx
    expect(value(declarations(admin, '.strip-badge'), 'height')).toBe(
      `${rpxToPx(28)}px`,
    );
    expect(value(declarations(mobile, '.strip-badge'), 'height')).toBe('28rpx');
    expect(value(declarations(admin, '.strip-badge'), 'font-size')).toBe(
      `${rpxToPx(18)}px`,
    );
    expect(value(declarations(mobile, '.strip-badge'), 'font-size')).toBe(
      '18rpx',
    );
    expect(value(declarations(admin, '.strip-badge'), 'border-radius')).toBe(
      '999px',
    );
    expect(value(declarations(mobile, '.strip-badge'), 'border-radius')).toBe(
      '999rpx',
    );
    expect(value(declarations(admin, '.strip-badge'), 'right')).toBe(
      `${rpxToPx(-6)}px`,
    );
    expect(value(declarations(mobile, '.strip-badge'), 'right')).toBe('-6rpx');

    // 右侧淡箭头：10rpx 见方、2rpx 中性灰描边
    expect(value(declarations(admin, '.strip-arrow'), 'width')).toBe(
      `${rpxToPx(10)}px`,
    );
    expect(value(declarations(mobile, '.strip-arrow'), 'width')).toBe('10rpx');
    expect(value(declarations(admin, '.strip-arrow'), 'border-top')).toBe(
      `${rpxToPx(2)}px solid #c9cdd4`,
    );
    expect(value(declarations(mobile, '.strip-arrow'), 'border-top')).toBe(
      '2rpx solid #c9cdd4',
    );
    expect(value(declarations(admin, '.strip-arrow'), 'right')).toBe(
      `${rpxToPx(10)}px`,
    );
    expect(value(declarations(mobile, '.strip-arrow'), 'right')).toBe('10rpx');

    // 促销色只走主题 token（多租户换肤自适应）：
    // 秒杀=主题渐变实心「抢」，拼团/折扣=主题浅底「省¥X / X折」
    for (const [label, style] of [
      ['管理端', admin],
      ['C 端', mobile],
    ] as const) {
      const strong = value(
        declarations(style, '.strip-badge--strong'),
        'background',
      );
      expect(strong, `${label} 秒杀角标须用主题渐变`).toContain(
        '--wot-color-theme-secondary',
      );
      expect(strong, `${label} 秒杀角标须用主题渐变`).toContain(
        'var(--wot-color-theme-primary',
      );
      expect(strong, `${label} 秒杀角标渐变方向`).toContain('135deg');
      expect(
        value(declarations(style, '.strip-badge--soft'), 'color'),
        `${label} 弱角标字色`,
      ).toBe('var(--wot-color-theme-primary, #ff2237)');
      expect(
        value(declarations(style, '.strip-badge--soft'), 'background'),
        `${label} 弱角标底色`,
      ).toBe('var(--wot-color-theme-background, #fff1ee)');
    }
  });

  it('活动入口条：长文案单行滚动两端同口径（间距/速度/溢出才滚）', () => {
    const adminMarquee = readFileSync(
      resolve(adminComponentsRoot, 'activity-strip/marquee-text.vue'),
      'utf8',
    );
    const mobileMarquee = readFileSync(
      resolve(mobileDiyRoot, 'diy-activity-strip/marquee-text.vue'),
      'utf8',
    );
    const mobileLogic = readFileSync(
      resolve(mobileDiyRoot, 'diy-activity-strip/marquee.ts'),
      'utf8',
    );

    // C 端纯逻辑常量是唯一口径；管理端预览必须逐值同步
    expect(mobileLogic).toContain('MARQUEE_GAP_PX = 12');
    expect(mobileLogic).toContain('MARQUEE_SPEED_PX_PER_SEC = 28');
    expect(mobileLogic).toContain('MARQUEE_OVERFLOW_EPSILON_PX = 1');
    expect(adminMarquee).toContain('const GAP_PX = 12');
    expect(adminMarquee).toContain('const SPEED_PX_PER_SEC = 28');
    expect(adminMarquee).toContain('const OVERFLOW_EPSILON_PX = 1');

    for (const [label, source] of [
      ['管理端', adminMarquee],
      ['C 端', mobileMarquee],
    ] as const) {
      // 不换行：长文案通过双份文本 + -50% 位移无缝循环
      expect(source, `${label} 跑马灯不应允许换行`).not.toContain(
        'white-space: normal',
      );
      expect(source, `${label} 轨道位移`).toContain('translateX(-50%)');
      expect(source, `${label} 单行静态态`).toContain('white-space: nowrap');
      expect(source, `${label} 触摸暂停`).toContain(
        'animation-play-state: paused',
      );
    }

    // 溢出阈值只在纯逻辑里判定：短文案保持静止，避免无意义晃动
    expect(mobileLogic).toContain(
      'return content > viewport + MARQUEE_OVERFLOW_EPSILON_PX',
    );
    // C 端总开关必须真的接到滚动组件上
    expect(readMobile('diy-activity-strip')).toContain(
      ':motion-enabled="mallThemeStore.autoMotionEnabled"',
    );

    // 公告条 ticker 模式复用本组件：文案不超宽也按容器宽重复偶数份滚动
    expect(adminMarquee).toContain('props.ticker');
  });

  it('活动入口条：自动/手动数据源与促销口径和 C 端同源', () => {
    const adminPreview = readAdmin('activity-strip');
    const adminTypes = readFileSync(
      resolve(adminComponentsRoot, 'activity-strip/types.ts'),
      'utf8',
    );
    const mobilePreview = readMobile('diy-activity-strip');
    // 折扣力度/省额口径已抽到 utils/discount-price.ts（带独立单测），契约跟随实现位置
    const mobileLogic = [
      readFileSync(
        resolve(mobileDiyRoot, 'diy-activity-strip/activity-strip.ts'),
        'utf8',
      ),
      readFileSync(
        resolve(mobileDiyRoot, '../../utils/discount-price.ts'),
        'utf8',
      ),
    ].join('\n');
    const mobileTypes = readFileSync(
      resolve(mobileDiyRoot, 'retail-types.ts'),
      'utf8',
    );

    // props 严格同形：卡片/判别联合数据源/列数（改字段必须两端一起改）
    for (const name of [
      'ActivityStripEntry',
      'ActivityStripDataSource',
      'ActivityStripProps',
    ]) {
      expect(adminTypes, `管理端类型缺少 ${name}`).toContain(name);
      expect(mobileTypes, `C 端类型缺少 ${name}`).toContain(name);
    }
    for (const [label, source] of [
      ['管理端', adminTypes],
      ['C 端', mobileTypes],
    ] as const) {
      expect(source, `${label} 不再提供每行列数`).not.toContain(
        'columns: 2 | 3 | 4',
      );
    }
    expect(adminTypes).toContain("dataSource: { mode: 'automatic' }");
    expect(adminTypes).toContain("mode: 'manual'");
    expect(mobilePreview).toContain("mode === 'manual'");
    expect(adminPreview).toContain("mode === 'manual'");

    // 三类内置入口固定跳会场聚合页：活动结束后入口不失效
    for (const path of [
      '/pages/promotion/discount',
      '/sub-pages/promotion/group-buy/group-buy-list/index',
      '/pages/promotion/seckill',
    ]) {
      expect(adminTypes, `管理端会场路由 ${path}`).toContain(path);
      expect(mobileLogic, `C 端会场路由 ${path}`).toContain(path);
    }

    // 折扣力度口径：0.95→9.5折；>=10 退化为中性「折扣」，永不出现「10折」；
    // type2→减¥X、type3→一口价（与后端语义一致）
    for (const [label, source] of [
      ['管理端预览', adminPreview],
      ['C 端逻辑', mobileLogic],
    ] as const) {
      expect(source, `${label} 折扣力度取一位小数`).toContain(
        'Math.round(Number(activity.discountValue) * 100) / 10',
      );
      expect(source, `${label} 折扣力度取一位小数`).toContain('toFixed(1)');
      expect(source, `${label} 力度 >=10 退化`).toContain('zhe >= 10');
      expect(source, `${label} 减价语义`).toContain('减¥');
      expect(source, `${label} 一口价语义`).toContain('一口价');
      // 拼团省额只在原价严格大于拼团价时出现，否则退化「拼」，不编造省额
      expect(source, `${label} 省额守卫`).toContain('if (!(saving > 0))');
    }
    // C 端已 i18n 化：无省额时返回翻译键，中文词条与画布文案同为「拼」
    const zhDiy = readFileSync(
      resolve(mobileLocalesRoot, 'modules/diy.zh-CN.ts'),
      'utf8',
    );
    expect(adminPreview, '管理端预览 无省额退化').toContain("return '拼'");
    expect(mobileLogic, 'C 端逻辑 无省额退化').toContain(
      "translate('diy.activityStrip.groupBuyBadge')",
    );
    expect(zhDiy).toContain("groupBuyBadge: '拼'");

    // 三类 loader 并行取数、单类失败隔离（只有三类全失败才上抛）
    for (const [label, source] of [
      ['管理端预览', adminPreview],
      ['C 端预览', mobilePreview],
    ] as const) {
      expect(source, `${label} 并行取数`).toContain('Promise.allSettled');
      expect(source, `${label} 手动模式直出`).toContain("mode === 'manual'");
    }
    for (const loader of [
      'loadSeckillSessions',
      'loadLimitedActivities',
      'loadDiscounts',
    ]) {
      expect(adminPreview, `管理端缺少 loader ${loader}`).toContain(loader);
      expect(mobilePreview, `C 端缺少 loader ${loader}`).toContain(loader);
    }

    // 全场折扣没有商品清单：两端缩略图都用取数层补的 coverImage（商品图优先），
    // 不退回「限」字色块；取不到图才回落首字兜底
    for (const [label, source] of [
      ['管理端预览', adminPreview],
      ['C 端逻辑', mobileLogic],
    ] as const) {
      expect(source, `${label} 折扣缩略图优先商品图`).toContain(
        'discount.goodsList[0]?.goodsImage || discount.coverImage',
      );
    }
    for (const [label, file] of [
      [
        '管理端',
        resolve(adminComponentsRoot, 'common/retail-preview/retail-data.ts'),
      ],
      ['C 端', resolve(mobileDiyRoot, 'retail-data.ts')],
    ] as const) {
      const source = readFileSync(file, 'utf8');
      expect(source, `${label} 缺图才补代表商品图`).toContain(
        'loadSalesTopGoodsImage',
      );
      // 代表图 = 全店销量 Top1 商品（真实参与全场折扣，不伪造）
      expect(source, `${label} 代表图取销量 Top1`).toContain(
        "createGoodsSortParams('sales'",
      );
      expect(source, `${label} coverImage 由取数层解析`).toContain(
        'coverImage:',
      );
    }

    // 某类无活动只隐藏该卡，剩余卡按实际卡数等宽重排（§5.5.6）；
    // 列数/展示数量已下线，按实际卡数自动排布（一行最多三张）
    for (const source of [adminPreview, mobileLogic]) {
      expect(source).toContain('stripGridColumns');
    }

    // 默认策略两端一致：无数据隐藏（整条不渲染）、失效隐藏
    expect(adminTypes).toContain("emptyStrategy: 'hide'");
    expect(adminTypes).toContain("invalidStrategy: 'hide'");
    expect(mobilePreview).toContain("emptyStrategy || 'hide'");
    expect(mobilePreview).toContain("invalidStrategy || 'hide'");
  });
});
