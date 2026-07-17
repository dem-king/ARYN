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
        price: 12.5,
        sales: 8,
        stock: 3,
      },
    ]);
  });

  it('normalizes active and ended activity states', () => {
    expect(
      normalizeRetailActivities([
        { activityStatus: '1', groupPrice: 9, id: 'a-1' },
        { activityStatus: '2', groupPrice: 7, id: 'a-2' },
      ]).map(({ activityPrice, id, status }) => ({
        activityPrice,
        id,
        status,
      })),
    ).toEqual([
      { activityPrice: 9, id: 'a-1', status: 'active' },
      { activityPrice: 7, id: 'a-2', status: 'ended' },
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

  it('maps admin sorting values to product page ordering', () => {
    expect(createRetailSortParams('sales')).toEqual({
      desc: 'sales_volume',
    });
    expect(createRetailSortParams('create_time')).toEqual({
      desc: 'create_time',
    });
    expect(createRetailSortParams('sales_price', true)).toEqual({
      asc: 'sales_price',
    });
  });
});
