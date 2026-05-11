import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemMenuApi } from '#/api/upms/menu';

export function useColumns(): VxeTableGridOptions<SystemMenuApi.SystemMenu>['columns'] {
  return [
    {
      align: 'left',
      field: 'name',
      fixed: 'left',
      slots: { default: 'title' },
      title: '菜单名称',
      treeNode: true,
      width: 250,
    },
    {
      field: 'permission',
      title: '菜单权限',
      width: 200,
    },
    {
      align: 'left',
      field: 'path',
      title: '菜单编码',
      width: 200,
    },
    {
      align: 'left',
      field: 'component',
      minWidth: 200,
      title: '菜单路径',
    },
    {
      align: 'right',
      field: 'operation',
      fixed: 'right',
      headerAlign: 'center',
      showOverflow: false,
      title: '操作',
      width: 250,
      slots: { default: 'operation' },
    },
  ];
}
