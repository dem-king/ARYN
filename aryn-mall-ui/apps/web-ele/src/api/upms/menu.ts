import { requestClient } from '#/api/request';

export namespace SystemMenuApi {
  /** 徽标颜色集合 */
  export const BadgeVariants = [
    'default',
    'destructive',
    'primary',
    'success',
    'warning',
  ] as const;
  /** 徽标类型集合 */
  export const BadgeTypes = ['dot', 'normal'] as const;
  /** 菜单类型集合 */
  export const MenuTypes = [
    'catalog',
    'menu',
    'embedded',
    'link',
    'button',
  ] as const;
  /** 系统菜单 */
  export interface SystemMenu {
    [key: string]: any;
    /** 后端权限标识 */
    authCode: string;
    /** 子级 */
    children?: SystemMenu[];
    /** 组件 */
    component?: string;
    /** 菜单ID */
    id: string;
    /** 菜单图标 */
    icon?: string;
    /** 菜单名称 */
    name: string;
    /** 路由路径 */
    path: string;
    /** 父级ID */
    parentId: string;
    /** 重定向 */
    redirect?: string;
    /** 菜单类型 */
    type: (typeof MenuTypes)[number];
  }
}
/**
 * 获取菜单
 */
export async function getMenu() {
  return requestClient.get('/upms/menu', {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 获取菜单树
 */
export async function getList() {
  return requestClient.get('/upms/menu/tree', {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 根据ID获取菜单
 */
export async function getById(id: string) {
  return requestClient.get(`/upms/menu/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 添加菜单
 */
export async function addObj(data: any) {
  return requestClient.post('/upms/menu', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 编辑菜单
 */
export async function editObj(data: any) {
  return requestClient.put('/upms/menu', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 删除菜单
 */
export async function delObj(id: string) {
  return requestClient.delete(`/upms/menu/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 查询租户菜单
 */
export async function getTenantMenu() {
  return requestClient.get('/upms/menu/tenant', {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 查询角色菜单
 */
export async function getMenusByRole(roleId: string) {
  return requestClient.get(`/upms/menu/role/${roleId}`, {});
}
