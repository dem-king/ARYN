import type { RetailDataDependencies } from './retail-data';

import { describe, expect, it, vi } from 'vitest';

import { createGoodsGroupDefaults } from '../../goods-group/types';
import { createGoodsRankingDefaults } from '../../goods-ranking/types';
import { createLimitedActivityDefaults } from '../../limited-activity/types';
import { createSeckillDefaults } from '../../seckill/types';
import {
  loadGoodsGroup,
  loadGoodsRanking,
  loadLimitedActivities,
  loadSeckillSessions,
  loadShopInfo,
  normalizeDiscountActivities,
} from './retail-data';

function createDependencies(
  overrides: Partial<RetailDataDependencies> = {},
): RetailDataDependencies {
  return {
    activityById: vi.fn(async () => ({})),
    activityPage: vi.fn(async () => ({ records: [] })),
    goodsByIds: vi.fn(async () => []),
    goodsPage: vi.fn(async () => ({ records: [] })),
    salesRanking: vi.fn(async () => []),
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

  it('normalizes the sales ranking and respects count', async () => {
    const props = createGoodsRankingDefaults();
    props.count = 1;

    const result = await loadGoodsRanking(
      props,
      createDependencies({
        salesRanking: vi.fn(async () => [
          { id: 'goods-1', name: '第一名', picUrl: 'one.png', salesVolume: 99 },
          { id: 'goods-2', name: '第二名', picUrl: 'two.png', salesVolume: 88 },
        ]),
      }),
    );

    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({
      id: 'goods-1',
      imageUrl: 'one.png',
      sales: 99,
    });
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
  });
});
