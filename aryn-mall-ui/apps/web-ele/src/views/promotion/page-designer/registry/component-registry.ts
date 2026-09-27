import type { Component } from 'vue';

import type { ComponentDefinition } from '../schema/types';

import type { PageDesignType } from '#/api/promotion/page-design';

import { defineAsyncComponent } from 'vue';

import {
  createCountdownDefaults,
  validateCountdown,
} from '../../page-design/components/countdown/types';
import {
  createDiscountDefaults,
  validateDiscount,
} from '../../page-design/components/discount/types';
import {
  createBottomNavDefaults,
  createCouponComboDefaults,
  createGoodsWaterfallDefaults,
  createMemberBenefitsDefaults,
  createServicePromiseDefaults,
  createVideoLiveDefaults,
  validateBottomNav,
  validateCouponCombo,
  validateGoodsWaterfall,
  validateMemberBenefits,
  validateServicePromise,
  validateVideoLive,
} from '../../page-design/components/extension/types';
import {
  createGoodsGroupDefaults,
  validateGoodsGroup,
} from '../../page-design/components/goods-group/types';
import {
  createGoodsRankingDefaults,
  validateGoodsRanking,
} from '../../page-design/components/goods-ranking/types';
import {
  createGoodsScrollDefaults,
  validateGoodsScroll,
} from '../../page-design/components/goods-scroll/types';
import {
  createLimitedActivityDefaults,
  validateLimitedActivity,
} from '../../page-design/components/limited-activity/types';
import {
  createMarketingEntryDefaults,
  validateMarketingEntry,
} from '../../page-design/components/marketing-entry/types';
import {
  createReplenishCardDefaults,
  validateReplenishCard,
} from '../../page-design/components/replenish-card/types';
import {
  createSeckillDefaults,
  validateSeckill,
} from '../../page-design/components/seckill/types';
import {
  createShipWorkbenchDefaults,
  validateShipWorkbench,
} from '../../page-design/components/ship-workbench/types';
import {
  createShopInfoDefaults,
  validateShopInfo,
} from '../../page-design/components/shop-info/types';
import { cloneDesignerValue } from '../schema/clone';

export const legacyComponentTypes = [
  'category-nav',
  'coupon-receive',
  'custom-html',
  'gap',
  'goods',
  'image-ad',
  'notice',
  'rich-text',
  'search-bar',
  'swiper-banner',
  'tab-nav',
  'title-text',
] as const;

export type LegacyComponentType = (typeof legacyComponentTypes)[number];

export const retailComponentTypes = [
  'goods-group',
  'goods-ranking',
  'goods-scroll',
  'limited-activity',
  'seckill',
  'discount',
  'countdown',
  'marketing-entry',
  'shop-info',
  'ship-workbench',
  'replenish-card',
] as const;

export type RetailComponentType = (typeof retailComponentTypes)[number];

export const extensionComponentTypes = [
  'goods-waterfall',
  'coupon-combo',
  'member-benefits',
  'service-promise',
  'bottom-nav',
  'video-live',
] as const;

export type ExtensionComponentType = (typeof extensionComponentTypes)[number];
export type RegisteredComponentType =
  ExtensionComponentType | LegacyComponentType | RetailComponentType;

export interface RegisteredComponentDefinition extends ComponentDefinition<
  Record<string, unknown>
> {
  preview: Component;
  settings: Component;
  supportedTerminals: ['admin', 'uniapp'];
}

