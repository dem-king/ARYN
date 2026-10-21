import { describe, expect, it, vi } from 'vitest';

import { pickerDefinitions } from './registry';

const pageDesignMocks = vi.hoisted(() => ({
  getById: vi.fn(),
  getPage: vi.fn(),
  pageTypeLabel: vi.fn((value: string) => `类型${value}`),
}));

vi.mock('#/api/product/goods-spu', () => ({ getByIds: vi.fn() }));
vi.mock('#/api/promotion/coupon-info', () => ({ getByIds: vi.fn() }));
vi.mock('#/api/promotion/discount', () => ({
  getById: vi.fn(),
  getPage: vi.fn(),
}));
vi.mock('#/api/promotion/group-buy-activity', () => ({
  getById: vi.fn(),
  getPage: vi.fn(),
}));
vi.mock('#/api/promotion/page-design', () => pageDesignMocks);
vi.mock('#/api/promotion/seckill', () => ({ getSessionPage: vi.fn() }));

describe('微页面选择器（装修链接 page 类型）', () => {
  it('列表只查「已发布 + pageType=0」的微页面，避免 C 端 diy-page 打不开', async () => {
    pageDesignMocks.getPage.mockResolvedValue({ records: [], total: 0 });

    await pickerDefinitions.page.dialogProps?.loader({ current: 2, size: 10 });

    expect(pageDesignMocks.getPage).toHaveBeenCalledWith({
      current: 2,
      pageType: '0',
      publishedStatus: '1',
      size: 10,
    });
  });

  it('回显时把未发布/内嵌页/查不到的页面标为失效，可删除重选', async () => {
    pageDesignMocks.getById
      .mockResolvedValueOnce({
        id: 'p1',
        pageName: '未发布微页面',
        pageType: '0',
        publishedStatus: '0',
      })
      .mockResolvedValueOnce({
        id: 'p2',
        pageName: '商详装修片段',
        pageType: '2',
        publishedStatus: '1',
      })
      .mockResolvedValueOnce({
        id: 'p3',
        pageName: '已发布微页面',
        pageType: '0',
        publishedStatus: '1',
      })
      .mockRejectedValueOnce(new Error('403'));

    const items = await pickerDefinitions.page.hydrate([
      'p1',
      'p2',
      'p3',
      'p4',
    ]);

    expect(items[0]).toMatchObject({ id: 'p1', invalid: true });
    expect(items[0]?.subtitle).toContain('未发布');
    expect(items[1]).toMatchObject({ id: 'p2', invalid: true });
    expect(items[1]?.subtitle).toContain('不能作为链接落地页');
    expect(items[2]).toMatchObject({ id: 'p3', title: '已发布微页面' });
    expect(items[2]?.invalid).toBeUndefined();
    expect(items[3]).toMatchObject({ id: 'p4', invalid: true });
  });
});
