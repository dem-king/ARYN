import { describe, expect, it } from 'vitest';

import { filterMenuTree, flattenMenuTree } from './menu-tree';

const tree = [
  {
    id: '1',
    name: '订单管理',
    path: '/order',
    type: '0',
    applicationKey: 'app_base',
    children: [
      {
        id: '11',
        name: '订单列表',
        path: '/order/list',
        component: 'order/list/index',
        permission: 'order:order:page',
        type: '0',
        applicationKey: 'app_base',
        children: [
          {
            id: '111',
            name: '订单导出',
            permission: 'order:order:export',
            type: '1',
            applicationKey: 'app_base',
          },
        ],
      },
      {
        id: '12',
        name: '退单列表',
        path: '/order/refund',
        type: '0',
        applicationKey: 'app_base',
      },
    ],
  },
  {
    id: '2',
    name: '营销管理',
    path: '/promotion',
    type: '0',
    applicationKey: 'app_market',
    children: [
      {
        id: '21',
        name: '优惠券',
        path: '/promotion/coupon',
        type: '0',
        applicationKey: 'app_market',
      },
    ],
  },
];

const ids = (nodes: ReturnType<typeof filterMenuTree>['nodes']) =>
  flattenMenuTree(nodes).map((node) => node.id);

describe('filterMenuTree', () => {
  it('无筛选条件时保留完整树并统计全部节点', () => {
    const { matchedCount, nodes } = filterMenuTree(tree);
    expect(matchedCount).toBe(6);
    expect(ids(nodes)).toEqual(['1', '11', '111', '12', '2', '21']);
  });

  it('关键字命中子节点时保留其祖先', () => {
    const { matchedCount, nodes } = filterMenuTree(tree, { keyword: '退单' });
    expect(matchedCount).toBe(1);
    expect(ids(nodes)).toEqual(['1', '12']);
  });

  it('关键字匹配权限标识与组件路径', () => {
    expect(ids(filterMenuTree(tree, { keyword: 'export' }).nodes)).toEqual([
      '1',
      '11',
      '111',
    ]);
    expect(ids(filterMenuTree(tree, { keyword: 'list/index' }).nodes)).toEqual([
      '1',
      '11',
    ]);
  });

  it('按类型过滤时隐藏不匹配的子节点', () => {
    const { matchedCount, nodes } = filterMenuTree(tree, { type: '1' });
    expect(matchedCount).toBe(1);
    expect(ids(nodes)).toEqual(['1', '11', '111']);
  });

  it('按应用过滤且保留未命中的祖先', () => {
    const { matchedCount, nodes } = filterMenuTree(tree, {
      applicationKey: 'app_market',
    });
    expect(matchedCount).toBe(2);
    expect(ids(nodes)).toEqual(['2', '21']);
  });

  it('组合条件只保留同时命中的节点', () => {
    const { matchedCount, nodes } = filterMenuTree(tree, {
      applicationKey: 'app_base',
      keyword: '订单',
      type: '1',
    });
    expect(matchedCount).toBe(1);
    expect(ids(nodes)).toEqual(['1', '11', '111']);
  });

  it('不修改原始树', () => {
    filterMenuTree(tree, { keyword: '退单' });
    expect(tree[0]?.children?.[1]?.name).toBe('退单列表');
    expect(tree[0]?.children).toHaveLength(2);
  });
});
