import type { DiscountProps } from '../../discount/types';
import type { GoodsGroupProps } from '../../goods-group/types';
import type { GoodsRankingProps } from '../../goods-ranking/types';
import type { LimitedActivityProps } from '../../limited-activity/types';
import type { SeckillProps } from '../../seckill/types';

import {
  getByIds as getGoodsByIds,
  getPage as getGoodsPage,
} from '#/api/product/goods-spu';
import { getProductSalesTop10 } from '#/api/product/goods-statistics';
import { getAppDiscountActivities } from '#/api/promotion/discount';
import {
  getById as getActivityById,
  getPage as getActivityPage,
} from '#/api/promotion/group-buy-activity';
import { getAppSessionGoods, getAppSessions } from '#/api/promotion/seckill';
import { getById as getTenantById } from '#/api/upms/tenant';

export interface RetailGoodsItem {
  id: string;
  imageUrl: string;
  name: string;
  price: number;
  /**
   * 商品原价（划线价），来源 `goods_spu.original_price`。
   *
   * 0 表示商家未填原价（存量商品普遍如此），渲染侧据此隐藏划线，
   * 否则会划出一个「￥0」。判断口径：仅当原价严格大于售价时显示。
   */
  originalPrice: number;
  sales: number;
  stock: number;
}

export interface RetailActivityItem {
  activityPrice: number;
  endTime: string;
  id: string;
  imageUrl: string;
  name: string;
  originalPrice: number;
  spuId: string;
  status: 'active' | 'ended' | 'pending';
}

export interface RetailSeckillGoodsItem {
  goodsImage: string;
  goodsName: string;
  originalPrice: number;
  remainingStock: number;
  seckillPrice: number;
  seckillStock: number;
  skuId: string;
  spuId: string;
}

export interface RetailSeckillSessionItem {
  countdown: number;
  endTime: string;
  goodsList: RetailSeckillGoodsItem[];
  sessionId: string;
  sessionName: string;
  startTime: string;
  status: number;
}

export interface RetailDiscountGoodsItem {
  discountPrice: number;
  goodsImage: string;
  goodsName: string;
  originalPrice: number;
  skuId: string;
  spuId: string;
}

export interface RetailDiscountActivityItem {
  activityId: string;
  activityName: string;
  countdown: number;
  discountType: number;
  discountValue: number;
  endTime: string;
  goodsList: RetailDiscountGoodsItem[];
  scope: number;
  startTime: string;
  status: number;
}

export interface RetailShopInfo {
  address: string;
  id: string;
  logoUrl: string;
  name: string;
  phone: string;
  siteUrl: string;
}

export interface RetailDataDependencies {
  activityById: (id: string) => Promise<unknown>;
  activityPage: (query: Record<string, unknown>) => Promise<unknown>;
  goodsByIds: (ids: string[]) => Promise<unknown>;
  goodsPage: (query: Record<string, unknown>) => Promise<unknown>;
  salesRanking: (query: Record<string, unknown>) => Promise<unknown>;
  seckillSessionGoods: (sessionId: string) => Promise<unknown>;
  seckillSessions: () => Promise<unknown>;
  shopById: (id: string) => Promise<unknown>;
}

const defaultDependencies: RetailDataDependencies = {
  activityById: getActivityById,
  activityPage: getActivityPage,
  goodsByIds: (ids) => getGoodsByIds(ids.join(',')),
  goodsPage: getGoodsPage,
  salesRanking: getProductSalesTop10,
  seckillSessionGoods: getAppSessionGoods,
  seckillSessions: getAppSessions,
  shopById: getTenantById,
};

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object'
    ? (value as Record<string, unknown>)
    : {};
}

function extractItems(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  const records = asRecord(value).records;
  return Array.isArray(records) ? records : [];
}

function toNumber(value: unknown): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function toText(value: unknown): string {
  return value === null || value === undefined ? '' : String(value);
}

function firstImage(record: Record<string, unknown>): string {
  const urls = record.spuUrls;
  if (Array.isArray(urls)) return toText(urls[0]);
  // goodsImage 是秒杀/折扣 VO 的图片字段名，与商品模块的 picUrl 不同名
  return toText(
    record.picUrl ?? record.imageUrl ?? record.goodsImage ?? record.logoUrl,
  );
}

function normalizeGoods(value: unknown): RetailGoodsItem[] {
  return extractItems(value)
    .map((item) => {
      const record = asRecord(item);
      return {
        id: toText(record.id),
        imageUrl: firstImage(record),
        name: toText(record.name ?? record.spuName),
        price: toNumber(record.salesPrice ?? record.price),
        originalPrice: toNumber(record.originalPrice),
        sales: toNumber(record.salesVolume),
        stock: toNumber(record.stock),
      };
    })
    .filter(({ id }) => Boolean(id));
}

