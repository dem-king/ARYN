import type { Component } from 'vue';

import type { ComponentDefinition } from '../schema/types';

import { defineAsyncComponent } from 'vue';

import {
  createCountdownDefaults,
  validateCountdown,
} from '../../page-design/components/countdown/types';
import {
  createGoodsGroupDefaults,
  validateGoodsGroup,
} from '../../page-design/components/goods-group/types';
import {
  createGoodsRankingDefaults,
  validateGoodsRanking,
} from '../../page-design/components/goods-ranking/types';
import {
  createLimitedActivityDefaults,
  validateLimitedActivity,
} from '../../page-design/components/limited-activity/types';
import {
  createMarketingEntryDefaults,
  validateMarketingEntry,
} from '../../page-design/components/marketing-entry/types';
import {
  createShopInfoDefaults,
  validateShopInfo,
} from '../../page-design/components/shop-info/types';
import { cloneDesignerValue } from '../schema/clone';

export const legacyComponentTypes = [
  'category-nav',
  'coupon-receive',
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
  'limited-activity',
  'countdown',
  'marketing-entry',
  'shop-info',
] as const;

export type RetailComponentType = (typeof retailComponentTypes)[number];
export type RegisteredComponentType = LegacyComponentType | RetailComponentType;

export interface RegisteredComponentDefinition
  extends ComponentDefinition<Record<string, unknown>> {
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
      scrollShow: false,
      showNum: 4,
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
    '限时活动',
    '营销活动',
    createLimitedActivityDefaults(),
    validateLimitedActivity,
    () => import('../../page-design/components/limited-activity/index.vue'),
    () => import('../../page-design/components/limited-activity/setting.vue'),
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
};

export function getComponentDefinition(type: string) {
  return componentRegistry[type as RegisteredComponentType];
}
