import type { RetailDataDependencies } from './retail-data';

import { describe, expect, it, vi } from 'vitest';

import { getById, getShopInfo } from '#/api/upms/tenant';

import { createGoodsWaterfallDefaults } from '../../extension/types';
import { createGoodsGroupDefaults } from '../../goods-group/types';
import { createGoodsRankingDefaults } from '../../goods-ranking/types';
import {
  loadGoodsGroup,
  loadGoodsRanking,
  loadGoodsWaterfall,
  loadLimitedActivities,
  loadSeckillSessions,
  loadShopInfo,
  normalizeDiscountActivities,
} from './retail-data';
import {
  createLimitedActivityDefaults,
  createSeckillDefaults,
} from './retail-legacy-props';

vi.mock('#/api/upms/tenant', () => ({
  getById: vi.fn(async () => {
    throw new Error(
      '装修画布禁止调用 /upms/tenant/{id}（缺 upms:systenant:get 权限）',
    );
  }),
  getShopInfo: vi.fn(async () => null),
}));

function createDependencies(
  overrides: Partial<RetailDataDependencies> = {},
): RetailDataDependencies {
  return {
    activityById: vi.fn(async () => ({})),
    activityPage: vi.fn(async () => ({ records: [] })),
    goodsByIds: vi.fn(async () => []),
    goodsPage: vi.fn(async () => ({ records: [] })),
    memberActivityGoods: vi.fn(async () => null),
    seckillSessionGoods: vi.fn(async () => ({})),
    seckillSessions: vi.fn(async () => []),
    shopById: vi.fn(async () => ({})),
    ...overrides,
  };
}

