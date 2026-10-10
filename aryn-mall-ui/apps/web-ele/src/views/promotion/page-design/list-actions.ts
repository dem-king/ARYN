import type {
  PageDesignRecord,
  PageDesignType,
} from '#/api/promotion/page-design';

import dayjs from 'dayjs';

import { isEffectiveSlotPageType } from '#/api/promotion/page-design';

/** 行级操作统一收敛进「⋯」菜单：标签、权限码与可见条件集中在此维护 */
export type PageDesignRowAction =
  | 'copy'
  | 'delete'
  | 'metrics'
  | 'publish'
  | 'releases'
  | 'set-home'
  | 'unpublish'
  | 'versions';

export const ROW_ACTION_LABELS: Record<PageDesignRowAction, string> = {
  copy: '复制',
  delete: '删除',
  metrics: '数据看板',
  publish: '发布',
  releases: '发布记录与审计',
  'set-home': '设为首页',
  unpublish: '下线',
  versions: '版本历史',
};

export const ROW_ACTION_ACCESS: Record<PageDesignRowAction, string> = {
  copy: 'promotion:pagedesign:add',
  delete: 'promotion:pagedesign:del',
  metrics: 'promotion:pagedesign:metrics',
  publish: 'promotion:pagedesign:submit',
  releases: 'promotion:pagedesign:get',
  'set-home': 'promotion:pagedesign:publish',
  unpublish: 'promotion:pagedesign:publish',
  versions: 'promotion:pagedesign:get',
};

/** 页面类型徽标配色：商城首页红/商品详情页橙/分类页蓝/个人中心页绿/微页面灰 */
const PAGE_TYPE_TAG_TYPES: Record<
  PageDesignType,
  'danger' | 'info' | 'primary' | 'success' | 'warning'
> = {
  '0': 'info',
  '1': 'danger',
  '2': 'warning',
  '3': 'primary',
  '4': 'success',
};

export function pageTypeTagType(pageType: PageDesignType) {
  return PAGE_TYPE_TAG_TYPES[pageType] ?? 'info';
}

export function draftLabel(row: PageDesignRecord) {
  if (row.publishedStatus !== '1') return '待发布';
  if (!row.publishedAt || !row.updateTime) return '已发布';
  return dayjs(row.updateTime).isAfter(dayjs(row.publishedAt))
    ? '有未发布修改'
    : '已同步';
}

export function draftTagType(row: PageDesignRecord) {
  if (row.publishedStatus !== '1') return 'info';
  return draftLabel(row) === '有未发布修改' ? 'warning' : 'success';
}

/** 行上是否为 C 端实际生效的装修（首页行显示「当前首页」，其余槽位页型显示「生效中」） */
export function effectiveLabel(row: PageDesignRecord) {
  return row.pageType === '1' ? '当前首页' : '生效中';
}

/**
 * 是否渲染「生效」徽标。
 *
 * 除后端回显的 `effective` 外还要排除微页面：后端已只在槽位页型上打标
 * （`PageDesignComponentTypes.EFFECTIVE_SLOT_PAGE_TYPES`），此处是防御性双保险，
 * 避免后端口径回退时列表又出现「微页面 · 生效中」这类无从解释的标签。
 */
export function showEffectiveBadge(row: PageDesignRecord) {
  return Boolean(row.effective) && isEffectiveSlotPageType(row.pageType);
}

/**
 * 行可见的操作集合（权限由 v-access 单独裁剪）。
 *
 * 「设为首页」仅对已发布且非当前首页的微页面开放：
 * 分类页/商品详情页/个人中心页是固定槽位，晋升会改写其 pageType，
 * 导致对应槽位在商城里消失（后端 setAsHome 同口径拦截）。
 */
export function rowActions(row: PageDesignRecord): PageDesignRowAction[] {
  const actions: PageDesignRowAction[] = [];
  if (row.publishedStatus !== '1' || draftLabel(row) === '有未发布修改') {
    actions.push('publish');
  }
  if (row.publishedStatus === '1') {
    actions.push('unpublish');
  }
  actions.push('copy', 'versions', 'metrics', 'releases');
  if (
    row.pageType === '0' &&
    row.publishedStatus === '1' &&
    row.homeStatus !== '1'
  ) {
    actions.push('set-home');
  }
  if (row.pageType !== '1') {
    actions.push('delete');
  }
  return actions;
}
