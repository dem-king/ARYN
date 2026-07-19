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