describe('retail preview data adapters', () => {
  it('loads and normalizes a rule-based goods group', async () => {
    const goodsPage = vi.fn(async () => ({
      records: [
        {
          id: 'goods-1',
          name: '夏日风扇',
          salesPrice: '39.90',
          salesVolume: 21,
          spuUrls: ['fan.png'],
          stock: 8,
        },
      ],
    }));
    const props = createGoodsGroupDefaults();
    props.count = 1;
    props.dataSource.categoryId = 'category-1';

    const result = await loadGoodsGroup(
      props,
      createDependencies({ goodsPage }),
    );

    expect(goodsPage).toHaveBeenCalledWith(
      expect.objectContaining({
        categorySecondId: 'category-1',
        current: 1,
        desc: 'sales_volume',
        size: 1,
      }),
    );
    expect(result).toEqual([
      {
        id: 'goods-1',
        imageUrl: 'fan.png',
        name: '夏日风扇',
        // 未下发原价的商品落 0，渲染侧据此隐藏划线（避免划出「￥0」）
        originalPrice: 0,
        price: 39.9,
        sales: 21,
        stock: 8,
      },
    ]);
  });

  it('sorts a rule-based goods group by price ascending', async () => {
    const goodsPage = vi.fn(async (_query: Record<string, unknown>) => ({
      records: [],
    }));
    const props = createGoodsGroupDefaults();
    props.dataSource.categoryId = 'category-2';
    props.dataSource.sort = 'sales_price';

    await loadGoodsGroup(props, createDependencies({ goodsPage }));

    expect(goodsPage).toHaveBeenCalledWith(
      expect.objectContaining({
        asc: 'sales_price',
        categorySecondId: 'category-2',
      }),
    );
    expect(goodsPage.mock.calls[0]?.[0]).not.toHaveProperty('categoryId');
    expect(goodsPage.mock.calls[0]?.[0]).not.toHaveProperty('desc');
  });

  it('sends only the bargain threshold for a bargain-sorted group (no asc/desc)', async () => {
    const goodsPage = vi.fn(async (_query: Record<string, unknown>) => ({
      records: [],
    }));
    const props = createGoodsGroupDefaults();
    props.dataSource.sort = 'bargain';
    (props.dataSource as { bargainRate?: number }).bargainRate = 1.2;

    await loadGoodsGroup(props, createDependencies({ goodsPage }));

    expect(goodsPage).toHaveBeenCalledWith(
      expect.objectContaining({ bargainRate: 1.2, current: 1 }),
    );
    const query = goodsPage.mock.calls[0]?.[0];
    // 后端 bargain 模式固定排序，多传 asc/desc 会与之冲突
    expect(query).not.toHaveProperty('asc');
    expect(query).not.toHaveProperty('desc');
  });

  it('defaults the bargain threshold to 1.15 for goods-tabs previews', async () => {
    const goodsPage = vi.fn(async (_query: Record<string, unknown>) => ({
      records: [],
    }));
    const props = createGoodsWaterfallDefaults();
    props.dataSource.sort = 'bargain';

    await loadGoodsWaterfall(props, createDependencies({ goodsPage }));

    expect(goodsPage).toHaveBeenCalledWith(
      expect.objectContaining({ bargainRate: 1.15 }),
    );
  });

  it('falls back to the default threshold when the configured one is out of range', async () => {
    const goodsPage = vi.fn(async (_query: Record<string, unknown>) => ({
      records: [],
    }));
    const props = createGoodsWaterfallDefaults();
    props.dataSource.sort = 'bargain';
    // 后端会直接拒绝越界阈值，预览必须自行回落而不是把 400 暴露给画布
    props.dataSource.bargainRate = 0.5;

    await loadGoodsWaterfall(props, createDependencies({ goodsPage }));

    expect(goodsPage).toHaveBeenCalledWith(
      expect.objectContaining({ bargainRate: 1.15 }),
    );
  });

  it('loads manually selected goods in configured order', async () => {
    const goodsByIds = vi.fn(async () => [
      { id: 'goods-2', name: '商品二' },
      { id: 'goods-1', name: '商品一' },
    ]);
    const props = createGoodsGroupDefaults();
    props.dataSource = {
      mode: 'manual',
      targetIds: ['goods-1', 'goods-2'],
    };

    const result = await loadGoodsGroup(
      props,
      createDependencies({ goodsByIds }),
    );

    expect(goodsByIds).toHaveBeenCalledWith(['goods-1', 'goods-2']);
    expect(result.map(({ id }) => id)).toEqual(['goods-1', 'goods-2']);
  });

  it('loads the sales ranking through the goods page and respects count', async () => {
    const goodsPage = vi.fn(async (_query: Record<string, unknown>) => ({
      records: [
        {
          id: 'goods-1',
          name: '第一名',
          salesPrice: '19.90',
          salesVolume: 99,
          spuUrls: ['one.png'],
          stock: 5,
        },
        {
          id: 'goods-2',
          name: '第二名',
          salesPrice: '9.90',
          salesVolume: 88,
          spuUrls: ['two.png'],
          stock: 20,
        },
      ],
    }));
    const props = createGoodsRankingDefaults();
    props.count = 1;

    const result = await loadGoodsRanking(
      props,
      createDependencies({ goodsPage }),
    );

    expect(goodsPage).toHaveBeenCalledWith(
      expect.objectContaining({ current: 1, desc: 'sales_volume', size: 1 }),
    );
    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({
      id: 'goods-1',
      imageUrl: 'one.png',
      price: 19.9,
      sales: 99,
      stock: 5,
    });
  });

  it('sorts the price ranking descending like the mobile renderer', async () => {
    const goodsPage = vi.fn(async (_query: Record<string, unknown>) => ({
      records: [],
    }));
    const props = createGoodsRankingDefaults();
    props.dataSource.metric = 'sales_price';

    await loadGoodsRanking(props, createDependencies({ goodsPage }));

    // 小程序排行对「价格排行」按降序取数；分组/瀑布流的低价优先是另一条路径
    expect(goodsPage).toHaveBeenCalledWith(
      expect.objectContaining({ desc: 'sales_price' }),
    );
  });

  it('normalizes the specs summary for waterfall/tabs cards', async () => {
    const goodsPage = vi.fn(async () => ({
      records: [
        {
          id: 'goods-1',
          name: '矿泉水',
          packageSpec: '950ml/瓶',
          salesPrice: 3.5,
          spuUrls: ['water.png'],
          stock: 30,
        },
        {
          id: 'goods-2',
          name: '单规格商品',
          packageSpec: '默认',
          salesPrice: 5,
          spuUrls: ['single.png'],
          stock: 30,
        },
      ],
    }));
    const props = createGoodsWaterfallDefaults();

    const result = await loadGoodsWaterfall(
      props,
      createDependencies({ goodsPage }),
    );

    expect(result[0]?.specsInfo).toBe('950ml/瓶');
    // 单规格的「默认」视为无规格，不能渲染成商品标签
    expect(result[1]?.specsInfo).toBeUndefined();
  });

  it('loads active activities and maps runtime status', async () => {
    const props = createLimitedActivityDefaults();

    const result = await loadLimitedActivities(
      props,
      createDependencies({
        activityPage: vi.fn(async () => ({
          records: [
            {
              activityName: '夏日拼团',
              activityStatus: '1',
              endedAt: '2099-07-20 12:00:00',
              groupPrice: 19.9,
              id: 'activity-1',
              originalPrice: 29.9,
            },
          ],
        })),
      }),
    );

    expect(result[0]).toMatchObject({
      activityPrice: 19.9,
      id: 'activity-1',
      name: '夏日拼团',
      status: 'active',
    });
  });

  it('fills activity images from related goods in one batch', async () => {
    const goodsByIds = vi.fn(async () => [
      {
        id: 'goods-1',
        salesPrice: 29.9,
        spuName: '夏日风扇',
        spuUrls: ['fan.png'],
      },
    ]);
    const props = createLimitedActivityDefaults();

    const result = await loadLimitedActivities(
      props,
      createDependencies({
        activityPage: vi.fn(async () => ({
          records: [
            {
              activityName: '夏日拼团',
              groupPrice: 19.9,
              id: 'activity-1',
              spuId: 'goods-1',
            },
          ],
        })),
        goodsByIds,
      }),
    );

    expect(goodsByIds).toHaveBeenCalledWith(['goods-1']);
    expect(result[0]).toMatchObject({
      imageUrl: 'fan.png',
      originalPrice: 29.9,
    });
  });

  it('loads the current tenant as shop information', async () => {
    const shopById = vi.fn(async () => ({
      address: '滨江路 1 号',
      id: 'tenant-1',
      logoUrl: 'logo.png',
      name: '悦航购',
      phone: '400-000-0000',
      siteUrl: 'https://example.test',
    }));

    const result = await loadShopInfo(
      'tenant-1',
      createDependencies({ shopById }),
    );

    expect(shopById).toHaveBeenCalledWith('tenant-1');
    expect(result).toEqual([
      {
        address: '滨江路 1 号',
        id: 'tenant-1',
        logoUrl: 'logo.png',
        name: '悦航购',
        phone: '400-000-0000',
        siteUrl: 'https://example.test',
      },
    ]);
  });

  it('loads shop information via the permission-free tenant shop endpoint', async () => {
    vi.mocked(getShopInfo).mockResolvedValue({
      address: '滨江路 1 号',
      id: 'tenant-1',
      logoUrl: 'logo.png',
      name: '悦航购',
      phone: '400-000-0000',
      siteUrl: 'https://example.test',
    });

    const result = await loadShopInfo('tenant-1');

    expect(getShopInfo).toHaveBeenCalledTimes(1);
    expect(getById).not.toHaveBeenCalled();
    expect(result).toEqual([
      {
        address: '滨江路 1 号',
        id: 'tenant-1',
        logoUrl: 'logo.png',
        name: '悦航购',
        phone: '400-000-0000',
        siteUrl: 'https://example.test',
      },
    ]);
  });
  it('loads seckill sessions and keeps a negative remaining stock as unknown', async () => {
    const seckillSessions = vi.fn(async () => [
      {
        goodsList: [
          {
            goodsImage: 'a.png',
            goodsName: '洗发水',
            originalPrice: 39.9,
            // 后端未下发剩余库存时必须是 -1（未知），不能落成 0（售罄）
            remainingStock: undefined,
            seckillPrice: 9.9,
            seckillStock: 100,
            skuId: 'sku-1',
            spuId: 'spu-1',
          },
        ],
        sessionId: 's-1',
        sessionName: '10 点场',
        status: 1,
      },
      // 已结束场次不应进入楼层
      { sessionId: 's-2', sessionName: '昨日场', status: 2 },
    ]);

    const result = await loadSeckillSessions(
      createSeckillDefaults(),
      createDependencies({ seckillSessions }),
    );

    expect(result).toHaveLength(1);
    expect(result[0]?.sessionId).toBe('s-1');
    expect(result[0]?.goodsList[0]?.remainingStock).toBe(-1);
    expect(result[0]?.goodsList[0]?.goodsName).toBe('洗发水');
  });

  it('passes the seckill session status through so the editor can label it', async () => {
    const seckillSessions = vi.fn(async () => [
      { sessionId: 's-9', sessionName: '预热场', status: 0 },
    ]);

    const result = await loadSeckillSessions(
      createSeckillDefaults(),
      createDependencies({ seckillSessions }),
    );

    // 未开始场次保留 status=0，前端据此显示「距开始」
    expect(result[0]?.status).toBe(0);
  });

  it('normalizes discount activities, keeping goods names and discount prices', () => {
    const result = normalizeDiscountActivities({
      records: [
        {
          activityId: 'd-1',
          activityName: '夏日折扣',
          discountType: 1,
          discountValue: 0.8,
          goodsList: [
            {
              discountPrice: 31.92,
              goodsImage: 'b.png',
              goodsName: '风扇',
              originalPrice: 39.9,
              skuId: 'sku-2',
              spuId: 'spu-2',
            },
          ],
          scope: 2,
          status: 1,
        },
        // 全场活动没有商品清单，应保留为空数组而不是被丢弃
        {
          activityId: 'd-2',
          activityName: '全场折扣',
          scope: 1,
          status: 1,
        },
      ],
    });

    expect(result).toHaveLength(2);
    expect(result[0]?.goodsList[0]).toEqual({
      discountPrice: 31.92,
      goodsImage: 'b.png',
      goodsName: '风扇',
      originalPrice: 39.9,
      skuId: 'sku-2',
      spuId: 'spu-2',
    });
    expect(result[1]?.goodsList).toEqual([]);
    // 缩略图由 loadDiscounts 补齐，归一化阶段统一落空串（与 C 端同口径）
    expect(result[0]?.coverImage).toBe('');
    expect(result[1]?.coverImage).toBe('');
  });
});

