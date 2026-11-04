import type { DiyCommonStyle } from '@vben/types';

import type { DecorationLink } from '../../../page-designer/schema/types';

const emptyLink = (): DecorationLink => ({
  params: {},
  path: '',
  type: 'custom',
});

const extensionCommonStyle = {
  bgColorDirection: 'to right',
  bgEndColor: '',
  bgPicUrl: '',
  bgStartColor: 'rgba(255, 255, 255, 1)',
  styleBottomMargin: 10,
  styleBottomPadding: 0,
  styleLbRadius: 0,
  styleLeftMargin: 0,
  styleLeftPadding: 0,
  styleLtRadius: 0,
  styleRbRadius: 0,
  styleRightMargin: 0,
  styleRightPadding: 0,
  styleRtRadius: 0,
  styleTopMargin: 0,
  styleTopPadding: 0,
};

/**
 * 多 Tab 商品流自动 Tab 支持的排序值（持久化在 `dataSource.sort`）。
 *
 * 与后端 `DefaultPageDesignDocumentValidator#validateGoodsTabs` 白名单、
 * C 端 `SORT_MAP` 严格一致：新增排序值必须三端同步。
 *
 * 其中 `bargain` 为旧版「折扣力度优先」，仅兼容历史装修文档，管理端不再提供新选；
 * `member_price` / `clearance` 是专区选品语义（见 `GOODS_TAB_MEMBER_SORT` 等）。
 */
export const GOODS_TAB_SORTS = [
  'sales',
  'create_time',
  'sales_price',
  'bargain',
  'member_price',
  'clearance',
] as const;

export type GoodsTabSort = (typeof GOODS_TAB_SORTS)[number];

/** 普通排序：销量 / 新品 / 价格（专区 Tab 不参与普通排序） */
export const GOODS_TAB_NORMAL_SORTS = [
  'sales',
  'create_time',
  'sales_price',
] as const;

/** 专区选品：会员专区（商品级会员标签），持久化在 `dataSource.sort` */
export const GOODS_TAB_MEMBER_SORT = 'member_price';

/** 专区选品：低价捡漏（商品级出清标签），持久化在 `dataSource.sort` */
export const GOODS_TAB_CLEARANCE_SORT = 'clearance';

/** 旧版「折扣力度优先」：仅兼容历史装修文档，管理端不再提供新选 */
export const GOODS_TAB_LEGACY_BARGAIN_SORT = 'bargain';

/** 低价捡漏折扣率阈值缺省值：原价 ≥ 售价 × bargainRate 的商品才进入 Tab */
export const DEFAULT_BARGAIN_RATE = 1.15;
/** 折扣率阈值合法区间 (1.00, 9.99]，与后端 apiPage 校验同口径 */
export const BARGAIN_RATE_MIN_EXCLUSIVE = 1;
export const BARGAIN_RATE_MAX_INCLUSIVE = 9.99;
/** 会员权益条单段文案最大长度，与后端校验同口径 */
export const MEMBER_BANNER_COPY_MAX_LENGTH = 30;

/** 多 Tab 商品流的数据源模式：分类/排序自动、手选商品 */
export const GOODS_TAB_DATA_SOURCE_MODES = ['automatic', 'manual'] as const;

export type GoodsTabDataSourceMode =
  (typeof GOODS_TAB_DATA_SOURCE_MODES)[number];

export interface ExtensionDataSourceConfig {
  cacheTtl?: number;
  categoryId?: string;
  mode: 'automatic' | 'manual' | 'ranking';
  /**
   * 排序 / 专区选品（同一字段）：
   * - 普通排序：`sales` / `create_time` / `sales_price`
   * - 专区选品：`member_price`（会员标签商品）/ `clearance`（出清标签商品）
   * - 旧版兼容：`bargain`（折扣力度优先，管理端已不再提供新选）
   */
  sort?: string;
  targetIds?: string[];
  /**
   * 旧版「折扣力度优先」的折扣率阈值：仅 `sort='bargain'` 时生效。
   * 区间 (1.00, 9.99]，缺省按 1.15 处理；管理端已不再展示该输入。
   */
  bargainRate?: number;
}