function restoreConfiguredOrder<T extends { id: string }>(
  items: T[],
  ids: string[],
): T[] {
  const order = new Map(ids.map((id, index) => [id, index]));
  return [...items].sort(
    (left, right) =>
      (order.get(left.id) ?? Number.MAX_SAFE_INTEGER) -
      (order.get(right.id) ?? Number.MAX_SAFE_INTEGER),
  );
}

function createGoodsSortParams(sort: string) {
  if (sort === 'sales_price') return { asc: 'sales_price' };
  return {
    desc: sort === 'create_time' ? 'create_time' : 'sales_volume',
  };
}

export async function loadGoodsGroup(
  props: GoodsGroupProps,
  dependencies: RetailDataDependencies = defaultDependencies,
): Promise<RetailGoodsItem[]> {
  if (props.dataSource.mode === 'manual') {
    const ids = props.dataSource.targetIds ?? [];
    const result = normalizeGoods(await dependencies.goodsByIds(ids));
    return restoreConfiguredOrder(result, ids).slice(0, props.count);
  }
  const response = await dependencies.goodsPage({
    categorySecondId: props.dataSource.categoryId || undefined,
    current: 1,
    ...createGoodsSortParams(props.dataSource.sort || 'sales'),
    size: props.count,
  });
  return normalizeGoods(response).slice(0, props.count);
}

export async function loadGoodsRanking(
  props: GoodsRankingProps,
  dependencies: RetailDataDependencies = defaultDependencies,
): Promise<RetailGoodsItem[]> {
  const metric = props.dataSource.metric || 'sales';
  const response =
    metric === 'sales'
      ? await dependencies.salesRanking({})
      : await dependencies.goodsPage({
          current: 1,
          desc: metric,
          size: props.count,
        });
  return normalizeGoods(response).slice(0, props.count);
}

function normalizeActivities(value: unknown): RetailActivityItem[] {
  return extractItems(value)
    .map((item) => {
      const record = asRecord(item);
      const rawStatus = toText(record.activityStatus ?? record.status);
      let status: RetailActivityItem['status'] = 'pending';
      if (rawStatus === '1' || rawStatus === 'active') status = 'active';
      if (rawStatus === '2' || rawStatus === 'ended') status = 'ended';
      return {
        activityPrice: toNumber(record.groupPrice ?? record.activityPrice),
        endTime: toText(record.endedAt ?? record.endTime),
        id: toText(record.id),
        imageUrl: firstImage(record),
        name: toText(record.activityName ?? record.name),
        originalPrice: toNumber(record.originalPrice),
        spuId: toText(record.spuId),
        status,
      } as RetailActivityItem;
    })
    .filter(({ id }) => Boolean(id));
}

export async function loadLimitedActivities(
  props: LimitedActivityProps,
  dependencies: RetailDataDependencies = defaultDependencies,
): Promise<RetailActivityItem[]> {
  async function withGoods(value: unknown) {
    const activities = normalizeActivities(value);
    const spuIds = [
      ...new Set(activities.map(({ spuId }) => spuId).filter(Boolean)),
    ];
    if (spuIds.length === 0) return activities;
    const goods = normalizeGoods(await dependencies.goodsByIds(spuIds));
    const goodsById = new Map(goods.map((item) => [item.id, item]));
    return activities.map((activity) => {
      const goodsItem = goodsById.get(activity.spuId);
      if (!goodsItem) return activity;
      return {
        ...activity,
        imageUrl: activity.imageUrl || goodsItem.imageUrl,
        name: activity.name || goodsItem.name,
        originalPrice: activity.originalPrice || goodsItem.price,
      };
    });
  }

  if (props.dataSource.mode === 'manual') {
    const ids = props.dataSource.targetIds ?? [];
    const responses = await Promise.all(
      ids.map((id) => dependencies.activityById(id)),
    );
    return restoreConfiguredOrder(await withGoods(responses), ids).slice(
      0,
      props.count,
    );
  }
  const response = await dependencies.activityPage({
    activityStatus: '1',
    current: 1,
    desc: 'create_time',
    size: props.count,
  });
  const activities = await withGoods(response);
  return activities.slice(0, props.count);
}

/**
 * 秒杀场次归一化：与 C 端 retail-normalizers.normalizeRetailSeckillSessions 同口径。
 *
 * 两端是独立实现，字段映射必须保持一致，否则编辑器预览与小程序实机又会漂移。
 */