describe('retail preview: 会员专区 activity 数据源', () => {
  function activityProps(count = 2) {
    const props = createGoodsWaterfallDefaults();
    props.count = count;
    // 与装修 JSON 契约一致：activityId 空串 = 自动取当前进行中的会员价活动
    props.dataSource = { activityId: '', cacheTtl: 0, mode: 'activity' };
    return props;
  }

  const activityGoods = (spuIds: string[] = ['spu-b', 'spu-a', 'spu-c']) => ({
    activityId: 'act-1',
    activityName: '黄金会员价',
    endTime: '2099-12-31 23:59:59',
    spuIds,
  });

  it('loads activity goods in the activity order and truncates to count', async () => {
    const memberActivityGoods = vi.fn(async () => activityGoods());
    const goodsByIds = vi.fn(async () => [
      { id: 'spu-a', name: '商品A', salesPrice: 10 },
      { id: 'spu-c', name: '商品C', salesPrice: 30 },
      { id: 'spu-b', name: '商品B', salesPrice: 20 },
    ]);
    const props = activityProps(2);
    props.dataSource.activityId = 'act-1';

    const result = await loadGoodsWaterfall(
      props,
      createDependencies({ goodsByIds, memberActivityGoods }),
    );

    // activityId 透传给 C 端只读接口，商品按活动配置顺序回排后截断 count
    expect(memberActivityGoods).toHaveBeenCalledWith('act-1');
    expect(goodsByIds).toHaveBeenCalledWith(['spu-b', 'spu-a', 'spu-c']);
    expect(result.map(({ id }) => id)).toEqual(['spu-b', 'spu-a']);
    // 活动模式不走商品分页接口
    expect(goodsByIds).toHaveBeenCalledTimes(1);
  });

  it('passes undefined when activityId is empty (auto: in-progress activity)', async () => {
    const memberActivityGoods = vi.fn(async () => activityGoods(['spu-a']));

    await loadGoodsWaterfall(
      activityProps(1),
      createDependencies({
        goodsByIds: vi.fn(async () => [{ id: 'spu-a', name: '商品A' }]),
        memberActivityGoods,
      }),
    );

    expect(memberActivityGoods).toHaveBeenCalledWith(undefined);
  });

  it('returns an empty list when there is no activity or no goods (null)', async () => {
    const goodsByIds = vi.fn(async () => []);

    const result = await loadGoodsWaterfall(
      activityProps(),
      createDependencies({
        goodsByIds,
        memberActivityGoods: vi.fn(async () => null),
      }),
    );

    expect(result).toEqual([]);
    // 活动为空时不必再请求商品，避免画布多发一次无意义请求
    expect(goodsByIds).not.toHaveBeenCalled();
  });

  it('degrades to an empty list when the activity endpoint fails', async () => {
    const result = await loadGoodsWaterfall(
      activityProps(),
      createDependencies({
        memberActivityGoods: vi.fn(async () => {
          throw new Error('403 无权限');
        }),
      }),
    );

    expect(result).toEqual([]);
  });

  it('does not call the activity endpoint for automatic/manual modes', async () => {
    const memberActivityGoods = vi.fn(async () => activityGoods());
    const goodsPage = vi.fn(async () => ({ records: [] }));

    // 自动模式：仍按商品分页取数
    await loadGoodsWaterfall(
      createGoodsWaterfallDefaults(),
      createDependencies({ goodsPage, memberActivityGoods }),
    );
    expect(memberActivityGoods).not.toHaveBeenCalled();
    expect(goodsPage).toHaveBeenCalled();

    // 手选模式：仍按 targetIds 取数
    const manualProps = createGoodsWaterfallDefaults();
    manualProps.dataSource = {
      mode: 'manual',
      targetIds: ['spu-manual'],
    };
    await loadGoodsWaterfall(
      manualProps,
      createDependencies({
        goodsByIds: vi.fn(async () => [{ id: 'spu-manual', name: '手选' }]),
        memberActivityGoods,
      }),
    );
    expect(memberActivityGoods).not.toHaveBeenCalled();
  });
});
