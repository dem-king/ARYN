interface BasicOption {
  label: string;
  value: string;
}

type SelectOption = BasicOption;

type TabOption = BasicOption;

interface BasicUserInfo {
  /**
   * 头像
   */
  avatar: string;
  /**
   * 邮箱
   */
  email?: string;
  /**
   * 首页地址
   */
  homePath?: string;
  /**
   * 用户权限
   */
  permissions?: string[];
  /**
   * 是否平台管理员（用户级身份：sys_user.platform_admin=1 且所属租户为平台租户）
   */
  platformAdmin?: boolean;
  /**
   * 用户昵称
   */
  realName: string;
  /**
   * 用户角色
   */
  roles?: string[];
  /**
   * 租户id
   */
  tenantId?: string;
  /**
   * 租户类型：1.平台租户；2.普通租户（后端按 sys_tenant.tenant_type 下发）
   */
  tenantType?: string;
  /**
   * 用户id
   */
  userId: string;
  /**
   * 用户名
   */
  username: string;
}

type ClassType =
  Array<ClassType> | boolean | null | object | string | undefined;

export type { BasicOption, BasicUserInfo, ClassType, SelectOption, TabOption };