export interface GoodsWaterfallProps extends Record<string, unknown> {
  columns: 2 | 3;
  commonStyle: DiyCommonStyle;
  count: number;
  dataSource: ExtensionDataSourceConfig;
  showOriginalPrice: boolean;
  showPrice: boolean;
  showSales: boolean;
  title: string;
}

export interface GoodsRecommendProps extends Record<string, unknown> {
  commonStyle: DiyCommonStyle;
  count: number;
  dataSource: ExtensionDataSourceConfig;
  showOriginalPrice: boolean;
  showPrice: boolean;
  title: string;
}

/**
 * Tab 级会员权益条配置（F2）。
 *
 * enabled=true 时 C 端在 Tab 头下方渲染一条权益横幅；会员/游客两段文案都留空
 * 则走 C 端内置文案，因此两段字段均可选。持久化在 `tab.memberBanner`。
 */
export interface GoodsTabMemberBanner {
  enabled: boolean;
  /** 会员文案（已登录且有等级），≤30 字；留空走内置文案 */
  memberCopy?: string;
  /** 游客文案（未登录 / 登录无等级），≤30 字；留空走内置文案 */
  guestCopy?: string;
}

/** 多 Tab 商品流中的单个 Tab：每个 Tab 拥有独立的数据源与分页状态 */
export interface GoodsTabConfig {
  dataSource: ExtensionDataSourceConfig;
  id: string;
  /** 会员权益条（F2），缺省视为关闭 */
  memberBanner?: GoodsTabMemberBanner;
  title: string;
}

export interface GoodsTabsProps extends Record<string, unknown> {
  columns: 2 | 3;
  commonStyle: DiyCommonStyle;
  count: number;
  emptyStrategy: 'hide' | 'placeholder';
  invalidStrategy: 'hide' | 'placeholder';
  showOriginalPrice: boolean;
  showPrice: boolean;
  showSales: boolean;
  stickyTabs: boolean;
  tabs: GoodsTabConfig[];
}

export function createGoodsWaterfallDefaults(): GoodsWaterfallProps {
  return {
    columns: 2 as 2 | 3,
    commonStyle: { ...extensionCommonStyle },
    count: 6,
    dataSource: {
      cacheTtl: 60,
      categoryId: '',
      mode: 'automatic' as const,
      sort: 'sales',
      targetIds: [] as string[],
    },
    showOriginalPrice: true,
    showPrice: true,
    showSales: true,
    title: '猜你喜欢',
  };
}

export function validateGoodsWaterfall(props: Record<string, unknown>) {
  const errors: string[] = [];
  const dataSource = props.dataSource as ExtensionDataSourceConfig | undefined;
  if (!props.count || Number(props.count) <= 0)
    errors.push('展示数量必须大于 0');
  if (
    dataSource?.mode === 'manual' &&
    (dataSource.targetIds ?? []).length === 0
  ) {
    errors.push('手选商品不能为空');
  }
  return errors;
}

export function createMemberBenefitsDefaults() {
  return {
    commonStyle: { ...extensionCommonStyle },
    dataSource: { mode: 'manual' as const, targetIds: [] as string[] },
    entries: [
      {
        description: '专属折扣与积分',
        iconUrl: '',
        id: 'benefit-1',
        link: emptyLink(),
        title: '会员权益',
      },
    ],
    title: '会员中心',
  };
}

export function validateMemberBenefits(props: Record<string, unknown>) {
  const entries = (props.entries ?? []) as Array<{ title?: string }>;
  if (!Array.isArray(entries) || entries.length === 0)
    return ['请至少配置一个权益项'];
  return entries.some((entry) => !entry.title) ? ['权益项标题不能为空'] : [];
}

