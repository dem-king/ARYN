import type { Component } from 'vue';

import type { IdPickerItem, IdPickerKind } from './helpers';

import type { PromotionTargetDialogProps } from '#/components/select-promotion-target/types';

import { defineAsyncComponent } from 'vue';

import { getByIds as getGoodsByIds } from '#/api/product/goods-spu';
import { getByIds as getCouponByIds } from '#/api/promotion/coupon-info';
import {
  getById as getDiscountById,
  getPage as getDiscountPage,
} from '#/api/promotion/discount';
import {
  getById as getGroupBuyById,
  getPage as getGroupBuyPage,
} from '#/api/promotion/group-buy-activity';
import {
  getById as getPageDesignById,
  getPage as getPageDesignPage,
  pageTypeLabel,
} from '#/api/promotion/page-design';
import { getSessionPage } from '#/api/promotion/seckill';

import { invalidItem } from './helpers';

/** 选择器分页查询入参由通用弹窗以 { current, size } 给定，转成各接口的查询类型 */
type QueryOf<T extends (query: never) => unknown> = Parameters<T>[0];

export interface PickerDefinition {
  buttonText: string;
  dialog: Component;
  /** 透传给通用活动选择器的 props（select-goods / select-coupon 自带弹窗，无需） */
  dialogProps?: Omit<PromotionTargetDialogProps, 'limitNum' | 'noun'>;
  emptyText: string;
  /** 回源补齐已选项名称/图片 */
  hydrate: (ids: string[]) => Promise<IdPickerItem[]>;
  /** 手选上限：与组件「展示数量」可设置的最大值一致 */
  limit: number;
  /** 弹窗回传行 → 面板展示行（与 hydrate 的映射同源，选中即刻可读） */
  mapRow: (row: Record<string, any>) => IdPickerItem;
  noun: string;
}

const ACTIVITY_STATUS_LABEL: Record<string, string> = {
  '0': '未开始',
  '1': '进行中',
  '2': '已结束',
  '3': '已暂停',
};

const GROUP_BUY_STATUS_LABEL: Record<string, string> = {
  '0': '草稿',
  '1': '进行中',
  '2': '已结束',
};

type TagType = 'danger' | 'info' | 'success' | 'warning';

function statusTag(status: unknown, labels: Record<string, string>) {
  const label = labels[String(status ?? '')] ?? '未知';
  let type: TagType = 'info';
  if (label === '进行中') {
    type = 'success';
  } else if (label === '已暂停') {
    type = 'danger';
  }
  return { text: label, type };
}

/** 后端 LocalDateTime 序列化可能是 `T` 分隔的 ISO 串，统一成可读文本 */
function formatTime(value: unknown): string {
  if (value === null || value === undefined || value === '') return '';
  return String(value).replace('T', ' ');
}

/** 保留 ids 的配置顺序；查不到的 ID 以「已失效」行兜底，保证可删除 */
function toItems(
  rows: unknown,
  ids: string[],
  mapRow: (row: Record<string, any>) => IdPickerItem,
): IdPickerItem[] {
  const list = Array.isArray(rows) ? (rows as Record<string, any>[]) : [];
  const byId = new Map<string, IdPickerItem>();
  for (const row of list) {
    const item = mapRow(row);
    if (item.id) byId.set(item.id, item);
  }
  return ids.map((id) => byId.get(id) ?? invalidItem(id));
}

const goodsMapRow = (row: Record<string, any>): IdPickerItem => ({
  id: String(row.id ?? ''),
  image: row.spuUrls?.[0],
  subtitle:
    row.salesPrice === null || row.salesPrice === undefined
      ? ''
      : `¥${row.salesPrice}`,
  title: String(row.name ?? '未命名商品'),
});

const couponMapRow = (row: Record<string, any>): IdPickerItem => ({
  id: String(row.id ?? ''),
  subtitle:
    String(row.couponType ?? '1') === '2'
      ? `${row.discount ?? 0}折券`
      : `¥${row.amount ?? 0} 满减券`,
  title: String(row.couponName ?? '优惠券'),
});