export function normalizeSeckillSessions(
  value: unknown,
): RetailSeckillSessionItem[] {
  return extractItems(value)
    .map((item) => {
      const record = asRecord(item);
      const goods = Array.isArray(record.goodsList) ? record.goodsList : [];
      return {
        countdown: toNumber(record.countdown),
        endTime: toText(record.endTime),
        goodsList: goods
          .map((goodsItem) => {
            const goodsRecord = asRecord(goodsItem);
            const rawRemaining = goodsRecord.remainingStock as
              number | undefined;
            return {
              goodsImage: firstImage(goodsRecord),
              goodsName: toText(goodsRecord.goodsName),
              originalPrice: toNumber(goodsRecord.originalPrice),
              remainingStock:
                rawRemaining === null || rawRemaining === undefined
                  ? -1
                  : toNumber(rawRemaining),
              seckillPrice: toNumber(goodsRecord.seckillPrice),
              seckillStock: toNumber(goodsRecord.seckillStock),
              skuId: toText(goodsRecord.skuId),
              spuId: toText(goodsRecord.spuId),
            };
          })
          .filter((goodsItem) => Boolean(goodsItem.skuId)),
        sessionId: toText(record.sessionId ?? record.id),
        sessionName: toText(record.sessionName),
        startTime: toText(record.startTime),
        status: toNumber(record.status),
      };
    })
    .filter((item) => Boolean(item.sessionId))
    .filter((item) => {
      // 与 C 端一致：已结束场次不进楼层
      return item.status !== 2;
    });
}

/** 秒杀会场取数；manual 模式 targetIds 存场次 ID */
export async function loadSeckillSessions(
  props: SeckillProps,
  dependencies: RetailDataDependencies = defaultDependencies,
): Promise<RetailSeckillSessionItem[]> {
  if (props.dataSource.mode === 'manual') {
    const ids = props.dataSource.targetIds ?? [];
    if (ids.length === 0) return [];
    const sessions = await Promise.all(
      ids.map((id) => dependencies.seckillSessionGoods(id)),
    );
    return normalizeSeckillSessions(sessions).slice(0, props.count);
  }
  const sessions = normalizeSeckillSessions(
    await dependencies.seckillSessions(),
  ).sort((a, b) => a.status - b.status);
  return sessions.slice(0, props.count);
}

/**
 * 折扣活动归一化：与 C 端 retail-normalizers.normalizeRetailDiscounts 同口径。
 */
export function normalizeDiscountActivities(
  value: unknown,
): RetailDiscountActivityItem[] {
  return extractItems(value)
    .map((item) => {
      const record = asRecord(item);
      const goods = Array.isArray(record.goodsList) ? record.goodsList : [];
      return {
        activityId: toText(record.activityId ?? record.id),
        activityName: toText(record.activityName),
        countdown: toNumber(record.countdown),
        discountType: toNumber(record.discountType),
        discountValue: toNumber(record.discountValue),
        endTime: toText(record.endTime),
        goodsList: goods
          .map((goodsItem) => {
            const goodsRecord = asRecord(goodsItem);
            return {
              discountPrice: toNumber(goodsRecord.discountPrice),
              goodsImage: firstImage(goodsRecord),
              goodsName: toText(goodsRecord.goodsName),
              originalPrice: toNumber(goodsRecord.originalPrice),
              skuId: toText(goodsRecord.skuId),
              spuId: toText(goodsRecord.spuId),
            };
          })
          .filter((goodsItem) => Boolean(goodsItem.skuId)),
        scope: toNumber(record.scope),
        startTime: toText(record.startTime),
        status: toNumber(record.status),
      };
    })
    .filter((item) => Boolean(item.activityId));
}

/** 折扣会场取数；manual 模式 targetIds 存活动 ID */
export async function loadDiscounts(
  props: DiscountProps,
): Promise<RetailDiscountActivityItem[]> {
  const response = await getAppDiscountActivities({
    current: 1,
    size: Math.max(props.count, 10),
  });
  const activities = normalizeDiscountActivities(response);
  if (props.dataSource.mode === 'manual') {
    const ids = props.dataSource.targetIds ?? [];
    if (ids.length === 0) return [];
    return activities
      .filter((activity) => ids.includes(activity.activityId))
      .slice(0, props.count);
  }
  return activities.slice(0, props.count);
}

export async function loadShopInfo(
  tenantId: string,
  dependencies: RetailDataDependencies = defaultDependencies,
): Promise<RetailShopInfo[]> {
  if (!tenantId) return [];
  const record = asRecord(await dependencies.shopById(tenantId));
  if (!record.id && !record.name) return [];
  return [
    {
      address: toText(record.address),
      id: toText(record.id || tenantId),
      logoUrl: toText(record.logoUrl),
      name: toText(record.name),
      phone: toText(record.phone),
      siteUrl: toText(record.siteUrl),
    },
  ];
}
