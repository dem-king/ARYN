import type { BasicUserInfo } from '@vben-core/typings';

/** 用户信息 */
interface UserInfo extends BasicUserInfo {
  /**
   * 用户描述
   */
  desc: string;
  /**
   * 首页地址
   */
  homePath: string;

  /**
   * accessToken
   */
  token: string;
  /**
   * 用户权限
   */
  permissions: string[];
  /**
   * 租户类型：1.平台租户；2.普通租户（后端按 sys_tenant.tenant_type 下发）
   */
  tenantType?: string;
  /**
   * 是否平台管理员（用户级身份，决定是否显示租户切换与平台管理菜单）
   */
  platformAdmin?: boolean;
}

export type { UserInfo };
