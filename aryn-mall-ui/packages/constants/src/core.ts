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
 * @zh_CN 平台租户ID
 */
export const ARYN_PLATFORM_TENANT_ID = '1881232176465358849';
