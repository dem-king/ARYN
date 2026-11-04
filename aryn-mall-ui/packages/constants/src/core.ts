/**
 * @zh_CN 登录页面 url 地址
 */
export const LOGIN_PATH = '/auth/login';

export interface LanguageOption {
  label: string;
  value: 'en-US' | 'zh-CN';
}

/**
 * Supported languages
 */
export const SUPPORT_LANGUAGES: LanguageOption[] = [
  {
    label: '简体中文',
    value: 'zh-CN',
  },
  {
    label: 'English',
    value: 'en-US',
  },
];
/**
 * @zh_CN 文档地址
 */
export const ARYN_DOC_URL = 'https://www.iaryn.cn';
/**
 * @zh_CN 源码地址
 */
export const ARYN_GITEE_URL = 'https://gitee.com/lijiaxing_boy/aryn-mall';
/**
 * @zh_CN 租户类型：平台租户（长期有效，不参与授权期管理）
 *
 * 平台管理员身份以登录用户信息里的 `tenantType` 判定（后端按
 * sys_tenant.tenant_type 下发），前端不再硬编码平台租户 ID。
 */
export const ARYN_TENANT_TYPE_PLATFORM = '1';
/**
 * @zh_CN 租户类型：普通租户（按授权起止时间管控）
 */
export const ARYN_TENANT_TYPE_STANDARD = '2';