export function createServicePromiseDefaults() {
  return {
    commonStyle: { ...extensionCommonStyle },
    items: [
      {
        description: '7 天无理由退货',
        iconUrl: '',
        id: 'promise-1',
        title: '放心退',
      },
      {
        description: '官方自营发货',
        iconUrl: '',
        id: 'promise-2',
        title: '正品保障',
      },
    ],
    title: '服务保障',
  };
}

export function validateServicePromise(props: Record<string, unknown>) {
  const items = (props.items ?? []) as Array<{ title?: string }>;
  if (!Array.isArray(items) || items.length === 0)
    return ['请至少配置一条服务承诺'];
  return items.some((item) => !item.title) ? ['服务承诺标题不能为空'] : [];
}

export function createVideoLiveDefaults() {
  return {
    commonStyle: { ...extensionCommonStyle },
    coverUrl: '',
    liveId: '',
    mode: 'live' as 'live' | 'video',
    title: '精彩视频',
    videoUrl: '',
  };
}

export function validateVideoLive(props: Record<string, unknown>) {
  if (props.mode === 'video' && !props.videoUrl) return ['请上传视频'];
  return [];
}

/** 图片魔方布局：格数由布局决定，items 长度必须与之一致 */
export type ImageCubeLayout = '1' | '1+2' | '1+3' | '2h' | '2v' | '4';

/** 各布局对应的格子数量 */
export const IMAGE_CUBE_LAYOUT_CELLS: Record<ImageCubeLayout, number> = {
  '1': 1,
  '1+2': 3,
  '1+3': 4,
  '2h': 2,
  '2v': 2,
  '4': 4,
};

export interface ImageCubeItem {
  id: string;
  link: DecorationLink;
  url: string;
}

export function createImageCubeDefaults() {
  return {
    commonStyle: { ...extensionCommonStyle },
    items: [1, 2, 3, 4].map((index) => ({
      id: `cube-${index}`,
      link: emptyLink(),
      url: '',
    })),
    layout: '4' as ImageCubeLayout,
  };
}

export function validateImageCube(props: Record<string, unknown>) {
  const layout = props.layout as ImageCubeLayout | undefined;
  if (!layout || !(layout in IMAGE_CUBE_LAYOUT_CELLS))
    return ['图片魔方布局不合法'];
  const items = (props.items ?? []) as ImageCubeItem[];
  if (!Array.isArray(items) || items.length !== IMAGE_CUBE_LAYOUT_CELLS[layout])
    return [`当前布局需要 ${IMAGE_CUBE_LAYOUT_CELLS[layout]} 张图片配置`];
  if (!items.some((item) => item.url)) return ['请至少为一张图片填入地址'];
  return [];
}

export function createGoodsRecommendDefaults(): GoodsRecommendProps {
  return {
    commonStyle: { ...extensionCommonStyle },
    count: 6,
    dataSource: {
      cacheTtl: 60,
      mode: 'automatic' as const,
      targetIds: [] as string[],
    },
    showOriginalPrice: true,
    showPrice: true,
    title: '看了又看',
  };
}

export function validateGoodsRecommend(props: Record<string, unknown>) {
  const errors: string[] = [];
  const dataSource = props.dataSource as ExtensionDataSourceConfig | undefined;
  if (!props.count || Number(props.count) <= 0)
    errors.push('展示数量必须大于 0');
  if (
    dataSource?.mode === 'manual' &&
    (dataSource.targetIds ?? []).length === 0
  ) {
    errors.push('手选商品不能为空');
  }
  return errors;
}

