import type { Preferences } from '@vben-core/preferences';
import type { DeepPartial } from '@vben-core/typings';

/**
 * 如果你想所有的app都使用相同的默认偏好设置，你可以在这里定义
 * 而不是去修改 @vben-core/preferences 中的默认偏好设置
 * @param preferences
 * @returns
 */

function defineOverridesPreferences(preferences: DeepPartial<Preferences>) {
  // 先铺调用方（app 级 preferences.ts）的 app 覆盖，再补团队统一默认值。
  // 不能直接整体替换 app 对象：否则调用方传入的 app.* 会被静默丢弃
  // （如管理端一期依赖的 loginExpiredMode: 'modal'）。
  preferences.app = {
    accessMode: 'backend',
    name: 'Aryn Mall',
    ...preferences.app,
  };
  preferences.theme = {
    mode: 'light',
  };
  preferences.logo = {
    enable: true,
    source: '/static/logo.png',
  };
  preferences.copyright = {
    companyName: 'Aryn Mall',
    companySiteLink: 'https://www.iaryn.cn',
    date: '2025',
    enable: true,
  };
  return preferences;
}

export { defineOverridesPreferences };

export * from '@vben-core/preferences';