const discountMapRow = (row: Record<string, any>): IdPickerItem => ({
  id: String(row.id ?? ''),
  subtitle: `${formatTime(row.startTime)} ~ ${formatTime(row.endTime)}`,
  title: String(row.activityName ?? '折扣活动'),
});

const groupBuyMapRow = (row: Record<string, any>): IdPickerItem => ({
  id: String(row.id ?? ''),
  subtitle: `拼团价 ¥${row.groupPrice ?? 0} · ${row.groupNum ?? 0} 人成团`,
  title: String(row.activityName ?? '拼团活动'),
});

const pageDesignMapRow = (row: Record<string, any>): IdPickerItem => ({
  id: String(row.id ?? ''),
  subtitle: `${pageTypeLabel(String(row.pageType ?? ''))} · ${
    String(row.publishedStatus ?? '0') === '1' ? '已发布' : '未发布'
  }`,
  title: String(row.pageName ?? '未命名页面'),
});

/**
 * 微页面链接在 C 端固定走 `/sub-pages/promotion/diy-page/index?id=...` 独立渲染：
 * - 未发布页面在 C 端 `getPublished` 会抛 "Published page does not exist"；
 * - pageType 1/2/3/4 是首页/商详/分类/个人中心的**内嵌装修片段**，不能当独立页面打开。
 * 因此选择器只列「已发布的微页面」，历史配置里的其它页面在回显时标失效，提示运营换掉。
 */
const pageDesignLinkItem = (row: Record<string, any>): IdPickerItem => {
  const item = pageDesignMapRow(row);
  if (String(row.pageType ?? '') !== '0') {
    return {
      ...item,
      invalid: true,
      subtitle: `${pageTypeLabel(String(row.pageType ?? ''))} · 不能作为链接落地页`,
    };
  }
  if (String(row.publishedStatus ?? '0') !== '1') {
    return {
      ...item,
      invalid: true,
      subtitle: '未发布 · 发布后才能作为链接落地页',
    };
  }
  return item;
};

const seckillSessionMapRow = (row: Record<string, any>): IdPickerItem => ({
  id: String(row.id ?? ''),
  subtitle: `${row.activityName ?? ''} · ${formatTime(row.startTime)}`,
  title: String(row.sessionName ?? '秒杀场次'),
});