export function createGoodsTabsDefaults(): GoodsTabsProps {
  return {
    columns: 2,
    commonStyle: { ...extensionCommonStyle },
    count: 10,
    emptyStrategy: 'placeholder',
    invalidStrategy: 'hide',
    showOriginalPrice: true,
    showPrice: true,
    showSales: true,
    stickyTabs: true,
    tabs: [
      {
        dataSource: {
          cacheTtl: 0,
          categoryId: '',
          mode: 'automatic',
          sort: 'sales',
          targetIds: [],
        },
        id: 'recommend',
        title: '猜你喜欢',
      },
      {
        dataSource: {
          cacheTtl: 0,
          categoryId: '',
          mode: 'automatic',
          sort: GOODS_TAB_CLEARANCE_SORT,
          targetIds: [],
        },
        id: 'bargain',
        title: '低价捡漏',
      },
      {
        dataSource: {
          cacheTtl: 0,
          categoryId: '',
          mode: 'automatic',
          sort: GOODS_TAB_MEMBER_SORT,
          targetIds: [],
        },
        id: 'member',
        title: '会员专区',
      },
    ],
  };
}

/**
 * 折扣率阈值归一化：缺省、非数字或越界一律回落默认值 1.15。
 *
 * 管理端画布预览与 C 端取数都按该口径发请求：后端对非法区间直接拒绝，
 * 存量配置里写了脏值时也不该让预览整块报错。
 */
export function normalizeBargainRate(value: unknown): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return DEFAULT_BARGAIN_RATE;
  if (
    parsed <= BARGAIN_RATE_MIN_EXCLUSIVE ||
    parsed > BARGAIN_RATE_MAX_INCLUSIVE
  ) {
    return DEFAULT_BARGAIN_RATE;
  }
  // 按分位取整，避免 1.1500000000000001 这类浮点噪音写进持久化 JSON
  return Math.round(parsed * 100) / 100;
}

function isValidBargainRate(value: unknown): boolean {
  const parsed = Number(value);
  return (
    Number.isFinite(parsed) &&
    parsed > BARGAIN_RATE_MIN_EXCLUSIVE &&
    parsed <= BARGAIN_RATE_MAX_INCLUSIVE
  );
}

/** 文案长度按 Unicode 码点计（中文与 emoji 都算 1 字），与后端 codePointCount 同口径 */
function copyLength(value: unknown): number {
  return [...String(value ?? '')].length;
}

/** Tab 级会员权益条校验（F2），与后端 validateMemberBanner 同口径 */
function validateMemberBanner(
  banner: unknown,
  tabLabel: string,
  errors: string[],
) {
  if (banner === undefined || banner === null) return;
  if (typeof banner !== 'object' || Array.isArray(banner)) {
    errors.push(`「${tabLabel}」会员权益条配置格式不正确`);
    return;
  }
  const config = banner as GoodsTabMemberBanner;
  if (config.enabled !== true) return;
  // 文案留空走 C 端内置模板，不报错；开启时才校验已填文案长度
  if (copyLength(config.memberCopy) > MEMBER_BANNER_COPY_MAX_LENGTH) {
    errors.push(
      `「${tabLabel}」会员权益条会员文案不能超过 ${MEMBER_BANNER_COPY_MAX_LENGTH} 字`,
    );
  }
  if (copyLength(config.guestCopy) > MEMBER_BANNER_COPY_MAX_LENGTH) {
    errors.push(
      `「${tabLabel}」会员权益条游客文案不能超过 ${MEMBER_BANNER_COPY_MAX_LENGTH} 字`,
    );
  }
}

