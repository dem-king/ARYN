import { describe, expect, it } from 'vitest';

import {
  mergeActivityGoods,
  normalizeRetailActivities,
  normalizeRetailGoods,
  normalizeRetailShop,
} from '../../../../../../../aryn-mall-uniapp/src/components/diy/retail-normalizers';
import { createRetailSortParams } from '../../../../../../../aryn-mall-uniapp/src/components/diy/retail-query';

describe('mobile retail response normalization', () => {
  it('keeps live price, stock, sales, and first image for goods', () => {
    expect(
      normalizeRetailGoods({
        records: [
          {
            id: 'g-1',
            name: 'Tea',
            salesPrice: '12.50',
            salesVolume: 8,
            spuUrls: ['tea.png'],
            stock: 3,
          },
        ],
      }),
    ).toEqual([
      {
        id: 'g-1',
        imageUrl: 'tea.png',
        name: 'Tea',
        // 未下发原价的商品落 0，渲染侧据此隐藏划线（避免划出「￥0」）
        originalPrice: 0,
        price: 12.5,
        sales: 8,
        stock: 3,
      },
    ]);
  });

  it('normalizes active and ended activity states', () => {
    expect(
      normalizeRetailActivities([
        { activityStatus: '1', groupNum: 3, groupPrice: 9, id: 'a-1' },
        { activityStatus: '2', groupPrice: 7, id: 'a-2' },
      ]).map(({ activityPrice, groupNum, id, status }) => ({
        activityPrice,
        groupNum,
        id,
        status,
      })),
    ).toEqual([
      { activityPrice: 9, groupNum: 3, id: 'a-1', status: 'active' },
      // 缺失成团人数落 0，渲染侧降级为通用「拼团」徽标
      { activityPrice: 7, groupNum: 0, id: 'a-2', status: 'ended' },
    ]);
  });

  it('selects the current tenant without exposing another shop', () => {
    expect(
      normalizeRetailShop({
        address: 'Shanghai',
        email: 'private@example.com',
        id: 'tenant-b',
        name: 'B',
        packageId: 'private-package',
        phone: '10086',
      }),
    ).toEqual([
      {
        address: 'Shanghai',
        id: 'tenant-b',
        logoUrl: '',
        name: 'B',
        phone: '10086',
        siteUrl: '',
      },
    ]);
  });

  it('fills activity images from their related goods', () => {
    const activities = normalizeRetailActivities([
      {
        activityName: 'Tea group buy',
        groupPrice: 9,
        id: 'activity-1',
        spuId: 'goods-1',
      },
    ]);
    const goods = normalizeRetailGoods([
      {
        id: 'goods-1',
        salesPrice: 12,
        spuName: 'Tea',
        spuUrls: ['tea.png'],
      },
    ]);

    expect(mergeActivityGoods(activities, goods)).toMatchObject([
      {
        id: 'activity-1',
        imageUrl: 'tea.png',
        name: 'Tea group buy',
        originalPrice: 12,
      },
    ]);
  });

  it('strips the duplicated 「N人成团·」 prefix only when groupNum is present', () => {
    // 图上已有人数徽标，名称再带一遍就是同义重复；groupNum 缺失时徽标不显示人数，
    // 前缀是唯一信息，必须保留。规则见 C 端 utils/activity-name.ts。
    const activities = normalizeRetailActivities([
      {
        activityName: '2人成团·农夫山泉整箱',
        groupNum: 2,
        id: 'a1',
        groupPrice: 25,
      },
      { activityName: '2人成团·老李卤豆干', id: 'a2', groupPrice: 24 },
      { activityName: '3人份火锅套餐', groupNum: 2, id: 'a3', groupPrice: 99 },
    ]);
    expect(activities.map((item) => item.name)).toEqual([
      '农夫山泉整箱',
      '2人成团·老李卤豆干',
      '3人份火锅套餐',
    ]);
  });

  it('maps admin sorting values to product page ordering', () => {
    // 默认带 id 兜底：销量/时间大量并列时 OFFSET 分页需要唯一列，否则重复或漏商品
    expect(createRetailSortParams('sales')).toEqual({
      desc: 'sales_volume,id',
    });
    expect(createRetailSortParams('create_time')).toEqual({
      desc: 'create_time,id',
    });
    expect(createRetailSortParams('sales_price', true)).toEqual({
      asc: 'sales_price,id',
    });
    // 传空兜底列时退回单列排序
    expect(createRetailSortParams('sales_price', true, '')).toEqual({
      asc: 'sales_price',
    });
  });
});
