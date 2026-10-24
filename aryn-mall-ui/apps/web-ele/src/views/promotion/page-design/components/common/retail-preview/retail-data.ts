import type {
  GoodsRecommendProps,
  GoodsWaterfallProps,
} from '../../extension/types';
import type { GoodsGroupProps } from '../../goods-group/types';
import type { GoodsRankingProps } from '../../goods-ranking/types';
import type { NewbieFloorProps } from '../../newbie-floor/types';
import type {
  DiscountProps,
  LimitedActivityProps,
  SeckillProps,
} from './retail-legacy-props';

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
import { getCurrentNewbieFloor } from '#/api/promotion/newbie-activity';
import { getAppSessionGoods, getAppSessions } from '#/api/promotion/seckill';
import { getShopInfo as getCurrentTenantShop } from '#/api/upms/tenant';

export interface RetailGoodsItem {
  id: string;
  imageUrl: string;
  name: string;
  /** 商品子标题（商品介绍），来源 goods_spu.sub_title */
  subTitle?: string;
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

/**
 * 拼团活动名去重：与 C 端 `aryn-mall-uniapp/src/utils/activity-name.ts` 同一规则。
 * 名称里的「N人成团·」与图上人数徽标重复；仅当 groupNum > 0 时剥离，
 * 避免误伤「3人份」「2人套餐」这类规格词，剥离后为空则回退原名。
 */
const GROUP_BADGE_PREFIX = /^\s*\d+\s*人成团\s*[·•・.\-—]?\s*/;

function stripGroupBadgePrefix(name: string, groupNum: number): string {
  const raw = (name || '').trim();
  if (!raw || !(groupNum > 0)) return raw;
  const stripped = raw.replace(GROUP_BADGE_PREFIX, '').trim();
  return stripped || raw;
}

export interface RetailActivityItem {
  activityPrice: number;
  endTime: string;
  /**
   * 成团人数（group_buy_activity.group_num）。
   * 管理端分页返回实体、C 端分页返回 GroupBuyActivityVO，均带该字段；
   * 历史归一化漏映射，导致楼层无法表达「2 人成团」。
   */
  groupNum: number;
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
  /**
   * 入口卡缩略图（取数层已按优先级解析好）。
   *
   * 指定商品活动取首件商品图；全场活动（scope=1）没有商品清单，
   * 由 loadDiscounts 补一张真实商品图作代表（全店销量 Top1 商品，确实参与全场折扣，
   * 不伪造数据）。仍取不到图时空串，渲染侧回落首字灰块。与 C 端同口径。
   */
  coverImage: string;
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

export interface RetailNewbieGoodsItem {
  goodsImage: string;
  goodsName: string;
  /** 新人专享价 */
  newbiePrice: number;
  /** 划线原价（当前售价） */
  originalPrice: number;
  skuId: string;
  spuId: string;
}

export interface RetailNewbieFloorItem {
  activityId: string;
  activityName: string;
  endTime: string;
  goodsList: RetailNewbieGoodsItem[];
  /** 每人每 SKU 限购件数（固定 1） */
  limitPerUser: number;
  registerWithinDays: number;
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
  // 装修账号普遍没有 upms:systenant:get，租户资料只能走免权限的当前租户门店接口；
  // 后端按「tenant-id 头 || 登录租户」解析，与组件侧 switch-tenant-id 口径一致
  shopById: () => getCurrentTenantShop(),
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
        subTitle: toText(record.subTitle).trim() || undefined,
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

/**
 * 商品瀑布流取数：与 C 端 diy-goods-waterfall 的 loadGoodsWaterfall 同口径。
 *
 * 画布预览不做无限滚动（C 端由 z-paging 分页追加），一次取 count 条即停；
 * 缓存只在 C 端生效，管理端配置一改立即重取，所见即最新配置。
 */
export async function loadGoodsWaterfall(
  props: GoodsWaterfallProps,
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

/**
 * 商品推荐取数：与 C 端 diy-goods-recommend 的 loadGoodsRecommend 同口径。
 *
 * 差异：画布没有「当前商品」上下文，不做商详场景的排除当前商品与
 * 按当前商品分类兜底，automatic 模式固定按销量取全站（或已配置分类）Top。
 */
export async function loadGoodsRecommend(
  props: GoodsRecommendProps,
  dependencies: RetailDataDependencies = defaultDependencies,
): Promise<RetailGoodsItem[]> {
  const count = Math.max(1, Number(props.count) || 6);
  if (props.dataSource.mode === 'manual') {
    const ids = props.dataSource.targetIds ?? [];
    const result = normalizeGoods(await dependencies.goodsByIds(ids));
    return restoreConfiguredOrder(result, ids).slice(0, count);
  }
  const response = await dependencies.goodsPage({
    categorySecondId: props.dataSource.categoryId || undefined,
    current: 1,
    ...createGoodsSortParams('sales'),
    size: count,
  });
  return normalizeGoods(response).slice(0, count);
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
        groupNum: toNumber(record.groupNum),
        id: toText(record.id),
        imageUrl: firstImage(record),
        name: stripGroupBadgePrefix(
          toText(record.activityName ?? record.name),
          toNumber(record.groupNum),
        ),
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
    .filter((item) => Boolean(item.sessionId));
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
  // 进行中优先、未开始次之（不能直接按 status 升序，否则 count=1 时取到未开始场次）
  const rank = (status: number) => {
    if (status === 1) return 0;
    if (status === 0) return 1;
    return 2;
  };
  const sessions = normalizeSeckillSessions(
    await dependencies.seckillSessions(),
  )
    // 自动模式：已结束场次不进楼层；手动模式保留（画布显示「本场已结束」，与 C 端一致）
    .filter((session) => session.status !== 2)
    .sort((a, b) => rank(a.status) - rank(b.status) || a.status - b.status);
  return sessions.slice(0, props.count);
}

/**
 * 折扣活动归一化：与 C 端 retail-normalizers.normalizeRetailDiscounts 同口径。
 *
 * coverImage 由 loadDiscounts 补齐（入口卡缩略图），归一化阶段先落空串，
 * 避免把「有无商品图」的判断散到渲染侧。
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
        coverImage: '',
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

/**
 * 折扣会场取数；manual 模式 targetIds 存活动 ID。
 *
 * 返回项的 coverImage 已补齐入口卡缩略图（与 C 端 loadDiscounts 同口径）。
 */
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
    return withDiscountCoverImages(
      activities
        .filter((activity) => ids.includes(activity.activityId))
        .slice(0, props.count),
    );
  }
  return withDiscountCoverImages(activities.slice(0, props.count));
}

/**
 * 全场折扣（scope=1）的入口卡代表图：全店销量 Top1 商品图。
 *
 * 与 C 端同口径：全场活动没有商品清单，但入口小卡需要一张真实商品图
 * （与相邻秒杀/拼团卡一致），不能退回「限」字灰块。全场活动对所有在售商品生效，
 * 取销量 Top1 商品只是挑一个真实参与者作代表，不伪造数据；
 * 请求失败/店内无商品时返回空串，照旧走首字灰块。
 *
 * 模块级缓存：编辑器每次改配置都会重跑 loader，同一会话只查一次即可；
 * 失败不缓存，下次还能补图。
 */
let salesTopGoodsImageTask: null | Promise<string> = null;

function loadSalesTopGoodsImage(): Promise<string> {
  salesTopGoodsImageTask ??= getGoodsPage({
    current: 1,
    ...createGoodsSortParams('sales'),
    size: 1,
  })
    .then((response) => normalizeGoods(response)[0]?.imageUrl ?? '')
    .catch(() => {
      salesTopGoodsImageTask = null;
      return '';
    });
  return salesTopGoodsImageTask;
}

/**
 * 补入口卡缩略图：指定商品活动取首件商品图，全场活动用代表商品图兜底。
 * 只在确有活动缺图时才发商品查询，避免无谓请求。
 */
async function withDiscountCoverImages(
  activities: RetailDiscountActivityItem[],
): Promise<RetailDiscountActivityItem[]> {
  const needsRepresentative = activities.some(
    (activity) => !activity.goodsList[0]?.goodsImage,
  );
  const representative = needsRepresentative
    ? await loadSalesTopGoodsImage()
    : '';
  return activities.map((activity) => ({
    ...activity,
    coverImage: activity.goodsList[0]?.goodsImage || representative,
  }));
}

/**
 * 新人专享楼层取数：固定读「当前进行中的新人价活动」，无活动返回空数组。
 *
 * 与小程序 retail-data.loadNewbieFloor 同源口径；管理端预览同样不做按人出价，
 * 展示的就是活动配置价（与 C 端楼层一致，非新人也能看到该价）。
 */
export async function loadNewbieFloor(
  props: NewbieFloorProps,
): Promise<RetailNewbieFloorItem[]> {
  return normalizeNewbieFloor(await getCurrentNewbieFloor())
    .slice(0, 1)
    .map((floor) => ({
      ...floor,
      goodsList: floor.goodsList.slice(0, props.count),
    }));
}

/** 新人专享活动归一化（单对象响应） */
export function normalizeNewbieFloor(value: unknown): RetailNewbieFloorItem[] {
  const record = asRecord(value);
  const activityId = toText(record.activityId);
  if (!activityId) return [];
  const goods = Array.isArray(record.goodsList) ? record.goodsList : [];
  return [
    {
      activityId,
      activityName: toText(record.activityName),
      endTime: toText(record.endTime),
      goodsList: goods
        .map((goodsItem) => {
          const goodsRecord = asRecord(goodsItem);
          return {
            goodsImage: firstImage(goodsRecord),
            goodsName: toText(goodsRecord.goodsName),
            newbiePrice: toNumber(goodsRecord.newbiePrice),
            originalPrice: toNumber(goodsRecord.originalPrice),
            skuId: toText(goodsRecord.skuId),
            spuId: toText(goodsRecord.spuId),
          };
        })
        .filter((goodsItem) => Boolean(goodsItem.skuId)),
      limitPerUser: toNumber(record.limitPerUser) || 1,
      registerWithinDays: toNumber(record.registerWithinDays),
    },
  ];
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