export function validateGoodsTabs(props: Record<string, unknown>) {
  const errors: string[] = [];
  const tabs = (props.tabs ?? []) as GoodsTabConfig[];
  if (!props.count || Number(props.count) <= 0)
    errors.push('展示数量必须大于 0');
  if (!Array.isArray(tabs) || tabs.length === 0)
    errors.push('请至少配置一个 Tab');
  if (tabs.length > 6) errors.push('最多配置 6 个 Tab');
  const ids = new Set<string>();
  tabs.forEach((tab, index) => {
    const tabLabel = tab.title || `Tab ${index + 1}`;
    if (!tab.title) errors.push(`第 ${index + 1} 个 Tab 标题不能为空`);
    if (tab.id) {
      if (ids.has(tab.id)) errors.push(`Tab 标识重复：${tab.id}`);
      ids.add(tab.id);
    }
    const dataSource = tab.dataSource;
    if (
      !dataSource ||
      !GOODS_TAB_DATA_SOURCE_MODES.includes(
        dataSource.mode as GoodsTabDataSourceMode,
      )
    ) {
      errors.push(`「${tabLabel}」数据源无效`);
      return;
    }
    if (
      dataSource.mode === 'manual' &&
      (dataSource.targetIds ?? []).length === 0
    ) {
      errors.push(`「${tabLabel}」手选商品不能为空`);
    }
    if (dataSource.mode === 'automatic') {
      // 缺省 sort 按 sales 兜底（与 C 端、后端校验一致），避免历史文档被误判
      const sort = dataSource.sort || 'sales';
      if (!GOODS_TAB_SORTS.includes(sort as GoodsTabSort)) {
        errors.push(
          `「${tabLabel}」排序方式不合法：${sort}（可选 ${GOODS_TAB_SORTS.join('/')}）`,
        );
      } else if (
        sort === 'bargain' &&
        dataSource.bargainRate !== undefined &&
        dataSource.bargainRate !== null &&
        !isValidBargainRate(dataSource.bargainRate)
      ) {
        errors.push(`「${tabLabel}」折扣率阈值必须在 (1.00, 9.99] 区间内`);
      }
    }
    validateMemberBanner(tab.memberBanner, tabLabel, errors);
  });
  return errors;
}

/**
 * 满减券阶梯的档数边界。
 *
 * 1 档没有对比意义；超过 4 档在 375px 宽度下每档只剩 80px 左右，
 * 「满 199 元」都会被挤断行。
 */
export const COUPON_LADDER_MIN_TIERS = 2;
export const COUPON_LADDER_MAX_TIERS = 4;

export function createCouponLadderDefaults() {
  return {
    badgeText: '',
    /**
     * 卡片自带白底，根节点不再铺白：否则两张卡之间的 10px 缝隙会被白色填满，
     * 渐变头图与内容卡会粘成一整块。
     */
    commonStyle: {
      ...extensionCommonStyle,
      bgStartColor: '',
      styleBottomMargin: 10,
      styleLeftMargin: 12,
      styleRightMargin: 12,
    },
    ctaLink: {
      params: {},
      path: '/sub-pages/promotion/coupon/coupon-list/index',
      type: 'custom' as const,
    } satisfies DecorationLink,
    ctaText: '去领券中心',
    dataSource: { mode: 'automatic' as const, targetIds: [] as string[] },
    noteText: '',
    showClaimBtn: true,
    showNum: 3,
    subtitle: '先领券 · 后加购 · 下单自动抵扣',
    title: '满减狂欢',
  };
}

/**
 * 满减券阶梯的发布校验。
 *
 * 档位数字不在校验范围内：它来自 coupon_info，运营改券即改档位，
 * schema 里没有任何金额字段可校验（这是与「自定义 HTML 手写满减」的关键区别）。
 */
export function validateCouponLadder(props: Record<string, unknown>) {
  const errors: string[] = [];
  if (!String(props.title ?? '').trim()) errors.push('标题不能为空');

  const showNum = Number(props.showNum);
  if (
    !Number.isInteger(showNum) ||
    showNum < COUPON_LADDER_MIN_TIERS ||
    showNum > COUPON_LADDER_MAX_TIERS
  ) {
    errors.push(
      `展示档数需在 ${COUPON_LADDER_MIN_TIERS}-${COUPON_LADDER_MAX_TIERS} 之间`,
    );
  }

  const dataSource = props.dataSource as
    undefined | { mode?: string; targetIds?: string[] };
  const targetIds = (dataSource?.targetIds ?? []).filter(Boolean);
  if (dataSource?.mode === 'manual' && targetIds.length === 0)
    errors.push('手选满减券不能为空');
  if (dataSource?.mode === 'manual' && targetIds.length > showNum)
    errors.push(`手选满减券最多 ${showNum} 张，超出部分不会被展示`);
  return errors;
}
