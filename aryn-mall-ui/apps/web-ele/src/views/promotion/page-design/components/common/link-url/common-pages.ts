/**
 * 装修链接「自定义路径」的常用 C 端页面。
 *
 * 运营记不住 `/sub-pages/...` 这类路由，手动输入又容易拼错（拼错后 C 端
 * 链接静默失效）。这里给出一份可点选的常用页面清单，选中后自动填入路径。
 *
 * 路径必须与 `aryn-mall-uniapp/src/pages.json` 保持一致，由
 * `page-designer/id-picker-contract.test.ts` 做跨仓校验。
 * 新增/调整 C 端页面时同步改这里，避免给运营一个打不开的“常用”入口。
 */
export interface CommonPagePreset {
  name: string;
  path: string;
}

export const COMMON_PAGE_PRESETS: CommonPagePreset[] = [
  { name: '首页', path: '/pages/home/index' },
  { name: '商品分类', path: '/pages/product/category/index' },
  { name: '购物车', path: '/pages/user/shopping-cart/index' },
  { name: '个人中心', path: '/pages/user/user-center/index' },
  { name: '商品搜索', path: '/sub-pages/product/goods-search/index' },
  { name: '商品列表', path: '/sub-pages/product/goods-list/index' },
  { name: '秒杀活动', path: '/pages/promotion/seckill' },
  { name: '折扣活动', path: '/pages/promotion/discount' },
  {
    name: '拼团活动列表',
    path: '/sub-pages/promotion/group-buy/group-buy-list/index',
  },
  {
    name: '优惠券列表',
    path: '/sub-pages/promotion/coupon/coupon-list/index',
  },
  {
    name: '我的优惠券',
    path: '/sub-pages/promotion/coupon/coupon-user/index',
  },
  { name: '订单列表', path: '/sub-pages/order/order-list/index' },
  { name: '收货地址', path: '/sub-pages/user/address/index' },
  { name: '我的收藏', path: '/sub-pages/user/collect/index' },
  { name: '我的足迹', path: '/sub-pages/user/footprint/index' },
  { name: '消息通知', path: '/sub-pages/message/notice/index' },
  { name: '会员中心', path: '/sub-pages/user/member/index' },
  { name: '我的分销', path: '/sub-pages/user/distribution/index' },
];

const commonPageNameByPath = new Map(
  COMMON_PAGE_PRESETS.map((preset) => [preset.path, preset.name]),
);

/** 路径命中常用页面时返回中文名，用于链接触发器回显；未命中返回空串 */
export function commonPageName(path: string | undefined): string {
  if (!path) return '';
  return commonPageNameByPath.get(path) ?? '';
}