const commonStyle = {
  bgColorDirection: 'to right',
  bgEndColor: '',
  bgPicUrl: '',
  bgStartColor: 'rgba(255, 255, 255, 1)',
  styleBottomMargin: 0,
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

function defineLegacyComponent(
  type: LegacyComponentType,
  label: string,
  category: string,
  defaults: Record<string, unknown>,
  preview: () => Promise<unknown>,
  settings: () => Promise<unknown>,
): RegisteredComponentDefinition {
  return {
    category,
    createDefaultProps: () => cloneDesignerValue(defaults),
    label,
    preview: defineAsyncComponent(preview as never),
    settings: defineAsyncComponent(settings as never),
    supportedTerminals: ['admin', 'uniapp'],
    type,
    validate: (props) =>
      props && Object.keys(props).length > 0 ? [] : ['组件配置不能为空'],
    version: 1,
  };
}

function defineRetailComponent<TProps extends Record<string, unknown>>(
  type: RetailComponentType,
  label: string,
  category: string,
  defaults: TProps,
  validate: (props: TProps) => string[],
  preview: () => Promise<unknown>,
  settings: () => Promise<unknown>,
): RegisteredComponentDefinition {
  return {
    category,
    createDefaultProps: () => cloneDesignerValue(defaults),
    label,
    preview: defineAsyncComponent(preview as never),
    settings: defineAsyncComponent(settings as never),
    supportedTerminals: ['admin', 'uniapp'],
    type,
    validate: (props) => validate(props as TProps),
    version: 1,
  };
}

function defineExtensionComponent<TProps extends Record<string, unknown>>(
  type: ExtensionComponentType,
  label: string,
  category: string,
  defaults: TProps,
  validate: (props: TProps) => string[],
  preview: () => Promise<unknown>,
  settings: () => Promise<unknown>,
): RegisteredComponentDefinition {
  return {
    category,
    createDefaultProps: () => cloneDesignerValue(defaults),
    label,
    preview: defineAsyncComponent(preview as never),
    settings: defineAsyncComponent(settings as never),
    supportedTerminals: ['admin', 'uniapp'],
    type,
    validate: (props) => validate(props as TProps),
    version: 1,
  };
}

export const componentRegistry: Record<
  RegisteredComponentType,
  RegisteredComponentDefinition
> = {
  'category-nav': defineLegacyComponent(
    'category-nav',
    '分类导航',
    '导航广告',
    {
      commonStyle: {
        ...commonStyle,
        styleBottomMargin: 10,
        styleTopMargin: 10,
      },
      fontColor: '#303133',
      imgRadius: 0,
      imgSize: 25,
      navList: [],
      scrollShow: false,
      showNum: 4,
    },
    () => import('../../page-design/components/category-nav/index.vue'),
    () => import('../../page-design/components/category-nav/setting.vue'),
  ),
  'coupon-receive': defineLegacyComponent(
    'coupon-receive',
    '优惠券',
    '营销活动',
    {
      commonStyle,
      showNum: 3,
      showReceiveBtn: true,
      showStyle: '2',
    },
    () => import('../../page-design/components/coupon-receive/index.vue'),
    () => import('../../page-design/components/coupon-receive/setting.vue'),
  ),
  'custom-html': {
    category: '基础组件',
    createDefaultProps: () => cloneDesignerValue({ height: 300, html: '' }),
    label: '自定义HTML',
    preview: defineAsyncComponent(
      () => import('../../page-design/components/custom-html/index.vue'),
    ),
    settings: defineAsyncComponent(
      () => import('../../page-design/components/custom-html/setting.vue'),
    ),
    supportedTerminals: ['admin', 'uniapp'],
    type: 'custom-html',
    validate: (props) => {
      const errors: string[] = [];
      const html = String((props as { html?: unknown })?.html ?? '').trim();
      if (!html) errors.push('HTML 内容不能为空');
      else if (html.length > 50_000)
        errors.push('HTML 内容不能超过 50000 字符');
      return errors;
    },
    version: 1,
  },
  gap: defineLegacyComponent(
    'gap',
    '辅助空白',
    '基础组件',
    {
      commonStyle,
      gapBgColorDirection: 'to right',
      gapBgEndColor: '',
      gapBgStartColor: '',
      height: 30,
    },
    () => import('../../page-design/components/gap/index.vue'),
    () => import('../../page-design/components/gap/setting.vue'),
  ),
  goods: defineLegacyComponent(
    'goods',
    '手选商品',
    '商品经营',
    {
      commonStyle,
      goodsList: [],
      showBuyBtn: true,
      showGoodsName: true,
      showPrice: true,
      showStyle: '2',
    },
    () => import('../../page-design/components/goods/index.vue'),
    () => import('../../page-design/components/goods/setting.vue'),
  ),
  'image-ad': defineLegacyComponent(
    'image-ad',
    '图片广告',
    '导航广告',
    {
      commonStyle,
      imageList: [],
      showStyle: '1',
    },
    () => import('../../page-design/components/image-ad/index.vue'),
    () => import('../../page-design/components/image-ad/setting.vue'),
  ),
  notice: defineLegacyComponent(
    'notice',
    '公告',
    '基础组件',
    {
      bgColor: '#fff8e6',
      commonStyle,
      content: '欢迎光临',
      iconColor: '#ff9900',
      textColor: '#5a3c14',
    },
    () => import('../../page-design/components/notice/index.vue'),
    () => import('../../page-design/components/notice/setting.vue'),
  ),
  'rich-text': defineLegacyComponent(
    'rich-text',
    '富文本',
    '基础组件',
    {
      commonStyle,
      content: '<p>请输入内容</p>',
    },
    () => import('../../page-design/components/rich-text/index.vue'),
    () => import('../../page-design/components/rich-text/setting.vue'),
  ),
  'search-bar': defineLegacyComponent(
    'search-bar',
    '搜索框',
    '基础组件',
    {
      backgroundColor: '#ffffff',
      borderRadius: 16,
      commonStyle,
      height: 36,
      placeholder: '搜索商品',
      textAlign: 'left',
    },
    () => import('../../page-design/components/search-bar/index.vue'),
    () => import('../../page-design/components/search-bar/setting.vue'),
  ),
  'swiper-banner': defineLegacyComponent(
    'swiper-banner',
    '轮播图',
    '导航广告',
    {
      borderRadius: 0,
      commonStyle: {
        ...commonStyle,
        styleLeftMargin: 10,
        styleRightMargin: 10,
      },
      height: 180,
      imageList: [],
      indicatorActiveColor: '#ffffff',
      indicatorColor: 'rgba(0, 0, 0, 0.3)',
      indicatorDots: true,
      interval: 3000,
    },
    () => import('../../page-design/components/swiper-banner/index.vue'),
    () => import('../../page-design/components/swiper-banner/setting.vue'),
  ),
  'tab-nav': defineLegacyComponent(
    'tab-nav',
    '图文导航',
    '导航广告',
    {
      commonStyle,
      fontColor: '#303133',
      imgRadius: 0,
      imgSize: 40,
      navList: [
        { link: null, title: '导航一', url: '' },
        { link: null, title: '导航二', url: '' },
        { link: null, title: '导航三', url: '' },
        { link: null, title: '导航四', url: '' },
      ],
      // 显示方式：grid 平铺 | scroll 横向滚动 | pager 分页滑动
      displayMode: 'grid',
      scrollShow: false,
      showNum: 4,
      pageRows: 3,
      indicatorDots: true,
      indicatorColor: 'rgba(0, 0, 0, 0.2)',
      indicatorActiveColor: '#1989fa',
      type: '1',
    },
    () => import('../../page-design/components/tab-nav/index.vue'),
    () => import('../../page-design/components/tab-nav/setting.vue'),
  ),
  'title-text': defineLegacyComponent(
    'title-text',
    '标题',
    '基础组件',
    {
      bottomLine: false,
      commonStyle,
      desc: '我是描述',
      descColor: '#303133',
      descSize: 12,
      descWeight: '0',
      link: null,
      moreBtn: false,
      moreBtnColor: '#303133',
      moreBtnSize: 14,
      moreBtnStyle: '1',
      moreBtnText: '查看更多',
      moreBtnWeight: '0',
      showDesc: false,
      showType: '1',
      title: '我是标题',
      titleColor: '#303133',
      titleSize: 14,
      titleWeight: '0',
    },
    () => import('../../page-design/components/title-text/index.vue'),
    () => import('../../page-design/components/title-text/setting.vue'),
  ),
  'goods-group': defineRetailComponent(
    'goods-group',
    '商品分组',
    '商品经营',
    createGoodsGroupDefaults(),
    validateGoodsGroup,
    () => import('../../page-design/components/goods-group/index.vue'),
    () => import('../../page-design/components/goods-group/setting.vue'),
  ),
  'goods-scroll': defineRetailComponent(
    'goods-scroll',
    '商品横滑条',
    '商品经营',
    createGoodsScrollDefaults(),
    validateGoodsScroll,
    () => import('../../page-design/components/goods-scroll/index.vue'),
    () => import('../../page-design/components/goods-scroll/setting.vue'),
  ),
  'goods-ranking': defineRetailComponent(
    'goods-ranking',
    '商品排行',
    '商品经营',
    createGoodsRankingDefaults(),
    validateGoodsRanking,
    () => import('../../page-design/components/goods-ranking/index.vue'),
    () => import('../../page-design/components/goods-ranking/setting.vue'),
  ),
  'limited-activity': defineRetailComponent(
    'limited-activity',
    '拼团',
    '营销活动',
    createLimitedActivityDefaults(),
    validateLimitedActivity,
    () => import('../../page-design/components/limited-activity/index.vue'),
    () => import('../../page-design/components/limited-activity/setting.vue'),
  ),
  discount: defineRetailComponent(
    'discount',
    '折扣',
    '营销活动',
    createDiscountDefaults(),
    validateDiscount,
    () => import('../../page-design/components/discount/index.vue'),
    () => import('../../page-design/components/discount/setting.vue'),
  ),
  seckill: defineRetailComponent(
    'seckill',
    '秒杀',
    '营销活动',
    createSeckillDefaults(),
    validateSeckill,
    () => import('../../page-design/components/seckill/index.vue'),
    () => import('../../page-design/components/seckill/setting.vue'),
  ),
  countdown: defineRetailComponent(
    'countdown',
    '倒计时',
    '营销活动',
    createCountdownDefaults(),
    validateCountdown,
    () => import('../../page-design/components/countdown/index.vue'),
    () => import('../../page-design/components/countdown/setting.vue'),
  ),
  'marketing-entry': defineRetailComponent(
    'marketing-entry',
    '营销入口',
    '导航广告',
    createMarketingEntryDefaults(),
    validateMarketingEntry,
    () => import('../../page-design/components/marketing-entry/index.vue'),
    () => import('../../page-design/components/marketing-entry/setting.vue'),
  ),
  'shop-info': defineRetailComponent(
    'shop-info',
    '店铺信息',
    '店铺服务',
    createShopInfoDefaults(),
    validateShopInfo,
    () => import('../../page-design/components/shop-info/index.vue'),
    () => import('../../page-design/components/shop-info/setting.vue'),
  ),
  'ship-workbench': defineRetailComponent(
    'ship-workbench',
    '船舶工作台',
    '店铺服务',
    createShipWorkbenchDefaults(),
    validateShipWorkbench,
    () => import('../../page-design/components/ship-workbench/index.vue'),
    () => import('../../page-design/components/ship-workbench/setting.vue'),
  ),
  'replenish-card': defineRetailComponent(
    'replenish-card',
    '补给单卡片',
    '店铺服务',
    createReplenishCardDefaults(),
    validateReplenishCard,
    () => import('../../page-design/components/replenish-card/index.vue'),
    () => import('../../page-design/components/replenish-card/setting.vue'),
  ),
  'goods-waterfall': defineExtensionComponent(
    'goods-waterfall',
    '商品瀑布流',
    '商品经营',
    createGoodsWaterfallDefaults(),
    validateGoodsWaterfall,
    () =>
      import('../../page-design/components/extension/goods-waterfall/index.vue'),
    () =>
      import('../../page-design/components/extension/goods-waterfall/setting.vue'),
  ),
  'coupon-combo': defineExtensionComponent(
    'coupon-combo',
    '优惠券组合',
    '营销活动',
    createCouponComboDefaults(),
    validateCouponCombo,
    () =>
      import('../../page-design/components/extension/coupon-combo/index.vue'),
    () =>
      import('../../page-design/components/extension/coupon-combo/setting.vue'),
  ),
  'member-benefits': defineExtensionComponent(
    'member-benefits',
    '会员权益',
    '营销活动',
    createMemberBenefitsDefaults(),
    validateMemberBenefits,
    () =>
      import('../../page-design/components/extension/member-benefits/index.vue'),
    () =>
      import('../../page-design/components/extension/member-benefits/setting.vue'),
  ),
  'service-promise': defineExtensionComponent(
    'service-promise',
    '服务承诺',
    '店铺服务',
    createServicePromiseDefaults(),
    validateServicePromise,
    () =>
      import('../../page-design/components/extension/service-promise/index.vue'),
    () =>
      import('../../page-design/components/extension/service-promise/setting.vue'),
  ),
  'bottom-nav': defineExtensionComponent(
    'bottom-nav',
    '底部导航',
    '导航广告',
    createBottomNavDefaults(),
    validateBottomNav,
    () => import('../../page-design/components/extension/bottom-nav/index.vue'),
    () =>
      import('../../page-design/components/extension/bottom-nav/setting.vue'),
  ),
  'video-live': defineExtensionComponent(
    'video-live',
    '视频/直播入口',
    '内容',
    createVideoLiveDefaults(),
    validateVideoLive,
    () => import('../../page-design/components/extension/video-live/index.vue'),
    () =>
      import('../../page-design/components/extension/video-live/setting.vue'),
  ),
};

/**
 * 商品详情页（pageType='2'）允许装修的组件白名单：
 * 商品类 + 图片类 + 文本类 + 营销类 + 辅助类。
 * 导航/搜索/底部导航/分类导航等页面级组件不在此列。
 */
export const detailPageAllowedComponents: ReadonlySet<RegisteredComponentType> =
  new Set([
    'coupon-combo',
    'coupon-receive',
    'custom-html',
    'discount',
    'gap',
    'goods',
    'goods-group',
    'goods-ranking',
    'goods-scroll',
    'goods-waterfall',
    'image-ad',
    'limited-activity',
    'notice',
    'rich-text',
    'seckill',
    'swiper-banner',
    'title-text',
  ]);

/**
 * 分类页（pageType='3'）允许装修的组件白名单：
 * 图片类 + 商品类 + 营销类 + 文本类 + 辅助类。
 * 排除搜索栏/分类导航/图文导航/底部导航/船舶工作台/补给单卡片等页面级组件。
 */
export const categoryPageAllowedComponents: ReadonlySet<RegisteredComponentType> =
  new Set([
    'countdown',
    'coupon-combo',
    'coupon-receive',
    'custom-html',
    'discount',
    'gap',
    'goods',
    'goods-group',
    'goods-ranking',
    'goods-scroll',
    'goods-waterfall',
    'image-ad',
    'limited-activity',
    'notice',
    'rich-text',
    'seckill',
    'swiper-banner',
    'title-text',
  ]);

/**
 * 个人中心页（pageType='4'）允许装修的组件白名单：
 * 图片类 + 营销类 + 文本类 + 自定义HTML + 辅助类。
 * 个人中心页通常不展示商品流，故排除商品类与搜索/导航/底部导航等页面级组件。
 */
export const userCenterAllowedComponents: ReadonlySet<RegisteredComponentType> =
  new Set([
    'countdown',
    'coupon-combo',
    'coupon-receive',
    'custom-html',
    'discount',
    'gap',
    'image-ad',
    'limited-activity',
    'notice',
    'rich-text',
    'seckill',
    'swiper-banner',
    'title-text',
  ]);

/** 各特殊页面类型对应的组件白名单（微页面/商城首页不做限制） */
export function getAllowedComponentsForPageType(
  pageType: PageDesignType | undefined,
): ReadonlySet<RegisteredComponentType> | undefined {
  switch (pageType) {
    case '2': {
      return detailPageAllowedComponents;
    }
    case '3': {
      return categoryPageAllowedComponents;
    }
    case '4': {
      return userCenterAllowedComponents;
    }
    default: {
      return undefined;
    }
  }
}

export function getComponentDefinition(type: string) {
  return componentRegistry[type as RegisteredComponentType];
}