export const pickerDefinitions: Record<IdPickerKind, PickerDefinition> = {
  goods: {
    buttonText: '选择商品',
    dialog: defineAsyncComponent(
      () => import('#/components/select-goods/index.vue'),
    ),
    emptyText: '未选择商品',
    limit: 20,
    mapRow: goodsMapRow,
    noun: '商品',
    hydrate: async (ids) =>
      toItems(await getGoodsByIds(ids.join(',')), ids, goodsMapRow),
  },
  coupon: {
    buttonText: '选择优惠券',
    dialog: defineAsyncComponent(
      () => import('#/components/select-coupon/index.vue'),
    ),
    emptyText: '未选择优惠券',
    limit: 30,
    mapRow: couponMapRow,
    noun: '优惠券',
    hydrate: async (ids) =>
      toItems(await getCouponByIds(ids), ids, couponMapRow),
  },
  'discount-activity': {
    buttonText: '选择折扣活动',
    dialog: defineAsyncComponent(
      () => import('#/components/select-promotion-target/index.vue'),
    ),
    dialogProps: {
      columns: [
        { label: '活动名称', prop: 'activityName' },
        {
          formatter: (row) =>
            `${formatTime(row.startTime)} ~ ${formatTime(row.endTime)}`,
          label: '活动时间',
        },
        {
          formatter: (row) => {
            const value = Number(row.discountValue ?? 0);
            if (row.discountType === 2) return `减 ${value} 元`;
            if (row.discountType === 3) return `固定价 ${value} 元`;
            return `${value} 折`;
          },
          label: '折扣内容',
        },
        {
          label: '状态',
          tag: (row) => statusTag(row.status, ACTIVITY_STATUS_LABEL),
        },
      ],
      loader: (query) =>
        getDiscountPage(query as QueryOf<typeof getDiscountPage>),
      rowLabel: (row) => String(row.activityName ?? ''),
      title: '折扣活动选择器',
    },
    emptyText: '未选择折扣活动',
    limit: 5,
    mapRow: discountMapRow,
    noun: '折扣活动',
    hydrate: async (ids) =>
      toItems(
        await Promise.all(ids.map((id) => getDiscountById(id))),
        ids,
        discountMapRow,
      ),
  },
  'groupbuy-activity': {
    buttonText: '选择拼团活动',
    dialog: defineAsyncComponent(
      () => import('#/components/select-promotion-target/index.vue'),
    ),
    dialogProps: {
      columns: [
        { label: '活动名称', prop: 'activityName' },
        { formatter: (row) => `¥${row.groupPrice ?? 0}`, label: '拼团价' },
        {
          formatter: (row) => `${row.groupNum ?? 0} 人`,
          label: '成团人数',
        },
        {
          formatter: (row) =>
            `${formatTime(row.startedAt)} ~ ${formatTime(row.endedAt)}`,
          label: '活动时间',
        },
        {
          label: '状态',
          tag: (row) => statusTag(row.activityStatus, GROUP_BUY_STATUS_LABEL),
        },
      ],
      loader: (query) => getGroupBuyPage(query),
      rowLabel: (row) => String(row.activityName ?? ''),
      title: '拼团活动选择器',
    },
    emptyText: '未选择拼团活动',
    limit: 10,
    mapRow: groupBuyMapRow,
    noun: '拼团活动',
    hydrate: async (ids) =>
      toItems(
        await Promise.all(ids.map((id) => getGroupBuyById(id))),
        ids,
        groupBuyMapRow,
      ),
  },
  page: {
    buttonText: '选择微页面',
    dialog: defineAsyncComponent(
      () => import('#/components/select-promotion-target/index.vue'),
    ),
    dialogProps: {
      columns: [
        { label: '页面名称', prop: 'pageName' },
        {
          formatter: (row) => pageTypeLabel(String(row.pageType ?? '')),
          label: '页面类型',
        },
        {
          label: '发布状态',
          tag: (row) =>
            String(row.publishedStatus ?? '0') === '1'
              ? { text: '已发布', type: 'success' }
              : { text: '未发布', type: 'info' },
        },
        {
          formatter: (row) => formatTime(row.updateTime),
          label: '更新时间',
        },
      ],
      loader: (query) =>
        getPageDesignPage({
          current: Number(query.current ?? 1),
          pageType: '0',
          publishedStatus: '1',
          size: Number(query.size ?? 20),
        }),
      rowLabel: (row) => String(row.pageName ?? ''),
      title: '微页面选择器',
    },
    emptyText: '未选择微页面',
    limit: 1,
    mapRow: pageDesignLinkItem,
    noun: '微页面',
    // 页面被删/无权限时返回 null，由 toItems 兜底成「已失效」行，避免整批查询失败
    hydrate: async (ids) => {
      const rows = await Promise.all(
        ids.map((id) => getPageDesignById(id).catch(() => null)),
      );
      return toItems(rows.filter(Boolean), ids, pageDesignLinkItem);
    },
  },
  'seckill-session': {
    buttonText: '选择场次',
    dialog: defineAsyncComponent(
      () => import('#/components/select-promotion-target/index.vue'),
    ),
    dialogProps: {
      columns: [
        { label: '场次名称', prop: 'sessionName' },
        { label: '所属活动', prop: 'activityName' },
        {
          formatter: (row) =>
            `${formatTime(row.startTime)} ~ ${formatTime(row.endTime)}`,
          label: '场次时间',
        },
        {
          label: '状态',
          tag: (row) => statusTag(row.status, ACTIVITY_STATUS_LABEL),
        },
      ],
      loader: (query) =>
        getSessionPage(query as QueryOf<typeof getSessionPage>),
      rowLabel: (row) => String(row.sessionName ?? ''),
      title: '秒杀场次选择器',
    },
    emptyText: '未选择场次',
    limit: 5,
    mapRow: seckillSessionMapRow,
    noun: '场次',
    hydrate: async (ids) => {
      const sessionPage = await getSessionPage({
        current: 1,
        ids: ids.join(','),
        size: ids.length,
      });
      return toItems(sessionPage?.records, ids, seckillSessionMapRow);
    },
  },
};
